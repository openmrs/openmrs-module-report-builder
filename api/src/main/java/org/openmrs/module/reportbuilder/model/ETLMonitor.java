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
 * Entity representing an ETL health monitor configuration. Monitors external ETL endpoints and
 * extracts data using JSONPath expressions.
 */
@Entity
@Table(name = "report_builder_etl_monitor", indexes = { @Index(name = "idx_etl_monitor_uuid", columnList = "uuid"),
        @Index(name = "idx_etl_monitor_code", columnList = "code"),
        @Index(name = "idx_etl_monitor_active", columnList = "active"),
        @Index(name = "idx_etl_monitor_category", columnList = "category"),
        @Index(name = "idx_etl_monitor_retired", columnList = "retired") })
public class ETLMonitor extends BaseOpenmrsMetadata implements Serializable {
	
	private static final long serialVersionUID = 1L;
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "etl_monitor_id")
	private Integer etlMonitorId;
	
	@Column(name = "code", length = 100)
	private String code;
	
	@Enumerated(EnumType.STRING)
	@Column(name = "monitor_type", length = 30, nullable = false)
	private MonitorType monitorType = MonitorType.STATUS_CARD;
	
	@Lob
	@Column(name = "config_json", columnDefinition = "LONGTEXT")
	private String configJson;
	
	@Lob
	@Column(name = "display_config_json", columnDefinition = "LONGTEXT")
	private String displayConfigJson;
	
	@Column(name = "refresh_interval")
	private Integer refreshInterval = 30; // seconds
	
	@Column(name = "timeout")
	private Integer timeout = 10; // seconds for HTTP timeout
	
	@Column(name = "active", nullable = false)
	private Boolean active = true;
	
	@Column(name = "category", length = 100)
	private String category;
	
	@Column(name = "sort_order")
	private Integer sortOrder = 0;
	
	// ========================
	// Enums
	// ========================
	
	/**
	 * Types of visual displays for the monitor
	 */
	public enum MonitorType {
		STATUS_CARD, // Single status indicator
		PROGRESS_BAR, // Progress percentage
		DATA_TABLE, // Tabular data
		TIME_SERIES, // Chart/time series data
		ERROR_LOG, // Error list
		METRICS_GRID // KPI/metric cards
	}
	
	// ========================
	// ID mapping (OpenMRS)
	// ========================
	
	@Override
	public Integer getId() {
		return etlMonitorId;
	}
	
	@Override
	public void setId(Integer id) {
		this.etlMonitorId = id;
	}
	
	// ========================
	// Getters & Setters
	// ========================
	
	public Integer getEtlMonitorId() {
		return etlMonitorId;
	}
	
	public void setEtlMonitorId(Integer etlMonitorId) {
		this.etlMonitorId = etlMonitorId;
	}
	
	public String getCode() {
		return code;
	}
	
	public void setCode(String code) {
		this.code = code;
	}
	
	public MonitorType getMonitorType() {
		return monitorType;
	}
	
	public void setMonitorType(MonitorType monitorType) {
		this.monitorType = monitorType;
	}
	
	public String getConfigJson() {
		return configJson;
	}
	
	public void setConfigJson(String configJson) {
		this.configJson = configJson;
	}
	
	public String getDisplayConfigJson() {
		return displayConfigJson;
	}
	
	public void setDisplayConfigJson(String displayConfigJson) {
		this.displayConfigJson = displayConfigJson;
	}
	
	public Integer getRefreshInterval() {
		return refreshInterval;
	}
	
	public void setRefreshInterval(Integer refreshInterval) {
		this.refreshInterval = refreshInterval;
	}
	
	public Integer getTimeout() {
		return timeout;
	}
	
	public void setTimeout(Integer timeout) {
		this.timeout = timeout;
	}
	
	public Boolean getActive() {
		return active;
	}
	
	public void setActive(Boolean active) {
		this.active = active;
	}
	
	public String getCategory() {
		return category;
	}
	
	public void setCategory(String category) {
		this.category = category;
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
	 * Checks if this monitor is currently active
	 */
	public boolean isActive() {
		return active != null && active;
	}
	
	/**
	 * Returns the monitor type in lowercase for display purposes
	 */
	public String getMonitorTypeLower() {
		return monitorType == null ? "" : monitorType.name().toLowerCase();
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
