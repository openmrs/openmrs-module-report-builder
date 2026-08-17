package org.openmrs.module.reportbuilder.web.controller.dto;

/**
 * Request DTO for importing reports from a directory. Specifies the source directory containing the
 * distribution package.
 */
public class ImportRequest {
	
	/**
	 * Path to the source directory containing the distribution package. The directory should
	 * contain the reportbuilder/ and reports/ subdirectories.
	 */
	private String sourceDirectory;
	
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
}
