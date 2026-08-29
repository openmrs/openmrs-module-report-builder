/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.reportbuilder.web.resource;

import org.openmrs.api.context.Context;
import org.openmrs.module.reportbuilder.api.ReportBuilderService;
import org.openmrs.module.reportbuilder.web.controller.dto.ShippingRequest;
import org.openmrs.module.reportbuilder.web.controller.dto.ShippingResult;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.RequestContext;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.annotation.Resource;
import org.openmrs.module.webservices.rest.web.resource.api.PageableResult;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingCrudResource;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingResourceDescription;
import org.openmrs.module.webservices.rest.web.resource.impl.NeedsPaging;
import org.openmrs.module.webservices.rest.web.representation.DefaultRepresentation;
import org.openmrs.module.webservices.rest.web.representation.FullRepresentation;
import org.openmrs.module.webservices.rest.web.representation.Representation;
import org.openmrs.module.webservices.rest.web.response.ResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * REST resource for shipping/exporting ReportBuilder reports. Provides endpoints to export reports
 * and dependencies to distribution packages.
 */
@Resource(name = RestConstants.VERSION_1 + "/reportbuilder/ship", supportedClass = ShippingResult.class, supportedOpenmrsVersions = { "1.8 - 9.0.*" })
public class ReportShippingResource extends DelegatingCrudResource<ShippingResult> {
	
	private static final Logger log = LoggerFactory.getLogger(ReportShippingResource.class);
	
	@Override
	public ShippingResult newDelegate() {
		return new ShippingResult();
	}
	
	@Override
	public Object create(SimpleObject post, RequestContext context) throws ResponseException {
		return post(post, context);
	}
	
	@Override
	public ShippingResult save(ShippingResult delegate) {
		throw new UnsupportedOperationException("Use POST to ship reports");
	}
	
	@Override
	public DelegatingResourceDescription getCreatableProperties() {
		// Shipping resource doesn't support standard creation properties
		return new DelegatingResourceDescription();
	}
	
	@Override
	public ShippingResult getByUniqueId(String uniqueId) {
		throw new UnsupportedOperationException("Shipping results are not retrievable by ID");
	}
	
	@Override
	protected void delete(ShippingResult delegate, String reason, RequestContext context) throws ResponseException {
		throw new UnsupportedOperationException("Shipping results cannot be deleted");
	}
	
	@Override
	public void purge(ShippingResult delegate, RequestContext context) throws ResponseException {
		throw new UnsupportedOperationException("Shipping results cannot be purged");
	}
	
	private ReportBuilderService getShippingService() {
		return Context.getService(ReportBuilderService.class);
	}
	
	/**
	 * POST handler for shipping/exporting reports based on type. Simple type-based routing:
	 * <p>
	 * 1. Export all compiled reports: { "type": "compiledReports", "destination": "/optional/path",
	 * "version": "1.0.0" }
	 * </p>
	 * <p>
	 * 2. Export all artifacts with dependencies: { "type": "artifacts", "destination":
	 * "/optional/path", "version": "1.0.0" }
	 * </p>
	 * 
	 * @param shippingRequest The shipping request
	 * @param context The request context
	 * @return SimpleObject with success status and export data
	 */
	public Object post(Object shippingRequest, RequestContext context) throws ResponseException {
		try {
			// Convert request to ShippingRequest
			ShippingRequest request;
			if (shippingRequest instanceof ShippingRequest) {
				request = (ShippingRequest) shippingRequest;
			} else if (shippingRequest instanceof SimpleObject) {
				request = convertToShippingRequest((SimpleObject) shippingRequest);
			} else {
				throw new IllegalArgumentException("Invalid request format");
			}
			
			// Determine export type (default to artifacts)
			String type = request.getType();
			if (type == null || type.trim().isEmpty()) {
				type = "artifacts";
			}
			
			log.info("Processing export request with type: {}", type);
			
			// Route based on type
			if ("compiledReports".equalsIgnoreCase(type)) {
				return exportAllCompiledReports(request);
			} else if ("artifacts".equalsIgnoreCase(type)) {
				return exportAllArtifacts(request);
			} else {
				throw new IllegalArgumentException("Invalid export type: " + type
				        + ". Must be 'compiledReports' or 'artifacts'");
			}
			
		}
		catch (Exception e) {
			throw new IllegalArgumentException("Failed to export reports: " + e.getMessage(), e);
		}
	}
	
	/**
	 * Export all compiled reports
	 */
	private Object exportAllCompiledReports(ShippingRequest request) {
		try {
			// Determine destination directory
			File destination;
			if (request.getDestination() != null && !request.getDestination().trim().isEmpty()) {
				destination = new File(request.getDestination());
			} else {
				destination = getShippingService().getDefaultShippingDirectory();
			}
			
			// Auto-generate version if not provided
			String version = request.getVersion();
			if (version == null || version.trim().isEmpty()) {
				version = generateVersion();
				log.debug("Auto-generated version: {}", version);
			}
			
			log.info("Exporting all compiled reports to: {} with version: {}", destination.getAbsolutePath(), version);
			
			// Get all reports and export each as compiled report
			List<org.openmrs.module.reportbuilder.model.ReportBuilderReport> reports = getShippingService()
			        .getReportBuilderReports(null, false, null, null);
			
			int successCount = 0;
			int errorCount = 0;
			List<String> exportedFiles = new ArrayList<String>();
			
			for (org.openmrs.module.reportbuilder.model.ReportBuilderReport report : reports) {
				try {
					File exportedFile = getShippingService().exportCompiledReport(report.getUuid(), destination);
					exportedFiles.add(exportedFile.getAbsolutePath());
					successCount++;
					log.debug("Exported compiled report: {} to {}", report.getName(), exportedFile.getName());
				}
				catch (Exception e) {
					errorCount++;
					log.error("Failed to export compiled report: {}", report.getName(), e);
				}
			}
			
			// Build response
			SimpleObject response = new SimpleObject();
			response.put("success", errorCount == 0);
			response.put("message", String.format("Exported %d compiled reports, %d failed", successCount, errorCount));
			response.put("version", version);
			response.put("destination", destination.getAbsolutePath());
			response.put("exportedFiles", exportedFiles);
			response.put("successCount", successCount);
			response.put("errorCount", errorCount);
			
			return response;
		}
		catch (Exception e) {
			SimpleObject response = new SimpleObject();
			response.put("success", false);
			response.put("message", "Failed to export compiled reports: " + e.getMessage());
			return response;
		}
	}
	
	/**
	 * Export all artifacts with dependencies (existing bulk functionality)
	 */
	private Object exportAllArtifacts(ShippingRequest request) {
		try {
			// Determine destination directory
			File destination;
			if (request.getDestination() != null && !request.getDestination().trim().isEmpty()) {
				destination = new File(request.getDestination());
			} else {
				destination = getShippingService().getDefaultShippingDirectory();
			}
			
			// Auto-generate version if not provided
			String version = request.getVersion();
			if (version == null || version.trim().isEmpty()) {
				version = generateVersion();
				log.debug("Auto-generated version: {}", version);
			}
			
			// Execute bulk export - all ReportBuilder artifacts and compiled reports
			log.debug("Starting bulk export of all ReportBuilder artifacts and compiled reports with version: {}", version);
			ShippingResult result = getShippingService().shipAllReports(version, destination);
			
			// Build response
			SimpleObject response = new SimpleObject();
			response.put("success", result.isSuccess());
			response.put("message",
			    result.isSuccess() ? "All artifacts extracted successfully to " + destination.getAbsolutePath()
			            : "Failed to extract artifacts: " + result.getErrorMessage());
			response.put("data", resultToSimpleObject(result));
			
			return response;
		}
		catch (Exception e) {
			SimpleObject response = new SimpleObject();
			response.put("success", false);
			response.put("message", "Failed to export artifacts: " + e.getMessage());
			return response;
		}
	}
	
	/**
	 * Convert SimpleObject to ShippingRequest
	 */
	private ShippingRequest convertToShippingRequest(SimpleObject obj) {
		ShippingRequest request = new ShippingRequest();
		request.setVersion((String) obj.get("version"));
		request.setDestination((String) obj.get("destination"));
		request.setType((String) obj.get("type"));
		return request;
	}
	
	/**
	 * Convert ShippingResult to SimpleObject for JSON response
	 */
	private SimpleObject resultToSimpleObject(ShippingResult result) {
		SimpleObject obj = new SimpleObject();
		obj.put("success", result.isSuccess());
		obj.put("reportCode", result.getReportCode());
		obj.put("version", result.getVersion());
		obj.put("sourceFile", result.getSourceFile());
		obj.put("compiledFile", result.getCompiledFile());
		obj.put("versionFile", result.getVersionFile());
		obj.put("errorMessage", result.getErrorMessage());
		
		// Add dependencies
		if (result.getDependencies() != null) {
			SimpleObject deps = new SimpleObject();
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
	
	@Override
	protected PageableResult doGetAll(RequestContext context) throws ResponseException {
		// Shipping results are not queryable
		return new NeedsPaging<ShippingResult>(Collections.<ShippingResult> emptyList(), context);
	}
	
	@Override
	public DelegatingResourceDescription getRepresentationDescription(Representation rep) {
		DelegatingResourceDescription description = new DelegatingResourceDescription();
		if (rep instanceof DefaultRepresentation) {
			description.addProperty("success");
			description.addProperty("reportCode");
			description.addProperty("version");
			description.addProperty("sourceFile");
			description.addProperty("compiledFile");
			description.addProperty("versionFile");
			description.addProperty("errorMessage");
			description.addSelfLink();
			return description;
		}
		if (rep instanceof FullRepresentation) {
			description.addProperty("success");
			description.addProperty("reportCode");
			description.addProperty("version");
			description.addProperty("sourceFile");
			description.addProperty("compiledFile");
			description.addProperty("versionFile");
			description.addProperty("errorMessage");
			description.addProperty("dependencies");
			description.addSelfLink();
			return description;
		}
		return null;
	}
	
	@Override
	protected PageableResult doSearch(RequestContext context) throws ResponseException {
		// Shipping results are not searchable
		return new NeedsPaging<ShippingResult>(Collections.<ShippingResult> emptyList(), context);
	}
	
	/**
	 * POST handler for exporting all reports with dependencies. Expected request body: { "version":
	 * "1.0.0", "destination": "/optional/path" }
	 * 
	 * @param bulkRequest The bulk export request
	 * @param context The request context
	 * @return SimpleObject with success status and aggregated export data
	 */
	public Object postAll(Object bulkRequest, RequestContext context) throws ResponseException {
		try {
			SimpleObject requestObj;
			if (bulkRequest instanceof SimpleObject) {
				requestObj = (SimpleObject) bulkRequest;
			} else {
				throw new IllegalArgumentException("Invalid request format");
			}
			
			String version = (String) requestObj.get("version");
			String destinationPath = (String) requestObj.get("destination");
			
			// Validate inputs
			if (version == null || version.trim().isEmpty()) {
				throw new IllegalArgumentException("version is required");
			}
			
			// Determine destination directory
			File destination;
			if (destinationPath != null && !destinationPath.trim().isEmpty()) {
				destination = new File(destinationPath);
			} else {
				destination = getShippingService().getDefaultShippingDirectory();
			}
			
			// Execute bulk export
			ShippingResult result = getShippingService().shipAllReports(version, destination);
			
			// Build response
			SimpleObject response = new SimpleObject();
			response.put("success", result.isSuccess());
			response.put("message",
			    result.isSuccess() ? "All reports exported successfully to " + destination.getAbsolutePath()
			            : "Failed to export all reports: " + result.getErrorMessage());
			response.put("data", resultToSimpleObject(result));
			
			return response;
			
		}
		catch (Exception e) {
			throw new IllegalArgumentException("Failed to export all reports: " + e.getMessage(), e);
		}
	}
	
	/**
	 * POST handler for exporting all entities of specific types. Expected request body: {
	 * "entityTypes": ["reports", "categories", "themes"], "version": "1.0.0", "destination":
	 * "/optional/path" }
	 * 
	 * @param bulkRequest The bulk entity export request
	 * @param context The request context
	 * @return SimpleObject with success status and export results
	 */
	public Object postBulk(Object bulkRequest, RequestContext context) throws ResponseException {
		try {
			SimpleObject requestObj;
			if (bulkRequest instanceof SimpleObject) {
				requestObj = (SimpleObject) bulkRequest;
			} else {
				throw new IllegalArgumentException("Invalid request format");
			}
			
			// Extract entity types
			List<String> entityTypes = (List<String>) requestObj.get("entityTypes");
			String version = (String) requestObj.get("version");
			String destinationPath = (String) requestObj.get("destination");
			
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
				destination = getShippingService().getDefaultShippingDirectory();
			}
			
			// Execute bulk export
			ShippingResult result = getShippingService().shipAllEntities(entityTypes, version, destination);
			
			// Build response
			SimpleObject response = new SimpleObject();
			response.put("success", result.isSuccess());
			response.put("message",
			    result.isSuccess() ? "Bulk export completed successfully to " + destination.getAbsolutePath()
			            : "Failed to complete bulk export: " + result.getErrorMessage());
			response.put("data", resultToSimpleObject(result));
			
			return response;
			
		}
		catch (Exception e) {
			throw new IllegalArgumentException("Failed to complete bulk export: " + e.getMessage(), e);
		}
	}
	
	/**
	 * Generate a version string based on current timestamp Format: YYYY.MM.DD-HHMM
	 */
	private String generateVersion() {
		java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy.MM.dd-HHmm");
		return sdf.format(new java.util.Date());
	}
}
