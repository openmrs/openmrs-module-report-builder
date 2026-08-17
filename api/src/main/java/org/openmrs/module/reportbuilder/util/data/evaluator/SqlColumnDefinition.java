package org.openmrs.module.reportbuilder.util.data.evaluator;

import org.openmrs.module.reporting.data.DataDefinition;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a SQL-based column definition with metadata for batch evaluation.
 */
public class SqlColumnDefinition {

	private final String columnKey;

	private final String columnName;

	private final DataDefinition dataDefinition;

	private final String sql;

	private boolean batchable = true;

	private SqlQueryAnalyzer.SqlColumnGroup group;

	// Store per-patient results after batched evaluation
	private Map<Integer, Object> evaluatedValues = new HashMap<>();

	public SqlColumnDefinition(String columnKey, String columnName, DataDefinition dataDefinition, String sql) {
		this.columnKey = columnKey;
		this.columnName = columnName;
		this.dataDefinition = dataDefinition;
		this.sql = sql;
	}

	public String getColumnKey() {
		return columnKey;
	}

	public String getColumnName() {
		return columnName;
	}

	public DataDefinition getDataDefinition() {
		return dataDefinition;
	}

	public String getSql() {
		return sql;
	}

	public boolean isBatchable() {
		return batchable;
	}

	public void setBatchable(boolean batchable) {
		this.batchable = batchable;
	}

	public SqlQueryAnalyzer.SqlColumnGroup getGroup() {
		return group;
	}

	public void setGroup(SqlQueryAnalyzer.SqlColumnGroup group) {
		this.group = group;
	}

	/**
	 * Gets the evaluated value for a specific patient.
	 */
	public Object getEvaluatedValue(Integer patientId) {
		return evaluatedValues.get(patientId);
	}

	/**
	 * Sets the evaluated value for a specific patient.
	 */
	public void setEvaluatedValue(Integer patientId, Object value) {
		evaluatedValues.put(patientId, value);
	}

	/**
	 * Returns all evaluated values for this column (patientId -> value).
	 */
	public Map<Integer, Object> getEvaluatedValues() {
		return evaluatedValues;
	}

	/**
	 * Returns the index of this column within its group. Used to map results
	 * from the combined query result set.
	 */
	public int getGroupIndex() {
		if (group != null) {
			return group.getColumns().indexOf(this);
		}
		return -1;
	}
}
