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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

/**
 * Tests the aggregate table model: header rowspan/colspan structure, disaggregation combos, total
 * fallback to summed combos, and label/spacer rows. This model drives the HTML, CSV and Excel
 * renderers, so these assertions pin the structure all download formats share.
 */
public class AggregateReportModelerTest {
	
	private static final String TEMPLATE_JSON = "{"
	        + "\"version\":1,\"name\":\"T\",\"code\":\"T\",\"template\":\"section-tabular\","
	        + "\"arrayName\":\"dataValues\",\"defaultValue\":0," + "\"groups\":[{\"title\":\"G\",\"rows\":[{"
	        + "\"type\":\"indicator\",\"label\":\"HT01a\",\"code\":\"HT01a\",\"indent\":1,"
	        + "\"showTotal\":true,\"showDisaggregation\":true,"
	        + "\"keyPattern\":\"{code}_{age}_{sex}\",\"dims\":{\"age\":\"agecat\",\"sex\":\"sex\"}}]}],"
	        + "\"dimensions\":{" + "\"sex\":[{\"id\":\"F\",\"label\":\"Female\"},{\"id\":\"M\",\"label\":\"Male\"}],"
	        + "\"agecat\":[{\"id\":\"20yrsplus\",\"label\":\"20yrs+\"}]}}";
	
	private final AggregateReportModeler modeler = new AggregateReportModeler();
	
	private final ObjectMapper mapper = new ObjectMapper();
	
	private ReportTableModel model(Map<String, Object> values) throws Exception {
		AggregateReportModeler.ReportDesignTemplate tpl = mapper.readValue(TEMPLATE_JSON,
		    AggregateReportModeler.ReportDesignTemplate.class);
		return modeler.buildModel(tpl, values);
	}
	
	@Test
	public void shouldBuildHeaderRowsWithSpans() throws Exception {
		ReportTableModel.Table table = model(new HashMap<String, Object>()).tables.get(0);
		
		assertEquals(2, table.headerRows.size());
		
		ReportTableModel.Row first = table.headerRows.get(0);
		assertEquals(3, first.cells.size());
		assertEquals("Indicator", first.cells.get(0).text);
		assertEquals(2, first.cells.get(0).rowspan);
		assertEquals(1, first.cells.get(0).colspan);
		assertEquals("20yrs+", first.cells.get(1).text);
		assertEquals(2, first.cells.get(1).colspan);
		assertEquals("Total", first.cells.get(2).text);
		assertEquals(2, first.cells.get(2).rowspan);
		
		ReportTableModel.Row second = table.headerRows.get(1);
		assertEquals(2, second.cells.size());
		assertEquals("Female", second.cells.get(0).text);
		assertEquals("Male", second.cells.get(1).text);
		
		assertEquals(4, table.columnCount());
	}
	
	@Test
	public void shouldValueCombosAndFallTotalBackToTheirSum() throws Exception {
		Map<String, Object> values = new HashMap<String, Object>();
		values.put("HT01a_20yrsplus_F", 5);
		values.put("HT01a_20yrsplus_M", 7);
		
		ReportTableModel.Row body = model(values).tables.get(0).bodyRows.get(0);
		
		assertEquals(4, body.cells.size());
		assertEquals("HT01a", body.cells.get(0).text);
		assertEquals(ReportTableModel.Style.LABEL_CELL, body.cells.get(0).style);
		assertEquals(1, body.cells.get(0).indent);
		assertEquals(Integer.valueOf(5), body.cells.get(1).numericValue);
		assertEquals(Integer.valueOf(7), body.cells.get(2).numericValue);
		// No explicit total placeholder: falls back to the sum of the combos
		assertEquals(Integer.valueOf(12), body.cells.get(3).numericValue);
	}
	
	@Test
	public void shouldShowEmptyTotalCellWhenTotalNotShown() throws Exception {
		String template = TEMPLATE_JSON.replace("\"showTotal\":true,", "\"showTotal\":false,");
		AggregateReportModeler.ReportDesignTemplate tpl = mapper.readValue(template,
		    AggregateReportModeler.ReportDesignTemplate.class);
		ReportTableModel model = modeler.buildModel(tpl, new HashMap<String, Object>());
		
		ReportTableModel.Row body = model.tables.get(0).bodyRows.get(0);
		assertEquals(3, body.cells.size());
		
		// No row shows a total, so the Total header column disappears entirely
		assertEquals(2, model.tables.get(0).headerRows.get(0).cells.size());
	}
	
	@Test
	public void shouldBuildLabelAndSpacerRowsSpanningTheTable() throws Exception {
		String template = "{\"version\":1,\"name\":\"T\",\"code\":\"T\",\"template\":\"section-tabular\","
		        + "\"defaultValue\":0,\"groups\":[{\"title\":\"G\",\"rows\":["
		        + "{\"type\":\"section-label\",\"label\":\"Section A\",\"indent\":2},"
		        + "{\"type\":\"group-label\",\"label\":\"Group B\"}," + "{\"type\":\"label\",\"label\":\"Label C\"},"
		        + "{\"type\":\"spacer\"},"
		        + "{\"type\":\"indicator\",\"label\":\"HT01\",\"code\":\"HT01\",\"showTotal\":true}]}]}";
		AggregateReportModeler.ReportDesignTemplate tpl = mapper.readValue(template,
		    AggregateReportModeler.ReportDesignTemplate.class);
		ReportTableModel model = modeler.buildModel(tpl, new HashMap<String, Object>());
		
		ReportTableModel.Table table = model.tables.get(0);
		// Indicator rows carry label + value + total cells (a pre-existing HTML quirk:
		// the no-dims header only declares Indicator/Value, label rows span 2)
		assertEquals(3, table.columnCount());
		
		ReportTableModel.Cell section = table.bodyRows.get(0).cells.get(0);
		assertEquals(ReportTableModel.Style.SECTION_LABEL, section.style);
		assertEquals(2, section.colspan);
		assertEquals("Section A", section.text);
		assertEquals(2, section.indent);
		
		assertEquals(ReportTableModel.Style.GROUP_LABEL, table.bodyRows.get(1).cells.get(0).style);
		assertEquals(ReportTableModel.Style.LABEL_ROW, table.bodyRows.get(2).cells.get(0).style);
		assertEquals(ReportTableModel.Style.SPACER, table.bodyRows.get(3).cells.get(0).style);
		
		// No-dims indicator: value resolves from the total placeholder key
		assertEquals(Integer.valueOf(0), table.bodyRows.get(4).cells.get(1).numericValue);
		assertEquals(Integer.valueOf(0), table.bodyRows.get(4).cells.get(2).numericValue);
	}
	
	@Test
	public void shouldSetEmptyMessageWhenNoGroups() throws Exception {
		AggregateReportModeler.ReportDesignTemplate tpl = new AggregateReportModeler.ReportDesignTemplate();
		ReportTableModel model = modeler.buildModel(tpl, new HashMap<String, Object>());
		
		assertTrue(model.tables.isEmpty());
		assertEquals("No groups defined in template.", model.emptyMessage);
	}
	
	@Test
	public void shouldEmitTitleAndSectionTitle() throws Exception {
		ReportTableModel model = model(new HashMap<String, Object>());
		assertEquals("T", model.title.trim());
		assertEquals("G", model.tables.get(0).sectionTitle.trim());
	}
}
