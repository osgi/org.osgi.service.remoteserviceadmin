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
import static org.osgi.framework.Constants.OBJECTCLASS;
import static org.osgi.service.remoteserviceadmin.RemoteConstants.ENDPOINT_FRAMEWORK_UUID;
import static org.osgi.service.remoteserviceadmin.RemoteConstants.ENDPOINT_ID;
import static org.osgi.service.remoteserviceadmin.RemoteConstants.SERVICE_IMPORTED_CONFIGS;
import static org.osgi.test.assertj.permission.PermissionAssertions.assertThat;

import java.security.Permission;
import java.security.PermissionCollection;
import java.util.HashMap;
import java.util.Map;
import java.util.PropertyPermission;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointPermission;

public class EndpointPermissionTests {

	@Test
	public void testInvalid() {
		// invalid actions
		invalidEndpointPermission("(a=b)", "x");
		invalidEndpointPermission("(a=b)", "   import  ,  x   ");
		invalidEndpointPermission("(a=b)", "");
		invalidEndpointPermission("(a=b)", "      ");
		invalidEndpointPermission("(a=b)", null);
		invalidEndpointPermission("(a=b)", ",");
		invalidEndpointPermission("(a=b)", ",xxx");
		invalidEndpointPermission("(a=b)", "xxx,");
		invalidEndpointPermission("(a=b)", "import,");
		invalidEndpointPermission("(a=b)", "export,   ");
		invalidEndpointPermission("(a=b)", "read,   ");
		invalidEndpointPermission("(a=b)", "importme,");
		invalidEndpointPermission("(a=b)", "exportme,");
		invalidEndpointPermission("(a=b)", "readme,");
		invalidEndpointPermission("(a=b)", ",import");
		invalidEndpointPermission("(a=b)", ",export");
		invalidEndpointPermission("(a=b)", ",read");
		invalidEndpointPermission("(a=b)", "   importme   ");
		invalidEndpointPermission("(a=b)", "   exportme     ");
		invalidEndpointPermission("(a=b)", "   readme     ");
		invalidEndpointPermission("(a=b)", "   impor");
		invalidEndpointPermission("(a=b)", "   expor");
		invalidEndpointPermission("(a=b)", "   rea");

		// invalid filter
		invalidEndpointPermission("()", "import");
		invalidEndpointPermission("foo", "import");
		invalidEndpointPermission("(foo=bar", "import");

		// null arg
		invalidEndpointPermission((String) null, "import");
		invalidEndpointPermission((EndpointDescription) null, null, "import");
		invalidEndpointPermission("(a=b)", null);
	}

	@Test
	public void testActions() {
		String someId = "someId";
		Map<String, Object> ep = new HashMap<String, Object>();
		ep.put(ENDPOINT_ID, someId);
		ep.put(OBJECTCLASS, new String[] {"foo"});
		ep.put(SERVICE_IMPORTED_CONFIGS, "config");
		EndpointDescription ed = new EndpointDescription(ep);
		String filterString = "(" + ENDPOINT_ID + "=" + someId + ")";
		Permission op = new PropertyPermission("java.home", "read");

		EndpointPermission p11 = new EndpointPermission(filterString,
				"    IMPORT,read   ");
		EndpointPermission p12 = new EndpointPermission(filterString,
				"READ  ,   import");
		EndpointPermission p13 = new EndpointPermission(filterString,
				"expORT   ");

		EndpointPermission p14 = new EndpointPermission(filterString,
				"    Import    ");
		EndpointPermission p15 = new EndpointPermission(filterString, "rEAd   ");
		EndpointPermission p16 = new EndpointPermission(filterString,
				"rEAd,export");
		EndpointPermission p17 = new EndpointPermission(filterString,
				"impORt, EXport");
		EndpointPermission p18 = new EndpointPermission(filterString,
				"rEAd, impORt, EXport");

		EndpointPermission p21 = new EndpointPermission(ed, null,
				"    IMPORT,read   ");
		EndpointPermission p22 = new EndpointPermission(ed, null,
				"READ  ,   import");
		EndpointPermission p23 = new EndpointPermission(ed, null, "expORT   ");

		EndpointPermission p24 = new EndpointPermission(ed, null,
				"    Import    ");
		EndpointPermission p25 = new EndpointPermission(ed, null, "rEAd   ");
		EndpointPermission p26 = new EndpointPermission(ed, null, "rEAd,export");
		EndpointPermission p27 = new EndpointPermission(ed, null,
				"impORt, EXport");
		EndpointPermission p28 = new EndpointPermission(ed, null,
				"rEAd, impORt, EXport");

		Assertions.assertThat(p11.getActions()).isEqualTo("read,import");
		Assertions.assertThat(p12.getActions()).isEqualTo("read,import");
		Assertions.assertThat(p13.getActions()).isEqualTo("read,export");
		Assertions.assertThat(p14.getActions()).isEqualTo("read,import");
		Assertions.assertThat(p15.getActions()).isEqualTo("read");
		Assertions.assertThat(p16.getActions()).isEqualTo("read,export");
		Assertions.assertThat(p17.getActions()).isEqualTo("read,import,export");
		Assertions.assertThat(p18.getActions()).isEqualTo("read,import,export");
		Assertions.assertThat(p21.getActions()).isEqualTo("read,import");
		Assertions.assertThat(p22.getActions()).isEqualTo("read,import");
		Assertions.assertThat(p23.getActions()).isEqualTo("read,export");
		Assertions.assertThat(p24.getActions()).isEqualTo("read,import");
		Assertions.assertThat(p25.getActions()).isEqualTo("read");
		Assertions.assertThat(p26.getActions()).isEqualTo("read,export");
		Assertions.assertThat(p27.getActions()).isEqualTo("read,import,export");
		Assertions.assertThat(p28.getActions()).isEqualTo("read,import,export");

		assertThat(p11).implies(p21);
		assertThat(p11).implies(p22);
		assertThat(p11).doesNotImply(p23);
		assertThat(p11).implies(p24);
		assertThat(p11).implies(p25);
		assertThat(p11).doesNotImply(p26);
		assertThat(p11).doesNotImply(p27);
		assertThat(p11).doesNotImply(p28);

		assertThat(p11).doesNotImply(p11);
		assertThat(p11).doesNotImply(p12);
		assertThat(p11).doesNotImply(p13);
		assertThat(p11).doesNotImply(p14);
		assertThat(p11).doesNotImply(p15);
		assertThat(p11).doesNotImply(p16);
		assertThat(p11).doesNotImply(p17);
		assertThat(p11).doesNotImply(p18);

		assertThat(p15).implies(p25);

		assertThat(p14).doesNotImply(p14);
		assertThat(p15).doesNotImply(p15);

		assertThat(p13).doesNotImply(p21);
		assertThat(p13).doesNotImply(p22);
		assertThat(p13).implies(p23);
		assertThat(p13).doesNotImply(p24);
		assertThat(p13).implies(p25);
		assertThat(p13).implies(p26);
		assertThat(p13).doesNotImply(p27);
		assertThat(p13).doesNotImply(p28);

		assertThat(p14).implies(p21);
		assertThat(p14).implies(p22);
		assertThat(p14).doesNotImply(p23);
		assertThat(p14).implies(p24);
		assertThat(p14).implies(p25);
		assertThat(p14).doesNotImply(p26);
		assertThat(p14).doesNotImply(p27);
		assertThat(p14).doesNotImply(p28);

		assertThat(p18).implies(p21);
		assertThat(p18).implies(p22);
		assertThat(p18).implies(p23);
		assertThat(p18).implies(p24);
		assertThat(p18).implies(p26);
		assertThat(p18).implies(p27);
		assertThat(p18).implies(p28);

		assertThat(p11).doesNotImply(op);

		assertThat(p11).isEquivalentTo(p11);
		assertThat(p11).isEquivalentTo(p12);
		assertThat(p11).isNotEquivalentTo(p13);
		assertThat(p11).isEquivalentTo(p14);
		assertThat(p11).isNotEquivalentTo(p15);
		assertThat(p11).isNotEquivalentTo(p16);
		assertThat(p11).isNotEquivalentTo(p17);
		assertThat(p11).isNotEquivalentTo(p18);

		assertThat(p12).isEquivalentTo(p11);
		assertThat(p12).isEquivalentTo(p14);

		assertThat(p13).isEquivalentTo(p16);
		assertThat(p13).isNotEquivalentTo(p14);
		assertThat(p13).isNotEquivalentTo(p15);

		assertThat(p14).isEquivalentTo(p11);
		assertThat(p14).isEquivalentTo(p12);
		assertThat(p14).isNotEquivalentTo(p15);

		assertThat(p15).isNotEquivalentTo(p11);
		assertThat(p15).isNotEquivalentTo(p14);

		assertThat(p16).isEquivalentTo(p13);
		assertThat(p16).isNotEquivalentTo(p14);

		assertThat(p17).isEquivalentTo(p18);
		assertThat(p17).isNotEquivalentTo(p15);
		assertThat(p18).isEquivalentTo(p17);

		assertThat(p21).isEquivalentTo(p21);
		assertThat(p21).isEquivalentTo(p22);
		assertThat(p21).isNotEquivalentTo(p23);
		assertThat(p21).isEquivalentTo(p24);
		assertThat(p21).isNotEquivalentTo(p25);
		assertThat(p21).isNotEquivalentTo(p26);
		assertThat(p21).isNotEquivalentTo(p27);
		assertThat(p21).isNotEquivalentTo(p28);

		assertThat(p22).isEquivalentTo(p21);
		assertThat(p22).isEquivalentTo(p24);

		assertThat(p23).isEquivalentTo(p26);
		assertThat(p23).isNotEquivalentTo(p24);
		assertThat(p23).isNotEquivalentTo(p25);

		assertThat(p24).isEquivalentTo(p21);
		assertThat(p24).isEquivalentTo(p22);
		assertThat(p24).isNotEquivalentTo(p25);

		assertThat(p25).isNotEquivalentTo(p21);
		assertThat(p25).isNotEquivalentTo(p24);

		assertThat(p26).isEquivalentTo(p23);
		assertThat(p26).isNotEquivalentTo(p24);

		assertThat(p27).isEquivalentTo(p28);
		assertThat(p27).isNotEquivalentTo(p25);
		assertThat(p28).isEquivalentTo(p27);

		PermissionCollection pc = p14.newPermissionCollection();

		assertThat(pc).hasNoElements();

		assertThat(pc).doesNotImply(p21);

		assertThat(pc).accepts(p14);
		assertThat(pc).implies(p21);
		assertThat(pc).implies(p22);
		assertThat(pc).doesNotImply(p23);
		assertThat(pc).implies(p24);
		assertThat(pc).implies(p25);
		assertThat(pc).doesNotImply(p26);
		assertThat(pc).doesNotImply(p27);
		assertThat(pc).doesNotImply(p28);

		assertThat(pc).accepts(p13);
		assertThat(pc).implies(p21);
		assertThat(pc).implies(p22);
		assertThat(pc).implies(p23);
		assertThat(pc).implies(p24);
		assertThat(pc).implies(p25);
		assertThat(pc).implies(p26);
		assertThat(pc).implies(p27);
		assertThat(pc).implies(p28);
		assertThat(pc).isSerializable();

		pc = p17.newPermissionCollection();
		assertThat(pc).accepts(p17);
		assertThat(pc).implies(p21);
		assertThat(pc).implies(p22);
		assertThat(pc).implies(p23);
		assertThat(pc).implies(p24);
		assertThat(pc).implies(p25);
		assertThat(pc).implies(p26);
		assertThat(pc).implies(p27);
		assertThat(pc).implies(p28);

		assertThat(pc).rejects(op);
		assertThat(pc).isSerializable();

		pc = p18.newPermissionCollection();

		assertThat(pc).accepts(p18);
		assertThat(pc).implies(p21);
		assertThat(pc).implies(p22);
		assertThat(pc).implies(p23);
		assertThat(pc).implies(p24);
		assertThat(pc).implies(p25);
		assertThat(pc).implies(p26);
		assertThat(pc).implies(p27);
		assertThat(pc).implies(p28);
		assertThat(pc).isSerializable();

		assertThat(pc).rejects(p22);

		pc.setReadOnly();

		assertThat(pc).rejects(p12);

		assertThat(pc).hasElements();

		assertThat(p11).isSerializable();
		assertThat(p12).isSerializable();
		assertThat(p13).isSerializable();
		assertThat(p14).isSerializable();
		assertThat(p15).isSerializable();
		assertThat(p16).isSerializable();
		assertThat(p17).isSerializable();
		assertThat(p18).isSerializable();
		assertThat(pc).isSerializable();

		assertThat(p21).isNotSerializable();
		assertThat(p22).isNotSerializable();
		assertThat(p23).isNotSerializable();
		assertThat(p24).isNotSerializable();
		assertThat(p25).isNotSerializable();
		assertThat(p26).isNotSerializable();
		assertThat(p27).isNotSerializable();
		assertThat(p28).isNotSerializable();
	}

	@Test
	public void testActionImplications() {
		EndpointPermission read = new EndpointPermission("*", "read");
		EndpointPermission export = new EndpointPermission("*", "export");
		EndpointPermission importx = new EndpointPermission("*", "import");

		Assertions.assertThat(read.getActions()).isEqualTo("read");
		Assertions.assertThat(export.getActions()).isEqualTo("read,export");
		Assertions.assertThat(importx.getActions()).isEqualTo("read,import");

		assertThat(export).implies(export);
		assertThat(importx).implies(importx);
		assertThat(read).implies(read);

		assertThat(export).doesNotImply(importx);
		assertThat(export).implies(read);

		assertThat(importx).doesNotImply(export);
		assertThat(importx).implies(read);

		assertThat(read).doesNotImply(importx);
		assertThat(read).doesNotImply(export);

		String someId = "someId";
		Map<String, Object> ep = new HashMap<String, Object>();
		ep.put(ENDPOINT_ID, someId);
		ep.put(OBJECTCLASS, new String[] {"foo"});
		ep.put(SERVICE_IMPORTED_CONFIGS, "config");
		EndpointDescription ed = new EndpointDescription(ep);
		String filterString = "(" + ENDPOINT_ID + "=" + someId + ")";

		EndpointPermission importid = new EndpointPermission(filterString,
				"import");
		EndpointPermission both = new EndpointPermission(ed, null,
				"import,export");
		PermissionCollection pc = importx.newPermissionCollection();
		assertThat(pc).accepts(export);
		assertThat(pc).accepts(importid);
		assertThat(pc).implies(both);
	}

	@Test
	public void testFiltersName() {
		EndpointPermission p31 = new EndpointPermission(
				"  (endpoint.id  =com.foo.service2)", "import");
		EndpointPermission p32 = new EndpointPermission(
				"(endpoint.id=com.foo.*)", "import");
		EndpointPermission p33 = new EndpointPermission("(endpoint.id=com.*)",
				"import");
		EndpointPermission p34 = new EndpointPermission("(endpoint.id=*)",
				"import");
		Map<String, Object> ep = new HashMap<String, Object>();
		ep.put(ENDPOINT_ID, "com.foo.service2");
		ep.put(OBJECTCLASS, new String[] {"foo"});
		ep.put(SERVICE_IMPORTED_CONFIGS, "config");
		EndpointDescription ed = new EndpointDescription(ep);
		EndpointPermission p35 = new EndpointPermission(ed, null, "import");
		EndpointPermission p38 = new EndpointPermission("*", "import");

		assertThat(p31).implies(p35);
		assertThat(p32).implies(p35);
		assertThat(p33).implies(p35);
		assertThat(p34).implies(p35);
		assertThat(p38).implies(p35);

		assertThat(p31).doesNotImply(p31);
		assertThat(p31).doesNotImply(p32);
		assertThat(p31).doesNotImply(p33);
		assertThat(p31).doesNotImply(p34);

		assertThat(p31).doesNotImply(p38);
		assertThat(p32).doesNotImply(p38);
		assertThat(p33).doesNotImply(p38);
		assertThat(p34).doesNotImply(p38);
		assertThat(p35).doesNotImply(p38);
		assertThat(p38).implies(p38);

		PermissionCollection pc = p31.newPermissionCollection();
		assertThat(pc).hasNoElements();

		assertThat(pc).accepts(p31);

		assertThat(pc).doesNotImply(p31);
		assertThat(pc).doesNotImply(p32);
		assertThat(pc).doesNotImply(p33);
		assertThat(pc).doesNotImply(p34);

		assertThat(pc).implies(p35);
		assertThat(pc).doesNotImply(p38);

		assertThat(pc).accepts(p32);
		assertThat(pc).implies(p35);
		assertThat(pc).doesNotImply(p38);

		assertThat(pc).accepts(p33);
		assertThat(pc).implies(p35);
		assertThat(pc).doesNotImply(p38);

		assertThat(pc).accepts(p34);
		assertThat(pc).implies(p35);
		assertThat(pc).doesNotImply(p38);

		assertThat(pc).hasElements();
		assertThat(pc).isSerializable();

		pc = p32.newPermissionCollection();
		assertThat(pc).accepts(p32);
		assertThat(pc).implies(p35);
		assertThat(pc).doesNotImply(p38);
		assertThat(pc).isSerializable();

		pc = p33.newPermissionCollection();
		assertThat(pc).accepts(p33);
		assertThat(pc).implies(p35);
		assertThat(pc).doesNotImply(p38);
		assertThat(pc).isSerializable();

		pc = p34.newPermissionCollection();
		assertThat(pc).accepts(p34);
		assertThat(pc).implies(p35);
		assertThat(pc).doesNotImply(p38);

		assertThat(p31).isSerializable();
		assertThat(p32).isSerializable();
		assertThat(p33).isSerializable();
		assertThat(p34).isSerializable();
		assertThat(p35).isNotSerializable();
		assertThat(p38).isSerializable();
		assertThat(pc).isSerializable();
	}

	@Test
	public void testFiltersProperties() {

		EndpointPermission p41 = new EndpointPermission("(id=2)", "import");
		EndpointPermission p42 = new EndpointPermission("(location=test.*)",
				"import");
		EndpointPermission p43 = new EndpointPermission("(name=test.*)",
				"import");
		EndpointPermission p45 = new EndpointPermission(
				"(endpoint.id=com.foo.*)", "import");

		Map<String, Object> ep1 = new HashMap<String, Object>();
		ep1.put(ENDPOINT_ID, "com.foo.service2");
		ep1.put(OBJECTCLASS, new String[] {"foo"});
		ep1.put(SERVICE_IMPORTED_CONFIGS, "config");
		ep1.put("id", "2");
		ep1.put("location", "test.location");
		ep1.put("name", "test.bsn");
		EndpointPermission p46 = new EndpointPermission(
				new EndpointDescription(ep1), null, "import");
		Map<String, Object> ep2 = new HashMap<String, Object>();
		ep2.put(ENDPOINT_ID, "com.bar.service2");
		ep2.put(OBJECTCLASS, new String[] {"foo"});
		ep2.put(SERVICE_IMPORTED_CONFIGS, "config");
		ep2.put("id", "3");
		ep2.put("location", "not.location");
		ep2.put("name", "not.bsn");
		EndpointPermission p47 = new EndpointPermission(
				new EndpointDescription(ep2), null, "import");

		assertThat(p41).implies(p46);
		assertThat(p42).implies(p46);
		assertThat(p43).implies(p46);
		assertThat(p45).implies(p46);

		assertThat(p41).doesNotImply(p47);
		assertThat(p42).doesNotImply(p47);
		assertThat(p43).doesNotImply(p47);
		assertThat(p45).doesNotImply(p47);

		assertThat(p41).doesNotImply(p41);
		assertThat(p41).doesNotImply(p42);
		assertThat(p41).doesNotImply(p43);
		assertThat(p41).doesNotImply(p45);

		PermissionCollection pc = p41.newPermissionCollection();
		assertThat(pc).hasNoElements();

		assertThat(pc).accepts(p43);
		assertThat(pc).implies(p46);
		assertThat(pc).doesNotImply(p47);
		assertThat(pc).isSerializable();

		pc = p41.newPermissionCollection();
		assertThat(pc).accepts(p42);
		assertThat(pc).implies(p46);
		assertThat(pc).doesNotImply(p47);
		assertThat(pc).isSerializable();

		pc = p41.newPermissionCollection();
		assertThat(pc).accepts(p41);
		assertThat(pc).implies(p46);
		assertThat(pc).doesNotImply(p47);
		assertThat(pc).hasElements();

		// throw IllegalArgumentException If specified permission to be
		// added was constructed with EndpointDescription.
		assertThat(pc).rejects(p46);
		assertThat(pc).rejects(p47);

		assertThat(p41).isSerializable();
		assertThat(p42).isSerializable();
		assertThat(p43).isSerializable();
		assertThat(p45).isSerializable();
		assertThat(p46).isNotSerializable();
		assertThat(p47).isNotSerializable();
		assertThat(pc).isSerializable();
	}

	@Test
	public void testPermissionCollection() {
		EndpointPermission p51 = new EndpointPermission(
				"  (endpoint.id  =com.foo.service2)", "import");
		EndpointPermission p52 = new EndpointPermission(
				"(endpoint.id=com.foo.*)", "import");
		EndpointPermission p53 = new EndpointPermission("(endpoint.id=com.*)",
				"import");
		EndpointPermission p54 = new EndpointPermission("(endpoint.id=*)",
				"import");
		Map<String, Object> ep = new HashMap<String, Object>();
		ep.put(ENDPOINT_ID, "com.foo.service2");
		ep.put(OBJECTCLASS, new String[] {"foo"});
		ep.put(SERVICE_IMPORTED_CONFIGS, "config");
		EndpointDescription ed = new EndpointDescription(ep);
		EndpointPermission p55 = new EndpointPermission(ed, null, "import");
		EndpointPermission p59 = new EndpointPermission("*", "import");

		Map<String, Object> ep1 = new HashMap<String, Object>();
		ep1.put(ENDPOINT_ID, "com.foo.service2");
		ep1.put(OBJECTCLASS, new String[] {"foo"});
		ep1.put(SERVICE_IMPORTED_CONFIGS, "config");
		ep1.put("id", "2");
		ep1.put("location", "test.location");
		ep1.put("name", "test.bsn");
		EndpointPermission p5a = new EndpointPermission(
				new EndpointDescription(ep1), null, "import");
		Map<String, Object> ep2 = new HashMap<String, Object>();
		ep2.put(ENDPOINT_ID, "com.bar.service2");
		ep2.put(OBJECTCLASS, new String[] {"foo"});
		ep2.put(SERVICE_IMPORTED_CONFIGS, "config");
		ep2.put("id", "3");
		ep2.put("location", "not.location");
		ep2.put("name", "not.bsn");
		EndpointPermission p5b = new EndpointPermission(
				new EndpointDescription(ep2), null, "import");

		PermissionCollection pc;
		pc = p51.newPermissionCollection();
		assertThat(pc).accepts(p51);
		assertThat(p51).implies(p5a);
		assertThat(pc).implies(p5a);
		assertThat(p51).implies(p55);
		assertThat(pc).implies(p55);
		assertThat(p51).doesNotImply(p5b);
		assertThat(pc).doesNotImply(p5b);
		assertThat(pc).isSerializable();

		pc = p51.newPermissionCollection();
		assertThat(pc).accepts(p51);
		assertThat(pc).implies(p5a);
		assertThat(pc).implies(p55);
		assertThat(pc).doesNotImply(p5b);
		assertThat(pc).isSerializable();

		pc = p52.newPermissionCollection();
		assertThat(pc).accepts(p52);
		assertThat(pc).implies(p5a);
		assertThat(pc).implies(p55);
		assertThat(pc).doesNotImply(p5b);
		assertThat(pc).isSerializable();

		pc = p52.newPermissionCollection();
		assertThat(pc).accepts(p52);
		assertThat(p52).implies(p5a);
		assertThat(pc).implies(p5a);
		assertThat(p52).implies(p55);
		assertThat(pc).implies(p55);
		assertThat(p52).doesNotImply(p5b);
		assertThat(pc).doesNotImply(p5b);
		assertThat(pc).isSerializable();

		pc = p53.newPermissionCollection();
		assertThat(pc).accepts(p53);
		assertThat(pc).implies(p5a);
		assertThat(pc).implies(p55);
		assertThat(pc).implies(p5b);
		assertThat(pc).isSerializable();

		pc = p53.newPermissionCollection();
		assertThat(pc).accepts(p53);
		assertThat(p53).implies(p5a);
		assertThat(pc).implies(p5a);
		assertThat(p53).implies(p55);
		assertThat(pc).implies(p55);
		assertThat(p53).implies(p5b);
		assertThat(pc).implies(p5b);
		assertThat(pc).isSerializable();

		pc = p54.newPermissionCollection();
		assertThat(pc).accepts(p54);
		assertThat(pc).accepts(p59);
		assertThat(pc).implies(p5a);
		assertThat(pc).implies(p55);
		assertThat(pc).implies(p5b);
		assertThat(pc).isSerializable();

		pc = p54.newPermissionCollection();
		assertThat(pc).accepts(p54);
		assertThat(p54).implies(p5a);
		assertThat(pc).implies(p5a);
		assertThat(p54).implies(p55);
		assertThat(pc).implies(p55);
		assertThat(p54).implies(p5b);
		assertThat(pc).implies(p5b);
		assertThat(pc).isSerializable();

		pc = p59.newPermissionCollection();
		assertThat(pc).accepts(p59);
		assertThat(p59).implies(p5a);
		assertThat(pc).implies(p5a);
		assertThat(p59).implies(p55);
		assertThat(pc).implies(p55);
		assertThat(p59).implies(p5b);
		assertThat(pc).implies(p5b);
		assertThat(pc).isSerializable();
	}

	@Test
	public void testLocalUUID() {
		String someUUID = "some.uuid";
		EndpointPermission p61 = new EndpointPermission(
				"  (endpoint.framework.uuid  =some.uuid)", "import");
		EndpointPermission p62 = new EndpointPermission(
				"(endpoint.framework.uuid=<<LOCAL>>)", "import");
		Map<String, Object> ep = new HashMap<String, Object>();
		ep.put(ENDPOINT_ID, "com.foo.service2");
		ep.put(OBJECTCLASS, new String[] {"foo"});
		ep.put(SERVICE_IMPORTED_CONFIGS, "config");
		ep.put(ENDPOINT_FRAMEWORK_UUID, someUUID);
		EndpointDescription ed = new EndpointDescription(ep);
		EndpointPermission p63 = new EndpointPermission(ed, null, "import");
		EndpointPermission p64 = new EndpointPermission(ed, "other.uuid",
				"import");
		EndpointPermission p65 = new EndpointPermission(ed, someUUID, "import");

		assertThat(p61).implies(p63);
		assertThat(p61).implies(p64);
		assertThat(p61).implies(p65);

		assertThat(p62).doesNotImply(p63);
		assertThat(p62).doesNotImply(p64);
		assertThat(p62).implies(p65);
	}

	private void invalidEndpointPermission(String filterString, String actions) {
		try {
			EndpointPermission p = new EndpointPermission(filterString, actions);
			fail(p + " created with invalid actions");
		}
		catch (IllegalArgumentException e) {
			// expected
		}
	}

	private static void invalidEndpointPermission(EndpointDescription endpoint,
			String localFrameworkUUID, String actions) {
		try {
			EndpointPermission p = new EndpointPermission(endpoint,
					localFrameworkUUID, actions);
			fail(p + " created with invalid arguments");
		}
		catch (IllegalArgumentException e) {
			// expected
		}
	}

}
