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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Renders a {@link ReportTableModel} as CSV (RFC 4180 quoting, CRLF line endings, UTF-8 with BOM so
 * Excel detects the encoding). The grid comes from
 * {@link ReportTableModel#flattenTable(ReportTableModel.Table)}, which expands header
 * colspan/rowspan the same way the HTML thead displays them, so CSV columns line up with the HTML
 * table.
 */
public class ReportModelCsvRenderer {
	
	/**
	 * Renders the model as a CSV byte stream.
	 */
	public byte[] render(ReportTableModel model) {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			Writer w = new OutputStreamWriter(bytes, StandardCharsets.UTF_8);
			
			// UTF-8 BOM so Excel detects the encoding when opening the file directly
			w.write('﻿');
			
			appendLine(w, model.title);
			appendLine(w, model.description);
			
			if (model.emptyMessage != null && !model.emptyMessage.isEmpty()) {
				appendLine(w, model.emptyMessage);
			}
			
			for (ReportTableModel.Table table : model.tables) {
				if (table.sectionTitle != null && !table.sectionTitle.isEmpty()) {
					appendLine(w, table.sectionTitle);
				}
				
				for (List<String> row : model.flattenTable(table)) {
					appendCsvRow(w, row);
				}
				
				// Blank line between tables
				w.write("\r\n");
			}
			
			appendLine(w, model.footerText);
			
			w.flush();
			return bytes.toByteArray();
		}
		catch (IOException e) {
			throw new RuntimeException("Failed to render CSV from report model", e);
		}
	}
	
	private void appendLine(Writer w, String text) throws IOException {
		if (text != null && !text.isEmpty()) {
			w.write(escape(text));
		}
		w.write("\r\n");
	}
	
	private void appendCsvRow(Writer w, List<String> values) throws IOException {
		for (int i = 0; i < values.size(); i++) {
			if (i > 0) {
				w.write(',');
			}
			w.write(escape(values.get(i)));
		}
		w.write("\r\n");
	}
	
	/**
	 * RFC 4180 quoting: wrap in double quotes when the value contains a comma, quote, or newline,
	 * doubling embedded quotes. Additionally guards against CSV formula injection (OWASP): values
	 * starting with =, +, -, or @ are prefixed with a single quote so Excel renders them as text
	 * instead of executing them as formulas - except plain numbers, which are left untouched so
	 * negative numbers like "-5" survive.
	 */
	private String escape(String value) {
		if (value == null) {
			return "";
		}
		
		if (startsWithFormulaTrigger(value) && !isPlainNumber(value)) {
			value = "'" + value;
		}
		
		boolean needsQuoting = value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0
		        || value.indexOf('\r') >= 0;
		if (!needsQuoting) {
			return value;
		}
		return "\"" + value.replace("\"", "\"\"") + "\"";
	}
	
	private boolean startsWithFormulaTrigger(String value) {
		char first = value.charAt(0);
		return first == '=' || first == '+' || first == '-' || first == '@';
	}
	
	private boolean isPlainNumber(String value) {
		try {
			Double.parseDouble(value);
			return true;
		}
		catch (NumberFormatException e) {
			return false;
		}
	}
}
