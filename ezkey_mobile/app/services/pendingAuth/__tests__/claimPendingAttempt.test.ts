/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: claimPendingAttempt tests
 * Description: Unit tests for the shared pending-claim helper (proof token, API pull, fail-closed verify).
 * @since 2026
 */

jest.mock('../../api/authAttempts', () => ({
  MALFORMED_PENDING_RESPONSE: 'MALFORMED_PENDING_RESPONSE',
  authAttemptsApi: {
    pending: jest.fn(),
  },
}));

jest.mock('../../crypto', () => ({
  cryptoService: {
    requireEnrollmentKeyPair: jest.fn(),
    sign: jest.fn(),
    verify: jest.fn(),
  },
}));

jest.mock('../../crypto/authAttemptPayload', () => ({
  buildPendingPayload: jest.fn(),
}));

jest.mock('../../../utils/generateProofToken', () => ({
  generateProofToken: jest.fn(),
}));

jest.mock('../../../utils/sha256HexUtf8', () => ({
  sha256HexUtf8: jest.fn(),
}));

import {authAttemptsApi, MALFORMED_PENDING_RESPONSE} from '../../api/authAttempts';
import {cryptoService} from '../../crypto';
import {buildPendingPayload} from '../../crypto/authAttemptPayload';
import {generateProofToken} from '../../../utils/generateProofToken';
import {sha256HexUtf8} from '../../../utils/sha256HexUtf8';
import {claimPendingAttempt} from '../claimPendingAttempt';
import type {StoredEnrollment} from '../../storage/enrollmentStorage';

const mockAuthAttemptsApi = jest.mocked(authAttemptsApi);
const mockCryptoService = jest.mocked(cryptoService);
const mockBuildPendingPayload = jest.mocked(buildPendingPayload);
const mockGenerateProofToken = jest.mocked(generateProofToken);
const mockSha256HexUtf8 = jest.mocked(sha256HexUtf8);

const sampleEnrollment: StoredEnrollment = {
  id: 'iaaaaaaaaaaaaaaaa_e1',
  integrationId: '1',
  integrationName: 'Acme',
  tenantName: 'ACME Corp',
  createdAt: '2026-01-01T00:00:00.000Z',
  lastActivityAt: '2026-01-01T00:00:00.000Z',
  enrollmentProofToken: 'enrollment-token',
  enrollmentId: '1',
  integrationPublicKey: 'integration-pubkey',
  installation: {
    id: 'https://auth.example.com',
    authUrl: 'https://auth.example.com',
    host: 'auth.example.com',
    name: 'Acme EU',
    lastRefreshedAt: '2026-05-08T00:00:00.000Z',
  },
};

describe('claimPendingAttempt', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockCryptoService.requireEnrollmentKeyPair.mockResolvedValue(undefined);
    mockGenerateProofToken.mockResolvedValue('device-proof-token');
    mockCryptoService.sign.mockResolvedValue('device-proof-sig');
    mockBuildPendingPayload.mockReturnValue('pending-payload');
    mockSha256HexUtf8.mockImplementation((value: string) => `sha256(${value})`);
    mockCryptoService.verify.mockResolvedValue(true);
  });

  it('returns none when the Auth API has no pending attempt', async () => {
    mockAuthAttemptsApi.pending.mockResolvedValue(undefined);

    const result = await claimPendingAttempt(sampleEnrollment);

    expect(result).toEqual({kind: 'none'});
    expect(mockCryptoService.verify).not.toHaveBeenCalled();
  });

  it('fails closed when pending returns a malformed HTTP 200 body', async () => {
    const malformedError = new Error('Malformed pending authentication response');
    malformedError.name = MALFORMED_PENDING_RESPONSE;
    mockAuthAttemptsApi.pending.mockRejectedValue(malformedError);

    const result = await claimPendingAttempt(sampleEnrollment);

    expect(result).toEqual({
      kind: 'fail_closed',
      reason: 'malformed_pending_response',
    });
    expect(mockCryptoService.verify).not.toHaveBeenCalled();
  });

  it('returns attempt when pending response verifies against the integration key', async () => {
    mockAuthAttemptsApi.pending.mockResolvedValue({
      authAttemptId: 42,
      authAttemptProofToken: 'attempt-token',
      authAttemptProofTokenSignedByIntegration: 'attempt-sig',
      authAttemptChallengeRequired: true,
      contextTitle: 'Login Request',
      contextMessage: 'Please approve this login.',
    });

    const result = await claimPendingAttempt(sampleEnrollment, {
      checkedAt: '2026-07-19T12:00:00.000Z',
    });

    expect(mockAuthAttemptsApi.pending).toHaveBeenCalledWith(
      {
        enrollmentId: '1',
        enrollmentProofToken: 'enrollment-token',
        deviceProofToken: 'device-proof-token',
        deviceProofTokenSigned: 'device-proof-sig',
      },
      'https://auth.example.com',
    );
    expect(mockBuildPendingPayload).toHaveBeenCalledWith(
      'attempt-token',
      true,
      'Login Request',
      'Please approve this login.',
    );
    expect(mockCryptoService.verify).toHaveBeenCalledWith(
      'pending-payload',
      'attempt-sig',
      'integration-pubkey',
    );
    expect(result).toEqual({
      kind: 'attempt',
      attempt: {
        authAttemptId: '42',
        authAttemptProofToken: 'attempt-token',
        authAttemptProofTokenSignedByIntegration: 'attempt-sig',
        challengeRequired: true,
        integrationName: 'Acme',
        tenantName: 'ACME Corp',
        createdAt: '2026-07-19T12:00:00.000Z',
        contextTitle: 'Login Request',
        contextMessage: 'Please approve this login.',
      },
    });
  });

  it('fails closed when the enrollment has no integration public key', async () => {
    mockAuthAttemptsApi.pending.mockResolvedValue({
      authAttemptId: 42,
      authAttemptProofToken: 'attempt-token',
      authAttemptProofTokenSignedByIntegration: 'attempt-sig',
      authAttemptChallengeRequired: false,
      contextTitle: undefined,
      contextMessage: undefined,
    });

    const result = await claimPendingAttempt({
      ...sampleEnrollment,
      integrationPublicKey: undefined,
    });

    expect(result).toEqual({
      kind: 'fail_closed',
      reason: 'missing_integration_public_key',
    });
    expect(mockCryptoService.verify).not.toHaveBeenCalled();
  });

  it('fails closed when the integration signature is invalid', async () => {
    mockAuthAttemptsApi.pending.mockResolvedValue({
      authAttemptId: 42,
      authAttemptProofToken: 'attempt-token',
      authAttemptProofTokenSignedByIntegration: 'bad-sig',
      authAttemptChallengeRequired: false,
      contextTitle: undefined,
      contextMessage: undefined,
    });
    mockCryptoService.verify.mockResolvedValue(false);

    const result = await claimPendingAttempt(sampleEnrollment);

    expect(result.kind).toBe('fail_closed');
    if (result.kind !== 'fail_closed') {
      return;
    }
    expect(result.reason).toBe('invalid_pending_signature');
    expect(result.diagnostics?.signatureValid).toBe(false);
  });

  it('emits progress steps for debug consumers', async () => {
    mockAuthAttemptsApi.pending.mockResolvedValue({
      authAttemptId: 7,
      authAttemptProofToken: 'attempt-token',
      authAttemptProofTokenSignedByIntegration: 'attempt-sig',
      authAttemptChallengeRequired: false,
      contextTitle: undefined,
      contextMessage: undefined,
    });
    const onStep = jest.fn();

    await claimPendingAttempt(sampleEnrollment, {onStep});

    expect(onStep.mock.calls.map(call => call[0])).toEqual([
      'start',
      'after_ensure',
      'after_pending',
      'before_verify',
      'after_verify',
    ]);
  });

  it('requires an existing key and does not generate when the alias is missing', async () => {
    mockCryptoService.requireEnrollmentKeyPair.mockRejectedValue(
      Object.assign(new Error('ENROLLMENT_KEY_MISSING'), {name: 'ENROLLMENT_KEY_MISSING'}),
    );

    await expect(claimPendingAttempt(sampleEnrollment)).rejects.toMatchObject({
      name: 'ENROLLMENT_KEY_MISSING',
    });
    expect(mockAuthAttemptsApi.pending).not.toHaveBeenCalled();
    expect(mockGenerateProofToken).not.toHaveBeenCalled();
  });

  it('propagates Auth API network errors to the caller', async () => {
    mockAuthAttemptsApi.pending.mockRejectedValue(new Error('Network error'));

    await expect(claimPendingAttempt(sampleEnrollment)).rejects.toThrow('Network error');
  });

  it('generates a fresh deviceProofToken on every user-initiated pull', async () => {
    mockAuthAttemptsApi.pending.mockResolvedValue(undefined);
    mockGenerateProofToken
      .mockResolvedValueOnce('device-proof-token-1')
      .mockResolvedValueOnce('device-proof-token-2');

    await claimPendingAttempt(sampleEnrollment);
    await claimPendingAttempt(sampleEnrollment);

    expect(mockGenerateProofToken).toHaveBeenCalledTimes(2);
    expect(mockAuthAttemptsApi.pending.mock.calls[0][0].deviceProofToken).toBe(
      'device-proof-token-1',
    );
    expect(mockAuthAttemptsApi.pending.mock.calls[1][0].deviceProofToken).toBe(
      'device-proof-token-2',
    );
  });
});

