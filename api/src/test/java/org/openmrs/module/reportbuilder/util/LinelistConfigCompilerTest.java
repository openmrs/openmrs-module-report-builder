/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark of the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.reportbuilder.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Test;

/**
 * Plain unit tests for {@link LinelistConfigCompiler}. No OpenMRS context is required because the
 * compiler is a pure v2 -&gt; legacy-config transformation.
 */
public class LinelistConfigCompilerTest {
	
	private static final ObjectMapper MAPPER = new ObjectMapper();
	
	private static final String V2_CONFIG = "{"
	        + "\"version\": 2,"
	        + "\"type\": \"LINE_LIST\","
	        + "\"categoryUuid\": \"\","
	        + "\"dataSources\": ["
	        + "  {\"uuid\":\"mamba_fact_encounter\",\"name\":\"enc\",\"type\":\"ETL\",\"role\":\"SECONDARY\","
	        + "   \"columns\":{\"Return Visit Date\":{\"name\":\"Return Visit Date\",\"type\":\"DATE\",\"sourceTable\":\"mamba_fact_encounter\"}}}"
	        + "],"
	        + "\"rowGrain\": \"PATIENT\","
	        + "\"buildMethod\": \"INDICATOR_BASED\","
	        + "\"indicatorRules\": [{\"id\":\"r1\"}],"
	        + "\"parameters\": ["
	        + "  {\"name\":\"startDate\",\"label\":\"Start Date\",\"type\":\"DATE\",\"required\":true,\"config\":{}},"
	        + "  {\"name\":\"endDate\",\"label\":\"End Date\",\"type\":\"DATE\",\"required\":true,\"config\":{}}"
	        + "],"
	        + "\"baseCohortDefinition\": {\"type\":\"SQL\",\"name\":\"Cohort\","
	        + "  \"config\":{\"sql\":\"SELECT DISTINCT a.patient_id FROM mamba_fact_encounter a WHERE a.return_visit_date BETWEEN ':startDate' AND ':endDate'\"}},"
	        + "\"dataSetDefinitions\": ["
	        + "  {\"name\":\"PATIENT_LIST\",\"type\":\"PATIENT_DATA_SET\",\"rowFilter\":{\"type\":\"SQL\",\"config\":{\"sql\":\"SELECT 1\"}},\"columns\":["
	        + "    {\"name\":\"Clinic No\",\"dataDefinition\":{\"type\":\"IDENTIFIER\",\"config\":{\"identifierTypeUuid\":\"abc\",\"preferred\":false}},\"repeatResolution\":{\"strategy\":\"LATEST\"}},"
	        + "    {\"name\":\"Full Name\",\"dataDefinition\":{\"type\":\"SQL\",\"config\":{\"sql\":\"`person`.`full_name`\"}}},"
	        + "    {\"name\":\"Gender\",\"dataDefinition\":{\"type\":\"SQL\",\"config\":{\"sql\":\"`person`.`gender`\"}}},"
	        + "    {\"name\":\"Birth Date\",\"dataDefinition\":{\"type\":\"SQL\",\"config\":{\"sql\":\"`person`.`birthdate`\"}}},"
	        + "    {\"name\":\"Age\",\"dataDefinition\":{\"type\":\"CALCULATION\",\"config\":{\"calculation\":\"AGE\",\"onDate\":true}}},"
	        + "    {\"name\":\"Return Visit Date\",\"dataDefinition\":{\"type\":\"SQL\",\"config\":{\"sql\":\"`mamba_fact_encounter`.`return_visit_date`\"}},\"repeatResolution\":{\"strategy\":\"LATEST\"}},"
	        + "    {\"name\":\"Fulfillment\",\"dataDefinition\":{\"type\":\"SQL\",\"config\":{\"sql\":\"SELECT CASE WHEN x=1 THEN 'Yes' ELSE 'No' END FROM t WHERE patient_id = :patientId LIMIT 1\"}}},"
	        + "    {\"name\":\"Telephone\",\"dataDefinition\":{\"type\":\"PERSON_ATTRIBUTE\",\"config\":{\"attributeTypeUuid\":\"tel-uuid\"}}},"
	        + "    {\"name\":\"Village\",\"dataDefinition\":{\"type\":\"SQL\",\"config\":{\"sql\":\"`person_address`.`city_village`\"}}},"
	        + "    {\"name\":\"Other\",\"dataDefinition\":{\"type\":\"SQL\",\"config\":{\"sql\":\"`custom_tbl`.`custom_col`\"}}}"
	        + "  ]}" + "]," + "\"limit\": 500" + "}";
	
	private JsonNode compile() throws Exception {
		JsonNode v2 = MAPPER.readTree(V2_CONFIG);
		return LinelistConfigCompiler.compile(v2, "My Report", "desc");
	}
	
	private JsonNode firstDataSetColumns(JsonNode out) {
		return out.path("dataSetDefinitions").path(0).path("columns");
	}
	
	private JsonNode column(JsonNode columns, String name) {
		for (JsonNode c : columns) {
			if (name.equals(c.path("name").asText())) {
				return c;
			}
		}
		throw new AssertionError("Column not found: " + name);
	}
	
	@Test
	public void stripsBuildOnlyKeysAndKeepsOnlyRuntimeKeys() throws Exception {
		JsonNode out = compile();
		Assert.assertFalse("dataSources must be stripped", out.has("dataSources"));
		Assert.assertFalse("rowGrain must be stripped", out.has("rowGrain"));
		Assert.assertFalse("buildMethod must be stripped", out.has("buildMethod"));
		Assert.assertFalse("indicatorRules must be stripped", out.has("indicatorRules"));
		Assert.assertFalse("version must be stripped", out.has("version"));
		Assert.assertFalse("type must be stripped", out.has("type"));
		Assert.assertFalse("categoryUuid must be stripped", out.has("categoryUuid"));
		Assert.assertFalse("_builder must be stripped", out.has("_builder"));
		
		Assert.assertEquals("LINELIST", out.path("reportType").asText());
		Assert.assertEquals("My Report", out.path("name").asText());
		Assert.assertEquals("desc", out.path("description").asText());
		Assert.assertEquals("FACILITY_REPORTS", out.path("category").asText());
		Assert.assertEquals(500, out.path("limit").asInt());
	}
	
	@Test
	public void unquotesBindParametersInCohortSql() throws Exception {
		JsonNode out = compile();
		String sql = out.path("baseCohortDefinition").path("config").path("sql").asText();
		Assert.assertTrue("expected :startDate, was: " + sql, sql.contains("BETWEEN :startDate AND :endDate"));
		Assert.assertFalse("quoted param must be removed: " + sql, sql.contains("':startDate'"));
	}
	
	@Test
	public void preservesFullParameterConfigIncludingBuilderOnlyFields() throws Exception {
		JsonNode out = compile();
		JsonNode params = out.path("parameters");
		Assert.assertEquals(2, params.size());
		for (JsonNode p : params) {
			// Config is now preserved for frontend UI rendering
			Assert.assertTrue("parameter config must be preserved", p.has("config"));
		}
		JsonNode startDateParam = params.path(0);
		Assert.assertEquals("startDate", startDateParam.path("name").asText());
		Assert.assertEquals("DATE", startDateParam.path("type").asText());
		Assert.assertEquals("Start Date", startDateParam.path("label").asText());
		Assert.assertTrue("startDate is required", startDateParam.path("required").asBoolean());
	}
	
	@Test
	public void mapsSimplePersonReferencesToTypedDefinitions() throws Exception {
		JsonNode columns = firstDataSetColumns(compile());
		
		JsonNode fullName = column(columns, "Full Name");
		Assert.assertEquals("PERSON_NAME", fullName.path("dataDefinition").path("type").asText());
		Assert.assertEquals("FULL_NAME", fullName.path("dataDefinition").path("config").path("type").asText());
		Assert.assertTrue(fullName.path("dataDefinition").path("config").path("preferred").asBoolean());
		
		JsonNode gender = column(columns, "Gender");
		Assert.assertEquals("PERSON_ATTRIBUTE", gender.path("dataDefinition").path("type").asText());
		Assert.assertEquals("GENDER", gender.path("dataDefinition").path("config").path("type").asText());
		
		JsonNode birth = column(columns, "Birth Date");
		Assert.assertEquals("PERSON_ATTRIBUTE", birth.path("dataDefinition").path("type").asText());
		Assert.assertEquals("BIRTHDATE", birth.path("dataDefinition").path("config").path("type").asText());
		Assert.assertEquals("BIRTHDATE_AGE", birth.path("converter").path("type").asText());
		Assert.assertEquals("MMM dd,yyyy", birth.path("converter").path("config").path("format").asText());
	}
	
	@Test
	public void resolvesCalculationOnDateAndIdentifierPreferred() throws Exception {
		JsonNode columns = firstDataSetColumns(compile());
		
		JsonNode age = column(columns, "Age");
		Assert.assertEquals("CALCULATION", age.path("dataDefinition").path("type").asText());
		Assert.assertEquals(":startDate", age.path("dataDefinition").path("config").path("onDate").asText());
		
		JsonNode clinic = column(columns, "Clinic No");
		Assert.assertEquals("IDENTIFIER", clinic.path("dataDefinition").path("type").asText());
		Assert.assertTrue("preferred must be forced true", clinic.path("dataDefinition").path("config").path("preferred")
		        .asBoolean());
		Assert.assertEquals("LATEST", clinic.path("repeatResolution").path("strategy").asText());
	}
	
	@Test
	public void compilesEtlReferenceToPerRowSubquery() throws Exception {
		JsonNode columns = firstDataSetColumns(compile());
		JsonNode rvd = column(columns, "Return Visit Date");
		Assert.assertEquals("SQL", rvd.path("dataDefinition").path("type").asText());
		String sql = rvd.path("dataDefinition").path("config").path("sql").asText();
		Assert.assertTrue("needs :patientId binding: " + sql, sql.contains(":patientId"));
		Assert.assertTrue("needs voided filter: " + sql, sql.contains("voided = 0"));
		Assert.assertTrue("needs date range: " + sql, sql.contains("BETWEEN :startDate AND :endDate"));
		Assert.assertTrue("needs latest-row ordering: " + sql, sql.contains("ORDER BY e.return_visit_date DESC LIMIT 1"));
		Assert.assertEquals("LATEST", rvd.path("repeatResolution").path("strategy").asText());
	}
	
	@Test
	public void keepsCustomSqlAndPersonAttributeAndAddressAndUnknownTableAsIs() throws Exception {
		JsonNode columns = firstDataSetColumns(compile());
		
		JsonNode fulfillment = column(columns, "Fulfillment");
		Assert.assertEquals("SQL", fulfillment.path("dataDefinition").path("type").asText());
		Assert.assertTrue("custom SQL must be kept verbatim", fulfillment.path("dataDefinition").path("config").path("sql")
		        .asText().startsWith("SELECT CASE"));
		
		JsonNode telephone = column(columns, "Telephone");
		Assert.assertEquals("PERSON_ATTRIBUTE", telephone.path("dataDefinition").path("type").asText());
		Assert.assertEquals("tel-uuid", telephone.path("dataDefinition").path("config").path("attributeTypeUuid").asText());
		
		JsonNode village = column(columns, "Village");
		Assert.assertEquals("PERSON_ADDRESS", village.path("dataDefinition").path("type").asText());
		Assert.assertEquals("ADDRESS_FIELD", village.path("dataDefinition").path("config").path("type").asText());
		Assert.assertEquals("cityVillage", village.path("dataDefinition").path("config").path("field").asText());
		
		JsonNode other = column(columns, "Other");
		Assert.assertEquals("SQL", other.path("dataDefinition").path("type").asText());
		Assert.assertEquals("`custom_tbl`.`custom_col`", other.path("dataDefinition").path("config").path("sql").asText());
	}
	
	@Test
	public void assignsSnakeCaseKeysToEveryColumn() throws Exception {
		JsonNode columns = firstDataSetColumns(compile());
		Assert.assertEquals("full_name", column(columns, "Full Name").path("key").asText());
		Assert.assertEquals("return_visit_date", column(columns, "Return Visit Date").path("key").asText());
		Assert.assertEquals("clinic_no", column(columns, "Clinic No").path("key").asText());
	}
	
	@Test
	public void defaultsParametersWhenNoneDeclared() throws Exception {
		JsonNode empty = MAPPER.readTree("{\"baseCohortDefinition\":{\"type\":\"SQL\",\"config\":{\"sql\":\"SELECT 1\"}},"
		        + "\"dataSetDefinitions\":[]}");
		JsonNode out = LinelistConfigCompiler.compile(empty, "R", null);
		Assert.assertEquals(2, out.path("parameters").size());
		Assert.assertEquals("startDate", out.path("parameters").path(0).path("name").asText());
		Assert.assertEquals("endDate", out.path("parameters").path(1).path("name").asText());
	}
}
