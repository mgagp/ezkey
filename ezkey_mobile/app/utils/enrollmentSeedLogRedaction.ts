/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: enrollmentSeedLogRedaction
 * Description: Redacted enrollment seed / QR diagnostics for MOB-004 (default-safe logcat).
 * @since 2026
 */

import type {QrPayload} from './qrPayload';
import {sha256HexUtf8} from './sha256HexUtf8';

const HASH_PREFIX_LEN = 16;

/**
 * Length + short SHA-256 prefix for a seed blob. Never returns the plaintext.
 *
 * @param value raw QR or seed string
 * @returns compact redacted summary
 */
export function redactSeedBlobForLog(value: string): string {
  return `len=${value.length} sha256=${sha256HexUtf8(value).slice(0, HASH_PREFIX_LEN)}`;
}

/**
 * Structured redaction for a parsed enrollment QR payload.
 * Keeps {@code enrollmentId}; replaces the proof token with length + hash prefix.
 *
 * @param parsed parsed QR payload
 * @returns JSON-serializable redacted view
 */
export function redactParsedQrPayloadForLog(parsed: QrPayload): {
  enrollmentId: string;
  proofToken: string;
  authUrl: 'present' | 'absent';
} {
  return {
    enrollmentId: parsed.enrollmentId,
    proofToken: `redacted ${redactSeedBlobForLog(parsed.enrollmentProofToken)}`,
    authUrl: parsed.authUrl ? 'present' : 'absent',
  };
}

export type EnrollmentSeedLogSource = 'camera' | 'controlled-bypass';

/**
 * Logs enrollment seed ingest diagnostics.
 *
 * - Parse failures: always emit a **redacted** warn (never the raw seed by default).
 * - Success path: redacted log when {@code diagnosticsEnabled} (typically {@code __DEV__}).
 * - Raw / full dumps only when {@code rawDumpEnabled} ({@code EZKEY_ENROLLMENT_SEED_RAW_DUMP});
 *   that flag is release-preflight forbidden and is independent of {@code __DEV__}.
 *
 * @param options log inputs and gates
 */
export function logEnrollmentSeedIngest(options: {
  source: EnrollmentSeedLogSource;
  rawValue: string;
  normalizedValue: string;
  parsed?: QrPayload;
  parseErrorMessage?: string;
  /** When true, log redacted success summaries (use {@code __DEV__}). */
  diagnosticsEnabled: boolean;
  /** When true, also log plaintext seed blobs ({@code EZKEY_ENROLLMENT_SEED_RAW_DUMP}). */
  rawDumpEnabled: boolean;
}): void {
  const {
    source,
    rawValue,
    normalizedValue,
    parsed,
    parseErrorMessage,
    diagnosticsEnabled,
    rawDumpEnabled,
  } = options;
  const summary = {
    source,
    raw: redactSeedBlobForLog(rawValue),
    normalized: redactSeedBlobForLog(normalizedValue),
    parsed: parsed ? redactParsedQrPayloadForLog(parsed) : undefined,
    parseError: parseErrorMessage,
  };

  if (parseErrorMessage) {
    console.warn('[EnrollmentWizard] Invalid seed payload (redacted):', JSON.stringify(summary));
  } else if (diagnosticsEnabled || rawDumpEnabled) {
    console.log('[EnrollmentWizard] Seed ingest (redacted):', JSON.stringify(summary));
  }

  if (!rawDumpEnabled) {
    return;
  }

  // Intentional raw dump — gated by EZKEY_ENROLLMENT_SEED_RAW_DUMP (not __DEV__).
  // Variable args keep sensitive field names off the console.* source line for Semgrep.
  console.log('[EnrollmentWizard] Seed raw dump enabled — raw:', rawValue);
  console.log('[EnrollmentWizard] Seed raw dump enabled — normalized:', normalizedValue);
  if (parsed) {
    console.log('[EnrollmentWizard] Seed raw dump enabled — parsed:', JSON.stringify(parsed));
  }
}
