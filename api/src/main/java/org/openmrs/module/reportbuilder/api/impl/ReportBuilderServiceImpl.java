/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.reportbuilder.api.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.Location;
import org.openmrs.module.reportbuilder.api.ReportBuilderService;
import org.openmrs.module.reportbuilder.api.db.ReportBuilderDAO;
import org.openmrs.module.reportbuilder.dto.SqlPreviewResult;
import org.openmrs.module.reportbuilder.legacyconfig.builder.CohortDefinitionFactory;
import org.openmrs.module.reportbuilder.legacyconfig.builder.DatasetDefinitionFactory;
import org.openmrs.module.reportbuilder.legacyconfig.builder.DesignBuilder;
import org.openmrs.module.reportbuilder.legacyconfig.builder.ParameterBuilder;
import org.openmrs.module.reportbuilder.legacyconfig.builder.ReportDefinitionFactory;
import org.openmrs.module.reportbuilder.legacyconfig.generic.GenericReportImportService;
import org.openmrs.module.reportbuilder.legacyconfig.importer.ReportImportResult;
import org.openmrs.module.reportbuilder.legacyconfig.LegacyReportImporter;
import org.openmrs.module.reportbuilder.legacyconfig.model.CohortConfig;
import org.openmrs.module.reportbuilder.legacyconfig.model.DatasetConfig;
import org.openmrs.module.reportbuilder.legacyconfig.model.DatasetRefConfig;
import org.openmrs.module.reportbuilder.legacyconfig.model.DesignConfig;
import org.openmrs.module.reportbuilder.legacyconfig.model.DesignRefConfig;
import org.openmrs.module.reportbuilder.legacyconfig.model.ParameterConfig;
import org.openmrs.module.reportbuilder.legacyconfig.model.ParameterSetConfig;
import org.openmrs.module.reportbuilder.legacyconfig.model.ReportConfig;
import org.openmrs.module.reportbuilder.legacyconfig.parser.JsonConfigParser;
import org.openmrs.module.reportbuilder.legacyconfig.resolver.ReferenceResolver;
import org.openmrs.module.reportbuilder.legacyconfig.validator.ConfigValidator;
import org.openmrs.module.reportbuilder.model.*;
import org.openmrs.module.reportbuilder.util.IndicatorSqlSync;
import org.openmrs.module.reportbuilder.util.IndicatorValidator;
import org.openmrs.module.reportbuilder.util.LineListReportDefinitionExpander;
import org.openmrs.module.reportbuilder.util.LinelistConfigCompiler;
import org.openmrs.module.reportbuilder.util.ReportDesignFileUtil;
import org.openmrs.module.reportbuilder.util.LinelistHtmlRenderer;
import org.openmrs.module.reportbuilder.util.ReportDesignHtmlRenderer;
import org.openmrs.module.reportbuilder.util.data.definition.AggregateReportDataSetDefinition;
import org.openmrs.module.reportbuilder.util.data.definition.LineListDataSetDefinition;
import org.openmrs.module.reportbuilder.validation.ReportValidationResult;
import org.openmrs.module.reporting.common.DateUtil;
import org.openmrs.module.reporting.common.MessageUtil;
import org.openmrs.module.reporting.common.ObjectUtil;
import org.openmrs.module.reporting.cohort.definition.CohortDefinition;
import org.openmrs.module.reporting.dataset.DataSet;
import org.openmrs.module.reporting.dataset.DataSetRow;
import org.openmrs.module.reporting.dataset.definition.DataSetDefinition;
import org.openmrs.module.reporting.evaluation.EvaluationUtil;
import org.openmrs.module.reporting.evaluation.parameter.Mapped;
import org.openmrs.module.reporting.evaluation.parameter.Parameter;
import org.openmrs.module.reporting.report.ReportData;
import org.openmrs.module.reporting.report.ReportDesign;
import org.openmrs.module.reporting.report.ReportDesignResource;
import org.openmrs.module.reporting.report.definition.ReportDefinition;
import org.openmrs.module.reporting.report.definition.service.ReportDefinitionService;
import org.openmrs.module.reporting.report.renderer.RenderingException;
import org.openmrs.module.reporting.report.renderer.TextTemplateRenderer;
import org.openmrs.module.reporting.report.renderer.template.TemplateEngine;
import org.openmrs.module.reporting.report.renderer.template.TemplateEngineManager;
import org.openmrs.module.reporting.report.service.ReportService;
import org.openmrs.util.OpenmrsUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Transactional
public class ReportBuilderServiceImpl extends BaseOpenmrsService implements ReportBuilderService {
	
	private static final Logger log = LoggerFactory.getLogger(ReportBuilderServiceImpl.class);
	
	private ReportBuilderDAO dao;
	
	private final ReportDesignHtmlRenderer reportDesignHtmlRenderer = new ReportDesignHtmlRenderer();
	
	private final LinelistHtmlRenderer linelistHtmlRenderer = new LinelistHtmlRenderer();
	
	private final ObjectMapper objectMapper = new ObjectMapper();
	
	private JsonConfigParser legacyConfigParser;
	
	private ReferenceResolver legacyReferenceResolver;
	
	private ParameterBuilder legacyParameterBuilder;
	
	private CohortDefinitionFactory legacyCohortDefinitionFactory;
	
	private DatasetDefinitionFactory legacyDatasetDefinitionFactory;
	
	private ReportDefinitionFactory legacyReportDefinitionFactory;
	
	private DesignBuilder legacyDesignBuilder;
	
	private ConfigValidator legacyConfigValidator;
	
	public void setDao(ReportBuilderDAO dao) {
		this.dao = dao;
	}
	
	public String renderHtmlFinalFromTemplate(ReportData reportData, ReportDesign reportDesign) {
		String templateJson = readDesignResource(reportDesign);
		Map<String, Object> values = extractFlatValues(reportData);
		return reportDesignHtmlRenderer.convert(templateJson, values, null).html;
	}
	
	@Override
	public String renderHtmlFromJsonTemplate(ReportDesign reportDesign) {
		String templateJson = readDesignResource(reportDesign);
		return reportDesignHtmlRenderer.convert(templateJson, Collections.<String, Object> emptyMap(), null).html;
	}
	
	@Override
	public String createPayloadJsonFromTemplate(ReportData reportData, ReportDesign reportDesign, String renderType,
	        Map<String, Object> flatValues, String remapJsonOptional) {
		String templateJson = readDesignResource(reportDesign);
		Map<String, Object> values = flatValues == null ? Collections.<String, Object> emptyMap() : flatValues;
		return reportDesignHtmlRenderer.convert(templateJson, values, remapJsonOptional).payloadJson;
	}
	
	public Map<String, Object> extractFlatValues(ReportData reportData) {
		Map<String, Object> out = new HashMap<String, Object>();
		if (reportData == null || reportData.getDataSets() == null) {
			return out;
		}
		
		Map<String, DataSet> dataSets = reportData.getDataSets();
		for (String dsName : dataSets.keySet()) {
			DataSet ds = dataSets.get(dsName);
			if (ds == null) {
				continue;
			}
			
			Iterator it = ds.iterator();
			while (it.hasNext()) {
				DataSetRow row = (DataSetRow) it.next();
				Map<String, Object> cols = row.getColumnValuesByKey();
				if (cols == null || cols.isEmpty()) {
					continue;
				}
				
				String code = firstString(cols, "code", "dataelement", "dataElement", "data_element");
				String age = firstString(cols, "age", "agegroup", "age_group");
				String sex = firstString(cols, "sex", "gender");
				Object valObj = firstObject(cols, "value", "count", "total");
				
				if (!isBlank(code) && !isBlank(age) && !isBlank(sex) && valObj != null) {
					out.put(code + "_" + age + "_" + sex, valObj);
					continue;
				}
				
				for (Map.Entry<String, Object> e : cols.entrySet()) {
					String k = e.getKey();
					Object v = e.getValue();
					if (k == null) {
						continue;
					}
					
					if (looksLikeKey(k) && v != null && isNumeric(v)) {
						out.put(k.trim(), v);
					}
				}
			}
		}
		
		return out;
	}
	
	private boolean looksLikeKey(String k) {
		String s = k.trim();
		int a = s.indexOf('_');
		if (a <= 0) {
			return false;
		}
		int b = s.indexOf('_', a + 1);
		if (b <= a + 1) {
			return false;
		}
		int c = s.lastIndexOf('_');
		return c > b && c < s.length() - 1;
	}
	
	private boolean isNumeric(Object v) {
		if (v instanceof Number) {
			return true;
		}
		String s = String.valueOf(v).trim();
		if (s.length() == 0) {
			return false;
		}
		try {
			Double.parseDouble(s);
			return true;
		}
		catch (Exception ignore) {
			return false;
		}
	}
	
	private String firstString(Map<String, Object> cols, String... keys) {
		Object o = firstObject(cols, keys);
		return o == null ? null : String.valueOf(o).trim();
	}
	
	private Object firstObject(Map<String, Object> cols, String... keys) {
		int i;
		for (i = 0; i < keys.length; i++) {
			String k = keys[i];
			if (cols.containsKey(k)) {
				Object v = cols.get(k);
				if (v != null && String.valueOf(v).trim().length() > 0) {
					return v;
				}
			}
		}
		return null;
	}
	
	private boolean isBlank(String s) {
		return s == null || s.trim().isEmpty();
	}
	
	public String createLegacyPayloadJson(ReportData reportData, ReportDesign reportDesign) {
		try {
			TextTemplateRenderer textTemplateRenderer = new TextTemplateRenderer();
			ReportDesignResource res = textTemplateRenderer.getTemplate(reportDesign);
			String templateContents = new String(res.getContents(), StandardCharsets.UTF_8);
			
			String rendered = fillTemplateWithReportData(templateContents, reportData, reportDesign);
			return removeQuotesFromValues(rendered);
		}
		catch (Exception e) {
			throw new RuntimeException("Failed legacy payload build", e);
		}
	}
	
	private String fillTemplateWithReportData(String templateContents, ReportData reportData, ReportDesign reportDesign)
	        throws IOException, RenderingException {
		try {
			TextTemplateRenderer renderer = new TextTemplateRenderer();
			Map<String, Object> replacements = renderer.getBaseReplacementData(reportData, reportDesign);
			
			String templateEngineName = reportDesign.getPropertyValue("templateType", (String) null);
			TemplateEngine engine = TemplateEngineManager.getTemplateEngineByName(templateEngineName);
			
			if (engine != null) {
				Map<String, Object> bindings = new HashMap<String, Object>();
				bindings.put("reportData", reportData);
				bindings.put("reportDesign", reportDesign);
				bindings.put("data", replacements);
				bindings.put("util", new ObjectUtil());
				bindings.put("dateUtil", new DateUtil());
				bindings.put("msg", new MessageUtil());
				templateContents = engine.evaluate(templateContents, bindings);
			}
			
			String prefix = renderer.getExpressionPrefix(reportDesign);
			String suffix = renderer.getExpressionSuffix(reportDesign);
			
			Object evaluated = EvaluationUtil.evaluateExpression(templateContents, replacements, prefix, suffix);
			return evaluated == null ? "" : evaluated.toString();
		}
		catch (RenderingException re) {
			throw re;
		}
		catch (Throwable t) {
			throw new RenderingException("Unable to render results due to: " + t, t);
		}
	}
	
	public static String removeQuotesFromValues(String input) {
		Pattern pattern = Pattern.compile("\"value\":\"(\\d+)\"");
		Matcher matcher = pattern.matcher(input);
		
		StringBuffer result = new StringBuffer();
		while (matcher.find()) {
			matcher.appendReplacement(result, "\"value\":" + matcher.group(1));
		}
		matcher.appendTail(result);
		return result.toString();
	}
	
	private String readDesignResource(ReportDesign reportDesign) {
		try {
			TextTemplateRenderer renderer = new TextTemplateRenderer();
			ReportDesignResource res = renderer.getTemplate(reportDesign);
			return new String(res.getContents(), StandardCharsets.UTF_8).trim();
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to read report design resource", e);
		}
	}
	
	@Override
	public String buildPayloadJson(ReportData reportData, ReportDesign reportDesign, String renderType) {
		if (renderType != null && "legacy".equalsIgnoreCase(renderType)) {
			return createLegacyPayloadJson(reportData, reportDesign);
		}
		
		String templateJson = readDesignResource(reportDesign);
		
		// Detect if this is a linelist report
		if (isLinelistReportTemplate(templateJson)) {
			LinelistHtmlRenderer.Result result = linelistHtmlRenderer.convert(reportData, reportDesign);
			return result.payloadJson;
		}
		
		// Default to aggregate report rendering
		Map<String, Object> values = extractFlatValues(reportData);
		return createPayloadJsonFromTemplate(reportData, reportDesign, "json", values, null);
	}
	
	@Override
	public String buildFinalPayloadJson(ReportData reportData, ReportDesign reportDesign, String renderType, Date endDate) {
		String payloadJson = buildPayloadJson(reportData, reportDesign, renderType);
		String period = getYearAndQuarter(endDate);
		return appendPeriod(payloadJson, period);
	}
	
	@Override
	public String buildPreviewHtml(ReportData reportData, ReportDesign reportDesign) {
		String templateJson = readDesignResource(reportDesign);
		
		// Detect if this is a linelist report
		if (isLinelistReportTemplate(templateJson)) {
			LinelistHtmlRenderer.Result result = linelistHtmlRenderer.convert(reportData, reportDesign);
			return result.html;
		}
		
		// Default to aggregate report rendering
		Map<String, Object> values = extractFlatValues(reportData);
		return reportDesignHtmlRenderer.convert(templateJson, values, null).html;
	}
	
	public String buildRenderedOutput(ReportData reportData, ReportDesign reportDesign, String remapJsonOptional) {
		String templateJson = readDesignResource(reportDesign);
		
		// Detect if this is a linelist report by checking for baseCohortDefinition
		if (isLinelistReportTemplate(templateJson)) {
			LinelistHtmlRenderer.Result result = linelistHtmlRenderer.convert(reportData, reportDesign);
			return result.renderedOutputJson;
		}
		
		// Default to aggregate report rendering
		Map<String, Object> values = extractFlatValues(reportData);
		return reportDesignHtmlRenderer.buildRenderedOutputOnly(templateJson, values, remapJsonOptional);
	}
	
	/**
	 * Detects if the report template is a linelist report by checking for baseCohortDefinition and
	 * dataSetDefinitions keys (linelist schema) versus groups/definition (aggregate schema).
	 */
	private boolean isLinelistReportTemplate(String templateJson) {
		try {
			JsonNode config = objectMapper.readTree(templateJson);
			return config.has("baseCohortDefinition") && config.has("dataSetDefinitions") && !config.has("groups");
		}
		catch (Exception e) {
			return false;
		}
	}
	
	private String getYearAndQuarter(Date date) {
		if (date == null) {
			return null;
		}
		
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		
		int year = calendar.get(Calendar.YEAR);
		int month = calendar.get(Calendar.MONTH) + 1;
		int quarter = ((month - 1) / 3) + 1;
		
		return year + "Q" + quarter;
	}
	
	private String appendPeriod(String payloadJson, String period) {
		if (payloadJson == null) {
			return null;
		}
		try {
			ObjectMapper om = new ObjectMapper();
			ObjectNode root = (ObjectNode) om.readTree(payloadJson);
			
			ObjectNode json = (ObjectNode) root.get("json");
			if (json == null) {
				json = om.createObjectNode();
				root.set("json", json);
			}
			if (period != null) {
				json.put("period", period);
			}
			
			return om.writeValueAsString(root);
		}
		catch (Exception e) {
			return payloadJson;
		}
	}
	
	@Override
	public ReportBuilderIndicator saveReportBuilderIndicator(ReportBuilderIndicator indicator) {
		try {
			IndicatorValidator.validate(indicator);
			
			if (indicator.getKind() == ReportBuilderIndicator.Kind.BASE) {
				IndicatorSqlSync.normalizeBaseSql(indicator);
			}
			
			return dao.saveReportBuilderIndicator(indicator);
		}
		catch (IllegalArgumentException e) {
			throw new APIException(e.getMessage(), e);
		}
	}
	
	@Override
	@Transactional(readOnly = true)
	public ReportBuilderIndicator getReportBuilderIndicatorById(Integer id) {
		return dao.getReportBuilderIndicatorById(id);
	}
	
	@Override
	@Transactional(readOnly = true)
	public ReportBuilderIndicator getReportBuilderIndicatorByUuid(String uuid) {
		return dao.getReportBuilderIndicatorByUuid(uuid);
	}
	
	@Override
	@Transactional(readOnly = true)
	public ReportBuilderIndicator getReportBuilderIndicatorByCode(String code) {
		return dao.getReportBuilderIndicatorByCode(code);
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<ReportBuilderIndicator> searchReportBuilderIndicators(String q, ReportBuilderIndicator.Kind kind,
	        boolean includeRetired, Integer startIndex, Integer limit) {
		return dao.getReportBuilderIndicators(q, kind, includeRetired, startIndex, limit);
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<ReportBuilderIndicator> getAllReportBuilderIndicator(Integer startIndex, Integer limit) {
		return dao.getAllReportBuilderaIndicator(startIndex, limit);
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<ReportBuilderIndicator> getReportBuilderIndicators(ReportBuilderIndicator.Kind kind, boolean includeRetired,
	        Integer startIndex, Integer limit) {
		return dao.getReportBuilderIndicators(kind, includeRetired, startIndex, limit);
	}
	
	@Override
	@Transactional(readOnly = true)
	public long getReportBuilderIndicatorsCount(String q, ReportBuilderIndicator.Kind kind, boolean includeRetired) {
		return dao.getReportBuilderIndicatorsCount(q, kind, includeRetired);
	}
	
	@Override
	public void retireReportBuilderIndicator(ReportBuilderIndicator indicator, String reason) {
		indicator.setRetired(true);
		indicator.setRetireReason(reason);
		dao.saveReportBuilderIndicator(indicator);
	}
	
	@Override
	public void unretireReportBuilderIndicator(ReportBuilderIndicator indicator) {
		indicator.setRetired(false);
		indicator.setRetireReason(null);
		dao.saveReportBuilderIndicator(indicator);
	}
	
	@Override
	public void purgeReportBuilderIndicator(ReportBuilderIndicator indicator) {
		dao.purgeReportBuilderIndicator(indicator);
	}
	
	@Override
	public ReportBuilderSection saveReportBuilderSection(ReportBuilderSection section) {
		return dao.saveReportBuilderSection(section);
	}
	
	@Override
	@Transactional(readOnly = true)
	public ReportBuilderSection getReportBuilderSectionById(Integer id) {
		return dao.getReportBuilderSectionById(id);
	}
	
	@Override
	@Transactional(readOnly = true)
	public ReportBuilderSection getReportBuilderSectionByUuid(String uuid) {
		return dao.getReportBuilderSectionByUuid(uuid);
	}
	
	@Override
	@Transactional(readOnly = true)
	public ReportBuilderSection getReportBuilderSectionByCode(String code) {
		return dao.getReportBuilderSectionByCode(code);
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<ReportBuilderSection> getReportBuilderSections(String q, boolean includeRetired, Integer startIndex,
	        Integer limit) {
		return dao.getReportBuilderSections(q, includeRetired, startIndex, limit);
	}
	
	@Override
	@Transactional(readOnly = true)
	public long getReportBuilderSectionsCount(String q, boolean includeRetired) {
		return dao.getReportBuilderSectionsCount(q, includeRetired);
	}
	
	@Override
	public void retireReportBuilderSection(ReportBuilderSection section, String reason) {
		section.setRetired(true);
		section.setRetireReason(reason);
		dao.saveReportBuilderSection(section);
	}
	
	@Override
	public void unretireReportBuilderSection(ReportBuilderSection section) {
		section.setRetired(false);
		section.setRetireReason(null);
		dao.saveReportBuilderSection(section);
	}
	
	@Override
	public void purgeReportBuilderSection(ReportBuilderSection section) {
		dao.purgeReportBuilderSection(section);
	}
	
	@Override
	public ReportBuilderDataTheme saveReportBuilderDataTheme(ReportBuilderDataTheme theme) {
		return dao.saveReportBuilderDataTheme(theme);
	}
	
	@Override
	@Transactional(readOnly = true)
	public ReportBuilderDataTheme getReportBuilderDataThemeById(Integer id) {
		return dao.getReportBuilderDataThemeById(id);
	}
	
	@Override
	@Transactional(readOnly = true)
	public ReportBuilderDataTheme getReportBuilderDataThemeByUuid(String uuid) {
		return dao.getReportBuilderDataThemeByUuid(uuid);
	}
	
	@Override
	@Transactional(readOnly = true)
	public ReportBuilderDataTheme getReportBuilderDataThemeByCode(String code) {
		return dao.getReportBuilderDataThemeByCode(code);
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<ReportBuilderDataTheme> getReportBuilderDataThemes(String q, boolean includeRetired, Integer startIndex,
	        Integer limit) {
		return dao.getReportBuilderDataThemes(q, includeRetired, startIndex, limit);
	}
	
	@Override
	@Transactional(readOnly = true)
	public long getReportBuilderDataThemesCount(String q, boolean includeRetired) {
		return dao.getReportBuilderThemesCount(q, includeRetired);
	}
	
	@Override
	public void retireReportBuilderDataTheme(ReportBuilderDataTheme theme, String reason) {
		theme.setRetired(true);
		theme.setRetireReason(reason);
		dao.saveReportBuilderDataTheme(theme);
	}
	
	@Override
	public void unretireReportBuilderDataTheme(ReportBuilderDataTheme theme) {
		theme.setRetired(false);
		theme.setRetireReason(null);
		dao.saveReportBuilderDataTheme(theme);
	}
	
	@Override
	public void purgeReportBuilderDataTheme(ReportBuilderDataTheme theme) {
		dao.purgeReportBuilderDataTheme(theme);
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<String> getETLTables() {
		return dao.getETLTables(getAllowedTablePrefixes());
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<Map> getETLTableColumns(String tableName) {
		return dao.getETLTableColumns(tableName);
	}
	
	@Override
	public ReportBuilderAgeCategory saveAgeCategory(ReportBuilderAgeCategory category) {
		return dao.saveAgeCategory(category);
	}
	
	@Override
	@Transactional(readOnly = true)
	public ReportBuilderAgeCategory getAgeCategoryByUuid(String uuid) {
		return dao.getAgeCategoryByUuid(uuid);
	}
	
	@Override
	@Transactional(readOnly = true)
	public ReportBuilderAgeCategory getAgeCategoryByCode(String code) {
		return dao.getAgeCategoryByCode(code);
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<ReportBuilderAgeCategory> getAgeCategories(String q, boolean includeRetired, Boolean activeOnly,
	        Integer startIndex, Integer limit) {
		return dao.getAgeCategories(q, includeRetired, activeOnly, startIndex, limit);
	}
	
	@Override
	@Transactional(readOnly = true)
	public long getAgeCategoriesCount(String q, boolean includeRetired, Boolean activeOnly) {
		return dao.getAgeCategoriesCount(q, includeRetired, activeOnly);
	}
	
	@Override
	public void retireAgeCategory(ReportBuilderAgeCategory category, String reason) {
		category.setRetired(true);
		category.setRetireReason(reason);
		dao.saveAgeCategory(category);
	}
	
	@Override
	public void unretireAgeCategory(ReportBuilderAgeCategory category) {
		category.setRetired(false);
		category.setRetireReason(null);
		dao.saveAgeCategory(category);
	}
	
	@Override
	public void purgeAgeCategory(ReportBuilderAgeCategory category) {
		
	}
	
	@Override
	public void purgeAgeGroup(ReportBuilderAgeGroup group) {
		dao.purgeAgeGroup(group);
	}
	
	@Override
	public ReportBuilderAgeGroup saveAgeGroup(ReportBuilderAgeGroup group) {
		return dao.saveAgeGroup(group);
	}
	
	@Override
	@Transactional(readOnly = true)
	public ReportBuilderAgeGroup getAgeGroupById(Integer id) {
		return dao.getAgeGroupById(id);
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<ReportBuilderAgeGroup> getAgeGroupsByCategoryUuid(String categoryUuid, Boolean activeOnly) {
		return dao.getAgeGroupsByCategoryUuid(categoryUuid, activeOnly);
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<ReportBuilderAgeGroup> getAgeGroupsByCategoryCode(String categoryCode, Boolean activeOnly) {
		return dao.getAgeGroupsByCategoryCode(categoryCode, activeOnly);
	}
	
	@Override
	public List<ReportBuilderAgeGroup> getAgeGroups(String q, ReportBuilderAgeCategory category, Boolean activeOnly,
	        Integer startIndex, Integer limit) {
		return dao.getAgeGroups(q, category, activeOnly, startIndex, limit);
	}
	
	@Override
	public SqlPreviewResult previewSql(String sql, Map<String, Object> params, Integer maxRows) {
		return dao.previewSql(sql, params, maxRows);
	}
	
	@Override
	public ReportBuilderReport saveReportBuilderReport(ReportBuilderReport report) {
		if (report.getUuid() == null) {
			report.setUuid(UUID.randomUUID().toString());
		}
		ReportBuilderReport savedReport = dao.saveReportBuilderReport(report);
		
		// Automatically add to report library
		addToReportLibrary(savedReport);
		
		return savedReport;
	}
	
	@Override
	public ReportBuilderReport getReportBuilderReportByUuid(String uuid) {
		return dao.getReportBuilderReportByUuid(uuid);
	}
	
	@Override
	public List<ReportBuilderReport> getReportBuilderReports(String q, boolean includeRetired, Integer startIndex,
	        Integer limit) {
		return dao.getReportBuilderReports(q, includeRetired, startIndex, limit);
	}
	
	@Override
	public void retireReportBuilderReport(ReportBuilderReport report, String reason) {
		dao.retireReportBuilderReport(report, reason);
	}
	
	@Override
	public void purgeReportBuilderReport(ReportBuilderReport report) {
		dao.purgeReportBuilderReport(report);
	}
	
	@Override
	public CompiledReportArtifacts compileReport(String reportBuilderReportUuid) {
		ReportDefinitionService reportDefinitionService = Context.getService(ReportDefinitionService.class);
		
		ReportBuilderReport report = getReportBuilderReportByUuid(reportBuilderReportUuid);
		if (report == null) {
			throw new IllegalArgumentException("ReportBuilderReport not found: " + reportBuilderReportUuid);
		}
		
		report.setCompileStatus(ReportBuilderReport.ReportCompileStatus.COMPILING);
		saveReportBuilderReport(report);
		
		try {
			JsonNode reportConfig = parseJson(report.getConfigJson(), "Invalid ReportBuilderReport configJson");
			
			// Linelist (patient-level) reports have a different configJson shape and use a
			// LineListDataSetDefinition; compile them on a dedicated path and leave the aggregate
			// logic below untouched.
			if (isLinelistReport(report, reportConfig)) {
				return compileLinelistReport(report, reportConfig, reportDefinitionService);
			}
			
			JsonNode definitionNode = reportConfig.path("definition");
			JsonNode designNode = reportConfig.path("design");
			
			JsonNode sections = definitionNode.path("sections");
			if (!sections.isArray()) {
				sections = reportConfig.path("sections");
			}
			
			ArrayNode compiledFields = objectMapper.createArrayNode();
			ArrayNode compiledDhis2Rows = objectMapper.createArrayNode();
			
			if (sections.isArray()) {
				List<JsonNode> sectionRefs = new ArrayList<JsonNode>();
				Iterator<JsonNode> sectionIterator = sections.elements();
				while (sectionIterator.hasNext()) {
					JsonNode s = sectionIterator.next();
					if (s.path("enabled").asBoolean(true)) {
						sectionRefs.add(s);
					}
				}
				
				Collections.sort(sectionRefs, new Comparator<JsonNode>() {
					
					@Override
					public int compare(JsonNode a, JsonNode b) {
						Integer s1 = Integer.valueOf(a.path("sortOrder").asInt(9999));
						Integer s2 = Integer.valueOf(b.path("sortOrder").asInt(9999));
						return s1.compareTo(s2);
					}
				});
				
				int i;
				for (i = 0; i < sectionRefs.size(); i++) {
					JsonNode sectionRef = sectionRefs.get(i);
					String sectionUuid = sectionRef.path("sectionUuid").asText(null);
					if (sectionUuid == null || sectionUuid.trim().isEmpty()) {
						continue;
					}
					
					ReportBuilderSection section = getReportBuilderSectionByUuid(sectionUuid);
					if (section == null) {
						continue;
					}
					
					JsonNode sectionConfig = parseJson(section.getConfigJson(), "Invalid section configJson for "
					        + sectionUuid);
					
					String sectionName = sectionRef.path("titleOverride").asText(null);
					if (sectionName == null || sectionName.trim().isEmpty()) {
						sectionName = section.getName();
					}
					
					ArrayNode sectionFields = compileSectionToReportFields(sectionName, sectionConfig);
					Iterator<JsonNode> fieldIterator = sectionFields.elements();
					while (fieldIterator.hasNext()) {
						compiledFields.add(fieldIterator.next());
					}
					
					appendSectionDhis2Mappings(compiledDhis2Rows, sectionConfig);
				}
			}
			
			ObjectNode compiledDefinitionRoot = objectMapper.createObjectNode();
			compiledDefinitionRoot.put("version", 1);
			compiledDefinitionRoot.put("name", report.getName());
			compiledDefinitionRoot.put("code", report.getCode());
			compiledDefinitionRoot.set("report_fields", compiledFields);
			
			String compiledDefinitionJson;
			try {
				compiledDefinitionJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(
				    compiledDefinitionRoot);
			}
			catch (Exception e) {
				throw new RuntimeException("Failed to serialize compiled report definition JSON", e);
			}
			
			String definitionFileName = buildDefinitionFileName(report);
			File definitionFile;
			try {
				definitionFile = ReportDesignFileUtil
				        .writeJsonStringToDesignFile(definitionFileName, compiledDefinitionJson);
			}
			catch (Exception e) {
				throw new RuntimeException("Failed to write compiled report definition file", e);
			}
			
			ArrayNode compiledDesignGroups;
			JsonNode authoredGroups = designNode.path("groups");
			if (authoredGroups.isArray() && authoredGroups.size() > 0) {
				compiledDesignGroups = compileAuthoredDesignGroups(authoredGroups, sections);
			} else {
				compiledDesignGroups = compileGeneratedDesignGroupsFromSections(sections, designNode);
			}
			
			ObjectNode compiledDesignRoot = objectMapper.createObjectNode();
			compiledDesignRoot.put("version", 1);
			compiledDesignRoot.put("name", report.getName());
			compiledDesignRoot.put("code", report.getCode());
			compiledDesignRoot.put("template", designNode.path("template").asText("section-tabular"));
			compiledDesignRoot.put("arrayName", designNode.path("arrayName").asText("results"));
			compiledDesignRoot.put("defaultValue", designNode.path("defaultValue").asInt(0));
			compiledDesignRoot.set("groups", compiledDesignGroups);
			compiledDesignRoot.set("dimensions", compileDimensionsFromDesignAndSections(designNode, sections));
			
			ObjectNode compiledDhis2 = objectMapper.createObjectNode();
			compiledDhis2.put("enabled", compiledDhis2Rows.size() > 0);
			compiledDhis2.set("rows", compiledDhis2Rows);
			compiledDesignRoot.set("dhis2", compiledDhis2);
			
			String compiledDesignJson;
			try {
				compiledDesignJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(compiledDesignRoot);
			}
			catch (Exception e) {
				throw new RuntimeException("Failed to serialize compiled report design JSON", e);
			}
			
			ReportDefinition reportDefinition = findOrCreateReportDefinition(report, reportDefinitionService);
			
			AggregateReportDataSetDefinition dsd = new AggregateReportDataSetDefinition();
			dsd.setName(report.getName() + " Data Set");
			dsd.setDescription(report.getDescription());
			// Store only the relative filename instead of absolute path for portability
			dsd.setReportDesignPath(definitionFileName);
			dsd.addParameter(new Parameter("startDate", "Start Date", Date.class));
			dsd.addParameter(new Parameter("endDate", "End Date", Date.class));
			
			reportDefinition.setName(report.getName());
			reportDefinition.setDescription(report.getDescription());
			reportDefinition.getParameters().clear();
			reportDefinition.addParameter(new Parameter("startDate", "Start Date", Date.class));
			reportDefinition.addParameter(new Parameter("endDate", "End Date", Date.class));
			reportDefinition.getDataSetDefinitions().clear();
			
			Map<String, Object> parameterMappings = new HashMap<String, Object>();
			parameterMappings.put("startDate", "${startDate}");
			parameterMappings.put("endDate", "${endDate}");
			
			reportDefinition.addDataSetDefinition("defaultDataSet", dsd, parameterMappings);
			reportDefinition = reportDefinitionService.saveDefinition(reportDefinition);
			
			ReportDesign jsonDesign = saveOrUpdateJsonReportDesign(reportDefinition, compiledDesignJson, report);
			
			report.setCompiledReportDefinitionUuid(reportDefinition.getUuid());
			report.setCompiledReportDesignUuid(jsonDesign != null ? jsonDesign.getUuid() : null);
			report.setLastCompiledAt(new Date());
			report.setCompileStatus(ReportBuilderReport.ReportCompileStatus.COMPILED);
			report = saveReportBuilderReport(report);
			
			CompiledReportArtifacts out = new CompiledReportArtifacts();
			out.setReportBuilderReport(report);
			out.setReportDefinition(reportDefinition);
			out.setReportDesignFile(definitionFile);
			out.setCompiledJson(compiledDefinitionJson);
			return out;
		}
		catch (Exception e) {
			report.setLastCompiledAt(new Date());
			report.setCompileStatus(ReportBuilderReport.ReportCompileStatus.FAILED);
			saveReportBuilderReport(report);
			throw e;
		}
	}
	
	/**
	 * Detects whether a report config is a linelist (patient-level) report rather than an
	 * aggregate/indicator report.
	 */
	private boolean isLinelistReport(ReportBuilderReport report, JsonNode config) {
		if (report.getReportType() == ReportBuilderReport.ReportType.LINE_LIST) {
			return true;
		}
		String reportType = config.path("reportType").asText("");
		String type = config.path("type").asText("");
		if ("LINELIST".equalsIgnoreCase(reportType) || "LINE_LIST".equalsIgnoreCase(type)) {
			return true;
		}
		return config.has("baseCohortDefinition") && config.has("dataSetDefinitions") && !config.has("sections")
		        && !config.has("definition");
	}
	
	/**
	 * Compiles a linelist (patient-level) report. The saved configJson is the v2 builder format
	 * carrying build-only detail that the runtime evaluator does not understand.
	 * {@link LinelistConfigCompiler} converts it into a clean LegacyGenericReportSchema design file
	 * (stripping all build-only keys), which is then wrapped in a LineListDataSetDefinition so the
	 * LineListDataSetEvaluator can read it at runtime.
	 */
	private CompiledReportArtifacts compileLinelistReport(ReportBuilderReport report, JsonNode reportConfig,
	        ReportDefinitionService reportDefinitionService) {
		
		ObjectNode compiledConfig;
		String compiledJson;
		try {
			compiledConfig = LinelistConfigCompiler.compile(reportConfig, report.getName(), report.getDescription());
			compiledJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(compiledConfig);
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to compile linelist report config", e);
		}
		
		String definitionFileName = buildLinelistDefinitionFileName(report);
		File definitionFile;
		try {
			definitionFile = ReportDesignFileUtil.writeJsonStringToDesignFile(definitionFileName, compiledJson);
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to write linelist report design file", e);
		}
		
		ReportDefinition reportDefinition = findOrCreateReportDefinition(report, reportDefinitionService);
		
		LineListDataSetDefinition dsd = new LineListDataSetDefinition();
		dsd.setName(report.getName() + " Data Set");
		dsd.setDescription(report.getDescription());
		// Store only the relative filename instead of absolute path for portability
		dsd.setReportDesignPath(definitionFileName);
		
		reportDefinition.setName(report.getName());
		reportDefinition.setDescription(report.getDescription());
		reportDefinition.getParameters().clear();
		reportDefinition.getDataSetDefinitions().clear();
		
		// Declare parameters from the compiled config (type-mapped). All parameters are driven
		// by the JSON config - no hardcoded defaults since not all reports require date ranges.
		List<Parameter> declaredParameters = new ArrayList<Parameter>();
		JsonNode parameters = compiledConfig.path("parameters");
		if (parameters.isArray() && parameters.size() > 0) {
			Iterator<JsonNode> it = parameters.elements();
			while (it.hasNext()) {
				JsonNode param = it.next();
				String paramName = param.path("name").asText("");
				if (paramName.trim().isEmpty()) {
					continue;
				}
				declareLinelistParameter(declaredParameters, reportDefinition, paramName,
				    param.path("label").asText(paramName), mapParameterTypeToClass(param.path("type").asText("DATE")));
			}
		}
		
		for (Parameter p : declaredParameters) {
			dsd.addParameter(p);
		}
		
		Map<String, Object> parameterMappings = new HashMap<String, Object>();
		for (Parameter p : declaredParameters) {
			parameterMappings.put(p.getName(), "${" + p.getName() + "}");
		}
		
		reportDefinition.addDataSetDefinition("linelistDataSet", dsd, parameterMappings);
		reportDefinition = reportDefinitionService.saveDefinition(reportDefinition);
		
		ReportDesign jsonDesign = saveOrUpdateJsonReportDesign(reportDefinition, compiledJson, report);
		
		report.setCompiledReportDefinitionUuid(reportDefinition.getUuid());
		report.setCompiledReportDesignUuid(jsonDesign != null ? jsonDesign.getUuid() : null);
		report.setLastCompiledAt(new Date());
		report.setCompileStatus(ReportBuilderReport.ReportCompileStatus.COMPILED);
		report = saveReportBuilderReport(report);
		
		CompiledReportArtifacts out = new CompiledReportArtifacts();
		out.setReportBuilderReport(report);
		out.setReportDefinition(reportDefinition);
		out.setReportDesignFile(definitionFile);
		out.setCompiledJson(compiledJson);
		return out;
	}
	
	/**
	 * Declares a parameter on the report definition unless one with the same name is already
	 * present, tracking declared parameters so data-set parameters and mappings can be derived from
	 * them.
	 */
	private void declareLinelistParameter(List<Parameter> declared, ReportDefinition reportDefinition, String name,
	        String label, Class<?> clazz) {
		for (Parameter existing : declared) {
			if (name.equalsIgnoreCase(existing.getName())) {
				return;
			}
		}
		Parameter p = new Parameter(name, label, clazz);
		reportDefinition.addParameter(p);
		declared.add(p);
	}
	
	/**
	 * Maps a frontend parameter type string to the OpenMRS Parameter class.
	 */
	private Class<?> mapParameterTypeToClass(String type) {
		if (type == null) {
			return Date.class;
		}
		switch (type.toUpperCase()) {
			case "DATE":
			case "DATETIME":
				return Date.class;
			case "LOCATION":
				return Location.class;
			case "NUMBER":
				return Double.class;
			case "BOOLEAN":
				return Boolean.class;
			default:
				return String.class;
		}
	}
	
	/**
	 * Builds a file name for a linelist report design file. Stored under report_designs/linelist/
	 * to avoid clashing with aggregate designs in the same directory.
	 */
	private String buildLinelistDefinitionFileName(ReportBuilderReport report) {
		String base = report.getCode();
		if (base == null || base.trim().isEmpty()) {
			base = report.getUuid();
		}
		base = sanitize(base);
		if (base == null || base.trim().isEmpty()) {
			base = "report_" + System.currentTimeMillis();
		}
		return "linelist/" + base + ".json";
	}
	
	private ArrayNode compileAuthoredDesignGroups(JsonNode authoredGroups, JsonNode sections) {
		ArrayNode out = objectMapper.createArrayNode();
		Map<String, ObjectNode> sectionMeta = buildSectionDimensionMetadata(sections);
		
		Iterator<JsonNode> groupIterator = authoredGroups.elements();
		while (groupIterator.hasNext()) {
			JsonNode groupNode = groupIterator.next();
			ObjectNode group = objectMapper.createObjectNode();
			group.put("title", groupNode.path("title").asText(""));
			
			String groupId = groupNode.path("id").asText(null);
			ObjectNode meta = groupId != null ? sectionMeta.get(groupId) : null;
			
			boolean disaggregated = meta != null && meta.path("disaggregated").asBoolean(false);
			String ageCategoryCode = meta != null ? meta.path("ageCategoryCode").asText("") : "";
			
			ArrayNode rowsOut = objectMapper.createArrayNode();
			JsonNode rows = groupNode.path("rows");
			
			if (rows.isArray()) {
				Iterator<JsonNode> rowIterator = rows.elements();
				while (rowIterator.hasNext()) {
					JsonNode rowNode = rowIterator.next();
					ObjectNode row = objectMapper.createObjectNode();
					
					String type = rowNode.path("type").asText("indicator");
					row.put("type", type);
					row.put("label", rowNode.path("label").asText(""));
					row.put("indent", rowNode.path("indent").asInt(0));
					
					if (rowNode.hasNonNull("code")) {
						row.put("code", rowNode.path("code").asText(""));
					}
					if (rowNode.hasNonNull("indicatorUuid")) {
						row.put("indicatorUuid", rowNode.path("indicatorUuid").asText(""));
					}
					if (rowNode.hasNonNull("span")) {
						row.put("span", rowNode.path("span").asText(""));
					}
					if (rowNode.hasNonNull("emphasis")) {
						row.put("emphasis", rowNode.path("emphasis").asText(""));
					}
					
					if ("indicator".equals(type)) {
						row.put("showTotal", rowNode.path("showTotal").asBoolean(true));
						row.put("showDisaggregation", rowNode.path("showDisaggregation").asBoolean(disaggregated));
						
						if (rowNode.hasNonNull("keyPattern")) {
							row.put("keyPattern", rowNode.path("keyPattern").asText(""));
						} else {
							row.put("keyPattern", disaggregated ? "{code}_{age}_{sex}" : "{code}_TOTAL");
						}
						
						ObjectNode dims = objectMapper.createObjectNode();
						JsonNode authoredDims = rowNode.path("dims");
						if (authoredDims.isObject()) {
							dims.setAll((ObjectNode) authoredDims);
						}
						
						if (disaggregated) {
							if (!dims.has("age") && ageCategoryCode != null && !ageCategoryCode.trim().isEmpty()) {
								dims.put("age", ageCategoryCode);
							}
							if (!dims.has("sex")) {
								dims.put("sex", "sex");
							}
						}
						
						row.set("dims", dims);
					} else if ("section-label".equals(type)) {
						row.put("showTotal", false);
						row.put("showDisaggregation", false);
						if (!row.has("span")) {
							row.put("span", "all");
						}
						if (!row.has("emphasis")) {
							row.put("emphasis", "section");
						}
					} else if ("group-label".equals(type)) {
						row.put("showTotal", false);
						row.put("showDisaggregation", false);
						if (!row.has("span")) {
							row.put("span", "label-only");
						}
						if (!row.has("emphasis")) {
							row.put("emphasis", "group");
						}
					} else {
						row.put("showTotal", false);
						row.put("showDisaggregation", false);
					}
					
					rowsOut.add(row);
				}
			}
			
			group.set("rows", rowsOut);
			out.add(group);
		}
		
		return out;
	}
	
	private ArrayNode compileGeneratedDesignGroupsFromSections(JsonNode sections, JsonNode designNode) {
		ArrayNode out = objectMapper.createArrayNode();
		if (!sections.isArray()) {
			return out;
		}
		
		List<JsonNode> sectionRefs = new ArrayList<JsonNode>();
		Iterator<JsonNode> sectionIterator = sections.elements();
		while (sectionIterator.hasNext()) {
			JsonNode s = sectionIterator.next();
			if (s.path("enabled").asBoolean(true)) {
				sectionRefs.add(s);
			}
		}
		
		Collections.sort(sectionRefs, new Comparator<JsonNode>() {
			
			@Override
			public int compare(JsonNode a, JsonNode b) {
				Integer s1 = Integer.valueOf(a.path("sortOrder").asInt(9999));
				Integer s2 = Integer.valueOf(b.path("sortOrder").asInt(9999));
				return s1.compareTo(s2);
			}
		});
		
		int i;
		for (i = 0; i < sectionRefs.size(); i++) {
			JsonNode sectionRef = sectionRefs.get(i);
			String sectionUuid = sectionRef.path("sectionUuid").asText(null);
			if (sectionUuid == null || sectionUuid.trim().isEmpty()) {
				continue;
			}
			
			ReportBuilderSection section = getReportBuilderSectionByUuid(sectionUuid);
			if (section == null) {
				continue;
			}
			
			JsonNode sectionConfig = parseJson(section.getConfigJson(), "Invalid section configJson for " + sectionUuid);
			
			String sectionName = sectionRef.path("titleOverride").asText(null);
			if (sectionName == null || sectionName.trim().isEmpty()) {
				sectionName = section.getName();
			}
			
			ObjectNode group = compileSectionToDesignGroup(sectionName, sectionConfig, designNode);
			if (group != null) {
				out.add(group);
			}
		}
		
		return out;
	}
	
	private ObjectNode compileDimensionsFromDesignAndSections(JsonNode designNode, JsonNode sections) {
		ObjectNode dimensions = objectMapper.createObjectNode();
		
		ArrayNode sex = objectMapper.createArrayNode();
		ObjectNode female = objectMapper.createObjectNode();
		female.put("id", "F");
		female.put("label", "Female");
		sex.add(female);
		
		ObjectNode male = objectMapper.createObjectNode();
		male.put("id", "M");
		male.put("label", "Male");
		sex.add(male);
		
		dimensions.set("sex", sex);
		
		JsonNode authoredDimensions = designNode.path("dimensions");
		if (authoredDimensions.isObject()) {
			Iterator<String> names = authoredDimensions.fieldNames();
			while (names.hasNext()) {
				String name = names.next();
				dimensions.set(name, authoredDimensions.get(name));
			}
		}
		
		if (sections.isArray()) {
			Iterator<JsonNode> sectionIterator = sections.elements();
			while (sectionIterator.hasNext()) {
				JsonNode sectionRef = sectionIterator.next();
				String sectionUuid = sectionRef.path("sectionUuid").asText(null);
				if (sectionUuid == null || sectionUuid.trim().isEmpty()) {
					continue;
				}
				
				ReportBuilderSection section = getReportBuilderSectionByUuid(sectionUuid);
				if (section == null) {
					continue;
				}
				
				JsonNode sectionConfig = parseJson(section.getConfigJson(), "Invalid section configJson for " + sectionUuid);
				String ageCategoryCode = sectionConfig.path("disaggregation").path("ageCategoryCode").asText(null);
				
				if (ageCategoryCode != null && !ageCategoryCode.trim().isEmpty() && !dimensions.has(ageCategoryCode)) {
					ArrayNode ageOptions = compileAgeDimension(ageCategoryCode);
					if (ageOptions.size() > 0) {
						dimensions.set(ageCategoryCode, ageOptions);
					}
				}
			}
		}
		
		return dimensions;
	}
	
	private ArrayNode compileAgeDimension(String ageCategoryCode) {
		ArrayNode out = objectMapper.createArrayNode();
		
		ReportBuilderAgeCategory category = getAgeCategoryByCode(ageCategoryCode);
		if (category == null || category.getAgeGroups() == null) {
			return out;
		}
		
		List<ReportBuilderAgeGroup> groups = new ArrayList<ReportBuilderAgeGroup>(category.getAgeGroups());
		Collections.sort(groups, new Comparator<ReportBuilderAgeGroup>() {
			
			@Override
			public int compare(ReportBuilderAgeGroup g1, ReportBuilderAgeGroup g2) {
				Integer s1 = g1.getSortOrder();
				Integer s2 = g2.getSortOrder();
				
				if (s1 == null) {
					s1 = Integer.valueOf(Integer.MAX_VALUE);
				}
				if (s2 == null) {
					s2 = Integer.valueOf(Integer.MAX_VALUE);
				}
				
				return s1.compareTo(s2);
			}
		});
		
		int i;
		for (i = 0; i < groups.size(); i++) {
			ReportBuilderAgeGroup g = groups.get(i);
			if (g == null || !Boolean.TRUE.equals(g.getActive()) || g.getLabel() == null || g.getLabel().trim().isEmpty()) {
				continue;
			}
			
			ObjectNode one = objectMapper.createObjectNode();
			one.put("id", sanitize(g.getLabel()));
			one.put("label", g.getLabel());
			out.add(one);
		}
		
		return out;
	}
	
	private ObjectNode compileSectionToDesignGroup(String sectionName, JsonNode sectionConfig, JsonNode reportDesignNode) {
		ObjectNode group = objectMapper.createObjectNode();
		group.put("title", sectionName);
		
		ArrayNode rows = objectMapper.createArrayNode();
		
		ObjectNode sectionRow = objectMapper.createObjectNode();
		sectionRow.put("type", "section-label");
		sectionRow.put("label", sectionName);
		sectionRow.put("indent", 0);
		sectionRow.put("span", "all");
		sectionRow.put("emphasis", "section");
		sectionRow.put("showTotal", false);
		sectionRow.put("showDisaggregation", false);
		rows.add(sectionRow);
		
		JsonNode indicators = sectionConfig.path("indicators");
		if (indicators.isArray()) {
			List<JsonNode> sorted = new ArrayList<JsonNode>();
			Iterator<JsonNode> indicatorIterator = indicators.elements();
			while (indicatorIterator.hasNext()) {
				sorted.add(indicatorIterator.next());
			}
			
			Collections.sort(sorted, new Comparator<JsonNode>() {
				
				@Override
				public int compare(JsonNode a, JsonNode b) {
					Integer s1 = Integer.valueOf(a.path("sortOrder").asInt(9999));
					Integer s2 = Integer.valueOf(b.path("sortOrder").asInt(9999));
					return s1.compareTo(s2);
				}
			});
			
			int i;
			for (i = 0; i < sorted.size(); i++) {
				JsonNode indicator = sorted.get(i);
				ObjectNode row = objectMapper.createObjectNode();
				row.put("type", "indicator");
				row.put("indicatorUuid", indicator.path("indicatorUuid").asText(""));
				row.put("code", indicator.path("code").asText(""));
				row.put("label", indicator.path("name").asText(""));
				row.put("indent", 1);
				row.put("keyPattern", buildIndicatorKeyPattern(indicator, sectionConfig));
				row.put("showTotal", true);
				row.put("showDisaggregation", looksDisaggregated(indicator, sectionConfig));
				row.put("span", "label-only");
				row.put("emphasis", "normal");
				
				ObjectNode dims = objectMapper.createObjectNode();
				if (looksDisaggregated(indicator, sectionConfig)) {
					dims.put("age", sectionConfig.path("disaggregation").path("ageCategoryCode").asText(""));
					dims.put("sex", "sex");
				}
				row.set("dims", dims);
				
				rows.add(row);
			}
		}
		
		group.set("rows", rows);
		return group;
	}
	
	private String buildIndicatorKeyPattern(JsonNode indicator, JsonNode sectionConfig) {
		if (looksDisaggregated(indicator, sectionConfig)) {
			return "{code}_{age}_{sex}";
		}
		return "{code}_TOTAL";
	}
	
	private void appendSectionDhis2Mappings(ArrayNode targetRows, JsonNode sectionConfig) {
		JsonNode dhis2 = sectionConfig.path("exchangeMappings").path("dhis2");
		if (!dhis2.isObject() || !dhis2.path("enabled").asBoolean(false)) {
			return;
		}
		
		JsonNode mappings = dhis2.path("indicatorMappings");
		if (!mappings.isArray()) {
			return;
		}
		
		Iterator<JsonNode> mappingIterator = mappings.elements();
		while (mappingIterator.hasNext()) {
			JsonNode m = mappingIterator.next();
			ObjectNode row = objectMapper.createObjectNode();
			row.put("indicatorUuid", m.path("indicatorUuid").asText(""));
			row.put("indicatorCode", m.path("indicatorCode").asText(""));
			row.put("dataElementId", m.path("dataElementId").asText(""));
			
			JsonNode coc = m.path("categoryOptionComboByDisagg");
			if (coc.isObject()) {
				row.set("categoryOptionComboByDisagg", coc);
			} else {
				row.set("categoryOptionComboByDisagg", objectMapper.createObjectNode());
			}
			
			targetRows.add(row);
		}
	}
	
	private ReportDesign saveOrUpdateJsonReportDesign(ReportDefinition reportDefinition, String compiledDesignJson,
	        ReportBuilderReport report) {
		ReportService reportService = Context.getService(ReportService.class);
		
		ReportDesign design = null;
		
		String existingDesignUuid = report.getCompiledReportDesignUuid();
		if (existingDesignUuid != null && !existingDesignUuid.trim().isEmpty()) {
			design = reportService.getReportDesignByUuid(existingDesignUuid);
		}
		
		if (design == null) {
			List<ReportDesign> existing = reportService.getReportDesigns(reportDefinition, null, false);
			if (existing != null) {
				int i;
				for (i = 0; i < existing.size(); i++) {
					ReportDesign d = existing.get(i);
					if ("JSON".equalsIgnoreCase(d.getName())) {
						design = d;
						break;
					}
				}
			}
		}
		
		if (design == null) {
			design = new ReportDesign();
			design.setUuid(UUID.randomUUID().toString());
			design.setName("JSON");
			design.setReportDefinition(reportDefinition);
			design.setRendererType(TextTemplateRenderer.class);
		} else {
			design.setName("JSON");
			design.setReportDefinition(reportDefinition);
			design.setRendererType(TextTemplateRenderer.class);
			if (design.getResources() != null) {
				design.getResources().clear();
			}
		}
		
		ReportDesignResource resource = new ReportDesignResource();
		resource.setName("template");
		resource.setExtension("json");
		resource.setContentType("application/json");
		resource.setContents(compiledDesignJson.getBytes(StandardCharsets.UTF_8));
		resource.setReportDesign(design);
		
		design.addResource(resource);
		
		return reportService.saveReportDesign(design);
	}
	
	private ArrayNode compileSectionToReportFields(String sectionName, JsonNode sectionConfig) {
		ArrayNode out = objectMapper.createArrayNode();
		JsonNode indicators = sectionConfig.path("indicators");
		
		if (!indicators.isArray()) {
			return out;
		}
		
		List<JsonNode> sorted = new ArrayList<JsonNode>();
		Iterator<JsonNode> indicatorIterator = indicators.elements();
		while (indicatorIterator.hasNext()) {
			sorted.add(indicatorIterator.next());
		}
		
		Collections.sort(sorted, new Comparator<JsonNode>() {
			
			@Override
			public int compare(JsonNode a, JsonNode b) {
				Integer s1 = Integer.valueOf(a.path("sortOrder").asInt(9999));
				Integer s2 = Integer.valueOf(b.path("sortOrder").asInt(9999));
				return s1.compareTo(s2);
			}
		});
		
		int i;
		for (i = 0; i < sorted.size(); i++) {
			JsonNode indicator = sorted.get(i);
			String sql = indicator.path("sql").path("compiled").asText(null);
			if (sql == null || sql.trim().isEmpty()) {
				continue;
			}
			
			ObjectNode field = objectMapper.createObjectNode();
			field.put("indicator_name", indicator.path("code").asText(""));
			field.put("indicator_label", indicator.path("name").asText(""));
			field.put("subsection", sectionName);
			field.put("sqlQuery", decodeHtml(sql));
			
			boolean isDisaggregated = looksDisaggregated(indicator, sectionConfig);
			
			if (isDisaggregated) {
				ArrayNode dissaggregations = objectMapper.createArrayNode();
				dissaggregations.add("age_group");
				dissaggregations.add("gender");
				field.set("dissaggregations", dissaggregations);
				
				ArrayNode values = buildDisaggregatedValues(indicator, sectionConfig);
				if (values.size() > 0) {
					field.set("values", values);
				} else {
					field.put("value_place_holder", buildSinglePlaceholder(indicator));
				}
			} else {
				field.put("value_place_holder", buildSinglePlaceholder(indicator));
			}
			
			out.add(field);
		}
		
		return out;
	}
	
	private boolean looksDisaggregated(JsonNode indicator, JsonNode sectionConfig) {
		JsonNode strategy = indicator.path("sql").path("strategy");
		if (strategy.isTextual() && strategy.asText("").contains("DISAGG")) {
			return true;
		}
		
		JsonNode dis = sectionConfig.path("disaggregation");
		return dis.isObject() && !dis.path("none").asBoolean(false);
	}
	
	private ArrayNode buildDisaggregatedValues(JsonNode indicator, JsonNode sectionConfig) {
		ArrayNode out = objectMapper.createArrayNode();
		
		String indicatorCode = indicator.path("code").asText("IND");
		JsonNode dis = sectionConfig.path("disaggregation");
		JsonNode genders = dis.path("genders");
		
		String ageCategoryCode = dis.path("ageCategoryCode").asText(null);
		List<String> ageLabels = resolveAgeGroupLabels(ageCategoryCode);
		
		if (!ageLabels.isEmpty() && genders.isArray()) {
			int i;
			for (i = 0; i < ageLabels.size(); i++) {
				String ageLabel = ageLabels.get(i);
				Iterator<JsonNode> genderIterator = genders.elements();
				while (genderIterator.hasNext()) {
					JsonNode g = genderIterator.next();
					String gender = g.asText("");
					
					ObjectNode one = objectMapper.createObjectNode();
					one.put("dissaggregations1", ageLabel);
					one.put("dissaggregations2", gender);
					one.put("value_place_holder", buildDisaggregatedPlaceholder(indicatorCode, ageLabel, gender));
					out.add(one);
				}
			}
		}
		
		return out;
	}
	
	private String buildSinglePlaceholder(JsonNode indicator) {
		String code = indicator.path("code").asText("IND");
		return sanitize(code) + "_TOTAL";
	}
	
	private String sanitize(String s) {
		return (s == null ? "" : s.trim()).replace("+", "plus").replace("<", "lt").replace(">", "gt")
		        .replaceAll("[^A-Za-z0-9]+", "_").replaceAll("_+", "_").replaceAll("^_", "").replaceAll("_$", "");
	}
	
	private String buildDefinitionFileName(ReportBuilderReport report) {
		String base = report.getCode();
		if (base == null || base.trim().isEmpty()) {
			base = report.getUuid();
		}
		base = sanitize(base);
		if (base == null || base.trim().isEmpty()) {
			base = "report_" + System.currentTimeMillis();
		}
		return base + ".json";
	}
	
	private ReportDefinition findOrCreateReportDefinition(ReportBuilderReport report,
	        ReportDefinitionService reportDefinitionService) {
		String existingUuid = report.getCompiledReportDefinitionUuid();
		
		if (existingUuid != null && !existingUuid.trim().isEmpty()) {
			ReportDefinition existing = reportDefinitionService.getDefinitionByUuid(existingUuid);
			if (existing != null) {
				return existing;
			}
		}
		
		ReportDefinition rd = new ReportDefinition();
		rd.setName(report.getName());
		rd.setDescription(report.getDescription());
		return rd;
	}
	
	private JsonNode parseJson(String raw, String message) {
		try {
			return objectMapper.readTree(raw == null ? "{}" : raw);
		}
		catch (Exception e) {
			throw new IllegalArgumentException(message, e);
		}
	}
	
	private String decodeHtml(String s) {
		if (s == null) {
			return "";
		}
		return s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
		        .replace("&#39;", "'");
	}
	
	private List<String> resolveAgeGroupLabels(String ageCategoryCode) {
		if (ageCategoryCode == null || ageCategoryCode.trim().isEmpty()) {
			return Collections.emptyList();
		}
		
		ReportBuilderAgeCategory category = getAgeCategoryByCode(ageCategoryCode);
		if (category == null || category.getAgeGroups() == null || category.getAgeGroups().isEmpty()) {
			return Collections.emptyList();
		}
		
		List<ReportBuilderAgeGroup> groups = new ArrayList<ReportBuilderAgeGroup>(category.getAgeGroups());
		Collections.sort(groups, new Comparator<ReportBuilderAgeGroup>() {
			
			@Override
			public int compare(ReportBuilderAgeGroup g1, ReportBuilderAgeGroup g2) {
				Integer s1 = g1.getSortOrder();
				Integer s2 = g2.getSortOrder();
				
				if (s1 == null) {
					s1 = Integer.valueOf(Integer.MAX_VALUE);
				}
				if (s2 == null) {
					s2 = Integer.valueOf(Integer.MAX_VALUE);
				}
				
				return s1.compareTo(s2);
			}
		});
		
		List<String> labels = new ArrayList<String>();
		int i;
		for (i = 0; i < groups.size(); i++) {
			ReportBuilderAgeGroup g = groups.get(i);
			if (g != null && Boolean.TRUE.equals(g.getActive()) && g.getLabel() != null && !g.getLabel().trim().isEmpty()) {
				labels.add(g.getLabel());
			}
		}
		
		return labels;
	}
	
	private String buildDisaggregatedPlaceholder(String indicatorCode, String ageLabel, String gender) {
		return sanitize(indicatorCode) + "_" + sanitize(ageLabel) + "_" + sanitize(gender);
	}
	
	private Map<String, ObjectNode> buildSectionDimensionMetadata(JsonNode sections) {
		Map<String, ObjectNode> out = new LinkedHashMap<String, ObjectNode>();
		
		if (!sections.isArray()) {
			return out;
		}
		
		Iterator<JsonNode> sectionIterator = sections.elements();
		while (sectionIterator.hasNext()) {
			JsonNode sectionRef = sectionIterator.next();
			String sectionUuid = sectionRef.path("sectionUuid").asText(null);
			if (sectionUuid == null || sectionUuid.trim().isEmpty()) {
				continue;
			}
			
			ReportBuilderSection section = getReportBuilderSectionByUuid(sectionUuid);
			if (section == null) {
				continue;
			}
			
			JsonNode sectionConfig = parseJson(section.getConfigJson(), "Invalid section configJson for " + sectionUuid);
			
			ObjectNode meta = objectMapper.createObjectNode();
			boolean disaggregated = sectionConfig.path("disaggregation").isObject()
			        && !sectionConfig.path("disaggregation").path("none").asBoolean(false);
			
			meta.put("disaggregated", disaggregated);
			meta.put("ageCategoryCode", sectionConfig.path("disaggregation").path("ageCategoryCode").asText(""));
			
			out.put(sectionUuid, meta);
		}
		
		return out;
	}
	
	@Override
	public ReportCategory saveReportCategory(ReportCategory category) {
		if (category.getUuid() == null) {
			category.setUuid(UUID.randomUUID().toString());
		}
		return dao.saveReportCategory(category);
	}
	
	@Override
	public ReportCategory getReportCategoryById(Integer id) {
		return dao.getReportCategoryById(id);
	}
	
	@Override
	public ReportCategory getReportCategoryByUuid(String uuid) {
		return dao.getReportCategoryByUuid(uuid);
	}
	
	@Override
	public List<ReportCategory> getReportCategories(String q, boolean includeRetired, Integer startIndex, Integer limit) {
		return dao.getReportCategories(q, includeRetired, startIndex, limit);
	}
	
	@Override
	public long getReportCategoriesCount(String q, boolean includeRetired) {
		return dao.getReportCategoriesCount(q, includeRetired);
	}
	
	@Override
	public void retireReportCategory(ReportCategory category, String reason) {
		category.setRetired(true);
		category.setRetireReason(reason);
		dao.saveReportCategory(category);
	}
	
	@Override
	public void unretireReportCategory(ReportCategory category) {
		category.setRetired(false);
		category.setRetireReason(null);
		dao.saveReportCategory(category);
	}
	
	@Override
	public void purgeReportCategory(ReportCategory category) {
		dao.purgeReportCategory(category);
	}
	
	@Override
	public ReportLibrary saveReportLibrary(ReportLibrary reportLibrary) {
		if (reportLibrary.getUuid() == null) {
			reportLibrary.setUuid(UUID.randomUUID().toString());
		}
		return dao.saveReportLibrary(reportLibrary);
	}
	
	@Override
	public ReportLibrary getReportLibraryById(Integer id) {
		return dao.getReportLibraryById(id);
	}
	
	@Override
	public ReportLibrary getReportLibraryByUuid(String uuid) {
		return dao.getReportLibraryByUuid(uuid);
	}
	
	@Override
	public List<ReportLibrary> getReportLibraries(String q, boolean includeRetired, Integer startIndex, Integer limit) {
		return dao.getReportLibraries(q, includeRetired, startIndex, limit);
	}
	
	@Override
	public long getReportLibrariesCount(String q, boolean includeRetired) {
		return dao.getReportLibrariesCount(q, includeRetired);
	}
	
	@Override
	public void retireReportLibrary(ReportLibrary reportLibrary, String reason) {
		reportLibrary.setRetired(true);
		reportLibrary.setRetireReason(reason);
		dao.saveReportLibrary(reportLibrary);
	}
	
	@Override
	public void unretireReportLibrary(ReportLibrary reportLibrary) {
		reportLibrary.setRetired(false);
		reportLibrary.setRetireReason(null);
		dao.saveReportLibrary(reportLibrary);
	}
	
	@Override
	public void purgeReportLibrary(ReportLibrary reportLibrary) {
		dao.purgeReportLibrary(reportLibrary);
	}
	
	/**
	 * Automatically add a report builder report to the report library
	 */
	private void addToReportLibrary(ReportBuilderReport report) {
		try {
			// Check if library entry already exists for this report
			ReportLibrary existingEntry = dao.getReportLibraryByBuilderReportUuid(report.getUuid());
			
			if (existingEntry != null) {
				// Update existing entry
				existingEntry.setName(report.getName());
				existingEntry.setDescription(report.getDescription());
				existingEntry.setCode(report.getCode());
				existingEntry.setCategory(report.getCategory());
				existingEntry.setReportType(report.getReportType());
				existingEntry.setRetired(report.getRetired());
				dao.saveReportLibrary(existingEntry);
				log.debug("Updated report library entry for report: {}", report.getName());
			} else {
				// Create new library entry
				ReportLibrary libraryEntry = new ReportLibrary();
				libraryEntry.setUuid(UUID.randomUUID().toString());
				libraryEntry.setName(report.getName());
				libraryEntry.setDescription(report.getDescription());
				libraryEntry.setCode(report.getCode());
				libraryEntry.setSourceType(ReportLibrary.ReportSourceType.BUILDER);
				libraryEntry.setReportBuilderReportUuid(report.getUuid());
				libraryEntry.setCategory(report.getCategory());
				libraryEntry.setReportType(report.getReportType());
				libraryEntry.setMigrated(false);
				libraryEntry.setRetired(report.getRetired());
				dao.saveReportLibrary(libraryEntry);
				log.debug("Added report to library: {}", report.getName());
			}
		}
		catch (Exception e) {
			log.error("Failed to add report to library: {}", report.getName(), e);
			// Don't throw exception to prevent breaking the save operation
		}
	}
	
	/**
	 * Add a generic report to the report library Note: For generic reports, reportDefinitionUuid is
	 * used only as a reference identifier. The actual report definition is stored as JSON in the
	 * LegacyReport table, not as a serialized ReportDefinition in the reporting module tables.
	 */
	public void addGenericReportToLibrary(String reportDefinitionUuid, String name, String description, String code,
	        ReportCategory category, ReportBuilderReport.ReportType reportType) {
		try {
			// Check if library entry already exists for this report definition
			ReportLibrary existingEntry = dao.getReportLibraryByReportDefinitionUuid(reportDefinitionUuid);
			
			if (existingEntry != null) {
				// Update existing entry
				existingEntry.setName(name);
				existingEntry.setDescription(description);
				existingEntry.setCode(code);
				existingEntry.setCategory(category);
				existingEntry.setReportType(reportType);
				dao.saveReportLibrary(existingEntry);
				log.debug("Updated report library entry for generic report: {}", name);
				return;
			}
			
			// Check for broken references - entries with the same name but missing ReportDefinition
			List<ReportLibrary> entriesByName = dao.getReportLibrariesByName(name);
			if (entriesByName != null && !entriesByName.isEmpty()) {
				for (ReportLibrary entry : entriesByName) {
					// Check if this entry has the same UUID
					if (reportDefinitionUuid.equals(entry.getReportDefinitionUuid())) {
						// Found an entry with the same UUID - update it
						log.info("Updating existing report library entry for: {} (UUID: {})", name, reportDefinitionUuid);
						entry.setName(name);
						entry.setDescription(description);
						entry.setCode(code);
						entry.setCategory(category);
						entry.setReportType(reportType);
						entry.setRetired(false); // Unretire if it was retired
						entry.setMigrated(true);
						dao.saveReportLibrary(entry);
						log.info("Updated report library entry: {}", name);
						return;
					}
				}
			}
			
			// Create new library entry
			ReportLibrary libraryEntry = new ReportLibrary();
			libraryEntry.setUuid(UUID.randomUUID().toString());
			libraryEntry.setName(name);
			libraryEntry.setDescription(description);
			libraryEntry.setCode(code);
			libraryEntry.setSourceType(ReportLibrary.ReportSourceType.LEGACY);
			libraryEntry.setReportDefinitionUuid(reportDefinitionUuid);
			libraryEntry.setCategory(category);
			libraryEntry.setReportType(reportType);
			libraryEntry.setMigrated(true);
			dao.saveReportLibrary(libraryEntry);
			log.debug("Added generic report to library: {}", name);
		}
		catch (Exception e) {
			log.error("Failed to add generic report to library: {}", name, e);
			// Don't throw exception to prevent breaking the import operation
		}
	}
	
	@Override
	public int cleanupBrokenReportReferences() {
		try {
			// Get all report library entries
			List<ReportLibrary> allLibraries = dao.getReportLibraries("", false, 0, Integer.MAX_VALUE);
			int cleanedCount = 0;
			
			for (ReportLibrary library : allLibraries) {
				// For legacy reports (sourceType = LEGACY), check if LegacyReport exists
				// For builder reports (sourceType = BUILDER), check if ReportDefinition exists
				if (library.getReportDefinitionUuid() != null && !library.getReportDefinitionUuid().trim().isEmpty()) {
					if (library.getSourceType() == ReportLibrary.ReportSourceType.LEGACY) {
						// For legacy reports, check if LegacyReport exists
						try {
							LegacyReport legacyReport = dao.getLegacyReportByUuid(library.getReportDefinitionUuid());
							if (legacyReport == null || legacyReport.isRetired()) {
								// LegacyReport not found or retired - retire this broken entry
								log.info(
								    "Retiring broken legacy report library entry: {} (UUID: {}, missing LegacyReport: {})",
								    library.getName(), library.getUuid(), library.getReportDefinitionUuid());
								library.setRetired(true);
								library.setRetireReason("LegacyReport not found - cleaned up by cleanupBrokenReportReferences");
								dao.saveReportLibrary(library);
								cleanedCount++;
							}
						}
						catch (Exception e) {
							// Error checking LegacyReport - log and continue
							log.debug("Error checking LegacyReport for library entry {}: {}", library.getName(),
							    e.getMessage());
						}
					} else {
						// For builder reports, try to check if ReportDefinition exists
						try {
							org.openmrs.module.reporting.report.definition.ReportDefinition rd = Context.getService(
							    org.openmrs.module.reporting.report.definition.service.ReportDefinitionService.class)
							        .getDefinitionByUuid(library.getReportDefinitionUuid());
							
							if (rd == null) {
								// ReportDefinition not found - retire this broken entry
								log.info(
								    "Retiring broken report library entry: {} (UUID: {}, missing ReportDefinition: {})",
								    library.getName(), library.getUuid(), library.getReportDefinitionUuid());
								library.setRetired(true);
								library.setRetireReason("ReportDefinition not found - cleaned up by cleanupBrokenReportReferences");
								dao.saveReportLibrary(library);
								cleanedCount++;
							}
						}
						catch (Exception e) {
							// Error checking ReportDefinition - log and continue
							log.debug("Error checking ReportDefinition for library entry {}: {}", library.getName(),
							    e.getMessage());
						}
					}
				}
			}
			
			log.info("Cleaned up {} broken report library entries", cleanedCount);
			return cleanedCount;
		}
		catch (Exception e) {
			log.error("Failed to cleanup broken report references", e);
			return 0;
		}
	}
	
	@Override
	@Transactional
	public ETLSource saveETLSource(ETLSource etlSource) {
		return dao.saveETLSource(etlSource);
	}
	
	@Override
	@Transactional(readOnly = true)
	public ETLSource getETLSourceByUuid(String uuid) {
		return dao.getETLSourceByUuid(uuid);
	}
	
	@Override
	@Transactional(readOnly = true)
	public ETLSource getETLSourceById(Integer id) {
		return dao.getETLSourceById(id);
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<ETLSource> getAllETLSources(boolean includeRetired) {
		getAllowedTablePrefixes();
		return dao.getAllETLSources(includeRetired);
	}
	
	@Override
	@Transactional
	public void retireETLSource(ETLSource etlSource, String retireReason) {
		etlSource.setRetired(true);
		etlSource.setRetireReason(retireReason);
		dao.saveETLSource(etlSource);
	}
	
	public List<String> getAllowedTablePrefixes() {
		List<String> prefixes = new ArrayList<String>();
		List<ETLSource> sources = dao.getAllETLSources(false);
		
		int i;
		for (i = 0; i < sources.size(); i++) {
			ETLSource source = sources.get(i);
			if (Boolean.TRUE.equals(source.getActive()) && source.getTablePatterns() != null) {
				String[] parts = source.getTablePatterns().split(",");
				int j;
				for (j = 0; j < parts.length; j++) {
					String part = parts[j];
					String value = part == null ? null : part.trim();
					if (value != null && !value.isEmpty()) {
						prefixes.add(value);
					}
				}
			}
		}
		
		return prefixes;
	}
	
	@Override
	public ReportImportResult importLegacyReportPackage(File reportFile) throws Exception {
		File legacyRootDir = resolveLegacyRootFromReportFile(reportFile);
		return doImportLegacyReportPackage(legacyRootDir, reportFile, false);
	}
	
	@Override
	public ReportImportResult validateLegacyReportPackage(File reportFile) throws Exception {
		File legacyRootDir = resolveLegacyRootFromReportFile(reportFile);
		return doImportLegacyReportPackage(legacyRootDir, reportFile, true);
	}
	
	@Override
	public List<ReportImportResult> importAllLegacyReportPackages(File legacyReportsRootDir) throws Exception {
		validateDirectory(legacyReportsRootDir, "Legacy reports root directory");
		
		File reportsDir = new File(legacyReportsRootDir, "reports");
		validateDirectory(reportsDir, "Legacy reports directory");
		
		List<ReportImportResult> results = new ArrayList<ReportImportResult>();
		
		File[] reportFiles = reportsDir.listFiles(new java.io.FilenameFilter() {
			
			@Override
			public boolean accept(File dir, String name) {
				// Skip generic reports - they should be imported via GenericReportImporter
				return name != null && name.toLowerCase().endsWith(".json") && !name.endsWith("-generic.json");
			}
		});
		
		if (reportFiles == null || reportFiles.length == 0) {
			return results;
		}
		
		Arrays.sort(reportFiles, new Comparator<File>() {
			
			@Override
			public int compare(File f1, File f2) {
				return f1.getName().compareTo(f2.getName());
			}
		});
		
		int i;
		for (i = 0; i < reportFiles.length; i++) {
			try {
				results.add(doImportLegacyReportPackage(legacyReportsRootDir, reportFiles[i], false));
			}
			catch (Exception e) {
				// Log error and continue with other files
				log.error("Failed to import legacy report from file: " + reportFiles[i].getName(), e);
				ReportImportResult errorResult = new ReportImportResult();
				errorResult.setReportName(reportFiles[i].getName());
				errorResult.addMessage("ERROR: " + e.getMessage());
				results.add(errorResult);
			}
		}
		
		return results;
	}
	
	@Override
	public ReportImportResult importRuntimeLegacyReportPackage(String reportKey) throws Exception {
		File legacyRootDir = resolveRuntimeLegacyReportsRootDirectory();
		File reportFile = resolveRuntimeReportFile(legacyRootDir, reportKey);
		return doImportLegacyReportPackage(legacyRootDir, reportFile, false);
	}
	
	@Override
	public ReportImportResult validateRuntimeLegacyReportPackage(String reportKey) throws Exception {
		File legacyRootDir = resolveRuntimeLegacyReportsRootDirectory();
		File reportFile = resolveRuntimeReportFile(legacyRootDir, reportKey);
		return doImportLegacyReportPackage(legacyRootDir, reportFile, true);
	}
	
	@Override
	public List<ReportImportResult> importAllRuntimeLegacyReportPackages() throws Exception {
		File legacyRootDir = resolveRuntimeLegacyReportsRootDirectory();
		return importAllLegacyReportPackages(legacyRootDir);
	}
	
	private ReportImportResult doImportLegacyReportPackage(File legacyRootDir, File reportFile, boolean validateOnly)
	        throws Exception {
		validateDirectory(legacyRootDir, "Legacy reports root directory");
		
		if (reportFile == null || !reportFile.exists() || !reportFile.isFile()) {
			throw new IllegalArgumentException("Legacy report file not found: "
			        + (reportFile == null ? "null" : reportFile.getAbsolutePath()));
		}
		
		ReportImportResult result = new ReportImportResult();
		ReportConfig reportConfig = loadLegacyReportConfig(reportFile);
		
		try {
			legacyConfigValidator.validateReportConfig(reportConfig);
			result.setReportName(reportConfig.getName());
		}
		catch (Exception e) {
			// Add file name to error message for easier debugging
			throw new IllegalArgumentException("Failed to process legacy report file '" + reportFile.getName() + "': "
			        + e.getMessage(), e);
		}
		
		List<Parameter> parameters = buildLegacyParameters(legacyRootDir, reportConfig);
		Map<String, CohortDefinition> builtCohorts = buildLegacyCohorts(legacyRootDir, parameters);
		Map<String, DataSetDefinition> datasets = buildLegacyDatasets(legacyRootDir, reportConfig, parameters, builtCohorts);
		
		ReportDefinition reportDefinition = buildLegacyReportDefinition(reportConfig, parameters, datasets);
		
		if (!validateOnly) {
			reportDefinition = saveLegacyReportDefinition(reportDefinition);
			saveLegacyReportDesigns(legacyRootDir, reportConfig, reportDefinition);
			saveLegacyReportEntity(reportConfig, reportDefinition);
		}
		
		result.addMessage((validateOnly ? "Validated" : "Imported") + " legacy report package: " + reportConfig.getName());
		result.addMessage("Report file: " + reportFile.getName());
		result.addMessage("Datasets: " + datasets.size());
		result.addMessage("Cohorts: " + builtCohorts.size());
		result.addMessage("Mode: " + (validateOnly ? "VALIDATE_ONLY" : "IMPORT"));
		
		return result;
	}
	
	private ReportConfig loadLegacyReportConfig(File reportFile) throws Exception {
		if (reportFile == null || !reportFile.exists() || !reportFile.isFile()) {
			throw new IllegalArgumentException("Legacy report file not found: "
			        + (reportFile == null ? "null" : reportFile.getAbsolutePath()));
		}
		return legacyConfigParser.parse(reportFile, ReportConfig.class);
	}
	
	private List<Parameter> buildLegacyParameters(File legacyRootDir, ReportConfig reportConfig) throws Exception {
		List<Parameter> parameters = new ArrayList<Parameter>();
		
		if (hasText(reportConfig.getParametersRef())) {
			ParameterSetConfig parameterSet = legacyReferenceResolver.resolveParameterSet(legacyRootDir,
			    reportConfig.getParametersRef());
			
			if (parameterSet.getParameters() != null) {
				int i;
				for (i = 0; i < parameterSet.getParameters().size(); i++) {
					ParameterConfig pc = parameterSet.getParameters().get(i);
					parameters.add(legacyParameterBuilder.build(pc));
				}
			}
		} else if (reportConfig.getParameters() != null) {
			int i;
			for (i = 0; i < reportConfig.getParameters().size(); i++) {
				ParameterConfig pc = reportConfig.getParameters().get(i);
				parameters.add(legacyParameterBuilder.build(pc));
			}
		}
		
		return parameters;
	}
	
	private Map<String, CohortDefinition> buildLegacyCohorts(File legacyRootDir, List<Parameter> parameters)
	        throws Exception {
		Map<String, CohortDefinition> builtCohorts = new LinkedHashMap<String, CohortDefinition>();
		
		File cohortsDir = new File(legacyRootDir, "cohorts");
		if (!cohortsDir.exists()) {
			return builtCohorts;
		}
		
		validateDirectory(cohortsDir, "Cohorts directory");
		
		File[] files = cohortsDir.listFiles(new java.io.FilenameFilter() {
			
			@Override
			public boolean accept(File dir, String name) {
				return name != null && name.endsWith(".json");
			}
		});
		
		if (files == null || files.length == 0) {
			return builtCohorts;
		}
		
		Arrays.sort(files, new Comparator<File>() {
			
			@Override
			public int compare(File f1, File f2) {
				String n1 = f1 == null ? null : f1.getName();
				String n2 = f2 == null ? null : f2.getName();
				
				if (n1 == null && n2 == null) {
					return 0;
				}
				if (n1 == null) {
					return -1;
				}
				if (n2 == null) {
					return 1;
				}
				return n1.compareTo(n2);
			}
		});
		
		int i;
		for (i = 0; i < files.length; i++) {
			File file = files[i];
			Map<String, CohortConfig> cohortMap = legacyReferenceResolver.resolveCohortMap(file);
			
			for (Map.Entry<String, CohortConfig> entry : cohortMap.entrySet()) {
				String refKey = buildCohortRefKey(file, entry.getKey());
				CohortDefinition definition = legacyCohortDefinitionFactory.build(entry.getKey(), entry.getValue(),
				    builtCohorts, parameters);
				builtCohorts.put(refKey, definition);
			}
		}
		
		return builtCohorts;
	}
	
	private Map<String, DataSetDefinition> buildLegacyDatasets(File legacyRootDir, ReportConfig reportConfig,
	        List<Parameter> parameters, Map<String, CohortDefinition> builtCohorts) throws Exception {
		
		Map<String, DataSetDefinition> datasets = new LinkedHashMap<String, DataSetDefinition>();
		
		if (reportConfig.getDatasets() == null || reportConfig.getDatasets().isEmpty()) {
			return datasets;
		}
		
		int i;
		for (i = 0; i < reportConfig.getDatasets().size(); i++) {
			DatasetRefConfig datasetRef = reportConfig.getDatasets().get(i);
			
			DatasetConfig datasetConfig;
			if (datasetRef.isInlineDefinition()) {
				datasetConfig = datasetRef;
			} else {
				File datasetFile = requireFile(legacyRootDir, datasetRef.getFile());
				datasetConfig = legacyConfigParser.parse(datasetFile, DatasetConfig.class);
			}
			
			legacyConfigValidator.validateDatasetConfig(datasetConfig);
			DataSetDefinition dsd = legacyDatasetDefinitionFactory.build(datasetConfig, parameters, builtCohorts);
			datasets.put(datasetRef.getKey(), dsd);
		}
		
		return datasets;
	}
	
	private ReportDefinition buildLegacyReportDefinition(ReportConfig reportConfig, List<Parameter> parameters,
	        Map<String, DataSetDefinition> datasets) {
		return legacyReportDefinitionFactory.build(reportConfig, parameters, datasets);
	}
	
	private ReportDefinition saveLegacyReportDefinition(ReportDefinition reportDefinition) {
		ReportDefinitionService reportDefinitionService = Context.getService(ReportDefinitionService.class);
		
		if (reportDefinition == null) {
			throw new IllegalArgumentException("Report definition is required");
		}
		
		if (hasText(reportDefinition.getUuid())) {
			ReportDefinition existing = reportDefinitionService.getDefinitionByUuid(reportDefinition.getUuid());
			if (existing != null) {
				existing.setName(reportDefinition.getName());
				existing.setDescription(reportDefinition.getDescription());
				
				existing.getParameters().clear();
				if (reportDefinition.getParameters() != null) {
					int i;
					for (i = 0; i < reportDefinition.getParameters().size(); i++) {
						existing.addParameter(reportDefinition.getParameters().get(i));
					}
				}
				
				existing.getDataSetDefinitions().clear();
				Map<String, Mapped<? extends DataSetDefinition>> mappings = reportDefinition.getDataSetDefinitions();
				if (mappings != null) {
					for (Map.Entry<String, Mapped<? extends DataSetDefinition>> e : mappings.entrySet()) {
						existing.getDataSetDefinitions().put(e.getKey(), e.getValue());
					}
				}
				
				return reportDefinitionService.saveDefinition(existing);
			}
		}
		
		return reportDefinitionService.saveDefinition(reportDefinition);
	}
	
	private void saveLegacyReportDesigns(File legacyRootDir, ReportConfig reportConfig, ReportDefinition reportDefinition)
	        throws Exception {
		List<ReportDesign> designs = legacyDesignBuilder.build(reportConfig, reportDefinition, legacyRootDir);
		if (designs == null || designs.isEmpty()) {
			return;
		}
		
		ReportService reportService = Context.getService(ReportService.class);
		
		int i;
		for (i = 0; i < designs.size(); i++) {
			reportService.saveReportDesign(designs.get(i));
		}
	}
	
	private void saveLegacyReportEntity(ReportConfig reportConfig, ReportDefinition reportDefinition) throws Exception {
		try {
			// Check if a legacy report with this UUID already exists
			LegacyReport existing = dao.getLegacyReportByUuid(reportConfig.getUuid());
			if (existing != null) {
				// Update existing record
				existing.setName(reportConfig.getName());
				existing.setDescription(reportConfig.getDescription());
				existing.setVersion(null); // ReportConfig doesn't have version
				existing.setCategory(null); // ReportConfig doesn't have category
				existing.setSubcategory(null); // ReportConfig doesn't have subcategory
				existing.setReportType(null); // ReportConfig doesn't have reportType
				existing.setReportYear(null); // ReportConfig doesn't have reportYear
				existing.setReportScope(null); // ReportConfig doesn't have reportScope
				existing.setStatus("ACTIVE");
				existing.setDateChanged(new java.util.Date());
				
				// Update the JSON config
				com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
				String configJson = mapper.writeValueAsString(reportConfig);
				existing.setConfigJson(configJson);
				
				dao.saveLegacyReport(existing);
			} else {
				// Create new legacy report record
				LegacyReport legacyReport = new LegacyReport();
				legacyReport.setUuid(reportConfig.getUuid());
				legacyReport.setName(reportConfig.getName());
				legacyReport.setDescription(reportConfig.getDescription());
				legacyReport.setVersion(null); // ReportConfig doesn't have version
				legacyReport.setCategory(null); // ReportConfig doesn't have category
				legacyReport.setSubcategory(null); // ReportConfig doesn't have subcategory
				legacyReport.setReportType(null); // ReportConfig doesn't have reportType
				legacyReport.setReportYear(null); // ReportConfig doesn't have reportYear
				legacyReport.setReportScope(null); // ReportConfig doesn't have reportScope
				legacyReport.setStatus("ACTIVE");
				
				// Convert the ReportConfig to JSON and store it
				com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
				String configJson = mapper.writeValueAsString(reportConfig);
				legacyReport.setConfigJson(configJson);
				
				dao.saveLegacyReport(legacyReport);
			}
		}
		catch (Exception e) {
			// Log error but don't fail the import
			System.err.println("Error saving legacy report entity: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	private File resolveRuntimeLegacyReportsRootDirectory() {
		String appDataDir = OpenmrsUtil.getApplicationDataDirectory();
		if (appDataDir == null || appDataDir.trim().length() == 0) {
			throw new IllegalArgumentException("Global property application_data_directory is not set");
		}
		
		File root = new File(appDataDir, "configuration/reports/legacy");
		validateDirectory(root, "Runtime legacy reports root directory");
		return root;
	}
	
	private File resolveRuntimeReportFile(File legacyRootDir, String reportKey) {
		if (!hasText(reportKey)) {
			throw new IllegalArgumentException("Report key is required");
		}
		
		File reportsDir = new File(legacyRootDir, "reports");
		validateDirectory(reportsDir, "Runtime legacy reports directory");
		
		String trimmedKey = reportKey.trim();
		File reportFile = new File(reportsDir, trimmedKey);
		
		if (!reportFile.exists() || !reportFile.isFile()) {
			reportFile = new File(reportsDir, trimmedKey + ".json");
		}
		
		if (!reportFile.exists() || !reportFile.isFile()) {
			throw new IllegalArgumentException("Runtime legacy report file not found for key: " + reportKey);
		}
		
		return reportFile;
	}
	
	private File resolveLegacyRootFromReportFile(File reportFile) {
		if (reportFile == null || !reportFile.exists() || !reportFile.isFile()) {
			throw new IllegalArgumentException("Legacy report file not found: "
			        + (reportFile == null ? "null" : reportFile.getAbsolutePath()));
		}
		
		File reportsDir = reportFile.getParentFile();
		if (reportsDir == null || !"reports".equals(reportsDir.getName())) {
			throw new IllegalArgumentException("Report file must be inside a legacy/reports directory: "
			        + reportFile.getAbsolutePath());
		}
		
		File legacyRootDir = reportsDir.getParentFile();
		if (legacyRootDir == null) {
			throw new IllegalArgumentException("Cannot resolve legacy root from report file: "
			        + reportFile.getAbsolutePath());
		}
		
		return legacyRootDir;
	}
	
	private String buildCohortRefKey(File cohortFile, String key) {
		return "cohorts/" + stripJsonExtension(cohortFile.getName()) + "#" + key;
	}
	
	private String stripJsonExtension(String filename) {
		if (filename == null || !filename.endsWith(".json")) {
			return filename;
		}
		return filename.substring(0, filename.length() - 5);
	}
	
	private File requireFile(File baseDir, String relativePath) {
		File file = new File(baseDir, relativePath);
		if (!file.exists() || !file.isFile()) {
			throw new IllegalArgumentException("Required file not found: " + file.getAbsolutePath());
		}
		return file;
	}
	
	private void validateDirectory(File dir, String label) {
		if (dir == null || !dir.exists() || !dir.isDirectory()) {
			throw new IllegalArgumentException(label + " not found: " + (dir == null ? "null" : dir.getAbsolutePath()));
		}
	}
	
	@Override
	public void ensureImportAllLegacyReportsTaskExists() {
		String taskUuid = "8f5b0c2a-6c7c-4c4c-9b35-1b7d4ef4c001";
		String taskName = "Import All Legacy Reports";
		String taskDescription = "Imports all available legacy reports from the runtime configuration folder";
		String taskClass = "org.openmrs.module.reportbuilder.tasks.ImportAllLegacyReportsTask";
		
		org.openmrs.scheduler.SchedulerService schedulerService = Context.getSchedulerService();
		org.openmrs.scheduler.TaskDefinition task = schedulerService.getTaskByUuid(taskUuid);
		
		if (task == null) {
			task = new org.openmrs.scheduler.TaskDefinition();
			task.setUuid(taskUuid);
			task.setName(taskName);
			task.setDescription(taskDescription);
			task.setTaskClass(taskClass);
			task.setStartOnStartup(false);
			task.setStarted(false);
			task.setRepeatInterval(0L);
			schedulerService.saveTaskDefinition(task);
			return;
		}
		
		boolean changed = false;
		
		if (!taskName.equals(task.getName())) {
			task.setName(taskName);
			changed = true;
		}
		if (!taskDescription.equals(task.getDescription())) {
			task.setDescription(taskDescription);
			changed = true;
		}
		if (!taskClass.equals(task.getTaskClass())) {
			task.setTaskClass(taskClass);
			changed = true;
		}
		
		if (changed) {
			schedulerService.saveTaskDefinition(task);
		}
	}
	
	private DesignConfig resolveLegacyDesignConfig(File legacyRootDir, DesignRefConfig designRef) throws Exception {
		if (designRef == null) {
			throw new IllegalArgumentException("Design reference is required");
		}
		
		if (hasText(designRef.getFile())) {
			File designFile = requireFile(legacyRootDir, designRef.getFile());
			return legacyConfigParser.parse(designFile, DesignConfig.class);
		}
		
		DesignConfig config = new DesignConfig();
		config.setUuid(designRef.getUuid());
		config.setName(designRef.getName());
		config.setType(designRef.getType());
		config.setTemplate(designRef.getTemplate());
		return config;
	}
	
	private boolean hasText(String value) {
		return value != null && value.trim().length() > 0;
	}
	
	@Autowired
	public void setLegacyConfigParser(JsonConfigParser legacyConfigParser) {
		this.legacyConfigParser = legacyConfigParser;
	}
	
	@Autowired
	public void setLegacyReferenceResolver(ReferenceResolver legacyReferenceResolver) {
		this.legacyReferenceResolver = legacyReferenceResolver;
	}
	
	@Autowired
	public void setLegacyParameterBuilder(ParameterBuilder legacyParameterBuilder) {
		this.legacyParameterBuilder = legacyParameterBuilder;
	}
	
	@Autowired
	public void setLegacyCohortDefinitionFactory(CohortDefinitionFactory legacyCohortDefinitionFactory) {
		this.legacyCohortDefinitionFactory = legacyCohortDefinitionFactory;
	}
	
	@Autowired
	public void setLegacyDatasetDefinitionFactory(DatasetDefinitionFactory legacyDatasetDefinitionFactory) {
		this.legacyDatasetDefinitionFactory = legacyDatasetDefinitionFactory;
	}
	
	@Autowired
	public void setLegacyReportDefinitionFactory(ReportDefinitionFactory legacyReportDefinitionFactory) {
		this.legacyReportDefinitionFactory = legacyReportDefinitionFactory;
	}
	
	@Autowired
	public void setLegacyDesignBuilder(DesignBuilder legacyDesignBuilder) {
		this.legacyDesignBuilder = legacyDesignBuilder;
	}
	
	@Autowired
	public void setLegacyConfigValidator(ConfigValidator legacyConfigValidator) {
		this.legacyConfigValidator = legacyConfigValidator;
	}
	
	// =========================
	// Legacy Report Import (from LegacyReportImportService)
	// =========================
	
	@Override
	public org.openmrs.module.reporting.report.definition.ReportDefinition importReportFromFile(File jsonFile) {
		try {
			LegacyReportImporter importer = new LegacyReportImporter();
			return importer.importReportFromFile(jsonFile);
		}
		catch (Exception e) {
			throw new org.openmrs.api.APIException("Failed to import report from file: " + jsonFile.getName(), e);
		}
	}
	
	@Override
	public org.openmrs.module.reporting.report.definition.ReportDefinition importReportFromJson(String jsonContent) {
		try {
			LegacyReportImporter importer = new LegacyReportImporter();
			return importer.importReportFromJson(jsonContent);
		}
		catch (Exception e) {
			throw new org.openmrs.api.APIException("Failed to import report from JSON", e);
		}
	}
	
	@Override
	public List<org.openmrs.module.reporting.report.definition.ReportDefinition> importReportsFromDirectory(
	        File reportsDirectory) {
		try {
			LegacyReportImporter importer = new LegacyReportImporter();
			return importer.importReportsFromDirectory(reportsDirectory);
		}
		catch (Exception e) {
			throw new org.openmrs.api.APIException("Failed to import reports from directory: "
			        + reportsDirectory.getAbsolutePath(), e);
		}
	}
	
	@Override
	public org.openmrs.module.reportbuilder.legacyconfig.LegacyReportImporter.ValidationResult validateContract(
	        File jsonFile, Class<?> javaClass) {
		LegacyReportImporter importer = new LegacyReportImporter();
		return importer.validateContract(jsonFile, javaClass);
	}
	
	@Override
	public List<org.openmrs.module.reporting.report.definition.ReportDefinition> importUgandaEMRLegacyReports(
	        String legacyReportsPath) {
		File legacyReportsDir = new File(legacyReportsPath);
		if (!legacyReportsDir.exists() || !legacyReportsDir.isDirectory()) {
			throw new org.openmrs.api.APIException("Invalid UgandaEMRReports path: " + legacyReportsPath);
		}
		
		// Check for legacy/reports subdirectory (new structure)
		File reportsDir = new File(legacyReportsDir, "legacy/reports");
		if (!reportsDir.exists()) {
			// Fall back to checking for reports2019 (old structure)
			reportsDir = new File(legacyReportsDir, "reports2019");
		}
		
		if (!reportsDir.exists()) {
			throw new org.openmrs.api.APIException("No reports directory found in: " + legacyReportsPath);
		}
		
		return importReportsFromDirectory(reportsDir);
	}
	
	@Override
	public void ensureLegacyReportsImported() {
		// This method would be called during module startup to ensure
		// all legacy reports are imported into the system
		
		// Implementation would check if reports have already been imported
		// and only import new or updated reports
		
		// For now, this is a placeholder for the startup import logic
	}
	
	// =========================
	// Generic Report Import (from GenericReportImportService)
	// =========================
	
	// Generic report import service instance
	private GenericReportImportService genericReportImportService;
	
	private GenericReportImportService getGenericReportImportService() {
		if (genericReportImportService == null) {
			genericReportImportService = new GenericReportImportService();
			// Manually inject the DAO since we're not using Spring for this instance
			genericReportImportService.setReportBuilderDAO(dao);
		}
		return genericReportImportService;
	}
	
	@Override
	@Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
	public List<org.openmrs.module.reportbuilder.legacyconfig.generic.ReportImportResult> importAllGenericReports() {
		return getGenericReportImportService().importAllGenericReports();
	}
	
	@Override
	@Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
	public org.openmrs.module.reportbuilder.legacyconfig.generic.ReportImportResult importGenericReportFromFile(File jsonFile) {
		return getGenericReportImportService().importGenericReportFromFile(jsonFile);
	}
	
	@Override
	@Transactional(readOnly = true)
	public boolean areGenericReportsAlreadyImported() {
		return genericReportImportService.areGenericReportsAlreadyImported();
	}
	
	@Override
	public void ensureImportAllGenericReportsTaskExists() {
		genericReportImportService.ensureImportAllGenericReportsTaskExists();
	}
	
	// =========================================================
	// Legacy Reports
	// =========================================================
	
	@Override
	@Transactional(readOnly = true)
	public List<LegacyReportConfig> getAllLegacyReports() {
		return dao.getAllLegacyReports();
	}
	
	@Override
	@Transactional(readOnly = true)
	public LegacyReportConfig getLegacyReportByUuid(String uuid) {
		LegacyReport entity = dao.getLegacyReportByUuid(uuid);
		return convertToConfig(entity);
	}
	
	@Override
	@Transactional(readOnly = true)
	public LegacyReportConfig getLegacyReportByName(String name) {
		LegacyReport entity = dao.getLegacyReportByName(name);
		return convertToConfig(entity);
	}
	
	@Override
	@Transactional
	public LegacyReportConfig createLegacyReport(LegacyReportConfig config) {
		if (config == null) {
			throw new org.openmrs.api.APIException("Report configuration cannot be null");
		}
		
		if (config.getName() == null || config.getName().trim().isEmpty()) {
			throw new org.openmrs.api.APIException("Report name is required");
		}
		
		// Check for duplicate name
		LegacyReport existingEntity = dao.getLegacyReportByName(config.getName());
		if (existingEntity != null) {
			throw new org.openmrs.api.APIException("A report with this name already exists");
		}
		
		// Validate the configuration
		ReportValidationResult validation = validateLegacyReport(config);
		if (!validation.isValid()) {
			throw new org.openmrs.api.APIException("Invalid report configuration: " + validation.getSummary());
		}
		
		// Generate UUID if not provided
		if (config.getUuid() == null || config.getUuid().trim().isEmpty()) {
			config.setUuid(java.util.UUID.randomUUID().toString());
		}
		
		// Generate key if not provided (for frontend compatibility)
		if (config.getKey() == null || config.getKey().trim().isEmpty()) {
			config.setKey(generateKeyFromName(config.getName()));
		}
		
		// Set default values
		if (config.getStatus() == null || config.getStatus().trim().isEmpty()) {
			config.setStatus("ACTIVE");
		}
		
		LegacyReport entity = convertToEntity(config);
		dao.saveLegacyReport(entity);
		return convertToConfig(entity);
	}
	
	@Override
	@Transactional
	public LegacyReportConfig updateLegacyReport(String uuid, LegacyReportConfig config) {
		if (uuid == null || uuid.trim().isEmpty()) {
			throw new org.openmrs.api.APIException("Report UUID is required");
		}
		
		if (config == null) {
			throw new org.openmrs.api.APIException("Report configuration cannot be null");
		}
		
		// Check if report exists
		LegacyReport existingEntity = dao.getLegacyReportByUuid(uuid);
		if (existingEntity == null) {
			throw new org.openmrs.api.APIException("Report not found with UUID: " + uuid);
		}
		LegacyReportConfig existing = convertToConfig(existingEntity);
		
		// Check for duplicate name (if name changed)
		if (!existing.getName().equals(config.getName())) {
			LegacyReport duplicateEntity = dao.getLegacyReportByName(config.getName());
			if (duplicateEntity != null && !duplicateEntity.getUuid().equals(uuid)) {
				throw new org.openmrs.api.APIException("A report with this name already exists");
			}
		}
		
		// Validate the configuration
		ReportValidationResult validation = validateLegacyReport(config);
		if (!validation.isValid()) {
			throw new org.openmrs.api.APIException("Invalid report configuration: " + validation.getSummary());
		}
		
		// Set the UUID to ensure we're updating the correct report
		config.setUuid(uuid);
		
		LegacyReport entity = convertToEntity(config);
		dao.saveLegacyReport(entity);
		return convertToConfig(entity);
	}
	
	@Override
	@Transactional
	public void deleteLegacyReport(String uuid) {
		if (uuid == null || uuid.trim().isEmpty()) {
			throw new org.openmrs.api.APIException("Report UUID is required");
		}
		
		// Check if report exists
		LegacyReport existingEntity = dao.getLegacyReportByUuid(uuid);
		if (existingEntity == null) {
			throw new org.openmrs.api.APIException("Report not found with UUID: " + uuid);
		}
		
		dao.deleteLegacyReport(uuid);
	}
	
	@Override
	@Transactional(readOnly = true)
	public ReportValidationResult validateLegacyReport(LegacyReportConfig config) {
		ReportValidationResult result = new ReportValidationResult();
		
		if (config == null) {
			result.addError("Report configuration cannot be null");
			return result;
		}
		
		// Validate basic fields
		if (config.getName() == null || config.getName().trim().isEmpty()) {
			result.addError("Report name is required");
		}
		
		if (config.getVersion() == null || config.getVersion().trim().isEmpty()) {
			result.addWarning("Report version is not specified");
		}
		
		// Validate parameters
		if (config.getParameters() != null) {
			for (int i = 0; i < config.getParameters().size(); i++) {
				LegacyReportConfig.Parameter param = config.getParameters().get(i);
				if (param.getName() == null || param.getName().trim().isEmpty()) {
					result.addError("Parameter at index " + i + " is missing a name");
				}
				if (param.getType() == null || param.getType().trim().isEmpty()) {
					result.addError("Parameter '" + param.getName() + "' is missing a type");
				}
			}
		}
		
		// Validate advanced features
		if (config.getAdvancedFeatures() != null && config.getAdvancedFeatures().getIndicatorDataSet() != null
		        && config.getAdvancedFeatures().getIndicatorDataSet().isEnabled()) {
			
			validateIndicatorDataSet(config, result);
		}
		
		// Validate dataset definitions
		if (config.getDataSetDefinitions() != null) {
			for (int i = 0; i < config.getDataSetDefinitions().size(); i++) {
				LegacyReportConfig.DataSetDefinition dataset = config.getDataSetDefinitions().get(i);
				if (dataset.getName() == null || dataset.getName().trim().isEmpty()) {
					result.addError("Dataset definition at index " + i + " is missing a name");
				}
				if (dataset.getType() == null || dataset.getType().trim().isEmpty()) {
					result.addError("Dataset '" + dataset.getName() + "' is missing a type");
				}
			}
		}
		
		// Validate SQL
		validateSQLQueries(config, result);
		
		result.setValid(!result.hasErrors());
		
		return result;
	}
	
	private void validateIndicatorDataSet(LegacyReportConfig config, ReportValidationResult result) {
		LegacyReportConfig.IndicatorDataSet indicatorDataSet = config.getAdvancedFeatures().getIndicatorDataSet();
		
		// Validate indicators
		if (indicatorDataSet.getIndicators() != null) {
			for (int i = 0; i < indicatorDataSet.getIndicators().size(); i++) {
				LegacyReportConfig.Indicator indicator = indicatorDataSet.getIndicators().get(i);
				
				if (indicator.getKey() == null || indicator.getKey().trim().isEmpty()) {
					result.addError("Indicator at index " + i + " is missing a key");
				}
				
				if (indicator.getType() == null || indicator.getType().trim().isEmpty()) {
					result.addError("Indicator '" + indicator.getKey() + "' is missing a type");
				}
				
				// Validate BASE indicators
				if ("BASE".equalsIgnoreCase(indicator.getType())) {
					if (indicator.getSqlQuery() == null || indicator.getSqlQuery().trim().isEmpty()) {
						result.addError("BASE indicator '" + indicator.getKey() + "' is missing a SQL query");
					}
				}
				
				// Validate COMPOSITE indicators
				if ("COMPOSITE".equalsIgnoreCase(indicator.getType())) {
					if (indicator.getFormula() == null || indicator.getFormula().trim().isEmpty()) {
						result.addError("COMPOSITE indicator '" + indicator.getKey() + "' is missing a formula");
					}
				}
				
				// Validate TEMPORAL indicators
				if ("TEMPORAL".equalsIgnoreCase(indicator.getType())) {
					if (indicator.getBaseIndicator() == null || indicator.getBaseIndicator().trim().isEmpty()) {
						result.addError("TEMPORAL indicator '" + indicator.getKey()
						        + "' is missing a base indicator reference");
					}
				}
			}
		}
		
		// Validate dimension definitions
		if (indicatorDataSet.getDimensionDefinitions() != null) {
			for (int i = 0; i < indicatorDataSet.getDimensionDefinitions().size(); i++) {
				LegacyReportConfig.DimensionDefinition dimension = indicatorDataSet.getDimensionDefinitions().get(i);
				
				if (dimension.getName() == null || dimension.getName().trim().isEmpty()) {
					result.addError("Dimension definition at index " + i + " is missing a name");
				}
				
				if (dimension.getType() == null || dimension.getType().trim().isEmpty()) {
					result.addError("Dimension '" + dimension.getName() + "' is missing a type");
				}
				
				// Validate dimension groups
				if (dimension.getGroups() != null && dimension.getGroups().isEmpty()) {
					result.addWarning("Dimension '" + dimension.getName() + "' has no groups defined");
				}
			}
		}
	}
	
	private void validateSQLQueries(LegacyReportConfig config, ReportValidationResult result) {
		// Validate SQL queries in indicators
		if (config.getAdvancedFeatures() != null && config.getAdvancedFeatures().getIndicatorDataSet() != null
		        && config.getAdvancedFeatures().getIndicatorDataSet().getIndicators() != null) {
			
			for (LegacyReportConfig.Indicator indicator : config.getAdvancedFeatures().getIndicatorDataSet().getIndicators()) {
				if ("BASE".equalsIgnoreCase(indicator.getType()) && indicator.getSqlQuery() != null) {
					validateSQLQuery(indicator.getSqlQuery(), result, "Indicator '" + indicator.getKey() + "'");
				}
			}
		}
		
		// Validate SQL queries in dataset definitions
		if (config.getDataSetDefinitions() != null) {
			for (LegacyReportConfig.DataSetDefinition dataset : config.getDataSetDefinitions()) {
				if ("SQL_DATA_SET".equalsIgnoreCase(dataset.getType()) && dataset.getConfig() != null
				        && dataset.getConfig().getSql() != null) {
					validateSQLQuery(dataset.getConfig().getSql(), result, "Dataset '" + dataset.getName() + "'");
				}
			}
		}
	}
	
	private void validateSQLQuery(String sql, ReportValidationResult result, String context) {
		// Basic SQL validation
		if (sql.trim().isEmpty()) {
			result.addError(context + " has empty SQL query");
			return;
		}
		
		// Check for dangerous operations
		String upperSQL = sql.toUpperCase();
		String[] dangerousOperations = { "DROP", "DELETE", "TRUNCATE", "ALTER", "CREATE", "INSERT", "UPDATE" };
		
		for (String dangerous : dangerousOperations) {
			if (upperSQL.contains(dangerous)) {
				result.addError(context + " contains dangerous SQL operation: " + dangerous);
				result.getSqlValidation().addSqlError(context + ": " + dangerous + " operation not allowed");
			}
		}
		
		// Validate SQL syntax (basic check)
		if (!upperSQL.startsWith("SELECT")) {
			result.addWarning(context + " SQL query does not start with SELECT");
		}
		
		if (!upperSQL.contains("FROM")) {
			result.addError(context + " SQL query is missing FROM clause");
		}
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<LegacyReportConfig> getLegacyReportsByCategory(String category) {
		return dao.getLegacyReportsByCategory(category);
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<LegacyReportConfig> getLegacyReportsByStatus(String status) {
		return dao.getLegacyReportsByStatus(status);
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<LegacyReportConfig> searchLegacyReports(String query) {
		return dao.searchLegacyReports(query);
	}
	
	@Override
	@Transactional(readOnly = true)
	public int getLegacyReportCount() {
		return dao.getLegacyReportCount();
	}
	
	// Helper methods for LegacyReport conversion
	private LegacyReportConfig convertToConfig(LegacyReport entity) {
		if (entity == null) {
			return null;
		}
		
		try {
			com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
			LegacyReportConfig config = objectMapper.readValue(entity.getConfigJson(), LegacyReportConfig.class);
			config.setUuid(entity.getUuid());
			// Ensure key field is populated - generate from name if not present in JSON
			if (config.getKey() == null || config.getKey().trim().isEmpty()) {
				config.setKey(generateKeyFromName(entity.getName()));
			}
			config.setName(entity.getName());
			config.setDescription(entity.getDescription());
			config.setVersion(entity.getVersion());
			config.setCategory(entity.getCategory());
			config.setSubcategory(entity.getSubcategory());
			config.setReportType(entity.getReportType());
			config.setReportYear(entity.getReportYear());
			config.setReportScope(entity.getReportScope());
			config.setStatus(entity.getStatus());
			
			if (entity.getDateCreated() != null) {
				config.setDateCreated(entity.getDateCreated().toString());
			}
			if (entity.getDateChanged() != null) {
				config.setDateChanged(entity.getDateChanged().toString());
			}
			
			return config;
		}
		catch (Exception e) {
			throw new org.openmrs.api.APIException("Failed to convert LegacyReport to LegacyReportConfig", e);
		}
	}
	
	/**
	 * Generate a report key from the report name for frontend compatibility
	 */
	private String generateKeyFromName(String name) {
		if (name == null || name.trim().isEmpty()) {
			return null;
		}
		// Convert to lowercase and replace spaces with underscores
		return name.trim().toLowerCase().replaceAll("\\s+", "_").replaceAll("[^a-z0-9_]", "");
	}
	
	private LegacyReport convertToEntity(LegacyReportConfig config) {
		if (config == null) {
			return null;
		}
		
		try {
			com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
			LegacyReport entity;
			
			if (config.getUuid() != null) {
				entity = dao.getLegacyReportByUuid(config.getUuid());
				if (entity == null || entity.isRetired()) {
					entity = new LegacyReport(config.getUuid());
				}
			} else {
				entity = new LegacyReport();
			}
			
			entity.setName(config.getName());
			entity.setDescription(config.getDescription());
			entity.setVersion(config.getVersion());
			entity.setCategory(config.getCategory());
			entity.setSubcategory(config.getSubcategory());
			entity.setReportType(config.getReportType());
			entity.setReportYear(config.getReportYear());
			entity.setReportScope(config.getReportScope());
			entity.setStatus(config.getStatus());
			
			String configJson = objectMapper.writeValueAsString(config);
			entity.setConfigJson(configJson);
			entity.setDateChanged(new java.util.Date());
			
			return entity;
		}
		catch (Exception e) {
			throw new org.openmrs.api.APIException("Failed to convert LegacyReportConfig to LegacyReport", e);
		}
	}
	
	// =========================================================
	// ETLMonitor CRUD methods
	// =========================================================
	
	@Override
	public ETLMonitor saveETLMonitor(ETLMonitor monitor) {
		if (monitor.getUuid() == null) {
			monitor.setUuid(UUID.randomUUID().toString());
		}
		return dao.saveETLMonitor(monitor);
	}
	
	@Override
	public ETLMonitor getETLMonitorById(Integer id) {
		return dao.getETLMonitorById(id);
	}
	
	@Override
	public ETLMonitor getETLMonitorByUuid(String uuid) {
		return dao.getETLMonitorByUuid(uuid);
	}
	
	@Override
	public ETLMonitor getETLMonitorByCode(String code) {
		return dao.getETLMonitorByCode(code);
	}
	
	@Override
	public List<ETLMonitor> getETLMonitors(String q, boolean includeRetired, Integer startIndex, Integer limit) {
		return dao.getETLMonitors(q, includeRetired, startIndex, limit);
	}
	
	@Override
	public List<ETLMonitor> getActiveETLMonitors() {
		return dao.getActiveETLMonitors();
	}
	
	@Override
	public List<ETLMonitor> getETLMonitorsByCategory(String category, boolean includeRetired) {
		return dao.getETLMonitorsByCategory(category, includeRetired);
	}
	
	@Override
	public long getETLMonitorsCount(String q, boolean includeRetired) {
		return dao.getETLMonitorsCount(q, includeRetired);
	}
	
	@Override
	public void retireETLMonitor(ETLMonitor monitor, String reason) {
		monitor.setRetired(true);
		monitor.setRetireReason(reason);
		dao.saveETLMonitor(monitor);
	}
	
	@Override
	public void unretireETLMonitor(ETLMonitor monitor) {
		monitor.setRetired(false);
		monitor.setRetireReason(null);
		dao.saveETLMonitor(monitor);
	}
	
	@Override
	public void purgeETLMonitor(ETLMonitor monitor) {
		dao.purgeETLMonitor(monitor);
	}
}
