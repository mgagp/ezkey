/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.admin;

/**
 * Shared literals for admin-api unit tests (system tenant defaults align with ezkey-core
 * migrations).
 */
public final class AdminTestConstants {

  private AdminTestConstants() {}

  /** Default system tenant display name in tests. */
  public static final String DEFAULT_SYSTEM_TENANT_NAME = "Ezkey System";
}
