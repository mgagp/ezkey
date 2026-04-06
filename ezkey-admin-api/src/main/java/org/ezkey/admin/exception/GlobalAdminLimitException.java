/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.admin.exception;

/**
 * Thrown when creating a global administrator would exceed the configured maximum number of active
 * global administrators.
 *
 * <p>Mapped to RFC 9457 problem type {@code
 * https://ezkey.io/problems/admin-provisioning/global-admin-limit-reached} with extension property
 * {@code parameters.maxGlobalAdmins}.
 */
public final class GlobalAdminLimitException extends RuntimeException {

  private final int maxGlobalAdmins;

  /**
   * Constructs an exception when the global administrator limit is reached.
   *
   * @param maxGlobalAdmins configured maximum number of active global administrators
   */
  public GlobalAdminLimitException(int maxGlobalAdmins) {
    super(
        "Maximum global administrators reached (%d). Cannot create more."
            .formatted(maxGlobalAdmins));
    this.maxGlobalAdmins = maxGlobalAdmins;
  }

  public int getMaxGlobalAdmins() {
    return maxGlobalAdmins;
  }
}
