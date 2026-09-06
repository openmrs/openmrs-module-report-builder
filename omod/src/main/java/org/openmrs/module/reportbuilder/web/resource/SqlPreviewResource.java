package org.openmrs.module.reportbuilder.web.resource;

import java.util.Collections;
import java.util.Map;

import org.openmrs.api.context.Context;
import org.openmrs.module.reportbuilder.security.ReportBuilderPrivileges;
import org.openmrs.module.reportbuilder.api.ReportBuilderService;
import org.openmrs.module.reportbuilder.dto.SqlPreviewResult;
import org.openmrs.module.reportbuilder.web.controller.dto.SqlPreviewRequest;
import org.openmrs.module.webservices.rest.web.RequestContext;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.annotation.Resource;
import org.openmrs.module.webservices.rest.web.representation.Representation;
import org.openmrs.module.webservices.rest.web.resource.api.PageableResult;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingCrudResource;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingResourceDescription;
import org.openmrs.module.webservices.rest.web.response.ResponseException;

/**
 * Controller-less SQL preview endpoint using RESTWS resource pattern. POST
 * /ws/rest/v1/reportbuilder/sqlpreview Body: { sql, params, maxRows } Response: same object + {
 * columns, rows, rowCount, truncated }
 */
@Resource(name = RestConstants.VERSION_1 + "/reportbuilder/sqlpreview", supportedClass = SqlPreviewRequest.class, supportedOpenmrsVersions = {
        "2.*", "3.*" })
public class SqlPreviewResource extends DelegatingCrudResource<SqlPreviewRequest> {
	
	private ReportBuilderService service() {
		return Context.getService(ReportBuilderService.class);
	}
	
	@Override
	public SqlPreviewRequest newDelegate() {
		return new SqlPreviewRequest();
	}
	
	@Override
	public SqlPreviewRequest save(SqlPreviewRequest delegate) {
		Context.requirePrivilege("Task: reportbuilder.sql.execute");
		
		Map<String, Object> params = delegate.getParams() != null ? delegate.getParams() : Collections
		        .<String, Object> emptyMap();
		
		SqlPreviewResult r = service().previewSql(decodeHtmlEntities(delegate.getSql()), params, delegate.getMaxRows());
		
		delegate.setColumns(r.getColumns());
		delegate.setRows(r.getRows());
		delegate.setRowCount(r.getRowCount());
		delegate.setTruncated(r.isTruncated());
		
		return delegate;
	}
	
	@Override
	public SqlPreviewRequest getByUniqueId(String uniqueId) {
		return null;
	}
	
	@Override
	protected void delete(SqlPreviewRequest delegate, String reason, RequestContext context) throws ResponseException {
		throw new UnsupportedOperationException("sqlpreview is not deletable");
	}
	
	@Override
	public void purge(SqlPreviewRequest delegate, RequestContext context) throws ResponseException {
		throw new UnsupportedOperationException("sqlpreview is not purgeable");
	}
	
	@Override
	public PageableResult doGetAll(RequestContext context) throws ResponseException {
		return null;
	}
	
	@Override
	protected PageableResult doSearch(RequestContext context) throws ResponseException {
		return null;
	}
	
	@Override
	public DelegatingResourceDescription getRepresentationDescription(Representation rep) {
		DelegatingResourceDescription d = new DelegatingResourceDescription();
		d.addProperty("sql");
		d.addProperty("params");
		d.addProperty("maxRows");
		d.addProperty("columns");
		d.addProperty("rows");
		d.addProperty("rowCount");
		d.addProperty("truncated");
		return d;
	}
	
	@Override
	public DelegatingResourceDescription getCreatableProperties() {
		DelegatingResourceDescription d = new DelegatingResourceDescription();
		d.addRequiredProperty("sql");
		d.addProperty("params");
		d.addProperty("maxRows");
		return d;
	}
	
	@Override
	public DelegatingResourceDescription getUpdatableProperties() {
		return null;
	}
	
	private static String decodeHtmlEntities(String s) {
		if (s == null) {
			return null;
		}
		
		String out = s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
		        .replace("&#39;", "'");
		
		out = out.replace("&gt;=", ">=").replace("&lt;=", "<=");
		out = out.replace("&gte;", ">=").replace("&ge;", ">=");
		out = out.replace("&lte;", "<=").replace("&le;", "<=");
		
		return out;
	}
}
