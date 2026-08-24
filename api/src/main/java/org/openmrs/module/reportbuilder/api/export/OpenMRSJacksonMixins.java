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

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.openmrs.Person;
import org.openmrs.PersonName;
import org.openmrs.User;

/**
 * Jackson mixins to break circular references in OpenMRS entities during export. The issue:
 * BaseOpenmrsMetadata has creator/changedBy/retiredBy fields that reference User, which has person,
 * which has names, which has creator again, creating infinite loops. Solution: Use custom
 * serializers that output just the UUID/ID instead of the full object.
 */
public class OpenMRSJacksonMixins {
	
	/**
	 * Mixin for User to serialize as UUID instead of full object
	 */
	@JsonSerialize(using = UserUuidSerializer.class)
	@JsonDeserialize(using = UserUuidDeserializer.class)
	public static abstract class UserMixin {}
	
	/**
	 * Mixin for Person to break the Person.names circular reference
	 */
	public static abstract class PersonMixin {
		
		@com.fasterxml.jackson.annotation.JsonIgnore
		public abstract java.util.Set<?> getNames();
	}
	
	/**
	 * Mixin for PersonName to break the PersonName.creator circular reference
	 */
	public static abstract class PersonNameMixin {
		
		@com.fasterxml.jackson.annotation.JsonIgnore
		public abstract User getCreator();
		
		@com.fasterxml.jackson.annotation.JsonIgnore
		public abstract User getChangedBy();
	}
	
	/**
	 * Mixin for BaseOpenmrsMetadata to serialize creator/changedBy/retiredBy as UUIDs and ensure
	 * dateCreated/dateChanged/dateRetired/retireReason are exported for migration purposes. This
	 * preserves audit information while avoiding circular references.
	 */
	public static abstract class BaseOpenmrsMetadataMixin {
		
		@JsonSerialize(using = CreatorUuidSerializer.class)
		@JsonDeserialize(using = CreatorUuidDeserializer.class)
		public abstract Object getCreator();
		
		@JsonSerialize(using = ChangedByUuidSerializer.class)
		@JsonDeserialize(using = ChangedByUuidDeserializer.class)
		public abstract Object getChangedBy();
		
		// OpenMRS BaseOpenmrsMetadata uses "retired" terminology, not "voided"
		@JsonSerialize(using = RetiredByUuidSerializer.class)
		@JsonDeserialize(using = RetiredByUuidDeserializer.class)
		@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
		public abstract Object getRetiredBy();
		
		// Ensure date fields are included in export for migration purposes
		@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
		public abstract java.util.Date getDateCreated();
		
		@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
		public abstract java.util.Date getDateChanged();
		
		@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
		public abstract java.util.Date getDateRetired();
		
		@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
		public abstract String getRetireReason();
	}
}
