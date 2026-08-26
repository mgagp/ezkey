/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Class: ApplicationReadyStartupOrder
 * Description: Shared @Order values for ApplicationReadyEvent listeners that must run in sequence.
 */

package org.ezkey.security;

/**
 * {@code @Order} values for {@code ApplicationReadyEvent} listeners that must run in a fixed
 * sequence.
 *
 * <p>Admin MFA bootstrap persists enrollment ciphertext and companion {@code *_encryption_key_id}
 * columns. Those foreign keys require {@code ezkey_encryption_key} rows from empty-table keyset
 * sync first. Lower values run first.
 *
 * @author Ezkey contributors
 * @since 2026
 */
public final class ApplicationReadyStartupOrder {

  /** {@link KeyRotationService#initializeKeysetSync()} — populate encryption-key metadata. */
  public static final int KEYSET_SYNC = 0;

  /** Initial global admin identity — identifiable admin row before MFA enrollment. */
  public static final int INITIAL_GLOBAL_ADMIN = 1;

  /** Admin MFA bootstrap — system integration and global-admin enrollment. */
  public static final int ADMIN_MFA_BOOTSTRAP = 2;

  private ApplicationReadyStartupOrder() {}
}
