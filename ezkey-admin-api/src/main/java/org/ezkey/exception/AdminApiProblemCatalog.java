/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.exception;

/**
 * Stable RFC 9457 {@code type} URIs and safe client-facing {@code title} / {@code detail} strings
 * for generic Admin API handling in {@link GlobalExceptionHandler} and {@link
 * ValidationExceptionHandler}. Domain-specific problems continue to use their own type URIs in
 * specialized handlers.
 */
public final class AdminApiProblemCatalog {

  /** Base URI for Admin API generic problem types. */
  public static final String BASE = "https://ezkey.io/problems/admin";

  public static final String TYPE_SYSTEM_NOT_CONFIGURED = BASE + "/system-tenant-not-configured";
  public static final String TYPE_RATE_LIMIT_EXCEEDED = BASE + "/rate-limit-exceeded";
  public static final String TYPE_RESOURCE_NOT_FOUND = BASE + "/resource-not-found";
  public static final String TYPE_INTERNAL_ERROR = BASE + "/internal-error";

  /** Bean validation, {@code @RequestParam} constraint violations, etc. */
  public static final String TYPE_VALIDATION_FAILED = BASE + "/validation-failed";

  public static final String TYPE_INVALID_ARGUMENT = BASE + "/invalid-argument";
  public static final String TYPE_STATE_CONFLICT = BASE + "/state-conflict";
  public static final String TYPE_OPTIMISTIC_LOCK_CONFLICT = BASE + "/optimistic-lock-conflict";
  public static final String TYPE_MALFORMED_REQUEST = BASE + "/malformed-request";
  public static final String TYPE_DATA_CONSTRAINT_VIOLATION = BASE + "/data-constraint-violation";

  public static final String TITLE_INTERNAL_ERROR = "Internal error";
  public static final String TITLE_TOO_MANY_REQUESTS = "Too many requests";
  public static final String TITLE_NOT_FOUND = "Resource not found";
  public static final String TITLE_VALIDATION_FAILED = "Validation failed";
  public static final String TITLE_INVALID_ARGUMENT = "Invalid argument";
  public static final String TITLE_STATE_CONFLICT = "State conflict";
  public static final String TITLE_OPTIMISTIC_LOCK = "Concurrent modification";
  public static final String TITLE_MALFORMED_REQUEST = "Invalid request body";
  public static final String TITLE_DATA_CONSTRAINT = "Data constraint violation";

  /** Safe detail when the server cannot complete the request (no stack or tenant internals). */
  public static final String DETAIL_UNEXPECTED = "An unexpected error occurred";

  private AdminApiProblemCatalog() {}
}
