/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: qrPayload
 * Description: Parser for enrollment QR code payloads (JSON and pipe-delimited formats).
 * Security Context: Validates and normalizes the Auth API URL embedded in QR codes. Enforces HTTPS-only
 *                   connections in production to prevent server impersonation via a crafted QR code.
 * @since 2025
 */

import {validateAuthUrl} from './urlValidation';

/**
 * Parsed result of an enrollment QR code payload.
 *
 * @since 2025
 */
export type QrPayload = {
  enrollmentId: string;
  enrollmentProofToken: string;
  /** Validated and normalized Auth API base URL when present in the QR code. */
  authUrl?: string;
};

/**
 * Parses the enrollment QR payload which may be JSON or a pipe-delimited fallback.
 *
 * The JSON format is the primary format and may include an `authUrl` field pointing to the Ezkey Auth API
 * host for this enrollment. When present the URL is validated (HTTPS enforced, dev loopback tolerated).
 *
 * The pipe-delimited format (`enrollmentId|enrollmentProofToken`) is kept for backward compatibility and
 * does not carry an `authUrl`; the global default from `env.apiBaseUrl` will be used instead.
 *
 * The function enforces the payload constraints described in `docs/ENDPOINT.md` ensuring we extract proof tokens
 * without introducing alternate parsing paths that could weaken enrollment verification.
 *
 * @param value Raw QR code payload string.
 * @return Structured enrollment payload including the optional validated `authUrl`.
 * @throws Error when the payload is empty, contains an invalid Auth API URL, or matches no supported format.
 * @since 2025
 */
export const parseQrPayload = (value: string): QrPayload => {
  const trimmed = value.trim();
  if (!trimmed) {
    throw new Error('Empty payload');
  }

  // Try JSON format first. Only JSON.parse itself is wrapped — validation errors propagate directly.
  let parsed:
    | {enrollmentId?: string | number; enrollmentProofToken?: string; authUrl?: string}
    | undefined;
  try {
    parsed = JSON.parse(trimmed) as typeof parsed;
  } catch {
    // Not valid JSON — fall through to pipe-delimited format.
  }

  if (parsed?.enrollmentId && parsed?.enrollmentProofToken) {
    if (parsed.authUrl !== undefined && validateAuthUrl(parsed.authUrl) == null) {
      throw new Error('Invalid Auth API URL in QR payload.');
    }
    return {
      enrollmentId: String(parsed.enrollmentId),
      enrollmentProofToken: String(parsed.enrollmentProofToken),
      authUrl: validateAuthUrl(parsed.authUrl),
    };
  }

  // Pipe-delimited fallback: enrollmentId|enrollmentProofToken
  const pipeParts = trimmed.split('|');
  if (pipeParts.length >= 2) {
    return {
      enrollmentId: pipeParts[0],
      enrollmentProofToken: pipeParts.slice(1).join('|'),
    };
  }

  throw new Error('Unsupported QR format');
};
