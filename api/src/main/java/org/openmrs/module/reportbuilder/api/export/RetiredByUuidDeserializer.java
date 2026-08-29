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
import org.openmrs.api.UserService;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

/**
 * Deserializer for retiredBy field that converts UUID string back to User object.
 */
public class RetiredByUuidDeserializer extends JsonDeserializer<org.openmrs.User> {
	
	@Override
	public org.openmrs.User deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
		String uuid = p.getValueAsString();
		if (uuid == null || uuid.trim().isEmpty() || uuid.equals("null")) {
			return null;
		}
		
		try {
			UserService userService = Context.getService(UserService.class);
			return userService.getUserByUuid(uuid);
		}
		catch (Exception e) {
			// If user not found, return null (will be handled by import logic)
			return null;
		}
	}
}
