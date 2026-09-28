package org.openmrs.module.reportbuilder.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.openmrs.module.reporting.report.ReportData;
import org.openmrs.module.reporting.report.ReportDesign;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Renders linelist report data into HTML table format. Expected input format is the
 * LegacyGenericReportSchema with baseCohortDefinition and dataSetDefinitions containing
 * PATIENT_DATA_SET columns. The HTML is emitted from a {@link ReportTableModel} built by
 * {@link LinelistReportModeler}, which also feeds the CSV and Excel renderers, so all download
 * formats mirror this HTML by construction.
 */
public class LinelistHtmlRenderer {
	
	private static final ObjectMapper MAPPER = new ObjectMapper();
	
	private final LinelistReportModeler modeler = new LinelistReportModeler();
	
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
	
	/**
	 * Converts linelist report data to HTML table format.
	 * 
	 * @param reportData the evaluated report data containing the linelist dataset
	 * @param reportDesign the report design containing the compiled linelist config
	 * @return Result containing html, payloadJson, and renderedOutputJson
	 */
	public Result convert(ReportData reportData, ReportDesign reportDesign) {
		try {
			// Read the compiled linelist config from the report design
			JsonNode config = MAPPER.readTree(modeler.readDesignResource(reportDesign));
			
			List<Map<String, Object>> dataRows = modeler.extractDataRows(reportData);
			List<LinelistReportModeler.ColumnDefinition> columns = modeler
			        .extractColumnDefinitionsFromData(config, dataRows);
			
			ReportTableModel model = modeler.buildModel(config, columns, dataRows);
			
			// Build HTML
			String html = renderHtmlDocument(model);
			
			// Build payload JSON
			String payloadJson = buildPayloadJson(config, model);
			
			// Build rendered output JSON
			String renderedOutputJson = buildRenderedOutputJson(config, model, html);
			
			return new Result(html, payloadJson, renderedOutputJson);
			
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to render linelist report", e);
		}
	}
	
	/**
	 * Renders the model as the HTML document: title, description, the flat table, and a row-count
	 * footer.
	 */
	private String renderHtmlDocument(ReportTableModel model) {
		StringBuilder sb = new StringBuilder();
		sb.append("<!doctype html><html><head><meta charset='utf-'/>");
		sb.append("<style>").append(getTableStyles()).append("</style></head><body>");
		
		if (model.title != null && !model.title.isEmpty()) {
			sb.append("<div class='reportTitle'>").append(esc(model.title)).append("</div>");
		}
		
		if (model.description != null && !model.description.isEmpty()) {
			sb.append("<div class='reportDescription'>").append(esc(model.description)).append("</div>");
		}
		
		if (model.emptyMessage != null && !model.emptyMessage.isEmpty()) {
			sb.append("<div>").append(esc(model.emptyMessage)).append("</div>");
			sb.append("</body></html>");
			return sb.toString();
		}
		
		ReportTableModel.Table table = model.tables.get(0);
		
		sb.append("<table class='linelist-table'>");
		sb.append("<thead><tr>");
		
		// Header row
		for (ReportTableModel.Cell cell : table.headerRows.get(0).cells) {
			sb.append("<th>").append(esc(cell.text)).append("</th>");
		}
		sb.append("</tr></thead>");
		
		// Data rows
		sb.append("<tbody>");
		for (ReportTableModel.Row row : table.bodyRows) {
			sb.append("<tr>");
			for (ReportTableModel.Cell cell : row.cells) {
				sb.append("<td>").append(esc(cell.text)).append("</td>");
			}
			sb.append("</tr>");
		}
		sb.append("</tbody>");
		
		sb.append("</table>");
		
		// Row count footer
		sb.append("<div class='rowCount'>").append(esc(model.footerText)).append("</div>");
		
		sb.append("</body></html>");
		return sb.toString();
	}
	
	/**
	 * Builds the payload JSON containing just the data values.
	 */
	private String buildPayloadJson(JsonNode config, ReportTableModel model) {
		try {
			ObjectNode root = MAPPER.createObjectNode();
			ObjectNode jsonData = MAPPER.createObjectNode();
			
			// Add metadata
			jsonData.put("name", config.path("name").asText(""));
			jsonData.put("description", config.path("description").asText(""));
			
			// No tables when the report has no columns: emit an empty payload like the
			// pre-model refactor did instead of crashing
			ReportTableModel.Table table = model.tables.isEmpty() ? null : model.tables.get(0);
			
			// Add columns
			ArrayNode columnsArray = MAPPER.createArrayNode();
			if (table != null) {
				for (ReportTableModel.Cell cell : table.headerRows.get(0).cells) {
					ObjectNode colNode = MAPPER.createObjectNode();
					colNode.put("key", cell.key);
					colNode.put("name", cell.text);
					columnsArray.add(colNode);
				}
			}
			jsonData.set("columns", columnsArray);
			
			// Add data rows
			ArrayNode rowsArray = MAPPER.createArrayNode();
			if (table != null) {
				for (ReportTableModel.Row row : table.bodyRows) {
					ObjectNode rowNode = MAPPER.createObjectNode();
					for (ReportTableModel.Cell cell : row.cells) {
						rowNode.put(cell.key, cell.text);
					}
					rowsArray.add(rowNode);
				}
			}
			jsonData.set("data", rowsArray);
			jsonData.put("rowCount", table == null ? 0 : table.bodyRows.size());
			
			root.set("json", jsonData);
			return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
			
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to build payload JSON", e);
		}
	}
	
	/**
	 * Builds the rendered output JSON containing both json and html.
	 */
	private String buildRenderedOutputJson(JsonNode config, ReportTableModel model, String html) {
		try {
			ObjectNode root = MAPPER.createObjectNode();
			
			// Re-use the payload JSON structure
			String payloadJson = buildPayloadJson(config, model);
			JsonNode payloadNode = MAPPER.readTree(payloadJson);
			root.set("json", payloadNode.path("json"));
			
			// Add HTML
			root.put("html", html);
			
			// Add empty dhis2 node for consistency with aggregate reports
			ObjectNode dhis2Node = MAPPER.createObjectNode();
			dhis2Node.put("enabled", false);
			dhis2Node.set("rows", MAPPER.createArrayNode());
			root.set("dhis2", dhis2Node);
			
			return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
			
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to build rendered output JSON", e);
		}
	}
	
	private String getTableStyles() {
		return "body{font-family:Arial,Helvetica,sans-serif;margin:12px;color:#222;}"
		        + ".reportTitle{font-size:16px;font-weight:bold;margin-bottom:10px;}"
		        + ".reportDescription{font-size:13px;color:#666;margin-bottom:15px;}"
		        + ".rowCount{margin-top:10px;font-size:12px;color:#666;}"
		        + "table.linelist-table{border-collapse:collapse;width:100%;margin-bottom:20px;}"
		        + "table.linelist-table th,table.linelist-table td{border:1px solid #ddd;padding:8px;"
		        + "font-size:12px;text-align:left;}" + "table.linelist-table th{font-weight:bold;position:sticky;top:0;}"
		        + "table.linelist-table tbody tr:nth-child(even){background:#f9f9f9;}"
		        + "table.linelist-table tbody tr:hover{background:#f0f0f0;}";
	}
	
	private String esc(String s) {
		if (s == null) {
			return "";
		}
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "&#39;")
		        .replace("\"", "&quot;");
	}
}
