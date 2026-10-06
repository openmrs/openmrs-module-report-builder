package org.openmrs.module.reportbuilder.api.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Context-free tests for the content-pack-tolerant layout discovery: entity files are classified by
 * their nearest ancestor directory named after a known entity type, so content packs can nest their
 * directories at any depth under reportbuilder roots, and multiple pack roots are located in a
 * deterministic order.
 */
public class ReportBuilderLayoutClassificationTest {
	
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();
	
	private ReportBuilderServiceImpl impl;
	
	@Before
	public void setup() {
		impl = new ReportBuilderServiceImpl();
	}
	
	@Test
	public void classifyEntityFiles_shouldClassifyCanonicalFlatLayout() throws IOException {
		write("configuration/reportbuilder/indicators/ind.json", "{}");
		write("configuration/reportbuilder/categories/cat.json", "{}");
		List<File> manifests = new ArrayList<File>();
		
		Map<String, List<File>> byType = impl.classifyEntityFiles(new File(folder.getRoot(), "configuration/reportbuilder"),
		    manifests);
		
		assertEquals(1, byType.get("indicators").size());
		assertEquals(1, byType.get("categories").size());
		assertFalse(byType.containsKey("sections"));
		assertTrue(manifests.isEmpty());
	}
	
	@Test
	public void classifyEntityFiles_shouldClassifyNamespacedContentPackLayout() throws IOException {
		write("configuration/reportbuilder/testcontentpack/indicators/ind.json", "{}");
		write("configuration/reportbuilder/testcontentpack2/age-groups/grp.json", "{}");
		List<File> manifests = new ArrayList<File>();
		
		Map<String, List<File>> byType = impl.classifyEntityFiles(new File(folder.getRoot(), "configuration/reportbuilder"),
		    manifests);
		
		assertEquals(1, byType.get("indicators").size());
		assertEquals(1, byType.get("age-groups").size());
		assertEquals("testcontentpack/indicators/ind.json classifies as indicators", new File(folder.getRoot(),
		        "configuration/reportbuilder/testcontentpack/indicators/ind.json").getAbsolutePath(),
		    byType.get("indicators").get(0).getAbsolutePath());
	}
	
	@Test
	public void classifyEntityFiles_shouldClassifyNestedSubdirectoriesUnderTypeDir() throws IOException {
		write("configuration/reportbuilder/indicators/sub/ind.json", "{}");
		List<File> manifests = new ArrayList<File>();
		
		Map<String, List<File>> byType = impl.classifyEntityFiles(new File(folder.getRoot(), "configuration/reportbuilder"),
		    manifests);
		
		assertEquals(1, byType.get("indicators").size());
	}
	
	@Test
	public void classifyEntityFiles_shouldRouteVersionJsonToManifests() throws IOException {
		write("configuration/reportbuilder/version.json", "{}");
		write("configuration/reportbuilder/testcontentpack/version.json", "{}");
		write("configuration/reportbuilder/testcontentpack/sections/sec.json", "{}");
		List<File> manifests = new ArrayList<File>();
		
		Map<String, List<File>> byType = impl.classifyEntityFiles(new File(folder.getRoot(), "configuration/reportbuilder"),
		    manifests);
		
		assertEquals(1, byType.get("sections").size());
		assertEquals(2, manifests.size());
	}
	
	@Test
	public void classifyEntityFiles_shouldSkipFilesWithNoTypeAncestor() throws IOException {
		write("configuration/reportbuilder/testcontentpack/notes.json", "{}");
		write("configuration/reportbuilder/report_designs/design.json", "{}");
		write("configuration/reportbuilder/reports/report.json", "{}");
		List<File> manifests = new ArrayList<File>();
		
		Map<String, List<File>> byType = impl.classifyEntityFiles(new File(folder.getRoot(), "configuration/reportbuilder"),
		    manifests);
		
		// "reports" is a known entity type, so its files classify; unknown dirs do not classify
		assertEquals(1, byType.get("reports").size());
		assertNull(byType.get("report_designs"));
	}
	
	@Test
	public void classifyEntityFiles_shouldSortEachBucketByPath() throws IOException {
		write("configuration/reportbuilder/testcontentpack/indicators/b.json", "{}");
		write("configuration/reportbuilder/testcontentpack/indicators/a.json", "{}");
		write("configuration/reportbuilder/indicators/c.json", "{}");
		List<File> manifests = new ArrayList<File>();
		
		Map<String, List<File>> byType = impl.classifyEntityFiles(new File(folder.getRoot(), "configuration/reportbuilder"),
		    manifests);
		
		List<File> bucket = byType.get("indicators");
		assertEquals(3, bucket.size());
		for (int i = 1; i < bucket.size(); i++) {
			assertTrue(bucket.get(i - 1).getAbsolutePath().compareTo(bucket.get(i).getAbsolutePath()) < 0);
		}
	}
	
	@Test
	public void classifyEntityFiles_shouldRebindTypeFromNearestTypeAncestor() throws IOException {
		// A dir named like a type nested inside another type dir re-binds classification
		write("configuration/reportbuilder/indicators/themes/theme.json", "{}");
		write("configuration/reportbuilder/indicators/ind.json", "{}");
		List<File> manifests = new ArrayList<File>();
		
		Map<String, List<File>> byType = impl.classifyEntityFiles(new File(folder.getRoot(), "configuration/reportbuilder"),
		    manifests);
		
		assertEquals(1, byType.get("themes").size());
		assertEquals(1, byType.get("indicators").size());
	}
	
	@Test
	public void locateReportBuilderRoots_shouldReturnCanonicalRootOnlyWhenNoPacks() throws IOException {
		assertTrue(new File(folder.getRoot(), "configuration/reportbuilder/indicators").mkdirs());
		
		List<File> roots = impl.locateReportBuilderRoots(folder.getRoot());
		
		assertEquals(1, roots.size());
		assertEquals(new File(folder.getRoot(), "configuration/reportbuilder").getAbsolutePath(), roots.get(0)
		        .getAbsolutePath());
	}
	
	@Test
	public void locateReportBuilderRoots_shouldReturnCanonicalFirstThenPacksAlphabetically() throws IOException {
		assertTrue(new File(folder.getRoot(), "configuration/reportbuilder/indicators").mkdirs());
		assertTrue(new File(folder.getRoot(), "configuration/testcontentpack2/reportbuilder/indicators").mkdirs());
		assertTrue(new File(folder.getRoot(), "configuration/testcontentpack/reportbuilder/indicators").mkdirs());
		
		List<File> roots = impl.locateReportBuilderRoots(folder.getRoot());
		
		assertEquals(3, roots.size());
		assertEquals(new File(folder.getRoot(), "configuration/reportbuilder").getAbsolutePath(), roots.get(0)
		        .getAbsolutePath());
		assertEquals(new File(folder.getRoot(), "configuration/testcontentpack/reportbuilder").getAbsolutePath(),
		    roots.get(1).getAbsolutePath());
		assertEquals(new File(folder.getRoot(), "configuration/testcontentpack2/reportbuilder").getAbsolutePath(), roots
		        .get(2).getAbsolutePath());
	}
	
	@Test
	public void locateReportBuilderRoots_shouldReturnPackRootWhenCanonicalMissing() throws IOException {
		assertTrue(new File(folder.getRoot(), "configuration/testcontentpack/reportbuilder/indicators").mkdirs());
		
		List<File> roots = impl.locateReportBuilderRoots(folder.getRoot());
		
		assertEquals(1, roots.size());
		assertEquals(new File(folder.getRoot(), "configuration/testcontentpack/reportbuilder").getAbsolutePath(),
		    roots.get(0).getAbsolutePath());
	}
	
	@Test
	public void locateReportBuilderRoots_shouldReturnEmptyWhenNoReportbuilderAnywhere() throws IOException {
		assertTrue(new File(folder.getRoot(), "configuration/testcontentpack/indicators").mkdirs());
		
		List<File> roots = impl.locateReportBuilderRoots(folder.getRoot());
		
		assertTrue(roots.isEmpty());
	}
	
	@Test
	public void collectLibraryFiles_shouldFindLibrariesInCanonicalAndPackLayouts() throws IOException {
		write("configuration/reportbuilder/library/canonical.json", "{}");
		write("configuration/reportbuilder/testcontentpack/library/pack.json", "{}");
		write("configuration/testcontentpack/reportbuilder/library/altroot.json", "{}");
		write("configuration/reportbuilder/testcontentpack/indicators/ind.json", "{}");
		
		List<File> libraryFiles = impl.collectLibraryFiles(folder.getRoot());
		
		assertEquals(3, libraryFiles.size());
		List<String> names = new ArrayList<String>();
		for (File file : libraryFiles) {
			names.add(file.getName());
		}
		assertTrue(names.contains("canonical.json"));
		assertTrue(names.contains("pack.json"));
		assertTrue(names.contains("altroot.json"));
		// Sorted by path within each root
		assertTrue(libraryFiles.get(0).getName().endsWith(".json"));
	}
	
	@Test
	public void collectLibraryFiles_shouldReturnEmptyWhenNoLibraries() throws IOException {
		write("configuration/reportbuilder/testcontentpack/indicators/ind.json", "{}");
		
		List<File> libraryFiles = impl.collectLibraryFiles(folder.getRoot());
		
		assertTrue(libraryFiles.isEmpty());
	}
	
	@Test
	public void locateReportsRoots_shouldReturnCanonicalFirstThenPackRootsAlphabetically() throws IOException {
		assertTrue(new File(folder.getRoot(), "configuration/reports/aggregates").mkdirs());
		assertTrue(new File(folder.getRoot(), "configuration/testcontentpack2/reports").mkdirs());
		assertTrue(new File(folder.getRoot(), "configuration/testcontentpack/reports").mkdirs());
		
		List<File> roots = impl.locateReportsRoots(folder.getRoot());
		
		assertEquals(3, roots.size());
		assertEquals(new File(folder.getRoot(), "configuration/reports").getAbsolutePath(), roots.get(0).getAbsolutePath());
		assertEquals(new File(folder.getRoot(), "configuration/testcontentpack/reports").getAbsolutePath(), roots.get(1)
		        .getAbsolutePath());
		assertEquals(new File(folder.getRoot(), "configuration/testcontentpack2/reports").getAbsolutePath(), roots.get(2)
		        .getAbsolutePath());
	}
	
	@Test
	public void locateReportsRoots_shouldReturnPackRootWhenCanonicalMissing() throws IOException {
		assertTrue(new File(folder.getRoot(), "configuration/testcontentpack/reports/aggregates").mkdirs());
		
		List<File> roots = impl.locateReportsRoots(folder.getRoot());
		
		assertEquals(1, roots.size());
		assertEquals(new File(folder.getRoot(), "configuration/testcontentpack/reports").getAbsolutePath(), roots.get(0)
		        .getAbsolutePath());
	}
	
	@Test
	public void locateReportsRoots_shouldReturnEmptyWhenNoReportsAnywhere() throws IOException {
		assertTrue(new File(folder.getRoot(), "configuration/reportbuilder/indicators").mkdirs());
		
		List<File> roots = impl.locateReportsRoots(folder.getRoot());
		
		assertTrue(roots.isEmpty());
	}
	
	@Test
	public void collectCompiledReportFiles_shouldMergeJsonAcrossCanonicalAndPackRootsRecursively() throws IOException {
		write("configuration/reports/aggregates/canonical.json", "{}");
		write("configuration/reports/dist/linelist/shipped.json", "{}");
		write("configuration/testcontentpack/reports/dist/aggregates/pack.json", "{}");
		// Non-reports content must not leak into the compiled-report candidates
		write("configuration/reportbuilder/indicators/ind.json", "{}");
		
		List<File> candidates = impl.collectCompiledReportFiles(folder.getRoot());
		
		assertEquals(3, candidates.size());
		List<String> names = new ArrayList<String>();
		for (File file : candidates) {
			names.add(file.getName());
		}
		assertTrue(names.contains("canonical.json"));
		assertTrue(names.contains("shipped.json"));
		assertTrue(names.contains("pack.json"));
	}
	
	private File write(String relativePath, String json) throws IOException {
		File file = new File(folder.getRoot(), relativePath);
		assertTrue(file.getParentFile().mkdirs() || file.getParentFile().isDirectory());
		Files.write(file.toPath(), json.getBytes("UTF-8"));
		return file;
	}
}
