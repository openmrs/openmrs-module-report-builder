/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.reportbuilder.model;

import org.openmrs.BaseOpenmrsMetadata;

import javax.persistence.*;
import java.io.Serializable;

/**
 * Entity representing a persisted dashboard configuration. A dashboard composes sections and
 * widgets (ETL monitors, reports) via a frontend-owned config_json payload, which the backend
 * stores and serves as an opaque JSON string.
 */
@Entity
@Table(name = "report_builder_dashboard", indexes = { @Index(name = "idx_dashboard_uuid", columnList = "uuid"),
        @Index(name = "idx_dashboard_code", columnList = "code"),
        @Index(name = "idx_dashboard_active", columnList = "active"),
        @Index(name = "idx_dashboard_type", columnList = "dashboard_type"),
        @Index(name = "idx_dashboard_retired", columnList = "retired") })
public class ReportBuilderDashboard extends BaseOpenmrsMetadata implements Serializable {
	
	private static final long serialVersionUID = 1L;
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "report_builder_dashboard_id")
	private Integer dashboardId;
	
	@Column(name = "code", length = 100)
	private String code;
	
	@Enumerated(EnumType.STRING)
	@Column(name = "dashboard_type", length = 30, nullable = false)
	private DashboardType dashboardType = DashboardType.CUSTOM;
	
	@Lob
	@Column(name = "config_json", columnDefinition = "LONGTEXT")
	private String configJson;
	
	@Column(name = "active", nullable = false)
	private Boolean active = true;
	
	@Column(name = "sort_order")
	private Integer sortOrder = 0;
	
	// ========================
	// Enums
	// ========================
	
	/**
	 * High-level grouping for dashboards. Open enum: new widget types in config_json require no
	 * backend change.
	 */
	public enum DashboardType {
		ETL, // ETL monitoring dashboards
		REPORT, // Report-centric dashboards
		CUSTOM // Anything else
	}
	
	// ========================
	// ID mapping (OpenMRS)
	// ========================
	
	@Override
	public Integer getId() {
		return dashboardId;
	}
	
	@Override
	public void setId(Integer id) {
		this.dashboardId = id;
	}
	
	// ========================
	// Getters & Setters
	// ========================
	
	public Integer getDashboardId() {
		return dashboardId;
	}
	
	public void setDashboardId(Integer dashboardId) {
		this.dashboardId = dashboardId;
	}
	
	public String getCode() {
		return code;
	}
	
	public void setCode(String code) {
		this.code = code;
	}
	
	public DashboardType getDashboardType() {
		return dashboardType;
	}
	
	public void setDashboardType(DashboardType dashboardType) {
		this.dashboardType = dashboardType;
	}
	
	public String getConfigJson() {
		return configJson;
	}
	
	public void setConfigJson(String configJson) {
		this.configJson = configJson;
	}
	
	public Boolean getActive() {
		return active;
	}
	
	public void setActive(Boolean active) {
		this.active = active;
	}
	
	public Integer getSortOrder() {
		return sortOrder;
	}
	
	public void setSortOrder(Integer sortOrder) {
		this.sortOrder = sortOrder;
	}
	
	// ========================
	// Helper Methods
	// ========================
	
	/**
	 * Checks if this dashboard is currently active
	 */
	public boolean isActive() {
		return active != null && active;
	}
	
	/**
	 * Returns a display-friendly representation with code if available
	 */
	public String getDisplay() {
		if (getName() != null && code != null && !code.trim().isEmpty()) {
			return getName() + " (" + code + ")";
		}
		return getName();
	}
}
