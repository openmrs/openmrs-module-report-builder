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

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.openmrs.module.reportbuilder.util.ReportTableModel.Cell;
import org.openmrs.module.reportbuilder.util.ReportTableModel.Row;
import org.openmrs.module.reportbuilder.util.ReportTableModel.Style;
import org.openmrs.module.reportbuilder.util.ReportTableModel.Table;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Renders a {@link ReportTableModel} as an Excel .xlsx workbook that mirrors the HTML rendering:
 * one sheet per table (aggregate groups; a single sheet for linelists), merged regions for header
 * rowspan/colspan and full-width label rows, bold headers on a light fill, gray section/label rows,
 * right-aligned numeric value cells, and cell indentation matching the HTML indent spans. Uses
 * {@link SXSSFWorkbook} so large linelists stream instead of building the whole workbook in memory.
 */
public class ReportModelXlsxRenderer {
	
	/** Default column width in characters. */
	private static final int COLUMN_WIDTH_CHARS = 18;
	
	/**
	 * Renders the model as an .xlsx byte stream.
	 */
	public byte[] render(ReportTableModel model) {
		SXSSFWorkbook workbook = new SXSSFWorkbook(100);
		try {
			Styles styles = new Styles(workbook);
			Set<String> usedSheetNames = new HashSet<String>();
			
			if (model.tables.isEmpty()) {
				Sheet sheet = workbook.createSheet(nextSheetName("Report", usedSheetNames));
				sheet.setDefaultColumnWidth(COLUMN_WIDTH_CHARS);
				int rowIndex = writeDocumentHeader(sheet, model, styles, 0);
				writeFooter(sheet, model, styles, rowIndex);
			}
			
			int tableIndex = 0;
			for (Table table : model.tables) {
				String baseName = table.sectionTitle != null && !table.sectionTitle.trim().isEmpty() ? table.sectionTitle
				        : (model.tables.size() == 1 ? "Report" : "Table " + (tableIndex + 1));
				
				Sheet sheet = workbook.createSheet(nextSheetName(baseName, usedSheetNames));
				sheet.setDefaultColumnWidth(COLUMN_WIDTH_CHARS);
				int rowIndex = 0;
				
				rowIndex = writeDocumentHeader(sheet, model, styles, rowIndex);
				
				if (table.sectionTitle != null && !table.sectionTitle.isEmpty()) {
					writeFullWidthText(sheet, table.sectionTitle, styles.sectionLabel, table.columnCount(), rowIndex, 0);
					rowIndex++;
				}
				
				int headerEnd = writeRows(sheet, table.headerRows, table.columnCount(), styles, rowIndex, true);
				rowIndex = writeRows(sheet, table.bodyRows, table.columnCount(), styles, headerEnd, false);
				
				if (headerEnd > 0) {
					sheet.createFreezePane(0, headerEnd);
				}
				
				writeFooter(sheet, model, styles, rowIndex);
				tableIndex++;
			}
			
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			workbook.write(bytes);
			return bytes.toByteArray();
		}
		catch (IOException e) {
			throw new RuntimeException("Failed to render Excel from report model", e);
		}
		finally {
			workbook.dispose();
		}
	}
	
	/** Writes the shared report title/description rows; returns the next row index. */
	private int writeDocumentHeader(Sheet sheet, ReportTableModel model, Styles styles, int rowIndex) {
		int width = 1;
		if (!model.tables.isEmpty()) {
			width = Math.max(1, model.tables.get(0).columnCount());
		}
		
		if (model.title != null && !model.title.isEmpty()) {
			writeFullWidthText(sheet, model.title, styles.title, width, rowIndex, 0);
			rowIndex++;
		}
		if (model.description != null && !model.description.isEmpty()) {
			writeFullWidthText(sheet, model.description, styles.description, width, rowIndex, 0);
			rowIndex++;
		}
		return rowIndex;
	}
	
	private void writeFooter(Sheet sheet, ReportTableModel model, Styles styles, int rowIndex) {
		if (model.footerText != null && !model.footerText.isEmpty()) {
			writeFullWidthText(sheet, model.footerText, styles.footer, 1, rowIndex, 0);
		}
	}
	
	/**
	 * Writes rows with merged regions for spans; returns the row index after the last written row.
	 * Header rows keep their rowspan/colspan spans; the returned index is also used for the freeze
	 * pane so headers stay visible while scrolling.
	 */
	private int writeRows(Sheet sheet, Iterable<Row> rows, int width, Styles styles, int startRowIndex, boolean isHeader) {
		// Grid positions occupied by spans from cells written earlier
		Set<Long> occupied = new HashSet<Long>();
		int rowIndex = startRowIndex;
		
		for (Row row : rows) {
			org.apache.poi.ss.usermodel.Row sheetRow = sheet.createRow(rowIndex);
			int col = 0;
			
			for (Cell cell : row.cells) {
				while (occupied.contains(key(rowIndex, col))) {
					col++;
				}
				
				int colspan = Math.max(1, cell.colspan);
				int rowspan = Math.max(1, cell.rowspan);
				
				// Skip spans wider/taller than the grid can hold (defensive against odd templates)
				for (int r = 0; r < rowspan; r++) {
					for (int c = 0; c < colspan; c++) {
						occupied.add(key(rowIndex + r, col + c));
					}
				}
				
				org.apache.poi.ss.usermodel.Row targetRow = sheetRow;
				for (int r = 1; r < rowspan; r++) {
					if (sheet.getRow(rowIndex + r) == null) {
						sheet.createRow(rowIndex + r);
					}
				}
				
				org.apache.poi.ss.usermodel.Cell poiCell = targetRow.createCell(col);
				writeCell(poiCell, cell, styles);
				
				if (colspan > 1 || rowspan > 1) {
					sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex + rowspan - 1, col, col + colspan - 1));
				}
				
				col += colspan;
			}
			
			rowIndex++;
		}
		
		return rowIndex;
	}
	
	private void writeCell(org.apache.poi.ss.usermodel.Cell poiCell, Cell cell, Styles styles) {
		CellStyle style = styles.forStyle(cell.style, cell.indent);
		
		if (cell.numericValue != null) {
			poiCell.setCellValue(cell.numericValue.doubleValue());
		} else {
			poiCell.setCellValue(cell.text == null ? "" : cell.text);
		}
		poiCell.setCellStyle(style);
	}
	
	/**
	 * Writes a text row merged across the given width (title, description, section titles, footer).
	 */
	private void writeFullWidthText(Sheet sheet, String text, CellStyle style, int width, int rowIndex, int col) {
		org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIndex);
		org.apache.poi.ss.usermodel.Cell cell = row.createCell(col);
		cell.setCellValue(text == null ? "" : text);
		cell.setCellStyle(style);
		if (width > 1) {
			sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, col, col + width - 1));
		}
	}
	
	private static long key(int row, int col) {
		return ((long) row << 20) | col;
	}
	
	private String nextSheetName(String base, Set<String> used) {
		String sanitized = base.replaceAll("[\\\\/*?:\\[\\]]", " ").trim();
		if (sanitized.isEmpty()) {
			sanitized = "Sheet";
		}
		String candidate = sanitized;
		int suffix = 1;
		while (used.contains(candidate.toLowerCase())) {
			String tail = "_" + (++suffix);
			candidate = sanitized.substring(0, Math.min(sanitized.length(), 31 - tail.length())) + tail;
		}
		used.add(candidate.toLowerCase());
		return candidate.substring(0, Math.min(candidate.length(), 31));
	}
	
	/**
	 * Cell styles mirroring the HTML CSS: gray fills for header/section rows, bold headers,
	 * right-aligned values, thin borders, indented labels.
	 */
	private static class Styles {
		
		final CellStyle title;
		
		final CellStyle description;
		
		final CellStyle header;
		
		final CellStyle sectionLabel;
		
		final CellStyle groupLabel;
		
		final CellStyle labelRow;
		
		final CellStyle labelCell;
		
		final CellStyle labelCellIndented;
		
		final CellStyle value;
		
		final CellStyle data;
		
		final CellStyle spacer;
		
		final CellStyle footer;
		
		Styles(SXSSFWorkbook workbook) {
			title = create(workbook, boldFont(workbook, 14, null), null, CellStyle.ALIGN_LEFT, 0);
			description = create(workbook, plainFont(workbook, 10, new Color(0x66, 0x66, 0x66)), null, CellStyle.ALIGN_LEFT,
			    0);
			header = create(workbook, boldFont(workbook, 10, null), new Color(0xF6, 0xF6, 0xF6), CellStyle.ALIGN_CENTER, 0);
			sectionLabel = create(workbook, boldFont(workbook, 11, null), new Color(0xF0, 0xF0, 0xF0), CellStyle.ALIGN_LEFT,
			    0);
			groupLabel = create(workbook, boldFont(workbook, 10, null), new Color(0xFA, 0xFA, 0xFA), CellStyle.ALIGN_LEFT, 0);
			labelRow = create(workbook, boldFont(workbook, 10, null), new Color(0xFC, 0xFC, 0xFC), CellStyle.ALIGN_LEFT, 0);
			labelCell = create(workbook, plainFont(workbook, 10, null), null, CellStyle.ALIGN_LEFT, 0);
			labelCellIndented = create(workbook, plainFont(workbook, 10, null), null, CellStyle.ALIGN_LEFT, 1);
			value = create(workbook, plainFont(workbook, 10, null), null, CellStyle.ALIGN_RIGHT, 0);
			data = create(workbook, plainFont(workbook, 10, null), null, CellStyle.ALIGN_LEFT, 0);
			spacer = create(workbook, plainFont(workbook, 10, null), null, CellStyle.ALIGN_LEFT, 0);
			footer = create(workbook, plainFont(workbook, 9, new Color(0x66, 0x66, 0x66)), null, CellStyle.ALIGN_LEFT, 0);
		}
		
		CellStyle forStyle(Style style, int indent) {
			if (style == null) {
				return data;
			}
			switch (style) {
				case HEADER:
					return header;
				case SECTION_LABEL:
					return sectionLabel;
				case GROUP_LABEL:
					return groupLabel;
				case LABEL_ROW:
					return labelRow;
				case LABEL_CELL:
					return indent > 0 ? labelCellIndented : labelCell;
				case VALUE:
					return value;
				case DATA:
					return data;
				case SPACER:
					return spacer;
				case FOOTER:
					return footer;
				default:
					return data;
			}
		}
		
		private CellStyle create(SXSSFWorkbook workbook, Font font, Color fill, short alignment, int indention) {
			CellStyle style = workbook.createCellStyle();
			style.setFont(font);
			style.setAlignment(alignment);
			style.setVerticalAlignment(CellStyle.VERTICAL_CENTER);
			if (indention > 0) {
				style.setIndention((short) indention);
			}
			if (fill != null) {
				XSSFCellStyle xssfStyle = (XSSFCellStyle) style;
				xssfStyle.setFillForegroundColor(new XSSFColor(fill));
				style.setFillPattern(CellStyle.SOLID_FOREGROUND);
			}
			// Thin borders on all sides, mirroring the HTML table's 1px cell borders
			style.setBorderTop(CellStyle.BORDER_THIN);
			style.setBorderBottom(CellStyle.BORDER_THIN);
			style.setBorderLeft(CellStyle.BORDER_THIN);
			style.setBorderRight(CellStyle.BORDER_THIN);
			return style;
		}
		
		private Font boldFont(SXSSFWorkbook workbook, int points, Color color) {
			Font font = plainFont(workbook, points, color);
			font.setBoldweight(Font.BOLDWEIGHT_BOLD);
			return font;
		}
		
		private Font plainFont(SXSSFWorkbook workbook, int points, Color color) {
			XSSFFont font = (XSSFFont) workbook.createFont();
			font.setFontName("Arial");
			font.setFontHeightInPoints((short) points);
			if (color != null) {
				font.setColor(new XSSFColor(color));
			}
			return font;
		}
	}
}
