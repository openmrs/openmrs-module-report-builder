/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.reportbuilder.api.db.hibernate;

import org.hibernate.CacheMode;
import org.hibernate.Criteria;
import org.hibernate.Query;
import org.hibernate.SQLQuery;
import org.hibernate.criterion.Restrictions;
import org.hibernate.criterion.MatchMode;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Disjunction;
import org.hibernate.criterion.Projections;
import org.hibernate.transform.AliasToEntityMapResultTransformer;
import org.hibernate.transform.Transformers;
import org.openmrs.api.context.Context;
import org.openmrs.api.db.hibernate.DbSession;
import org.openmrs.api.db.hibernate.DbSessionFactory;
import org.openmrs.module.reportbuilder.api.db.ReportBuilderDAO;
import org.openmrs.module.reportbuilder.dto.SqlPreviewResult;
import org.openmrs.module.reportbuilder.model.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hibernate.criterion.Restrictions.like;

public class HibernateReportBuilderDAO implements ReportBuilderDAO {
	
	@Autowired
	DbSessionFactory sessionFactory;
	
	/**
	 * @return the sessionFactory
	 */
	private DbSession getSession() {
		return sessionFactory.getCurrentSession();
	}
	
	/**
	 * @param sessionFactory the sessionFactory to set
	 */
	public void setSessionFactory(DbSessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}
	
	// =========================================================
	// ReportBuilderIndicator
	// =========================================================
	
	public ReportBuilderIndicator saveReportBuilderIndicator(ReportBuilderIndicator indicator) {
		getSession().saveOrUpdate(indicator);
		return indicator;
	}
	
	public ReportBuilderIndicator getReportBuilderIndicatorById(Integer id) {
		return (ReportBuilderIndicator) getSession().get(ReportBuilderIndicator.class, id);
	}
	
	public ReportBuilderIndicator getReportBuilderIndicatorByUuid(String uuid) {
		Criteria c = getSession().createCriteria(ReportBuilderIndicator.class);
		c.add(Restrictions.eq("uuid", uuid));
		return (ReportBuilderIndicator) c.uniqueResult();
	}
	
	public ReportBuilderIndicator getReportBuilderIndicatorByCode(String code) {
		if (code == null)
			return null;
		Criteria c = getSession().createCriteria(ReportBuilderIndicator.class);
		c.add(Restrictions.eq("code", code));
		return (ReportBuilderIndicator) c.uniqueResult();
	}
	
	public List<ReportBuilderIndicator> getReportBuilderIndicators(String qStr, ReportBuilderIndicator.Kind kind,
	        boolean includeRetired, Integer startIndex, Integer limit) {
		
		Criteria c = getSession().createCriteria(ReportBuilderIndicator.class);
		c.setCacheMode(CacheMode.IGNORE);
		
		if (!includeRetired)
			c.add(Restrictions.eq("retired", false));
		if (kind != null)
			c.add(Restrictions.eq("kind", kind));
		
		if (qStr != null && qStr.trim().length() > 0) {
			String q = like(qStr);
			c.add(Restrictions.or(Restrictions.ilike("name", q), Restrictions.ilike("description", q),
			    Restrictions.ilike("code", q)));
		}
		
		if (startIndex != null)
			c.setFirstResult(Math.max(0, startIndex));
		if (limit != null)
			c.setMaxResults(Math.max(1, limit));
		
		return (List<ReportBuilderIndicator>) c.list();
	}
	
	public List<ReportBuilderIndicator> getAllReportBuilderaIndicator(Integer startIndex, Integer limit) {
		
		Criteria c = getSession().createCriteria(ReportBuilderIndicator.class);
		c.setCacheMode(CacheMode.IGNORE);
		
		if (startIndex != null)
			c.setFirstResult(Math.max(0, startIndex));
		if (limit != null)
			c.setMaxResults(Math.max(1, limit));
		
		return (List<ReportBuilderIndicator>) c.list();
	}
	
	public List<ReportBuilderAgeGroup> getAgeGroups(String q, ReportBuilderAgeCategory category, Boolean activeOnly,
	        Integer startIndex, Integer limit) {
		
		Criteria c = getSession().createCriteria(ReportBuilderAgeGroup.class);
		c.setCacheMode(CacheMode.IGNORE);
		
		// filters
		if (category != null) {
			c.add(Restrictions.eq("ageCategory", category));
		}
		
		if (activeOnly != null) {
			// activeOnly=true -> active=true
			// activeOnly=false -> active=false
			c.add(Restrictions.eq("active", activeOnly));
		}
		
		if (q != null && !q.trim().isEmpty()) {
			String like = "%" + q.trim() + "%";
			c.add(Restrictions.or(Restrictions.ilike("label", like), Restrictions.ilike("code", like)));
		}
		
		// ordering: category then sort order then label
		c.createAlias("ageCategory", "ac"); // safe for ordering
		
		// paging
		if (startIndex != null)
			c.setFirstResult(Math.max(0, startIndex));
		if (limit != null)
			c.setMaxResults(Math.max(1, limit));
		
		return (List<ReportBuilderAgeGroup>) c.list();
	}
	
	public List<ReportBuilderIndicator> getReportBuilderIndicators(ReportBuilderIndicator.Kind kind, boolean includeRetired,
	        Integer startIndex, Integer limit) {
		
		Criteria c = getSession().createCriteria(ReportBuilderIndicator.class);
		c.setCacheMode(CacheMode.IGNORE);
		
		if (!includeRetired)
			c.add(Restrictions.eq("retired", false));
		if (kind != null)
			c.add(Restrictions.eq("kind", kind));
		
		if (startIndex != null)
			c.setFirstResult(Math.max(0, startIndex));
		if (limit != null)
			c.setMaxResults(Math.max(1, limit));
		
		return (List<ReportBuilderIndicator>) c.list();
	}
	
	public long getReportBuilderIndicatorsCount(String qStr, ReportBuilderIndicator.Kind kind, boolean includeRetired) {
		// simplest: HQL count
		StringBuilder hql = new StringBuilder("select count(i) from ReportBuilderIndicator i where 1=1 ");
		if (!includeRetired)
			hql.append("and i.retired = false ");
		if (kind != null)
			hql.append("and i.kind = :kind ");
		if (qStr != null && qStr.trim().length() > 0) {
			hql.append("and (lower(i.name) like :q or lower(i.description) like :q or lower(i.code) like :q) ");
		}
		
		Query q = getSession().createQuery(hql.toString());
		if (kind != null)
			q.setParameter("kind", kind);
		if (qStr != null && qStr.trim().length() > 0)
			q.setString("q", like(qStr));
		
		Number n = (Number) q.uniqueResult();
		return n == null ? 0L : n.longValue();
	}
	
	public void purgeReportBuilderIndicator(ReportBuilderIndicator indicator) {
		getSession().delete(indicator);
	}
	
	public void retireReportBuilderIndicator(ReportBuilderIndicator indicator, String reason) {
		indicator.setRetired(true);
		indicator.setRetireReason(reason);
		indicator.setDateRetired(new Date());
		indicator.setRetiredBy(Context.getAuthenticatedUser());
		getSession().saveOrUpdate(indicator);
	}
	
	public void unretireReportBuilderIndicator(ReportBuilderIndicator indicator) {
		indicator.setRetired(false);
		indicator.setRetireReason(null);
		indicator.setDateRetired(null);
		indicator.setRetiredBy(null);
		getSession().saveOrUpdate(indicator);
	}
	
	// =========================================================
	// ReportBuilderSection
	// =========================================================
	
	public ReportBuilderSection saveReportBuilderSection(ReportBuilderSection section) {
		getSession().saveOrUpdate(section);
		return section;
	}
	
	public ReportBuilderSection getReportBuilderSectionById(Integer id) {
		return (ReportBuilderSection) getSession().get(ReportBuilderSection.class, id);
	}
	
	public ReportBuilderSection getReportBuilderSectionByUuid(String uuid) {
		Criteria c = getSession().createCriteria(ReportBuilderSection.class);
		c.add(Restrictions.eq("uuid", uuid));
		return (ReportBuilderSection) c.uniqueResult();
	}
	
	public ReportBuilderSection getReportBuilderSectionByCode(String code) {
		if (code == null)
			return null;
		Criteria c = getSession().createCriteria(ReportBuilderSection.class);
		c.add(Restrictions.eq("code", code));
		return (ReportBuilderSection) c.uniqueResult();
	}
	
	public List<ReportBuilderSection> getReportBuilderSections(String qStr, boolean includeRetired, Integer startIndex,
	        Integer limit) {
		Criteria c = getSession().createCriteria(ReportBuilderSection.class);
		c.setCacheMode(CacheMode.IGNORE);
		
		if (!includeRetired)
			c.add(Restrictions.eq("retired", false));
		
		if (qStr != null && qStr.trim().length() > 0) {
			String q = like(qStr);
			c.add(Restrictions.or(Restrictions.ilike("name", q), Restrictions.ilike("description", q),
			    Restrictions.ilike("code", q)));
		}
		
		if (startIndex != null)
			c.setFirstResult(Math.max(0, startIndex));
		if (limit != null)
			c.setMaxResults(Math.max(1, limit));
		
		return (List<ReportBuilderSection>) c.list();
	}
	
	public long getReportBuilderSectionsCount(String qStr, boolean includeRetired) {
		StringBuilder hql = new StringBuilder("select count(s) from ReportBuilderSection s where 1=1 ");
		if (!includeRetired)
			hql.append("and s.retired = false ");
		if (qStr != null && qStr.trim().length() > 0) {
			hql.append("and (lower(s.name) like :q or lower(s.description) like :q or lower(s.code) like :q) ");
		}
		
		Query q = getSession().createQuery(hql.toString());
		if (qStr != null && qStr.trim().length() > 0)
			q.setString("q", like(qStr));
		
		Number n = (Number) q.uniqueResult();
		return n == null ? 0L : n.longValue();
	}
	
	public void purgeReportBuilderSection(ReportBuilderSection section) {
		getSession().delete(section);
	}
	
	// =========================================================
	// ReportBuilderDataTheme
	// =========================================================
	
	public ReportBuilderDataTheme saveReportBuilderDataTheme(ReportBuilderDataTheme theme) {
		getSession().saveOrUpdate(theme);
		return theme;
	}
	
	public ReportBuilderDataTheme getReportBuilderDataThemeById(Integer id) {
		return (ReportBuilderDataTheme) getSession().get(ReportBuilderDataTheme.class, id);
	}
	
	public ReportBuilderDataTheme getReportBuilderDataThemeByUuid(String uuid) {
		Criteria c = getSession().createCriteria(ReportBuilderDataTheme.class);
		c.add(Restrictions.eq("uuid", uuid));
		return (ReportBuilderDataTheme) c.uniqueResult();
	}
	
	public ReportBuilderDataTheme getReportBuilderDataThemeByCode(String code) {
		if (code == null)
			return null;
		Criteria c = getSession().createCriteria(ReportBuilderDataTheme.class);
		c.add(Restrictions.eq("code", code));
		return (ReportBuilderDataTheme) c.uniqueResult();
	}
	
	public List<ReportBuilderDataTheme> getReportBuilderDataThemes(String qStr, boolean includeRetired, Integer startIndex,
	        Integer limit) {
		
		Criteria c = getSession().createCriteria(ReportBuilderDataTheme.class);
		c.setCacheMode(CacheMode.IGNORE);
		
		if (!includeRetired)
			c.add(Restrictions.eq("retired", false));
		
		if (qStr != null && qStr.trim().length() > 0) {
			String q = like(qStr);
			c.add(Restrictions.or(Restrictions.ilike("name", q), Restrictions.ilike("description", q),
			    Restrictions.ilike("code", q)));
		}
		
		if (startIndex != null)
			c.setFirstResult(Math.max(0, startIndex));
		if (limit != null)
			c.setMaxResults(Math.max(1, limit));
		
		return (List<ReportBuilderDataTheme>) c.list();
	}
	
	public long getReportBuilderThemesCount(String qStr, boolean includeRetired) {
		StringBuilder hql = new StringBuilder("select count(t) from ReportBuilderDataTheme t where 1=1 ");
		if (!includeRetired)
			hql.append("and t.retired = false ");
		if (qStr != null && qStr.trim().length() > 0) {
			hql.append("and (lower(t.name) like :q or lower(t.description) like :q or lower(t.code) like :q) ");
		}
		
		Query q = getSession().createQuery(hql.toString());
		if (qStr != null && qStr.trim().length() > 0)
			q.setString("q", like(qStr));
		
		Number n = (Number) q.uniqueResult();
		return n == null ? 0L : n.longValue();
	}
	
	public void purgeReportBuilderDataTheme(ReportBuilderDataTheme theme) {
		getSession().delete(theme);
	}
	
	// =========================================================
	// Age Categories
	// =========================================================
	
	public ReportBuilderAgeCategory saveAgeCategory(ReportBuilderAgeCategory category) {
		getSession().saveOrUpdate(category);
		return category;
	}
	
	public ReportBuilderAgeCategory getAgeCategoryById(Integer id) {
		return (ReportBuilderAgeCategory) getSession().get(ReportBuilderAgeCategory.class, id);
	}
	
	public ReportBuilderAgeCategory getAgeCategoryByUuid(String uuid) {
		Criteria c = getSession().createCriteria(ReportBuilderAgeCategory.class);
		c.add(Restrictions.eq("uuid", uuid));
		return (ReportBuilderAgeCategory) c.uniqueResult();
	}
	
	public ReportBuilderAgeCategory getAgeCategoryByCode(String code) {
		Criteria c = getSession().createCriteria(ReportBuilderAgeCategory.class);
		c.add(Restrictions.eq("code", code));
		return (ReportBuilderAgeCategory) c.uniqueResult();
	}
	
	public List<ReportBuilderAgeCategory> getAgeCategories(String qStr, boolean includeRetired, Boolean activeOnly,
	        Integer startIndex, Integer limit) {
		
		Criteria c = getSession().createCriteria(ReportBuilderAgeCategory.class);
		c.setCacheMode(CacheMode.IGNORE);
		
		if (!includeRetired)
			c.add(Restrictions.eq("retired", false));
		if (activeOnly != null && activeOnly)
			c.add(Restrictions.eq("active", true));
		
		if (qStr != null && qStr.trim().length() > 0) {
			String q = like(qStr);
			c.add(Restrictions.or(Restrictions.ilike("name", q), Restrictions.ilike("description", q),
			    Restrictions.ilike("code", q)));
		}
		
		if (startIndex != null)
			c.setFirstResult(Math.max(0, startIndex));
		if (limit != null)
			c.setMaxResults(Math.max(1, limit));
		
		return (List<ReportBuilderAgeCategory>) c.list();
	}
	
	public long getAgeCategoriesCount(String qStr, boolean includeRetired, Boolean activeOnly) {
		StringBuilder hql = new StringBuilder("select count(c) from ReportBuilderAgeCategory c where 1=1 ");
		if (!includeRetired)
			hql.append("and c.retired = false ");
		if (activeOnly != null && activeOnly)
			hql.append("and c.active = true ");
		if (qStr != null && qStr.trim().length() > 0) {
			hql.append("and (lower(c.name) like :q or lower(c.description) like :q or lower(c.code) like :q) ");
		}
		
		Query q = getSession().createQuery(hql.toString());
		if (qStr != null && qStr.trim().length() > 0)
			q.setString("q", like(qStr));
		
		Number n = (Number) q.uniqueResult();
		return n == null ? 0L : n.longValue();
	}
	
	public void purgeAgeCategory(ReportBuilderAgeCategory category) {
		getSession().delete(category);
	}
	
	// =========================================================
	// Age Groups
	// =========================================================
	
	public ReportBuilderAgeGroup saveAgeGroup(ReportBuilderAgeGroup group) {
		getSession().saveOrUpdate(group);
		return group;
	}
	
	public ReportBuilderAgeGroup getAgeGroupById(Integer id) {
		return (ReportBuilderAgeGroup) getSession().get(ReportBuilderAgeGroup.class, id);
	}
	
	public List<ReportBuilderAgeGroup> getAgeGroupsByCategoryUuid(String categoryUuid, Boolean activeOnly) {
		StringBuilder hql = new StringBuilder("select g from ReportBuilderAgeGroup g where g.ageCategory.uuid = :uuid ");
		if (activeOnly != null && activeOnly)
			hql.append("and g.active = true ");
		hql.append("order by g.sortOrder asc");
		
		Query q = getSession().createQuery(hql.toString());
		q.setString("uuid", categoryUuid);
		return (List<ReportBuilderAgeGroup>) q.list();
	}
	
	public List<ReportBuilderAgeGroup> getAgeGroupsByCategoryCode(String categoryCode, Boolean activeOnly) {
		StringBuilder hql = new StringBuilder("select g from ReportBuilderAgeGroup g where g.ageCategory.code = :code ");
		if (activeOnly != null && activeOnly)
			hql.append("and g.active = true ");
		hql.append("order by g.sortOrder asc");
		
		Query q = getSession().createQuery(hql.toString());
		q.setString("code", categoryCode);
		return (List<ReportBuilderAgeGroup>) q.list();
	}
	
	public void purgeAgeGroup(ReportBuilderAgeGroup group) {
		getSession().delete(group);
	}
	
	// =========================================================
	// Utility: list ETL source tables for Theme Builder UI
	// =========================================================
	
	@SuppressWarnings("unchecked")
	public List<String> getETLTables(List<String> allowedPrefixes) {
		
		if (allowedPrefixes == null || allowedPrefixes.isEmpty()) {
			return Collections.emptyList();
		}
		
		StringBuilder sql = new StringBuilder();
		sql.append("select t.TABLE_NAME ").append("from INFORMATION_SCHEMA.TABLES t ")
		        .append("where t.TABLE_SCHEMA = database() ").append("and t.TABLE_TYPE in ('BASE TABLE','VIEW') ");
		
		// Build dynamic LIKE conditions
		sql.append("and (");
		
		for (int i = 0; i < allowedPrefixes.size(); i++) {
			String param = "prefix" + i;
			
			sql.append("t.TABLE_NAME like :").append(param).append(" escape '\\\\'");
			
			if (i < allowedPrefixes.size() - 1) {
				sql.append(" OR ");
			}
		}
		
		sql.append(") ");
		sql.append("order by t.TABLE_NAME asc");
		
		Query q = getSession().createSQLQuery(sql.toString());
		
		// Set parameters safely
		for (int i = 0; i < allowedPrefixes.size(); i++) {
			String prefix = allowedPrefixes.get(i);
			
			// ensure proper wildcard usage
			q.setString("prefix" + i, prefix + "%");
		}
		
		return (List<String>) q.list();
	}
	
	@SuppressWarnings({ "unchecked", "deprecation" })
	public List<Map> getETLTableColumns(String tableName) {
		
		if (tableName == null || tableName.trim().isEmpty()) {
			return Collections.emptyList();
		}
		
		tableName = tableName.trim();
		
		String sql = "SELECT " + "  c.COLUMN_NAME AS columnName, " + "  c.DATA_TYPE   AS dataType "
		        + "FROM INFORMATION_SCHEMA.COLUMNS c " + "WHERE c.TABLE_SCHEMA = DATABASE() "
		        + "  AND c.TABLE_NAME = :tableName " + "ORDER BY c.ORDINAL_POSITION";
		
		Query q = getSession().createSQLQuery(sql);
		q.setString("tableName", tableName);
		
		// This makes q.list() return List<Map> instead of List<Object[]>
		q.setResultTransformer(Transformers.ALIAS_TO_ENTITY_MAP);
		
		return (List<Map>) q.list();
	}
	
	public SqlPreviewResult previewSql(String rawSql, Map<String, Object> params, Integer maxRows) {
		
		if (rawSql == null || rawSql.trim().isEmpty()) {
			throw new IllegalArgumentException("sql is required");
		}
		
		int rowsLimit = maxRows != null ? maxRows : 200;
		rowsLimit = Math.max(1, Math.min(rowsLimit, 1000));
		
		String sql = normalizeSql(rawSql);
		validateSql(sql);
		
		// UI SQL uses quoted params like ':startDate' - convert to bindable named params
		sql = normalizeQuotedParams(sql);
		
		// Enforce row limit for both SELECT and WITH (MySQL/MariaDB)
		String limitedSql = wrapWithLimit(sql);
		
		// DbSession supports createSQLQuery
		SQLQuery q = getSession().createSQLQuery(limitedSql);
		q.setCacheMode(CacheMode.IGNORE);
		
		// bind request params - only those that actually exist in the SQL query
		Set<String> sqlParams = extractNamedParameters(limitedSql);
		Map<String, Object> safeParams = (params != null) ? params : Collections.<String, Object> emptyMap();
		for (Map.Entry<String, Object> e : safeParams.entrySet()) {
			// Only set parameter if it exists in the SQL query
			if (sqlParams.contains(e.getKey())) {
				q.setParameter(e.getKey(), e.getValue());
			}
		}
		q.setParameter("__maxRows", rowsLimit);
		
		// result rows as Map<alias, value>
		@SuppressWarnings("deprecation")
		SQLQuery mapQuery = (SQLQuery) q.setResultTransformer(AliasToEntityMapResultTransformer.INSTANCE);
		
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> result = (List<Map<String, Object>>) mapQuery.list();
		
		List<String> columns = new ArrayList<>();
		if (!result.isEmpty()) {
			// Often LinkedHashMap - preserves column order from SQL
			columns.addAll(result.get(0).keySet());
		}
		
		List<List<Object>> rows = new ArrayList<>();
		for (Map<String, Object> rowMap : result) {
			List<Object> row = new ArrayList<>(columns.size());
			for (String c : columns) {
				row.add(rowMap.get(c));
			}
			rows.add(row);
		}
		
		boolean truncated = rows.size() >= rowsLimit;
		return new SqlPreviewResult(columns, rows, rows.size(), truncated);
	}
	
	// -------------------- helpers --------------------
	
	private static final Pattern FORBIDDEN = Pattern.compile(
	    "\\b(INSERT|UPDATE|DELETE|DROP|ALTER|TRUNCATE|CREATE|GRANT|REVOKE)\\b", Pattern.CASE_INSENSITIVE);
	
	/**
	 * Pattern to match named parameters in SQL queries like :paramName Matches word characters and
	 * underscores after a colon
	 */
	private static final Pattern NAMED_PARAM_PATTERN = Pattern.compile(":([\\w_]+)");
	
	/**
	 * Extracts all named parameter names from a SQL query. For example, from
	 * "SELECT * FROM table WHERE date BETWEEN :startDate AND :endDate" returns ["startDate",
	 * "endDate"]
	 * 
	 * @param sql the SQL query to parse
	 * @return set of parameter names found in the query
	 */
	private static Set<String> extractNamedParameters(String sql) {
		Set<String> params = new HashSet<>();
		Matcher matcher = NAMED_PARAM_PATTERN.matcher(sql);
		while (matcher.find()) {
			params.add(matcher.group(1));
		}
		return params;
	}
	
	private static String normalizeSql(String sql) {
		String s = sql.trim();
		// remove one trailing semicolon only
		if (s.endsWith(";"))
			s = s.substring(0, s.length() - 1).trim();
		return s;
	}
	
	private static void validateSql(String sql) {
		String upper = sql.trim().toUpperCase(Locale.ROOT);
		
		if (!(upper.startsWith("SELECT") || upper.startsWith("WITH"))) {
			throw new IllegalArgumentException("Only SELECT/WITH queries are allowed");
		}
		
		// block multiple statements: any remaining ';' is suspicious
		if (sql.contains(";")) {
			throw new IllegalArgumentException("Multiple statements are not allowed");
		}
		
		if (FORBIDDEN.matcher(sql).find()) {
			throw new IllegalArgumentException("Only read-only queries are allowed");
		}
	}
	
	/**
	 * Converts quoted params ':startDate' => :startDate so Hibernate can bind them.
	 */
	private static String normalizeQuotedParams(String sql) {
		return sql.replace("':startDate'", ":startDate").replace("':endDate'", ":endDate");
	}
	
	private static String wrapWithLimit(String sql) {
		return "SELECT * FROM (" + sql + ") _rb_preview LIMIT :__maxRows";
	}
	
	public ReportBuilderReport saveReportBuilderReport(ReportBuilderReport report) {
		getSession().saveOrUpdate(report);
		return report;
	}
	
	public ReportBuilderReport getReportBuilderReportByUuid(String uuid) {
		Criteria c = getSession().createCriteria(ReportBuilderReport.class);
		c.add(Restrictions.eq("uuid", uuid));
		return (ReportBuilderReport) c.uniqueResult();
	}
	
	public ReportBuilderReport getReportBuilderReportByCode(String code) {
		Criteria c = getSession().createCriteria(ReportBuilderReport.class);
		c.add(Restrictions.eq("code", code));
		c.setMaxResults(1);
		return (ReportBuilderReport) c.uniqueResult();
	}
	
	@SuppressWarnings("unchecked")
	public List<ReportBuilderReport> getReportBuilderReports(String q, boolean includeRetired, Integer startIndex,
	        Integer limit) {
		Criteria c = getSession().createCriteria(ReportBuilderReport.class);
		
		if (!includeRetired) {
			c.add(Restrictions.eq("retired", false));
		}
		
		if (q != null && !q.trim().isEmpty()) {
			c.add(Restrictions.or(Restrictions.ilike("name", q.trim(), MatchMode.ANYWHERE),
			    Restrictions.ilike("description", q.trim(), MatchMode.ANYWHERE),
			    Restrictions.ilike("code", q.trim(), MatchMode.ANYWHERE)));
		}
		
		c.addOrder(Order.asc("name"));
		
		if (startIndex != null) {
			c.setFirstResult(startIndex);
		}
		
		if (limit != null) {
			c.setMaxResults(limit);
		}
		
		return c.list();
	}
	
	public void deleteReportBuilderReport(ReportBuilderReport report) {
		getSession().delete(report);
	}
	
	public void retireReportBuilderReport(ReportBuilderReport report, String reason) {
		report.setRetired(true);
		report.setRetireReason(reason);
		report.setDateRetired(new Date());
		report.setRetiredBy(Context.getAuthenticatedUser());
		getSession().saveOrUpdate(report);
	}
	
	public void unretireReportBuilderReport(ReportBuilderReport report) {
		report.setRetired(false);
		report.setRetireReason(null);
		report.setDateRetired(null);
		report.setRetiredBy(null);
		getSession().saveOrUpdate(report);
	}
	
	public void purgeReportBuilderReport(ReportBuilderReport report) {
		getSession().delete(report);
	}
	
	public ReportCategory saveReportCategory(ReportCategory category) {
		getSession().saveOrUpdate(category);
		return category;
	}
	
	public ReportCategory getReportCategoryById(Integer id) {
		return (ReportCategory) getSession().get(ReportCategory.class, id);
	}
	
	public ReportCategory getReportCategoryByUuid(String uuid) {
		Criteria criteria = getSession().createCriteria(ReportCategory.class);
		criteria.add(Restrictions.eq("uuid", uuid));
		return (ReportCategory) criteria.uniqueResult();
	}
	
	@SuppressWarnings("unchecked")
	public List<ReportCategory> getReportCategories(String q, boolean includeRetired, Integer startIndex, Integer limit) {
		
		Criteria criteria = getSession().createCriteria(ReportCategory.class);
		
		if (!includeRetired) {
			criteria.add(Restrictions.eq("retired", false));
		}
		
		if (q != null && !q.trim().isEmpty()) {
			String query = "%" + q.trim().toLowerCase() + "%";
			
			Disjunction or = Restrictions.disjunction();
			or.add(Restrictions.ilike("name", query));
			or.add(Restrictions.ilike("description", query));
			
			criteria.add(or);
		}
		
		criteria.addOrder(Order.asc("name"));
		
		if (startIndex != null) {
			criteria.setFirstResult(startIndex);
		}
		
		if (limit != null) {
			criteria.setMaxResults(limit);
		}
		
		return criteria.list();
	}
	
	public long getReportCategoriesCount(String q, boolean includeRetired) {
		
		Criteria criteria = getSession().createCriteria(ReportCategory.class);
		
		if (!includeRetired) {
			criteria.add(Restrictions.eq("retired", false));
		}
		
		if (q != null && !q.trim().isEmpty()) {
			String query = "%" + q.trim().toLowerCase() + "%";
			
			Disjunction or = Restrictions.disjunction();
			or.add(Restrictions.ilike("name", query));
			or.add(Restrictions.ilike("description", query));
			
			criteria.add(or);
		}
		
		criteria.setProjection(Projections.rowCount());
		
		Number count = (Number) criteria.uniqueResult();
		return count == null ? 0 : count.longValue();
	}
	
	public void purgeReportCategory(ReportCategory category) {
		getSession().delete(category);
	}
	
	// =========================
	// ReportLibrary DAO
	// =========================
	
	public ReportLibrary saveReportLibrary(ReportLibrary reportLibrary) {
		getSession().saveOrUpdate(reportLibrary);
		return reportLibrary;
	}
	
	public ReportLibrary getReportLibraryById(Integer id) {
		return (ReportLibrary) getSession().get(ReportLibrary.class, id);
	}
	
	public ReportLibrary getReportLibraryByUuid(String uuid) {
		Criteria c = getSession().createCriteria(ReportLibrary.class);
		c.add(Restrictions.eq("uuid", uuid));
		return (ReportLibrary) c.uniqueResult();
	}
	
	@SuppressWarnings("unchecked")
	public List<ReportLibrary> getReportLibraries(String q, boolean includeRetired, Integer startIndex, Integer limit) {
		Criteria c = getSession().createCriteria(ReportLibrary.class);
		
		if (!includeRetired) {
			c.add(Restrictions.eq("retired", false));
		}
		
		if (q != null && !q.trim().isEmpty()) {
			String query = "%" + q.trim().toLowerCase() + "%";
			Disjunction or = Restrictions.disjunction();
			or.add(Restrictions.ilike("name", query));
			or.add(Restrictions.ilike("description", query));
			or.add(Restrictions.ilike("code", query));
			c.add(or);
		}
		
		c.addOrder(Order.asc("name"));
		
		if (startIndex != null) {
			c.setFirstResult(startIndex);
		}
		
		if (limit != null) {
			c.setMaxResults(limit);
		}
		
		return c.list();
	}
	
	public long getReportLibrariesCount(String q, boolean includeRetired) {
		Criteria c = getSession().createCriteria(ReportLibrary.class);
		
		if (!includeRetired) {
			c.add(Restrictions.eq("retired", false));
		}
		
		if (q != null && !q.trim().isEmpty()) {
			String query = "%" + q.trim().toLowerCase() + "%";
			Disjunction or = Restrictions.disjunction();
			or.add(Restrictions.ilike("name", query));
			or.add(Restrictions.ilike("description", query));
			or.add(Restrictions.ilike("code", query));
			c.add(or);
		}
		
		c.setProjection(Projections.rowCount());
		
		Number count = (Number) c.uniqueResult();
		return count == null ? 0 : count.longValue();
	}
	
	public ReportLibrary getReportLibraryByReportDefinitionUuid(String reportDefinitionUuid) {
		Criteria c = getSession().createCriteria(ReportLibrary.class);
		c.add(Restrictions.eq("reportDefinitionUuid", reportDefinitionUuid));
		return (ReportLibrary) c.uniqueResult();
	}
	
	@SuppressWarnings("unchecked")
	public List<ReportLibrary> getReportLibrariesByName(String name) {
		Criteria c = getSession().createCriteria(ReportLibrary.class);
		c.add(Restrictions.eq("name", name));
		c.add(Restrictions.eq("retired", false));
		return c.list();
	}
	
	public ReportLibrary getReportLibraryByBuilderReportUuid(String builderReportUuid) {
		// Tolerates legacy duplicate library rows pointing at the same report instead of failing
		// with NonUniqueResultException
		Criteria c = getSession().createCriteria(ReportLibrary.class);
		c.add(Restrictions.eq("reportBuilderReportUuid", builderReportUuid));
		c.setMaxResults(1);
		java.util.List<?> matches = c.list();
		return matches.isEmpty() ? null : (ReportLibrary) matches.get(0);
	}
	
	public void purgeReportLibrary(ReportLibrary reportLibrary) {
		getSession().delete(reportLibrary);
	}
	
	public void retireReportLibrary(ReportLibrary reportLibrary, String reason) {
		reportLibrary.setRetired(true);
		reportLibrary.setRetireReason(reason);
		reportLibrary.setDateRetired(new Date());
		reportLibrary.setRetiredBy(Context.getAuthenticatedUser());
		getSession().saveOrUpdate(reportLibrary);
	}
	
	public void unretireReportLibrary(ReportLibrary reportLibrary) {
		reportLibrary.setRetired(false);
		reportLibrary.setRetireReason(null);
		reportLibrary.setDateRetired(null);
		reportLibrary.setRetiredBy(null);
		getSession().saveOrUpdate(reportLibrary);
	}
	
	public ETLSource saveETLSource(ETLSource etlSource) {
		getSession().saveOrUpdate(etlSource);
		return etlSource;
	}
	
	public ETLSource getETLSourceByUuid(String uuid) {
		Criteria c = getSession().createCriteria(ETLSource.class);
		c.add(Restrictions.eq("uuid", uuid));
		return (ETLSource) c.uniqueResult();
	}
	
	public ETLSource getETLSourceById(Integer id) {
		return (ETLSource) getSession().get(ETLSource.class, id);
	}
	
	@SuppressWarnings("unchecked")
	public List<ETLSource> getAllETLSources(boolean includeRetired) {
		Criteria c = getSession().createCriteria(ETLSource.class);
		if (!includeRetired) {
			c.add(Restrictions.eq("retired", false));
		}
		return c.list();
	}
	
	public void deleteETLSource(ETLSource etlSource) {
		getSession().delete(etlSource);
	}
	
	// =========================================================
	// Legacy Reports
	// =========================================================
	
	private com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
	
	public LegacyReport saveLegacyReport(LegacyReport legacyReport) {
		getSession().saveOrUpdate(legacyReport);
		return legacyReport;
	}
	
	public LegacyReport getLegacyReportByUuid(String uuid) {
		Criteria c = getSession().createCriteria(LegacyReport.class);
		c.add(Restrictions.eq("uuid", uuid));
		c.add(Restrictions.eq("retired", false));
		return (LegacyReport) c.uniqueResult();
	}
	
	public LegacyReport getLegacyReportByName(String name) {
		Criteria c = getSession().createCriteria(LegacyReport.class);
		c.add(Restrictions.eq("name", name));
		c.add(Restrictions.eq("retired", false));
		return (LegacyReport) c.uniqueResult();
	}
	
	@SuppressWarnings("unchecked")
	public List<LegacyReportConfig> getAllLegacyReports() {
		Criteria c = getSession().createCriteria(LegacyReport.class);
		c.add(Restrictions.eq("retired", false));
		List<LegacyReport> entities = c.list();
		return convertToConfigs(entities);
	}
	
	@SuppressWarnings("unchecked")
	public List<LegacyReportConfig> getLegacyReportsByCategory(String category) {
		Criteria c = getSession().createCriteria(LegacyReport.class);
		c.add(Restrictions.eq("category", category));
		c.add(Restrictions.eq("retired", false));
		List<LegacyReport> entities = c.list();
		return convertToConfigs(entities);
	}
	
	@SuppressWarnings("unchecked")
	public List<LegacyReportConfig> getLegacyReportsByStatus(String status) {
		Criteria c = getSession().createCriteria(LegacyReport.class);
		c.add(Restrictions.eq("status", status));
		c.add(Restrictions.eq("retired", false));
		List<LegacyReport> entities = c.list();
		return convertToConfigs(entities);
	}
	
	@SuppressWarnings("unchecked")
	public List<LegacyReportConfig> searchLegacyReports(String query) {
		Criteria c = getSession().createCriteria(LegacyReport.class);
		c.add(Restrictions.or(Restrictions.ilike("name", "%" + query + "%"),
		    Restrictions.ilike("description", "%" + query + "%")));
		c.add(Restrictions.eq("retired", false));
		List<LegacyReport> entities = c.list();
		return convertToConfigs(entities);
	}
	
	public int getLegacyReportCount() {
		Criteria c = getSession().createCriteria(LegacyReport.class);
		c.add(Restrictions.eq("retired", false));
		c.setProjection(Projections.rowCount());
		return ((Long) c.uniqueResult()).intValue();
	}
	
	public void deleteLegacyReport(String uuid) {
		LegacyReport entity = (LegacyReport) getSession().get(LegacyReport.class, uuid);
		if (entity != null) {
			entity.setRetired(true);
			getSession().saveOrUpdate(entity);
		}
	}
	
	// Helper methods for LegacyReport conversion
	private List<LegacyReportConfig> convertToConfigs(List<LegacyReport> entities) {
		List<LegacyReportConfig> configs = new ArrayList<>();
		for (LegacyReport entity : entities) {
			configs.add(convertToConfig(entity));
		}
		return configs;
	}
	
	private LegacyReportConfig convertToConfig(LegacyReport entity) {
		if (entity == null) {
			return null;
		}
		
		try {
			LegacyReportConfig config = objectMapper.readValue(entity.getConfigJson(), LegacyReportConfig.class);
			config.setUuid(entity.getUuid());
			config.setName(entity.getName());
			config.setDescription(entity.getDescription());
			config.setVersion(entity.getVersion());
			config.setCategory(entity.getCategory());
			config.setSubcategory(entity.getSubcategory());
			config.setReportType(entity.getReportType());
			config.setReportYear(entity.getReportYear());
			config.setReportScope(entity.getReportScope());
			config.setStatus(entity.getStatus());
			
			if (entity.getDateCreated() != null) {
				config.setDateCreated(entity.getDateCreated().toString());
			}
			if (entity.getDateChanged() != null) {
				config.setDateChanged(entity.getDateChanged().toString());
			}
			
			return config;
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to convert LegacyReport to LegacyReportConfig", e);
		}
	}
	
	private LegacyReport convertToEntity(LegacyReportConfig config) {
		if (config == null) {
			return null;
		}
		
		try {
			LegacyReport entity;
			if (config.getUuid() != null) {
				entity = (LegacyReport) getSession().get(LegacyReport.class, config.getUuid());
				if (entity == null) {
					entity = new LegacyReport(config.getUuid());
				}
			} else {
				entity = new LegacyReport();
			}
			
			entity.setName(config.getName());
			entity.setDescription(config.getDescription());
			entity.setVersion(config.getVersion());
			entity.setCategory(config.getCategory());
			entity.setSubcategory(config.getSubcategory());
			entity.setReportType(config.getReportType());
			entity.setReportYear(config.getReportYear());
			entity.setReportScope(config.getReportScope());
			entity.setStatus(config.getStatus());
			
			String configJson = objectMapper.writeValueAsString(config);
			entity.setConfigJson(configJson);
			entity.setDateChanged(new Date());
			
			return entity;
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to convert LegacyReportConfig to LegacyReport", e);
		}
	}
	
	private String like(String q) {
		return "%" + q.trim().toLowerCase() + "%";
	}
	
	// =========================================================
	// ETLMonitor CRUD methods
	// =========================================================
	
	@Override
	public ETLMonitor saveETLMonitor(ETLMonitor monitor) {
		getSession().saveOrUpdate(monitor);
		return monitor;
	}
	
	@Override
	public ETLMonitor getETLMonitorById(Integer id) {
		return (ETLMonitor) getSession().get(ETLMonitor.class, id);
	}
	
	@Override
	public ETLMonitor getETLMonitorByUuid(String uuid) {
		Criteria c = getSession().createCriteria(ETLMonitor.class);
		c.add(Restrictions.eq("uuid", uuid));
		return (ETLMonitor) c.uniqueResult();
	}
	
	@Override
	public ETLMonitor getETLMonitorByCode(String code) {
		if (code == null) {
			return null;
		}
		Criteria c = getSession().createCriteria(ETLMonitor.class);
		c.add(Restrictions.eq("code", code));
		return (ETLMonitor) c.uniqueResult();
	}
	
	@Override
	public List<ETLMonitor> getETLMonitors(String qStr, boolean includeRetired, Integer startIndex, Integer limit) {
		Criteria c = getSession().createCriteria(ETLMonitor.class);
		c.setCacheMode(CacheMode.IGNORE);
		
		if (!includeRetired) {
			c.add(Restrictions.eq("retired", false));
		}
		
		if (qStr != null && !qStr.trim().isEmpty()) {
			Disjunction or = Restrictions.disjunction();
			String likeStr = like(qStr);
			or.add(Restrictions.like("name", likeStr, MatchMode.ANYWHERE));
			or.add(Restrictions.like("code", likeStr, MatchMode.ANYWHERE));
			or.add(Restrictions.like("description", likeStr, MatchMode.ANYWHERE));
			or.add(Restrictions.like("category", likeStr, MatchMode.ANYWHERE));
			c.add(or);
		}
		
		c.addOrder(Order.asc("sortOrder"));
		c.addOrder(Order.desc("dateCreated"));
		
		if (startIndex != null && startIndex > 0) {
			c.setFirstResult(startIndex);
		}
		if (limit != null && limit > 0) {
			c.setMaxResults(limit);
		}
		
		return c.list();
	}
	
	@Override
	public List<ETLMonitor> getETLMonitorsByCategory(String category, boolean includeRetired) {
		Criteria c = getSession().createCriteria(ETLMonitor.class);
		c.setCacheMode(CacheMode.IGNORE);
		
		if (!includeRetired) {
			c.add(Restrictions.eq("retired", false));
		}
		
		if (category != null && !category.trim().isEmpty()) {
			c.add(Restrictions.eq("category", category));
		}
		
		c.addOrder(Order.asc("sortOrder"));
		return c.list();
	}
	
	@Override
	public List<ETLMonitor> getActiveETLMonitors() {
		Criteria c = getSession().createCriteria(ETLMonitor.class);
		c.add(Restrictions.eq("retired", false));
		c.add(Restrictions.eq("active", true));
		c.addOrder(Order.asc("sortOrder"));
		return c.list();
	}
	
	@Override
	public long getETLMonitorsCount(String qStr, boolean includeRetired) {
		Criteria c = getSession().createCriteria(ETLMonitor.class);
		c.setProjection(Projections.rowCount());
		
		if (!includeRetired) {
			c.add(Restrictions.eq("retired", false));
		}
		
		if (qStr != null && !qStr.trim().isEmpty()) {
			Disjunction or = Restrictions.disjunction();
			String likeStr = like(qStr);
			or.add(Restrictions.like("name", likeStr, MatchMode.ANYWHERE));
			or.add(Restrictions.like("code", likeStr, MatchMode.ANYWHERE));
			or.add(Restrictions.like("description", likeStr, MatchMode.ANYWHERE));
			or.add(Restrictions.like("category", likeStr, MatchMode.ANYWHERE));
			c.add(or);
		}
		
		return ((Long) c.uniqueResult()).longValue();
	}
	
	@Override
	public void purgeETLMonitor(ETLMonitor monitor) {
		getSession().delete(monitor);
	}
	
	// =========================================================
	// ReportBuilderDashboard CRUD methods
	// =========================================================
	
	@Override
	public ReportBuilderDashboard saveReportBuilderDashboard(ReportBuilderDashboard dashboard) {
		getSession().saveOrUpdate(dashboard);
		return dashboard;
	}
	
	@Override
	public ReportBuilderDashboard getDashboardById(Integer id) {
		return (ReportBuilderDashboard) getSession().get(ReportBuilderDashboard.class, id);
	}
	
	@Override
	public ReportBuilderDashboard getDashboardByUuid(String uuid) {
		Criteria c = getSession().createCriteria(ReportBuilderDashboard.class);
		c.add(Restrictions.eq("uuid", uuid));
		return (ReportBuilderDashboard) c.uniqueResult();
	}
	
	@Override
	public ReportBuilderDashboard getDashboardByCode(String code) {
		if (code == null || code.trim().isEmpty()) {
			return null;
		}
		Criteria c = getSession().createCriteria(ReportBuilderDashboard.class);
		c.add(Restrictions.eq("code", code.trim()));
		return (ReportBuilderDashboard) c.uniqueResult();
	}
	
	@Override
	public List<ReportBuilderDashboard> getDashboards(String qStr, boolean includeRetired, Integer startIndex, Integer limit) {
		Criteria c = getSession().createCriteria(ReportBuilderDashboard.class);
		c.setCacheMode(CacheMode.IGNORE);
		
		if (!includeRetired) {
			c.add(Restrictions.eq("retired", false));
		}
		
		if (qStr != null && !qStr.trim().isEmpty()) {
			Disjunction or = Restrictions.disjunction();
			String likeStr = like(qStr);
			or.add(Restrictions.like("name", likeStr, MatchMode.ANYWHERE));
			or.add(Restrictions.like("code", likeStr, MatchMode.ANYWHERE));
			or.add(Restrictions.like("description", likeStr, MatchMode.ANYWHERE));
			c.add(or);
		}
		
		c.addOrder(Order.asc("sortOrder"));
		c.addOrder(Order.desc("dateCreated"));
		
		if (startIndex != null && startIndex > 0) {
			c.setFirstResult(startIndex);
		}
		if (limit != null && limit > 0) {
			c.setMaxResults(limit);
		}
		
		return c.list();
	}
	
	@Override
	public List<ReportBuilderDashboard> getActiveDashboards() {
		Criteria c = getSession().createCriteria(ReportBuilderDashboard.class);
		c.add(Restrictions.eq("retired", false));
		c.add(Restrictions.eq("active", true));
		c.addOrder(Order.asc("sortOrder"));
		c.addOrder(Order.asc("name"));
		return c.list();
	}
	
	@Override
	public List<ReportBuilderDashboard> getDashboardsByType(String dashboardType, boolean includeRetired) {
		ReportBuilderDashboard.DashboardType type;
		try {
			type = ReportBuilderDashboard.DashboardType.valueOf(dashboardType);
		}
		catch (IllegalArgumentException | NullPointerException e) {
			return Collections.emptyList();
		}
		
		Criteria c = getSession().createCriteria(ReportBuilderDashboard.class);
		c.setCacheMode(CacheMode.IGNORE);
		
		if (!includeRetired) {
			c.add(Restrictions.eq("retired", false));
		}
		
		c.add(Restrictions.eq("dashboardType", type));
		c.addOrder(Order.asc("sortOrder"));
		return c.list();
	}
	
	@Override
	public long getDashboardsCount(String qStr, boolean includeRetired) {
		Criteria c = getSession().createCriteria(ReportBuilderDashboard.class);
		c.setProjection(Projections.rowCount());
		
		if (!includeRetired) {
			c.add(Restrictions.eq("retired", false));
		}
		
		if (qStr != null && !qStr.trim().isEmpty()) {
			Disjunction or = Restrictions.disjunction();
			String likeStr = like(qStr);
			or.add(Restrictions.like("name", likeStr, MatchMode.ANYWHERE));
			or.add(Restrictions.like("code", likeStr, MatchMode.ANYWHERE));
			or.add(Restrictions.like("description", likeStr, MatchMode.ANYWHERE));
			c.add(or);
		}
		
		return ((Long) c.uniqueResult()).longValue();
	}
	
	@Override
	public void purgeReportBuilderDashboard(ReportBuilderDashboard dashboard) {
		getSession().delete(dashboard);
	}
}
