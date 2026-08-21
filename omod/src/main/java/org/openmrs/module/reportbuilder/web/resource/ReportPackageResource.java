/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.reportbuilder.web.resource;

import org.openmrs.api.context.Context;
import org.openmrs.module.reportbuilder.api.ReportBuilderService;
import org.openmrs.module.reportbuilder.web.controller.dto.PackageInfo;
import org.openmrs.module.webservices.rest.web.RequestContext;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.annotation.Resource;
import org.openmrs.module.webservices.rest.web.resource.api.PageableResult;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingCrudResource;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingResourceDescription;
import org.openmrs.module.webservices.rest.web.resource.impl.AlreadyPaged;
import org.openmrs.module.webservices.rest.web.representation.DefaultRepresentation;
import org.openmrs.module.webservices.rest.web.representation.FullRepresentation;
import org.openmrs.module.webservices.rest.web.representation.Representation;
import org.openmrs.module.webservices.rest.web.response.ResponseException;

import java.util.Collections;
import java.util.List;

/**
 * REST resource for browsing report distribution packages. Provides endpoints to list exported
 * packages that can be imported into another OpenMRS instance.
 */
@Resource(name = RestConstants.VERSION_1 + "/reportbuilder/packages", supportedClass = PackageInfo.class, supportedOpenmrsVersions = { "1.8 - 9.0.*" })
public class ReportPackageResource extends DelegatingCrudResource<PackageInfo> {
	
	@Override
	public PackageInfo newDelegate() {
		return new PackageInfo();
	}
	
	@Override
	public PackageInfo save(PackageInfo delegate) {
		throw new UnsupportedOperationException("Packages cannot be created via REST");
	}
	
	@Override
	public PackageInfo getByUniqueId(String uniqueId) {
		throw new UnsupportedOperationException("Packages are not retrievable by ID");
	}
	
	@Override
	protected void delete(PackageInfo delegate, String reason, RequestContext context) throws ResponseException {
		throw new UnsupportedOperationException("Packages cannot be deleted via REST");
	}
	
	@Override
	public void purge(PackageInfo delegate, RequestContext context) throws ResponseException {
		throw new UnsupportedOperationException("Packages cannot be purged via REST");
	}
	
	private ReportBuilderService getService() {
		return Context.getService(ReportBuilderService.class);
	}
	
	@Override
	protected PageableResult doGetAll(RequestContext context) throws ResponseException {
		String q = trimToNull(context.getParameter("q"));
		String status = trimToNull(context.getParameter("status"));
		Integer startIndex = context.getStartIndex() != null ? context.getStartIndex() : 0;
		Integer limit = context.getLimit() != null ? context.getLimit() : 50;
		
		List<PackageInfo> results = getService().getAvailablePackages(q, status, startIndex, limit);
		long count = getService().getAvailablePackagesCount(q, status);
		
		return new AlreadyPaged<PackageInfo>(context, results, false, count);
	}
	
	@Override
	protected PageableResult doSearch(RequestContext context) throws ResponseException {
		// Search uses the same implementation as getAll
		return doGetAll(context);
	}
	
	@Override
	public DelegatingResourceDescription getRepresentationDescription(Representation rep) {
		DelegatingResourceDescription description = new DelegatingResourceDescription();
		
		if (rep instanceof DefaultRepresentation) {
			description.addProperty("name");
			description.addProperty("version");
			description.addProperty("description");
			description.addProperty("exportedAt");
			description.addProperty("exportedBy");
			description.addProperty("size");
			description.addProperty("status");
			description.addProperty("path");
			description.addSelfLink();
			return description;
		}
		
		if (rep instanceof FullRepresentation) {
			description.addProperty("name");
			description.addProperty("version");
			description.addProperty("description");
			description.addProperty("exportedAt");
			description.addProperty("exportedBy");
			description.addProperty("size");
			description.addProperty("status");
			description.addProperty("path");
			description.addProperty("dependencies");
			description.addSelfLink();
			return description;
		}
		
		return null;
	}
	
	@Override
	public DelegatingResourceDescription getCreatableProperties() {
		return new DelegatingResourceDescription();
	}
	
	/**
	 * Trim a string to null if empty
	 */
	private String trimToNull(String value) {
		if (value == null || value.trim().isEmpty()) {
			return null;
		}
		return value.trim();
	}
}
