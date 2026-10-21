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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointEvent;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;
import org.osgi.service.remoteserviceadmin.ExportRegistration;
import org.osgi.service.remoteserviceadmin.RemoteConstants;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdmin;
import org.osgi.test.cases.remoteserviceadmin.common.A;
import org.osgi.test.cases.remoteserviceadmin.common.B;
import org.osgi.test.cases.remoteserviceadmin.common.ModifiableService;
import org.osgi.test.cases.remoteserviceadmin.common.RemoteServiceConstants;
import org.osgi.test.cases.remoteserviceadmin.impl.TestServiceImpl;

/**
 * Test the discovery portion of the spec by registering a EndpointDescription
 * in one framework and expecting it to show up in the other framework.
 *
 * @author <a href="mailto:tdiekman@tibco.com">Tim Diekmann</a>
 * @version 1.0.0
 */
@SuppressWarnings("deprecation")
public class DiscoveryTest extends MultiFrameworkTestCase {
	private static final String	SYSTEM_PACKAGES_EXTRA	= "org.osgi.test.cases.remoteserviceadmin.system.packages.extra";

	private final static List<Class< ? >>	SUPPORTED_TYPES			= Arrays
			.asList(new Class[] {
					String.class,
		Long.TYPE, Long.class,
		Integer.TYPE, Integer.class,
		Byte.TYPE, Byte.class,
		Character.TYPE, Character.class,
		Double.TYPE, Double.class,
		Float.TYPE, Float.class,
		Boolean.TYPE, Boolean.class,
		Short.TYPE, Short.class});

	private long timeout;

	/**
	 * @see org.osgi.test.cases.remoteserviceadmin.junit.MultiFrameworkTestCase#setUp()
	 */
	@BeforeEach
	public void setUp() throws Exception {
		super.setUp();
		timeout = getLongProperty("rsa.tck.timeout", 300000L);
	}
	/**
	 * @see org.osgi.test.cases.remoteserviceadmin.junit.MultiFrameworkTestCase#tearDown()
	 */
	@AfterEach
	public void tearDown() throws Exception {
		super.tearDown();
		System.out.println("Sleep for 5s before ending to allow for cleanup of ports before the next run.");
		Thread.sleep(5000);
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
	 * 122.6 Discovery
	 */
	@Test
	public void testDiscovery122_6() throws Exception {
		// verify that the server framework is exporting the test packages
		verifyFramework();

		//
		// install test bundle in child framework
		//
		BundleContext childContext = getFramework().getBundleContext();

		Bundle tb1Bundle = installBundle(childContext, "/tb1.jar");
		assertThat(tb1Bundle).isNotNull();

		//
		// register EndpointListener in parent framework
		//
		final int serviceId = 12345;
		final EndpointListenerImpl endpointListenerImpl = new SelectiveEndpointListenerImpl(
				serviceId);

		final String endpointListenerFilter = "(!(org.osgi.framework.uuid=" + context.getProperty("org.osgi.framework.uuid") + "))";
		String secondFilter = "(mykey=has been overridden)";
		Hashtable<String, Object> endpointListenerProperties = new Hashtable<String, Object>();
		endpointListenerProperties.put(
				org.osgi.service.remoteserviceadmin.EndpointListener.ENDPOINT_LISTENER_SCOPE,
				new String[] {
						endpointListenerFilter, secondFilter
				});

		ServiceRegistration<org.osgi.service.remoteserviceadmin.EndpointListener> endpointListenerRegistration = context
				.registerService(
						org.osgi.service.remoteserviceadmin.EndpointListener.class,
						endpointListenerImpl, endpointListenerProperties);
		assertThat(endpointListenerRegistration).isNotNull();

		EndpointListenerImpl emptyEndpointListener = null;
		try {
			//
			// 122.6.1 Scope and Filters
			// register an EndpointListener w/o a scope. If called, then fail
			//
			emptyEndpointListener = scope_and_filter_122_6_1("");

			// start test bundle in child framework
			// this will run the test in the child framework and fail
			tb1Bundle.start();

			System.out.println("************* wait for Signal 1 ********************");
			// verify callback in parent framework
			assertThat(endpointListenerImpl.getSemAdded()
					.tryAcquire(timeout, TimeUnit.MILLISECONDS)).isTrue();

			// 122.6.2 callback has to return first matched filter
			assertThat(endpointListenerImpl.getAddedMatchedFilter()).as("filter doesn't match the first filter").isEqualTo(endpointListenerFilter);

			EndpointDescription ep = endpointListenerImpl.getAddedEndpoint();
			assertThat(ep).isNotNull();
			assertThat(ep.getServiceId()).as("remote service id is incorrect").isEqualTo(serviceId);
			assertThat(ep.getId()).as("remote.id does not match").isEqualTo("someURI");
			assertThat(ep
					.getFrameworkUUID()).as("remote framework id is incorrect").isEqualTo(getFramework()
					.getBundleContext().getProperty("org.osgi.framework.uuid"));
			assertThat(ep.getFrameworkUUID().equals(
							context.getProperty("org.osgi.framework.uuid"))).as("remote framework id has to be UUID of remote not local framework").isFalse();
			assertThat(ep.getInterfaces().contains(A.class.getName())).as("discovered interfaces don't contain " + A.class.getName()).isTrue();
			assertThat(ep.getInterfaces().contains(B.class.getName())).as("discovered interfaces must not contain " + B.class.getName()).isFalse();
			assertThat(ep.getProperties().get("mykey")).as("the property of the service should have been overridden by the EndpointDescription").isEqualTo("has been overridden");
			assertThat(ep.getProperties().get("myprop")).as("the property myprop is missing").isEqualTo("myvalue");

			// verify 122.6.1
			assertThat(emptyEndpointListener.getAddedEndpoint()).isNull();

			System.out.println("************* Sleeping for 5s so that the discovery can settle ********************");
			Thread.sleep(5000);
			//
			// remove the endpoint
			//
			tb1Bundle.stop();

			System.out.println("************* wait for Signal 2 ********************");
			// verify callback in parent framework
			assertThat(endpointListenerImpl.getSemRemoved().tryAcquire(
					timeout, TimeUnit.MILLISECONDS)).isTrue();

			// 122.6.2 callback has to return first matched filter
			assertThat(endpointListenerImpl.getRemMatchedFilter()).as("filter doesn't match the first filter").isEqualTo(endpointListenerFilter);

			ep = endpointListenerImpl.getRemovedEndpoint();
			assertThat(ep).isNotNull();
			assertThat(ep.getServiceId()).as("remote service id is incorrect").isEqualTo(serviceId);
			assertThat(ep.getId()).as("remote.id does not match").isEqualTo("someURI");
			assertThat(ep
					.getFrameworkUUID()).as("remote framework id is incorrect").isEqualTo(getFramework()
					.getBundleContext().getProperty("org.osgi.framework.uuid"));
			assertThat(ep.getFrameworkUUID().equals(
							context.getProperty("org.osgi.framework.uuid"))).as("remote framework id has to be UUID of remote not local framework").isFalse();
			assertThat(ep.getInterfaces().contains(A.class.getName())).as("discovered interfaces don't contain " + A.class.getName()).isTrue();
			assertThat(ep.getInterfaces().contains(B.class.getName())).as("discovered interfaces must not contain " + B.class.getName()).isFalse();
			assertThat(ep.getProperties().get("mykey")).as("the property of the service should have been overridden by the EndpointDescription").isEqualTo("has been overridden");
			assertThat(ep.getProperties().get("myprop")).as("the property myprop is missing").isEqualTo("myvalue");
		} finally {
			endpointListenerRegistration.unregister();
			
			if (emptyEndpointListener != null) {
				emptyEndpointListener.getServiceRegistration().unregister();
			}
		}
	}

	/**
	 * 122.6 Discovery
	 *
	 * EndpointDescription via XML file
	 */
	@Test
	public void testDiscovery122_6_8() throws Exception {
		Set<String> set = new HashSet<String>();
		set.add("one");
		set.add("two");
		List<String> list = new LinkedList<String>();
		list.add("first");
		list.add("second");

		// register a service and get the exported EndpointDescription
		Hashtable<String, Object> dictionary = new Hashtable<String, Object>();
		dictionary.put("mykey", "will be overridden");
		dictionary.put("myprop", "myvalue");
		dictionary.put("good test", true);
		dictionary.put("myset", set);
		dictionary.put(RemoteServiceConstants.SERVICE_EXPORTED_INTERFACES, "*");

		TestServiceImpl service = new TestServiceImpl();

		ServiceRegistration< ? > registration = context
				.registerService(new String[] {
						A.class.getName(), B.class.getName()
				}, service, dictionary);
		assertThat(registration).isNotNull();
		
		ServiceReference<RemoteServiceAdmin> rsaRef = null;
		
		try {
			rsaRef = context.getServiceReference(RemoteServiceAdmin.class);
			assertThat(rsaRef).isNotNull();
			RemoteServiceAdmin rsa = context.getService(rsaRef);
			assertThat(rsa).isNotNull();

			Map<String, Object> properties = loadCTProperties();
			properties.put("mykey", "has been overridden");
			properties.put("mylist", list);
			properties.put("myfloat", 3.1415f);
			properties.put("mydouble", -3.1415d);
			properties.put("mychar", 't');
			properties.put("myxml", "<myxml>test</myxml>");

			// export the service
			Collection<ExportRegistration> exportRegistrations = rsa.exportService(registration.getReference(), properties);
			assertThat(exportRegistrations).isNotNull();
			assertThat(exportRegistrations).isNotEmpty();
			
			try {

				EndpointDescription description = exportRegistrations.iterator().next().getExportReference().getExportedEndpoint();
				assertThat(description).isNotNull();

				// add a new property that only the XML description has
				Map<String, Object> props = new HashMap<String, Object>();
				props.putAll(description.getProperties());
				props.put("newkey", "newvalue");
				props.remove(RemoteConstants.ENDPOINT_FRAMEWORK_UUID); // this points to the parent framework and needs to be removed
				props.remove("service.id"); // this is specific to the parent framework
				description = new EndpointDescription(props);

				// verify that the server framework is exporting the test
				// packages
				verifyFramework();
				BundleContext childContext = getFramework().getBundleContext();

				// provide the Service interfaces to the child framework to
				// allow the rsa implementation to import the service as soon as
				// the config bundle appears

				Bundle tbInterfacesBundle = installBundle(childContext,
						"/tbInterfaces.jar");
				assertThat(tbInterfacesBundle).isNotNull();
				// tbInterfacesBundle.start();

				// create an XML file version of the description
				String xmlStr = toXml(description);
				System.out.println(xmlStr);

				// create a bundle and start the bundle in the child framework
				String testbundleloc = createBundle(xmlStr);

				Bundle testbundle = childContext.installBundle(testbundleloc);
				assertThat(testbundle).isNotNull();

				testbundle.start();

				//
				// install test bundle in child framework
				//

				Bundle tb3Bundle = installBundle(childContext,"/tb3.jar");
				assertThat(tb3Bundle).isNotNull();

				tb3Bundle.start(); // throws Exception if test was not successful

				Thread.sleep(2000);

				// remove the proxy
				testbundle.stop();

				tb3Bundle.stop(); // throws Exception if test was not successful
			} finally {
				for (ExportRegistration reg : exportRegistrations) {
					reg.close();
				}
			}
		} finally {
			if (rsaRef != null) {
				context.ungetService(rsaRef);
			}
			
			registration.unregister();
		}
	}

	@Test
	public void testDiscoveryBasicEndpointEvents_122_6_3()
			throws Exception {
		// verify that the server framework is exporting the test packages
		verifyFramework();

		//
		// install test bundle in child framework
		//
		BundleContext childContext = getFramework().getBundleContext();

		Bundle tb7Bundle = installBundle(childContext, "/tb7.jar");
		assertThat(tb7Bundle).isNotNull();

		//
		// register EndpointListener in parent framework
		//
		EndpointEventListenerImpl endpointEventListenerImpl = new EndpointEventListenerImpl();
		
		Hashtable<String, Object> endpointEventListenerProperties = new Hashtable<String, Object>();
		final String endpointEventListenerFilter = "(&(mykey=has been overridden)(!(org.osgi.framework.uuid="
				+ context.getProperty("org.osgi.framework.uuid") + ")))";
		String secondFilter = "(mykey=has been overridden)";
		endpointEventListenerProperties.put(
				EndpointEventListener.ENDPOINT_LISTENER_SCOPE, new String[] {
						endpointEventListenerFilter, secondFilter });

		ServiceRegistration<EndpointEventListener> endpointEventListenerRegistration = context
				.registerService(EndpointEventListener.class,
						endpointEventListenerImpl,
						endpointEventListenerProperties);
		assertThat(endpointEventListenerRegistration).isNotNull();
		
		EndpointEventListenerImpl emptyEndpointEventListener = null;
		try {
			//
			// 122.6.1 Scope and Filters
			// register an EndpointListener w/o a scope. If called, then fail
			//
			emptyEndpointEventListener = scope_and_filter_122_6_1___RSA_1_1("");

			// start test bundle in child framework
			// this will run the test in the child framework and fail
			tb7Bundle.start();

			System.out
					.println("************* wait for Signal 1 (EndpointEvent:added) ********************");
			// verify callback in parent framework
			assertThat(endpointEventListenerImpl.getSemAdded().tryAcquire(
					timeout, TimeUnit.MILLISECONDS)).isTrue();
			System.out
					.println("************* recieved Signal 1 (EndpointEvent:added) ********************");

			verifyBasicEndpointEventBehavior(
					endpointEventListenerImpl.getLastMatchedFilter(),
					endpointEventListenerImpl.getLastAddedEndpoint(),
					endpointEventListenerFilter);

			System.out
					.println("************* Sleeping for 5s so that the discovery can settle ********************");
			Thread.sleep(5000);


			// get the service provided by our test bundle
			ServiceReference< ? > modServiceRef = tb7Bundle
					.getRegisteredServices()[0];
			assertThat(modServiceRef).isNotNull();
			
			// let it know that we want it to modify its registration (and raise
			// an endpoint modified event)
			Object modService = tb7Bundle.getBundleContext().getService(
					modServiceRef);
			assertThat(modService).isNotNull();
			modService.getClass().getDeclaredMethod("addServiceProperty")
					.invoke(modService);

			
			System.out
					.println("************* wait for Signal 2 (EndpointEvent:modified) ********************");
			// verify callback in parent framework
			assertThat(endpointEventListenerImpl.getSemModified()
					.tryAcquire(
							timeout, TimeUnit.MILLISECONDS)).isTrue();
			System.out
					.println("************* recieved Signal 2 (EndpointEvent:modified) ********************");

			verifyBasicEndpointEventBehavior(
					endpointEventListenerImpl.getLastMatchedFilter(),
					endpointEventListenerImpl.getLastAddedEndpoint(),
					endpointEventListenerFilter);

			System.out
					.println("************* Sleeping for 5s so that the discovery can settle ********************");
			Thread.sleep(5000);

			//
			// remove the endpoint
			//
			tb7Bundle.stop();

			 System.out
					.println("************* wait for Signal 3 (EndpointEvent:removed) ********************");
			 // verify callback in parent framework
			assertThat(endpointEventListenerImpl.getSemRemoved().tryAcquire(
					timeout, TimeUnit.MILLISECONDS)).isTrue();
			System.out
					.println("************* recieved Signal 3 (EndpointEvent:removed) ********************");

			verifyBasicEndpointEventBehavior(
					endpointEventListenerImpl.getLastMatchedFilter(),
					endpointEventListenerImpl.getLastRemovedEndpoint(),
					endpointEventListenerFilter);

			// verify that we didn't receive any other events
			assertThat(endpointEventListenerImpl.getEventCount()).isEqualTo(3);

			// verify 122.6.1
			assertThat(emptyEndpointEventListener.getEventCount()).isZero();

		} finally {
			endpointEventListenerRegistration.unregister();

			if (emptyEndpointEventListener != null) {
				emptyEndpointEventListener.getServiceRegistration().unregister();
			}
		}
	}

	private void verifyBasicEndpointEventBehavior(String lastMatchedFilter,
			EndpointDescription ep, final String endpointEventListenerFilter) {
		// 122.6.2 callback has to return first matched filter
		assertThat(lastMatchedFilter).as("filter doesn't match the first filter").isEqualTo(endpointEventListenerFilter);

		assertThat(ep).isNotNull();
		assertThat(ep.getServiceId()).as("remote service id is incorrect").isEqualTo(12345);
		assertThat(ep.getId()).as("remote.id does not match").isEqualTo("someURI");
		assertThat(ep.getFrameworkUUID()).as("remote framework id is incorrect").isEqualTo(getFramework()
				.getBundleContext().getProperty("org.osgi.framework.uuid"));
		assertThat(ep.getFrameworkUUID()
						.equals(context.getProperty(
								"org.osgi.framework.uuid"))).as("remote framework id has to be UUID of remote not local framework").isFalse();
		assertThat(ep.getInterfaces()
				.contains(ModifiableService.class.getName())).as("discovered interfaces don't contain "
				+ ModifiableService.class.getName()).isTrue();
		assertThat(ep.getInterfaces().contains(B.class.getName())).as("discovered interfaces must not contain " + B.class.getName()).isFalse();
		assertThat(ep.getProperties().get("mykey")).as("the property of the service should have been overridden by the EndpointDescription").isEqualTo("has been overridden");
		assertThat(ep
				.getProperties().get("myprop")).as("the property myprop is missing").isEqualTo("myvalue");
	}

	/**
	 * Creates a Bundle that contains the given XML as an Endpoint Description
	 * 
	 * @param xmlStr
	 * @return
	 * @throws IOException
	 */
	private String createBundle(String xmlStr) throws IOException {
		File bundle = File.createTempFile("rsatck", ".jar");

		Manifest manifest = new Manifest();
		Attributes attrs = manifest.getMainAttributes();
		attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
		attrs.put(new Attributes.Name(
				org.osgi.framework.Constants.BUNDLE_NAME),
				"RemoteServiceAdmin TCK");
		attrs.put(new Attributes.Name(org.osgi.framework.Constants.BUNDLE_SYMBOLICNAME), "org.osgi.test.cases.remoteserviceadmin.testbundle");
		attrs.put(new Attributes.Name(org.osgi.framework.Constants.BUNDLE_MANIFESTVERSION), "2");
		attrs.put(new Attributes.Name(org.osgi.framework.Constants.BUNDLE_VERSION),	"1.0.0");
		attrs.put(
				new Attributes.Name(org.osgi.framework.Constants.BUNDLE_VENDOR),
				"OSGi TCK");
		attrs.put(new Attributes.Name("Remote-Service"), "endpoint.xml");

		JarOutputStream jos = new JarOutputStream(new FileOutputStream(bundle), manifest);

		JarEntry je = new JarEntry("endpoint.xml");
		jos.putNextEntry(je);

		jos.write(xmlStr.getBytes());

		jos.flush();
		jos.closeEntry();
		jos.close();

		return bundle.toURI().toString();
	}

	/**
	 * @param description
	 * @return
	 */
	private String toXml(EndpointDescription description) {
		StringBuffer sb = new StringBuffer();

		sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>").append("\n");
		sb.append("<endpoint-descriptions xmlns=\"http://www.osgi.org/xmlns/rsa/v1.0.0\">").append("\n");
		sb.append("<endpoint-description>").append("\n");

		String str;
		for (Iterator<Map.Entry<String, Object>> it = description.getProperties().entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<String, Object> entry = it.next();
			Object value = entry.getValue();
			Class<?> clazz = value.getClass();
			str = null;

			if (SUPPORTED_TYPES.contains(clazz)) {
				String type = clazz.getName();
				type = type.substring(type.lastIndexOf(".") != -1 ? type.lastIndexOf(".") + 1 : 0);

				if ("String".equals(type) && ((String)value).startsWith("<")) {
					str = ">\n <xml>\n" + value.toString() + "\n </xml>";

				} else {
					str = " value-type=\"" + type + "\" value=\"" + value.toString() + "\">";
				}
			} else if (value instanceof List<?>) {
				str = asList((List<?>)value);
			} else if (value instanceof Set<?>) {
				str = asSet((Set<?>)value);
			} else if ((str = isArray(value)) != null) {

			} else {
				System.err.println("unsupported property type " + clazz.getName());
				continue;
			}

			sb.append(" <property name=\"").append(entry.getKey()).append("\"");
			sb.append(str).append("\n");
			sb.append(" </property>").append("\n");
		}

		sb.append("</endpoint-description>").append("\n");
		sb.append("</endpoint-descriptions>");

		return sb.toString();
	}

	/**
	 * @param value
	 * @return
	 */
	private String isArray(Object value) {
		if (!value.getClass().isArray()) {
			return null;
		}

		Class< ? > type = value.getClass().getComponentType();
		if (!SUPPORTED_TYPES.contains(type)) {
			System.err.println("unsupported array type " + type);
		}

		String str = type.getName();
		str = str.substring(str.lastIndexOf(".") != -1 ? str.lastIndexOf(".") + 1 : 0);


		StringBuffer sb = new StringBuffer();
		sb.append(" value-type=\"").append(str).append("\">\n");
		sb.append("  <array>").append("\n");

		Object[] arr = (Object[]) value;
		for (int i = 0; i < arr.length; i++) {
			sb.append("   <value>").append(arr[i].toString()).append("</value>").append("\n");
		}

		sb.append("  </array>");
		return sb.toString();
	}

	/**
	 * @param value
	 * @return
	 */
	private String asList(List<?> value) {
		StringBuffer sb = new StringBuffer();

		if (value.size() > 0) {
			Class<?> type = value.iterator().next().getClass();
			if (!SUPPORTED_TYPES.contains(type)) {
				System.err.println("unsupported list type " + type);
			}

			String str = type.getName();
			str = str.substring(str.lastIndexOf(".") != -1 ? str.lastIndexOf(".") + 1 : 0);

			sb.append(" value-type=\"").append(str).append("\">\n");
		} else {
			sb.append("\">\n");
		}


		sb.append("  <list>").append("\n");
		for (Iterator<?> it = value.iterator(); it.hasNext(); ) {
			sb.append("   <value>").append(it.next().toString()).append("</value>").append("\n");
		}
		sb.append("  </list>");

		return sb.toString();
	}

	/**
	 * @param value
	 * @return
	 */
	private String asSet(Set<?> value) {
		StringBuffer sb = new StringBuffer();

		if (value.size() > 0) {
			Class<?> type = value.iterator().next().getClass();
			if (!SUPPORTED_TYPES.contains(type)) {
				System.err.println("unsupported list type " + type);
			}

			String str = type.getName();
			str = str.substring(str.lastIndexOf(".") != -1 ? str.lastIndexOf(".") + 1 : 0);

			sb.append(" value-type=\"").append(str).append("\">\n");
		} else {
			sb.append("\">\n");
		}

		sb.append("  <set>").append("\n");
		for (Iterator<?> it = value.iterator(); it.hasNext(); ) {
			sb.append("   <value>").append(it.next().toString()).append("</value>").append("\n");
		}
		sb.append("  </set>");

		return sb.toString();
	}

	/**
	 * Test empty filter scope for EndpointListener
	 */
	private EndpointListenerImpl scope_and_filter_122_6_1(String scope)
			throws Exception {
		Hashtable<String, String> elp = new Hashtable<String, String>();
		elp.put(org.osgi.service.remoteserviceadmin.EndpointListener.ENDPOINT_LISTENER_SCOPE,
				scope);

		EndpointListenerImpl el = new EndpointListenerImpl();
		ServiceRegistration<org.osgi.service.remoteserviceadmin.EndpointListener> elr = context
				.registerService(
						org.osgi.service.remoteserviceadmin.EndpointListener.class,
				el, elp);
		assertThat(elr).isNotNull();
		assertThat(elr.getReference()).isNotNull();
		el.setServiceRegistration(elr);

		return el;
	}

	/**
	 * Test empty filter scope for EndpointEventListener
	 */
	private EndpointEventListenerImpl scope_and_filter_122_6_1___RSA_1_1(
			String scope)
			throws Exception {
		Hashtable<String, String> elp = new Hashtable<String, String>();
		elp.put(EndpointEventListener.ENDPOINT_LISTENER_SCOPE, scope);

		EndpointEventListenerImpl el = new EndpointEventListenerImpl();
		ServiceRegistration<EndpointEventListener> elr = context
				.registerService(EndpointEventListener.class, el, elp);
		assertThat(elr).isNotNull();
		assertThat(elr.getReference()).isNotNull();
		el.setServiceRegistration(elr);

		return el;
	}


	/**
	 * @deprecated
	 */
	@Deprecated
	class EndpointListenerImpl
			implements org.osgi.service.remoteserviceadmin.EndpointListener {
		private Semaphore semAdded = new Semaphore(0);
		private Semaphore semRemoved = new Semaphore(0);
		private String addedMatchedFilter;
		private String remMatchedFilter;
		private EndpointDescription addedEndpoint;
		private EndpointDescription removedEndpoint;
		private ServiceRegistration<org.osgi.service.remoteserviceadmin.EndpointListener>	serviceRegistration;

		/**
		 * @param serviceRegistration the serviceRegistration to set
		 */
		public void setServiceRegistration(
				ServiceRegistration<org.osgi.service.remoteserviceadmin.EndpointListener> serviceRegistration) {
			this.serviceRegistration = serviceRegistration;
		}
		
		/**
		 * @return the serviceRegistration
		 */
		public ServiceRegistration<org.osgi.service.remoteserviceadmin.EndpointListener> getServiceRegistration() {
			return this.serviceRegistration;
		}
		
		/**
		 * @see org.osgi.service.remoteserviceadmin.EndpointListener#endpointAdded(org.osgi.service.remoteserviceadmin.EndpointDescription, java.lang.String)
		 */
		@Override
		public void endpointAdded(EndpointDescription endpoint,
				String matchedFilter) {

			this.addedEndpoint = endpoint;
			this.addedMatchedFilter = matchedFilter;
			semAdded.release();
		}

		/**
		 * @see org.osgi.service.remoteserviceadmin.EndpointListener#endpointRemoved(org.osgi.service.remoteserviceadmin.EndpointDescription, java.lang.String)
		 */
		@Override
		public void endpointRemoved(EndpointDescription endpoint,
				String matchedFilter) {

			this.removedEndpoint = endpoint;
			this.remMatchedFilter = matchedFilter;
			semRemoved.release();
		}

		/**
		 * @return the sem
		 */
		public Semaphore getSemAdded() {
			return semAdded;
		}

		/**
		 * @return the sem
		 */
		public Semaphore getSemRemoved() {
			return semRemoved;
		}

		/**
		 * @return the matchedFilter
		 */
		public String getAddedMatchedFilter() {
			return addedMatchedFilter;
		}

		/**
		 * @return the matchedFilter
		 */
		public String getRemMatchedFilter() {
			return remMatchedFilter;
		}

		/**
		 * @return the addedEndpoint
		 */
		public EndpointDescription getAddedEndpoint() {
			return addedEndpoint;
		}

		/**
		 * @return the removedEndpoint
		 */
		public EndpointDescription getRemovedEndpoint() {
			return removedEndpoint;
		}
	}

	class SelectiveEndpointListenerImpl
			extends EndpointListenerImpl {
		private final int serviceId;

		public SelectiveEndpointListenerImpl(int serviceId) {
			this.serviceId = serviceId;
		}

		@Override
		public void endpointAdded(EndpointDescription endpoint,
				String matchedFilter) {

			if (serviceId == endpoint.getServiceId()) {
				super.endpointAdded(endpoint, matchedFilter);
			}
		}

		@Override
		public void endpointRemoved(EndpointDescription endpoint,
				String matchedFilter) {

			if (serviceId == endpoint.getServiceId()) {
				super.endpointRemoved(endpoint, matchedFilter);
			}
		}
	}

	class EndpointEventListenerImpl implements EndpointEventListener {

		private Semaphore semAdded = new Semaphore(0);
		private Semaphore semRemoved = new Semaphore(0);
		private Semaphore semModified = new Semaphore(0);
		private Semaphore semModifiedEndmatch = new Semaphore(0);
		private ServiceRegistration<EndpointEventListener> serviceRegistration;
		private AtomicInteger eventCount = new AtomicInteger(0);
		private String lastMatchedFilter;
		private EndpointDescription lastAddedEndpoint;
		private EndpointDescription lastRemovedEndpoint;

		@Override
		public void endpointChanged(EndpointEvent event, String matchedFilter) {
			System.out
					.println("***************************************************************** ENDPOINT CHANGED!!! "
							+ event.getType());
			
			eventCount.incrementAndGet();

			lastMatchedFilter = matchedFilter;

			if (EndpointEvent.ADDED == event.getType()) {
				lastAddedEndpoint = event.getEndpoint();
				semAdded.release();
			}

			if (EndpointEvent.MODIFIED == event.getType()) {
				semModified.release();
			}

			if (EndpointEvent.MODIFIED_ENDMATCH == event.getType()) {
				semModifiedEndmatch.release();
			}

			if (EndpointEvent.REMOVED == event.getType()) {
				lastRemovedEndpoint = event.getEndpoint();
				semRemoved.release();
			}
		}

		public Semaphore getSemModified() {
			return semModified;
		}

		public EndpointDescription getLastRemovedEndpoint() {
			return lastRemovedEndpoint;
		}

		public EndpointDescription getLastAddedEndpoint() {
			return lastAddedEndpoint;
		}

		public int getEventCount() {
			return eventCount.get();
		}

		public ServiceRegistration<EndpointEventListener> getServiceRegistration() {
			return serviceRegistration;
		}

		public void setServiceRegistration(
				ServiceRegistration<EndpointEventListener> sr) {
			serviceRegistration = sr;
		}

		public Semaphore getSemRemoved() {
			return semRemoved;
		}

		public Semaphore getSemAdded() {
			return semAdded;
		}

		public String getLastMatchedFilter() {
			return lastMatchedFilter;
		}

	}
}
