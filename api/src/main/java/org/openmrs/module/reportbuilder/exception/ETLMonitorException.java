/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.reportbuilder.exception;

/**
 * Exception thrown when ETL Monitor operations fail. Includes error codes for different types of
 * failures.
 */
public class ETLMonitorException extends Exception {
	
	private static final long serialVersionUID = 1L;
	
	private final ErrorCode errorCode;
	
	/**
	 * Error codes for ETL Monitor failures
	 */
	public enum ErrorCode {
		/** Invalid configuration (missing URL, invalid JSON, etc.) */
		INVALID_CONFIG,
		/** HTTP request failed (4xx, 5xx errors) */
		HTTP_ERROR,
		/** Request timed out */
		TIMEOUT,
		/** Failed to parse JSON response */
		PARSE_ERROR,
		/** Authentication failed */
		AUTH_ERROR,
		/** Invalid JSONPath expression */
		INVALID_PATH
	}
	
	/**
	 * Create a new ETLMonitorException with an error code and message
	 * 
	 * @param errorCode the error code
	 * @param message the error message
	 */
	public ETLMonitorException(ErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}
	
	/**
	 * Create a new ETLMonitorException with an error code, message, and cause
	 * 
	 * @param errorCode the error code
	 * @param message the error message
	 * @param cause the underlying cause
	 */
	public ETLMonitorException(ErrorCode errorCode, String message, Throwable cause) {
		super(message, cause);
		this.errorCode = errorCode;
	}
	
	/**
	 * Get the error code
	 * 
	 * @return the error code
	 */
	public ErrorCode getErrorCode() {
		return errorCode;
	}
	
	/**
	 * Check if this is a configuration error
	 * 
	 * @return true if the error code is INVALID_CONFIG
	 */
	public boolean isConfigError() {
		return errorCode == ErrorCode.INVALID_CONFIG;
	}
	
	/**
	 * Check if this is an authentication error
	 * 
	 * @return true if the error code is AUTH_ERROR
	 */
	public boolean isAuthError() {
		return errorCode == ErrorCode.AUTH_ERROR;
	}
	
	/**
	 * Check if this is a timeout error
	 * 
	 * @return true if the error code is TIMEOUT
	 */
	public boolean isTimeout() {
		return errorCode == ErrorCode.TIMEOUT;
	}
	
	/**
	 * Check if this is an HTTP error
	 * 
	 * @return true if the error code is HTTP_ERROR
	 */
	public boolean isHttpError() {
		return errorCode == ErrorCode.HTTP_ERROR;
	}
	
	/**
	 * Check if this is a parse error
	 * 
	 * @return true if the error code is PARSE_ERROR
	 */
	public boolean isParseError() {
		return errorCode == ErrorCode.PARSE_ERROR;
	}
	
	/**
	 * Check if this is an invalid path error
	 * 
	 * @return true if the error code is INVALID_PATH
	 */
	public boolean isInvalidPath() {
		return errorCode == ErrorCode.INVALID_PATH;
	}
}
