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

package org.osgi.test.cases.remoteserviceadmin.tb8;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.ExportReference;
import org.osgi.service.remoteserviceadmin.ExportRegistration;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdmin;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdminEvent;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdminListener;
import org.osgi.test.cases.remoteserviceadmin.common.A;
import org.osgi.test.cases.remoteserviceadmin.common.B;
import org.osgi.test.cases.remoteserviceadmin.common.ModifiableService;
import org.osgi.test.cases.remoteserviceadmin.common.RemoteServiceConstants;
import org.osgi.test.cases.remoteserviceadmin.common.TestEventHandler;
import org.osgi.test.cases.remoteserviceadmin.common.TestRemoteServiceAdminListener;
import org.osgi.test.cases.remoteserviceadmin.common.Utils;
import org.osgi.test.cases.remoteserviceadmin.util.TestBundleSupport;
import org.osgi.util.tracker.ServiceTracker;

/**
 * 
 * Test bundle that registers a service to be exported and manually notifies any
 * discovery implementation that it should publish an endpoint for this service.
 * The bundle is very similar to tb1 but uses the RSA 1.1 EndpointEventListener
 * instead of the deprecated EndpointListener.
 * 
 * The bundle further provides an implementation of the ModifiableService
 * allowing to remotely change the service registration to cause a service
 * modified event.
 * 
 * @author <a href="mailto:marc@marc-schaaf.de">Marc Schaaf</a>
 * 
 */
public class Activator implements BundleActivator, ModifiableService, B {

	private ServiceRegistration<ModifiableService> registration;
	private BundleContext bctx;
	private Map<String, Object> endpointProperties;
	private ServiceReference<RemoteServiceAdmin>	rsaRef;
	private RemoteServiceAdmin rsa;
	private Collection<ExportRegistration> exportRegistrations;
	private TestRemoteServiceAdminListener remoteServiceAdminListener;
	private long timeout;
	private final String version = "1.0.0";

	/**
	 * Used for writing the endpoint description to a system property to be used
	 * by parent framework @see {@link Utils.exportEndpointDescription}
	 */
	private int registrationCounter = 0;
	private TestEventHandler m_eventHandler;

	private Hashtable<String, String> createBasicServiceProperties() {
		Hashtable<String, String> dictionary = new Hashtable<String, String>();
		dictionary.put("mykey", "will be overridden");
		dictionary.put("myprop", "myvalue");
		dictionary.put(RemoteServiceConstants.SERVICE_EXPORTED_INTERFACES,
				ModifiableService.class.getName());
		return dictionary;
	}

	@Override
	public void start(BundleContext context) throws Exception {
		this.bctx = context;
		timeout = TestBundleSupport.getLongProperty("rsa.tck.timeout",
				300000L);

		Hashtable<String, String> dictionary = createBasicServiceProperties();

		registration = context.registerService(ModifiableService.class, this,
				dictionary);

		test();

	}

	private void test() throws Exception {

		m_eventHandler = new TestEventHandler(timeout);

		Hashtable<String, Object> props = new Hashtable<String, Object>();
		props.put(EventConstants.EVENT_TOPIC, new String[] {
				"org/osgi/service/remoteserviceadmin/EXPORT_REGISTRATION",
				"org/osgi/service/remoteserviceadmin/EXPORT_UNREGISTRATION",
				"org/osgi/service/remoteserviceadmin/EXPORT_ERROR",
				"org/osgi/service/remoteserviceadmin/EXPORT_UPDATE" });

		bctx.registerService(EventHandler.class.getName(), m_eventHandler,
				props);

		// lookup RemoteServiceAdmin service
		ServiceTracker<RemoteServiceAdmin, RemoteServiceAdmin> tracker = new ServiceTracker<RemoteServiceAdmin, RemoteServiceAdmin>(
				bctx, RemoteServiceAdmin.class, null);
		tracker.open();
		tracker.waitForService(timeout);

		rsaRef = tracker.getServiceReference();

		tracker.close();

		assertThat(rsaRef).isNotNull();
		rsa = bctx.getService(rsaRef);
		assertThat(rsa).isNotNull();

		//
		// register a RemoteServiceAdminListener to receive the export
		// notification
		//
		remoteServiceAdminListener = new TestRemoteServiceAdminListener(timeout);
		ServiceRegistration<RemoteServiceAdminListener> sr = bctx
				.registerService(RemoteServiceAdminListener.class,
				remoteServiceAdminListener, null);
		assertThat(sr).isNotNull();

		// load the external properties file with the config types for the
		// server side service
		endpointProperties = Utils.loadServerTCKProperties(bctx);
		endpointProperties.putAll(createBasicServiceProperties());
		Utils.processFreePortProperties(endpointProperties);

		// export the service
		exportRegistrations = rsa.exportService(registration.getReference(),
				endpointProperties);
		assertThat(exportRegistrations).isNotNull();
		assertThat(exportRegistrations).isNotEmpty();

		for (Iterator<ExportRegistration> it = exportRegistrations.iterator(); it
				.hasNext();) {
			ExportRegistration er = it.next();
			assertThat(er.getException()).isNull();
			ExportReference ref = er.getExportReference();
			assertThat(ref).isNotNull();

			// 122.4.1 Exporting
			assertThat(ref.getExportedService()).isEqualTo(registration.getReference());

			assertThat(TestBundleSupport
					.arrayToString((Object[]) ref.getExportedService()
							.getProperty("objectClass"), true)).isEqualTo(TestBundleSupport.arrayToString(
					(Object[]) registration.getReference().getProperty(
							"objectClass"), true));
			assertThat(ref
							.getExportedService().getProperty("service.id")).isEqualTo(registration.getReference().getProperty("service.id"));

			EndpointDescription ed = ref.getExportedEndpoint();
			verifyBasicEndpointDescriptionProperties(ed);

			Utils.exportEndpointDescription(ed, version, registrationCounter++);

			{ // check if the required EXPORT_REGISTRATION event was raised
				Event event = m_eventHandler
						.getNextEventForTopic("org/osgi/service/remoteserviceadmin/EXPORT_REGISTRATION");
				assertThat(event).isNotNull();
				RemoteServiceAdminEvent rsaevent = TestEventHandler
						.verifyBasicRsaEventProperties(rsaRef, event);
				assertThat(rsaevent).isNotNull();
				assertThat(event.getProperty("timestamp")).isNotNull();

				// check event type
				@SuppressWarnings("unused")
				String topic = event.getTopic();
				assertThat(event.getProperty("cause")).as("cause in event").isNull();
				assertThat(rsaevent.getType()).isEqualTo(RemoteServiceAdminEvent.EXPORT_REGISTRATION);
			}
		}


	}

	private void stoptest() throws Exception {

	}

	@Override
	public void stop(BundleContext context) throws Exception {
		registration.unregister();
		stoptest();
	}

	@Override
	public String getB() {
		return "this is B";
	}

	@Override
	public void addServiceProperty() {
		// System.out
		// .println("TestBundle8: ------------------------- ADD SERVICE PROPERTY AND REQUEST UPDATE(Endpoint) FOR ALL ExportRegistrations ---------------- ");
		// update the service registration
		Hashtable<String, String> properties_reg = createBasicServiceProperties();
		properties_reg.put("someNewProp", "SomeValue");
		registration.setProperties(properties_reg);

		for (Iterator<ExportRegistration> it = exportRegistrations.iterator(); it
				.hasNext();) {
			ExportRegistration er = it.next();

			endpointProperties = Utils.loadServerTCKProperties(bctx);
			endpointProperties.putAll(properties_reg);
			
			EndpointDescription edNew = er.update(endpointProperties);
			assertThat(edNew).isNotNull();

			try {
				// make the updated description available for the parent test
				// case
				Utils.exportEndpointDescription(edNew, version,
						registrationCounter++);
			} catch (IOException e) {
				e.printStackTrace();
				fail("was unable to publish the updated endpoint description for the parent framework");
			}

			// verify that all relevant props are there including the updated
			// one

			verifyBasicEndpointDescriptionProperties(edNew);
			assertThat(edNew.getProperties()
					.get("someNewProp")).as("The updated endpoint description must contain the new service property").isEqualTo("SomeValue");

			{ // check if the required EXPORT_UPDATE event was raised
				Event event = m_eventHandler
						.getNextEventForTopic("org/osgi/service/remoteserviceadmin/EXPORT_UPDATE");
				assertThat(event).isNotNull();
				RemoteServiceAdminEvent rsaevent = TestEventHandler
						.verifyBasicRsaEventProperties(rsaRef, event);
				assertThat(rsaevent).isNotNull();
				assertThat(event.getProperty("timestamp")).isNotNull();

				// check event type
				@SuppressWarnings("unused")
				String topic = event.getTopic();
				assertThat(event.getProperty("cause")).as("cause in event").isNull();
				assertThat(rsaevent.getType()).isEqualTo(RemoteServiceAdminEvent.EXPORT_UPDATE);
			}
		}

	}

	@Override
	public void changeRequiredServiceProperty() {
		// not needed here...
	}

	private void verifyBasicEndpointDescriptionProperties(EndpointDescription ed) {
		assertThat(ed).isNotNull();
		assertThat(ed.getProperties().get("objectClass")).isNotNull();
		assertThat(ed.getInterfaces().contains(
				ModifiableService.class.getName())).isTrue();
		assertThat(ed.getInterfaces().contains(A.class.getName())).isFalse();
		assertThat(ed.getInterfaces().contains(B.class.getName())).isFalse();

		assertThat(ed.getId()).isNotNull();
		assertThat(ed.getConfigurationTypes()).isNotNull();
		assertThat(ed.getConfigurationTypes().isEmpty()).isFalse();
		assertThat(ed.getFrameworkUUID()).isEqualTo(bctx.getProperty("org.osgi.framework.uuid"));
		assertThat(ed.getProperties().get("endpoint.service.id")).isNotNull();
	}
}
