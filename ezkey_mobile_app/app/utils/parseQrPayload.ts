/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: parseQrPayload
 * Description: Parses enrollment QR code payloads (JSON or pipe-delimited format).
 * @since 2025
 */

import {validateAuthUrl} from './urlValidation';

export type QrPayload = {
  enrollmentId: string;
  enrollmentProofToken: string;
  language?: string;
  authUrl?: string;
};

/**
 * Parses raw QR payload into structured enrollment data.
 * Supports JSON format or pipe-delimited enrollmentId|enrollmentProofToken.
 *
 * @param value Raw QR code payload.
 * @return Structured enrollment payload.
 * @throws Error when the payload does not contain the expected fields.
 */
export function parseQrPayload(value: string): QrPayload {
  const trimmed = value.trim();
  if (!trimmed) {
    throw new Error('Empty payload');
  }
  try {
    const json = JSON.parse(trimmed);
    if (json.enrollmentId && json.enrollmentProofToken) {
      return {
        enrollmentId: String(json.enrollmentId),
        enrollmentProofToken: String(json.enrollmentProofToken),
        language: json.language ? String(json.language) : undefined,
        authUrl: validateAuthUrl(json.authUrl),
      };
    }
  } catch {
    // try pipe format
  }
  const pipeParts = trimmed.split('|');
  if (pipeParts.length >= 2) {
    return {
      enrollmentId: pipeParts[0],
      enrollmentProofToken: pipeParts.slice(1).join('|'),
    };
  }
  throw new Error('Unsupported QR format');
}
