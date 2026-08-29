/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.reportbuilder.api.impl;

import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.openmrs.module.reporting.dataset.DataSetColumn;
import org.openmrs.module.reporting.dataset.DataSetRow;
import org.openmrs.module.reporting.dataset.SimpleDataSet;
import org.openmrs.module.reporting.report.ReportData;

/**
 * Simple unit tests for {@link ReportBuilderServiceImpl#extractFlatValues(ReportData)}. Regression
 * coverage for the column-key filter: aggregate placeholder columns are shaped {@code code_age_sex}
 * or {@code code_TOTAL}, and age-group labels only contribute an extra underscore when they happen
 * to contain a separator (e.g. "0-28d" -> "0_28d"). Labels that sanitize to a single token
 * ("20yrsplus", "25yrsplus") must not be filtered out.
 */
public class ExtractFlatValuesSimpleTest {
	
	private ReportData reportDataWithColumns(Map<String, Object> columnValues) {
		DataSetRow row = new DataSetRow();
		for (Map.Entry<String, Object> entry : columnValues.entrySet()) {
			row.addColumnValue(new DataSetColumn(entry.getKey(), entry.getKey(), Object.class), entry.getValue());
		}
		
		SimpleDataSet dataSet = new SimpleDataSet(null, null);
		dataSet.addRow(row);
		
		ReportData reportData = new ReportData();
		Map<String, org.openmrs.module.reporting.dataset.DataSet> dataSets = new HashMap<String, org.openmrs.module.reporting.dataset.DataSet>();
		dataSets.put("defaultDataSet", dataSet);
		reportData.setDataSets(dataSets);
		return reportData;
	}
	
	@Test
	public void shouldKeepKeysWhoseLabelSanitizesToSingleToken() {
		Map<String, Object> columns = new HashMap<String, Object>();
		columns.put("HT01a_20yrsplus_F", 1);
		columns.put("HT01a_20yrsplus_M", 0);
		columns.put("HT02b_25yrsplus_F", 3);
		
		Map<String, Object> out = new ReportBuilderServiceImpl().extractFlatValues(reportDataWithColumns(columns));
		
		Assert.assertEquals("Top-band cells must not be dropped", Integer.valueOf(1), out.get("HT01a_20yrsplus_F"));
		Assert.assertEquals(Integer.valueOf(0), out.get("HT01a_20yrsplus_M"));
		Assert.assertEquals(Integer.valueOf(3), out.get("HT02b_25yrsplus_F"));
	}
	
	@Test
	public void shouldKeepHyphenatedAndCompoundLabelKeys() {
		Map<String, Object> columns = new HashMap<String, Object>();
		columns.put("HT01a_0_28d_F", 2);
		columns.put("HT06b1_20_24_yrs_M", 4);
		
		Map<String, Object> out = new ReportBuilderServiceImpl().extractFlatValues(reportDataWithColumns(columns));
		
		Assert.assertEquals("Hyphen-derived keys must keep working", Integer.valueOf(2), out.get("HT01a_0_28d_F"));
		Assert.assertEquals("Compound-label keys must keep working", Integer.valueOf(4), out.get("HT06b1_20_24_yrs_M"));
	}
	
	@Test
	public void shouldKeepTotalKeys() {
		Map<String, Object> columns = new HashMap<String, Object>();
		columns.put("ANC1_TOTAL", 9);
		columns.put("HT01a_TOTAL", 0);
		
		Map<String, Object> out = new ReportBuilderServiceImpl().extractFlatValues(reportDataWithColumns(columns));
		
		Assert.assertEquals("Total placeholders must not be dropped", Integer.valueOf(9), out.get("ANC1_TOTAL"));
		Assert.assertEquals(Integer.valueOf(0), out.get("HT01a_TOTAL"));
	}
	
	@Test
	public void shouldKeepTwoSegmentKeys() {
		Map<String, Object> columns = new HashMap<String, Object>();
		columns.put("ANC1_MALE", 2);
		columns.put("start_date", 5);
		
		Map<String, Object> out = new ReportBuilderServiceImpl().extractFlatValues(reportDataWithColumns(columns));
		
		Assert.assertEquals("Label-suffixed placeholders must not be dropped", Integer.valueOf(2), out.get("ANC1_MALE"));
		Assert.assertEquals("Other two-segment numeric columns pass as harmless extras", Integer.valueOf(5),
		    out.get("start_date"));
	}
	
	@Test
	public void shouldRejectNonPlaceholderColumns() {
		Map<String, Object> columns = new HashMap<String, Object>();
		columns.put("value", 42);
		columns.put("_leading", 8);
		columns.put("a_b_", 6);
		columns.put("HT01a_0_28d_F", "not numeric");
		
		Map<String, Object> out = new ReportBuilderServiceImpl().extractFlatValues(reportDataWithColumns(columns));
		
		Assert.assertNull("Columns without an internal underscore must be rejected", out.get("value"));
		Assert.assertNull("Leading underscore must be rejected", out.get("_leading"));
		Assert.assertNull("Dangling underscore must be rejected", out.get("a_b_"));
		Assert.assertNull("Non-numeric values must be rejected", out.get("HT01a_0_28d_F"));
	}
}
