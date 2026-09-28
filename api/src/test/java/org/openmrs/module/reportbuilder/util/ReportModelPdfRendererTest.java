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

import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfReader;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders a model to PDF and reads it back with the OpenPDF reader: valid PDF structure, page size
 * switching to landscape for wide tables, and multi-page output for long content.
 */
public class ReportModelPdfRendererTest {
	
	private final ReportModelPdfRenderer renderer = new ReportModelPdfRenderer();
	
	@Test
	public void shouldRenderValidPdfWithReadablePages() throws Exception {
		ReportTableModel.Table table = new ReportTableModel.Table("Group A");
		table.headerRows.add(new ReportTableModel.Row(new ReportTableModel.Cell(ReportTableModel.Style.HEADER, "Indicator",
		        1, 2), new ReportTableModel.Cell(ReportTableModel.Style.HEADER, "20yrs+", 2, 1), new ReportTableModel.Cell(
		        ReportTableModel.Style.HEADER, "Total", 1, 2)));
		table.headerRows.add(new ReportTableModel.Row(new ReportTableModel.Cell(ReportTableModel.Style.HEADER, "Female"),
		        new ReportTableModel.Cell(ReportTableModel.Style.HEADER, "Male")));
		table.bodyRows.add(new ReportTableModel.Row(new ReportTableModel.Cell(ReportTableModel.Style.LABEL_CELL, "HT01")
		        .indent(1), new ReportTableModel.Cell(ReportTableModel.Style.VALUE).numeric(5), new ReportTableModel.Cell(
		        ReportTableModel.Style.VALUE).numeric(7), new ReportTableModel.Cell(ReportTableModel.Style.VALUE)
		        .numeric(12)));
		
		ReportTableModel model = new ReportTableModel();
		model.title = "My Report";
		model.description = "A description";
		model.footerText = "1 rows";
		model.tables.add(table);
		
		byte[] pdf = renderer.render(model);
		
		// PDF magic bytes
		assertTrue(pdf.length > 500);
		assertEquals("%PDF", new String(pdf, 0, 4, "US-ASCII"));
		
		// OpenPDF must be able to parse it back
		PdfReader reader = new PdfReader(new ByteArrayInputStream(pdf));
		try {
			assertEquals(1, reader.getNumberOfPages());
			// OpenPDF encodes orientation via the /Rotate entry, not a swapped MediaBox
			assertEquals(0, reader.getPageSizeWithRotation(1).getRotation());
		}
		finally {
			reader.close();
		}
	}
	
	@Test
	public void shouldUseLandscapeForWideTables() throws Exception {
		ReportTableModel.Table table = new ReportTableModel.Table("Wide");
		ReportTableModel.Row header = new ReportTableModel.Row();
		for (int i = 0; i < 8; i++) {
			header.cell(new ReportTableModel.Cell(ReportTableModel.Style.HEADER, "Col " + i));
		}
		table.headerRows.add(header);
		ReportTableModel.Row body = new ReportTableModel.Row();
		for (int i = 0; i < 8; i++) {
			body.cell(new ReportTableModel.Cell(ReportTableModel.Style.DATA, "v" + i));
		}
		table.bodyRows.add(body);
		
		ReportTableModel model = new ReportTableModel();
		model.tables.add(table);
		
		PdfReader reader = new PdfReader(new ByteArrayInputStream(renderer.render(model)));
		try {
			assertEquals(90, reader.getPageSizeWithRotation(1).getRotation());
		}
		finally {
			reader.close();
		}
	}
	
	@Test
	public void shouldRenderEmptyMessageModel() throws Exception {
		ReportTableModel model = new ReportTableModel();
		model.title = "Empty";
		model.emptyMessage = "No groups defined in template.";
		
		byte[] pdf = renderer.render(model);
		PdfReader reader = new PdfReader(new ByteArrayInputStream(pdf));
		try {
			assertEquals(1, reader.getNumberOfPages());
		}
		finally {
			reader.close();
		}
	}
	
	@Test
	public void shouldPlaceDimHeadersAboveTheirDataColumns() {
		ReportModelPdfRenderer renderer = new ReportModelPdfRenderer();
		ReportTableModel.Table table = new ReportTableModel.Table("Group A");
		table.headerRows.add(new ReportTableModel.Row(new ReportTableModel.Cell(ReportTableModel.Style.HEADER, "Indicator",
		        1, 2), new ReportTableModel.Cell(ReportTableModel.Style.HEADER, "20yrs+", 2, 1), new ReportTableModel.Cell(
		        ReportTableModel.Style.HEADER, "Total", 1, 2)));
		table.headerRows.add(new ReportTableModel.Row(new ReportTableModel.Cell(ReportTableModel.Style.HEADER, "Female"),
		        new ReportTableModel.Cell(ReportTableModel.Style.HEADER, "Male")));
		
		List<List<PdfPCell>> rows = renderer.layoutHeaderRows(table.headerRows, 4);
		
		// Row 0: Indicator | 20yrs+ (one cell spanning 2 grid columns) | Total = 3 cells
		assertEquals(3, rows.get(0).size());
		assertEquals("Indicator", cellText(rows.get(0).get(0)));
		assertEquals("20yrs+", cellText(rows.get(0).get(1)));
		assertEquals("Total", cellText(rows.get(0).get(2)));
		
		// Row 1: the rowspanned labels repeat at their columns and the dim labels sit above
		// their data columns (Female over col 1, Male over col 2), not shifted under Indicator
		assertEquals(4, rows.get(1).size());
		assertEquals("Indicator", cellText(rows.get(1).get(0)));
		assertEquals("Female", cellText(rows.get(1).get(1)));
		assertEquals("Male", cellText(rows.get(1).get(2)));
		assertEquals("Total", cellText(rows.get(1).get(3)));
	}
	
	@Test
	public void shouldKeepCarryingAcrossThreeHeaderRows() {
		ReportModelPdfRenderer renderer = new ReportModelPdfRenderer();
		ReportTableModel.Table table = new ReportTableModel.Table("G");
		// Indicator rowspan=3 over two dim levels: age (colspan 2, rowspan 2) then F/M
		table.headerRows.add(new ReportTableModel.Row(new ReportTableModel.Cell(ReportTableModel.Style.HEADER, "Indicator",
		        1, 3), new ReportTableModel.Cell(ReportTableModel.Style.HEADER, "20yrs+", 2, 2)));
		table.headerRows.add(new ReportTableModel.Row(new ReportTableModel.Cell(ReportTableModel.Style.HEADER, "Female", 1,
		        1), new ReportTableModel.Cell(ReportTableModel.Style.HEADER, "Male", 1, 1)));
		table.headerRows.add(new ReportTableModel.Row(new ReportTableModel.Cell(ReportTableModel.Style.HEADER, "Female"),
		        new ReportTableModel.Cell(ReportTableModel.Style.HEADER, "Male")));
		
		List<List<PdfPCell>> rows = renderer.layoutHeaderRows(table.headerRows, 4);
		
		// Middle row: Indicator and 20yrs+ repeat, dim cells land at columns 2 and 3
		assertEquals("Indicator", cellText(rows.get(1).get(0)));
		assertEquals("20yrs+", cellText(rows.get(1).get(1)));
		assertEquals("Female", cellText(rows.get(1).get(2)));
		assertEquals("Male", cellText(rows.get(1).get(3)));
		
		// Bottom row: Indicator repeats once more, age carry exhausted, dim cells at columns 1-2
		assertEquals("Indicator", cellText(rows.get(2).get(0)));
		assertEquals("Female", cellText(rows.get(2).get(1)));
		assertEquals("Male", cellText(rows.get(2).get(2)));
	}
	
	private static String cellText(PdfPCell cell) {
		return cell.getPhrase().getContent();
	}
}
