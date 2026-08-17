/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark of the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.reportbuilder.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Transforms a linelist report's v2 builder {@code configJson} into the lean, strictly-runnable
 * {@link org.openmrs.module.reportbuilder.contract.LegacyGenericReportSchema} format that
 * {@code LineListDataSetEvaluator} deserializes at runtime. The builder config carries build-only
 * detail ({@code dataSources}, {@code rowGrain}, {@code indicatorRules}, {@code buildMethod},
 * {@code version}, {@code type}, {@code _builder}, etc.) that the runtime evaluator does not
 * understand. This compiler strips all of that and normalizes the remaining structure:
 * <ul>
 * <li>SQL bind parameters are unquoted ({@code ':startDate'} -&gt; {@code :startDate}) for OpenMRS
 * {@code SqlQueryBuilder}.</li>
 * <li>Simple {@code table.column} references are compiled to proper OpenMRS data definition types
 * (e.g. {@code person.gender} -&gt; {@code PERSON_ATTRIBUTE GENDER} , {@code mamba_*} /
 * {@code etl_*} tables -&gt; per-row {@code :patientId} subqueries).</li>
 * <li>Custom per-row SQL is kept verbatim; {@code CALCULATION} {@code onDate=true} becomes
 * {@code "$ startDate}"}; {@code IDENTIFIER} gets {@code preferred=true}.</li>
 * <li>Every column receives a snake_case {@code key}.</li>
 * <li>The output contains only runtime keys ({@code name}, {@code description}, {@code parameters},
 * {@code baseCohortDefinition}, {@code dataSetDefinitions}, {@code category}, {@code reportType},
 * optional {@code limit}).</li>
 * </ul>
 * This is a faithful Java port of the frontend {@code compileToBackendConfig()} in
 * {@code openmrs-esm-report-builder/src/types/linelist/compile-config.ts}.
 * <p>
 * <b>Parameter format note:</b> SQL uses {@code :startDate} / {@code :endDate} format (not
 * {@code $ startDate} ). This matches the AggregateDataSetEvaluator convention.
 */
public final class LinelistConfigCompiler {
	
	private static final ObjectMapper MAPPER = new ObjectMapper();
	
	/** Matches a simple {@code `?table`?.`?field`?} reference (group 1 = table, group 2 = field). */
	private static final Pattern SIMPLE_REF = Pattern.compile("`?(\\w+)`?\\.\\s*`?(\\w+)`?");
	
	private static final Pattern QUOTED_PARAM_SINGLE = Pattern.compile("':(\\w+)'");
	
	private static final Pattern QUOTED_PARAM_DOUBLE = Pattern.compile("\":(\\w+)\"");
	
	private static final Pattern CUSTOM_SQL_SELECT = Pattern.compile("^\\s*SELECT\\s", Pattern.CASE_INSENSITIVE);
	
	private static final Pattern PATIENT_ID_PLACEHOLDER = Pattern.compile(":patientId\\b", Pattern.CASE_INSENSITIVE);
	
	private static final Pattern VOIDED_TABLE = Pattern.compile("encounter|obs|visit|appointment", Pattern.CASE_INSENSITIVE);
	
	private LinelistConfigCompiler() {
	}
	
	/**
	 * Compiles the v2 builder config into a clean, runnable legacy report definition.
	 * 
	 * @param v2Config the raw builder {@code configJson} tree
	 * @param name the report name (from the {@code ReportBuilderReport} entity)
	 * @param description the report description (may be {@code null})
	 * @return an {@link ObjectNode} containing only runtime keys
	 */
	public static ObjectNode compile(JsonNode v2Config, String name, String description) {
		ObjectNode root = MAPPER.createObjectNode();
		root.put("name", name == null ? "" : name);
		if (description != null && !description.isEmpty()) {
			root.put("description", description);
		}
		
		String category = v2Config.path("categoryUuid").asText("");
		root.put("category", (category == null || category.isEmpty()) ? "FACILITY_REPORTS" : category);
		root.put("reportType", "LINELIST");
		
		JsonNode limitNode = v2Config.path("limit");
		if (limitNode != null && limitNode.isNumber() && limitNode.asInt() > 0) {
			root.put("limit", limitNode.asInt());
		}
		
		root.set("parameters", compileParameters(v2Config.path("parameters")));
		
		JsonNode baseCohort = v2Config.path("baseCohortDefinition");
		root.set("baseCohortDefinition", compileCohortDefinition(baseCohort));
		
		Map<String, JsonNode> columnMetaLookup = buildColumnMetaLookup(v2Config.path("dataSources"));
		
		ArrayNode dataSetDefinitions = MAPPER.createArrayNode();
		JsonNode dsdArray = v2Config.path("dataSetDefinitions");
		if (dsdArray.isArray()) {
			for (JsonNode ds : dsdArray) {
				ObjectNode outDs = MAPPER.createObjectNode();
				outDs.put("name", ds.path("name").asText(""));
				outDs.put("type", "PATIENT_DATA_SET");
				// rowFilter mirrors the base cohort (the patient set each column is evaluated against).
				outDs.set("rowFilter", compileCohortDefinition(baseCohort));
				
				ArrayNode columns = MAPPER.createArrayNode();
				JsonNode columnsNode = ds.path("columns");
				if (columnsNode.isArray()) {
					for (JsonNode col : columnsNode) {
						ObjectNode compiledCol = compileColumn(col, columnMetaLookup);
						if (compiledCol != null) {
							if (!compiledCol.has("key")) {
								compiledCol.put("key", nameToKey(compiledCol.path("name").asText("")));
							}
							columns.add(compiledCol);
						}
					}
				}
				outDs.set("columns", columns);
				dataSetDefinitions.add(outDs);
			}
		}
		root.set("dataSetDefinitions", dataSetDefinitions);
		
		return root;
	}
	
	/**
	 * Rule: unquote SQL bind parameters to OpenMRS reporting format. {@code ':startDate'} -&gt;
	 * {@code :startDate} , {@code ":endDate"} -&gt; {@code :endDate} .
	 */
	private static String compileSqlParams(String sql) {
		if (sql == null || sql.isEmpty()) {
			return sql == null ? "" : sql;
		}
		// Unquote quoted parameters: ':startDate' -> :startDate
		String result = QUOTED_PARAM_SINGLE.matcher(sql).replaceAll(":$1");
		result = QUOTED_PARAM_DOUBLE.matcher(result).replaceAll(":$1");
		
		// Strip trailing semicolons to prevent syntax errors
		result = result.trim();
		if (result.endsWith(";")) {
			result = result.substring(0, result.length() - 1);
		}
		
		return result;
	}
	
	/**
	 * Rule: derive a snake_case {@code key} from a display name, e.g. "Full Name" -&gt;
	 * {@code full_name}.
	 */
	private static String nameToKey(String name) {
		if (name == null || name.isEmpty()) {
			return "";
		}
		return name.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
	}
	
	/**
	 * Rule: preserve the entire parameter node including {@code name/label/type/required/config/
	 * defaultValue/displayOrder}, not just the four core fields. This ensures the frontend receives
	 * the full parameter configuration needed to render proper input controls (e.g., dateRangeMode,
	 * relativePeriod, options for LIST types, validation rules, etc.).
	 * <p>
	 * Falls back to the standard {@code startDate}/{@code endDate} date parameters when none are
	 * declared.
	 */
	private static ArrayNode compileParameters(JsonNode params) {
		ArrayNode out = MAPPER.createArrayNode();
		if (params == null || !params.isArray() || params.size() == 0) {
			out.add(newParameter("startDate", "Start Date", "DATE", true));
			out.add(newParameter("endDate", "End Date", "DATE", true));
			return out;
		}
		for (JsonNode p : params) {
			String paramName = p.path("name").asText("");
			if (paramName.isEmpty()) {
				continue;
			}
			// Preserve entire parameter node including config, defaultValue, displayOrder
			out.add((JsonNode) p.deepCopy());
		}
		return out;
	}
	
	private static ObjectNode newParameter(String name, String label, String type, boolean required) {
		ObjectNode o = MAPPER.createObjectNode();
		o.put("name", name);
		o.put("label", label);
		o.put("type", type);
		o.put("required", required);
		return o;
	}
	
	/**
	 * Rule: compile the (base) cohort SQL - keep its name, fix its bind parameters, and preserve
	 * filterMap for data consistency.
	 */
	private static ObjectNode compileCohortDefinition(JsonNode cohort) {
		ObjectNode out = MAPPER.createObjectNode();
		out.put("type", "SQL");
		out.put("name", cohort.path("name").asText(""));
		ObjectNode config = MAPPER.createObjectNode();
		config.put("sql", compileSqlParams(cohort.path("config").path("sql").asText("")));
		
		// Preserve filterMap for data consistency when column queries reference the same table
		JsonNode filterMap = cohort.path("config").path("filterMap");
		if (filterMap != null && filterMap.isObject() && filterMap.size() > 0) {
			config.set("filterMap", filterMap.deepCopy());
		}
		
		out.set("config", config);
		return out;
	}
	
	/**
	 * Builds a lookup of column metadata (name/type/sourceTable) keyed by column display name, from
	 * the builder {@code dataSources[].columns} (an object keyed by display name).
	 */
	private static Map<String, JsonNode> buildColumnMetaLookup(JsonNode dataSources) {
		Map<String, JsonNode> lookup = new HashMap<String, JsonNode>();
		if (dataSources == null || !dataSources.isArray()) {
			return lookup;
		}
		for (JsonNode ds : dataSources) {
			JsonNode columns = ds.path("columns");
			if (columns.isObject()) {
				Iterator<Map.Entry<String, JsonNode>> it = columns.fields();
				while (it.hasNext()) {
					Map.Entry<String, JsonNode> entry = it.next();
					lookup.put(entry.getKey(), entry.getValue());
				}
			}
		}
		return lookup;
	}
	
	/**
	 * Rule: compile a single column's data definition. Custom per-row SQL is kept; simple
	 * {@code table.column} references are mapped to OpenMRS types; typed definitions are
	 * normalized.
	 */
	private static ObjectNode compileColumn(JsonNode col, Map<String, JsonNode> lookup) {
		// Extract key and metadata to preserve column ordering
		String key = col.has("key") ? col.path("key").asText() : null;
		JsonNode metadata = col.has("_metadata") ? col.get("_metadata") : null;
		
		JsonNode def = col.path("dataDefinition");
		String defType = def.path("type").asText("");
		ObjectNode defConfig = asObjectCopy(def.path("config"));
		String sql = defConfig.path("sql").asText("");
		
		boolean isCustomSql = CUSTOM_SQL_SELECT.matcher(sql).find() || PATIENT_ID_PLACEHOLDER.matcher(sql).find();
		JsonNode repeatResolution = col.path("repeatResolution");
		boolean hasRepeatResolution = repeatResolution.isObject();
		
		// CUSTOM SQL: keep verbatim
		if (isCustomSql) {
			return columnWith(col.path("name").asText(""), key, metadata, "SQL", sqlConfig(sql),
			    hasRepeatResolution ? repeatResolution : null, null);
		}
		
		// CALCULATION: resolve onDate (true -> ":startDate")
		if ("CALCULATION".equalsIgnoreCase(defType)) {
			ObjectNode cfg = defConfig.deepCopy();
			JsonNode onDateNode = cfg.path("onDate");
			String onDate;
			if (onDateNode.isBoolean() && onDateNode.asBoolean()) {
				onDate = ":startDate";
			} else if (onDateNode.isTextual() && !onDateNode.asText().isEmpty()) {
				onDate = onDateNode.asText();
			} else {
				onDate = ":startDate";
			}
			cfg.put("onDate", onDate);
			// CALCULATION columns do not carry repeatResolution in the builder contract.
			return columnWith(col.path("name").asText(""), key, metadata, "CALCULATION", cfg, null, null);
		}
		
		// IDENTIFIER: force preferred = true
		if ("IDENTIFIER".equalsIgnoreCase(defType)) {
			ObjectNode cfg = defConfig.deepCopy();
			cfg.put("preferred", true);
			return columnWith(col.path("name").asText(""), key, metadata, "IDENTIFIER", cfg,
			    hasRepeatResolution ? repeatResolution : null, null);
		}
		
		// PERSON_ATTRIBUTE / PERSON_NAME: keep as-is
		if ("PERSON_ATTRIBUTE".equalsIgnoreCase(defType) || "PERSON_NAME".equalsIgnoreCase(defType)) {
			return columnWith(col.path("name").asText(""), key, metadata, defType.toUpperCase(), defConfig.deepCopy(),
			    hasRepeatResolution ? repeatResolution : null, null);
		}
		
		// SQL with a simple table.column reference: compile to a typed definition or subquery
		if ("SQL".equalsIgnoreCase(defType)) {
			Matcher m = SIMPLE_REF.matcher(sql);
			if (m.find()) {
				String table = m.group(1);
				String field = m.group(2);
				ObjectNode compiled = compileSimpleReference(col.path("name").asText(""), table, field, lookup, key,
				    metadata);
				if (compiled != null) {
					if (hasRepeatResolution) {
						compiled.set("repeatResolution", repeatResolution);
					}
					return compiled;
				}
			}
		}
		
		// Fallback: preserve type (defaulting to SQL) and config
		String type = defType.isEmpty() ? "SQL" : defType.toUpperCase();
		return columnWith(col.path("name").asText(""), key, metadata, type, defConfig.deepCopy(),
		    hasRepeatResolution ? repeatResolution : null, null);
	}
	
	/**
	 * Rule: compile a simple {@code table.column} reference into the proper OpenMRS data
	 * definition.
	 * <ul>
	 * <li>{@code person.*} -&gt; PERSON_NAME / PERSON_ATTRIBUTE (GENDER, BIRTHDATE, DEATH_DATE)</li>
	 * <li>{@code person_address.*} -&gt; PERSON_ADDRESS{ADDRESS_FIELD}</li>
	 * <li>{@code mamba_*} / {@code etl_*} -&gt; per-row {@code :patientId} SQL subquery</li>
	 * </ul>
	 */
	private static ObjectNode compileSimpleReference(String columnName, String table, String field,
	        Map<String, JsonNode> lookup, String key, JsonNode metadata) {
		String lowerField = field.toLowerCase();
		
		if ("person".equals(table)) {
			if (lowerField.contains("name") || lowerField.contains("full_name")) {
				ObjectNode cfg = MAPPER.createObjectNode();
				cfg.put("type", "FULL_NAME");
				cfg.put("preferred", true);
				return columnWith(columnName, key, metadata, "PERSON_NAME", cfg, null, null);
			}
			if (lowerField.equals("gender") || lowerField.equals("sex")) {
				return attributeColumn(columnName, "GENDER", null, key, metadata);
			}
			if (lowerField.contains("birthdate") || lowerField.contains("birth_date")) {
				ObjectNode converterCfg = MAPPER.createObjectNode();
				converterCfg.put("format", "MMM dd,yyyy");
				ObjectNode converter = MAPPER.createObjectNode();
				converter.put("type", "BIRTHDATE_AGE");
				converter.set("config", converterCfg);
				return attributeColumn(columnName, "BIRTHDATE", converter, key, metadata);
			}
			if (lowerField.contains("death")) {
				return attributeColumn(columnName, "DEATH_DATE", null, key, metadata);
			}
		}
		
		if ("person_address".equals(table)) {
			Map<String, String> addressFieldMap = new HashMap<String, String>();
			addressFieldMap.put("city_village", "cityVillage");
			addressFieldMap.put("address1", "address1");
			addressFieldMap.put("address2", "address2");
			addressFieldMap.put("state_province", "stateProvince");
			addressFieldMap.put("country", "country");
			addressFieldMap.put("postal_code", "postalCode");
			addressFieldMap.put("county_district", "countyDistrict");
			ObjectNode cfg = MAPPER.createObjectNode();
			cfg.put("type", "ADDRESS_FIELD");
			cfg.put("field", addressFieldMap.getOrDefault(lowerField, field));
			return columnWith(columnName, key, metadata, "PERSON_ADDRESS", cfg, null, null);
		}
		
		if (table.startsWith("mamba_") || table.startsWith("etl_")) {
			return etlSubqueryColumn(columnName, table, field, lowerField, lookup, key, metadata);
		}
		
		// Unknown table: preserve as a simple SQL reference
		return columnWith(columnName, key, metadata, "SQL", sqlConfig("`" + table + "`.`" + field + "`"), null, null);
	}
	
	/**
	 * Builds a per-row {@code :patientId} SQL subquery for an ETL fact-table column, adding a
	 * voided filter for encounter/obs/visit/appointment tables and a date-range filter + latest-row
	 * ordering for date columns.
	 */
	private static ObjectNode etlSubqueryColumn(String columnName, String table, String field, String lowerField,
	        Map<String, JsonNode> lookup, String key, JsonNode metadata) {
		JsonNode colMeta = lookup.get(columnName);
		boolean isDateColumn = "DATE".equalsIgnoreCase(colMeta.path("type").asText("")) || lowerField.contains("date");
		
		StringBuilder subquery = new StringBuilder();
		subquery.append("SELECT e.").append(field).append(" FROM ").append(table)
		        .append(" e WHERE e.client_id = :patientId");
		
		if (VOIDED_TABLE.matcher(table).find()) {
			subquery.append(" AND e.voided = 0");
		}
		
		if (isDateColumn) {
			String dateColumn = lowerField.contains("return_visit") ? "return_visit_date" : field;
			subquery.append(" AND e.").append(dateColumn).append(" BETWEEN :startDate AND :endDate");
			subquery.append(" ORDER BY e.").append(dateColumn).append(" DESC LIMIT 1");
		} else {
			subquery.append(" LIMIT 1");
		}
		
		return columnWith(columnName, key, metadata, "SQL", sqlConfig(subquery.toString()), null, null);
	}
	
	private static ObjectNode attributeColumn(String columnName, String attributeType, ObjectNode converter, String key,
	        JsonNode metadata) {
		ObjectNode cfg = MAPPER.createObjectNode();
		cfg.put("type", attributeType);
		return columnWith(columnName, key, metadata, "PERSON_ATTRIBUTE", cfg, null, converter);
	}
	
	private static ObjectNode sqlConfig(String sql) {
		ObjectNode cfg = MAPPER.createObjectNode();
		cfg.put("sql", sql);
		return cfg;
	}
	
	/**
	 * Assembles a compiled column node: {@code name, dataDefinition: type,config},
	 * [repeatResolution], [converter]}}.
	 */
	private static ObjectNode columnWith(String name, String type, ObjectNode config, JsonNode repeatResolution,
	        ObjectNode converter) {
		return columnWith(name, null, null, type, config, repeatResolution, converter);
	}
	
	/**
	 * Assembles a compiled column node with optional key and metadata for column ordering.
	 */
	private static ObjectNode columnWith(String name, String key, JsonNode metadata, String type, ObjectNode config,
	        JsonNode repeatResolution, ObjectNode converter) {
		ObjectNode column = MAPPER.createObjectNode();
		column.put("name", name);
		if (key != null && !key.trim().isEmpty()) {
			column.put("key", key);
		}
		ObjectNode dataDefinition = MAPPER.createObjectNode();
		dataDefinition.put("type", type);
		dataDefinition.set("config", config != null ? config : MAPPER.createObjectNode());
		column.set("dataDefinition", dataDefinition);
		if (repeatResolution != null && repeatResolution.isObject()) {
			column.set("repeatResolution", repeatResolution);
		}
		if (converter != null) {
			column.set("converter", converter);
		}
		if (metadata != null && metadata.isObject()) {
			column.set("_metadata", metadata);
		}
		return column;
	}
	
	/**
	 * Returns a detached {@link ObjectNode} copy of the given node, or a fresh empty object when
	 * the node is missing/non-object.
	 */
	private static ObjectNode asObjectCopy(JsonNode node) {
		if (node != null && node.isObject()) {
			return (ObjectNode) node.deepCopy();
		}
		return MAPPER.createObjectNode();
	}
}
