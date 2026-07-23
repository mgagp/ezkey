/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: localEnrollmentIdentity
 * Description: Installation-scoped local enrollment identity for storage and Keystore handles.
 * Security Context: Server enrollment ids are installation-local integers. Local handles must
 *                   include the trust-zone (normalized Auth URL) so multi-install on one phone
 *                   cannot collide on Keystore aliases or sealed-secret keys (MOB-011).
 * @since 2026
 */

import {sha256} from 'js-sha256';

import type {StoredEnrollment} from '../services/storage/enrollmentStorage';

/**
 * Error code when a pending/respond path needs a Keystore key that is absent.
 *
 * @since 2026
 */
export const ENROLLMENT_KEY_MISSING = 'ENROLLMENT_KEY_MISSING';

/**
 * Derives a Keystore-safe, installation-scoped local enrollment id (O3).
 *
 * Format: {@code i{16-hex-sha256(installationId)}_e{serverEnrollmentId}}
 * — alphanumeric plus underscore only, suitable for Android Keystore alias suffixes.
 *
 * @param installationId Canonical trust-zone id (normalized Auth URL).
 * @param serverEnrollmentId Auth API / DB enrollment id for that installation.
 * @return Local enrollment handle used for storage, navigation, and Keystore.
 * @throws Error when either input is empty.
 * @since 2026
 */
export function deriveLocalEnrollmentId(
  installationId: string,
  serverEnrollmentId: string | number,
): string {
  const zone = installationId.trim();
  const server = String(serverEnrollmentId).trim();
  if (!zone || !server) {
    throw new Error('installationId and serverEnrollmentId are required for local enrollment id');
  }
  const installHash = sha256(zone).slice(0, 16);
  return `i${installHash}_e${server}`;
}

/**
 * Resolves the Auth API enrollment id from a stored record.
 *
 * New records persist {@link StoredEnrollment.enrollmentId} as the server id and
 * {@link StoredEnrollment.id} as the local handle. Legacy rows may still have
 * {@code id === server id}.
 *
 * @param enrollment Stored enrollment.
 * @return Server enrollment id string for Auth API request bodies.
 * @since 2026
 */
export function resolveServerEnrollmentId(enrollment: StoredEnrollment): string {
  if (enrollment.enrollmentId != null && String(enrollment.enrollmentId).trim() !== '') {
    return String(enrollment.enrollmentId).trim();
  }
  return enrollment.id.toString();
}
