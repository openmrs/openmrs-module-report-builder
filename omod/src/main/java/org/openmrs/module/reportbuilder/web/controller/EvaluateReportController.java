package org.openmrs.module.reportbuilder.web.controller;

import org.openmrs.api.context.Context;
import org.openmrs.module.reportbuilder.security.ReportBuilderPrivileges;
import org.openmrs.module.reporting.common.DateUtil;
import org.openmrs.module.reporting.dataset.DataSet;
import org.openmrs.module.reporting.dataset.DataSetRow;
import org.openmrs.module.reporting.evaluation.EvaluationContext;
import org.openmrs.module.reporting.evaluation.parameter.Parameter;
import org.openmrs.module.reporting.report.ReportData;
import org.openmrs.module.reporting.report.ReportDesign;
import org.openmrs.module.reporting.report.definition.ReportDefinition;
import org.openmrs.module.reporting.report.definition.service.ReportDefinitionService;
import org.openmrs.module.reporting.report.service.ReportService;
import org.openmrs.module.reportbuilder.api.ReportBuilderService;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.List;
import java.util.Map;
import java.util.Iterator;

@RestController
@RequestMapping(value = "/rest/" + RestConstants.VERSION_1 + EvaluateReportController.REPORTBUILDER
        + EvaluateReportController.SET)
public class EvaluateReportController {
	
	public static final String REPORTBUILDER = "/reportbuilder";
	
	public static final String SET = "/reportingDefinition";
	
	@Autowired
	public GenericConversionService conversionService;
	
	@Autowired
	public ReportService reportService;
	
	@RequestMapping(method = RequestMethod.GET)
	@ResponseBody
	public Object getReportData(HttpServletRequest request,
	        @RequestParam(required = false, value = "uuid") String directUuid,
	        @RequestParam(required = false, value = "reportLibraryUuid") String reportLibraryUuid,
	        @RequestParam(required = false, value = "renderType") String renderType) {
		Context.requirePrivilege("Task: reportbuilder.report.run");
		try {
			String normalizedRenderType = normalizeRenderType(renderType);
			
			String endDateStr = request.getParameter("endDate");
			if (endDateStr != null && !endDateStr.trim().isEmpty() && !validateDateIsValidFormat(endDateStr)) {
				SimpleObject message = new SimpleObject();
				message.put("error", "given date " + endDateStr + " is not valid");
				return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body(message);
			}
			
			// Get services
			ReportDefinitionService reportDefinitionService = Context.getService(ReportDefinitionService.class);
			ReportBuilderService reportBuilderService = Context.getService(ReportBuilderService.class);
			
			// Resolve the actual report definition UUID
			String reportDefinitionUuid = directUuid;
			if (reportLibraryUuid != null && !reportLibraryUuid.trim().isEmpty()) {
				// Resolve from ReportLibrary
				org.openmrs.module.reportbuilder.model.ReportLibrary libraryEntry = reportBuilderService
				        .getReportLibraryByUuid(reportLibraryUuid);
				if (libraryEntry == null) {
					return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
					        .body("{\"error\":\"ReportLibrary entry not found with UUID: " + reportLibraryUuid + "\"}");
				}
				reportDefinitionUuid = libraryEntry.getReportDefinitionUuid();
				if (reportDefinitionUuid == null || reportDefinitionUuid.trim().isEmpty()) {
					return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
					        .body("{\"error\":\"ReportLibrary entry has no associated ReportDefinition UUID\"}");
				}
			}
			
			if (reportDefinitionUuid == null || reportDefinitionUuid.trim().isEmpty()) {
				return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
				        .body("{\"error\":\"Either 'uuid' or 'reportLibraryUuid' parameter is required\"}");
			}
			
			ReportDefinition reportDefinition = reportDefinitionService.getDefinitionByUuid(reportDefinitionUuid);
			
			if (reportDefinition == null) {
				return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
				        .body("{\"error\":\"ReportDefinition not found with UUID: " + reportDefinitionUuid + "\"}");
			}
			
			EvaluationContext evaluationContext = new EvaluationContext();
			evaluationContext.setParameterValues(resolveParameterValues(request, reportDefinition));
			
			ReportData reportData = reportDefinitionService.evaluate(reportDefinition, evaluationContext);
			
			if ("list".equals(normalizedRenderType)) {
				// Check if this is a linelist report - if so, also include HTML rendering
				ReportDesign jsonDesign = findReportDesign(reportDefinition, "JSON");
				boolean isLinelist = jsonDesign != null && isLinelistReportDesign(jsonDesign);
				
				Map<String, List<SimpleObject>> out = new HashMap<String, List<SimpleObject>>();
				for (Map.Entry<String, DataSet> e : reportData.getDataSets().entrySet()) {
					out.put(e.getKey(), convertDataSetToSimpleObject(e.getValue()));
				}
				
				// For linelist reports, also add HTML rendering to the response
				if (isLinelist && jsonDesign != null) {
					try {
						String html = reportBuilderService.buildRenderedOutput(reportData, jsonDesign, null);
						// Parse the rendered output to extract just the HTML
						com.fasterxml.jackson.databind.JsonNode renderedJson = new com.fasterxml.jackson.databind.ObjectMapper()
						        .readTree(html);
						if (renderedJson.has("html")) {
							SimpleObject meta = new SimpleObject();
							meta.put("html", renderedJson.path("html").asText());
							out.put("_html", Collections.singletonList(meta));
						}
					}
					catch (Exception e) {
						// If HTML generation fails, still return the JSON data
					}
				}
				
				return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(out);
			}
			
			if ("json".equals(normalizedRenderType) || "html".equals(normalizedRenderType)) {
				ReportDesign jsonDesign = findReportDesign(reportDefinition, "JSON");
				if (jsonDesign == null) {
					return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.APPLICATION_JSON)
					        .body("{\"error\":\"No JSON design found\"}");
				}
				
				if ("html".equals(normalizedRenderType)) {
					String rendered = reportBuilderService.buildRenderedOutput(reportData, jsonDesign, null);
					return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.TEXT_HTML).body(rendered);
				}
				
				Date endDate = null;
				if (endDateStr != null && !endDateStr.trim().isEmpty()) {
					endDate = new SimpleDateFormat("yyyy-MM-dd").parse(endDateStr);
				}
				
				String payload = (endDate != null) ? reportBuilderService.buildFinalPayloadJson(reportData, jsonDesign,
				    "json", endDate) : reportBuilderService.buildPayloadJson(reportData, jsonDesign, "json");
				
				return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(payload);
			}
			
			if ("excel".equals(normalizedRenderType)) {
				ReportDesign excelDesign = findExcelDesign(reportDefinition);
				if (excelDesign == null) {
					return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.APPLICATION_JSON)
					        .body("{\"error\":\"No Excel design found\"}");
				}
				
				SimpleObject out = new SimpleObject();
				out.put("message", "Excel design found");
				out.put("designUuid", excelDesign.getUuid());
				out.put("designName", excelDesign.getName());
				out.put("note",
				    "Excel rendering endpoint is recognized, but binary streaming is not yet implemented in this controller.");
				
				return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(out);
			}
			
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
			        .body("{\"error\":\"Unsupported renderType. Use list, json, html, or excel\"}");
			
		}
		catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.APPLICATION_JSON)
			        .body("{\"error\":\"" + e.getMessage() + "\"}");
		}
	}
	
	private String normalizeRenderType(String renderType) {
		if (renderType == null || renderType.trim().isEmpty()) {
			return "list";
		}
		
		String rt = renderType.trim().toLowerCase();
		if ("xls".equals(rt) || "xlsx".equals(rt)) {
			return "excel";
		}
		return rt;
	}
	
	private ReportDesign findReportDesign(ReportDefinition reportDefinition, String designName) {
		List<ReportDesign> designs = reportService.getReportDesigns(reportDefinition, null, false);
		if (designs == null) {
			return null;
		}
		
		for (ReportDesign design : designs) {
			if (designName.equalsIgnoreCase(design.getName())) {
				return design;
			}
		}
		return null;
	}
	
	private ReportDesign findExcelDesign(ReportDefinition reportDefinition) {
		List<ReportDesign> designs = reportService.getReportDesigns(reportDefinition, null, false);
		if (designs == null) {
			return null;
		}
		
		for (ReportDesign design : designs) {
			String name = design.getName() != null ? design.getName().trim().toLowerCase() : "";
			if ("excel".equals(name) || "xls".equals(name) || "xlsx".equals(name)) {
				return design;
			}
		}
		
		for (ReportDesign design : designs) {
			String rendererType = design.getRendererType() != null ? design.getRendererType().getName().trim() : "";
			if (rendererType.contains("XlsReportRenderer")) {
				return design;
			}
		}
		
		return null;
	}
	
	/**
	 * Detects if a report design is for a linelist report by checking the template JSON for
	 * baseCohortDefinition and dataSetDefinitions keys.
	 */
	private boolean isLinelistReportDesign(ReportDesign reportDesign) {
		if (reportDesign == null || reportDesign.getResources() == null) {
			return false;
		}
		
		for (org.openmrs.module.reporting.report.ReportDesignResource resource : reportDesign.getResources()) {
			if ("template".equals(resource.getName())) {
				byte[] content = resource.getContents();
				if (content != null && content.length > 0) {
					try {
						String templateJson = new String(content, StandardCharsets.UTF_8);
						com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
						com.fasterxml.jackson.databind.JsonNode config = mapper.readTree(templateJson);
						// Linelist reports have baseCohortDefinition and dataSetDefinitions, not groups
						return config.has("baseCohortDefinition") && config.has("dataSetDefinitions")
						        && !config.has("groups");
					}
					catch (Exception e) {
						// If parsing fails, assume it's not a linelist report
						return false;
					}
				}
			}
		}
		
		return false;
	}
	
	private Map<String, Object> resolveParameterValues(HttpServletRequest request, ReportDefinition rd) {
		Map<String, Object> vals = new HashMap<String, Object>();
		
		for (Parameter p : rd.getParameters()) {
			String name = p.getName();
			String submitted = request.getParameter(name);
			
			if (p.getCollectionType() != null) {
				throw new IllegalStateException("Collection parameter not supported yet: " + name);
			}
			
			Object converted = null;
			boolean hasValue = submitted != null && !submitted.trim().isEmpty();
			
			if (!hasValue) {
				converted = p.getDefaultValue();
			} else {
				converted = convertParameterValue(submitted.trim(), p.getType());
			}
			
			if (converted == null && p.getDefaultValue() == null && p.isRequired()) {
				throw new IllegalArgumentException("Missing required parameters: " + name);
			}
			
			vals.put(name, converted);
		}
		
		return vals;
	}
	
	private Object convertParameterValue(String submitted, Class<?> targetType) {
		if (submitted == null) {
			return null;
		}
		
		if (Date.class.isAssignableFrom(targetType)) {
			Date d = tryParseDate(submitted);
			if (d != null) {
				return d;
			}
			
			try {
				return DateUtil.parseYmd(submitted);
			}
			catch (Exception ignore) {
				return null;
			}
		}
		
		try {
			Object converted = conversionService.convert(submitted, targetType);
			if (converted != null) {
				return converted;
			}
		}
		catch (Exception ignore) {}
		
		try {
			if (Integer.class.equals(targetType) || int.class.equals(targetType)) {
				return Integer.valueOf(submitted);
			}
			if (Long.class.equals(targetType) || long.class.equals(targetType)) {
				return Long.valueOf(submitted);
			}
			if (Double.class.equals(targetType) || double.class.equals(targetType)) {
				return Double.valueOf(submitted);
			}
			if (Boolean.class.equals(targetType) || boolean.class.equals(targetType)) {
				return Boolean.valueOf(submitted);
			}
			if (String.class.equals(targetType)) {
				return submitted;
			}
		}
		catch (Exception ignore) {
			return null;
		}
		
		return null;
	}
	
	private Date tryParseDate(String value) {
		List<String> patterns = new ArrayList<String>();
		patterns.add("yyyy-MM-dd");
		patterns.add("yyyy-MM-dd'T'HH:mm:ss");
		patterns.add("yyyy-MM-dd'T'HH:mm:ss.SSS");
		patterns.add("dd/MM/yyyy");
		patterns.add("MM/dd/yyyy");
		
		for (String pattern : patterns) {
			try {
				SimpleDateFormat sdf = new SimpleDateFormat(pattern);
				sdf.setLenient(false);
				return sdf.parse(value);
			}
			catch (ParseException ignored) {}
		}
		return null;
	}
	
	private boolean validateDateIsValidFormat(String date) {
		try {
			DateUtil.parseYmd(date);
			return true;
		}
		catch (Exception ex) {
			return false;
		}
	}
	
	private List<SimpleObject> convertDataSetToSimpleObject(DataSet d) {
		List<SimpleObject> rows = new ArrayList<SimpleObject>();
		if (d == null) {
			return rows;
		}
		
		Iterator it = d.iterator();
		while (it.hasNext()) {
			DataSetRow r = (DataSetRow) it.next();
			SimpleObject so = new SimpleObject();
			for (String key : r.getColumnValuesByKey().keySet()) {
				Object v = r.getColumnValue(key);
				so.add(key, v == null ? "" : v);
			}
			rows.add(so);
		}
		return rows;
	}
}
