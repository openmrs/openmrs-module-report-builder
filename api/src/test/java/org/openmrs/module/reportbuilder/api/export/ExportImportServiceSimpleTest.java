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
import org.openmrs.module.reportbuilder.web.controller.dto.ImportRequest;
import org.openmrs.module.reportbuilder.web.controller.dto.ShippingResult;
import org.openmrs.module.reportbuilder.web.controller.dto.ImportResult;

/**
 * Simple unit tests for export/import request/response handling. Tests DTO behavior and service
 * interfaces.
 */
public class ExportImportServiceSimpleTest {
	
	@Test
	public void shouldCreateImportRequest() {
		ImportRequest request = new ImportRequest();
		Assert.assertNotNull("ImportRequest should be created", request);
	}
	
	@Test
	public void shouldSetImportRequestProperties() {
		ImportRequest request = new ImportRequest();
		
		request.setSourceDirectory("/path/to/import");
		
		Assert.assertEquals("Source directory should match", "/path/to/import", request.getSourceDirectory());
	}
	
	@Test
	public void shouldHandleNullSourceDirectory() {
		ImportRequest request = new ImportRequest();
		
		Assert.assertNull("Source directory should be null initially", request.getSourceDirectory());
		
		request.setSourceDirectory(null);
		
		Assert.assertNull("Source directory should remain null", request.getSourceDirectory());
	}
	
	@Test
	public void shouldHandleEmptySourceDirectory() {
		ImportRequest request = new ImportRequest();
		
		request.setSourceDirectory("");
		
		Assert.assertEquals("Source directory should be empty", "", request.getSourceDirectory());
		Assert.assertTrue("Empty source directory should have length 0", request.getSourceDirectory().isEmpty());
	}
	
	@Test
	public void shouldValidateImportRequestStructure() {
		ImportRequest request = new ImportRequest();
		
		// Test that the request has the expected structure
		Assert.assertNotNull("Request should not be null", request);
		
		// After setting a valid path
		request.setSourceDirectory("/valid/path/to/import");
		Assert.assertTrue("Source directory should start with /", request.getSourceDirectory().startsWith("/"));
	}
	
	@Test
	public void shouldCreateMultipleImportRequests() {
		ImportRequest request1 = new ImportRequest();
		request1.setSourceDirectory("/path1");
		
		ImportRequest request2 = new ImportRequest();
		request2.setSourceDirectory("/path2");
		
		Assert.assertEquals("First request should have path1", "/path1", request1.getSourceDirectory());
		Assert.assertEquals("Second request should have path2", "/path2", request2.getSourceDirectory());
	}
	
	@Test
	public void shouldHandleShippingResultTransitions() {
		ShippingResult result = new ShippingResult();
		
		// Initially not successful
		Assert.assertFalse("Should not be successful initially", result.isSuccess());
		
		// Mark as successful
		result.setSuccess(true);
		Assert.assertTrue("Should be successful after setting", result.isSuccess());
		
		// Add error message
		result.setErrorMessage("Test error");
		result.setSuccess(false);
		Assert.assertFalse("Should not be successful with error", result.isSuccess());
		Assert.assertEquals("Error message should match", "Test error", result.getErrorMessage());
	}
	
	@Test
	public void shouldHandleImportResultTransitions() {
		ImportResult result = new ImportResult();
		
		// Initially not successful
		Assert.assertFalse("Should not be successful initially", result.isSuccess());
		
		// Add a success - should become successful
		result.addSuccess("report", "report.json");
		Assert.assertTrue("Should be successful with no errors", result.isSuccess());
		Assert.assertEquals("Should have 1 success", 1, result.getSuccessCount());
		
		// Add an error - should become not successful
		result.addError("indicator", "indicator.json", "Parse error");
		Assert.assertFalse("Should not be successful with errors", result.isSuccess());
		Assert.assertEquals("Should have 1 error", 1, result.getErrorCount());
		
		// Summary should be settable
		result.setSummary("Partial import completed");
		Assert.assertEquals("Summary should match", "Partial import completed", result.getSummary());
	}
	
	@Test
	public void shouldHandleBatchResults() {
		ImportResult result1 = new ImportResult();
		result1.addSuccess("report", "report1.json");
		
		ImportResult result2 = new ImportResult();
		result2.addSuccess("report", "report2.json");
		result2.addSuccess("category", "category.json");
		
		Assert.assertEquals("First result should have 1 success", 1, result1.getSuccessCount());
		Assert.assertEquals("Second result should have 2 successes", 2, result2.getSuccessCount());
		
		int totalSuccesses = result1.getSuccessCount() + result2.getSuccessCount();
		Assert.assertEquals("Total successes should be 3", 3, totalSuccesses);
	}
	
	@Test
	public void shouldHandleComplexShippingResult() {
		ShippingResult result = new ShippingResult();
		
		result.setSuccess(true);
		result.setReportCode("COMPLEX-001");
		result.setVersion("2.5.0");
		result.setSourceFile("/complex/path/to/source.json");
		result.setCompiledFile("/complex/path/to/compiled.json");
		result.setVersionFile("/complex/path/to/version.json");
		
		// Add multiple dependencies
		for (int i = 0; i < 3; i++) {
			result.getDependencies().addCategory("category" + i + ".json");
			result.getDependencies().addIndicator("indicator" + i + ".json");
			result.getDependencies().addSection("section" + i + ".json");
		}
		
		Assert.assertTrue("Should be successful", result.isSuccess());
		Assert.assertEquals("Should have 3 categories", 3, result.getDependencies().getCategories().size());
		Assert.assertEquals("Should have 3 indicators", 3, result.getDependencies().getIndicators().size());
		Assert.assertEquals("Should have 3 sections", 3, result.getDependencies().getSections().size());
	}
	
	@Test
	public void shouldHandleErrorRecovery() {
		ImportResult result = new ImportResult();
		
		// Add some errors
		result.addError("indicator1", "indicator1.json", "Error 1");
		result.addError("indicator2", "indicator2.json", "Error 2");
		
		Assert.assertFalse("Should not be successful", result.isSuccess());
		Assert.assertEquals("Should have 2 errors", 2, result.getErrorCount());
		
		// Set success to true manually (simulating retry success)
		result.setSuccess(true);
		result.setSummary("Retry successful");
		
		Assert.assertTrue("Should be successful after manual override", result.isSuccess());
		Assert.assertEquals("Summary should reflect retry", "Retry successful", result.getSummary());
	}
	
	@Test
	public void shouldHandleDependencyOrdering() {
		ShippingResult result = new ShippingResult();
		
		// Add dependencies in a specific order
		result.getDependencies().addCategory("category1.json");
		result.getDependencies().addTheme("theme1.json");
		result.getDependencies().addIndicator("indicator1.json");
		result.getDependencies().addSection("section1.json");
		result.getDependencies().addLibrary("library1.json");
		
		// Verify order is maintained
		Assert.assertEquals("First category should be category1", "category1.json", result.getDependencies().getCategories()
		        .get(0));
		Assert.assertEquals("First theme should be theme1", "theme1.json", result.getDependencies().getThemes().get(0));
		Assert.assertEquals("First indicator should be indicator1", "indicator1.json", result.getDependencies()
		        .getIndicators().get(0));
		Assert.assertEquals("First section should be section1", "section1.json",
		    result.getDependencies().getSections().get(0));
		Assert.assertEquals("First library should be library1", "library1.json", result.getDependencies().getLibrary()
		        .get(0));
	}
}
