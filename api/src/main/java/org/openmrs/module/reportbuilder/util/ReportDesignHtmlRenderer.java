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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Renders compiled report design JSON into: 1) HTML preview/final document 2) Payload JSON for
 * downstream exchange 3) Composite output wrapper: { "json": { ... }, "html": "...", "dhis2": { ...
 * } } Expected input format: { "version": 1, "name": "...", "code": "...", "template":
 * "section-tabular", "arrayName": "results", "defaultValue": 0, "groups": [ ... ], "dimensions": {
 * ... }, "dhis2": { ... } } The HTML output is emitted from a {@link ReportTableModel} built by
 * {@link AggregateReportModeler}, which owns all template resolution (dimensions, combos, totals,
 * key lookups). The CSV and Excel renderers consume the same model, keeping every download format
 * faithful to the HTML.
 */
public class ReportDesignHtmlRenderer {
	
	private static final ObjectMapper MAPPER = new ObjectMapper();
	
	private final AggregateReportModeler modeler = new AggregateReportModeler();
	
	public static class Result {
		
		public final String html;
		
		public final String payloadJson;
		
		public final String renderedOutputJson;
		
		public Result(String html, String payloadJson, String renderedOutputJson) {
			this.html = html;
			this.payloadJson = payloadJson;
			this.renderedOutputJson = renderedOutputJson;
		}
	}
	
	public Result convert(String templateJson, Map<String, Object> flatValues, String remapJsonOptional) {
		String html = renderHtmlFinal(templateJson, flatValues);
		String payload = buildPayloadOnly(templateJson, flatValues, remapJsonOptional);
		String rendered = buildRenderedOutputOnly(templateJson, flatValues, remapJsonOptional);
		return new Result(html, payload, rendered);
	}
	
	/* -------------------- FINAL HTML -------------------- */
	
	public String renderHtmlFinal(String templateJson, Map<String, Object> flatValues) {
		try {
			AggregateReportModeler.ReportDesignTemplate tpl = MAPPER.readValue(templateJson,
			    AggregateReportModeler.ReportDesignTemplate.class);
			Map<String, Object> values = flatValues == null ? Collections.<String, Object> emptyMap() : flatValues;
			ReportTableModel model = modeler.buildModel(tpl, values);
			return renderFinalHtmlDocument(model);
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to render FINAL HTML from report design", e);
		}
	}
	
	private String renderFinalHtmlDocument(ReportTableModel model) {
		StringBuilder sb = new StringBuilder();
		sb.append("<!doctype html><html><head><meta charset='utf-8'/>");
		sb.append("<style>").append("body{font-family:Arial,Helvetica,sans-serif;margin:12px;color:#222;}")
		        .append(".reportTitle{font-size:16px;font-weight:bold;margin-bottom:10px;}")
		        .append(".sectionTitle{margin:18px 0 8px 0;font-size:14px;font-weight:bold;}")
		        .append("table{border-collapse:collapse;width:100%;margin-bottom:22px;}")
		        .append("th,td{border:1px solid #ddd;padding:6px;font-size:12px;vertical-align:middle;}")
		        .append("th{background:#f6f6f6;text-align:center;}").append("td.label{text-align:left;white-space:nowrap;}")
		        .append("td.val{text-align:right;}")
		        .append("tr.sectionRow td{background:#f0f0f0;font-weight:bold;font-size:13px;}")
		        .append("tr.groupRow td{background:#fafafa;font-weight:bold;}")
		        .append("tr.labelRow td{background:#fcfcfc;font-weight:600;}")
		        .append("tr.spacerRow td{background:#fff;height:10px;border-left:none;border-right:none;}")
		        .append(".indent{display:inline-block;}").append("</style></head><body>");
		
		if (model.emptyMessage != null && !model.emptyMessage.isEmpty()) {
			sb.append("<div>").append(esc(model.emptyMessage)).append("</div>");
			sb.append("</body></html>");
			return sb.toString();
		}
		
		if (model.title != null && !model.title.trim().isEmpty()) {
			sb.append("<div class='reportTitle'>").append(esc(model.title)).append("</div>");
		}
		
		for (ReportTableModel.Table table : model.tables) {
			sb.append(renderGroupTable(table));
		}
		
		sb.append("</body></html>");
		return sb.toString();
	}
	
	private String renderGroupTable(ReportTableModel.Table table) {
		StringBuilder sb = new StringBuilder();
		sb.append("<div class='sectionTitle'>").append(esc(table.sectionTitle)).append("</div>");
		sb.append("<table>");
		sb.append("<thead>");
		
		for (ReportTableModel.Row row : table.headerRows) {
			sb.append("<tr>");
			for (ReportTableModel.Cell cell : row.cells) {
				sb.append("<th");
				if (cell.rowspan > 1 || cell.emitRowSpanAttr) {
					sb.append(" rowspan='").append(cell.rowspan).append("'");
				}
				if (cell.colspan > 1) {
					sb.append(" colspan='").append(cell.colspan).append("'");
				}
				sb.append(">").append(esc(cell.text)).append("</th>");
			}
			sb.append("</tr>");
		}
		
		sb.append("</thead>");
		sb.append("<tbody>");
		
		for (ReportTableModel.Row row : table.bodyRows) {
			ReportTableModel.Cell first = row.cells.isEmpty() ? null : row.cells.get(0);
			
			if (first != null && isFullWidthRow(first)) {
				sb.append("<tr class='").append(rowClass(first.style)).append("'>");
				sb.append("<td");
				if (first.colspan > 1) {
					sb.append(" colspan='").append(first.colspan).append("'");
				}
				sb.append(">").append(indentSpan(first.indent));
				if (first.style != ReportTableModel.Style.SPACER) {
					sb.append(esc(first.text));
				}
				sb.append("</td>");
				sb.append("</tr>");
				continue;
			}
			
			sb.append("<tr>");
			for (ReportTableModel.Cell cell : row.cells) {
				if (cell.style == ReportTableModel.Style.LABEL_CELL) {
					sb.append("<td class='label'>").append(indentSpan(cell.indent)).append(esc(cell.text)).append("</td>");
				} else {
					sb.append("<td class='val'>").append(esc(cell.text)).append("</td>");
				}
			}
			sb.append("</tr>");
		}
		
		sb.append("</tbody>");
		sb.append("</table>");
		return sb.toString();
	}
	
	private boolean isFullWidthRow(ReportTableModel.Cell cell) {
		return cell.style == ReportTableModel.Style.SECTION_LABEL || cell.style == ReportTableModel.Style.GROUP_LABEL
		        || cell.style == ReportTableModel.Style.LABEL_ROW || cell.style == ReportTableModel.Style.SPACER;
	}
	
	private String rowClass(ReportTableModel.Style style) {
		if (style == ReportTableModel.Style.SECTION_LABEL) {
			return "sectionRow";
		}
		if (style == ReportTableModel.Style.GROUP_LABEL) {
			return "groupRow";
		}
		if (style == ReportTableModel.Style.LABEL_ROW) {
			return "labelRow";
		}
		if (style == ReportTableModel.Style.SPACER) {
			return "spacerRow";
		}
		return "";
	}
	
	private String indentSpan(Integer depth) {
		int d = depth == null ? 0 : depth.intValue();
		if (d <= 0) {
			return "";
		}
		return "<span class='indent' style='width:" + (d * 14) + "px'></span>";
	}
	
	private String esc(String s) {
		if (s == null)
			return "";
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
	
	/* -------------------- PAYLOAD JSON -------------------- */
	
	public String buildPayloadOnly(String templateJson, Map<String, Object> flatValues, String remapJsonOptional) {
		try {
			AggregateReportModeler.ReportDesignTemplate tpl = MAPPER.readValue(templateJson,
			    AggregateReportModeler.ReportDesignTemplate.class);
			
			RemapConfig remap = null;
			if (remapJsonOptional != null && !remapJsonOptional.trim().isEmpty()) {
				remap = MAPPER.readValue(remapJsonOptional, RemapConfig.class);
			}
			
			ObjectNode payloadNode = buildPayloadNode(tpl, flatValues == null ? Collections.<String, Object> emptyMap()
			        : flatValues, remap);
			
			return MAPPER.writeValueAsString(payloadNode);
			
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to build payload JSON from report design", e);
		}
	}
	
	public String buildRenderedOutputOnly(String templateJson, Map<String, Object> flatValues, String remapJsonOptional) {
		try {
			AggregateReportModeler.ReportDesignTemplate tpl = MAPPER.readValue(templateJson,
			    AggregateReportModeler.ReportDesignTemplate.class);
			Map<String, Object> values = flatValues == null ? Collections.<String, Object> emptyMap() : flatValues;
			
			RemapConfig remap = null;
			if (remapJsonOptional != null && !remapJsonOptional.trim().isEmpty()) {
				remap = MAPPER.readValue(remapJsonOptional, RemapConfig.class);
			}
			
			ObjectNode payloadNode = buildPayloadNode(tpl, values, remap);
			ReportTableModel model = modeler.buildModel(tpl, values);
			String html = renderFinalHtmlDocument(model);
			
			ObjectNode root = MAPPER.createObjectNode();
			root.set("json",
			    payloadNode.path("json").isObject() ? (ObjectNode) payloadNode.path("json") : MAPPER.createObjectNode());
			root.put("html", html);
			root.set("dhis2", buildDhis2Node(tpl));
			
			return MAPPER.writeValueAsString(root);
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to build rendered output from report design", e);
		}
	}
	
	private ObjectNode buildPayloadNode(AggregateReportModeler.ReportDesignTemplate tpl, Map<String, Object> values,
	        RemapConfig remap) {
		ObjectNode root = MAPPER.createObjectNode();
		ObjectNode json = MAPPER.createObjectNode();
		root.set("json", json);
		
		ArrayNode dataValues = MAPPER.createArrayNode();
		
		if (tpl == null || tpl.groups == null) {
			json.set(modeler.safe(tpl == null ? null : tpl.arrayName, "dataValues"), dataValues);
			return root;
		}
		
		int defaultValue = tpl.defaultValue == null ? 0 : tpl.defaultValue;
		
		for (AggregateReportModeler.Group group : tpl.groups) {
			if (group == null || group.rows == null) {
				continue;
			}
			
			for (AggregateReportModeler.Row row : group.rows) {
				if (row == null || !"indicator".equals(modeler.safe(row.type))) {
					continue;
				}
				
				java.util.List<AggregateReportModeler.ResolvedDim> dims = modeler.resolveDimsForRow(tpl, row);
				java.util.List<AggregateReportModeler.DimCombo> combos = modeler.buildCombos(dims);
				
				if (!Boolean.TRUE.equals(row.showDisaggregation) || combos.isEmpty()) {
					String key = modeler.buildTotalKey(row);
					Integer v = modeler.coerceToInt(modeler.lookupValue(values, key));
					if (v == null) {
						v = modeler.coerceToInt(modeler.lookupValue(values, row.code));
					}
					if (v == null) {
						v = defaultValue;
					}
					
					ObjectNode dv = MAPPER.createObjectNode();
					dv.put("value", v);
					
					Map<String, String> mapped = remap == null ? Collections.<String, String> emptyMap() : remap.apply(key);
					
					dv.put("dataElement", mapped.containsKey("dataElement") ? mapped.get("dataElement") : key);
					
					if (mapped.containsKey("categoryOptionCombo")) {
						dv.put("categoryOptionCombo", mapped.get("categoryOptionCombo"));
					}
					if (mapped.containsKey("attributeOptionCombo")) {
						dv.put("attributeOptionCombo", mapped.get("attributeOptionCombo"));
					}
					
					dataValues.add(dv);
					continue;
				}
				
				for (AggregateReportModeler.DimCombo combo : combos) {
					String computedKey = modeler.buildKeyFlexible(modeler.safe(row.keyPattern, "{code}_{age}_{sex}"),
					    modeler.sanitizeCode(row.code), combo.placeholders);
					
					Integer v = modeler.coerceToInt(modeler.lookupValue(values, computedKey));
					if (v == null) {
						v = defaultValue;
					}
					
					ObjectNode dv = MAPPER.createObjectNode();
					dv.put("value", v);
					
					Map<String, String> mapped = remap == null ? Collections.<String, String> emptyMap() : remap
					        .apply(computedKey);
					
					dv.put("dataElement", mapped.containsKey("dataElement") ? mapped.get("dataElement") : computedKey);
					
					if (mapped.containsKey("categoryOptionCombo")) {
						dv.put("categoryOptionCombo", mapped.get("categoryOptionCombo"));
					}
					if (mapped.containsKey("attributeOptionCombo")) {
						dv.put("attributeOptionCombo", mapped.get("attributeOptionCombo"));
					}
					
					dataValues.add(dv);
				}
				
				if (Boolean.TRUE.equals(row.showTotal)) {
					String totalKey = modeler.buildTotalKey(row);
					Integer total = modeler.resolveTotalValue(values, row, combos, defaultValue);
					
					ObjectNode dv = MAPPER.createObjectNode();
					dv.put("value", total);
					
					Map<String, String> mapped = remap == null ? Collections.<String, String> emptyMap() : remap
					        .apply(totalKey);
					
					dv.put("dataElement", mapped.containsKey("dataElement") ? mapped.get("dataElement") : totalKey);
					
					if (mapped.containsKey("categoryOptionCombo")) {
						dv.put("categoryOptionCombo", mapped.get("categoryOptionCombo"));
					}
					if (mapped.containsKey("attributeOptionCombo")) {
						dv.put("attributeOptionCombo", mapped.get("attributeOptionCombo"));
					}
					
					dataValues.add(dv);
				}
			}
		}
		
		json.set(modeler.safe(tpl.arrayName, "dataValues"), dataValues);
		return root;
	}
	
	private ObjectNode buildDhis2Node(AggregateReportModeler.ReportDesignTemplate tpl) {
		ObjectNode dhis2Node = MAPPER.createObjectNode();
		
		if (tpl == null || tpl.dhis2 == null) {
			dhis2Node.put("enabled", false);
			dhis2Node.set("rows", MAPPER.createArrayNode());
			return dhis2Node;
		}
		
		dhis2Node.put("enabled", Boolean.TRUE.equals(tpl.dhis2.enabled));
		dhis2Node.set("rows", tpl.dhis2.rows == null ? MAPPER.createArrayNode() : MAPPER.valueToTree(tpl.dhis2.rows));
		
		return dhis2Node;
	}
	
	/* -------------------- POJOs -------------------- */
	
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class RemapConfig {
		
		public int version;
		
		public java.util.List<Rule> rules = new java.util.ArrayList<Rule>();
		
		public Map<String, String> defaults = new HashMap<String, String>();
		
		public Map<String, String> apply(String computedKey) {
			Map<String, String> out = new HashMap<String, String>(defaults);
			for (Rule r : rules) {
				if (r == null || r.match == null)
					continue;
				
				boolean ok = false;
				if (r.match.key != null && r.match.key.equals(computedKey)) {
					ok = true;
				} else if (r.match.prefix != null && computedKey.startsWith(r.match.prefix)) {
					ok = true;
				}
				
				if (ok && r.set != null) {
					out.putAll(r.set);
				}
			}
			return out;
		}
	}
	
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Rule {
		
		public Match match;
		
		public Map<String, String> set;
	}
	
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Match {
		
		public String key;
		
		public String prefix;
	}
}
