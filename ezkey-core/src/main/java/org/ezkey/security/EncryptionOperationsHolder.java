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

import java.util.function.Supplier;

/**
 * Holds the current {@link EncryptionOperations} implementation for JPA entities and listeners.
 *
 * <p>JPA entities and entity listeners are not regular Spring beans. This holder provides a small,
 * explicit bridge that keeps the domain model decoupled from the concrete encryption service.
 *
 * <p>At runtime the holder receives a lazy {@link Supplier} (registered by {@link
 * EncryptionEntityListener}) instead of a resolved instance. The encryption service depends on a
 * JPA repository, so resolving it while Hibernate builds the entity manager factory closes a bean
 * cycle (Spring Boot 4.2, #1458). The supplier is asked on the first {@link #get()} — that is, the
 * first time an encrypted field is written or read — and its first non-null answer is cached.
 *
 * @since 2025
 */
public final class EncryptionOperationsHolder {

  private static volatile EncryptionOperations encryptionOperations;

  private static volatile Supplier<EncryptionOperations> encryptionOperationsSupplier;

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
   * Registers a lazy source for the runtime encryption operations.
   *
   * <p>Replaces any cached instance: the next {@link #get()} asks the supplier again.
   *
   * @param supplier lazy source that may answer null while no encryption bean exists
   */
  public static void setSupplier(Supplier<EncryptionOperations> supplier) {
    encryptionOperationsSupplier = supplier;
    encryptionOperations = null;
  }

  /**
   * Returns the current runtime encryption operations.
   *
   * @return the current runtime encryption operations, or null when unavailable
   */
  public static EncryptionOperations get() {
    EncryptionOperations cached = encryptionOperations;
    if (cached != null) {
      return cached;
    }
    Supplier<EncryptionOperations> supplier = encryptionOperationsSupplier;
    if (supplier == null) {
      return null;
    }
    EncryptionOperations resolved = supplier.get();
    if (resolved != null) {
      encryptionOperations = resolved;
    }
    return resolved;
  }

  /** Clears the current runtime encryption operations and any registered supplier. */
  public static void clear() {
    encryptionOperations = null;
    encryptionOperationsSupplier = null;
  }
}
