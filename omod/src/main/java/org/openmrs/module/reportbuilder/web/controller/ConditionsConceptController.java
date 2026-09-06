package org.openmrs.module.reportbuilder.web.controller;

import org.openmrs.Concept;
import org.openmrs.api.APIAuthenticationException;
import org.openmrs.api.context.Context;
import org.openmrs.module.reportbuilder.security.ReportBuilderPrivileges;
import org.openmrs.api.ConceptService;
import org.openmrs.module.reportbuilder.web.resources.mapper.ConceptMapper;
import org.openmrs.module.webservices.rest.web.RequestContext;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * REST controller for condition concepts Replaces legacy ugandaemr-reports
 * ConditionsConceptsRestController
 */
@Controller
@RequestMapping(value = "/rest/" + RestConstants.VERSION_1 + ConditionsConceptController.REPORTBUILDER
        + ConditionsConceptController.CONCEPTS_CONDITIONS)
public class ConditionsConceptController {
	
	public static final String REPORTBUILDER = "/reportbuilder";
	
	public static final String CONCEPTS_CONDITIONS = "/concepts/conditions";
	
	@ExceptionHandler(APIAuthenticationException.class)
    @RequestMapping(method = RequestMethod.GET)
    @ResponseBody
    public Object get(HttpServletRequest request, RequestContext context) {
        Context.requirePrivilege("Task: reportbuilder.report.view");
        try {
            List<ConceptMapper> conceptMapperList = new ArrayList<>();

            // Get condition concepts - these are concepts with specific names or UUIDs
            // For now, returning a hardcoded list as per legacy implementation
            ConceptService conceptService = Context.getConceptService();

            // Common condition concept UUIDs (these would typically be configured)
            String[] conditionConceptUuids = {
                "a8a0f076-1e66-4028-92c4-4b3cd8f58d3c", // HIV Status
                "90003" // Positive (example)
            };

            List<Concept> concepts = new ArrayList<>();
            for (String uuid : conditionConceptUuids) {
                try {
                    Concept c = conceptService.getConceptByUuid(uuid);
                    if (c != null) {
                        concepts.add(c);
                    }
                } catch (Exception e) {
                    // Concept not found, skip
                }
            }

            if (!concepts.isEmpty()) {
                conceptMapperList = convertConcepts(concepts, "Condition");
            }

            return new ResponseEntity<Object>(conceptMapperList, HttpStatus.OK);

        } catch (Exception ex) {
            return new ResponseEntity<String>(ex.getMessage() + Arrays.toString(ex.getStackTrace()),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
	
	/**
	 * Convert concepts to ConceptMapper list
	 */
	private List<ConceptMapper> convertConcepts(List<Concept> concepts, String type) {
		List<ConceptMapper> mappers = new ArrayList<ConceptMapper>();
		for (Concept c : concepts) {
			ConceptMapper mapper = new ConceptMapper();
			mapper.setConceptName(c.getName().getName());
			mapper.setUuid(c.getUuid());
			mapper.setConceptId(c.getId().toString());
			mapper.setType(type);
			mappers.add(mapper);
		}
		return mappers;
	}
}
