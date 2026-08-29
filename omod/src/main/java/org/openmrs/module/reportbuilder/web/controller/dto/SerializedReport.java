package org.openmrs.module.reportbuilder.web.controller.dto;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.openmrs.module.reportbuilder.model.ReportBuilderReport;

/**
 * DTO representing a serialized report ready for distribution.
 * Contains both the source definition and the compiled configuration.
 * This makes the compiled report self-sufficient for import and execution.
 */
public class SerializedReport {

    private String uuid;
    private String name;
    private String description;
    private String code;
    private String version;
    private String category;
    private String categoryUuid;
    private String subcategory;
    private String reportType;
    private String status;
    private String compiledAt;
    private String compiledBy;
    private ObjectNode config;
    private Dependencies dependencies;

    /**
     * Constructor for creating a serialized report from a ReportBuilderReport
     *
     * @param report        The source report entity
     * @param compiledConfig The compiled configuration object
     */
    public SerializedReport(ReportBuilderReport report, ObjectNode compiledConfig) {
        this.uuid = report.getUuid();
        this.name = report.getName();
        this.description = report.getDescription();
        this.code = report.getCode();
        this.version = "1.0.0"; // Default version
        this.reportType = report.getReportType() != null ? report.getReportType().name() : "AGGREGATE";
        this.status = report.getCompileStatus() != null ? report.getCompileStatus().name() : "DRAFT";
        this.config = compiledConfig;
        this.dependencies = new Dependencies();
    }

    public SerializedReport() {
        this.dependencies = new Dependencies();
    }

    // Getters and Setters

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getCategoryUuid() {
        return categoryUuid;
    }

    public void setCategoryUuid(String categoryUuid) {
        this.categoryUuid = categoryUuid;
    }

    public String getSubcategory() {
        return subcategory;
    }

    public void setSubcategory(String subcategory) {
        this.subcategory = subcategory;
    }

    public String getReportType() {
        return reportType;
    }

    public void setReportType(String reportType) {
        this.reportType = reportType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCompiledAt() {
        return compiledAt;
    }

    public void setCompiledAt(String compiledAt) {
        this.compiledAt = compiledAt;
    }

    public String getCompiledBy() {
        return compiledBy;
    }

    public void setCompiledBy(String compiledBy) {
        this.compiledBy = compiledBy;
    }

    public ObjectNode getConfig() {
        return config;
    }

    public void setConfig(ObjectNode config) {
        this.config = config;
    }

    public Dependencies getDependencies() {
        return dependencies;
    }

    public void setDependencies(Dependencies dependencies) {
        this.dependencies = dependencies;
    }

    /**
     * Inner class representing all dependencies referenced by this report.
     * Contains lists of UUIDs/codes for each dependency type.
     */
    public static class Dependencies {

        private java.util.List<String> indicators = new java.util.ArrayList<>();
        private java.util.List<String> sections = new java.util.ArrayList<>();
        private java.util.List<String> themes = new java.util.ArrayList<>();
        private java.util.List<String> ageCategories = new java.util.ArrayList<>();
        private java.util.List<String> ageGroups = new java.util.ArrayList<>();
        private String dataSource;
        private java.util.List<String> etlMonitors = new java.util.ArrayList<>();

        public java.util.List<String> getIndicators() {
            return indicators;
        }

        public void setIndicators(java.util.List<String> indicators) {
            this.indicators = indicators;
        }

        public void addIndicator(String indicator) {
            this.indicators.add(indicator);
        }

        public java.util.List<String> getSections() {
            return sections;
        }

        public void setSections(java.util.List<String> sections) {
            this.sections = sections;
        }

        public void addSection(String section) {
            this.sections.add(section);
        }

        public java.util.List<String> getThemes() {
            return themes;
        }

        public void setThemes(java.util.List<String> themes) {
            this.themes = themes;
        }

        public void addTheme(String theme) {
            this.themes.add(theme);
        }

        public java.util.List<String> getAgeCategories() {
            return ageCategories;
        }

        public void setAgeCategories(java.util.List<String> ageCategories) {
            this.ageCategories = ageCategories;
        }

        public void addAgeCategory(String ageCategory) {
            this.ageCategories.add(ageCategory);
        }

        public java.util.List<String> getAgeGroups() {
            return ageGroups;
        }

        public void setAgeGroups(java.util.List<String> ageGroups) {
            this.ageGroups = ageGroups;
        }

        public void addAgeGroup(String ageGroup) {
            this.ageGroups.add(ageGroup);
        }

        public String getDataSource() {
            return dataSource;
        }

        public void setDataSource(String dataSource) {
            this.dataSource = dataSource;
        }

        public java.util.List<String> getEtlMonitors() {
            return etlMonitors;
        }

        public void setEtlMonitors(java.util.List<String> etlMonitors) {
            this.etlMonitors = etlMonitors;
        }

        public void addETLMonitor(String etlMonitor) {
            this.etlMonitors.add(etlMonitor);
        }
    }
}
