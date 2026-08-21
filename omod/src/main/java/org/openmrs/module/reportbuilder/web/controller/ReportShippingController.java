/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.reportbuilder.web.controller;

import org.openmrs.api.context.Context;
import org.openmrs.module.reportbuilder.api.ReportBuilderService;
import org.openmrs.module.reportbuilder.web.controller.dto.ShippingResult;
import org.openmrs.module.webservices.rest.web.RestConstants;
import java.util.Map;
import java.util.HashMap;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.List;

/**
 * REST controller for Report Shipping endpoints. Provides additional URLs for bulk export
 * operations.
 */
@Controller
@RequestMapping(value = "/rest/" + RestConstants.VERSION_1 + "/reportbuilder/ship")
public class ReportShippingController {
	
	private ReportBuilderService getReportBuilderService() {
		return Context.getService(ReportBuilderService.class);
	}
	
	/**
	 * Export all reports with dependencies POST /ws/rest/v1/reportbuilder/ship/all
	 * 
	 * @param requestBody Request body with version and optional destination
	 * @return Map<String, Object> with success status and export results
	 */
	@RequestMapping(method = RequestMethod.POST, value = "/all")
	@ResponseBody
	public Object exportAllReports(@RequestBody Map<String, Object> requestBody) {
		try {
			String version = (String) requestBody.get("version");
			String destinationPath = (String) requestBody.get("destination");
			
			// Validate inputs
			if (version == null || version.trim().isEmpty()) {
				throw new IllegalArgumentException("version is required");
			}
			
			// Determine destination directory
			File destination;
			if (destinationPath != null && !destinationPath.trim().isEmpty()) {
				destination = new File(destinationPath);
			} else {
				destination = getReportBuilderService().getDefaultShippingDirectory();
			}
			
			// Execute bulk export
			ShippingResult result = getReportBuilderService().shipAllReports(version, destination);
			
			// Build response
			Map<String, Object> response = new HashMap<>();
			response.put("success", result.isSuccess());
			response.put("message",
			    result.isSuccess() ? "All reports exported successfully to " + destination.getAbsolutePath()
			            : "Failed to export all reports: " + result.getErrorMessage());
			response.put("data", resultToMap(result));
			
			return response;
			
		}
		catch (Exception e) {
			throw new IllegalArgumentException("Failed to export all reports: " + e.getMessage(), e);
		}
	}
	
	/**
	 * Export all entities of specific types POST /ws/rest/v1/reportbuilder/ship/bulk
	 * 
	 * @param requestBody Request body with entityTypes, version, and optional destination
	 * @return Map<String, Object> with success status and export results
	 */
	@RequestMapping(method = RequestMethod.POST, value = "/bulk")
	@ResponseBody
	public Object exportBulkEntities(@RequestBody Map<String, Object> requestBody) {
		try {
			// Extract entity types
			@SuppressWarnings("unchecked")
			List<String> entityTypes = (List<String>) requestBody.get("entityTypes");
			String version = (String) requestBody.get("version");
			String destinationPath = (String) requestBody.get("destination");
			
			// Validate inputs
			if (entityTypes == null || entityTypes.isEmpty()) {
				throw new IllegalArgumentException("entityTypes is required");
			}
			if (version == null || version.trim().isEmpty()) {
				throw new IllegalArgumentException("version is required");
			}
			
			// Determine destination directory
			File destination;
			if (destinationPath != null && !destinationPath.trim().isEmpty()) {
				destination = new File(destinationPath);
			} else {
				destination = getReportBuilderService().getDefaultShippingDirectory();
			}
			
			// Execute bulk export
			ShippingResult result = getReportBuilderService().shipAllEntities(entityTypes, version, destination);
			
			// Build response
			Map<String, Object> response = new HashMap<>();
			response.put("success", result.isSuccess());
			response.put("message",
			    result.isSuccess() ? "Bulk export completed successfully to " + destination.getAbsolutePath()
			            : "Failed to complete bulk export: " + result.getErrorMessage());
			response.put("data", resultToMap(result));
			
			return response;
			
		}
		catch (Exception e) {
			throw new IllegalArgumentException("Failed to complete bulk export: " + e.getMessage(), e);
		}
	}
	
	/**
	 * Convert ShippingResult to Map<String, Object> for JSON response
	 */
	private Map<String, Object> resultToMap(ShippingResult result) {
		Map<String, Object> obj = new HashMap<>();
		obj.put("success", result.isSuccess());
		obj.put("reportCode", result.getReportCode());
		obj.put("version", result.getVersion());
		obj.put("sourceFile", result.getSourceFile());
		obj.put("compiledFile", result.getCompiledFile());
		obj.put("versionFile", result.getVersionFile());
		obj.put("errorMessage", result.getErrorMessage());
		
		// Add dependencies
		if (result.getDependencies() != null) {
			Map<String, Object> deps = new HashMap<>();
			deps.put("categories", result.getDependencies().getCategories());
			deps.put("library", result.getDependencies().getLibrary());
			deps.put("indicators", result.getDependencies().getIndicators());
			deps.put("sections", result.getDependencies().getSections());
			deps.put("themes", result.getDependencies().getThemes());
			deps.put("ageCategories", result.getDependencies().getAgeCategories());
			deps.put("ageGroups", result.getDependencies().getAgeGroups());
			deps.put("etlSources", result.getDependencies().getEtlSources());
			deps.put("etlMonitors", result.getDependencies().getEtlMonitors());
			obj.put("dependencies", deps);
		}
		
		return obj;
	}
}
