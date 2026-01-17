/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

/**
 * Authentication attempt domain (core MFA flow).
 *
 * <p>
 * This package and its subpackages implement the lifecycle of an authentication
 * attempt:
 * creation, device claiming (pending/read-once), device response
 * (approve/deny), and optional wait
 * semantics.
 *
 * <h2>Security invariants</h2>
 *
 * <ul>
 * <li><b>Read-once guarantee:</b> a pending attempt can be claimed only once by
 * a legitimate
 * device; after being claimed it transitions away from {@code PENDING}
 * (typically to
 * {@code READ}).
 * <li><b>Anti-replay:</b> device proof tokens must not be reusable (enforced
 * via hashing and
 * uniqueness checks).
 * <li><b>Supersession:</b> when a newer attempt exists for the same enrollment,
 * older attempts are
 * treated as conceptually invalid/expired to prevent responding to stale
 * requests.
 * <li><b>Expiration:</b> attempts are time-bound and must not be
 * claimable/respondable after
 * {@code expiresAt}.
 * </ul>
 *
 * <h2>Concurrency and transactions</h2>
 *
 * <p>
 * Pending-claim operations are designed to be safe under concurrency:
 *
 * <ul>
 * <li>Row-level locking is used when claiming the most recent valid pending
 * attempt.
 * <li>State transitions are performed atomically inside transactions.
 * </ul>
 *
 * <h2>Data handling</h2>
 *
 * <p>
 * Proof tokens are treated as sensitive data:
 *
 * <ul>
 * <li>Persisted values may be encrypted at rest (see
 * {@link org.ezkey.security.EncryptionService})
 * <li>Deterministic hashes are used for secure lookups and uniqueness
 * constraints (see {@link
 * org.ezkey.security.SensitiveDataHasher})
 * </ul>
 *
 * <h2>Key entry points</h2>
 *
 * <ul>
 * <li>{@link org.ezkey.authattempt.service.AuthAttemptService}
 * <li>{@link org.ezkey.authattempt.service.AuthAttemptPendingService}
 * <li>{@link org.ezkey.authattempt.service.AuthAttemptRespondService}
 * <li>{@link org.ezkey.authattempt.service.AuthAttemptWaitService}
 * <li>{@link org.ezkey.authattempt.domain.repository.AuthAttemptRepository}
 * <li>{@link org.ezkey.authattempt.domain.entity.AuthAttempt}
 * </ul>
 *
 * <h2>References</h2>
 *
 * <ul>
 * <li>{@code docs/ENDPOINT.md} (token security and pending/respond
 * expectations)
 * </ul>
 *
 * @since 2025
 */
package org.ezkey.authattempt;
