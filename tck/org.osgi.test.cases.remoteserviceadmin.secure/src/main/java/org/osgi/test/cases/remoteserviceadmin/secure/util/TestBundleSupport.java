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

package org.osgi.test.cases.remoteserviceadmin.secure.util;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;

/**
 * The helpers the test bundles used from OSGiTestCaseProperties and
 * DefaultTestBundleControl of the TCK test support, unchanged. Embedded in
 * each test bundle that uses it (-conditionalpackage), so the properties are
 * read from the context of that bundle.
 */
public class TestBundleSupport {
	private TestBundleSupport() {
		// empty
	}

	/**
	 * Return the property value from the bundle context properties.
	 *
	 * @param key The property key name.
	 * @return The property value or null if the property is not set.
	 */
	public static String getProperty(String key) {
		return AccessController.doPrivileged((PrivilegedAction<String>) () -> {
			Bundle bundle = FrameworkUtil.getBundle(TestBundleSupport.class);
			if (bundle != null) {
				BundleContext context;
				try {
					context = bundle.getBundleContext();
				} catch (SecurityException e) {
					context = null;
				}
				if (context != null) {
					return context.getProperty(key);
				}
			}
			return System.getProperty(key);
		});
	}

	/**
	 * Return the integer property value from the bundle context properties.
	 *
	 * @param key The property key name.
	 * @param defaultValue The default property value to return if the property
	 *        is not set.
	 * @return The property value or defaultValue if the property is not set.
	 */
	public static int getIntegerProperty(String key, int defaultValue) {
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
	public static long getLongProperty(String key, long defaultValue) {
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

	public static String arrayToString(Object[] array, boolean sort) {
		StringBuffer buf = new StringBuffer();

		if (sort) {
			Arrays.sort(array);
		}

		if (array != null) {
			buf.append("{ ");
			for (int i = 0; i < array.length; i++) {
				buf.append(array[i]);
				if (i + 1 < array.length) {
					buf.append(", ");
				}
			}

			buf.append(" }");

		} else {
			buf.append("null");
		}

		return buf.toString();
	}
}
