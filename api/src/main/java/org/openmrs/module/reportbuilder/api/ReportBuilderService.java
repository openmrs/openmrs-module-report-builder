/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.reportbuilder.api;

import org.openmrs.api.OpenmrsService;
import org.openmrs.module.reportbuilder.dto.SqlPreviewResult;
import org.openmrs.module.reportbuilder.legacyconfig.importer.ReportImportResult;
import org.openmrs.module.reportbuilder.model.*;
import org.openmrs.module.reportbuilder.validation.ReportValidationResult;
import org.openmrs.module.reporting.report.ReportData;
import org.openmrs.module.reporting.report.ReportDesign;
import org.openmrs.module.reporting.report.definition.ReportDefinition;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * The main service of this module, which is exposed for other modules. See
 * moduleApplicationContext.xml on how it is wired up.
 */
@Transactional
public interface ReportBuilderService extends OpenmrsService {
	
	/** Returns HTML as a string for preview/printing */
	@Transactional(readOnly = true)
	String renderHtmlFromJsonTemplate(ReportDesign reportDesign);
	
	/** Returns payload JSON as a string (no JsonNode leaks) */
	@Transactional(readOnly = true)
	String createPayloadJsonFromTemplate(ReportData reportData, ReportDesign reportDesign, String renderType,
	        Map<String, Object> flatValues, String remapJsonOptional);
	
	@Transactional(readOnly = true)
	String buildPayloadJson(ReportData reportData, ReportDesign reportDesign, String renderType);
	
	@Transactional(readOnly = true)
	String buildFinalPayloadJson(ReportData reportData, ReportDesign reportDesign, String renderType, Date endDate);
	
	@Transactional(readOnly = true)
	String buildPreviewHtml(ReportData reportData, ReportDesign reportDesign);
	
	@Transactional(readOnly = true)
	String buildRenderedOutput(ReportData reportData, ReportDesign reportDesign, String remapJsonOptional);
	
	// =========================
	// Indicator
	// =========================
	@Transactional
	ReportBuilderIndicator saveReportBuilderIndicator(ReportBuilderIndicator indicator);
	
	@Transactional(readOnly = true)
	ReportBuilderIndicator getReportBuilderIndicatorById(Integer id);
	
	@Transactional(readOnly = true)
	ReportBuilderIndicator getReportBuilderIndicatorByUuid(String uuid);
	
	@Transactional(readOnly = true)
	ReportBuilderIndicator getReportBuilderIndicatorByCode(String code);
	
	@Transactional(readOnly = true)
	List<ReportBuilderIndicator> searchReportBuilderIndicators(String q, ReportBuilderIndicator.Kind kind,
	        boolean includeRetired, Integer startIndex, Integer limit);
	
	@Transactional(readOnly = true)
	List<ReportBuilderIndicator> getAllReportBuilderIndicator(Integer startIndex, Integer limit);
	
	@Transactional(readOnly = true)
	List<ReportBuilderIndicator> getReportBuilderIndicators(ReportBuilderIndicator.Kind kind, boolean includeRetired,
	        Integer startIndex, Integer limit);
	
	@Transactional(readOnly = true)
	long getReportBuilderIndicatorsCount(String q, ReportBuilderIndicator.Kind kind, boolean includeRetired);
	
	@Transactional
	void retireReportBuilderIndicator(ReportBuilderIndicator indicator, String reason);
	
	@Transactional
	void unretireReportBuilderIndicator(ReportBuilderIndicator indicator);
	
	@Transactional
	void purgeReportBuilderIndicator(ReportBuilderIndicator indicator);
	
	// =========================
	// Section
	// =========================
	@Transactional
	ReportBuilderSection saveReportBuilderSection(ReportBuilderSection section);
	
	@Transactional(readOnly = true)
	ReportBuilderSection getReportBuilderSectionById(Integer id);
	
	@Transactional(readOnly = true)
	ReportBuilderSection getReportBuilderSectionByUuid(String uuid);
	
	@Transactional(readOnly = true)
	ReportBuilderSection getReportBuilderSectionByCode(String code);
	
	@Transactional(readOnly = true)
	List<ReportBuilderSection> getReportBuilderSections(String q, boolean includeRetired, Integer startIndex, Integer limit);
	
	@Transactional(readOnly = true)
	long getReportBuilderSectionsCount(String q, boolean includeRetired);
	
	@Transactional
	void retireReportBuilderSection(ReportBuilderSection section, String reason);
	
	@Transactional
	void unretireReportBuilderSection(ReportBuilderSection section);
	
	@Transactional
	void purgeReportBuilderSection(ReportBuilderSection section);
	
	// =========================
	// DataTheme
	// =========================
	@Transactional
	ReportBuilderDataTheme saveReportBuilderDataTheme(ReportBuilderDataTheme theme);
	
	@Transactional(readOnly = true)
	ReportBuilderDataTheme getReportBuilderDataThemeById(Integer id);
	
	@Transactional(readOnly = true)
	ReportBuilderDataTheme getReportBuilderDataThemeByUuid(String uuid);
	
	@Transactional(readOnly = true)
	ReportBuilderDataTheme getReportBuilderDataThemeByCode(String code);
	
	@Transactional(readOnly = true)
	List<ReportBuilderDataTheme> getReportBuilderDataThemes(String q, boolean includeRetired, Integer startIndex,
	        Integer limit);
	
	@Transactional(readOnly = true)
	long getReportBuilderDataThemesCount(String q, boolean includeRetired);
	
	@Transactional
	void retireReportBuilderDataTheme(ReportBuilderDataTheme theme, String reason);
	
	@Transactional
	void unretireReportBuilderDataTheme(ReportBuilderDataTheme theme);
	
	@Transactional
	void purgeReportBuilderDataTheme(ReportBuilderDataTheme theme);
	
	@Transactional(readOnly = true)
	List<String> getETLTables();
	
	@Transactional(readOnly = true)
	List<Map> getETLTableColumns(String tableName);
	
	// Categories
	@Transactional
	ReportBuilderAgeCategory saveAgeCategory(ReportBuilderAgeCategory category);
	
	@Transactional(readOnly = true)
	ReportBuilderAgeCategory getAgeCategoryByUuid(String uuid);
	
	@Transactional(readOnly = true)
	ReportBuilderAgeCategory getAgeCategoryByCode(String code);
	
	@Transactional(readOnly = true)
	List<ReportBuilderAgeCategory> getAgeCategories(String q, boolean includeRetired, Boolean activeOnly,
	        Integer startIndex, Integer limit);
	
	@Transactional(readOnly = true)
	long getAgeCategoriesCount(String q, boolean includeRetired, Boolean activeOnly);
	
	@Transactional
	void retireAgeCategory(ReportBuilderAgeCategory category, String reason);
	
	@Transactional
	void unretireAgeCategory(ReportBuilderAgeCategory category);
	
	@Transactional
	void purgeAgeCategory(ReportBuilderAgeCategory category);
	
	// Groups
	@Transactional
	ReportBuilderAgeGroup saveAgeGroup(ReportBuilderAgeGroup group);
	
	@Transactional(readOnly = true)
	ReportBuilderAgeGroup getAgeGroupById(Integer id);
	
	@Transactional(readOnly = true)
	List<ReportBuilderAgeGroup> getAgeGroupsByCategoryUuid(String categoryUuid, Boolean activeOnly);
	
	@Transactional(readOnly = true)
	List<ReportBuilderAgeGroup> getAgeGroupsByCategoryCode(String categoryCode, Boolean activeOnly);
	
	@Transactional
	void purgeAgeGroup(ReportBuilderAgeGroup group);
	
	@Transactional(readOnly = true)
	List<ReportBuilderAgeGroup> getAgeGroups(String q, ReportBuilderAgeCategory category, Boolean activeOnly,
	        Integer startIndex, Integer limit);
	
	@Transactional(readOnly = true)
	SqlPreviewResult previewSql(String sql, Map<String, Object> params, Integer maxRows);
	
	@Transactional
	ReportBuilderReport saveReportBuilderReport(ReportBuilderReport report);
	
	@Transactional(readOnly = true)
	ReportBuilderReport getReportBuilderReportByUuid(String uuid);
	
	@Transactional(readOnly = true)
	List<ReportBuilderReport> getReportBuilderReports(String q, boolean includeRetired, Integer startIndex, Integer limit);
	
	@Transactional
	void retireReportBuilderReport(ReportBuilderReport report, String reason);
	
	@Transactional
	void purgeReportBuilderReport(ReportBuilderReport report);
	
	@Transactional
	CompiledReportArtifacts compileReport(String reportBuilderReportUuid);
	
	@Transactional
	ReportCategory saveReportCategory(ReportCategory category);
	
	@Transactional(readOnly = true)
	ReportCategory getReportCategoryById(Integer id);
	
	@Transactional(readOnly = true)
	ReportCategory getReportCategoryByUuid(String uuid);
	
	@Transactional(readOnly = true)
	List<ReportCategory> getReportCategories(String q, boolean includeRetired, Integer startIndex, Integer limit);
	
	@Transactional(readOnly = true)
	long getReportCategoriesCount(String q, boolean includeRetired);
	
	@Transactional
	void retireReportCategory(ReportCategory category, String reason);
	
	@Transactional
	void unretireReportCategory(ReportCategory category);
	
	@Transactional
	void purgeReportCategory(ReportCategory category);
	
	@Transactional
	ReportLibrary saveReportLibrary(ReportLibrary reportLibrary);
	
	@Transactional(readOnly = true)
	ReportLibrary getReportLibraryById(Integer id);
	
	@Transactional(readOnly = true)
	ReportLibrary getReportLibraryByUuid(String uuid);
	
	@Transactional(readOnly = true)
	List<ReportLibrary> getReportLibraries(String q, boolean includeRetired, Integer startIndex, Integer limit);
	
	@Transactional(readOnly = true)
	long getReportLibrariesCount(String q, boolean includeRetired);
	
	@Transactional
	void retireReportLibrary(ReportLibrary reportLibrary, String reason);
	
	@Transactional
	void unretireReportLibrary(ReportLibrary reportLibrary);
	
	@Transactional
	void purgeReportLibrary(ReportLibrary reportLibrary);
	
	/**
	 * Add a generic report to the report library
	 */
	@Transactional
	void addGenericReportToLibrary(String reportDefinitionUuid, String name, String description, String code,
	        ReportCategory category, ReportBuilderReport.ReportType reportType);
	
	/**
	 * Clean up broken report library entries where ReportDefinition doesn't exist
	 * 
	 * @return Number of entries cleaned up
	 */
	@Transactional
	int cleanupBrokenReportReferences();
	
	@Transactional
	ETLSource saveETLSource(ETLSource etlSource);
	
	@Transactional(readOnly = true)
	ETLSource getETLSourceByUuid(String uuid);
	
	@Transactional(readOnly = true)
	ETLSource getETLSourceById(Integer id);
	
	@Transactional(readOnly = true)
	List<ETLSource> getAllETLSources(boolean includeRetired);
	
	@Transactional
	void retireETLSource(ETLSource etlSource, String retireReason);
	
	@Transactional(readOnly = true)
	List<String> getAllowedTablePrefixes();
	
	class CompiledReportArtifacts {
		
		private ReportBuilderReport reportBuilderReport;
		
		private ReportDefinition reportDefinition;
		
		private File reportDesignFile;
		
		private String compiledJson;
		
		public ReportBuilderReport getReportBuilderReport() {
			return reportBuilderReport;
		}
		
		public void setReportBuilderReport(ReportBuilderReport reportBuilderReport) {
			this.reportBuilderReport = reportBuilderReport;
		}
		
		public ReportDefinition getReportDefinition() {
			return reportDefinition;
		}
		
		public void setReportDefinition(ReportDefinition reportDefinition) {
			this.reportDefinition = reportDefinition;
		}
		
		public File getReportDesignFile() {
			return reportDesignFile;
		}
		
		public void setReportDesignFile(File reportDesignFile) {
			this.reportDesignFile = reportDesignFile;
		}
		
		public String getCompiledJson() {
			return compiledJson;
		}
		
		public void setCompiledJson(String compiledJson) {
			this.compiledJson = compiledJson;
		}
	}
	
	public ReportImportResult importLegacyReportPackage(File reportDir) throws Exception;
	
	public List<ReportImportResult> importAllLegacyReportPackages(File legacyReportsRootDir) throws Exception;
	
	public ReportImportResult validateLegacyReportPackage(File reportDir) throws Exception;
	
	ReportImportResult importRuntimeLegacyReportPackage(String reportKey) throws Exception;
	
	ReportImportResult validateRuntimeLegacyReportPackage(String reportKey) throws Exception;
	
	List<ReportImportResult> importAllRuntimeLegacyReportPackages() throws Exception;
	
	void ensureImportAllLegacyReportsTaskExists();
	
	// =========================
	// Legacy Report Import (from LegacyReportImportService)
	// =========================
	
	/**
	 * Import a single report from a JSON file
	 * 
	 * @param jsonFile The JSON configuration file
	 * @return OpenMRS ReportDefinition
	 */
	org.openmrs.module.reporting.report.definition.ReportDefinition importReportFromFile(File jsonFile);
	
	/**
	 * Import a report from JSON string
	 * 
	 * @param jsonContent The JSON configuration
	 * @return OpenMRS ReportDefinition
	 */
	org.openmrs.module.reporting.report.definition.ReportDefinition importReportFromJson(String jsonContent);
	
	/**
	 * Import all reports from a directory
	 * 
	 * @param reportsDirectory Directory containing JSON report files
	 * @return List of imported ReportDefinitions
	 */
	List<org.openmrs.module.reporting.report.definition.ReportDefinition> importReportsFromDirectory(File reportsDirectory);
	
	/**
	 * Validate that a JSON report file matches its Java contract
	 * 
	 * @param jsonFile The JSON configuration file
	 * @param javaClass The corresponding Java class
	 * @return validation result with any discrepancies
	 */
	org.openmrs.module.reportbuilder.legacyconfig.LegacyReportImporter.ValidationResult validateContract(File jsonFile,
	        Class<?> javaClass);
	
	/**
	 * Import all UgandaEMRReports legacy reports and convert them to ReportBuilder format
	 * 
	 * @param legacyReportsPath Path to UgandaEMRReports legacy directory
	 * @return List of imported ReportDefinitions
	 */
	List<org.openmrs.module.reporting.report.definition.ReportDefinition> importUgandaEMRLegacyReports(
	        String legacyReportsPath);
	
	/**
	 * Ensure that all legacy reports are imported on module startup
	 */
	void ensureLegacyReportsImported();
	
	// =========================
	// Generic Report Import (from GenericReportImportService)
	// =========================
	
	/**
	 * Import all generic reports from runtime directory
	 * 
	 * @return List of import results
	 */
	List<org.openmrs.module.reportbuilder.legacyconfig.generic.ReportImportResult> importAllGenericReports();
	
	/**
	 * Import a single generic report from file
	 * 
	 * @param jsonFile The JSON file to import
	 * @return Import result
	 */
	org.openmrs.module.reportbuilder.legacyconfig.generic.ReportImportResult importGenericReportFromFile(File jsonFile);
	
	/**
	 * Check if generic reports have already been imported
	 * 
	 * @return true if reports are already imported
	 */
	boolean areGenericReportsAlreadyImported();
	
	/**
	 * Ensure generic reports import task exists
	 */
	void ensureImportAllGenericReportsTaskExists();
	
	// =========================================================
	// Legacy Reports
	// =========================================================
	
	/**
	 * Get all legacy reports.
	 * 
	 * @return list of all legacy reports
	 */
	List<LegacyReportConfig> getAllLegacyReports();
	
	/**
	 * Get a legacy report by UUID.
	 * 
	 * @param uuid the UUID of the report
	 * @return the legacy report, or null if not found
	 */
	LegacyReportConfig getLegacyReportByUuid(String uuid);
	
	/**
	 * Get a legacy report by name.
	 * 
	 * @param name the name of the report
	 * @return the legacy report, or null if not found
	 */
	LegacyReportConfig getLegacyReportByName(String name);
	
	/**
	 * Create a new legacy report.
	 * 
	 * @param config the report configuration to create
	 * @return the created report configuration
	 */
	LegacyReportConfig createLegacyReport(LegacyReportConfig config);
	
	/**
	 * Update an existing legacy report.
	 * 
	 * @param uuid the UUID of the report to update
	 * @param config the updated report configuration
	 * @return the updated report configuration
	 */
	LegacyReportConfig updateLegacyReport(String uuid, LegacyReportConfig config);
	
	/**
	 * Delete a legacy report by UUID (soft delete).
	 * 
	 * @param uuid the UUID of the report to delete
	 */
	void deleteLegacyReport(String uuid);
	
	/**
	 * Validate a legacy report configuration.
	 * 
	 * @param config the report configuration to validate
	 * @return validation result with errors and warnings
	 */
	ReportValidationResult validateLegacyReport(LegacyReportConfig config);
	
	/**
	 * Get legacy reports by category.
	 * 
	 * @param category the category to filter by
	 * @return list of legacy reports in the category
	 */
	List<LegacyReportConfig> getLegacyReportsByCategory(String category);
	
	/**
	 * Get legacy reports by status.
	 * 
	 * @param status the status to filter by
	 * @return list of legacy reports with the status
	 */
	List<LegacyReportConfig> getLegacyReportsByStatus(String status);
	
	/**
	 * Search legacy reports by name or description.
	 * 
	 * @param query the search query
	 * @return list of matching legacy reports
	 */
	List<LegacyReportConfig> searchLegacyReports(String query);
	
	/**
	 * Get count of legacy reports.
	 * 
	 * @return the count of legacy reports
	 */
	int getLegacyReportCount();
	
	// =========================================================
	// ETLMonitor
	// =========================================================
	
	/**
	 * Save or update an ETL Monitor
	 * 
	 * @param monitor the monitor to save
	 * @return the saved monitor
	 */
	@Transactional
	ETLMonitor saveETLMonitor(ETLMonitor monitor);
	
	/**
	 * Get an ETL Monitor by ID
	 * 
	 * @param id the monitor ID
	 * @return the monitor, or null if not found
	 */
	@Transactional(readOnly = true)
	ETLMonitor getETLMonitorById(Integer id);
	
	/**
	 * Get an ETL Monitor by UUID
	 * 
	 * @param uuid the monitor UUID
	 * @return the monitor, or null if not found
	 */
	@Transactional(readOnly = true)
	ETLMonitor getETLMonitorByUuid(String uuid);
	
	/**
	 * Get an ETL Monitor by code
	 * 
	 * @param code the monitor code
	 * @return the monitor, or null if not found
	 */
	@Transactional(readOnly = true)
	ETLMonitor getETLMonitorByCode(String code);
	
	/**
	 * Get ETL Monitors with optional search and pagination
	 * 
	 * @param q the search query (searches name, code, description, category)
	 * @param includeRetired whether to include retired monitors
	 * @param startIndex the start index for pagination
	 * @param limit the maximum number of results
	 * @return list of monitors
	 */
	@Transactional(readOnly = true)
	List<ETLMonitor> getETLMonitors(String q, boolean includeRetired, Integer startIndex, Integer limit);
	
	/**
	 * Get all active ETL Monitors
	 * 
	 * @return list of active monitors
	 */
	@Transactional(readOnly = true)
	List<ETLMonitor> getActiveETLMonitors();
	
	/**
	 * Get ETL Monitors by category
	 * 
	 * @param category the category to filter by
	 * @param includeRetired whether to include retired monitors
	 * @return list of monitors in the category
	 */
	@Transactional(readOnly = true)
	List<ETLMonitor> getETLMonitorsByCategory(String category, boolean includeRetired);
	
	/**
	 * Get count of ETL Monitors with optional search
	 * 
	 * @param q the search query
	 * @param includeRetired whether to include retired monitors
	 * @return the count of monitors
	 */
	@Transactional(readOnly = true)
	long getETLMonitorsCount(String q, boolean includeRetired);
	
	/**
	 * Retire an ETL Monitor
	 * 
	 * @param monitor the monitor to retire
	 * @param reason the reason for retiring
	 */
	@Transactional
	void retireETLMonitor(ETLMonitor monitor, String reason);
	
	/**
	 * Unretire an ETL Monitor
	 * 
	 * @param monitor the monitor to unretire
	 */
	@Transactional
	void unretireETLMonitor(ETLMonitor monitor);
	
	/**
	 * Permanently delete an ETL Monitor
	 * 
	 * @param monitor the monitor to purge
	 */
	@Transactional
	void purgeETLMonitor(ETLMonitor monitor);
	
	// ========== Report Shipping Methods ==========
	
	/**
	 * Ship a single report to a distribution package. Exports the report definition and all
	 * dependencies to the destination directory.
	 * 
	 * @param reportUuid UUID of the report to ship
	 * @param version Version string for the distribution package
	 * @param destination Destination directory for the exported files
	 * @return ShippingResult containing details of the shipped report and dependencies
	 */
	org.openmrs.module.reportbuilder.web.controller.dto.ShippingResult shipReport(String reportUuid, String version,
	        File destination);
	
	/**
	 * Ship multiple reports in a single distribution package.
	 * 
	 * @param reportUuids List of report UUIDs to ship
	 * @param version Version string for the distribution package
	 * @param destination Destination directory for the exported files
	 * @return ShippingResult containing aggregated details
	 */
	org.openmrs.module.reportbuilder.web.controller.dto.ShippingResult shipBatch(List<String> reportUuids, String version,
	        File destination);
	
	/**
	 * Export a single entity of a specific type to a file.
	 * 
	 * @param entityType Type of entity to export (e.g., "category", "indicator", "theme")
	 * @param entityUuid UUID of the entity to export
	 * @param destination Destination directory for the exported file
	 * @return File containing the exported entity
	 */
	File exportEntity(String entityType, String entityUuid, File destination);
	
	/**
	 * Get the default configuration directory for shipping.
	 * 
	 * @return Default shipping destination directory
	 */
	File getDefaultShippingDirectory();
	
	/**
	 * Get the default configuration directory for import. Returns the same directory as the default
	 * shipping directory, allowing seamless import from packages exported to the default location.
	 * 
	 * @return Default import source directory
	 */
	File getDefaultImportDirectory();
	
	/**
	 * Export all reports with their dependencies.
	 * 
	 * @param version Version string for the distribution package
	 * @param destination Destination directory for the exported files
	 * @return ShippingResult containing aggregated details
	 */
	org.openmrs.module.reportbuilder.web.controller.dto.ShippingResult shipAllReports(String version, File destination);
	
	/**
	 * Export all entities of specific types with their dependencies.
	 * 
	 * @param entityTypes List of entity types to export
	 * @param version Version string for the distribution package
	 * @param destination Destination directory for the exported files
	 * @return ShippingResult containing aggregated details
	 */
	org.openmrs.module.reportbuilder.web.controller.dto.ShippingResult shipAllEntities(java.util.List<String> entityTypes,
	        String version, File destination);
	
	// ========== Report Import Methods ==========
	
	/**
	 * Import all entities from a distribution directory.
	 * 
	 * @param sourceDir Source directory containing the distribution package
	 * @return ImportResult containing summary, successes, and errors
	 */
	org.openmrs.module.reportbuilder.web.controller.dto.ImportResult importFromDirectory(File sourceDir);
	
	/**
	 * Import a single entity from a file.
	 * 
	 * @param entityType Type of entity to import (e.g., "category", "indicator", "theme")
	 * @param file File containing the entity definition
	 * @return ImportResult for this single import operation
	 */
	org.openmrs.module.reportbuilder.web.controller.dto.ImportResult importEntity(String entityType, File file);
	
	/**
	 * Validate a distribution package without importing.
	 * 
	 * @param sourceDir Source directory to validate
	 * @return true if package is valid, false otherwise
	 */
	boolean validatePackage(File sourceDir);
	
	/**
	 * Get the import order for entity types based on dependencies.
	 * 
	 * @return List of entity types in import order
	 */
	java.util.List<String> getImportOrder();
	
	// ========== Report Package Methods ==========
	
	/**
	 * Get all available packages in the shipping directory.
	 * 
	 * @param search Optional search term to filter by name or version
	 * @param status Optional status filter ("valid" or "invalid")
	 * @param startIndex Starting index for pagination
	 * @param limit Maximum number of results to return
	 * @return List of package information for available packages
	 */
	java.util.List<org.openmrs.module.reportbuilder.web.controller.dto.PackageInfo> getAvailablePackages(String search,
	        String status, Integer startIndex, Integer limit);
	
	/**
	 * Get the total count of available packages matching the given filters.
	 * 
	 * @param search Optional search term to filter by name or version
	 * @param status Optional status filter ("valid" or "invalid")
	 * @return Total count of matching packages
	 */
	long getAvailablePackagesCount(String search, String status);
	
}
