/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.reportbuilder.security;

/**
 * Privileges for the report builder module, grouped by domain with the OpenMRS
 * {@code Task: <module>.<domain>.<action>} naming convention. The privileges are provisioned by the
 * initializer module from
 * {@code omod/src/main/resources/configuration/privileges/reportbuilder-privileges.csv}, and
 * bundled into the module roles defined in
 * {@code omod/src/main/resources/configuration/roles/reportbuilder-roles.csv}. Domains and their
 * actions:
 * <ul>
 * <li>report - report definitions (view/add/edit/purge/compile/run)</li>
 * <li>indicator - SQL indicators (view/add/edit/purge)</li>
 * <li>section - report sections (view/add/edit/purge)</li>
 * <li>theme - data themes (view/add/edit/purge)</li>
 * <li>dashboard - dashboards (view/add/edit/purge)</li>
 * <li>category - report categories (view/add/edit/purge)</li>
 * <li>agegroup - age categories and age groups (view/add/edit/purge)</li>
 * <li>library - report library entries (view/add/edit/purge)</li>
 * <li>etlsource - ETL source connections and their tables (view/add/edit/purge)</li>
 * <li>etlmonitor - ETL health monitors (view/add/edit/purge)</li>
 * <li>schema - database schema browsing (view)</li>
 * <li>sql - executing SQL previews (execute)</li>
 * <li>package - distribution packages (import/export)</li>
 * </ul>
 */
public final class ReportBuilderPrivileges {
	
	// ========================
	// Report
	// ========================
	public static final String REPORT_VIEW = "Task: reportbuilder.report.view";
	
	public static final String REPORT_ADD = "Task: reportbuilder.report.add";
	
	public static final String REPORT_EDIT = "Task: reportbuilder.report.edit";
	
	public static final String REPORT_PURGE = "Task: reportbuilder.report.purge";
	
	public static final String REPORT_COMPILE = "Task: reportbuilder.report.compile";
	
	public static final String REPORT_RUN = "Task: reportbuilder.report.run";
	
	// ========================
	// Indicator
	// ========================
	public static final String INDICATOR_VIEW = "Task: reportbuilder.indicator.view";
	
	public static final String INDICATOR_ADD = "Task: reportbuilder.indicator.add";
	
	public static final String INDICATOR_EDIT = "Task: reportbuilder.indicator.edit";
	
	public static final String INDICATOR_PURGE = "Task: reportbuilder.indicator.purge";
	
	// ========================
	// Section
	// ========================
	public static final String SECTION_VIEW = "Task: reportbuilder.section.view";
	
	public static final String SECTION_ADD = "Task: reportbuilder.section.add";
	
	public static final String SECTION_EDIT = "Task: reportbuilder.section.edit";
	
	public static final String SECTION_PURGE = "Task: reportbuilder.section.purge";
	
	// ========================
	// Theme
	// ========================
	public static final String THEME_VIEW = "Task: reportbuilder.theme.view";
	
	public static final String THEME_ADD = "Task: reportbuilder.theme.add";
	
	public static final String THEME_EDIT = "Task: reportbuilder.theme.edit";
	
	public static final String THEME_PURGE = "Task: reportbuilder.theme.purge";
	
	// ========================
	// Dashboard
	// ========================
	public static final String DASHBOARD_VIEW = "Task: reportbuilder.dashboard.view";
	
	public static final String DASHBOARD_ADD = "Task: reportbuilder.dashboard.add";
	
	public static final String DASHBOARD_EDIT = "Task: reportbuilder.dashboard.edit";
	
	public static final String DASHBOARD_PURGE = "Task: reportbuilder.dashboard.purge";
	
	// ========================
	// Category
	// ========================
	public static final String CATEGORY_VIEW = "Task: reportbuilder.category.view";
	
	public static final String CATEGORY_ADD = "Task: reportbuilder.category.add";
	
	public static final String CATEGORY_EDIT = "Task: reportbuilder.category.edit";
	
	public static final String CATEGORY_PURGE = "Task: reportbuilder.category.purge";
	
	// ========================
	// Age groups (age categories and age groups)
	// ========================
	public static final String AGEGROUP_VIEW = "Task: reportbuilder.agegroup.view";
	
	public static final String AGEGROUP_ADD = "Task: reportbuilder.agegroup.add";
	
	public static final String AGEGROUP_EDIT = "Task: reportbuilder.agegroup.edit";
	
	public static final String AGEGROUP_PURGE = "Task: reportbuilder.agegroup.purge";
	
	// ========================
	// Library
	// ========================
	public static final String LIBRARY_VIEW = "Task: reportbuilder.library.view";
	
	public static final String LIBRARY_ADD = "Task: reportbuilder.library.add";
	
	public static final String LIBRARY_EDIT = "Task: reportbuilder.library.edit";
	
	public static final String LIBRARY_PURGE = "Task: reportbuilder.library.purge";
	
	// ========================
	// ETL sources
	// ========================
	public static final String ETLSOURCE_VIEW = "Task: reportbuilder.etlsource.view";
	
	public static final String ETLSOURCE_ADD = "Task: reportbuilder.etlsource.add";
	
	public static final String ETLSOURCE_EDIT = "Task: reportbuilder.etlsource.edit";
	
	public static final String ETLSOURCE_PURGE = "Task: reportbuilder.etlsource.purge";
	
	// ========================
	// ETL monitors
	// ========================
	public static final String ETLMONITOR_VIEW = "Task: reportbuilder.etlmonitor.view";
	
	public static final String ETLMONITOR_ADD = "Task: reportbuilder.etlmonitor.add";
	
	public static final String ETLMONITOR_EDIT = "Task: reportbuilder.etlmonitor.edit";
	
	public static final String ETLMONITOR_PURGE = "Task: reportbuilder.etlmonitor.purge";
	
	// ========================
	// Schema and SQL
	// ========================
	public static final String SCHEMA_VIEW = "Task: reportbuilder.schema.view";
	
	public static final String SQL_EXECUTE = "Task: reportbuilder.sql.execute";
	
	// ========================
	// Distribution packages
	// ========================
	public static final String PACKAGE_IMPORT = "Task: reportbuilder.package.import";
	
	public static final String PACKAGE_EXPORT = "Task: reportbuilder.package.export";
	
	private ReportBuilderPrivileges() {
	}
}
