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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.util.HashSet;
import java.util.Set;

/**
 * Reads the rendered workbook back with POI and asserts the structural mirror of the HTML: merged
 * regions for header rowspan/colspan, real numeric value cells, sheet-per-table layout and the
 * document title row.
 */
public class ReportModelXlsxRendererTest {
	
	private final ReportModelXlsxRenderer renderer = new ReportModelXlsxRenderer();
	
	private XSSFSheet sheetOf(ReportTableModel model) throws Exception {
		XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(renderer.render(model)));
		return workbook.getSheetAt(0);
	}
	
	@Test
	public void shouldMergeHeaderSpansAndWriteNumericValues() throws Exception {
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
		model.tables.add(table);
		
		XSSFSheet sheet = sheetOf(model);
		
		// Row 0: title, row 1: section title, rows 2-3: header, row 4: data
		assertEquals("My Report", sheet.getRow(0).getCell(0).getStringCellValue());
		assertEquals("Group A", sheet.getRow(1).getCell(0).getStringCellValue());
		assertEquals("Indicator", sheet.getRow(2).getCell(0).getStringCellValue());
		assertEquals("Female", sheet.getRow(3).getCell(1).getStringCellValue());
		assertEquals("Male", sheet.getRow(3).getCell(2).getStringCellValue());
		assertEquals("HT01", sheet.getRow(4).getCell(0).getStringCellValue());
		
		// Value cells must be real numbers, not text
		assertEquals(5.0, sheet.getRow(4).getCell(1).getNumericCellValue(), 0);
		assertEquals(12.0, sheet.getRow(4).getCell(3).getNumericCellValue(), 0);
		
		// Merged regions: title row, section title, Indicator rowspan, dim colspan, Total rowspan
		Set<String> merges = new HashSet<String>();
		for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
			CellRangeAddress region = sheet.getMergedRegion(i);
			merges.add(region.getFirstRow() + ":" + region.getFirstColumn() + ":" + region.getLastRow() + ":"
			        + region.getLastColumn());
		}
		assertTrue(merges.contains("0:0:0:3")); // title across the table width
		assertTrue(merges.contains("1:0:1:3")); // section title across the table width
		assertTrue(merges.contains("2:0:3:0")); // Indicator rowspan=2
		assertTrue(merges.contains("2:1:2:2")); // 20yrs+ colspan=2
		assertTrue(merges.contains("2:3:3:3")); // Total rowspan=2
	}
	
	@Test
	public void shouldCreateOneSheetPerTableWithUniqueNames() throws Exception {
		ReportTableModel model = new ReportTableModel();
		model.tables.add(new ReportTableModel.Table("TB Section"));
		model.tables.add(new ReportTableModel.Table("TB Section"));
		model.tables.get(0).bodyRows.add(new ReportTableModel.Row(
		        new ReportTableModel.Cell(ReportTableModel.Style.DATA, "a")));
		model.tables.get(1).bodyRows.add(new ReportTableModel.Row(
		        new ReportTableModel.Cell(ReportTableModel.Style.DATA, "b")));
		
		XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(renderer.render(model)));
		
		assertEquals(2, workbook.getNumberOfSheets());
		assertEquals("TB Section", workbook.getSheetName(0));
		assertEquals("TB Section_2", workbook.getSheetName(1));
		assertNotNull(workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
	}
	
	@Test
	public void shouldWriteDataCellsAsText() throws Exception {
		ReportTableModel model = new ReportTableModel();
		ReportTableModel.Table table = new ReportTableModel.Table("");
		table.bodyRows.add(new ReportTableModel.Row(new ReportTableModel.Cell(ReportTableModel.Style.DATA, "Alice"),
		        new ReportTableModel.Cell(ReportTableModel.Style.DATA, "N/A")));
		model.tables.add(table);
		
		XSSFSheet sheet = sheetOf(model);
		assertTrue(org.apache.poi.ss.usermodel.Cell.CELL_TYPE_STRING == sheet.getRow(0).getCell(0).getCellType());
		assertEquals("Alice", sheet.getRow(0).getCell(0).getStringCellValue());
		assertEquals("N/A", sheet.getRow(0).getCell(1).getStringCellValue());
	}
}
