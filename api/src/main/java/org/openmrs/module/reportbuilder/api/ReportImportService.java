/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.reportbuilder.api;

import org.openmrs.api.OpenmrsService;
import org.openmrs.module.reportbuilder.web.controller.dto.ImportResult;

import java.io.File;

/**
 * Service for importing ReportBuilder entities from distribution packages. Handles the import of
 * reports and dependencies from a structured directory format.
 */
public interface ReportImportService extends OpenmrsService {
	
	/**
	 * Import all entities from a distribution directory. Imports entities in dependency order to
	 * ensure parent references are resolved.
	 * 
	 * @param sourceDir Source directory containing the distribution package
	 * @return ImportResult containing summary, successes, and errors
	 */
	ImportResult importFromDirectory(File sourceDir);
	
	/**
	 * Import a single entity from a file. Supports all entity types (categories, indicators,
	 * sections, themes, reports, etc.)
	 * 
	 * @param entityType Type of entity to import (e.g., "category", "indicator", "theme")
	 * @param file File containing the entity definition
	 * @return ImportResult for this single import operation
	 */
	ImportResult importEntity(String entityType, File file);
	
	/**
	 * Validate a distribution package without importing. Checks for required structure, valid JSON,
	 * and dependency completeness.
	 * 
	 * @param sourceDir Source directory to validate
	 * @return true if package is valid, false otherwise
	 */
	boolean validatePackage(File sourceDir);
	
	/**
	 * Get the import order for entity types based on dependencies. Returns the ordered list of
	 * entity types that should be imported.
	 * 
	 * @return List of entity types in import order
	 */
	java.util.List<String> getImportOrder();
}
