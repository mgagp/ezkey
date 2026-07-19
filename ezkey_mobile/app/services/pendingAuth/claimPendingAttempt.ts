/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: claimPendingAttempt
 * Description: Shared pending-claim orchestration for Enrollment Detail and Pending Auth.
 * Security Context: Owns device proof token generation, Auth API pending pull, and fail-closed
 *                   Ed25519 verification of the integration-signed pending payload. Callers must
 *                   remain user-initiated (no background polling).
 * @since 2026
 */

import {authAttemptsApi} from '../api/authAttempts';
import {buildPendingPayload} from '../crypto/authAttemptPayload';
import {cryptoService} from '../crypto';
import {StoredEnrollment} from '../storage/enrollmentStorage';
import {generateProofToken} from '../../utils/generateProofToken';
import {sha256HexUtf8} from '../../utils/sha256HexUtf8';
import {PendingAttempt} from './types';

/**
 * Optional diagnostic snapshot for on-device pending-auth debug panels.
 * Hashes and short prefixes only — never full proof tokens or payloads.
 *
 * @since 2026
 */
export type ClaimPendingDiagnostics = {
  integrationPublicKeyLength?: number;
  integrationPublicKeyPrefix?: string;
  integrationPublicKeySha256Utf8Hex?: string;
  pendingPayloadLength?: number;
  pendingPayloadSha256Utf8Hex?: string;
  signatureLength?: number;
  signatureSha256Utf8Hex?: string;
  signaturePrefix?: string;
  signatureValid?: boolean;
};

/**
 * Progress steps emitted during claim orchestration (debug / field diagnosis).
 *
 * @since 2026
 */
export type ClaimPendingStep =
  | 'start'
  | 'after_ensure'
  | 'after_pending'
  | 'before_verify'
  | 'after_verify';

/**
 * Discriminated result of a single user-initiated pending claim.
 *
 * @since 2026
 */
export type ClaimPendingResult =
  | {kind: 'none'}
  | {kind: 'attempt'; attempt: PendingAttempt}
  | {
      kind: 'fail_closed';
      reason: 'missing_integration_public_key' | 'invalid_pending_signature';
      diagnostics?: ClaimPendingDiagnostics;
    };

/**
 * Options for {@link claimPendingAttempt}.
 *
 * @since 2026
 */
export type ClaimPendingOptions = {
  /**
   * ISO timestamp used as {@link PendingAttempt.createdAt}.
   * Defaults to {@code new Date().toISOString()} when omitted.
   */
  checkedAt?: string;
  /** Optional progress callback for Pending Auth debug panel steps. */
  onStep?: (step: ClaimPendingStep, diagnostics?: ClaimPendingDiagnostics) => void;
};

/**
 * Claims a pending authentication attempt for the given enrollment.
 *
 * Performs device proof token generation, Auth API {@code pending} pull, and fail-closed
 * verification of the integration signature over the canonical pending payload.
 * Network and unexpected crypto failures propagate as thrown errors for caller mapping.
 *
 * @param enrollment Stored enrollment used for proof tokens and integration public key
 * @param options Optional checked-at timestamp and debug step callback
 * @returns Discriminated claim result ({@code none}, {@code attempt}, or {@code fail_closed})
 * @since 2026
 */
export async function claimPendingAttempt(
  enrollment: StoredEnrollment,
  options?: ClaimPendingOptions,
): Promise<ClaimPendingResult> {
  const checkedAt = options?.checkedAt ?? new Date().toISOString();
  const onStep = options?.onStep;

  onStep?.('start');

  const enrollmentKeyId = enrollment.id.toString();
  await cryptoService.ensureEnrollmentKeyPair(enrollmentKeyId);
  onStep?.('after_ensure');

  const deviceProofToken = await generateProofToken();
  const deviceProofTokenSigned = await cryptoService.sign(enrollmentKeyId, deviceProofToken);
  const response = await authAttemptsApi.pending(
    {
      enrollmentId: enrollment.id,
      enrollmentProofToken: enrollment.enrollmentProofToken,
      deviceProofToken,
      deviceProofTokenSigned,
    },
    enrollment.installation?.authUrl,
  );

  if (!response) {
    return {kind: 'none'};
  }
  onStep?.('after_pending');

  const integrationPublicKey = enrollment.integrationPublicKey;
  if (!integrationPublicKey) {
    return {
      kind: 'fail_closed',
      reason: 'missing_integration_public_key',
    };
  }

  const pendingPayload = buildPendingPayload(
    response.authAttemptProofToken,
    response.authAttemptChallengeRequired ?? false,
    response.contextTitle,
    response.contextMessage,
  );
  const signature = response.authAttemptProofTokenSignedByIntegration ?? '';
  const diagnostics: ClaimPendingDiagnostics = {
    integrationPublicKeyLength: integrationPublicKey.length,
    integrationPublicKeyPrefix: integrationPublicKey.slice(0, 24),
    integrationPublicKeySha256Utf8Hex: sha256HexUtf8(integrationPublicKey),
    pendingPayloadLength: pendingPayload.length,
    pendingPayloadSha256Utf8Hex: sha256HexUtf8(pendingPayload),
    signatureLength: signature.length,
    signatureSha256Utf8Hex: sha256HexUtf8(signature),
    signaturePrefix: signature.slice(0, 24),
  };
  onStep?.('before_verify', diagnostics);

  const signatureValid = await cryptoService.verify(
    pendingPayload,
    response.authAttemptProofTokenSignedByIntegration,
    integrationPublicKey,
  );
  const afterVerifyDiagnostics: ClaimPendingDiagnostics = {
    ...diagnostics,
    signatureValid,
  };
  onStep?.('after_verify', afterVerifyDiagnostics);

  if (!signatureValid) {
    return {
      kind: 'fail_closed',
      reason: 'invalid_pending_signature',
      diagnostics: afterVerifyDiagnostics,
    };
  }

  return {
    kind: 'attempt',
    attempt: {
      authAttemptId: String(response.authAttemptId),
      authAttemptProofToken: response.authAttemptProofToken,
      authAttemptProofTokenSignedByIntegration:
        response.authAttemptProofTokenSignedByIntegration,
      challengeRequired: response.authAttemptChallengeRequired ?? false,
      integrationName: enrollment.integrationName,
      tenantName: enrollment.tenantName,
      createdAt: checkedAt,
      contextTitle: response.contextTitle,
      contextMessage: response.contextMessage,
    },
  };
}
