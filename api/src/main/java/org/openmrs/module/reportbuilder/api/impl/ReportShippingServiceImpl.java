/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.reportbuilder.api.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.reportbuilder.api.ReportBuilderService;
import org.openmrs.module.reportbuilder.api.ReportShippingService;
import org.openmrs.module.reportbuilder.api.db.ReportBuilderDAO;
import org.openmrs.module.reportbuilder.model.*;
import org.openmrs.module.reportbuilder.web.controller.dto.*;
import org.openmrs.util.OpenmrsUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Implementation of the ReportShippingService. Handles export/shipping of ReportBuilder reports and
 * dependencies to distribution packages.
 */
@Transactional
public class ReportShippingServiceImpl extends BaseOpenmrsService implements ReportShippingService {
	
	private static final Logger log = LoggerFactory.getLogger(ReportShippingServiceImpl.class);
	
	/**
	 * Import order respecting dependencies - foundation entities first
	 */
	private static final List<String> DEPENDENCY_ORDER = Arrays.asList("categories", "age-categories", "age-groups",
	    "etl-sources", "etl-monitors", "indicators", "sections", "themes", "reports", "library");
	
	private ReportBuilderDAO dao;
	
	private final ObjectMapper objectMapper = new ObjectMapper();
	
	public void setDao(ReportBuilderDAO dao) {
		this.dao = dao;
	}
	
	@Override
	public ShippingResult shipReport(String reportUuid, String version, File destination) {
		ShippingResult result = new ShippingResult();
		
		try {
			log.info("Shipping report: {} version: {} to: {}", reportUuid, version, destination.getAbsolutePath());
			
			// Validate report exists
			ReportBuilderReport report = dao.getReportBuilderReportByUuid(reportUuid);
			if (report == null) {
				result.setSuccess(false);
				result.setErrorMessage("Report not found with UUID: " + reportUuid);
				return result;
			}
			
			// Create directory structure
			createDirectories(destination);
			
			// Set basic result info
			result.setReportCode(report.getCode() != null ? report.getCode() : report.getUuid());
			result.setVersion(version);
			
			// Collect and export dependencies
			exportDependencies(report, destination, result);
			
			// Export the report source definition
			File sourceFile = exportReportSource(report, destination);
			result.setSourceFile(sourceFile.getAbsolutePath());
			
			// Compile and export the report configuration
			ObjectNode compiledConfig = compileReportConfiguration(report);
			File compiledFile = exportCompiledReport(report, compiledConfig, destination);
			result.setCompiledFile(compiledFile.getAbsolutePath());
			
			// Generate version metadata
			File versionFile = generateVersionMetadata(report, version, result, destination);
			result.setVersionFile(versionFile.getAbsolutePath());
			
			result.setSuccess(true);
			log.info("Successfully shipped report: {}", report.getName());
			
		}
		catch (Exception e) {
			log.error("Failed to ship report: {}", reportUuid, e);
			result.setSuccess(false);
			result.setErrorMessage(e.getMessage());
		}
		
		return result;
	}
	
	@Override
    public ShippingResult shipBatch(List<String> reportUuids, String version, File destination) {
        ShippingResult result = new ShippingResult();
        Set<String> combinedDependencies = new HashSet<>();

        try {
            log.info("Shipping batch of {} reports version: {} to: {}", reportUuids.size(), version, destination.getAbsolutePath());

            // Create directory structure
            createDirectories(destination);

            // Process each report and collect dependencies
            for (String reportUuid : reportUuids) {
                ReportBuilderReport report = dao.getReportBuilderReportByUuid(reportUuid);
                if (report == null) {
                    log.warn("Report not found with UUID: {}, skipping", reportUuid);
                    continue;
                }

                // Export report source
                exportReportSource(report, destination);

                // Compile and export
                ObjectNode compiledConfig = compileReportConfiguration(report);
                exportCompiledReport(report, compiledConfig, destination);

                // Dependencies would be accumulated here
                log.debug("Processed report: {}", report.getName());
            }

            // Generate combined version metadata
            // (Simplified - would need full implementation for batch shipping)
            result.setSuccess(true);
            result.setVersion(version);

        } catch (Exception e) {
            log.error("Failed to ship batch reports", e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
        }

        return result;
    }
	
	@Override
	public File exportEntity(String entityType, String entityUuid, File destination) {
		try {
			switch (entityType.toLowerCase()) {
				case "category":
					ReportCategory category = dao.getReportCategoryByUuid(entityUuid);
					if (category != null) {
						return exportCategory(category, destination);
					}
					break;
				case "indicator":
					ReportBuilderIndicator indicator = dao.getReportBuilderIndicatorByUuid(entityUuid);
					if (indicator != null) {
						return exportIndicator(indicator, destination);
					}
					break;
				case "section":
					ReportBuilderSection section = dao.getReportBuilderSectionByUuid(entityUuid);
					if (section != null) {
						return exportSection(section, destination);
					}
					break;
				case "theme":
					ReportBuilderDataTheme theme = dao.getReportBuilderDataThemeByUuid(entityUuid);
					if (theme != null) {
						return exportTheme(theme, destination);
					}
					break;
				case "age-category":
					ReportBuilderAgeCategory ageCategory = dao.getAgeCategoryByUuid(entityUuid);
					if (ageCategory != null) {
						return exportAgeCategory(ageCategory, destination);
					}
					break;
				case "etl-source":
					ETLSource etlSource = dao.getETLSourceByUuid(entityUuid);
					if (etlSource != null) {
						return exportETLSource(etlSource, destination);
					}
					break;
				case "etl-monitor":
					ETLMonitor etlMonitor = dao.getETLMonitorByUuid(entityUuid);
					if (etlMonitor != null) {
						return exportETLMonitor(etlMonitor, destination);
					}
					break;
				default:
					throw new IllegalArgumentException("Unknown entity type: " + entityType);
			}
			throw new IllegalArgumentException("Entity not found with UUID: " + entityUuid);
			
		}
		catch (Exception e) {
			log.error("Failed to export entity: {} with UUID: {}", entityType, entityUuid, e);
			throw new APIException("Failed to export entity", e);
		}
	}
	
	@Override
	public File getDefaultShippingDirectory() {
		String openmrsData = System.getProperty("OPENMRS_APPLICATION_DATA_DIRECTORY");
		if (openmrsData == null) {
			openmrsData = System.getProperty("OPENMRS_HOME");
		}
		if (openmrsData == null) {
			openmrsData = ".";
		}
		return new File(openmrsData, "configuration" + File.separator + "reportbuilder");
	}
	
	// ========================================================================
	// Private helper methods
	// ========================================================================
	
	/**
	 * Create the required directory structure for shipping
	 */
	private void createDirectories(File destination) {
		File reportbuilderDir = new File(destination, "reportbuilder");
		reportbuilderDir.mkdirs();
		
		for (String type : DEPENDENCY_ORDER) {
			new File(reportbuilderDir, type).mkdirs();
		}
		
		// Create compiled reports directories
		new File(destination, "reports" + File.separator + "aggregates").mkdirs();
		new File(destination, "reports" + File.separator + "linelist").mkdirs();
	}
	
	/**
	 * Export all dependencies for a report
	 */
	private void exportDependencies(ReportBuilderReport report, File destination, ShippingResult result) {
		// Export category if present
		if (report.getCategory() != null) {
			File file = exportCategory(report.getCategory(), destination);
			result.getDependencies().addCategory(file.getName());
		}
		
		// Additional dependencies would be extracted from configJson
		// and exported here (indicators, sections, themes, etc.)
	}
	
	/**
	 * Export the report source definition
	 */
	private File exportReportSource(ReportBuilderReport report, File destination) {
		try {
			SourceDefinitionExport export = new SourceDefinitionExport();
			export.setUuid(report.getUuid());
			export.setName(report.getName());
			export.setDescription(report.getDescription());
			export.setCode(report.getCode());
			export.setReportType(report.getReportType() != null ? report.getReportType().name() : null);
			export.setConfigJson(report.getConfigJson());
			export.setMetaJson(report.getMetaJson());
			export.setVersion("1.0.0");
			export.setExportedAt(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(new Date()));
			export.setExportedBy(Context.getAuthenticatedUser() != null ? Context.getAuthenticatedUser().getUsername()
			        : "system");
			
			if (report.getCategory() != null) {
				export.setCategoryUuid(report.getCategory().getUuid());
				export.setCategoryName(report.getCategory().getName());
			}
			
			String filename = getFileNameForExport(report.getCode(), report.getUuid());
			File file = new File(destination, "reportbuilder" + File.separator + "reports" + File.separator + filename);
			
			objectMapper.writeValue(file, export);
			log.debug("Exported report source: {}", file.getAbsolutePath());
			return file;
			
		}
		catch (IOException e) {
			throw new APIException("Failed to export report source", e);
		}
	}
	
	/**
	 * Compile the report configuration
	 */
	private ObjectNode compileReportConfiguration(ReportBuilderReport report) {
		try {
			ReportBuilderService service = Context.getService(ReportBuilderService.class);
			// Use existing compilation logic
			// This would call the existing compileReportDefinition method
			ObjectNode config = objectMapper.createObjectNode();
			config.put("uuid", report.getUuid());
			config.put("name", report.getName());
			config.put("code", report.getCode());
			// Additional compilation would happen here
			return config;
		}
		catch (Exception e) {
			log.warn("Failed to fully compile report configuration, using basic config", e);
			return objectMapper.createObjectNode();
		}
	}
	
	/**
	 * Export the compiled report
	 */
	private File exportCompiledReport(ReportBuilderReport report, ObjectNode compiledConfig, File destination) {
		try {
			SerializedReport serialized = new SerializedReport(report, compiledConfig);
			serialized.setCompiledAt(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(new Date()));
			serialized.setCompiledBy(Context.getAuthenticatedUser() != null ? Context.getAuthenticatedUser().getUsername()
			        : "system");
			
			if (report.getCategory() != null) {
				serialized.setCategory(report.getCategory().getName());
			}
			
			String subdir = report.getReportType() == ReportBuilderReport.ReportType.LINE_LIST ? "linelist" : "aggregates";
			String filename = getFileNameForExport(report.getCode(), report.getUuid());
			File file = new File(destination, "reports" + File.separator + subdir + File.separator + filename);
			
			objectMapper.writeValue(file, serialized);
			log.debug("Exported compiled report: {}", file.getAbsolutePath());
			return file;
			
		}
		catch (IOException e) {
			throw new APIException("Failed to export compiled report", e);
		}
	}
	
	/**
	 * Generate version metadata file
	 */
	private File generateVersionMetadata(ReportBuilderReport report, String version, ShippingResult result, File destination)
	        throws IOException {
		VersionMetadata metadata = new VersionMetadata();
		
		// Package info
		metadata.getPackageInfo().setName(report.getCode() != null ? report.getCode() : report.getUuid());
		metadata.getPackageInfo().setVersion(version);
		metadata.getPackageInfo().setDescription("Report distribution package");
		metadata.getPackageInfo().setExportedAt(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(new Date()));
		metadata.getPackageInfo().setExportedBy(
		    Context.getAuthenticatedUser() != null ? Context.getAuthenticatedUser().getUsername() : "system");
		metadata.getPackageInfo().setReportBuilderVersion("1.0.0");
		
		// Contents
		VersionMetadata.ReportInfo reportInfo = new VersionMetadata.ReportInfo(report.getUuid(),
		        report.getCode() != null ? report.getCode() : report.getUuid(), report.getReportType() != null ? report
		                .getReportType().name() : "AGGREGATE", result.getSourceFile(), result.getCompiledFile());
		metadata.getContents().addReport(reportInfo);
		
		// Add dependencies to contents
		for (String dep : result.getDependencies().getCategories()) {
			metadata.getContents().getDependencies().addCategory(dep);
		}
		// Add other dependencies similarly...
		
		File versionFile = new File(destination, "version.json");
		objectMapper.writeValue(versionFile, metadata);
		log.debug("Generated version metadata: {}", versionFile.getAbsolutePath());
		return versionFile;
	}
	
	/**
	 * Generate filename for export - uses code if available, falls back to UUID
	 */
	private String getFileNameForExport(String code, String uuid) {
		if (code != null && !code.trim().isEmpty()) {
			return code + ".json";
		}
		return uuid + ".json";
	}
	
	// ========================================================================
	// Individual entity export methods
	// ========================================================================
	
	private File exportCategory(ReportCategory category, File destination) {
		try {
			SourceDefinitionExport export = new SourceDefinitionExport();
			export.setUuid(category.getUuid());
			export.setName(category.getName());
			export.setDescription(category.getDescription());
			export.setCode(category.getUuid()); // Use UUID as code since ReportCategory has no code field
			export.setVersion("1.0.0");
			export.setExportedAt(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(new Date()));
			
			String filename = getFileNameForExport(null, category.getUuid());
			File file = new File(destination, "reportbuilder" + File.separator + "categories" + File.separator + filename);
			
			objectMapper.writeValue(file, export);
			return file;
			
		}
		catch (IOException e) {
			throw new APIException("Failed to export category", e);
		}
	}
	
	private File exportIndicator(ReportBuilderIndicator indicator, File destination) {
		try {
			SourceDefinitionExport export = new SourceDefinitionExport();
			export.setUuid(indicator.getUuid());
			export.setName(indicator.getName());
			export.setDescription(indicator.getDescription());
			export.setCode(indicator.getCode());
			export.setConfigJson(objectMapper.writeValueAsString(indicator));
			export.setVersion("1.0.0");
			export.setExportedAt(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(new Date()));
			
			String filename = getFileNameForExport(indicator.getCode(), indicator.getUuid());
			File file = new File(destination, "reportbuilder" + File.separator + "indicators" + File.separator + filename);
			
			objectMapper.writeValue(file, export);
			return file;
			
		}
		catch (IOException e) {
			throw new APIException("Failed to export indicator", e);
		}
	}
	
	private File exportSection(ReportBuilderSection section, File destination) {
		try {
			SourceDefinitionExport export = new SourceDefinitionExport();
			export.setUuid(section.getUuid());
			export.setName(section.getName());
			export.setDescription(section.getDescription());
			export.setCode(section.getCode());
			export.setConfigJson(section.getConfigJson());
			export.setVersion("1.0.0");
			export.setExportedAt(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(new Date()));
			
			String filename = getFileNameForExport(section.getCode(), section.getUuid());
			File file = new File(destination, "reportbuilder" + File.separator + "sections" + File.separator + filename);
			
			objectMapper.writeValue(file, export);
			return file;
			
		}
		catch (IOException e) {
			throw new APIException("Failed to export section", e);
		}
	}
	
	private File exportTheme(ReportBuilderDataTheme theme, File destination) {
		try {
			SourceDefinitionExport export = new SourceDefinitionExport();
			export.setUuid(theme.getUuid());
			export.setName(theme.getName());
			export.setDescription(theme.getDescription());
			export.setCode(theme.getCode());
			export.setConfigJson(theme.getConfigJson());
			export.setVersion("1.0.0");
			export.setExportedAt(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(new Date()));
			
			String filename = getFileNameForExport(theme.getCode(), theme.getUuid());
			File file = new File(destination, "reportbuilder" + File.separator + "themes" + File.separator + filename);
			
			objectMapper.writeValue(file, export);
			return file;
			
		}
		catch (IOException e) {
			throw new APIException("Failed to export theme", e);
		}
	}
	
	private File exportAgeCategory(ReportBuilderAgeCategory category, File destination) {
		try {
			SourceDefinitionExport export = new SourceDefinitionExport();
			export.setUuid(category.getUuid());
			export.setName(category.getName());
			export.setDescription(category.getDescription());
			export.setCode(category.getCode());
			export.setVersion("1.0.0");
			export.setExportedAt(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(new Date()));
			
			String filename = getFileNameForExport(category.getCode(), category.getUuid());
			File file = new File(destination, "reportbuilder" + File.separator + "age-categories" + File.separator
			        + filename);
			
			objectMapper.writeValue(file, export);
			return file;
			
		}
		catch (IOException e) {
			throw new APIException("Failed to export age category", e);
		}
	}
	
	private File exportETLSource(ETLSource source, File destination) {
		try {
			SourceDefinitionExport export = new SourceDefinitionExport();
			export.setUuid(source.getUuid());
			export.setName(source.getName());
			export.setDescription(source.getDescription());
			export.setCode(source.getCode());
			// ETLSource doesn't have configJson - skip it
			export.setVersion("1.0.0");
			export.setExportedAt(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(new Date()));
			
			String filename = getFileNameForExport(source.getCode(), source.getUuid());
			File file = new File(destination, "reportbuilder" + File.separator + "etl-sources" + File.separator + filename);
			
			objectMapper.writeValue(file, export);
			return file;
			
		}
		catch (IOException e) {
			throw new APIException("Failed to export ETL source", e);
		}
	}
	
	private File exportETLMonitor(ETLMonitor monitor, File destination) {
		try {
			SourceDefinitionExport export = new SourceDefinitionExport();
			export.setUuid(monitor.getUuid());
			export.setName(monitor.getName());
			export.setDescription(monitor.getDescription());
			export.setCode(monitor.getCode());
			export.setConfigJson(objectMapper.writeValueAsString(monitor));
			export.setVersion("1.0.0");
			export.setExportedAt(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(new Date()));
			
			String filename = getFileNameForExport(monitor.getCode(), monitor.getUuid());
			File file = new File(destination, "reportbuilder" + File.separator + "etl-monitors" + File.separator + filename);
			
			objectMapper.writeValue(file, export);
			return file;
			
		}
		catch (IOException e) {
			throw new APIException("Failed to export ETL monitor", e);
		}
	}
}
