package org.openmrs.module.reportbuilder.web.controller.dto;

/**
 * DTO for exporting a source definition entity (indicators, sections, themes, etc.). Contains the
 * core fields needed for re-importing the entity.
 */
public class SourceDefinitionExport {
	
	/**
	 * UUID of the entity
	 */
	private String uuid;
	
	/**
	 * Display name of the entity
	 */
	private String name;
	
	/**
	 * Description of the entity
	 */
	private String description;
	
	/**
	 * Unique code identifier (used for filename)
	 */
	private String code;
	
	/**
	 * Report type (for reports only)
	 */
	private String reportType;
	
	/**
	 * UUID of the parent category
	 */
	private String categoryUuid;
	
	/**
	 * Name of the parent category
	 */
	private String categoryName;
	
	/**
	 * Configuration JSON containing entity-specific settings
	 */
	private String configJson;
	
	/**
	 * Metadata JSON with additional properties
	 */
	private String metaJson;
	
	/**
	 * Version string for this export
	 */
	private String version;
	
	/**
	 * Timestamp when export was created
	 */
	private String exportedAt;
	
	/**
	 * Username of person who performed the export
	 */
	private String exportedBy;
	
	public SourceDefinitionExport() {
	}
	
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
	
	public String getReportType() {
		return reportType;
	}
	
	public void setReportType(String reportType) {
		this.reportType = reportType;
	}
	
	public String getCategoryUuid() {
		return categoryUuid;
	}
	
	public void setCategoryUuid(String categoryUuid) {
		this.categoryUuid = categoryUuid;
	}
	
	public String getCategoryName() {
		return categoryName;
	}
	
	public void setCategoryName(String categoryName) {
		this.categoryName = categoryName;
	}
	
	public String getConfigJson() {
		return configJson;
	}
	
	public void setConfigJson(String configJson) {
		this.configJson = configJson;
	}
	
	public String getMetaJson() {
		return metaJson;
	}
	
	public void setMetaJson(String metaJson) {
		this.metaJson = metaJson;
	}
	
	public String getVersion() {
		return version;
	}
	
	public void setVersion(String version) {
		this.version = version;
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
}
