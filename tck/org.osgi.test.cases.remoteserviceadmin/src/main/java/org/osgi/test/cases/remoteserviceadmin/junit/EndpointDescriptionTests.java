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
import static org.osgi.framework.Constants.OBJECTCLASS;
import static org.osgi.framework.Constants.SERVICE_EXPORTED_CONFIGS;
import static org.osgi.framework.Constants.SERVICE_ID;
import static org.osgi.framework.Constants.SERVICE_IMPORTED_CONFIGS;
import static org.osgi.framework.Constants.SERVICE_INTENTS;
import static org.osgi.service.remoteserviceadmin.RemoteConstants.ENDPOINT_FRAMEWORK_UUID;
import static org.osgi.service.remoteserviceadmin.RemoteConstants.ENDPOINT_ID;
import static org.osgi.service.remoteserviceadmin.RemoteConstants.ENDPOINT_PACKAGE_VERSION_;
import static org.osgi.service.remoteserviceadmin.RemoteConstants.ENDPOINT_SERVICE_ID;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;
import org.osgi.service.remoteserviceadmin.EndpointDescription;


public class EndpointDescriptionTests {

	@Test
	public void testFromMap() {
		EndpointDescription ed;
		Map<String, Object> props;

		props = new HashMap<String, Object>();
		try {
			ed = newEndpointDescription(props);
			fail("missing required properties");
		}
		catch (IllegalArgumentException e) {
			// expected
		}

		String testUUID = "testUUID";
		String someId = "someId";
		props.put(ENDPOINT_ID, someId);
		try {
			ed = newEndpointDescription(props);
			fail("missing required properties");
		}
		catch (IllegalArgumentException e) {
			// expected
		}

		props.put(OBJECTCLASS, new String[] {"foo"});
		try {
			ed = newEndpointDescription(props);
			fail("missing required properties");
		}
		catch (IllegalArgumentException e) {
			// expected
		}

		props.put(SERVICE_IMPORTED_CONFIGS, "config");
		ed = newEndpointDescription(props);
		assertThat(ed.getId()).as("wrong remote id").isEqualTo(someId);

		assertThat(ed.getServiceId()).as("remote service id should be zero").isZero();
		assertThat(ed
				.getFrameworkUUID()).as("remote framework uuid should be null").isNull();

		props.put(ENDPOINT_FRAMEWORK_UUID, testUUID);
		ed = newEndpointDescription(props);
		assertThat(ed.getFrameworkUUID()).as("wrong uuid").isEqualTo(testUUID);

		props.put(ENDPOINT_SERVICE_ID, "not a valid long");
		try {
			ed = newEndpointDescription(props);
			fail("invalid endpoint.id property");
		}
		catch (IllegalArgumentException e) {
			// expected
		}

		props.put(ENDPOINT_SERVICE_ID, new Object());
		try {
			ed = newEndpointDescription(props);
			fail("invalid endpoint.id property");
		}
		catch (IllegalArgumentException e) {
			// expected
		}

		Long someServiceId = Long.valueOf(12l);
		props.put(ENDPOINT_SERVICE_ID, someServiceId);
		ed = newEndpointDescription(props);
		assertThat(ed.getServiceId()).as("wrong id").isEqualTo(someServiceId.longValue());

		props.put(OBJECTCLASS, "not a String[]");
		try {
			ed = newEndpointDescription(props);
			fail("invalid objectClass property");
		}
		catch (IllegalArgumentException e) {
			// expected
		}

		props.put(OBJECTCLASS, new String[] {});
		try {
			ed = newEndpointDescription(props);
			fail("invalid objectClass property");
		}
		catch (IllegalArgumentException e) {
			// expected
		}

		String[] objectClass = new String[] {"com.acme.Foo", "com.acme.FOO"};
		props.put(OBJECTCLASS, objectClass);
		ed = newEndpointDescription(props);
		testMutability(ed);
		List<String> interfs = ed.getInterfaces();
		assertThat(interfs).as("should have 2 interfaces").hasSize(2);
		assertThat(interfs.get(0)).as("first interface wrong").isEqualTo(objectClass[0]);
		assertThat(interfs.get(1)).as("second interface wrong").isEqualTo(objectClass[1]);
		assertThat(ed
				.getPackageVersion(getPackageName(objectClass[0]))).as("package version wrong").isEqualTo(Version.emptyVersion);
		assertThat(ed
				.getPackageVersion(getPackageName(objectClass[1]))).as("package version wrong").isEqualTo(Version.emptyVersion);
		assertThat(ed
				.getPackageVersion("xxx")).as("package version wrong").isEqualTo(Version.emptyVersion);

		props.put(ENDPOINT_PACKAGE_VERSION_ + getPackageName(objectClass[0]),
				"bad version");
		try {
			ed = newEndpointDescription(props);
			fail("invalid package version property");
		}
		catch (IllegalArgumentException e) {
			// expected
		}

		Version someVersion = new Version(1, 2, 3, "somequalifier");
		props.put(ENDPOINT_PACKAGE_VERSION_ + getPackageName(objectClass[0]),
				someVersion.toString());
		ed = newEndpointDescription(props);
		assertThat(ed
				.getPackageVersion(getPackageName(objectClass[0]))).as("package version wrong").isEqualTo(someVersion);

		props
				.remove(ENDPOINT_PACKAGE_VERSION_
						+ getPackageName(objectClass[0]));
		props.put(ENDPOINT_PACKAGE_VERSION_ + getPackageName(objectClass[1]),
				"bad version");
		try {
			ed = newEndpointDescription(props);
			fail("invalid package version property");
		}
		catch (IllegalArgumentException e) {
			// expected
		}

		props.put(ENDPOINT_PACKAGE_VERSION_ + getPackageName(objectClass[1]),
				someVersion.toString());
		ed = newEndpointDescription(props);
		assertThat(ed
				.getPackageVersion(getPackageName(objectClass[1]))).as("package version wrong").isEqualTo(someVersion);

	}

	@Test
	public void testBadMap() {
		@SuppressWarnings("unused")
		EndpointDescription ed;
		Map<String, Object> props = new HashMap<String, Object>();
		String testUUID = "testUUID";
		String someId = "someId";
		props.put(ENDPOINT_ID, someId);
		props.put(OBJECTCLASS, new String[] {"foo"});
		props.put(SERVICE_IMPORTED_CONFIGS, "config");
		BundleContext testContext = newMockBundleContext(testUUID);
		Bundle testBundle = newMockBundle(1, "testName", "testLocation",
				testContext);
		Map<String, Object> serviceProps = new TreeMap<String, Object>(
				String.CASE_INSENSITIVE_ORDER);
		ServiceReference< ? > ref = newMockServiceReference(testBundle,
				serviceProps);

		props.put("foo", "bar");
		props.put("Foo", "bar");

		try {
			ed = newEndpointDescription(props);
			fail("duplicate keys");
		}
		catch (IllegalArgumentException e) {
			// expected
		}

		try {
			ed = newEndpointDescription(ref, props);
			fail("duplicate keys");
		}
		catch (IllegalArgumentException e) {
			// expected
		}

		props.remove("Foo");
		props.put("foo", "bar");
		@SuppressWarnings({
				"rawtypes", "unchecked"
		})
		Map<Object, Object> bad = (Map) props;
		bad.put(this, "bar");

		try {
			ed = newEndpointDescription(props);
			fail("non string key");
		}
		catch (IllegalArgumentException e) {
			// expected
		}
		try {
			ed = newEndpointDescription(ref, props);
			fail("non string key");
		}
		catch (IllegalArgumentException e) {
			// expected
		}
	}

	@Test
	public void testFromReference() {
		EndpointDescription ed;
		Map<String, Object> props = new HashMap<String, Object>();
		String testUUID = "testUUID";
		BundleContext testContext = newMockBundleContext(testUUID);
		Bundle testBundle = newMockBundle(1, "testName", "testLocation",
				testContext);
		Map<String, Object> serviceProps = new TreeMap<String, Object>(
				String.CASE_INSENSITIVE_ORDER);
		ServiceReference< ? > ref = newMockServiceReference(testBundle,
				serviceProps);

		try {
			ed = newEndpointDescription(ref, null);
			fail("missing required properties");
		}
		catch (IllegalArgumentException e) {
			// expected
		}

		try {
			ed = newEndpointDescription(ref, props);
			fail("missing required properties");
		}
		catch (IllegalArgumentException e) {
			// expected
		}

		String someId = "someId";
		props.put(ENDPOINT_ID, someId);
		try {
			ed = newEndpointDescription(ref, null);
			fail("missing required properties");
		}
		catch (IllegalArgumentException e) {
			// expected
		}

		serviceProps.put(ENDPOINT_ID, someId);
		try {
			ed = newEndpointDescription(ref, props);
			fail("missing required properties");
		}
		catch (IllegalArgumentException e) {
			// expected
		}

		props.put(OBJECTCLASS, new String[] {"foo"});
		try {
			ed = newEndpointDescription(ref, null);
			fail("missing required properties");
		}
		catch (IllegalArgumentException e) {
			// expected
		}

		serviceProps.put(OBJECTCLASS, new String[] {"foo"});
		try {
			ed = newEndpointDescription(ref, props);
			fail("missing required properties");
		}
		catch (IllegalArgumentException e) {
			// expected
		}

		serviceProps.put(SERVICE_IMPORTED_CONFIGS, "config");
		ed = newEndpointDescription(ref, null);
		assertThat(ed.getId()).as("wrong remote id").isEqualTo(someId);

		assertThat(ed.getServiceId()).as("remote service id should be zero").isZero();
		assertThat(ed.getFrameworkUUID()).as("wrong uuid").isEqualTo(testUUID);

		props.put(ENDPOINT_FRAMEWORK_UUID, "newUUID");
		props.put(SERVICE_IMPORTED_CONFIGS, "config");
		ed = newEndpointDescription(props);
		assertThat(ed.getFrameworkUUID()).as("wrong uuid").isEqualTo("newUUID");
		props.remove(ENDPOINT_FRAMEWORK_UUID);

		props.put(ENDPOINT_SERVICE_ID, "not a valid long");
		try {
			ed = newEndpointDescription(ref, props);
			fail("invalid endpoint.id property");
		}
		catch (IllegalArgumentException e) {
			// expected
		}

		props.put(ENDPOINT_SERVICE_ID, new Object());
		try {
			ed = newEndpointDescription(ref, props);
			fail("invalid endpoint.id property");
		}
		catch (IllegalArgumentException e) {
			// expected
		}

		Long someServiceId = Long.valueOf(12l);
		props.remove(ENDPOINT_SERVICE_ID);
		serviceProps.put(SERVICE_ID, someServiceId);
		ed = newEndpointDescription(ref, props);
		assertThat(ed.getServiceId()).as("wrong id").isEqualTo(someServiceId.longValue());

		String[] objectClass = new String[] {"com.acme.Foo", "com.acme.FOO"};
		props.put(OBJECTCLASS, objectClass);
		serviceProps.put(OBJECTCLASS, "not a String[]");
		ed = newEndpointDescription(ref, props);

		serviceProps.put("OBJECTCLASS", objectClass);
		props.put(OBJECTCLASS, "not a String[]");
		try {
			ed = newEndpointDescription(ref, props);
			fail("invalid objectClass property");
		}
		catch (IllegalArgumentException e) {
			// expected
		}

		props.remove(OBJECTCLASS);
		ed = newEndpointDescription(ref, props);
		testMutability(ed);
		List<String> interfs = ed.getInterfaces();
		assertThat(interfs).as("should have 2 interfaces").hasSize(2);
		assertThat(interfs.get(0)).as("first interface wrong").isEqualTo(objectClass[0]);
		assertThat(interfs.get(1)).as("second interface wrong").isEqualTo(objectClass[1]);
		assertThat(ed
				.getPackageVersion(getPackageName(objectClass[0]))).as("package version wrong").isEqualTo(Version.emptyVersion);
		assertThat(ed
				.getPackageVersion(getPackageName(objectClass[1]))).as("package version wrong").isEqualTo(Version.emptyVersion);
		assertThat(ed
				.getPackageVersion("xxx")).as("package version wrong").isEqualTo(Version.emptyVersion);

		serviceProps.put(ENDPOINT_PACKAGE_VERSION_
				+ getPackageName(objectClass[0]), "bad version");
		try {
			ed = newEndpointDescription(ref, props);
			fail("invalid package version property");
		}
		catch (IllegalArgumentException e) {
			// expected
		}

		Version someVersion = new Version(1, 2, 3, "somequalifier");
		serviceProps.put(ENDPOINT_PACKAGE_VERSION_
				+ getPackageName(objectClass[0]), someVersion.toString());
		ed = newEndpointDescription(ref, props);
		assertThat(ed
				.getPackageVersion(getPackageName(objectClass[0]))).as("package version wrong").isEqualTo(someVersion);

		serviceProps.put(ENDPOINT_PACKAGE_VERSION_
				+ getPackageName(objectClass[1]), "bad version");
		try {
			ed = newEndpointDescription(ref, props);
			fail("invalid package version property");
		}
		catch (IllegalArgumentException e) {
			// expected
		}
	}

	@Test
	public void testIntents() {
		EndpointDescription ed;
		Map<String, Object> props = new HashMap<String, Object>();
		String someId = "someId";
		props.put(ENDPOINT_ID, someId);
		props.put(OBJECTCLASS, new String[] {"foo"});
		props.put(SERVICE_IMPORTED_CONFIGS, "config");
		String testUUID = "testUUID";
		BundleContext testContext = newMockBundleContext(testUUID);
		Bundle testBundle = newMockBundle(1, "testName", "testLocation",
				testContext);
		Map<String, Object> serviceProps = new TreeMap<String, Object>(
				String.CASE_INSENSITIVE_ORDER);
		serviceProps.put(ENDPOINT_ID, someId);
		serviceProps.put(OBJECTCLASS, new String[] {"foo"});
		serviceProps.put(SERVICE_IMPORTED_CONFIGS, "config");
		ServiceReference< ? > ref = newMockServiceReference(testBundle,
				serviceProps);
		List<String> intents;

		ed = newEndpointDescription(props);
		intents = ed.getIntents();
		assertThat(intents).as("intents null").isNotNull();
		assertThat(intents).as("intents not empty").isEmpty();
		testListMutability(intents);

		ed = newEndpointDescription(ref, null);
		intents = ed.getIntents();
		assertThat(intents).as("intents null").isNotNull();
		assertThat(intents).as("intents not empty").isEmpty();
		testListMutability(intents);

		props.put(SERVICE_INTENTS, this);
		serviceProps.put(SERVICE_INTENTS, this);

		ed = newEndpointDescription(props);
		intents = ed.getIntents();
		assertThat(intents).as("intents null").isNotNull();
		assertThat(intents).as("intents not empty").isEmpty();
		testListMutability(intents);

		ed = newEndpointDescription(ref, null);
		intents = ed.getIntents();
		assertThat(intents).as("intents null").isNotNull();
		assertThat(intents).as("intents not empty").isEmpty();
		testListMutability(intents);

		String scalarIntent = "some.intent";
		props.put(SERVICE_INTENTS, scalarIntent);
		serviceProps.put(SERVICE_INTENTS, scalarIntent);

		ed = newEndpointDescription(props);
		intents = ed.getIntents();
		assertThat(intents).as("intents null").isNotNull();
		assertThat(intents.isEmpty()).as("intents not empty").isFalse();
		assertThat(intents.size()).as("only one element").isEqualTo(1);
		assertThat(intents.get(0)).as("wrong intent value").isEqualTo(scalarIntent);
		testListMutability(intents);

		ed = newEndpointDescription(ref, null);
		intents = ed.getIntents();
		assertThat(intents).as("intents null").isNotNull();
		assertThat(intents.isEmpty()).as("intents not empty").isFalse();
		assertThat(intents.size()).as("only one element").isEqualTo(1);
		assertThat(intents.get(0)).as("wrong intent value").isEqualTo(scalarIntent);
		testListMutability(intents);

		String[] arrayIntents = new String[] {"some.intent1", "some.intent2"};
		props.put(SERVICE_INTENTS, arrayIntents);
		serviceProps.put(SERVICE_INTENTS, arrayIntents);

		ed = newEndpointDescription(props);
		intents = ed.getIntents();
		assertThat(intents).as("intents null").isNotNull();
		assertThat(intents.isEmpty()).as("intents not empty").isFalse();
		assertThat(intents
				.size()).as("wrong number of elements").isEqualTo(arrayIntents.length);
		for (int i = 0; i < arrayIntents.length; i++) {
			assertThat(intents.get(i)).as("wrong intent value").isEqualTo(arrayIntents[i]);
		}
		testListMutability(intents);

		ed = newEndpointDescription(ref, null);
		intents = ed.getIntents();
		assertThat(intents).as("intents null").isNotNull();
		assertThat(intents.isEmpty()).as("intents not empty").isFalse();
		assertThat(intents
				.size()).as("wrong number of elements").isEqualTo(arrayIntents.length);
		for (int i = 0; i < arrayIntents.length; i++) {
			assertThat(intents.get(i)).as("wrong intent value").isEqualTo(arrayIntents[i]);
		}
		testListMutability(intents);

		List<String> listIntents = Arrays.asList("some.intent3",
				"some.intent4", "some.intent5");
		props.put(SERVICE_INTENTS, listIntents);
		serviceProps.put(SERVICE_INTENTS, listIntents);

		ed = newEndpointDescription(props);
		intents = ed.getIntents();
		assertThat(intents).as("intents null").isNotNull();
		assertThat(intents.isEmpty()).as("intents not empty").isFalse();
		assertThat(intents
				.size()).as("wrong number of elements").isEqualTo(listIntents.size());
		for (int i = 0; i < listIntents.size(); i++) {
			assertThat(intents
					.get(i)).as("wrong intent value").isEqualTo(listIntents.get(i));
		}
		testListMutability(intents);

		ed = newEndpointDescription(ref, null);
		intents = ed.getIntents();
		assertThat(intents).as("intents null").isNotNull();
		assertThat(intents.isEmpty()).as("intents not empty").isFalse();
		assertThat(intents
				.size()).as("wrong number of elements").isEqualTo(listIntents.size());
		for (int i = 0; i < listIntents.size(); i++) {
			assertThat(intents
					.get(i)).as("wrong intent value").isEqualTo(listIntents.get(i));
		}
		testListMutability(intents);

	}

	@Test
	public void testConfigurationsTypes() {
		EndpointDescription ed;
		Map<String, Object> props = new HashMap<String, Object>();
		String someId = "someId";
		props.put(ENDPOINT_ID, someId);
		props.put(OBJECTCLASS, new String[] {"foo"});
		props.put(SERVICE_IMPORTED_CONFIGS, "config");
		String testUUID = "testUUID";
		BundleContext testContext = newMockBundleContext(testUUID);
		Bundle testBundle = newMockBundle(1, "testName", "testLocation",
				testContext);
		Map<String, Object> serviceProps = new TreeMap<String, Object>(
				String.CASE_INSENSITIVE_ORDER);
		serviceProps.put(ENDPOINT_ID, someId);
		serviceProps.put(OBJECTCLASS, new String[] {"foo"});
		serviceProps.put(SERVICE_IMPORTED_CONFIGS, "config");
		ServiceReference< ? > ref = newMockServiceReference(testBundle,
				serviceProps);
		List<String> configTypes;

		props.put(SERVICE_IMPORTED_CONFIGS, this);
		serviceProps.put(SERVICE_IMPORTED_CONFIGS, this);

		try {
			ed = newEndpointDescription(props);
			fail("config type empty");
		}
		catch (IllegalArgumentException e) {
			// expected
		}

		try {
			ed = newEndpointDescription(ref, null);
			fail("config type empty");
		}
		catch (IllegalArgumentException e) {
			// expected
		}

		String scalarConfigType = "some.configtype";
		props.put(SERVICE_IMPORTED_CONFIGS, scalarConfigType);
		serviceProps.put(SERVICE_IMPORTED_CONFIGS, scalarConfigType);

		ed = newEndpointDescription(props);
		configTypes = ed.getConfigurationTypes();
		assertThat(configTypes).as("configtypes null").isNotNull();
		assertThat(configTypes.isEmpty()).as("configtypes not empty").isFalse();
		assertThat(configTypes.size()).as("only one element").isEqualTo(1);
		assertThat(configTypes
				.get(0)).as("wrong configtype value").isEqualTo(scalarConfigType);
		testListMutability(configTypes);

		ed = newEndpointDescription(ref, null);
		configTypes = ed.getConfigurationTypes();
		assertThat(configTypes).as("configtypes null").isNotNull();
		assertThat(configTypes.isEmpty()).as("configtypes not empty").isFalse();
		assertThat(configTypes.size()).as("only one element").isEqualTo(1);
		assertThat(configTypes
				.get(0)).as("wrong configtype value").isEqualTo(scalarConfigType);
		testListMutability(configTypes);

		String[] arrayConfigTypes = new String[] {"some.configtype1",
				"some.configtype2"};
		props.put(SERVICE_IMPORTED_CONFIGS, arrayConfigTypes);
		serviceProps.put(SERVICE_IMPORTED_CONFIGS, arrayConfigTypes);

		ed = newEndpointDescription(props);
		configTypes = ed.getConfigurationTypes();
		assertThat(configTypes).as("configtypes null").isNotNull();
		assertThat(configTypes.isEmpty()).as("configtypes not empty").isFalse();
		assertThat(configTypes.size()).as("wrong number of elements").isEqualTo(arrayConfigTypes.length);
		for (int i = 0; i < arrayConfigTypes.length; i++) {
			assertThat(configTypes.get(i)).as("wrong configtype value").isEqualTo(arrayConfigTypes[i]);
		}
		testListMutability(configTypes);

		ed = newEndpointDescription(ref, null);
		configTypes = ed.getConfigurationTypes();
		assertThat(configTypes).as("configtypes null").isNotNull();
		assertThat(configTypes.isEmpty()).as("configtypes not empty").isFalse();
		assertThat(configTypes.size()).as("wrong number of elements").isEqualTo(arrayConfigTypes.length);
		for (int i = 0; i < arrayConfigTypes.length; i++) {
			assertThat(configTypes.get(i)).as("wrong configtype value").isEqualTo(arrayConfigTypes[i]);
		}
		testListMutability(configTypes);

		List<String> listConfigTypes = Arrays.asList("some.configtype3",
				"some.configtype4", "some.configtype5");
		props.put(SERVICE_IMPORTED_CONFIGS, listConfigTypes);
		serviceProps.put(SERVICE_IMPORTED_CONFIGS, listConfigTypes);

		ed = newEndpointDescription(props);
		configTypes = ed.getConfigurationTypes();
		assertThat(configTypes).as("configtypes null").isNotNull();
		assertThat(configTypes.isEmpty()).as("configtypes not empty").isFalse();
		assertThat(configTypes.size()).as("wrong number of elements").isEqualTo(listConfigTypes.size());
		for (int i = 0; i < listConfigTypes.size(); i++) {
			assertThat(configTypes.get(i)).as("wrong configtype value").isEqualTo(listConfigTypes.get(i));
		}
		testListMutability(configTypes);

		ed = newEndpointDescription(ref, null);
		configTypes = ed.getConfigurationTypes();
		assertThat(configTypes).as("configtypes null").isNotNull();
		assertThat(configTypes.isEmpty()).as("configtypes not empty").isFalse();
		assertThat(configTypes.size()).as("wrong number of elements").isEqualTo(listConfigTypes.size());
		for (int i = 0; i < listConfigTypes.size(); i++) {
			assertThat(configTypes.get(i)).as("wrong configtype value").isEqualTo(listConfigTypes.get(i));
		}
		testListMutability(configTypes);

	}

	@Test
	public void testHashcode() {
		Map<String, Object> props = new HashMap<String, Object>();
		String someId = "someId";
		props.put(ENDPOINT_ID, "  " + someId + "\t");
		props.put(OBJECTCLASS, new String[] {"foo"});
		props.put(SERVICE_IMPORTED_CONFIGS, "config");
		String testUUID = "testUUID";
		BundleContext testContext = newMockBundleContext(testUUID);
		Bundle testBundle = newMockBundle(1, "testName", "testLocation",
				testContext);
		Map<String, Object> serviceProps = new TreeMap<String, Object>(
				String.CASE_INSENSITIVE_ORDER);
		serviceProps.put(ENDPOINT_ID, someId);
		serviceProps.put(OBJECTCLASS, new String[] {"foo"});
		serviceProps.put(SERVICE_IMPORTED_CONFIGS, "config");
		ServiceReference< ? > ref = newMockServiceReference(testBundle,
				serviceProps);
		EndpointDescription ed1, ed2, ed3;

		ed1 = newEndpointDescription(props);
		ed2 = newEndpointDescription(ref, null);
		props.put(ENDPOINT_ID, "other.id");
		ed3 = newEndpointDescription(props);

		assertThat(ed1.hashCode() == ed2.hashCode()).as("hashCode should equal").isTrue();
		assertThat(ed1.hashCode() != ed3
				.hashCode()).as("hashCode should not equal").isTrue();
		assertThat(ed2.hashCode() != ed3
				.hashCode()).as("hashCode should not equal").isTrue();
		assertThat(ed1.getId()).as("id not trimmed of whitespace").isEqualTo(someId);
	}

	@SuppressWarnings("unlikely-arg-type")
	@Test
	public void testEquals() {
		Map<String, Object> props = new HashMap<String, Object>();
		String someId = "someId";
		props.put(ENDPOINT_ID, "  " + someId + "\t");
		props.put(OBJECTCLASS, new String[] {"foo"});
		props.put(SERVICE_IMPORTED_CONFIGS, "config");
		String testUUID = "testUUID";
		BundleContext testContext = newMockBundleContext(testUUID);
		Bundle testBundle = newMockBundle(1, "testName", "testLocation",
				testContext);
		Map<String, Object> serviceProps = new TreeMap<String, Object>(
				String.CASE_INSENSITIVE_ORDER);
		serviceProps.put(ENDPOINT_ID, someId);
		serviceProps.put(OBJECTCLASS, new String[] {"foo"});
		serviceProps.put(SERVICE_IMPORTED_CONFIGS, "config");
		ServiceReference< ? > ref = newMockServiceReference(testBundle,
				serviceProps);
		EndpointDescription ed1, ed2, ed3;

		ed1 = newEndpointDescription(props);
		ed2 = newEndpointDescription(ref, null);
		props.put(ENDPOINT_ID, "other.id");
		ed3 = newEndpointDescription(props);

		assertThat(ed1.equals(ed2)).as("should equal").isTrue();
		assertThat(ed2.equals(ed1)).as("should equal").isTrue();
		assertThat(ed1.equals(ed3)).as("should not equal").isFalse();
		assertThat(ed3.equals(ed1)).as("should not equal").isFalse();
		assertThat(ed2.equals(ed3)).as("should not equal").isFalse();
		assertThat(ed3.equals(ed1)).as("should not equal").isFalse();
		assertThat(ed1.equals(ed1)).as("should equal").isTrue();
		assertThat(ed2.equals(ed2)).as("should equal").isTrue();
		assertThat(ed3.equals(ed3)).as("should equal").isTrue();
		assertThat(ed2.equals(this)).as("should not equal").isFalse();
		assertThat(ed1.getId()).as("id not trimmed of whitespace").isEqualTo(someId);
	}

	@Test
	public void testIsSame() {
		Map<String, Object> props = new HashMap<String, Object>();
		props.put(ENDPOINT_ID, "id1");
		props.put(OBJECTCLASS, new String[] {"foo"});
		props.put(SERVICE_IMPORTED_CONFIGS, "config");
		String testUUID = "testUUID";
		BundleContext testContext = newMockBundleContext(testUUID);
		Bundle testBundle = newMockBundle(1, "testName", "testLocation",
				testContext);
		Map<String, Object> serviceProps = new TreeMap<String, Object>(
				String.CASE_INSENSITIVE_ORDER);
		serviceProps.put(ENDPOINT_ID, "id2");
		serviceProps.put(OBJECTCLASS, new String[] {"foo"});
		serviceProps.put(SERVICE_IMPORTED_CONFIGS, "config");
		Long someID = Long.valueOf(12l);
		props.put(ENDPOINT_SERVICE_ID, someID);
		serviceProps.put(SERVICE_ID, someID);
		ServiceReference< ? > ref = newMockServiceReference(testBundle,
				serviceProps);
		EndpointDescription ed1, ed2, ed3, ed4;

		ed1 = newEndpointDescription(props);
		ed2 = newEndpointDescription(ref, null);
		ed3 = newEndpointDescription(props);
		props.put(ENDPOINT_ID, "id4");
		props.put(ENDPOINT_FRAMEWORK_UUID, testUUID);
		ed4 = newEndpointDescription(props);

		assertThat(ed1.isSameService(ed1)).as("should be same").isTrue();
		assertThat(ed3.isSameService(ed3)).as("should be same").isTrue();
		assertThat(ed1.isSameService(ed3)).as("should be same").isTrue();
		assertThat(ed3.isSameService(ed1)).as("should be same").isTrue();
		assertThat(ed2.isSameService(ed4)).as("should be same").isTrue();
		assertThat(ed4.isSameService(ed2)).as("should be same").isTrue();
		assertThat(ed1.isSameService(ed2)).as("should not be same").isFalse();
		assertThat(ed2.isSameService(ed1)).as("should not be same").isFalse();
	}

	@Test
	public void testMatches() {
		Map<String, Object> props = new HashMap<String, Object>();
		props.put(ENDPOINT_ID, "id1");
		props.put(OBJECTCLASS, new String[] {"foo"});
		props.put(SERVICE_IMPORTED_CONFIGS, "config");
		String testUUID = "testUUID";
		BundleContext testContext = newMockBundleContext(testUUID);
		Bundle testBundle = newMockBundle(1, "testName", "testLocation",
				testContext);
		Map<String, Object> serviceProps = new TreeMap<String, Object>(
				String.CASE_INSENSITIVE_ORDER);
		serviceProps.put(ENDPOINT_ID, "id2");
		serviceProps.put(OBJECTCLASS, new String[] {"foo"});
		serviceProps.put(SERVICE_IMPORTED_CONFIGS, "config");
		Long someID = Long.valueOf(12l);
		props.put(ENDPOINT_SERVICE_ID, someID);
		serviceProps.put(SERVICE_ID, someID);
		ServiceReference< ? > ref = newMockServiceReference(testBundle,
				serviceProps);
		EndpointDescription ed1, ed2;

		ed1 = newEndpointDescription(props);
		ed2 = newEndpointDescription(ref, null);

		String filter = "(" + ENDPOINT_ID + "=id1)";
		assertThat(ed1.matches(filter)).as("filter does not match").isTrue();
		assertThat(ed2.matches(filter)).as("filter matches").isFalse();

		filter = "(ENDPOINT.id=id2)";
		assertThat(ed2.matches(filter)).as("filter does not match").isTrue();
		assertThat(ed1.matches(filter)).as("filter matches").isFalse();

		filter = "(" + ENDPOINT_FRAMEWORK_UUID + "=" + testUUID + ")";
		assertThat(ed2.matches(filter)).as("filter does not match").isTrue();
		assertThat(ed1.matches(filter)).as("filter matches").isFalse();

		filter = "(" + ENDPOINT_FRAMEWORK_UUID + "=*)";
		assertThat(ed2.matches(filter)).as("filter does not match").isTrue();
		assertThat(ed1.matches(filter)).as("filter matches").isFalse();

		try {
			ed1.matches("(xx=foo");
			fail("invalid filter syntax allowed");
		}
		catch (IllegalArgumentException e) {
			// expected
		}
	}

	@Test
	public void testStripServiceExported() {
		Map<String, Object> props = new HashMap<String, Object>();
		String someId = "someId";
		props.put(ENDPOINT_ID, "  " + someId + "\t");
		props.put(OBJECTCLASS, new String[] {"foo"});
		props.put(SERVICE_IMPORTED_CONFIGS, "config");
		props.put("SERVICE.EXPORTED.CONFIGS", "config");
		String testUUID = "testUUID";
		BundleContext testContext = newMockBundleContext(testUUID);
		Bundle testBundle = newMockBundle(1, "testName", "testLocation",
				testContext);
		Map<String, Object> serviceProps = new TreeMap<String, Object>(
				String.CASE_INSENSITIVE_ORDER);
		serviceProps.put(ENDPOINT_ID, someId);
		serviceProps.put(OBJECTCLASS, new String[] {"foo"});
		serviceProps.put(SERVICE_IMPORTED_CONFIGS, "config");
		serviceProps.put("Service.Exported.Configs", "config");
		ServiceReference< ? > ref = newMockServiceReference(testBundle,
				serviceProps);
		EndpointDescription ed1, ed2;

		ed1 = newEndpointDescription(props);
		ed2 = newEndpointDescription(ref, null);

		assertThat(ed1
				.getProperties().containsKey(SERVICE_EXPORTED_CONFIGS)).as("service.exported.* property not removed").isFalse();
		assertThat(ed2
				.getProperties().containsKey(SERVICE_EXPORTED_CONFIGS)).as("service.exported.* property not removed").isFalse();
	}

	private EndpointDescription newEndpointDescription(
			ServiceReference< ? > ref,
			Map<String, Object> props) {
		EndpointDescription ed = new EndpointDescription(ref, props);
		testMutability(ed);
		return ed;
	}

	private EndpointDescription newEndpointDescription(Map<String, Object> props) {
		EndpointDescription ed = new EndpointDescription(props);
		testMutability(ed);
		return ed;
	}

	private void testMutability(EndpointDescription ed) {
		Map<String, Object> p = ed.getProperties();
		try {
			if (!p.isEmpty()) {
				p.clear();
				fail("properties is mutable");
			}
		}
		catch (RuntimeException e) {
			// expected
		}
		assertThatExceptionOfType(RuntimeException.class).as("properties is mutable")
				.isThrownBy(() -> p.put("foo", "bar"));
		List<String> interfs = ed.getInterfaces();
		testListMutability(interfs);
	}

	@SuppressWarnings("unchecked")
	private <T> void testListMutability(List<T> list) {
		try {
			if (!list.isEmpty()) {
				list.clear();
				fail("list is mutable");
			}
		}
		catch (RuntimeException e) {
			// expected
		}
		assertThatExceptionOfType(RuntimeException.class).as("list is mutable")
				.isThrownBy(() -> list.add((T) "foo"));
	}

	private String getPackageName(String className) {
		int index = className.lastIndexOf('.');
		if (index == -1) {
			return "";
		}
		return className.substring(0, index);
	}

	public static BundleContext newMockBundleContext(String uuid) {
		return MockFactory.newMock(BundleContext.class,
				new MockBundleContext(uuid));
	}

	private static class MockBundleContext {
		private final String	uuid;

		MockBundleContext(String uuid) {
			this.uuid = uuid;
		}

		@SuppressWarnings("unused")
		public String getProperty(String key) {
			if (key.equals("org.osgi.framework.uuid")) {
				return uuid;
			}
			return null;
		}
	}

	public static Bundle newMockBundle(long id, String name, String location,
			BundleContext context) {
		return MockFactory.newMock(Bundle.class, new MockBundle(id,
				name, location, context));
	}

	private static class MockBundle {
		private final long			id;
		private final String		name;
		private final String		location;
		private final BundleContext	context;

		MockBundle(long id, String name, String location, BundleContext context) {
			this.id = id;
			this.name = name;
			this.location = location;
			this.context = context;
		}

		@SuppressWarnings("unused")
		public long getBundleId() {
			return id;
		}

		@SuppressWarnings("unused")
		public String getLocation() {
			return location;
		}

		@SuppressWarnings("unused")
		public String getSymbolicName() {
			return name;
		}

		@SuppressWarnings("unused")
		public BundleContext getBundleContext() {
			return context;
		}
	}

	public static ServiceReference< ? > newMockServiceReference(Bundle bundle,
			Map<String, Object> properties) {
		return MockFactory.newMock(ServiceReference.class,
				new MockServiceReference(bundle, properties));
	}

	private static class MockServiceReference {
		private final Bundle				bundle;
		private final Map<String, Object>	properties;

		MockServiceReference(Bundle bundle, Map<String, Object> properties) {
			this.bundle = bundle;
			this.properties = properties;
		}

		@SuppressWarnings("unused")
		public Bundle getBundle() {
			return bundle;
		}

		@SuppressWarnings("unused")
		public Object getProperty(String key) {
			Object result = properties.get(key);
			if (result != null) {
				return result;
			}
			for (Iterator<String> iter = properties.keySet().iterator(); iter
					.hasNext();) {
				String k = iter.next();
				if (k.equalsIgnoreCase(key)) {
					return properties.get(k);
				}
			}
			return null;
		}

		@SuppressWarnings("unused")
		public String[] getPropertyKeys() {
			String[] result = new String[properties.size()];
			properties.keySet().toArray(result);
			return result;
		}
	}

}
