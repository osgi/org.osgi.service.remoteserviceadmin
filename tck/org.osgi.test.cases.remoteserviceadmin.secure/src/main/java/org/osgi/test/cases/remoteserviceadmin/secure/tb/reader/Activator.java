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
package org.osgi.test.cases.remoteserviceadmin.secure.tb.reader;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.remoteserviceadmin.ExportReference;
import org.osgi.service.remoteserviceadmin.ImportReference;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdmin;

/**
 * This class reads imported and exported service endpoints from
 * the Remote Admin service. For this it requires the READ EndpointPermission.
 * 
 * @author <a href="mailto:tdiekman@tibco.com">Tim Diekmann</a>
 * @version 1.0.0
 */
public class Activator implements BundleActivator {
	BundleContext                  context;
	RemoteServiceAdmin             rsa;

	/**
	 * @see org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext)
	 */
	@Override
	public void start(BundleContext context) throws Exception {
		this.context = context;
		test();
	}

	/**
	 * @see org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
	 */
	@Override
	public void stop(BundleContext context) throws Exception {
	}

	/**
	 * Look up the RSA service and list all exported and imported services.
	 * This requires READ permissions.
	 * 
	 * @throws Exception
	 */
	public void test() throws Exception {
		// lookup RemoteServiceAdmin service

		boolean isExportingFramework = "exporter"
				.equals(context
						.getProperty("org.osgi.test.cases.remoteserviceadmin.secure.role"));

		// ////////////// DEBUG REMOVE
		System.out.println("************* Activator in which framework:  "
				+ isExportingFramework);

		Bundle[] bs = context.getBundles();
		for (Bundle bundle : bs) {
			System.out.println("*******  " + bundle.getSymbolicName());
		}
		// //////////////DEBUG REMOVE END

		ServiceReference<RemoteServiceAdmin> rsaRef = context
				.getServiceReference(RemoteServiceAdmin.class);
		assertThat(rsaRef).isNotNull();
		rsa = context.getService(rsaRef);
		assertThat(rsa).isNotNull();
		try {
			Collection<ExportReference> coll = rsa.getExportedServices();
			System.out.println("********** " + coll);
			assertThat(coll).isNotNull();
			if (isExportingFramework) {
				assertThat(coll.size()).isGreaterThan(0);
			} else {
				assertThat(coll.size()).isEqualTo(0);
			}
			
			Collection<ImportReference> icoll = rsa.getImportedEndpoints();
			assertThat(icoll).isNotNull();
			if (!isExportingFramework) {
				assertThat(icoll.size()).isGreaterThan(0);
			} else {
				assertThat(icoll.size()).isEqualTo(0);
			}
		} finally {
			// Make sure the service instance of the RSA can be closed by the RSA Service Factory
			context.ungetService(rsaRef);
		}
	}
}
