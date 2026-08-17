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
import org.openmrs.module.reportbuilder.api.ReportShippingService;
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

import java.io.File;
import java.util.Collections;

/**
 * REST resource for shipping/exporting ReportBuilder reports. Provides endpoints to export reports
 * and dependencies to distribution packages.
 */
@Resource(name = RestConstants.VERSION_1 + "/reportbuilder/ship", supportedClass = ShippingResult.class, supportedOpenmrsVersions = { "1.8 - 9.0.*" })
public class ReportShippingResource extends DelegatingCrudResource<ShippingResult> {
	
	@Override
	public ShippingResult newDelegate() {
		return new ShippingResult();
	}
	
	@Override
	public ShippingResult save(ShippingResult delegate) {
		throw new UnsupportedOperationException("Use POST to ship reports");
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
	
	private ReportShippingService getShippingService() {
		return Context.getService(ReportShippingService.class);
	}
	
	/**
	 * POST handler for shipping a single report. Expected request body: { "reportUuid":
	 * "uuid-here", "version": "1.0.0", "destination": "/optional/path" // defaults to OpenMRS
	 * config dir }
	 * 
	 * @param shippingRequest The shipping request
	 * @param context The request context
	 * @return SimpleObject with success status and ShippingResult data
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
			
			// Determine destination directory
			File destination;
			if (request.getDestination() != null && !request.getDestination().trim().isEmpty()) {
				destination = new File(request.getDestination());
			} else {
				destination = getShippingService().getDefaultShippingDirectory();
			}
			
			// Validate inputs
			if (request.getReportUuid() == null || request.getReportUuid().trim().isEmpty()) {
				throw new IllegalArgumentException("reportUuid is required");
			}
			if (request.getVersion() == null || request.getVersion().trim().isEmpty()) {
				throw new IllegalArgumentException("version is required");
			}
			
			// Execute shipping
			ShippingResult result = getShippingService().shipReport(request.getReportUuid(), request.getVersion(),
			    destination);
			
			// Build response
			SimpleObject response = new SimpleObject();
			response.put("success", result.isSuccess());
			response.put("message", result.isSuccess() ? "Report shipped successfully to " + destination.getAbsolutePath()
			        : "Failed to ship report: " + result.getErrorMessage());
			response.put("data", resultToSimpleObject(result));
			
			return response;
			
		}
		catch (Exception e) {
			throw new IllegalArgumentException("Failed to ship report: " + e.getMessage(), e);
		}
	}
	
	/**
	 * Convert SimpleObject to ShippingRequest
	 */
	private ShippingRequest convertToShippingRequest(SimpleObject obj) {
		ShippingRequest request = new ShippingRequest();
		request.setReportUuid((String) obj.get("reportUuid"));
		request.setVersion((String) obj.get("version"));
		request.setDestination((String) obj.get("destination"));
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
}
