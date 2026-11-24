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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * <p>When a supplier is registered but answers null (no {@link EncryptionOperations} bean: broken
 * wiring in the APIs, where {@code EncryptionService} is always a bean), the holder logs one WARN
 * per registered supplier. Encrypted fields are then stored and returned as plaintext, so that
 * state must stay observable (#1471 security opinion, follow-up to #1462).
 *
 * @since 2025
 */
public final class EncryptionOperationsHolder {

  private static volatile EncryptionOperations encryptionOperations;

  private static volatile Supplier<EncryptionOperations> encryptionOperationsSupplier;

  private static final Logger logger = LoggerFactory.getLogger(EncryptionOperationsHolder.class);

  /** Ensures the "supplier answered null" WARN is logged once per registered supplier. */
  private static final AtomicBoolean NULL_RESOLUTION_WARNED = new AtomicBoolean();

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
    NULL_RESOLUTION_WARNED.set(false);
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
    } else if (NULL_RESOLUTION_WARNED.compareAndSet(false, true)) {
      logger.warn(
          "No EncryptionOperations bean is available: encrypted fields will be stored and read as"
              + " plaintext. Expected only when at-rest encryption is not wired (tests); in the"
              + " APIs this means broken wiring.");
    }
    return resolved;
  }

  /** Clears the current runtime encryption operations and any registered supplier. */
  public static void clear() {
    encryptionOperations = null;
    encryptionOperationsSupplier = null;
    NULL_RESOLUTION_WARNED.set(false);
  }
}
