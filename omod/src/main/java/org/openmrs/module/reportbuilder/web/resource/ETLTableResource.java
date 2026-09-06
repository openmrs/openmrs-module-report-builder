package org.openmrs.module.reportbuilder.web.resource;

import org.openmrs.api.context.Context;
import org.openmrs.module.reportbuilder.api.ReportBuilderService;
import org.openmrs.module.webservices.rest.web.RequestContext;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.annotation.Resource;
import org.openmrs.module.webservices.rest.web.representation.Representation;
import org.openmrs.module.webservices.rest.web.resource.api.PageableResult;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingCrudResource;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingResourceDescription;
import org.openmrs.module.webservices.rest.web.resource.impl.NeedsPaging;
import org.openmrs.module.webservices.rest.web.response.ResponseException;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Resource(name = RestConstants.VERSION_1 + "/etltable", supportedClass = ETLTableResource.ETLTable.class, supportedOpenmrsVersions = {
        "2.*", "3.*" })
public class ETLTableResource extends DelegatingCrudResource<ETLTableResource.ETLTable> {
	
	public static class ETLTable {
		
		private String uuid; // synthetic, not persisted
		
		private String name;
		
		private Long rows; // approximate INFORMATION_SCHEMA count; null for views
		
		private Date updateTime; // last modification time; null when unknown
		
		private String tableType; // "BASE TABLE" or "VIEW"
		
		public ETLTable() {
		}
		
		public ETLTable(String name) {
			this(name, null, null, null);
		}
		
		public ETLTable(String name, Long rows, Date updateTime, String tableType) {
			this.name = name;
			this.rows = rows;
			this.updateTime = updateTime;
			this.tableType = tableType;
			// stable-enough synthetic uuid derived from name
			this.uuid = UUID.nameUUIDFromBytes(("etl-table:" + name).getBytes()).toString();
		}
		
		public String getUuid() {
			return uuid;
		}
		
		public void setUuid(String uuid) {
			this.uuid = uuid;
		}
		
		public String getName() {
			return name;
		}
		
		public void setName(String name) {
			this.name = name;
		}
		
		public Long getRows() {
			return rows;
		}
		
		public void setRows(Long rows) {
			this.rows = rows;
		}
		
		public Date getUpdateTime() {
			return updateTime;
		}
		
		public void setUpdateTime(Date updateTime) {
			this.updateTime = updateTime;
		}
		
		public String getTableType() {
			return tableType;
		}
		
		public void setTableType(String tableType) {
			this.tableType = tableType;
		}
	}
	
	private ReportBuilderService service() {
		return Context.getService(ReportBuilderService.class);
	}
	
	/** Tolerates alias-case differences in the INFORMATION_SCHEMA result maps */
	private static Object firstOf(Map row, String... keys) {
		for (String key : keys) {
			Object value = row.get(key);
			if (value != null) {
				return value;
			}
		}
		// case-insensitive fallback
		for (Object keyObj : row.keySet()) {
			String key = String.valueOf(keyObj);
			for (String wanted : keys) {
				if (key.equalsIgnoreCase(wanted)) {
					return row.get(keyObj);
				}
			}
		}
		return null;
	}
	
	private static String toName(Map row) {
		Object value = firstOf(row, "tableName", "TABLE_NAME");
		return value == null ? null : String.valueOf(value);
	}
	
	private static Long toRowCount(Map row) {
		Object value = firstOf(row, "tableRows", "TABLE_ROWS");
		if (value instanceof Number) {
			return ((Number) value).longValue();
		}
		return null;
	}
	
	private static Date toUpdateTime(Map row) {
		Object value = firstOf(row, "updateTime", "UPDATE_TIME");
		if (value instanceof Date) {
			return (Date) value;
		}
		return null;
	}
	
	private static String toTableType(Map row) {
		Object value = firstOf(row, "tableType", "TABLE_TYPE");
		return value == null ? null : String.valueOf(value);
	}
	
	@Override
	public ETLTable newDelegate() {
		return new ETLTable();
	}
	
	@Override
	public ETLTable save(ETLTable delegate) {
		// Read-only resource
		throw new UnsupportedOperationException("etltable is read-only");
	}
	
	@Override
	public ETLTable getByUniqueId(String uniqueId) {
		// optional: not needed for UI; return null for now
		return null;
	}
	
	@Override
	protected void delete(ETLTable delegate, String reason, RequestContext context) throws ResponseException {
		throw new UnsupportedOperationException("etltable is read-only");
	}
	
	@Override
	public void purge(ETLTable delegate, RequestContext context) throws ResponseException {
		throw new UnsupportedOperationException("etltable is read-only");
	}
	
	@Override
	public PageableResult doGetAll(RequestContext context) throws ResponseException {
		List<Map> tables = service().getETLTables(); // from DAO query INFORMATION_SCHEMA

		List<ETLTable> rows = new ArrayList<>();
		for (Map table : tables) {
			rows.add(new ETLTable(toName(table), toRowCount(table), toUpdateTime(table), toTableType(table)));
		}

		// NeedsPaging will apply startIndex & limit automatically
		return new NeedsPaging<>(rows, context);
	}
	
	@Override
	protected PageableResult doSearch(RequestContext context) throws ResponseException {
		// optional: allow q filter on table name
		String q = context.getParameter("q");
		List<Map> tables = service().getETLTables();

		List<ETLTable> rows = new ArrayList<>();
		for (Map table : tables) {
			String name = toName(table);
			if (name == null) {
				continue;
			}
			if (q == null || q.trim().isEmpty() || name.toLowerCase().contains(q.toLowerCase())) {
				rows.add(new ETLTable(name, toRowCount(table), toUpdateTime(table), toTableType(table)));
			}
		}
		return new NeedsPaging<>(rows, context);
	}
	
	@Override
	public DelegatingResourceDescription getRepresentationDescription(Representation rep) {
		DelegatingResourceDescription d = new DelegatingResourceDescription();
		d.addProperty("uuid");
		d.addProperty("name");
		d.addProperty("rows");
		d.addProperty("updateTime");
		d.addProperty("tableType");
		return d;
	}
	
	@Override
	public DelegatingResourceDescription getCreatableProperties() {
		return null; // read-only
	}
	
	@Override
	public DelegatingResourceDescription getUpdatableProperties() {
		return null; // read-only
	}
	
	public String getDisplayString(ETLTable obj) {
		return obj.getName();
	}
}
