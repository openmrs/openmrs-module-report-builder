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
import org.openmrs.module.reportbuilder.web.controller.dto.ShippingResult;

import java.io.File;
import java.util.List;

/**
 * Service for shipping/exporting ReportBuilder reports to distribution packages. Handles the export
 * of reports and all their dependencies to a structured directory format.
 */
public interface ReportShippingService extends OpenmrsService {
	
	/**
	 * Ship a single report to a distribution package. Exports the report definition and all
	 * dependencies to the destination directory.
	 * 
	 * @param reportUuid UUID of the report to ship
	 * @param version Version string for the distribution package
	 * @param destination Destination directory for the exported files
	 * @return ShippingResult containing details of the shipped report and dependencies
	 */
	ShippingResult shipReport(String reportUuid, String version, File destination);
	
	/**
	 * Ship multiple reports in a single distribution package. All reports and their combined
	 * dependencies are exported to the destination directory.
	 * 
	 * @param reportUuids List of report UUIDs to ship
	 * @param version Version string for the distribution package
	 * @param destination Destination directory for the exported files
	 * @return ShippingResult containing aggregated details of all shipped reports
	 */
	ShippingResult shipBatch(List<String> reportUuids, String version, File destination);
	
	/**
	 * Export a single entity of a specific type to a file. Used for individual entity exports
	 * (categories, themes, indicators, etc.)
	 * 
	 * @param entityType Type of entity to export (e.g., "category", "indicator", "theme")
	 * @param entityUuid UUID of the entity to export
	 * @param destination Destination directory for the exported file
	 * @return File containing the exported entity
	 */
	File exportEntity(String entityType, String entityUuid, File destination);
	
	/**
	 * Get the default configuration directory for shipping. Returns the OpenMRS application data
	 * directory's configuration folder.
	 * 
	 * @return Default shipping destination directory
	 */
	File getDefaultShippingDirectory();
}
