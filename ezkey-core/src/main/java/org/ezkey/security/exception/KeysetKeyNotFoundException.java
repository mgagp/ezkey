/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Exception: KeysetKeyNotFoundException
 * Description: Typed refusal when a key id is absent from the keyset after a versioned reload.
 */

package org.ezkey.security.exception;

import java.security.GeneralSecurityException;

/**
 * Thrown when a key id is still absent from the in-memory keyset after a versioned reload from the
 * database blob (fail-closed: no {@code setPrimary}, no blob write).
 *
 * <p>When the blob is known to be current, this is a true inconsistency (ERROR). When the replica
 * was stale and the kid was never introduced, treat as an expected promotion conflict (WARN +
 * {@code KEY_PROMOTION_CONFLICT}).
 *
 * @author Ezkey contributors
 * @since 2026
 */
@SuppressWarnings("serial")
public class KeysetKeyNotFoundException extends GeneralSecurityException {

  private final long keyId;
  private final boolean afterFreshReload;

  /**
   * Creates a typed key-not-found failure.
   *
   * @param keyId unsigned key id that was requested
   * @param afterFreshReload {@code true} when the blob version matched memory after reload (true
   *     inconsistency); {@code false} when conflict may still be expected under HA races
   * @param message human-readable detail
   */
  public KeysetKeyNotFoundException(long keyId, boolean afterFreshReload, String message) {
    super(message);
    this.keyId = keyId;
    this.afterFreshReload = afterFreshReload;
  }

  /**
   * Returns the missing key id (unsigned).
   *
   * @return the key id
   */
  public long getKeyId() {
    return keyId;
  }

  /**
   * Returns whether the keyset was known current after reload when the kid was refused.
   *
   * @return {@code true} for true inconsistency (ERROR path)
   */
  public boolean isAfterFreshReload() {
    return afterFreshReload;
  }
}
