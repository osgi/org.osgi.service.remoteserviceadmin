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
import static org.assertj.core.api.Assertions.fail;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.ServiceReference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.ImportReference;
import org.osgi.service.remoteserviceadmin.ImportRegistration;
import org.osgi.service.remoteserviceadmin.RemoteConstants;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdmin;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdminEvent;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdminListener;
import org.osgi.test.cases.remoteserviceadmin.common.A;
import org.osgi.test.cases.remoteserviceadmin.common.B;
import org.osgi.test.cases.remoteserviceadmin.common.TestEventHandler;
import org.osgi.test.cases.remoteserviceadmin.common.Utils;
import org.osgi.test.common.annotation.InjectBundleInstaller;
import org.osgi.test.common.install.BundleInstaller;

/**
 * Use RSA service to register a service in a child framework and then import
 * the same service on the parent framework. This test does not explicitly use
 * the discovery, but manually conveys the endpoint information.
 * 
 * @author <a href="mailto:tdiekman@tibco.com">Tim Diekmann</a>
 * @version 1.0.0
 */
public class RemoteServiceAdminTest extends MultiFrameworkTestCase {
	private static final String	SYSTEM_PACKAGES_EXTRA	= "org.osgi.test.cases.remoteserviceadmin.system.packages.extra";

	long						timeout;

	@InjectBundleInstaller
	BundleInstaller				installer;
	
	/**
	 * @see org.osgi.test.cases.remoteserviceadmin.junit.MultiFrameworkTestCase#setUp()
	 */
	@BeforeEach
	public void setUp() throws Exception {
		super.setUp();
		timeout = getLongProperty("rsa.tck.timeout", 300000L);
	}
	
	/**
	 * @see org.osgi.test.cases.remoteserviceadmin.junit.MultiFrameworkTestCase#getConfiguration()
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
		return configuration;
	}

	/**
	 * Sets up a child framework in which a service is exported. In the parent framework the
	 * EndpointDescription is passed to the RSA service to import the service from the child
	 * framework. This manual step bypasses Discovery, which would normally do the transport
	 * between the two frameworks.
	 */
	@Test
	public void testExportImportManually() throws Exception {
		verifyFramework();
		
		//
		// install test bundle in child framework
		//
		BundleContext childContext = getFramework().getBundleContext();
		
		Bundle tb2Bundle = installBundle(childContext, "/tb2.jar");
		assertThat(tb2Bundle).isNotNull();
		
		// start test bundle in child framework
		// the bundle registers a service with interface A and then uses the RemoteServiceAdmin service
		// to export the service. It registers a RemoteServiceAdminListener to receive confirmation
		// of the export of the service.
		// This method will throw an exception if any of the tests fail.
		// The bundle also writes the EndpointDescription of the exported service into a system
		// property that is then read in test to reconstruct the endpoint, this bypasses Discovery
		// for the cases in which the implementation to test does not support the optional Discovery.
		tb2Bundle.start();

		//
		// find the RSA in the parent framework and import the
		// service
		//
		ServiceReference<RemoteServiceAdmin> rsaRef = context
				.getServiceReference(RemoteServiceAdmin.class);
		assertThat(rsaRef).isNotNull();
		RemoteServiceAdmin rsa = context.getService(rsaRef);
		assertThat(rsa).isNotNull();

		try {
			// reconstruct the endpoint description in version 1.0.0
			EndpointDescription endpoint = Utils.reconstructEndpoint("1.0.0",
					context);
			// gather all the service and exporting intents
			List<String> endpointIntents = endpoint.getIntents();
			assertThat(endpointIntents).isNotNull();
			assertThat(endpointIntents).isNotEmpty();

			//
			// 122.4.2: Importing
			// test an unsupported config type
			//
			Map<String, Object> map = new HashMap<String, Object>(endpoint.getProperties());
			map.put(RemoteConstants.SERVICE_IMPORTED_CONFIGS, "guaranteed_unsupported_" + System.currentTimeMillis());
			EndpointDescription clone = new EndpointDescription(map);
			assertThat(rsa.importService(clone)).as("122.4.2: with no supported config type no service may be imported").isNull();

			//
			// 122.4.8: Events
			// add a RemoteServiceAdminLister and an EventHandler and check for import events

			//
			// register a RemoteServiceAdminListener to receive the import
			// notification
			//
			TestRemoteServiceAdminListener remoteServiceAdminListener = new TestRemoteServiceAdminListener();
			registerService(RemoteServiceAdminListener.class.getName(), remoteServiceAdminListener, null);

			//
			// register a EventHandler to receive the import
			// notification
			//
			Hashtable<String, Object> props = new Hashtable<String, Object>();
			props.put(EventConstants.EVENT_TOPIC, new String[]{
					"org/osgi/service/remoteserviceadmin/IMPORT_REGISTRATION",
					"org/osgi/service/remoteserviceadmin/IMPORT_UNREGISTRATION",
			"org/osgi/service/remoteserviceadmin/IMPORT_ERROR"});
			TestEventHandler eventHandler = new TestEventHandler(timeout);
			registerService(EventHandler.class.getName(), eventHandler, props);

			//
			// 122.4.2: Importing
			// positive test: import the service
			//
			ImportRegistration importReg = rsa.importService(endpoint);
			assertThat(importReg).isNotNull();
			assertThat(importReg.getException()).isNull();

			ImportReference importRef = importReg.getImportReference();
			assertThat(importRef).isNotNull();
			
			ServiceReference< ? > sref = importRef.getImportedService();
			assertThat(sref).isNotNull();
			assertThat(sref.getProperty("mykey")).isEqualTo("has been overridden");
			assertThat(sref.getProperty("myprop")).isEqualTo("myvalue");
			assertThat(((List<?>) sref.getProperty("mylist")).size()).isEqualTo(2);
			assertThat(((Set<?>) sref.getProperty("myset")).size()).isEqualTo(2);
			assertThat(sref.getProperty("myfloat")).isEqualTo(3.1415f);
			assertThat(sref.getProperty("mydouble")).isEqualTo(-3.1415d);
			assertThat(sref.getProperty("mychar")).isEqualTo('t');
			assertThat(sref.getProperty(".do_not_forward")).isNull();
			assertThat(sref.getProperty("myxml")).isEqualTo("<myxml>test</myxml>");

			assertThat(sref.getProperty(RemoteConstants.SERVICE_IMPORTED)).as("122.4.2: the service.imported property has to be set").isNotNull();
			assertThat(sref.getProperty(RemoteConstants.SERVICE_IMPORTED_CONFIGS)).isNotNull();
			List<String> intents = getPropertyAsList(sref.getProperty(RemoteConstants.SERVICE_INTENTS));
			assertThat(intents).as("122.4.2: service reference has to have service.intents property set").isNotNull();
			assertThat(intents.containsAll(endpointIntents)).as("122.4.2: imported service.intents has to include all service.intents of exported service").isTrue();

			// validate EndpointDescription
			EndpointDescription description = importRef.getImportedEndpoint();
			assertThat(description).isNotNull();
			assertThat(description.getProperties().get("mykey")).isEqualTo("has been overridden");
			try {
				new EndpointDescription(sref, description.getProperties()); // this throws an exception if
			} catch (IllegalArgumentException ie) {
				fail("invalid endpoint description returned from imported service");
			}
			assertThat(description.getProperties().get(RemoteConstants.SERVICE_IMPORTED)).isNotNull();
			assertThat(description.getInterfaces().size() == 1).isTrue();
			assertThat(description.getInterfaces().contains(A.class.getName())).isTrue();

			// check for event notifications
			//
			// 122.8 verify that import notification was sent to RemoteServiceAdminListeners
			//
			RemoteServiceAdminEvent rsaevent = remoteServiceAdminListener.getNextEvent();
			assertThat(remoteServiceAdminListener.getEventCount()).isZero();
			assertThat(rsaevent).as("no RemoteServiceAdminEvent received").isNotNull();
			assertThat(rsaevent.getSource()).as("122.10.11: source must not be null").isNotNull();

			ImportReference importReference = null;
			EndpointDescription importedED = null;

			//
			// check received event type whether import was successful or not
			assertThat(rsaevent.getType()).isEqualTo(RemoteServiceAdminEvent.IMPORT_REGISTRATION);
			assertThat(rsaevent.getException()).isNull();
			importReference = rsaevent.getImportReference();
			assertThat(importReference).as("ImportReference expected in event").isNotNull();
			importedED = importReference.getImportedEndpoint();
			assertThat(importedED).isNotNull();
			// compare endpoints
			assertThat(endpoint.isSameService(importedED)).isTrue();


			//
			// 122.8.1 verify that import notification was sent to EventHandler
			//
			Event event = eventHandler
					.getNextEventForTopic("org/osgi/service/remoteserviceadmin/IMPORT_REGISTRATION");
			assertThat(event).as("no Event received").isNotNull();
			assertThat(eventHandler.getEventCount()).isZero();

            assertThat(event.getProperty("bundle")).as("122.7: bundle has to refer to RSA bundle").isEqualTo(rsaRef.getBundle());
            assertThat(event.getProperty("bundle.symbolicname")).as("122.7: bundle.symbolicname has to refer to RSA bundle SymbolicName").isEqualTo(rsaRef.getBundle().getSymbolicName());
            assertThat(event.getProperty("bundle.id")).as("122.7: bundle.id has to refer to RSA bundle id").isEqualTo(rsaRef.getBundle().getBundleId());
            assertThat(event.getProperty("bundle.version")).as("122.7: bundle.version has to refer to RSA bundle version").isEqualTo(rsaRef.getBundle().getVersion());
            rsaevent = (RemoteServiceAdminEvent) event.getProperty("event");
            assertThat(rsaevent).isNotNull();
			assertThat(event.getProperty("timestamp")).isNotNull();

			// check event type
			String topic = event.getTopic();
			assertThat(topic).isEqualTo("org/osgi/service/remoteserviceadmin/IMPORT_REGISTRATION");
			assertThat(event.getProperty("cause")).as("cause in event").isNull();
			assertThat(rsaevent.getType()).isEqualTo(RemoteServiceAdminEvent.IMPORT_REGISTRATION);

			//
			// 122.4.2: Importing
			// import the EndpointDescription a second time
			// ensure that it is pointing to the same EndpointDescrition/proxy combination
			// but returning a new ImportRegistration instance
			ImportRegistration importReg2 = rsa.importService(endpoint);
			assertThat(importReg2).isNotNull();
			assertThat(importReg2.getException()).isNull();
			assertThat(importReg2).as("122.4.2: RSA has to create a new ImportRegistration on importService").isNotSameAs(importReg);

			// check for events on second import
			rsaevent = remoteServiceAdminListener.getNextEvent();
			assertThat(rsaevent).as("no RemoteServiceAdminEvent received").isNotNull();
			assertThat(remoteServiceAdminListener.getEventCount()).isZero();
			assertThat(rsaevent.getSource()).as("122.10.11: source must not be null").isNotNull();

			assertThat(rsaevent.getType()).isEqualTo(RemoteServiceAdminEvent.IMPORT_REGISTRATION);
			assertThat(rsaevent.getException()).isNull();
			ImportReference importRef2 = importReg.getImportReference();
			assertThat(importRef2).isNotNull();
			ServiceReference< ? > sref2 = importRef.getImportedService();
			assertThat(sref2).isNotNull();
			// Bug 2642, It is not required anymore that proxy services are
			// shared among multiple imports
			// assertSame("122.4.2: ImportRegistration has to point to the same proxy service",
			// sref, sref2);

			event = eventHandler
					.getNextEventForTopic("org/osgi/service/remoteserviceadmin/IMPORT_REGISTRATION");
			assertThat(event).as("no Event received").isNotNull();
			assertThat(eventHandler.getEventCount()).isZero();
			topic = event.getTopic();
			assertThat(topic).isEqualTo("org/osgi/service/remoteserviceadmin/IMPORT_REGISTRATION");
			assertThat(event.getProperty("cause")).as("cause in event").isNull();

			// invoke service
			A serviceA = null;

			//
			// invoke the service
			//
			// no version
			serviceA = getService(A.class);
			try {
				assertThat(serviceA).isNotNull();
				assertThat(serviceA.getA()).isEqualTo("this is A");
			} finally {
				ungetService(serviceA);
			}

			// not available version
			Filter filter = context.createFilter("(version=3.0.0)");
			ServiceReference< ? >[] refs = context
					.getServiceReferences(A.class.getName(), filter.toString());
			try {
				assertThat(refs).isNull();
			} finally {
				if (refs != null) {
					for (int i = 0; i < refs.length; i++) {
						context.ungetService(refs[i]);
					}
				}
			}

			//
			// 122.4.2: Importing
			// there are 2 ImportRegistrations now open. Ensure that the proxy
			// is still available after closing only one of them
			importReg2.close();
			importReg2.close(); // calling this multiple times must not cause a problem

			serviceA = getService(A.class);
			try {
				assertThat(serviceA).isNotNull();
				assertThat(serviceA.getA()).isEqualTo("this is A");
			} finally {
				ungetService(serviceA);
			}

			//
			// 122.4.8 on close of the ImportRegistration expect another event
			//
			rsaevent = remoteServiceAdminListener.getNextEvent();
			assertThat(rsaevent).as("no RemoteServiceAdminEvent received").isNotNull();
			assertThat(rsaevent.getSource()).as("122.10.11: source must not be null").isNotNull();
			assertThat(rsaevent.getException()).isNull();
			assertThat(rsaevent.getType()).as("122.10.11: event type is wrong").isEqualTo(RemoteServiceAdminEvent.IMPORT_UNREGISTRATION);

			importReference = rsaevent.getImportReference();
			assertThat(importReference).as("ImportReference expected in event").isNotNull();

			assertThat(importReference.getImportedEndpoint()).isNull();

			event = eventHandler
					.getNextEventForTopic("org/osgi/service/remoteserviceadmin/IMPORT_UNREGISTRATION");
			assertThat(event).as("no Event received").isNotNull();
			assertThat(eventHandler.getEventCount()).isZero();
			topic = event.getTopic();
			assertThat(topic).as("122.4.8: wrong event topic").isEqualTo("org/osgi/service/remoteserviceadmin/IMPORT_UNREGISTRATION");
			assertThat(event.getProperty("cause")).as("cause in event").isNull();

			//
			// now close the last one, this should remove the proxy service
			importReg.close();
			importReg.close(); // calling this multiple times must not cause a problem

			serviceA = (A) context.getService(sref);
			assertThat(serviceA).as("122.4.2: the last ImportRegistration has been closed, but proxy is still available").isNull();
			assertThat(rsa.getImportedEndpoints().contains(importRef)).as("122.4.2: the last ImportRegistration has been closed, but RSA still lists it").isFalse();

			//
			// 122.4.8 on close of the ImportRegistration expect another event
			//
			rsaevent = remoteServiceAdminListener.getNextEvent();
			assertThat(rsaevent).as("no RemoteServiceAdminEvent received").isNotNull();
			assertThat(rsaevent.getSource()).as("122.10.11: source must not be null").isNotNull();
			assertThat(rsaevent.getException()).isNull();
			assertThat(rsaevent.getType()).as("122.10.11: event type is wrong").isEqualTo(RemoteServiceAdminEvent.IMPORT_UNREGISTRATION);

			importReference = rsaevent.getImportReference();
			assertThat(importReference).as("ImportReference expected in event").isNotNull();

			assertThat(importReference.getImportedEndpoint()).isNull();

			event = eventHandler
					.getNextEventForTopic("org/osgi/service/remoteserviceadmin/IMPORT_UNREGISTRATION");
			assertThat(event).as("no Event received").isNotNull();
			assertThat(eventHandler.getEventCount()).isZero();
			topic = event.getTopic();
			assertThat(topic).as("122.4.8: wrong event topic").isEqualTo("org/osgi/service/remoteserviceadmin/IMPORT_UNREGISTRATION");
			assertThat(event.getProperty("cause")).as("cause in event").isNull();

		} finally {
			// Make sure the service instance of the RSA can be closed by the RSA Service Factory
			context.ungetService(rsaRef);
		}
	}

	/**
	 * Sets up a child framework in which a service is exported. In the parent framework the
	 * EndpointDescription is passed to the RSA service to import the service from the child
	 * framework. This manual step bypasses Discovery, which would normally do the transport
	 * between the two frameworks.
	 * 
	 * This test is similar to testExportImportManually, but exports the same interface in
	 * multiple versions and imports them in multiple versions.
	 */
	@Test
	public void testExportImportManuallyMultipleVersions() throws Exception {
		verifyFramework();
		
		//
		// install test bundle in child framework
		//
		BundleContext childContext = getFramework().getBundleContext();
		
		Bundle tb2Bundle = installBundle(childContext, "/tb2.jar");
		assertThat(tb2Bundle).isNotNull();
		
		// start test bundle in child framework
		// the bundle registers a service with interface A and then uses the RemoteServiceAdmin service
		// to export the service. It registers a RemoteServiceAdminListener to receive confirmation
		// of the export of the service.
		// This method will throw an exception if any of the tests fail.
		// The bundle also writes the EndpointDescription of the exported service into a system
		// property that is then read in test to reconstruct the endpoint, this bypasses Discovery
		// for the cases in which the implementation to test does not support the optional Discovery.
		tb2Bundle.start();

		// install another version of tb2, but this time it exports the
		// interface in version 2.0.0
		Bundle tb4Bundle = installBundle(childContext, "/tb4.jar");
		assertThat(tb4Bundle).isNotNull();
		tb4Bundle.start();
		
		// install a consumer bundle that tries to use service A in version 1.0.0
		Bundle tb5Bundle = installer.installBundle("/tb5.jar", false);
		assertThat(tb5Bundle).isNotNull();
		
		// and another one that tries to use service A in version 2.0.0
		Bundle tb6Bundle = installer.installBundle("/tb6.jar", false);
		assertThat(tb6Bundle).isNotNull();
		
		//
		// find the RSA in the parent framework and import the
		// service
		//
		ServiceReference<RemoteServiceAdmin> rsaRef = context
				.getServiceReference(RemoteServiceAdmin.class);
		assertThat(rsaRef).isNotNull();
		RemoteServiceAdmin rsa = context.getService(rsaRef);
		assertThat(rsa).isNotNull();

		try {
			// reconstruct the endpoint description in version 1.0.0
			EndpointDescription endpoint = Utils.reconstructEndpoint("1.0.0",
					context);
			assertThat(endpoint).isNotNull();

			// import version 1.0.0
			ImportRegistration importReg1 = rsa.importService(endpoint);
			assertThat(importReg1).isNotNull();
			assertThat(importReg1.getException()).isNull();

			// now import version 2.0.0
			endpoint = Utils.reconstructEndpoint("2.0.0", context);
			assertThat(endpoint).isNotNull();

			ImportRegistration importReg2 = rsa.importService(endpoint);
			assertThat(importReg2).isNotNull();
			assertThat(importReg2.getException()).isNull();

			// start consumer bundles, will throw exceptions if not working
			tb5Bundle.start();
			tb6Bundle.start();
			
			// closing the import registrations is supposed to remove the services
			importReg1.close();
			importReg2.close();

			tb5Bundle.stop();
			tb6Bundle.stop();
		} finally {
			// Make sure the service instance of the RSA can be closed by the RSA Service Factory
			context.ungetService(rsaRef);
		}
	}

	/**
	 * @param property Object
	 * @return List<String> of content of the property
	 */
	@SuppressWarnings("unchecked")
	private List<String> getPropertyAsList(Object property) throws Exception {
		if (property instanceof List) {
			return (List<String>) property;
		} else if (property instanceof Collection) {
			return new ArrayList<String>((Collection<String>) property);
		} else if (property instanceof String[]) {
			return Arrays.asList((String[])property);
		} else if (property instanceof String) {
			ArrayList<String> list = new ArrayList<String>();
			StringTokenizer st = new StringTokenizer((String) property, " ,");
			while (st.hasMoreTokens()) {
				list.add(st.nextToken());
			}
			return list;
		} else {
			throw new Exception("unsupported property type");
		}
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
	 * RemoteServiceAdminListener implementation, which collects and returns the
	 * received events in order.
	 * 
	 * @author <a href="mailto:tdiekman@tibco.com">Tim Diekmann</a>
	 * 
	 */
	class TestRemoteServiceAdminListener implements RemoteServiceAdminListener {
		private final LinkedList<RemoteServiceAdminEvent> eventlist = new LinkedList<RemoteServiceAdminEvent>();
		private final Semaphore sem = new Semaphore(0);

		/**
		 * @see org.osgi.service.remoteserviceadmin.RemoteServiceAdminListener#remoteAdminEvent(org.osgi.service.remoteserviceadmin.RemoteServiceAdminEvent)
		 */
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
