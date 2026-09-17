/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: useEnrollmentWizard hook tests
 * Description: Unit tests covering the enrollment wizard business logic:
 *              camera permission, QR scanning, bind/verify flows, and error states.
 * @since 2025
 */

jest.mock('../useEnrollments', () => ({
  useSaveEnrollment: jest.fn(),
}));

jest.mock('../../services/api/enrollments', () => ({
  enrollmentsApi: {
    bind: jest.fn(),
    verify: jest.fn(),
  },
}));

jest.mock('../../services/api/instanceInfo', () => ({
  fetchVerifiedInstanceInfo: jest.fn(),
}));

jest.mock('../../services/crypto', () => ({
  cryptoService: {
    verify: jest.fn(),
    ensureEnrollmentKeyPair: jest.fn(),
    deleteEnrollmentKeyPair: jest.fn(),
    getPublicKey: jest.fn(),
    getEnrollmentPrivateKeyStorageTier: jest.fn(),
    sign: jest.fn(),
  },
}));

jest.mock('../../utils/qrPayload', () => ({
  parseQrPayload: jest.fn(),
}));

jest.mock('../../services/crypto/enrollmentPayload', () => ({
  buildBindPayload: jest.fn(),
  buildVerifyDevicePayload: jest.fn(),
  buildVerifyResultPayload: jest.fn(),
}));

jest.mock('../../utils/integrationKeyAlgorithm', () => ({
  integrationKeyAlgorithmBindError: jest.fn(),
}));

jest.mock('../../utils/installationMetadata', () => ({
  buildInstallation: jest.fn(),
  resolveEnrollmentAuthUrl: jest.fn(),
}));

jest.mock('../../config/env', () => ({
  env: {
    configuredApiBaseUrl: 'https://ezkey.example.com',
    enrollmentSeedRawDump: false,
    enrollmentSeedBypassEnabled: false,
    enrollmentSeedBypassAck: undefined,
    enrollmentSeedBypassQrPayload: undefined,
  },
}));

import React from 'react';
import {act, create} from 'react-test-renderer';
import {Alert} from 'react-native';
import {useSaveEnrollment} from '../useEnrollments';
import {enrollmentsApi} from '../../services/api/enrollments';
import {fetchVerifiedInstanceInfo} from '../../services/api/instanceInfo';
import {cryptoService} from '../../services/crypto';
import {parseQrPayload} from '../../utils/qrPayload';
import {
  buildBindPayload,
  buildVerifyDevicePayload,
  buildVerifyResultPayload,
} from '../../services/crypto/enrollmentPayload';
import {integrationKeyAlgorithmBindError} from '../../utils/integrationKeyAlgorithm';
import {buildInstallation, resolveEnrollmentAuthUrl} from '../../utils/installationMetadata';
import {useEnrollmentWizard} from '../useEnrollmentWizard';
import type {EnrollmentWizardState} from '../useEnrollmentWizard';

const mockUseSaveEnrollment = jest.mocked(useSaveEnrollment);
const mockEnrollmentsApi = jest.mocked(enrollmentsApi);
const mockCryptoService = jest.mocked(cryptoService);
const mockParseQrPayload = jest.mocked(parseQrPayload);
const mockBuildBindPayload = jest.mocked(buildBindPayload);
const mockBuildVerifyDevicePayload = jest.mocked(buildVerifyDevicePayload);
const mockBuildVerifyResultPayload = jest.mocked(buildVerifyResultPayload);
const mockAlgoError = jest.mocked(integrationKeyAlgorithmBindError);
const mockFetchVerifiedInstanceInfo = jest.mocked(fetchVerifiedInstanceInfo);
const mockBuildInstallation = jest.mocked(buildInstallation);
const mockResolveEnrollmentAuthUrl = jest.mocked(resolveEnrollmentAuthUrl);

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

const mockPopToTop = jest.fn();
const mockGoBack = jest.fn();
const mockSaveEnrollmentMutateAsync = jest.fn();
let alertSpy: jest.SpyInstance;

function renderHook(popToTop = mockPopToTop): {result: {current: EnrollmentWizardState}} {
  const result = {current: null as unknown as EnrollmentWizardState};

  function Probe() {
    result.current = useEnrollmentWizard(popToTop);
    return null;
  }

  act(() => {
    create(React.createElement(Probe));
  });

  return {result};
}

/** Drives the hook to a state where `draft` is set (after a successful QR scan + bind). */
async function renderHookWithDraft(): Promise<{result: {current: EnrollmentWizardState}}> {
  mockParseQrPayload.mockReturnValue({
    enrollmentId: 'enr-1',
    enrollmentProofToken: 'token-abc',
    authUrl: 'https://auth.example.com',
  });
  mockEnrollmentsApi.bind.mockResolvedValue({
    enrollmentId: 1,
    enrollmentProofToken: 'token-abc',
    integrationPublicKey: 'pubkey',
    integrationKeyAlgorithm: 'ed25519',
    integrationName: 'Acme',
    tenantId: 3,
    tenantName: 'Unicorn farm accountability',
    tenantDescription: 'Ops',
    isSystemIntegration: false,
    enrollmentBindPayloadSignedByIntegration: 'bind-sig',
  });
  mockBuildBindPayload.mockReturnValue('bind-payload');
  mockAlgoError.mockReturnValue(null);
  mockCryptoService.verify.mockResolvedValue(true);

  const hook = renderHook();

  await act(async () => {
    hook.result.current.handleQrScanned('qr-value');
  });

  return hook;
}

function stubVerifyPath() {
  mockCryptoService.ensureEnrollmentKeyPair.mockResolvedValue(true);
  mockCryptoService.getPublicKey.mockResolvedValue('device-pub');
  mockCryptoService.getEnrollmentPrivateKeyStorageTier.mockResolvedValue('STANDARD');
  mockCryptoService.sign.mockResolvedValue('verify-device-sig');
  mockCryptoService.deleteEnrollmentKeyPair.mockResolvedValue(undefined as never);
  mockEnrollmentsApi.verify.mockResolvedValue({
    active: true,
    enrollmentVerifyMessage: 'ok',
    enrollmentVerifyPayloadSignedByIntegration: 'verify-result-sig',
  });
  mockBuildVerifyDevicePayload.mockReturnValue('verify-device-payload');
  mockBuildVerifyResultPayload.mockReturnValue('verify-result-payload');
  mockResolveEnrollmentAuthUrl.mockReturnValue('https://auth.example.com');
  mockFetchVerifiedInstanceInfo.mockResolvedValue(null);
  mockBuildInstallation.mockReturnValue({
    id: 'https://auth.example.com',
    authUrl: 'https://auth.example.com',
    host: 'auth.example.com',
    name: 'auth.example.com',
  });
  mockSaveEnrollmentMutateAsync.mockResolvedValue(undefined);
}

// ---------------------------------------------------------------------------
// Setup
// ---------------------------------------------------------------------------

beforeEach(() => {
  jest.clearAllMocks();
  alertSpy = jest.spyOn(Alert, 'alert').mockImplementation(() => {});
  mockUseSaveEnrollment.mockReturnValue({
    mutateAsync: mockSaveEnrollmentMutateAsync,
  } as unknown as ReturnType<typeof useSaveEnrollment>);
});

afterEach(() => {
  alertSpy.mockRestore();
});

// ---------------------------------------------------------------------------
// Initial state
// ---------------------------------------------------------------------------

describe('initial state', () => {
  it('starts with no draft, scanner hidden, and no errors', () => {
    const {result} = renderHook();

    expect(result.current.hasDraft).toBe(false);
    expect(result.current.scannerVisible).toBe(false);
    expect(result.current.bindError).toBeUndefined();
    expect(result.current.cameraError).toBeUndefined();
    expect(result.current.challengeError).toBeUndefined();
    expect(result.current.isSubmitting).toBe(false);
    expect(result.current.isBinding).toBe(false);
    expect(result.current.enrollmentChallenge).toBe('');
  });

  it('primaryDisabled is false and secondaryDisabled is false when idle', () => {
    const {result} = renderHook();

    // No draft: primary opens scanner (never disabled unless binding)
    expect(result.current.primaryDisabled).toBe(false);
    expect(result.current.secondaryDisabled).toBe(false);
  });
});

// ---------------------------------------------------------------------------
// handleScannerDismiss
// ---------------------------------------------------------------------------

describe('handleScannerDismiss', () => {
  it('closes the scanner', async () => {
    const {result} = renderHook();

    // Open scanner first
    await act(async () => {
      result.current.handlePrimary(true, jest.fn());
    });
    expect(result.current.scannerVisible).toBe(true);

    // Dismiss it
    await act(async () => {
      result.current.handleScannerDismiss();
    });
    expect(result.current.scannerVisible).toBe(false);
  });
});

// ---------------------------------------------------------------------------
// handleBack
// ---------------------------------------------------------------------------

describe('handleBack', () => {
  it('calls goBack when there is no draft and not submitting', () => {
    const {result} = renderHook();

    act(() => {
      result.current.handleBack(mockGoBack);
    });

    expect(mockGoBack).toHaveBeenCalledTimes(1);
  });

  it('clears the draft without calling goBack when a draft is present', async () => {
    const {result} = await renderHookWithDraft();

    expect(result.current.hasDraft).toBe(true);

    await act(async () => {
      result.current.handleBack(mockGoBack);
    });

    expect(result.current.hasDraft).toBe(false);
    expect(mockGoBack).not.toHaveBeenCalled();
  });
});

// ---------------------------------------------------------------------------
// handleSecondary
// ---------------------------------------------------------------------------

describe('handleSecondary', () => {
  it('shows the "learn more" Alert when there is no draft', () => {
    const {result} = renderHook();

    act(() => {
      result.current.handleSecondary();
    });

    expect(alertSpy).toHaveBeenCalledTimes(1);
  });

  it('clears the draft and calls popToTop when a draft is present', async () => {
    const {result} = await renderHookWithDraft();
    expect(result.current.hasDraft).toBe(true);

    await act(async () => {
      result.current.handleSecondary();
    });

    expect(result.current.hasDraft).toBe(false);
    expect(mockPopToTop).toHaveBeenCalledTimes(1);
    expect(alertSpy).not.toHaveBeenCalled();
  });
});

// ---------------------------------------------------------------------------
// handlePrimary — camera permission
// ---------------------------------------------------------------------------

describe('handlePrimary — camera permission', () => {
  it('opens the scanner immediately when camera permission is already granted', async () => {
    const {result} = renderHook();

    await act(async () => {
      result.current.handlePrimary(true, jest.fn());
    });

    expect(result.current.scannerVisible).toBe(true);
  });

  it('requests permission and opens scanner when permission is granted', async () => {
    const requestPermission = jest.fn().mockResolvedValue(true);
    const {result} = renderHook();

    await act(async () => {
      result.current.handlePrimary(false, requestPermission);
    });

    expect(requestPermission).toHaveBeenCalledTimes(1);
    expect(result.current.scannerVisible).toBe(true);
    expect(result.current.cameraError).toBeUndefined();
  });

  it('sets cameraError when permission is denied', async () => {
    const requestPermission = jest.fn().mockResolvedValue(false);
    const {result} = renderHook();

    await act(async () => {
      result.current.handlePrimary(false, requestPermission);
    });

    expect(result.current.scannerVisible).toBe(false);
    expect(result.current.cameraError).toBeDefined();
  });
});

// ---------------------------------------------------------------------------
// handleQrScanned
// ---------------------------------------------------------------------------

describe('handleQrScanned — invalid QR', () => {
  it('shows an Alert when the QR payload cannot be parsed', async () => {
    mockParseQrPayload.mockImplementation(() => {
      throw new Error('Unsupported QR format');
    });
    const {result} = renderHook();

    await act(async () => {
      result.current.handleQrScanned('bad-qr');
    });

    expect(alertSpy).toHaveBeenCalledTimes(1);
    expect(result.current.scannerVisible).toBe(false);
    expect(result.current.hasDraft).toBe(false);
  });
});

describe('handleQrScanned — bind success', () => {
  it('sets the draft and clears bindError when bind and signature verification succeed', async () => {
    const {result} = await renderHookWithDraft();

    expect(result.current.hasDraft).toBe(true);
    expect(result.current.bindError).toBeUndefined();
    expect(result.current.scannerVisible).toBe(false);
    expect(result.current.draft?.tenantName).toBe('Unicorn farm accountability');
    expect(result.current.draft?.tenantId).toBe(3);
  });

  it('sets bindError when the integration signature is invalid', async () => {
    mockParseQrPayload.mockReturnValue({
      enrollmentId: 'enr-1',
      enrollmentProofToken: 'token-abc',
      authUrl: 'https://auth.example.com',
    });
    mockEnrollmentsApi.bind.mockResolvedValue({
      enrollmentId: 1,
      enrollmentProofToken: 'token-abc',
      integrationPublicKey: 'pubkey',
      integrationKeyAlgorithm: 'ed25519',
      integrationName: 'Acme',
      isSystemIntegration: false,
      enrollmentBindPayloadSignedByIntegration: 'bad-sig',
    });
    mockBuildBindPayload.mockReturnValue('bind-payload');
    mockAlgoError.mockReturnValue(null);
    mockCryptoService.verify.mockResolvedValue(false); // signature invalid

    const {result} = renderHook();

    await act(async () => {
      result.current.handleQrScanned('qr-value');
    });

    expect(result.current.hasDraft).toBe(false);
    expect(result.current.bindError).toBeDefined();
  });

  it('sets bindError when the bind API call fails', async () => {
    mockParseQrPayload.mockReturnValue({
      enrollmentId: 'enr-1',
      enrollmentProofToken: 'token-abc',
      authUrl: 'https://auth.example.com',
    });
    mockEnrollmentsApi.bind.mockRejectedValue(new Error('Network error'));
    mockAlgoError.mockReturnValue(null);

    const {result} = renderHook();

    await act(async () => {
      result.current.handleQrScanned('qr-value');
    });

    expect(result.current.hasDraft).toBe(false);
    expect(result.current.bindError).toBeDefined();
  });

  it('sets bindError when the integration key algorithm is unsupported', async () => {
    mockParseQrPayload.mockReturnValue({
      enrollmentId: 'enr-1',
      enrollmentProofToken: 'token-abc',
      authUrl: 'https://auth.example.com',
    });
    mockEnrollmentsApi.bind.mockResolvedValue({
      enrollmentId: 1,
      enrollmentProofToken: 'token-abc',
      integrationPublicKey: 'pubkey',
      integrationKeyAlgorithm: 'rsa',
      integrationName: 'Acme',
      isSystemIntegration: false,
      enrollmentBindPayloadSignedByIntegration: 'sig',
    });
    mockAlgoError.mockReturnValue('Unsupported algorithm: rsa');

    const {result} = renderHook();

    await act(async () => {
      result.current.handleQrScanned('qr-value');
    });

    expect(result.current.hasDraft).toBe(false);
    expect(result.current.bindError).toBe('Unsupported algorithm: rsa');
  });
});

// ---------------------------------------------------------------------------
// handlePrimary — verify / finalize
// ---------------------------------------------------------------------------

describe('handlePrimary — verify', () => {
  it('sets challengeError and does not call verify when the challenge is not 6 digits', async () => {
    const {result} = await renderHookWithDraft();
    stubVerifyPath();

    await act(async () => {
      result.current.setEnrollmentChallenge('12345');
    });
    await act(async () => {
      result.current.handlePrimary(true, jest.fn());
    });

    expect(result.current.challengeError).toBeDefined();
    expect(mockEnrollmentsApi.verify).not.toHaveBeenCalled();
    expect(mockSaveEnrollmentMutateAsync).not.toHaveBeenCalled();
  });

  it('persists the enrollment and returns home after a valid verify-result signature', async () => {
    const {result} = await renderHookWithDraft();
    stubVerifyPath();
    mockCryptoService.verify.mockResolvedValue(true);

    await act(async () => {
      result.current.setEnrollmentChallenge('123456');
    });
    await act(async () => {
      result.current.handlePrimary(true, jest.fn());
    });

    expect(mockCryptoService.ensureEnrollmentKeyPair).toHaveBeenCalled();
    expect(mockEnrollmentsApi.verify).toHaveBeenCalled();
    expect(mockSaveEnrollmentMutateAsync).toHaveBeenCalledWith(
      expect.objectContaining({
        tenantId: 3,
        tenantName: 'Unicorn farm accountability',
        tenantDescription: 'Ops',
      }),
    );
    expect(mockPopToTop).toHaveBeenCalledTimes(1);
    expect(result.current.hasDraft).toBe(false);
  });

  it('deletes the key and does not persist when the verify-result signature is invalid', async () => {
    const {result} = await renderHookWithDraft();
    stubVerifyPath();
    mockCryptoService.verify.mockResolvedValue(false);

    await act(async () => {
      result.current.setEnrollmentChallenge('123456');
    });
    await act(async () => {
      result.current.handlePrimary(true, jest.fn());
    });

    expect(mockSaveEnrollmentMutateAsync).not.toHaveBeenCalled();
    expect(mockPopToTop).not.toHaveBeenCalled();
    expect(mockCryptoService.deleteEnrollmentKeyPair).toHaveBeenCalled();
    expect(result.current.challengeError).toBeDefined();
    expect(result.current.enrollmentChallenge).toBe('');
  });

  it('deletes the key and surfaces challengeError when verify throws after key creation', async () => {
    const {result} = await renderHookWithDraft();
    stubVerifyPath();
    mockEnrollmentsApi.verify.mockRejectedValue(new Error('verify failed'));

    await act(async () => {
      result.current.setEnrollmentChallenge('123456');
    });
    await act(async () => {
      result.current.handlePrimary(true, jest.fn());
    });

    expect(mockSaveEnrollmentMutateAsync).not.toHaveBeenCalled();
    expect(mockCryptoService.deleteEnrollmentKeyPair).toHaveBeenCalled();
    expect(result.current.challengeError).toBe('Request failed.');
  });

  it('still persists when signed instance-info is unavailable', async () => {
    const {result} = await renderHookWithDraft();
    stubVerifyPath();
    mockCryptoService.verify.mockResolvedValue(true);
    mockFetchVerifiedInstanceInfo.mockResolvedValue(null);

    await act(async () => {
      result.current.setEnrollmentChallenge('123456');
    });
    await act(async () => {
      result.current.handlePrimary(true, jest.fn());
    });

    expect(mockSaveEnrollmentMutateAsync).toHaveBeenCalled();
    expect(mockPopToTop).toHaveBeenCalledTimes(1);
  });
});
