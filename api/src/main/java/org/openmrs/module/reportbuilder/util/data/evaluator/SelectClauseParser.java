package org.openmrs.module.reportbuilder.util.data.evaluator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses SQL SELECT clauses to extract column mappings for filterMap support.
 */
public class SelectClauseParser {
	
	private static final Logger log = LoggerFactory.getLogger(SelectClauseParser.class);
	
	// Pattern to match SELECT clause
	private static final Pattern SELECT_PATTERN = Pattern.compile("SELECT\\s+(.+?)\\s+FROM", Pattern.CASE_INSENSITIVE
	        | Pattern.DOTALL);
	
	/**
	 * Extracts column mappings from the SELECT clause based on filterMap. Returns a map of column
	 * index → filter parameter name.
	 * 
	 * @param sql The SQL query
	 * @param filterMap The filterMap from base cohort config
	 * @return Map of column index (0-based) to parameter name
	 */
	public static Map<Integer, String> extractColumnMappings(String sql, Map<String, String> filterMap) {
		Map<Integer, String> mappings = new HashMap<>();

		if (filterMap == null || filterMap.isEmpty()) {
			return mappings;
		}

		// Extract SELECT clause
		Matcher selectMatcher = SELECT_PATTERN.matcher(sql);
		if (!selectMatcher.find()) {
			log.warn("Could not find SELECT clause in SQL");
			return mappings;
		}

		String selectClause = selectMatcher.group(1).trim();

		// Split by comma (but handle subqueries and functions)
		String[] columns = splitSelectColumns(selectClause);

		// Build index → parameter name mapping
		for (int i = 0; i < columns.length; i++) {
			String column = columns[i].trim();

			// Check if this column matches any filterMap entry
			for (Map.Entry<String, String> entry : filterMap.entrySet()) {
				String paramName = entry.getKey();
				String columnRef = entry.getValue();

				// Remove table alias for comparison (vl.patient_id → patient_id)
				String columnRefWithoutAlias = removeTableAlias(columnRef);
				String columnWithoutAlias = removeTableAlias(column);

				if (columnRefWithoutAlias.equalsIgnoreCase(columnWithoutAlias)) {
					mappings.put(i, paramName);
					log.debug("Column index {} ({}) mapped to filter parameter '{}'", i, column, paramName);
					break;
				}
			}
		}

		log.info("Extracted {} column mappings from SELECT clause", mappings.size());
		return mappings;
	}
	
	/**
	 * Splits SELECT columns by comma, handling nested parentheses in functions.
	 */
	private static String[] splitSelectColumns(String selectClause) {
		java.util.List<String> columns = new java.util.ArrayList<>();
		StringBuilder current = new StringBuilder();
		int parentheses = 0;

		for (int i = 0; i < selectClause.length(); i++) {
			char c = selectClause.charAt(i);

			if (c == '(') {
				parentheses++;
				current.append(c);
			}
			else if (c == ')') {
				parentheses--;
				current.append(c);
			}
			else if (c == ',' && parentheses == 0) {
				columns.add(current.toString().trim());
				current = new StringBuilder();
			}
			else {
				current.append(c);
			}
		}

		// Add the last column
		if (current.length() > 0) {
			columns.add(current.toString().trim());
		}

		return columns.toArray(new String[0]);
	}
	
	/**
	 * Removes table alias from a column reference. "vl.patient_id" → "patient_id" "patient_id" →
	 * "patient_id"
	 */
	private static String removeTableAlias(String columnRef) {
		if (columnRef == null) {
			return null;
		}
		
		// Check for table.column pattern
		int dotIndex = columnRef.indexOf('.');
		if (dotIndex > 0 && dotIndex < columnRef.length() - 1) {
			return columnRef.substring(dotIndex + 1).trim();
		}
		
		return columnRef.trim();
	}
}
