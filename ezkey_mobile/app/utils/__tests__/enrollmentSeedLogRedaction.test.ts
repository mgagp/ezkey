/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

import {
  logEnrollmentSeedIngest,
  redactParsedQrPayloadForLog,
  redactSeedBlobForLog,
} from '../enrollmentSeedLogRedaction';
import {sha256HexUtf8} from '../sha256HexUtf8';

describe('enrollmentSeedLogRedaction', () => {
  const secretToken = 'super-secret-enrollment-proof-token';
  const rawQr = JSON.stringify({
    enrollmentId: '42',
    enrollmentProofToken: secretToken,
    authUrl: 'https://auth.example.com',
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  it('redactSeedBlobForLog never includes the plaintext', () => {
    const redacted = redactSeedBlobForLog(rawQr);
    expect(redacted).toContain(`len=${rawQr.length}`);
    expect(redacted).toContain(sha256HexUtf8(rawQr).slice(0, 16));
    expect(redacted).not.toContain(secretToken);
    expect(redacted).not.toContain(rawQr);
  });

  it('redactParsedQrPayloadForLog keeps enrollmentId and redacts the proof token', () => {
    const redacted = redactParsedQrPayloadForLog({
      enrollmentId: '42',
      enrollmentProofToken: secretToken,
      authUrl: 'https://auth.example.com',
    });
    expect(redacted.enrollmentId).toBe('42');
    expect(redacted.authUrl).toBe('present');
    expect(redacted.proofToken).toContain('redacted');
    expect(redacted.proofToken).not.toContain(secretToken);
    expect(JSON.stringify(redacted)).not.toContain(secretToken);
  });

  it('success path logs redacted summary without plaintext when diagnostics enabled', () => {
    const logSpy = jest.spyOn(console, 'log').mockImplementation(() => {});
    logEnrollmentSeedIngest({
      source: 'camera',
      rawValue: rawQr,
      normalizedValue: rawQr,
      parsed: {
        enrollmentId: '42',
        enrollmentProofToken: secretToken,
        authUrl: 'https://auth.example.com',
      },
      diagnosticsEnabled: true,
      rawDumpEnabled: false,
    });
    expect(logSpy).toHaveBeenCalled();
    const joined = logSpy.mock.calls.map(args => args.join(' ')).join('\n');
    expect(joined).toContain('Seed ingest (redacted)');
    expect(joined).not.toContain(secretToken);
    expect(joined).not.toContain('Seed raw dump enabled');
  });

  it('parse failure always warns redacted and omits plaintext without raw dump', () => {
    const warnSpy = jest.spyOn(console, 'warn').mockImplementation(() => {});
    const logSpy = jest.spyOn(console, 'log').mockImplementation(() => {});
    logEnrollmentSeedIngest({
      source: 'controlled-bypass',
      rawValue: 'bad-qr',
      normalizedValue: 'bad-qr',
      parseErrorMessage: 'Unsupported QR format',
      diagnosticsEnabled: false,
      rawDumpEnabled: false,
    });
    expect(warnSpy).toHaveBeenCalled();
    const warned = warnSpy.mock.calls.map(args => args.join(' ')).join('\n');
    expect(warned).toContain('Invalid seed payload (redacted)');
    expect(warned).toContain('Unsupported QR format');
    expect(logSpy).not.toHaveBeenCalled();
  });

  it('raw dump flag emits plaintext blobs in addition to redacted summary', () => {
    const logSpy = jest.spyOn(console, 'log').mockImplementation(() => {});
    logEnrollmentSeedIngest({
      source: 'camera',
      rawValue: rawQr,
      normalizedValue: rawQr,
      parsed: {
        enrollmentId: '42',
        enrollmentProofToken: secretToken,
        authUrl: 'https://auth.example.com',
      },
      diagnosticsEnabled: false,
      rawDumpEnabled: true,
    });
    const joined = logSpy.mock.calls.map(args => args.join(' ')).join('\n');
    expect(joined).toContain('Seed ingest (redacted)');
    expect(joined).toContain('Seed raw dump enabled');
    expect(joined).toContain(secretToken);
  });
});
