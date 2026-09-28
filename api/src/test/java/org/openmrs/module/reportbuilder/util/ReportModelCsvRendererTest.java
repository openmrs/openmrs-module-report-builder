/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.reportbuilder.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.Test;

/**
 * Tests CSV output: BOM, RFC 4180 quoting, and header flattening that expands the HTML
 * colspan/rowspan spans so columns stay aligned.
 */
public class ReportModelCsvRendererTest {
	
	private final ReportModelCsvRenderer renderer = new ReportModelCsvRenderer();
	
	private String csv(ReportTableModel model) {
		String out = new String(renderer.render(model), StandardCharsets.UTF_8);
		if (out.startsWith("\ufeff")) {
			out = out.substring(1);
		}
		return out;
	}
	
	@Test
	public void shouldStartWithUtf8Bom() {
		byte[] bytes = renderer.render(new ReportTableModel());
		assertEquals(0xEF, bytes[0] & 0xFF);
		assertEquals(0xBB, bytes[1] & 0xFF);
		assertEquals(0xBF, bytes[2] & 0xFF);
	}
	
	@Test
	public void shouldFlattenHeaderSpansAndPadLabelRows() throws Exception {
		ReportTableModel.Table table = new ReportTableModel.Table("");
		table.headerRows.add(new ReportTableModel.Row(new ReportTableModel.Cell(ReportTableModel.Style.HEADER, "Indicator",
		        1, 2), new ReportTableModel.Cell(ReportTableModel.Style.HEADER, "20yrs+", 2, 1), new ReportTableModel.Cell(
		        ReportTableModel.Style.HEADER, "Total", 1, 2)));
		table.headerRows.add(new ReportTableModel.Row(new ReportTableModel.Cell(ReportTableModel.Style.HEADER, "Female"),
		        new ReportTableModel.Cell(ReportTableModel.Style.HEADER, "Male")));
		table.bodyRows.add(new ReportTableModel.Row(new ReportTableModel.Cell(ReportTableModel.Style.LABEL_CELL, "HT01"),
		        new ReportTableModel.Cell(ReportTableModel.Style.VALUE).numeric(5), new ReportTableModel.Cell(
		                ReportTableModel.Style.VALUE).numeric(7), new ReportTableModel.Cell(ReportTableModel.Style.VALUE)
		                .numeric(12)));
		
		ReportTableModel model = new ReportTableModel();
		model.tables.add(table);
		
		String out = csv(model);
		// Two leading empty lines: empty title and description
		String[] lines = out.split("\r\n");
		assertEquals("Indicator,20yrs+,20yrs+,Total", lines[2]);
		assertEquals("Indicator,Female,Male,Total", lines[3]);
		assertEquals("HT01,5,7,12", lines[4]);
	}
	
	@Test
	public void shouldQuoteValuesWithCommasQuotesAndNewlines() {
		ReportTableModel.Table table = new ReportTableModel.Table("");
		table.bodyRows.add(new ReportTableModel.Row(new ReportTableModel.Cell(ReportTableModel.Style.DATA,
		        "Doe, John \"JD\""), new ReportTableModel.Cell(ReportTableModel.Style.DATA, "line1\nline2")));
		
		ReportTableModel model = new ReportTableModel();
		model.tables.add(table);
		
		String out = csv(model);
		assertTrue(out.contains("\"Doe, John \"\"JD\"\"\",\"line1\nline2\""));
	}
	
	@Test
	public void shouldIncludeTitleDescriptionAndFooter() {
		ReportTableModel model = new ReportTableModel();
		model.title = "My Report";
		model.description = "A description";
		model.footerText = "2 rows";
		ReportTableModel.Table table = new ReportTableModel.Table("");
		table.bodyRows.add(new ReportTableModel.Row(new ReportTableModel.Cell(ReportTableModel.Style.DATA, "a"),
		        new ReportTableModel.Cell(ReportTableModel.Style.DATA, "b")));
		model.tables.add(table);
		
		String out = csv(model);
		String[] lines = out.split("\r\n");
		assertEquals("My Report", lines[0]);
		assertEquals("A description", lines[1]);
		assertEquals("a,b", lines[2]);
		assertEquals("2 rows", lines[4]);
	}
	
	@Test
	public void shouldReadBackAsUtf8() throws Exception {
		ReportTableModel model = new ReportTableModel();
		model.title = "Kabarole";
		ReportTableModel.Table table = new ReportTableModel.Table("");
		table.bodyRows.add(new ReportTableModel.Row(new ReportTableModel.Cell(ReportTableModel.Style.DATA, "Kabaroleé"))); // accented character round trip
		model.tables.add(table);
		
		Reader reader = new InputStreamReader(new ByteArrayInputStream(renderer.render(model)), StandardCharsets.UTF_8);
		StringBuilder sb = new StringBuilder();
		int c;
		while ((c = reader.read()) >= 0) {
			sb.append((char) c);
		}
		assertTrue(sb.toString().contains("Kabaroleé"));
	}
	
	@Test
	public void shouldPrefixFormulaLikeCellsWithApostrophe() {
		ReportTableModel model = new ReportTableModel();
		ReportTableModel.Table table = new ReportTableModel.Table("");
		table.bodyRows.add(new ReportTableModel.Row(new ReportTableModel.Cell(ReportTableModel.Style.DATA,
		        "=HYPERLINK(\"http://evil.example\", \"click\")"), new ReportTableModel.Cell(ReportTableModel.Style.DATA,
		        "@cmd"), new ReportTableModel.Cell(ReportTableModel.Style.DATA, "-5"), new ReportTableModel.Cell(
		        ReportTableModel.Style.DATA, "normal text")));
		model.tables.add(table);
		
		String out = csv(model);
		
		// Formula-like values are apostrophe-prefixed so Excel treats them as text
		assertTrue(out.contains("\"'=HYPERLINK"));
		assertTrue(out.contains("'@cmd"));
		// plain and negative numbers are untouched
		assertTrue(out.contains(",-5,normal text"));
	}
}
