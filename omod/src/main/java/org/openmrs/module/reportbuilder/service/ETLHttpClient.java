/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.reportbuilder.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.openmrs.module.reportbuilder.contract.ETLMonitorConfig.ApiEndpoint;
import org.openmrs.module.reportbuilder.exception.ETLMonitorException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;

/**
 * Service for executing HTTP requests to external ETL endpoints. Supports multiple authentication
 * methods and configurable timeouts.
 */
@Service
public class ETLHttpClient {
	
	private static final int DEFAULT_TIMEOUT_SECONDS = 10;
	
	private int timeout = DEFAULT_TIMEOUT_SECONDS;
	
	/**
	 * Execute an HTTP request based on the endpoint configuration
	 * 
	 * @param endpoint the API endpoint configuration
	 * @return the response body as a string
	 * @throws ETLMonitorException if the request fails
	 */
	public String executeRequest(ApiEndpoint endpoint) throws ETLMonitorException {
		if (endpoint == null || endpoint.getUrl() == null || endpoint.getUrl().trim().isEmpty()) {
			throw new ETLMonitorException(ETLMonitorException.ErrorCode.INVALID_CONFIG, "Endpoint URL is required");
		}
		
		// Validate URL protocol
		String url = endpoint.getUrl().trim();
		if (!url.startsWith("http://") && !url.startsWith("https://")) {
			throw new ETLMonitorException(ETLMonitorException.ErrorCode.INVALID_CONFIG,
			        "URL must use http:// or https:// protocol");
		}
		
		// Get timeout from endpoint or use default
		int timeoutMs = (endpoint.getAuthConfig() != null && endpoint.getAuthConfig().containsKey("timeout")) ? parseTimeout(endpoint
		        .getAuthConfig().get("timeout")) : this.timeout * 1000;
		
		CloseableHttpClient httpClient = null;
		try {
			// Configure HTTP client with timeout
			RequestConfig config = RequestConfig.custom().setConnectTimeout(timeoutMs)
			        .setConnectionRequestTimeout(timeoutMs).setSocketTimeout(timeoutMs).build();
			
			httpClient = HttpClients.createDefault();
			
			// Create request based on method
			HttpRequestBase request;
			String method = endpoint.getMethod() != null ? endpoint.getMethod().toUpperCase() : "GET";
			
			if ("POST".equalsIgnoreCase(method)) {
				HttpPost postRequest = new HttpPost(url);
				// Set request body if provided
				if (endpoint.getRequestBody() != null && !endpoint.getRequestBody().isEmpty()) {
					String jsonBody = new ObjectMapper().writeValueAsString(endpoint.getRequestBody());
					postRequest.setEntity(new StringEntity(jsonBody, "UTF-8"));
					postRequest.setHeader("Content-Type", "application/json");
				}
				request = postRequest;
			} else {
				// Build URL with query parameters for GET request
				String fullUrl = url;
				if (endpoint.getQueryParams() != null && !endpoint.getQueryParams().isEmpty()) {
					fullUrl = buildUrlWithParams(url, endpoint.getQueryParams());
				}
				request = new HttpGet(fullUrl);
			}
			
			// Apply authentication
			applyAuthentication(request, endpoint);
			
			// Apply custom headers
			if (endpoint.getHeaders() != null) {
				for (Map.Entry<String, String> header : endpoint.getHeaders().entrySet()) {
					request.addHeader(header.getKey(), header.getValue());
				}
			}
			
			// Set default Accept header if not present
			if (request.getFirstHeader("Accept") == null) {
				request.addHeader("Accept", "application/json");
			}
			
			// Execute request
			HttpResponse response = httpClient.execute(request);
			int statusCode = response.getStatusLine().getStatusCode();
			
			// Check for HTTP errors
			if (statusCode >= 400) {
				String errorBody = EntityUtils.toString(response.getEntity(), "UTF-8");
				if (statusCode == 401 || statusCode == 403) {
					throw new ETLMonitorException(ETLMonitorException.ErrorCode.AUTH_ERROR, "Authentication failed: "
					        + statusCode);
				} else {
					throw new ETLMonitorException(ETLMonitorException.ErrorCode.HTTP_ERROR,
					        "HTTP request failed with status " + statusCode + ": " + errorBody);
				}
			}
			
			// Return response body
			return EntityUtils.toString(response.getEntity(), "UTF-8");
			
		}
		catch (ETLMonitorException e) {
			throw e;
		}
		catch (java.net.SocketTimeoutException e) {
			throw new ETLMonitorException(ETLMonitorException.ErrorCode.TIMEOUT, "Request timed out after " + timeoutMs
			        + "ms", e);
		}
		catch (IOException e) {
			throw new ETLMonitorException(ETLMonitorException.ErrorCode.HTTP_ERROR,
			        "HTTP request failed: " + e.getMessage(), e);
		}
		catch (Exception e) {
			throw new ETLMonitorException(ETLMonitorException.ErrorCode.INVALID_CONFIG, "Failed to execute request: "
			        + e.getMessage(), e);
		}
		finally {
			if (httpClient != null) {
				try {
					httpClient.close();
				}
				catch (IOException e) {
					// Ignore close errors
				}
			}
		}
	}
	
	/**
	 * Apply authentication to the HTTP request based on the auth type
	 * 
	 * @param request the HTTP request
	 * @param endpoint the endpoint configuration
	 */
	private void applyAuthentication(HttpRequestBase request, ApiEndpoint endpoint) throws ETLMonitorException {
		if (endpoint.getAuthType() == null) {
			return;
		}
		
		switch (endpoint.getAuthType()) {
			case BASIC:
				applyBasicAuth(request, endpoint);
				break;
			case API_KEY:
				applyApiKeyAuth(request, endpoint);
				break;
			case BEARER_TOKEN:
				applyBearerTokenAuth(request, endpoint);
				break;
			case OAUTH2:
				// OAuth2 would require additional token exchange logic
				// For now, treat it similar to bearer token
				applyBearerTokenAuth(request, endpoint);
				break;
			case NONE:
			default:
				// No authentication
				break;
		}
	}
	
	/**
	 * Apply Basic Authentication
	 */
	private void applyBasicAuth(HttpRequestBase request, ApiEndpoint endpoint) throws ETLMonitorException {
		if (endpoint.getAuthConfig() == null) {
			throw new ETLMonitorException(ETLMonitorException.ErrorCode.AUTH_ERROR,
			        "Basic auth requires username and password in authConfig");
		}
		
		String username = endpoint.getAuthConfig().get("username");
		String password = endpoint.getAuthConfig().get("password");
		
		if (username == null || password == null) {
			throw new ETLMonitorException(ETLMonitorException.ErrorCode.AUTH_ERROR,
			        "Basic auth requires username and password in authConfig");
		}
		
		// Support environment variable substitution
		username = substituteEnvVariable(username);
		password = substituteEnvVariable(password);
		
		String auth = username + ":" + password;
		String encodedAuth = java.util.Base64.getEncoder().encodeToString(auth.getBytes());
		request.addHeader("Authorization", "Basic " + encodedAuth);
	}
	
	/**
	 * Apply API Key Authentication
	 */
	private void applyApiKeyAuth(HttpRequestBase request, ApiEndpoint endpoint) throws ETLMonitorException {
		if (endpoint.getAuthConfig() == null) {
			throw new ETLMonitorException(ETLMonitorException.ErrorCode.AUTH_ERROR,
			        "API Key auth requires headerName and apiKey in authConfig");
		}
		
		String headerName = endpoint.getAuthConfig().get("headerName");
		String apiKey = endpoint.getAuthConfig().get("apiKey");
		
		if (headerName == null || apiKey == null) {
			throw new ETLMonitorException(ETLMonitorException.ErrorCode.AUTH_ERROR,
			        "API Key auth requires headerName and apiKey in authConfig");
		}
		
		// Support environment variable substitution
		apiKey = substituteEnvVariable(apiKey);
		
		request.addHeader(headerName, apiKey);
	}
	
	/**
	 * Apply Bearer Token Authentication
	 */
	private void applyBearerTokenAuth(HttpRequestBase request, ApiEndpoint endpoint) throws ETLMonitorException {
		if (endpoint.getAuthConfig() == null) {
			throw new ETLMonitorException(ETLMonitorException.ErrorCode.AUTH_ERROR,
			        "Bearer Token auth requires token in authConfig");
		}
		
		String token = endpoint.getAuthConfig().get("token");
		
		if (token == null) {
			throw new ETLMonitorException(ETLMonitorException.ErrorCode.AUTH_ERROR,
			        "Bearer Token auth requires token in authConfig");
		}
		
		// Support environment variable substitution
		token = substituteEnvVariable(token);
		
		request.addHeader("Authorization", "Bearer " + token);
	}
	
	/**
	 * Substitute environment variables in values Format: ${env.VARIABLE_NAME}
	 */
	private String substituteEnvVariable(String value) {
		if (value == null) {
			return null;
		}
		
		if (value.matches("^\\$\\{env\\..+\\}$")) {
			String envVarName = value.substring(6, value.length() - 1);
			String envValue = System.getenv(envVarName);
			if (envValue != null) {
				return envValue;
			}
			// If env var not found, return original value
			return value;
		}
		
		return value;
	}
	
	/**
	 * Build URL with query parameters
	 */
	private String buildUrlWithParams(String baseUrl, Map<String, String> params) {
		if (params == null || params.isEmpty()) {
			return baseUrl;
		}
		
		StringBuilder url = new StringBuilder(baseUrl);
		boolean firstParam = !baseUrl.contains("?");
		
		for (Map.Entry<String, String> param : params.entrySet()) {
			if (firstParam) {
				url.append("?");
				firstParam = false;
			} else {
				url.append("&");
			}
			try {
				url.append(java.net.URLEncoder.encode(param.getKey(), "UTF-8")).append("=")
				        .append(java.net.URLEncoder.encode(param.getValue(), "UTF-8"));
			}
			catch (java.io.UnsupportedEncodingException e) {
				// Should not happen with UTF-8
			}
		}
		
		return url.toString();
	}
	
	/**
	 * Parse timeout string to milliseconds
	 */
	private int parseTimeout(String timeoutStr) {
		try {
			int timeout = Integer.parseInt(timeoutStr);
			// Clamp between 1 second and 5 minutes
			return Math.max(1000, Math.min(timeout * 1000, 300000));
		}
		catch (NumberFormatException e) {
			return DEFAULT_TIMEOUT_SECONDS * 1000;
		}
	}
	
	/**
	 * Set the default timeout in seconds
	 */
	public void setTimeout(int timeoutSeconds) {
		if (timeoutSeconds < 1 || timeoutSeconds > 300) {
			throw new IllegalArgumentException("Timeout must be between 1 and 300 seconds");
		}
		this.timeout = timeoutSeconds;
	}
	
	/**
	 * Get the current timeout in seconds
	 */
	public int getTimeout() {
		return timeout;
	}
}
