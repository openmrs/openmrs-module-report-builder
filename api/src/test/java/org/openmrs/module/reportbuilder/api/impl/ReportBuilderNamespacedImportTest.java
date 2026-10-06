package org.openmrs.module.reportbuilder.api.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.openmrs.api.context.Context;
import org.openmrs.module.reportbuilder.api.ReportBuilderService;
import org.openmrs.module.reportbuilder.model.ReportBuilderIndicator;
import org.openmrs.module.reportbuilder.model.ReportCategory;
import org.openmrs.module.reportbuilder.web.controller.dto.ImportResult;
import org.openmrs.test.BaseModuleContextSensitiveTest;

/**
 * Verifies that the bulk import tolerates content-pack layouts: entity files nested under pack
 * directories (reportbuilder/{pack}/... or configuration/{pack}/reportbuilder/...) import exactly
 * like the canonical flat layout, multiple pack subtrees merge without duplicating rows, and
 * unclassifiable files are skipped without failing the import.
 */
public class ReportBuilderNamespacedImportTest extends BaseModuleContextSensitiveTest {
	
	private static final String CATEGORY_UUID = "11111111-2222-3333-4444-555555555555";
	
	private static final String INDICATOR_UUID = "33333333-4444-5555-6666-777777777777";
	
	private ReportBuilderService service;
	
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();
	
	@Before
	public void setup() {
		service = Context.getService(ReportBuilderService.class);
	}
	
	@Test
	public void importFromDirectory_shouldStillImportCanonicalFlatLayout() throws Exception {
		write("configuration/reportbuilder/categories/cat.json", categoryJson(CATEGORY_UUID, "Flat Category"));
		
		ImportResult result = service.importFromDirectory(folder.getRoot());
		
		assertEquals("Flat layout import errors: " + result.getErrors(), 1, result.getSuccessCount());
		assertNotNull(service.getReportCategoryByUuid(CATEGORY_UUID));
	}
	
	@Test
	public void importFromDirectory_shouldImportNamespacedContentPackLayout() throws Exception {
		write("configuration/reportbuilder/testcontentpack/indicators/ind.json",
		    indicatorJson(INDICATOR_UUID, "Testcontentpack Indicator"));
		
		ImportResult result = service.importFromDirectory(folder.getRoot());
		
		assertEquals("Namespaced pack import errors: " + result.getErrors(), 1, result.getSuccessCount());
		ReportBuilderIndicator indicator = service.getReportBuilderIndicatorByUuid(INDICATOR_UUID);
		assertNotNull("Indicator from pack subtree should be persisted", indicator);
		assertEquals("Testcontentpack Indicator", indicator.getName());
	}
	
	@Test
	public void importFromDirectory_shouldImportAlternatePackRootLayout() throws Exception {
		write("configuration/testcontentpack/reportbuilder/indicators/ind.json",
		    indicatorJson(INDICATOR_UUID, "Alt Root Indicator"));
		
		ImportResult result = service.importFromDirectory(folder.getRoot());
		
		assertEquals("Alternate root import errors: " + result.getErrors(), 1, result.getSuccessCount());
		assertNotNull(service.getReportBuilderIndicatorByUuid(INDICATOR_UUID));
	}
	
	@Test
	public void importFromDirectory_shouldMergeMultiplePackSubtreesWithoutDuplicates() throws Exception {
		// Same category uuid shipped by two packs with different names - second import updates,
		// never duplicates
		write("configuration/reportbuilder/testcontentpack/categories/cat.json", categoryJson(CATEGORY_UUID, "Testcontentpack Name"));
		write("configuration/reportbuilder/testcontentpack2/categories/cat.json", categoryJson(CATEGORY_UUID, "Testcontentpack2 Name"));

		ImportResult result = service.importFromDirectory(folder.getRoot());

		assertEquals("Two-pack import errors: " + result.getErrors(), 2, result.getSuccessCount());
		ReportCategory category = service.getReportCategoryByUuid(CATEGORY_UUID);
		assertNotNull(category);
		List<ReportCategory> matches = service.getReportCategories(null, false, null, null);
		long rowsForUuid = matches.stream().filter(row -> CATEGORY_UUID.equals(row.getUuid())).count();
		assertEquals("Exactly one row expected for the shared uuid, got: " + rowsForUuid, 1, rowsForUuid);
		// testcontentpack sorts before testcontentpack2 across roots? No - both live under the same root, buckets
		// sort by path, so testcontentpack imports first and testcontentpack2's update wins.
		assertEquals("Testcontentpack2 Name", category.getName());
	}
	
	@Test
	public void importFromDirectory_shouldSkipUnclassifiableFilesWithoutFailing() throws Exception {
		write("configuration/reportbuilder/testcontentpack/notes.json", "{\"something\":\"unrelated\"}");
		write("configuration/reportbuilder/testcontentpack/indicators/ind.json",
		    indicatorJson(INDICATOR_UUID, "Good Indicator"));
		
		ImportResult result = service.importFromDirectory(folder.getRoot());
		
		assertEquals("Unclassifiable file must not block valid entities: " + result.getErrors(), 1, result.getSuccessCount());
		assertEquals(0, result.getErrorCount());
		assertNotNull(service.getReportBuilderIndicatorByUuid(INDICATOR_UUID));
	}
	
	@Test
	public void importFromDirectory_shouldFailCleanlyWhenNoReportbuilderContentExists() throws Exception {
		write("configuration/testcontentpack/some/other.json", "{}");
		
		ImportResult result = service.importFromDirectory(folder.getRoot());
		
		assertFalse(result.isSuccess());
		assertEquals(0, result.getSuccessCount());
	}
	
	@Test
	public void validatePackage_shouldAcceptCanonicalFlatNamespacedAndAlternateRootLayouts() throws Exception {
		// Flat canonical
		write("configuration/reportbuilder/indicators/ind.json", indicatorJson(INDICATOR_UUID, "A"));
		assertTrue(service.validatePackage(folder.getRoot()));
		
		// Namespaced single pack (separate tree)
		File namespaced = folder.newFolder("namespaced");
		writeUnder(namespaced, "configuration/reportbuilder/testcontentpack/indicators/ind.json",
		    indicatorJson(INDICATOR_UUID, "B"));
		assertTrue(service.validatePackage(namespaced));
		
		// Alternate root
		File alternate = folder.newFolder("alternate");
		writeUnder(alternate, "configuration/testcontentpack/reportbuilder/indicators/ind.json",
		    indicatorJson(INDICATOR_UUID, "C"));
		assertTrue(service.validatePackage(alternate));
		
		// No reportbuilder content anywhere
		File empty = folder.newFolder("no-reportbuilder");
		writeUnder(empty, "configuration/testcontentpack/other/x.json", "{}");
		assertFalse(service.validatePackage(empty));
		
		// Reportbuilder root present but no classifiable entity files
		File hollow = folder.newFolder("hollow");
		writeUnder(hollow, "configuration/reportbuilder/testcontentpack/notes.json", "{}");
		assertFalse(service.validatePackage(hollow));
	}
	
	private File write(String relativePath, String json) throws IOException {
		return writeUnder(folder.getRoot(), relativePath, json);
	}
	
	private File writeUnder(File root, String relativePath, String json) throws IOException {
		File file = new File(root, relativePath);
		assertTrue(file.getParentFile().mkdirs() || file.getParentFile().isDirectory());
		Files.write(file.toPath(), json.getBytes(StandardCharsets.UTF_8));
		return file;
	}
	
	private String categoryJson(String uuid, String name) {
		return "{\"uuid\":\"" + uuid + "\",\"name\":\"" + name + "\",\"description\":\"pack import test\"}";
	}
	
	private String indicatorJson(String uuid, String name) {
		return "{\"uuid\":\"" + uuid + "\",\"name\":\"" + name + "\",\"kind\":\"CUSTOM\"}";
	}
}
