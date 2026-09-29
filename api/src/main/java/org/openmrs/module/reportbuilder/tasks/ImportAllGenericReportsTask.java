package org.openmrs.module.reportbuilder.tasks;

import org.openmrs.api.context.Context;
import org.openmrs.scheduler.tasks.AbstractTask;
import org.openmrs.module.reportbuilder.api.ReportBuilderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scheduled task for importing all compiled reports from the runtime directory. This task can be
 * scheduled to run periodically or triggered manually to import/reimport compiled reports from the
 * external directory, using the same bulk import path as the REST import resource.
 */
public class ImportAllGenericReportsTask extends AbstractTask {
	
	protected final Logger log = LoggerFactory.getLogger(getClass());
	
	@Override
	public void execute() {
		try {
			log.info("Starting scheduled compiled report import task");
			
			ReportBuilderService service = Context.getService(ReportBuilderService.class);
			if (service == null) {
				log.error("Report builder service not available");
				return;
			}
			
			ReportBuilderService.CompiledReportsImportSummary summary = service.importAllCompiledReports(service
			        .getDefaultImportDirectory());
			
			for (ReportBuilderService.CompiledReportImportEntry entry : summary.getImportedReports()) {
				log.info("Imported: " + entry.getFileName() + " -> " + entry.getName() + " [uuid=" + entry.getUuid()
				        + ", reportDefinitionUuid=" + entry.getReportDefinitionUuid() + "]");
			}
			
			for (String error : summary.getErrors()) {
				log.error("Failed: " + error);
			}
			
			log.info("Scheduled import complete: " + summary.getSuccessCount() + " succeeded, " + summary.getErrorCount()
			        + " failed, " + summary.getSkippedCount() + " unrecognized files skipped from "
			        + summary.getSourceDirectory());
			
		}
		catch (Exception e) {
			log.error("Failed to execute compiled report import task", e);
			
			// Don't re-throw - let the task complete gracefully
			// Individual import failures are logged separately
		}
	}
}
