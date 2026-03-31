/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

/**
 * Enrollment domain (device binding and enrollment lifecycle).
 *
 * <p>This package and its subpackages model the enrollment of a mobile device for a given
 * integration. Enrollment is the foundation for authentication attempts: it holds the device public
 * key used for signature verification and the enrollment proof token used to securely identify the
 * enrollment without enabling ID enumeration.
 *
 * <h2>Security invariants</h2>
 *
 * <ul>
 *   <li><b>Anti-enumeration:</b> enrollment access for device polling is protected by a proof token
 *       (not by ID alone). Implementations should require proof-token possession to locate an
 *       enrollment.
 *   <li><b>Searchable secret pattern:</b> proof tokens are stored encrypted at rest while a
 *       deterministic hash is used for lookup/uniqueness.
 *   <li><b>Key material boundaries:</b> device public keys and integration key material are treated
 *       as sensitive data, and selected fields are encrypted at rest.
 * </ul>
 *
 * <h2>Lifecycle</h2>
 *
 * <p>Enrollment lifecycle is tracked via status and active flags. Services in this area are
 * responsible for enforcing allowed transitions (e.g., bind/verify flows).
 *
 * <h2>Data handling</h2>
 *
 * <p>{@link org.ezkey.enrollment.domain.entity.Enrollment} uses the transient/plaintext vs
 * persisted/encrypted pattern for sensitive strings, with hashing for secure equality checks and
 * lookups.
 *
 * <h2>Key entry points</h2>
 *
 * <ul>
 *   <li>{@link org.ezkey.enrollment.service.EnrollmentService}
 *   <li>{@link org.ezkey.enrollment.service.EnrollmentBindService}
 *   <li>{@link org.ezkey.enrollment.service.EnrollmentVerifyService}
 *   <li>{@link org.ezkey.enrollment.domain.repository.EnrollmentRepository}
 *   <li>{@link org.ezkey.enrollment.domain.entity.Enrollment}
 * </ul>
 *
 * <h2>References</h2>
 *
 * <ul>
 *   <li>{@code docs/ENDPOINT.md} (pending/polling model and proof-token usage)
 * </ul>
 *
 * @since 2025
 */
package org.ezkey.enrollment;
