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

import java.io.IOException;
import java.util.ArrayList;

import org.junit.Assert;
import org.junit.Test;
import org.openmrs.module.reportbuilder.web.controller.dto.ImportRequest;
import org.openmrs.module.reportbuilder.web.controller.dto.ShippingResult;
import org.openmrs.module.reportbuilder.web.controller.dto.ImportResult;

/**
 * Unit tests for import/export functionality. Tests the DTOs and method signatures that were fixed
 * during compilation error resolution.
 */
public class ReportBuilderImportExportServiceTest {
	
	@Test
	public void shouldImportCompiledReportWithThreeParameterAddError() throws IOException {
		// This test verifies the fix for the addError method call
		// that now requires three parameters: type, filename, message
		
		ImportResult result = new ImportResult();
		
		// Test the fixed addError signature with three parameters
		result.addError("CompiledReport", "test-report.json", "Failed to parse report definition");
		
		Assert.assertFalse("Import should not be successful with errors", result.isSuccess());
		Assert.assertEquals("Should have 1 error", 1, result.getErrorCount());
		
		// Verify error details
		Assert.assertEquals("Error type should be CompiledReport", "CompiledReport", result.getErrors().get(0).getType());
		Assert.assertEquals("Error filename should match", "test-report.json", result.getErrors().get(0).getFilename());
		Assert.assertEquals("Error message should match", "Failed to parse report definition", result.getErrors().get(0)
		        .getMessage());
	}
	
	@Test
	public void shouldImportCompiledReportUsingAddSuccessInsteadOfIncrementCount() throws IOException {
		// This test verifies the fix for replacing incrementSuccessCount()
		// with the proper addSuccess(type, filename) method call
		
		ImportResult result = new ImportResult();
		
		// Test the fixed method using addSuccess instead of incrementSuccessCount
		result.addSuccess("CompiledReport", "report1.json");
		result.addSuccess("CompiledReport", "report2.json");
		
		Assert.assertTrue("Import should be successful", result.isSuccess());
		Assert.assertEquals("Should have 2 successes", 2, result.getSuccessCount());
		
		// Verify success details
		Assert.assertEquals("Success type should be CompiledReport", "CompiledReport", result.getSuccesses().get(0)
		        .getType());
		Assert.assertEquals("Success filename should match", "report1.json", result.getSuccesses().get(0).getFilename());
	}
	
	@Test
	public void shouldHandleMixedSuccessAndErrorDuringImport() throws IOException {
		// Test handling mixed success and error scenarios
		ImportResult result = new ImportResult();
		
		// Simulate some successful imports
		result.addSuccess("CompiledReport", "success-report1.json");
		result.addSuccess("CompiledReport", "success-report2.json");
		
		// Simulate some failed imports
		result.addError("CompiledReport", "error-report1.json", "Invalid JSON structure");
		result.addError("CompiledReport", "error-report2.json", "Missing required fields");
		
		Assert.assertFalse("Import should not be successful with errors", result.isSuccess());
		Assert.assertEquals("Should have 2 successes", 2, result.getSuccessCount());
		Assert.assertEquals("Should have 2 errors", 2, result.getErrorCount());
	}
	
	@Test
	public void shouldImportCompiledReportWithErrorRecovery() throws IOException {
		// Test error recovery scenario where errors are logged but import continues
		ImportResult result = new ImportResult();

		// First attempt fails
		result.addError("CompiledReport", "temp-report.json", "Temporary parse error");

		Assert.assertFalse("Should not be successful after error", result.isSuccess());
		Assert.assertEquals("Should have 1 error", 1, result.getErrorCount());

		// Simulate retry - clear errors and add success
		result.setErrors(new ArrayList<>());
		result.addSuccess("CompiledReport", "temp-report.json");

		Assert.assertTrue("Should be successful after retry", result.isSuccess());
		Assert.assertEquals("Should have 1 success", 1, result.getSuccessCount());
		Assert.assertEquals("Should have no errors after retry", 0, result.getErrorCount());
	}
	
	@Test
	public void shouldHandleBulkImportWithMultipleCompiledReports() throws IOException {
		// Test bulk import scenario with multiple compiled reports
		ImportResult result = new ImportResult();

		// Simulate importing multiple compiled reports
		for (int i = 1; i <= 10; i++) {
			result.addSuccess("CompiledReport", "report" + i + ".json");
		}

		Assert.assertTrue("Should be successful", result.isSuccess());
		Assert.assertEquals("Should have 10 successes", 10, result.getSuccessCount());
		Assert.assertEquals("Should have no errors", 0, result.getErrorCount());

		// Verify all reports are tracked
		for (int i = 1; i <= 10; i++) {
			final int index = i;
			boolean found = result.getSuccesses().stream()
					.anyMatch(s -> s.getFilename().equals("report" + index + ".json"));
			Assert.assertTrue("Should contain report" + i + ".json", found);
		}
	}
	
	@Test
	public void shouldInitializeCompiledReportsListInShippingResult() {
		// Test that ShippingResult properly initializes the compiledReports list
		ShippingResult result = new ShippingResult();
		
		Assert.assertNotNull("Compiled reports list should be initialized", result.getCompiledReports());
		Assert.assertTrue("Compiled reports list should be empty initially", result.getCompiledReports().isEmpty());
	}
	
	@Test
	public void shouldAddCompiledReportsToShippingResult() {
		// Test adding compiled reports to ShippingResult
		ShippingResult result = new ShippingResult();
		
		// Add some compiled reports
		result.getCompiledReports().add("report1.json");
		result.getCompiledReports().add("report2.json");
		result.getCompiledReports().add("report3.json");
		
		Assert.assertEquals("Should have 3 compiled reports", 3, result.getCompiledReports().size());
	}
	
	@Test
	public void shouldHandleNullCompiledReportsSafely() {
		// Test safe handling of null compiledReports list
		ShippingResult result = new ShippingResult();

		// Simulate service code checking for null before adding
		if (result.getCompiledReports() == null) {
			result.setCompiledReports(new ArrayList<>());
		}
		result.getCompiledReports().add("safe-report.json");

		Assert.assertEquals("Should have 1 compiled report", 1, result.getCompiledReports().size());
		Assert.assertEquals("Report name should match", "safe-report.json", result.getCompiledReports().get(0));
	}
	
	@Test
	public void shouldCreateImportRequestWithSourceDirectory() {
		// Test creating an import request with a source directory
		ImportRequest request = new ImportRequest();
		request.setSourceDirectory("/path/to/import");
		
		Assert.assertNotNull("ImportRequest should be created", request);
		Assert.assertEquals("Source directory should match", "/path/to/import", request.getSourceDirectory());
	}
	
	@Test
	public void shouldHandleEmptyImportDirectory() throws IOException {
		// Test handling empty import directory
		ImportResult result = new ImportResult();
		
		// Empty import - no successes or errors (success is false by default)
		Assert.assertFalse("Empty import should not be successful initially", result.isSuccess());
		Assert.assertEquals("Should have 0 successes", 0, result.getSuccessCount());
		Assert.assertEquals("Should have 0 errors", 0, result.getErrorCount());
		
		// When no errors, we can set success to true
		result.setSuccess(true);
		Assert.assertTrue("Empty import with no errors should be successful", result.isSuccess());
	}
	
	@Test
	public void shouldImportCompiledReportWithMetadataTracking() throws IOException {
		// Test that metadata is properly tracked during import
		ImportResult result = new ImportResult();
		
		// Add a successful import
		result.addSuccess("CompiledReport", "metadata-report.json");
		
		// Verify metadata is captured
		Assert.assertEquals("Type should be CompiledReport", "CompiledReport", result.getSuccesses().get(0).getType());
		Assert.assertEquals("Filename should match", "metadata-report.json", result.getSuccesses().get(0).getFilename());
		
		// Set summary
		result.setSummary("Successfully imported 1 compiled report");
		Assert.assertEquals("Summary should match", "Successfully imported 1 compiled report", result.getSummary());
	}
	
	@Test
	public void shouldHandleImportWithAllErrorTypes() throws IOException {
		// Test handling different types of import errors
		ImportResult result = new ImportResult();

		// Add different types of errors
		result.addError("CompiledReport", "report1.json", "Parse error");
		result.addError("ReportDefinition", "report2.json", "Validation error");
		result.addError("Category", "category1.json", "Missing required field");

		Assert.assertFalse("Should not be successful with errors", result.isSuccess());
		Assert.assertEquals("Should have 3 errors", 3, result.getErrorCount());

		// Verify error types are preserved
		boolean hasCompiledReportError = result.getErrors().stream()
				.anyMatch(e -> e.getType().equals("CompiledReport"));
		boolean hasReportDefinitionError = result.getErrors().stream()
				.anyMatch(e -> e.getType().equals("ReportDefinition"));
		boolean hasCategoryError = result.getErrors().stream()
				.anyMatch(e -> e.getType().equals("Category"));

		Assert.assertTrue("Should have CompiledReport error", hasCompiledReportError);
		Assert.assertTrue("Should have ReportDefinition error", hasReportDefinitionError);
		Assert.assertTrue("Should have Category error", hasCategoryError);
	}
}
