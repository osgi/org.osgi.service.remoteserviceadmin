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
package org.osgi.test.cases.remoteservices.junit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.Assertions.within;
import static org.osgi.framework.Constants.BUNDLE_MANIFESTVERSION;
import static org.osgi.framework.Constants.BUNDLE_SYMBOLICNAME;
import static org.osgi.framework.Constants.IMPORT_PACKAGE;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.ServiceException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.Version;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.test.cases.remoteservices.common.A;
import org.osgi.test.cases.remoteservices.common.AsyncJava8Types;
import org.osgi.test.cases.remoteservices.common.AsyncTypes;
import org.osgi.test.cases.remoteservices.common.B;
import org.osgi.test.cases.remoteservices.common.BasicTypes;
import org.osgi.test.cases.remoteservices.common.C;
import org.osgi.test.cases.remoteservices.common.DTOType;
import org.osgi.test.cases.remoteservices.common.SlowService;
import org.osgi.test.cases.remoteservices.impl.AsyncJava8TypesImpl;
import org.osgi.test.cases.remoteservices.impl.AsyncTypesImpl;
import org.osgi.test.cases.remoteservices.impl.BasicTypesTestServiceImpl;
import org.osgi.test.cases.remoteservices.impl.SlowServiceImpl;
import org.osgi.test.cases.remoteservices.impl.TestServiceImpl;
import org.osgi.util.function.Predicate;
import org.osgi.util.promise.Promise;
import org.osgi.util.promise.Success;
import org.osgi.util.tracker.ServiceTracker;

/**
 * @author <a href="mailto:tdiekman@tibco.com">Tim Diekmann</a>
 *
 */
public class SimpleTest extends MultiFrameworkTestCase {

	/**
	 * Package to be exported by the server side System Bundle
	 */
	private static final String ORG_OSGI_TEST_CASES_REMOTESERVICES_COMMON = "org.osgi.test.cases.remoteservices.common";

	/**
	 * Magic value. Properties with this value will be replaced by a socket port number that is currently free.
	 */
    private static final String FREE_PORT = "@@FREE_PORT@@";

	private long timeout;

	/**
	 * @see org.osgi.test.cases.remoteserviceadmin.junit.MultiFrameworkTestCase#setUp()
	 */
	@BeforeEach
	public void setUp() throws Exception {
		super.setUp();
		timeout = getLongProperty("rsa.tck.timeout", 30000L);
	}

	/**
	 * @see org.osgi.test.cases.remoteservices.junit.MultiFrameworkTestCase#getConfiguration()
	 */
	@Override
	public Map<String,String> getConfiguration() {
		Map<String,String> configuration = new HashMap<>();
		configuration.put(Constants.FRAMEWORK_STORAGE_CLEAN, "true");

		//make sure that the server framework System Bundle exports the interfaces
		String systemPacakagesXtra = ORG_OSGI_TEST_CASES_REMOTESERVICES_COMMON;
		configuration.put(Constants.FRAMEWORK_SYSTEMPACKAGES_EXTRA,
				systemPacakagesXtra);
		String configuratorInitial = System
				.getProperty("server.configurator.initial");
		if (configuratorInitial != null) {
			configuration.put("configurator.initial", configuratorInitial);
		}
		return configuration;
	}

	/**
	 * @throws Exception
	 */
	@Test
	public void testSimpleRegistration() throws Exception {
		Hashtable<String,Object> properties = registrationTestServiceProperties();

		// install server side test service in the sub-framework
		ServiceRegistration< ? > srTestService = installTestBundleAndRegisterServiceObject(
				TestServiceImpl.class.getName(), properties, A.class.getName(),
				B.class.getName());
		assertThat(srTestService).isNotNull();

		System.out.println("registered test service A and B on server side");

		Thread.sleep(1000);

		try {
			{
				ServiceTracker<A,A> clientTracker = new ServiceTracker<>(
						context, A.class, null);
				clientTracker.open();

				// the proxy should appear in this framework
				A client = clientTracker.waitForService(timeout);
				assertThat(client).as("no proxy for service A found!").isNotNull();

				ServiceReference<A> sr = clientTracker.getServiceReference();
				System.out.println(sr);
				assertThat(sr).isNotNull();
				assertThat(sr.getProperty("service.imported")).isNotNull();
				assertThat(sr.getProperty("service.id")).isNotNull();
				assertThat(sr.getProperty("endpoint.id")).isNotNull();
				assertThat(sr.getProperty("service.imported.configs")).isNotNull();
				assertThat(sr.getProperty(".myprop")).as("private properties must not be exported").isNull();
				assertThat(sr.getProperty("implementation")).as("property implementation missing from proxy").isEqualTo("1");
				assertThat(sr.getProperty(
						RemoteServiceConstants.SERVICE_EXPORTED_INTENTS)).isNull();
				assertThat(sr.getProperty(
						RemoteServiceConstants.SERVICE_EXPORTED_INTENTS_EXTRA)).isNull();
				assertThat(sr.getProperty(
						RemoteServiceConstants.SERVICE_EXPORTED_INTERFACES)).isNull();
				assertThat(sr.getProperty(
						RemoteServiceConstants.SERVICE_EXPORTED_CONFIGS)).isNull();

				// invoke the proxy
				assertThat(client.getA()).isEqualTo("A");

				clientTracker.close();

			}
			// now check on the hosting framework for the service to become available
			// make sure C was not registered, since it wasn't exported
			assertThat(context.getServiceReference(C.class)).as("service C should not have been found as it was not exported").isNull();

			{
				// check for service B
				ServiceTracker<B,B> clientTracker = new ServiceTracker<>(
						context, B.class, null);
				clientTracker.open();

				// the proxy should appear in this framework
				B clientB = clientTracker.waitForService(timeout);
				assertThat(clientB).as("no proxy for service B found!").isNotNull();

				ServiceReference<B> sr = clientTracker.getServiceReference();
				System.out.println(sr);
				assertThat(sr).isNotNull();
				assertThat(sr.getProperty("service.imported")).isNotNull();
				assertThat(sr.getProperty("service.id")).isNotNull();
				assertThat(sr.getProperty("endpoint.id")).isNotNull();
				assertThat(sr.getProperty("service.imported.configs")).isNotNull();
				assertThat(sr.getProperty(".myprop")).as("private properties must not be exported").isNull();
				assertThat(sr.getProperty("implementation")).as("property implementation missing from proxy").isEqualTo("1");
				assertThat(sr.getProperty(
						RemoteServiceConstants.SERVICE_EXPORTED_INTENTS)).isNull();
				assertThat(sr.getProperty(
						RemoteServiceConstants.SERVICE_EXPORTED_INTENTS_EXTRA)).isNull();
				assertThat(sr.getProperty(
						RemoteServiceConstants.SERVICE_EXPORTED_INTERFACES)).isNull();
				assertThat(sr.getProperty(
						RemoteServiceConstants.SERVICE_EXPORTED_CONFIGS)).isNull();

				// invoke the proxy
				assertThat(clientB.getB()).isEqualTo("B");

				clientTracker.close();
			}

		} finally {
			srTestService.unregister();
		}
	}

	private Hashtable<String,Object> registrationTestServiceProperties()
			throws Exception {
		// verify that the server framework is exporting the test packages
		verifyFramework();

		Set<String> supportedConfigTypes = getSupportedConfigTypes();

		// load the external properties file with the config types for the
		// server side service
		Hashtable<String,Object> properties = loadServerTCKProperties();

		// make sure the given config type is in the set of supported config
		// types
		String str = (String) properties
				.get(RemoteServiceConstants.SERVICE_EXPORTED_CONFIGS);

		// I hate not having String.split() available
		StringTokenizer st = new StringTokenizer(str, " ");
		boolean found = false;
		while (st.hasMoreTokens()) {
			if (supportedConfigTypes.contains(st.nextToken())) {
				found = true;
				break;
			}
		}
		assertThat(found).as("the given service.exported.configs type is not supported by the installed Distribution Provider").isTrue();

		// add some properties for testing
		properties.put(RemoteServiceConstants.SERVICE_EXPORTED_INTERFACES, "*");
		properties.put("implementation", "1");
		properties.put(".myprop", "must not be visible on client side");
		processFreePortProperties(properties);
		return properties;
	}

	/**
	 * @throws Exception
	 */
	@Test
	public void testBasicTypesIntent() throws Exception {

		Filter filter = getFramework().getBundleContext().createFilter(
				"(" + DistributionProviderConstants.REMOTE_CONFIGS_SUPPORTED
						+ "=*)");
		ServiceTracker< ? , ? > dpTracker = new ServiceTracker<>(
				getFramework().getBundleContext(), filter, null);
		dpTracker.open();

		Object dp = dpTracker.waitForService(timeout);
		assertThat(dp).as("No DistributionProvider found").isNotNull();
		ServiceReference< ? > dpReference = dpTracker.getServiceReference();
		assertThat(dpReference).isNotNull();
		Object property = dpReference.getProperty(
				DistributionProviderConstants.REMOTE_INTENTS_SUPPORTED);
		assertThat(property).as("No intents supported").isNotNull();

		Collection<String> intents;
		if (property instanceof String) {
			intents = Collections.singleton((String) property);
		} else if (property instanceof String[]) {
			intents = Arrays.asList((String[]) property);
		} else if (property instanceof Collection) {
			@SuppressWarnings("unchecked")
			Collection<String> tmp = (Collection<String>) property;
			intents = tmp;
		} else {
			throw new IllegalArgumentException(
					"Supported intents are not of the correct type "
							+ property.getClass());
		}

		assertThat(intents.contains("osgi.basic")).as("Did not support osgi.basic").isTrue();
	}

	/**
	 * @throws Exception
	 */
	@Test
	public void testBasicTypes() throws Exception {
		Hashtable<String,Object> properties = registrationTestServiceProperties();

		properties.put(RemoteServiceConstants.SERVICE_EXPORTED_INTENTS,
				"osgi.basic");

		// register the service in the server side framework
		ServiceRegistration< ? > srTestService = installTestBundleAndRegisterServiceObject(
				BasicTypesTestServiceImpl.class.getName(), properties,
				BasicTypes.class.getName());

		System.out
				.println("registered basic types test service on server side");

		try {
			// now check on the hosting framework for the service to become
			// available
			ServiceTracker<BasicTypes,BasicTypes> clientTracker = new ServiceTracker<>(
					context, BasicTypes.class, null);
			clientTracker.open();

			// the proxy should appear in this framework
			BasicTypes client = clientTracker.waitForService(timeout);
			assertThat(client).as("no proxy for BasicTypes found!").isNotNull();

			ServiceReference<BasicTypes> sr = clientTracker
					.getServiceReference();

			Object property = sr
					.getProperty(RemoteServiceConstants.SERVICE_INTENTS);
			assertThat(property).as("No intents supported").isNotNull();

			Collection<String> intents;
			if (property instanceof String) {
				intents = Collections.singleton((String) property);
			} else if (property instanceof String[]) {
				intents = Arrays.asList((String[]) property);
			} else if (property instanceof Collection) {
				@SuppressWarnings("unchecked")
				Collection<String> tmp = (Collection<String>) property;
				intents = tmp;
			} else {
				throw new IllegalArgumentException(
						"Supported intents are not of the correct type "
								+ property.getClass());
			}

			assertThat(intents.contains("osgi.basic")).as("Did not pick up the osgi.basic intent").isTrue();

			// booleans
			assertThat(client.getBooleanPrimitive(true)).isFalse();
			assertThat(client.getBoolean(false)).isTrue();
			assertThat(Arrays.equals(new boolean[] {
					true, false
			}, client.getBooleanPrimitiveArray(new boolean[] {
					false, true
			}))).isTrue();
			assertThat(client.getBooleanArray(new Boolean[] {
					true, false
			})).isEqualTo(new Boolean[] {
					false, true
			});
			assertThat(client.getBooleanList(Arrays.asList(false, false, true))).isEqualTo(Arrays.asList(true, true, false));
			assertThat(client.getBooleanSet(Collections.singleton(false))).isEqualTo(Collections.singleton(true));

			// bytes
			assertThat(client.getBytePrimitive((byte) 1)).isEqualTo((byte) -1);
			assertThat(client.getByte((byte) 1)).isEqualTo(Byte.valueOf((byte) -1));
			assertThat(client.getBytePrimitiveArray(new byte[] {
					1, 2, 3
			})).isEqualTo(new byte[] {
					-1, -2, -3
			});
			assertThat(client.getByteArray(new Byte[] {
					1, 2, 3
			})).isEqualTo(new Byte[] {
					(byte) 3, (byte) 2, (byte) 1
			});

			assertThat(client
					.getByteList(Arrays.asList((byte) 3, (byte) 2, (byte) 1))).isEqualTo(Arrays.asList((byte) 1, (byte) 2, (byte) 3));
			assertThat(client.getByteSet(new HashSet<>(
							Arrays.asList((byte) 1, (byte) 2, (byte) 3)))).isEqualTo(new HashSet<>(Arrays.asList((byte) 2, (byte) 4, (byte) 6)));

			// shorts
			assertThat(client.getShortPrimitive((short) 1)).isEqualTo((short) -1);
			assertThat(client.getShort((short) 1)).isEqualTo(Short.valueOf((short) -1));
			assertThat(client.getShortPrimitiveArray(new short[] {
					1, 2, 3
			})).isEqualTo(new short[] {
					-1, -2, -3
			});
			assertThat(client.getShortArray(new Short[] {
					1, 2, 3
			})).isEqualTo(new Short[] {
					(short) 3, (short) 2, (short) 1
			});

			assertThat(client.getShortList(
							Arrays.asList((short) 3, (short) 2, (short) 1))).isEqualTo(Arrays.asList((short) 1, (short) 2, (short) 3));
			assertThat(client.getShortSet(new HashSet<>(
							Arrays.asList((short) 1, (short) 2, (short) 3)))).isEqualTo(new HashSet<>(
							Arrays.asList((short) 2, (short) 4, (short) 6)));

			// chars
			assertThat(client.getCharPrimitive('a')).isEqualTo('b');
			assertThat(client.getChar('a')).isEqualTo(Character.valueOf('b'));
			assertThat(client.getCharacterPrimitiveArray(new char[] {
					'a', 'b', 'c'
			})).isEqualTo(new char[] {
					'b', 'c', 'd'
			});
			assertThat(client.getCharacterArray(new Character[] {
					'a', 'b', 'c'
			})).isEqualTo(new Character[] {
					'c', 'b', 'a'
			});

			assertThat(client.getCharacterList(Arrays.asList('a', 'b', 'c'))).isEqualTo(Arrays.asList('c', 'b', 'a'));
			assertThat(client.getCharacterSet(
							new HashSet<>(Arrays.asList('c', 'b', 'a')))).isEqualTo(new HashSet<>(Arrays.asList('A', 'B', 'C')));

			// ints
			assertThat(client.getIntPrimitive(1)).isEqualTo(-1);
			assertThat(client.getInt(1)).isEqualTo(Integer.valueOf(-1));
			assertThat(client.getIntPrimitiveArray(new int[] {
					1, 2, 3
			})).isEqualTo(new int[] {
					-1, -2, -3
			});
			assertThat(client.getIntegerArray(new Integer[] {
					1, 2, 3
			})).isEqualTo(new Integer[] {
					3, 2, 1
			});

			assertThat(client.getIntegerList(Arrays.asList(3, 2, 1))).isEqualTo(Arrays.asList(1, 2, 3));
			assertThat(client
					.getIntegerSet(new HashSet<>(Arrays.asList(1, 2, 3)))).isEqualTo(new HashSet<>(Arrays.asList(2, 4, 6)));

			// longs
			assertThat(client.getLongPrimitive(1)).isEqualTo(-1);
			assertThat(client.getLong((long) 1)).isEqualTo(Long.valueOf(-1));
			assertThat(client.getLongPrimitiveArray(new long[] {
					1, 2, 3
			})).isEqualTo(new long[] {
					-1, -2, -3
			});
			assertThat(client.getLongArray(new Long[] {
					(long) 1, (long) 2, (long) 3
			})).isEqualTo(new Long[] {
					(long) 3, (long) 2, (long) 1
			});

			assertThat(client
					.getLongList(Arrays.asList((long) 3, (long) 2, (long) 1))).isEqualTo(Arrays.asList((long) 1, (long) 2, (long) 3));
			assertThat(client.getLongSet(new HashSet<>(
							Arrays.asList((long) 1, (long) 2, (long) 3)))).isEqualTo(new HashSet<>(Arrays.asList((long) 2, (long) 4, (long) 6)));

			// floats
			assertThat(client.getFloatPrimitive(1)).isCloseTo(-1f, within(0.01f));
			assertThat(client.getFloat((float) 1)).isEqualTo(Float.valueOf(-1));
			assertThat(client.getFloatPrimitiveArray(new float[] {
					1, 2, 3
			})).containsExactly(new float[] {
					-1, -2, -3
			}, within(0.01f));
			assertThat(client.getFloatArray(new Float[] {
					(float) 1, (float) 2, (float) 3
			})).isEqualTo(new Float[] {
					(float) 3, (float) 2, (float) 1
			});

			assertThat(client.getFloatList(
							Arrays.asList((float) 3, (float) 2, (float) 1))).isEqualTo(Arrays.asList((float) 1, (float) 2, (float) 3));
			assertThat(client.getFloatSet(new HashSet<>(
							Arrays.asList((float) 1, (float) 2, (float) 3)))).isEqualTo(new HashSet<>(
							Arrays.asList((float) 2, (float) 4, (float) 6)));

			// doubles
			assertThat(client.getDoublePrimitive(1)).isCloseTo(-1d, within(0.01d));
			assertThat(client.getDouble((double) 1)).isEqualTo(Double.valueOf(-1));
			assertThat(client.getDoublePrimitiveArray(new double[] {
					1, 2, 3
			})).containsExactly(new double[] {
					-1, -2, -3
			}, within(0.01d));
			assertThat(client.getDoubleArray(new Double[] {
					(double) 1, (double) 2, (double) 3
			})).isEqualTo(new Double[] {
					(double) 3, (double) 2, (double) 1
			});

			assertThat(client.getDoubleList(
							Arrays.asList((double) 3, (double) 2, (double) 1))).isEqualTo(Arrays.asList((double) 1, (double) 2, (double) 3));
			assertThat(client.getDoubleSet(new HashSet<>(Arrays.asList((double) 1,
							(double) 2, (double) 3)))).isEqualTo(new HashSet<>(
							Arrays.asList((double) 2, (double) 4, (double) 6)));

			// Strings
			assertThat(client.getString("bang")).isEqualTo("BANG");
			assertThat(client.getStringArray(new String[] {
					"fee", "fi", "fo", "fum"
			})).isEqualTo(new String[] {
					"fum", "fo", "fi", "fee"
			});

			assertThat(client
					.getStringList(Arrays.asList("fee", "fi", "fo", "fum"))).isEqualTo(Arrays.asList("fum", "fo", "fi", "fee"));
			assertThat(client.getStringSet(new HashSet<>(
							Arrays.asList("fee", "fi", "fo", "fum")))).isEqualTo(new HashSet<>(Arrays.asList("FEE", "FI", "FO", "FUM")));

			// Versions
			assertThat(client.getVersion(Version.parseVersion("1.2.3"))).isEqualTo(Version.parseVersion("2.3.4"));
			assertThat(client.getVersionArray(new Version[] {
					Version.parseVersion("1.2.3"), Version.parseVersion("4.5.6")
			})).isEqualTo(new Version[] {
					Version.parseVersion("4.5.6"), Version.parseVersion("1.2.3")
			});

			assertThat(client.getVersionList(
							Arrays.asList(Version.parseVersion("1.2.3"),
									Version.parseVersion("4.5.6")))).isEqualTo(Arrays.asList(Version.parseVersion("4.5.6"),
							Version.parseVersion("1.2.3")));
			assertThat(client.getVersionSet(new HashSet<>(
							Arrays.asList(Version.parseVersion("1.2.3"),
									Version.parseVersion("4.5.6"))))).isEqualTo(new HashSet<>(Arrays.asList(Version.parseVersion("2.3.4"),
							Version.parseVersion("5.6.7"))));

			// enums
			assertThat(client.getEnum(TimeUnit.NANOSECONDS)).isEqualTo(TimeUnit.MICROSECONDS);
			assertThat(client.getEnumArray(new TimeUnit[] {
					TimeUnit.DAYS, TimeUnit.HOURS
			})).isEqualTo(new TimeUnit[] {
					TimeUnit.HOURS, TimeUnit.DAYS
			});

			assertThat(client
					.getEnumList(Arrays.asList(TimeUnit.DAYS, TimeUnit.HOURS))).isEqualTo(Arrays.asList(TimeUnit.HOURS, TimeUnit.DAYS));
			assertThat(client.getEnumSet(new HashSet<>(Arrays
							.asList(TimeUnit.MILLISECONDS, TimeUnit.MINUTES)))).isEqualTo(new HashSet<>(
							Arrays.asList(TimeUnit.HOURS, TimeUnit.SECONDS)));

			// dtos
			DTOType typeA = new DTOType();
			typeA.value = "a";
			DTOType typeB = new DTOType();
			typeB.value = "b";
			DTOType typeC = new DTOType();
			typeC.value = "c";

			assertThat(client.getDTO(typeA).value).isEqualTo("A");

			DTOType[] array = client.getDTOTypeArray(new DTOType[] {
					typeA, typeB, typeC
			});
			assertThat(array).hasSize(3);
			assertThat(array[0].value).isEqualTo("c");
			assertThat(array[1].value).isEqualTo("b");
			assertThat(array[2].value).isEqualTo("a");

			List<DTOType> list = client
					.getDTOTypeList(Arrays.asList(typeA, typeB, typeC));
			assertThat(list).hasSize(3);
			assertThat(list.get(0).value).isEqualTo("c");
			assertThat(list.get(1).value).isEqualTo("b");
			assertThat(list.get(2).value).isEqualTo("a");

			Set<String> values = new HashSet<>();
			for (DTOType dtoType : client.getDTOTypeSet(
					new HashSet<>(Arrays.asList(typeA, typeB, typeC)))) {
				values.add(dtoType.value);
			}

			assertThat(values).isEqualTo(new HashSet<>(Arrays.asList("A", "B", "C")));

			
			Map<String, Integer> map = new HashMap<>();
			map.put("foo", 1);
			map.put("bar", 2);
			
			client.populateMap(map);
			
			assertThat(client.getMap()).isEqualTo(map);
			
			clientTracker.close();

		} finally {
			srTestService.unregister();
		}
	}

	/**
	 * @throws Exception
	 */
	@Test
	public void testBasicTimeout() throws Exception {
		Hashtable<String,Object> properties = registrationTestServiceProperties();

		properties.put(RemoteServiceConstants.SERVICE_EXPORTED_INTENTS,
				"osgi.basic");

		properties.put("osgi.basic.timeout", "3000");

		// register the service in the server side framework
		ServiceRegistration< ? > srTestService = installTestBundleAndRegisterServiceObject(
				SlowServiceImpl.class.getName(), properties,
				SlowService.class.getName());

		System.out.println("registered slow test service on server side");

		try {
			// now check on the hosting framework for the service to become
			// available
			ServiceTracker<SlowService,SlowService> clientTracker = new ServiceTracker<>(
					context, SlowService.class, null);
			clientTracker.open();

			// the proxy should appear in this framework
			SlowService client = clientTracker.waitForService(timeout);
			assertThat(client).as("no proxy for SlowService found!").isNotNull();

			ServiceReference<SlowService> sr = clientTracker
					.getServiceReference();

			Object property = sr
					.getProperty(RemoteServiceConstants.SERVICE_INTENTS);
			assertThat(property).as("No intents supported").isNotNull();

			Collection<String> intents;
			if (property instanceof String) {
				intents = Collections.singleton((String) property);
			} else if (property instanceof String[]) {
				intents = Arrays.asList((String[]) property);
			} else if (property instanceof Collection) {
				@SuppressWarnings("unchecked")
				Collection<String> tmp = (Collection<String>) property;
				intents = tmp;
			} else {
				throw new IllegalArgumentException(
						"Supported intents are not of the correct type "
								+ property.getClass());
			}

			assertThat(intents.contains("osgi.basic")).as("Did not pick up the osgi.basic intent").isTrue();

			// should work fine
			assertThat(client.goSlow(1000)).isEqualTo("ready");

			try {
				client.goSlow(5000);
				fail("Invocation should have timed out!");
			} catch (ServiceException se) {
				assertThat(se.getType()).isEqualTo(ServiceException.REMOTE);
			}

			clientTracker.close();

		} finally {
			srTestService.unregister();
		}
	}

	/**
	 * @throws Exception
	 */
	@Test
	public void testAsyncTypesIntent() throws Exception {

		Filter filter = getFramework().getBundleContext().createFilter(
				"(" + DistributionProviderConstants.REMOTE_CONFIGS_SUPPORTED
						+ "=*)");
		ServiceTracker< ? , ? > dpTracker = new ServiceTracker<>(
				getFramework().getBundleContext(), filter, null);
		dpTracker.open();

		Object dp = dpTracker.waitForService(timeout);
		assertThat(dp).as("No DistributionProvider found").isNotNull();
		ServiceReference< ? > dpReference = dpTracker.getServiceReference();
		assertThat(dpReference).isNotNull();
		Object property = dpReference.getProperty(
				DistributionProviderConstants.REMOTE_INTENTS_SUPPORTED);
		assertThat(property).as("No intents supported").isNotNull();

		Collection<String> intents;
		if (property instanceof String) {
			intents = Collections.singleton((String) property);
		} else if (property instanceof String[]) {
			intents = Arrays.asList((String[]) property);
		} else if (property instanceof Collection) {
			@SuppressWarnings("unchecked")
			Collection<String> tmp = (Collection<String>) property;
			intents = tmp;
		} else {
			throw new IllegalArgumentException(
					"Supported intents are not of the correct type "
							+ property.getClass());
		}

		assertThat(intents.contains("osgi.async")).as("Did not support osgi.async").isTrue();
	}

	/**
	 * @throws Exception
	 */
	@Test
	public void testAsyncTypes() throws Exception {
		Hashtable<String,Object> properties = registrationTestServiceProperties();

		properties.put(RemoteServiceConstants.SERVICE_EXPORTED_INTENTS,
				"osgi.async");

		// register the service in the server side framework
		ServiceRegistration< ? > srTestService = installTestBundleAndRegisterServiceObject(
				AsyncTypesImpl.class.getName(), properties,
				AsyncTypes.class.getName());

		System.out
				.println("registered basic types test service on server side");

		try {
			// now check on the hosting framework for the service to become
			// available
			ServiceTracker<AsyncTypes,AsyncTypes> clientTracker = new ServiceTracker<>(
					context, AsyncTypes.class, null);
			clientTracker.open();

			// the proxy should appear in this framework
			AsyncTypes client = clientTracker.waitForService(timeout);
			assertThat(client).as("no proxy for AsyncTypes found!").isNotNull();

			ServiceReference<AsyncTypes> sr = clientTracker
					.getServiceReference();

			Object property = sr
					.getProperty(RemoteServiceConstants.SERVICE_INTENTS);
			assertThat(property).as("No intents supported").isNotNull();

			Collection<String> intents;
			if (property instanceof String) {
				intents = Collections.singleton((String) property);
			} else if (property instanceof String[]) {
				intents = Arrays.asList((String[]) property);
			} else if (property instanceof Collection) {
				@SuppressWarnings("unchecked")
				Collection<String> tmp = (Collection<String>) property;
				intents = tmp;
			} else {
				throw new IllegalArgumentException(
						"Supported intents are not of the correct type "
								+ property.getClass());
			}

			assertThat(intents.contains("osgi.async")).as("Did not pick up the osgi.async intent").isTrue();

			final Semaphore s = new Semaphore(0);

			// Future
			Future<String> f = client.getFuture(1000);
			assertThat(f.isDone()).isFalse();
			assertThat(f.get(2, TimeUnit.SECONDS)).isEqualTo(AsyncTypes.RESULT);

			// Promise
			Promise<String> p = client.getPromise(1000);
			p.filter(new Predicate<String>() {
				@Override
				public boolean test(String x) {
					return AsyncTypes.RESULT.equals(x);
				}
			}).then(new Success<String,Object>() {
				@Override
				public Promise<Object> call(Promise<String> x)
						throws Exception {
					s.release();
					return null;
				}
			});
			assertThat(s.tryAcquire()).isFalse();
			assertThat(s.tryAcquire(2, TimeUnit.SECONDS)).isTrue();

			clientTracker.close();

		} finally {
			srTestService.unregister();
		}
	}

	/**
	 * @throws Exception
	 */
	@Test
	public void testAsyncJava8Types() throws Exception {
		try {
			Hashtable<String,Object> properties = registrationTestServiceProperties();

			properties.put(RemoteServiceConstants.SERVICE_EXPORTED_INTENTS,
					"osgi.async");

			// register the service in the server side framework
			ServiceRegistration< ? > srTestService = installTestBundleAndRegisterServiceObject(
					AsyncJava8TypesImpl.class.getName(), properties,
					AsyncJava8Types.class.getName());

			System.out.println(
					"registered basic types test service on server side");

			try {
				// now check on the hosting framework for the service to become
				// available
				ServiceTracker<AsyncJava8Types,AsyncJava8Types> clientTracker = new ServiceTracker<>(
						context, AsyncJava8Types.class, null);
				clientTracker.open();

				// the proxy should appear in this framework
				AsyncJava8Types client = clientTracker.waitForService(timeout);
				assertThat(client).as("no proxy for AsyncJava8Types found!").isNotNull();

				ServiceReference<AsyncJava8Types> sr = clientTracker
						.getServiceReference();

				Object property = sr
						.getProperty(RemoteServiceConstants.SERVICE_INTENTS);
				assertThat(property).as("No intents supported").isNotNull();

				Collection<String> intents;
				if (property instanceof String) {
					intents = Collections.singleton((String) property);
				} else if (property instanceof String[]) {
					intents = Arrays.asList((String[]) property);
				} else if (property instanceof Collection) {
					@SuppressWarnings("unchecked")
					Collection<String> tmp = (Collection<String>) property;
					intents = tmp;
				} else {
					throw new IllegalArgumentException(
							"Supported intents are not of the correct type "
									+ property.getClass());
				}

				assertThat(intents.contains("osgi.async")).as("Did not pick up the osgi.async intent").isTrue();

				final Semaphore s = new Semaphore(0);

				// CompletableFuture
				CompletableFuture<String> cf = client
						.getCompletableFuture(1000);
				cf.thenRun(new Runnable() {
					@Override
					public void run() {
						s.release();
					}
				});
				assertThat(s.tryAcquire()).isFalse();
				assertThat(s.tryAcquire(2, TimeUnit.SECONDS)).isTrue();
				assertThat(cf.get()).isEqualTo(AsyncJava8Types.RESULT);

				// CompletionStage
				CompletionStage<String> cs = client.getCompletionStage(1000);
				cs.thenAccept(new Consumer<String>() {
					@Override
					public void accept(String r) {
						if (AsyncJava8Types.RESULT.equals(r)) {
							s.release();
						} else {
							System.out
									.println("Received the wrong result " + r);
						}
					}
				});
				assertThat(s.tryAcquire()).isFalse();
				assertThat(s.tryAcquire(2, TimeUnit.SECONDS)).isTrue();

				clientTracker.close();

			} finally {
				srTestService.unregister();
			}
		} catch (NoClassDefFoundError e) {
			// must be less than java 8.
		}
	}

	private void processFreePortProperties(
			Hashtable<String,Object> properties) {
        String freePort = getFreePort();
		for (Iterator<Entry<String,Object>> it = properties.entrySet()
				.iterator(); it.hasNext();) {
			Entry<String,Object> entry = it.next();
            if (entry.getValue().toString().trim().equals(FREE_PORT)) {
                entry.setValue(freePort);
            }
        }
    }

    private String getFreePort() {
        try {
            ServerSocket ss = new ServerSocket(0);
            String port = "" + ss.getLocalPort();
            ss.close();
            System.out.println("Found free port " + port);
            return port;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
	 * @return
	 */
	private Hashtable<String,Object> loadServerTCKProperties() {
		String serverconfig = getProperty("org.osgi.test.cases.remoteservices.serverconfig");
		assertThat(serverconfig).as("did not find org.osgi.test.cases.remoteservices.serverconfig system property").isNotNull();
		Hashtable<String,Object> properties = new Hashtable<>();

		for (StringTokenizer tok = new StringTokenizer(serverconfig, ","); tok
				.hasMoreTokens();) {
			String propertyName = tok.nextToken();
			String value = getProperty(propertyName);
			assertThat(value).as("system property not found: " + propertyName).isNotNull();
			properties.put(propertyName, value);
		}

		return properties;
	}

	private Set<String> getSupportedConfigTypes() throws Exception {
		// make sure there is a Distribution Provider installed in the framework
//		Filter filter = getFramework().getBundleContext().createFilter("(&(objectClass=*)(" +
//				DistributionProviderConstants.REMOTE_CONFIGS_SUPPORTED + "=*))");
		Filter filter = getFramework().getBundleContext().createFilter("(" +
				DistributionProviderConstants.REMOTE_CONFIGS_SUPPORTED + "=*)");
		ServiceTracker< ? , ? > dpTracker = new ServiceTracker<>(
				getFramework().getBundleContext(), filter, null);
		dpTracker.open();

		Object dp = dpTracker.waitForService(timeout);
		assertThat(dp).as("No DistributionProvider found").isNotNull();
		ServiceReference< ? > dpReference = dpTracker.getServiceReference();
		assertThat(dpReference).isNotNull();
		assertThat(dpReference.getProperty(DistributionProviderConstants.REMOTE_INTENTS_SUPPORTED)).isNotNull();

		Set<String> supportedConfigTypes = new HashSet<>(); // collect all
															// supported config
															// types

		Object configProperty = dpReference.getProperty(DistributionProviderConstants.REMOTE_CONFIGS_SUPPORTED);
		if (configProperty instanceof String) {
			StringTokenizer st = new StringTokenizer((String)configProperty, " ");
			while (st.hasMoreTokens()) {
				supportedConfigTypes.add(st.nextToken());
			}
		} else if (configProperty instanceof Collection) {
			@SuppressWarnings("unchecked")
			Collection<String> col = (Collection<String>) configProperty;
			for (Iterator<String> it = col.iterator(); it.hasNext();) {
				supportedConfigTypes.add(it.next());
			}
		} else { // assume String[]
			String[] arr = (String[]) configProperty;
			for (int i=0; i<arr.length; i++) {
				supportedConfigTypes.add(arr[i]);
			}
		}
		dpTracker.close();

		return supportedConfigTypes;
	}

	/**
	 * Verifies the server side framework that it exports the test packages for the interface
	 * used by the test service.
	 * @throws Exception
	 */
	private void verifyFramework() throws Exception {
		Framework f = getFramework();
		BundleWiring wiring = f.getBundleContext().getBundle()
				.adapt(BundleWiring.class);
		assertThat(wiring).as("Framework is not supplying a BundleWiring for the system bundle").isNotNull();
		List<BundleCapability> exportedPkgs = wiring
				.getCapabilities(BundleRevision.PACKAGE_NAMESPACE);

		for (BundleCapability exportedPkg : exportedPkgs) {
			String name = (String) exportedPkg.getAttributes().get(
					BundleRevision.PACKAGE_NAMESPACE);
			if (name.equals(ORG_OSGI_TEST_CASES_REMOTESERVICES_COMMON)) {
				return;
			}
		}
		fail("Framework System Bundle is not exporting package "
				+ ORG_OSGI_TEST_CASES_REMOTESERVICES_COMMON);
	}

	private ServiceRegistration< ? > installTestBundleAndRegisterServiceObject(
			String serviceClassName, Dictionary<String,Object> properties,
			String... ifaces) throws Exception {
		Bundle bundle = getFramework().getBundleContext()
				.installBundle("test-bundle", createTestBundle());
		bundle.start();

		Object object = bundle.loadClass(serviceClassName)
				.getConstructor()
				.newInstance();

		return bundle.getBundleContext().registerService(ifaces, object,
				properties);
	}

	private InputStream createTestBundle() throws IOException {
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
			Manifest manifest = new Manifest();
			Attributes mainAttributes = manifest.getMainAttributes();

			mainAttributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
			mainAttributes.put(new Attributes.Name(BUNDLE_MANIFESTVERSION),
					"2");
			mainAttributes.put(new Attributes.Name(BUNDLE_SYMBOLICNAME),
					ORG_OSGI_TEST_CASES_REMOTESERVICES_COMMON + ".bundle");
			mainAttributes.put(new Attributes.Name(IMPORT_PACKAGE),
					"org.osgi.util.promise;version=\"[1,2)\",org.osgi.framework");

			try (JarOutputStream jos = new JarOutputStream(baos, manifest)) {
				Bundle bundle = context.getBundle();
				writePackage(jos, "/org/osgi/test/cases/remoteservices/common",
						bundle);
				writePackage(jos, "/org/osgi/test/cases/remoteservices/impl",
						bundle);
			}
			return new ByteArrayInputStream(baos.toByteArray());
		}
	}

	private void writePackage(JarOutputStream jos, String path, Bundle bundle)
			throws IOException {
		Enumeration<String> paths = bundle.getEntryPaths(path);
		while (paths.hasMoreElements()) {
			String name = paths.nextElement();
			jos.putNextEntry(new ZipEntry(name));
			try (InputStream is = bundle.getResource(name).openStream()) {
				byte[] b = new byte[4096];
				int i;
				while ((i = is.read(b)) != -1) {
					jos.write(b, 0, i);
				}
				jos.closeEntry();
			}
		}
	}
}
