/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.reportbuilder.api.export;

import org.junit.Assert;
import org.junit.Test;
import org.openmrs.module.reportbuilder.web.controller.dto.ImportResult;
import org.openmrs.module.reportbuilder.web.controller.dto.ShippingResult;
import org.openmrs.module.reportbuilder.web.controller.dto.ShippingRequest;
import org.openmrs.module.reportbuilder.web.controller.dto.SerializedReport;

/**
 * Simple unit tests for export/import DTOs. Tests basic DTO functionality without complex
 * dependencies.
 */
public class ImportExportDtoSimpleTest {
	
	@Test
	public void shouldCreateImportResult() {
		ImportResult result = new ImportResult();
		Assert.assertNotNull("ImportResult should be created", result);
		Assert.assertFalse("Should not be successful by default", result.isSuccess());
	}
	
	@Test
	public void shouldSetImportResultBasicProperties() {
		ImportResult result = new ImportResult();
		
		result.setSuccess(true);
		result.setSummary("Import completed successfully");
		
		Assert.assertTrue("Should be successful", result.isSuccess());
		Assert.assertEquals("Summary should match", "Import completed successfully", result.getSummary());
	}
	
	@Test
	public void shouldAddImportSuccessWithStringParams() {
		ImportResult result = new ImportResult();
		
		result.addSuccess("report", "report.json");
		result.addSuccess("category", "category.json");
		
		Assert.assertEquals("Should have 2 successes", 2, result.getSuccessCount());
		Assert.assertEquals("Should have 0 errors", 0, result.getErrorCount());
		Assert.assertTrue("Should be successful with no errors", result.isSuccess());
	}
	
	@Test
	public void shouldAddImportErrorWithStringParams() {
		ImportResult result = new ImportResult();
		
		result.addError("indicator", "indicator.json", "Invalid SQL syntax");
		result.addError("theme", "theme.json", "Missing required field");
		
		Assert.assertEquals("Should have 0 successes", 0, result.getSuccessCount());
		Assert.assertEquals("Should have 2 errors", 2, result.getErrorCount());
		Assert.assertFalse("Should not be successful with errors", result.isSuccess());
	}
	
	@Test
	public void shouldGetSuccessAndErrorCounts() {
		ImportResult result = new ImportResult();
		
		result.addSuccess("report", "report.json");
		result.addSuccess("category", "category.json");
		result.addSuccess("theme", "theme.json");
		result.addError("indicator", "indicator.json", "Parse error");
		
		Assert.assertEquals("Should have 3 successes", 3, result.getSuccessCount());
		Assert.assertEquals("Should have 1 error", 1, result.getErrorCount());
	}
	
	@Test
	public void shouldCreateShippingResult() {
		ShippingResult result = new ShippingResult();
		Assert.assertNotNull("ShippingResult should be created", result);
		Assert.assertFalse("Should not be successful by default", result.isSuccess());
		Assert.assertNotNull("Dependencies should not be null", result.getDependencies());
	}
	
	@Test
	public void shouldSetShippingResultProperties() {
		ShippingResult result = new ShippingResult();
		
		result.setSuccess(true);
		result.setReportCode("TEST-001");
		result.setVersion("1.0.0");
		result.setSourceFile("/path/to/source.json");
		result.setVersionFile("/path/to/version.json");
		
		Assert.assertTrue("Should be successful", result.isSuccess());
		Assert.assertEquals("Report code should match", "TEST-001", result.getReportCode());
		Assert.assertEquals("Version should match", "1.0.0", result.getVersion());
		Assert.assertEquals("Source file should match", "/path/to/source.json", result.getSourceFile());
		Assert.assertEquals("Version file should match", "/path/to/version.json", result.getVersionFile());
	}
	
	@Test
	public void shouldHandleShippingDependencies() {
		ShippingResult result = new ShippingResult();
		
		result.getDependencies().addCategory("category1.json");
		result.getDependencies().addTheme("theme1.json");
		result.getDependencies().addIndicator("indicator1.json");
		result.getDependencies().addSection("section1.json");
		result.getDependencies().addLibrary("library1.json");
		
		Assert.assertEquals("Should have 1 category", 1, result.getDependencies().getCategories().size());
		Assert.assertEquals("Should have 1 theme", 1, result.getDependencies().getThemes().size());
		Assert.assertEquals("Should have 1 indicator", 1, result.getDependencies().getIndicators().size());
		Assert.assertEquals("Should have 1 section", 1, result.getDependencies().getSections().size());
		Assert.assertEquals("Should have 1 library", 1, result.getDependencies().getLibrary().size());
	}
	
	@Test
	public void shouldHandleShippingError() {
		ShippingResult result = new ShippingResult();
		
		result.setSuccess(false);
		result.setErrorMessage("Report not found");
		
		Assert.assertFalse("Should not be successful", result.isSuccess());
		Assert.assertEquals("Error message should match", "Report not found", result.getErrorMessage());
	}
	
	@Test
	public void shouldCreateShippingRequest() {
		ShippingRequest request = new ShippingRequest();
		Assert.assertNotNull("ShippingRequest should be created", request);
	}
	
	@Test
	public void shouldSetShippingRequestProperties() {
		ShippingRequest request = new ShippingRequest();
		
		request.setReportUuid("report-uuid-123");
		request.setVersion("2.0.0");
		request.setDestination("/path/to/export");
		
		Assert.assertEquals("Report UUID should match", "report-uuid-123", request.getReportUuid());
		Assert.assertEquals("Version should match", "2.0.0", request.getVersion());
		Assert.assertEquals("Destination should match", "/path/to/export", request.getDestination());
	}
	
	@Test
	public void shouldCreateSerializedReport() {
		SerializedReport report = new SerializedReport();
		Assert.assertNotNull("SerializedReport should be created", report);
		Assert.assertNotNull("Dependencies should not be null", report.getDependencies());
	}
	
	@Test
	public void shouldSetSerializedReportBasicProperties() {
		SerializedReport report = new SerializedReport();
		
		report.setUuid("test-uuid-123");
		report.setName("Test Report");
		report.setCode("TEST001");
		report.setDescription("Test description");
		report.setReportType("patient-list");
		report.setVersion("1.0.0");
		
		Assert.assertEquals("UUID should match", "test-uuid-123", report.getUuid());
		Assert.assertEquals("Name should match", "Test Report", report.getName());
		Assert.assertEquals("Code should match", "TEST001", report.getCode());
		Assert.assertEquals("Description should match", "Test description", report.getDescription());
		Assert.assertEquals("Report type should match", "patient-list", report.getReportType());
		Assert.assertEquals("Version should match", "1.0.0", report.getVersion());
	}
	
	@Test
	public void shouldHandleSerializedReportDependencies() {
		SerializedReport report = new SerializedReport();
		
		report.getDependencies().addIndicator("indicator-1");
		report.getDependencies().addTheme("theme-1");
		report.getDependencies().addAgeCategory("agecat-1");
		
		Assert.assertEquals("Should have 1 indicator", 1, report.getDependencies().getIndicators().size());
		Assert.assertEquals("Should have 1 theme", 1, report.getDependencies().getThemes().size());
		Assert.assertEquals("Should have 1 age category", 1, report.getDependencies().getAgeCategories().size());
	}
	
	@Test
	public void shouldHandleSerializedReportExtendedProperties() {
		SerializedReport report = new SerializedReport();
		
		report.setCategory("General");
		report.setSubcategory("Patient Lists");
		report.setStatus("COMPLETED");
		report.setCompiledAt("2025-08-17T12:00:00");
		report.setCompiledBy("admin");
		
		Assert.assertEquals("Category should match", "General", report.getCategory());
		Assert.assertEquals("Subcategory should match", "Patient Lists", report.getSubcategory());
		Assert.assertEquals("Status should match", "COMPLETED", report.getStatus());
		Assert.assertEquals("Compiled at should match", "2025-08-17T12:00:00", report.getCompiledAt());
		Assert.assertEquals("Compiled by should match", "admin", report.getCompiledBy());
	}
	
	@Test
	public void shouldHandleMultipleDependencies() {
		SerializedReport report = new SerializedReport();
		
		for (int i = 0; i < 3; i++) {
			report.getDependencies().addIndicator("indicator-" + i);
			report.getDependencies().addSection("section-" + i);
		}
		
		Assert.assertEquals("Should have 3 indicators", 3, report.getDependencies().getIndicators().size());
		Assert.assertEquals("Should have 3 sections", 3, report.getDependencies().getSections().size());
	}
	
	@Test
	public void shouldHandleEmptyDependencies() {
		SerializedReport report = new SerializedReport();
		
		Assert.assertNotNull("Indicators should not be null", report.getDependencies().getIndicators());
		Assert.assertNotNull("Sections should not be null", report.getDependencies().getSections());
		Assert.assertNotNull("Themes should not be null", report.getDependencies().getThemes());
		Assert.assertNotNull("Age categories should not be null", report.getDependencies().getAgeCategories());
		
		Assert.assertTrue("Indicators should be empty", report.getDependencies().getIndicators().isEmpty());
		Assert.assertTrue("Sections should be empty", report.getDependencies().getSections().isEmpty());
		Assert.assertTrue("Themes should be empty", report.getDependencies().getThemes().isEmpty());
		Assert.assertTrue("Age categories should be empty", report.getDependencies().getAgeCategories().isEmpty());
	}
	
	@Test
	public void shouldHandleImportResultSuccessAndErrorObjects() {
		ImportResult result = new ImportResult();
		
		// Add success using string params (the actual API)
		result.addSuccess("report", "report.json");
		
		// Get the success object and verify its properties
		ImportResult.ImportSuccess success = result.getSuccesses().get(0);
		Assert.assertEquals("Type should be report", "report", success.getType());
		Assert.assertEquals("Filename should be report.json", "report.json", success.getFilename());
		
		// Add error using string params (the actual API)
		result.addError("indicator", "indicator.json", "Parse error");
		
		// Get the error object and verify its properties
		ImportResult.ImportError error = result.getErrors().get(0);
		Assert.assertEquals("Type should be indicator", "indicator", error.getType());
		Assert.assertEquals("Filename should be indicator.json", "indicator.json", error.getFilename());
		Assert.assertEquals("Message should match", "Parse error", error.getMessage());
	}
}
