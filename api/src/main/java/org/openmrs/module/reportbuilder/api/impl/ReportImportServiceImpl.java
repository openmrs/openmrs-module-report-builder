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
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.reportbuilder.api.ReportImportService;
import org.openmrs.module.reportbuilder.api.db.ReportBuilderDAO;
import org.openmrs.module.reportbuilder.model.*;
import org.openmrs.module.reportbuilder.web.controller.dto.ImportResult;
import org.openmrs.module.reportbuilder.web.controller.dto.SourceDefinitionExport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * Implementation of the ReportImportService. Handles import of ReportBuilder entities from
 * distribution packages.
 */
@Transactional
public class ReportImportServiceImpl extends BaseOpenmrsService implements ReportImportService {
	
	private static final Logger log = LoggerFactory.getLogger(ReportImportServiceImpl.class);
	
	/**
	 * Import order respecting dependencies - foundation entities first
	 */
	private static final List<String> IMPORT_ORDER = Arrays.asList("categories", "age-categories", "age-groups",
	    "etl-sources", "etl-monitors", "indicators", "sections", "themes", "reports", "library");
	
	private ReportBuilderDAO dao;
	
	private final ObjectMapper objectMapper = new ObjectMapper();
	
	public void setDao(ReportBuilderDAO dao) {
		this.dao = dao;
	}
	
	@Override
	public ImportResult importFromDirectory(File sourceDir) {
		ImportResult result = new ImportResult();
		result.setSummary("Import from directory: " + sourceDir.getAbsolutePath());
		
		try {
			log.info("Starting import from directory: {}", sourceDir.getAbsolutePath());
			
			// Validate directory structure
			File reportbuilderDir = new File(sourceDir, "reportbuilder");
			if (!reportbuilderDir.exists()) {
				result.setSuccess(false);
				result.setSummary("Invalid distribution package: missing reportbuilder directory");
				return result;
			}
			
			// Import in dependency order
			for (String type : IMPORT_ORDER) {
				File typeDir = new File(reportbuilderDir, type);
				if (typeDir.exists() && typeDir.isDirectory()) {
					importType(type, typeDir, result);
				}
			}
			
			// Import compiled reports
			File reportsDir = new File(sourceDir, "reports");
			if (reportsDir.exists()) {
				importCompiledReports(reportsDir, result);
			}
			
			// Update summary
			int successCount = result.getSuccessCount();
			int errorCount = result.getErrorCount();
			result.setSummary(String.format("Import complete: %d succeeded, %d failed", successCount, errorCount));
			result.setSuccess(errorCount == 0);
			
			log.info("Import complete: {} succeeded, {} failed", successCount, errorCount);
			
		}
		catch (Exception e) {
			log.error("Failed to import from directory: {}", sourceDir.getAbsolutePath(), e);
			result.setSuccess(false);
			result.setSummary("Import failed: " + e.getMessage());
		}
		
		return result;
	}
	
	@Override
	public ImportResult importEntity(String entityType, File file) {
		ImportResult result = new ImportResult();
		
		try {
			String filename = file.getName();
			log.info("Importing {} from file: {}", entityType, filename);
			
			switch (entityType.toLowerCase()) {
				case "category":
					importCategory(file);
					result.addSuccess(entityType, filename);
					break;
				case "age-category":
					importAgeCategory(file);
					result.addSuccess(entityType, filename);
					break;
				case "age-group":
					importAgeGroup(file);
					result.addSuccess(entityType, filename);
					break;
				case "etl-source":
					importETLSource(file);
					result.addSuccess(entityType, filename);
					break;
				case "etl-monitor":
					importETLMonitor(file);
					result.addSuccess(entityType, filename);
					break;
				case "indicator":
					importIndicator(file);
					result.addSuccess(entityType, filename);
					break;
				case "section":
					importSection(file);
					result.addSuccess(entityType, filename);
					break;
				case "theme":
					importTheme(file);
					result.addSuccess(entityType, filename);
					break;
				case "report":
					importReport(file);
					result.addSuccess(entityType, filename);
					break;
				case "library":
					importLibraryEntry(file);
					result.addSuccess(entityType, filename);
					break;
				default:
					result.addError(entityType, filename, "Unknown entity type: " + entityType);
			}
			
			result.setSummary("Imported 1 entity");
			
		}
		catch (Exception e) {
			log.error("Failed to import entity from: {}", file.getName(), e);
			result.addError(entityType, file.getName(), e.getMessage());
			result.setSuccess(false);
			result.setSummary("Import failed: " + e.getMessage());
		}
		
		return result;
	}
	
	@Override
	public boolean validatePackage(File sourceDir) {
		try {
			File reportbuilderDir = new File(sourceDir, "reportbuilder");
			if (!reportbuilderDir.exists() || !reportbuilderDir.isDirectory()) {
				log.warn("Invalid package: missing reportbuilder directory");
				return false;
			}
			
			// Check for at least one entity directory
			boolean hasEntities = false;
			for (String type : IMPORT_ORDER) {
				File typeDir = new File(reportbuilderDir, type);
				if (typeDir.exists() && typeDir.isDirectory() && typeDir.list().length > 0) {
					hasEntities = true;
					break;
				}
			}
			
			if (!hasEntities) {
				log.warn("Invalid package: no entities found");
				return false;
			}
			
			// Check for version file
			File versionFile = new File(sourceDir, "version.json");
			if (!versionFile.exists()) {
				log.warn("Warning: missing version.json file");
			}
			
			return true;
			
		}
		catch (Exception e) {
			log.error("Failed to validate package", e);
			return false;
		}
	}
	
	@Override
    public List<String> getImportOrder() {
        return new ArrayList<>(IMPORT_ORDER);
    }
	
	// ========================================================================
	// Private helper methods
	// ========================================================================
	
	/**
	 * Import all entities of a specific type from a directory
	 */
	private void importType(String type, File dir, ImportResult result) {
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));

        if (files == null || files.length == 0) {
            return;
        }

        log.info("Importing {} entities from: {}", type, dir.getAbsolutePath());

        for (File file : files) {
            try {
                switch (type) {
                    case "categories":
                        importCategory(file);
                        break;
                    case "age-categories":
                        importAgeCategory(file);
                        break;
                    case "age-groups":
                        importAgeGroup(file);
                        break;
                    case "etl-sources":
                        importETLSource(file);
                        break;
                    case "etl-monitors":
                        importETLMonitor(file);
                        break;
                    case "indicators":
                        importIndicator(file);
                        break;
                    case "sections":
                        importSection(file);
                        break;
                    case "themes":
                        importTheme(file);
                        break;
                    case "reports":
                        importReport(file);
                        break;
                    case "library":
                        importLibraryEntry(file);
                        break;
                    default:
                        log.warn("Unknown import type: {}", type);
                        continue;
                }
                result.addSuccess(type, file.getName());

            } catch (Exception e) {
                log.error("Failed to import: {}", file.getName(), e);
                result.addError(type, file.getName(), e.getMessage());
            }
        }
    }
	
	/**
	 * Import compiled reports from reports directory
	 */
	private void importCompiledReports(File reportsDir, ImportResult result) {
        File[] subdirs = reportsDir.listFiles(File::isDirectory);

        if (subdirs == null) {
            return;
        }

        for (File subdir : subdirs) {
            if ("aggregates".equals(subdir.getName()) || "linelist".equals(subdir.getName())) {
                File[] files = subdir.listFiles((d, name) -> name.endsWith(".json"));
                if (files != null) {
                    for (File file : files) {
                        try {
                            // Compiled reports are already handled in reports import
                            // This is for reference only
                            log.debug("Found compiled report: {}", file.getName());
                        } catch (Exception e) {
                            log.warn("Could not process compiled report: {}", file.getName(), e);
                        }
                    }
                }
            }
        }
    }
	
	/**
	 * List JSON files in a directory
	 */
	private List<File> listJsonFiles(File dir) {
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        return files != null ? Arrays.asList(files) : Collections.emptyList();
    }
	
	/**
	 * Read entity export from file
	 */
	private SourceDefinitionExport readExportFile(File file) throws IOException {
		String jsonContent = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
		return objectMapper.readValue(jsonContent, SourceDefinitionExport.class);
	}
	
	// ========================================================================
	// Individual entity import methods (UUID-based deduplication pattern)
	// ========================================================================
	
	/**
	 * Import a ReportCategory entity
	 */
	private void importCategory(File file) throws IOException {
		SourceDefinitionExport export = readExportFile(file);
		
		ReportCategory existing = dao.getReportCategoryByUuid(export.getUuid());
		
		if (existing != null) {
			// Update existing
			existing.setName(export.getName());
			existing.setDescription(export.getDescription());
			dao.saveReportCategory(existing);
			log.debug("Updated existing category: {}", export.getName());
		} else {
			// Create new
			ReportCategory category = new ReportCategory();
			category.setUuid(export.getUuid());
			category.setName(export.getName());
			category.setDescription(export.getDescription());
			dao.saveReportCategory(category);
			log.debug("Created new category: {}", export.getName());
		}
	}
	
	/**
	 * Import a ReportLibrary entity
	 */
	private void importLibraryEntry(File file) throws IOException {
		SourceDefinitionExport export = readExportFile(file);
		
		ReportLibrary existing = dao.getReportLibraryByUuid(export.getUuid());
		
		if (existing != null) {
			// Update existing
			existing.setName(export.getName());
			existing.setDescription(export.getDescription());
			dao.saveReportLibrary(existing);
			log.debug("Updated existing library entry: {}", export.getName());
		} else {
			// Create new
			ReportLibrary library = new ReportLibrary();
			library.setUuid(export.getUuid());
			library.setName(export.getName());
			library.setDescription(export.getDescription());
			
			// Set references if available
			if (export.getCategoryUuid() != null) {
				ReportCategory category = dao.getReportCategoryByUuid(export.getCategoryUuid());
				if (category != null) {
					library.setCategory(category);
				}
			}
			
			dao.saveReportLibrary(library);
			log.debug("Created new library entry: {}", export.getName());
		}
	}
	
	/**
	 * Import a ReportBuilderIndicator entity
	 */
	private void importIndicator(File file) throws IOException {
		SourceDefinitionExport export = readExportFile(file);
		
		ReportBuilderIndicator existing = dao.getReportBuilderIndicatorByUuid(export.getUuid());
		
		if (existing != null) {
			// Update existing
			existing.setName(export.getName());
			existing.setDescription(export.getDescription());
			existing.setCode(export.getCode());
			existing.setConfigJson(export.getConfigJson());
			existing.setMetaJson(export.getMetaJson());
			dao.saveReportBuilderIndicator(existing);
			log.debug("Updated existing indicator: {}", export.getName());
		} else {
			// Create new
			ReportBuilderIndicator indicator = new ReportBuilderIndicator();
			indicator.setUuid(export.getUuid());
			indicator.setName(export.getName());
			indicator.setDescription(export.getDescription());
			indicator.setCode(export.getCode());
			indicator.setConfigJson(export.getConfigJson());
			indicator.setMetaJson(export.getMetaJson());
			dao.saveReportBuilderIndicator(indicator);
			log.debug("Created new indicator: {}", export.getName());
		}
	}
	
	/**
	 * Import a ReportBuilderSection entity
	 */
	private void importSection(File file) throws IOException {
		SourceDefinitionExport export = readExportFile(file);
		
		ReportBuilderSection existing = dao.getReportBuilderSectionByUuid(export.getUuid());
		
		if (existing != null) {
			// Update existing
			existing.setName(export.getName());
			existing.setDescription(export.getDescription());
			existing.setCode(export.getCode());
			existing.setConfigJson(export.getConfigJson());
			dao.saveReportBuilderSection(existing);
			log.debug("Updated existing section: {}", export.getName());
		} else {
			// Create new
			ReportBuilderSection section = new ReportBuilderSection();
			section.setUuid(export.getUuid());
			section.setName(export.getName());
			section.setDescription(export.getDescription());
			section.setCode(export.getCode());
			section.setConfigJson(export.getConfigJson());
			dao.saveReportBuilderSection(section);
			log.debug("Created new section: {}", export.getName());
		}
	}
	
	/**
	 * Import a ReportBuilderDataTheme entity
	 */
	private void importTheme(File file) throws IOException {
		SourceDefinitionExport export = readExportFile(file);
		
		ReportBuilderDataTheme existing = dao.getReportBuilderDataThemeByUuid(export.getUuid());
		
		if (existing != null) {
			// Update existing
			existing.setName(export.getName());
			existing.setDescription(export.getDescription());
			existing.setCode(export.getCode());
			existing.setConfigJson(export.getConfigJson());
			dao.saveReportBuilderDataTheme(existing);
			log.debug("Updated existing theme: {}", export.getName());
		} else {
			// Create new
			ReportBuilderDataTheme theme = new ReportBuilderDataTheme();
			theme.setUuid(export.getUuid());
			theme.setName(export.getName());
			theme.setDescription(export.getDescription());
			theme.setCode(export.getCode());
			theme.setConfigJson(export.getConfigJson());
			dao.saveReportBuilderDataTheme(theme);
			log.debug("Created new theme: {}", export.getName());
		}
	}
	
	/**
	 * Import a ReportBuilderAgeCategory entity
	 */
	private void importAgeCategory(File file) throws IOException {
		SourceDefinitionExport export = readExportFile(file);
		
		ReportBuilderAgeCategory existing = dao.getAgeCategoryByUuid(export.getUuid());
		
		if (existing != null) {
			// Update existing
			existing.setName(export.getName());
			existing.setDescription(export.getDescription());
			existing.setCode(export.getCode());
			dao.saveAgeCategory(existing);
			log.debug("Updated existing age category: {}", export.getName());
		} else {
			// Create new
			ReportBuilderAgeCategory category = new ReportBuilderAgeCategory();
			category.setUuid(export.getUuid());
			category.setName(export.getName());
			category.setDescription(export.getDescription());
			category.setCode(export.getCode());
			dao.saveAgeCategory(category);
			log.debug("Created new age category: {}", export.getName());
		}
	}
	
	/**
	 * Import a ReportBuilderAgeGroup entity Note: AgeGroups don't have UUID, use category + label
	 * for deduplication
	 */
	/**
	 * Import a ReportBuilderAgeGroup entity Note: AgeGroups don't have UUID, use category + label
	 * for deduplication
	 */
	private void importAgeGroup(File file) throws IOException {
		// Parse the export file to get age group data
		SourceDefinitionExport export = readExportFile(file);
		
		// Look up the category by UUID
		ReportBuilderAgeCategory category = null;
		if (export.getCategoryUuid() != null) {
			category = dao.getAgeCategoryByUuid(export.getCategoryUuid());
		}
		if (category == null) {
			throw new IOException("Age group import requires valid category UUID: " + export.getCategoryUuid());
		}
		
		// Get all age groups for this category
		List<ReportBuilderAgeGroup> existingGroups = dao.getAgeGroupsByCategoryUuid(category.getUuid(), false);
		
		// Try to find matching age group by label
		ReportBuilderAgeGroup existing = null;
		for (ReportBuilderAgeGroup group : existingGroups) {
			if (group.getLabel() != null && group.getLabel().equals(export.getName())) {
				existing = group;
				break;
			}
		}
		
		if (existing != null) {
			// Update existing age group
			existing.setLabel(export.getName());
			existing.setCode(export.getCode());
			// Set min/max age days from config if available
			if (export.getConfigJson() != null) {
				// Parse config JSON for age range values
				// This would be populated from the export
				existing.setMinAgeDays(0); // Placeholder
				existing.setMaxAgeDays(0); // Placeholder
			}
			dao.saveAgeGroup(existing);
			log.debug("Updated existing age group: {}", export.getName());
		} else {
			// Create new age group
			ReportBuilderAgeGroup ageGroup = new ReportBuilderAgeGroup();
			ageGroup.setLabel(export.getName());
			ageGroup.setCode(export.getCode());
			ageGroup.setAgeCategory(category);
			ageGroup.setMinAgeDays(0); // Would be parsed from config
			ageGroup.setMaxAgeDays(0); // Would be parsed from config
			ageGroup.setActive(true);
			dao.saveAgeGroup(ageGroup);
			log.debug("Created new age group: {}", export.getName());
		}
	}
	
	/**
	 * Import an ETLSource entity
	 */
	private void importETLSource(File file) throws IOException {
		SourceDefinitionExport export = readExportFile(file);
		
		ETLSource existing = dao.getETLSourceByUuid(export.getUuid());
		
		if (existing != null) {
			// Update existing
			existing.setName(export.getName());
			existing.setDescription(export.getDescription());
			existing.setCode(export.getCode());
			dao.saveETLSource(existing);
			log.debug("Updated existing ETL source: {}", export.getName());
		} else {
			// Create new
			ETLSource source = new ETLSource();
			source.setUuid(export.getUuid());
			source.setName(export.getName());
			source.setDescription(export.getDescription());
			source.setCode(export.getCode());
			dao.saveETLSource(source);
			log.debug("Created new ETL source: {}", export.getName());
		}
	}
	
	/**
	 * Import an ETLMonitor entity
	 */
	private void importETLMonitor(File file) throws IOException {
		SourceDefinitionExport export = readExportFile(file);
		
		ETLMonitor existing = dao.getETLMonitorByUuid(export.getUuid());
		
		if (existing != null) {
			// Update existing
			existing.setName(export.getName());
			existing.setDescription(export.getDescription());
			existing.setCode(export.getCode());
			existing.setConfigJson(export.getConfigJson());
			dao.saveETLMonitor(existing);
			log.debug("Updated existing ETL monitor: {}", export.getName());
		} else {
			// Create new
			ETLMonitor monitor = new ETLMonitor();
			monitor.setUuid(export.getUuid());
			monitor.setName(export.getName());
			monitor.setDescription(export.getDescription());
			monitor.setCode(export.getCode());
			monitor.setConfigJson(export.getConfigJson());
			dao.saveETLMonitor(monitor);
			log.debug("Created new ETL monitor: {}", export.getName());
		}
	}
	
	/**
	 * Import a ReportBuilderReport entity
	 */
	private void importReport(File file) throws IOException {
		SourceDefinitionExport export = readExportFile(file);
		
		ReportBuilderReport existing = dao.getReportBuilderReportByUuid(export.getUuid());
		
		if (existing != null) {
			// Update existing
			existing.setName(export.getName());
			existing.setDescription(export.getDescription());
			existing.setCode(export.getCode());
			existing.setConfigJson(export.getConfigJson());
			existing.setMetaJson(export.getMetaJson());
			
			// Handle category reference
			if (export.getCategoryUuid() != null) {
				ReportCategory category = dao.getReportCategoryByUuid(export.getCategoryUuid());
				if (category != null) {
					existing.setCategory(category);
				}
			}
			
			dao.saveReportBuilderReport(existing);
			log.debug("Updated existing report: {}", export.getName());
		} else {
			// Create new
			ReportBuilderReport report = new ReportBuilderReport();
			report.setUuid(export.getUuid());
			report.setName(export.getName());
			report.setDescription(export.getDescription());
			report.setCode(export.getCode());
			report.setConfigJson(export.getConfigJson());
			report.setMetaJson(export.getMetaJson());
			
			// Handle report type
			if (export.getReportType() != null) {
				report.setReportType(export.getReportType());
			}
			
			// Handle category reference
			if (export.getCategoryUuid() != null) {
				ReportCategory category = dao.getReportCategoryByUuid(export.getCategoryUuid());
				if (category != null) {
					report.setCategory(category);
				}
			}
			
			dao.saveReportBuilderReport(report);
			log.debug("Created new report: {}", export.getName());
		}
	}
}
