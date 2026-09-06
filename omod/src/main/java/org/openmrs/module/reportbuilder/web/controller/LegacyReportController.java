package org.openmrs.module.reportbuilder.web.controller;

import org.openmrs.api.context.Context;
import org.openmrs.module.reportbuilder.security.ReportBuilderPrivileges;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openmrs.api.APIException;
import org.openmrs.module.reportbuilder.api.ReportBuilderService;
import org.openmrs.module.reportbuilder.legacyconfig.LegacyReportImporter;
import org.openmrs.module.reportbuilder.legacyconfig.model.ReportConfig;
import org.openmrs.module.reportbuilder.model.LegacyReportConfig;
import org.openmrs.module.reportbuilder.validation.ReportValidationResult;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.response.ResponseException;
import org.openmrs.module.webservices.rest.web.v1_0.controller.BaseRestController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

@Controller
@RequestMapping(value = "/rest/" + RestConstants.VERSION_1 + "/reportbuilder/legacy")
public class LegacyReportController extends BaseRestController {
	
	private final ObjectMapper objectMapper = new ObjectMapper();
	
	@Autowired
	private ReportBuilderService reportBuilderService;
	
	private final LegacyReportImporter legacyReportImporter = new LegacyReportImporter();
	
	private static final String TEMP_UPLOAD_DIR = System.getProperty("java.io.tmpdir") + "/reportbuilder_uploads";
	
	/**
	 * List all legacy reports
	 */
	@RequestMapping(method = RequestMethod.GET)
	@ResponseBody
	public SimpleObject listLegacyReports() {
		Context.requirePrivilege("Task: reportbuilder.report.view");
		SimpleObject response = new SimpleObject();
		try {
			List<LegacyReportConfig> reports = reportBuilderService.getAllLegacyReports();

			List<SimpleObject> results = new ArrayList<>();
			for (LegacyReportConfig report : reports) {
				SimpleObject reportObj = new SimpleObject();
				reportObj.put("uuid", report.getUuid());
				reportObj.put("key", report.getKey()); // Add key field for frontend
				reportObj.put("name", report.getName());
				reportObj.put("description", report.getDescription());
				reportObj.put("status", report.getStatus());
				reportObj.put("version", report.getVersion());
				reportObj.put("category", report.getCategory());
				reportObj.put("subcategory", report.getSubcategory());
				reportObj.put("reportType", report.getReportType());
				reportObj.put("parameters", report.getParameters());
				// Map dataSetDefinitions to datasets for frontend compatibility
				reportObj.put("datasets", report.getDataSetDefinitions());
				results.add(reportObj);
			}

			response.put("results", results);
			response.put("count", results.size());
			return response;
		} catch (Exception e) {
			throw new APIException("Failed to list legacy reports", e);
		}
	}
	
	/**
	 * Get a specific legacy report by UUID
	 */
	@RequestMapping(value = "/{uuid}", method = RequestMethod.GET)
	@ResponseBody
	public SimpleObject getLegacyReport(@PathVariable("uuid") String uuid) {
		Context.requirePrivilege("Task: reportbuilder.report.view");
		SimpleObject response = new SimpleObject();
		try {
			LegacyReportConfig report = reportBuilderService.getLegacyReportByUuid(uuid);
			
			if (report == null) {
				response.put("uuid", uuid);
				response.put("status", "NOT_FOUND");
				response.put("success", false);
				response.put("error", "Report not found");
				return response;
			}
			
			// Convert the complete report to SimpleObject with frontend-compatible field names
			response.put("uuid", report.getUuid());
			response.put("key", report.getKey()); // Add key field for frontend
			response.put("name", report.getName());
			response.put("description", report.getDescription());
			response.put("status", report.getStatus());
			response.put("version", report.getVersion());
			response.put("category", report.getCategory());
			response.put("subcategory", report.getSubcategory());
			response.put("reportType", report.getReportType());
			response.put("reportYear", report.getReportYear());
			response.put("reportScope", report.getReportScope());
			response.put("parameters", report.getParameters());
			response.put("advancedFeatures", report.getAdvancedFeatures());
			// Map dataSetDefinitions to datasets for frontend compatibility
			response.put("datasets", report.getDataSetDefinitions());
			// Map jsonTemplateConfig to designs for frontend compatibility
			response.put("designs", extractDesignsFromConfig(report.getJsonTemplateConfig()));
			response.put("jsonTemplateConfig", report.getJsonTemplateConfig());
			response.put("dateCreated", report.getDateCreated());
			response.put("dateChanged", report.getDateChanged());
			
			response.put("success", true);
			return response;
		}
		catch (Exception e) {
			throw new APIException("Failed to get legacy report: " + uuid, e);
		}
	}
	
	/**
	 * Extract designs from jsonTemplateConfig for frontend compatibility
	 */
	private List<Map<String, Object>> extractDesignsFromConfig(Map<String, Object> jsonTemplateConfig) {
		List<Map<String, Object>> designs = new ArrayList<>();
		if (jsonTemplateConfig != null) {
			// Create a design object from the template config
			Map<String, Object> design = new HashMap<>();
			design.put("type", jsonTemplateConfig.get("type"));
			design.put("name", jsonTemplateConfig.get("name"));
			design.put("template", jsonTemplateConfig.get("template"));
			designs.add(design);
		}
		return designs;
	}
	
	/**
	 * Create a new legacy report
	 */
	@RequestMapping(method = RequestMethod.POST)
	@ResponseBody
	public SimpleObject createLegacyReport(@RequestBody Map<String, Object> payload) {
		Context.requirePrivilege("Task: reportbuilder.package.import");
		SimpleObject response = new SimpleObject();
		
		try {
			// Convert the payload to LegacyReportConfig
			String json = objectMapper.writeValueAsString(payload);
			LegacyReportConfig config = objectMapper.readValue(json, LegacyReportConfig.class);
			
			// Create the report
			LegacyReportConfig created = reportBuilderService.createLegacyReport(config);
			
			response.put("success", true);
			response.put("uuid", created.getUuid());
			response.put("name", created.getName());
			response.put("message", "Legacy report created successfully");
			
		}
		catch (APIException e) {
			response.put("success", false);
			response.put("error", e.getMessage());
		}
		catch (Exception e) {
			response.put("success", false);
			response.put("error", "Failed to create legacy report: " + e.getMessage());
		}
		
		return response;
	}
	
	/**
	 * Update an existing legacy report
	 */
	@RequestMapping(value = "/{uuid}", method = RequestMethod.POST)
	@ResponseBody
	public SimpleObject updateLegacyReport(@PathVariable("uuid") String uuid, @RequestBody Map<String, Object> payload) {
		Context.requirePrivilege("Task: reportbuilder.package.import");
		SimpleObject response = new SimpleObject();
		
		try {
			// Convert the payload to LegacyReportConfig
			String json = objectMapper.writeValueAsString(payload);
			LegacyReportConfig config = objectMapper.readValue(json, LegacyReportConfig.class);
			
			// Update the report
			LegacyReportConfig updated = reportBuilderService.updateLegacyReport(uuid, config);
			
			response.put("success", true);
			response.put("uuid", updated.getUuid());
			response.put("name", updated.getName());
			response.put("message", "Legacy report updated successfully");
			
		}
		catch (APIException e) {
			response.put("success", false);
			response.put("error", e.getMessage());
		}
		catch (Exception e) {
			response.put("success", false);
			response.put("error", "Failed to update legacy report: " + e.getMessage());
		}
		
		return response;
	}
	
	/**
	 * Delete a legacy report
	 */
	@RequestMapping(value = "/{uuid}", method = RequestMethod.DELETE)
	@ResponseBody
	public SimpleObject deleteLegacyReport(@PathVariable("uuid") String uuid) {
		Context.requirePrivilege("Task: reportbuilder.package.import");
		SimpleObject response = new SimpleObject();
		
		try {
			reportBuilderService.deleteLegacyReport(uuid);
			
			response.put("success", true);
			response.put("message", "Legacy report deleted successfully");
			
		}
		catch (APIException e) {
			response.put("success", false);
			response.put("error", e.getMessage());
		}
		catch (Exception e) {
			response.put("success", false);
			response.put("error", "Failed to delete legacy report: " + e.getMessage());
		}
		
		return response;
	}
	
	/**
	 * Upload and validate a legacy report JSON file
	 */
	@RequestMapping(value = "/upload", method = RequestMethod.POST)
	@ResponseBody
	public SimpleObject uploadLegacyReport(@RequestParam("file") MultipartFile file) {
		Context.requirePrivilege("Task: reportbuilder.package.import");
		SimpleObject response = new SimpleObject();
		
		if (file.isEmpty()) {
			response.put("success", false);
			response.put("error", "File is empty");
			return response;
		}
		
		if (!file.getOriginalFilename().endsWith(".json")) {
			response.put("success", false);
			response.put("error", "Only JSON files are allowed");
			return response;
		}
		
		try {
			// Create upload directory if it doesn't exist
			File uploadDir = new File(TEMP_UPLOAD_DIR);
			if (!uploadDir.exists()) {
				uploadDir.mkdirs();
			}
			
			// Save the uploaded file
			Path tempFile = Files.createTempFile(uploadDir.toPath(), "legacy_report_", ".json");
			Files.copy(file.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);
			
			// Parse and validate the JSON
			String jsonContent = new String(Files.readAllBytes(tempFile));
			ReportConfig reportConfig = objectMapper.readValue(jsonContent, ReportConfig.class);
			
			// Validate the report
			LegacyReportImporter.ValidationResult validationResult = legacyReportImporter.validateContract(
			    tempFile.toFile(), null);
			
			response.put("success", true);
			response.put("filename", file.getOriginalFilename());
			response.put("reportUuid", reportConfig.getUuid());
			response.put("reportName", reportConfig.getName());
			response.put("reportKey", reportConfig.getKey());
			response.put("description", reportConfig.getDescription());
			response.put("status", reportConfig.getStatus());
			response.put("parameters", reportConfig.getParameters());
			response.put("datasets", reportConfig.getDatasets());
			response.put("designs", reportConfig.getDesigns());
			response.put("jsonTemplateConfig", reportConfig.getJsonTemplateConfig());
			response.put("tempFilePath", tempFile.toString());
			response.put("valid", validationResult.isValid());
			
			if (!validationResult.getErrors().isEmpty()) {
				response.put("errors", validationResult.getErrors());
			}
			
		}
		catch (Exception e) {
			response.put("success", false);
			response.put("error", "Failed to parse JSON file: " + e.getMessage());
		}
		
		return response;
	}
	
	/**
	 * Import a validated legacy report into the report builder
	 */
	@RequestMapping(value = "/import", method = RequestMethod.POST)
	@ResponseBody
	public SimpleObject importLegacyReport(@RequestBody Map<String, Object> payload) {
		Context.requirePrivilege("Task: reportbuilder.package.import");
		SimpleObject response = new SimpleObject();
		
		try {
			String tempFilePath = (String) payload.get("tempFilePath");
			String reportName = (String) payload.get("reportName");
			
			if (tempFilePath == null) {
				response.put("success", false);
				response.put("error", "tempFilePath is required");
				return response;
			}
			
			File jsonFile = new File(tempFilePath);
			if (!jsonFile.exists()) {
				response.put("success", false);
				response.put("error", "Temporary file not found");
				return response;
			}
			
			// Import the report using the legacy importer
			org.openmrs.module.reporting.report.definition.ReportDefinition reportDefinition = legacyReportImporter
			        .importReportFromFile(jsonFile);
			
			response.put("success", true);
			response.put("reportName", reportName);
			response.put("reportDefinitionUuid", reportDefinition.getUuid());
			response.put("message", "Legacy report imported successfully");
			
			// Clean up temp file
			Files.deleteIfExists(jsonFile.toPath());
			
		}
		catch (Exception e) {
			response.put("success", false);
			response.put("error", "Failed to import legacy report: " + e.getMessage());
		}
		
		return response;
	}
	
	/**
	 * Validate a legacy report JSON file
	 */
	@RequestMapping(value = "/validate", method = RequestMethod.POST)
	@ResponseBody
	public SimpleObject validateLegacyReport(@RequestBody Map<String, Object> payload) {
		Context.requirePrivilege("Task: reportbuilder.package.import");
		SimpleObject response = new SimpleObject();
		
		try {
			// Convert the payload to LegacyReportConfig
			String json = objectMapper.writeValueAsString(payload);
			LegacyReportConfig config = objectMapper.readValue(json, LegacyReportConfig.class);
			
			// Validate using the service
			ReportValidationResult validationResult = reportBuilderService.validateLegacyReport(config);
			
			response.put("success", true);
			response.put("valid", validationResult.isValid());
			response.put("errors", validationResult.getErrors());
			response.put("warnings", validationResult.getWarnings());
			
			// Add SQL validation details
			SimpleObject sqlValidation = new SimpleObject();
			sqlValidation.put("passed", validationResult.getSqlValidation().isPassed());
			sqlValidation.put("sqlErrors", validationResult.getSqlValidation().getSqlErrors());
			sqlValidation.put("sqlWarnings", validationResult.getSqlValidation().getSqlWarnings());
			response.put("sqlValidation", sqlValidation);
			
			// Add basic report info
			response.put("reportName", config.getName());
			response.put("parametersCount", config.getParameters() != null ? config.getParameters().size() : 0);
			response.put("datasetsCount", config.getDataSetDefinitions() != null ? config.getDataSetDefinitions().size() : 0);
			
		}
		catch (Exception e) {
			response.put("success", false);
			response.put("valid", false);
			response.put("error", "Failed to validate JSON: " + e.getMessage());
		}
		
		return response;
	}
}
