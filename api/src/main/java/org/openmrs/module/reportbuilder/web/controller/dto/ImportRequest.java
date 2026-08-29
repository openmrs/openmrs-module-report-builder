package org.openmrs.module.reportbuilder.web.controller.dto;

/**
 * Request DTO for importing reports. Specifies the import type and source directory.
 */
public class ImportRequest {
	
	/**
	 * Path to the source directory containing the distribution package. For artifacts type, should
	 * contain reportbuilder/ and reports/ subdirectories. For compiledReports type, should contain
	 * configuration/reports/ subdirectory.
	 */
	private String sourceDirectory;
	
	/**
	 * Import type: "artifacts" for bulk import from directory, "compiledReports" for compiled
	 * reports import. If not specified, defaults to "artifacts".
	 */
	private String type;
	
	public ImportRequest() {
	}
	
	public ImportRequest(String sourceDirectory) {
		this.sourceDirectory = sourceDirectory;
	}
	
	public String getSourceDirectory() {
		return sourceDirectory;
	}
	
	public void setSourceDirectory(String sourceDirectory) {
		this.sourceDirectory = sourceDirectory;
	}
	
	public String getType() {
		return type;
	}
	
	public void setType(String type) {
		this.type = type;
	}
}
