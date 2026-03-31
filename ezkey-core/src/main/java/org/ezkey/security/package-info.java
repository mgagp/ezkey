/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

/**
 * Security primitives for encryption-at-rest, key lifecycle, and sensitive-data handling.
 *
 * <p>This package provides:
 *
 * <ul>
 *   <li>High-level encryption/decryption services for sensitive string values
 *   <li>Keyset lifecycle management and AEAD primitive provisioning
 *   <li>Key rotation and re-encryption orchestration (including HA-safe scheduling)
 *   <li>Hashing utilities for secure lookups of encrypted values
 * </ul>
 *
 * <h2>Encryption format contract</h2>
 *
 * <p>{@link org.ezkey.security.EncryptionService} uses a strict, self-describing format:
 *
 * <pre>{@code
 * ENC:<unsignedKeyId>:<Base64(ciphertext)>
 * }</pre>
 *
 * <p>The prefix:
 *
 * <ul>
 *   <li>allows reliable detection of encrypted vs plaintext values
 *   <li>carries the key ID to support operational queries and re-encryption workflows
 * </ul>
 *
 * <h2>Searchable secrets pattern</h2>
 *
 * <p>For secrets that must remain searchable (e.g., proof tokens), Ezkey stores:
 *
 * <ul>
 *   <li>the encrypted value for confidentiality
 *   <li>a deterministic SHA-256 hash for lookups/uniqueness (see {@link
 *       org.ezkey.security.SensitiveDataHasher})
 * </ul>
 *
 * <h2>JPA integration contract</h2>
 *
 * <p>{@link org.ezkey.security.EncryptionEntityListener} applies encryption for selected transient
 * fields just before persistence/update. The contract is:
 *
 * <ul>
 *   <li>Entities store sensitive plaintext in transient fields
 *   <li>Persisted fields contain either plaintext (when encryption unavailable) or {@code ENC:...}
 *   <li>Listener best-effort encrypts; failures should not break business operations
 * </ul>
 *
 * <h2>Key rotation and HA</h2>
 *
 * <p>Rotation/promotion tasks must be safe in high-availability deployments:
 *
 * <ul>
 *   <li>Distributed locks (ShedLock) prevent concurrent execution across instances
 *   <li>Promotion must update the underlying keyset first, then synchronize metadata
 * </ul>
 *
 * <h2>Key entry points</h2>
 *
 * <ul>
 *   <li>{@link org.ezkey.security.TinkKeyManager}
 *   <li>{@link org.ezkey.security.EncryptionService}
 *   <li>{@link org.ezkey.security.EncryptionEntityListener}
 *   <li>{@link org.ezkey.security.KeyRotationService}
 *   <li>{@link org.ezkey.security.ReencryptionService}
 *   <li>{@link org.ezkey.security.SensitiveDataHasher}
 * </ul>
 *
 * <h2>References</h2>
 *
 * <ul>
 *   <li>{@code docs/ENDPOINT.md} (token security model)
 * </ul>
 *
 * @since 2025
 */
package org.ezkey.security;
