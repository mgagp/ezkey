/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: usePendingAuth hook tests
 * Description: Unit tests covering pending-auth business logic: loading attempts,
 *              Ed25519 signature verification, approve/deny respond flow, and error states.
 * @since 2025
 */

jest.mock('../useEnrollments', () => ({
  useEnrollmentById: jest.fn(),
  useMarkEnrollmentPendingChecked: jest.fn(),
}));

jest.mock('../../services/api/authAttempts', () => ({
  authAttemptsApi: {
    pending: jest.fn(),
    respond: jest.fn(),
  },
}));

jest.mock('../../services/crypto', () => ({
  cryptoService: {
    ensureEnrollmentKeyPair: jest.fn(),
    sign: jest.fn(),
    signForRespond: jest.fn(),
    verify: jest.fn(),
  },
}));

jest.mock('../../services/crypto/authAttemptPayload', () => ({
  buildPendingPayload: jest.fn(),
  buildRespondPayload: jest.fn(),
  buildRespondResultPayload: jest.fn(),
}));

jest.mock('../../services/security/approvalRequirement', () => ({
  requiresProtectedApproval: jest.fn(),
}));

jest.mock('../../services/storage/securityPreferenceStorage', () => ({
  securityPreferenceStorage: {
    getSecurityLevel: jest.fn(),
  },
}));

jest.mock('../../utils/generateProofToken', () => ({
  generateProofToken: jest.fn(),
}));

jest.mock('../../utils/sha256HexUtf8', () => ({
  sha256HexUtf8: jest.fn(),
}));

jest.mock('../../state/enrollmentStore', () => ({
  useEnrollmentStore: jest.fn(),
}));

import React from 'react';
import {act, create} from 'react-test-renderer';
import {useEnrollmentById, useMarkEnrollmentPendingChecked} from '../useEnrollments';
import {authAttemptsApi} from '../../services/api/authAttempts';
import {cryptoService} from '../../services/crypto';
import {
  buildPendingPayload,
  buildRespondPayload,
  buildRespondResultPayload,
} from '../../services/crypto/authAttemptPayload';
import {requiresProtectedApproval} from '../../services/security/approvalRequirement';
import {securityPreferenceStorage} from '../../services/storage/securityPreferenceStorage';
import {generateProofToken} from '../../utils/generateProofToken';
import {sha256HexUtf8} from '../../utils/sha256HexUtf8';
import {useEnrollmentStore} from '../../state/enrollmentStore';
import {usePendingAuth} from '../usePendingAuth';
import type {PendingAuthState} from '../usePendingAuth';
import type {PendingAttempt} from '../../services/pendingAuth/types';
import type {StoredEnrollment} from '../../services/storage/enrollmentStorage';

const mockUseEnrollmentById = jest.mocked(useEnrollmentById);
const mockUseMarkEnrollmentPendingChecked = jest.mocked(useMarkEnrollmentPendingChecked);
const mockAuthAttemptsApi = jest.mocked(authAttemptsApi);
const mockCryptoService = jest.mocked(cryptoService);
const mockBuildPendingPayload = jest.mocked(buildPendingPayload);
const mockBuildRespondPayload = jest.mocked(buildRespondPayload);
const mockBuildRespondResultPayload = jest.mocked(buildRespondResultPayload);
const mockRequiresProtectedApproval = jest.mocked(requiresProtectedApproval);
const mockSecurityPreferenceStorage = jest.mocked(securityPreferenceStorage);
const mockGenerateProofToken = jest.mocked(generateProofToken);
const mockSha256HexUtf8 = jest.mocked(sha256HexUtf8);
const mockUseEnrollmentStore = jest.mocked(useEnrollmentStore);

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

const sampleEnrollment: StoredEnrollment = {
  id: 'enr-1',
  integrationId: 'int-1',
  integrationName: 'Acme',
  tenantName: 'ACME Corp',
  createdAt: '2026-01-01T00:00:00.000Z',
  lastActivityAt: '2026-01-01T00:00:00.000Z',
  enrollmentProofToken: 'enrollment-token',
  integrationPublicKey: 'integration-pubkey',
  installation: {
    id: 'https://auth.example.com',
    authUrl: 'https://auth.example.com',
    host: 'auth.example.com',
    name: 'Acme EU',
    lastRefreshedAt: '2026-05-08T00:00:00.000Z',
  },
};

const sampleAttempt: PendingAttempt = {
  authAttemptId: 'attempt-1',
  authAttemptProofToken: 'attempt-token',
  authAttemptProofTokenSignedByIntegration: 'attempt-sig',
  challengeRequired: false,
  integrationName: 'Acme',
  tenantName: 'ACME Corp',
  createdAt: '2026-05-08T10:00:00.000Z',
  contextTitle: 'Login Request',
  contextMessage: 'Please approve this login.',
};

const defaultNav = {
  canGoBack: jest.fn().mockReturnValue(true),
  goBack: jest.fn(),
  navigateToEnrollmentDetail: jest.fn(),
};

const mockMarkPendingChecked = jest.fn().mockResolvedValue(undefined);
const mockSetRecentAuthResult = jest.fn();

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function renderHook(
  enrollmentId = 'enr-1',
  initialAttempt: PendingAttempt | undefined = undefined,
  nav = defaultNav,
): {result: {current: PendingAuthState}} {
  const result = {current: null as unknown as PendingAuthState};

  function Probe() {
    result.current = usePendingAuth(enrollmentId, initialAttempt, nav);
    return null;
  }

  act(() => {
    create(React.createElement(Probe));
  });

  return {result};
}

// ---------------------------------------------------------------------------
// Setup
// ---------------------------------------------------------------------------

beforeEach(() => {
  jest.clearAllMocks();

  mockUseEnrollmentById.mockReturnValue({
    data: sampleEnrollment,
    isLoading: false,
  } as unknown as ReturnType<typeof useEnrollmentById>);

  mockUseMarkEnrollmentPendingChecked.mockReturnValue({
    mutateAsync: mockMarkPendingChecked,
  } as unknown as ReturnType<typeof useMarkEnrollmentPendingChecked>);

  mockUseEnrollmentStore.mockImplementation((selector: (store: any) => any) =>
    selector({setRecentAuthResult: mockSetRecentAuthResult}),
  );

  // Default: no pending attempt
  mockAuthAttemptsApi.pending.mockResolvedValue(undefined);
  mockAuthAttemptsApi.respond.mockResolvedValue({
    authAttemptId: 1,
    authAttemptResult: 'APPROVED',
    authAttemptMessage: 'Approved',
    authAttemptProofTokenResultSignedByIntegration: 'respond-result-sig',
  });

  mockCryptoService.ensureEnrollmentKeyPair.mockResolvedValue(true);
  mockCryptoService.sign.mockResolvedValue('device-sig');
  mockCryptoService.signForRespond.mockResolvedValue('respond-sig');
  mockCryptoService.verify.mockResolvedValue(true);

  mockGenerateProofToken.mockResolvedValue('device-proof-token');
  mockSha256HexUtf8.mockReturnValue('sha256hex');
  mockBuildPendingPayload.mockReturnValue('pending-payload');
  mockBuildRespondPayload.mockReturnValue('respond-payload');
  mockBuildRespondResultPayload.mockReturnValue('respond-result-payload');

  mockRequiresProtectedApproval.mockReturnValue(false);
  mockSecurityPreferenceStorage.getSecurityLevel.mockResolvedValue('standard');

  defaultNav.canGoBack.mockReturnValue(true);
  defaultNav.goBack.mockReset();
  defaultNav.navigateToEnrollmentDetail.mockReset();
});

// ---------------------------------------------------------------------------
// Initial state
// ---------------------------------------------------------------------------

describe('initial state', () => {
  it('exposes the initialAttempt when one is provided', () => {
    // Pass initialAttempt to suppress auto-load
    const {result} = renderHook('enr-1', sampleAttempt);

    expect(result.current.attempt).toEqual(sampleAttempt);
    expect(result.current.globalError).toBeUndefined();
    expect(result.current.isProcessing).toBe(false);
  });

  it('shows empty state when no attempt is loaded, not loading, and no error', async () => {
    // No enrollment loaded yet → auto-load won't fire
    mockUseEnrollmentById.mockReturnValue({
      data: undefined,
      isLoading: false,
    } as unknown as ReturnType<typeof useEnrollmentById>);

    const {result} = renderHook('enr-1', undefined);

    expect(result.current.showEmptyState).toBe(true);
  });
});

// ---------------------------------------------------------------------------
// loadPendingAttempt
// ---------------------------------------------------------------------------

describe('loadPendingAttempt', () => {
  it('returns early without calling the API when the enrollment is not loaded', async () => {
    mockUseEnrollmentById.mockReturnValue({
      data: undefined,
      isLoading: false,
    } as unknown as ReturnType<typeof useEnrollmentById>);

    const {result} = renderHook('enr-1', undefined);

    await act(async () => {
      await result.current.loadPendingAttempt();
    });

    expect(mockAuthAttemptsApi.pending).not.toHaveBeenCalled();
  });

  it('clears the attempt when the API returns null (no pending auth)', async () => {
    mockAuthAttemptsApi.pending.mockResolvedValue(undefined);

    // Pass initialAttempt to suppress auto-load; we call loadPendingAttempt manually
    const {result} = renderHook('enr-1', sampleAttempt);

    await act(async () => {
      await result.current.loadPendingAttempt();
    });

    expect(result.current.attempt).toBeUndefined();
    expect(result.current.globalError).toBeUndefined();
  });

  it('sets the attempt when the API returns a pending auth and the signature is valid', async () => {
    mockAuthAttemptsApi.pending.mockResolvedValue({
      authAttemptId: 42,
      authAttemptProofToken: 'attempt-token',
      authAttemptProofTokenSignedByIntegration: 'attempt-sig',
      authAttemptChallengeRequired: false,
      contextTitle: 'Login Request',
      contextMessage: 'Please approve this login.',
    });
    mockCryptoService.verify.mockResolvedValue(true);

    // Pass initialAttempt to suppress auto-load
    const {result} = renderHook('enr-1', undefined);
    // Without initialAttempt, the auto-load fires on mount — flush it fully
    await act(async () => {});

    expect(result.current.attempt).toBeDefined();
    expect(result.current.attempt?.authAttemptProofToken).toBe('attempt-token');
    expect(result.current.globalError).toBeUndefined();
  });

  it('sets globalError and clears attempt when the integration signature is invalid', async () => {
    mockAuthAttemptsApi.pending.mockResolvedValue({
      authAttemptId: 42,
      authAttemptProofToken: 'attempt-token',
      authAttemptProofTokenSignedByIntegration: 'bad-sig',
      authAttemptChallengeRequired: false,
      contextTitle: undefined,
      contextMessage: undefined,
    });
    mockCryptoService.verify.mockResolvedValue(false);

    const {result} = renderHook('enr-1', sampleAttempt);

    await act(async () => {
      await result.current.loadPendingAttempt();
    });

    expect(result.current.attempt).toBeUndefined();
    expect(result.current.globalError).toBeDefined();
  });

  it('sets globalError when the enrollment has no integration public key', async () => {
    mockUseEnrollmentById.mockReturnValue({
      data: {...sampleEnrollment, integrationPublicKey: undefined},
      isLoading: false,
    } as unknown as ReturnType<typeof useEnrollmentById>);
    mockAuthAttemptsApi.pending.mockResolvedValue({
      authAttemptId: 42,
      authAttemptProofToken: 'attempt-token',
      authAttemptProofTokenSignedByIntegration: 'sig',
      authAttemptChallengeRequired: false,
      contextTitle: undefined,
      contextMessage: undefined,
    });

    const {result} = renderHook('enr-1', sampleAttempt);

    await act(async () => {
      await result.current.loadPendingAttempt();
    });

    expect(result.current.globalError).toBeDefined();
    expect(result.current.attempt).toBeUndefined();
  });

  it('sets globalError when the API call throws a network error', async () => {
    mockAuthAttemptsApi.pending.mockRejectedValue(new Error('Network error'));

    const {result} = renderHook('enr-1', sampleAttempt);

    await act(async () => {
      await result.current.loadPendingAttempt();
    });

    expect(result.current.globalError).toBeDefined();
  });
});

// ---------------------------------------------------------------------------
// handleRespond
// ---------------------------------------------------------------------------

describe('handleRespond', () => {
  it('calls the respond API and navigates away on a successful approval', async () => {
    const {result} = renderHook('enr-1', sampleAttempt);

    await act(async () => {
      await result.current.handleRespond(true);
    });

    expect(mockAuthAttemptsApi.respond).toHaveBeenCalledTimes(1);
    const call = mockAuthAttemptsApi.respond.mock.calls[0][0];
    expect(call.authAttemptAccepted).toBe(true);
    expect(mockSetRecentAuthResult).toHaveBeenCalledWith(
      'enr-1',
      expect.objectContaining({status: 'approved'}),
    );
    expect(defaultNav.goBack).toHaveBeenCalledTimes(1);
  });

  it('calls the respond API with accepted=false on denial', async () => {
    mockAuthAttemptsApi.respond.mockResolvedValue({
      authAttemptId: 1,
      authAttemptResult: 'DENIED',
      authAttemptMessage: 'Denied',
      authAttemptProofTokenResultSignedByIntegration: 'respond-result-sig',
    });
    const {result} = renderHook('enr-1', sampleAttempt);

    await act(async () => {
      await result.current.handleRespond(false);
    });

    expect(mockAuthAttemptsApi.respond).toHaveBeenCalledTimes(1);
    const call = mockAuthAttemptsApi.respond.mock.calls[0][0];
    expect(call.authAttemptAccepted).toBe(false);
    expect(mockSetRecentAuthResult).toHaveBeenCalledWith(
      'enr-1',
      expect.objectContaining({status: 'rejected'}),
    );
  });

  it('sets formError and does not call API when challenge is required but input is empty', async () => {
    const challengeAttempt: PendingAttempt = {
      ...sampleAttempt,
      challengeRequired: true,
    };
    const {result} = renderHook('enr-1', challengeAttempt);

    // challengeInput defaults to '' which is shorter than AUTH_CHALLENGE_LENGTH (2)
    await act(async () => {
      await result.current.handleRespond(true);
    });

    expect(result.current.formError).toBeDefined();
    expect(mockAuthAttemptsApi.respond).not.toHaveBeenCalled();
  });

  it('allows denial even when challenge is required and input is empty', async () => {
    const challengeAttempt: PendingAttempt = {
      ...sampleAttempt,
      challengeRequired: true,
    };
    const {result} = renderHook('enr-1', challengeAttempt);

    await act(async () => {
      await result.current.handleRespond(false);
    });

    // Denial must bypass the challenge guard
    expect(result.current.formError).toBeUndefined();
    expect(mockAuthAttemptsApi.respond).toHaveBeenCalledTimes(1);
  });

  it('sets globalError when the respond result signature is invalid', async () => {
    mockCryptoService.verify.mockResolvedValue(false);

    const {result} = renderHook('enr-1', sampleAttempt);

    await act(async () => {
      await result.current.handleRespond(true);
    });

    expect(result.current.globalError).toBeDefined();
    expect(mockSetRecentAuthResult).not.toHaveBeenCalled();
  });

  it('sets globalError when the respond API call throws', async () => {
    mockAuthAttemptsApi.respond.mockRejectedValue(new Error('Server error'));

    const {result} = renderHook('enr-1', sampleAttempt);

    await act(async () => {
      await result.current.handleRespond(true);
    });

    expect(result.current.globalError).toBeDefined();
    expect(mockSetRecentAuthResult).not.toHaveBeenCalled();
  });

  it('sets globalError when the respond result has no signature', async () => {
    mockAuthAttemptsApi.respond.mockResolvedValue({
      authAttemptId: 1,
      authAttemptResult: 'APPROVED',
      authAttemptMessage: 'Approved',
      authAttemptProofTokenResultSignedByIntegration: '   ', // blank
    });

    const {result} = renderHook('enr-1', sampleAttempt);

    await act(async () => {
      await result.current.handleRespond(true);
    });

    expect(result.current.globalError).toBeDefined();
  });
});

// ---------------------------------------------------------------------------
// Derived state — primaryTitle and secondaryTitle
// ---------------------------------------------------------------------------

describe('derived state', () => {
  it('uses contextTitle as primaryTitle when present', () => {
    const {result} = renderHook('enr-1', sampleAttempt);

    // sampleAttempt.contextTitle = 'Login Request'
    expect(result.current.primaryTitle).toBe('Login Request');
  });

  it('falls back to integrationName as primaryTitle when contextTitle is absent', () => {
    const noTitleAttempt: PendingAttempt = {
      ...sampleAttempt,
      contextTitle: undefined,
    };
    const {result} = renderHook('enr-1', noTitleAttempt);

    expect(result.current.primaryTitle).toBe('Acme');
  });

  it('uses integrationName as secondaryTitle when contextTitle is set and different', () => {
    const {result} = renderHook('enr-1', sampleAttempt);

    // contextTitle='Login Request', integrationName='Acme' → different → show integrationName
    expect(result.current.secondaryTitle).toBe('Acme');
  });

  it('uses tenantName as secondaryTitle when contextTitle equals integrationName', () => {
    const sameNameAttempt: PendingAttempt = {
      ...sampleAttempt,
      contextTitle: 'Acme', // same as integrationName
    };
    const {result} = renderHook('enr-1', sameNameAttempt);

    expect(result.current.secondaryTitle).toBe('ACME Corp');
  });
});
