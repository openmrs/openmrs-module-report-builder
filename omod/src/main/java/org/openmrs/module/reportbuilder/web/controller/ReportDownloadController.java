package org.openmrs.module.reportbuilder.web.controller;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.api.APIAuthenticationException;
import org.openmrs.api.context.Context;
import org.openmrs.module.reporting.common.DateUtil;
import org.openmrs.module.reporting.evaluation.EvaluationContext;
import org.openmrs.module.reporting.evaluation.parameter.Parameter;
import org.openmrs.module.reporting.report.Report;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
	
	@Autowired
	public GenericConversionService conversionService;
	
	@Autowired
	public ReportService reportService;
	
	/**
	 * Download report as Excel file
	 * 
	 * @param request HTTP request
	 * @param reportDefinitionUuid Report definition UUID (required)
	 * @return Excel file download or error response
	 */
	@ExceptionHandler(APIAuthenticationException.class)
	@RequestMapping(method = RequestMethod.GET)
	@ResponseBody
	public Object download(HttpServletRequest request,
	        @RequestParam(required = true, value = "uuid") String reportDefinitionUuid) {
		try {
			String endDateStr = request.getParameter("endDate");
			if (endDateStr != null && !validateDateIsValidFormat(endDateStr)) {
				SimpleObject message = new SimpleObject();
				message.put("error", "Given date " + endDateStr + " is not valid");
				
				return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body(message);
			}
			
			EvaluationContext context = new EvaluationContext();
			ReportDefinitionService service = Context.getService(ReportDefinitionService.class);
			ReportDefinition rd = service.getDefinitionByUuid(reportDefinitionUuid);
			
			if (rd != null) {
				Collection<Parameter> missingParameters = new ArrayList<Parameter>();
				Map<String, Object> parameterValues = new HashMap<String, Object>();
				
				for (Parameter parameter : rd.getParameters()) {
					String submitted = request.getParameter(parameter.getName());
					if (parameter.getCollectionType() != null) {
						SimpleObject errorResponse = new SimpleObject();
						errorResponse.put("error", "Collection parameters not yet implemented");
						return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).contentType(MediaType.APPLICATION_JSON)
						        .body(errorResponse);
					}
					Object converted;
					if (StringUtils.isEmpty(submitted)) {
						converted = parameter.getDefaultValue();
					} else {
						converted = conversionService.convert(submitted, parameter.getType());
					}
					if (converted == null) {
						missingParameters.add(parameter);
					}
					parameterValues.put(parameter.getName(), converted);
				}
				
				context.setParameterValues(parameterValues);
				
				return downloadExcelReport(rd, parameterValues);
			} else {
				SimpleObject message = new SimpleObject();
				message.put("error", "Report definition not found");
				return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON).body(message);
			}
			
		}
		catch (Exception ex) {
			SimpleObject errorResponse = new SimpleObject();
			errorResponse.put("error", ex.getMessage());
			return new ResponseEntity<String>(errorResponse.toString(), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
	
	/**
	 * Download report as CSV file
	 * 
	 * @param request HTTP request
	 * @param reportDefinitionUuid Report definition UUID (required)
	 * @return CSV file download or error response
	 */
	@RequestMapping(method = RequestMethod.GET, params = "format=csv")
	@ResponseBody
	public Object downloadCsv(HttpServletRequest request,
	        @RequestParam(required = true, value = "uuid") String reportDefinitionUuid) {
		try {
			String endDateStr = request.getParameter("endDate");
			if (endDateStr != null && !validateDateIsValidFormat(endDateStr)) {
				SimpleObject message = new SimpleObject();
				message.put("error", "Given date " + endDateStr + " is not valid");
				
				return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body(message);
			}
			
			EvaluationContext context = new EvaluationContext();
			ReportDefinitionService service = Context.getService(ReportDefinitionService.class);
			ReportDefinition rd = service.getDefinitionByUuid(reportDefinitionUuid);
			
			if (rd != null) {
				Map<String, Object> parameterValues = new HashMap<String, Object>();
				
				for (Parameter parameter : rd.getParameters()) {
					String submitted = request.getParameter(parameter.getName());
					Object converted;
					if (StringUtils.isEmpty(submitted)) {
						converted = parameter.getDefaultValue();
					} else {
						converted = conversionService.convert(submitted, parameter.getType());
					}
					parameterValues.put(parameter.getName(), converted);
				}
				
				context.setParameterValues(parameterValues);
				
				// CSV export would be implemented here
				SimpleObject notImplementedResponse = new SimpleObject();
				notImplementedResponse.put("error", "CSV export not yet implemented");
				notImplementedResponse.put("note", "Use Excel format instead");
				return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).contentType(MediaType.APPLICATION_JSON)
				        .body(notImplementedResponse);
			} else {
				SimpleObject message = new SimpleObject();
				message.put("error", "Report definition not found");
				return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON).body(message);
			}
			
		}
		catch (Exception ex) {
			SimpleObject errorResponse = new SimpleObject();
			errorResponse.put("error", ex.getMessage());
			return new ResponseEntity<String>(errorResponse.toString(), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
	
	/**
	 * Generate Excel report download
	 */
	private Object downloadExcelReport(ReportDefinition rd, Map<String, Object> parameterValues) {
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
}
