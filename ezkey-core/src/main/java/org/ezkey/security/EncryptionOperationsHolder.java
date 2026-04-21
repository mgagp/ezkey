/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Utility: EncryptionOperationsHolder
 * Description: Runtime holder exposing the current encryption implementation to unmanaged JPA code.
 */

package org.ezkey.security;

/**
 * Holds the current {@link EncryptionOperations} implementation for JPA entities and listeners.
 *
 * <p>JPA entities and entity listeners are not regular Spring beans. This holder provides a small,
 * explicit bridge that keeps the domain model decoupled from the concrete encryption service.
 *
 * @since 2025
 */
public final class EncryptionOperationsHolder {

  private static volatile EncryptionOperations encryptionOperations;

  private EncryptionOperationsHolder() {}

  /**
   * Registers the current runtime encryption operations.
   *
   * @param operations runtime encryption operations, or null when encryption is unavailable
   */
  public static void set(EncryptionOperations operations) {
    encryptionOperations = operations;
  }

  /**
   * Returns the current runtime encryption operations.
   *
   * @return the current runtime encryption operations, or null when unavailable
   */
  public static EncryptionOperations get() {
    return encryptionOperations;
  }

  /** Clears the current runtime encryption operations. */
  public static void clear() {
    encryptionOperations = null;
  }
}
