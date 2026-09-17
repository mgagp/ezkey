/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Enrollment continuity workflow — MOBILE_FUNCTIONAL_FLOWS.md Enrollment Nominal / Exception.
 * Product intent: bind and verify stay cryptographically linked; persist only after both signatures pass.
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

const mockPopToTop = jest.fn();
const mockSaveEnrollmentMutateAsync = jest.fn();
let alertSpy: jest.SpyInstance;

function renderHook(): {result: {current: EnrollmentWizardState}} {
  const result = {current: null as unknown as EnrollmentWizardState};

  function Probe() {
    result.current = useEnrollmentWizard(mockPopToTop);
    return null;
  }

  act(() => {
    create(React.createElement(Probe));
  });

  return {result};
}

beforeEach(() => {
  jest.clearAllMocks();
  alertSpy = jest.spyOn(Alert, 'alert').mockImplementation(() => {});
  mockUseSaveEnrollment.mockReturnValue({
    mutateAsync: mockSaveEnrollmentMutateAsync,
  } as unknown as ReturnType<typeof useSaveEnrollment>);
  mockParseQrPayload.mockReturnValue({
    enrollmentId: '1',
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
    enrollmentBindPayloadSignedByIntegration: 'bind-sig',
  });
  mockBuildBindPayload.mockReturnValue('bind-payload');
  mockAlgoError.mockReturnValue(null);
  mockCryptoService.verify.mockResolvedValue(true);
  mockCryptoService.ensureEnrollmentKeyPair.mockResolvedValue(true);
  mockCryptoService.getPublicKey.mockResolvedValue('device-pub');
  mockCryptoService.getEnrollmentPrivateKeyStorageTier.mockResolvedValue('STANDARD');
  mockCryptoService.sign.mockResolvedValue('verify-device-sig');
  mockCryptoService.deleteEnrollmentKeyPair.mockResolvedValue(true);
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
});

afterEach(() => {
  alertSpy.mockRestore();
});

describe('enrollment continuity (bind linked to verify)', () => {
  it('persists enrollment only after bind and verify-result signatures both pass', async () => {
    const {result} = renderHook();

    await act(async () => {
      result.current.handleQrScanned('qr-value');
    });
    expect(result.current.hasDraft).toBe(true);
    expect(mockCryptoService.verify).toHaveBeenNthCalledWith(
      1,
      'bind-payload',
      'bind-sig',
      'pubkey',
    );
    expect(mockSaveEnrollmentMutateAsync).not.toHaveBeenCalled();

    await act(async () => {
      result.current.setEnrollmentChallenge('123456');
    });
    await act(async () => {
      result.current.handlePrimary(true, jest.fn());
    });

    expect(mockCryptoService.verify).toHaveBeenNthCalledWith(
      2,
      'verify-result-payload',
      'verify-result-sig',
      'pubkey',
    );
    expect(mockSaveEnrollmentMutateAsync).toHaveBeenCalledWith(
      expect.objectContaining({
        enrollmentProofToken: 'token-abc',
        enrollmentId: '1',
        integrationPublicKey: 'pubkey',
      }),
    );
    expect(mockPopToTop).toHaveBeenCalledTimes(1);
  });

  it('does not persist when the verify-result signature fails after a trusted bind', async () => {
    mockCryptoService.verify.mockResolvedValueOnce(true).mockResolvedValueOnce(false);
    const {result} = renderHook();

    await act(async () => {
      result.current.handleQrScanned('qr-value');
    });
    expect(result.current.hasDraft).toBe(true);

    await act(async () => {
      result.current.setEnrollmentChallenge('123456');
    });
    await act(async () => {
      result.current.handlePrimary(true, jest.fn());
    });

    expect(mockSaveEnrollmentMutateAsync).not.toHaveBeenCalled();
    expect(mockPopToTop).not.toHaveBeenCalled();
    expect(mockCryptoService.deleteEnrollmentKeyPair).toHaveBeenCalled();
  });
});
