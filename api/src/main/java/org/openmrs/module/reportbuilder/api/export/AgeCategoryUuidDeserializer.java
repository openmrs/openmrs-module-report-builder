/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.reportbuilder.api.export;

import java.io.IOException;

import org.openmrs.api.context.Context;
import org.openmrs.module.reportbuilder.api.ReportBuilderService;
import org.openmrs.module.reportbuilder.model.ReportBuilderAgeCategory;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

/**
 * Deserializer for ReportBuilderAgeCategory that converts UUID string back to the full object.
 */
public class AgeCategoryUuidDeserializer extends JsonDeserializer<ReportBuilderAgeCategory> {
	
	@Override
	public ReportBuilderAgeCategory deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
		String uuid = p.getValueAsString();
		if (uuid == null || uuid.trim().isEmpty() || uuid.equals("null")) {
			return null;
		}
		
		try {
			ReportBuilderService service = Context.getService(ReportBuilderService.class);
			return service.getAgeCategoryByUuid(uuid);
		}
		catch (Exception e) {
			// If category not found, return null (will be handled by import logic)
			return null;
		}
	}
}
