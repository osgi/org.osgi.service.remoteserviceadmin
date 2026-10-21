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
import static org.osgi.test.assertj.bundle.BundleAssert.assertThat;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.URL;
import java.net.URLDecoder;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Map;
import java.util.StringTokenizer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;
import org.osgi.test.common.annotation.InjectBundleContext;

/**
 * @author <a href="mailto:tdiekman@tibco.com">Tim Diekmann</a>
 *
 */
public abstract class MultiFrameworkTestCase {

	@InjectBundleContext
	BundleContext		context;

	/**
	 * Project name. Used as a namespace for TCK (child) framework specific run
	 * properties.
	 */
	private static final String PROJECT_NAME = "org.osgi.test.cases.remoteservices";

	/**
	 * Magic value. Properties with this value will be replaced by a socket port
	 * number that is currently free.
	 */
	private static final String FREE_PORT = "@@FREE_PORT@@";

	private static final String STORAGEROOT = "org.osgi.test.cases.remoteservices.storageroot";
	private static final String DEFAULT_STORAGEROOT = "generated/testframeworkstorage";
	private static final String FRAMEWORK_FACTORY = "/META-INF/services/org.osgi.framework.launch.FrameworkFactory";
	private Framework framework;
	private String frameworkFactoryClassName;
	private FrameworkFactory frameworkFactory;
	private String rootStorageArea;

	@BeforeEach
	public void setUp() throws Exception {
		frameworkFactoryClassName = getFrameworkFactoryClassName();
		assertThat(frameworkFactoryClassName).as("Could not find framework factory class").isNotNull();
		frameworkFactory = getFrameworkFactory();

		rootStorageArea = getStorageAreaRoot();
		assertThat(rootStorageArea).as("No storage area root found").isNotNull();
		File rootFile = new File(rootStorageArea);
		delete(rootFile);
		assertThat(rootFile.exists() && !rootFile.isDirectory()).as("Root storage area is not a directory: " + rootFile.getPath()).isFalse();
		if (!rootFile.isDirectory())
			assertThat(rootFile.mkdirs()).as("Could not create root directory: " + rootFile.getPath()).isTrue();
		
		Map<String, Object> configuration = getFrameworkConfiguration();
		configuration.putAll(getConfiguration());
		configuration.put(Constants.FRAMEWORK_STORAGE, rootFile.getAbsolutePath());
		processFreePortProperties(configuration);
		
		framework = createFramework(configuration);
		initFramework();
		startFramework();
		
		installFramework();
	}

	/**
	 * Load the default framework properties
	 * 
	 * @return The map
	 */
	private Map<String, Object> getFrameworkConfiguration() {
		Map<String, Object> properties = new HashMap<String, Object>();
		String fwproperties = System.getProperty(PROJECT_NAME
				+ ".framework.properties");
		if (fwproperties != null) {
			for (StringTokenizer tok = new StringTokenizer(fwproperties, ","); tok
					.hasMoreTokens();) {
				String fwproperty = tok.nextToken();
				StringTokenizer equaltok = new StringTokenizer(
						fwproperty.trim(), "=");
				String name = equaltok.nextToken().trim();
				String value = null;
				try {
					value = URLDecoder.decode(equaltok.nextToken().trim(),
							"UTF-8");
				} catch (UnsupportedEncodingException e) {
					// Valid Java implementations must support UTF-8
					throw new AssertionError();
				}
				properties.put(name, value);
			}
		}
		return properties;
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
			ss.close();
			System.out.println("Found free port " + port);
			return port;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	@AfterEach
	public void tearDown() throws Exception {
		stopFramework();
	}

	/**
	 * @return started Framework instance
	 */
	public Framework getFramework() {
		Framework f = framework;
		if (f == null || f.getState() != Bundle.ACTIVE) {
			fail("Framework is not started yet");
		}
		return f;
	}
	
	/**
	 * This method is implemented by subclasses, which contain the test cases
	 * @return Map with framework properties.
	 */
	public abstract Map<String,String> getConfiguration();

	/**
	 * Install a bundle into the framework.
	 * @param bundle Bundle location to install
	 * @return Bundle that was created by the framework and installed
	 * @throws BundleException
	 * @throws IOException
	 */
	public Bundle installBundle(String bundle) throws BundleException, IOException {
		BundleContext fwkContext = getFramework().getBundleContext();
		assertThat(fwkContext).as("Framework context is null").isNotNull();
		URL input = getBundleInput(bundle);
		assertThat(input).as("Cannot find resource: " + bundle).isNotNull();
		return fwkContext.installBundle(bundle, input.openStream());
	}

	private String getFrameworkFactoryClassName() throws IOException {
		BundleContext context = getBundleContextWithoutFail();
        URL factoryService = context == null ? this.getClass().getResource(FRAMEWORK_FACTORY) : context.getBundle(0).getEntry(FRAMEWORK_FACTORY);
		assertThat(factoryService).as("Could not locate: " + FRAMEWORK_FACTORY).isNotNull();
		return getClassName(factoryService);

	}

	private String getClassName(URL factoryService) throws IOException {
		InputStream in = factoryService.openStream();
		BufferedReader br = null;
		try {
			br = new BufferedReader(new InputStreamReader(in));
			for (String line = br.readLine(); line != null; line=br.readLine()) {
				int pound = line.indexOf('#');
				if (pound >= 0)
					line = line.substring(0, pound);
				line.trim();
				if (!"".equals(line))
					return line;
			}
		} finally {
			try {
				if (br != null)
					br.close();
			}
			catch (IOException e) {
				// did our best; just ignore
			}
		}
		return null;
	}

	private String getStorageAreaRoot() {
		BundleContext context = getBundleContextWithoutFail();
		if (context == null) {
			String storageroot = getProperty(STORAGEROOT, DEFAULT_STORAGEROOT);
			assertThat(storageroot).as("Must set property: " + STORAGEROOT).isNotNull();
			return storageroot;
		}
		return context.getDataFile("storageroot").getAbsolutePath();
	}

	private Class<FrameworkFactory> loadFrameworkClass(String className)
			throws ClassNotFoundException {
		BundleContext context = getBundleContextWithoutFail();
		@SuppressWarnings("unchecked")
		Class<FrameworkFactory> clazz = (Class<FrameworkFactory>) (context == null
				? Class.forName(className)
				: context.getBundle(0).loadClass(className));
		return clazz;
	}

	private BundleContext getBundleContextWithoutFail() {
		try {
			if ("true".equals(getProperty("noframework")))
				return null;
			return context;
		} catch (Throwable t) {
			return null; // don't fail
		}
	}

	private FrameworkFactory getFrameworkFactory() {
		try {
			Class<FrameworkFactory> clazz = loadFrameworkClass(
					frameworkFactoryClassName);
			return clazz.getConstructor().newInstance();
		} catch (Exception e) {
			fail("Failed to get the framework constructor", e);
		}
		return null;
	}

	private boolean delete(File file) {
		if (file.exists()) {
			if (file.isDirectory()) {
				String list[] = file.list();
				if (list != null) {
					int len = list.length;
					for (int i = 0; i < len; i++)
						if (!delete(new File(file, list[i])))
							return false;
				}
			}

			return file.delete();
		}
		return (true);
	}

	@SuppressWarnings({
			"unchecked", "rawtypes"
	})
	private Framework createFramework(Map<String,Object> configuration) {
		Framework framework = null;
		try {
			framework = frameworkFactory.newFramework((Map) configuration);
		}
		catch (Exception e) {
			fail("Failed to construct the framework", e);
		}
		assertThat(framework).as("Wrong state for newly constructed framework").isInState(Bundle.INSTALLED);
		return framework;
	}

	private URL getBundleInput(String bundle) {
		BundleContext context = getBundleContextWithoutFail();
		return context == null ? this.getClass().getResource(bundle) : context.getBundle().getEntry(bundle);
	}


	private void initFramework() {
		try {
			framework.init();
			assertThat(framework.getBundleContext()).as("BundleContext is null after init").isNotNull();
		}
		catch (BundleException e) {
			fail("Unexpected BundleException initializing", e);
		}
		assertThat(framework).as("Wrong framework state after init").isInState(Bundle.STARTING);
	}

	private void startFramework() {
		try {
			framework.start();
			assertThat(framework.getBundleContext()).as("BundleContext is null after start").isNotNull();
		}
		catch (BundleException e) {
			fail("Unexpected BundleException initializing", e);
		}
		assertThat(framework).as("Wrong framework state after init").isInState(Bundle.ACTIVE);

	}

	private void stopFramework() {
		int previousState = framework.getState();
		try {
            framework.stop();
			FrameworkEvent event = framework.waitForStop(10000);
			assertThat(event).as("FrameworkEvent is null").isNotNull();
			assertThat(event.getType()).as("Wrong event type").isEqualTo(FrameworkEvent.STOPPED);
			assertThat(framework.getBundleContext()).as("BundleContext is not null after stop").isNull();
		}
		catch (BundleException e) {
			fail("Unexpected BundleException stopping", e);
		}
		catch (InterruptedException e) {
			fail("Unexpected InterruptedException waiting for stop", e);
		}
		// if the framework was not STARTING STOPPING or ACTIVE then we assume the waitForStop returned immediately with a FrameworkEvent.STOPPED 
		// and does not change the state of the framework
		int expectedState = (previousState & (Bundle.STARTING | Bundle.ACTIVE | Bundle.STOPPING)) != 0 ? Bundle.RESOLVED : previousState;
		assertThat(framework.getState()).as("Wrong framework state after init").isEqualTo(expectedState);
	}

	private void installFramework() throws Exception {
		Framework f = getFramework();
		
		List<Bundle> bundles = new LinkedList<>();
		
		StringTokenizer st = new StringTokenizer(getProperty(
				"org.osgi.test.cases.remoteservices.bundles", ""), ",");
		while (st.hasMoreTokens()) {
			String bundle = st.nextToken();
			
			Bundle b = f.getBundleContext().installBundle("file:" + bundle);
			assertThat(b).isNotNull();
			assertThat(b).as("Bundle " + b.getSymbolicName() + " is not INSTALLED").isInState(Bundle.INSTALLED);
			
			System.out.println("installed bundle " + b.getSymbolicName());
			bundles.add(b);
		}
		
		for (Iterator<Bundle> it = bundles.iterator(); it.hasNext();) {
			Bundle b = it.next();
			
			if (b.getHeaders().get(Constants.FRAGMENT_HOST) == null) {
				b.start();
				assertThat(b).as("Bundle " + b.getSymbolicName() + " is not ACTIVE").isInState(Bundle.ACTIVE);
				
				System.out.println("started bundle " + b.getSymbolicName());
			}
		}
	}

	/**
	 * Return the property value from the bundle context properties, as
	 * OSGiTestCase.getProperty of the TCK test support did.
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
}
