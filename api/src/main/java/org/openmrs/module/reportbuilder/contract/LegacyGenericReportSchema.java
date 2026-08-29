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
import java.util.Map;

/**
 * Legacy generic report schema that matches the actual JSON structure used in the 114 migrated
 * reports. This schema maintains compatibility with the existing UgandaEMRReports migration format.
 * Key differences from newer schemas: - Uses dataSetDefinitions array instead of single dataSet -
 * Flat categorization fields (category, subcategory, reportType, reportYear, reportScope) - Simpler
 * parameter structure without nested Parameters class - Version format like "1.4-generic"
 */
public class LegacyGenericReportSchema {
	
	/**
	 * Root JSON structure for a report definition - matches actual migrated report structure
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class ReportDefinition {
		
		private String version;
		
		private String uuid;
		
		private String name;
		
		private String description;
		
		private Parameter[] parameters;
		
		private BaseCohortDefinition baseCohortDefinition;
		
		private DataSetDefinition[] dataSetDefinitions;
		
		// Categorization fields (flat structure)
		private String category;
		
		private String categoryUuid;
		
		private String subcategory;
		
		private String reportType;
		
		private String reportYear;
		
		private String reportScope;
		
		/**
		 * Cross-instance linkage carried by shipped compiled designs. Populated at compile time so
		 * a package can recreate or update the matching ReportLibrary entry on import.
		 */
		private String reportBuilderReportUuid;
		
		private String reportDefinitionUuid;
		
		private String reportLibraryUuid;
		
		public String getCategoryUuid() {
			return categoryUuid;
		}
		
		public void setCategoryUuid(String categoryUuid) {
			this.categoryUuid = categoryUuid;
		}
		
		public String getReportBuilderReportUuid() {
			return reportBuilderReportUuid;
		}
		
		public void setReportBuilderReportUuid(String reportBuilderReportUuid) {
			this.reportBuilderReportUuid = reportBuilderReportUuid;
		}
		
		public String getReportDefinitionUuid() {
			return reportDefinitionUuid;
		}
		
		public void setReportDefinitionUuid(String reportDefinitionUuid) {
			this.reportDefinitionUuid = reportDefinitionUuid;
		}
		
		public String getReportLibraryUuid() {
			return reportLibraryUuid;
		}
		
		public void setReportLibraryUuid(String reportLibraryUuid) {
			this.reportLibraryUuid = reportLibraryUuid;
		}
		
		// Getters and setters
		public String getVersion() {
			return version;
		}
		
		public void setVersion(String version) {
			this.version = version;
		}
		
		public String getUuid() {
			return uuid;
		}
		
		public void setUuid(String uuid) {
			this.uuid = uuid;
		}
		
		public String getName() {
			return name;
		}
		
		public void setName(String name) {
			this.name = name;
		}
		
		public String getDescription() {
			return description;
		}
		
		public void setDescription(String description) {
			this.description = description;
		}
		
		public Parameter[] getParameters() {
			return parameters;
		}
		
		public void setParameters(Parameter[] parameters) {
			this.parameters = parameters;
		}
		
		public BaseCohortDefinition getBaseCohortDefinition() {
			return baseCohortDefinition;
		}
		
		public void setBaseCohortDefinition(BaseCohortDefinition baseCohortDefinition) {
			this.baseCohortDefinition = baseCohortDefinition;
		}
		
		public DataSetDefinition[] getDataSetDefinitions() {
			return dataSetDefinitions;
		}
		
		public void setDataSetDefinitions(DataSetDefinition[] dataSetDefinitions) {
			this.dataSetDefinitions = dataSetDefinitions;
		}
		
		public String getCategory() {
			return category;
		}
		
		public void setCategory(String category) {
			this.category = category;
		}
		
		public String getSubcategory() {
			return subcategory;
		}
		
		public void setSubcategory(String subcategory) {
			this.subcategory = subcategory;
		}
		
		public String getReportType() {
			return reportType;
		}
		
		public void setReportType(String reportType) {
			this.reportType = reportType;
		}
		
		public String getReportYear() {
			return reportYear;
		}
		
		public void setReportYear(String reportYear) {
			this.reportYear = reportYear;
		}
		
		public String getReportScope() {
			return reportScope;
		}
		
		public void setReportScope(String reportScope) {
			this.reportScope = reportScope;
		}
	}
	
	/**
	 * Parameter definition
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Parameter {
		
		private String name;
		
		private String label;
		
		private String type;
		
		private boolean required;
		
		public String getName() {
			return name;
		}
		
		public void setName(String name) {
			this.name = name;
		}
		
		public String getLabel() {
			return label;
		}
		
		public void setLabel(String label) {
			this.label = label;
		}
		
		public String getType() {
			return type;
		}
		
		public void setType(String type) {
			this.type = type;
		}
		
		public boolean isRequired() {
			return required;
		}
		
		public void setRequired(boolean required) {
			this.required = required;
		}
	}
	
	/**
	 * Base cohort definition with optional filterMap for data consistency.
	 * <p>
	 * The filterMap allows column queries to reference specific identifiers from the base cohort
	 * result set, ensuring data consistency when multiple rows exist per patient.
	 * <p>
	 * <b>Example:</b>
	 * 
	 * <pre>
	 * {
	 *   "type": "SQL",
	 *   "name": "Patient Cohort",
	 *   "config": {
	 *     "sql": "SELECT DISTINCT vl.patient_id, episode_id, native_order_id
	 *             FROM mamba_fact_viral_load_episode vl
	 *             WHERE vl.accession_number IS NOT NULL
	 *               AND vl.order_date BETWEEN :startDate AND :endDate",
	 *     "filterMap": {
	 *       "patientId": "vl.patient_id",
	 *       "orderId": "vl.native_order_id",
	 *       "episodeId": "vl.episode_id"
	 *     }
	 *   }
	 * }
	 * </pre>
	 * <p>
	 * <b>Frontend Instructions:</b>
	 * <ul>
	 * <li>Add filterMap to config when the base cohort SELECTs multiple identifier columns</li>
	 * <li>Each key in filterMap becomes a parameter available to column queries (e.g., :orderId)</li>
	 * <li>The value should be the column reference (can include table alias)</li>
	 * <li>Column SQL can then use these parameters to filter to the exact matched row</li>
	 * <li>Use consistent parameter naming - filterMap keys should match the parameter names used in
	 * column SQL</li>
	 * </ul>
	 * <p>
	 * <b>Example Column SQL using filterMap:</b>
	 * 
	 * <pre>
	 * "dataDefinition": {
	 *   "type": "SQL",
	 *   "config": {
	 *     "sql": "SELECT vl.sample_collection_date FROM mamba_fact_viral_load_episode vl
	 *             WHERE vl.patient_id = :patientId
	 *               AND vl.native_order_id = :orderId
	 *               AND vl.episode_id = :episodeId
	 *             ORDER BY vl.sample_collection_date DESC LIMIT 1"
	 *   }
	 * }
	 * </pre>
	 * <p>
	 * <b>Backward Compatibility:</b>
	 * <ul>
	 * <li>filterMap is optional - existing reports without it continue to work</li>
	 * <li>When absent, column queries use only :patientId parameter</li>
	 * <li>Reports without filterMap will use the original batched query behavior (same table
	 * detection)</li>
	 * </ul>
	 * <p>
	 * <b>Validation Rules:</b>
	 * <ul>
	 * <li>All filterMap keys must correspond to columns in the base cohort SELECT clause</li>
	 * <li>filterMap values should reference valid column expressions (with or without table alias)</li>
	 * <li>Column SQL should use parameter names that match filterMap keys (prefixed with :)</li>
	 * </ul>
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class BaseCohortDefinition {
		
		private String type;
		
		private String name;
		
		private Map<String, Object> config;
		
		public String getType() {
			return type;
		}
		
		public void setType(String type) {
			this.type = type;
		}
		
		public String getName() {
			return name;
		}
		
		public void setName(String name) {
			this.name = name;
		}
		
		public Map<String, Object> getConfig() {
			return config;
		}
		
		public void setConfig(Map<String, Object> config) {
			this.config = config;
		}
		
		/**
		 * Extracts the filterMap from the config, if present.
		 * 
		 * @return Map of filter parameter names to column references, or empty map if not present
		 */
		@SuppressWarnings("unchecked")
		public Map<String, String> getFilterMap() {
			if (config == null || !config.containsKey("filterMap")) {
				return new java.util.HashMap<String, String>();
			}
			Object filterMapObj = config.get("filterMap");
			if (filterMapObj instanceof Map) {
				return (Map<String, String>) filterMapObj;
			}
			return new java.util.HashMap<String, String>();
		}
		
		/**
		 * Checks if this base cohort definition has a filterMap configured.
		 * 
		 * @return true if filterMap is present and non-empty
		 */
		public boolean hasFilterMap() {
			return !getFilterMap().isEmpty();
		}
	}
	
	/**
	 * Dataset definition
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class DataSetDefinition {
		
		private String name;
		
		private String type; // "PATIENT_DATA_SET", "SQL_DATA_SET", "INDICATOR_DATA_SET"
		
		private RowFilter rowFilter;
		
		private Column[] columns;
		
		public String getName() {
			return name;
		}
		
		public void setName(String name) {
			this.name = name;
		}
		
		public String getType() {
			return type;
		}
		
		public void setType(String type) {
			this.type = type;
		}
		
		public RowFilter getRowFilter() {
			return rowFilter;
		}
		
		public void setRowFilter(RowFilter rowFilter) {
			this.rowFilter = rowFilter;
		}
		
		public Column[] getColumns() {
			return columns;
		}
		
		public void setColumns(Column[] columns) {
			this.columns = columns;
		}
	}
	
	/**
	 * Row filter
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class RowFilter {
		
		private String type;
		
		private String name;
		
		private Map<String, Object> config;
		
		public String getType() {
			return type;
		}
		
		public void setType(String type) {
			this.type = type;
		}
		
		public String getName() {
			return name;
		}
		
		public void setName(String name) {
			this.name = name;
		}
		
		public Map<String, Object> getConfig() {
			return config;
		}
		
		public void setConfig(Map<String, Object> config) {
			this.config = config;
		}
	}
	
	/**
	 * Column definition
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Column {
		
		private String name;
		
		private String key;
		
		private DataDefinition dataDefinition;
		
		private Converter converter;
		
		private Map<String, Object> _metadata;
		
		public String getName() {
			return name;
		}
		
		public void setName(String name) {
			this.name = name;
		}
		
		public String getKey() {
			return key;
		}
		
		public void setKey(String key) {
			this.key = key;
		}
		
		public DataDefinition getDataDefinition() {
			return dataDefinition;
		}
		
		public void setDataDefinition(DataDefinition dataDefinition) {
			this.dataDefinition = dataDefinition;
		}
		
		public Converter getConverter() {
			return converter;
		}
		
		public void setConverter(Converter converter) {
			this.converter = converter;
		}
		
		public Map<String, Object> get_metadata() {
			return _metadata;
		}
		
		public void set_metadata(Map<String, Object> _metadata) {
			this._metadata = _metadata;
		}
		
		/**
		 * Get the position from metadata for column ordering.
		 * 
		 * @return the position value, or null if not set
		 */
		public Integer getPosition() {
			if (_metadata != null && _metadata.containsKey("position")) {
				Object pos = _metadata.get("position");
				if (pos instanceof Number) {
					return ((Number) pos).intValue();
				}
				if (pos instanceof String) {
					try {
						return Integer.parseInt((String) pos);
					}
					catch (NumberFormatException e) {
						// ignore
					}
				}
			}
			return null;
		}
	}
	
	/**
	 * Data definition
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class DataDefinition {
		
		private String type;
		
		private Map<String, Object> config;
		
		public String getType() {
			return type;
		}
		
		public void setType(String type) {
			this.type = type;
		}
		
		public Map<String, Object> getConfig() {
			return config;
		}
		
		public void setConfig(Map<String, Object> config) {
			this.config = config;
		}
	}
	
	/**
	 * Converter definition
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Converter {
		
		private String type;
		
		private Map<String, Object> config;
		
		public String getType() {
			return type;
		}
		
		public void setType(String type) {
			this.type = type;
		}
		
		public Map<String, Object> getConfig() {
			return config;
		}
		
		public void setConfig(Map<String, Object> config) {
			this.config = config;
		}
	}
}
