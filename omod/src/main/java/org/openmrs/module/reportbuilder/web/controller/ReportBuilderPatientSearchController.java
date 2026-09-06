package org.openmrs.module.reportbuilder.web.controller;

import org.openmrs.Cohort;
import org.openmrs.api.APIAuthenticationException;
import org.openmrs.api.context.Context;
import org.openmrs.module.reportbuilder.security.ReportBuilderPrivileges;
import org.openmrs.api.CohortService;
import org.openmrs.module.webservices.rest.SimpleObject;
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
import java.util.List;

/**
 * REST controller for patient search cohorts Provides saved patient searches for cohort-based
 * reporting
 */
@Controller
@RequestMapping(value = "/rest/" + RestConstants.VERSION_1 + ReportBuilderPatientSearchController.REPORTBUILDER
        + ReportBuilderPatientSearchController.PATIENT_SEARCH)
public class ReportBuilderPatientSearchController {
	
	public static final String REPORTBUILDER = "/reportbuilder";
	
	public static final String PATIENT_SEARCH = "/patientsearch";
	
	@ExceptionHandler(APIAuthenticationException.class)
	@RequestMapping(method = RequestMethod.GET)
	@ResponseBody
	public ResponseEntity<Object> getAll(HttpServletRequest request,
	        @RequestParam(required = false, value = "includeVoided") boolean includeVoided) {
		Context.requirePrivilege("Task: reportbuilder.dashboard.view");
		try {
			CohortService cohortService = Context.getCohortService();
			List<SimpleObject> objects = new ArrayList<SimpleObject>();
			
			// Get all cohorts (saved searches)
			List<Cohort> cohorts = cohortService.getAllCohorts(includeVoided);
			
			for (Cohort cohort : cohorts) {
				SimpleObject details = new SimpleObject();
				details.add("name", cohort.getName());
				details.add("uuid", cohort.getUuid());
				details.add("id", cohort.getId());
				details.add("description", cohort.getDescription());
				details.add("size", cohort.getSize());
				details.add("voided", cohort.isVoided());
				
				if (!cohort.isVoided() || includeVoided) {
					objects.add(details);
				}
			}
			
			return new ResponseEntity<Object>(objects, HttpStatus.OK);
			
		}
		catch (Exception ex) {
			SimpleObject errorResponse = new SimpleObject();
			errorResponse.add("error", ex.getMessage());
			return new ResponseEntity<Object>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
	
	/**
	 * Get a specific patient search by UUID
	 */
	@RequestMapping(method = RequestMethod.GET, params = "uuid")
	@ResponseBody
	public ResponseEntity<Object> getByUuid(HttpServletRequest request,
	        @RequestParam(required = true, value = "uuid") String uuid) {
		Context.requirePrivilege("Task: reportbuilder.dashboard.view");
		try {
			CohortService cohortService = Context.getCohortService();
			Cohort cohort = cohortService.getCohortByUuid(uuid);
			
			if (cohort == null) {
				SimpleObject errorResponse = new SimpleObject();
				errorResponse.add("error", "Patient search not found");
				return new ResponseEntity<Object>(errorResponse, HttpStatus.NOT_FOUND);
			}
			
			SimpleObject details = new SimpleObject();
			details.add("name", cohort.getName());
			details.add("uuid", cohort.getUuid());
			details.add("id", cohort.getId());
			details.add("description", cohort.getDescription());
			details.add("size", cohort.getSize());
			details.add("voided", cohort.isVoided());
			
			return new ResponseEntity<Object>(details, HttpStatus.OK);
			
		}
		catch (Exception ex) {
			SimpleObject errorResponse = new SimpleObject();
			errorResponse.add("error", ex.getMessage());
			return new ResponseEntity<Object>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
	
	/**
	 * Search patient searches by name
	 */
	@RequestMapping(method = RequestMethod.GET, params = "q")
	@ResponseBody
	public ResponseEntity<Object> search(HttpServletRequest request,
	        @RequestParam(required = true, value = "q") String query,
	        @RequestParam(required = false, value = "includeVoided") boolean includeVoided) {
		Context.requirePrivilege("Task: reportbuilder.dashboard.view");
		try {
			CohortService cohortService = Context.getCohortService();
			List<SimpleObject> objects = new ArrayList<SimpleObject>();
			
			// Search cohorts by name
			List<Cohort> allCohorts = cohortService.getAllCohorts(includeVoided);
			
			for (Cohort cohort : allCohorts) {
				if (cohort.getName() != null && cohort.getName().toLowerCase().contains(query.toLowerCase())) {
					SimpleObject details = new SimpleObject();
					details.add("name", cohort.getName());
					details.add("uuid", cohort.getUuid());
					details.add("id", cohort.getId());
					details.add("description", cohort.getDescription());
					details.add("size", cohort.getSize());
					details.add("voided", cohort.isVoided());
					
					if (!cohort.isVoided() || includeVoided) {
						objects.add(details);
					}
				}
			}
			
			return new ResponseEntity<Object>(objects, HttpStatus.OK);
			
		}
		catch (Exception ex) {
			SimpleObject errorResponse = new SimpleObject();
			errorResponse.add("error", ex.getMessage());
			return new ResponseEntity<Object>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
}
