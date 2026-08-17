package org.openmrs.module.reportbuilder.web.controller.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO for version metadata of a distribution package.
 * Contains package information and contents manifest.
 */
public class VersionMetadata {

    private PackageInfo packageInfo;
    private Contents contents;

    public VersionMetadata() {
        this.packageInfo = new PackageInfo();
        this.contents = new Contents();
    }

    public PackageInfo getPackageInfo() {
        return packageInfo;
    }

    public void setPackageInfo(PackageInfo packageInfo) {
        this.packageInfo = packageInfo;
    }

    public Contents getContents() {
        return contents;
    }

    public void setContents(Contents contents) {
        this.contents = contents;
    }

    /**
     * Package information including name, version, and export details.
     */
    public static class PackageInfo {

        private String name;
        private String version;
        private String description;
        private String exportedAt;
        private String exportedBy;
        private String reportBuilderVersion;

        public PackageInfo() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getExportedAt() {
            return exportedAt;
        }

        public void setExportedAt(String exportedAt) {
            this.exportedAt = exportedAt;
        }

        public String getExportedBy() {
            return exportedBy;
        }

        public void setExportedBy(String exportedBy) {
            this.exportedBy = exportedBy;
        }

        public String getReportBuilderVersion() {
            return reportBuilderVersion;
        }

        public void setReportBuilderVersion(String reportBuilderVersion) {
            this.reportBuilderVersion = reportBuilderVersion;
        }
    }

    /**
     * Contents manifest including all reports and dependencies in the package.
     */
    public static class Contents {

        private List<ReportInfo> reports = new ArrayList<>();
        private DependencyInfo dependencies;

        public Contents() {
            this.dependencies = new DependencyInfo();
        }

        public List<ReportInfo> getReports() {
            return reports;
        }

        public void setReports(List<ReportInfo> reports) {
            this.reports = reports;
        }

        public void addReport(ReportInfo report) {
            this.reports.add(report);
        }

        public DependencyInfo getDependencies() {
            return dependencies;
        }

        public void setDependencies(DependencyInfo dependencies) {
            this.dependencies = dependencies;
        }
    }

    /**
     * Information about a single report in the package.
     */
    public static class ReportInfo {

        private String uuid;
        private String code;
        private String type;
        private String sourceFile;
        private String compiledFile;

        public ReportInfo() {
        }

        public ReportInfo(String uuid, String code, String type, String sourceFile, String compiledFile) {
            this.uuid = uuid;
            this.code = code;
            this.type = type;
            this.sourceFile = sourceFile;
            this.compiledFile = compiledFile;
        }

        public String getUuid() {
            return uuid;
        }

        public void setUuid(String uuid) {
            this.uuid = uuid;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getSourceFile() {
            return sourceFile;
        }

        public void setSourceFile(String sourceFile) {
            this.sourceFile = sourceFile;
        }

        public String getCompiledFile() {
            return compiledFile;
        }

        public void setCompiledFile(String compiledFile) {
            this.compiledFile = compiledFile;
        }
    }

    /**
     * Summary of all dependencies in the package.
     */
    public static class DependencyInfo {

        private List<String> categories = new ArrayList<>();
        private List<String> library = new ArrayList<>();
        private List<String> indicators = new ArrayList<>();
        private List<String> sections = new ArrayList<>();
        private List<String> themes = new ArrayList<>();
        private List<String> ageCategories = new ArrayList<>();
        private List<String> ageGroups = new ArrayList<>();
        private List<String> etlSources = new ArrayList<>();
        private List<String> etlMonitors = new ArrayList<>();

        public List<String> getCategories() {
            return categories;
        }

        public void setCategories(List<String> categories) {
            this.categories = categories;
        }

        public void addCategory(String category) {
            this.categories.add(category);
        }

        public List<String> getLibrary() {
            return library;
        }

        public void setLibrary(List<String> library) {
            this.library = library;
        }

        public void addLibrary(String lib) {
            this.library.add(lib);
        }

        public List<String> getIndicators() {
            return indicators;
        }

        public void setIndicators(List<String> indicators) {
            this.indicators = indicators;
        }

        public void addIndicator(String indicator) {
            this.indicators.add(indicator);
        }

        public List<String> getSections() {
            return sections;
        }

        public void setSections(List<String> sections) {
            this.sections = sections;
        }

        public void addSection(String section) {
            this.sections.add(section);
        }

        public List<String> getThemes() {
            return themes;
        }

        public void setThemes(List<String> themes) {
            this.themes = themes;
        }

        public void addTheme(String theme) {
            this.themes.add(theme);
        }

        public List<String> getAgeCategories() {
            return ageCategories;
        }

        public void setAgeCategories(List<String> ageCategories) {
            this.ageCategories = ageCategories;
        }

        public void addAgeCategory(String ageCategory) {
            this.ageCategories.add(ageCategory);
        }

        public List<String> getAgeGroups() {
            return ageGroups;
        }

        public void setAgeGroups(List<String> ageGroups) {
            this.ageGroups = ageGroups;
        }

        public void addAgeGroup(String ageGroup) {
            this.ageGroups.add(ageGroup);
        }

        public List<String> getEtlSources() {
            return etlSources;
        }

        public void setEtlSources(List<String> etlSources) {
            this.etlSources = etlSources;
        }

        public void addETLSource(String etlSource) {
            this.etlSources.add(etlSource);
        }

        public List<String> getEtlMonitors() {
            return etlMonitors;
        }

        public void setEtlMonitors(List<String> etlMonitors) {
            this.etlMonitors = etlMonitors;
        }

        public void addETLMonitor(String etlMonitor) {
            this.etlMonitors.add(etlMonitor);
        }
    }
}
