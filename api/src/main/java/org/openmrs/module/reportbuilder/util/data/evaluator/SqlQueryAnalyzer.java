package org.openmrs.module.reportbuilder.util.data.evaluator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Analyzes SQL column definitions to identify columns that can be fetched together
 * in a single query. Groups columns by:
 * <ul>
 * <li>Base table name (e.g., mamba_fact_viral_load_episode)</li>
 * <li>WHERE clause pattern (e.g., patient_id = :patientId)</li>
 * <li>ORDER BY + LIMIT pattern (for LATEST/FIRST strategies)</li>
 * </ul>
 *
 * <p>Additionally extracts base cohort filters to ensure data consistency - when columns
 * reference the same table as the base cohort, the cohort's WHERE conditions are included
 * in the batched query to ensure we're selecting from the same filtered row set.
 */
public class SqlQueryAnalyzer {

	private static final Logger log = LoggerFactory.getLogger(SqlQueryAnalyzer.class);

	// Pattern to match: SELECT ... FROM table_name ... WHERE ... ORDER BY ... LIMIT
	private static final Pattern TABLE_PATTERN = Pattern.compile(
	    "FROM\\s+(\\w+(?:\\.\\w+)?)(?:\\s+(\\w+))?", Pattern.CASE_INSENSITIVE);

	private static final Pattern WHERE_PATTERN = Pattern.compile(
	    "WHERE\\s+(.+?)(?:\\s+GROUP\\s+BY|\\s+ORDER\\s+BY|\\s+LIMIT|$)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

	private static final Pattern ORDER_BY_PATTERN = Pattern.compile(
	    "ORDER\\s+BY\\s+(.+?)(?:\\s+LIMIT|$)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

	private static final Pattern LIMIT_PATTERN = Pattern.compile(
	    "LIMIT\\s+(\\d+)", Pattern.CASE_INSENSITIVE);

	private static final Pattern SELECT_COLUMN_PATTERN = Pattern.compile(
	    "SELECT\\s+(.+?)\\s+FROM", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

	private static final Pattern BETWEEN_PATTERN = Pattern.compile(
	    "(\\w+(?:\\.\\w+)?)\\s+BETWEEN\\s+:(\\w+)\\s+AND\\s+:(\\w+)", Pattern.CASE_INSENSITIVE);

	private static final Pattern IS_NOT_NULL_PATTERN = Pattern.compile(
	    "(\\w+(?:\\.\\w+)?)\\s+IS\\s+NOT\\s+NULL", Pattern.CASE_INSENSITIVE);

	/**
	 * Groups SQL column definitions by their query pattern. Returns a map where each key
	 * is a query signature and value contains all columns that can be fetched together.
	 *
	 * @param sqlColumns List of SQL column definitions to group
	 * @param baseCohortSql Optional base cohort SQL to extract filters from
	 * @return Map of group key to column group
	 */
	public static Map<String, SqlColumnGroup> groupSqlColumns(List<SqlColumnDefinition> sqlColumns,
	        String baseCohortSql) {
		Map<String, SqlColumnGroup> groups = new HashMap<>();

		// Extract base cohort filters if provided
		BaseCohortFilters cohortFilters = null;
		if (baseCohortSql != null && !baseCohortSql.trim().isEmpty()) {
			cohortFilters = extractBaseCohortFilters(baseCohortSql);
			log.debug("Extracted base cohort filters: table={}, filters={}", cohortFilters.getTableName(),
			    cohortFilters.getFilters());
		}

		for (SqlColumnDefinition colDef : sqlColumns) {
			try {
				QuerySignature signature = analyzeQuery(colDef.getSql());
				String groupKey = signature.getGroupKey();

				SqlColumnGroup group = groups.get(groupKey);
				if (group == null) {
					group = new SqlColumnGroup(signature, cohortFilters);
					groups.put(groupKey, group);
				}

				group.addColumn(colDef);
			}
			catch (Exception e) {
				log.warn("Could not analyze SQL for column {}, will evaluate separately: {}", colDef.getColumnKey(),
				    e.getMessage());
				// Mark as non-batchable - will be evaluated individually
				colDef.setBatchable(false);
			}
		}

		log.info("Grouped {} SQL columns into {} batch groups", sqlColumns.size(), groups.size());
		return groups;
	}

	/**
	 * Overloaded method for backward compatibility - groups without base cohort filters.
	 */
	public static Map<String, SqlColumnGroup> groupSqlColumns(List<SqlColumnDefinition> sqlColumns) {
		return groupSqlColumns(sqlColumns, null);
	}

	/**
	 * Extracts filters from the base cohort SQL to ensure data consistency.
	 * Returns the table name and list of WHERE conditions.
	 */
	private static BaseCohortFilters extractBaseCohortFilters(String sql) {
		BaseCohortFilters filters = new BaseCohortFilters();

		String decodedSql = decodeHtmlEntities(sql);

		// Extract table name
		Matcher tableMatcher = TABLE_PATTERN.matcher(decodedSql);
		if (tableMatcher.find()) {
			filters.tableName = tableMatcher.group(1);
			filters.tableAlias = tableMatcher.groupCount() > 1 && tableMatcher.group(2) != null ? tableMatcher.group(2)
			    : tableMatcher.group(1);
		}

		// Extract WHERE clause
		Matcher whereMatcher = WHERE_PATTERN.matcher(decodedSql);
		if (whereMatcher.find()) {
			String whereClause = whereMatcher.group(1).trim();
			filters.filters = parseWhereConditions(whereClause);
		}

		return filters;
	}

	/**
	 * Parses WHERE clause into individual conditions. Handles:
	 * - column BETWEEN :param1 AND :param2
	 * - column IS NOT NULL
	 * - column = :parameter
	 */
	private static List<WhereCondition> parseWhereConditions(String whereClause) {
		List<WhereCondition> conditions = new ArrayList<>();

		// Split by AND (but not inside parentheses)
		String[] parts = whereClause.split("\\s+AND\\s+");
		for (String part : parts) {
			part = part.trim();

			// Check for BETWEEN pattern
			Matcher betweenMatcher = BETWEEN_PATTERN.matcher(part);
			if (betweenMatcher.find()) {
				conditions.add(new WhereCondition(part, betweenMatcher.group(1), "BETWEEN",
				    new String[] { betweenMatcher.group(2), betweenMatcher.group(3) }));
				continue;
			}

			// Check for IS NOT NULL pattern
			Matcher isNotNullMatcher = IS_NOT_NULL_PATTERN.matcher(part);
			if (isNotNullMatcher.find()) {
				conditions.add(new WhereCondition(part, isNotNullMatcher.group(1), "IS_NOT_NULL", null));
				continue;
			}

			// Generic condition - store as-is
			conditions.add(new WhereCondition(part, null, "GENERIC", null));
		}

		return conditions;
	}

	/**
	 * Parses a SQL query to extract its signature components (table, where, orderBy, limit).
	 */
	private static QuerySignature analyzeQuery(String sql) {
		String decodedSql = decodeHtmlEntities(sql);

		// Extract table name
		String table = extractTableName(decodedSql);
		if (table == null) {
			throw new IllegalArgumentException("Could not extract table name from SQL");
		}

		// Extract WHERE clause (normalized)
		String where = extractWhereClause(decodedSql);

		// Extract ORDER BY (normalized)
		String orderBy = extractOrderBy(decodedSql);

		// Extract LIMIT
		Integer limit = extractLimit(decodedSql);

		return new QuerySignature(table, where, orderBy, limit);
	}

	private static String extractTableName(String sql) {
		Matcher matcher = TABLE_PATTERN.matcher(sql);
		if (matcher.find()) {
			String tableName = matcher.group(1);
			String alias = matcher.groupCount() > 1 && matcher.group(2) != null ? matcher.group(2) : tableName;
			return tableName + (alias != null && !alias.equals(tableName) ? " " + alias : "");
		}
		return null;
	}

	private static String extractWhereClause(String sql) {
		Matcher matcher = WHERE_PATTERN.matcher(sql);
		if (matcher.find()) {
			String where = matcher.group(1).trim();
			// Normalize WHERE: extract just the essential condition pattern
			return normalizeWhereClause(where);
		}
		return "";
	}

	private static String normalizeWhereClause(String where) {
		// Remove table aliases to normalize: "vl.patient_id = :patientId" -> "patient_id = :patientId"
		Pattern aliasPattern = Pattern.compile("(\\w+)\\.(\\w+)", Pattern.CASE_INSENSITIVE);
		Matcher matcher = aliasPattern.matcher(where);
		StringBuffer sb = new StringBuffer();
		Set<String> seenAliases = new LinkedHashSet<>();

		while (matcher.find()) {
			String alias = matcher.group(1);
			String column = matcher.group(2);
			seenAliases.add(alias);
			matcher.appendReplacement(sb, "{" + alias + "}." + column);
		}
		matcher.appendTail(sb);

		// Replace {alias} with just {} if consistent
		String result = sb.toString();
		for (String alias : seenAliases) {
			result = result.replace("{" + alias + "}.", "");
		}

		return result;
	}

	private static String extractOrderBy(String sql) {
		Matcher matcher = ORDER_BY_PATTERN.matcher(sql);
		if (matcher.find()) {
			String orderBy = matcher.group(1).trim();
			// Normalize ORDER BY: remove table aliases
			return normalizeOrderBy(orderBy);
		}
		return "";
	}

	private static String normalizeOrderBy(String orderBy) {
		// Remove table aliases: "vl.sample_collection_date DESC" -> "sample_collection_date DESC"
		Pattern aliasPattern = Pattern.compile("(\\w+)\\.(\\w+)", Pattern.CASE_INSENSITIVE);
		Matcher matcher = aliasPattern.matcher(orderBy);
		StringBuffer sb = new StringBuffer();

		while (matcher.find()) {
			matcher.appendReplacement(sb, matcher.group(2));
		}
		matcher.appendTail(sb);

		return sb.toString().trim();
	}

	private static Integer extractLimit(String sql) {
		Matcher matcher = LIMIT_PATTERN.matcher(sql);
		if (matcher.find()) {
			return Integer.parseInt(matcher.group(1));
		}
		return null;
	}

	private static String decodeHtmlEntities(String sql) {
		return sql.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"");
	}

	/**
	 * Represents filters extracted from the base cohort SQL.
	 */
	public static class BaseCohortFilters {

		private String tableName;

		private String tableAlias;

		private List<WhereCondition> filters = new ArrayList<>();

		public String getTableName() {
			return tableName;
		}

		public String getTableAlias() {
			return tableAlias != null ? tableAlias : tableName;
		}

		public List<WhereCondition> getFilters() {
			return filters;
		}

		public boolean hasFilters() {
			return filters != null && !filters.isEmpty();
		}
	}

	/**
	 * Represents a single WHERE condition.
	 */
	public static class WhereCondition {

		private final String originalCondition;

		private final String column;

		private final String operator;

		private final String[] parameters;

		public WhereCondition(String originalCondition, String column, String operator, String[] parameters) {
			this.originalCondition = originalCondition;
			this.column = column;
			this.operator = operator;
			this.parameters = parameters;
		}

		public String getOriginalCondition() {
			return originalCondition;
		}

		public String getColumn() {
			return column;
		}

		public String getOperator() {
			return operator;
		}

		public String[] getParameters() {
			return parameters;
		}

		/**
		 * Replaces table alias in the condition with the target alias.
		 */
		public String withAlias(String targetAlias) {
			if (column != null && column.contains(".")) {
				String[] parts = column.split("\\.");
				return originalCondition.replace(parts[0] + ".", targetAlias + ".");
			}
			return originalCondition;
		}
	}

	/**
	 * Represents the normalized signature of a SQL query for grouping purposes.
	 */
	public static class QuerySignature {

		private final String tableName;

		private final String whereClause;

		private final String orderBy;

		private final Integer limit;

		private final String tableAlias; // Extracted alias for the main table

		public QuerySignature(String tableName, String whereClause, String orderBy, Integer limit) {
			this.tableName = tableName;
			this.whereClause = whereClause;
			this.orderBy = orderBy;
			this.limit = limit;
			// Extract alias from table name (e.g., "mamba_fact_viral_load_episode vl" -> "vl")
			String[] parts = tableName.split("\\s+");
			this.tableAlias = parts.length > 1 ? parts[1] : (parts.length > 0 ? parts[0] : null);
		}

		/**
		 * Returns a unique key for this query pattern. Columns with the same key can be
		 * batched together.
		 */
		public String getGroupKey() {
			StringBuilder key = new StringBuilder();
			key.append(tableName.split("\\s+")[0]); // Base table without alias
			if (!whereClause.isEmpty()) {
				key.append("|WHERE:").append(whereClause);
			}
			if (!orderBy.isEmpty()) {
				key.append("|ORDER:").append(orderBy);
			}
			if (limit != null) {
				key.append("|LIMIT:").append(limit);
			}
			return key.toString();
		}

		public String getTableName() {
			return tableName;
		}

		public String getTableAlias() {
			return tableAlias != null ? tableAlias : tableName.split("\\s+")[0];
		}

		public String getWhereClause() {
			return whereClause;
		}

		public String getOrderBy() {
			return orderBy;
		}

		public Integer getLimit() {
			return limit;
		}

		/**
		 * Builds a combined SQL query that fetches all columns in this group at once.
		 *
		 * @param columnSelects List of SELECT expressions for each column
		 * @param baseCohortFilters Optional base cohort filters to include
		 * @return Combined SQL query
		 */
		public String buildCombinedQuery(List<String> columnSelects, BaseCohortFilters baseCohortFilters) {
			StringBuilder sql = new StringBuilder();
			sql.append("SELECT ");

			// Add patient_id first (we need this to map results)
			sql.append(getTableAlias()).append(".patient_id");

			// Add all column selections
			for (int i = 0; i < columnSelects.size(); i++) {
				sql.append(", ").append(columnSelects.get(i));
			}

			sql.append(" FROM ").append(getTableName());

			// Build WHERE clause combining column's WHERE with base cohort filters
			List<String> whereConditions = new ArrayList<>();

			// Add the column's own WHERE condition (e.g., patient_id = :patientId)
			if (!whereClause.isEmpty()) {
				whereConditions.add(whereClause);
			}

			// Add base cohort filters if they apply to the same table
			if (baseCohortFilters != null && baseCohortFilters.hasFilters()) {
				String columnTable = tableName.split("\\s+")[0];
				String cohortTable = baseCohortFilters.getTableName();

				// Check if filters apply to the same table
				if (columnTable.equalsIgnoreCase(cohortTable)) {
					for (WhereCondition filter : baseCohortFilters.getFilters()) {
						whereConditions.add(filter.withAlias(getTableAlias()));
					}
					log.debug("Applied {} base cohort filters to batched query", baseCohortFilters.getFilters().size());
				}
			}

			// Combine WHERE conditions
			if (!whereConditions.isEmpty()) {
				sql.append(" WHERE ");
				for (int i = 0; i < whereConditions.size(); i++) {
					if (i > 0) {
						sql.append(" AND ");
					}
					sql.append(whereConditions.get(i));
				}
			}

			if (!orderBy.isEmpty()) {
				sql.append(" ORDER BY ").append(orderBy);
			}

			if (limit != null) {
				sql.append(" LIMIT ").append(limit);
			}

			return sql.toString();
		}
	}

	/**
	 * Holds all SQL column definitions that share the same query signature.
	 */
	public static class SqlColumnGroup {

		private final QuerySignature signature;

		private final BaseCohortFilters baseCohortFilters;

		private final List<SqlColumnDefinition> columns = new ArrayList<>();

		public SqlColumnGroup(QuerySignature signature, BaseCohortFilters baseCohortFilters) {
			this.signature = signature;
			this.baseCohortFilters = baseCohortFilters;
		}

		public void addColumn(SqlColumnDefinition column) {
			column.setBatchable(true);
			column.setGroup(this);
			columns.add(column);
		}

		public QuerySignature getSignature() {
			return signature;
		}

		public List<SqlColumnDefinition> getColumns() {
			return columns;
		}

		/**
		 * Builds and returns the combined SQL query for this group.
		 */
		public String buildCombinedQuery() {
			List<String> columnSelects = new ArrayList<>();
			for (SqlColumnDefinition col : columns) {
				columnSelects.add(extractColumnSelect(col.getSql()));
			}
			return signature.buildCombinedQuery(columnSelects, baseCohortFilters);
		}

		private String extractColumnSelect(String sql) {
			// Extract just the SELECT part (what's being selected)
			// e.g., "SELECT vl.order_date FROM ..." -> "vl.order_date"
			Matcher matcher = SELECT_COLUMN_PATTERN.matcher(sql);
			if (matcher.find()) {
				String selectPart = matcher.group(1).trim();
				return selectPart;
			}
			return "*"; // Fallback
		}
	}
}
