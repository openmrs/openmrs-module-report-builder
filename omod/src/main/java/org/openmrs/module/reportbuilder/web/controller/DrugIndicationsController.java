package org.openmrs.module.reportbuilder.web.controller;

import org.openmrs.Concept;
import org.openmrs.OrderType;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * REST controller for drug order indications Replaces legacy ugandaemr-reports
 * DrugIndicationsRestController
 */
@Controller
@RequestMapping(value = "/rest/" + RestConstants.VERSION_1 + DrugIndicationsController.REPORTBUILDER
        + DrugIndicationsController.ORDER_INDICATIONS)
public class DrugIndicationsController {
	
	public static final String REPORTBUILDER = "/reportbuilder";
	
	public static final String ORDER_INDICATIONS = "/order/indications";
	
	@ExceptionHandler(APIAuthenticationException.class)
    @RequestMapping(method = RequestMethod.GET)
    @ResponseBody
    public Object get(HttpServletRequest request, RequestContext context,
            @RequestParam(required = true, value = "uuid") String uuid) {
        Context.requirePrivilege("Task: reportbuilder.report.view");
        try {
            OrderType orderType = Context.getOrderService().getOrderTypeByUuid(uuid);

            if (orderType == null) {
                return new ResponseEntity<String>("Order type not found", HttpStatus.NOT_FOUND);
            }

            // For now, return order type information
            // In the legacy module, this would return both coded and non-coded reasons
            List<Object> reasons = new ArrayList<>();

            // Add non-coded reasons (free text options)
            List<String> nonCodedReasons = getNonCodedOrderReasons(orderType);
            for (String reason : nonCodedReasons) {
                reasons.add(reason);
            }

            // Add coded reasons (concept-based)
            List<Concept> codedReasons = getCodedOrderReasons(orderType);
            for (Concept concept : codedReasons) {
                ConceptMapper mapper = new ConceptMapper();
                mapper.setConceptName(concept.getName().getName());
                mapper.setUuid(concept.getUuid());
                mapper.setConceptId(concept.getId().toString());
                mapper.setType("OrderReason");
                reasons.add(mapper);
            }

            return new ResponseEntity<>(reasons, HttpStatus.OK);

        } catch (Exception ex) {
            return new ResponseEntity<String>(ex.getMessage() + Arrays.toString(ex.getStackTrace()),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
	
	/**
	 * Get non-coded order reasons (free text options) This is a simplified implementation
	 */
	private List<String> getNonCodedOrderReasons(OrderType orderType) {
		List<String> reasons = new ArrayList<String>();
		
		// Common non-coded reasons (would typically be configured)
		if ("Drug Order".equals(orderType.getName())) {
			reasons.add("Side Effects");
			reasons.add("Treatment Failure");
			reasons.add("Patient Request");
			reasons.add("Stock Out");
		}
		
		return reasons;
	}
	
	/**
	 * Get coded order reasons (concept-based) This is a simplified implementation
	 */
	private List<Concept> getCodedOrderReasons(OrderType orderType) {
		List<Concept> concepts = new ArrayList<Concept>();
		ConceptService conceptService = Context.getConceptService();
		
		// Common coded reason concept UUIDs (would typically be configured)
		String[] reasonConceptUuids = { "160244AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", // Treatment failure
		        "5240AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" // Side effect
		};
		
		for (String uuid : reasonConceptUuids) {
			try {
				Concept c = conceptService.getConceptByUuid(uuid);
				if (c != null) {
					concepts.add(c);
				}
			}
			catch (Exception e) {
				// Concept not found, skip
			}
		}
		
		return concepts;
	}
}
