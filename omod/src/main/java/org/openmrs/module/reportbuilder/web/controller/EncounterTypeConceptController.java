package org.openmrs.module.reportbuilder.web.controller;

import org.openmrs.Concept;
import org.openmrs.EncounterType;
import org.openmrs.api.APIAuthenticationException;
import org.openmrs.api.context.Context;
import org.openmrs.module.reportbuilder.security.ReportBuilderPrivileges;
import org.openmrs.api.ConceptService;
import org.openmrs.api.EncounterService;
import org.openmrs.module.reportbuilder.web.resources.mapper.ConceptMapper;
import org.openmrs.module.webservices.rest.web.RequestContext;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * REST controller for encounter type concepts Replaces legacy ugandaemr-reports
 * EncounterTypeConceptsRestController
 */
@Controller
@RequestMapping(value = "/rest/" + RestConstants.VERSION_1 + EncounterTypeConceptController.REPORTBUILDER
        + EncounterTypeConceptController.CONCEPTS_ENCOUNTERTYPE)
public class EncounterTypeConceptController {
	
	public static final String REPORTBUILDER = "/reportbuilder";
	
	public static final String CONCEPTS_ENCOUNTERTYPE = "/concepts/encountertype";
	
	@ExceptionHandler(APIAuthenticationException.class)
    @RequestMapping(method = RequestMethod.GET)
    @ResponseBody
    public Object get(HttpServletRequest request, RequestContext context,
            @RequestParam(required = false, value = "encounterTypeUuid") String encounterTypeUuid) {
        Context.requirePrivilege("Task: reportbuilder.report.view");
        try {
            List<ConceptMapper> conceptMapperList = new ArrayList<>();

            ConceptService conceptService = Context.getConceptService();
            EncounterService encounterService = Context.getEncounterService();

            if (encounterTypeUuid != null && !encounterTypeUuid.isEmpty()) {
                // Get concepts specific to an encounter type
                EncounterType encounterType = encounterService.getEncounterTypeByUuid(encounterTypeUuid);
                if (encounterType != null) {
                    // For now, return encounter type info
                    ConceptMapper mapper = new ConceptMapper();
                    mapper.setConceptName(encounterType.getName());
                    mapper.setUuid(encounterType.getUuid());
                    mapper.setConceptId(encounterType.getId().toString());
                    mapper.setType("EncounterType");
                    conceptMapperList.add(mapper);
                }
            } else {
                // Get all encounter types
                List<EncounterType> encounterTypes = encounterService.getAllEncounterTypes();
                for (EncounterType et : encounterTypes) {
                    if (!et.isRetired()) {
                        ConceptMapper mapper = new ConceptMapper();
                        mapper.setConceptName(et.getName());
                        mapper.setUuid(et.getUuid());
                        mapper.setConceptId(et.getId().toString());
                        mapper.setType("EncounterType");
                        conceptMapperList.add(mapper);
                    }
                }
            }

            return new ResponseEntity<Object>(conceptMapperList, HttpStatus.OK);

        } catch (Exception ex) {
            return new ResponseEntity<String>(ex.getMessage() + Arrays.toString(ex.getStackTrace()),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
