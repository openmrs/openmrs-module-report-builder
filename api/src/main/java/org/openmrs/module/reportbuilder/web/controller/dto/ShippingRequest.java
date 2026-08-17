package org.openmrs.module.reportbuilder.web.controller.dto;

/**
 * Request DTO for shipping a single report to a distribution package. Used to initiate the
 * export/shipping process for a ReportBuilder report.
 */
public class ShippingRequest {
	
	/**
	 * UUID of the report to ship
	 */
	private String reportUuid;
	
	/**
	 * Version string for the distribution package
	 */
	private String version;
	
	/**
	 * Optional destination directory path. If not specified, defaults to the OpenMRS configuration
	 * directory.
	 */
	private String destination;
	
	public ShippingRequest() {
	}
	
	public ShippingRequest(String reportUuid, String version) {
		this.reportUuid = reportUuid;
		this.version = version;
	}
	
	public String getReportUuid() {
		return reportUuid;
	}
	
	public void setReportUuid(String reportUuid) {
		this.reportUuid = reportUuid;
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
}
