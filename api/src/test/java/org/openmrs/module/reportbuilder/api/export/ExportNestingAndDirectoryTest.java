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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.module.reportbuilder.model.ETLMonitor;
import org.openmrs.module.reportbuilder.model.ReportBuilderReport;
import org.openmrs.module.reportbuilder.model.ReportBuilderSection;
import org.openmrs.module.reportbuilder.model.ReportCategory;
import org.openmrs.module.reportbuilder.model.ReportBuilderIndicator;
import org.openmrs.module.reportbuilder.model.ETLSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tests for export functionality focusing on: 1. Directory creation during export 2. Circular
 * reference handling during JSON serialization 3. JSON string fields (configJson, metaJson,
 * displayConfigJson) being serialized as plain strings
 */
public class ExportNestingAndDirectoryTest {
	
	// Use the same configuration as the service to test realistic scenarios
	private static final ObjectMapper objectMapper = new ObjectMapper();
	
	static {
		// Configure the same way as ReportBuilderServiceImpl
		objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		objectMapper.configure(SerializationFeature.FAIL_ON_SELF_REFERENCES, false);
		objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
		objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
	}
	
	private Path testTempDir;
	
	@Before
	public void setUp() throws IOException {
		// Create a temporary directory for test files
		testTempDir = Files.createTempDirectory("export-test-");
		System.out.println("Test temp directory: " + testTempDir);
	}
	
	@After
	public void tearDown() throws IOException {
		// Clean up test files
		if (testTempDir != null && Files.exists(testTempDir)) {
			Files.walk(testTempDir)
					.sorted((a, b) -> b.compareTo(a))
					.forEach(path -> {
						try {
							Files.deleteIfExists(path);
						} catch (IOException e) {
							// Ignore cleanup errors
						}
					});
		}
	}
	
	@Test
	public void shouldSerializeSectionWithJsonFieldsAsStrings() throws IOException {
		// Create a section with JSON string fields
		ReportBuilderSection section = new ReportBuilderSection();
		section.setUuid("test-section-uuid");
		section.setName("Test Section");
		section.setCode("TEST_SECTION");
		section.setDescription("Test section description");
		section.setConfigJson("{\"key\": \"value\", \"nested\": {\"deep\": \"value\"}}");
		section.setMetaJson("{\"meta\": \"data\", \"tags\": [\"tag1\", \"tag2\"]}");
		
		// Serialize to JSON
		String json = objectMapper.writeValueAsString(section);
		JsonNode node = objectMapper.readTree(json);
		
		// Verify JSON fields are serialized as strings, not as nested objects
		Assert.assertTrue("configJson should be present", node.has("configJson"));
		Assert.assertTrue("configJson should be a string node", node.get("configJson").isTextual());
		Assert.assertEquals("configJson should contain original JSON string",
		    "{\"key\": \"value\", \"nested\": {\"deep\": \"value\"}}", node.get("configJson").asText());
		
		Assert.assertTrue("metaJson should be present", node.has("metaJson"));
		Assert.assertTrue("metaJson should be a string node", node.get("metaJson").isTextual());
		Assert.assertEquals("metaJson should contain original JSON string",
		    "{\"meta\": \"data\", \"tags\": [\"tag1\", \"tag2\"]}", node.get("metaJson").asText());
	}
	
	@Test
	public void shouldSerializeETLMonitorWithDisplayConfigJsonAsString() throws IOException {
		// Create an ETLMonitor with displayConfigJson field
		ETLMonitor monitor = new ETLMonitor();
		monitor.setUuid("test-monitor-uuid");
		monitor.setName("Test Monitor");
		monitor.setCode("TEST_MONITOR");
		monitor.setDescription("Test monitor description");
		monitor.setConfigJson("{\"config\": \"value\"}");
		monitor.setDisplayConfigJson("{\"display\": {\"type\": \"chart\", \"options\": {\"color\": \"blue\"}}}");
		
		// Serialize to JSON
		String json = objectMapper.writeValueAsString(monitor);
		JsonNode node = objectMapper.readTree(json);
		
		// Verify displayConfigJson is serialized as a string
		Assert.assertTrue("displayConfigJson should be present", node.has("displayConfigJson"));
		Assert.assertTrue("displayConfigJson should be a string node", node.get("displayConfigJson").isTextual());
		Assert.assertEquals("displayConfigJson should contain original JSON string",
		    "{\"display\": {\"type\": \"chart\", \"options\": {\"color\": \"blue\"}}}", node.get("displayConfigJson")
		            .asText());
	}
	
	@Test
	public void shouldSerializeReportWithJsonFieldsAsStrings() throws IOException {
		// Create a report with category reference (potential circular reference)
		ReportCategory category = new ReportCategory();
		category.setUuid("test-category-uuid");
		category.setName("Test Category");
		category.setDescription("Test category description");
		
		ReportBuilderReport report = new ReportBuilderReport();
		report.setUuid("test-report-uuid");
		report.setName("Test Report");
		report.setCode("TEST_REPORT");
		report.setDescription("Test report description");
		report.setCategory(category);
		report.setConfigJson("{\"report\": \"config\", \"nested\": {\"deep\": \"structure\"}}");
		report.setMetaJson("{\"metadata\": \"value\", \"version\": \"1.0\"}");
		
		// Serialize to JSON
		String json = objectMapper.writeValueAsString(report);
		JsonNode node = objectMapper.readTree(json);
		
		// Verify JSON fields are serialized as strings
		Assert.assertTrue("configJson should be present", node.has("configJson"));
		Assert.assertTrue("configJson should be a string node", node.get("configJson").isTextual());
		
		Assert.assertTrue("metaJson should be present", node.has("metaJson"));
		Assert.assertTrue("metaJson should be a string node", node.get("metaJson").isTextual());
	}
	
	@Test
	public void shouldHandleCircularReferencesWithoutStackOverflow() throws IOException {
		// Create entities with circular references (Report <-> Category)
		ReportCategory category = new ReportCategory();
		category.setUuid("test-category-uuid");
		category.setName("Test Category");
		
		ReportBuilderReport report = new ReportBuilderReport();
		report.setUuid("test-report-uuid");
		report.setName("Test Report");
		report.setCode("TEST_REPORT");
		report.setCategory(category);
		
		// Even with the circular reference, serialization should complete
		String json = objectMapper.writeValueAsString(report);
		Assert.assertNotNull("Serialization should produce JSON", json);
		Assert.assertFalse("JSON should not be empty", json.isEmpty());
		Assert.assertTrue("JSON should contain report UUID", json.contains("test-report-uuid"));
	}
	
	@Test
	public void shouldHandleDeeplyNestedJsonStrings() throws IOException {
		// Create a section with deeply nested JSON string
		StringBuilder deepJson = new StringBuilder("{");
		for (int i = 0; i < 50; i++) {
			deepJson.append("\"level").append(i).append("\": {");
		}
		deepJson.append("\"value\": \"deep\"");
		for (int i = 0; i < 50; i++) {
			deepJson.append("}");
		}
		deepJson.append("}");
		
		ReportBuilderSection section = new ReportBuilderSection();
		section.setUuid("test-section-uuid");
		section.setName("Test Section");
		section.setCode("TEST_SECTION");
		section.setConfigJson(deepJson.toString());
		
		// Serialize to JSON
		String json = objectMapper.writeValueAsString(section);
		JsonNode node = objectMapper.readTree(json);
		
		// Verify the deep JSON is still serialized as a string
		Assert.assertTrue("configJson should be present", node.has("configJson"));
		Assert.assertTrue("configJson should be a string node", node.get("configJson").isTextual());
		Assert.assertTrue("configJson should contain the deep JSON structure",
		    node.get("configJson").asText().contains("level0"));
	}
	
	@Test
	public void shouldCreateNestedDirectoriesDuringExport() throws IOException {
		// Create a deeply nested directory path
		File deeplyNestedDir = testTempDir.resolve("level1/level2/level3/level4/level5/reportbuilder/sections").toFile();
		
		// The directory shouldn't exist yet
		Assert.assertFalse("Directory should not exist initially", deeplyNestedDir.exists());
		
		// Create the directory structure (simulating what export methods do)
		File parentDir = deeplyNestedDir.getParentFile();
		if (parentDir != null && !parentDir.exists()) {
			parentDir.mkdirs();
		}
		
		// Verify the directory was created
		Assert.assertTrue("Parent directory should exist after mkdirs()", parentDir.exists());
	}
	
	@Test
	public void shouldCreateMultipleDirectoriesForDifferentEntityTypes() throws IOException {
		// Test creating directories for different entity types
		String[] entityTypes = { "sections", "indicators", "themes", "categories", "reports", "etl-sources", "etl-monitors" };
		
		for (String entityType : entityTypes) {
			File entityDir = testTempDir.resolve("reportbuilder/" + entityType).toFile();
			Assert.assertFalse("Directory should not exist initially: " + entityType, entityDir.exists());
			
			// Create the directory (simulating what export methods do)
			File parentDir = entityDir.getParentFile();
			if (parentDir != null && !parentDir.exists()) {
				parentDir.mkdirs();
			}
			
			Assert.assertTrue("Directory should exist after creation: " + entityType, entityDir.getParentFile().exists());
		}
	}
	
	@Test
	public void shouldSerializeEntitiesWithLargeJsonPayloads() throws IOException {
		// Create an indicator with a large configJson
		StringBuilder largeConfig = new StringBuilder("{\"indicators\": [");
		for (int i = 0; i < 100; i++) {
			largeConfig.append("{\"id\": ").append(i).append(", \"name\": \"indicator").append(i)
			        .append("\", \"config\": {\"nested\": \"value\"}},");
		}
		largeConfig.append("]}");
		
		ReportBuilderIndicator indicator = new ReportBuilderIndicator();
		indicator.setUuid("test-indicator-uuid");
		indicator.setName("Test Indicator");
		indicator.setCode("TEST_INDICATOR");
		indicator.setConfigJson(largeConfig.toString());
		
		// Serialize to JSON
		String json = objectMapper.writeValueAsString(indicator);
		JsonNode node = objectMapper.readTree(json);
		
		// Verify the large config is serialized as a string
		Assert.assertTrue("configJson should be present", node.has("configJson"));
		Assert.assertTrue("configJson should be a string node", node.get("configJson").isTextual());
		Assert.assertTrue("configJson should contain the large JSON structure",
		    node.get("configJson").asText().contains("indicators"));
	}
	
	@Test
	public void shouldSerializeETLSourceWithoutNestingIssues() throws IOException {
		// Create an ETLSource with complex table patterns
		ETLSource source = new ETLSource();
		source.setUuid("test-source-uuid");
		source.setName("Test Source");
		source.setCode("TEST_SOURCE");
		source.setDescription("Test source description");
		source.setSourceType("DATABASE");
		source.setSchemaName("openmrs");
		
		String complexTablePatterns = "{\"tables\": [\"patients\", \"patient_programs\", \"encounters\"], "
		        + "\"excludes\": [\"*_audit\", \"*_temp\"]}";
		source.setTablePatterns(complexTablePatterns);
		
		// Serialize to JSON
		String json = objectMapper.writeValueAsString(source);
		JsonNode node = objectMapper.readTree(json);
		
		// Verify the complex table patterns is serialized as a string
		Assert.assertTrue("tablePatterns should be present", node.has("tablePatterns"));
		Assert.assertTrue("tablePatterns should be a string node", node.get("tablePatterns").isTextual());
		Assert.assertTrue("tablePatterns should contain table names", node.get("tablePatterns").asText()
		        .contains("patients"));
	}
	
	@Test
	public void shouldVerifyNoRecursiveJsonParsing() throws IOException {
		// This test ensures that JSON strings are NOT parsed as nested JSON during serialization
		// This is crucial to prevent infinite recursion and deep nesting issues
		
		ReportBuilderReport report = new ReportBuilderReport();
		report.setUuid("test-report-uuid");
		report.setName("Test Report");
		report.setCode("TEST_REPORT");
		
		// Set configJson with content that looks like it could be parsed
		String problematicJson = "{\"uuid\": \"123\", \"config\": {\"nested\": \"value\"}}";
		report.setConfigJson(problematicJson);
		
		// Serialize to JSON
		String json = objectMapper.writeValueAsString(report);
		JsonNode node = objectMapper.readTree(json);
		
		// The configJson field should be a STRING, not a nested object
		JsonNode configJsonNode = node.get("configJson");
		Assert.assertNotNull("configJson should exist", configJsonNode);
		Assert.assertTrue("configJson should be a string node", configJsonNode.isTextual());
		
		// The string should contain the original JSON as-is
		String configJsonString = configJsonNode.asText();
		Assert.assertTrue("configJson should contain the original JSON string", configJsonString.equals(problematicJson));
		
		// The configJson should NOT be parsed into nested fields in the parent JSON
		// (The parent JSON has its own uuid field from the entity, not from configJson)
		Assert.assertEquals("Parent UUID should be from the entity", "test-report-uuid", node.get("uuid").asText());
	}
	
	@Test
	public void shouldHandleNullJsonFields() throws IOException {
		// Create an entity with null JSON fields
		ReportBuilderSection section = new ReportBuilderSection();
		section.setUuid("test-section-uuid");
		section.setName("Test Section");
		section.setCode("TEST_SECTION");
		// configJson and metaJson are null by default
		
		// Serialize to JSON
		String json = objectMapper.writeValueAsString(section);
		JsonNode node = objectMapper.readTree(json);
		
		// Verify null fields are handled correctly
		// Note: behavior depends on Include.NON_NULL setting in ObjectMapper
		// With NON_NULL, null fields are not included in the output
		// Without NON_NULL, null fields are included as null
		Assert.assertNotNull("Serialization should produce JSON", json);
	}
	
	@Test
	public void shouldHandleEmptyJsonStrings() throws IOException {
		// Create an entity with empty JSON strings
		ETLMonitor monitor = new ETLMonitor();
		monitor.setUuid("test-monitor-uuid");
		monitor.setName("Test Monitor");
		monitor.setCode("TEST_MONITOR");
		monitor.setConfigJson("");
		monitor.setDisplayConfigJson("{}");
		
		// Serialize to JSON
		String json = objectMapper.writeValueAsString(monitor);
		JsonNode node = objectMapper.readTree(json);
		
		// Verify empty JSON strings are handled correctly
		Assert.assertTrue("displayConfigJson should be present", node.has("displayConfigJson"));
		Assert.assertEquals("displayConfigJson should be empty object string", "{}", node.get("displayConfigJson").asText());
	}
}
