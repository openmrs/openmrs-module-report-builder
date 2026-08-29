package org.openmrs.module.reportbuilder.api.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.openmrs.module.reportbuilder.api.db.ReportBuilderDAO;
import org.openmrs.module.reportbuilder.api.impl.ReportBuilderServiceImpl;
import org.openmrs.module.reportbuilder.contract.LegacyGenericReportSchema;
import org.openmrs.module.reportbuilder.model.ReportBuilderReport;
import org.openmrs.module.reportbuilder.model.ReportCategory;
import org.openmrs.module.reportbuilder.model.ReportLibrary;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the compile-time identity stamp produces exactly the fields required by
 * compiledReports import to recreate/update the builder report, its compiled definition and the
 * linked ReportLibrary - and that an evaluator-style read of the resulting file keeps them intact.
 */
public class CompiledReportIdentityStampTest {
	
	private static final String[] IDENTITY_KEYS = { "reportBuilderReportUuid", "reportDefinitionUuid", "reportLibraryUuid" };
	
	private ReportBuilderServiceImpl serviceWithLibrary(final ReportLibrary library) throws Exception {
		ReportBuilderServiceImpl service = new ReportBuilderServiceImpl();
		InvocationHandler handler = new InvocationHandler() {
			
			@Override
			public Object invoke(Object proxy, Method method, Object[] args) {
				if ("getReportLibraryByBuilderReportUuid".equals(method.getName())) {
					return library;
				}
				return null;
			}
		};
		ReportBuilderDAO dao = (ReportBuilderDAO) Proxy.newProxyInstance(ReportBuilderDAO.class.getClassLoader(),
		    new Class<?>[] { ReportBuilderDAO.class }, handler);
		Field daoField = ReportBuilderServiceImpl.class.getDeclaredField("dao");
		daoField.setAccessible(true);
		daoField.set(service, dao);
		return service;
	}
	
	private ObjectNode stamp(ReportBuilderServiceImpl service, boolean linelist) throws Exception {
		ReportBuilderReport report = new ReportBuilderReport();
		report.setUuid("11111111-2222-3333-4444-555555555555");
		report.setName("Viral Blood Collection Report");
		report.setCode("VBC_01");
		
		ReportCategory category = new ReportCategory();
		category.setUuid("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
		category.setName("FACILITY_REPORTS");
		report.setCategory(category);
		
		// Written by findOrCreateReportDefinition before stamping during real compiles.
		final String compiledDefinitionUuid = "dddddddd-eeee-ffff-0000-111111111111";
		
		Method stamp = ReportBuilderServiceImpl.class.getDeclaredMethod("stampCompiledIdentity", ReportBuilderReport.class,
		    ObjectNode.class, String.class, String.class, String.class);
		stamp.setAccessible(true);
		ObjectNode target = new ObjectMapper().createObjectNode();
		target.put("code", report.getCode()); // pre-existing body key, must survive stamping
		stamp.invoke(service, report, target, linelist ? "LINE_LIST" : "AGGREGATE", category.getName(),
		    compiledDefinitionUuid);
		return target;
	}
	
	@Test
	public void aggregateStampCarriesFullIdentityAndPersistsToFile() throws Exception {
		ObjectNode stamped = stamp(serviceWithLibrary(null), false);
		
		assertEquals("Viral Blood Collection Report", stamped.path("name").asText());
		assertEquals("FACILITY_REPORTS", stamped.path("category").asText());
		assertEquals("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee", stamped.path("categoryUuid").asText());
		assertEquals("AGGREGATE", stamped.path("reportType").asText());
		assertEquals("11111111-2222-3333-4444-555555555555", stamped.path("reportBuilderReportUuid").asText());
		assertEquals("dddddddd-eeee-ffff-0000-111111111111", stamped.path("reportDefinitionUuid").asText());
		assertEquals("VBC_01", stamped.path("code").asText());
		assertFalse(stamped.has("reportLibraryUuid")); // not yet in library -> field omitted
		
		// Persist under the canonical layout shape and confirm identity survives the round trip.
		File dir = Files.createTempDirectory("reports-aggregates").toFile();
		File out = new File(dir, "aggregates_VBC_01.json");
		new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(out, stamped);
		
		ObjectMapper mapper = new ObjectMapper();
		mapper.readTree(out); // parses cleanly
		ObjectNode back = (ObjectNode) mapper.readTree(out);
		assertEquals("AGGREGATE", back.path("reportType").asText());
		assertEquals("11111111-2222-3333-4444-555555555555", back.path("reportBuilderReportUuid").asText());
		assertTrue(out.delete());
	}
	
	@Test
	public void linelistStampCarriesLibraryUuidWhenPresent() throws Exception {
		ReportLibrary library = new ReportLibrary();
		library.setUuid("99999999-8888-7777-6666-555555555555");
		
		ObjectNode stamped = stamp(serviceWithLibrary(library), true);
		
		assertEquals("LINE_LIST", stamped.path("reportType").asText());
		assertEquals("99999999-8888-7777-6666-555555555555", stamped.path("reportLibraryUuid").asText());
		for (String key : IDENTITY_KEYS) {
			assertTrue(stamped.has(key), "missing " + key);
		}
	}
	
	/**
	 * The linelist evaluator deserializes the whole design file into
	 * LegacyGenericReportSchema.ReportDefinition - this proves that parse keeps every stamped
	 * linkage field typed instead of dropping or failing on them.
	 */
	@Test
	public void evaluatorStyleParseRetainsStampedIdentity() throws Exception {
		ObjectNode stamped = stamp(serviceWithLibrary(null), true);
		
		File out = Files.createTempFile("linelist_", ".json").toFile();
		new ObjectMapper().writeValue(out, stamped);
		
		LegacyGenericReportSchema.ReportDefinition parsed = new ObjectMapper().readValue(out,
		    LegacyGenericReportSchema.ReportDefinition.class);
		
		assertEquals("Viral Blood Collection Report", parsed.getName());
		assertEquals("LINE_LIST", parsed.getReportType());
		assertEquals("FACILITY_REPORTS", parsed.getCategory());
		assertEquals("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee", parsed.getCategoryUuid());
		assertEquals("11111111-2222-3333-4444-555555555555", parsed.getReportBuilderReportUuid());
		assertEquals("dddddddd-eeee-ffff-0000-111111111111", parsed.getReportDefinitionUuid());
		assertNull(parsed.getReportLibraryUuid());
		
		// What the evaluator itself relies on still resolves to nothing harmful.
		assertNull(parsed.getDataSetDefinitions());
		assertTrue(out.delete());
	}
	
	@Test
	public void reportTypeParsingAcceptsCommonSpellings() {
		assertEquals(ReportBuilderReport.ReportType.LINE_LIST, ReportBuilderReport.ReportType.fromString("LINELIST"));
		assertEquals(ReportBuilderReport.ReportType.LINE_LIST, ReportBuilderReport.ReportType.fromString("line_list"));
		assertEquals(ReportBuilderReport.ReportType.LINE_LIST, ReportBuilderReport.ReportType.fromString("LINE_LIST"));
		assertEquals(ReportBuilderReport.ReportType.AGGREGATE, ReportBuilderReport.ReportType.fromString("aggregate"));
		assertEquals(ReportBuilderReport.ReportType.AGGREGATE, ReportBuilderReport.ReportType.fromString(null));
		assertEquals(ReportBuilderReport.ReportType.AGGREGATE, ReportBuilderReport.ReportType.fromString(""));
		assertEquals(ReportBuilderReport.ReportType.AGGREGATE, ReportBuilderReport.ReportType.fromString("nonsense"));
	}
}
