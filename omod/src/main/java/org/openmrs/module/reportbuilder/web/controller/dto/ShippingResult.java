package org.openmrs.module.reportbuilder.web.controller.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Result DTO for the shipping operation.
 * Contains details about the exported report and its dependencies.
 */
public class ShippingResult {

    private boolean success;
    private String reportCode;
    private String version;
    private String sourceFile;
    private String compiledFile;
    private ShippingDependencies dependencies;
    private String versionFile;
    private String errorMessage;

    public ShippingResult() {
        this.dependencies = new ShippingDependencies();
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getReportCode() {
        return reportCode;
    }

    public void setReportCode(String reportCode) {
        this.reportCode = reportCode;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
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

    public ShippingDependencies getDependencies() {
        return dependencies;
    }

    public void setDependencies(ShippingDependencies dependencies) {
        this.dependencies = dependencies;
    }

    public String getVersionFile() {
        return versionFile;
    }

    public void setVersionFile(String versionFile) {
        this.versionFile = versionFile;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    /**
     * Inner class representing all dependencies that were shipped with the report.
     * Each list contains the file names of the exported dependencies.
     */
    public static class ShippingDependencies {

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

        public void addCategory(String name) {
            this.categories.add(name);
        }

        public List<String> getLibrary() {
            return library;
        }

        public void setLibrary(List<String> library) {
            this.library = library;
        }

        public void addLibrary(String name) {
            this.library.add(name);
        }

        public List<String> getIndicators() {
            return indicators;
        }

        public void setIndicators(List<String> indicators) {
            this.indicators = indicators;
        }

        public void addIndicator(String name) {
            this.indicators.add(name);
        }

        public List<String> getSections() {
            return sections;
        }

        public void setSections(List<String> sections) {
            this.sections = sections;
        }

        public void addSection(String name) {
            this.sections.add(name);
        }

        public List<String> getThemes() {
            return themes;
        }

        public void setThemes(List<String> themes) {
            this.themes = themes;
        }

        public void addTheme(String name) {
            this.themes.add(name);
        }

        public List<String> getAgeCategories() {
            return ageCategories;
        }

        public void setAgeCategories(List<String> ageCategories) {
            this.ageCategories = ageCategories;
        }

        public void addAgeCategory(String name) {
            this.ageCategories.add(name);
        }

        public List<String> getAgeGroups() {
            return ageGroups;
        }

        public void setAgeGroups(List<String> ageGroups) {
            this.ageGroups = ageGroups;
        }

        public void addAgeGroup(String name) {
            this.ageGroups.add(name);
        }

        public List<String> getEtlSources() {
            return etlSources;
        }

        public void setEtlSources(List<String> etlSources) {
            this.etlSources = etlSources;
        }

        public void addETLSource(String name) {
            this.etlSources.add(name);
        }

        public List<String> getEtlMonitors() {
            return etlMonitors;
        }

        public void setEtlMonitors(List<String> etlMonitors) {
            this.etlMonitors = etlMonitors;
        }

        public void addETLMonitor(String name) {
            this.etlMonitors.add(name);
        }
    }
}
