/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.reportbuilder.contract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration contract for ETL Monitor.
 * Contains API endpoint definition, display columns, and transformation rules.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ETLMonitorConfig {

	private ApiEndpoint apiEndpoint;
	private List<DisplayColumn> columns = new ArrayList<>();
	private List<TransformRule> transforms = new ArrayList<>();

	// ========================
	// Getters & Setters
	// ========================

	public ApiEndpoint getApiEndpoint() {
		return apiEndpoint;
	}

	public void setApiEndpoint(ApiEndpoint apiEndpoint) {
		this.apiEndpoint = apiEndpoint;
	}

	public List<DisplayColumn> getColumns() {
		return columns;
	}

	public void setColumns(List<DisplayColumn> columns) {
		this.columns = columns;
	}

	public List<TransformRule> getTransforms() {
		return transforms;
	}

	public void setTransforms(List<TransformRule> transforms) {
		this.transforms = transforms;
	}

	// ========================
	// Nested Classes
	// ========================

	/**
	 * API endpoint configuration
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class ApiEndpoint {

		private String url;
		private String method = "GET"; // GET, POST
		private Map<String, String> headers = new HashMap<>();

		// Authentication
		private AuthType authType = AuthType.NONE;
		private Map<String, String> authConfig = new HashMap<>();

		// Request body (for POST requests)
		private Map<String, Object> requestBody;

		// Query parameters
		private Map<String, String> queryParams = new HashMap<>();

		public enum AuthType {
			NONE, BASIC, API_KEY, BEARER_TOKEN, OAUTH2, OPENMRS
		}

		// ========================
		// Getters & Setters
		// ========================

		public String getUrl() {
			return url;
		}

		public void setUrl(String url) {
			this.url = url;
		}

		public String getMethod() {
			return method;
		}

		public void setMethod(String method) {
			this.method = method;
		}

		public Map<String, String> getHeaders() {
			return headers;
		}

		public void setHeaders(Map<String, String> headers) {
			this.headers = headers;
		}

		public AuthType getAuthType() {
			return authType;
		}

		public void setAuthType(AuthType authType) {
			this.authType = authType;
		}

		public Map<String, String> getAuthConfig() {
			return authConfig;
		}

		public void setAuthConfig(Map<String, String> authConfig) {
			this.authConfig = authConfig;
		}

		public Map<String, Object> getRequestBody() {
			return requestBody;
		}

		public void setRequestBody(Map<String, Object> requestBody) {
			this.requestBody = requestBody;
		}

		public Map<String, String> getQueryParams() {
			return queryParams;
		}

		public void setQueryParams(Map<String, String> queryParams) {
			this.queryParams = queryParams;
		}
	}

	/**
	 * Display column configuration for data extraction and rendering
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class DisplayColumn {

		private String key;           // Internal identifier
		private String header;        // Display label
		private String jsonPath;      // JSONPath expression
		private ColumnType columnType = ColumnType.TEXT;
		private String format;        // Optional format string
		private Map<String, String> colorMap = new HashMap<>(); // For status badges
		private String defaultValue;  // Fallback if path not found
		private Boolean sortable = true;
		private Integer width;        // For table columns

		public enum ColumnType {
			TEXT, NUMBER, PERCENTAGE,
			PROGRESS_BAR, STATUS_BADGE,
			TIMESTAMP, DURATION,
			BOOLEAN, ICON, LINK
		}

		// ========================
		// Getters & Setters
		// ========================

		public String getKey() {
			return key;
		}

		public void setKey(String key) {
			this.key = key;
		}

		public String getHeader() {
			return header;
		}

		public void setHeader(String header) {
			this.header = header;
		}

		public String getJsonPath() {
			return jsonPath;
		}

		public void setJsonPath(String jsonPath) {
			this.jsonPath = jsonPath;
		}

		public ColumnType getColumnType() {
			return columnType;
		}

		public void setColumnType(ColumnType columnType) {
			this.columnType = columnType;
		}

		public String getFormat() {
			return format;
		}

		public void setFormat(String format) {
			this.format = format;
		}

		public Map<String, String> getColorMap() {
			return colorMap;
		}

		public void setColorMap(Map<String, String> colorMap) {
			this.colorMap = colorMap;
		}

		public String getDefaultValue() {
			return defaultValue;
		}

		public void setDefaultValue(String defaultValue) {
			this.defaultValue = defaultValue;
		}

		public Boolean getSortable() {
			return sortable;
		}

		public void setSortable(Boolean sortable) {
			this.sortable = sortable;
		}

		public Integer getWidth() {
			return width;
		}

		public void setWidth(Integer width) {
			this.width = width;
		}
	}

	/**
	 * Transformation rule for complex data transformations
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class TransformRule {

		private String key;
		private String transform; // JavaScript expression for complex transforms
		private Map<String, Object> variables = new HashMap<>();

		// ========================
		// Getters & Setters
		// ========================

		public String getKey() {
			return key;
		}

		public void setKey(String key) {
			this.key = key;
		}

		public String getTransform() {
			return transform;
		}

		public void setTransform(String transform) {
			this.transform = transform;
		}

		public Map<String, Object> getVariables() {
			return variables;
		}

		public void setVariables(Map<String, Object> variables) {
			this.variables = variables;
		}
	}
}
