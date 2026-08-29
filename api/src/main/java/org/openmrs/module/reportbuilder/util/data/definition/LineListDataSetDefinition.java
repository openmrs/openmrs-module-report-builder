package org.openmrs.module.reportbuilder.util.data.definition;

import org.openmrs.api.AdministrationService;
import org.openmrs.api.context.Context;
import org.openmrs.module.reporting.dataset.definition.BaseDataSetDefinition;
import org.openmrs.module.reporting.definition.configuration.ConfigurationProperty;
import org.openmrs.util.OpenmrsUtil;

import java.io.File;
import java.util.Date;

/**
 * Data set definition for ETL-based line listing reports. Similar to
 * AggregateReportDataSetDefinition but for patient-level line lists.
 */
public class LineListDataSetDefinition extends BaseDataSetDefinition {
	
	/** Legacy root under the application data directory, honoring the global property override. */
	public static final String REPORTS_PATH = "configuration/reportbuilder";
	
	public static final String GP_TO_DIR_PATH = "reportbuilder.reports.directory";
	
	/** Legacy designs folder name kept for backward-compatible reads of pre-existing installs. */
	public static final String REPORT_DESIGNS_FOLDER = "report_designs";
	
	/**
	 * Canonical store for compiled design files: &lt;OPENMRS_APPDATA&gt;/configuration/reports with
	 * type subfolders aggregates/ and linelist/.
	 */
	public static final String CONFIGURATION_FOLDER = "configuration";
	
	public static final String CANONICAL_REPORTS_FOLDER = "reports";
	
	@ConfigurationProperty
	private Date startDate;
	
	@ConfigurationProperty
	private Date endDate;
	
	/**
	 * Backward-compatible storage of the report design location. Existing definitions may store an
	 * absolute file path. New definitions may store a relative file name/path such as:
	 * linelist/my_report.json or some/subfolder/my_report.json At runtime, relative paths are
	 * resolved under the configured reportbuilder report designs directory.
	 */
	@ConfigurationProperty
	private File reportDesign;
	
	public LineListDataSetDefinition() {
		super();
	}
	
	public LineListDataSetDefinition(String name, String description) {
		super(name, description);
	}
	
	public Date getStartDate() {
		return startDate;
	}
	
	public void setStartDate(Date startDate) {
		this.startDate = startDate;
	}
	
	public Date getEndDate() {
		return endDate;
	}
	
	public void setEndDate(Date endDate) {
		this.endDate = endDate;
	}
	
	/**
	 * Returns the resolved report design file. Rules: - null &gt; null - absolute path &gt; return
	 * as-is - relative path &gt; resolve under the canonical configuration/reports directory,
	 * falling back to the legacy report designs directory when the file only exists there.
	 */
	public File getReportDesign() {
		if (reportDesign == null) {
			return null;
		}
		
		if (reportDesign.isAbsolute()) {
			return reportDesign;
		}
		
		File resolved = new File(getReportDesignDirectory(), reportDesign.getPath());
		if (!resolved.exists()) {
			File legacy = new File(getLegacyReportDesignDirectory(), reportDesign.getPath());
			if (legacy.exists()) {
				return legacy;
			}
		}
		return resolved;
	}
	
	/**
	 * Stores the raw file reference. If you pass an absolute file, it will be used as-is. If you
	 * pass a relative file, it will resolve under the reportbuilder report designs directory.
	 */
	public void setReportDesign(File reportDesign) {
		this.reportDesign = reportDesign;
	}
	
	/**
	 * Convenience setter for storing a relative design path or file name.
	 */
	public void setReportDesignPath(String relativePath) {
		if (relativePath == null || relativePath.trim().isEmpty()) {
			this.reportDesign = null;
		} else {
			this.reportDesign = new File(relativePath.trim());
		}
	}
	
	/**
	 * Returns the raw configured value exactly as stored, without resolution.
	 */
	public File getRawReportDesign() {
		return reportDesign;
	}
	
	/**
	 * Returns the canonical directory holding compiled design files:
	 * &lt;OPENMRS_APPDATA&gt;/configuration/reports (type subfolders aggregates/ and linelist/ are
	 * part of the stored relative paths). Created on first access.
	 */
	public static File getReportDesignDirectory() {
		File dir = new File(new File(OpenmrsUtil.getApplicationDataDirectory(), CONFIGURATION_FOLDER),
		        CANONICAL_REPORTS_FOLDER);
		
		if (!dir.exists() && !dir.mkdirs()) {
			throw new RuntimeException("Failed to create report design directory: " + dir.getAbsolutePath());
		}
		
		return dir;
	}
	
	/**
	 * Returns the legacy designs directory
	 * (&lt;OPENMRS_APPDATA&gt;/&lt;reportbuilder.reports.directory GP, default
	 * configuration/reportbuilder&gt;/report_designs) honoring any global property override. Kept
	 * solely so previously compiled reports keep evaluating before they are recompiled into the
	 * canonical location.
	 */
	public static File getLegacyReportDesignDirectory() {
		AdministrationService administrationService = Context.getAdministrationService();
		String pathToDIR = administrationService.getGlobalProperty(GP_TO_DIR_PATH);
		
		if (pathToDIR == null || pathToDIR.trim().isEmpty()) {
			pathToDIR = REPORTS_PATH;
		}
		
		return new File(new File(OpenmrsUtil.getApplicationDataDirectory(), pathToDIR), REPORT_DESIGNS_FOLDER);
	}
	
	/**
	 * Resolves a report design file name under the standard reportbuilder folder.
	 */
	public static File resolveReportDesignFile(String fileName) {
		return new File(getReportDesignDirectory(), fileName);
	}
}
