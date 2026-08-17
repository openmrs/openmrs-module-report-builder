/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark of the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.reportbuilder.web.resource;

import org.openmrs.api.context.Context;
import org.openmrs.module.reportbuilder.api.ReportBuilderService;
import org.openmrs.module.reportbuilder.model.ETLMonitor;
import org.openmrs.module.webservices.rest.web.RequestContext;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.annotation.Resource;
import org.openmrs.module.webservices.rest.web.representation.DefaultRepresentation;
import org.openmrs.module.webservices.rest.web.representation.FullRepresentation;
import org.openmrs.module.webservices.rest.web.representation.Representation;
import org.openmrs.module.webservices.rest.web.resource.api.PageableResult;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingCrudResource;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingResourceDescription;
import org.openmrs.module.webservices.rest.web.resource.impl.AlreadyPaged;
import org.openmrs.module.webservices.rest.web.resource.impl.NeedsPaging;
import org.openmrs.module.webservices.rest.web.response.ResponseException;

import java.util.List;

/**
 * REST resource for ETL Monitor CRUD operations. Provides endpoints for managing ETL health monitor
 * configurations.
 */
@Resource(name = RestConstants.VERSION_1 + "/reportbuilder/etl-monitor", supportedClass = ETLMonitor.class, supportedOpenmrsVersions = { "1.8 - 9.0.*" })
public class ETLMonitorResource extends DelegatingCrudResource<ETLMonitor> {
	
	private ReportBuilderService service() {
		return Context.getService(ReportBuilderService.class);
	}
	
	@Override
	public ETLMonitor newDelegate() {
		return new ETLMonitor();
	}
	
	@Override
	public ETLMonitor save(ETLMonitor delegate) {
		return service().saveETLMonitor(delegate);
	}
	
	@Override
	public ETLMonitor getByUniqueId(String uuid) {
		return service().getETLMonitorByUuid(uuid);
	}
	
	@Override
	protected PageableResult doGetAll(RequestContext context) throws ResponseException {
		String q = trimToNull(context.getParameter("q"));
		Boolean includeRetired = parseBooleanOrNull(context.getParameter("includeRetired"));
		String category = trimToNull(context.getParameter("category"));
		Boolean activeOnly = parseBooleanOrNull(context.getParameter("activeOnly"));
		
		// Default to not including retired unless explicitly requested
		boolean includeRetiredFinal = (includeRetired != null) ? includeRetired : false;
		
		List<ETLMonitor> results;
		
		if (category != null) {
			// Filter by category
			results = service().getETLMonitorsByCategory(category, includeRetiredFinal);
		} else if (activeOnly != null && activeOnly) {
			// Get only active monitors
			results = service().getActiveETLMonitors();
		} else if (q != null) {
			// Search with query
			results = service().getETLMonitors(q, includeRetiredFinal, context.getStartIndex(), context.getLimit());
			return new NeedsPaging<ETLMonitor>(results, context);
		} else {
			// Get all with pagination
			results = service().getETLMonitors(null, includeRetiredFinal, context.getStartIndex(), context.getLimit());
			return new NeedsPaging<ETLMonitor>(results, context);
		}
		
		// For category and activeOnly queries, return as already paged
		return new AlreadyPaged<ETLMonitor>(context, results, false);
	}
	
	@Override
	protected PageableResult doSearch(RequestContext context) throws ResponseException {
		String q = trimToNull(context.getParameter("q"));
		Boolean includeRetired = parseBooleanOrNull(context.getParameter("includeRetired"));
		
		boolean includeRetiredFinal = (includeRetired != null) ? includeRetired : false;
		
		List<ETLMonitor> results = service().getETLMonitors(q, includeRetiredFinal, context.getStartIndex(),
		    context.getLimit());
		
		return new NeedsPaging<ETLMonitor>(results, context);
	}
	
	@Override
	protected void delete(ETLMonitor delegate, String reason, RequestContext context) throws ResponseException {
		if (reason == null || reason.trim().isEmpty()) {
			reason = "Retired via REST API";
		}
		service().retireETLMonitor(delegate, reason);
	}
	
	@Override
	public void purge(ETLMonitor delegate, RequestContext context) throws ResponseException {
		service().purgeETLMonitor(delegate);
	}
	
	@Override
	public DelegatingResourceDescription getRepresentationDescription(Representation rep) {
		DelegatingResourceDescription description = new DelegatingResourceDescription();
		
		if (rep instanceof DefaultRepresentation) {
			description.addProperty("uuid");
			description.addProperty("name");
			description.addProperty("description");
			description.addProperty("code");
			description.addProperty("monitorType");
			description.addProperty("active");
			description.addProperty("category");
			description.addProperty("sortOrder");
			description.addProperty("retired");
			description.addSelfLink();
			description.addLink("full", ".?v=" + RestConstants.REPRESENTATION_FULL);
			return description;
		}
		
		if (rep instanceof FullRepresentation) {
			description.addProperty("uuid");
			description.addProperty("name");
			description.addProperty("description");
			description.addProperty("code");
			description.addProperty("monitorType");
			description.addProperty("configJson");
			description.addProperty("displayConfigJson");
			description.addProperty("refreshInterval");
			description.addProperty("timeout");
			description.addProperty("active");
			description.addProperty("category");
			description.addProperty("sortOrder");
			description.addProperty("retired");
			description.addProperty("dateCreated");
			description.addProperty("dateChanged");
			description.addSelfLink();
			return description;
		}
		
		return null;
	}
	
	@Override
	public DelegatingResourceDescription getCreatableProperties() {
		DelegatingResourceDescription description = new DelegatingResourceDescription();
		description.addRequiredProperty("name");
		description.addProperty("description");
		description.addProperty("code");
		description.addProperty("monitorType");
		description.addProperty("configJson");
		description.addProperty("displayConfigJson");
		description.addProperty("refreshInterval");
		description.addProperty("timeout");
		description.addProperty("active");
		description.addProperty("category");
		description.addProperty("sortOrder");
		return description;
	}
	
	// Helper methods
	
	/**
	 * Trim a string to null if empty
	 */
	private String trimToNull(String value) {
		if (value == null || value.trim().isEmpty()) {
			return null;
		}
		return value.trim();
	}
	
	/**
	 * Parse a boolean string to Boolean, or null if not a valid boolean
	 */
	private Boolean parseBooleanOrNull(String value) {
		if (value == null || value.trim().isEmpty()) {
			return null;
		}
		value = value.trim().toLowerCase();
		if ("true".equals(value)) {
			return true;
		}
		if ("false".equals(value)) {
			return false;
		}
		return null;
	}
}
