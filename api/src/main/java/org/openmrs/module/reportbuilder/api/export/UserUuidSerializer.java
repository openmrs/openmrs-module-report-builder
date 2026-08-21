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

import org.openmrs.User;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

/**
 * Serializer for User objects that outputs just the UUID instead of the full object. This prevents
 * circular references (User → Person → names → PersonName → creator → User).
 */
public class UserUuidSerializer extends JsonSerializer<User> {
	
	@Override
	public void serialize(User user, JsonGenerator gen, SerializerProvider provider) throws IOException {
		if (user == null) {
			gen.writeNull();
			return;
		}
		
		// Output just the UUID
		String uuid = user.getUuid();
		if (uuid != null) {
			gen.writeString(uuid);
		} else {
			// Fallback to username if UUID is not available
			String username = user.getUsername();
			if (username != null) {
				gen.writeString(username);
			} else {
				gen.writeNull();
			}
		}
	}
}
