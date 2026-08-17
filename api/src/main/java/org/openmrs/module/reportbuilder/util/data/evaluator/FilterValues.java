package org.openmrs.module.reportbuilder.util.data.evaluator;

import java.util.HashMap;
import java.util.Map;

/**
 * Stores filter values extracted from base cohort execution for each patient.
 * Used to ensure data consistency when column queries reference specific identifiers.
 */
public class FilterValues {

	/**
	 * Maps patient_id to their specific filter values.
	 * Structure: patient_id → filter parameter name → value
	 * Example: {123 → {"orderId": "ORD-001", "episodeId": "E001"}}
	 */
	private final Map<Integer, Map<String, Object>> values = new HashMap<>();

	/**
	 * The filterMap configuration from base cohort.
	 * Maps parameter names to column expressions.
	 * Example: {"orderId": "vl.native_order_id", "episodeId": "vl.episode_id"}
	 */
	private final Map<String, String> filterMap;

	public FilterValues(Map<String, String> filterMap) {
		this.filterMap = filterMap;
	}

	/**
	 * Stores filter values for a specific patient.
	 *
	 * @param patientId The patient ID
	 * @param row The result row containing column values (index → value)
	 * @param columnMapping The mapping from column index to filter parameter name
	 */
	public void addValues(Integer patientId, Object[] row, Map<Integer, String> columnMapping) {
		Map<String, Object> patientValues = new HashMap<>();

		for (Map.Entry<Integer, String> entry : columnMapping.entrySet()) {
			int columnIndex = entry.getKey();
			String paramName = entry.getValue();

			if (columnIndex < row.length) {
				patientValues.put(paramName, row[columnIndex]);
			}
		}

		if (!patientValues.isEmpty()) {
			values.put(patientId, patientValues);
		}
	}

	/**
	 * Gets the filter value for a specific patient and parameter.
	 *
	 * @param patientId The patient ID
	 * @param paramName The filter parameter name (e.g., "orderId")
	 * @return The filter value, or null if not found
	 */
	public Object getValue(Integer patientId, String paramName) {
		Map<String, Object> patientValues = values.get(patientId);
		return patientValues != null ? patientValues.get(paramName) : null;
	}

	/**
	 * Gets all filter values for a specific patient.
	 *
	 * @param patientId The patient ID
	 * @return Map of parameter names to values, or empty map if not found
	 */
	public Map<String, Object> getValues(Integer patientId) {
		Map<String, Object> patientValues = values.get(patientId);
		return patientValues != null ? patientValues : new HashMap<String, Object>();
	}

	/**
	 * Checks if filter values are available for a patient.
	 *
	 * @param patientId The patient ID
	 * @return true if values exist for this patient
	 */
	public boolean hasValues(Integer patientId) {
		return values.containsKey(patientId) && !values.get(patientId).isEmpty();
	}

	/**
	 * Gets all patient IDs that have filter values.
	 *
	 * @return Set of patient IDs
	 */
	public java.util.Set<Integer> getPatientIds() {
		return values.keySet();
	}

	/**
	 * Gets the filterMap configuration.
	 *
	 * @return Map of parameter names to column expressions
	 */
	public Map<String, String> getFilterMap() {
		return filterMap;
	}

	/**
	 * Checks if any filter values are stored.
	 *
	 * @return true if values exist for any patient
	 */
	public boolean isEmpty() {
		return values.isEmpty();
	}

	/**
	 * Applies filter values to a SQL query by replacing parameter placeholders.
	 *
	 * For each parameter in filterMap (e.g., "orderId"), replaces occurrences
	 * of :orderId with the actual value for the specified patient.
	 *
	 * @param query The SQL query with parameter placeholders
	 * @param patientId The patient ID to get values for
	 * @return The query with parameters replaced
	 */
	public String applyToQuery(String query, Integer patientId) {
		if (filterMap == null || filterMap.isEmpty()) {
			return query;
		}

		Map<String, Object> patientValues = values.get(patientId);
		if (patientValues == null || patientValues.isEmpty()) {
			return query;
		}

		String result = query;
		for (Map.Entry<String, Object> entry : patientValues.entrySet()) {
			String paramName = entry.getKey();
			Object value = entry.getValue();

			// Replace :paramName with the value (quoted if string)
			String placeholder = ":" + paramName;
			String replacement;
			if (value == null) {
				replacement = "NULL";
			} else if (value instanceof Number) {
				replacement = value.toString();
			} else if (value instanceof Boolean) {
				replacement = ((Boolean) value) ? "1" : "0";
			} else {
				// String value - quote it and escape single quotes
				String strValue = value.toString().replace("'", "''");
				replacement = "'" + strValue + "'";
			}

			result = result.replace(placeholder, replacement);
		}

		return result;
	}
}
