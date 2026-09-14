/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 * <p>
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.reportbuilder.web.resource;

import org.openmrs.api.context.Context;
import org.openmrs.module.reportbuilder.api.ReportBuilderService;
import org.openmrs.module.reportbuilder.model.ReportBuilderReport;
import org.openmrs.module.reportbuilder.web.controller.dto.ImportRequest;
import org.openmrs.module.reportbuilder.web.controller.dto.ImportResult;
import org.openmrs.module.reportbuilder.web.controller.dto.SerializedReport;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * REST resource for importing ReportBuilder entities from distribution packages. Provides endpoints
 * to import reports and dependencies from exported directories.
 */
@Resource(name = RestConstants.VERSION_1 + "/reportbuilder/import", supportedClass = ImportResult.class, supportedOpenmrsVersions = { "1.8 - 9.0.*" })
public class ReportImportResource extends DelegatingCrudResource<ImportResult> {
	
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ReportImportResource.class);
	
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
	 * "sourceDirectory": "/path/to/distribution/package", // optional - uses default if not
	 * provided "type": "artifacts" // or "compiledReports" for compiled reports import }
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
			
			// Type-based routing
			String importType = request.getType();
			if ("compiledReports".equals(importType)) {
				return importCompiledReports(request);
			} else {
				// Default to artifacts (existing bulk import)
				if (!getImportService().validatePackage(sourceDir)) {
					throw new IllegalArgumentException("Invalid distribution package structure");
				}
				ImportResult result = getImportService().importFromDirectory(sourceDir);
				SimpleObject response = new SimpleObject();
				response.put("success", result.isSuccess());
				response.put("summary", result.getSummary());
				response.put("data", resultToSimpleObject(result));
				return response;
			}
			
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
		request.setType((String) obj.get("type"));
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
	
	/**
	 * Import compiled reports from configuration/reports/ directory
	 */
	private Object importCompiledReports(ImportRequest request) {
		try {
			// Determine source directory (default to import directory)
			File sourceDir;
			if (request.getSourceDirectory() != null && !request.getSourceDirectory().trim().isEmpty()) {
				sourceDir = new File(request.getSourceDirectory());
			} else {
				sourceDir = getImportService().getDefaultImportDirectory();
			}
			
			// Look for compiled reports in the reports subdirectory
			File reportsDir = new File(sourceDir, "configuration" + File.separator + "reports");
			if (!reportsDir.exists() || !reportsDir.isDirectory()) {
				throw new IllegalArgumentException("No reports directory found at: " + reportsDir.getAbsolutePath());
			}
			
			log.info("Importing compiled reports from: {}", reportsDir.getAbsolutePath());
			
			int successCount = 0;
			int errorCount = 0;
			int skippedCount = 0;
			List<SimpleObject> importedReports = new ArrayList<SimpleObject>();
			
			// Walk every .json under configuration/reports - including distribution packages
			// shipped under dist/{aggregates,linelist} by the export flow.
			List<File> candidateFiles = new ArrayList<File>();
			collectJsonFiles(reportsDir, candidateFiles);
			
			for (File reportFile : candidateFiles) {
				try {
					com.fasterxml.jackson.databind.JsonNode root = readRoot(reportFile);
					if (root == null || !root.isObject()) {
						// Unparseable/non-object JSON - cannot be a report at all.
						skippedCount++;
						log.warn("Skipping unrecognized JSON file: {}", reportFile.getAbsolutePath());
						continue;
					}
					
					ReportBuilderService.CompiledReportArtifacts result;
					if (root.has("uuid") && root.has("config")) {
						// Shipped SerializedReport wrapper (export flow, under dist/).
						log.info("Importing shipped compiled report: {}", reportFile.getName());
						result = getImportService().importSerializedReport(reportFile, null);
					} else {
						// Raw compiled design produced by compile - the canonical folder is itself a
						// valid package. Synthesize a SerializedReport for entity-level import.
						log.info("Importing raw compiled design: {}", reportFile.getName());
						SerializedReport serializedReport = toSerializedReport(root, reportFile);
						result = getImportService().importSerializedReportFromObject(serializedReport, null);
					}
					
					SimpleObject reportInfo = new SimpleObject();
					if (result.getReportBuilderReport() != null) {
						reportInfo.put("uuid", result.getReportBuilderReport().getUuid());
						reportInfo.put("name", result.getReportBuilderReport().getName());
						reportInfo.put("code", result.getReportBuilderReport().getCode());
						
						// Create or update the linked ReportLibrary entry for this report, pointing
						// it at the definition actually saved by this import (carries the
						// package-stamped reportDefinitionUuid).
						getImportService().saveOrUpdateLibraryEntry(result.getReportBuilderReport().getUuid(),
						    result.getReportDefinition() != null ? result.getReportDefinition().getUuid() : null);
					}
					if (result.getReportDefinition() != null) {
						reportInfo.put("reportDefinitionUuid", result.getReportDefinition().getUuid());
					}
					reportInfo.put("file", reportFile.getName());
					importedReports.add(reportInfo);
					
					successCount++;
					log.info("Successfully imported compiled report: {}", reportFile.getName());
					
				}
				catch (Exception e) {
					errorCount++;
					log.error("Failed to import compiled report: {}", reportFile.getName(), e);
					try {
						Context.clearSession();
					}
					catch (Exception inner) {
						log.debug("Failed to clear session after import error", inner);
					}
				}
			}
			
			// Build response
			SimpleObject response = new SimpleObject();
			response.put("success", errorCount == 0);
			response.put("message", String.format("Imported %d compiled reports, %d failed (%d unrecognized files skipped)",
			    successCount, errorCount, skippedCount));
			response.put("sourceDirectory", reportsDir.getAbsolutePath());
			response.put("importedReports", importedReports);
			response.put("successCount", successCount);
			response.put("errorCount", errorCount);
			response.put("skippedCount", skippedCount);
			
			return response;
		}
		catch (Exception e) {
			SimpleObject response = new SimpleObject();
			response.put("success", false);
			response.put("message", "Failed to import compiled reports: " + e.getMessage());
			return response;
		}
	}
	
	/**
	 * Recursively collects .json files under dir. - Java 7 compatible
	 */
	private void collectJsonFiles(File dir, List<File> out) {
		File[] children = dir.listFiles();
		if (children == null) {
			return;
		}
		for (File child : children) {
			if (child.isDirectory()) {
				collectJsonFiles(child, out);
			} else if (child.getName().endsWith(".json")) {
				out.add(child);
			}
		}
	}
	
	/**
	 * Parses a JSON file, returning null when it is unreadable or not valid JSON.
	 */
	private com.fasterxml.jackson.databind.JsonNode readRoot(File file) {
		try {
			return new com.fasterxml.jackson.databind.ObjectMapper().readTree(file);
		}
		catch (Exception e) {
			return null;
		}
	}
	
	private String textOrNull(com.fasterxml.jackson.databind.JsonNode node, String field) {
		if (node != null && node.hasNonNull(field)) {
			String value = node.get(field).asText();
			return value == null || value.trim().isEmpty() ? null : value;
		}
		return null;
	}
	
	/**
	 * Builds a SerializedReport from a raw compiled design file. Identity comes from the stamped
	 * flat keys written at compile time (name, code, category, reportType,
	 * reportBuilderReportUuid). Falls back to the filename and folder location (linelist vs other)
	 * for files compiled before identity stamping existed.
	 */
	private org.openmrs.module.reportbuilder.web.controller.dto.SerializedReport toSerializedReport(
	        com.fasterxml.jackson.databind.JsonNode root, File reportFile) {
		
		org.openmrs.module.reportbuilder.web.controller.dto.SerializedReport serialized = new org.openmrs.module.reportbuilder.web.controller.dto.SerializedReport();
		
		String fallbackName = reportFile.getName();
		if (fallbackName.endsWith(".json")) {
			fallbackName = fallbackName.substring(0, fallbackName.length() - ".json".length());
		}
		
		String name = textOrNull(root, "name");
		serialized.setName(name != null ? name : fallbackName);
		serialized.setCode(textOrNull(root, "code"));
		serialized.setDescription(textOrNull(root, "description"));
		serialized.setCategory(textOrNull(root, "category"));
		serialized.setCategoryUuid(textOrNull(root, "categoryUuid"));
		
		// Identity uuid stamped by the source instance keeps imports idempotent across sites.
		String builderUuid = textOrNull(root, "reportBuilderReportUuid");
		if (builderUuid != null) {
			serialized.setUuid(builderUuid);
		}
		
		// Explicit stamping wins over folder inference; enum parsing tolerates LINELIST/LINE_LIST.
		String stampedType = textOrNull(root, "reportType");
		if (stampedType != null) {
			serialized.setReportType(ReportBuilderReport.ReportType.fromString(stampedType).name());
		} else {
			File parent = reportFile.getParentFile();
			boolean linelistFolder = parent != null && "linelist".equalsIgnoreCase(parent.getName());
			serialized.setReportType(linelistFolder ? "LINE_LIST" : "AGGREGATE");
		}
		
		serialized.setStatus("COMPILED");
		serialized.setConfig((com.fasterxml.jackson.databind.node.ObjectNode) root);
		return serialized;
	}
	
}
