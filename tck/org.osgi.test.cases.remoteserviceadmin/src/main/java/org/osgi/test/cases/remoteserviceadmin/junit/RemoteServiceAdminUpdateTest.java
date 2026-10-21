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
package org.osgi.test.cases.remoteserviceadmin.junit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.ImportRegistration;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdmin;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdminEvent;
import org.osgi.test.cases.remoteserviceadmin.common.ModifiableService;
import org.osgi.test.cases.remoteserviceadmin.common.TestEventHandler;
import org.osgi.test.cases.remoteserviceadmin.common.Utils;

public class RemoteServiceAdminUpdateTest extends MultiFrameworkTestCase {
	private static final String SYSTEM_PACKAGES_EXTRA = "org.osgi.test.cases.remoteserviceadmin.system.packages.extra";
	private long m_timeout;

	@Override
	public Map<String, String> getConfiguration() {
		Map<String, String> configuration = new HashMap<String, String>();
		configuration.put(Constants.FRAMEWORK_STORAGE_CLEAN, "true");

		// make sure that the server framework System Bundle exports the
		// interfaces
		String systemPackagesXtra = getProperty(SYSTEM_PACKAGES_EXTRA);
		if (systemPackagesXtra != null) {
			configuration.put(Constants.FRAMEWORK_SYSTEMPACKAGES_EXTRA,
					systemPackagesXtra);
		}
		int console = getIntegerProperty("osgi.console", 0);
		if (console != 0) {
			configuration.put("osgi.console", "" + console + 1);
		}

		m_timeout = getLongProperty("rsa.tck.timeout", 300000L);

		return configuration;
	}

	/**
	 * Uses tb8 to export a "modifiable" service, imports this service and asks
	 * it to modify it own service export. Then check if the updated service can
	 * be updated and contains the newly added parameter that changed its
	 * registration.
	 * */
	@Test
	public void testExportImportManually() throws Exception {
		verifyFramework();

		TestEventHandler eventHandler = new TestEventHandler(m_timeout);

		Hashtable<String, Object> props = new Hashtable<String, Object>();
		props.put(EventConstants.EVENT_TOPIC, new String[] {
				"org/osgi/service/remoteserviceadmin/IMPORT_REGISTRATION",
				"org/osgi/service/remoteserviceadmin/IMPORT_UNREGISTRATION",
				"org/osgi/service/remoteserviceadmin/IMPORT_ERROR",
				"org/osgi/service/remoteserviceadmin/IMPORT_UPDATE" });

		// register an event handler to verify that the required register and
		// update events are generated
		registerService(EventHandler.class.getName(), eventHandler, props);

		//
		// install test bundle in child framework
		//
		BundleContext childContext = getFramework().getBundleContext();


		Bundle tb8Bundle = installBundle(childContext, "/tb8.jar");
		assertThat(tb8Bundle).isNotNull();
		tb8Bundle.start();

		//
		// find the RSA in the parent framework and import the
		// service
		//
		ServiceReference<RemoteServiceAdmin> rsaRef = context
				.getServiceReference(RemoteServiceAdmin.class);
		assertThat(rsaRef).isNotNull();
		RemoteServiceAdmin rsa = context.getService(
				rsaRef);
		assertThat(rsa).isNotNull();

		try {
			// give the child framework and tb8 some time to start and export
			// the service
			Thread.sleep(10000);

			// reconstruct the endpoint description in version 1.0.0
			EndpointDescription endpoint = Utils.reconstructEndpoint("1.0.0",
					context);
			// gather all the service and exporting intents
			List<String> endpointIntents = endpoint.getIntents();
			assertThat(endpointIntents).isNotNull();
			assertThat(endpointIntents).isNotEmpty();

			//
			// 122.4.2: Importing
			// positive test: import the service
			//
			ImportRegistration importReg = rsa.importService(endpoint);
			assertThat(importReg).isNotNull();
			assertThat(importReg.getException()).isNull();

			{ // check if the required IMPORT_REGISTRATION event was raised
				Event event = eventHandler
					.getNextEventForTopic("org/osgi/service/remoteserviceadmin/IMPORT_REGISTRATION");
				assertThat(event).isNotNull();
				RemoteServiceAdminEvent rsaevent = TestEventHandler
						.verifyBasicRsaEventProperties(rsaRef, event);
				assertThat(rsaevent).isNotNull();
				assertThat(event.getProperty("timestamp")).isNotNull();

				// check event type
				@SuppressWarnings("unused")
				String topic = event.getTopic();
				assertThat(event.getProperty("cause")).as("cause in event").isNull();
				assertThat(rsaevent.getType()).isEqualTo(RemoteServiceAdminEvent.IMPORT_REGISTRATION);
			}

			// get the modifiable service that has been exported from tb8
			ModifiableService modifiableService = null;
			{
				ServiceReference<ModifiableService> modifiableServiceRef = context
						.getServiceReference(ModifiableService.class);
				assertThat(modifiableServiceRef).isNotNull();
				modifiableService = context
						.getService(
						modifiableServiceRef);
				assertThat(modifiableService).isNotNull();
			}

			//
			// Now as we have imported the service, ask the service to modify
			// itself and the export description
			//
			modifiableService.addServiceProperty();

			//
			// Give the remoting implementation some time to actually update the
			// service export
			//
			Thread.sleep(5000);
			

			//
			// now try to import the updated service export and verify that the
			// new property is available
			//

			// reconstruct the endpoint description of the modified endpoint
			EndpointDescription endpointModified = Utils.reconstructEndpoint(
					"1.0.0", context, 1);

			// request the update of the import
			assertThat(importReg.update(endpointModified)).isTrue();
			assertThat(importReg.getException()).isNull();

			// get the modified service that has been exported by tb8
			ModifiableService alreadyModifiedService = null;
			{
				Collection<ServiceReference<ModifiableService>> alreadyModifiedServiceRefs = context
						.getServiceReferences(
								ModifiableService.class,
								"(someNewProp=SomeValue)");

				assertThat(alreadyModifiedServiceRefs.size() >= 1).as("after importing the updated remote service, we need to be able to find at least one suitable service").isTrue();

				ServiceReference<ModifiableService> alreadyModifiedServiceRef = alreadyModifiedServiceRefs
						.iterator()
						.next();
				
				assertThat(alreadyModifiedServiceRef).isNotNull();
				alreadyModifiedService = context
						.getService(
						alreadyModifiedServiceRef);
				assertThat(alreadyModifiedService).isNotNull();
			}

			{ // check if the required IMPORT_UPDATE event was raised
				Event event = eventHandler
						.getNextEventForTopic("org/osgi/service/remoteserviceadmin/IMPORT_UPDATE");
				assertThat(event).as("After the upddate of an imported endpoit we should have received an IMPORT_UPDATE event").isNotNull();

				RemoteServiceAdminEvent rsaevent = TestEventHandler
						.verifyBasicRsaEventProperties(
						rsaRef, event);
				assertThat(rsaevent).isNotNull();
				assertThat(event.getProperty("timestamp")).isNotNull();

				// check event type
				@SuppressWarnings("unused")
				String topic = event.getTopic();
				assertThat(event.getProperty("cause")).as("cause in event").isNull();
				assertThat(rsaevent.getType()).isEqualTo(RemoteServiceAdminEvent.IMPORT_UPDATE);
			}

		} finally {
			// Make sure the service instance of the RSA can be closed by the
			// RSA Service Factory
			context.ungetService(rsaRef);
		}
	}
}
