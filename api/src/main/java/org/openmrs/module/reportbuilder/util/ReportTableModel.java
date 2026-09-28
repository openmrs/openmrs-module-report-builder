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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Format-neutral table model shared by the HTML, CSV and Excel renderers. The model builders (
 * {@link AggregateReportModeler}, {@link LinelistReportModeler}) walk the compiled report design
 * once and produce this structure; each format renderer then emits its own output from it, so all
 * download formats stay faithful to the HTML rendering by construction. Cells carry colspan/rowspan
 * spans (mirroring the HTML thead/tbody markup), an indent depth for hierarchical labels, and a
 * style that each renderer maps to its own visual treatment (CSS class, gray fill, font weight,
 * ...). Value cells may additionally carry the numeric value so Excel can write real numbers
 * instead of text.
 */
public class ReportTableModel {
	
	/**
	 * Visual role of a cell. Named after the HTML classes the renderers emit so the mapping is
	 * obvious: LABEL_CELL is the leading cell of an indicator row (td.label), LABEL_ROW is a
	 * full-width tr.labelRow, etc.
	 */
	public enum Style {
		TITLE, DESCRIPTION, HEADER, SECTION_LABEL, GROUP_LABEL, LABEL_ROW, LABEL_CELL, VALUE, DATA, SPACER, FOOTER
	}
	
	public static class Cell {
		
		public String text = "";
		
		/** Numeric value for VALUE cells; null when the cell is text-only or empty. */
		public Integer numericValue;
		
		/** Source column key for data cells (linelist payload needs it); null otherwise. */
		public String key;
		
		public int colspan = 1;
		
		public int rowspan = 1;
		
		/**
		 * HTML detail: the original aggregate renderer emitted the rowspan attribute on the
		 * Indicator/Total header cells even when there is a single header row (rowspan='1'), while
		 * CSV and Excel treat a rowspan of 1 as "no merge". Set only by the aggregate modeler for
		 * those cells; the HTML renderer emits the attribute whenever this is true, other formats
		 * ignore it.
		 */
		public boolean emitRowSpanAttr = false;
		
		public int indent = 0;
		
		public Style style;
		
		public Cell() {
		}
		
		public Cell(Style style) {
			this.style = style;
		}
		
		public Cell(Style style, String text) {
			this.style = style;
			this.text = text == null ? "" : text;
		}
		
		public Cell(Style style, String text, int colspan, int rowspan) {
			this(style, text);
			this.colspan = colspan;
			this.rowspan = rowspan;
		}
		
		public Cell indent(int depth) {
			this.indent = depth;
			return this;
		}
		
		public Cell numeric(Integer value) {
			this.numericValue = value;
			this.text = String.valueOf(value);
			return this;
		}
		
		public boolean isMerged() {
			return colspan > 1 || rowspan > 1;
		}
	}
	
	public static class Row {
		
		public final List<Cell> cells = new ArrayList<Cell>();
		
		public Row() {
		}
		
		public Row(Cell... cells) {
			for (Cell c : cells) {
				this.cells.add(c);
			}
		}
		
		public Row cell(Cell c) {
			cells.add(c);
			return this;
		}
	}
	
	/** One rendered table: an aggregate group table, or the single linelist table. */
	public static class Table {
		
		/** Section title rendered above the table (aggregate group title); may be empty. */
		public String sectionTitle = "";
		
		public final List<Row> headerRows = new ArrayList<Row>();
		
		public final List<Row> bodyRows = new ArrayList<Row>();
		
		public Table(String sectionTitle) {
			this.sectionTitle = sectionTitle == null ? "" : sectionTitle;
		}
		
		/**
		 * Grid width of the table: the widest flattened row, header rows first.
		 */
		public int columnCount() {
			int width = 0;
			for (Row row : headerRows) {
				width = Math.max(width, flattenedWidth(row));
			}
			for (Row row : bodyRows) {
				width = Math.max(width, flattenedWidth(row));
			}
			return width;
		}
		
		private int flattenedWidth(Row row) {
			int width = 0;
			for (Cell c : row.cells) {
				width += Math.max(1, c.colspan);
			}
			return width;
		}
	}
	
	public String title = "";
	
	public String description = "";
	
	/** Message rendered when the report has no content (no columns / no groups). */
	public String emptyMessage = "";
	
	public String footerText = "";
	
	public final List<Table> tables = new ArrayList<Table>();
	
	/**
	 * Flattens a table to a plain grid of strings for CSV output. Header cells are repeated across
	 * their colspan and rowspan so columns stay aligned (Indicator / dim label / Total repeated on
	 * every header row they span). Non-header merged cells keep their text in the first grid column
	 * and pad the rest of the span with empty strings, so section/label rows do not duplicate their
	 * text across the whole width.
	 * 
	 * @return one list per grid row, each exactly {@link Table#columnCount()} wide
	 */
	public List<List<String>> flattenTable(Table table) {
		int width = table.columnCount();
		List<List<String>> grid = new ArrayList<List<String>>();
		
		// Texts carried into upcoming rows by header rowspans, keyed by target row index
		Map<Integer, String[]> carry = new TreeMap<Integer, String[]>();
		
		flattenRows(table.headerRows, width, true, grid, carry);
		flattenRows(table.bodyRows, width, false, grid, carry);
		
		return grid;
	}
	
	private void flattenRows(List<Row> rows, int width, boolean isHeader, List<List<String>> grid,
	        Map<Integer, String[]> carry) {
		for (Row row : rows) {
			String[] out = carry.remove(grid.size());
			if (out == null) {
				out = new String[width];
			}
			
			int col = 0;
			for (Cell cell : row.cells) {
				// Skip grid columns already occupied by rowspans opened on earlier rows
				while (col < width && out[col] != null) {
					col++;
				}
				
				int colspan = Math.max(1, cell.colspan);
				for (int c = 0; c < colspan && col + c < width; c++) {
					out[col + c] = (c == 0 || isHeader) ? cell.text : "";
				}
				
				if (cell.rowspan > 1 && isHeader) {
					for (int r = 1; r < cell.rowspan; r++) {
						String[] target = carry.get(grid.size() + r);
						if (target == null) {
							target = new String[width];
							carry.put(grid.size() + r, target);
						}
						for (int c = 0; c < colspan && col + c < width; c++) {
							target[col + c] = cell.text;
						}
					}
				}
				
				col += colspan;
			}
			
			List<String> rowOut = new ArrayList<String>(width);
			for (int c = 0; c < width; c++) {
				rowOut.add(out[c] == null ? "" : out[c]);
			}
			grid.add(rowOut);
		}
	}
}
