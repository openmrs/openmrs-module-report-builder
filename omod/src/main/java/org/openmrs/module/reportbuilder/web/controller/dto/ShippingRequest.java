package org.openmrs.module.reportbuilder.web.controller.dto;

/**
 * Request DTO for exporting reports. Specifies the export type and destination.
 */
public class ShippingRequest {
	
	/**
	 * Version string for the distribution package
	 */
	private String version;
	
	/**
	 * Optional destination directory path. If not specified, defaults to the OpenMRS configuration
	 * directory.
	 */
	private String destination;
	
	/**
	 * Export type: "artifacts" for full artifacts with dependencies, "compiledReports" for compiled
	 * reports only. If not specified, defaults to "artifacts".
	 */
	private String type;
	
	public ShippingRequest() {
	}
	
	public ShippingRequest(String version) {
		this.version = version;
	}
	
	public String getVersion() {
		return version;
	}
	
	public void setVersion(String version) {
		this.version = version;
	}
	
	public String getDestination() {
		return destination;
	}
	
	public void setDestination(String destination) {
		this.destination = destination;
	}
	
	public String getType() {
		return type;
	}
	
	public void setType(String type) {
		this.type = type;
	}
}
