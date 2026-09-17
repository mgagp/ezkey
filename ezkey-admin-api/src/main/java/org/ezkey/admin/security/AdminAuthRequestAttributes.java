/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.admin.security;

/** Request attribute names used by Admin API authentication filters and controllers. */
public final class AdminAuthRequestAttributes {

  public static final String AUTH_SOURCE = "org.ezkey.admin.auth.source";
  public static final String PLAIN_TOKEN = "org.ezkey.admin.auth.plainToken";
  public static final String EXPIRES_AT = "org.ezkey.admin.auth.expiresAt";
  public static final String USERNAME = "org.ezkey.admin.auth.username";

  /** Tenant display name for tenant- or integration-scoped sessions; omitted for global admins. */
  public static final String TENANT_NAME = "org.ezkey.admin.auth.tenantName";

  private AdminAuthRequestAttributes() {}

  /** Authentication source for the current request. */
  public enum AuthSource {
    BEARER,
    COOKIE
  }
}
