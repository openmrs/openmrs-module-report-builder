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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a {@link ReportTableModel} from a compiled aggregate report design template and the flat
 * placeholder values extracted from the evaluated report. This is the format-neutral twin of the
 * HTML group-table walk that used to live inside {@link ReportDesignHtmlRenderer}: dimension
 * resolution, combo enumeration, key building and total fallbacks all live here, so the HTML, CSV
 * and Excel renderers all see exactly the same rows and values. The resolution primitives are
 * public because the payload-JSON path in {@link ReportDesignHtmlRenderer} shares them.
 */
public class AggregateReportModeler {
	
	/**
	 * Builds the table model. One {@link ReportTableModel.Table} per non-null group, headers
	 * carrying the same rowspan/colspan spans the HTML thead emits.
	 */
	public ReportTableModel buildModel(ReportDesignTemplate tpl, Map<String, Object> values) {
		ReportTableModel model = new ReportTableModel();
		
		if (tpl == null || tpl.groups == null || tpl.groups.isEmpty()) {
			model.emptyMessage = "No groups defined in template.";
			return model;
		}
		
		// Untrimmed on purpose: the HTML renderer escapes these exactly as authored
		model.title = tpl.name == null ? "" : tpl.name;
		
		int defaultValue = tpl.defaultValue == null ? 0 : tpl.defaultValue;
		for (Group group : tpl.groups) {
			if (group == null) {
				continue;
			}
			model.tables.add(buildGroupTable(tpl, group, values, defaultValue));
		}
		
		return model;
	}
	
	private ReportTableModel.Table buildGroupTable(ReportDesignTemplate tpl, Group group, Map<String, Object> values,
	        int defaultValue) {
		ReportTableModel.Table table = new ReportTableModel.Table(group.title == null ? "" : group.title);
		
		List<Row> rows = group.rows == null ? Collections.<Row> emptyList() : group.rows;
		
		Row firstIndicator = findFirstIndicator(rows);
		List<ResolvedDim> headerDims = firstIndicator == null ? Collections.<ResolvedDim> emptyList() : resolveDimsForRow(
		    tpl, firstIndicator);
		
		List<DimCombo> headerCombos = buildCombos(headerDims);
		boolean anyTotal = hasAnyTotal(rows);
		int totalColumnCount = 1 + (headerDims.isEmpty() ? 1 : headerCombos.size() + (anyTotal ? 1 : 0));
		
		buildHeaderRows(table, headerDims, anyTotal);
		
		for (Row row : rows) {
			if (row == null) {
				continue;
			}
			
			String type = safe(row.type);
			
			if ("section-label".equals(type)) {
				table.bodyRows.add(new ReportTableModel.Row(labelCell(ReportTableModel.Style.SECTION_LABEL, row,
				    totalColumnCount)));
				continue;
			}
			
			if ("group-label".equals(type)) {
				table.bodyRows.add(new ReportTableModel.Row(labelCell(ReportTableModel.Style.GROUP_LABEL, row,
				    totalColumnCount)));
				continue;
			}
			
			if ("label".equals(type)) {
				table.bodyRows.add(new ReportTableModel.Row(labelCell(ReportTableModel.Style.LABEL_ROW, row,
				    totalColumnCount)));
				continue;
			}
			
			if ("spacer".equals(type)) {
				table.bodyRows.add(new ReportTableModel.Row(new ReportTableModel.Cell(ReportTableModel.Style.SPACER, "",
				        totalColumnCount, 1)));
				continue;
			}
			
			if (!"indicator".equals(type)) {
				continue;
			}
			
			buildIndicatorRow(table, tpl, row, rows, values, defaultValue, anyTotal);
		}
		
		return table;
	}
	
	private void buildHeaderRows(ReportTableModel.Table table, List<ResolvedDim> headerDims, boolean anyTotal) {
		if (headerDims.isEmpty()) {
			table.headerRows.add(new ReportTableModel.Row(new ReportTableModel.Cell(ReportTableModel.Style.HEADER,
			        "Indicator"), new ReportTableModel.Cell(ReportTableModel.Style.HEADER, "Value")));
			return;
		}
		
		ReportTableModel.Cell indicatorHeader = new ReportTableModel.Cell(ReportTableModel.Style.HEADER, "Indicator", 1,
		        headerDims.size());
		indicatorHeader.emitRowSpanAttr = true;
		ReportTableModel.Row first = new ReportTableModel.Row(indicatorHeader);
		appendHeaderCells(first, headerDims, 0);
		if (anyTotal) {
			ReportTableModel.Cell totalHeader = new ReportTableModel.Cell(ReportTableModel.Style.HEADER, "Total", 1,
			        headerDims.size());
			totalHeader.emitRowSpanAttr = true;
			first.cell(totalHeader);
		}
		table.headerRows.add(first);
		
		for (int headerRow = 1; headerRow < headerDims.size(); headerRow++) {
			ReportTableModel.Row row = new ReportTableModel.Row();
			appendHeaderCells(row, headerDims, headerRow);
			table.headerRows.add(row);
		}
	}
	
	/**
	 * Mirrors the HTML thead recursion: each header row's cells repeat according to the item count
	 * of the dimensions above and span the item count of the dimensions below.
	 */
	private void appendHeaderCells(ReportTableModel.Row row, List<ResolvedDim> dims, int headerRow) {
		int repeat = 1;
		for (int i = 0; i < headerRow; i++) {
			repeat *= dims.get(i).items.size();
		}
		
		int colspan = 1;
		for (int i = headerRow + 1; i < dims.size(); i++) {
			colspan *= dims.get(i).items.size();
		}
		
		for (int r = 0; r < repeat; r++) {
			for (DimItem item : dims.get(headerRow).items) {
				// Untrimmed on purpose: the HTML renderer escapes item.label exactly as authored
				String label = item == null || item.label == null ? "" : item.label;
				row.cell(new ReportTableModel.Cell(ReportTableModel.Style.HEADER, label, colspan, 1));
			}
		}
	}
	
	private void buildIndicatorRow(ReportTableModel.Table table, ReportDesignTemplate tpl, Row row, List<Row> rows,
	        Map<String, Object> values, int defaultValue, boolean anyTotal) {
		ReportTableModel.Row out = new ReportTableModel.Row(new ReportTableModel.Cell(ReportTableModel.Style.LABEL_CELL,
		        displayLabel(row)).indent(row.indent == null ? 0 : row.indent));
		
		List<ResolvedDim> rowDims = resolveDimsForRow(tpl, row);
		List<DimCombo> rowCombos = buildCombos(rowDims);
		
		if (rowCombos.isEmpty() || !Boolean.TRUE.equals(row.showDisaggregation)) {
			Integer v = resolveSingleValue(values, row, defaultValue);
			out.cell(new ReportTableModel.Cell(ReportTableModel.Style.VALUE).numeric(v));
			
			if (anyTotal) {
				if (Boolean.TRUE.equals(row.showTotal)) {
					Integer total = resolveTotalValue(values, row, null, defaultValue);
					out.cell(new ReportTableModel.Cell(ReportTableModel.Style.VALUE).numeric(total));
				} else {
					out.cell(new ReportTableModel.Cell(ReportTableModel.Style.VALUE, ""));
				}
			}
		} else {
			for (DimCombo combo : rowCombos) {
				String key = buildKeyFlexible(safe(row.keyPattern, "{code}_{age}_{sex}"), sanitizeCode(row.code),
				    combo.placeholders);
				Integer v = coerceToInt(lookupValue(values, key));
				if (v == null) {
					v = defaultValue;
				}
				out.cell(new ReportTableModel.Cell(ReportTableModel.Style.VALUE).numeric(v));
			}
			
			if (anyTotal) {
				if (Boolean.TRUE.equals(row.showTotal)) {
					Integer total = resolveTotalValue(values, row, rowCombos, defaultValue);
					out.cell(new ReportTableModel.Cell(ReportTableModel.Style.VALUE).numeric(total));
				} else {
					out.cell(new ReportTableModel.Cell(ReportTableModel.Style.VALUE, ""));
				}
			}
		}
		
		table.bodyRows.add(out);
	}
	
	private ReportTableModel.Cell labelCell(ReportTableModel.Style style, Row row, int totalColumnCount) {
		return new ReportTableModel.Cell(style, displayLabel(row), totalColumnCount, 1).indent(row.indent == null ? 0
		        : row.indent);
	}
	
	/* -------------------- ROW RESOLUTION (shared with the payload path) -------------------- */
	
	private Row findFirstIndicator(List<Row> rows) {
		if (rows == null) {
			return null;
		}
		for (Row row : rows) {
			if (row != null && "indicator".equals(safe(row.type))) {
				return row;
			}
		}
		return null;
	}
	
	public boolean hasAnyTotal(List<Row> rows) {
		if (rows == null) {
			return false;
		}
		for (Row row : rows) {
			if (row != null && "indicator".equals(safe(row.type)) && Boolean.TRUE.equals(row.showTotal)) {
				return true;
			}
		}
		return false;
	}
	
	public Integer resolveSingleValue(Map<String, Object> values, Row row, int defaultValue) {
		String code = sanitizeCode(row.code);
		Integer v = coerceToInt(lookupValue(values, code));
		if (v != null) {
			return v;
		}
		
		String totalKey = buildTotalKey(row);
		v = coerceToInt(lookupValue(values, totalKey));
		return v == null ? defaultValue : v;
	}
	
	public Integer resolveTotalValue(Map<String, Object> values, Row row, List<DimCombo> combos, int defaultValue) {
		String totalKey = buildTotalKey(row);
		Integer v = coerceToInt(lookupValue(values, totalKey));
		if (v != null) {
			return v;
		}
		return sumComboValues(values, row, combos, defaultValue);
	}
	
	/**
	 * Falls the row total back to the sum of its disaggregation cells when the data carries no
	 * explicit total placeholder - disaggregated indicators only emit per-cell columns, so without
	 * this the Total column renders the default while the cells show values.
	 */
	public Integer sumComboValues(Map<String, Object> values, Row row, List<DimCombo> combos, int defaultValue) {
		if (combos == null || combos.isEmpty()) {
			return Integer.valueOf(defaultValue);
		}
		
		int sum = 0;
		boolean any = false;
		
		for (DimCombo combo : combos) {
			String key = buildKeyFlexible(safe(row.keyPattern, "{code}_{age}_{sex}"), sanitizeCode(row.code),
			    combo.placeholders);
			Integer v = coerceToInt(lookupValue(values, key));
			if (v != null) {
				sum += v;
				any = true;
			}
		}
		
		return Integer.valueOf(any ? sum : defaultValue);
	}
	
	public String buildTotalKey(Row row) {
		String code = sanitizeCode(row.code);
		String keyPattern = safe(row.keyPattern);
		
		if (keyPattern.contains("{code}") && !keyPattern.contains("{age}") && !keyPattern.contains("{sex}")) {
			return keyPattern.replace("{code}", code);
		}
		
		if (keyPattern.contains("{code}") && (keyPattern.contains("{age}") || keyPattern.contains("{sex}"))) {
			return code + "_TOTAL";
		}
		
		return code + "_TOTAL";
	}
	
	/**
	 * Normalizes an indicator code to the spelling used in data placeholders. The compile side
	 * sanitizes codes when building placeholder keys (non-alphanumeric runs collapse to
	 * underscores, leading/trailing separators drop), so a template code authored as "HT01a." must
	 * be spelled "HT01a" when building lookup keys - otherwise the raw code produces keys like
	 * "HT01a._Y20P_F" that no data column matches.
	 */
	public String sanitizeCode(String code) {
		String out = safe(code).replace("+", "plus").replace("<", "lt").replace(">", "gt");
		out = out.replaceAll("[^A-Za-z0-9]+", "_").replaceAll("_+", "_").replaceAll("^_", "").replaceAll("_$", "");
		return out;
	}
	
	public static class ResolvedDim {
		
		final String kind;
		
		final String dimName;
		
		final List<DimItem> items;
		
		ResolvedDim(String kind, String dimName, List<DimItem> items) {
			this.kind = kind;
			this.dimName = dimName;
			this.items = items;
		}
	}
	
	public List<ResolvedDim> resolveDimsForRow(ReportDesignTemplate tpl, Row row) {
		if (row == null || row.dims == null || row.dims.isEmpty()) {
			return Collections.emptyList();
		}
		
		LinkedHashMap<String, String> resolved = new LinkedHashMap<String, String>();
		for (Map.Entry<String, String> e : row.dims.entrySet()) {
			String kind = e.getKey();
			String dimName = valueOrDefault(e.getValue(), kind);
			if (kind == null || kind.trim().isEmpty()) {
				continue;
			}
			
			List<DimItem> items = safeDim(tpl, dimName);
			if (items != null && !items.isEmpty()) {
				resolved.put(kind, dimName);
			}
		}
		
		List<ResolvedDim> out = new ArrayList<ResolvedDim>();
		for (Map.Entry<String, String> e : resolved.entrySet()) {
			out.add(new ResolvedDim(e.getKey(), e.getValue(), safeDim(tpl, e.getValue())));
		}
		return out;
	}
	
	private String valueOrDefault(String v, String def) {
		if (v == null)
			return def;
		String t = v.trim();
		return t.length() == 0 ? def : t;
	}
	
	private List<DimItem> safeDim(ReportDesignTemplate tpl, String name) {
		if (tpl == null || tpl.dimensions == null || name == null) {
			return Collections.emptyList();
		}
		List<DimItem> d = tpl.dimensions.get(name);
		return d == null ? Collections.<DimItem> emptyList() : d;
	}
	
	public static class DimCombo {
		
		final Map<String, String> placeholders;
		
		DimCombo(Map<String, String> placeholders) {
			this.placeholders = placeholders;
		}
	}
	
	public List<DimCombo> buildCombos(List<ResolvedDim> dims) {
		if (dims == null || dims.isEmpty()) {
			return Collections.emptyList();
		}
		
		List<DimCombo> out = new ArrayList<DimCombo>();
		buildCombosRec(dims, 0, new LinkedHashMap<String, String>(), out);
		return out;
	}
	
	private void buildCombosRec(List<ResolvedDim> dims, int idx, Map<String, String> acc, List<DimCombo> out) {
		if (idx >= dims.size()) {
			out.add(new DimCombo(new LinkedHashMap<String, String>(acc)));
			return;
		}
		
		ResolvedDim d = dims.get(idx);
		for (DimItem it : d.items) {
			acc.put(d.kind, it == null ? "" : safe(it.id));
			buildCombosRec(dims, idx + 1, acc, out);
		}
		acc.remove(d.kind);
	}
	
	public String buildKeyFlexible(String pattern, String code, Map<String, String> placeholders) {
		String key = safe(pattern, "{code}");
		key = key.replace("{code}", safe(code));
		
		if (placeholders != null) {
			for (Map.Entry<String, String> e : placeholders.entrySet()) {
				String kind = e.getKey();
				String val = e.getValue();
				if (kind == null) {
					continue;
				}
				key = key.replace("{" + kind + "}", val == null ? "" : val);
			}
		}
		
		return key;
	}
	
	public Integer coerceToInt(Object raw) {
		if (raw == null)
			return null;
		if (raw instanceof Number)
			return ((Number) raw).intValue();
		
		String s = String.valueOf(raw).trim();
		if (s.isEmpty())
			return null;
		
		try {
			return (int) Math.round(Double.parseDouble(s));
		}
		catch (Exception ignore) {
			return null;
		}
	}
	
	/**
	 * Resolves the value for a compiled placeholder key, falling back to the alternative spellings
	 * the runtime may have produced. Compile-time sanitize() spells "+" as "plus" and "<"/">" as
	 * "lt"/"gt" inside design placeholder keys, while the runtime column keys built by
	 * AggregateDataSetEvaluator.sanitizeKey strip those characters - so the design key
	 * HT01a_20yrsplus_F may be stored as HT01a_20yrs_F, or lt10_yrs as 10_yrs. Exact matches always
	 * win; variants are tried only after the exact key misses.
	 */
	public Object lookupValue(Map<String, Object> values, String key) {
		if (values == null || key == null) {
			return null;
		}
		
		Object v = values.get(key);
		if (v != null) {
			return v;
		}
		
		if (key.indexOf('_') < 0) {
			return null;
		}
		
		String stripped = swapSanitizeMarkers(key, true);
		if (stripped != null) {
			v = values.get(stripped);
			if (v != null) {
				return v;
			}
		}
		
		String raw = swapSanitizeMarkers(key, false);
		if (raw != null) {
			v = values.get(raw);
		}
		return v;
	}
	
	/**
	 * Rewrites the sanitize()-introduced markers in a key's segments: toStripped=true turns
	 * "20yrsplus" into "20yrs" and "lt10"/"gt60" into "10"/"60" (the evaluator spelling);
	 * toStripped=false turns them into "20yrs+"/"&lt;10"/"&gt;60" (the raw-character spelling used
	 * by unsanitized keys). Returns null when no segment carries a marker, i.e. there is no
	 * alternative spelling to try.
	 */
	private String swapSanitizeMarkers(String key, boolean toStripped) {
		String[] segments = key.split("_", -1);
		StringBuilder sb = new StringBuilder();
		boolean changed = false;
		
		for (int i = 0; i < segments.length; i++) {
			if (i > 0) {
				sb.append('_');
			}
			
			String s = segments[i];
			String swapped;
			if (s.endsWith("plus") && s.length() > 4) {
				swapped = s.substring(0, s.length() - 4) + (toStripped ? "" : "+");
			} else if (s.startsWith("lt") && s.length() > 2 && Character.isDigit(s.charAt(2))) {
				swapped = (toStripped ? "" : "<") + s.substring(2);
			} else if (s.startsWith("gt") && s.length() > 2 && Character.isDigit(s.charAt(2))) {
				swapped = (toStripped ? "" : ">") + s.substring(2);
			} else {
				swapped = s;
			}
			
			if (!swapped.equals(s)) {
				changed = true;
			}
			sb.append(swapped);
		}
		
		return changed ? sb.toString() : null;
	}
	
	public String displayLabel(Row row) {
		String code = safe(row.code);
		String label = safe(row.label);
		
		if (code.length() > 0 && label.length() > 0 && !label.startsWith(code)) {
			return code + " " + label;
		}
		return label.length() > 0 ? label : code;
	}
	
	public String safe(String value) {
		return value == null ? "" : value.trim();
	}
	
	public String safe(String value, String defaultValue) {
		String s = safe(value);
		return s.length() == 0 ? defaultValue : s;
	}
	
	/* -------------------- POJOs -------------------- */
	
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class ReportDesignTemplate {
		
		public Integer version;
		
		public String name;
		
		public String code;
		
		public String template;
		
		public String arrayName = "dataValues";
		
		public Integer defaultValue = 0;
		
		public List<Group> groups = new ArrayList<Group>();
		
		public Map<String, List<DimItem>> dimensions = new LinkedHashMap<String, List<DimItem>>();
		
		public Dhis2 dhis2;
	}
	
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Group {
		
		public String title;
		
		public List<Row> rows = new ArrayList<Row>();
	}
	
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Row {
		
		public String type;
		
		public String label;
		
		public Integer indent;
		
		public String code;
		
		public String indicatorUuid;
		
		public String span;
		
		public String emphasis;
		
		public Boolean showTotal;
		
		public Boolean showDisaggregation;
		
		public String keyPattern;
		
		public Map<String, String> dims = new LinkedHashMap<String, String>();
	}
	
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class DimItem {
		
		public String id;
		
		public String label;
	}
	
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Dhis2 {
		
		public Boolean enabled;
		
		public List<Object> rows = new ArrayList<Object>();
	}
}
