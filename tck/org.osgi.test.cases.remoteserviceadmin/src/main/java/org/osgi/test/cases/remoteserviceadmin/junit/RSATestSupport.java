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

import static org.assertj.core.api.Assertions.fail;

import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;

import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.test.common.annotation.InjectBundleContext;

/**
 * The helpers these tests used from OSGiTestCase and DefaultTestBundleControl
 * of the TCK test support, unchanged: properties are read from the bundle
 * context, registered and fetched services are remembered by their service
 * object, so they can be released by it.
 */
abstract class RSATestSupport {
	@InjectBundleContext
	protected BundleContext								context;

	private final Map<Object,ServiceRegistration< ? >>	serviceRegistry	= new HashMap<Object,ServiceRegistration< ? >>();
	private final Map<Object,ServiceReference< ? >>		fetchedServices	= new HashMap<Object,ServiceReference< ? >>();
	private volatile String								webServer;

	/**
	 * Return the property value from the bundle context properties.
	 *
	 * @param key The property key name.
	 * @return The property value or null if the property is not set.
	 */
	public String getProperty(String key) {
		return AccessController.doPrivileged((PrivilegedAction<String>) () -> {
			if (context != null) {
				return context.getProperty(key);
			}
			return System.getProperty(key);
		});
	}

	/**
	 * Return the property value from the bundle context properties.
	 *
	 * @param key The property key name.
	 * @param defaultValue The default property value to return if the property
	 *        is not set.
	 * @return The property value or defaultValue if the property is not set.
	 */
	public String getProperty(String key, String defaultValue) {
		String propValue = getProperty(key);
		if (propValue == null) {
			return defaultValue;
		}
		return propValue;
	}

	/**
	 * Return the integer property value from the bundle context properties.
	 *
	 * @param key The property key name.
	 * @param defaultValue The default property value to return if the property
	 *        is not set.
	 * @return The property value or defaultValue if the property is not set.
	 */
	public int getIntegerProperty(String key, int defaultValue) {
		String propValue = getProperty(key);
		if (propValue == null) {
			return defaultValue;
		}
		try {
			return Integer.parseInt(propValue);
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	/**
	 * Return the long property value from the bundle context properties.
	 *
	 * @param key The property key name.
	 * @param defaultValue The default property value to return if the property
	 *        is not set.
	 * @return The property value or defaultValue if the property is not set.
	 */
	public long getLongProperty(String key, long defaultValue) {
		String propValue = getProperty(key);
		if (propValue == null) {
			return defaultValue;
		}
		try {
			return Long.parseLong(propValue);
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	public String getWebServer() {
		String w = webServer;
		if (w == null) {
			URL base = context.getBundle().getEntry("/");
			webServer = w = (base == null) ? "/" : base.toExternalForm();
		}
		return w;
	}

	public void registerService(String clazz, Object service,
			Dictionary<String, ? > properties) throws Exception {
		ServiceRegistration< ? > sr = context.registerService(clazz, service,
				properties);
		synchronized (serviceRegistry) {
			serviceRegistry.put(service, sr);
		}
	}

	/**
	 * Convenience method for getting services from the framework in cases when
	 * you don't want to keep track of the ServiceReference.
	 * <p>
	 * To unregister the service, use the ungetService(Object service) method.
	 *
	 * @param clazz the Class object of the service you with to retrieve
	 * @return a service object
	 * @throws NullPointerException if the service couldn't be retrieved from
	 *         the framework.
	 */
	public <S> S getService(Class<S> clazz) {
		try {
			return getService(clazz, null);
		} catch (InvalidSyntaxException e) {
			/* null can not give an exception of this type */
		}
		return null;
	}

	public <S> S getService(Class<S> clazz, String filter)
			throws InvalidSyntaxException {
		@SuppressWarnings("unchecked")
		ServiceReference<S>[] refs = (ServiceReference<S>[]) context
				.getServiceReferences(clazz.getName(), filter);

		if (refs == null) {
			fail("Can't get service reference for " + clazz.getName() + filter);
		}

		ServiceReference<S> chosenRef = pickServiceReference(refs);

		S service = context.getService(chosenRef);

		if (service == null) {
			fail("Can't get service for " + clazz.getName() + filter);
		}

		/* Save the service and its reference */
		synchronized (fetchedServices) {
			fetchedServices.put(service, chosenRef);
		}

		return service;
	}

	public ServiceReference< ? > getServiceReference(Object service) {
		ServiceReference< ? > ref;
		synchronized (fetchedServices) {
			ref = fetchedServices.get(service);
		}

		if (ref == null) {
			fail("trying to get ServiceReference for a service which is not currently bound");
		}

		return ref;
	}

	public void ungetService(Object service) {
		ServiceReference< ? > ref;
		synchronized (fetchedServices) {
			ref = fetchedServices.remove(service);
		}

		if (ref == null) {
			fail("trying to unget a service which is not currently bound");
		}

		context.ungetService(ref);
	}

	public void ungetAllServices() {
		ServiceReference< ? >[] refs;
		synchronized (fetchedServices) {
			refs = fetchedServices.values()
					.toArray(new ServiceReference[fetchedServices.size()]);
			fetchedServices.clear();
		}

		for (int l = refs.length, i = 0; i < l; i++) {
			context.ungetService(refs[i]);
		}
	}

	/**
	 * Picks a service from the given array of references. The rules the service
	 * is picked by are the same as the rules defined in
	 * BundleContext.getServiceReference() (highest ranking, lowest service ID
	 * if the ranking is a tie)
	 */
	private static <S> ServiceReference<S> pickServiceReference(
			ServiceReference<S>[] refs) {
		ServiceReference<S> highest = refs[0];

		for (int i = 1; i < refs.length; i++) {
			ServiceReference<S> challenger = refs[i];

			if (ranking(highest) < ranking(challenger)) {
				highest = challenger;
			} else if (ranking(highest) == ranking(challenger)) {
				if (serviceid(highest) > serviceid(challenger)) {
					highest = challenger;
				}
			}
		}

		return highest;
	}

	/**
	 * Get service ranking from a service reference.
	 *
	 * @param s The service reference
	 * @return Ranking value of service, default value is zero
	 */
	private static int ranking(ServiceReference< ? > s) {
		Object v = s.getProperty(Constants.SERVICE_RANKING);

		if (v != null && v instanceof Integer) {
			return ((Integer) v).intValue();
		} else {
			return 0;
		}
	}

	private static long serviceid(ServiceReference< ? > s) {
		Long sid = (Long) s.getProperty(Constants.SERVICE_ID);

		return sid.longValue();
	}
}
