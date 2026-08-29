/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.reportbuilder.util;

import static org.junit.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.junit.Test;

/**
 * Simple unit tests for {@link ReportDesignHtmlRenderer} value lookups. Regression coverage for the
 * render-level key tolerance: design placeholders spell "+" as "plus" and "&lt;"/"&gt;" as
 * "lt"/"gt" (compile-time sanitize), while runtime column keys may carry the raw character or have
 * it stripped, so lookups must fall back to those spellings when the exact key misses.
 */
public class ReportDesignHtmlRendererSimpleTest {
	
	private static final String TEMPLATE_JSON = "{"
	        + "\"version\":1,\"name\":\"T\",\"code\":\"T\",\"template\":\"section-tabular\","
	        + "\"arrayName\":\"dataValues\",\"defaultValue\":0," + "\"groups\":[{\"title\":\"G\",\"rows\":[{"
	        + "\"type\":\"indicator\",\"label\":\"HT01a\",\"code\":\"HT01a\",\"indent\":1,"
	        + "\"showTotal\":true,\"showDisaggregation\":true,"
	        + "\"keyPattern\":\"{code}_{age}_{sex}\",\"dims\":{\"age\":\"agecat\",\"sex\":\"sex\"}}]}],"
	        + "\"dimensions\":{" + "\"sex\":[{\"id\":\"F\",\"label\":\"Female\"},{\"id\":\"M\",\"label\":\"Male\"}],"
	        + "\"agecat\":[{\"id\":\"20yrsplus\",\"label\":\"20yrs+\"}]}}";
	
	private static final String TEMPLATE_LT_JSON = TEMPLATE_JSON.replace("\"20yrsplus\",\"label\":\"20yrs+\"",
	    "\"lt10_yrs\",\"label\":\"<10 yrs\"");
	
	private static final String TEMPLATE_DOT_JSON = TEMPLATE_JSON.replace("\"code\":\"HT01a\",\"indent\":1",
	    "\"code\":\"HT01a.\",\"indent\":1");
	
	private final ReportDesignHtmlRenderer renderer = new ReportDesignHtmlRenderer();
	
	private final ObjectMapper mapper = new ObjectMapper();
	
	private Integer findDataValue(String payloadJson, String dataElement) throws Exception {
		JsonNode values = mapper.readTree(payloadJson).path("json").path("dataValues");
		if (!values.isArray()) {
			return null;
		}
		Iterator<JsonNode> it = values.elements();
		while (it.hasNext()) {
			JsonNode dv = it.next();
			if (dataElement.equals(dv.path("dataElement").asText(null))) {
				return dv.path("value").asInt();
			}
		}
		return null;
	}
	
	@Test
	public void shouldResolveExactPlaceholderKeys() throws Exception {
		Map<String, Object> values = new HashMap<String, Object>();
		values.put("HT01a_20yrsplus_F", 5);
		values.put("HT01a_TOTAL", 9);
		
		String payload = renderer.buildPayloadOnly(TEMPLATE_JSON, values, null);
		
		assertEquals(Integer.valueOf(5), findDataValue(payload, "HT01a_20yrsplus_F"));
		assertEquals(Integer.valueOf(9), findDataValue(payload, "HT01a_TOTAL"));
	}
	
	@Test
	public void shouldFallBackToStrippedPlusSpelling() throws Exception {
		Map<String, Object> values = new HashMap<String, Object>();
		values.put("HT01a_20yrs_F", 7);
		
		String payload = renderer.buildPayloadOnly(TEMPLATE_JSON, values, null);
		
		assertEquals("Placeholder 20yrsplus must resolve against runtime key 20yrs", Integer.valueOf(7),
		    findDataValue(payload, "HT01a_20yrsplus_F"));
	}
	
	@Test
	public void shouldFallBackToStrippedLtSpelling() throws Exception {
		Map<String, Object> values = new HashMap<String, Object>();
		values.put("HT01a_10_yrs_F", 3);
		
		String payload = renderer.buildPayloadOnly(TEMPLATE_LT_JSON, values, null);
		
		assertEquals("Placeholder lt10_yrs must resolve against runtime key 10_yrs", Integer.valueOf(3),
		    findDataValue(payload, "HT01a_lt10_yrs_F"));
	}
	
	@Test
	public void shouldFallBackToRawPlusSpelling() throws Exception {
		Map<String, Object> values = new HashMap<String, Object>();
		values.put("HT01a_20yrs+_F", 4);
		
		String payload = renderer.buildPayloadOnly(TEMPLATE_JSON, values, null);
		
		assertEquals("Placeholder 20yrsplus must resolve against raw key 20yrs+", Integer.valueOf(4),
		    findDataValue(payload, "HT01a_20yrsplus_F"));
	}
	
	@Test
	public void shouldSanitizeDottedIndicatorCodesWhenBuildingKeys() throws Exception {
		Map<String, Object> values = new HashMap<String, Object>();
		values.put("HT01a_20yrsplus_F", 1);
		values.put("HT01a_TOTAL", 6);
		
		String payload = renderer.buildPayloadOnly(TEMPLATE_DOT_JSON, values, null);
		
		assertEquals("Template code HT01a. must resolve against sanitized data key HT01a_20yrsplus_F", Integer.valueOf(1),
		    findDataValue(payload, "HT01a_20yrsplus_F"));
		assertEquals("Total keys must be built from the sanitized code too", Integer.valueOf(6),
		    findDataValue(payload, "HT01a_TOTAL"));
	}
	
	@Test
	public void shouldFallBackToSummingCellsForTotalWhenNoTotalKey() throws Exception {
		Map<String, Object> values = new HashMap<String, Object>();
		values.put("HT01a_20yrsplus_F", 2);
		values.put("HT01a_20yrsplus_M", 3);
		
		String payload = renderer.buildPayloadOnly(TEMPLATE_JSON, values, null);
		
		assertEquals("Total must fall back to the sum of the row's cells", Integer.valueOf(5),
		    findDataValue(payload, "HT01a_TOTAL"));
	}
	
	@Test
	public void shouldDefaultToZeroWhenNoSpellingMatches() throws Exception {
		Map<String, Object> values = new HashMap<String, Object>();
		values.put("UNRELATED_KEY", 1);
		
		String payload = renderer.buildPayloadOnly(TEMPLATE_JSON, values, null);
		
		assertEquals(Integer.valueOf(0), findDataValue(payload, "HT01a_20yrsplus_F"));
		assertEquals(Integer.valueOf(0), findDataValue(payload, "HT01a_TOTAL"));
	}
}
