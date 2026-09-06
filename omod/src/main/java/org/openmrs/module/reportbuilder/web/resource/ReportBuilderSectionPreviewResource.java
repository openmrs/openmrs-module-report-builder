package org.openmrs.module.reportbuilder.web.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openmrs.api.context.Context;
import org.openmrs.module.reportbuilder.security.ReportBuilderPrivileges;
import org.openmrs.module.reportbuilder.api.ReportBuilderService;
import org.openmrs.module.reportbuilder.dto.SqlPreviewResult;
import org.openmrs.module.reportbuilder.model.ReportBuilderSection;
import org.openmrs.module.reportbuilder.web.controller.dto.SectionPreviewRequest;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.RequestContext;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.annotation.Resource;
import org.openmrs.module.webservices.rest.web.representation.Representation;
import org.openmrs.module.webservices.rest.web.resource.api.PageableResult;
import org.openmrs.module.webservices.rest.web.resource.impl.AlreadyPaged;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingCrudResource;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingResourceDescription;
import org.openmrs.module.webservices.rest.web.response.ResourceDoesNotSupportOperationException;
import org.openmrs.module.webservices.rest.web.response.ResponseException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Resource(name = RestConstants.VERSION_1 + "/reportbuilder/sectionpreview", supportedClass = SectionPreviewRequest.class, supportedOpenmrsVersions = { "1.8 - 9.0.*" })
public class ReportBuilderSectionPreviewResource extends DelegatingCrudResource<SectionPreviewRequest> {
	
	private ReportBuilderService service() {
		return Context.getService(ReportBuilderService.class);
	}
	
	@Override
	public SectionPreviewRequest newDelegate() {
		return new SectionPreviewRequest();
	}
	
	@Override
	public SectionPreviewRequest save(SectionPreviewRequest delegate) {
		return delegate;
	}
	
	@Override
	public SectionPreviewRequest getByUniqueId(String uniqueId) {
		throw new ResourceDoesNotSupportOperationException();
	}
	
	@Override
	protected void delete(SectionPreviewRequest delegate, String reason, RequestContext context) throws ResponseException {
		throw new ResourceDoesNotSupportOperationException();
	}
	
	@Override
	public void purge(SectionPreviewRequest delegate, RequestContext context) throws ResponseException {
		throw new ResourceDoesNotSupportOperationException();
	}
	
	@Override
	protected PageableResult doGetAll(RequestContext context) {
		return new AlreadyPaged<SectionPreviewRequest>(context, Collections.<SectionPreviewRequest> emptyList(), false);
	}
	
	@Override
	protected PageableResult doSearch(RequestContext context) throws ResponseException {
		throw new ResourceDoesNotSupportOperationException();
	}
	
	@Override
	public DelegatingResourceDescription getRepresentationDescription(Representation rep) {
		return null;
	}
	
	@Override
	public DelegatingResourceDescription getCreatableProperties() {
		DelegatingResourceDescription d = new DelegatingResourceDescription();
		d.addRequiredProperty("sectionUuid");
		d.addProperty("startDate"); // Optional - if not provided, returns all data from earliest time
		d.addRequiredProperty("endDate");
		d.addProperty("indicatorUuid");
		d.addProperty("maxRows");
		d.addProperty("params");
		return d;
	}
	
	@Override
	public Object create(SimpleObject post, RequestContext context) throws ResponseException {
		Context.requirePrivilege("Task: reportbuilder.sql.execute");
		
		String sectionUuid = value(post, "sectionUuid");
		String indicatorUuid = value(post, "indicatorUuid");
		String startDate = value(post, "startDate");
		String endDate = value(post, "endDate");
		
		if (isBlank(sectionUuid)) {
			throw new IllegalArgumentException("sectionUuid is required");
		}
		if (isBlank(endDate)) {
			throw new IllegalArgumentException("endDate is required (YYYY-MM-DD)");
		}
		
		// If startDate is not provided, use a default early date to return all data "ever since"
		String effectiveStartDate = startDate;
		if (isBlank(startDate)) {
			effectiveStartDate = "1900-01-01"; // Use a very early date to represent "all time"
		}
		
		Map<String, Object> params = new HashMap<String, Object>();
		Object rawParams = post.get("params");
		if (rawParams instanceof Map) {
			Map<?, ?> inputParams = (Map<?, ?>) rawParams;
			for (Map.Entry<?, ?> entry : inputParams.entrySet()) {
				if (entry.getKey() != null) {
					params.put(String.valueOf(entry.getKey()), entry.getValue());
				}
			}
		}
		
		params.put("startDate", effectiveStartDate);
		params.put("endDate", endDate);
		
		Integer maxRows = null;
		if (post.get("maxRows") != null) {
			try {
				maxRows = Integer.valueOf(post.get("maxRows").toString());
			}
			catch (Exception ignored) {}
		}
		
		ReportBuilderSection section = service().getReportBuilderSectionByUuid(sectionUuid);
		if (section == null) {
			throw new IllegalStateException("No ReportBuilderSection found for uuid: " + sectionUuid);
		}
		
		String configJson = section.getConfigJson();
		if (isBlank(configJson)) {
			SimpleObject out = new SimpleObject();
			out.add("sectionUuid", sectionUuid);
			out.add("results", Collections.emptyList());
			return out;
		}
		
		if (!isBlank(indicatorUuid)) {
			String compiledSql;
			try {
				compiledSql = extractCompiledSql(configJson, indicatorUuid);
			}
			catch (Exception e) {
				throw new IllegalArgumentException("Failed to parse section configJson: " + e.getMessage());
			}
			
			if (isBlank(compiledSql)) {
				throw new IllegalArgumentException("Compiled SQL not found for indicator " + indicatorUuid);
			}
			
			SqlPreviewResult r = service().previewSql(decodeHtmlEntities(compiledSql), params, maxRows);
			
			SimpleObject single = new SimpleObject();
			single.add("indicatorUuid", indicatorUuid);
			single.add("columns", r.getColumns());
			single.add("rows", r.getRows());
			single.add("rowCount", r.getRowCount());
			single.add("truncated", r.isTruncated());
			single.add("error", null);
			
			SimpleObject out = new SimpleObject();
			out.add("sectionUuid", sectionUuid);
			out.add("results", Collections.singletonList(single));
			return out;
		}
		
		JsonNode root;
		JsonNode indicators;
		try {
			ObjectMapper mapper = new ObjectMapper();
			root = mapper.readTree(configJson);
			indicators = root.path("indicators");
		}
		catch (Exception e) {
			throw new IllegalArgumentException("Failed to parse section configJson: " + e.getMessage());
		}
		
		if (!indicators.isArray()) {
			SimpleObject out = new SimpleObject();
			out.add("sectionUuid", sectionUuid);
			out.add("results", Collections.emptyList());
			return out;
		}
		
		List<SimpleObject> results = new ArrayList<SimpleObject>();
		
		for (JsonNode item : indicators) {
			String id = item.path("indicatorUuid").asText(null);
			String kind = item.path("kind").asText(null);
			String name = item.path("name").asText(null);
			String code = item.path("code").asText(null);
			String compiled = item.path("sql").path("compiled").asText(null);
			
			SimpleObject one = new SimpleObject();
			one.add("indicatorUuid", id);
			one.add("kind", kind);
			one.add("name", name);
			one.add("code", code);
			
			if (isBlank(compiled)) {
				one.add("columns", Collections.emptyList());
				one.add("rows", Collections.emptyList());
				one.add("rowCount", 0);
				one.add("truncated", false);
				one.add("error", "Missing compiled SQL in section configJson");
				results.add(one);
				continue;
			}
			
			try {
				SqlPreviewResult r = service().previewSql(decodeHtmlEntities(compiled), params, maxRows);
				one.add("columns", r.getColumns());
				one.add("rows", r.getRows());
				one.add("rowCount", r.getRowCount());
				one.add("truncated", r.isTruncated());
				one.add("error", null);
			}
			catch (Exception ex) {
				one.add("columns", Collections.emptyList());
				one.add("rows", Collections.emptyList());
				one.add("rowCount", 0);
				one.add("truncated", false);
				one.add("error", ex.getMessage());
			}
			
			results.add(one);
		}
		
		SimpleObject out = new SimpleObject();
		out.add("sectionUuid", sectionUuid);
		out.add("results", results);
		return out;
	}
	
	private static String extractCompiledSql(String configJson, String indicatorUuid) throws Exception {
		ObjectMapper mapper = new ObjectMapper();
		JsonNode root = mapper.readTree(configJson);
		JsonNode indicators = root.path("indicators");
		if (!indicators.isArray()) {
			return null;
		}
		
		for (JsonNode item : indicators) {
			String id = item.path("indicatorUuid").asText(null);
			if (indicatorUuid.equals(id)) {
				return item.path("sql").path("compiled").asText(null);
			}
		}
		return null;
	}
	
	private static String decodeHtmlEntities(String s) {
		if (s == null) {
			return null;
		}
		
		// Handle double-encoded entities by repeatedly decoding until no more changes
		String out = s;
		String prev;
		int maxIterations = 5; // Prevent infinite loops
		int iterations = 0;
		
		do {
			prev = out;
			
			// IMPORTANT: Decode &amp; FIRST to handle double-encoded entities like &amp;lt; -> <
			// This prevents &amp;gt; from becoming &gt; instead of >
			out = out.replace("&amp;", "&");
			
			// Then decode other entities
			out = out.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
			        .replace("&#x27;", "'").replace("&#x60;", "`").replace("&#x3D;", "=").replace("&#x2F;", "/");
			
			iterations++;
		} while (!out.equals(prev) && iterations < maxIterations);
		
		// Handle combined entities (>=, <=) - these should already be decoded above
		// but just in case there are some variants:
		out = out.replace("&gte;", ">=").replace("&ge;", ">=").replace("&lte;", "<=").replace("&le;", "<=");
		
		// Handle SQL-style double escaping in VALUES clauses
		// Fix cases like (''F'') which should be ('F')
		// The problem is SQL values like (''F''), (''M'') in VALUES clause
		// We need to convert ''X'' to 'X' for any string X
		// Using a loop to handle strings of any length
		while (out.contains("''") && out.indexOf("''") != out.lastIndexOf("''")) {
			// Find pattern like ''something'' and replace with 'something'
			int first = out.indexOf("''");
			int second = out.indexOf("''", first + 2);
			if (second > first + 2) {
				// Found ''something'' pattern
				String content = out.substring(first + 2, second);
				// Replace only if content doesn't contain another '' (avoid nested)
				if (!content.contains("''")) {
					String replacement = "'" + content + "'";
					out = out.substring(0, first) + replacement + out.substring(second + 2);
				} else {
					// Content has nested '', skip this one
					break;
				}
			} else {
				// No valid pair found
				break;
			}
		}
		
		return out;
	}
	
	private static String value(SimpleObject post, String key) {
		return post.get(key) != null ? post.get(key).toString().trim() : null;
	}
	
	private static boolean isBlank(String s) {
		return s == null || s.trim().isEmpty();
	}
}
