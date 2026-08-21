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

import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.User;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

/**
 * Deserializer for creator field during import. Converts UUID/ID string back to User object,
 * defaulting to system user (ID 1) if not specified.
 */
public class CreatorUuidDeserializer extends JsonDeserializer<Object> {
	
	@Override
	public Object deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
		String value = p.getValueAsString();
		
		if (value == null || value.trim().isEmpty()) {
			// Default to system user ID 1
			return getUserById("1");
		}
		
		// Try to find user by UUID first
		User user = getUserByUuid(value);
		if (user != null) {
			return user;
		}
		
		// Try to find user by ID
		user = getUserById(value);
		if (user != null) {
			return user;
		}
		
		// Default to system user ID 1 if not found
		return getUserById("1");
	}
	
	private User getUserByUuid(String uuid) {
		try {
			return Context.getUserService().getUserByUuid(uuid);
		}
		catch (Exception e) {
			return null;
		}
	}
	
	private User getUserById(String userId) {
		try {
			Integer id = Integer.parseInt(userId);
			return Context.getUserService().getUser(id);
		}
		catch (Exception e) {
			// If parsing fails or user not found, return default system user
			try {
				return Context.getUserService().getUser(1);
			}
			catch (Exception ex) {
				throw new APIException("Could not find default system user with ID 1");
			}
		}
	}
}
