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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openmrs.module.reporting.dataset.DataSet;
import org.openmrs.module.reporting.dataset.DataSetRow;
import org.openmrs.module.reporting.report.ReportData;
import org.openmrs.module.reporting.report.ReportDesign;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Builds a {@link ReportTableModel} from evaluated linelist report data. Expected input format is
 * the LegacyGenericReportSchema with baseCohortDefinition and dataSetDefinitions containing
 * PATIENT_DATA_SET columns. This is the format-neutral twin of the HTML table walk that used to
 * live inside {@link LinelistHtmlRenderer}: column extraction (including expanded columns like
 * weight_1, weight_2) happens here once, and the HTML, CSV and Excel renderers all consume the same
 * model.
 */
public class LinelistReportModeler {
	
	private static final ObjectMapper MAPPER = new ObjectMapper();
	
	/**
	 * Builds the table model: report title/description from the compiled config, one flat table
	 * with a single header row, and a "N rows" footer.
	 */
	public ReportTableModel buildModel(ReportData reportData, ReportDesign reportDesign) {
		try {
			// Read the compiled linelist config from the report design
			String templateJson = readDesignResource(reportDesign);
			JsonNode config = MAPPER.readTree(templateJson);
			
			// Extract data rows from the report data FIRST
			List<Map<String, Object>> dataRows = extractDataRows(reportData);
			
			// Extract column definitions from actual data (handles expanded columns)
			List<ColumnDefinition> columns = extractColumnDefinitionsFromData(config, dataRows);
			
			return buildModel(config, columns, dataRows);
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to model linelist report", e);
		}
	}
	
	/**
	 * Builds the table model from an already-parsed config and extracted columns/rows. Exposed so
	 * the HTML renderer can build its HTML and payload JSON from one shared walk.
	 */
	public ReportTableModel buildModel(JsonNode config, List<ColumnDefinition> columns, List<Map<String, Object>> dataRows) {
		ReportTableModel model = new ReportTableModel();
		
		model.title = config.path("name").asText("");
		model.description = config.path("description").asText("");
		
		if (columns.isEmpty()) {
			model.emptyMessage = "No columns defined in report.";
			return model;
		}
		
		ReportTableModel.Table table = new ReportTableModel.Table("");
		
		ReportTableModel.Row header = new ReportTableModel.Row();
		for (ColumnDefinition col : columns) {
			ReportTableModel.Cell cell = new ReportTableModel.Cell(ReportTableModel.Style.HEADER, col.displayName);
			cell.key = col.key;
			header.cell(cell);
		}
		table.headerRows.add(header);
		
		for (Map<String, Object> dataRow : dataRows) {
			ReportTableModel.Row row = new ReportTableModel.Row();
			for (ColumnDefinition col : columns) {
				Object value = dataRow.get(col.key);
				ReportTableModel.Cell cell = new ReportTableModel.Cell(ReportTableModel.Style.DATA,
				        value != null ? value.toString() : "");
				cell.key = col.key;
				row.cell(cell);
			}
			table.bodyRows.add(row);
		}
		
		model.tables.add(table);
		model.footerText = dataRows.size() + " rows";
		
		return model;
	}
	
	/**
	 * Reads the JSON template from the report design resource.
	 */
	public String readDesignResource(ReportDesign reportDesign) {
		if (reportDesign == null || reportDesign.getResources() == null) {
			throw new RuntimeException("Report design has no resources");
		}
		
		for (org.openmrs.module.reporting.report.ReportDesignResource resource : reportDesign.getResources()) {
			if ("template".equals(resource.getName())) {
				byte[] content = resource.getContents();
				if (content == null || content.length == 0) {
					throw new RuntimeException("Report design template is empty");
				}
				return new String(content, StandardCharsets.UTF_8);
			}
		}
		
		throw new RuntimeException("No template resource found in report design");
	}
	
	public static class ColumnDefinition {
		
		public final String key;
		
		public final String displayName;
		
		public ColumnDefinition(String key, String displayName) {
			this.key = key;
			this.displayName = displayName;
		}
	}
	
	/**
	 * Extracts column definitions from the compiled linelist config. Returns columns from the
	 * config for display name mapping (fallback path when there is no data).
	 */
	public List<ColumnDefinition> extractColumnDefinitions(JsonNode config) {
		List<ColumnDefinition> columns = new ArrayList<ColumnDefinition>();
		
		JsonNode dataSetDefinitions = config.path("dataSetDefinitions");
		if (!dataSetDefinitions.isArray() || dataSetDefinitions.size() == 0) {
			return columns;
		}
		
		for (JsonNode dsd : dataSetDefinitions) {
			if ("PATIENT_DATA_SET".equals(dsd.path("type").asText())) {
				JsonNode columnsNode = dsd.path("columns");
				if (columnsNode.isArray()) {
					for (JsonNode col : columnsNode) {
						String name = col.path("name").asText("");
						String key = col.has("key") ? col.path("key").asText() : nameToKey(name);
						columns.add(new ColumnDefinition(key, name));
					}
				}
				break;
			}
		}
		
		return columns;
	}
	
	/**
	 * Extracts column definitions from actual data rows. This handles expanded columns (e.g.,
	 * weight_1, weight_2) that aren't in the original config. Uses display names from config where
	 * available, otherwise formats the key for display.
	 */
	public List<ColumnDefinition> extractColumnDefinitionsFromData(JsonNode config, List<Map<String, Object>> dataRows) {
		List<ColumnDefinition> columns = new ArrayList<ColumnDefinition>();
		
		if (dataRows.isEmpty()) {
			// Fallback to config columns if no data
			return extractColumnDefinitions(config);
		}
		
		// Extract display name mapping from config (in config order)
		Map<String, String> configDisplayNames = new LinkedHashMap<String, String>();
		List<String> configColumnOrder = new ArrayList<String>();
		JsonNode dataSetDefinitions = config.path("dataSetDefinitions");
		if (dataSetDefinitions.isArray()) {
			for (JsonNode dsd : dataSetDefinitions) {
				if ("PATIENT_DATA_SET".equals(dsd.path("type").asText())) {
					JsonNode columnsNode = dsd.path("columns");
					if (columnsNode.isArray()) {
						for (JsonNode col : columnsNode) {
							String name = col.path("name").asText("");
							String key = col.has("key") ? col.path("key").asText() : nameToKey(name);
							configDisplayNames.put(key, name);
							configColumnOrder.add(key);
						}
					}
					break;
				}
			}
		}
		
		// Get all column keys from data (including expanded columns)
		Map<String, Object> firstRow = dataRows.get(0);
		Set<String> dataColumnKeys = firstRow.keySet();
		
		// Build columns list by iterating through config columns in order
		// This ensures the primary order is the config's order
		for (String configKey : configColumnOrder) {
			if (dataColumnKeys.contains(configKey)) {
				columns.add(new ColumnDefinition(configKey, configDisplayNames.get(configKey)));
			}
		}
		
		// Add any expanded columns not in config (e.g., weight_1, weight_2) at the end
		for (String dataKey : dataColumnKeys) {
			if (!configDisplayNames.containsKey(dataKey)) {
				// Check if this is an expanded column
				String baseKey = findBaseKeyForExpandedColumn(dataKey, configDisplayNames.keySet());
				String displayName;
				if (baseKey != null && configDisplayNames.containsKey(baseKey)) {
					String baseName = configDisplayNames.get(baseKey);
					int occurrence = getOccurrenceNumber(dataKey);
					displayName = baseName + " " + occurrence;
				} else {
					displayName = formatKeyForDisplay(dataKey);
				}
				columns.add(new ColumnDefinition(dataKey, displayName));
			}
		}
		
		return columns;
	}
	
	/**
	 * Finds the base key for an expanded column. For example, given "weight_2" and base keys
	 * ["weight", "name"], returns "weight".
	 */
	private String findBaseKeyForExpandedColumn(String expandedKey, Set<String> baseKeys) {
		for (String baseKey : baseKeys) {
			if (expandedKey.startsWith(baseKey + "_")) {
				String suffix = expandedKey.substring(baseKey.length() + 1);
				try {
					// Check if suffix is a number
					Integer.parseInt(suffix);
					return baseKey;
				}
				catch (NumberFormatException e) {
					// Not a number suffix, continue
				}
			}
		}
		return null;
	}
	
	/**
	 * Extracts the occurrence number from an expanded column key. For example, "weight_2" returns
	 * 2.
	 */
	private int getOccurrenceNumber(String columnKey) {
		int lastUnderscore = columnKey.lastIndexOf('_');
		if (lastUnderscore > 0) {
			String suffix = columnKey.substring(lastUnderscore + 1);
			try {
				return Integer.parseInt(suffix);
			}
			catch (NumberFormatException e) {
				return 1;
			}
		}
		return 1;
	}
	
	/**
	 * Formats a column key for display. Converts "patient_name" to "Patient Name", "weight_1" to
	 * "Weight 1", etc.
	 */
	private String formatKeyForDisplay(String key) {
		if (key == null || key.isEmpty()) {
			return "";
		}
		
		// Split by underscore and capitalize each part
		String[] parts = key.split("_");
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				sb.append(" ");
			}
			if (!parts[i].isEmpty()) {
				sb.append(Character.toUpperCase(parts[i].charAt(0)));
				if (parts[i].length() > 1) {
					sb.append(parts[i].substring(1).toLowerCase());
				}
			}
		}
		return sb.toString();
	}
	
	/**
	 * Extracts data rows from the evaluated report data.
	 */
	public List<Map<String, Object>> extractDataRows(ReportData reportData) {
		List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
		
		if (reportData == null || reportData.getDataSets() == null) {
			return rows;
		}
		
		for (DataSet dataSet : reportData.getDataSets().values()) {
			Iterator<?> it = dataSet.iterator();
			while (it.hasNext()) {
				DataSetRow dataRow = (DataSetRow) it.next();
				Map<String, Object> row = new LinkedHashMap<String, Object>();
				for (Map.Entry<String, Object> entry : dataRow.getColumnValuesByKey().entrySet()) {
					row.put(entry.getKey(), entry.getValue());
				}
				rows.add(row);
			}
		}
		
		return rows;
	}
	
	private String nameToKey(String name) {
		if (name == null || name.isEmpty()) {
			return "";
		}
		return name.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
	}
}
