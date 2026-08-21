/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.reportbuilder.web.controller.dto;

/**
 * DTO representing metadata for a report distribution package. Contains information about an
 * exported package that can be imported into another OpenMRS instance.
 */
public class PackageInfo {
	
	private String name;
	
	private String version;
	
	private String description;
	
	private String exportedAt;
	
	private String exportedBy;
	
	private Long size;
	
	private String status;
	
	private String path;
	
	private PackageDependencySummary dependencies;
	
	public PackageInfo() {
		this.dependencies = new PackageDependencySummary();
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
	
	public Long getSize() {
		return size;
	}
	
	public void setSize(Long size) {
		this.size = size;
	}
	
	public String getStatus() {
		return status;
	}
	
	public void setStatus(String status) {
		this.status = status;
	}
	
	public String getPath() {
		return path;
	}
	
	public void setPath(String path) {
		this.path = path;
	}
	
	public PackageDependencySummary getDependencies() {
		return dependencies;
	}
	
	public void setDependencies(PackageDependencySummary dependencies) {
		this.dependencies = dependencies;
	}
}
