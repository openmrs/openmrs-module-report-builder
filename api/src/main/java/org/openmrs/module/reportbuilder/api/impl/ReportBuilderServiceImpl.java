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
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.annotation.JsonInclude;
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
import org.openmrs.module.reportbuilder.web.controller.dto.*;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
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
	
	/**
	 * Maximum nesting depth for JSON serialization to handle OpenMRS entity graphs with circular
	 * references. Default Jackson limit is 1000; increased to accommodate deep object hierarchies
	 * from BaseOpenmrsMetadata inheritance chains.
	 */
	private static final int MAX_JSON_NESTING_DEPTH = 1500;
	
	private final ObjectMapper objectMapper;
	
	{
		// Configure ObjectMapper to handle OpenMRS entities and circular references
		objectMapper = new ObjectMapper();
		// Don't fail on unknown properties (for backward compatibility)
		objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		// Don't fail on self references (circular references)
		objectMapper.configure(SerializationFeature.FAIL_ON_SELF_REFERENCES, false);
		// Don't fail on empty beans
		objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
		// Exclude null values to reduce unnecessary data
		objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
		
		// Register mixins to break OpenMRS circular references
		// This prevents: creator → User.person → Person.names → PersonName.creator → User.person → ...
		// Also prevents: creator/changedBy/voidedBy fields from creating infinite loops
		objectMapper.addMixIn(org.openmrs.User.class,
		    org.openmrs.module.reportbuilder.api.export.OpenMRSJacksonMixins.UserMixin.class);
		objectMapper.addMixIn(org.openmrs.Person.class,
		    org.openmrs.module.reportbuilder.api.export.OpenMRSJacksonMixins.PersonMixin.class);
		objectMapper.addMixIn(org.openmrs.PersonName.class,
		    org.openmrs.module.reportbuilder.api.export.OpenMRSJacksonMixins.PersonNameMixin.class);
		// Apply to all report builder entities that extend BaseOpenmrsMetadata
		objectMapper.addMixIn(org.openmrs.BaseOpenmrsMetadata.class,
		    org.openmrs.module.reportbuilder.api.export.OpenMRSJacksonMixins.BaseOpenmrsMetadataMixin.class);
	}
	
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
		if (a <= 0 || s.charAt(s.length() - 1) == '_') {
			return false;
		}
		return true;
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
	@Transactional
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
	@Transactional
	public void purgeReportBuilderIndicator(ReportBuilderIndicator indicator) {
		dao.purgeReportBuilderIndicator(indicator);
	}
	
	@Override
	@Transactional
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
	@Transactional
	public void retireReportBuilderDataTheme(ReportBuilderDataTheme theme, String reason) {
		theme.setRetired(true);
		theme.setRetireReason(reason);
		dao.saveReportBuilderDataTheme(theme);
	}
	
	@Override
	@Transactional
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
	@Transactional
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
	@Transactional
	public void retireAgeCategory(ReportBuilderAgeCategory category, String reason) {
		category.setRetired(true);
		category.setRetireReason(reason);
		dao.saveAgeCategory(category);
	}
	
	@Override
	@Transactional
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
			// LineListDataSetDefinition; compile them on a dedicated path.
			if (isLinelistReport(report, reportConfig)) {
				return compileLinelistReport(report, reportConfig, reportDefinitionService);
			}
			
			// Aggregate reports are compiled on a dedicated path.
			return compileAggregateReport(report, reportConfig, reportDefinitionService);
		}
		catch (Exception e) {
			report.setLastCompiledAt(new Date());
			report.setCompileStatus(ReportBuilderReport.ReportCompileStatus.FAILED);
			saveReportBuilderReport(report);
			throw e;
		}
	}
	
	@Override
	public CompiledReportArtifacts compileAndAddToLibrary(String reportBuilderReportUuid, String categoryUuid) {
		// Resolve and apply the category offered at compile time BEFORE compiling so
		// stampCompiledIdentity can include both category name and categoryUuid in the
		// compiled design file.
		ReportBuilderReport report = getReportBuilderReportByUuid(reportBuilderReportUuid);
		if (report == null) {
			throw new IllegalArgumentException("ReportBuilderReport not found: " + reportBuilderReportUuid);
		}
		
		if (categoryUuid != null && !categoryUuid.trim().isEmpty()) {
			ReportCategory category = dao.getReportCategoryByUuid(categoryUuid);
			if (category == null) {
				throw new IllegalArgumentException("ReportCategory not found: " + categoryUuid);
			}
			if (report.getCategory() == null || !categoryUuid.equals(report.getCategory().getUuid())) {
				report.setCategory(category);
				saveReportBuilderReport(report);
			}
		}
		
		// Compile the report
		CompiledReportArtifacts compiled = compileReport(reportBuilderReportUuid);
		
		// Add to library (creates or updates ReportLibrary entry); picks up the category applied above.
		addToReportLibrary(getReportBuilderReportByUuid(reportBuilderReportUuid));
		
		return compiled;
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
	
	private CompiledReportArtifacts compileAggregateReport(ReportBuilderReport builderReport, JsonNode reportConfig,
	        ReportDefinitionService reportDefinitionService) {
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
				
				JsonNode sectionConfig = parseJson(section.getConfigJson(), "Invalid section configJson for " + sectionUuid);
				
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
		
		// Resolve the OpenMRS ReportDefinition up front so its uuid can be stamped into the
		// shipped design file (used by import to recreate/update linked entities).
		ReportDefinition reportDefinition = findOrCreateReportDefinition(builderReport, reportDefinitionService);
		
		ObjectNode compiledDefinitionRoot = objectMapper.createObjectNode();
		compiledDefinitionRoot.put("version", 1);
		compiledDefinitionRoot.put("name", builderReport.getName());
		compiledDefinitionRoot.put("code", builderReport.getCode());
		compiledDefinitionRoot.set("report_fields", compiledFields);
		
		stampCompiledIdentity(builderReport, compiledDefinitionRoot, "AGGREGATE",
		    builderReport.getCategory() != null ? builderReport.getCategory().getName() : null,
		    reportDefinition != null ? reportDefinition.getUuid() : null);
		
		String compiledDefinitionJson;
		try {
			compiledDefinitionJson = objectMapper.writerWithDefaultPrettyPrinter()
			        .writeValueAsString(compiledDefinitionRoot);
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to serialize compiled report definition JSON", e);
		}
		
		String definitionFileName = buildDefinitionFileName(builderReport);
		File definitionFile;
		try {
			definitionFile = ReportDesignFileUtil.writeJsonStringToDesignFile(definitionFileName, compiledDefinitionJson);
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
		compiledDesignRoot.put("name", builderReport.getName());
		compiledDesignRoot.put("code", builderReport.getCode());
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
		
		AggregateReportDataSetDefinition dsd = new AggregateReportDataSetDefinition();
		dsd.setName(builderReport.getName() + " Data Set");
		dsd.setDescription(builderReport.getDescription());
		// Store only the relative filename instead of absolute path for portability
		dsd.setReportDesignPath(definitionFileName);
		dsd.addParameter(new Parameter("startDate", "Start Date", Date.class));
		dsd.addParameter(new Parameter("endDate", "End Date", Date.class));
		
		reportDefinition.setName(builderReport.getName());
		reportDefinition.setDescription(builderReport.getDescription());
		reportDefinition.getParameters().clear();
		reportDefinition.addParameter(new Parameter("startDate", "Start Date", Date.class));
		reportDefinition.addParameter(new Parameter("endDate", "End Date", Date.class));
		reportDefinition.getDataSetDefinitions().clear();
		
		Map<String, Object> parameterMappings = new HashMap<String, Object>();
		parameterMappings.put("startDate", "${startDate}");
		parameterMappings.put("endDate", "${endDate}");
		
		reportDefinition.addDataSetDefinition("defaultDataSet", dsd, parameterMappings);
		reportDefinition = reportDefinitionService.saveDefinition(reportDefinition);
		
		ReportDesign jsonDesign = saveOrUpdateJsonReportDesign(reportDefinition, compiledDesignJson, builderReport);
		
		builderReport.setCompiledReportDefinitionUuid(reportDefinition.getUuid());
		builderReport.setCompiledReportDesignUuid(jsonDesign != null ? jsonDesign.getUuid() : null);
		builderReport.setLastCompiledAt(new Date());
		builderReport.setCompileStatus(ReportBuilderReport.ReportCompileStatus.COMPILED);
		builderReport = saveReportBuilderReport(builderReport);
		
		CompiledReportArtifacts out = new CompiledReportArtifacts();
		out.setReportBuilderReport(builderReport);
		out.setReportDefinition(reportDefinition);
		out.setReportDesignFile(definitionFile);
		out.setCompiledJson(compiledDefinitionJson);
		return out;
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
			
			// Resolve the OpenMRS ReportDefinition up front so its uuid can be stamped into the
			// shipped design file (used by import to recreate/update linked entities).
			ReportDefinition reportDefinition = findOrCreateReportDefinition(report, reportDefinitionService);
			
			stampCompiledIdentity(report, compiledConfig, "LINE_LIST", report.getCategory() != null ? report.getCategory()
			        .getName() : null, reportDefinition != null ? reportDefinition.getUuid() : null);
			
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
		
		// Already resolved above for identity stamping.
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
	 * Builds a file name for a linelist report design file. Stored under the canonical
	 * configuration/reports root, i.e. reports/linelist/{code}.json to avoid clashing with
	 * aggregate designs in the same directory.
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
		
		List<ReportBuilderAgeGroup> groups = resolveActiveAgeGroups(ageCategoryCode);
		
		int i;
		for (i = 0; i < groups.size(); i++) {
			ReportBuilderAgeGroup g = groups.get(i);
			
			// Item ids key the data join and must be stable under label edits; the label stays
			// display-only. Groups without a code fall back to the label.
			String idSource = hasText(g.getCode()) ? g.getCode() : g.getLabel();
			
			ObjectNode one = objectMapper.createObjectNode();
			one.put("id", sanitize(idSource));
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
		List<ReportBuilderAgeGroup> ageGroups = resolveActiveAgeGroups(ageCategoryCode);
		
		if (!ageGroups.isEmpty() && genders.isArray()) {
			int i;
			for (i = 0; i < ageGroups.size(); i++) {
				ReportBuilderAgeGroup ageGroup = ageGroups.get(i);
				// dissaggregations1 keeps the label at compile time for display/compat; the code is
				// the stable identity the evaluator matches against when labels are renamed.
				String disaggCode = hasText(ageGroup.getCode()) ? ageGroup.getCode() : ageGroup.getLabel();
				Iterator<JsonNode> genderIterator = genders.elements();
				while (genderIterator.hasNext()) {
					JsonNode g = genderIterator.next();
					String gender = g.asText("");
					
					ObjectNode one = objectMapper.createObjectNode();
					one.put("dissaggregations1", ageGroup.getLabel());
					one.put("disaggregation_code", disaggCode);
					one.put("dissaggregations2", gender);
					one.put("value_place_holder", buildDisaggregatedPlaceholder(indicatorCode, disaggCode, gender));
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
		// Relative to the canonical configuration/reports root; keeps aggregate designs grouped
		// under reports/aggregates alongside linelist designs under reports/linelist.
		return "aggregates/" + base + ".json";
	}
	
	/**
	 * Stamps cross-instance identity onto a compiled design file so importing it can recreate or
	 * update the ReportBuilderReport, its compiled ReportDefinition and the linked ReportLibrary.
	 * Canonical reportType value is AGGREGATE | LINE_LIST. reportLibraryUuid reflects an existing
	 * library entry when one already exists for this report on this instance.
	 */
	private void stampCompiledIdentity(ReportBuilderReport report, ObjectNode target, String canonicalReportType,
	        String categoryName, String reportDefinitionUuid) {
		target.put("name", report.getName());
		if (categoryName != null && !categoryName.trim().isEmpty()) {
			target.put("category", categoryName);
		}
		if (report.getCategory() != null && report.getCategory().getUuid() != null) {
			target.put("categoryUuid", report.getCategory().getUuid());
		}
		target.put("reportType", canonicalReportType);
		if (report.getUuid() != null && !report.getUuid().trim().isEmpty()) {
			target.put("reportBuilderReportUuid", report.getUuid());
		}
		if (reportDefinitionUuid != null && !reportDefinitionUuid.trim().isEmpty()) {
			target.put("reportDefinitionUuid", reportDefinitionUuid);
		}
		try {
			ReportLibrary library = dao.getReportLibraryByBuilderReportUuid(report.getUuid());
			if (library != null && library.getUuid() != null) {
				target.put("reportLibraryUuid", library.getUuid());
			}
		}
		catch (Exception e) {
			log.debug("Could not resolve ReportLibrary while stamping identity for {}", report.getName(), e);
		}
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
	
	private List<ReportBuilderAgeGroup> resolveActiveAgeGroups(String ageCategoryCode) {
		if (!hasText(ageCategoryCode)) {
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
		
		List<ReportBuilderAgeGroup> out = new ArrayList<ReportBuilderAgeGroup>();
		int i;
		for (i = 0; i < groups.size(); i++) {
			ReportBuilderAgeGroup g = groups.get(i);
			if (g != null && Boolean.TRUE.equals(g.getActive()) && g.getLabel() != null && !g.getLabel().trim().isEmpty()) {
				out.add(g);
			}
		}
		
		return out;
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
	@Transactional
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
	 * Creates or updates the ReportLibrary entry linked to a builder report.
	 */
	@Override
	public void saveOrUpdateLibraryEntry(String reportBuilderReportUuid) {
		if (reportBuilderReportUuid == null || reportBuilderReportUuid.trim().isEmpty()) {
			return;
		}
		try {
			ReportBuilderReport report = dao.getReportBuilderReportByUuid(reportBuilderReportUuid);
			if (report == null) {
				log.warn("saveOrUpdateLibraryEntry: no builder report found for uuid {}", reportBuilderReportUuid);
				return;
			}
			addToReportLibrary(report);
		}
		catch (Exception e) {
			log.error("Failed to sync library entry for builder report {}", reportBuilderReportUuid, e);
		}
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
				existingEntry.setReportDefinitionUuid(report.getCompiledReportDefinitionUuid());
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
				libraryEntry.setReportDefinitionUuid(report.getCompiledReportDefinitionUuid());
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
	@Transactional
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
	@Transactional
	public void retireETLMonitor(ETLMonitor monitor, String reason) {
		monitor.setRetired(true);
		monitor.setRetireReason(reason);
		dao.saveETLMonitor(monitor);
	}
	
	@Override
	@Transactional
	public void unretireETLMonitor(ETLMonitor monitor) {
		monitor.setRetired(false);
		monitor.setRetireReason(null);
		dao.saveETLMonitor(monitor);
	}
	
	@Override
	@Transactional
	public void purgeETLMonitor(ETLMonitor monitor) {
		dao.purgeETLMonitor(monitor);
	}
	
	// =========================================================
	// ReportBuilderDashboard CRUD methods
	// =========================================================
	
	@Override
	@Transactional
	public ReportBuilderDashboard saveReportBuilderDashboard(ReportBuilderDashboard dashboard) {
		if (dashboard.getName() == null || dashboard.getName().trim().isEmpty()) {
			throw new APIException("Dashboard name is required");
		}
		
		// Null-defaults
		if (dashboard.getDashboardType() == null) {
			dashboard.setDashboardType(ReportBuilderDashboard.DashboardType.CUSTOM);
		}
		if (dashboard.getActive() == null) {
			dashboard.setActive(true);
		}
		if (dashboard.getSortOrder() == null) {
			dashboard.setSortOrder(0);
		}
		if (dashboard.getUuid() == null) {
			dashboard.setUuid(UUID.randomUUID().toString());
		}
		
		// Code uniqueness is service-level only; there is no DB unique constraint on code
		String code = dashboard.getCode() == null ? null : dashboard.getCode().trim();
		dashboard.setCode(code);
		if (code != null && !code.isEmpty()) {
			ReportBuilderDashboard existing = dao.getDashboardByCode(code);
			if (existing != null && !existing.getUuid().equals(dashboard.getUuid())) {
				throw new APIException("Dashboard code '" + code + "' is already in use by dashboard '" + existing.getName()
				        + "'");
			}
		}
		
		// configJson is stored opaquely; only its JSON validity is enforced, never its schema
		String configJson = dashboard.getConfigJson();
		if (configJson != null && !configJson.trim().isEmpty()) {
			try {
				objectMapper.readTree(configJson);
			}
			catch (Exception e) {
				throw new APIException("configJson is not valid JSON: " + e.getMessage());
			}
		}
		
		return dao.saveReportBuilderDashboard(dashboard);
	}
	
	@Override
	public ReportBuilderDashboard getReportBuilderDashboardById(Integer id) {
		return dao.getDashboardById(id);
	}
	
	@Override
	public ReportBuilderDashboard getReportBuilderDashboardByUuid(String uuid) {
		return dao.getDashboardByUuid(uuid);
	}
	
	@Override
	public ReportBuilderDashboard getReportBuilderDashboardByCode(String code) {
		return dao.getDashboardByCode(code);
	}
	
	@Override
	public List<ReportBuilderDashboard> getReportBuilderDashboards(String q, boolean includeRetired, Integer startIndex,
	        Integer limit) {
		return dao.getDashboards(q, includeRetired, startIndex, limit);
	}
	
	@Override
	public List<ReportBuilderDashboard> getActiveReportBuilderDashboards() {
		return dao.getActiveDashboards();
	}
	
	@Override
	public List<ReportBuilderDashboard> getReportBuilderDashboardsByType(String dashboardType, boolean includeRetired) {
		return dao.getDashboardsByType(dashboardType, includeRetired);
	}
	
	@Override
	public long getReportBuilderDashboardsCount(String q, boolean includeRetired) {
		return dao.getDashboardsCount(q, includeRetired);
	}
	
	@Override
	@Transactional
	public void retireReportBuilderDashboard(ReportBuilderDashboard dashboard, String reason) {
		dashboard.setRetired(true);
		dashboard.setRetiredBy(Context.getAuthenticatedUser());
		dashboard.setDateRetired(new Date());
		dashboard.setRetireReason(reason);
		dao.saveReportBuilderDashboard(dashboard);
	}
	
	@Override
	@Transactional
	public void unretireReportBuilderDashboard(ReportBuilderDashboard dashboard) {
		dashboard.setRetired(false);
		dashboard.setRetiredBy(null);
		dashboard.setDateRetired(null);
		dashboard.setRetireReason(null);
		dao.saveReportBuilderDashboard(dashboard);
	}
	
	@Override
	@Transactional
	public void purgeReportBuilderDashboard(ReportBuilderDashboard dashboard) {
		dao.purgeReportBuilderDashboard(dashboard);
	}
	
	// ========== Report Shipping Method Implementations ==========
	
	/**
	 * Import order respecting dependencies - foundation entities first
	 */
	private static final java.util.List<String> DEPENDENCY_ORDER = java.util.Arrays.asList("categories", "age-categories",
	    "age-groups", "etl-sources", "etl-monitors", "indicators", "sections", "themes", "reports", "dashboards", "library");
	
	@Override
	public ShippingResult shipReport(String reportUuid, String version, File destination) {
		ShippingResult result = new ShippingResult();
		
		try {
			log.info("Shipping report: {} version: {} to: {}", reportUuid, version, destination.getAbsolutePath());
			
			// Validate report exists
			ReportBuilderReport report = getReportBuilderReportByUuid(reportUuid);
			if (report == null) {
				result.setSuccess(false);
				result.setErrorMessage("Report not found with UUID: " + reportUuid);
				return result;
			}
			
			// Create directory structure
			createShippingDirectories(destination);
			
			// Set basic result info
			result.setReportCode(report.getCode() != null ? report.getCode() : report.getUuid());
			result.setVersion(version);
			
			// Collect and export dependencies
			exportShippingDependencies(report, destination, result);
			
			// Export the report source definition
			File sourceFile = exportReportSource(report, destination);
			result.setSourceFile(sourceFile.getAbsolutePath());
			
			// Compile and export the report configuration
			com.fasterxml.jackson.databind.node.ObjectNode compiledConfig = compileReportConfiguration(report);
			File compiledFile = exportCompiledReport(report, compiledConfig, destination);
			result.setCompiledFile(compiledFile.getAbsolutePath());
			
			// Generate version metadata
			File versionFile = generateVersionMetadata(report, version, result, destination);
			result.setVersionFile(versionFile.getAbsolutePath());
			
			result.setSuccess(true);
			log.info("Successfully shipped report: {}", report.getName());
			
		}
		catch (Exception e) {
			log.error("Failed to ship report: {}", reportUuid, e);
			result.setSuccess(false);
			result.setErrorMessage(e.getMessage());
		}
		
		return result;
	}
	
	@Override
	public ShippingResult shipBatch(java.util.List<String> reportUuids,
	        String version, File destination) {
		ShippingResult result = new ShippingResult();
		java.util.Set<String> combinedDependencies = new java.util.HashSet<>();

		try {
			log.info("Shipping batch of {} reports version: {} to: {}", reportUuids.size(), version,
			    destination.getAbsolutePath());

			// Create directory structure
			createShippingDirectories(destination);

			// Process each report and collect dependencies
			for (String reportUuid : reportUuids) {
				ReportBuilderReport report = getReportBuilderReportByUuid(reportUuid);
				if (report == null) {
					log.warn("Report not found with UUID: {}, skipping", reportUuid);
					continue;
				}

				// Export report source
				exportReportSource(report, destination);

				// Compile and export
				com.fasterxml.jackson.databind.node.ObjectNode compiledConfig = compileReportConfiguration(report);
				exportCompiledReport(report, compiledConfig, destination);

				// Dependencies would be accumulated here
				log.debug("Processed report: {}", report.getName());
			}

			// Generate combined version metadata
			// (Simplified - would need full implementation for batch shipping)
			result.setSuccess(true);
			result.setVersion(version);

		}
		catch (Exception e) {
			log.error("Failed to ship batch reports", e);
			result.setSuccess(false);
			result.setErrorMessage(e.getMessage());
		}

		return result;
	}
	
	@Override
	public File exportEntity(String entityType, String entityUuid, File destination) {
		try {
			switch (entityType.toLowerCase()) {
				case "category":
					ReportCategory category = getReportCategoryByUuid(entityUuid);
					if (category != null) {
						return exportCategory(category, destination);
					}
					break;
				case "indicator":
					ReportBuilderIndicator indicator = getReportBuilderIndicatorByUuid(entityUuid);
					if (indicator != null) {
						return exportIndicator(indicator, destination);
					}
					break;
				case "section":
					ReportBuilderSection section = getReportBuilderSectionByUuid(entityUuid);
					if (section != null) {
						return exportSection(section, destination);
					}
					break;
				case "theme":
					ReportBuilderDataTheme theme = getReportBuilderDataThemeByUuid(entityUuid);
					if (theme != null) {
						return exportTheme(theme, destination);
					}
					break;
				case "age-category":
					ReportBuilderAgeCategory ageCategory = getAgeCategoryByUuid(entityUuid);
					if (ageCategory != null) {
						return exportAgeCategory(ageCategory, destination);
					}
					break;
				case "age-group":
					// Age groups use ID instead of UUID
					try {
						Integer ageGroupId = Integer.parseInt(entityUuid);
						ReportBuilderAgeGroup ageGroup = getAgeGroupById(ageGroupId);
						if (ageGroup != null) {
							return exportAgeGroup(ageGroup, destination);
						}
					}
					catch (NumberFormatException e) {
						log.warn("Invalid age group ID: {}", entityUuid);
					}
					break;
				case "etl-source":
					ETLSource etlSource = getETLSourceByUuid(entityUuid);
					if (etlSource != null) {
						return exportETLSource(etlSource, destination);
					}
					break;
				case "etl-monitor":
					ETLMonitor etlMonitor = getETLMonitorByUuid(entityUuid);
					if (etlMonitor != null) {
						return exportETLMonitor(etlMonitor, destination);
					}
					break;
				case "dashboard":
					ReportBuilderDashboard dashboard = getReportBuilderDashboardByUuid(entityUuid);
					if (dashboard != null) {
						return exportDashboard(dashboard, destination);
					}
					break;
				case "library":
					ReportLibrary library = getReportLibraryByUuid(entityUuid);
					if (library != null) {
						return exportLibrary(library, destination);
					}
					break;
				default:
					throw new IllegalArgumentException("Unknown entity type: " + entityType);
			}
			throw new IllegalArgumentException("Entity not found with UUID: " + entityUuid);
			
		}
		catch (Exception e) {
			log.error("Failed to export entity: {} with UUID: {}", entityType, entityUuid, e);
			throw new APIException("Failed to export entity", e);
		}
	}
	
	@Override
	public File getDefaultShippingDirectory() {
		String openmrsData = System.getProperty("OPENMRS_APPLICATION_DATA_DIRECTORY");
		if (openmrsData == null) {
			openmrsData = System.getProperty("OPENMRS_HOME");
		}
		if (openmrsData == null) {
			openmrsData = ".";
		}
		// Return the parent directory of configuration - createDirectories will add "configuration" prefix
		return new File(openmrsData);
	}
	
	@Override
	public File getDefaultImportDirectory() {
		// Import and export use the same default directory
		return getDefaultShippingDirectory();
	}
	
	/**
	 * Entity types the bulk export owns, in the order used by {@link #shipAllReports(String, File)}
	 * Also the whitelist of directories {@link #cleanExportDirectories(File, java.util.List)} may
	 * clear.
	 */
	private static final List<String> EXPORTABLE_ENTITY_TYPES = java.util.Arrays.asList("reports", "categories", "library",
	    "indicators", "sections", "themes", "age-categories", "age-groups", "etl-sources", "etl-monitors", "dashboards");
	
	@Override
	@Transactional(readOnly = true)
	public ShippingResult shipAllReports(String version, File destination) {
		log.info("Starting export of all ReportBuilder artifacts and reports, version: {}", version);

		// Export all artifacts including reports, indicators, themes, sections, categories, etc.
		return shipAllEntities(new ArrayList<>(EXPORTABLE_ENTITY_TYPES), version, destination);
	}
	
	@Override
	@Transactional(readOnly = true)
	public ShippingResult shipAllEntities(
	        java.util.List<String> entityTypes, String version, File destination) {
		log.info("Starting bulk export of entity types: {}, version: {} to {}", entityTypes, version,
		    destination.getAbsolutePath());

		ShippingResult result = new ShippingResult();
		result.setVersion(version);
		result.setSuccess(true);
		List<String> exportFailures = new ArrayList<>();

		try {
			CURRENT_EXPORT_FILENAMES.set(new java.util.HashSet<String>());

			// Create proper directory structure: configuration/reportbuilder/ and configuration/reports/
			createShippingDirectories(destination);
			log.info("Created directory structure at: {}", new File(destination, "configuration").getAbsolutePath());

			// Remove stale json from the entity directories this run owns, so repeated exports into
			// the same destination produce a clean snapshot of the source rather than an accumulation
			// across runs
			cleanExportDirectories(destination, entityTypes);

			// Export each entity type
			for (String entityType : entityTypes) {
				log.debug("Exporting entity type: {}", entityType);

				switch (entityType.toLowerCase()) {
					case "reports":
						List<ReportBuilderReport> reports = getReportBuilderReports(null, false, null, null);
						for (ReportBuilderReport report : reports) {
							try {
								File reportFile = exportReportSource(report, destination);
								log.debug("Exported report: {} to {}", report.getName(), reportFile.getName());
							}
							catch (Exception e) {
								recordExportFailure(result, exportFailures, "report " + report.getName(), e);
							}
						}
						break;

					case "categories":
						List<ReportCategory> categories = getReportCategories(null, false, null, null);
						for (ReportCategory cat : categories) {
							try {
								File categoryFile = exportEntity("category", cat.getUuid(), destination);
								log.debug("Exported category: {} to {}", cat.getName(), categoryFile.getName());
							}
							catch (Exception e) {
								recordExportFailure(result, exportFailures, "category " + cat.getName(), e);
							}
						}
						break;

					case "themes":
						List<ReportBuilderDataTheme> themes = getReportBuilderDataThemes(null, false, null, null);
						for (ReportBuilderDataTheme theme : themes) {
							try {
								File themeFile = exportEntity("theme", theme.getUuid(), destination);
								log.debug("Exported theme: {} to {}", theme.getName(), themeFile.getName());
							}
							catch (Exception e) {
								recordExportFailure(result, exportFailures, "theme " + theme.getName(), e);
							}
						}
						break;

					case "indicators":
						// Get all indicators by fetching all kinds
						List<ReportBuilderIndicator> allIndicators = new ArrayList<>();
						for (ReportBuilderIndicator.Kind kind : ReportBuilderIndicator.Kind.values()) {
							List<ReportBuilderIndicator> kindIndicators = getReportBuilderIndicators(kind, false, null, null);
							if (kindIndicators != null) {
								allIndicators.addAll(kindIndicators);
							}
						}
						for (ReportBuilderIndicator ind : allIndicators) {
							try {
								File indicatorFile = exportEntity("indicator", ind.getUuid(), destination);
								log.debug("Exported indicator: {} to {}", ind.getName(), indicatorFile.getName());
							}
							catch (Exception e) {
								recordExportFailure(result, exportFailures, "indicator " + ind.getName(), e);
							}
						}
						break;

					case "sections":
						List<ReportBuilderSection> sections = getReportBuilderSections(null, false, null, null);
						for (ReportBuilderSection section : sections) {
							try {
								File sectionFile = exportEntity("section", section.getUuid(), destination);
								log.debug("Exported section: {} to {}", section.getName(), sectionFile.getName());
							}
							catch (Exception e) {
								recordExportFailure(result, exportFailures, "section " + section.getName(), e);
							}
						}
						break;

					case "age-categories":
						List<ReportBuilderAgeCategory> ageCategories = getAgeCategories(null, false, null, null, null);
						for (ReportBuilderAgeCategory ageCategory : ageCategories) {
							try {
								File ageCatFile = exportEntity("age-category", ageCategory.getUuid(), destination);
								log.debug("Exported age category: {} to {}", ageCategory.getName(), ageCatFile.getName());
							}
							catch (Exception e) {
								recordExportFailure(result, exportFailures, "age category " + ageCategory.getName(), e);
							}
						}
						break;

					case "age-groups":
						// Age groups belong to their age category and are embedded in its export file -
						// no separate age-group files are written. The directory is still cleared by
						// cleanExportDirectories so legacy separate files do not linger in reused
						// destinations.
						log.debug("Age groups are exported embedded in their age categories");
						break;

					case "dashboards":
						List<ReportBuilderDashboard> dashboards = getReportBuilderDashboards(null, false, null, null);
						for (ReportBuilderDashboard dashboard : dashboards) {
							try {
								File dashboardFile = exportEntity("dashboard", dashboard.getUuid(), destination);
								log.debug("Exported dashboard: {} to {}", dashboard.getName(), dashboardFile.getName());
							}
							catch (Exception e) {
								recordExportFailure(result, exportFailures, "dashboard " + dashboard.getName(), e);
							}
						}
						break;

					case "library":
						List<ReportLibrary> libraries = getReportLibraries(null, false, null, null);
						for (ReportLibrary library : libraries) {
							try {
								File libraryFile = exportEntity("library", library.getUuid(), destination);
								log.debug("Exported library: {} to {}", library.getName(), libraryFile.getName());
							}
							catch (Exception e) {
								recordExportFailure(result, exportFailures, "library " + library.getName(), e);
							}
						}
						break;

					case "etl-sources":
						List<ETLSource> etlSources = getAllETLSources(false);
						for (ETLSource source : etlSources) {
							try {
								File sourceFile = exportEntity("etl-source", source.getUuid(), destination);
								log.debug("Exported ETL source: {} to {}", source.getName(), sourceFile.getName());
							}
							catch (Exception e) {
								recordExportFailure(result, exportFailures, "ETL source " + source.getName(), e);
							}
						}
						break;

					case "etl-monitors":
						List<ETLMonitor> etlMonitors = getETLMonitors(null, false, null, null);
						for (ETLMonitor monitor : etlMonitors) {
							try {
								File monitorFile = exportEntity("etl-monitor", monitor.getUuid(), destination);
								log.debug("Exported ETL monitor: {} to {}", monitor.getName(), monitorFile.getName());
							}
							catch (Exception e) {
								recordExportFailure(result, exportFailures, "ETL monitor " + monitor.getName(), e);
							}
						}
						break;

					default:
						log.warn("Unknown entity type: {}", entityType);
						break;
				}
			}

			// Create version file in reportbuilder directory
			File reportbuilderDir = new File(destination, "configuration" + File.separator + "reportbuilder");
			createShippingVersionFile(reportbuilderDir, version);

			if (!exportFailures.isEmpty()) {
				result.setSuccess(false);
				result.setErrorMessage("Failed to export " + exportFailures.size() + " entities. First failure: "
				        + exportFailures.get(0));
			}

			result.setReportCode("BULK_EXPORT");
			result.setSourceFile(reportbuilderDir.getAbsolutePath());
			result.setVersionFile(new File(reportbuilderDir, "version.json").getAbsolutePath());

			log.info("Bulk export completed successfully to: {}", reportbuilderDir.getAbsolutePath());

		}
		catch (Exception e) {
			log.error("Failed to complete bulk export", e);
			result.setSuccess(false);
			result.setErrorMessage("Failed to complete bulk export: " + e.getMessage());
		}
		finally {
			CURRENT_EXPORT_FILENAMES.remove();
		}

		return result;
	}
	
	@Override
	public File exportCompiledReport(String reportUuid, File destination) {
		try {
			log.info("Exporting compiled report: {}", reportUuid);
			
			// Get the report
			ReportBuilderReport report = dao.getReportBuilderReportByUuid(reportUuid);
			if (report == null) {
				throw new APIException("Report not found: " + reportUuid);
			}
			
			// Compile the report configuration
			com.fasterxml.jackson.databind.node.ObjectNode compiledConfig = compileReportConfiguration(report);
			
			// Export the compiled report using the private method
			File exportedFile = exportCompiledReport(report, compiledConfig, destination);
			
			log.info("Successfully exported compiled report: {} to {}", report.getName(), exportedFile.getAbsolutePath());
			return exportedFile;
			
		}
		catch (Exception e) {
			log.error("Failed to export compiled report: {}", reportUuid, e);
			throw new APIException("Failed to export compiled report: " + e.getMessage(), e);
		}
	}
	
	// ========== Report Import Method Implementations ==========
	
	/**
	 * Import order respecting dependencies - foundation entities first
	 */
	private static final java.util.List<String> IMPORT_ORDER = java.util.Arrays.asList("categories", "age-categories",
	    "age-groups", "etl-sources", "etl-monitors", "indicators", "sections", "themes", "reports", "dashboards", "library");
	
	/**
	 * Imports a distribution package directory. Each entity file is imported in its own independent
	 * transaction (REQUIRES_NEW), so a failure rolls back and is reported for that item only while
	 * every other item still commits. Runs with NOT_SUPPORTED so a caller's transaction, if any, is
	 * suspended for the duration: imported items are committed independently and remain persisted
	 * even if the caller's transaction later rolls back.
	 */
	@Override
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public ImportResult importFromDirectory(File sourceDir) {
		ImportResult result = new ImportResult();
		result.setSummary("Import from directory: " + sourceDir.getAbsolutePath());
		TransactionTemplate template = newImportTransactionTemplate();
		
		try {
			log.info("Starting import from directory: {}", sourceDir.getAbsolutePath());
			
			// Validate directory structure - look for configuration/reportbuilder
			File configDir = new File(sourceDir, "configuration");
			File reportbuilderDir = new File(configDir, "reportbuilder");
			if (!reportbuilderDir.exists()) {
				result.setSuccess(false);
				result.setSummary("Invalid distribution package: missing configuration/reportbuilder directory");
				return result;
			}
			
			// Read version manifest if available
			VersionMetadata versionManifest = readVersionManifest(reportbuilderDir);
			if (versionManifest != null && versionManifest.getPackageInfo() != null) {
				log.info("Found version manifest: {} version {}", versionManifest.getPackageInfo().getName(),
				    versionManifest.getPackageInfo().getVersion());
				// Add package info to summary
				result.setSummary(String.format("Importing package: %s v%s from %s", versionManifest.getPackageInfo()
				        .getName(), versionManifest.getPackageInfo().getVersion(), sourceDir.getAbsolutePath()));
			}
			
			// Import in dependency order
			for (String type : IMPORT_ORDER) {
				File typeDir = new File(reportbuilderDir, type);
				if (typeDir.exists() && typeDir.isDirectory()) {
					importType(type, typeDir, result, template);
				}
			}
			
			// Import compiled reports from configuration/reports
			File reportsDir = new File(configDir, "reports");
			if (reportsDir.exists()) {
				importCompiledReports(reportsDir, result);
			}
			
			// Update summary
			int successCount = result.getSuccessCount();
			int errorCount = result.getErrorCount();
			String packageInfo = versionManifest != null && versionManifest.getPackageInfo() != null ? String.format(
			    " (%s v%s)", versionManifest.getPackageInfo().getName(), versionManifest.getPackageInfo().getVersion()) : "";
			result.setSummary(String.format("Import complete%s: %d succeeded, %d failed", packageInfo, successCount,
			    errorCount));
			result.setSuccess(errorCount == 0);
			
			log.info("Import complete: {} succeeded, {} failed", successCount, errorCount);
			
		}
		catch (Exception e) {
			log.error("Failed to import from directory: {}", sourceDir.getAbsolutePath(), e);
			result.setSuccess(false);
			result.setSummary("Import failed: " + e.getMessage());
		}
		
		return result;
	}
	
	/**
	 * Imports a single entity file in its own independent transaction (REQUIRES_NEW). On failure
	 * the item is rolled back and reported in the returned result instead of propagating to the
	 * caller.
	 */
	@Override
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public ImportResult importEntity(String entityType, File file) {
		ImportResult result = new ImportResult();

		try {
			String filename = file.getName();
			log.info("Importing {} from file: {}", entityType, filename);

			Boolean imported = newImportTransactionTemplate().execute(status -> {
				try {
					switch (entityType.toLowerCase()) {
						case "category":
							importCategory(file);
							break;
						case "age-category":
							importAgeCategory(file);
							break;
						case "age-group":
							importAgeGroup(file);
							break;
						case "etl-source":
							importETLSource(file);
							break;
						case "etl-monitor":
							importETLMonitor(file);
							break;
						case "indicator":
							importIndicator(file);
							break;
						case "section":
							importSection(file);
							break;
						case "theme":
							importTheme(file);
							break;
						case "report":
							importReport(file);
							break;
						case "dashboard":
							importDashboard(file);
							break;
						case "library":
							importLibraryEntry(file);
							break;
						default:
							result.addError(entityType, filename, "Unknown entity type: " + entityType);
							return false;
					}
					return true;
				}
				catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			});

			if (Boolean.TRUE.equals(imported)) {
				result.addSuccess(entityType, filename);
			}
			result.setSummary("Imported 1 entity");

		}
		catch (Exception e) {
			log.error("Failed to import entity from: {}", file.getName(), e);
			result.addError(entityType, file.getName(), describeFailure(e));
			result.setSuccess(false);
			result.setSummary("Import failed: " + describeFailure(e));
			clearSessionAfterFailure();
		}

		return result;
	}
	
	@Override
	public boolean validatePackage(File sourceDir) {
		try {
			// Look for configuration/reportbuilder directory
			File configDir = new File(sourceDir, "configuration");
			File reportbuilderDir = new File(configDir, "reportbuilder");
			if (!reportbuilderDir.exists() || !reportbuilderDir.isDirectory()) {
				log.warn("Invalid package: missing configuration/reportbuilder directory");
				return false;
			}
			
			// Check for at least one entity directory
			boolean hasEntities = false;
			for (String type : IMPORT_ORDER) {
				File typeDir = new File(reportbuilderDir, type);
				if (typeDir.exists() && typeDir.isDirectory() && typeDir.list().length > 0) {
					hasEntities = true;
					break;
				}
			}
			
			if (!hasEntities) {
				log.warn("Invalid package: no entities found");
				return false;
			}
			
			// Check for version file in reportbuilder directory
			File versionFile = new File(reportbuilderDir, "version.json");
			if (!versionFile.exists()) {
				log.warn("Warning: missing version.json file");
			}
			
			return true;
			
		}
		catch (Exception e) {
			log.error("Failed to validate package", e);
			return false;
		}
	}
	
	@Override
	public java.util.List<String> getImportOrder() {
		return new ArrayList<>(IMPORT_ORDER);
	}
	
	// ========================================================================
	// Private helper methods for Shipping
	// ========================================================================
	
	/**
	 * Create the required directory structure for shipping Creates:
	 * {destination}/configuration/reportbuilder/ and {destination}/configuration/reports/
	 */
	/**
	 * Removes stale export json files from the directories this run writes, so repeated exports
	 * into the same destination never leave behind entities that no longer exist at the source.
	 * Only the whitelisted entity-type directories are cleared; other content (e.g. report_designs,
	 * which the export does not own) is left untouched.
	 */
	private void cleanExportDirectories(File destination, java.util.List<String> entityTypes) {
		// Age groups are no longer exported as separate files (they are embedded in their age
		// category), so their directory is cleared on every bulk export regardless of the requested
		// types, removing files left by older module versions
		java.util.LinkedHashSet<String> typesToClean = new java.util.LinkedHashSet<String>(entityTypes);
		typesToClean.add("age-groups");
		for (String entityType : typesToClean) {
			if (!EXPORTABLE_ENTITY_TYPES.contains(entityType)) {
				continue;
			}
			File dir = new File(destination, "configuration" + File.separator + "reportbuilder" + File.separator
			        + entityType);
			File[] staleFiles = dir.listFiles((d, name) -> name.endsWith(".json"));
			if (staleFiles == null) {
				continue;
			}
			for (File staleFile : staleFiles) {
				if (!staleFile.delete()) {
					log.warn("Could not remove stale export file: {}", staleFile.getAbsolutePath());
				}
			}
		}
	}
	
	/**
	 * Records a per-item export failure so {@link ShippingResult} reflects partial failures instead
	 * of reporting success while files were skipped.
	 */
	private void recordExportFailure(ShippingResult result, List<String> exportFailures, String entity, Exception e) {
		log.error("Failed to export {}", entity, e);
		exportFailures.add(entity + ": " + e.getMessage());
	}
	
	private void createShippingDirectories(File destination) {
		// Create the configuration directory structure
		File configDir = new File(destination, "configuration");
		File reportbuilderDir = new File(configDir, "reportbuilder");
		reportbuilderDir.mkdirs();
		
		// Create reportbuilder source definition subdirectories
		new File(reportbuilderDir, "reports").mkdirs();
		new File(reportbuilderDir, "categories").mkdirs();
		new File(reportbuilderDir, "indicators").mkdirs();
		new File(reportbuilderDir, "sections").mkdirs();
		new File(reportbuilderDir, "themes").mkdirs();
		new File(reportbuilderDir, "age-categories").mkdirs();
		new File(reportbuilderDir, "age-groups").mkdirs();
		new File(reportbuilderDir, "etl-sources").mkdirs();
		new File(reportbuilderDir, "etl-monitors").mkdirs();
		new File(reportbuilderDir, "dashboards").mkdirs();
		new File(reportbuilderDir, "library").mkdirs();
		
		// Create compiled reports directories
		File reportsDir = new File(configDir, "reports");
		new File(reportsDir, "aggregates").mkdirs();
		new File(reportsDir, "linelist").mkdirs();
		
		log.info("Created directory structure at: {}", configDir.getAbsolutePath());
	}
	
	/**
	 * Export all dependencies for a report
	 */
	private void exportShippingDependencies(ReportBuilderReport report, File destination, ShippingResult result) {
		// Export category if present
		if (report.getCategory() != null) {
			File file = exportCategory(report.getCategory(), destination);
			result.getDependencies().addCategory(file.getName());
		}
		
		// Additional dependencies would be extracted from configJson
		// and exported here (indicators, sections, themes, etc.)
	}
	
	/**
	 * Export the report source definition
	 */
	private File exportReportSource(ReportBuilderReport report, File destination) {
		try {
			// Serialize the report directly in database model format
			String filename = getFileNameForExport(report.getCode(), report.getUuid());
			File file = new File(destination, "configuration" + File.separator + "reportbuilder" + File.separator
			        + "reports" + File.separator + filename);
			
			// Ensure parent directory exists
			File parentDir = file.getParentFile();
			if (parentDir != null && !parentDir.exists()) {
				parentDir.mkdirs();
			}
			
			objectMapper.writeValue(file, report);
			log.debug("Exported report source: {}", file.getAbsolutePath());
			return file;
			
		}
		catch (IOException e) {
			throw new APIException("Failed to export report source", e);
		}
	}
	
	/**
	 * Compile the report configuration
	 */
	private com.fasterxml.jackson.databind.node.ObjectNode compileReportConfiguration(ReportBuilderReport report) {
		try {
			// Use existing compilation logic
			// This would call the existing compileReportDefinition method
			com.fasterxml.jackson.databind.node.ObjectNode config = objectMapper.createObjectNode();
			config.put("uuid", report.getUuid());
			config.put("name", report.getName());
			config.put("code", report.getCode());
			// Additional compilation would happen here
			return config;
		}
		catch (Exception e) {
			log.warn("Failed to fully compile report configuration, using basic config", e);
			return objectMapper.createObjectNode();
		}
	}
	
	@Override
	public CompiledReportArtifacts importSerializedReportFromObject(SerializedReport serializedReport, String categoryUuid) {
		log.info("Importing serialized report: {}", serializedReport.getName());
		
		try {
			// Validate the serialized report
			ReportValidationResult validationResult = validateSerializedReport(serializedReport);
			if (validationResult.hasErrors()) {
				throw new APIException("Cannot import report - validation failed: " + validationResult.getErrors());
			}
			if (validationResult.hasWarnings()) {
				log.warn("Importing report with warnings: {}", validationResult.getWarnings());
			}
			
			// Create or update the report entity
			ReportBuilderReport report = createOrUpdateReportFromSerialized(serializedReport, categoryUuid);
			
			// Compile the report to generate the report definition
			ReportDefinition reportDefinition = compileReportForImport(report, serializedReport.getConfig());
			
			// Create the artifacts object
			CompiledReportArtifacts artifacts = new CompiledReportArtifacts();
			artifacts.setReportBuilderReport(report);
			artifacts.setReportDefinition(reportDefinition);
			artifacts.setCompiledJson(objectMapper.writeValueAsString(serializedReport.getConfig()));
			
			log.info("Successfully imported serialized report: {}", serializedReport.getName());
			return artifacts;
			
		}
		catch (Exception e) {
			log.error("Failed to import serialized report: {}", serializedReport.getName(), e);
			throw new APIException("Failed to import serialized report: " + e.getMessage(), e);
		}
	}
	
	@Override
	public CompiledReportArtifacts importSerializedReport(java.io.File reportFile, String categoryUuid) {
		log.info("Importing serialized report from file: {}", reportFile.getAbsolutePath());
		
		try {
			if (!reportFile.exists() || !reportFile.isFile()) {
				throw new APIException("Report file does not exist or is not a file: " + reportFile.getAbsolutePath());
			}
			
			// Deserialize the report file
			SerializedReport serializedReport = objectMapper.readValue(reportFile, SerializedReport.class);
			
			// Import library dependencies first
			importLibraryDependenciesFromFile(serializedReport, reportFile);
			
			// Import using the object method
			return importSerializedReportFromObject(serializedReport, categoryUuid);
			
		}
		catch (Exception e) {
			log.error("Failed to import serialized report from file: {}", reportFile.getAbsolutePath(), e);
			throw new APIException("Failed to import serialized report from file: " + e.getMessage(), e);
		}
	}
	
	/**
	 * Validate a serialized report before import
	 */
	private ReportValidationResult validateSerializedReport(SerializedReport serializedReport) {
		ReportValidationResult result = new ReportValidationResult();
		
		if (serializedReport == null) {
			result.addError("Serialized report cannot be null");
			return result;
		}
		
		// Validate basic fields
		if (serializedReport.getName() == null || serializedReport.getName().trim().isEmpty()) {
			result.addError("Report name is required");
		}
		
		if (serializedReport.getCode() == null || serializedReport.getCode().trim().isEmpty()) {
			result.addError("Report code is required");
		}
		
		if (serializedReport.getConfig() == null) {
			result.addError("Report configuration is required");
		}
		
		// Validate dependencies
		if (serializedReport.getDependencies() != null) {
			validateDependencies(serializedReport.getDependencies(), result);
		} else {
			result.addWarning("No dependencies specified - report may not function correctly");
		}
		
		result.setValid(!result.hasErrors());
		return result;
	}
	
	/**
	 * Validate dependencies referenced by a serialized report
	 */
	private void validateDependencies(SerializedReport.Dependencies dependencies, ReportValidationResult result) {
		if (dependencies == null) {
			result.addWarning("Dependencies object is null");
			return;
		}
		
		// Validate indicators
		if (dependencies.getIndicators() != null) {
			for (String uuid : dependencies.getIndicators()) {
				if (uuid != null && !uuid.isEmpty()) {
					ReportBuilderIndicator indicator = dao.getReportBuilderIndicatorByUuid(uuid);
					if (indicator == null) {
						result.addError("Required indicator not found: " + uuid);
					} else if (indicator.isRetired()) {
						result.addWarning("Indicator is retired: " + uuid + " (" + indicator.getName() + ")");
					}
				}
			}
		}
		
		// Validate sections
		if (dependencies.getSections() != null) {
			for (String uuid : dependencies.getSections()) {
				if (uuid != null && !uuid.isEmpty()) {
					ReportBuilderSection section = dao.getReportBuilderSectionByUuid(uuid);
					if (section == null) {
						result.addError("Required section not found: " + uuid);
					} else if (section.isRetired()) {
						result.addWarning("Section is retired: " + uuid + " (" + section.getName() + ")");
					}
				}
			}
		}
		
		// Validate themes
		if (dependencies.getThemes() != null) {
			for (String uuid : dependencies.getThemes()) {
				if (uuid != null && !uuid.isEmpty()) {
					ReportBuilderDataTheme theme = dao.getReportBuilderDataThemeByUuid(uuid);
					if (theme == null) {
						result.addError("Required theme not found: " + uuid);
					} else if (theme.isRetired()) {
						result.addWarning("Theme is retired: " + uuid + " (" + theme.getName() + ")");
					}
				}
			}
		}
		
		// Validate age categories
		if (dependencies.getAgeCategories() != null) {
			for (String uuid : dependencies.getAgeCategories()) {
				if (uuid != null && !uuid.isEmpty()) {
					ReportBuilderAgeCategory category = dao.getAgeCategoryByUuid(uuid);
					if (category == null) {
						result.addError("Required age category not found: " + uuid);
					} else if (!category.getActive()) {
						result.addWarning("Age category is inactive: " + uuid + " (" + category.getName() + ")");
					}
				}
			}
		}
		
		// Validate libraries
		if (dependencies.getLibraries() != null) {
			for (String uuid : dependencies.getLibraries()) {
				if (uuid != null && !uuid.isEmpty()) {
					ReportLibrary library = dao.getReportLibraryByUuid(uuid);
					if (library == null) {
						result.addError("Required library not found: " + uuid);
					} else if (library.isRetired()) {
						result.addWarning("Library is retired: " + uuid + " (" + library.getName() + ")");
					}
				}
			}
		}
		
		// Validate ETL monitors
		if (dependencies.getEtlMonitors() != null) {
			for (String uuid : dependencies.getEtlMonitors()) {
				if (uuid != null && !uuid.isEmpty()) {
					ETLMonitor monitor = dao.getETLMonitorByUuid(uuid);
					if (monitor == null) {
						result.addError("Required ETL monitor not found: " + uuid);
					} else if (monitor.isRetired()) {
						result.addWarning("ETL monitor is retired: " + uuid + " (" + monitor.getName() + ")");
					}
				}
			}
		}
		
		// Validate data source if specified
		if (dependencies.getDataSource() != null && !dependencies.getDataSource().isEmpty()) {
			// Data source validation would depend on your data source configuration
			// For now, just log it
			log.debug("Report uses data source: {}", dependencies.getDataSource());
		}
	}
	
	/**
	 * Create or update a ReportBuilderReport from a SerializedReport
	 */
	private ReportBuilderReport createOrUpdateReportFromSerialized(SerializedReport serializedReport, String categoryUuid) {
		// Check if report already exists - by uuid first, then by code as a fallback so packages
		// without shared uuids (raw compiled design files) still update instead of duplicating.
		ReportBuilderReport existingReport = dao.getReportBuilderReportByUuid(serializedReport.getUuid());
		if (existingReport == null && serializedReport.getCode() != null && !serializedReport.getCode().trim().isEmpty()) {
			existingReport = dao.getReportBuilderReportByCode(serializedReport.getCode());
		}
		
		ReportBuilderReport report;
		if (existingReport != null) {
			log.info("Updating existing report: {}", serializedReport.getName());
			report = existingReport;
		} else {
			log.info("Creating new report: {}", serializedReport.getName());
			report = new ReportBuilderReport();
			report.setUuid(serializedReport.getUuid());
			report.setDateCreated(new Date());
		}
		
		// Set basic properties
		report.setName(serializedReport.getName());
		report.setDescription(serializedReport.getDescription());
		report.setCode(serializedReport.getCode());
		
		// Set report type
		if (serializedReport.getReportType() != null) {
			try {
				report.setReportType(ReportBuilderReport.ReportType.valueOf(serializedReport.getReportType()));
			}
			catch (IllegalArgumentException e) {
				log.warn("Invalid report type: {}", serializedReport.getReportType());
			}
		}
		
		// Set status
		if (serializedReport.getStatus() != null) {
			try {
				report.setCompileStatus(ReportBuilderReport.ReportCompileStatus.valueOf(serializedReport.getStatus()));
			}
			catch (IllegalArgumentException e) {
				log.warn("Invalid compile status: {}", serializedReport.getStatus());
			}
		}
		
		// Set category: explicit request param wins, then a category uuid stamped in the package,
		// then a name lookup as the weakest match.
		if (categoryUuid != null && !categoryUuid.isEmpty()) {
			ReportCategory category = dao.getReportCategoryByUuid(categoryUuid);
			if (category != null) {
				report.setCategory(category);
			}
		} else if (serializedReport.getCategoryUuid() != null && !serializedReport.getCategoryUuid().trim().isEmpty()) {
			ReportCategory category = dao.getReportCategoryByUuid(serializedReport.getCategoryUuid());
			if (category == null && serializedReport.getCategory() != null && !serializedReport.getCategory().isEmpty()) {
				// Category id from another instance is unknown here; fall back to its name.
				List<ReportCategory> categories = dao.getReportCategories(serializedReport.getCategory(), false, 0, 1);
				if (!categories.isEmpty()) {
					category = categories.get(0);
				}
			}
			if (category != null) {
				report.setCategory(category);
			}
		} else if (serializedReport.getCategory() != null && !serializedReport.getCategory().isEmpty()) {
			// Try to find category by name
			List<ReportCategory> categories = dao.getReportCategories(serializedReport.getCategory(), false, 0, 1);
			if (!categories.isEmpty()) {
				report.setCategory(categories.get(0));
			}
		}
		
		report.setChangedBy(Context.getAuthenticatedUser());
		report.setDateChanged(new Date());
		
		// Save the report
		return dao.saveReportBuilderReport(report);
	}
	
	/**
	 * Compile a report during import to generate the ReportDefinition Reconstructs the
	 * ReportDefinition and ReportDesign from the compiled config
	 */
	private ReportDefinition compileReportForImport(ReportBuilderReport report, ObjectNode compiledConfig) {
		try {
			ReportDefinitionService reportDefinitionService = Context.getService(ReportDefinitionService.class);
			ReportDefinition reportDefinition = findOrCreateReportDefinition(report, reportDefinitionService);
			
			// Clear existing definitions
			reportDefinition.getParameters().clear();
			reportDefinition.getDataSetDefinitions().clear();
			
			// Set basic properties
			reportDefinition.setName(report.getName());
			reportDefinition.setDescription(report.getDescription());
			
			// Convert compiled config to JSON string for ReportDesign
			String compiledJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(compiledConfig);
			
			// Create definition file name
			String definitionFileName = buildLinelistDefinitionFileName(report);
			
			// Process based on report type
			if (report.getReportType() == ReportBuilderReport.ReportType.LINE_LIST) {
				compileLinelistReportForImport(report, compiledConfig, reportDefinition, reportDefinitionService);
			} else {
				// For aggregate reports, we need similar logic
				compileAggregateReportForImport(report, compiledConfig, reportDefinition, reportDefinitionService);
			}
			
			// Save the ReportDesign
			ReportDesign jsonDesign = saveOrUpdateJsonReportDesign(reportDefinition, compiledJson, report);
			
			// Update report with compiled artifacts
			report.setCompiledReportDefinitionUuid(reportDefinition.getUuid());
			report.setCompiledReportDesignUuid(jsonDesign != null ? jsonDesign.getUuid() : null);
			report.setLastCompiledAt(new Date());
			report.setCompileStatus(ReportBuilderReport.ReportCompileStatus.COMPILED);
			report.setConfigJson(compiledJson);
			
			// Save the updated report
			saveReportBuilderReport(report);
			
			log.debug("Compiled report definition for import: {}", report.getName());
			return reportDefinition;
			
		}
		catch (Exception e) {
			log.error("Failed to compile report during import: {}", report.getName(), e);
			throw new APIException("Failed to compile report during import: " + e.getMessage(), e);
		}
	}
	
	/**
	 * Compile a linelist report during import
	 */
	private void compileLinelistReportForImport(ReportBuilderReport report, ObjectNode compiledConfig,
	        ReportDefinition reportDefinition, ReportDefinitionService reportDefinitionService) throws Exception {
		
		// Process parameters from compiled config
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
		
		// Create LineListDataSetDefinition
		LineListDataSetDefinition dsd = new LineListDataSetDefinition();
		dsd.setName(report.getName() + " Data Set");
		dsd.setDescription(report.getDescription());
		
		// Use relative filename for portability
		String definitionFileName = buildLinelistDefinitionFileName(report);
		dsd.setReportDesignPath(definitionFileName);
		
		// Add parameters to dataset
		for (Parameter p : declaredParameters) {
			dsd.addParameter(p);
		}
		
		// Create parameter mappings
		Map<String, Object> parameterMappings = new HashMap<String, Object>();
		for (Parameter p : declaredParameters) {
			parameterMappings.put(p.getName(), "${" + p.getName() + "}");
		}
		
		// Add dataset definition to report
		reportDefinition.addDataSetDefinition("linelistDataSet", dsd, parameterMappings);
		reportDefinition = reportDefinitionService.saveDefinition(reportDefinition);
	}
	
	/**
	 * Compile an aggregate report during import
	 */
	private void compileAggregateReportForImport(ReportBuilderReport report, ObjectNode compiledConfig,
	        ReportDefinition reportDefinition, ReportDefinitionService reportDefinitionService) throws Exception {
		
		// Process parameters from compiled config
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
		
		// Process dataSetDefinitions if present (aggregate reports use these)
		JsonNode dataSetDefinitions = compiledConfig.path("dataSetDefinitions");
		boolean hasDataSets = false;
		if (dataSetDefinitions.isArray() && dataSetDefinitions.size() > 0) {
			Iterator<JsonNode> it = dataSetDefinitions.elements();
			int dsIndex = 0;
			while (it.hasNext()) {
				JsonNode dsConfig = it.next();
				String dsName = dsConfig.path("name").asText("dataset" + dsIndex);
				String dsType = dsConfig.path("type").asText("PATIENT_DATA_SET");
				
				// Create appropriate dataset definition based on type
				if ("PATIENT_DATA_SET".equals(dsType)) {
					hasDataSets = true;
					LineListDataSetDefinition dsd = new LineListDataSetDefinition();
					dsd.setName(dsName);
					dsd.setDescription(dsConfig.path("description").asText(""));
					
					// Add parameters
					for (Parameter p : declaredParameters) {
						dsd.addParameter(p);
					}
					
					Map<String, Object> parameterMappings = new HashMap<String, Object>();
					for (Parameter p : declaredParameters) {
						parameterMappings.put(p.getName(), "${" + p.getName() + "}");
					}
					
					reportDefinition.addDataSetDefinition(dsName, dsd, parameterMappings);
				}
				// Add other dataset types as needed
				dsIndex++;
			}
		}
		
		// Default aggregate dataset pointing at the compiled design file under the canonical
		// configuration/reports root - mirrors what a normal compile produces, so imported
		// aggregate reports can actually evaluate.
		if (!hasDataSets) {
			String designPath = buildDefinitionFileName(report);
			AggregateReportDataSetDefinition dsd = new AggregateReportDataSetDefinition();
			dsd.setName(report.getName() + " Data Set");
			dsd.setDescription(report.getDescription());
			dsd.setReportDesignPath(designPath);
			
			Map<String, Object> parameterMappings = new HashMap<String, Object>();
			reportDefinition.addDataSetDefinition("defaultDataSet", dsd, parameterMappings);
		}
		
		reportDefinition = reportDefinitionService.saveDefinition(reportDefinition);
		
		reportDefinition = reportDefinitionService.saveDefinition(reportDefinition);
	}
	
	/**
	 * Export the compiled report
	 */
	private File exportCompiledReport(ReportBuilderReport report,
	        com.fasterxml.jackson.databind.node.ObjectNode compiledConfig, File destination) {
		try {
			SerializedReport serialized = new SerializedReport(report, compiledConfig);
			serialized.setCompiledAt(new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(new Date()));
			serialized.setCompiledBy(Context.getAuthenticatedUser() != null ? Context.getAuthenticatedUser().getUsername()
			        : "system");
			
			if (report.getCategory() != null) {
				serialized.setCategory(report.getCategory().getName());
				serialized.setCategoryUuid(report.getCategory().getUuid());
			}
			
			// Populate dependencies from compiled config
			populateDependencies(serialized, report, compiledConfig, destination);
			
			String subdir = report.getReportType() == ReportBuilderReport.ReportType.LINE_LIST ? "linelist" : "aggregates";
			String filename = getFileNameForExport(report.getCode(), report.getUuid());
			// Shipped copies hold SerializedReport wrappers (different JSON shape from the live raw
			// configs the evaluators read), so they live under reports/dist/ to never collide with
			// the canonical configuration/reports/{aggregates,linelist} design store.
			File file = new File(destination, "configuration" + File.separator + "reports" + File.separator + "dist"
			        + File.separator + subdir + File.separator + filename);
			
			// Ensure parent directory exists
			File parentDir = file.getParentFile();
			if (parentDir != null && !parentDir.exists()) {
				parentDir.mkdirs();
			}
			
			objectMapper.writeValue(file, serialized);
			log.debug("Exported compiled report: {}", file.getAbsolutePath());
			return file;
			
		}
		catch (IOException e) {
			throw new APIException("Failed to export compiled report", e);
		}
	}
	
	/**
	 * Populate dependencies for a serialized report by extracting referenced UUIDs from the
	 * compiled config. Also exports associated library and tracks it as a dependency.
	 */
	private void populateDependencies(SerializedReport serialized, ReportBuilderReport report, ObjectNode compiledConfig,
	        File destination) {
		try {
			// 1. Find and add library dependency
			ReportLibrary library = dao.getReportLibraryByBuilderReportUuid(report.getUuid());
			if (library != null) {
				// Export library file
				File libraryFile = exportLibrary(library, destination);
				// Add library UUID to dependencies
				serialized.getDependencies().addLibrary(library.getUuid());
				log.debug("Added library dependency: {} for report: {}", library.getUuid(), report.getName());
			}
			
			// 2. Extract indicator UUIDs from compiled config
			if (compiledConfig != null && compiledConfig.has("indicators") && compiledConfig.get("indicators").isArray()) {
				JsonNode indicators = compiledConfig.get("indicators");
				for (JsonNode indicator : indicators) {
					if (indicator.has("uuid")) {
						String uuid = indicator.get("uuid").asText();
						if (uuid != null && !uuid.isEmpty()) {
							serialized.getDependencies().addIndicator(uuid);
						}
					}
				}
			}
			
			// 3. Extract section UUIDs from compiled config
			if (compiledConfig != null && compiledConfig.has("sections") && compiledConfig.get("sections").isArray()) {
				JsonNode sections = compiledConfig.get("sections");
				for (JsonNode section : sections) {
					if (section.has("uuid")) {
						String uuid = section.get("uuid").asText();
						if (uuid != null && !uuid.isEmpty()) {
							serialized.getDependencies().addSection(uuid);
						}
					}
				}
			}
			
			// 4. Extract theme UUIDs from compiled config
			if (compiledConfig != null && compiledConfig.has("themes") && compiledConfig.get("themes").isArray()) {
				JsonNode themes = compiledConfig.get("themes");
				for (JsonNode theme : themes) {
					if (theme.has("uuid")) {
						String uuid = theme.get("uuid").asText();
						if (uuid != null && !uuid.isEmpty()) {
							serialized.getDependencies().addTheme(uuid);
						}
					}
				}
			}
			
			// 5. Extract age category UUIDs from compiled config
			if (compiledConfig != null && compiledConfig.has("ageCategories")
			        && compiledConfig.get("ageCategories").isArray()) {
				JsonNode ageCategories = compiledConfig.get("ageCategories");
				for (JsonNode ageCategory : ageCategories) {
					if (ageCategory.has("uuid")) {
						String uuid = ageCategory.get("uuid").asText();
						if (uuid != null && !uuid.isEmpty()) {
							serialized.getDependencies().addAgeCategory(uuid);
						}
					}
				}
			}
			
			// 6. Extract ETL monitor UUIDs from compiled config
			if (compiledConfig != null && compiledConfig.has("etlMonitors") && compiledConfig.get("etlMonitors").isArray()) {
				JsonNode etlMonitors = compiledConfig.get("etlMonitors");
				for (JsonNode etlMonitor : etlMonitors) {
					if (etlMonitor.has("uuid")) {
						String uuid = etlMonitor.get("uuid").asText();
						if (uuid != null && !uuid.isEmpty()) {
							serialized.getDependencies().addETLMonitor(uuid);
						}
					}
				}
			}
			
			// 7. Extract data source if present
			if (compiledConfig != null && compiledConfig.has("dataSource")) {
				String dataSource = compiledConfig.get("dataSource").asText();
				if (dataSource != null && !dataSource.isEmpty()) {
					serialized.getDependencies().setDataSource(dataSource);
				}
			}
			
			log.debug("Populated dependencies for report: {} (indicators: {}, sections: {}, themes: {}, libraries: {})",
			    report.getName(), serialized.getDependencies().getIndicators().size(), serialized.getDependencies()
			            .getSections().size(), serialized.getDependencies().getThemes().size(), serialized.getDependencies()
			            .getLibraries().size());
			
		}
		catch (Exception e) {
			log.warn("Failed to populate some dependencies for report: {}", report.getName(), e);
			// Don't fail export for dependency population issues
		}
	}
	
	/**
	 * Generate version metadata file
	 */
	private File generateVersionMetadata(ReportBuilderReport report, String version, ShippingResult result, File destination)
	        throws IOException {
		VersionMetadata metadata = new VersionMetadata();
		
		// Package info
		metadata.getPackageInfo().setName(report.getCode() != null ? report.getCode() : report.getUuid());
		metadata.getPackageInfo().setVersion(version);
		metadata.getPackageInfo().setDescription("Report distribution package");
		metadata.getPackageInfo().setExportedAt(new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(new Date()));
		metadata.getPackageInfo().setExportedBy(
		    Context.getAuthenticatedUser() != null ? Context.getAuthenticatedUser().getUsername() : "system");
		metadata.getPackageInfo().setReportBuilderVersion("1.0.0");
		
		// Contents
		VersionMetadata.ReportInfo reportInfo = new VersionMetadata.ReportInfo(report.getUuid(),
		        report.getCode() != null ? report.getCode() : report.getUuid(), report.getReportType() != null ? report
		                .getReportType().name() : "AGGREGATE", result.getSourceFile(), result.getCompiledFile());
		metadata.getContents().addReport(reportInfo);
		
		// Add dependencies to contents
		for (String dep : result.getDependencies().getCategories()) {
			metadata.getContents().getDependencies().addCategory(dep);
		}
		// Add other dependencies similarly...
		
		File versionFile = new File(destination, "version.json");
		objectMapper.writeValue(versionFile, metadata);
		log.debug("Generated version metadata: {}", versionFile.getAbsolutePath());
		return versionFile;
	}
	
	/**
	 * Generate filename for export - uses code if available, falls back to UUID
	 */
	/**
	 * Files already claimed during the current bulk export run, so entities sharing a code do not
	 * silently overwrite each other's export file. Null outside a bulk run, leaving single-entity
	 * exports with the plain code-based name.
	 */
	private static final ThreadLocal<java.util.Set<String>> CURRENT_EXPORT_FILENAMES = new ThreadLocal<>();

	private String getFileNameForExport(String code, String uuid) {
		String filename = (code != null && !code.trim().isEmpty()) ? code + ".json" : uuid + ".json";
		java.util.Set<String> claimed = CURRENT_EXPORT_FILENAMES.get();
		if (claimed != null && !filename.equals(uuid + ".json")) {
			if (!claimed.add(filename)) {
				log.warn("Duplicate export code '{}' - using the uuid filename instead to avoid overwriting", code);
				filename = uuid + ".json";
			}
		}
		return filename;
	}
	
	/**
	 * Create a version metadata file for bulk exports
	 */
	private void createShippingVersionFile(File destination, String version) {
		try {
			VersionMetadata metadata = new VersionMetadata();
			
			// Package info
			metadata.getPackageInfo().setName("bulk-export");
			metadata.getPackageInfo().setVersion(version);
			metadata.getPackageInfo().setDescription("Bulk export of ReportBuilder entities");
			metadata.getPackageInfo().setExportedAt(
			    new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(new Date()));
			metadata.getPackageInfo().setExportedBy(
			    Context.getAuthenticatedUser() != null ? Context.getAuthenticatedUser().getUsername() : "system");
			metadata.getPackageInfo().setReportBuilderVersion("1.0.0");
			
			File versionFile = new File(destination, "version.json");
			objectMapper.writeValue(versionFile, metadata);
			log.debug("Created version metadata file: {}", versionFile.getAbsolutePath());
			
		}
		catch (IOException e) {
			log.warn("Failed to create version metadata file", e);
		}
	}
	
	// ========================================================================
	// Individual entity export methods
	// ========================================================================
	
	private File exportCategory(ReportCategory category, File destination) {
		try {
			String filename = getFileNameForExport(null, category.getUuid());
			File file = new File(destination, "configuration" + File.separator + "reportbuilder" + File.separator
			        + "categories" + File.separator + filename);
			
			// Ensure parent directory exists
			File parentDir = file.getParentFile();
			if (parentDir != null && !parentDir.exists()) {
				parentDir.mkdirs();
			}
			
			objectMapper.writeValue(file, category);
			log.debug("Exported category: {}", file.getAbsolutePath());
			return file;
			
		}
		catch (IOException e) {
			throw new APIException("Failed to export category", e);
		}
	}
	
	private File exportIndicator(ReportBuilderIndicator indicator, File destination) {
		try {
			String filename = getFileNameForExport(indicator.getCode(), indicator.getUuid());
			File file = new File(destination, "configuration" + File.separator + "reportbuilder" + File.separator
			        + "indicators" + File.separator + filename);
			
			// Ensure parent directory exists
			File parentDir = file.getParentFile();
			if (parentDir != null && !parentDir.exists()) {
				parentDir.mkdirs();
			}
			
			objectMapper.writeValue(file, indicator);
			log.debug("Exported indicator: {}", file.getAbsolutePath());
			return file;
			
		}
		catch (IOException e) {
			throw new APIException("Failed to export indicator", e);
		}
	}
	
	private File exportSection(ReportBuilderSection section, File destination) {
		try {
			String filename = getFileNameForExport(section.getCode(), section.getUuid());
			File file = new File(destination, "configuration" + File.separator + "reportbuilder" + File.separator
			        + "sections" + File.separator + filename);
			
			// Ensure parent directory exists
			File parentDir = file.getParentFile();
			if (parentDir != null && !parentDir.exists()) {
				parentDir.mkdirs();
			}
			
			objectMapper.writeValue(file, section);
			log.debug("Exported section: {}", file.getAbsolutePath());
			return file;
			
		}
		catch (IOException e) {
			throw new APIException("Failed to export section", e);
		}
	}
	
	private File exportTheme(ReportBuilderDataTheme theme, File destination) {
		try {
			String filename = getFileNameForExport(theme.getCode(), theme.getUuid());
			File file = new File(destination, "configuration" + File.separator + "reportbuilder" + File.separator + "themes"
			        + File.separator + filename);
			
			// Ensure parent directory exists
			File parentDir = file.getParentFile();
			if (parentDir != null && !parentDir.exists()) {
				parentDir.mkdirs();
			}
			
			objectMapper.writeValue(file, theme);
			log.debug("Exported theme: {}", file.getAbsolutePath());
			return file;
			
		}
		catch (IOException e) {
			throw new APIException("Failed to export theme", e);
		}
	}
	
	private File exportAgeCategory(ReportBuilderAgeCategory category, File destination) {
		try {
			String filename = getFileNameForExport(category.getCode(), category.getUuid());
			File file = new File(destination, "configuration" + File.separator + "reportbuilder" + File.separator
			        + "age-categories" + File.separator + filename);
			
			// Ensure parent directory exists
			File parentDir = file.getParentFile();
			if (parentDir != null && !parentDir.exists()) {
				parentDir.mkdirs();
			}
			
			// Age groups belong to their age category - embed them so no separate age-group files
			// exist, and the import recreates the whole family from this one file
			com.fasterxml.jackson.databind.node.ObjectNode node = objectMapper.valueToTree(category);
			node.set("ageGroups", objectMapper.valueToTree(getAgeGroupsByCategoryUuid(category.getUuid(), null)));
			objectMapper.writeValue(file, node);
			log.debug("Exported age category: {}", file.getAbsolutePath());
			return file;
			
		}
		catch (IOException e) {
			throw new APIException("Failed to export age category", e);
		}
	}
	
	private File exportAgeGroup(ReportBuilderAgeGroup ageGroup, File destination) {
		try {
			String filename = getFileNameForExport(ageGroup.getCode(), String.valueOf(ageGroup.getId()));
			File file = new File(destination, "configuration" + File.separator + "reportbuilder" + File.separator
			        + "age-groups" + File.separator + filename);
			
			// Ensure parent directory exists
			File parentDir = file.getParentFile();
			if (parentDir != null && !parentDir.exists()) {
				parentDir.mkdirs();
			}
			
			objectMapper.writeValue(file, ageGroup);
			log.debug("Exported age group: {}", file.getAbsolutePath());
			return file;
			
		}
		catch (IOException e) {
			throw new APIException("Failed to export age group", e);
		}
	}
	
	private File exportETLSource(ETLSource source, File destination) {
		try {
			String filename = getFileNameForExport(source.getCode(), source.getUuid());
			File file = new File(destination, "configuration" + File.separator + "reportbuilder" + File.separator
			        + "etl-sources" + File.separator + filename);
			
			// Ensure parent directory exists
			File parentDir = file.getParentFile();
			if (parentDir != null && !parentDir.exists()) {
				parentDir.mkdirs();
			}
			
			objectMapper.writeValue(file, source);
			log.debug("Exported ETL source: {}", file.getAbsolutePath());
			return file;
			
		}
		catch (IOException e) {
			throw new APIException("Failed to export ETL source", e);
		}
	}
	
	private File exportETLMonitor(ETLMonitor monitor, File destination) {
		try {
			String filename = getFileNameForExport(monitor.getCode(), monitor.getUuid());
			File file = new File(destination, "configuration" + File.separator + "reportbuilder" + File.separator
			        + "etl-monitors" + File.separator + filename);
			
			// Ensure parent directory exists
			File parentDir = file.getParentFile();
			if (parentDir != null && !parentDir.exists()) {
				parentDir.mkdirs();
			}
			
			objectMapper.writeValue(file, monitor);
			log.debug("Exported ETL monitor: {}", file.getAbsolutePath());
			return file;
			
		}
		catch (IOException e) {
			throw new APIException("Failed to export ETL monitor", e);
		}
	}
	
	private File exportDashboard(ReportBuilderDashboard dashboard, File destination) {
		try {
			String filename = getFileNameForExport(dashboard.getCode(), dashboard.getUuid());
			File file = new File(destination, "configuration" + File.separator + "reportbuilder" + File.separator
			        + "dashboards" + File.separator + filename);
			
			// Ensure parent directory exists
			File parentDir = file.getParentFile();
			if (parentDir != null && !parentDir.exists()) {
				parentDir.mkdirs();
			}
			
			objectMapper.writeValue(file, dashboard);
			log.debug("Exported dashboard: {}", file.getAbsolutePath());
			return file;
			
		}
		catch (IOException e) {
			throw new APIException("Failed to export dashboard", e);
		}
	}
	
	private File exportLibrary(ReportLibrary library, File destination) {
		try {
			String filename = getFileNameForExport(library.getCode(), library.getUuid());
			File file = new File(destination, "configuration" + File.separator + "reportbuilder" + File.separator
			        + "library" + File.separator + filename);
			
			// Ensure parent directory exists
			File parentDir = file.getParentFile();
			if (parentDir != null && !parentDir.exists()) {
				parentDir.mkdirs();
			}
			
			objectMapper.writeValue(file, library);
			log.debug("Exported library: {}", file.getAbsolutePath());
			return file;
			
		}
		catch (IOException e) {
			throw new APIException("Failed to export library", e);
		}
	}
	
	// ========================================================================
	// Private helper methods for Import
	// ========================================================================
	
	/**
	 * Import all entities of a specific type from a directory
	 */
	/**
	 * Builds a transaction template for a single import item. Each item commits or rolls back
	 * independently: REQUIRES_NEW keeps item transactions isolated even when a caller of
	 * {@link #importFromDirectory(File)} or {@link #importEntity(String, File)} already runs inside
	 * one. Built per import run rather than cached, mirroring the per-call construction used by
	 * metadatadeploy and atomfeed.
	 */
	private TransactionTemplate newImportTransactionTemplate() {
		PlatformTransactionManager transactionManager = Context.getRegisteredComponent("transactionManager",
		    PlatformTransactionManager.class);
		TransactionTemplate template = new TransactionTemplate(transactionManager);
		template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		return template;
	}
	
	/**
	 * Builds a human-readable reason for an import failure. Walks to the root cause so wrapped
	 * persistence exceptions report the underlying problem, and falls back to the exception type
	 * and throw site when the message is empty (e.g. a NullPointerException).
	 */
	private String describeFailure(Throwable e) {
		Throwable root = e;
		while (root.getCause() != null && root.getCause() != root) {
			root = root.getCause();
		}
		
		String message = e.getMessage();
		if (message == null || message.trim().isEmpty()) {
			message = e.getClass().getSimpleName();
			StackTraceElement top = root.getStackTrace().length > 0 ? root.getStackTrace()[0] : null;
			if (top != null) {
				message += " thrown at " + top;
			}
		} else if (root != e && root.getMessage() != null && !root.getMessage().trim().isEmpty()
		        && !message.contains(root.getMessage())) {
			message += " (caused by " + root.getClass().getSimpleName() + ": " + root.getMessage().trim() + ")";
		}
		
		return message;
	}
	
	/**
	 * Clears the Hibernate session after a failed import item. Load-bearing rather than defensive:
	 * on web requests OpenMRS pre-binds one session per request (OpenSessionInViewFilter) that
	 * every per-item transaction reuses, so a failed item's pending writes would otherwise be
	 * flushed and committed by the next item's transaction.
	 */
	private void clearSessionAfterFailure() {
		try {
			Context.clearSession();
		}
		catch (Exception e) {
			log.debug("Could not clear session after a failed import item", e);
		}
	}
	
	/**
	 * Resolves the code for a new entity from its file: the explicit code when present, otherwise
	 * derived from the name and then the uuid, so package files without a code still import.
	 */
	private String deriveCode(JsonNode node) {
		JsonNode code = node.get("code");
		if (code != null && !code.isNull() && !code.asText().trim().isEmpty()) {
			return code.asText().trim();
		}
		JsonNode name = node.get("name");
		if (name != null && !name.isNull() && !name.asText().trim().isEmpty()) {
			return name.asText().trim();
		}
		return node.get("uuid").asText();
	}
	
	/**
	 * Imports every entity file of one type directory. Each file is imported in its own transaction
	 * via the given template; a failure is isolated to its file and reported in the result while
	 * all other files continue.
	 */
	private void importType(String type, File dir, ImportResult result, TransactionTemplate template) {
		File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));

		if (files == null || files.length == 0) {
			return;
		}

		log.info("Importing {} entities from: {}", type, dir.getAbsolutePath());

		for (File file : files) {
			String fileName = file.getName();
			log.info("[IMPORT] Starting import: type={}, file={}", type, fileName);

			try {
				Boolean imported = template.execute(status -> {
					try {
						switch (type) {
							case "categories":
								importCategory(file);
								break;
							case "age-categories":
								importAgeCategory(file);
								break;
							case "age-groups":
								importAgeGroup(file);
								break;
							case "etl-sources":
								importETLSource(file);
								break;
							case "etl-monitors":
								importETLMonitor(file);
								break;
							case "indicators":
								importIndicator(file);
								break;
							case "sections":
								importSection(file);
								break;
							case "themes":
								importTheme(file);
								break;
							case "reports":
								importReport(file);
								break;
							case "dashboards":
								importDashboard(file);
								break;
							case "library":
								importLibraryEntry(file);
								break;
							default:
								log.warn("Unknown import type: {}", type);
								return false;
						}
						return true;
					}
					catch (IOException e) {
						throw new UncheckedIOException(e);
					}
				});

				if (Boolean.TRUE.equals(imported)) {
					log.info("[IMPORT] Successfully imported: type={}, file={}", type, fileName);
					result.addSuccess(type, fileName);
				}

			}
			catch (Exception e) {
				log.error("[IMPORT] Failed to import: type={}, file={}, error={}", type, fileName, describeFailure(e), e);
				result.addError(type, fileName, describeFailure(e));
				clearSessionAfterFailure();
			}
		}
	}
	
	/**
	 * Import compiled reports from reports directory
	 */
	private void importCompiledReports(File reportsDir,
	        ImportResult result) {
		File[] subdirs = reportsDir.listFiles(File::isDirectory);

		if (subdirs == null) {
			return;
		}

		for (File subdir : subdirs) {
			if ("aggregates".equals(subdir.getName()) || "linelist".equals(subdir.getName())) {
				File[] files = subdir.listFiles((d, name) -> name.endsWith(".json"));
				if (files != null) {
					for (File file : files) {
						try {
							// Compiled reports are already handled in reports import
							// This is for reference only
							log.debug("Found compiled report: {}", file.getName());
						}
						catch (Exception e) {
							log.warn("Could not process compiled report: {}", file.getName(), e);
						}
					}
				}
			}
		}
	}
	
	/**
	 * Read entity export from file - reads database model format Returns a JsonNode for flexible
	 * parsing of different entity types
	 */
	private com.fasterxml.jackson.databind.JsonNode readEntityFile(File file) throws IOException {
		String jsonContent = new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
		return objectMapper.readTree(jsonContent);
	}
	
	/**
	 * Read version manifest from package directory
	 */
	private VersionMetadata readVersionManifest(File packageDir) {
		try {
			File versionFile = new File(packageDir, "version.json");
			if (!versionFile.exists()) {
				log.debug("No version.json found in package directory");
				return null;
			}
			
			String jsonContent = new String(java.nio.file.Files.readAllBytes(versionFile.toPath()), StandardCharsets.UTF_8);
			return objectMapper.readValue(jsonContent, VersionMetadata.class);
		}
		catch (Exception e) {
			log.warn("Failed to read version manifest: {}", e.getMessage());
			return null;
		}
	}
	
	// ========================================================================
	// Individual entity import methods (UUID-based deduplication pattern)
	// ========================================================================
	
	/**
	 * Import a ReportCategory entity - reads database model format
	 */
	private void importCategory(File file) throws IOException {
		com.fasterxml.jackson.databind.JsonNode node = readEntityFile(file);
		
		String uuid = node.get("uuid").asText();
		ReportCategory existing = getReportCategoryByUuid(uuid);
		
		if (existing != null) {
			// Update existing
			existing.setName(node.get("name").asText());
			if (node.has("description") && !node.get("description").isNull()) {
				existing.setDescription(node.get("description").asText());
			}
			// Handle retired status
			if (node.has("retired")) {
				existing.setRetired(node.get("retired").asInt() == 1);
			}
			saveReportCategory(existing);
			log.debug("Updated existing category: {}", existing.getName());
		} else {
			// Create new
			ReportCategory category = new ReportCategory();
			category.setUuid(uuid);
			category.setName(node.get("name").asText());
			if (node.has("description") && !node.get("description").isNull()) {
				category.setDescription(node.get("description").asText());
			}
			if (node.has("retired")) {
				category.setRetired(node.get("retired").asInt() == 1);
			}
			saveReportCategory(category);
			log.debug("Created new category: {}", category.getName());
		}
	}
	
	/**
	 * Import library dependencies for a serialized report from file Looks for library files in the
	 * expected location relative to the report file
	 */
	private void importLibraryDependenciesFromFile(SerializedReport serializedReport, File reportFile) {
		if (serializedReport.getDependencies() == null || serializedReport.getDependencies().getLibraries() == null
		        || serializedReport.getDependencies().getLibraries().isEmpty()) {
			log.debug("No library dependencies to import for report: {}", serializedReport.getName());
			return;
		}

		// Determine the import directory (parent of the reports directory)
		File importDir = reportFile.getParentFile();
		while (importDir != null && !importDir.getName().equals("reports")) {
			importDir = importDir.getParentFile();
		}

		if (importDir != null) {
			importDir = importDir.getParentFile(); // Go up to the root import directory
		}

		if (importDir == null || !importDir.exists()) {
			log.warn("Could not determine import directory for library dependencies");
			return;
		}

		// Import each library
		for (String libraryUuid : serializedReport.getDependencies().getLibraries()) {
			try {
				// Look for library file in the expected location
				File libraryDir = new File(importDir,
				    "configuration" + File.separator + "reportbuilder" + File.separator + "library");

				if (libraryDir.exists() && libraryDir.isDirectory()) {
					File[] libraryFiles = libraryDir.listFiles((d, name) -> name.endsWith(".json"));

					if (libraryFiles != null) {
						for (File libraryFile : libraryFiles) {
							try {
								JsonNode node = objectMapper.readTree(libraryFile);
								String fileUuid = node.path("uuid").asText(null);

								if (libraryUuid.equals(fileUuid)) {
									log.info("Found library file for UUID: {}, importing: {}", libraryUuid,
									    libraryFile.getName());
									importLibraryEntry(libraryFile);
									break;
								}
							}
							catch (Exception e) {
								log.warn("Failed to read library file: {}", libraryFile.getName(), e);
								clearSessionAfterFailure();
							}
						}
					}
				}

				// Verify library was imported
				ReportLibrary library = dao.getReportLibraryByUuid(libraryUuid);
				if (library == null) {
					log.warn("Library file not found for UUID: {}", libraryUuid);
				} else {
					log.debug("Successfully imported library: {} ({})", library.getName(), libraryUuid);
				}

			}
			catch (Exception e) {
				log.error("Failed to import library dependency: {}", libraryUuid, e);
				clearSessionAfterFailure();
			}
		}
	}
	
	/**
	 * Import a ReportLibrary entity - reads database model format Enhanced to import all fields
	 * that are exported
	 */
	private void importLibraryEntry(File file) throws IOException {
		JsonNode node = readEntityFile(file);
		
		String uuid = node.get("uuid").asText();
		ReportLibrary existing = getReportLibraryByUuid(uuid);
		
		// The report save path auto-creates a library row per report under a fresh uuid - dedup by
		// the owning report as well, or every builder-backed library file imports a duplicate
		if (existing == null) {
			String builderReportUuid = extractJsonStringField(node, "reportBuilderReportUuid");
			if (builderReportUuid != null && !builderReportUuid.trim().isEmpty()) {
				existing = dao.getReportLibraryByBuilderReportUuid(builderReportUuid);
			}
		}
		
		// Extract metaJson as JSON string (export uses camelCase)
		String metaJson = null;
		if (node.has("metaJson") && !node.get("metaJson").isNull()) {
			metaJson = objectMapper.writeValueAsString(node.get("metaJson"));
		}
		
		if (existing != null) {
			// Update existing - all fields
			existing.setName(node.get("name").asText());
			if (node.has("description") && !node.get("description").isNull()) {
				existing.setDescription(node.get("description").asText());
			}
			
			// Import code field
			if (node.has("code") && !node.get("code").isNull()) {
				if (node.has("code") && !node.get("code").isNull()) {
					existing.setCode(node.get("code").asText());
				}
			}
			
			// Import sourceType field
			if (node.has("sourceType") && !node.get("sourceType").isNull()) {
				try {
					existing.setSourceType(node.get("sourceType").asText());
				}
				catch (IllegalArgumentException e) {
					log.warn("Invalid sourceType value: {}", node.get("sourceType").asText());
				}
			}
			
			// Import reportDefinitionUuid field
			if (node.has("reportDefinitionUuid") && !node.get("reportDefinitionUuid").isNull()) {
				existing.setReportDefinitionUuid(node.get("reportDefinitionUuid").asText());
			}
			
			// Import reportBuilderReportUuid field
			if (node.has("reportBuilderReportUuid") && !node.get("reportBuilderReportUuid").isNull()) {
				existing.setReportBuilderReportUuid(node.get("reportBuilderReportUuid").asText());
			}
			
			// Handle category reference via categoryUuid
			if (node.has("categoryUuid") && !node.get("categoryUuid").isNull()) {
				String categoryUuid = node.get("categoryUuid").asText();
				ReportCategory category = getReportCategoryByUuid(categoryUuid);
				if (category != null) {
					existing.setCategory(category);
				}
			}
			
			// Import reportType field
			if (node.has("reportType") && !node.get("reportType").isNull()) {
				try {
					existing.setReportType(node.get("reportType").asText());
				}
				catch (IllegalArgumentException e) {
					log.warn("Invalid reportType value: {}", node.get("reportType").asText());
				}
			}
			
			// Import migrated field
			if (node.has("migrated") && !node.get("migrated").isNull()) {
				existing.setMigrated(node.get("migrated").asBoolean());
			}
			
			// Import metaJson field
			if (metaJson != null) {
				existing.setMetaJson(metaJson);
			}
			
			// Handle retired status
			if (node.has("retired")) {
				existing.setRetired(node.get("retired").asBoolean());
				if (node.has("retireReason") && !node.get("retireReason").isNull()) {
					dao.retireReportLibrary(existing, node.get("retireReason").asText());
				}
			} else {
				dao.unretireReportLibrary(existing);
			}
			
			saveReportLibrary(existing);
			log.debug("Updated existing library entry: {}", existing.getName());
		} else {
			// Create new - all fields
			ReportLibrary library = new ReportLibrary();
			library.setUuid(uuid);
			library.setName(node.get("name").asText());
			if (node.has("description") && !node.get("description").isNull()) {
				library.setDescription(node.get("description").asText());
			}
			
			// Import code field
			if (node.has("code") && !node.get("code").isNull()) {
				library.setCode(node.get("code").asText());
			}
			
			// Import sourceType field
			if (node.has("sourceType") && !node.get("sourceType").isNull()) {
				try {
					library.setSourceType(node.get("sourceType").asText());
				}
				catch (IllegalArgumentException e) {
					log.warn("Invalid sourceType value: {}", node.get("sourceType").asText());
				}
			}
			
			// Import reportDefinitionUuid field
			if (node.has("reportDefinitionUuid") && !node.get("reportDefinitionUuid").isNull()) {
				library.setReportDefinitionUuid(node.get("reportDefinitionUuid").asText());
			}
			
			// Import reportBuilderReportUuid field
			if (node.has("reportBuilderReportUuid") && !node.get("reportBuilderReportUuid").isNull()) {
				library.setReportBuilderReportUuid(node.get("reportBuilderReportUuid").asText());
			}
			
			// Handle category reference via categoryUuid
			if (node.has("categoryUuid") && !node.get("categoryUuid").isNull()) {
				String categoryUuid = node.get("categoryUuid").asText();
				ReportCategory category = getReportCategoryByUuid(categoryUuid);
				if (category != null) {
					library.setCategory(category);
				}
			}
			
			// Import reportType field
			if (node.has("reportType") && !node.get("reportType").isNull()) {
				try {
					library.setReportType(node.get("reportType").asText());
				}
				catch (IllegalArgumentException e) {
					log.warn("Invalid reportType value: {}", node.get("reportType").asText());
				}
			}
			
			// Import migrated field
			if (node.has("migrated") && !node.get("migrated").isNull()) {
				library.setMigrated(node.get("migrated").asBoolean());
			}
			
			// Import metaJson field
			if (metaJson != null) {
				library.setMetaJson(metaJson);
			}
			
			saveReportLibrary(library);
			
			// Handle retired status after save
			if (node.has("retired") && node.get("retired").asBoolean()) {
				if (node.has("retireReason") && !node.get("retireReason").isNull()) {
					dao.retireReportLibrary(library, node.get("retireReason").asText());
				}
			}
			
			log.debug("Created new library entry: {}", library.getName());
		}
	}
	
	/**
	 * Import a ReportBuilderIndicator entity - reads database model format Enhanced to import all
	 * fields that are exported
	 */
	private void importIndicator(File file) throws IOException {
		log.info("[IMPORT] Processing indicator file: {}", file.getName());
		com.fasterxml.jackson.databind.JsonNode node = readEntityFile(file);
		
		String uuid = node.get("uuid").asText();
		ReportBuilderIndicator existing = getReportBuilderIndicatorByUuid(uuid);
		
		// Extract configJson and metaJson as JSON strings (export uses camelCase)
		String configJson = null;
		String metaJson = null;
		if (node.has("configJson") && !node.get("configJson").isNull()) {
			configJson = objectMapper.writeValueAsString(node.get("configJson"));
		}
		if (node.has("metaJson") && !node.get("metaJson").isNull()) {
			metaJson = objectMapper.writeValueAsString(node.get("metaJson"));
		}
		
		// CRITICAL: Provide default configJson if missing (database constraint requires non-null)
		if (configJson == null || configJson.trim().isEmpty()) {
			configJson = "{}";
			log.warn("Missing configJson for indicator {}, using default empty object", uuid);
		}
		
		if (existing != null) {
			// Update existing - all fields
			existing.setName(node.get("name").asText());
			if (node.has("description") && !node.get("description").isNull()) {
				existing.setDescription(node.get("description").asText());
			}
			if (node.has("code") && !node.get("code").isNull()) {
				existing.setCode(node.get("code").asText());
			}
			// Always set configJson (has default value from above)
			existing.setConfigJson(configJson);
			if (metaJson != null) {
				existing.setMetaJson(metaJson);
			}
			
			// Import kind field
			if (node.has("kind") && !node.get("kind").isNull()) {
				try {
					existing.setKind(ReportBuilderIndicator.Kind.valueOf(node.get("kind").asText()));
				}
				catch (IllegalArgumentException e) {
					log.warn("Invalid kind value: {}", node.get("kind").asText());
				}
			}
			
			// Import defaultValueType field
			if (node.has("defaultValueType") && !node.get("defaultValueType").isNull()) {
				try {
					existing.setDefaultValueType(ReportBuilderIndicator.ValueType.valueOf(node.get("defaultValueType")
					        .asText()));
				}
				catch (IllegalArgumentException e) {
					log.warn("Invalid defaultValueType value: {}", node.get("defaultValueType").asText());
				}
			}
			
			// Import themeUuid field
			if (node.has("themeUuid") && !node.get("themeUuid").isNull()) {
				existing.setThemeUuid(node.get("themeUuid").asText());
			}
			
			// Import sqlTemplate field
			if (node.has("sqlTemplate") && !node.get("sqlTemplate").isNull()) {
				existing.setSqlTemplate(node.get("sqlTemplate").asText());
			}
			
			// Import denominatorSqlTemplate field
			if (node.has("denominatorSqlTemplate") && !node.get("denominatorSqlTemplate").isNull()) {
				existing.setDenominatorSqlTemplate(node.get("denominatorSqlTemplate").asText());
			}
			
			// Handle retired status
			if (node.has("retired")) {
				existing.setRetired(node.get("retired").asBoolean());
				if (node.has("retireReason") && !node.get("retireReason").isNull()) {
					dao.retireReportBuilderIndicator(existing, node.get("retireReason").asText());
				}
			} else {
				dao.unretireReportBuilderIndicator(existing);
			}
			
			// Import audit fields if present (for migration purposes)
			if (node.has("creator") && !node.get("creator").isNull()) {
				try {
					String creatorUuid = node.get("creator").asText();
					if (creatorUuid != null && !creatorUuid.isEmpty() && !creatorUuid.equals("null")) {
						org.openmrs.User creator = Context.getService(org.openmrs.api.UserService.class).getUserByUuid(
						    creatorUuid);
						if (creator != null) {
							existing.setCreator(creator);
						}
					}
				}
				catch (Exception e) {
					log.debug("Could not set creator for indicator: {}", e.getMessage());
				}
			}
			if (node.has("dateCreated") && !node.get("dateCreated").isNull()) {
				try {
					long dateCreated = node.get("dateCreated").asLong();
					existing.setDateCreated(new java.util.Date(dateCreated));
				}
				catch (Exception e) {
					log.debug("Could not set dateCreated for indicator: {}", e.getMessage());
				}
			}
			
			saveReportBuilderIndicator(existing);
			log.info("[IMPORT] Successfully updated indicator: {} ({})", existing.getName(), file.getName());
		} else {
			// Create new - all fields
			ReportBuilderIndicator indicator = new ReportBuilderIndicator();
			indicator.setUuid(uuid);
			indicator.setName(node.get("name").asText());
			if (node.has("description") && !node.get("description").isNull()) {
				indicator.setDescription(node.get("description").asText());
			}
			indicator.setCode(deriveCode(node));
			// Always set configJson (has default value from above)
			indicator.setConfigJson(configJson);
			if (metaJson != null) {
				indicator.setMetaJson(metaJson);
			}
			
			// Import kind field
			if (node.has("kind") && !node.get("kind").isNull()) {
				try {
					indicator.setKind(ReportBuilderIndicator.Kind.valueOf(node.get("kind").asText()));
				}
				catch (IllegalArgumentException e) {
					log.warn("Invalid kind value: {}", node.get("kind").asText());
				}
			}
			
			// Import defaultValueType field
			if (node.has("defaultValueType") && !node.get("defaultValueType").isNull()) {
				try {
					indicator.setDefaultValueType(ReportBuilderIndicator.ValueType.valueOf(node.get("defaultValueType")
					        .asText()));
				}
				catch (IllegalArgumentException e) {
					log.warn("Invalid defaultValueType value: {}", node.get("defaultValueType").asText());
				}
			}
			
			// Import themeUuid field
			if (node.has("themeUuid") && !node.get("themeUuid").isNull()) {
				indicator.setThemeUuid(node.get("themeUuid").asText());
			}
			
			// Import sqlTemplate field
			if (node.has("sqlTemplate") && !node.get("sqlTemplate").isNull()) {
				indicator.setSqlTemplate(node.get("sqlTemplate").asText());
			}
			
			// Import denominatorSqlTemplate field
			if (node.has("denominatorSqlTemplate") && !node.get("denominatorSqlTemplate").isNull()) {
				indicator.setDenominatorSqlTemplate(node.get("denominatorSqlTemplate").asText());
			}
			
			// Handle retired status BEFORE save to avoid Hibernate issues
			if (node.has("retired")) {
				indicator.setRetired(node.get("retired").asBoolean());
				if (node.has("retireReason") && !node.get("retireReason").isNull()) {
					indicator.setRetireReason(node.get("retireReason").asText());
				}
				// Import retiredBy if present (for migration purposes)
				if (node.has("retiredBy") && !node.get("retiredBy").isNull()) {
					try {
						String retiredByUuid = node.get("retiredBy").asText();
						if (retiredByUuid != null && !retiredByUuid.isEmpty() && !retiredByUuid.equals("null")) {
							org.openmrs.User retiredBy = Context.getService(org.openmrs.api.UserService.class)
							        .getUserByUuid(retiredByUuid);
							if (retiredBy != null) {
								indicator.setRetiredBy(retiredBy);
							}
						}
					}
					catch (Exception e) {
						log.debug("Could not set retiredBy for indicator: {}", e.getMessage());
					}
				}
				// Import dateRetired if present (for migration purposes)
				if (node.has("dateRetired") && !node.get("dateRetired").isNull()) {
					try {
						long dateRetired = node.get("dateRetired").asLong();
						indicator.setDateRetired(new java.util.Date(dateRetired));
					}
					catch (Exception e) {
						log.debug("Could not set dateRetired for indicator: {}", e.getMessage());
					}
				}
				// If retired but no retiredBy/dateRetired was in import, set to current user/date
				if (indicator.getRetired() && indicator.getRetiredBy() == null) {
					indicator.setRetiredBy(org.openmrs.api.context.Context.getAuthenticatedUser());
					if (indicator.getDateRetired() == null) {
						indicator.setDateRetired(new java.util.Date());
					}
					if (indicator.getRetireReason() == null) {
						indicator.setRetireReason("Imported as retired");
					}
				}
			}
			
			// Import audit fields if present (for migration purposes)
			if (node.has("creator") && !node.get("creator").isNull()) {
				try {
					String creatorUuid = node.get("creator").asText();
					if (creatorUuid != null && !creatorUuid.isEmpty() && !creatorUuid.equals("null")) {
						org.openmrs.User creator = Context.getService(org.openmrs.api.UserService.class).getUserByUuid(
						    creatorUuid);
						if (creator != null) {
							indicator.setCreator(creator);
						}
					}
				}
				catch (Exception e) {
					log.debug("Could not set creator for indicator: {}", e.getMessage());
				}
			}
			if (node.has("dateCreated") && !node.get("dateCreated").isNull()) {
				try {
					long dateCreated = node.get("dateCreated").asLong();
					indicator.setDateCreated(new java.util.Date(dateCreated));
				}
				catch (Exception e) {
					log.debug("Could not set dateCreated for indicator: {}", e.getMessage());
				}
			}
			// Set changedBy to current user for new/updated records
			indicator.setChangedBy(org.openmrs.api.context.Context.getAuthenticatedUser());
			indicator.setDateChanged(new java.util.Date());
			
			saveReportBuilderIndicator(indicator);
			
			log.info("[IMPORT] Successfully created indicator: {} ({})", indicator.getName(), file.getName());
		}
	}
	
	/**
	 * Import a ReportBuilderSection entity - reads database model format
	 */
	private void importSection(File file) throws IOException {
		com.fasterxml.jackson.databind.JsonNode node = readEntityFile(file);
		
		String uuid = node.get("uuid").asText();
		ReportBuilderSection existing = getReportBuilderSectionByUuid(uuid);
		
		// Extract configJson as JSON string (export uses camelCase)
		String configJson = null;
		if (node.has("configJson") && !node.get("configJson").isNull()) {
			configJson = objectMapper.writeValueAsString(node.get("configJson"));
		}
		
		if (existing != null) {
			// Update existing
			existing.setName(node.get("name").asText());
			if (node.has("description") && !node.get("description").isNull()) {
				existing.setDescription(node.get("description").asText());
			}
			if (node.has("code") && !node.get("code").isNull()) {
				existing.setCode(node.get("code").asText());
			}
			if (configJson != null) {
				existing.setConfigJson(configJson);
			}
			saveReportBuilderSection(existing);
			log.debug("Updated existing section: {}", existing.getName());
		} else {
			// Create new
			ReportBuilderSection section = new ReportBuilderSection();
			section.setUuid(uuid);
			section.setName(node.get("name").asText());
			if (node.has("description") && !node.get("description").isNull()) {
				section.setDescription(node.get("description").asText());
			}
			section.setCode(deriveCode(node));
			if (configJson != null) {
				section.setConfigJson(configJson);
			}
			saveReportBuilderSection(section);
			log.debug("Created new section: {}", section.getName());
		}
	}
	
	/**
	 * Import a ReportBuilderDataTheme entity
	 */
	private void importTheme(File file) throws IOException {
		com.fasterxml.jackson.databind.JsonNode node = readEntityFile(file);
		
		String uuid = node.get("uuid").asText();
		ReportBuilderDataTheme existing = getReportBuilderDataThemeByUuid(uuid);
		
		// Extract config/meta JSON strings (the export serializes entity beans in camelCase; older
		// packages used the snake_case database-model names)
		String configJson = extractJsonStringField(node, "config_json", "configJson");
		String metaJson = extractJsonStringField(node, "meta_json", "metaJson");
		String domain = extractJsonStringField(node, "domain");
		
		if (existing != null) {
			// Update existing
			existing.setName(node.get("name").asText());
			if (node.has("description") && !node.get("description").isNull()) {
				existing.setDescription(node.get("description").asText());
			}
			if (node.has("code") && !node.get("code").isNull()) {
				existing.setCode(node.get("code").asText());
			}
			if (domain != null) {
				existing.setDomain(domain);
			}
			if (configJson != null) {
				existing.setConfigJson(configJson);
			}
			if (metaJson != null) {
				existing.setMetaJson(metaJson);
			}
			saveReportBuilderDataTheme(existing);
			log.debug("Updated existing theme: {}", existing.getName());
		} else {
			// Create new
			ReportBuilderDataTheme theme = new ReportBuilderDataTheme();
			theme.setUuid(uuid);
			theme.setName(node.get("name").asText());
			if (node.has("description") && !node.get("description").isNull()) {
				theme.setDescription(node.get("description").asText());
			}
			theme.setCode(deriveCode(node));
			if (domain != null) {
				theme.setDomain(domain);
			}
			if (configJson != null) {
				theme.setConfigJson(configJson);
			}
			if (metaJson != null) {
				theme.setMetaJson(metaJson);
			}
			saveReportBuilderDataTheme(theme);
			log.debug("Created new theme: {}", theme.getName());
		}
	}
	
	/**
	 * Import a ReportBuilderAgeCategory entity - reads database model format
	 */
	private void importAgeCategory(File file) throws IOException {
		com.fasterxml.jackson.databind.JsonNode node = readEntityFile(file);

		String uuid = node.get("uuid").asText();
		ReportBuilderAgeCategory existing = getAgeCategoryByUuid(uuid);

		ReportBuilderAgeCategory savedCategory;
		if (existing != null) {
			// Update existing
			existing.setName(node.get("name").asText());
			if (node.has("description") && !node.get("description").isNull()) {
				existing.setDescription(node.get("description").asText());
			}
			if (node.has("code") && !node.get("code").isNull()) {
				existing.setCode(node.get("code").asText());
			}
			saveAgeCategory(existing);
			log.debug("Updated existing age category: {}", existing.getName());
			savedCategory = existing;
		} else {
			// Create new
			ReportBuilderAgeCategory category = new ReportBuilderAgeCategory();
			category.setUuid(uuid);
			category.setName(node.get("name").asText());
			if (node.has("description") && !node.get("description").isNull()) {
				category.setDescription(node.get("description").asText());
			}
			category.setCode(deriveCode(node));
			saveAgeCategory(category);
			log.debug("Created new age category: {}", category.getName());
			savedCategory = category;
		}

		// Age groups belong to their age category - current exports embed them in the category file,
		// and they are imported here in the same transaction so the family lands together
		com.fasterxml.jackson.databind.JsonNode embeddedGroups = node.get("ageGroups");
		if (embeddedGroups != null && embeddedGroups.isArray()) {
			for (com.fasterxml.jackson.databind.JsonNode groupNode : embeddedGroups) {
				upsertAgeGroup(savedCategory, groupNode);
			}
		}
	}
	
	/**
	 * Import a ReportBuilderAgeGroup entity Note: AgeGroups don't have UUID, use category + label
	 * for deduplication
	 */
	/**
	 * Imports one standalone age-group file. Only legacy packages carry separate age-group files -
	 * current exports embed the groups in their age category, which {@link #importAgeCategory(File)}
	 * imports together with them.
	 */
	private void importAgeGroup(File file) throws IOException {
		com.fasterxml.jackson.databind.JsonNode node = readEntityFile(file);
		
		String code = extractJsonStringField(node, "code");
		String label = extractJsonStringField(node, "label");
		
		// Resolve the parent category: the export writes the category as a uuid string (or a nested
		// entity), older packages carry a category_uuid field
		com.fasterxml.jackson.databind.JsonNode categoryRef = firstNonNullNode(node, "category_uuid", "categoryUuid");
		if (categoryRef == null) {
			com.fasterxml.jackson.databind.JsonNode nested = node.get("ageCategory");
			if (nested != null && !nested.isNull()) {
				if (nested.isObject()) {
					categoryRef = firstNonNullNode(nested, "uuid");
				} else if (nested.isTextual() && !nested.asText().trim().isEmpty()) {
					categoryRef = nested;
				}
			}
		}
		ReportBuilderAgeCategory category = categoryRef != null ? getAgeCategoryByUuid(categoryRef.asText()) : null;
		if (category == null) {
			throw new APIException("Age group '" + (label != null ? label : code)
			        + "' references an age category that has not been imported: "
			        + (categoryRef != null ? categoryRef.asText() : "none"));
		}
		
		upsertAgeGroup(category, node);
	}
	
	/**
	 * Creates or updates one age group of the given category from its serialized form. Age groups
	 * have no uuid - dedup is by code, falling back to label, within the category.
	 */
	private void upsertAgeGroup(ReportBuilderAgeCategory category, com.fasterxml.jackson.databind.JsonNode node)
	        throws IOException {
		String code = extractJsonStringField(node, "code");
		String label = extractJsonStringField(node, "label");
		if (code == null && label == null) {
			throw new APIException("Age group has neither code nor label");
		}
		
		Integer minAgeDays = readIntField(node, "min_age_days", "minAgeDays");
		Integer maxAgeDays = readIntField(node, "max_age_days", "maxAgeDays");
		Integer sortOrder = readIntField(node, "sort_order", "sortOrder");
		com.fasterxml.jackson.databind.JsonNode activeNode = firstNonNullNode(node, "active");
		
		ReportBuilderAgeGroup existing = null;
		for (ReportBuilderAgeGroup group : getAgeGroupsByCategoryUuid(category.getUuid(), null)) {
			if ((code != null && code.equals(group.getCode())) || (label != null && label.equals(group.getLabel()))) {
				existing = group;
				break;
			}
		}
		
		if (existing == null) {
			ReportBuilderAgeGroup group = new ReportBuilderAgeGroup();
			group.setAgeCategory(category);
			group.setCode(code != null ? code : label);
			group.setLabel(label != null ? label : code);
			applyAgeGroupFields(group, minAgeDays, maxAgeDays, sortOrder != null ? sortOrder : Integer.valueOf(0),
			    activeNode);
			saveAgeGroup(group);
			log.debug("Created new age group: {}", group.getLabel());
		} else {
			if (code != null) {
				existing.setCode(code);
			}
			if (label != null) {
				existing.setLabel(label);
			}
			applyAgeGroupFields(existing, minAgeDays, maxAgeDays, sortOrder, activeNode);
			saveAgeGroup(existing);
			log.debug("Updated existing age group: {}", existing.getLabel());
		}
	}
	
	/**
	 * Applies the optional numeric/flag fields of an age group, preserving existing values when the
	 * file omits them.
	 */
	private void applyAgeGroupFields(ReportBuilderAgeGroup group, Integer minAgeDays, Integer maxAgeDays, Integer sortOrder,
	        com.fasterxml.jackson.databind.JsonNode activeNode) {
		if (minAgeDays != null) {
			group.setMinAgeDays(minAgeDays);
		}
		if (maxAgeDays != null) {
			group.setMaxAgeDays(maxAgeDays);
		}
		if (sortOrder != null) {
			group.setSortOrder(sortOrder);
		}
		if (activeNode != null) {
			group.setActive(activeNode.asBoolean());
		}
	}
	
	/**
	 * Import an ETLSource entity
	 */
	private void importETLSource(File file) throws IOException {
		com.fasterxml.jackson.databind.JsonNode node = readEntityFile(file);
		
		String uuid = node.get("uuid").asText();
		ETLSource existing = getETLSourceByUuid(uuid);
		
		if (existing != null) {
			// Update existing
			existing.setName(node.get("name").asText());
			if (node.has("description") && !node.get("description").isNull()) {
				existing.setDescription(node.get("description").asText());
			}
			if (node.has("code") && !node.get("code").isNull()) {
				existing.setCode(node.get("code").asText());
			}
			saveETLSource(existing);
			log.debug("Updated existing ETL source: {}", existing.getName());
		} else {
			// Create new
			ETLSource source = new ETLSource();
			source.setUuid(uuid);
			source.setName(node.get("name").asText());
			if (node.has("description") && !node.get("description").isNull()) {
				source.setDescription(node.get("description").asText());
			}
			source.setCode(deriveCode(node));
			saveETLSource(source);
			log.debug("Created new ETL source: {}", source.getName());
		}
	}
	
	/**
	 * Import an ETLMonitor entity - reads database model format
	 */
	private void importETLMonitor(File file) throws IOException {
		com.fasterxml.jackson.databind.JsonNode node = readEntityFile(file);
		
		String uuid = node.get("uuid").asText();
		ETLMonitor existing = getETLMonitorByUuid(uuid);
		
		// Extract config/display config JSON strings (the export serializes entity beans in
		// camelCase; older packages used the snake_case database-model names)
		String configJson = extractJsonStringField(node, "config_json", "configJson");
		String displayConfigJson = extractJsonStringField(node, "display_config_json", "displayConfigJson");
		
		if (existing != null) {
			// Update existing
			existing.setName(node.get("name").asText());
			if (node.has("description") && !node.get("description").isNull()) {
				existing.setDescription(node.get("description").asText());
			}
			if (node.has("code") && !node.get("code").isNull()) {
				existing.setCode(node.get("code").asText());
			}
			if (configJson != null) {
				existing.setConfigJson(configJson);
			}
			if (displayConfigJson != null) {
				existing.setDisplayConfigJson(displayConfigJson);
			}
			saveETLMonitor(existing);
			log.debug("Updated existing ETL monitor: {}", existing.getName());
		} else {
			// Create new
			ETLMonitor monitor = new ETLMonitor();
			monitor.setUuid(uuid);
			monitor.setName(node.get("name").asText());
			if (node.has("description") && !node.get("description").isNull()) {
				monitor.setDescription(node.get("description").asText());
			}
			monitor.setCode(deriveCode(node));
			if (configJson != null) {
				monitor.setConfigJson(configJson);
			}
			if (displayConfigJson != null) {
				monitor.setDisplayConfigJson(displayConfigJson);
			}
			saveETLMonitor(monitor);
			log.debug("Created new ETL monitor: {}", monitor.getName());
		}
	}
	
	/**
	 * Import a ReportBuilderDashboard entity - reads database model format. Accepts both camelCase
	 * (this module's export shape) and snake_case config keys for cross-version packages.
	 */
	private void importDashboard(File file) throws IOException {
		com.fasterxml.jackson.databind.JsonNode node = readEntityFile(file);
		
		String uuid = node.get("uuid").asText();
		ReportBuilderDashboard existing = getReportBuilderDashboardByUuid(uuid);
		if (existing == null && node.has("code") && !node.get("code").isNull() && hasText(node.get("code").asText())) {
			existing = getReportBuilderDashboardByCode(node.get("code").asText());
		}
		
		String configJson = extractJsonStringField(node, "configJson", "config_json");
		ReportBuilderDashboard.DashboardType dashboardType = null;
		for (String key : new String[] { "dashboardType", "dashboard_type" }) {
			if (dashboardType == null && node.has(key) && !node.get(key).isNull()) {
				try {
					dashboardType = ReportBuilderDashboard.DashboardType.valueOf(node.get(key).asText());
				}
				catch (IllegalArgumentException e) {
					log.warn("Invalid dashboard type value: {}", node.get(key).asText());
				}
			}
		}
		
		if (existing != null) {
			// Update existing - all fields
			existing.setName(node.get("name").asText());
			if (node.has("description") && !node.get("description").isNull()) {
				existing.setDescription(node.get("description").asText());
			}
			if (node.has("code") && !node.get("code").isNull()) {
				if (node.has("code") && !node.get("code").isNull()) {
					existing.setCode(node.get("code").asText());
				}
			}
			if (dashboardType != null) {
				existing.setDashboardType(dashboardType);
			}
			if (configJson != null) {
				existing.setConfigJson(configJson);
			}
			if (node.has("active") && !node.get("active").isNull()) {
				existing.setActive(node.get("active").asBoolean());
			}
			if (node.has("sortOrder") && !node.get("sortOrder").isNull()) {
				existing.setSortOrder(node.get("sortOrder").asInt());
			}
			if (node.has("retired")) {
				existing.setRetired(node.get("retired").asBoolean());
				if (node.has("retireReason") && !node.get("retireReason").isNull()) {
					existing.setRetireReason(node.get("retireReason").asText());
				}
			}
			saveReportBuilderDashboard(existing);
			log.debug("Updated existing dashboard: {}", existing.getName());
		} else {
			// Create new - all fields
			ReportBuilderDashboard dashboard = new ReportBuilderDashboard();
			dashboard.setUuid(uuid);
			dashboard.setName(node.get("name").asText());
			if (node.has("description") && !node.get("description").isNull()) {
				dashboard.setDescription(node.get("description").asText());
			}
			if (node.has("code") && !node.get("code").isNull()) {
				dashboard.setCode(node.get("code").asText());
			}
			if (dashboardType != null) {
				dashboard.setDashboardType(dashboardType);
			}
			if (configJson != null) {
				dashboard.setConfigJson(configJson);
			}
			if (node.has("active") && !node.get("active").isNull()) {
				dashboard.setActive(node.get("active").asBoolean());
			}
			if (node.has("sortOrder") && !node.get("sortOrder").isNull()) {
				dashboard.setSortOrder(node.get("sortOrder").asInt());
			}
			saveReportBuilderDashboard(dashboard);
			
			// Handle retired status after save
			if (node.has("retired") && node.get("retired").asBoolean()) {
				String reason = node.has("retireReason") && !node.get("retireReason").isNull() ? node.get("retireReason")
				        .asText() : "Imported as retired";
				retireReportBuilderDashboard(dashboard, reason);
			}
			
			log.debug("Created new dashboard: {}", dashboard.getName());
		}
	}
	
	/**
	 * Extracts a JSON-valued field as its raw string content. Text nodes (this module's export
	 * shape for config fields) are returned verbatim; structured nodes (cross-version packages
	 * embedding the JSON object) are re-serialized.
	 */
	private String extractJsonStringField(com.fasterxml.jackson.databind.JsonNode node, String... names) throws IOException {
		for (String name : names) {
			com.fasterxml.jackson.databind.JsonNode field = node.get(name);
			if (field != null && !field.isNull()) {
				if (field.isTextual()) {
					String text = field.textValue();
					return text == null || text.trim().isEmpty() ? null : text;
				}
				return objectMapper.writeValueAsString(field);
			}
		}
		return null;
	}
	
	/**
	 * Returns the first present, non-null field among the given names, tolerating packages that
	 * name fields in the snake_case database-model format or the camelCase entity-bean format.
	 */
	private com.fasterxml.jackson.databind.JsonNode firstNonNullNode(com.fasterxml.jackson.databind.JsonNode node,
	        String... names) {
		for (String name : names) {
			com.fasterxml.jackson.databind.JsonNode field = node.get(name);
			if (field != null && !field.isNull()) {
				return field;
			}
		}
		return null;
	}
	
	/**
	 * Resolves the category uuid of an exported report: an explicit category_uuid/categoryUuid
	 * field, or the uuid of the nested category entity written by the export.
	 */
	private String resolveCategoryUuid(com.fasterxml.jackson.databind.JsonNode node) throws IOException {
		com.fasterxml.jackson.databind.JsonNode ref = firstNonNullNode(node, "category_uuid", "categoryUuid");
		if (ref == null) {
			com.fasterxml.jackson.databind.JsonNode nested = node.get("category");
			if (nested != null && nested.isObject()) {
				return extractJsonStringField(nested, "uuid");
			}
			return null;
		}
		return ref.asText();
	}
	
	/**
	 * Applies the lastCompiledAt value, accepting either epoch millis (entity-bean export) or an
	 * ISO 8601 timestamp (older packages).
	 */
	private void applyLastCompiledAt(ReportBuilderReport report, com.fasterxml.jackson.databind.JsonNode field) {
		if (field == null) {
			return;
		}
		try {
			if (field.isNumber()) {
				report.setLastCompiledAt(new java.util.Date(field.asLong()));
			} else {
				report.setLastCompiledAt(new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(field.asText()));
			}
		}
		catch (Exception e) {
			log.warn("Invalid lastCompiledAt value: {}", field.asText());
		}
	}
	
	/**
	 * Reads an integer field tolerating snake_case and camelCase names.
	 */
	private Integer readIntField(com.fasterxml.jackson.databind.JsonNode node, String... names) {
		com.fasterxml.jackson.databind.JsonNode field = firstNonNullNode(node, names);
		if (field == null) {
			return null;
		}
		try {
			return Integer.valueOf(field.asText().trim());
		}
		catch (NumberFormatException e) {
			return null;
		}
	}
	
	/**
	 * Import a ReportBuilderReport entity - reads database model format Enhanced to import all
	 * fields that are exported
	 */
	private void importReport(File file) throws IOException {
		com.fasterxml.jackson.databind.JsonNode node = readEntityFile(file);
		
		String uuid = node.get("uuid").asText();
		ReportBuilderReport existing = getReportBuilderReportByUuid(uuid);
		
		// Extract config/meta JSON strings (the export serializes entity beans in camelCase; older
		// packages used the snake_case database-model names)
		String configJson = extractJsonStringField(node, "config_json", "configJson");
		String metaJson = extractJsonStringField(node, "meta_json", "metaJson");
		String reportType = extractJsonStringField(node, "report_type", "reportType");
		String compiledDefinitionUuid = extractJsonStringField(node, "compiled_report_definition_uuid",
		    "compiledReportDefinitionUuid");
		String compiledDesignUuid = extractJsonStringField(node, "compiled_report_design_uuid", "compiledReportDesignUuid");
		String compileStatus = extractJsonStringField(node, "compile_status", "compileStatus");
		String categoryUuid = resolveCategoryUuid(node);
		com.fasterxml.jackson.databind.JsonNode lastCompiledAt = firstNonNullNode(node, "last_compiled_at", "lastCompiledAt");
		
		if (existing != null) {
			// Update existing - all fields
			existing.setName(node.get("name").asText());
			if (node.has("description") && !node.get("description").isNull()) {
				existing.setDescription(node.get("description").asText());
			}
			if (node.has("code") && !node.get("code").isNull()) {
				existing.setCode(node.get("code").asText());
			}
			if (configJson != null) {
				existing.setConfigJson(configJson);
			}
			if (metaJson != null) {
				existing.setMetaJson(metaJson);
			}
			
			// Handle category reference
			if (categoryUuid != null) {
				ReportCategory category = getReportCategoryByUuid(categoryUuid);
				if (category != null) {
					existing.setCategory(category);
				}
			}
			
			if (reportType != null) {
				existing.setReportType(reportType);
			}
			
			if (compiledDefinitionUuid != null) {
				existing.setCompiledReportDefinitionUuid(compiledDefinitionUuid);
			}
			
			if (compiledDesignUuid != null) {
				existing.setCompiledReportDesignUuid(compiledDesignUuid);
			}
			
			applyLastCompiledAt(existing, lastCompiledAt);
			
			if (compileStatus != null) {
				try {
					existing.setCompileStatus(ReportBuilderReport.ReportCompileStatus.valueOf(compileStatus));
				}
				catch (IllegalArgumentException e) {
					log.warn("Invalid compileStatus value: {}", compileStatus);
				}
			}
			
			// Handle retired status
			if (node.has("retired")) {
				existing.setRetired(node.get("retired").asBoolean());
				if (node.has("retire_reason") && !node.get("retire_reason").isNull()) {
					dao.retireReportBuilderReport(existing, node.get("retire_reason").asText());
				}
			} else {
				dao.unretireReportBuilderReport(existing);
			}
			
			saveReportBuilderReport(existing);
			log.debug("Updated existing report: {}", existing.getName());
		} else {
			// Create new - all fields
			ReportBuilderReport report = new ReportBuilderReport();
			report.setUuid(uuid);
			report.setName(node.get("name").asText());
			if (node.has("description") && !node.get("description").isNull()) {
				report.setDescription(node.get("description").asText());
			}
			report.setCode(deriveCode(node));
			if (configJson != null) {
				report.setConfigJson(configJson);
			}
			if (metaJson != null) {
				report.setMetaJson(metaJson);
			}
			
			if (reportType != null) {
				report.setReportType(reportType);
			}
			
			// Handle category reference
			if (categoryUuid != null) {
				ReportCategory category = getReportCategoryByUuid(categoryUuid);
				if (category != null) {
					report.setCategory(category);
				}
			}
			
			if (compiledDefinitionUuid != null) {
				report.setCompiledReportDefinitionUuid(compiledDefinitionUuid);
			}
			
			if (compiledDesignUuid != null) {
				report.setCompiledReportDesignUuid(compiledDesignUuid);
			}
			
			applyLastCompiledAt(report, lastCompiledAt);
			
			if (compileStatus != null) {
				try {
					report.setCompileStatus(ReportBuilderReport.ReportCompileStatus.valueOf(compileStatus));
				}
				catch (IllegalArgumentException e) {
					log.warn("Invalid compileStatus value: {}", compileStatus);
				}
			}
			
			saveReportBuilderReport(report);
			
			// Handle retired status after save
			if (node.has("retired") && node.get("retired").asBoolean()) {
				if (node.has("retire_reason") && !node.get("retire_reason").isNull()) {
					retireReportBuilderReport(report, node.get("retire_reason").asText());
				}
			}
			
			log.debug("Created new report: {}", report.getName());
		}
	}
	
	// ========== Report Package Methods ==========
	
	@Override
	@Transactional(readOnly = true)
	public java.util.List<PackageInfo> getAvailablePackages(
	        String search, String status, Integer startIndex, Integer limit) {
		java.util.List<PackageInfo> packages = new java.util.ArrayList<>();

		try {
			// Get the packages directory
			File shippingDir = getDefaultShippingDirectory();
			File packagesDir = new File(shippingDir, "configuration" + File.separator + "reportbuilder");

			if (!packagesDir.exists() || !packagesDir.isDirectory()) {
				log.debug("Packages directory does not exist: {}", packagesDir.getAbsolutePath());
				return packages;
			}

			// List all subdirectories (each is a potential package)
			File[] packageDirs = packagesDir.listFiles();
			if (packageDirs == null) {
				return packages;
			}

			// Process each directory
			for (File packageDir : packageDirs) {
				if (!packageDir.isDirectory()) {
					continue;
				}

				try {
					PackageInfo packageInfo = buildPackageInfo(packageDir);
					if (packageInfo != null && matchesFilters(packageInfo, search, status)) {
						packages.add(packageInfo);
					}
				}
				catch (Exception e) {
					log.warn("Failed to read package from directory: {}", packageDir.getAbsolutePath(), e);
				}
			}

			// Sort by exported date descending
			java.util.Collections.sort(packages, new java.util.Comparator<PackageInfo>() {
				@Override
				public int compare(PackageInfo p1,
				        PackageInfo p2) {
					if (p1.getExportedAt() == null) return 1;
					if (p2.getExportedAt() == null) return -1;
					return p2.getExportedAt().compareTo(p1.getExportedAt());
				}
			});

			// Apply pagination
			int start = startIndex != null ? startIndex : 0;
			int maxResults = limit != null ? limit : 50;

			int end = Math.min(start + maxResults, packages.size());
			if (start >= packages.size()) {
				return new java.util.ArrayList<PackageInfo>();
			}

			return packages.subList(start, end);

		}
		catch (Exception e) {
			log.error("Failed to get available packages", e);
			return packages;
		}
	}
	
	@Override
	@Transactional(readOnly = true)
	public long getAvailablePackagesCount(String search, String status) {
		try {
			File shippingDir = getDefaultShippingDirectory();
			File packagesDir = new File(shippingDir, "configuration" + File.separator + "reportbuilder");
			
			if (!packagesDir.exists() || !packagesDir.isDirectory()) {
				return 0;
			}
			
			File[] packageDirs = packagesDir.listFiles();
			if (packageDirs == null) {
				return 0;
			}
			
			long count = 0;
			for (File packageDir : packageDirs) {
				if (!packageDir.isDirectory()) {
					continue;
				}
				
				try {
					PackageInfo packageInfo = buildPackageInfo(packageDir);
					if (packageInfo != null && matchesFilters(packageInfo, search, status)) {
						count++;
					}
				}
				catch (Exception e) {
					log.warn("Failed to read package from directory: {}", packageDir.getAbsolutePath(), e);
				}
			}
			
			return count;
			
		}
		catch (Exception e) {
			log.error("Failed to get available packages count", e);
			return 0;
		}
	}
	
	/**
	 * Build PackageInfo from a package directory
	 */
	private PackageInfo buildPackageInfo(File packageDir) {
		PackageInfo packageInfo = new PackageInfo();
		packageInfo.setPath(packageDir.getAbsolutePath());
		packageInfo.setStatus("invalid"); // Default to invalid
		
		try {
			// Read version.json
			File versionFile = new File(packageDir, "version.json");
			if (!versionFile.exists()) {
				log.warn("Package missing version.json: {}", packageDir.getAbsolutePath());
				packageInfo.setName(packageDir.getName());
				return packageInfo;
			}
			
			// Parse version.json
			VersionMetadata metadata = objectMapper.readValue(versionFile, VersionMetadata.class);
			
			// Extract package info
			if (metadata.getPackageInfo() != null) {
				packageInfo.setName(metadata.getPackageInfo().getName());
				packageInfo.setVersion(metadata.getPackageInfo().getVersion());
				packageInfo.setDescription(metadata.getPackageInfo().getDescription());
				packageInfo.setExportedAt(metadata.getPackageInfo().getExportedAt());
				packageInfo.setExportedBy(metadata.getPackageInfo().getExportedBy());
			} else {
				packageInfo.setName(packageDir.getName());
			}
			
			// Extract dependency counts
			if (metadata.getContents() != null && metadata.getContents().getDependencies() != null) {
				PackageDependencySummary summary = new PackageDependencySummary();
				VersionMetadata.DependencyInfo deps = metadata.getContents().getDependencies();
				
				summary.setCategories(deps.getCategories() != null ? deps.getCategories().size() : 0);
				summary.setIndicators(deps.getIndicators() != null ? deps.getIndicators().size() : 0);
				summary.setThemes(deps.getThemes() != null ? deps.getThemes().size() : 0);
				summary.setSections(deps.getSections() != null ? deps.getSections().size() : 0);
				summary.setLibrary(deps.getLibrary() != null ? deps.getLibrary().size() : 0);
				summary.setAgeCategories(deps.getAgeCategories() != null ? deps.getAgeCategories().size() : 0);
				summary.setAgeGroups(deps.getAgeGroups() != null ? deps.getAgeGroups().size() : 0);
				summary.setEtlSources(deps.getEtlSources() != null ? deps.getEtlSources().size() : 0);
				summary.setEtlMonitors(deps.getEtlMonitors() != null ? deps.getEtlMonitors().size() : 0);
				
				packageInfo.setDependencies(summary);
			}
			
			// Calculate directory size
			packageInfo.setSize(calculateDirectorySize(packageDir));
			
			// Validate package structure
			packageInfo.setStatus(validatePackageStructure(packageDir, metadata) ? "valid" : "invalid");
			
		}
		catch (Exception e) {
			log.warn("Failed to parse version.json for package: {}", packageDir.getAbsolutePath(), e);
			packageInfo.setName(packageDir.getName());
			packageInfo.setStatus("invalid");
		}
		
		return packageInfo;
	}
	
	/**
	 * Validate package structure
	 */
	private boolean validatePackageStructure(File packageDir, VersionMetadata metadata) {
		// Must have valid metadata with name and version
		if (metadata.getPackageInfo() == null) {
			return false;
		}
		if (metadata.getPackageInfo().getName() == null || metadata.getPackageInfo().getName().trim().isEmpty()) {
			return false;
		}
		if (metadata.getPackageInfo().getVersion() == null || metadata.getPackageInfo().getVersion().trim().isEmpty()) {
			return false;
		}
		
		// Must have at least one report source file
		File configDir = new File(packageDir, "configuration");
		File reportbuilderDir = new File(configDir, "reportbuilder");
		if (!reportbuilderDir.exists() || !reportbuilderDir.isDirectory()) {
			return false;
		}
		
		File reportsDir = new File(reportbuilderDir, "reports");
		if (reportsDir.exists() && reportsDir.isDirectory()) {
			File[] reportFiles = reportsDir.listFiles();
			if (reportFiles != null && reportFiles.length > 0) {
				return true;
			}
		}
		
		return false;
	}
	
	/**
	 * Calculate total size of a directory recursively
	 */
	private long calculateDirectorySize(File directory) {
		long size = 0;
		File[] files = directory.listFiles();
		if (files != null) {
			for (File file : files) {
				if (file.isFile()) {
					size += file.length();
				} else {
					size += calculateDirectorySize(file);
				}
			}
		}
		return size;
	}
	
	/**
	 * Check if package matches the given filters
	 */
	private boolean matchesFilters(PackageInfo packageInfo, String search, String status) {
		// Filter by search term
		if (search != null && !search.trim().isEmpty()) {
			String searchLower = search.toLowerCase();
			boolean matchesName = packageInfo.getName() != null && packageInfo.getName().toLowerCase().contains(searchLower);
			boolean matchesVersion = packageInfo.getVersion() != null
			        && packageInfo.getVersion().toLowerCase().contains(searchLower);
			if (!matchesName && !matchesVersion) {
				return false;
			}
		}
		
		// Filter by status
		if (status != null && !status.trim().isEmpty()) {
			if (!status.equals(packageInfo.getStatus())) {
				return false;
			}
		}
		
		return true;
	}
}
