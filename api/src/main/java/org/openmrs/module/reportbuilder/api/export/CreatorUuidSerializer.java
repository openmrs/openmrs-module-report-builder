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
import java.lang.reflect.Method;

import org.openmrs.User;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

/**
 * Serializer for creator field that outputs just the UUID instead of the full User object. This
 * prevents circular references while preserving audit information.
 */
public class CreatorUuidSerializer extends JsonSerializer<Object> {
	
	@Override
	public void serialize(Object value, JsonGenerator gen, SerializerProvider provider) throws IOException {
		if (value == null) {
			// Default to system user ID 1 when null
			gen.writeString("1");
			return;
		}
		
		// Handle both User objects and direct UUID strings
		if (value instanceof String) {
			gen.writeString((String) value);
		} else if (value instanceof User) {
			User user = (User) value;
			// Try to get UUID first, fall back to username if needed
			String uuid = getUuid(user);
			if (uuid != null) {
				gen.writeString(uuid);
			} else {
				// Fallback to username if UUID is not available
				String username = user.getUsername();
				if (username != null) {
					gen.writeString(username);
				} else {
					gen.writeString("1");
				}
			}
		} else {
			// Try to get UUID via reflection for any object type
			String uuid = getUuidViaReflection(value);
			if (uuid != null) {
				gen.writeString(uuid);
			} else {
				// Fallback to toString
				gen.writeString(value.toString());
			}
		}
	}
	
	private String getUuid(User user) {
		try {
			return user.getUuid();
		}
		catch (Exception e) {
			return null;
		}
	}
	
	private String getUuidViaReflection(Object obj) {
		try {
			Method getUuidMethod = obj.getClass().getMethod("getUuid");
			Object uuidObj = getUuidMethod.invoke(obj);
			return uuidObj != null ? uuidObj.toString() : null;
		}
		catch (Exception e) {
			return null;
		}
	}
}
