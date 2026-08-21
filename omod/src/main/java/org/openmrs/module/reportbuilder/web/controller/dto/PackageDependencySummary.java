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
 * DTO representing a summary count of dependencies in a package. Used to display the number of each
 * entity type included in a distribution package.
 */
public class PackageDependencySummary {
	
	private Integer categories;
	
	private Integer indicators;
	
	private Integer themes;
	
	private Integer sections;
	
	private Integer library;
	
	private Integer ageCategories;
	
	private Integer ageGroups;
	
	private Integer etlSources;
	
	private Integer etlMonitors;
	
	public Integer getCategories() {
		return categories;
	}
	
	public void setCategories(Integer categories) {
		this.categories = categories;
	}
	
	public Integer getIndicators() {
		return indicators;
	}
	
	public void setIndicators(Integer indicators) {
		this.indicators = indicators;
	}
	
	public Integer getThemes() {
		return themes;
	}
	
	public void setThemes(Integer themes) {
		this.themes = themes;
	}
	
	public Integer getSections() {
		return sections;
	}
	
	public void setSections(Integer sections) {
		this.sections = sections;
	}
	
	public Integer getLibrary() {
		return library;
	}
	
	public void setLibrary(Integer library) {
		this.library = library;
	}
	
	public Integer getAgeCategories() {
		return ageCategories;
	}
	
	public void setAgeCategories(Integer ageCategories) {
		this.ageCategories = ageCategories;
	}
	
	public Integer getAgeGroups() {
		return ageGroups;
	}
	
	public void setAgeGroups(Integer ageGroups) {
		this.ageGroups = ageGroups;
	}
	
	public Integer getEtlSources() {
		return etlSources;
	}
	
	public void setEtlSources(Integer etlSources) {
		this.etlSources = etlSources;
	}
	
	public Integer getEtlMonitors() {
		return etlMonitors;
	}
	
	public void setEtlMonitors(Integer etlMonitors) {
		this.etlMonitors = etlMonitors;
	}
}
