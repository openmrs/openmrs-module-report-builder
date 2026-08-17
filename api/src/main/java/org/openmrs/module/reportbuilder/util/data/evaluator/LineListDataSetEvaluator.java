package org.openmrs.module.reportbuilder.util.data.evaluator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.openmrs.Cohort;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType;
import org.openmrs.PersonAddress;
import org.openmrs.PersonAttribute;
import org.openmrs.PersonAttributeType;
import org.openmrs.PersonName;
import org.openmrs.annotation.Handler;
import org.openmrs.api.context.Context;
import org.openmrs.module.reporting.common.DateUtil;
import org.openmrs.module.reporting.data.DataDefinition;
import org.openmrs.module.reporting.data.converter.DataConverter;
import org.openmrs.module.reporting.data.converter.PropertyConverter;
import org.openmrs.module.reporting.data.patient.definition.PatientDataDefinition;
import org.openmrs.module.reporting.data.patient.service.PatientDataService;
import org.openmrs.module.reporting.data.person.definition.PersonDataDefinition;
import org.openmrs.module.reporting.data.person.service.PersonDataService;
import org.openmrs.module.reporting.dataset.DataSetRow;
import org.openmrs.module.reporting.dataset.SimpleDataSet;
import org.openmrs.module.reporting.dataset.definition.DataSetDefinition;
import org.openmrs.module.reporting.dataset.definition.evaluator.DataSetEvaluator;
import org.openmrs.module.reporting.evaluation.EvaluationContext;
import org.openmrs.module.reporting.evaluation.EvaluationException;
import org.openmrs.module.reporting.evaluation.querybuilder.SqlQueryBuilder;
import org.openmrs.module.reporting.evaluation.service.EvaluationService;
import org.openmrs.module.reportbuilder.contract.LegacyGenericReportSchema;
import org.openmrs.module.reportbuilder.legacyconfig.resolver.GenericConverterResolver;
import org.openmrs.module.reportbuilder.legacyconfig.resolver.GenericDataDefinitionResolver;
import org.openmrs.module.reportbuilder.util.PatientDataHelper;
import org.openmrs.module.reportbuilder.util.data.definition.LineListDataSetDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.util.*;

/**
 * Evaluator for ETL-based line listing reports. Reads a JSON configuration file
 * (LegacyGenericReportSchema format) and builds a patient dataset by:
 * <ol>
 * <li>Parsing the baseCohortDefinition SQL to get patient IDs</li>
 * <li>Creating columns from the dataSetDefinitions</li>
 * <li>Evaluating each column for each patient</li>
 * </ol>
 * SQL and CALCULATION columns are evaluated per-patient via direct SQL execution, with optimization
 * to batch same-table columns into single queries. Typed OpenMRS data definitions (PERSON_NAME,
 * PERSON_ATTRIBUTE, PERSON_ADDRESS, IDENTIFIER, GENDER, BIRTHDATE) are evaluated once over the
 * whole patient cohort via the reporting module's PatientDataService / PersonDataService and looked
 * up per row.
 */
@Handler(supports = { LineListDataSetDefinition.class })
public class LineListDataSetEvaluator implements DataSetEvaluator {
	
	private static final Logger log = LoggerFactory.getLogger(LineListDataSetEvaluator.class);
	
	@Autowired
	private EvaluationService evaluationService;
	
	private final GenericDataDefinitionResolver dataDefinitionResolver;
	
	private final GenericConverterResolver converterResolver;
	
	// Store filter values extracted from base cohort execution
	private FilterValues filterValues;
	
	public LineListDataSetEvaluator() {
		this.dataDefinitionResolver = new GenericDataDefinitionResolver();
		this.converterResolver = new GenericConverterResolver();
	}
	
	@Override
	public SimpleDataSet evaluate(DataSetDefinition dataSetDefinition, EvaluationContext evaluationContext)
	        throws EvaluationException {
		LineListDataSetDefinition definition = (LineListDataSetDefinition) dataSetDefinition;
		
		SimpleDataSet dataSet = new SimpleDataSet(definition, evaluationContext);
		
		File reportDesign = definition.getReportDesign();
		if (reportDesign == null) {
			throw new RuntimeException("Report design file is not configured");
		}
		if (!reportDesign.exists()) {
			throw new RuntimeException("Report design file not found: " + reportDesign.getAbsolutePath());
		}
		
		try {
			ObjectMapper objectMapper = new ObjectMapper();
			LegacyGenericReportSchema.ReportDefinition reportConfig = objectMapper.readValue(reportDesign,
			    LegacyGenericReportSchema.ReportDefinition.class);
			
			// Initialize filter values from base cohort configuration
			Map<String, String> filterMap = reportConfig.getBaseCohortDefinition() != null ? reportConfig
			        .getBaseCohortDefinition().getFilterMap() : new HashMap<String, String>();
			this.filterValues = new FilterValues(filterMap);
			
			// Get base cohort SQL for filter extraction
			String baseCohortSql = getBaseCohortSql(reportConfig);
			
			// Get patient IDs from base cohort definition
			Set<Integer> patientIds = getPatientIdsFromBaseCohort(reportConfig, evaluationContext);
			
			log.debug("Found {} patients in line list", patientIds.size());
			
			// Get the first PATIENT_DATA_SET dataset definition
			LegacyGenericReportSchema.DataSetDefinition patientDataSet = findPatientDataSet(reportConfig);
			if (patientDataSet == null) {
				throw new RuntimeException("No PATIENT_DATA_SET found in report configuration");
			}
			
			// Scope all per-cohort data evaluation to this report's patient set.
			evaluationContext.setBaseCohort(new Cohort(patientIds));
			
			// Build column definitions
			Map<String, ColumnDefinition> columns = buildColumnDefinitions(patientDataSet);
			
			// Pre-evaluate typed data definitions once over the whole cohort (one service call each).
			Map<String, Map<Integer, Object>> columnValueMaps = preEvaluateColumns(columns, evaluationContext);
			
			// Pre-evaluate SQL columns using batched queries for same-table columns
			Map<String, Map<Integer, Object>> sqlColumnValues = preEvaluateSqlColumns(columns, patientIds,
			    evaluationContext, baseCohortSql);
			// Merge SQL values into the main column value map
			columnValueMaps.putAll(sqlColumnValues);
			
			PatientDataHelper pdh = new PatientDataHelper();
			
			// Evaluate each column for each patient and add rows
			for (Integer patientId : patientIds) {
				DataSetRow row = new DataSetRow();
				
				for (Map.Entry<String, ColumnDefinition> entry : columns.entrySet()) {
					String columnKey = entry.getKey();
					ColumnDefinition colDef = entry.getValue();
					
					try {
						Object value = resolveColumnValue(colDef, patientId, evaluationContext,
						    columnValueMaps.get(columnKey));
						pdh.addCol(row, columnKey, value);
					}
					catch (Exception e) {
						log.error("Failed to evaluate column {} for patient {}: {}", columnKey, patientId, e.getMessage());
						pdh.addCol(row, columnKey, null);
					}
				}
				
				dataSet.addRow(row);
			}
			
			log.info("Evaluated line list with {} rows", dataSet.getRows().size());
			return dataSet;
			
		}
		catch (Exception e) {
			throw new EvaluationException("Failed to evaluate line list dataset: " + e.getMessage(), e);
		}
	}
	
	/**
	 * Resolves a single column's value for a patient. First checks for pre-fetched values (from
	 * batched SQL or typed cohort evaluation), then falls back to per-patient SQL execution if
	 * needed.
	 */
	private Object resolveColumnValue(ColumnDefinition colDef, Integer patientId, EvaluationContext context,
	        Map<Integer, Object> preFetchedValues) {
		// Check if we have pre-fetched values (from batched SQL or typed cohort evaluation)
		if (preFetchedValues != null && preFetchedValues.containsKey(patientId)) {
			return unwrapValue(preFetchedValues.get(patientId), colDef);
		}
		
		// Fallback: evaluate SQL column individually (for non-batchable columns)
		DataDefinition dataDef = colDef.getDataDefinition();
		if (isSqlPatientDataDefinition(dataDef)) {
			return evaluateSqlPatientDataDefinition(dataDef, patientId, context);
		}
		
		return null;
	}
	
	/**
	 * Pre-evaluates SQL columns using batched queries. Groups compatible SQL columns together and
	 * executes a single query per patient per group instead of one query per column per patient.
	 * 
	 * @param columns All column definitions
	 * @param patientIds Patient IDs to evaluate for
	 * @param context Evaluation context with parameter values
	 * @param baseCohortSql Base cohort SQL to extract filters from for data consistency
	 * @return Map of columnKey -> patientId -> value
	 */
	private Map<String, Map<Integer, Object>> preEvaluateSqlColumns(Map<String, ColumnDefinition> columns,
	        Set<Integer> patientIds, EvaluationContext context, String baseCohortSql) {

		// Step 1: Extract all SQL columns
		List<SqlColumnDefinition> sqlColumns = extractSqlColumns(columns);
		if (sqlColumns.isEmpty()) {
			return Collections.emptyMap();
		}

		log.info("Found {} SQL columns, attempting batched evaluation", sqlColumns.size());

		// Step 2: Group SQL columns by their query pattern, including base cohort filters
		Map<String, SqlQueryAnalyzer.SqlColumnGroup> groups = SqlQueryAnalyzer.groupSqlColumns(sqlColumns,
		    baseCohortSql);
		log.info("Grouped into {} batch groups", groups.size());

		// Step 3: Execute batched queries for each group
		for (SqlQueryAnalyzer.SqlColumnGroup group : groups.values()) {
			executeBatchedQuery(group, patientIds, context);
		}

		// Step 4: Build result map from evaluated values
		Map<String, Map<Integer, Object>> result = new HashMap<>();
		for (SqlColumnDefinition sqlCol : sqlColumns) {
			if (sqlCol.isBatchable() && sqlCol.getGroup() != null) {
				// This column was batched - use the pre-fetched values directly
				result.put(sqlCol.getColumnKey(), sqlCol.getEvaluatedValues());
			}
		}

		return result;
	}
	
	/**
	 * Extracts all SQL column definitions from the column map.
	 */
	private List<SqlColumnDefinition> extractSqlColumns(Map<String, ColumnDefinition> columns) {
		List<SqlColumnDefinition> sqlColumns = new ArrayList<>();
		for (Map.Entry<String, ColumnDefinition> entry : columns.entrySet()) {
			ColumnDefinition colDef = entry.getValue();
			DataDefinition dataDef = colDef.getDataDefinition();
			if (isSqlPatientDataDefinition(dataDef)) {
				try {
					String sql = (String) dataDef.getClass().getMethod("getSql").invoke(dataDef);
					sqlColumns.add(new SqlColumnDefinition(entry.getKey(), colDef.getName(), dataDef,
					    decodeHtmlEntities(sql)));
				}
				catch (Exception e) {
					log.warn("Could not extract SQL for column {}: {}", entry.getKey(), e.getMessage());
				}
			}
		}
		return sqlColumns;
	}
	
	/**
	 * Executes a batched query for a column group, fetching values for all patients at once.
	 * Results are distributed back to individual column definitions.
	 */
	private void executeBatchedQuery(SqlQueryAnalyzer.SqlColumnGroup group, Set<Integer> patientIds,
	        EvaluationContext context) {
		
		String combinedQueryTemplate = group.buildCombinedQuery();
		
		log.debug("Executing batched query for group with {} columns across {} patients", group.getColumns().size(),
		    patientIds.size());
		
		// Track query count for logging
		int queryCount = 0;
		
		for (Integer patientId : patientIds) {
			try {
				// Replace :patientId in the combined query
				String query = combinedQueryTemplate.replace(":patientId", String.valueOf(patientId));
				
				// Replace filterMap parameters with patient-specific values
				if (filterValues != null && !filterValues.isEmpty()) {
					query = filterValues.applyToQuery(query, patientId);
				}
				
				// Replace other parameters (dates, etc.)
				query = replaceParameterPlaceholders(query, context);
				
				// Execute the query
				SqlQueryBuilder queryBuilder = new SqlQueryBuilder(query);
				List<Object[]> results = evaluationService.evaluateToList(queryBuilder, context);
				queryCount++;
				
				if (results != null && !results.isEmpty()) {
					Object[] row = results.get(0);
					// row[0] is patient_id, row[1+] are the column values
					
					// Distribute each column value to its respective SqlColumnDefinition
					for (SqlColumnDefinition sqlCol : group.getColumns()) {
						int colIndex = sqlCol.getGroupIndex() + 1; // +1 because row[0] is patient_id
						Object value = (colIndex < row.length) ? row[colIndex] : null;
						sqlCol.setEvaluatedValue(patientId, value);
					}
				} else {
					// No results for this patient - set null for all columns
					for (SqlColumnDefinition sqlCol : group.getColumns()) {
						sqlCol.setEvaluatedValue(patientId, null);
					}
				}
			}
			catch (Exception e) {
				log.error("Failed to execute batched query for patient {}: {}", patientId, e.getMessage());
				// Set null for all columns for this patient
				for (SqlColumnDefinition sqlCol : group.getColumns()) {
					sqlCol.setEvaluatedValue(patientId, null);
				}
			}
		}
		
		log.info("Executed {} queries for {} columns (batched)", queryCount, group.getColumns().size());
	}
	
	/**
	 * Pre-evaluates every non-SQL column's data definition once over the cohort, returning a
	 * per-column map of patientId -&gt; value. Person-level definitions are evaluated via
	 * PersonDataService (patient ids are person ids); patient-level definitions via
	 * PatientDataService.
	 */
	private Map<String, Map<Integer, Object>> preEvaluateColumns(Map<String, ColumnDefinition> columns,
	        EvaluationContext context) {
		Map<String, Map<Integer, Object>> result = new HashMap<String, Map<Integer, Object>>();
		PersonDataService personDataService = null;
		PatientDataService patientDataService = null;
		
		for (Map.Entry<String, ColumnDefinition> entry : columns.entrySet()) {
			ColumnDefinition colDef = entry.getValue();
			DataDefinition dataDef = colDef.getDataDefinition();
			if (isSqlPatientDataDefinition(dataDef)) {
				continue; // handled by batched SQL evaluation
			}
			Map<Integer, Object> values;
			try {
				if (dataDef instanceof PersonDataDefinition) {
					if (personDataService == null) {
						personDataService = Context.getService(PersonDataService.class);
					}
					values = personDataService.evaluate((PersonDataDefinition) dataDef, context).getData();
				} else if (dataDef instanceof PatientDataDefinition) {
					if (patientDataService == null) {
						patientDataService = Context.getService(PatientDataService.class);
					}
					values = patientDataService.evaluate((PatientDataDefinition) dataDef, context).getData();
				} else {
					log.warn("Unsupported data definition type for cohort evaluation: {}", dataDef.getClass()
					        .getSimpleName());
					values = new HashMap<Integer, Object>();
				}
			}
			catch (Exception e) {
				log.error("Failed to evaluate column {} over cohort: {}", colDef.getKey(), e.getMessage(), e);
				values = new HashMap<Integer, Object>();
			}
			result.put(entry.getKey(), values);
		}
		return result;
	}
	
	/**
	 * Converts a resolved data value into a display value: applies the column converter when
	 * present, otherwise unwraps known OpenMRS value objects (PersonName, PatientIdentifier,
	 * PersonAttribute, PersonAddress) into plain scalar values.
	 */
	private Object unwrapValue(Object value, ColumnDefinition colDef) {
		if (value == null) {
			return null;
		}
		
		if (colDef.getConverter() != null) {
			try {
				return colDef.getConverter().convert(value);
			}
			catch (Exception e) {
				log.warn("Converter failed for column {}: {}", colDef.getKey(), e.getMessage());
			}
		}
		
		// Collapse single-element collections (e.g. identifier lists).
		if (value instanceof Collection) {
			Collection<?> collection = (Collection<?>) value;
			if (collection.isEmpty()) {
				return null;
			}
			value = collection.iterator().next();
			if (value == null) {
				return null;
			}
		}
		
		if (value instanceof PersonName) {
			return ((PersonName) value).getFullName();
		}
		if (value instanceof PatientIdentifier) {
			return ((PatientIdentifier) value).getIdentifier();
		}
		if (value instanceof PersonAttribute) {
			return ((PersonAttribute) value).getValue();
		}
		if (value instanceof PersonAddress) {
			return extractAddressField((PersonAddress) value, colDef.getAddressField());
		}
		return value;
	}
	
	/**
	 * Extracts a single address field (e.g. cityVillage, address5) from a PersonAddress, falling
	 * back to the full address string when the field cannot be read.
	 */
	private Object extractAddressField(PersonAddress address, String field) {
		if (field == null || field.trim().isEmpty()) {
			return address.toString();
		}
		try {
			return new PropertyConverter(PersonAddress.class, field).convert(address);
		}
		catch (Exception e) {
			log.warn("Could not extract address field '{}': {}", field, e.getMessage());
			return address.toString();
		}
	}
	
	private boolean isSqlPatientDataDefinition(DataDefinition dataDef) {
		return dataDef != null && "SqlPatientDataDefinition".equals(dataDef.getClass().getSimpleName());
	}
	
	/**
	 * Extract the base cohort SQL for filter extraction. This is used to ensure data consistency by
	 * including the base cohort's WHERE conditions in batched column queries.
	 */
	private String getBaseCohortSql(LegacyGenericReportSchema.ReportDefinition reportConfig) {
		LegacyGenericReportSchema.BaseCohortDefinition baseCohort = reportConfig.getBaseCohortDefinition();
		if (baseCohort == null) {
			return null;
		}
		
		if (!"SQL".equalsIgnoreCase(baseCohort.getType())) {
			return null;
		}
		
		Map<String, Object> config = baseCohort.getConfig();
		if (config == null || !config.containsKey("sql")) {
			return null;
		}
		
		return (String) config.get("sql");
	}
	
	/**
	 * Get patient IDs from the base cohort definition SQL, and extract filter values if filterMap
	 * is configured.
	 */
	private Set<Integer> getPatientIdsFromBaseCohort(LegacyGenericReportSchema.ReportDefinition reportConfig,
	        EvaluationContext context) {
		Set<Integer> patientIds = new HashSet<Integer>();
		
		LegacyGenericReportSchema.BaseCohortDefinition baseCohort = reportConfig.getBaseCohortDefinition();
		if (baseCohort == null) {
			throw new RuntimeException("Base cohort definition is required");
		}
		
		if (!"SQL".equalsIgnoreCase(baseCohort.getType())) {
			throw new RuntimeException("Only SQL base cohort definitions are supported, got: " + baseCohort.getType());
		}
		
		Map<String, Object> config = baseCohort.getConfig();
		if (config == null || !config.containsKey("sql")) {
			throw new RuntimeException("Base cohort definition missing SQL query");
		}
		
		String sql = (String) config.get("sql");
		sql = decodeHtmlEntities(sql);
		// Replace all parameter placeholders with resolved values from EvaluationContext
		sql = replaceParameterPlaceholders(sql, context);
		
		// Extract column mappings for filter values if filterMap is present
		Map<Integer, String> columnMappings = SelectClauseParser.extractColumnMappings(sql, filterValues.getFilterMap());
		
		try {
			SqlQueryBuilder queryBuilder = new SqlQueryBuilder(sql);
			List<Object[]> results = evaluationService.evaluateToList(queryBuilder, context);
			
			for (Object[] row : results) {
				if (row != null && row.length > 0) {
					Object id = row[0];
					Integer patientId = null;
					
					if (id instanceof Number) {
						patientId = ((Number) id).intValue();
						patientIds.add(patientId);
					} else if (id != null) {
						try {
							patientId = Integer.parseInt(id.toString());
							patientIds.add(patientId);
						}
						catch (NumberFormatException e) {
							log.warn("Could not parse patient ID: {}", id);
						}
					}
					
					// Extract filter values for this patient
					if (patientId != null && !columnMappings.isEmpty()) {
						filterValues.addValues(patientId, row, columnMappings);
					}
				}
			}
		}
		catch (Exception e) {
			log.error("Failed to execute base cohort SQL: {}", e.getMessage(), e);
			throw new RuntimeException("Failed to execute base cohort SQL: " + e.getMessage(), e);
		}
		
		return patientIds;
	}
	
	/**
	 * Find the PATIENT_DATA_SET dataset definition
	 */
	private LegacyGenericReportSchema.DataSetDefinition findPatientDataSet(
	        LegacyGenericReportSchema.ReportDefinition reportConfig) {
		if (reportConfig.getDataSetDefinitions() == null) {
			return null;
		}
		
		for (LegacyGenericReportSchema.DataSetDefinition dsd : reportConfig.getDataSetDefinitions()) {
			if ("PATIENT_DATA_SET".equalsIgnoreCase(dsd.getType())) {
				return dsd;
			}
		}
		
		return null;
	}
	
	/**
	 * Build column definitions from the dataset configuration. Columns are ordered by their
	 * _metadata.position field if available, otherwise by their original order in the config.
	 */
	private Map<String, ColumnDefinition> buildColumnDefinitions(LegacyGenericReportSchema.DataSetDefinition patientDataSet) {
		Map<String, ColumnDefinition> columns = new LinkedHashMap<String, ColumnDefinition>();
		
		if (patientDataSet.getColumns() == null) {
			return columns;
		}
		
		// Sort columns by position metadata, preserving original order for columns without position
		List<LegacyGenericReportSchema.Column> sortedColumns = new ArrayList<LegacyGenericReportSchema.Column>(
		        Arrays.asList(patientDataSet.getColumns()));
		Collections.sort(sortedColumns, new java.util.Comparator<LegacyGenericReportSchema.Column>() {
			
			@Override
			public int compare(LegacyGenericReportSchema.Column c1, LegacyGenericReportSchema.Column c2) {
				Integer pos1 = c1.getPosition();
				Integer pos2 = c2.getPosition();
				if (pos1 != null && pos2 != null) {
					return pos1.compareTo(pos2);
				}
				if (pos1 != null) {
					return -1; // columns with position come first
				}
				if (pos2 != null) {
					return 1; // columns without position come after
				}
				return 0; // preserve original order
			}
		});
		
		for (LegacyGenericReportSchema.Column column : sortedColumns) {
			String key = column.getKey();
			if (key == null || key.trim().isEmpty()) {
				key = column.getName();
			}
			if (key == null || key.trim().isEmpty()) {
				log.warn("Column missing key and name, skipping");
				continue;
			}
			
			LegacyGenericReportSchema.DataDefinition dataDef = column.getDataDefinition();
			if (dataDef == null) {
				log.warn("Column {} missing data definition, skipping", key);
				continue;
			}
			
			// Check if this column should be expanded into multiple columns (e.g., MOST_RECENT_N)
			if (dataDefinitionResolver.shouldExpandColumn(dataDef)) {
				int modifierCount = dataDefinitionResolver.getModifierCount(dataDef);
				log.info("Expanding column {} into {} columns", key, modifierCount);
				
				DataConverter converter = null;
				if (column.getConverter() != null) {
					converter = converterResolver.resolveConverter(column.getConverter());
				}
				
				// Create N columns with suffixes _1, _2, _3, etc.
				for (int i = 0; i < modifierCount; i++) {
					String expandedKey = key + "_" + (i + 1);
					String expandedName = column.getName() + " " + (i + 1);
					
					DataDefinition expandedDataDef = dataDefinitionResolver
					        .createObservationDefinitionWithOffset(dataDef, i);
					if (expandedDataDef == null) {
						log.warn("Could not create expanded data definition for column {}, occurrence {}", key, i + 1);
						continue;
					}
					
					columns.put(expandedKey, new ColumnDefinition(expandedKey, expandedName, expandedDataDef, converter,
					        dataDef.getConfig()));
				}
			} else {
				// Single column - normal processing
				DataDefinition resolvedDataDef = dataDefinitionResolver.resolveDataDefinition(dataDef);
				if (resolvedDataDef == null) {
					log.warn("Could not resolve data definition for column {}, skipping", key);
					continue;
				}
				
				DataConverter converter = null;
				if (column.getConverter() != null) {
					converter = converterResolver.resolveConverter(column.getConverter());
				}
				
				columns.put(key,
				    new ColumnDefinition(key, column.getName(), resolvedDataDef, converter, dataDef.getConfig()));
			}
		}
		
		return columns;
	}
	
	/**
	 * Evaluate a SqlPatientDataDefinition for a specific patient (fallback for non-batchable
	 * columns)
	 */
	private Object evaluateSqlPatientDataDefinition(DataDefinition dataDef, Integer patientId, EvaluationContext context) {
		try {
			// Use reflection to get the SQL from SqlPatientDataDefinition
			String sql = (String) dataDef.getClass().getMethod("getSql").invoke(dataDef);
			
			if (sql == null || sql.trim().isEmpty()) {
				return null;
			}
			
			// Decode HTML entities in SQL (e.g. &lt; to <)
			sql = decodeHtmlEntities(sql);
			
			// Replace :patientId placeholder with actual patient ID
			sql = sql.replace(":patientId", String.valueOf(patientId));
			
			// Replace all parameter placeholders (dates, locations, concepts, etc.) with resolved values
			// This handles :startDate, :endDate, :location, :concept, :program, :provider, etc.
			sql = replaceParameterPlaceholders(sql, context);
			
			// Execute the query (all parameters are now resolved, no EvaluationContext binding needed)
			SqlQueryBuilder queryBuilder = new SqlQueryBuilder(sql);
			List<Object[]> results = evaluationService.evaluateToList(queryBuilder, context);
			
			if (results == null || results.isEmpty()) {
				return null;
			}
			
			// Return the first column of the first row
			Object[] firstRow = results.get(0);
			if (firstRow != null && firstRow.length > 0) {
				return firstRow[0];
			}
			
			return null;
			
		}
		catch (Exception e) {
			log.error("Failed to evaluate SqlPatientDataDefinition for patient {}: {}", patientId, e.getMessage());
			return null;
		}
	}
	
	/**
	 * Decode HTML entities in SQL string. The JSON configuration may contain HTML entities like
	 * &lt; &gt; &amp; &quot; which need to be decoded before executing the SQL.
	 */
	private String decodeHtmlEntities(String sql) {
		return sql.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"");
	}
	
	/**
	 * Replaces all parameter placeholders with resolved values from EvaluationContext. Handles
	 * Dates, Locations, Programs, and other reference types.
	 */
	private String replaceParameterPlaceholders(String sql, EvaluationContext context) {
		String result = sql;
		
		// Extract parameter names from SQL (using :parameterName pattern)
		java.util.Set<String> paramNames = extractParameterNames(sql);
		
		for (String paramName : paramNames) {
			Object value = context.getParameterValue(paramName);
			if (value == null) {
				continue;
			}
			
			String replacement = formatParameterForSql(value);
			// Replace :paramName with formatted value
			result = result.replace(":" + paramName, replacement);
		}
		
		return result;
	}
	
	/**
	 * Extracts parameter names from SQL template (e.g., :startDate, :location).
	 */
	private java.util.Set<String> extractParameterNames(String sql) {
		java.util.Set<String> params = new java.util.HashSet<String>();
		java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(":(\\w+)");
		java.util.regex.Matcher matcher = pattern.matcher(sql);
		
		while (matcher.find()) {
			params.add(matcher.group(1));
		}
		
		return params;
	}
	
	/**
	 * Formats a parameter value for SQL binding. Handles all LinelistParameterType values: - DATE,
	 * DATETIME: Date objects with optional time component - LOCATION, CONCEPT, PROGRAM, PROVIDER:
	 * OpenMRS reference types by UUID - IDENTIFIER_TYPE: PatientIdentifierType by UUID -
	 * PERSON_ATTRIBUTE: PersonAttributeType by UUID - BOOLEAN: 1/0 for SQL compatibility - NUMBER:
	 * numeric values - TEXT, LIST: string values - CODED_VALUE: typically a Concept, handled by
	 * Concept case
	 */
	private String formatParameterForSql(Object value) {
		if (value == null) {
			return "NULL";
		}
		
		// DATE and DATETIME parameters - check if time component is present
		if (value instanceof Date) {
			Date date = (Date) value;
			// Use datetime format if the date has a time component (not midnight)
			// or if it's a DATETIME parameter type
			Calendar cal = Calendar.getInstance();
			cal.setTime(date);
			if (cal.get(Calendar.HOUR_OF_DAY) != 0 || cal.get(Calendar.MINUTE) != 0 || cal.get(Calendar.SECOND) != 0
			        || cal.get(Calendar.MILLISECOND) != 0) {
				// DATETIME with time component
				return "'" + DateUtil.formatDate(date, "yyyy-MM-dd HH:mm:ss") + "'";
			}
			// DATE only (no time component or time is 00:00:00.000)
			return "'" + DateUtil.formatDate(date, "yyyy-MM-dd") + "'";
		}
		if (value instanceof org.openmrs.Location) {
			return "'" + ((org.openmrs.Location) value).getUuid() + "'";
		}
		if (value instanceof org.openmrs.Program) {
			return "'" + ((org.openmrs.Program) value).getUuid() + "'";
		}
		if (value instanceof org.openmrs.Provider) {
			return "'" + ((org.openmrs.Provider) value).getUuid() + "'";
		}
		if (value instanceof org.openmrs.Concept) {
			return "'" + ((org.openmrs.Concept) value).getUuid() + "'";
		}
		if (value instanceof org.openmrs.PatientIdentifierType) {
			return "'" + ((org.openmrs.PatientIdentifierType) value).getUuid() + "'";
		}
		if (value instanceof org.openmrs.PersonAttributeType) {
			return "'" + ((org.openmrs.PersonAttributeType) value).getUuid() + "'";
		}
		if (value instanceof Boolean) {
			return Boolean.TRUE.equals(value) ? "1" : "0";
		}
		if (value instanceof Number) {
			return value.toString();
		}
		// Default: treat as string (includes UUIDs sent as strings)
		return "'" + value.toString() + "'";
	}
	
	/**
	 * Internal class to hold column definition metadata
	 */
	private static class ColumnDefinition {
		
		private final String key;
		
		private final String name;
		
		private final DataDefinition dataDefinition;
		
		private final DataConverter converter;
		
		private final Map<String, Object> rawConfig;
		
		public ColumnDefinition(String key, String name, DataDefinition dataDefinition, DataConverter converter,
		    Map<String, Object> rawConfig) {
			this.key = key;
			this.name = name;
			this.dataDefinition = dataDefinition;
			this.converter = converter;
			this.rawConfig = rawConfig;
		}
		
		public String getKey() {
			return key;
		}
		
		public String getName() {
			return name;
		}
		
		public DataDefinition getDataDefinition() {
			return dataDefinition;
		}
		
		public DataConverter getConverter() {
			return converter;
		}
		
		/**
		 * The address field to extract (e.g. cityVillage, address5) for PERSON_ADDRESS columns,
		 * read from the raw data definition config.
		 */
		public String getAddressField() {
			if (rawConfig != null) {
				Object field = rawConfig.get("field");
				if (field != null) {
					return field.toString();
				}
			}
			return null;
		}
	}
}
