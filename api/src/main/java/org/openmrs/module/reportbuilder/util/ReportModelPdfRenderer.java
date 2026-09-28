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

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.openmrs.module.reportbuilder.util.ReportTableModel.Cell;
import org.openmrs.module.reportbuilder.util.ReportTableModel.Row;
import org.openmrs.module.reportbuilder.util.ReportTableModel.Style;
import org.openmrs.module.reportbuilder.util.ReportTableModel.Table;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders a {@link ReportTableModel} as a PDF document that mirrors the HTML rendering: document
 * title and description, one table per group with its section title, bold headers on a light fill,
 * gray section/label rows spanning the table width, right-aligned numeric value cells and indented
 * labels. Uses OpenPDF (LGPL/MPL). PdfPTable supports colspan but not rowspan, so a header cell
 * that spans rows (Indicator/Total on multi-row dimension headers) is repeated on each header row
 * it covers - the same expansion the CSV renderer applies. Pages switch to landscape automatically
 * for wide tables (more than 6 columns).
 */
public class ReportModelPdfRenderer {
	
	/** Tables wider than this render on landscape pages. */
	private static final int LANDSCAPE_COLUMN_THRESHOLD = 6;
	
	private static final Color BORDER_GRAY = new Color(0xDD, 0xDD, 0xDD);
	
	private static final Color HEADER_FILL = new Color(0xF6, 0xF6, 0xF6);
	
	private static final Color SECTION_FILL = new Color(0xF0, 0xF0, 0xF0);
	
	private static final Color GROUP_FILL = new Color(0xFA, 0xFA, 0xFA);
	
	private static final Color LABEL_FILL = new Color(0xFC, 0xFC, 0xFC);
	
	private static final Color TEXT_GRAY = new Color(0x66, 0x66, 0x66);
	
	/**
	 * Renders the model as PDF bytes.
	 */
	public byte[] render(ReportTableModel model) {
		try {
			int width = 1;
			for (Table table : model.tables) {
				width = Math.max(width, table.columnCount());
			}
			
			Rectangle pageSize = width > LANDSCAPE_COLUMN_THRESHOLD ? PageSize.A4.rotate() : PageSize.A4;
			Document document = new Document(pageSize, 24, 24, 24, 24);
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			PdfWriter.getInstance(document, bytes);
			document.open();
			
			if (model.title != null && !model.title.isEmpty()) {
				document.add(paragraph(model.title, 14, Font.BOLD, Color.BLACK, 0, 6));
			}
			if (model.description != null && !model.description.isEmpty()) {
				document.add(paragraph(model.description, 10, Font.NORMAL, TEXT_GRAY, 0, 10));
			}
			
			if (model.emptyMessage != null && !model.emptyMessage.isEmpty()) {
				document.add(paragraph(model.emptyMessage, 10, Font.ITALIC, TEXT_GRAY, 0, 10));
			}
			
			for (Table table : model.tables) {
				if (table.sectionTitle != null && !table.sectionTitle.isEmpty()) {
					document.add(paragraph(table.sectionTitle, 12, Font.BOLD, Color.BLACK, 12, 4));
				}
				document.add(buildPdfTable(table));
				document.add(new Paragraph(8));
			}
			
			if (model.footerText != null && !model.footerText.isEmpty()) {
				document.add(paragraph(model.footerText, 8, Font.NORMAL, TEXT_GRAY, 8, 0));
			}
			
			document.close();
			return bytes.toByteArray();
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to render PDF from report model", e);
		}
	}
	
	private Paragraph paragraph(String text, int size, int style, Color color, int spacingBefore, int spacingAfter) {
		Font font = new Font(Font.HELVETICA, size, style, color);
		Paragraph p = new Paragraph(text == null ? "" : text, font);
		if (spacingBefore > 0) {
			p.setSpacingBefore(spacingBefore);
		}
		if (spacingAfter > 0) {
			p.setSpacingAfter(spacingAfter);
		}
		return p;
	}
	
	/**
	 * Builds one PDF table. Cells are placed on a grid so multi-row dimension headers stay aligned;
	 * rowspanned header cells are re-emitted on each header row they cover (PdfPTable has no
	 * rowspan). Body rows are padded to the full grid width so backgrounds span like the HTML.
	 */
	private PdfPTable buildPdfTable(Table table) {
		int width = Math.max(1, table.columnCount());
		
		PdfPTable pdfTable = new PdfPTable(width);
		pdfTable.setWidthPercentage(100);
		
		for (List<PdfPCell> rowCells : layoutHeaderRows(table.headerRows, width)) {
			for (PdfPCell cell : rowCells) {
				pdfTable.addCell(cell);
			}
		}
		
		for (Row row : table.bodyRows) {
			int filled = 0;
			for (Cell cell : row.cells) {
				pdfTable.addCell(createCell(cell, Math.max(1, cell.colspan)));
				filled += Math.max(1, cell.colspan);
			}
			while (filled < width) {
				pdfTable.addCell(emptyCell());
				filled++;
			}
		}
		
		return pdfTable;
	}
	
	/**
	 * Lays out all header rows, carrying rowspanned labels forward: a header cell with a rowspan is
	 * repeated at its column on every header row it covers, and the covered rows place their own
	 * cells after it (so dim labels land above their data columns, not one to the left).
	 */
	/* Package-private for tests */
	List<List<PdfPCell>> layoutHeaderRows(List<Row> headerRows, int width) {
		Map<Integer, String> carried = new HashMap<Integer, String>();
		Map<Integer, Integer> carriedRows = new HashMap<Integer, Integer>();
		List<List<PdfPCell>> out = new ArrayList<List<PdfPCell>>();
		
		for (Row row : headerRows) {
			// Carries opened by THIS row's own rowspanned cells must not be consumed by the
			// same pass - only carries that already existed when the row started
			List<Integer> consumed = new ArrayList<Integer>(carriedRows.keySet());
			
			out.add(layoutHeaderRow(row, width, carried, carriedRows));
			
			// Consume one covered row from each carry opened on an earlier row
			for (Integer col : consumed) {
				int left = carriedRows.get(col) - 1;
				if (left <= 0) {
					carriedRows.remove(col);
					carried.remove(col);
				} else {
					carriedRows.put(col, left);
				}
			}
		}
		return out;
	}
	
	/**
	 * Lays one header row out into exactly {@code width} grid cells, left to right: carried
	 * rowspanned labels repeat at their column, real cells render at their anchor column with their
	 * colspan, and any gap pads with an empty cell.
	 */
	/* Package-private for tests */
	List<PdfPCell> layoutHeaderRow(Row row, int width, Map<Integer, String> carried, Map<Integer, Integer> carriedRows) {
		Cell[] anchors = new Cell[width];
		boolean[] continuation = new boolean[width];
		boolean[] carriedSlot = new boolean[width];
		for (Integer c : carried.keySet()) {
			carriedSlot[c] = true;
		}
		
		int col = 0;
		for (Cell cell : row.cells) {
			while (col < width && (anchors[col] != null || carriedSlot[col])) {
				col++;
			}
			if (col >= width) {
				break;
			}
			
			anchors[col] = cell;
			int colspan = Math.max(1, cell.colspan);
			for (int c = 1; c < colspan && col + c < width; c++) {
				anchors[col + c] = cell;
				continuation[col + c] = true;
			}
			col += colspan;
		}
		
		List<PdfPCell> out = new ArrayList<PdfPCell>(width);
		for (int c = 0; c < width; c++) {
			if (carriedSlot[c]) {
				out.add(createCell(new Cell(Style.HEADER, carried.get(c)), 1));
				continue;
			}
			
			Cell cell = anchors[c];
			if (cell == null) {
				out.add(emptyCell());
				continue;
			}
			if (continuation[c]) {
				continue; // covered by the anchor cell's colspan
			}
			
			int colspan = 1;
			for (int c2 = c + 1; c2 < width && anchors[c2] == cell && continuation[c2]; c2++) {
				colspan++;
			}
			
			if (cell.rowspan > 1) {
				carried.put(c, cell.text == null ? "" : cell.text);
				carriedRows.put(c, cell.rowspan - 1);
			}
			out.add(createCell(cell, colspan));
		}
		return out;
	}
	
	private PdfPCell createCell(Cell cell, int colspan) {
		String text = cell.text == null ? "" : cell.text;
		int alignment = Element.ALIGN_LEFT;
		Font font = new Font(Font.HELVETICA, 9, Font.NORMAL, Color.BLACK);
		Color background = null;
		
		if (cell.style != null) {
			switch (cell.style) {
				case HEADER:
					alignment = Element.ALIGN_CENTER;
					font = new Font(Font.HELVETICA, 9, Font.BOLD, Color.BLACK);
					background = HEADER_FILL;
					break;
				case SECTION_LABEL:
					font = new Font(Font.HELVETICA, 10, Font.BOLD, Color.BLACK);
					background = SECTION_FILL;
					break;
				case GROUP_LABEL:
					font = new Font(Font.HELVETICA, 9, Font.BOLD, Color.BLACK);
					background = GROUP_FILL;
					break;
				case LABEL_ROW:
					font = new Font(Font.HELVETICA, 9, Font.BOLD, Color.BLACK);
					background = LABEL_FILL;
					break;
				case VALUE:
					alignment = Element.ALIGN_RIGHT;
					break;
				case FOOTER:
					font = new Font(Font.HELVETICA, 8, Font.NORMAL, TEXT_GRAY);
					break;
				default:
					break;
			}
		}
		
		PdfPCell pdfCell = new PdfPCell(new Phrase(text, font));
		pdfCell.setColspan(Math.max(1, colspan));
		pdfCell.setHorizontalAlignment(alignment);
		pdfCell.setPadding(4);
		pdfCell.setBorderColor(BORDER_GRAY);
		pdfCell.setBorderWidth(0.5f);
		if (background != null) {
			pdfCell.setBackgroundColor(background);
		}
		if (cell.indent > 0) {
			pdfCell.setPaddingLeft(4 + cell.indent * 12);
		}
		return pdfCell;
	}
	
	private PdfPCell emptyCell() {
		PdfPCell cell = new PdfPCell(new Phrase("", new Font(Font.HELVETICA, 9, Font.NORMAL, Color.BLACK)));
		cell.setBorderColor(BORDER_GRAY);
		cell.setBorderWidth(0.5f);
		cell.setPadding(4);
		return cell;
	}
}
