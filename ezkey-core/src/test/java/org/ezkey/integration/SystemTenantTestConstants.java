/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Constants: SystemTenantTestConstants
 * Description: Shared default display strings for system tenant in unit tests.
 */

package org.ezkey.integration;

/**
 * Default system tenant display values used in unit tests (aligns with migration V3 / org
 * defaults).
 */
public final class SystemTenantTestConstants {

  private SystemTenantTestConstants() {}

  /** Default system tenant name used when tests construct a tenant entity. */
  public static final String DEFAULT_SYSTEM_TENANT_NAME = "Ezkey System";

  /** Default system tenant description used when tests construct a tenant entity. */
  public static final String DEFAULT_SYSTEM_TENANT_DESCRIPTION = "System tenant";
}
