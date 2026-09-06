package org.openmrs.module.reportbuilder.web.controller;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.Cohort;
import org.openmrs.PersonAttributeType;
import org.openmrs.api.APIAuthenticationException;
import org.openmrs.api.CohortService;
import org.openmrs.api.ConceptService;
import org.openmrs.api.PatientService;
import org.openmrs.api.context.Context;
import org.openmrs.module.reportbuilder.security.ReportBuilderPrivileges;
import org.openmrs.module.reporting.common.DateUtil;
import org.openmrs.module.reporting.common.ReflectionUtil;
import org.openmrs.module.reporting.cohort.EvaluatedCohort;
import org.openmrs.module.reporting.cohort.definition.CohortDefinition;
import org.openmrs.module.reporting.cohort.definition.service.CohortDefinitionService;
import org.openmrs.module.reporting.dataset.DataSet;
import org.openmrs.module.reporting.dataset.SimpleDataSet;
import org.openmrs.module.reporting.dataset.definition.PatientDataSetDefinition;
import org.openmrs.module.reporting.evaluation.EvaluationContext;
import org.openmrs.module.reporting.evaluation.parameter.Mapped;
import org.openmrs.module.reporting.report.ReportDesign;
import org.openmrs.module.reporting.report.ReportRequest;
import org.openmrs.module.reporting.report.definition.ReportDefinition;
import org.openmrs.module.reporting.report.definition.service.ReportDefinitionService;
import org.openmrs.module.reporting.report.renderer.RenderingMode;
import org.openmrs.module.reporting.report.service.ReportService;
import org.openmrs.reporting.export.DataExportUtil;
import org.openmrs.reporting.export.DataExportReportObject;
import org.openmrs.module.reportbuilder.web.resources.mapper.Column;
import org.openmrs.module.reportbuilder.web.resources.mapper.DataExportMapper;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.RequestContext;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.util.ReportingcompatibilityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * REST controller for dynamic data export functionality Replaces legacy ugandaemr-reports
 * DataExportRestController
 */
@Controller
@RequestMapping(value = "/rest/" + RestConstants.VERSION_1 + DataExportController.REPORTBUILDER
        + DataExportController.DATA_EXPORT)
public class DataExportController {
	
	public static final String REPORTBUILDER = "/reportbuilder";
	
	public static final String DATA_EXPORT = "/dataExport";
	
	@Autowired
	public GenericConversionService conversionService;
	
	@Autowired
	public ReportService reportService;
	
	/**
	 * Data export endpoint - generates Excel exports based on cohort and column definitions
	 * 
	 * @param payload DataExportMapper containing cohort and columns
	 * @param requestContext Request context
	 * @return Excel file download or error response
	 */
	@ExceptionHandler(APIAuthenticationException.class)
	@RequestMapping(method = RequestMethod.POST, consumes = "application/json")
	@ResponseBody
	public Object exportData(@RequestBody DataExportMapper payload, RequestContext requestContext) {
		Context.requirePrivilege("Task: reportbuilder.report.run");
		
		org.openmrs.module.reportbuilder.web.resources.mapper.Cohort reportCohort = payload.getCohort();
		List<Column> columnList = payload.getColumns();
		
		EvaluationContext context = new EvaluationContext();
		SimpleDataSet dataSet = new SimpleDataSet(new PatientDataSetDefinition(), context);
		Cohort baseCohort = new Cohort();
		List<Map<String, Object>> parameters = reportCohort.getParameters();
		
		Map<String, Object> cohortParameters = getParameters(parameters);
		DataExportReportObject exportReportObject = new DataExportReportObject();
		List<Integer> patientIds = new ArrayList<Integer>();
		
		context.setParameterValues(cohortParameters);
		if (reportCohort.getUuid() != null && !columnList.isEmpty() && reportCohort.getType() != null) {
			String cohortType = reportCohort.getType();
			try {
				if ("Report".equals(cohortType)) {
					ReportDefinitionService service = Context.getService(ReportDefinitionService.class);
					ReportDefinition rd = service.getDefinitionByUuid(reportCohort.getUuid());
					
					if (rd != null) {
						Mapped<? extends CohortDefinition> cd = rd.getBaseCohortDefinition();
						if (cd != null) {
							ReflectionUtil.setPropertyValue(cd, "startDate", cohortParameters.get("startDate"));
							ReflectionUtil.setPropertyValue(cd, "endDate", cohortParameters.get("endDate"));
							EvaluatedCohort evaluatedCohort = Context.getService(CohortDefinitionService.class).evaluate(cd,
							    context);
							baseCohort.setMemberIds(evaluatedCohort.getMemberIds());
						}
					}
					patientIds.addAll(baseCohort.getMemberIds());
					exportReportObject.setPatientIds(patientIds);
					
				} else if ("Cohort".equals(cohortType)) {
					CohortService cohortService = Context.getCohortService();
					baseCohort = cohortService.getCohortByUuid(reportCohort.getUuid());
					patientIds.addAll(baseCohort.getMemberIds());
					exportReportObject.setPatientIds(patientIds);
					
				} else if ("Program".equals(cohortType)) {
					// Program-based cohorts not supported yet
					SimpleObject errorResponse = new SimpleObject();
					errorResponse.put("error", "Program-based cohorts are not yet supported");
					return new ResponseEntity<Object>(errorResponse, HttpStatus.NOT_IMPLEMENTED);
					
				} else if ("Patient Search".equals(cohortType)) {
					// Patient search cohorts would require additional service
					SimpleObject errorResponse = new SimpleObject();
					errorResponse.put("error", "Patient Search cohorts require legacy module support");
					return new ResponseEntity<Object>(errorResponse, HttpStatus.NOT_IMPLEMENTED);
				}
				
				addColumnsToDataExportObject(columnList, exportReportObject);
				exportReportObject.setName(reportCohort.getName());
				context.setParameterValues(cohortParameters);
				
				// Generate Excel export
				DataExportUtil.generateExport(exportReportObject, ReportingcompatibilityUtil.convert(baseCohort), null);
				
				File file = DataExportUtil.getGeneratedFile(exportReportObject);
				
				String s = new SimpleDateFormat("yyyyMMdd_Hm").format(new Date(file.lastModified()));
				String filename = exportReportObject.getName().replace(" ", "_") + "-" + s + ".xls";
				
				return ResponseEntity.ok().header(HttpHeaders.CONTENT_TYPE, "application/vnd.ms-excel")
				        .header(HttpHeaders.PRAGMA, "no-cache")
				        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename).body(file);
				
			}
			catch (Exception e) {
				SimpleObject errorResponse = new SimpleObject();
				errorResponse.put("error", e.getMessage());
				return new ResponseEntity<Object>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
			}
		} else {
			SimpleObject errorResponse = new SimpleObject();
			errorResponse.put("error", "Missing cohort or column list for this report");
			return new ResponseEntity<Object>(errorResponse, HttpStatus.BAD_REQUEST);
		}
	}
	
	/**
	 * Get data as JSON instead of Excel download
	 * 
	 * @param payload DataExportMapper containing cohort and columns
	 * @param requestContext Request context
	 * @return JSON response with patient data
	 */
	@RequestMapping(method = RequestMethod.POST, consumes = "application/json", params = "format=json")
	@ResponseBody
	public Object exportDataAsJson(@RequestBody DataExportMapper payload, RequestContext requestContext) {
		Context.requirePrivilege("Task: reportbuilder.report.run");
		
		org.openmrs.module.reportbuilder.web.resources.mapper.Cohort reportCohort = payload.getCohort();
		List<Column> columnList = payload.getColumns();
		
		EvaluationContext context = new EvaluationContext();
		SimpleDataSet dataSet = new SimpleDataSet(new PatientDataSetDefinition(), context);
		org.openmrs.Cohort baseCohort = new Cohort();
		List<Map<String, Object>> parameters = reportCohort.getParameters();
		
		Map<String, Object> cohortParameters = getParameters(parameters);
		DataExportReportObject exportReportObject = new DataExportReportObject();
		List<Integer> patientIds = new ArrayList<Integer>();
		
		context.setParameterValues(cohortParameters);
		if (reportCohort.getUuid() != null && !columnList.isEmpty() && reportCohort.getType() != null) {
			String cohortType = reportCohort.getType();
			try {
				if ("Report".equals(cohortType)) {
					ReportDefinitionService service = Context.getService(ReportDefinitionService.class);
					ReportDefinition rd = service.getDefinitionByUuid(reportCohort.getUuid());
					
					if (rd != null) {
						Mapped<? extends CohortDefinition> cd = rd.getBaseCohortDefinition();
						if (cd != null) {
							ReflectionUtil.setPropertyValue(cd, "startDate", cohortParameters.get("startDate"));
							ReflectionUtil.setPropertyValue(cd, "endDate", cohortParameters.get("endDate"));
							EvaluatedCohort evaluatedCohort = Context.getService(CohortDefinitionService.class).evaluate(cd,
							    context);
							baseCohort.setMemberIds(evaluatedCohort.getMemberIds());
						}
					}
					patientIds.addAll(baseCohort.getMemberIds());
					exportReportObject.setPatientIds(patientIds);
					
				} else if ("Cohort".equals(cohortType)) {
					CohortService cohortService = Context.getCohortService();
					baseCohort = cohortService.getCohortByUuid(reportCohort.getUuid());
					patientIds.addAll(baseCohort.getMemberIds());
					exportReportObject.setPatientIds(patientIds);
				}
				
				// For JSON format, return patient list with basic info
				SimpleObject result = new SimpleObject();
				result.put("name", reportCohort.getName());
				result.put("patientCount", patientIds.size());
				result.put("patientIds", patientIds);
				result.put("columns", columnList.size());
				
				return new ResponseEntity<Object>(result, HttpStatus.OK);
				
			}
			catch (Exception e) {
				SimpleObject errorResponse = new SimpleObject();
				errorResponse.put("error", e.getMessage());
				return new ResponseEntity<Object>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
			}
		} else {
			SimpleObject errorResponse = new SimpleObject();
			errorResponse.put("error", "Missing cohort or column list for this report");
			return new ResponseEntity<Object>(errorResponse, HttpStatus.BAD_REQUEST);
		}
	}
	
	/**
	 * Convert parameter list to map
	 */
	private Map<String, Object> getParameters(List<Map<String, Object>> list) {
		Map<String, Object> parameterValues = new HashMap<String, Object>();
		if (!list.isEmpty()) {
			for (Map<String, Object> objectMap : list) {
				Iterator<String> keys = objectMap.keySet().iterator();
				while (keys.hasNext()) {
					String key = keys.next();
					String mapValue = (String) objectMap.get(key);
					parameterValues.put(key, DateUtil.parseYmd(mapValue));
				}
			}
		}
		return parameterValues;
	}
	
	/**
	 * Add columns to data export object based on column type
	 */
	private void addColumnsToDataExportObject(List<Column> columnList, DataExportReportObject dataExportReportObject) {
		
		for (Column column : columnList) {
			String expression = column.getExpression();
			String type = column.getType();
			String column_label = column.getLabel();
			
			if (isExpressionAConcept(expression)) {
				ConceptService conceptService = Context.getConceptService();
				org.openmrs.Concept concept = conceptService.getConceptByUuid(expression);
				if (concept != null) {
					dataExportReportObject.addConceptColumn(column_label, DataExportReportObject.MODIFIER_LAST, null,
					    concept.getId().toString(), null);
				}
			} else {
				PatientService patientService = Context.getPatientService();
				
				if ("PatientIdentifier".equals(type)) {
					org.openmrs.PatientIdentifierType patientIdentifierType = patientService
					        .getPatientIdentifierTypeByUuid(expression);
					dataExportReportObject.addSimpleColumn(column_label, "$!{fn.getPatientIdentifier('"
					        + patientIdentifierType.getId() + "')}");
					
				} else if ("PersonName".equals(type)) {
					dataExportReportObject.addSimpleColumn(column_label, "$!{fn.getPatientAttr('PersonName', '" + expression
					        + "')}");
					
				} else if ("PersonAttribute".equals(type)) {
					PersonAttributeType personAttributeType = Context.getPersonService().getPersonAttributeTypeByUuid(
					    expression);
					dataExportReportObject.addSimpleColumn(column_label,
					    "$!{fn.getPersonAttribute('" + personAttributeType.getName() + "')}");
					
				} else if ("Demographics".equals(type)) {
					if ("Age".equals(expression)) {
						dataExportReportObject.addSimpleColumn(column_label,
						    "$!{fn.calculateAge($fn.getPatientAttr('Person', 'birthdate'))}");
					} else {
						dataExportReportObject.addSimpleColumn(column_label, "$!{fn.getPatientAttr('Person', '" + expression
						        + "')}");
					}
				} else if ("Address".equals(type)) {
					dataExportReportObject.addSimpleColumn(column_label, "$!{fn.getPatientAttr('PersonAddress', '"
					        + expression + "')}");
				}
			}
		}
	}
	
	/**
	 * Check if expression is a concept UUID
	 */
	private boolean isExpressionAConcept(String conceptUuid) {
		boolean isConcept = false;
		ConceptService conceptService = Context.getConceptService();
		org.openmrs.Concept concept = conceptService.getConceptByUuid(conceptUuid);
		if (concept != null) {
			isConcept = true;
		}
		return isConcept;
	}
}
