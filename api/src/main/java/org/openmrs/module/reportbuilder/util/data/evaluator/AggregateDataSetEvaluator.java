package org.openmrs.module.reportbuilder.util.data.evaluator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openmrs.annotation.Handler;
import org.openmrs.api.db.hibernate.DbSessionFactory;
import org.openmrs.module.reportbuilder.model.ValueHolder;
import org.openmrs.module.reportbuilder.util.PatientDataHelper;
import org.openmrs.module.reportbuilder.util.data.definition.AggregateReportDataSetDefinition;
import org.openmrs.module.reporting.common.DateUtil;
import org.openmrs.module.reporting.dataset.DataSetRow;
import org.openmrs.module.reporting.dataset.SimpleDataSet;
import org.openmrs.module.reporting.dataset.definition.DataSetDefinition;
import org.openmrs.module.reporting.dataset.definition.evaluator.DataSetEvaluator;
import org.openmrs.module.reporting.evaluation.EvaluationContext;
import org.openmrs.module.reporting.evaluation.EvaluationException;
import org.openmrs.module.reporting.evaluation.querybuilder.SqlQueryBuilder;
import org.openmrs.module.reporting.evaluation.service.EvaluationService;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Handler(supports = { AggregateReportDataSetDefinition.class })
public class AggregateDataSetEvaluator implements DataSetEvaluator {
	
	@Autowired
	private EvaluationService evaluationService;
	
	@Autowired
	private DbSessionFactory sessionFactory;
	
	@Override
	public SimpleDataSet evaluate(DataSetDefinition dataSetDefinition, EvaluationContext evaluationContext)
	        throws EvaluationException {
		AggregateReportDataSetDefinition definition = (AggregateReportDataSetDefinition) dataSetDefinition;
		
		SimpleDataSet dataSet = new SimpleDataSet(definition, evaluationContext);
		DataSetRow row = getReportQuery(definition, evaluationContext);
		dataSet.addRow(row);
		
		return dataSet;
	}
	
	private List<Object[]> getEtl(String q, EvaluationContext context) {
		SqlQueryBuilder query = new SqlQueryBuilder(q);
		return evaluationService.evaluateToList(query, context);
	}
	
	private DataSetRow getReportQuery(AggregateReportDataSetDefinition definition, EvaluationContext evaluationContext) {
		DataSetRow row = new DataSetRow();
		
		Date startDateValue = resolveDateParameter(evaluationContext, "startDate", definition.getStartDate());
		Date endDateValue = resolveDateParameter(evaluationContext, "endDate", definition.getEndDate());
		
		String startDate = formatDateYmd(startDateValue);
		String endDate = formatDateYmd(endDateValue);
		
		File file = definition.getReportDesign();
		
		if (file == null) {
			throw new RuntimeException("Report design file is not configured");
		}
		if (!file.exists()) {
			throw new RuntimeException("Report design file not found: " + file.getAbsolutePath());
		}
		
		ObjectMapper objectMapper = new ObjectMapper();
		
		try {
			JsonNode rootNode = objectMapper.readTree(file);
			
			// Label -> code map from the live age group dimension, used to keep value matching
			// aligned when age group labels are renamed after a report was compiled.
			Map<String, String> ageGroupCodeByLabel = resolveAgeGroupCodeByLabel(evaluationContext);
			
			if (rootNode.has("report_fields") && rootNode.path("report_fields").isArray()) {
				return evaluateLegacyReport(rootNode, evaluationContext, row, startDate, endDate, ageGroupCodeByLabel);
			}
			
			if (rootNode.has("indicators") && rootNode.path("indicators").isArray()) {
				return evaluateSingleSection(rootNode, evaluationContext, row, startDate, endDate);
			}
			
			if (rootNode.has("sections") && rootNode.path("sections").isArray()) {
				return evaluateSectionBasedReport(rootNode, evaluationContext, row, startDate, endDate);
			}
			
			throw new RuntimeException("Unsupported report design format: " + file.getAbsolutePath());
		}
		catch (Exception e) {
			// Include the root cause for better debugging
			Throwable rootCause = e;
			while (rootCause.getCause() != null) {
				rootCause = rootCause.getCause();
			}
			throw new RuntimeException("Failed to evaluate report design: " + file.getAbsolutePath() + ". Root cause: "
			        + rootCause.getMessage(), e);
		}
	}
	
	private DataSetRow evaluateLegacyReport(JsonNode rootNode, EvaluationContext evaluationContext, DataSetRow row,
	        String startDate, String endDate, Map<String, String> ageGroupCodeByLabel) {
		
		JsonNode reportFieldsArray = rootNode.path("report_fields");
		
		for (JsonNode reportField : reportFieldsArray) {
			String sqlQuery = cleanupSql(reportField.path("sqlQuery").asText());
			
			// Skip indicators with placeholder SQL
			if (sqlQuery == null || sqlQuery.trim().isEmpty() || sqlQuery.contains("PLACEHOLDER")) {
				// Add zeros for all value placeholders
				if (reportField.has("values")) {
					JsonNode valuesArray = reportField.path("values");
					PatientDataHelper pdh = new PatientDataHelper();
					for (JsonNode valueObject : valuesArray) {
						String valuePlaceHolder = valueObject.path("value_place_holder").asText();
						pdh.addCol(row, valuePlaceHolder, 0);
					}
				} else if (reportField.has("value_place_holder")) {
					String valuePlaceHolder = reportField.path("value_place_holder").asText();
					PatientDataHelper pdh = new PatientDataHelper();
					pdh.addCol(row, valuePlaceHolder, 0);
				}
				continue;
			}
			
			String query = applyDatePlaceholders(sqlQuery, startDate, endDate);
			
			try {
				List<Object[]> results = getEtl(query, evaluationContext);
				
				if (reportField.has("values")) {
					List<ValueHolder> convertedResults = convertToValueHolderList(results);
					row = placesValuesToDataSetRow(reportField, convertedResults, row, ageGroupCodeByLabel);
				} else if (reportField.has("value_place_holder")) {
					ValueHolder convertedResult = null;
					if (results != null && !results.isEmpty()) {
						convertedResult = convertSingleResultToValueHolder(results.get(0));
					}
					row = placesValueToDataSetRow(reportField, convertedResult, row);
				}
			}
			catch (Exception e) {
				// Log and continue with zeros for this indicator
				System.err.println("Error evaluating indicator " + reportField.path("indicator_name").asText() + ": "
				        + e.getMessage());
				// Add zeros for all value placeholders
				if (reportField.has("values")) {
					JsonNode valuesArray = reportField.path("values");
					PatientDataHelper pdh = new PatientDataHelper();
					for (JsonNode valueObject : valuesArray) {
						String valuePlaceHolder = valueObject.path("value_place_holder").asText();
						pdh.addCol(row, valuePlaceHolder, 0);
					}
				} else if (reportField.has("value_place_holder")) {
					String valuePlaceHolder = reportField.path("value_place_holder").asText();
					PatientDataHelper pdh = new PatientDataHelper();
					pdh.addCol(row, valuePlaceHolder, 0);
				}
			}
		}
		
		return row;
	}
	
	private DataSetRow evaluateSingleSection(JsonNode rootNode, EvaluationContext evaluationContext, DataSetRow row,
	        String startDate, String endDate) {
		
		String sectionName = rootNode.path("name").asText("");
		JsonNode indicators = rootNode.path("indicators");
		
		for (JsonNode indicator : indicators) {
			row = evaluateNewIndicator(sectionName, indicator, evaluationContext, row, startDate, endDate);
		}
		
		return row;
	}
	
	private DataSetRow evaluateSectionBasedReport(JsonNode rootNode, EvaluationContext evaluationContext, DataSetRow row,
	        String startDate, String endDate) {
		
		JsonNode sections = rootNode.path("sections");
		
		for (JsonNode section : sections) {
			String sectionName = section.path("name").asText("");
			JsonNode indicators = section.path("indicators");
			
			if (indicators.isArray()) {
				for (JsonNode indicator : indicators) {
					row = evaluateNewIndicator(sectionName, indicator, evaluationContext, row, startDate, endDate);
				}
			}
		}
		
		return row;
	}
	
	private DataSetRow evaluateNewIndicator(String sectionName, JsonNode indicator, EvaluationContext evaluationContext,
	        DataSetRow row, String startDate, String endDate) {
		
		String indicatorCode = indicator.path("code").asText("");
		String indicatorName = indicator.path("name").asText("");
		String sqlQuery = cleanupSql(indicator.path("sql").path("compiled").asText(""));
		
		if (sqlQuery == null || sqlQuery.trim().isEmpty()) {
			return row;
		}
		
		String query = applyDatePlaceholders(sqlQuery, startDate, endDate);
		List<Object[]> results = getEtl(query, evaluationContext);
		
		String baseKey = buildBaseColumnKey(indicatorCode, indicatorName, sectionName);
		PatientDataHelper pdh = new PatientDataHelper();
		
		if (results == null || results.isEmpty()) {
			pdh.addCol(row, baseKey + "_TOTAL", 0);
			return row;
		}
		
		for (Object[] result : results) {
			if (result == null || result.length == 0) {
				continue;
			}
			
			if (result.length >= 3) {
				String disag1 = safeString(result[0]);
				String disag2 = safeString(result[1]);
				int value = safeInt(result[2]);
				
				String col = baseKey + "_" + sanitizeKey(disag1) + "_" + sanitizeKey(disag2);
				pdh.addCol(row, col, value);
				continue;
			}
			
			if (result.length == 2) {
				String maybeLabel = safeString(result[0]);
				int value = safeInt(result[1]);
				
				String col = baseKey + "_" + sanitizeKey(maybeLabel);
				pdh.addCol(row, col, value);
				continue;
			}
			
			if (result.length == 1) {
				int value = safeInt(result[0]);
				pdh.addCol(row, baseKey + "_TOTAL", value);
			}
		}
		
		return row;
	}
	
	private Date resolveDateParameter(EvaluationContext context, String name, Date fallback) {
		Object val = context.getParameterValue(name);
		if (val instanceof Date) {
			return (Date) val;
		}
		return fallback;
	}
	
	private String formatDateYmd(Date date) {
		return date != null ? DateUtil.formatDate(date, "yyyy-MM-dd") : null;
	}
	
	private static String cleanupSql(String sql) {
		if (sql == null) {
			return "";
		}
		
		return sql.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
		        .replace("&#39;", "'").trim();
	}
	
	private static String applyDatePlaceholders(String sql, String startDate, String endDate) {
		String out = sql;
		
		if (startDate != null) {
			out = out.replace(":startDate", "'" + startDate + "'");
		}
		
		if (endDate != null) {
			out = out.replace(":endDate", "'" + endDate + "'");
		}
		
		return out;
	}
	
	private static String buildBaseColumnKey(String indicatorCode, String indicatorName, String sectionName) {
		String primary = indicatorCode != null && !indicatorCode.trim().isEmpty() ? indicatorCode : indicatorName;
		
		if (primary == null || primary.trim().isEmpty()) {
			primary = sectionName;
		}
		if (primary == null || primary.trim().isEmpty()) {
			primary = "VALUE";
		}
		
		return sanitizeKey(primary);
	}
	
	private static String sanitizeKey(String s) {
		if (s == null || s.trim().isEmpty()) {
			return "NA";
		}
		
		String out = s.trim().replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">");
		
		out = out.replaceAll("[^A-Za-z0-9]+", "_");
		out = out.replaceAll("_+", "_");
		out = out.replaceAll("^_", "");
		out = out.replaceAll("_$", "");
		
		return out.isEmpty() ? "NA" : out;
	}
	
	private static String safeString(Object o) {
		return o == null ? "" : String.valueOf(o);
	}
	
	private static int safeInt(Object o) {
		if (o == null) {
			return 0;
		}
		
		try {
			if (o instanceof Number) {
				return ((Number) o).intValue();
			}
			return Integer.parseInt(String.valueOf(o));
		}
		catch (Exception e) {
			return 0;
		}
	}
	
	private static boolean safeEquals(String a, String b) {
		return (a == null ? "" : a).equals(b == null ? "" : b);
	}
	
	static DataSetRow placesValuesToDataSetRow(JsonNode reportField, List<ValueHolder> values, DataSetRow row,
	        Map<String, String> ageGroupCodeByLabel) {
		JsonNode valuesArray = reportField.path("values");
		PatientDataHelper pdh = new PatientDataHelper();
		
		for (JsonNode valueObject : valuesArray) {
			String dissaggregations1 = valueObject.path("dissaggregations1").asText();
			String disaggCode = valueObject.path("disaggregation_code").asText(null);
			String dissaggregations2 = valueObject.path("dissaggregations2").asText();
			String valuePlaceHolder = valueObject.path("value_place_holder").asText();
			
			ValueHolder valueHolder = null;
			if (!values.isEmpty()) {
				int i;
				for (i = 0; i < values.size(); i++) {
					ValueHolder v = values.get(i);
					if (ageDisaggMatches(v, dissaggregations1, disaggCode, ageGroupCodeByLabel)
					        && safeEquals(v.getDisag2(), dissaggregations2)) {
						valueHolder = v;
						break;
					}
				}
			}
			
			int count = 0;
			if (valueHolder != null) {
				count = safeInt(valueHolder.getPlaceholder());
			}
			
			pdh.addCol(row, valuePlaceHolder, count);
		}
		
		return row;
	}
	
	/**
	 * A SQL disaggregation row matches a design value entry when the age value equals the compiled
	 * label, or - for designs carrying a {@code disaggregation_code} stamp - when the label
	 * resolves, via the live age group dimension, to that code. The code path keeps values aligned
	 * when age group labels are renamed after the report was compiled.
	 */
	private static boolean ageDisaggMatches(ValueHolder v, String compiledLabel, String disaggCode,
	        Map<String, String> ageGroupCodeByLabel) {
		if (safeEquals(v.getDisag1(), compiledLabel)) {
			return true;
		}
		if (disaggCode == null || ageGroupCodeByLabel == null || ageGroupCodeByLabel.isEmpty()) {
			return false;
		}
		String label = v.getDisag1() == null ? "" : v.getDisag1().trim();
		String code = ageGroupCodeByLabel.get(label);
		return code != null && code.trim().equalsIgnoreCase(disaggCode.trim());
	}
	
	/**
	 * Resolves active age group labels to their stable codes from the live dimension table. The
	 * generated indicator SQL emits the label at evaluation time, so this map is what ties a
	 * renamed label back to the code stamped into the compiled design. Returns an empty map on any
	 * failure so evaluation falls back to plain label matching.
	 */
	private Map<String, String> resolveAgeGroupCodeByLabel(EvaluationContext evaluationContext) {
		Map<String, String> out = new HashMap<String, String>();
		try {
			List<Object[]> rows = getEtl("SELECT label, code FROM report_builder_dim_age_group WHERE is_active = 1",
			    evaluationContext);
			if (rows != null) {
				int i;
				for (i = 0; i < rows.size(); i++) {
					Object[] r = rows.get(i);
					if (r == null || r.length < 2 || r[0] == null || r[1] == null) {
						continue;
					}
					String label = String.valueOf(r[0]).trim();
					String code = String.valueOf(r[1]).trim();
					if (label.isEmpty() || code.isEmpty()) {
						continue;
					}
					// First spelling wins on the rare ambiguous label; exact label matching in
					// ageDisaggMatches still takes precedence over the code path.
					if (!out.containsKey(label)) {
						out.put(label, code);
					}
				}
			}
		}
		catch (Exception e) {
			System.err.println("Could not resolve age group labels to codes; falling back to label matching: "
			        + e.getMessage());
			return new HashMap<String, String>();
		}
		return out;
	}
	
	private static DataSetRow placesValueToDataSetRow(JsonNode reportField, ValueHolder valueHolder, DataSetRow row) {
		String valuePlaceHolder = reportField.path("value_place_holder").asText();
		PatientDataHelper pdh = new PatientDataHelper();
		
		int count = 0;
		if (valueHolder != null) {
			count = safeInt(valueHolder.getPlaceholder());
		}
		
		pdh.addCol(row, valuePlaceHolder, count);
		return row;
	}
	
	public static List<ValueHolder> convertToValueHolderList(List<Object[]> results) {
		List<ValueHolder> valueHolderList = new ArrayList<ValueHolder>();
		
		if (results != null && !results.isEmpty()) {
			int i;
			for (i = 0; i < results.size(); i++) {
				Object[] result = results.get(i);
				if (result == null || result.length < 3) {
					continue;
				}
				
				String disag1 = safeString(result[0]);
				String disag2 = safeString(result[1]);
				String placeholder = safeString(result[2]);
				
				valueHolderList.add(new ValueHolder(disag1, disag2, placeholder));
			}
		}
		
		return valueHolderList;
	}
	
	public static ValueHolder convertSingleResultToValueHolder(Object[] result) {
		if (result == null || result.length == 0) {
			return null;
		}
		
		if (result.length == 1) {
			return new ValueHolder("TOTAL", null, safeString(result[0]));
		}
		
		return new ValueHolder(safeString(result[0]), null, safeString(result[1]));
	}
	
	public DbSessionFactory getSessionFactory() {
		return sessionFactory;
	}
	
	public void setSessionFactory(DbSessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}
}
