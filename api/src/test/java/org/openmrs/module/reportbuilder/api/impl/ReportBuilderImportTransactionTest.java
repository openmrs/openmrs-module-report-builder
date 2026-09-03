package org.openmrs.module.reportbuilder.api.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.openmrs.api.context.Context;
import org.openmrs.module.reportbuilder.api.ReportBuilderService;
import org.openmrs.module.reportbuilder.model.ReportBuilderAgeCategory;
import org.openmrs.module.reportbuilder.model.ReportBuilderAgeGroup;
import org.openmrs.module.reportbuilder.model.ReportCategory;
import org.openmrs.module.reportbuilder.model.ReportLibrary;
import org.openmrs.module.reportbuilder.model.ReportBuilderDataTheme;
import org.openmrs.module.reportbuilder.model.ReportBuilderReport;
import org.openmrs.module.reportbuilder.web.controller.dto.ImportResult;
import org.openmrs.module.reportbuilder.web.controller.dto.ShippingResult;
import org.openmrs.test.BaseModuleContextSensitiveTest;

/**
 * Verifies that directory and single-entity imports commit each item in its own transaction: a
 * malformed or invalid file must be reported as a failure without rolling back or poisoning the
 * other items in the same import run.
 */
public class ReportBuilderImportTransactionTest extends BaseModuleContextSensitiveTest {
	
	private static final String GOOD_CATEGORY_UUID = "11111111-2222-3333-4444-555555555555";
	
	private static final String OTHER_CATEGORY_UUID = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
	
	private static final String REPORT_UUID = "22222222-3333-4444-5555-666666666666";
	
	private static final String REPORT_NO_CODE_UUID = "44444444-5555-6666-7777-888888888888";
	
	private static final String INDICATOR_UUID = "33333333-4444-5555-6666-777777777777";
	
	private static final String REPORT_CAMEL_UUID = "55555555-6666-7777-8888-999999999999";
	
	private static final String THEME_UUID = "66666666-7777-8888-9999-aaaaaaaaaaaa";
	
	private static final String AGE_CAT_UUID = "77777777-8888-9999-aaaa-bbbbbbbbbbbb";
	
	private static final String AGE_CAT_LEGACY_UUID = "99999999-aaaa-bbbb-cccc-dddddddddddd";
	
	private static final String AGE_CAT_EXPORT_UUID = "aaaaaaaa-bbbb-cccc-dddd-eeeeffff0000";
	
	private static final String EXPORT_CATEGORY_UUID = "88888888-9999-aaaa-bbbb-cccccccccccc";
	
	private static final String REPORT_LIB_UUID = "bbbbbbbb-cccc-dddd-eeee-ffff00001111";
	
	private static final String LIBRARY_UUID = "cccccccc-dddd-eeee-ffff-000011112222";
	
	private ReportBuilderService service;
	
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();
	
	@Before
	public void setup() {
		service = Context.getService(ReportBuilderService.class);
	}
	
	@Test
	public void importFromDirectory_shouldPersistValidItemsWhenAnotherFileIsMalformed() throws Exception {
		File sourceDir = buildCategoryPackage(goodCategoryJson(GOOD_CATEGORY_UUID, "Good Category"),
		    "{ this is not valid json");
		
		ImportResult result = service.importFromDirectory(sourceDir);
		
		assertEquals(1, result.getSuccessCount());
		assertEquals(1, result.getErrorCount());
		assertEquals("categories", result.getErrors().get(0).getType());
		assertTrue(result.getErrors().get(0).getMessage() != null
		        && !result.getErrors().get(0).getMessage().trim().isEmpty());
		
		// The valid item must be durable even though a sibling file failed
		assertNotNull("Valid category should be persisted despite the malformed sibling file",
		    service.getReportCategoryByUuid(GOOD_CATEGORY_UUID));
	}
	
	@Test
	public void importFromDirectory_shouldIsolateItemMissingRequiredField() throws Exception {
		File sourceDir = buildCategoryPackage(goodCategoryJson(GOOD_CATEGORY_UUID, "Good Category"), "{\"uuid\":\""
		        + OTHER_CATEGORY_UUID + "\",\"description\":\"no name field\"}");
		
		ImportResult result = service.importFromDirectory(sourceDir);
		
		assertEquals(1, result.getSuccessCount());
		assertEquals(1, result.getErrorCount());
		assertNotNull(service.getReportCategoryByUuid(GOOD_CATEGORY_UUID));
		assertNull(service.getReportCategoryByUuid(OTHER_CATEGORY_UUID));
	}
	
	@Test
	public void importEntity_shouldImportValidFileAndReportInvalidFileCleanly() throws Exception {
		File packageDir = buildCategoryPackage(goodCategoryJson(GOOD_CATEGORY_UUID, "Good Category"), "{\"uuid\":\""
		        + OTHER_CATEGORY_UUID + "\",\"description\":\"no name field\"}");
		
		File goodFile = new File(packageDir, "configuration/reportbuilder/categories/good.json");
		ImportResult goodResult = service.importEntity("category", goodFile);
		assertEquals(1, goodResult.getSuccessCount());
		assertNotNull(service.getReportCategoryByUuid(GOOD_CATEGORY_UUID));
		
		File badFile = new File(packageDir, "configuration/reportbuilder/categories/bad.json");
		ImportResult badResult = service.importEntity("category", badFile);
		assertEquals(1, badResult.getErrorCount());
		assertNotNull(badResult.getErrors().get(0).getMessage());
		assertNull(service.getReportCategoryByUuid(OTHER_CATEGORY_UUID));
	}
	
	@Test
	public void importFromDirectory_shouldDeriveReportCodeFromNameWhenAbsent() throws Exception {
		File reportsDir = new File(folder.getRoot(), "configuration/reportbuilder/reports");
		assertTrue(reportsDir.mkdirs());
		Files.write(new File(reportsDir, "no-code-report.json").toPath(),
		    ("{\"uuid\":\"" + REPORT_NO_CODE_UUID + "\",\"name\":\"No Code Report\",\"config_json\":{\"k\":\"v\"}}")
		            .getBytes(StandardCharsets.UTF_8));
		
		ImportResult result = service.importFromDirectory(folder.getRoot());
		
		assertEquals(1, result.getSuccessCount());
		assertEquals("No Code Report", service.getReportBuilderReportByUuid(REPORT_NO_CODE_UUID).getCode());
	}
	
	@Test
	public void importEntity_shouldDeriveIndicatorCodeFromNameWhenAbsent() throws Exception {
		File indicatorFile = writeEntityFile("indicators", "no-code-indicator.json", "{\"uuid\":\"" + INDICATOR_UUID
		        + "\",\"name\":\"No Code Indicator\",\"kind\":\"CUSTOM\"}");
		
		ImportResult result = service.importEntity("indicator", indicatorFile);
		
		assertEquals("Indicator import errors: " + result.getErrors(), 1, result.getSuccessCount());
		assertEquals("No Code Indicator", service.getReportBuilderIndicatorByUuid(INDICATOR_UUID).getCode());
	}
	
	@Test
	public void importEntity_shouldPreserveExistingCodeWhenUpdateOmitsIt() throws Exception {
		File originalFile = writeEntityFile("reports", "with-code.json", "{\"uuid\":\"" + REPORT_UUID
		        + "\",\"name\":\"Coded Report\",\"code\":\"CODED_REPORT\",\"config_json\":{\"k\":\"v\"}}");
		assertEquals(1, service.importEntity("report", originalFile).getSuccessCount());
		
		File updateFile = writeEntityFile("reports", "no-code-update.json", "{\"uuid\":\"" + REPORT_UUID
		        + "\",\"name\":\"Coded Report Renamed\",\"config_json\":{\"k\":\"v2\"}}");
		assertEquals(1, service.importEntity("report", updateFile).getSuccessCount());
		
		assertEquals("CODED_REPORT", service.getReportBuilderReportByUuid(REPORT_UUID).getCode());
	}
	
	@Test
	public void importFromDirectory_shouldReadCamelCaseConfigJsonFromExportedReport() throws Exception {
		File reportsDir = new File(folder.getRoot(), "configuration/reportbuilder/reports");
		assertTrue(reportsDir.mkdirs());
		Files.write(
		    new File(reportsDir, "camel-report.json").toPath(),
		    ("{\"uuid\":\"" + REPORT_CAMEL_UUID
		            + "\",\"name\":\"Camel Report\",\"code\":\"CAMEL_R\",\"configJson\":{\"k\":\"v\"}," + "\"reportType\":\"aggregate\",\"compileStatus\":\"COMPILED\"}")
		            .getBytes(StandardCharsets.UTF_8));
		
		ImportResult result = service.importFromDirectory(folder.getRoot());
		
		assertEquals("Report import errors: " + result.getErrors(), 1, result.getSuccessCount());
		ReportBuilderReport report = service.getReportBuilderReportByUuid(REPORT_CAMEL_UUID);
		assertNotNull(report);
		assertEquals("{\"k\":\"v\"}", report.getConfigJson());
		assertEquals(ReportBuilderReport.ReportType.AGGREGATE, report.getReportType());
		assertEquals(ReportBuilderReport.ReportCompileStatus.COMPILED, report.getCompileStatus());
	}
	
	@Test
	public void importFromDirectory_shouldReadCamelCaseConfigJsonFromExportedTheme() throws Exception {
		File themesDir = new File(folder.getRoot(), "configuration/reportbuilder/themes");
		assertTrue(themesDir.mkdirs());
		Files.write(
		    new File(themesDir, "camel-theme.json").toPath(),
		    ("{\"uuid\":\"" + THEME_UUID + "\",\"name\":\"Camel Theme\",\"code\":\"CAMEL_T\",\"domain\":\"OBSERVATIONS\"," + "\"configJson\":\"{\\\"color\\\":\\\"blue\\\"}\"}")
		            .getBytes(StandardCharsets.UTF_8));
		
		ImportResult result = service.importFromDirectory(folder.getRoot());
		
		assertEquals("Theme import errors: " + result.getErrors(), 1, result.getSuccessCount());
		ReportBuilderDataTheme theme = service.getReportBuilderDataThemeByUuid(THEME_UUID);
		assertNotNull(theme);
		assertEquals("{\"color\":\"blue\"}", theme.getConfigJson());
	}
	
	@Test
	public void importFromDirectory_shouldImportAgeGroupsEmbeddedInTheirCategory() throws Exception {
		File catsDir = new File(folder.getRoot(), "configuration/reportbuilder/age-categories");
		assertTrue(catsDir.mkdirs());
		Files.write(
		    new File(catsDir, "cat.json").toPath(),
		    ("{\"uuid\":\"" + AGE_CAT_UUID
		            + "\",\"name\":\"Adults\",\"code\":\"ADULT\",\"ageGroups\":[{\"code\":\"ADULT_15\",\"label\":\"15-49\"," + "\"minAgeDays\":5475,\"maxAgeDays\":18250,\"sortOrder\":1,\"active\":true}]}")
		            .getBytes(StandardCharsets.UTF_8));
		
		ImportResult result = service.importFromDirectory(folder.getRoot());
		
		assertEquals("Import errors: " + result.getErrors(), 1, result.getSuccessCount());
		assertNotNull(service.getAgeCategoryByUuid(AGE_CAT_UUID));
		List<ReportBuilderAgeGroup> groups = service.getAgeGroupsByCategoryUuid(AGE_CAT_UUID, null);
		assertEquals("Embedded group should be imported with its category", 1, groups.size());
		assertEquals("ADULT_15", groups.get(0).getCode());
	}
	
	@Test
	public void importEntity_shouldImportLegacyStandaloneAgeGroup() throws Exception {
		File catsDir = new File(folder.getRoot(), "configuration/reportbuilder/age-categories");
		File groupsDir = new File(folder.getRoot(), "configuration/reportbuilder/age-groups");
		assertTrue(catsDir.mkdirs());
		assertTrue(groupsDir.mkdirs());
		Files.write(new File(catsDir, "cat.json").toPath(),
		    ("{\"uuid\":\"" + AGE_CAT_LEGACY_UUID + "\",\"name\":\"Adults\",\"code\":\"ADULT_LEG\"}")
		            .getBytes(StandardCharsets.UTF_8));
		Files.write(new File(groupsDir, "grp.json").toPath(), ("{\"code\":\"ADULT_15\",\"label\":\"15-49\","
		        + "\"minAgeDays\":5475,\"maxAgeDays\":18250,\"sortOrder\":1,\"active\":true,\"ageCategory\":\""
		        + AGE_CAT_LEGACY_UUID + "\"}").getBytes(StandardCharsets.UTF_8));
		
		// Legacy packages carry the category and its groups as separate files
		assertEquals(
		    1,
		    service.importEntity("age-category",
		        new File(folder.getRoot(), "configuration/reportbuilder/age-categories/cat.json")).getSuccessCount());
		ImportResult result = service.importEntity("age-group", new File(folder.getRoot(),
		        "configuration/reportbuilder/age-groups/grp.json"));
		
		assertEquals("Legacy age-group import errors: " + result.getErrors(), 1, result.getSuccessCount());
		List<ReportBuilderAgeGroup> groups = service.getAgeGroupsByCategoryUuid(AGE_CAT_LEGACY_UUID, null);
		assertEquals(1, groups.size());
		assertEquals("ADULT_15", groups.get(0).getCode());
	}
	
	@Test
	public void shipAllEntities_shouldEmbedAgeGroupsInTheirCategoryExport() throws Exception {
		ReportBuilderAgeCategory category = new ReportBuilderAgeCategory();
		category.setUuid(AGE_CAT_EXPORT_UUID);
		category.setName("Adults Export");
		category.setCode("ADULT_EXP");
		service.saveAgeCategory(category);

		String[] groupCodes = { "ADULT_15", "ADULT_50" };
		for (int i = 0; i < groupCodes.length; i++) {
			ReportBuilderAgeGroup group = new ReportBuilderAgeGroup();
			group.setAgeCategory(category);
			group.setCode(groupCodes[i]);
			group.setLabel(groupCodes[i]);
			group.setMinAgeDays(i);
			group.setMaxAgeDays(i + 1);
			group.setSortOrder(i);
			group.setActive(true);
			service.saveAgeGroup(group);
		}

		File destination = folder.newFolder("agegroup-export");
		// Plant a legacy separate-file export to prove the retired format is cleaned up
		File legacyGroupsDir = new File(destination, "configuration/reportbuilder/age-groups");
		assertTrue(legacyGroupsDir.mkdirs());
		Files.write(new File(legacyGroupsDir, "legacy.json").toPath(), "{}".getBytes(StandardCharsets.UTF_8));

		ShippingResult result = service.shipAllEntities(Collections.singletonList("age-categories"), "1.0.0",
		    destination);

		assertTrue("Export failed: " + result.getErrorMessage(), result.isSuccess());
		File categoryFile = new File(destination, "configuration/reportbuilder/age-categories/ADULT_EXP.json");
		assertTrue("Category export file missing: " + categoryFile, categoryFile.exists());
		com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper()
		        .readTree(categoryFile);
		assertTrue("Export must embed ageGroups", node.has("ageGroups") && node.get("ageGroups").isArray());
		List<String> embeddedCodes = new ArrayList<String>();
		node.get("ageGroups").forEach(group -> embeddedCodes.add(group.get("code").asText()));
		assertEquals("Exported category JSON: " + node.toString(), Arrays.asList("ADULT_15", "ADULT_50"),
		    embeddedCodes);

		File[] legacyRemains = legacyGroupsDir.listFiles((dir, name) -> name.endsWith(".json"));
		assertEquals("Legacy separate age-group files must be cleaned from the destination", 0,
		    legacyRemains == null ? 0 : legacyRemains.length);
	}
	
	@Test
	public void shipAllEntities_shouldRemoveStaleFilesFromPreviousExports() throws Exception {
		ReportCategory exportCategory = new ReportCategory();
		exportCategory.setUuid(EXPORT_CATEGORY_UUID);
		exportCategory.setName("Export Category");
		service.saveReportCategory(exportCategory);
		File destination = folder.newFolder("destination");

		assertTrue(service.shipAllEntities(Collections.singletonList("categories"), "1.0.0", destination).isSuccess());

		// Simulate a file left behind by an earlier export of an entity that no longer exists
		File categoriesDir = new File(destination, "configuration/reportbuilder/categories");
		Files.write(new File(categoriesDir, "stale.json").toPath(),
		    "{\"uuid\":\"stale-entity\"}".getBytes(StandardCharsets.UTF_8));

		ShippingResult second = service.shipAllEntities(Collections.singletonList("categories"), "1.0.1", destination);

		assertTrue(second.isSuccess());
		List<String> remaining = Arrays.asList(categoriesDir.list((dir, name) -> name.endsWith(".json")));
		assertFalse("Stale export file should have been removed: " + remaining, remaining.contains("stale.json"));
		assertTrue("Current entity should be exported: " + remaining,
		    remaining.contains(EXPORT_CATEGORY_UUID + ".json"));
	}
	
	/**
	 * Duplicate codes are legal in production data (bombo has six sections coded CORE), so a second
	 * entity claiming the same export filename must fall back to its uuid instead of overwriting.
	 */
	@Test
	public void importFromDirectory_shouldNotDuplicateLibraryEntriesForBuilderReports() throws Exception {
		File reportsDir = new File(folder.getRoot(), "configuration/reportbuilder/reports");
		File libraryDir = new File(folder.getRoot(), "configuration/reportbuilder/library");
		assertTrue(reportsDir.mkdirs());
		assertTrue(libraryDir.mkdirs());
		Files.write(
		    new File(reportsDir, "report.json").toPath(),
		    ("{\"uuid\":\"" + REPORT_LIB_UUID + "\",\"name\":\"Lib Report\",\"code\":\"LIB_R\",\"configJson\":{\"k\":\"v\"}}")
		            .getBytes(StandardCharsets.UTF_8));
		Files.write(new File(libraryDir, "lib.json").toPath(), ("{\"uuid\":\"" + LIBRARY_UUID
		        + "\",\"name\":\"Lib Report\",\"code\":\"LIB_R\",\"sourceType\":\"BUILDER\",\"reportBuilderReportUuid\":\""
		        + REPORT_LIB_UUID + "\"}").getBytes(StandardCharsets.UTF_8));
		
		ImportResult result = service.importFromDirectory(folder.getRoot());
		
		assertEquals("Import errors: " + result.getErrors(), 2, result.getSuccessCount());
		assertNull("Library entry must dedup by the owning report", service.getReportLibraryByUuid(LIBRARY_UUID));
		List<ReportLibrary> rows = service.getReportLibraries(null, false, null, null);
		long rowsForReport = rows.stream()
		        .filter(row -> REPORT_LIB_UUID.equals(row.getReportBuilderReportUuid())).count();
		assertEquals("Exactly one library row for the report expected, got: " + rowsForReport, 1, rowsForReport);
	}
	
	@Test
	public void getFileNameForExport_shouldFallBackToUuidWhenCodeAlreadyClaimed() throws Exception {
		Method method = ReportBuilderServiceImpl.class.getDeclaredMethod("getFileNameForExport", String.class, String.class);
		method.setAccessible(true);
		Field claimedField = ReportBuilderServiceImpl.class.getDeclaredField("CURRENT_EXPORT_FILENAMES");
		claimedField.setAccessible(true);
		@SuppressWarnings("unchecked")
		ThreadLocal<java.util.Set<String>> claimed = (ThreadLocal<java.util.Set<String>>) claimedField.get(null);
		claimed.set(new java.util.HashSet<String>());
		try {
			ReportBuilderServiceImpl impl = new ReportBuilderServiceImpl();
			assertEquals("CORE.json", method.invoke(impl, "CORE", "uuid-a"));
			assertEquals("Second entity must not overwrite the first", "uuid-b.json", method.invoke(impl, "CORE", "uuid-b"));
			claimed.set(new java.util.HashSet<String>());
			assertEquals("A fresh run claims code names again", "CORE.json", method.invoke(impl, "CORE", "uuid-a"));
		}
		finally {
			claimed.remove();
		}
	}
	
	private File writeEntityFile(String typeDir, String fileName, String json) throws IOException {
		File dir = new File(folder.getRoot(), "configuration/reportbuilder/" + typeDir);
		assertTrue(dir.mkdirs() || dir.isDirectory());
		File file = new File(dir, fileName);
		Files.write(file.toPath(), json.getBytes(StandardCharsets.UTF_8));
		return file;
	}
	
	private File buildCategoryPackage(String... categoryJsons) throws IOException {
		File categoriesDir = new File(folder.getRoot(), "configuration/reportbuilder/categories");
		assertTrue(categoriesDir.mkdirs());
		
		int index = 0;
		for (String json : categoryJsons) {
			String fileName = index++ == 0 ? "good.json" : "bad.json";
			Files.write(new File(categoriesDir, fileName).toPath(), json.getBytes(StandardCharsets.UTF_8));
		}
		return folder.getRoot();
	}
	
	private String goodCategoryJson(String uuid, String name) {
		return "{\"uuid\":\"" + uuid + "\",\"name\":\"" + name + "\",\"description\":\"import test\"}";
	}
}
