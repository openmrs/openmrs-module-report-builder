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
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.openmrs.module.reporting.dataset.DataSet;
import org.openmrs.module.reporting.dataset.DataSetColumn;
import org.openmrs.module.reporting.dataset.DataSetMetaData;
import org.openmrs.module.reporting.dataset.DataSetRow;
import org.openmrs.module.reporting.dataset.definition.DataSetDefinition;
import org.openmrs.module.reporting.evaluation.EvaluationContext;
import org.openmrs.module.reporting.report.ReportData;
import org.openmrs.module.reporting.report.ReportDesign;
import org.openmrs.module.reporting.report.ReportDesignResource;

/**
 * Regression guard for the model-based rendering refactor: the HTML renderers now emit from
 * {@link ReportTableModel}, and these exact-string assertions pin their output so the CSV/Excel
 * extraction did not change a single byte of the HTML.
 */
public class DownloadRenderingRegressionTest {
	
	private final ReportDesignHtmlRenderer aggregateRenderer = new ReportDesignHtmlRenderer();
	
	private final LinelistHtmlRenderer linelistRenderer = new LinelistHtmlRenderer();
	
	private static final String AGGREGATE_CSS = "body{font-family:Arial,Helvetica,sans-serif;margin:12px;color:#222;}"
	        + ".reportTitle{font-size:16px;font-weight:bold;margin-bottom:10px;}"
	        + ".sectionTitle{margin:18px 0 8px 0;font-size:14px;font-weight:bold;}"
	        + "table{border-collapse:collapse;width:100%;margin-bottom:22px;}"
	        + "th,td{border:1px solid #ddd;padding:6px;font-size:12px;vertical-align:middle;}"
	        + "th{background:#f6f6f6;text-align:center;}" + "td.label{text-align:left;white-space:nowrap;}"
	        + "td.val{text-align:right;}" + "tr.sectionRow td{background:#f0f0f0;font-weight:bold;font-size:13px;}"
	        + "tr.groupRow td{background:#fafafa;font-weight:bold;}" + "tr.labelRow td{background:#fcfcfc;font-weight:600;}"
	        + "tr.spacerRow td{background:#fff;height:10px;border-left:none;border-right:none;}"
	        + ".indent{display:inline-block;}";
	
	private static final String LINELIST_CSS = "body{font-family:Arial,Helvetica,sans-serif;margin:12px;color:#222;}"
	        + ".reportTitle{font-size:16px;font-weight:bold;margin-bottom:10px;}"
	        + ".reportDescription{font-size:13px;color:#666;margin-bottom:15px;}"
	        + ".rowCount{margin-top:10px;font-size:12px;color:#666;}"
	        + "table.linelist-table{border-collapse:collapse;width:100%;margin-bottom:20px;}"
	        + "table.linelist-table th,table.linelist-table td{border:1px solid #ddd;padding:8px;"
	        + "font-size:12px;text-align:left;}" + "table.linelist-table th{font-weight:bold;position:sticky;top:0;}"
	        + "table.linelist-table tbody tr:nth-child(even){background:#f9f9f9;}"
	        + "table.linelist-table tbody tr:hover{background:#f0f0f0;}";
	
	@Test
	public void aggregateHtmlShouldBeUnchangedByModelRefactor() {
		String template = "{\"version\":1,\"name\":\"T\",\"code\":\"T\",\"template\":\"section-tabular\","
		        + "\"defaultValue\":0,\"groups\":[{\"title\":\"G\",\"rows\":["
		        + "{\"type\":\"section-label\",\"label\":\"Section A\",\"indent\":1},"
		        + "{\"type\":\"indicator\",\"label\":\"HT01\",\"code\":\"HT01\",\"showTotal\":true}]}]}";
		
		Map<String, Object> values = new HashMap<String, Object>();
		values.put("HT01_TOTAL", 3);
		
		String expected = "<!doctype html><html><head><meta charset='utf-8'/>"
		        + "<style>"
		        + AGGREGATE_CSS
		        + "</style></head><body>"
		        + "<div class='reportTitle'>T</div>"
		        + "<div class='sectionTitle'>G</div><table><thead><tr><th>Indicator</th><th>Value</th></tr></thead><tbody>"
		        + "<tr class='sectionRow'><td colspan='2'><span class='indent' style='width:14px'></span>Section A</td></tr>"
		        + "<tr><td class='label'>HT01</td><td class='val'>3</td>" + "<td class='val'>3</td></tr>"
		        + "</tbody></table></body></html>";
		
		assertEquals(expected, aggregateRenderer.renderHtmlFinal(template, values));
	}
	
	@Test
	public void aggregateHtmlWithDisaggregationShouldBeUnchanged() {
		String template = "{\"version\":1,\"name\":\"T\",\"code\":\"T\",\"template\":\"section-tabular\","
		        + "\"defaultValue\":0,\"groups\":[{\"title\":\"G\",\"rows\":[{"
		        + "\"type\":\"indicator\",\"label\":\"HT01a\",\"code\":\"HT01a\","
		        + "\"showTotal\":true,\"showDisaggregation\":true,"
		        + "\"keyPattern\":\"{code}_{sex}\",\"dims\":{\"sex\":\"sex\"}}]}],"
		        + "\"dimensions\":{\"sex\":[{\"id\":\"F\",\"label\":\"Female\"},{\"id\":\"M\",\"label\":\"Male\"}]}}";
		
		Map<String, Object> values = new HashMap<String, Object>();
		values.put("HT01a_F", 5);
		values.put("HT01a_M", 7);
		
		String expected = "<!doctype html><html><head><meta charset='utf-8'/>" + "<style>" + AGGREGATE_CSS
		        + "</style></head><body>" + "<div class='reportTitle'>T</div>"
		        + "<div class='sectionTitle'>G</div><table><thead>"
		        + "<tr><th rowspan='1'>Indicator</th><th>Female</th><th>Male</th>" + "<th rowspan='1'>Total</th></tr>"
		        + "</thead><tbody>" + "<tr><td class='label'>HT01a</td><td class='val'>5</td><td class='val'>7</td>"
		        + "<td class='val'>12</td></tr>" + "</tbody></table></body></html>";
		
		assertEquals(expected, aggregateRenderer.renderHtmlFinal(template, values));
	}
	
	@Test
	public void linelistHtmlShouldBeUnchangedByModelRefactor() {
		String config = "{\"name\":\"Linelist\",\"description\":\"Test\","
		        + "\"dataSetDefinitions\":[{\"type\":\"PATIENT_DATA_SET\","
		        + "\"columns\":[{\"name\":\"Patient Name\"},{\"name\":\"Weight\"}]}]}";
		
		Map<String, Object> row = new LinkedHashMap<String, Object>();
		row.put("patient_name", "Alice");
		row.put("weight", 60);
		
		String expected = "<!doctype html><html><head><meta charset='utf-'/>" + "<style>" + LINELIST_CSS
		        + "</style></head><body>" + "<div class='reportTitle'>Linelist</div>"
		        + "<div class='reportDescription'>Test</div>"
		        + "<table class='linelist-table'><thead><tr><th>Patient Name</th><th>Weight</th></tr></thead><tbody>"
		        + "<tr><td>Alice</td><td>60</td></tr></tbody></table>" + "<div class='rowCount'>1 rows</div></body></html>";
		
		assertEquals(expected, linelistRenderer.convert(reportData(row), designWithConfig(config)).html);
	}
	
	@Test
	public void linelistModelShouldOrderConfigColumnsFirstThenExpanded() {
		String config = "{\"name\":\"Linelist\",\"description\":\"\","
		        + "\"dataSetDefinitions\":[{\"type\":\"PATIENT_DATA_SET\","
		        + "\"columns\":[{\"name\":\"Patient Name\"},{\"name\":\"Weight\"}]}]}";
		
		Map<String, Object> row = new LinkedHashMap<String, Object>();
		row.put("patient_name", "Alice");
		row.put("weight", 60);
		row.put("weight_1", 55);
		row.put("weight_2", 60);
		
		LinelistReportModeler modeler = new LinelistReportModeler();
		ReportTableModel model = modeler.buildModel(reportData(row), designWithConfig(config));
		
		List<String> headers = new ArrayList<String>();
		for (ReportTableModel.Cell cell : model.tables.get(0).headerRows.get(0).cells) {
			headers.add(cell.text);
		}
		assertEquals(Arrays.asList("Patient Name", "Weight", "Weight 1", "Weight 2"), headers);
		
		List<String> values = new ArrayList<String>();
		for (ReportTableModel.Cell cell : model.tables.get(0).bodyRows.get(0).cells) {
			values.add(cell.text);
		}
		assertEquals(Arrays.asList("Alice", "60", "55", "60"), values);
		assertEquals("1 rows", model.footerText);
	}
	
	/* -------------------- fixtures -------------------- */
	
	private ReportData reportData(Map<String, Object>... rows) {
		final List<Map<String, Object>> data = Arrays.asList(rows);
		DataSet dataSet = new DataSet() {
			
			@Override
			public Iterator<DataSetRow> iterator() {
				List<DataSetRow> list = new ArrayList<DataSetRow>();
				for (Map<String, Object> row : data) {
					DataSetRow r = new DataSetRow();
					for (Map.Entry<String, Object> e : row.entrySet()) {
						r.addColumnValue(new DataSetColumn(e.getKey(), e.getKey(), Object.class), e.getValue());
					}
					list.add(r);
				}
				return list.iterator();
			}
			
			@Override
			public DataSetMetaData getMetaData() {
				return null;
			}
			
			@Override
			public DataSetDefinition getDefinition() {
				return null;
			}
			
			@Override
			public EvaluationContext getContext() {
				return null;
			}
		};
		
		Map<String, DataSet> dataSets = new HashMap<String, DataSet>();
		dataSets.put("data", dataSet);
		
		ReportData reportData = new ReportData();
		reportData.setDataSets(dataSets);
		return reportData;
	}
	
	private ReportDesign designWithConfig(String configJson) {
		ReportDesign design = new ReportDesign();
		ReportDesignResource resource = new ReportDesignResource();
		resource.setName("template");
		resource.setContents(configJson.getBytes(StandardCharsets.UTF_8));
		resource.setReportDesign(design);
		design.addResource(resource);
		return design;
	}
	
	@Test
	public void linelistPayloadShouldSurviveAReportWithNoColumns() {
		// Config declares no PATIENT_DATA_SET columns and there is no data: the model has no
		// tables, and the payload must come back empty instead of throwing
		String config = "{\"name\":\"Empty\",\"description\":\"\","
		        + "\"dataSetDefinitions\":[{\"type\":\"PATIENT_DATA_SET\",\"columns\":[]}]}";
		
		LinelistHtmlRenderer.Result result = linelistRenderer.convert(reportData(), designWithConfig(config));
		
		assertTrue(result.payloadJson.contains("\"columns\" : [ ]"));
		assertTrue(result.payloadJson.contains("\"rowCount\" : 0"));
		assertTrue(result.renderedOutputJson.contains("\"html\""));
	}
}
