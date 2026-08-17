/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.reportbuilder.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.openmrs.module.reportbuilder.contract.ETLMonitorConfig.DisplayColumn;
import org.openmrs.module.reportbuilder.exception.ETLMonitorException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for extracting data from JSON responses using Jackson JSON parser. Supports extraction of
 * multiple values and complex transformations without external JSONPath dependencies.
 */
@Service
public class JsonPathExtractor {
	
	private final ObjectMapper objectMapper = new ObjectMapper();
	
	/**
	 * Extract a single value from JSON using a simple path expression Path format: "$.field.nested"
	 * for nested fields, "$.array[0]" for array access
	 * 
	 * @param json the JSON string
	 * @param path the path expression
	 * @return the extracted value
	 * @throws ETLMonitorException if the path is invalid or not found
	 */
	public Object extractValue(String json, String path) throws ETLMonitorException {
		if (json == null || json.trim().isEmpty()) {
			throw new ETLMonitorException(ETLMonitorException.ErrorCode.INVALID_CONFIG, "JSON content is empty");
		}
		
		if (path == null || path.trim().isEmpty()) {
			throw new ETLMonitorException(ETLMonitorException.ErrorCode.INVALID_CONFIG, "Path expression is required");
		}
		
		try {
			JsonNode root = objectMapper.readTree(json);
			return navigatePath(root, path);
		}
		catch (ETLMonitorException e) {
			throw e;
		}
		catch (Exception e) {
			throw new ETLMonitorException(ETLMonitorException.ErrorCode.INVALID_PATH, "Invalid path expression: " + path
			        + " - " + e.getMessage(), e);
		}
	}
	
	/**
	 * Navigate through JSON tree using path expression Supports: $.field, $.field.nested,
	 * $.array[0], $.array[*]
	 */
	private Object navigatePath(JsonNode node, String path) throws ETLMonitorException {
		if (node == null || node.isNull()) {
			throw new ETLMonitorException(ETLMonitorException.ErrorCode.INVALID_PATH,
					"Path not found: " + path);
		}

		// Remove leading "$." if present
		if (path.startsWith("$.")) {
			path = path.substring(2);
		} else if (path.equals("$")) {
			return convertJsonNode(node);
		}

		String[] parts = path.split("\\.");
		JsonNode current = node;

		for (int i = 0; i < parts.length; i++) {
			String part = parts[i].trim();

			if (part.isEmpty()) {
				continue;
			}

			// Handle array access: field[0] or field[*]
			if (part.contains("[") && part.contains("]")) {
				String fieldName = part.substring(0, part.indexOf("["));
				String arrayIndex = part.substring(part.indexOf("[") + 1, part.indexOf("]"));

				// First navigate to the field
				if (!fieldName.isEmpty()) {
					if (!current.has(fieldName)) {
						throw new ETLMonitorException(ETLMonitorException.ErrorCode.INVALID_PATH,
								"Field not found: " + fieldName);
					}
					current = current.get(fieldName);
				}

				if (current == null || !current.isArray()) {
					throw new ETLMonitorException(ETLMonitorException.ErrorCode.INVALID_PATH,
							"Not an array: " + fieldName);
				}

				ArrayNode arrayNode = (ArrayNode) current;

				// Handle wildcard [*] - return all elements
				if ("*".equals(arrayIndex)) {
					List<Object> result = new ArrayList<>();
					for (JsonNode item : arrayNode) {
						result.add(convertJsonNode(item));
					}
					// If there are more parts to process, handle them recursively
					if (i < parts.length - 1) {
						String remainingPath = "";
						for (int j = i + 1; j < parts.length; j++) {
							remainingPath += (j > i + 1 ? "." : "") + parts[j];
						}
						List<Object> finalResult = new ArrayList<>();
						for (Object item : result) {
							if (item instanceof JsonNode) {
								try {
									finalResult.add(navigatePath((JsonNode) item, remainingPath));
								} catch (ETLMonitorException e) {
									// Skip items that don't match the remaining path
									finalResult.add(null);
								}
							}
						}
						return finalResult;
					}
					return result;
				}

				// Handle specific index
				try {
					int index = Integer.parseInt(arrayIndex);
					if (index >= 0 && index < arrayNode.size()) {
						current = arrayNode.get(index);
					} else {
						throw new ETLMonitorException(ETLMonitorException.ErrorCode.INVALID_PATH,
								"Array index out of bounds: " + index);
					}
				} catch (NumberFormatException e) {
					throw new ETLMonitorException(ETLMonitorException.ErrorCode.INVALID_PATH,
							"Invalid array index: " + arrayIndex);
				}
			} else {
				// Regular field access
				if (!current.has(part)) {
					throw new ETLMonitorException(ETLMonitorException.ErrorCode.INVALID_PATH,
							"Field not found: " + part);
				}
				current = current.get(part);
			}

			if (current == null || current.isNull()) {
				if (i < parts.length - 1) {
					throw new ETLMonitorException(ETLMonitorException.ErrorCode.INVALID_PATH,
							"Path not found: " + path);
				}
				return null;
			}
		}

		return convertJsonNode(current);
	}
	
	/**
	 * Convert JsonNode to appropriate Java type
	 */
	private Object convertJsonNode(JsonNode node) {
		if (node == null || node.isNull()) {
			return null;
		}

		if (node.isBoolean()) {
			return node.asBoolean();
		}

		if (node.isInt()) {
			return node.asInt();
		}

		if (node.isLong()) {
			return node.asLong();
		}

		if (node.isDouble() || node.isFloat()) {
			return node.asDouble();
		}

		if (node.isArray()) {
			List<Object> list = new ArrayList<>();
			for (JsonNode item : node) {
				list.add(convertJsonNode(item));
			}
			return list;
		}

		if (node.isObject()) {
			// Convert object to Map
			Map<String, Object> map = new LinkedHashMap<>();
			java.util.Iterator<java.util.Map.Entry<String, JsonNode>> fields = node.fields();
			while (fields.hasNext()) {
				java.util.Map.Entry<String, JsonNode> entry = fields.next();
				map.put(entry.getKey(), convertJsonNode(entry.getValue()));
			}
			return map;
		}

		// Default: return as text
		return node.asText();
	}
	
	/**
	 * Extract multiple values from JSON based on display column configurations
	 * 
	 * @param json the JSON string
	 * @param columns the list of display columns with path expressions
	 * @return a map of column keys to extracted values
	 */
	public Map<String, Object> extractMultiple(String json, List<DisplayColumn> columns) {
		Map<String, Object> result = new LinkedHashMap<>();

		if (columns == null || columns.isEmpty()) {
			return result;
		}

		for (DisplayColumn column : columns) {
			String key = column.getKey();
			String path = column.getJsonPath();
			String defaultValue = column.getDefaultValue();

			if (key == null || path == null) {
				continue;
			}

			try {
				Object value = extractValue(json, path);

				// Format value based on column type
				value = formatValue(value, column);
				result.put(key, value);
			}
			catch (ETLMonitorException e) {
				// Use default value if extraction fails
				if (defaultValue != null) {
					result.put(key, defaultValue);
				} else {
					// For failed extractions without default, store null
					result.put(key, null);
				}
			}
		}

		return result;
	}
	
	/**
	 * Extract values for a data table (multiple rows)
	 * 
	 * @param json the JSON string
	 * @param columns the list of display columns
	 * @return a list of maps representing table rows
	 */
	public List<Map<String, Object>> extractTable(String json, List<DisplayColumn> columns) {
		List<Map<String, Object>> table = new ArrayList<>();

		if (columns == null || columns.isEmpty()) {
			return table;
		}

		// Find the first array path to use as the table rows
		String arrayPath = null;
		for (DisplayColumn column : columns) {
			if (column.getJsonPath() != null && column.getJsonPath().contains("[*]")) {
				// Extract the array part (e.g., "$.data.items[*]" -> "$.data.items[*]")
				arrayPath = column.getJsonPath().replaceAll("\\.\\*", "[*]");
				if (!arrayPath.endsWith("[*]")) {
					arrayPath = column.getJsonPath().replaceAll("\\[\\*\\].*", "[*]");
				}
				break;
			}
		}

		if (arrayPath == null) {
			// No array found, return single row
			Map<String, Object> row = extractMultiple(json, columns);
			table.add(row);
			return table;
		}

		try {
			JsonNode root = objectMapper.readTree(json);
			Object arrayResult = navigatePath(root, arrayPath);

			if (!(arrayResult instanceof List)) {
				// Not an array, fall back to single row
				Map<String, Object> row = extractMultiple(json, columns);
				table.add(row);
				return table;
			}

			List<Object> rows = (List<Object>) arrayResult;

			for (Object rowObj : rows) {
				if (!(rowObj instanceof JsonNode || rowObj instanceof Map)) {
					continue;
				}

				// Convert row object back to JSON string for path extraction
				String rowJson;
				try {
					if (rowObj instanceof JsonNode) {
						rowJson = objectMapper.writeValueAsString(rowObj);
					} else {
						rowJson = objectMapper.writeValueAsString(rowObj);
					}
				} catch (Exception e) {
					continue;
				}

				Map<String, Object> rowData = new LinkedHashMap<>();

				for (DisplayColumn column : columns) {
					String key = column.getKey();
					String jsonPath = column.getJsonPath();

					if (key == null || jsonPath == null) {
						continue;
					}

					try {
						// For table extraction, we need to evaluate the path relative to each row
						// Extract the field path from the array path
						String fieldPath = extractFieldPath(jsonPath, arrayPath);

						Object value = extractValue(rowJson, fieldPath);

						value = formatValue(value, column);
						rowData.put(key, value);
					}
					catch (Exception e) {
						String defaultValue = column.getDefaultValue();
						if (defaultValue != null) {
							rowData.put(key, defaultValue);
						} else {
							rowData.put(key, null);
						}
					}
				}

				table.add(rowData);
			}
		}
		catch (Exception e) {
			// If table extraction fails, fall back to single row extraction
			Map<String, Object> row = extractMultiple(json, columns);
			table.add(row);
		}

		return table;
	}
	
	/**
	 * Extract the field path relative to the row by removing the array path prefix Example:
	 * jsonPath: "$.data.items[*].name", arrayPath: "$.data.items[*]" result: "$.name"
	 */
	private String extractFieldPath(String jsonPath, String arrayPath) {
		// Remove the array path prefix
		if (arrayPath.endsWith("[*]")) {
			String prefix = arrayPath.substring(0, arrayPath.length() - 3); // Remove [*]
			if (jsonPath.startsWith(prefix)) {
				String suffix = jsonPath.substring(prefix.length());
				if (suffix.startsWith(".")) {
					return "$" + suffix;
				}
				return "$." + suffix;
			}
		}
		
		// Fallback: try to extract just the field name
		String[] parts = jsonPath.split("\\.");
		if (parts.length > 0) {
			String lastPart = parts[parts.length - 1];
			if (lastPart.contains("[*]")) {
				// This is an array path, get the field after it
				return "$." + lastPart.replaceAll("\\[\\*\\]\\.", "");
			}
			return "$." + lastPart;
		}
		
		return jsonPath;
	}
	
	/**
	 * Format a value based on the column type
	 * 
	 * @param value the value to format
	 * @param column the display column configuration
	 * @return the formatted value
	 */
	private Object formatValue(Object value, DisplayColumn column) {
		if (value == null) {
			return null;
		}
		
		DisplayColumn.ColumnType columnType = column.getColumnType();
		if (columnType == null) {
			return value;
		}
		
		switch (columnType) {
			case NUMBER:
				if (value instanceof Number) {
					return ((Number) value).doubleValue();
				}
				try {
					return Double.parseDouble(value.toString());
				}
				catch (NumberFormatException e) {
					return value;
				}
				
			case PERCENTAGE:
				if (value instanceof Number) {
					double num = ((Number) value).doubleValue();
					// Convert to percentage (0-100 range)
					return num * 100;
				}
				try {
					double num = Double.parseDouble(value.toString());
					return num * 100;
				}
				catch (NumberFormatException e) {
					return value;
				}
				
			case BOOLEAN:
				if (value instanceof Boolean) {
					return value;
				}
				return Boolean.parseBoolean(value.toString());
				
			case TIMESTAMP:
			case DURATION:
			case TEXT:
			case ICON:
			case LINK:
			case PROGRESS_BAR:
			case STATUS_BADGE:
			default:
				// For text-based types, return as string
				return value.toString();
		}
	}
	
	/**
	 * Validate that a path expression is syntactically valid
	 * 
	 * @param path the path expression to validate
	 * @return true if valid, false otherwise
	 */
	public boolean isValidJsonPath(String path) {
		if (path == null || path.trim().isEmpty()) {
			return false;
		}
		
		// Basic validation: must start with $ or be a simple field name
		if (!path.startsWith("$") && !path.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
			return false;
		}
		
		try {
			// Check for balanced brackets
			int openCount = 0;
			for (char c : path.toCharArray()) {
				if (c == '[')
					openCount++;
				if (c == ']')
					openCount--;
				if (openCount < 0)
					return false;
			}
			return openCount == 0;
		}
		catch (Exception e) {
			return false;
		}
	}
	
	/**
	 * Get a list of all keys from a JSON object
	 * 
	 * @param json the JSON string
	 * @return a list of keys, or empty list if not an object
	 */
	public List<String> getKeys(String json) {
		List<String> keys = new ArrayList<>();

		try {
			JsonNode root = objectMapper.readTree(json);
			if (root.isObject()) {
				java.util.Iterator<String> fieldNames = root.fieldNames();
				while (fieldNames.hasNext()) {
					keys.add(fieldNames.next());
				}
			}
		}
		catch (Exception e) {
			// Invalid JSON, return empty list
		}

		return keys;
	}
}
