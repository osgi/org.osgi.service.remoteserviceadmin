/*******************************************************************************
 * Copyright (c) Contributors to the Eclipse Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0 
 *******************************************************************************/
package org.osgi.test.cases.remoteserviceadmin.secure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdmin;

/**
 * Use RSA service to register a service in a child framework and then import
 * the same service on the parent framework. This test does not explicitly use
 * the discovery, but manually conveys the endpoint information.
 *
 * @author <a href="mailto:tdiekman@tibco.com">Tim Diekmann</a>
 * @version 1.0.0
 */
public class RemoteServiceAdminSecureTest extends MultiFrameworkTestCase {
	private static final String	SYSTEM_PACKAGES_EXTRA	= "org.osgi.test.cases.remoteserviceadmin.secure.system.packages.extra";

	/**
	 * @see org.osgi.test.cases.remoteserviceadmin.secure.MultiFrameworkTestCase#getConfiguration()
	 */
	@Override
	public Map<String, String> getConfiguration() {
		Map<String, String> configuration = new HashMap<String, String>();
		configuration.put(Constants.FRAMEWORK_STORAGE_CLEAN, "true");

		//make sure that the server framework System Bundle exports the interfaces
		String systemPackagesXtra = getProperty(SYSTEM_PACKAGES_EXTRA);
		if (systemPackagesXtra != null) {
			configuration.put(Constants.FRAMEWORK_SYSTEMPACKAGES_EXTRA,
					systemPackagesXtra);
		}
		int console = getIntegerProperty("osgi.console", 0);
		if (console != 0) {
			configuration.put("osgi.console", "" + console + 1);
		}

		configuration.put("org.osgi.test.cases.remoteserviceadmin.secure.role",
				"exporter");

		return configuration;
	}

	/**
	 * Sets up a child framework in which a service is exported. In the parent framework the
	 * EndpointDescription is passed to the RSA service to import the service from the child
	 * framework. This manual step bypasses Discovery, which would normally do the transport
	 * between the two frameworks.
	 */
	@Test
	public void testExportImportManuallyWithPermision() throws Exception {
		verifyFramework();

		//
		// install test bundle in child framework
		//
		BundleContext childContext = getFramework().getBundleContext();

		Bundle tbexporterBundle = installBundle(childContext, "/tb_exporter.jar");
		assertThat(tbexporterBundle).isNotNull();
		assertThat(childContext
						.getProperty("org.osgi.test.cases.remoteserviceadmin.secure.role")).isEqualTo("exporter");

		// start test bundle in child framework
		// this will run the test in the child framework and fail
		tbexporterBundle.start();

		// now install the importer in the parent (this) framework
		Bundle tbimporterBundle = installBundle(context, "/tb_importer.jar");
		assertThat(tbimporterBundle).isNotNull();
		assertThat(context.getProperty(
						"org.osgi.test.cases.remoteserviceadmin.secure.role")).isNull();

		tbimporterBundle.start();

		Thread.sleep(2000); // wait 2 s

		// //////////////DEBUG REMOVE
		ServiceReference<RemoteServiceAdmin> rsaRef = context
				.getServiceReference(RemoteServiceAdmin.class);
		assertThat(rsaRef).isNotNull();
		RemoteServiceAdmin rsa = context
				.getService(rsaRef);
		assertThat(rsa).isNotNull();
		System.out.println("##############  " + rsa.getImportedEndpoints());
		// //////////////DEBUG REMOVE END


		// install the reader bundle to test the READ permission
		Bundle creaderBundle = installBundle(childContext, "/tb_reader.jar");
		assertThat(creaderBundle).isNotNull();
		Bundle preaderBundle = installBundle(context, "/tb_reader.jar");
		assertThat(preaderBundle).isNotNull();

		// start the reader bundles which will try to call getExportedEndpoints
		// / getImportedEndpoints and check if the results are correct for the
		// exporting and the importing side
		creaderBundle.start();

		preaderBundle.start();

		tbimporterBundle.stop();

		tbexporterBundle.stop();
	}


	/**
	 * Sets up a child framework.
	 * Install and try to start an exporter, an importer, and a reader without
	 * the appropriate permissions.
	 */
	@Test
	public void testNoPermissions() throws Exception {
		verifyFramework();

		//
		// install test bundle in child framework
		//
		BundleContext childContext = getFramework().getBundleContext();

		Bundle tbexporterBundle = installBundle(childContext, "/tb_exporter_noperm.jar");
		assertThat(tbexporterBundle).isNotNull();

		// start test bundle in child framework
		// this will run the test in the child framework and fail
		try {
			tbexporterBundle.start();
			fail("The bundle should not start cleanly");
		} catch (BundleException be) {
			assertThat(be.getCause()).isNotNull();
			assertThat(be.getCause() instanceof InvocationTargetException
							&& "Export Failed"
									.equals(be.getCause().getMessage())).as("Not caused by a failed ExportRegistration").isTrue();
			assertThat(be.getCause().getCause()).isInstanceOf(SecurityException.class);
		}

		tbexporterBundle.stop();


		// now install a build that has the required permissions so that a
		// service is exported that can later be imported
		Bundle tbexporterPermBundle = installBundle(childContext, "/tb_exporter.jar");
		assertThat(tbexporterPermBundle).isNotNull();
		tbexporterPermBundle.start();


		// now install the importer in the parent (this) framework
		Bundle tbimporterBundle = installBundle(context, "/tb_importer_noperm.jar");
		assertThat(tbimporterBundle).isNotNull();

		try {
			tbimporterBundle.start();
			fail("SecurityException expected");
		} catch (Exception se) {
			assertThat(se.getCause()).isNotNull();
			assertThat(se.getCause()).isInstanceOf(SecurityException.class);
		}

		// install the reader bundle to test the READ permission
		Bundle preaderBundle = installBundle(context, "/tb_reader_noperm.jar");
		assertThat(preaderBundle).isNotNull();

		// Bug 2596: calls to getExportedServices / getImportedServices should
		// not return a SecurityException but return an empty List. The bundle
		// Activator checks for this and fails with an assertion
		try {
			preaderBundle.start();
			// fail("SecurityException expected");
		} catch (Exception se) {
			assertThat(se.getCause()).isNotNull();
			assertThat(se.getCause()).isInstanceOf(SecurityException.class);
		}

		tbexporterPermBundle.stop();
		tbimporterBundle.stop();


	}
}
