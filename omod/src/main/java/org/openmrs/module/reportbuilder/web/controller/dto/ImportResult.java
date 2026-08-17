package org.openmrs.module.reportbuilder.web.controller.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Result DTO for the import operation.
 * Contains summary, successes, and errors from the import process.
 */
public class ImportResult {

    private boolean success;
    private String summary;
    private List<ImportSuccess> successes = new ArrayList<>();
    private List<ImportError> errors = new ArrayList<>();

    public ImportResult() {
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<ImportSuccess> getSuccesses() {
        return successes;
    }

    public void setSuccesses(List<ImportSuccess> successes) {
        this.successes = successes;
    }

    public List<ImportError> getErrors() {
        return errors;
    }

    public void setErrors(List<ImportError> errors) {
        this.errors = errors;
    }

    /**
     * Add a successful import to the result.
     *
     * @param type     Entity type that was imported
     * @param filename Name of the file that was imported
     */
    public void addSuccess(String type, String filename) {
        successes.add(new ImportSuccess(type, filename));
        updateSuccessStatus();
    }

    /**
     * Add an error to the result.
     *
     * @param type     Entity type that failed to import
     * @param filename Name of the file that failed
     * @param message  Error message describing the failure
     */
    public void addError(String type, String filename, String message) {
        errors.add(new ImportError(type, filename, message));
        updateSuccessStatus();
    }

    /**
     * Update success status based on whether there are any errors.
     */
    private void updateSuccessStatus() {
        this.success = errors.isEmpty();
    }

    /**
     * Get the count of successful imports.
     */
    public int getSuccessCount() {
        return successes.size();
    }

    /**
     * Get the count of failed imports.
     */
    public int getErrorCount() {
        return errors.size();
    }

    /**
     * Represents a successfully imported entity.
     */
    public static class ImportSuccess {

        private String type;
        private String filename;

        public ImportSuccess() {
        }

        public ImportSuccess(String type, String filename) {
            this.type = type;
            this.filename = filename;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getFilename() {
            return filename;
        }

        public void setFilename(String filename) {
            this.filename = filename;
        }

        @Override
        public String toString() {
            return "Imported " + type + " from " + filename;
        }
    }

    /**
     * Represents a failed import attempt.
     */
    public static class ImportError {

        private String type;
        private String filename;
        private String message;

        public ImportError() {
        }

        public ImportError(String type, String filename, String message) {
            this.type = type;
            this.filename = filename;
            this.message = message;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getFilename() {
            return filename;
        }

        public void setFilename(String filename) {
            this.filename = filename;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        @Override
        public String toString() {
            return "Failed to import " + type + " from " + filename + ": " + message;
        }
    }
}
