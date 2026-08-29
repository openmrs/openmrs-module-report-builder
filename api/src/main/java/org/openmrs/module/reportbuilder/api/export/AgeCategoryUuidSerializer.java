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

import org.openmrs.module.reportbuilder.model.ReportBuilderAgeCategory;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

/**
 * Serializer for ReportBuilderAgeCategory that outputs just the UUID instead of the full object.
 * This prevents circular reference issues when exporting age categories with their groups.
 */
public class AgeCategoryUuidSerializer extends JsonSerializer<ReportBuilderAgeCategory> {
	
	@Override
	public void serialize(ReportBuilderAgeCategory value, JsonGenerator gen, SerializerProvider provider) throws IOException {
		if (value == null) {
			gen.writeNull();
			return;
		}
		
		// Serialize as just the UUID string
		String uuid = value.getUuid();
		if (uuid != null) {
			gen.writeString(uuid);
		} else {
			gen.writeNull();
		}
	}
}
