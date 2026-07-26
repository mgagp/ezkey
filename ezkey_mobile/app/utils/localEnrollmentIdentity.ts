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
 * Derives a Keystore-safe installation trust-zone scope id.
 *
 * Format: {@code i{16-hex-sha256(installationId)}} — alphanumeric only, suitable both as an
 * Android Keystore alias suffix (signing keys, MOB-011) and as an app seal-key alias suffix
 * (MOB-017 — installation-scoped seal key, replacing the single app-wide `ezkey_app_seal_v1`).
 * Reused by {@link deriveLocalEnrollmentId} so both handles share the same installation hash.
 *
 * @param installationId Canonical trust-zone id (normalized Auth URL).
 * @return Keystore-safe installation scope id.
 * @throws Error when the input is empty.
 * @since 2026
 */
export function deriveInstallationScopeId(installationId: string): string {
  const zone = installationId.trim();
  if (!zone) {
    throw new Error('installationId is required to derive an installation scope id');
  }
  return `i${sha256(zone).slice(0, 16)}`;
}

/**
 * Derives a Keystore-safe, installation-scoped local enrollment id (O3).
 *
 * Format: {@code {installationScopeId}_e{serverEnrollmentId}}
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
  const server = String(serverEnrollmentId).trim();
  if (!server) {
    throw new Error('installationId and serverEnrollmentId are required for local enrollment id');
  }
  return `${deriveInstallationScopeId(installationId)}_e${server}`;
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
