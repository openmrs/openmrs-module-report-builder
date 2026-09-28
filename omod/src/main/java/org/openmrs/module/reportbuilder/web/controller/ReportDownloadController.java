package org.openmrs.module.reportbuilder.web.controller;

import org.openmrs.api.APIAuthenticationException;
import org.openmrs.api.context.Context;
import org.openmrs.module.reportbuilder.api.ReportBuilderService;
import org.openmrs.module.reportbuilder.security.ReportBuilderPrivileges;
import org.openmrs.module.reporting.common.DateUtil;
import org.openmrs.module.reporting.evaluation.EvaluationContext;
import org.openmrs.module.reporting.evaluation.parameter.Parameter;
import org.openmrs.module.reporting.report.Report;
import org.openmrs.module.reporting.report.ReportData;
import org.openmrs.module.reporting.report.ReportDesign;
import org.openmrs.module.reporting.report.ReportRequest;
import org.openmrs.module.reporting.report.definition.ReportDefinition;
import org.openmrs.module.reporting.report.definition.service.ReportDefinitionService;
import org.openmrs.module.reporting.report.renderer.RenderingMode;
import org.openmrs.module.reporting.report.service.ReportService;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * REST controller for downloading reports as Excel files Replaces legacy ugandaemr-reports
 * ProcessAndDownloadReportController
 */
@Controller
@RequestMapping(value = "/rest/" + RestConstants.VERSION_1 + ReportDownloadController.REPORTBUILDER
        + ReportDownloadController.REPORT_DOWNLOAD)
public class ReportDownloadController {
	
	public static final String REPORTBUILDER = "/reportbuilder";
	
	public static final String REPORT_DOWNLOAD = "/reportDownload";
	
	public static final String EXCEL_REPORT_RENDERER_TYPE = "org.openmrs.module.reporting.report.renderer.XlsReportRenderer";
	
	public static final String XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
	
	public static final String CSV_CONTENT_TYPE = "text/csv";
	
	public static final String PDF_CONTENT_TYPE = "application/pdf";
	
	@Autowired
	public GenericConversionService conversionService;
	
	@Autowired
	public ReportService reportService;
	
	@Autowired
	public org.openmrs.module.reportbuilder.api.ReportBuilderService reportBuilderService;
	
	/**
	 * Download report as a file. Format selection: renderType=excel (default) | csv | pdf. The
	 * format param is accepted as an alias of renderType (the frontend uses both spellings), and
	 * both are matched case-insensitively so format=CSV or renderType=CSV also route to the CSV
	 * download. The report can be addressed either directly with uuid (a ReportDefinition uuid) or
	 * via reportLibraryUuid (a ReportLibrary entry, resolved to its ReportDefinition). Request
	 * parameters are resolved against the report definition's declared parameters: submitted values
	 * are converted with tolerant date parsing, missing values fall back to the parameter default,
	 * and required parameters are enforced.
	 * 
	 * @param request HTTP request
	 * @param directUuid Report definition UUID (optional when reportLibraryUuid is given)
	 * @param reportLibraryUuid Report library entry UUID (optional)
	 * @param renderType excel (default), csv, pdf
	 * @return file download or error response
	 */
	@ExceptionHandler(APIAuthenticationException.class)
	@RequestMapping(method = RequestMethod.GET)
	@ResponseBody
	public Object download(HttpServletRequest request, @RequestParam(required = false, value = "uuid") String directUuid,
	        @RequestParam(required = false, value = "reportLibraryUuid") String reportLibraryUuid,
	        @RequestParam(required = false, value = "renderType") String renderType) {
		Context.requirePrivilege("Task: reportbuilder.report.run");
		try {
			// The frontend sends the requested format either as format or as renderType; format
			// wins when both are present because it is the download-specific parameter
			String requested = request.getParameter("format");
			if (requested == null || requested.trim().isEmpty()) {
				requested = renderType;
			}
			String normalizedRenderType = normalizeRenderType(requested);
			
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
			
			if ("csv".equals(normalizedRenderType)) {
				return downloadCsvReport(reportDefinition, evaluationContext);
			}
			
			if ("pdf".equals(normalizedRenderType)) {
				return downloadPdfReport(reportDefinition, evaluationContext);
			}
			
			if (!"excel".equals(normalizedRenderType)) {
				// Fail loudly instead of silently falling back to Excel - a silent fallback is how
				// "I selected CSV but got xlsx" bugs happen
				SimpleObject message = new SimpleObject();
				message.put("error", "Unsupported format '" + requested + "'. Use excel, csv, or pdf"
				        + " (list/json/html renders are on the reportingDefinition endpoint)");
				return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body(message);
			}
			
			return downloadExcelReport(reportDefinition, evaluationContext);
		}
		catch (IllegalArgumentException | IllegalStateException ex) {
			// Parameter validation problems are the client's fault: 400 with the reason
			SimpleObject errorResponse = new SimpleObject();
			errorResponse.put("error", ex.getMessage());
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
			        .body(errorResponse);
		}
		catch (Exception ex) {
			SimpleObject errorResponse = new SimpleObject();
			errorResponse.put("error", ex.getMessage());
			return new ResponseEntity<String>(errorResponse.toString(), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
	
	/**
	 * Download report as CSV file. Resolves the report exactly like {@link #download} — uuid or
	 * reportLibraryUuid, with the same parameter resolution (defaults, required enforcement,
	 * tolerant date parsing).
	 * 
	 * @param request HTTP request
	 * @param directUuid Report definition UUID (optional when reportLibraryUuid is given)
	 * @param reportLibraryUuid Report library entry UUID (optional)
	 * @return CSV file download or error response
	 */
	@RequestMapping(method = RequestMethod.GET, params = "format=csv")
	@ResponseBody
	public Object downloadCsv(HttpServletRequest request, @RequestParam(required = false, value = "uuid") String directUuid,
	        @RequestParam(required = false, value = "reportLibraryUuid") String reportLibraryUuid) {
		Context.requirePrivilege("Task: reportbuilder.report.run");
		try {
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
			
			return downloadCsvReport(reportDefinition, evaluationContext);
		}
		catch (IllegalArgumentException | IllegalStateException ex) {
			// Parameter validation problems are the client's fault: 400 with the reason
			SimpleObject errorResponse = new SimpleObject();
			errorResponse.put("error", ex.getMessage());
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
			        .body(errorResponse);
		}
		catch (Exception ex) {
			SimpleObject errorResponse = new SimpleObject();
			errorResponse.put("error", ex.getMessage());
			return new ResponseEntity<String>(errorResponse.toString(), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
	
	/**
	 * Generate Excel report download. When the report has a compiled JSON design, the workbook is
	 * built from the same table model that drives the HTML rendering, so the download mirrors the
	 * HTML output. Reports without a JSON design fall back to the legacy reporting-module
	 * XlsReportRenderer template path.
	 */
	private Object downloadExcelReport(ReportDefinition rd, EvaluationContext context) {
		ReportDesign jsonDesign = findReportDesign(rd, "JSON");
		if (jsonDesign != null) {
			try {
				ReportData reportData = Context.getService(ReportDefinitionService.class).evaluate(rd, context);
				byte[] data = reportBuilderService.buildExcelOutput(reportData, jsonDesign);
				
				return ResponseEntity
				        .ok()
				        .header(HttpHeaders.CONTENT_TYPE, XLSX_CONTENT_TYPE)
				        .header(HttpHeaders.CONTENT_DISPOSITION,
				            "attachment; filename=" + buildFilename(rd.getName(), "xlsx")).body(data);
			}
			catch (Exception e) {
				SimpleObject errorResponse = new SimpleObject();
				errorResponse.put("error", "Failed to render Excel download: " + e.getMessage());
				return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.APPLICATION_JSON)
				        .body(errorResponse);
			}
		}
		
		return downloadLegacyExcelReport(rd,
		    context.getParameterValues() == null ? new HashMap<String, Object>() : context.getParameterValues());
	}
	
	/**
	 * Generate CSV report download, mirroring the HTML rendering.
	 */
	private Object downloadCsvReport(ReportDefinition rd, EvaluationContext context) {
		ReportDesign jsonDesign = findReportDesign(rd, "JSON");
		if (jsonDesign == null) {
			SimpleObject errorResponse = new SimpleObject();
			errorResponse.put("error", "No JSON design found for report");
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.APPLICATION_JSON)
			        .body(errorResponse);
		}
		
		try {
			ReportData reportData = Context.getService(ReportDefinitionService.class).evaluate(rd, context);
			byte[] data = reportBuilderService.buildCsvOutput(reportData, jsonDesign);
			
			return ResponseEntity.ok().header(HttpHeaders.CONTENT_TYPE, CSV_CONTENT_TYPE)
			        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + buildFilename(rd.getName(), "csv"))
			        .body(data);
		}
		catch (Exception e) {
			SimpleObject errorResponse = new SimpleObject();
			errorResponse.put("error", "Failed to render CSV download: " + e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.APPLICATION_JSON)
			        .body(errorResponse);
		}
	}
	
	/**
	 * Generate PDF report download, mirroring the HTML rendering.
	 */
	private Object downloadPdfReport(ReportDefinition rd, EvaluationContext context) {
		ReportDesign jsonDesign = findReportDesign(rd, "JSON");
		if (jsonDesign == null) {
			SimpleObject errorResponse = new SimpleObject();
			errorResponse.put("error", "No JSON design found for report");
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.APPLICATION_JSON)
			        .body(errorResponse);
		}
		
		try {
			ReportData reportData = Context.getService(ReportDefinitionService.class).evaluate(rd, context);
			byte[] data = reportBuilderService.buildPdfOutput(reportData, jsonDesign);
			
			return ResponseEntity.ok().header(HttpHeaders.CONTENT_TYPE, PDF_CONTENT_TYPE)
			        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + buildFilename(rd.getName(), "pdf"))
			        .body(data);
		}
		catch (Exception e) {
			SimpleObject errorResponse = new SimpleObject();
			errorResponse.put("error", "Failed to render PDF download: " + e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.APPLICATION_JSON)
			        .body(errorResponse);
		}
	}
	
	/**
	 * Legacy Excel path: renders through the reporting module's XlsReportRenderer using a
	 * hand-authored Excel template design.
	 */
	private Object downloadLegacyExcelReport(ReportDefinition rd, Map<String, Object> parameterValues) {
		ReportRequest reportRequest = new ReportRequest();
		reportRequest.setReportDefinition(new org.openmrs.module.reporting.evaluation.parameter.Mapped<ReportDefinition>(rd,
		        parameterValues));
		reportRequest.setStatus(ReportRequest.Status.REQUESTED);
		List<ReportDesign> reportDesigns = reportService.getReportDesigns(rd, null, false);
		
		ReportDesign reportDesign = findExcelDesign(reportDesigns);
		RenderingMode renderingMode = null;
		if (reportDesign != null) {
			String reportRenderingMode = EXCEL_REPORT_RENDERER_TYPE + "!" + reportDesign.getUuid();
			renderingMode = new RenderingMode(reportRenderingMode);
			if (!renderingMode.getRenderer().canRender(rd)) {
				SimpleObject errorResponse = new SimpleObject();
				errorResponse.put("error", "Unable to render Report with " + reportRenderingMode);
				return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.APPLICATION_JSON)
				        .body(errorResponse);
			}
			reportRequest.setRenderingMode(renderingMode);
		} else {
			SimpleObject errorResponse = new SimpleObject();
			errorResponse.put("error", "No Excel design found for report");
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.APPLICATION_JSON)
			        .body(errorResponse);
		}
		Report report = reportService.runReport(reportRequest);
		
		// download report
		String filename = renderingMode.getRenderer().getFilename(report.getRequest()).replace(" ", "_");
		String contentType = renderingMode.getRenderer().getRenderedContentType(report.getRequest());
		byte[] data = report.getRenderedOutput();
		
		if (data == null) {
			SimpleObject errorResponse = new SimpleObject();
			errorResponse.put("error", "Error retrieving the report");
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.APPLICATION_JSON)
			        .body(errorResponse);
		} else {
			return ResponseEntity.ok().header(HttpHeaders.CONTENT_TYPE, contentType)
			        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename).body(data);
		}
	}
	
	/**
	 * Find a report design by name (case-insensitive)
	 */
	private ReportDesign findReportDesign(ReportDefinition rd, String designName) {
		List<ReportDesign> designs = reportService.getReportDesigns(rd, null, false);
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
	
	/**
	 * Sanitized download filename for the report
	 */
	private String buildFilename(String reportName, String extension) {
		String base = reportName == null || reportName.trim().isEmpty() ? "report" : reportName.trim();
		return base.replaceAll("[^A-Za-z0-9._-]+", "_") + "." + extension;
	}
	
	/**
	 * Normalizes the renderType request parameter. Excel is the default download format; xls/xlsx
	 * are accepted as aliases of excel, csv routes to the CSV download.
	 */
	private String normalizeRenderType(String renderType) {
		if (renderType == null || renderType.trim().isEmpty()) {
			return "excel";
		}
		
		String rt = renderType.trim().toLowerCase();
		if ("xls".equals(rt) || "xlsx".equals(rt)) {
			return "excel";
		}
		return rt;
	}
	
	/**
	 * Validate date format
	 */
	private boolean validateDateIsValidFormat(String date) {
		try {
			DateUtil.parseYmd(date);
			return true;
		}
		catch (Exception ex) {
			return false;
		}
	}
	
	/**
	 * Find Excel design from list of report designs
	 */
	private ReportDesign findExcelDesign(List<ReportDesign> reportDesigns) {
		for (ReportDesign design : reportDesigns) {
			if ("Excel".equals(design.getName())) {
				return design;
			}
		}
		return null;
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
				// A value was submitted but could not be converted: fall back to the declared
				// default instead of silently evaluating the report with a null parameter
				if (converted == null) {
					converted = p.getDefaultValue();
				}
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
	
	/**
	 * Parses a submitted date value. Only unambiguous formats are accepted: ISO yyyy-MM-dd and its
	 * datetime variants. Day-first and month-first slash patterns were deliberately dropped - they
	 * silently misinterpret 06/07/2024 depending on an assumed convention (and disagree with the
	 * endDate validation, which is strict yyyy-MM-dd).
	 */
	private Date tryParseDate(String value) {
		List<String> patterns = new ArrayList<String>();
		patterns.add("yyyy-MM-dd");
		patterns.add("yyyy-MM-dd'T'HH:mm:ss");
		patterns.add("yyyy-MM-dd'T'HH:mm:ss.SSS");
		
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
	
}
