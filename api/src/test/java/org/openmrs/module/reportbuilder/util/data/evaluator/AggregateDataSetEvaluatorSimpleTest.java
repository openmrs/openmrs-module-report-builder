/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.reportbuilder.util.data.evaluator;

import static org.junit.Assert.assertEquals;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.openmrs.module.reportbuilder.model.ValueHolder;
import org.openmrs.module.reporting.dataset.DataSetRow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Simple unit tests for {@link AggregateDataSetEvaluator#placesValuesToDataSetRow}. Regression
 * coverage for the age disaggregation matching: generated indicator SQL emits the age group label
 * live from the dimension table, so when a label is renamed after a report was compiled, the
 * label-to-code map must still route the value to the compiled placeholder.
 */
public class AggregateDataSetEvaluatorSimpleTest {
	
	private final ObjectMapper mapper = new ObjectMapper();
	
	private JsonNode reportField(String json) throws Exception {
		return mapper.readTree(json);
	}
	
	@Test
	public void shouldMatchByCodeWhenLabelRenamed() throws Exception {
		JsonNode field = reportField("{\"values\":[{" + "\"dissaggregations1\":\"20yrs+\",\"disaggregation_code\":\"Y20P\","
		        + "\"dissaggregations2\":\"F\",\"value_place_holder\":\"HT04d_Y20P_F\"}]}");
		
		java.util.List<ValueHolder> values = new java.util.ArrayList<ValueHolder>();
		values.add(new ValueHolder("20+ yrs", "F", "3"));
		values.add(new ValueHolder("20+ yrs", "M", "1"));
		
		Map<String, String> codeByLabel = new HashMap<String, String>();
		codeByLabel.put("20+ yrs", "Y20P");
		
		DataSetRow row = AggregateDataSetEvaluator.placesValuesToDataSetRow(field, values, new DataSetRow(), codeByLabel);
		
		assertEquals("Renamed label must reach the compiled placeholder via its code", 3,
		    ((Integer) row.getColumnValue("HT04d_Y20P_F")).intValue());
	}
	
	@Test
	public void shouldStillMatchByLabelForLegacyDesigns() throws Exception {
		JsonNode field = reportField("{\"values\":[{" + "\"dissaggregations1\":\"20+ yrs\","
		        + "\"dissaggregations2\":\"F\",\"value_place_holder\":\"HT04d_20plus_yrs_F\"}]}");
		
		java.util.List<ValueHolder> values = new java.util.ArrayList<ValueHolder>();
		values.add(new ValueHolder("20+ yrs", "F", "5"));
		
		DataSetRow row = AggregateDataSetEvaluator.placesValuesToDataSetRow(field, values, new DataSetRow(),
		    Collections.<String, String> emptyMap());
		
		assertEquals("Designs without a code stamp keep matching by label", 5,
		    ((Integer) row.getColumnValue("HT04d_20plus_yrs_F")).intValue());
	}
	
	@Test
	public void shouldDefaultToZeroWhenNothingMatches() throws Exception {
		JsonNode field = reportField("{\"values\":[{" + "\"dissaggregations1\":\"20yrs+\",\"disaggregation_code\":\"Y20P\","
		        + "\"dissaggregations2\":\"F\",\"value_place_holder\":\"HT04d_Y20P_F\"}]}");
		
		java.util.List<ValueHolder> values = new java.util.ArrayList<ValueHolder>();
		values.add(new ValueHolder("unrelated", "F", "9"));
		
		DataSetRow row = AggregateDataSetEvaluator.placesValuesToDataSetRow(field, values, new DataSetRow(),
		    Collections.<String, String> emptyMap());
		
		assertEquals(0, ((Integer) row.getColumnValue("HT04d_Y20P_F")).intValue());
	}
}
