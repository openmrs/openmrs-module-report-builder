package org.openmrs.module.reportbuilder.web.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.openmrs.api.context.Context;
import org.openmrs.module.reporting.report.definition.ReportDefinition;
import org.openmrs.module.reportbuilder.api.ReportBuilderService;
import org.openmrs.module.reportbuilder.model.ReportCategory;
import org.openmrs.module.reportbuilder.model.ReportLibrary;
import org.openmrs.module.reportbuilder.web.controller.dto.ReportBuilderReportCompileResult;
import org.openmrs.module.webservices.rest.web.RequestContext;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.annotation.Resource;
import org.openmrs.module.webservices.rest.web.representation.DefaultRepresentation;
import org.openmrs.module.webservices.rest.web.representation.FullRepresentation;
import org.openmrs.module.webservices.rest.web.representation.Representation;
import org.openmrs.module.webservices.rest.web.resource.api.PageableResult;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingCrudResource;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingResourceDescription;
import org.openmrs.module.webservices.rest.web.response.ResponseException;

@Resource(name = RestConstants.VERSION_1 + "/reportbuilder/reportcompile", supportedClass = ReportBuilderReportCompileResult.class, supportedOpenmrsVersions = { "1.8 - 9.0.*" })
public class ReportBuilderReportCompileResource extends DelegatingCrudResource<ReportBuilderReportCompileResult> {
	
	@Override
	public ReportBuilderReportCompileResult newDelegate() {
		return new ReportBuilderReportCompileResult();
	}
	
	/**
	 * POST /ws/rest/v1/reportbuilder/reportcompile Request body: { "reportUuid": "...", "category":
	 * "category-uuid" }
	 */
	@Override
	public ReportBuilderReportCompileResult save(ReportBuilderReportCompileResult delegate) {
		if (delegate == null || delegate.getReportUuid() == null || delegate.getReportUuid().trim().isEmpty()) {
			throw new IllegalArgumentException("reportUuid is required");
		}
		
		ReportBuilderService ReportBuilderService = Context.getService(ReportBuilderService.class);
		ReportBuilderService.CompiledReportArtifacts result = ReportBuilderService.compileReport(delegate.getReportUuid());
		
		ReportDefinition rd = result.getReportDefinition();
		
		ReportBuilderReportCompileResult out = new ReportBuilderReportCompileResult();
		out.setReportUuid(result.getReportBuilderReport() != null ? result.getReportBuilderReport().getUuid() : delegate
		        .getReportUuid());
		out.setReportDefinitionUuid(rd != null ? rd.getUuid() : null);
		out.setReportDefinitionName(rd != null ? rd.getName() : null);
		out.setReportDesignPath(result.getReportDesignFile() != null ? result.getReportDesignFile().getAbsolutePath() : null);
		out.setCompiled(Boolean.TRUE);
		// Include compiled config (with parameters) for frontend theme config creation
		out.setCompiledJson(result.getCompiledJson());
		
		// If category is provided, automatically add to report library using the category UUID
		if (delegate.getCategory() != null && !delegate.getCategory().trim().isEmpty() && rd != null) {
			try {
				// Frontend sends category UUID - look up by UUID (not name)
				String categoryUuid = delegate.getCategory().trim();
				ReportCategory category = ReportBuilderService.getReportCategoryByUuid(categoryUuid);
				
				if (category == null) {
					// Only log warning if category not found - don't create a new one
					// The category should already exist in the system
					System.err.println("Category not found with UUID: " + categoryUuid);
					out.setAddedToLibrary(Boolean.FALSE);
					return out;
				}
				
				// Check if report library entry already exists for this report definition
				ReportLibrary existingEntry = null;
				for (ReportLibrary rl : ReportBuilderService.getReportLibraries(null, false, 0, null)) {
					if (rd.getUuid().equals(rl.getReportDefinitionUuid())) {
						existingEntry = rl;
						break;
					}
				}
				
				if (existingEntry != null) {
					// Update existing entry with latest report metadata
					existingEntry.setCategory(category);
					existingEntry.setName(rd.getName());
					existingEntry.setDescription(rd.getDescription());
					existingEntry.setReportDefinitionUuid(rd.getUuid());
					existingEntry.setReportBuilderReportUuid(result.getReportBuilderReport() != null ? result
					        .getReportBuilderReport().getUuid() : null);
					if (result.getReportBuilderReport() != null) {
						existingEntry.setReportType(result.getReportBuilderReport().getReportType());
					}
					// Ensure sourceType remains BUILDER for compiled reports
					existingEntry.setSourceType(ReportLibrary.ReportSourceType.BUILDER);
					// Update parameters in metaJson for frontend UI rendering
					if (result.getCompiledJson() != null) {
						existingEntry.setMetaJson(extractParametersMetaJson(result.getCompiledJson()));
					}
					ReportBuilderService.saveReportLibrary(existingEntry);
					out.setAddedToLibrary(Boolean.TRUE);
					out.setReportLibraryUuid(existingEntry.getUuid());
				} else {
					// Create new report library entry
					ReportLibrary reportLibrary = new ReportLibrary();
					reportLibrary.setReportDefinitionUuid(rd.getUuid());
					reportLibrary.setName(rd.getName());
					reportLibrary.setDescription(rd.getDescription());
					reportLibrary.setCategory(category);
					reportLibrary.setSourceType(ReportLibrary.ReportSourceType.BUILDER);
					reportLibrary.setReportBuilderReportUuid(result.getReportBuilderReport() != null ? result
					        .getReportBuilderReport().getUuid() : null);
					reportLibrary.setReportType(result.getReportBuilderReport() != null ? result.getReportBuilderReport()
					        .getReportType()
					        : org.openmrs.module.reportbuilder.model.ReportBuilderReport.ReportType.AGGREGATE);
					reportLibrary.setMigrated(Boolean.FALSE);
					
					// Store parameters in metaJson for frontend UI rendering
					if (result.getCompiledJson() != null) {
						reportLibrary.setMetaJson(extractParametersMetaJson(result.getCompiledJson()));
					}
					
					ReportLibrary saved = ReportBuilderService.saveReportLibrary(reportLibrary);
					out.setAddedToLibrary(Boolean.TRUE);
					out.setReportLibraryUuid(saved.getUuid());
				}
			}
			catch (Exception e) {
				// Log error but don't fail the compilation
				out.setAddedToLibrary(Boolean.FALSE);
			}
		}
		
		return out;
	}
	
	@Override
	public ReportBuilderReportCompileResult getByUniqueId(String uniqueId) {
		return null;
	}
	
	@Override
	protected void delete(ReportBuilderReportCompileResult delegate, String reason, RequestContext context)
	        throws ResponseException {
		throw new UnsupportedOperationException("Delete is not supported for reportcompile");
	}
	
	@Override
	public void purge(ReportBuilderReportCompileResult delegate, RequestContext context) throws ResponseException {
		throw new UnsupportedOperationException("Purge is not supported for reportcompile");
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
		
		if (rep instanceof DefaultRepresentation || rep instanceof FullRepresentation) {
			d.addProperty("reportUuid");
			d.addProperty("reportDefinitionUuid");
			d.addProperty("reportDefinitionName");
			d.addProperty("reportDesignPath");
			d.addProperty("compiled");
			d.addProperty("compiledJson");
			d.addProperty("addedToLibrary");
			d.addProperty("reportLibraryUuid");
		}
		
		return d;
	}
	
	@Override
	public DelegatingResourceDescription getCreatableProperties() {
		DelegatingResourceDescription d = new DelegatingResourceDescription();
		d.addRequiredProperty("reportUuid");
		d.addRequiredProperty("category"); // Required: for adding to report library
		return d;
	}
	
	@Override
	public DelegatingResourceDescription getUpdatableProperties() {
		return null;
	}
	
	/**
	 * Extracts only the parameters array from the compiled report JSON and returns it as a minimal
	 * metaJson object { "parameters": [...] }. This prevents the full report definition from being
	 * stored in metaJson, which should only contain metadata.
	 */
	private String extractParametersMetaJson(String compiledJson) {
		if (compiledJson == null || compiledJson.trim().isEmpty()) {
			return "{}";
		}
		
		try {
			ObjectMapper mapper = new ObjectMapper();
			JsonNode rootNode = mapper.readTree(compiledJson);
			JsonNode parametersNode = rootNode.path("parameters");
			
			// Create a minimal metaJson with only parameters
			ObjectNode metaJsonNode = mapper.createObjectNode();
			if (parametersNode != null && parametersNode.isArray() && parametersNode.size() > 0) {
				metaJsonNode.set("parameters", parametersNode);
			}
			
			return mapper.writeValueAsString(metaJsonNode);
		}
		catch (Exception e) {
			// If parsing fails, return empty object
			return "{}";
		}
	}
}
