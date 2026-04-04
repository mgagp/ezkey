/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.crypto.exception;

/**
 * Stable RFC 9457 {@code type} URIs and titles for {@link
 * org.ezkey.crypto.controller.CryptoGlobalExceptionHandler}. The Crypto API is a non-production
 * testing surface; {@code detail} strings may still include diagnostic text for operators.
 */
public final class CryptoApiProblemCatalog {

  public static final String BASE = "https://ezkey.io/problems/crypto";

  public static final String TYPE_MALFORMED_JSON = BASE + "/malformed-json";
  public static final String TYPE_VALIDATION_FAILED = BASE + "/validation-failed";
  public static final String TYPE_INVALID_ARGUMENT = BASE + "/invalid-argument";
  public static final String TYPE_INTERNAL_ERROR = BASE + "/internal-error";
  public static final String TYPE_UNEXPECTED_ERROR = BASE + "/unexpected-error";

  public static final String TITLE_INVALID_JSON = "Invalid JSON";
  public static final String TITLE_VALIDATION_FAILED = "Validation failed";
  public static final String TITLE_INVALID_ARGUMENT = "Invalid argument";
  public static final String TITLE_INTERNAL_ERROR = "Internal error";
  public static final String TITLE_UNEXPECTED_ERROR = "Unexpected error";

  private CryptoApiProblemCatalog() {}
}
