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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.io.Serializable;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.ExportReference;
import org.osgi.service.remoteserviceadmin.ExportRegistration;
import org.osgi.service.remoteserviceadmin.RemoteConstants;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdmin;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdminEvent;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdminListener;
import org.osgi.test.cases.remoteserviceadmin.common.A;
import org.osgi.test.cases.remoteserviceadmin.common.B;
import org.osgi.test.cases.remoteserviceadmin.common.RemoteServiceConstants;
import org.osgi.test.cases.remoteserviceadmin.common.TestEventHandler;
import org.osgi.util.tracker.ServiceTracker;

/**
 * RSA 122.4.1 Exporting test cases
 *
 * @author <a href="mailto:tdiekman@tibco.com">Tim Diekmann</a>
 * @version 1.0.0
 */
public class RemoteServiceAdminExportTest extends RSATestSupport {
	/**
	 * Magic value. Properties with this value will be replaced by a socket port number that is currently free.
	 */
    private static final String FREE_PORT = "@@FREE_PORT@@";

	private RemoteServiceAdmin remoteServiceAdmin;

	long						timeout;

	@BeforeEach
	public void setUp() throws Exception {


		timeout = getLongProperty("rsa.tck.timeout", 300000L);

		// Wait for the RSA service to be registered before beginning
		ServiceTracker<RemoteServiceAdmin, RemoteServiceAdmin> tracker = new ServiceTracker<RemoteServiceAdmin, RemoteServiceAdmin>(
				context, RemoteServiceAdmin.class, null);
		tracker.open();
		tracker.waitForService(timeout);
		tracker.close();

		remoteServiceAdmin = getService(RemoteServiceAdmin.class);
	}
	@AfterEach
	public void tearDown() throws Exception {

		ungetAllServices();

		// wait until all remaining exports / imports are gone
		remoteServiceAdmin = getService(RemoteServiceAdmin.class);
		int x = (int) (timeout / 500L);
		while (remoteServiceAdmin.getExportedServices().size() > 0
				|| remoteServiceAdmin.getImportedEndpoints().size() > 0) {
			--x;

			System.out
					.println("Teardown: Waiting for remaining exports / imports to be closed");

			if (x < 0)
				throw new Exception(
						"there are exported or imported services left after the test ended");
			Thread.sleep(500);
		}
		ungetAllServices();
		// wait for a little while so that no events remain queued somewhere
		Thread.sleep(3000);

	}

	/**
	 * 122.4.1 Exporting
	 *
	 * Tests the export of a service with negative edge cases according to
	 * the spec.
	 */
	@Test
	public void testExportUnsupportedConfigType() throws Exception {
		// create an register a service
		Hashtable<String, String> dictionary = new Hashtable<String, String>();
		dictionary.put("mykey", "will be overridden");
		dictionary.put("myprop", "myvalue");
		dictionary.put(RemoteServiceConstants.SERVICE_EXPORTED_INTERFACES, A.class.getName());

		TestService service = new TestService();

		ServiceRegistration< ? > registration = context
				.registerService(new String[] {
						A.class.getName(), B.class.getName()
				},
				                                                        service, dictionary);
		assertThat(registration).isNotNull();

		try {
			//
			// 122.4.1 export the service
			//
			Map<String, Object> properties = loadCTProperties();
			properties.put("mykey", "has been overridden");
			properties.put("objectClass", "can.not.be.changed.Class");
			properties.put("service.id", "can.not.be.changed.Id");

			//
			// negative test: give a configuration type that is not supported by the RSA
			// implementation. The exportService call has to be ignored then
			properties.put(RemoteConstants.SERVICE_EXPORTED_CONFIGS, "guaranteed_not_supported_" + System.currentTimeMillis());

			Collection<ExportRegistration> exportRegistrations = remoteServiceAdmin.exportService(registration.getReference(), properties);
			assertThat(exportRegistrations).as("122.4.1: service must not be exported due to unsatisfied config type").isEmpty();
		} finally {
			registration.unregister();
		}
	}

	/**
	 * 122.4.1 Exporting
	 *
	 * Tests the export of a service with negative edge cases according to
	 * the spec.
	 */
	@Test
	public void testExportUnsupportedIntent() throws Exception {
		// create an register a service
		Hashtable<String, String> dictionary = new Hashtable<String, String>();
		dictionary.put("mykey", "will be overridden");
		dictionary.put("myprop", "myvalue");
		dictionary.put(RemoteServiceConstants.SERVICE_EXPORTED_INTERFACES, A.class.getName());

		TestService service = new TestService();

		ServiceRegistration< ? > registration = context
				.registerService(new String[] {
						A.class.getName(), B.class.getName()
				},
				                                                        service, dictionary);
		assertThat(registration).isNotNull();

		try {
			//
			// 122.4.1 export the service
			//
			Map<String, Object> properties = loadCTProperties();
			properties.put("mykey", "has been overridden");
			properties.put("objectClass", "can.not.be.changed.Class");
			properties.put("service.id", "can.not.be.changed.Id");

			//
			// negative test: give a service intent that is not supported by the RSA
			// implementation. The exportService call has to be ignored then
			properties.put(RemoteConstants.SERVICE_EXPORTED_INTENTS, "guaranteed_not_supported_" + System.currentTimeMillis());

			Collection<ExportRegistration> exportRegistrations = remoteServiceAdmin.exportService(registration.getReference(), properties);
			assertThat(exportRegistrations).as("122.4.1: service must not be exported due to unsatisfied service intent").isEmpty();
			//		assertTrue("122.4.1: service must not be exported due to unsatisfied config type", exportRegistrations.isEmpty());
		} finally {
			registration.unregister();
		}
	}

	/**
	 * 122.4.1 Exporting
	 *
	 * Tests the export of a service with negative edge cases according to
	 * the spec.
	 */
	@Test
	public void testExportUnsupportedIntentExtra() throws Exception {
		// create an register a service
		Hashtable<String, String> dictionary = new Hashtable<String, String>();
		dictionary.put("mykey", "will be overridden");
		dictionary.put("myprop", "myvalue");
		dictionary.put(RemoteServiceConstants.SERVICE_EXPORTED_INTERFACES, A.class.getName());

		TestService service = new TestService();

		ServiceRegistration< ? > registration = context
				.registerService(new String[] {
						A.class.getName(), B.class.getName()
				},
				                                                        service, dictionary);
		assertThat(registration).isNotNull();

		try {
			//
			// 122.4.1 export the service
			//
			Map<String, Object> properties = loadCTProperties();
			properties.put("mykey", "has been overridden");
			properties.put("objectClass", "can.not.be.changed.Class");
			properties.put("service.id", "can.not.be.changed.Id");

			//
			// negative test: give a service intent that is not supported by the RSA
			// implementation. The exportService call has to be ignored then
			properties.put(RemoteConstants.SERVICE_EXPORTED_INTENTS_EXTRA, "guaranteed_not_supported_" + System.currentTimeMillis());

			Collection<ExportRegistration> exportRegistrations = remoteServiceAdmin.exportService(registration.getReference(), properties);
			assertThat(exportRegistrations).as("122.4.1: service must not be exported due to unsatisfied service intent").isEmpty();
			//		assertTrue("122.4.1: service must not be exported due to unsatisfied config type", exportRegistrations.isEmpty());
		} finally {
			registration.unregister();
		}
	}

	/**
	 * 122.4.1 Exporting
	 *
	 * Tests the export of a service with negative edge cases according to
	 * the spec.
	 */
	@Test
	public void testExportInvalidInterface() throws Exception {
		// create an register a service
		Hashtable<String, String> dictionary = new Hashtable<String, String>();
		dictionary.put("mykey", "will be overridden");
		dictionary.put("myprop", "myvalue");
		dictionary.put(RemoteServiceConstants.SERVICE_EXPORTED_INTERFACES, A.class.getName());

		TestService service = new TestService();

		ServiceRegistration< ? > registration = context
				.registerService(new String[] {
						A.class.getName(), B.class.getName()
				},
				                                                        service, dictionary);
		assertThat(registration).isNotNull();

		try {
			//
			// 122.4.1 export the service
			//
			Map<String, Object> properties = loadCTProperties();
			properties.put("mykey", "has been overridden");
			properties.put("objectClass", "can.not.be.changed.Class");
			properties.put("service.id", "can.not.be.changed.Id");

			properties.put(RemoteConstants.SERVICE_EXPORTED_INTERFACES, "something.else.Interface");

			assertThatExceptionOfType(IllegalArgumentException.class).as("122.5.1: syntactically incorrect exported interfaces must result in an IllegalArgumentException")
					.isThrownBy(() -> remoteServiceAdmin.exportService(registration.getReference(), properties));
		} finally {
			registration.unregister();
		}
	}

	/**
	 * 122.4.1 Exporting
	 * 
	 * Tests the export of a service with negative edge cases according to the
	 * spec. Register a service and make sure that an event is sent to a
	 * registered RemoteServiceAdminListener
	 */
	@Test
	public void testExportRSAListenerNotification() throws Exception {
		// create an register a service
		Hashtable<String, String> dictionary = new Hashtable<String, String>();
		dictionary.put("mykey", "will be overridden");
		dictionary.put("myprop", "myvalue");

		TestService service = new TestService();

		ServiceRegistration< ? > registration = context
				.registerService(new String[] {
						A.class.getName(), B.class.getName()
				},
				                                                        service, dictionary);
		assertThat(registration).isNotNull();

		try {
			//
			// register a RemoteServiceAdminListener to receive the export
			// notification
			//
			TestRemoteServiceAdminListener remoteServiceAdminListener = new TestRemoteServiceAdminListener();
			registerService(RemoteServiceAdminListener.class.getName(), remoteServiceAdminListener, null);

			//
			// 122.4.1 export the service
			//
			Map<String, Object> properties = loadCTProperties();
			properties.put("mykey", "has been overridden");
			properties.put("objectClass", "can.not.be.changed.Class");
			properties.put("service.id", "can.not.be.changed.Id");
            properties.put(RemoteServiceConstants.SERVICE_EXPORTED_INTERFACES, A.class.getName());

			Collection<ExportRegistration> exportRegistrations = remoteServiceAdmin.exportService(registration.getReference(), properties);
			assertThat(exportRegistrations).isNotNull();

			//
			// 122.8 verify that export notification was sent to RemoteServiceAdminListeners
			//
			boolean exportFailed = false;

			Thread.sleep(2000); // give the system the chance to deliver all
								// events.
			RemoteServiceAdminEvent event = remoteServiceAdminListener.getNextEvent();
			assertThat(event).as("no RemoteServiceAdminEvent received").isNotNull();
			assertThat(remoteServiceAdminListener.getEventCount()).isZero();
			assertThat(event.getSource()).as("122.10.11: source must not be null").isNotNull();

			ExportReference exportReference  = null;

			switch (event.getType()) {
			case RemoteServiceAdminEvent.EXPORT_REGISTRATION:
				assertThat(event.getException()).isNull();
				assertThat(event.getType()).as("122.10.11: event type is wrong").isEqualTo(RemoteServiceAdminEvent.EXPORT_REGISTRATION);

				exportReference = event.getExportReference();
				assertThat(exportReference).as("ExportReference expected in event").isNotNull();

				assertThat(exportReference.getExportedEndpoint()).isNotNull();

				// validate EndpointDescription

				try {
					new EndpointDescription(exportReference.getExportedEndpoint().getProperties());
				} catch (IllegalArgumentException ie) {
					fail("invalid EndpointDescription of successful export registration");
				}

				for (Iterator<ExportRegistration> it = exportRegistrations.iterator(); it.hasNext(); ) {
					assertThat(it.next().getException()).as("successful export must not throw Exception").isNull();
				}
				break;
			case RemoteServiceAdminEvent.EXPORT_ERROR:
				exportFailed = true;
				assertThat(event.getException()).isNotNull();
                assertThat(event.getExportReference()).as("122.7: event must contain the ExportReference").isNotNull();
				break;
			default:
				fail("wrong event type");
			}


			// gather all export references so that we can check if calls
			// against them are really null once the ExportRegistrations where
			// closed. Need to gather them here as later there will be no chance
			// to get them anympore.
			List<ExportReference> oldExportReferences = new LinkedList<ExportReference>();
			for (Iterator<ExportRegistration> regIt = exportRegistrations
					.iterator(); regIt.hasNext();) {
				ExportRegistration exportRegistration = regIt.next();
				oldExportReferences
						.add(exportRegistration.getExportReference());
			}

			// ungetting the RSA service will also close the ExportRegistration and therefore
			// emit an event
			ungetService(remoteServiceAdmin);

			Thread.sleep(2000); // give the system the chance to deliver all
								// events.

			if (!exportFailed) {

				for (ExportReference exRef : oldExportReferences) {
					assertThat(exRef.getExportedEndpoint()).as("122.4.1: after closing ExportRegistration, ExportReference.getExportedEndpoint must return null").isNull();
					assertThat(exRef.getExportedService()).as("122.4.1: after closing ExportRegistration, ExportReference.getExportedService must return null").isNull();
				}
				oldExportReferences.clear();


				for (Iterator<ExportRegistration> registrationiterator = exportRegistrations.iterator(); registrationiterator.hasNext();) {
					ExportRegistration exportregistration = registrationiterator.next();

					ExportReference reference = exportregistration.getExportReference();
					assertThat(reference).as("After closing the ExportRegistration, all calls against the ExportRegistration must return null").isNull();
				}

				event = remoteServiceAdminListener.getNextEvent();
				assertThat(event).as("no RemoteServiceAdminEvent received").isNotNull();
				assertThat(event.getSource()).as("122.10.11: source must not be null").isNotNull();
				assertThat(event.getException()).isNull();
				assertThat(event.getType()).as("122.10.11: event type is wrong").isEqualTo(RemoteServiceAdminEvent.EXPORT_UNREGISTRATION);

				exportReference = event.getExportReference();
				assertThat(exportReference).as("ExportReference expected in event").isNotNull();

				assertThat(exportReference.getExportedEndpoint()).isNull();

				assertThat(remoteServiceAdminListener.getEventCount()).isZero();
			}

		} finally {
			registration.unregister();
		}
	}

	/**
	 * 122.4.1 Exporting
	 *
	 * Tests the export of a service with negative edge cases according to
	 * the spec.
	 * Register a service and make sure that an event is sent to a registered event handler
	 */
	@Test
	public void testExportEventNotification() throws Exception {
		// create an register a service
		Hashtable<String, String> dictionary = new Hashtable<String, String>();
		dictionary.put("mykey", "will be overridden");
		dictionary.put("myprop", "myvalue");

		TestService service = new TestService();

		ServiceRegistration< ? > registration = context
				.registerService(new String[] {
						A.class.getName(), B.class.getName()
				},
				                                                        service, dictionary);
		assertThat(registration).isNotNull();

		try {
			//
			// register a EventHandler to receive the export
			// notification
			//
			Hashtable<String, Object> props = new Hashtable<String, Object>();
			props.put(EventConstants.EVENT_TOPIC, new String[]{
					"org/osgi/service/remoteserviceadmin/EXPORT_REGISTRATION",
					"org/osgi/service/remoteserviceadmin/EXPORT_UNREGISTRATION",
					"org/osgi/service/remoteserviceadmin/EXPORT_ERROR"});
			TestEventHandler eventHandler = new TestEventHandler(timeout);

			registerService(EventHandler.class.getName(), eventHandler, props);

			props = new Hashtable<String, Object>();
			props.put(EventConstants.EVENT_TOPIC, new String[]{
					"org/osgi/service/remoteserviceadmin/EXPORT_UNREGISTRATION"});
			TestEventHandler unregistrationEventHandler = new TestEventHandler(
					timeout);

			registerService(EventHandler.class.getName(), unregistrationEventHandler, props);

			//
			// 122.4.1 export the service
			//
			Map<String, Object> properties = loadCTProperties();
			properties.put("mykey", "has been overridden");
			properties.put("objectClass", "can.not.be.changed.Class");
			properties.put("service.id", "can.not.be.changed.Id");
			properties.put(RemoteServiceConstants.SERVICE_EXPORTED_INTERFACES,
					A.class.getName());

			Collection<ExportRegistration> exportRegistrations = remoteServiceAdmin.exportService(registration.getReference(), properties);
			assertThat(exportRegistrations).isNotNull();

			ExportRegistration exportRegistration = exportRegistrations.iterator().next();
			assertThat(exportRegistration).isNotNull();
			ExportReference reference = exportRegistration.getExportReference();
			assertThat(reference).isNotNull();
			EndpointDescription description = reference.getExportedEndpoint();
			assertThat(description).isNotNull();

			//
			// 122.8.1 verify that export notification was sent to EventHandler
			//
			ServiceReference< ? > sref = getServiceReference(
					remoteServiceAdmin);

			Event event = eventHandler.getNextEventForTopic(
					"org/osgi/service/remoteserviceadmin/EXPORT_REGISTRATION",
					"org/osgi/service/remoteserviceadmin/EXPORT_ERROR");
			assertThat(event).as("no Event received").isNotNull();

			assertThat(event.getProperty("bundle")).isEqualTo(sref.getBundle());
			assertThat(event.getProperty("bundle.symbolicname")).isEqualTo(sref.getBundle().getSymbolicName());
			assertThat(event.getProperty("bundle.id")).isEqualTo(sref.getBundle().getBundleId());
			assertThat(event.getProperty("bundle.version")).isEqualTo(sref.getBundle().getVersion());
			assertThat(event.getProperty("timestamp")).isNotNull();
			RemoteServiceAdminEvent rsaevent = (RemoteServiceAdminEvent) event.getProperty("event");
			assertThat(rsaevent).isNotNull();

			boolean exportFailed = false;
			if ("org/osgi/service/remoteserviceadmin/EXPORT_REGISTRATION".equals(event.getTopic())) {
				assertThat(event.getProperty("cause")).isNull();
				assertThat(rsaevent.getType()).isEqualTo(RemoteServiceAdminEvent.EXPORT_REGISTRATION);
			} else if ("org/osgi/service/remoteserviceadmin/EXPORT_ERROR".equals(event.getTopic())) {
				exportFailed = true;
				assertThat(event.getProperty("cause")).isNotNull();
				assertThat(rsaevent.getType()).isEqualTo(RemoteServiceAdminEvent.EXPORT_ERROR);
			}


			// ungetting the RSA service will also close the ExportRegistration and therefore
			// emit an event
			ungetService(remoteServiceAdmin);

			if (!exportFailed) {
				event = unregistrationEventHandler
						.getNextEventForTopic("org/osgi/service/remoteserviceadmin/EXPORT_UNREGISTRATION");
				assertThat(event).as("no EXPORT_UNREGISTRATION event received").isNotNull();
				// David B: you should be getting 2 unregistration events, one for each service?
				// commented out the next line
				// assertEquals(0, unregistrationEventHandler.getEventCount());

				assertThat(event.getTopic()).isEqualTo("org/osgi/service/remoteserviceadmin/EXPORT_UNREGISTRATION");
				assertThat(event.getProperty("bundle")).isEqualTo(sref.getBundle());
				assertThat(event.getProperty("bundle.symbolicname")).isEqualTo(sref.getBundle().getSymbolicName());
				rsaevent = (RemoteServiceAdminEvent) event.getProperty("event");
				assertThat(rsaevent).isNotNull();
				assertThat(rsaevent.getType()).isEqualTo(RemoteServiceAdminEvent.EXPORT_UNREGISTRATION);
				assertThat(event.getProperty("bundle.id")).isEqualTo(sref.getBundle().getBundleId());
				assertThat(event.getProperty("bundle.version")).isEqualTo(sref.getBundle().getVersion());
				assertThat(event.getProperty("timestamp")).isNotNull();
			}
		} finally {
			registration.unregister();
		}
	}

	/**
	 * 122.4.1 Exporting
	 *
	 * Tests the export of a service with negative edge cases according to
	 * the spec.
	 * Register a service multiple times. Each registration is supposed to return
	 * a new ExportRegistration
	 */
	@Test
	public void testExportMultipleRegistrations() throws Exception {
		// create and register a service
		Hashtable<String, String> dictionary = new Hashtable<String, String>();
		dictionary.put("mykey", "will be overridden");
		dictionary.put("myprop", "myvalue");

		TestService service = new TestService();

		ServiceRegistration< ? > registration = context
				.registerService(new String[] {
						A.class.getName(), B.class.getName()
				},
				                                                        service, dictionary);
		assertThat(registration).isNotNull();

		try {
			// lookup RemoteServiceAdmin service
			ServiceReference< ? > rsaRef = context
					.getServiceReference(RemoteServiceAdmin.class.getName());
			assertThat(rsaRef).isNotNull();
			RemoteServiceAdmin rsa = (RemoteServiceAdmin) context.getService(rsaRef);
			assertThat(rsa).isNotNull();

			try {
				//
				// 122.4.1 export the service
				//
				Map<String, Object> properties = loadCTProperties();
				properties.put("mykey", "has been overridden");
				properties.put("objectClass", "can.not.be.changed.Class");
				properties.put("service.id", "can.not.be.changed.Id");
				properties.put(RemoteServiceConstants.SERVICE_EXPORTED_INTERFACES, A.class.getName());

				Collection<ExportRegistration> exportRegistrations = rsa.exportService(registration.getReference(), properties);
				assertThat(exportRegistrations).isNotNull();

				// do it a second time
				Collection<ExportRegistration> exportRegistrations2 = rsa.exportService(registration.getReference(), properties);
				assertThat(exportRegistrations2).isNotNull();

				assertThat(exportRegistrations2).hasSize(exportRegistrations.size());

				// 122.4.1: Exporting
				// all registrations have to point to the same exported service
				// and endpoints are shared among the registrations
				List<EndpointDescription> endpoints = new ArrayList<EndpointDescription>();

				Collection<ExportReference> exportrefs = rsa.getExportedServices();

				for (ExportReference eRef : exportrefs) {
					System.out.println("Exported Endpoint: "
							+ eRef.getExportedEndpoint());
				}
				
				assertThat(exportrefs).isNotNull();
				assertThat(exportrefs).hasSize(exportRegistrations.size() + exportRegistrations2.size());

				for (Iterator<ExportRegistration> it = exportRegistrations.iterator(); it.hasNext();) {
					ExportRegistration er = it.next();

					ExportReference ref = er.getExportReference();
					assertThat(ref).isNotNull();

					assertThat(ref.getExportedService()).isEqualTo(registration.getReference());
					EndpointDescription ed = ref.getExportedEndpoint();
					endpoints.add(ed);
					// 122.2.5: verify EndpointDescription object
					for (Iterator<String> i = ed.getProperties().keySet().iterator(); i.hasNext();) {
						assertThat(i.next()).doesNotStartWith("service.exports.");
					}
					assertThat(ed.getProperties().get("service.imported")).isNotNull();
					assertThat(ed.getFrameworkUUID()).isNotNull();
					assertThat(ed.getId()).isNotNull();
					assertThat(ed.getInterfaces().contains(A.class.getName())).isTrue();
					assertThat(ed.getInterfaces().contains(B.class.getName())).isFalse();
					assertThat(ed.getConfigurationTypes()).isNotNull();
					assertThat(ed.getIntents()).isNotNull();

					// ensure that the registered exported endpoint is the same as the exported reference endpoint from the RSA
					boolean found = false;
					for (Iterator<ExportReference> refit = exportrefs.iterator(); refit.hasNext();) {
						ExportReference exref = refit.next();

						if (ed.equals(exref.getExportedEndpoint())) {
							found = true;
							break;
						}
					}
                    assertThat(found).as("ExportReference does not show up in exported reference list of RSA service").isTrue();
				}

				for (Iterator<ExportRegistration> it = exportRegistrations2.iterator(); it.hasNext();) {
					ExportRegistration er = it.next();

					ExportReference ref = er.getExportReference();
					assertThat(ref).isNotNull();

					assertThat(ref.getExportedService()).isEqualTo(registration.getReference());
					// As of RSA 1.1 endpoints are not required to be shared so
					// we don't test for it anymore
					// assertTrue(endpoints.contains(ref.getExportedEndpoint()));

                    assertThat(exportrefs.contains(ref)).as("ExportReference does not show up in exported reference list of RSA service").isTrue();
				}

				// now close one of the registrations and ensure that service is still
				// exported until all registrations are closed
				for (Iterator<ExportRegistration> it = exportRegistrations2.iterator(); it.hasNext();) {
					ExportRegistration er = it.next();

					ExportReference exref = er.getExportReference();
					er.close();
					try {
						er.close(); // must
					} catch (Exception ex) {
						fail("calling close() multiple times must not fail");
					}

					assertThat(exref.getExportedEndpoint()).as("122.4.1: after closing ExportRegistration, ExportReference.getExportedEndpoint must return null").isNull();
					assertThat(exref.getExportedService()).as("122.4.1: after closing ExportRegistration, ExportReference.getExportedService must return null").isNull();
				}
				assertThat(rsa.getExportedServices()).hasSize(exportRegistrations.size());
				for (Iterator<ExportRegistration> it = exportRegistrations.iterator(); it.hasNext();) {
					ExportRegistration er = it.next();

					ExportReference exref = er.getExportReference();
					er.close();
					try {
						er.close(); // must
					} catch (Exception ex) {
						fail("calling close() multiple times must not fail");
					}

					assertThat(exref.getExportedEndpoint()).as("122.4.1: after closing ExportRegistration, ExportReference.getExportedEndpoint must return null").isNull();
					assertThat(exref.getExportedService()).as("122.4.1: after closing ExportRegistration, ExportReference.getExportedService must return null").isNull();
				}
				assertThat(rsa.getExportedServices()).isNotNull();
				assertThat(rsa.getExportedServices()).isEmpty();
			} finally {
				context.ungetService(rsaRef);
			}
		} finally {
			registration.unregister();
		}
	}

	/**
	 * 122.4.1 Exporting
	 *
	 * Tests the export of a service with negative edge cases according to
	 * the spec.
	 * Register a service. Read the configuration types from the EndpointDescription
	 * and ensure that there is a property present that starts with the name of the
	 * config type
	 */
	@Test
	public void testExportConfigurationType() throws Exception {
		Map<String,Object> properties = loadCTProperties();
		String serverConfigType = (String) properties
				.get("service.exported.configs");
		// read supported configuration types from the DistributionProvider
		ServiceReference< ? >[] dprefs = context.getServiceReferences(
				(String) null, "(remote.configs.supported=*)");
		assertThat(dprefs).isNotNull();
		ServiceReference< ? > dpref = dprefs[0];
		String[] supportedConfigTypes = getConfigTypes(dpref.getProperty("remote.configs.supported"));
		assertThat(supportedConfigTypes.length > 0).isTrue();
		// also check that the desired serverConfigType is present in the RSA
		// provided supportedConfigTypes
		assertThat(Arrays.asList(supportedConfigTypes).contains(serverConfigType)).isTrue();
		System.out.println("DSP supported config types:");
		for (String c : supportedConfigTypes) {
			System.out.println(
					" " + c + (c.equals(serverConfigType) ? " - USED" : ""));
		}
		String[] serviceExportedConfigs = new String[] {
				serverConfigType
		};
		// create and register a service
		Hashtable<String, Object> dictionary = new Hashtable<String, Object>();
		dictionary.put("mykey", "will be overridden");
		dictionary.put("myprop", "myvalue");
		dictionary.put(RemoteConstants.SERVICE_EXPORTED_CONFIGS,
				supportedConfigTypes);

		TestService service = new TestService();

		ServiceRegistration< ? > registration = context
				.registerService(new String[] {
						A.class.getName(), B.class.getName()
				},
				                                                        service, dictionary);
		assertThat(registration).isNotNull();

		try {
			//
			// register a RemoteServiceAdminListener to receive the export
			// notification
			//
			TestRemoteServiceAdminListener remoteServiceAdminListener = new TestRemoteServiceAdminListener();
			registerService(RemoteServiceAdminListener.class.getName(), remoteServiceAdminListener, null);

			//
			// 122.4.1 export the service
			//
			properties.put("mykey", "has been overridden");
			properties.put("objectClass", "can.not.be.changed.Class");
			properties.put("service.id", "can.not.be.changed.Id");
            properties.put(RemoteServiceConstants.SERVICE_EXPORTED_INTERFACES, A.class.getName());
			properties.put(RemoteConstants.SERVICE_EXPORTED_CONFIGS,
					serviceExportedConfigs);

			Collection<ExportRegistration> exportRegistrations = remoteServiceAdmin.exportService(registration.getReference(), properties);
			assertThat(exportRegistrations).isNotNull();
			assertThat(exportRegistrations).isNotEmpty();

			//
			// 122.8 verify that export notification was sent to RemoteServiceAdminListeners
			//
			boolean exportFailed = false;

			Thread.sleep(2000); // give the system the chance to deliver all
								// events.
			RemoteServiceAdminEvent event = remoteServiceAdminListener.getNextEvent();
			assertThat(event).as("no RemoteServiceAdminEvent received").isNotNull();
			assertThat(remoteServiceAdminListener.getEventCount()).isZero();
			assertThat(event.getSource()).as("122.10.11: source must not be null").isNotNull();

			ExportReference exportReference  = null;

			switch (event.getType()) {
			case RemoteServiceAdminEvent.EXPORT_REGISTRATION:
				assertThat(event.getException()).isNull();
				assertThat(event.getType()).as("122.10.11: event type is wrong").isEqualTo(RemoteServiceAdminEvent.EXPORT_REGISTRATION);

				exportReference = event.getExportReference();
				assertThat(exportReference).as("ExportReference expected in event").isNotNull();

				assertThat(exportReference.getExportedEndpoint()).isNotNull();
				break;
			case RemoteServiceAdminEvent.EXPORT_ERROR:
				exportFailed = true;
				assertThat(event.getException()).isNotNull();
                assertThat(event.getExportReference()).as("122.7: the event must contain the ExportReference").isNotNull();
				break;
			default:
				fail("wrong event type");
			}

			List<String> configproperties = new ArrayList<String>();

			// extract configuration type and configuration properties
			if (!exportFailed) {
				EndpointDescription epd = exportReference.getExportedEndpoint();
				List<String> configtypes = epd.getConfigurationTypes();
				assertThat(configtypes).isNotNull();
				for (Iterator<String> it = configtypes.iterator(); it.hasNext();) {
					String configtype = it.next();

					// check for config type properties
					assertThat(configtype).isNotNull();
					// Modified to look for config type through all available configs
					assertThat(Arrays.asList(supportedConfigTypes).contains(configtype)).as("service was exported with different config type than requested").isTrue();

					for (Iterator<String> keys = epd.getProperties().keySet().iterator(); keys.hasNext(); ) {
						String key = keys.next();

						if (key.startsWith(configtype + ".")) {
							configproperties.add(key);
						}
					}
					assertThat(configproperties).as("122.4: config type properties missing from EndpointDescription").isNotEmpty();
				}
			}

			// ungetting the RSA service will also close the ExportRegistration and therefore
			// emit an event
			ungetService(remoteServiceAdmin);

			Thread.sleep(2000); // give the system the chance to deliver all
								// events.

			if (!exportFailed) {
				event = remoteServiceAdminListener.getNextEvent();
				assertThat(event).as("no RemoteServiceAdminEvent received").isNotNull();
				assertThat(event.getSource()).as("122.10.11: source must not be null").isNotNull();
				assertThat(event.getException()).isNull();
				assertThat(event.getType()).as("122.10.11: event type is wrong").isEqualTo(RemoteServiceAdminEvent.EXPORT_UNREGISTRATION);

				exportReference = event.getExportReference();
				assertThat(exportReference).as("ExportReference expected in event").isNotNull();

				assertThat(exportReference.getExportedEndpoint()).isNull();
				assertThat(remoteServiceAdminListener.getEventCount()).isZero();
			}
		} finally {
			registration.unregister();
		}
	}

	/**
	 * 122.4.1 Exporting
	 *
	 * Tests the export of a service with negative edge cases according to
	 * the spec.
	 * Register a service. Read the configuration types from the EndpointDescription
	 * and ensure that there is a property present that starts with the name of the
	 * config type. Then modify the configuration properties and try to export the
	 * service again. It should fail and generate events.
	 */
	@Test
	public void testForceExportFailure() throws Exception {
		Map<String,Object> properties = loadCTProperties();
		String serverConfigType = (String) properties
				.get("service.exported.configs");
		// read supported configuration types from the DistributionProvider
		ServiceReference< ? >[] dprefs = context.getServiceReferences(
				(String) null, "(remote.configs.supported=*)");
		assertThat(dprefs).isNotNull();
		ServiceReference< ? > dpref = dprefs[0];
		String[] supportedConfigTypes = getConfigTypes(dpref.getProperty("remote.configs.supported"));
		assertThat(supportedConfigTypes.length > 0).isTrue();
		// also check that the desired serverConfigType is present in the RSA
		// provided supportedConfigTypes
		assertThat(Arrays.asList(supportedConfigTypes).contains(serverConfigType)).isTrue();
		System.out.println("DSP supported config types:");
		for (String c : supportedConfigTypes) {
			System.out.println(
					" " + c + (c.equals(serverConfigType) ? " - USED" : ""));
		}
		String[] serviceExportedConfigs = new String[] {
				serverConfigType
		};
		// create and register a service
		Hashtable<String, Object> dictionary = new Hashtable<String, Object>();
		dictionary.put("mykey", "will be overridden");
		dictionary.put("myprop", "myvalue");
		dictionary.put(RemoteConstants.SERVICE_EXPORTED_CONFIGS,
				supportedConfigTypes);

		TestService service = new TestService();

		ServiceRegistration< ? > registration = context
				.registerService(new String[] {
						A.class.getName(), B.class.getName()
				},
				                                                        service, dictionary);
		assertThat(registration).isNotNull();

		try {
			//
			// register a RemoteServiceAdminListener to receive the export
			// notification
			//
			TestRemoteServiceAdminListener remoteServiceAdminListener = new TestRemoteServiceAdminListener();
			registerService(RemoteServiceAdminListener.class.getName(), remoteServiceAdminListener, null);

			//
			// 122.4.1 export the service
			//
			properties.put("mykey", "has been overridden");
			properties.put("objectClass", "can.not.be.changed.Class");
			properties.put("service.id", "can.not.be.changed.Id");
            properties.put(RemoteServiceConstants.SERVICE_EXPORTED_INTERFACES, A.class.getName());
			properties.put(RemoteConstants.SERVICE_EXPORTED_CONFIGS,
					serviceExportedConfigs);

			Collection<ExportRegistration> exportRegistrations = remoteServiceAdmin.exportService(registration.getReference(), properties);
			assertThat(exportRegistrations).isNotNull();
			assertThat(exportRegistrations).as("only one export registration was expected since only one config type was requested").hasSize(1);

			//
			// 122.8 verify that export notification was sent to RemoteServiceAdminListeners
			//
			Thread.sleep(2000); // give the system a chance to deliver all events
			RemoteServiceAdminEvent event = remoteServiceAdminListener.getNextEvent();
			assertThat(event).as("no RemoteServiceAdminEvent received").isNotNull();
			assertThat(remoteServiceAdminListener.getEventCount()).isZero();
			assertThat(event.getSource()).as("122.10.11: source must not be null").isNotNull();

			ExportReference exportReference  = null;

			switch (event.getType()) {
			case RemoteServiceAdminEvent.EXPORT_REGISTRATION:
				assertThat(event.getException()).isNull();
				assertThat(event.getType()).as("122.10.11: event type is wrong").isEqualTo(RemoteServiceAdminEvent.EXPORT_REGISTRATION);

				exportReference = event.getExportReference();
				assertThat(exportReference).as("ExportReference expected in event").isNotNull();

				assertThat(exportReference.getExportedEndpoint()).isNotNull();
				break;
			case RemoteServiceAdminEvent.EXPORT_ERROR:
                assertThat(event.getException()).isNotNull();
                assertThat(event.getExportReference()).as("122.7: the event must have the ExportReference").isNotNull();
				return;
			default:
				fail("wrong event type");
			}


			// extract configuration type and configuration properties
			ArrayList<String> configproperties = new ArrayList<String>();

			EndpointDescription epd = exportReference.getExportedEndpoint();
			List<String> configtypes = epd.getConfigurationTypes();
			assertThat(configtypes).isNotNull();
			for (Iterator<String> it = configtypes.iterator(); it.hasNext();) {
				String configtype = it.next();

				// check for config type properties
				assertThat(configtype).isNotNull();
				assertThat(Arrays.asList(supportedConfigTypes).contains(configtype)).as("service was exported with different config type than requested").isTrue();

				for (Iterator<String> keys = epd.getProperties().keySet().iterator(); keys.hasNext(); ) {
					String key = keys.next();

					if (key.startsWith(configtype + ".")) {
						configproperties.add(key);
					}
				}
				assertThat(configproperties).as("122.4: config type properties missing from EndpointDescription").isNotEmpty();
			}

			// unexport the service
			exportRegistrations.iterator().next().close();

			Thread.sleep(2000); // give the system the chance to deliver all
								// events.
			event = remoteServiceAdminListener.getNextEvent();
			assertThat(event).as("no RemoteServiceAdminEvent received").isNotNull();
			assertThat(event.getSource()).as("122.10.11: source must not be null").isNotNull();
			assertThat(event.getException()).isNull();
			assertThat(event.getType()).as("122.10.11: event type is wrong").isEqualTo(RemoteServiceAdminEvent.EXPORT_UNREGISTRATION);

			exportReference = event.getExportReference();
			assertThat(exportReference).as("ExportReference expected in event").isNotNull();

			assertThat(exportReference.getExportedEndpoint()).isNull();
			assertThat(remoteServiceAdminListener.getEventCount()).isZero();

			// register the service again
			dictionary.remove(RemoteConstants.SERVICE_EXPORTED_CONFIGS);
			registration = context.registerService(new String[]{A.class.getName(), B.class.getName()},
					service, dictionary);

			// manipulate the config property
			// by putting garbage in them
			for (Iterator<String> it = configproperties.iterator(); it.hasNext();) {
				properties.put(it.next(), new TestService());
			}

			// export the service again
			// https://www.osgi.org/members/bugzilla/show_bug.cgi?id=2591
			try {
				exportRegistrations = remoteServiceAdmin.exportService(
						registration.getReference(), properties);
				fail("Expected an illegal argumnet exception for garbage input");
			} catch (IllegalArgumentException e) {
				// as expected

			}

			// ungetting the RSA service will also close the ExportRegistration and therefore
			// emit an event
			ungetService(remoteServiceAdmin);
		} finally {
			try {
                registration.unregister();
            } catch (Exception e) {
                // We're just cleaning up here, so ignore the exception, if any
            }
		}
	}

	/**
	 * 122.4.1 Exporting
	 *
	 * Tests the export of a service with negative edge cases according to
	 * the spec.
	 */
	@Test
	public void testExportConfigOverride() throws Exception {
		Map<String,Object> properties = loadCTProperties();
		String serverConfigType = (String) properties
				.get("service.exported.configs");
		// read supported configuration types from the DistributionProvider
		ServiceReference< ? >[] dprefs = context.getServiceReferences(
				(String) null, "(remote.configs.supported=*)");
		assertThat(dprefs).isNotNull();
		ServiceReference< ? > dpref = dprefs[0];
		String[] supportedConfigTypes = getConfigTypes(dpref.getProperty("remote.configs.supported"));
		assertThat(supportedConfigTypes.length > 0).isTrue();
		// also check that the desired serverConfigType is present in the RSA
		// provided supportedConfigTypes
		assertThat(Arrays.asList(supportedConfigTypes).contains(serverConfigType)).isTrue();
		System.out.println("DSP supported config types:");
		for (String c : supportedConfigTypes) {
			System.out.println(
					" " + c + (c.equals(serverConfigType) ? " - USED" : ""));
		}
		String[] serviceExportedConfigs = new String[] {
				serverConfigType
		};

		// create and register a service
		Hashtable<String, Object> dictionary = new Hashtable<String, Object>();
		dictionary.put("mykey", "will be overridden");
		dictionary.put("myprop", "myvalue");
		dictionary.put(RemoteServiceConstants.SERVICE_EXPORTED_INTERFACES, B.class.getName());
		dictionary.put(RemoteConstants.SERVICE_EXPORTED_CONFIGS,
				supportedConfigTypes);

		TestService service = new TestService();

		ServiceRegistration< ? > registration = context
				.registerService(new String[] {
						A.class.getName(), B.class.getName()
				},
				                                                        service, dictionary);
		assertThat(registration).isNotNull();

		try {
			properties.put("MyKey", "has been overridden"); // 122.10.10.1: change the case of the key
			properties.put("objectClass", "can.not.be.changed.Class");
			properties.put("service.id", "can.not.be.changed.Id");
			properties.put(RemoteConstants.SERVICE_EXPORTED_CONFIGS,
					serviceExportedConfigs);
			properties.put(RemoteServiceConstants.SERVICE_EXPORTED_INTERFACES, A.class.getName());

			Collection<ExportRegistration> exportRegistrations = remoteServiceAdmin.exportService(registration.getReference(), properties);
			assertThat(exportRegistrations).isNotNull();
			assertThat(exportRegistrations).isNotEmpty();

			ExportRegistration exportreg = exportRegistrations.iterator().next();
			assertThat(exportreg.getException()).isNull();
			ExportReference exportref = exportreg.getExportReference();
			assertThat(exportref).isNotNull();
			ServiceReference< ? > serviceref = exportref.getExportedService();
			assertThat(serviceref).isNotNull();
			EndpointDescription endpointDescription = exportref.getExportedEndpoint();
			assertThat(endpointDescription).isNotNull();

			// this will throw an exception if the EndpointDescription information is invalid or incomplete
			new EndpointDescription(serviceref, endpointDescription.getProperties());

			assertThat(endpointDescription.getInterfaces().contains(B.class.getName())).isFalse();
			assertThat(endpointDescription.getProperties().get("mykey")).isEqualTo("has been overridden");
		} finally {
			registration.unregister();
		}
	}

	/**
	 * 122.4.1 Exporting
	 *
	 * Tests the export of a service with negative edge cases according to
	 * the spec.
	 */
	@Test
	public void testExportEmptyConfig() throws Exception {
		// read supported configuration types from the DistributionProvider
		ServiceReference< ? >[] dprefs = context.getServiceReferences(
				(String) null, "(remote.configs.supported=*)");
		assertThat(dprefs).isNotNull();
		ServiceReference< ? > dpref = dprefs[0];
		String[] supportedConfigTypes = getConfigTypes(dpref.getProperty("remote.configs.supported"));
		assertThat(supportedConfigTypes.length).isGreaterThan(0);
		System.out.println("DSP supported config types:");
		for (String c : supportedConfigTypes) {
			System.out.println(" " + c);
		}

		// create and register a service
		Hashtable<String, Object> dictionary = new Hashtable<String, Object>();
		dictionary.put("mykey", "will be overridden");
		dictionary.put("myprop", "myvalue");
		dictionary.put(RemoteServiceConstants.SERVICE_EXPORTED_INTERFACES, A.class.getName());

		TestService service = new TestService();

		ServiceRegistration< ? > registration = context
				.registerService(new String[] {
						A.class.getName(), B.class.getName()
				},
				                                                        service, dictionary);
		assertThat(registration).isNotNull();

		try {
			Collection<ExportRegistration> exportRegistrations = remoteServiceAdmin.exportService(registration.getReference(), null);
			assertThat(exportRegistrations).isNotNull();
			assertThat(exportRegistrations).isNotEmpty();

			ExportRegistration exportreg = exportRegistrations.iterator().next();
			assertThat(exportreg.getException()).isNull();
			ExportReference exportref = exportreg.getExportReference();
			assertThat(exportref).isNotNull();
			ServiceReference< ? > serviceref = exportref.getExportedService();
			assertThat(serviceref).isNotNull();
			EndpointDescription endpointDescription = exportref.getExportedEndpoint();
			assertThat(endpointDescription).isNotNull();

			// this will throw an exception if the EndpointDescription information is invalid or incomplete
			new EndpointDescription(serviceref, endpointDescription.getProperties());

			assertThat(endpointDescription.getInterfaces().contains(A.class.getName())).isTrue();
		} finally {
			registration.unregister();
		}
	}

	/**
	 * @param property
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private String[] getConfigTypes(Object property) {
		if (property instanceof String) {
			return new String[]{(String) property};
		} else if (property instanceof String[]) {
			return (String[]) property;
		} else if (property instanceof Collection<?>) {
			return ((Collection<String>)property).toArray(new String[]{});
		}
		return new String[]{};
	}

    /**
     * Substitute the free port placeholder for a free port
     *
     * @param properties
     */
    private void processFreePortProperties(Map<String, Object> properties) {
        String freePort = getFreePort();
		for (Iterator<Entry<String,Object>> it = properties.entrySet()
				.iterator(); it.hasNext();) {
			Entry<String,Object> entry = it.next();
            if (entry.getValue().toString().trim().equals(FREE_PORT)) {
                entry.setValue(freePort);
            }
        }
    }

    /**
     * @return a free socket port
     */
    private String getFreePort() {
        try {
            ServerSocket ss = new ServerSocket(0);
            String port = "" + ss.getLocalPort();

            System.out.println("Found free port " + port);
            ss.close();

            return port;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Load the properties from the bnd.bnd file and substitute the free port placeholder.
     *
	 * @return map of properties set in the "org.osgi.test.cases.remoteserviceadmin.serverconfig" property
	 *         in the runoptions in bnd.bnd
	 */
	private Map<String, Object> loadCTProperties() {
		String serverconfig = getProperty("org.osgi.test.cases.remoteserviceadmin.serverconfig");
		assertThat(serverconfig).as("did not find org.osgi.test.cases.remoteserviceadmin.serverconfig system property").isNotNull();
		Map<String, Object> properties = new HashMap<String, Object>();

		for (StringTokenizer tok = new StringTokenizer(serverconfig, ","); tok
				.hasMoreTokens();) {
			String propertyName = tok.nextToken();
			String value = getProperty(propertyName);
			assertThat(value).as("system property not found: " + propertyName).isNotNull();
			properties.put(propertyName, value);
		}

		processFreePortProperties(properties);

		return properties;
	}

	class TestService implements A, B, Serializable {
		/**
		 *
		 */
		private static final long serialVersionUID = 1L;

		/**
		 * @see org.osgi.test.cases.remoteserviceadmin.common.A#getA()
		 */
		@Override
		public String getA() {
			return "this is A";
		}

		/**
		 * @see org.osgi.test.cases.remoteserviceadmin.common.B#getB()
		 */
		@Override
		public String getB() {
			return "this is B";
		}

	}

	/**
	 * RemoteServiceAdminListener implementation, which collects and
	 * returns the received events in order.
	 *
	 * @author <a href="mailto:tdiekman@tibco.com">Tim Diekmann</a>
	 *
	 */
	class TestRemoteServiceAdminListener implements RemoteServiceAdminListener {
		private final LinkedList<RemoteServiceAdminEvent> eventlist = new LinkedList<RemoteServiceAdminEvent>();
		private final Semaphore sem = new Semaphore(0);

		@Override
		public void remoteAdminEvent(final RemoteServiceAdminEvent event) {
			eventlist.add(event);
			sem.release();
		}

		RemoteServiceAdminEvent getNextEvent() {
			try {
				sem.tryAcquire(timeout, TimeUnit.MILLISECONDS);
			} catch (InterruptedException e1) {
				return null;
			}

			try {
				return eventlist.removeFirst();
			} catch (NoSuchElementException e) {
				return null;
			}
		}

		int getEventCount() {
			return eventlist.size();
		}
	}

}
