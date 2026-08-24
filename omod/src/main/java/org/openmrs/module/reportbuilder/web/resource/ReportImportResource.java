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
import org.openmrs.module.reportbuilder.web.controller.dto.ImportRequest;
import org.openmrs.module.reportbuilder.web.controller.dto.ImportResult;
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
import java.util.List;

/**
 * REST resource for importing ReportBuilder entities from distribution packages. Provides endpoints
 * to import reports and dependencies from exported directories.
 */
@Resource(name = RestConstants.VERSION_1 + "/reportbuilder/import", supportedClass = ImportResult.class, supportedOpenmrsVersions = { "1.8 - 9.0.*" })
public class ReportImportResource extends DelegatingCrudResource<ImportResult> {
	
	@Override
	public ImportResult newDelegate() {
		return new ImportResult();
	}
	
	@Override
	public ImportResult save(ImportResult delegate) {
		throw new UnsupportedOperationException("Use POST to import reports");
	}
	
	@Override
	public Object create(SimpleObject post, RequestContext context) throws ResponseException {
		return post(post, context);
	}
	
	@Override
	public DelegatingResourceDescription getCreatableProperties() {
		// Import resource doesn't support standard creation properties
		return new DelegatingResourceDescription();
	}
	
	@Override
	public ImportResult getByUniqueId(String uniqueId) {
		throw new UnsupportedOperationException("Import results are not retrievable by ID");
	}
	
	@Override
	protected void delete(ImportResult delegate, String reason, RequestContext context) throws ResponseException {
		throw new UnsupportedOperationException("Import results cannot be deleted");
	}
	
	@Override
	public void purge(ImportResult delegate, RequestContext context) throws ResponseException {
		throw new UnsupportedOperationException("Import results cannot be purged");
	}
	
	private ReportBuilderService getImportService() {
		return Context.getService(ReportBuilderService.class);
	}
	
	/**
	 * POST handler for importing reports from a directory. Expected request body: {
	 * "sourceDirectory": "/path/to/distribution/package" // optional - uses default if not provided
	 * }
	 * 
	 * @param importRequest The import request
	 * @param context The request context
	 * @return SimpleObject with success status and ImportResult data
	 */
	public Object post(Object importRequest, RequestContext context) throws ResponseException {
		try {
			// Convert request to ImportRequest
			ImportRequest request;
			if (importRequest instanceof ImportRequest) {
				request = (ImportRequest) importRequest;
			} else if (importRequest instanceof SimpleObject) {
				request = convertToImportRequest((SimpleObject) importRequest);
			} else {
				throw new IllegalArgumentException("Invalid request format");
			}
			
			// Determine source directory
			File sourceDir;
			if (request.getSourceDirectory() != null && !request.getSourceDirectory().trim().isEmpty()) {
				sourceDir = new File(request.getSourceDirectory());
			} else {
				sourceDir = getImportService().getDefaultImportDirectory();
			}
			
			if (!sourceDir.exists() || !sourceDir.isDirectory()) {
				throw new IllegalArgumentException("Invalid source directory: " + sourceDir.getAbsolutePath());
			}
			
			// Validate package structure (optional but recommended)
			if (!getImportService().validatePackage(sourceDir)) {
				throw new IllegalArgumentException("Invalid distribution package structure");
			}
			
			// Execute import
			ImportResult result = getImportService().importFromDirectory(sourceDir);
			
			// Build response
			SimpleObject response = new SimpleObject();
			response.put("success", result.isSuccess());
			response.put("summary", result.getSummary());
			response.put("data", resultToSimpleObject(result));
			
			return response;
			
		}
		catch (Exception e) {
			throw new IllegalArgumentException("Failed to import reports: " + e.getMessage(), e);
		}
	}
	
	/**
	 * Convert SimpleObject to ImportRequest
	 */
	private ImportRequest convertToImportRequest(SimpleObject obj) {
		ImportRequest request = new ImportRequest();
		request.setSourceDirectory((String) obj.get("sourceDirectory"));
		return request;
	}
	
	/**
	 * Convert ImportResult to SimpleObject for JSON response
	 */
	private SimpleObject resultToSimpleObject(ImportResult result) {
		SimpleObject obj = new SimpleObject();
		obj.put("success", result.isSuccess());
		obj.put("summary", result.getSummary());
		obj.put("successCount", result.getSuccessCount());
		obj.put("errorCount", result.getErrorCount());
		
		// Add successes - Java 7 compatible
		java.util.List<SimpleObject> successList = new java.util.ArrayList<SimpleObject>();
		for (ImportResult.ImportSuccess success : result.getSuccesses()) {
			SimpleObject s = new SimpleObject();
			s.put("type", success.getType());
			s.put("filename", success.getFilename());
			successList.add(s);
		}
		obj.put("successes", successList.toArray(new SimpleObject[0]));
		
		// Add errors - Java 7 compatible
		java.util.List<SimpleObject> errorList = new java.util.ArrayList<SimpleObject>();
		for (ImportResult.ImportError error : result.getErrors()) {
			SimpleObject e = new SimpleObject();
			e.put("type", error.getType());
			e.put("filename", error.getFilename());
			e.put("message", error.getMessage());
			errorList.add(e);
		}
		obj.put("errors", errorList.toArray(new SimpleObject[0]));
		
		return obj;
	}
	
	@Override
	protected PageableResult doGetAll(RequestContext context) throws ResponseException {
		// Import results are not queryable
		return new NeedsPaging<ImportResult>(java.util.Collections.<ImportResult> emptyList(), context);
	}
	
	@Override
	public DelegatingResourceDescription getRepresentationDescription(Representation rep) {
		DelegatingResourceDescription description = new DelegatingResourceDescription();
		if (rep instanceof DefaultRepresentation) {
			description.addProperty("success");
			description.addProperty("summary");
			description.addProperty("successCount");
			description.addProperty("errorCount");
			description.addSelfLink();
			return description;
		}
		if (rep instanceof FullRepresentation) {
			description.addProperty("success");
			description.addProperty("summary");
			description.addProperty("successCount");
			description.addProperty("errorCount");
			description.addProperty("successes");
			description.addProperty("errors");
			description.addSelfLink();
			return description;
		}
		return null;
	}
	
	@Override
	protected PageableResult doSearch(RequestContext context) throws ResponseException {
		// Import results are not searchable
		return new NeedsPaging<ImportResult>(java.util.Collections.<ImportResult> emptyList(), context);
	}
}
