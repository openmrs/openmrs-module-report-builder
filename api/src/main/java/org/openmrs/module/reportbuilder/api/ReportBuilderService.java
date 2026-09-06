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
import org.openmrs.annotation.Authorized;
import static org.openmrs.module.reportbuilder.security.ReportBuilderPrivileges.*;
import org.openmrs.module.reportbuilder.dto.SqlPreviewResult;
import org.openmrs.module.reportbuilder.legacyconfig.importer.ReportImportResult;
import org.openmrs.module.reportbuilder.model.*;
import org.openmrs.module.reportbuilder.web.controller.dto.SerializedReport;
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
	@Authorized("Task: reportbuilder.report.run")
	String renderHtmlFromJsonTemplate(ReportDesign reportDesign);
	
	/** Returns payload JSON as a string (no JsonNode leaks) */
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.report.run")
	String createPayloadJsonFromTemplate(ReportData reportData, ReportDesign reportDesign, String renderType,
	        Map<String, Object> flatValues, String remapJsonOptional);
	
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.report.run")
	String buildPayloadJson(ReportData reportData, ReportDesign reportDesign, String renderType);
	
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.report.run")
	String buildFinalPayloadJson(ReportData reportData, ReportDesign reportDesign, String renderType, Date endDate);
	
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.sql.execute")
	String buildPreviewHtml(ReportData reportData, ReportDesign reportDesign);
	
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.report.run")
	String buildRenderedOutput(ReportData reportData, ReportDesign reportDesign, String remapJsonOptional);
	
	// =========================
	// Indicator
	// =========================
	@Transactional
	@Authorized({ "Task: reportbuilder.indicator.add", "Task: reportbuilder.indicator.edit" })
	ReportBuilderIndicator saveReportBuilderIndicator(ReportBuilderIndicator indicator);
	
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.indicator.view")
	ReportBuilderIndicator getReportBuilderIndicatorById(Integer id);
	
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.indicator.view")
	ReportBuilderIndicator getReportBuilderIndicatorByUuid(String uuid);
	
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.indicator.view")
	ReportBuilderIndicator getReportBuilderIndicatorByCode(String code);
	
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.indicator.view")
	List<ReportBuilderIndicator> searchReportBuilderIndicators(String q, ReportBuilderIndicator.Kind kind,
	        boolean includeRetired, Integer startIndex, Integer limit);
	
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.indicator.view")
	List<ReportBuilderIndicator> getAllReportBuilderIndicator(Integer startIndex, Integer limit);
	
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.indicator.view")
	List<ReportBuilderIndicator> getReportBuilderIndicators(ReportBuilderIndicator.Kind kind, boolean includeRetired,
	        Integer startIndex, Integer limit);
	
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.indicator.view")
	long getReportBuilderIndicatorsCount(String q, ReportBuilderIndicator.Kind kind, boolean includeRetired);
	
	@Transactional
	@Authorized("Task: reportbuilder.indicator.edit")
	void retireReportBuilderIndicator(ReportBuilderIndicator indicator, String reason);
	
	@Transactional
	@Authorized("Task: reportbuilder.indicator.edit")
	void unretireReportBuilderIndicator(ReportBuilderIndicator indicator);
	
	@Transactional
	@Authorized("Task: reportbuilder.indicator.purge")
	void purgeReportBuilderIndicator(ReportBuilderIndicator indicator);
	
	// =========================
	// Section
	// =========================
	@Transactional
	@Authorized({ "Task: reportbuilder.section.add", "Task: reportbuilder.section.edit" })
	ReportBuilderSection saveReportBuilderSection(ReportBuilderSection section);
	
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.section.view")
	ReportBuilderSection getReportBuilderSectionById(Integer id);
	
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.section.view")
	ReportBuilderSection getReportBuilderSectionByUuid(String uuid);
	
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.section.view")
	ReportBuilderSection getReportBuilderSectionByCode(String code);
	
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.section.view")
	List<ReportBuilderSection> getReportBuilderSections(String q, boolean includeRetired, Integer startIndex, Integer limit);
	
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.section.view")
	long getReportBuilderSectionsCount(String q, boolean includeRetired);
	
	@Transactional
	@Authorized("Task: reportbuilder.section.edit")
	void retireReportBuilderSection(ReportBuilderSection section, String reason);
	
	@Transactional
	@Authorized("Task: reportbuilder.section.edit")
	void unretireReportBuilderSection(ReportBuilderSection section);
	
	@Transactional
	@Authorized("Task: reportbuilder.section.purge")
	void purgeReportBuilderSection(ReportBuilderSection section);
	
	// =========================
	// DataTheme
	// =========================
	@Transactional
	@Authorized({ "Task: reportbuilder.theme.add", "Task: reportbuilder.theme.edit" })
	ReportBuilderDataTheme saveReportBuilderDataTheme(ReportBuilderDataTheme theme);
	
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.theme.view")
	ReportBuilderDataTheme getReportBuilderDataThemeById(Integer id);
	
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.theme.view")
	ReportBuilderDataTheme getReportBuilderDataThemeByUuid(String uuid);
	
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.theme.view")
	ReportBuilderDataTheme getReportBuilderDataThemeByCode(String code);
	
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.theme.view")
	List<ReportBuilderDataTheme> getReportBuilderDataThemes(String q, boolean includeRetired, Integer startIndex,
	        Integer limit);
	
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.theme.view")
	long getReportBuilderDataThemesCount(String q, boolean includeRetired);
	
	@Transactional
	@Authorized("Task: reportbuilder.theme.edit")
	void retireReportBuilderDataTheme(ReportBuilderDataTheme theme, String reason);
	
	@Transactional
	@Authorized("Task: reportbuilder.theme.edit")
	void unretireReportBuilderDataTheme(ReportBuilderDataTheme theme);
	
	@Transactional
	@Authorized("Task: reportbuilder.theme.purge")
	void purgeReportBuilderDataTheme(ReportBuilderDataTheme theme);
	
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.etlsource.view")
	List<Map> getETLTables();
	
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.etlsource.view")
	List<Map> getETLTableColumns(String tableName);
	
	// Categories
	@Transactional
	@Authorized({ "Task: reportbuilder.agegroup.add", "Task: reportbuilder.agegroup.edit" })
	ReportBuilderAgeCategory saveAgeCategory(ReportBuilderAgeCategory category);
	
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.agegroup.view")
	ReportBuilderAgeCategory getAgeCategoryByUuid(String uuid);
	
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.agegroup.view")
	ReportBuilderAgeCategory getAgeCategoryByCode(String code);
	
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.agegroup.view")
	List<ReportBuilderAgeCategory> getAgeCategories(String q, boolean includeRetired, Boolean activeOnly,
	        Integer startIndex, Integer limit);
	
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.agegroup.view")
	long getAgeCategoriesCount(String q, boolean includeRetired, Boolean activeOnly);
	
	@Transactional
	@Authorized("Task: reportbuilder.agegroup.edit")
	void retireAgeCategory(ReportBuilderAgeCategory category, String reason);
	
	@Transactional
	@Authorized("Task: reportbuilder.agegroup.edit")
	void unretireAgeCategory(ReportBuilderAgeCategory category);
	
	@Transactional
	@Authorized("Task: reportbuilder.agegroup.purge")
	void purgeAgeCategory(ReportBuilderAgeCategory category);
	
	// Groups
	@Transactional
	@Authorized({ "Task: reportbuilder.agegroup.add", "Task: reportbuilder.agegroup.edit" })
	ReportBuilderAgeGroup saveAgeGroup(ReportBuilderAgeGroup group);
	
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.agegroup.view")
	ReportBuilderAgeGroup getAgeGroupById(Integer id);
	
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.agegroup.view")
	List<ReportBuilderAgeGroup> getAgeGroupsByCategoryUuid(String categoryUuid, Boolean activeOnly);
	
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.agegroup.view")
	List<ReportBuilderAgeGroup> getAgeGroupsByCategoryCode(String categoryCode, Boolean activeOnly);
	
	@Transactional
	@Authorized("Task: reportbuilder.agegroup.purge")
	void purgeAgeGroup(ReportBuilderAgeGroup group);
	
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.agegroup.view")
	List<ReportBuilderAgeGroup> getAgeGroups(String q, ReportBuilderAgeCategory category, Boolean activeOnly,
	        Integer startIndex, Integer limit);
	
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.sql.execute")
	SqlPreviewResult previewSql(String sql, Map<String, Object> params, Integer maxRows);
	
	@Transactional
	@Authorized({ "Task: reportbuilder.report.add", "Task: reportbuilder.report.edit" })
	ReportBuilderReport saveReportBuilderReport(ReportBuilderReport report);
	
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.report.view")
	ReportBuilderReport getReportBuilderReportByUuid(String uuid);
	
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.report.view")
	List<ReportBuilderReport> getReportBuilderReports(String q, boolean includeRetired, Integer startIndex, Integer limit);
	
	@Transactional
	@Authorized("Task: reportbuilder.report.edit")
	void retireReportBuilderReport(ReportBuilderReport report, String reason);
	
	@Transactional
	@Authorized("Task: reportbuilder.report.purge")
	void purgeReportBuilderReport(ReportBuilderReport report);
	
	@Transactional
	@Authorized("Task: reportbuilder.report.compile")
	CompiledReportArtifacts compileReport(String reportBuilderReportUuid);
	
	/**
	 * Compiles a report and optionally adds it to the report library with the specified category.
	 * This method is reusable from both REST API and export/import processes.
	 * 
	 * @param reportBuilderReportUuid The UUID of the report to compile
	 * @param categoryUuid The UUID of the category to add the report to (can be null)
	 * @return CompiledReportArtifacts containing the compiled report and metadata
	 */
	@Transactional
	@Authorized("Task: reportbuilder.report.compile")
	CompiledReportArtifacts compileAndAddToLibrary(String reportBuilderReportUuid, String categoryUuid);
	
	@Transactional
	@Authorized({ "Task: reportbuilder.category.add", "Task: reportbuilder.category.edit" })
	ReportCategory saveReportCategory(ReportCategory category);
	
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.category.view")
	ReportCategory getReportCategoryById(Integer id);
	
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.category.view")
	ReportCategory getReportCategoryByUuid(String uuid);
	
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.category.view")
	List<ReportCategory> getReportCategories(String q, boolean includeRetired, Integer startIndex, Integer limit);
	
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.category.view")
	long getReportCategoriesCount(String q, boolean includeRetired);
	
	@Transactional
	@Authorized("Task: reportbuilder.category.edit")
	void retireReportCategory(ReportCategory category, String reason);
	
	@Transactional
	@Authorized("Task: reportbuilder.category.edit")
	void unretireReportCategory(ReportCategory category);
	
	@Transactional
	@Authorized("Task: reportbuilder.category.purge")
	void purgeReportCategory(ReportCategory category);
	
	@Transactional
	@Authorized({ "Task: reportbuilder.library.add", "Task: reportbuilder.library.edit" })
	ReportLibrary saveReportLibrary(ReportLibrary reportLibrary);
	
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.library.view")
	ReportLibrary getReportLibraryById(Integer id);
	
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.library.view")
	ReportLibrary getReportLibraryByUuid(String uuid);
	
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.library.view")
	List<ReportLibrary> getReportLibraries(String q, boolean includeRetired, Integer startIndex, Integer limit);
	
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.library.view")
	long getReportLibrariesCount(String q, boolean includeRetired);
	
	@Transactional
	@Authorized("Task: reportbuilder.library.edit")
	void retireReportLibrary(ReportLibrary reportLibrary, String reason);
	
	@Transactional
	@Authorized("Task: reportbuilder.library.edit")
	void unretireReportLibrary(ReportLibrary reportLibrary);
	
	@Transactional
	@Authorized("Task: reportbuilder.library.purge")
	void purgeReportLibrary(ReportLibrary reportLibrary);
	
	/**
	 * Add a generic report to the report library
	 */
	@Transactional
	@Authorized({ "Task: reportbuilder.library.add", "Task: reportbuilder.library.edit" })
	void addGenericReportToLibrary(String reportDefinitionUuid, String name, String description, String code,
	        ReportCategory category, ReportBuilderReport.ReportType reportType);
	
	/**
	 * Clean up broken report library entries where ReportDefinition doesn't exist
	 * 
	 * @return Number of entries cleaned up
	 */
	@Transactional
	@Authorized("Task: reportbuilder.report.edit")
	int cleanupBrokenReportReferences();
	
	@Transactional
	@Authorized({ "Task: reportbuilder.etlsource.add", "Task: reportbuilder.etlsource.edit" })
	ETLSource saveETLSource(ETLSource etlSource);
	
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.etlsource.view")
	ETLSource getETLSourceByUuid(String uuid);
	
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.etlsource.view")
	ETLSource getETLSourceById(Integer id);
	
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.etlsource.view")
	List<ETLSource> getAllETLSources(boolean includeRetired);
	
	@Transactional
	@Authorized("Task: reportbuilder.etlsource.edit")
	void retireETLSource(ETLSource etlSource, String retireReason);
	
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.sql.execute")
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
	
	@Authorized("Task: reportbuilder.package.import")
	public ReportImportResult importLegacyReportPackage(File reportDir) throws Exception;
	
	@Authorized("Task: reportbuilder.package.import")
	public List<ReportImportResult> importAllLegacyReportPackages(File legacyReportsRootDir) throws Exception;
	
	@Authorized("Task: reportbuilder.package.import")
	public ReportImportResult validateLegacyReportPackage(File reportDir) throws Exception;
	
	@Authorized("Task: reportbuilder.package.import")
	ReportImportResult importRuntimeLegacyReportPackage(String reportKey) throws Exception;
	
	@Authorized("Task: reportbuilder.package.import")
	ReportImportResult validateRuntimeLegacyReportPackage(String reportKey) throws Exception;
	
	@Authorized("Task: reportbuilder.package.import")
	List<ReportImportResult> importAllRuntimeLegacyReportPackages() throws Exception;
	
	@Authorized("Task: reportbuilder.package.import")
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
	@Authorized("Task: reportbuilder.package.import")
	org.openmrs.module.reporting.report.definition.ReportDefinition importReportFromFile(File jsonFile);
	
	/**
	 * Import a report from JSON string
	 * 
	 * @param jsonContent The JSON configuration
	 * @return OpenMRS ReportDefinition
	 */
	@Authorized("Task: reportbuilder.package.import")
	org.openmrs.module.reporting.report.definition.ReportDefinition importReportFromJson(String jsonContent);
	
	/**
	 * Import all reports from a directory
	 * 
	 * @param reportsDirectory Directory containing JSON report files
	 * @return List of imported ReportDefinitions
	 */
	@Authorized("Task: reportbuilder.package.import")
	List<org.openmrs.module.reporting.report.definition.ReportDefinition> importReportsFromDirectory(File reportsDirectory);
	
	/**
	 * Validate that a JSON report file matches its Java contract
	 * 
	 * @param jsonFile The JSON configuration file
	 * @param javaClass The corresponding Java class
	 * @return validation result with any discrepancies
	 */
	@Authorized("Task: reportbuilder.package.import")
	org.openmrs.module.reportbuilder.legacyconfig.LegacyReportImporter.ValidationResult validateContract(File jsonFile,
	        Class<?> javaClass);
	
	/**
	 * Import all UgandaEMRReports legacy reports and convert them to ReportBuilder format
	 * 
	 * @param legacyReportsPath Path to UgandaEMRReports legacy directory
	 * @return List of imported ReportDefinitions
	 */
	@Authorized("Task: reportbuilder.package.import")
	List<org.openmrs.module.reporting.report.definition.ReportDefinition> importUgandaEMRLegacyReports(
	        String legacyReportsPath);
	
	/**
	 * Ensure that all legacy reports are imported on module startup
	 */
	@Authorized("Task: reportbuilder.package.import")
	void ensureLegacyReportsImported();
	
	// =========================
	// Generic Report Import (from GenericReportImportService)
	// =========================
	
	/**
	 * Import all generic reports from runtime directory
	 * 
	 * @return List of import results
	 */
	@Authorized("Task: reportbuilder.package.import")
	List<org.openmrs.module.reportbuilder.legacyconfig.generic.ReportImportResult> importAllGenericReports();
	
	/**
	 * Import a single generic report from file
	 * 
	 * @param jsonFile The JSON file to import
	 * @return Import result
	 */
	@Authorized("Task: reportbuilder.package.import")
	org.openmrs.module.reportbuilder.legacyconfig.generic.ReportImportResult importGenericReportFromFile(File jsonFile);
	
	/**
	 * Check if generic reports have already been imported
	 * 
	 * @return true if reports are already imported
	 */
	@Authorized("Task: reportbuilder.package.import")
	boolean areGenericReportsAlreadyImported();
	
	/**
	 * Ensure generic reports import task exists
	 */
	@Authorized("Task: reportbuilder.package.import")
	void ensureImportAllGenericReportsTaskExists();
	
	// =========================================================
	// Legacy Reports
	// =========================================================
	
	/**
	 * Get all legacy reports.
	 * 
	 * @return list of all legacy reports
	 */
	@Authorized("Task: reportbuilder.report.view")
	List<LegacyReportConfig> getAllLegacyReports();
	
	/**
	 * Get a legacy report by UUID.
	 * 
	 * @param uuid the UUID of the report
	 * @return the legacy report, or null if not found
	 */
	@Authorized("Task: reportbuilder.report.view")
	LegacyReportConfig getLegacyReportByUuid(String uuid);
	
	/**
	 * Get a legacy report by name.
	 * 
	 * @param name the name of the report
	 * @return the legacy report, or null if not found
	 */
	@Authorized("Task: reportbuilder.report.view")
	LegacyReportConfig getLegacyReportByName(String name);
	
	/**
	 * Create a new legacy report.
	 * 
	 * @param config the report configuration to create
	 * @return the created report configuration
	 */
	@Authorized("Task: reportbuilder.package.import")
	LegacyReportConfig createLegacyReport(LegacyReportConfig config);
	
	/**
	 * Update an existing legacy report.
	 * 
	 * @param uuid the UUID of the report to update
	 * @param config the updated report configuration
	 * @return the updated report configuration
	 */
	@Authorized("Task: reportbuilder.package.import")
	LegacyReportConfig updateLegacyReport(String uuid, LegacyReportConfig config);
	
	/**
	 * Delete a legacy report by UUID (soft delete).
	 * 
	 * @param uuid the UUID of the report to delete
	 */
	@Authorized("Task: reportbuilder.package.import")
	void deleteLegacyReport(String uuid);
	
	/**
	 * Validate a legacy report configuration.
	 * 
	 * @param config the report configuration to validate
	 * @return validation result with errors and warnings
	 */
	@Authorized("Task: reportbuilder.package.import")
	ReportValidationResult validateLegacyReport(LegacyReportConfig config);
	
	/**
	 * Get legacy reports by category.
	 * 
	 * @param category the category to filter by
	 * @return list of legacy reports in the category
	 */
	@Authorized("Task: reportbuilder.report.view")
	List<LegacyReportConfig> getLegacyReportsByCategory(String category);
	
	/**
	 * Get legacy reports by status.
	 * 
	 * @param status the status to filter by
	 * @return list of legacy reports with the status
	 */
	@Authorized("Task: reportbuilder.report.view")
	List<LegacyReportConfig> getLegacyReportsByStatus(String status);
	
	/**
	 * Search legacy reports by name or description.
	 * 
	 * @param query the search query
	 * @return list of matching legacy reports
	 */
	@Authorized("Task: reportbuilder.report.view")
	List<LegacyReportConfig> searchLegacyReports(String query);
	
	/**
	 * Get count of legacy reports.
	 * 
	 * @return the count of legacy reports
	 */
	@Authorized("Task: reportbuilder.report.view")
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
	@Authorized({ "Task: reportbuilder.etlmonitor.add", "Task: reportbuilder.etlmonitor.edit" })
	ETLMonitor saveETLMonitor(ETLMonitor monitor);
	
	/**
	 * Get an ETL Monitor by ID
	 * 
	 * @param id the monitor ID
	 * @return the monitor, or null if not found
	 */
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.etlmonitor.view")
	ETLMonitor getETLMonitorById(Integer id);
	
	/**
	 * Get an ETL Monitor by UUID
	 * 
	 * @param uuid the monitor UUID
	 * @return the monitor, or null if not found
	 */
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.etlmonitor.view")
	ETLMonitor getETLMonitorByUuid(String uuid);
	
	/**
	 * Get an ETL Monitor by code
	 * 
	 * @param code the monitor code
	 * @return the monitor, or null if not found
	 */
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.etlmonitor.view")
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
	@Authorized("Task: reportbuilder.etlmonitor.view")
	List<ETLMonitor> getETLMonitors(String q, boolean includeRetired, Integer startIndex, Integer limit);
	
	/**
	 * Get all active ETL Monitors
	 * 
	 * @return list of active monitors
	 */
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.etlmonitor.view")
	List<ETLMonitor> getActiveETLMonitors();
	
	/**
	 * Get ETL Monitors by category
	 * 
	 * @param category the category to filter by
	 * @param includeRetired whether to include retired monitors
	 * @return list of monitors in the category
	 */
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.etlmonitor.view")
	List<ETLMonitor> getETLMonitorsByCategory(String category, boolean includeRetired);
	
	/**
	 * Get count of ETL Monitors with optional search
	 * 
	 * @param q the search query
	 * @param includeRetired whether to include retired monitors
	 * @return the count of monitors
	 */
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.etlmonitor.view")
	long getETLMonitorsCount(String q, boolean includeRetired);
	
	/**
	 * Retire an ETL Monitor
	 * 
	 * @param monitor the monitor to retire
	 * @param reason the reason for retiring
	 */
	@Transactional
	@Authorized("Task: reportbuilder.etlmonitor.edit")
	void retireETLMonitor(ETLMonitor monitor, String reason);
	
	/**
	 * Unretire an ETL Monitor
	 * 
	 * @param monitor the monitor to unretire
	 */
	@Transactional
	@Authorized("Task: reportbuilder.etlmonitor.edit")
	void unretireETLMonitor(ETLMonitor monitor);
	
	/**
	 * Permanently delete an ETL Monitor
	 * 
	 * @param monitor the monitor to purge
	 */
	@Transactional
	@Authorized("Task: reportbuilder.etlmonitor.purge")
	void purgeETLMonitor(ETLMonitor monitor);
	
	// =========================================================
	// ReportBuilderDashboard
	// =========================================================
	
	/**
	 * Save or update a dashboard
	 * 
	 * @param dashboard the dashboard to save
	 * @return the saved dashboard
	 */
	@Transactional
	@Authorized({ "Task: reportbuilder.dashboard.add", "Task: reportbuilder.dashboard.edit" })
	ReportBuilderDashboard saveReportBuilderDashboard(ReportBuilderDashboard dashboard);
	
	/**
	 * Get a dashboard by ID
	 * 
	 * @param id the dashboard ID
	 * @return the dashboard, or null if not found
	 */
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.dashboard.view")
	ReportBuilderDashboard getReportBuilderDashboardById(Integer id);
	
	/**
	 * Get a dashboard by UUID
	 * 
	 * @param uuid the dashboard UUID
	 * @return the dashboard, or null if not found
	 */
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.dashboard.view")
	ReportBuilderDashboard getReportBuilderDashboardByUuid(String uuid);
	
	/**
	 * Get a dashboard by code
	 * 
	 * @param code the dashboard code
	 * @return the dashboard, or null if not found
	 */
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.dashboard.view")
	ReportBuilderDashboard getReportBuilderDashboardByCode(String code);
	
	/**
	 * Get dashboards with optional search and pagination
	 * 
	 * @param q the search query (searches name, code, description)
	 * @param includeRetired whether to include retired dashboards
	 * @param startIndex the start index for pagination
	 * @param limit the maximum number of results
	 * @return list of dashboards
	 */
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.dashboard.view")
	List<ReportBuilderDashboard> getReportBuilderDashboards(String q, boolean includeRetired, Integer startIndex,
	        Integer limit);
	
	/**
	 * Get all active dashboards
	 * 
	 * @return list of active dashboards
	 */
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.dashboard.view")
	List<ReportBuilderDashboard> getActiveReportBuilderDashboards();
	
	/**
	 * Get dashboards by type
	 * 
	 * @param dashboardType the dashboard type name (ETL, REPORT, CUSTOM)
	 * @param includeRetired whether to include retired dashboards
	 * @return list of dashboards of the given type, or empty if the type is unknown
	 */
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.dashboard.view")
	List<ReportBuilderDashboard> getReportBuilderDashboardsByType(String dashboardType, boolean includeRetired);
	
	/**
	 * Get count of dashboards with optional search
	 * 
	 * @param q the search query
	 * @param includeRetired whether to include retired dashboards
	 * @return the count of dashboards
	 */
	@Transactional(readOnly = true)
	@Authorized("Task: reportbuilder.dashboard.view")
	long getReportBuilderDashboardsCount(String q, boolean includeRetired);
	
	/**
	 * Retire a dashboard
	 * 
	 * @param dashboard the dashboard to retire
	 * @param reason the reason for retiring
	 */
	@Transactional
	@Authorized("Task: reportbuilder.dashboard.edit")
	void retireReportBuilderDashboard(ReportBuilderDashboard dashboard, String reason);
	
	/**
	 * Unretire a dashboard
	 * 
	 * @param dashboard the dashboard to unretire
	 */
	@Transactional
	@Authorized("Task: reportbuilder.dashboard.edit")
	void unretireReportBuilderDashboard(ReportBuilderDashboard dashboard);
	
	/**
	 * Permanently delete a dashboard
	 * 
	 * @param dashboard the dashboard to purge
	 */
	@Transactional
	@Authorized("Task: reportbuilder.dashboard.purge")
	void purgeReportBuilderDashboard(ReportBuilderDashboard dashboard);
	
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
	@Authorized("Task: reportbuilder.package.export")
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
	@Authorized("Task: reportbuilder.package.export")
	org.openmrs.module.reportbuilder.web.controller.dto.ShippingResult shipBatch(List<String> reportUuids, String version,
	        File destination);
	
	/**
	 * Export a single compiled report to a self-contained file with dependencies.
	 * 
	 * @param reportUuid UUID of the report to export
	 * @param destination Destination directory for the exported file
	 * @return File containing the exported compiled report
	 */
	@Authorized("Task: reportbuilder.package.export")
	java.io.File exportCompiledReport(String reportUuid, java.io.File destination);
	
	/**
	 * Export a single entity of a specific type to a file.
	 * 
	 * @param entityType Type of entity to export (e.g., "category", "indicator", "theme")
	 * @param entityUuid UUID of the entity to export
	 * @param destination Destination directory for the exported file
	 * @return File containing the exported entity
	 */
	@Authorized("Task: reportbuilder.package.export")
	File exportEntity(String entityType, String entityUuid, File destination);
	
	/**
	 * Get the default configuration directory for shipping.
	 * 
	 * @return Default shipping destination directory
	 */
	@Authorized("Task: reportbuilder.package.export")
	File getDefaultShippingDirectory();
	
	/**
	 * Get the default configuration directory for import. Returns the same directory as the default
	 * shipping directory, allowing seamless import from packages exported to the default location.
	 * 
	 * @return Default import source directory
	 */
	@Authorized("Task: reportbuilder.package.import")
	File getDefaultImportDirectory();
	
	/**
	 * Export all reports with their dependencies.
	 * 
	 * @param version Version string for the distribution package
	 * @param destination Destination directory for the exported files
	 * @return ShippingResult containing aggregated details
	 */
	@Authorized("Task: reportbuilder.package.export")
	org.openmrs.module.reportbuilder.web.controller.dto.ShippingResult shipAllReports(String version, File destination);
	
	/**
	 * Export all entities of specific types with their dependencies.
	 * 
	 * @param entityTypes List of entity types to export
	 * @param version Version string for the distribution package
	 * @param destination Destination directory for the exported files
	 * @return ShippingResult containing aggregated details
	 */
	@Authorized("Task: reportbuilder.package.export")
	org.openmrs.module.reportbuilder.web.controller.dto.ShippingResult shipAllEntities(java.util.List<String> entityTypes,
	        String version, File destination);
	
	// ========== Report Import Methods ==========
	
	/**
	 * Import all entities from a distribution directory.
	 * 
	 * @param sourceDir Source directory containing the distribution package
	 * @return ImportResult containing summary, successes, and errors
	 */
	@Authorized("Task: reportbuilder.package.import")
	org.openmrs.module.reportbuilder.web.controller.dto.ImportResult importFromDirectory(File sourceDir);
	
	/**
	 * Import a single entity from a file.
	 * 
	 * @param entityType Type of entity to import (e.g., "category", "indicator", "theme")
	 * @param file File containing the entity definition
	 * @return ImportResult for this single import operation
	 */
	@Authorized("Task: reportbuilder.package.import")
	org.openmrs.module.reportbuilder.web.controller.dto.ImportResult importEntity(String entityType, File file);
	
	/**
	 * Validate a distribution package without importing.
	 * 
	 * @param sourceDir Source directory to validate
	 * @return true if package is valid, false otherwise
	 */
	@Authorized("Task: reportbuilder.package.import")
	boolean validatePackage(File sourceDir);
	
	/**
	 * Get the import order for entity types based on dependencies.
	 * 
	 * @return List of entity types in import order
	 */
	@Authorized("Task: reportbuilder.package.import")
	java.util.List<String> getImportOrder();
	
	// ========== Serialized Report Import Methods ==========
	
	/**
	 * Import a serialized report from a file. This method: 1. Reads the serialized report from the
	 * file 2. Stores it in the serialized_object table and returns the UUID 3. Creates/updates
	 * ReportBuilderReport entity 4. Reuses compileAndAddToLibrary to create ReportLibrary entry
	 * with serialized object UUID 5. Ensures no duplicates by checking existing entries
	 * 
	 * @param reportFile The file containing the serialized report
	 * @param categoryUuid The UUID of the category to add the report to (can be null)
	 * @return CompiledReportArtifacts containing the imported report and metadata
	 */
	@Authorized("Task: reportbuilder.report.compile")
	CompiledReportArtifacts importSerializedReport(java.io.File reportFile, String categoryUuid);
	
	/**
	 * Import a serialized report directly from a SerializedReport object. This is useful for
	 * programmatic import without file I/O.
	 * 
	 * @param serializedReport The serialized report to import
	 * @param categoryUuid The UUID of the category to add the report to (can be null)
	 * @return CompiledReportArtifacts containing the imported report and metadata
	 */
	@Authorized("Task: reportbuilder.report.compile")
	CompiledReportArtifacts importSerializedReportFromObject(SerializedReport serializedReport, String categoryUuid);
	
	/**
	 * Creates or updates the ReportLibrary entry linked to a builder report, syncing name, code,
	 * category, report type and the compiled ReportDefinition uuid. Safe no-op when the uuid is
	 * blank or no matching report exists. Failures are logged, never thrown.
	 * 
	 * @param reportBuilderReportUuid the builder report to sync into the library
	 */
	@Authorized({ "Task: reportbuilder.library.add", "Task: reportbuilder.library.edit" })
	void saveOrUpdateLibraryEntry(String reportBuilderReportUuid);
	
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
	@Authorized("Task: reportbuilder.package.export")
	java.util.List<org.openmrs.module.reportbuilder.web.controller.dto.PackageInfo> getAvailablePackages(String search,
	        String status, Integer startIndex, Integer limit);
	
	/**
	 * Get the total count of available packages matching the given filters.
	 * 
	 * @param search Optional search term to filter by name or version
	 * @param status Optional status filter ("valid" or "invalid")
	 * @return Total count of matching packages
	 */
	@Authorized("Task: reportbuilder.package.export")
	long getAvailablePackagesCount(String search, String status);
	
}
