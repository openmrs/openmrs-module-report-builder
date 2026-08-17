package org.openmrs.module.reportbuilder.api.db;

import org.openmrs.module.reportbuilder.dto.SqlPreviewResult;
import org.openmrs.module.reportbuilder.model.ETLSource;
import org.openmrs.module.reportbuilder.model.ETLMonitor;
import org.openmrs.module.reportbuilder.model.LegacyReport;
import org.openmrs.module.reportbuilder.model.LegacyReportConfig;
import org.openmrs.module.reportbuilder.model.ReportBuilderAgeCategory;
import org.openmrs.module.reportbuilder.model.ReportBuilderAgeGroup;
import org.openmrs.module.reportbuilder.model.ReportBuilderDataTheme;
import org.openmrs.module.reportbuilder.model.ReportBuilderIndicator;
import org.openmrs.module.reportbuilder.model.ReportBuilderReport;
import org.openmrs.module.reportbuilder.model.ReportBuilderSection;
import org.openmrs.module.reportbuilder.model.ReportCategory;
import org.openmrs.module.reportbuilder.model.ReportLibrary;

import java.util.List;
import java.util.Map;

public interface ReportBuilderDAO {
	
	ReportBuilderIndicator saveReportBuilderIndicator(ReportBuilderIndicator indicator);
	
	ReportBuilderIndicator getReportBuilderIndicatorById(Integer id);
	
	ReportBuilderIndicator getReportBuilderIndicatorByUuid(String uuid);
	
	ReportBuilderIndicator getReportBuilderIndicatorByCode(String code);
	
	List<ReportBuilderIndicator> getReportBuilderIndicators(String qStr, ReportBuilderIndicator.Kind kind,
	        boolean includeRetired, Integer startIndex, Integer limit);
	
	List<ReportBuilderIndicator> getAllReportBuilderaIndicator(Integer startIndex, Integer limit);
	
	List<ReportBuilderIndicator> getReportBuilderIndicators(ReportBuilderIndicator.Kind kind, boolean includeRetired,
	        Integer startIndex, Integer limit);
	
	long getReportBuilderIndicatorsCount(String qStr, ReportBuilderIndicator.Kind kind, boolean includeRetired);
	
	void purgeReportBuilderIndicator(ReportBuilderIndicator indicator);
	
	ReportBuilderSection saveReportBuilderSection(ReportBuilderSection section);
	
	ReportBuilderSection getReportBuilderSectionById(Integer id);
	
	ReportBuilderSection getReportBuilderSectionByUuid(String uuid);
	
	ReportBuilderSection getReportBuilderSectionByCode(String code);
	
	List<ReportBuilderSection> getReportBuilderSections(String qStr, boolean includeRetired, Integer startIndex,
	        Integer limit);
	
	long getReportBuilderSectionsCount(String qStr, boolean includeRetired);
	
	void purgeReportBuilderSection(ReportBuilderSection section);
	
	ReportBuilderDataTheme saveReportBuilderDataTheme(ReportBuilderDataTheme theme);
	
	ReportBuilderDataTheme getReportBuilderDataThemeById(Integer id);
	
	ReportBuilderDataTheme getReportBuilderDataThemeByUuid(String uuid);
	
	ReportBuilderDataTheme getReportBuilderDataThemeByCode(String code);
	
	List<ReportBuilderDataTheme> getReportBuilderDataThemes(String qStr, boolean includeRetired, Integer startIndex,
	        Integer limit);
	
	long getReportBuilderThemesCount(String qStr, boolean includeRetired);
	
	void purgeReportBuilderDataTheme(ReportBuilderDataTheme theme);
	
	ReportBuilderAgeCategory saveAgeCategory(ReportBuilderAgeCategory category);
	
	ReportBuilderAgeCategory getAgeCategoryById(Integer id);
	
	ReportBuilderAgeCategory getAgeCategoryByUuid(String uuid);
	
	ReportBuilderAgeCategory getAgeCategoryByCode(String code);
	
	List<ReportBuilderAgeCategory> getAgeCategories(String qStr, boolean includeRetired, Boolean activeOnly,
	        Integer startIndex, Integer limit);
	
	long getAgeCategoriesCount(String qStr, boolean includeRetired, Boolean activeOnly);
	
	void purgeAgeCategory(ReportBuilderAgeCategory category);
	
	ReportBuilderAgeGroup saveAgeGroup(ReportBuilderAgeGroup group);
	
	ReportBuilderAgeGroup getAgeGroupById(Integer id);
	
	List<ReportBuilderAgeGroup> getAgeGroups(String q, ReportBuilderAgeCategory category, Boolean activeOnly,
	        Integer startIndex, Integer limit);
	
	List<ReportBuilderAgeGroup> getAgeGroupsByCategoryUuid(String categoryUuid, Boolean activeOnly);
	
	List<ReportBuilderAgeGroup> getAgeGroupsByCategoryCode(String categoryCode, Boolean activeOnly);
	
	void purgeAgeGroup(ReportBuilderAgeGroup group);
	
	List<String> getETLTables(List<String> allowedPrefixes);
	
	List<Map> getETLTableColumns(String tableName);
	
	SqlPreviewResult previewSql(String rawSql, Map<String, Object> params, Integer maxRows);
	
	ReportBuilderReport saveReportBuilderReport(ReportBuilderReport report);
	
	ReportBuilderReport getReportBuilderReportByUuid(String uuid);
	
	List<ReportBuilderReport> getReportBuilderReports(String q, boolean includeRetired, Integer startIndex, Integer limit);
	
	void deleteReportBuilderReport(ReportBuilderReport report);
	
	void retireReportBuilderReport(ReportBuilderReport report, String reason);
	
	void purgeReportBuilderReport(ReportBuilderReport report);
	
	ReportCategory saveReportCategory(ReportCategory category);
	
	ReportCategory getReportCategoryById(Integer id);
	
	ReportCategory getReportCategoryByUuid(String uuid);
	
	List<ReportCategory> getReportCategories(String q, boolean includeRetired, Integer startIndex, Integer limit);
	
	long getReportCategoriesCount(String q, boolean includeRetired);
	
	void purgeReportCategory(ReportCategory category);
	
	ReportLibrary saveReportLibrary(ReportLibrary reportLibrary);
	
	ReportLibrary getReportLibraryById(Integer id);
	
	ReportLibrary getReportLibraryByUuid(String uuid);
	
	List<ReportLibrary> getReportLibraries(String q, boolean includeRetired, Integer startIndex, Integer limit);
	
	long getReportLibrariesCount(String q, boolean includeRetired);
	
	ReportLibrary getReportLibraryByReportDefinitionUuid(String reportDefinitionUuid);
	
	List<ReportLibrary> getReportLibrariesByName(String name);
	
	ReportLibrary getReportLibraryByBuilderReportUuid(String builderReportUuid);
	
	void purgeReportLibrary(ReportLibrary reportLibrary);
	
	ETLSource saveETLSource(ETLSource etlSource);
	
	ETLSource getETLSourceByUuid(String uuid);
	
	ETLSource getETLSourceById(Integer id);
	
	List<ETLSource> getAllETLSources(boolean includeRetired);
	
	void deleteETLSource(ETLSource etlSource);
	
	// Legacy Report CRUD methods
	LegacyReport saveLegacyReport(LegacyReport legacyReport);
	
	LegacyReport getLegacyReportByUuid(String uuid);
	
	LegacyReport getLegacyReportByName(String name);
	
	List<LegacyReportConfig> getAllLegacyReports();
	
	List<LegacyReportConfig> getLegacyReportsByCategory(String category);
	
	List<LegacyReportConfig> getLegacyReportsByStatus(String status);
	
	List<LegacyReportConfig> searchLegacyReports(String query);
	
	int getLegacyReportCount();
	
	void deleteLegacyReport(String uuid);
	
	// =========================================================
	// ETLMonitor CRUD methods
	// =========================================================
	
	ETLMonitor saveETLMonitor(ETLMonitor monitor);
	
	ETLMonitor getETLMonitorById(Integer id);
	
	ETLMonitor getETLMonitorByUuid(String uuid);
	
	ETLMonitor getETLMonitorByCode(String code);
	
	List<ETLMonitor> getETLMonitors(String qStr, boolean includeRetired, Integer startIndex, Integer limit);
	
	List<ETLMonitor> getETLMonitorsByCategory(String category, boolean includeRetired);
	
	List<ETLMonitor> getActiveETLMonitors();
	
	long getETLMonitorsCount(String qStr, boolean includeRetired);
	
	void purgeETLMonitor(ETLMonitor monitor);
}
