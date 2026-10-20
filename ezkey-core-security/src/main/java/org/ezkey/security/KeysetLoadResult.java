/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Class: KeysetLoadResult
 * Description: Typed outcome of loading ezkey_keyset_blob (B3 — never map failures to ABSENT).
 */

package org.ezkey.security;

/**
 * Result of attempting to load {@code ezkey_keyset_blob} into memory.
 *
 * <p>Callers must not treat {@link #FAILED_CLOSED} or {@link #LEGACY_ENVELOPE} as “no row” — that
 * caused destructive CAS replacement of live keys (B3).
 *
 * @since 2026
 */
public enum KeysetLoadResult {
  /** Blob decrypted and installed in memory. */
  LOADED,
  /** No singleton row (first boot may claim). */
  ABSENT,
  /**
   * Row exists but cannot be used: DB unreachable, wrong master key, corrupt Tink-native envelope,
   * or other non-legacy failure. Fail closed — do not generate or overwrite.
   */
  FAILED_CLOSED,
  /**
   * Positively identified ADR-0011 legacy outer-encrypted envelope (cleartext JSON keyset wrapped
   * in master AEAD). Destructive replace only when the opt-in flag is enabled and a backup is kept.
   */
  LEGACY_ENVELOPE
}
