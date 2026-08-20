/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Authentication continuity workflow — MOBILE_FUNCTIONAL_FLOWS.md Authentication Nominal / Exception.
 * Product intent: pending and respond stay cryptographically linked; Home never starts a claim.
 *
 * Pull-model / MOB-015 entry points (not remounted here):
 * - Home never calls claimPendingAttempt — __tests__/HomeScreen.test.tsx
 * - Broken Detail offers no Check pending — __tests__/EnrollmentDetailScreen.test.tsx
 */

jest.mock('../app/hooks/useEnrollments', () => ({
  useEnrollments: jest.fn(),
  useDeleteEnrollment: jest.fn(),
  useMarkEnrollmentPendingChecked: jest.fn(),
  useEnrollmentById: jest.fn(),
}));

jest.mock('../app/services/pendingAuth/claimPendingAttempt', () => ({
  claimPendingAttempt: jest.fn(),
}));

jest.mock('../app/services/api/authAttempts', () => ({
  authAttemptsApi: {
    pending: jest.fn(),
    respond: jest.fn(),
  },
}));

jest.mock('../app/services/crypto', () => ({
  cryptoService: {
    requireEnrollmentKeyPair: jest.fn(),
    ensureEnrollmentKeyPair: jest.fn(),
    sign: jest.fn(),
    signForRespond: jest.fn(),
    verify: jest.fn(),
    deleteEnrollmentKeyPair: jest.fn(),
  },
}));

jest.mock('../app/services/crypto/authAttemptPayload', () => ({
  buildPendingPayload: jest.fn(),
  buildRespondPayload: jest.fn(),
  buildRespondResultPayload: jest.fn(),
}));

jest.mock('../app/services/security/approvalRequirement', () => ({
  requiresProtectedApproval: jest.fn(),
}));

jest.mock('../app/services/storage/securityPreferenceStorage', () => ({
  securityPreferenceStorage: {
    getSecurityLevel: jest.fn(),
  },
}));

jest.mock('../app/utils/generateProofToken', () => ({
  generateProofToken: jest.fn(),
}));

jest.mock('../app/utils/sha256HexUtf8', () => ({
  sha256HexUtf8: jest.fn(),
}));

jest.mock('../app/state/enrollmentStore', () => ({
  useEnrollmentStore: jest.fn(),
}));

import React from 'react';
import renderer from 'react-test-renderer';
import {EnrollmentDetailScreen} from '../app/screens/EnrollmentDetail';
import {PendingAuthScreen} from '../app/screens/PendingAuth';
import {
  useDeleteEnrollment,
  useEnrollmentById,
  useEnrollments,
  useMarkEnrollmentPendingChecked,
} from '../app/hooks/useEnrollments';
import {claimPendingAttempt} from '../app/services/pendingAuth/claimPendingAttempt';
import {authAttemptsApi} from '../app/services/api/authAttempts';
import {cryptoService} from '../app/services/crypto';
import {
  buildRespondPayload,
  buildRespondResultPayload,
} from '../app/services/crypto/authAttemptPayload';
import {requiresProtectedApproval} from '../app/services/security/approvalRequirement';
import {securityPreferenceStorage} from '../app/services/storage/securityPreferenceStorage';
import {useEnrollmentStore} from '../app/state/enrollmentStore';

const mockUseEnrollments = jest.mocked(useEnrollments);
const mockUseDeleteEnrollment = jest.mocked(useDeleteEnrollment);
const mockUseMarkEnrollmentPendingChecked = jest.mocked(useMarkEnrollmentPendingChecked);
const mockUseEnrollmentById = jest.mocked(useEnrollmentById);
const mockClaimPendingAttempt = jest.mocked(claimPendingAttempt);
const mockAuthAttemptsApi = jest.mocked(authAttemptsApi);
const mockCryptoService = jest.mocked(cryptoService);
const mockBuildRespondPayload = jest.mocked(buildRespondPayload);
const mockBuildRespondResultPayload = jest.mocked(buildRespondResultPayload);
const mockRequiresProtectedApproval = jest.mocked(requiresProtectedApproval);
const mockSecurityPreferenceStorage = jest.mocked(securityPreferenceStorage);
const mockUseEnrollmentStore = jest.mocked(useEnrollmentStore);
const mockSetRecentAuthResult = jest.fn();

const enrollmentId = 'iaaaaaaaaaaaaaaaa_e1';

const healthyEnrollment = {
  id: enrollmentId,
  integrationId: '1',
  integrationName: 'Acme',
  tenantName: 'ACME Corp',
  installation: {
    id: 'https://auth.example.com',
    authUrl: 'https://auth.example.com',
    host: 'auth.example.com',
    name: 'Acme EU',
  },
  enrollmentProofToken: 'enrollment-token',
  enrollmentId: '1',
  integrationPublicKey: 'integration-pubkey',
  createdAt: '2026-01-01T00:00:00.000Z',
  lastActivityAt: '2026-01-01T00:00:00.000Z',
};

const pendingAttempt = {
  authAttemptId: '42',
  authAttemptProofToken: 'attempt-token',
  authAttemptProofTokenSignedByIntegration: 'attempt-sig',
  challengeRequired: false,
  integrationName: 'Acme',
  tenantName: 'ACME Corp',
  createdAt: '2026-08-18T12:00:00.000Z',
  contextTitle: 'Login Request',
  contextMessage: 'Please approve this login.',
};

describe('authentication continuity (pending linked to respond)', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockUseDeleteEnrollment.mockReturnValue({isPending: false, mutate: jest.fn()} as never);
    mockUseMarkEnrollmentPendingChecked.mockReturnValue({
      mutateAsync: jest.fn().mockResolvedValue(undefined),
    } as never);
    mockUseEnrollments.mockReturnValue({
      data: {enrollments: [healthyEnrollment], broken: [], collectionError: false},
      isLoading: false,
      refetch: jest.fn(),
    } as never);
    mockUseEnrollmentById.mockReturnValue({
      data: healthyEnrollment,
      isLoading: false,
    } as never);
    mockUseEnrollmentStore.mockImplementation((selector: (store: any) => any) =>
      selector({
        setRecentAuthResult: mockSetRecentAuthResult,
        selectedId: undefined,
        recentAuthResults: {},
        setSelected: jest.fn(),
        clear: jest.fn(),
      }),
    );
    mockClaimPendingAttempt.mockResolvedValue({kind: 'attempt', attempt: pendingAttempt});
    mockAuthAttemptsApi.respond.mockResolvedValue({
      authAttemptId: 42,
      authAttemptResult: 'APPROVED',
      authAttemptMessage: 'Approved',
      authAttemptProofTokenResultSignedByIntegration: 'respond-result-sig',
    });
    mockCryptoService.requireEnrollmentKeyPair.mockResolvedValue(undefined);
    mockCryptoService.signForRespond.mockResolvedValue('respond-sig');
    mockCryptoService.verify.mockResolvedValue(true);
    mockBuildRespondPayload.mockReturnValue('respond-payload');
    mockBuildRespondResultPayload.mockReturnValue('respond-result-payload');
    mockRequiresProtectedApproval.mockReturnValue(false);
    mockSecurityPreferenceStorage.getSecurityLevel.mockResolvedValue('standard');
  });

  it('returns a trusted outcome only after pending claim and respond-result signatures both pass', async () => {
    const detailNavigation = {
      navigate: jest.fn(),
      setOptions: jest.fn(),
      popToTop: jest.fn(),
    };
    const detailRoute = {
      key: 'EnrollmentDetail-key',
      name: 'EnrollmentDetail' as const,
      params: {enrollmentId},
    };

    const detailProps = {
      navigation: detailNavigation as never,
      route: detailRoute as never,
    };

    let detailTree: renderer.ReactTestRenderer;
    await renderer.act(async () => {
      detailTree = renderer.create(<EnrollmentDetailScreen {...detailProps} />);
    });

    const checkPending = detailTree!.root.find(
      node => node.props.testID === 'ezkey.e2e.enrollmentDetail.checkPending',
    );
    await renderer.act(async () => {
      await checkPending.props.onPress();
    });

    expect(detailNavigation.navigate).toHaveBeenCalledWith('PendingAuth', {
      enrollmentId,
      initialAttempt: pendingAttempt,
    });

    const pendingNavigation = {
      navigate: jest.fn(),
      goBack: jest.fn(),
      canGoBack: jest.fn().mockReturnValue(true),
    };
    const pendingRoute = {
      key: 'PendingAuth-key',
      name: 'PendingAuth' as const,
      params: {enrollmentId, initialAttempt: pendingAttempt},
    };

    const pendingProps = {
      navigation: pendingNavigation as never,
      route: pendingRoute as never,
    };

    let pendingTree: renderer.ReactTestRenderer;
    await renderer.act(async () => {
      pendingTree = renderer.create(<PendingAuthScreen {...pendingProps} />);
    });

    const approve = pendingTree!.root.find(
      node => node.props.testID === 'ezkey.e2e.pendingAuth.approve',
    );
    await renderer.act(async () => {
      await approve.props.onPress();
    });

    expect(mockAuthAttemptsApi.respond).toHaveBeenCalledWith(
      expect.objectContaining({authAttemptAccepted: true, authAttemptId: '42'}),
      'https://auth.example.com',
    );
    expect(mockCryptoService.verify).toHaveBeenCalledWith(
      'respond-result-payload',
      'respond-result-sig',
      'integration-pubkey',
    );
    expect(mockSetRecentAuthResult).toHaveBeenCalledWith(
      enrollmentId,
      expect.objectContaining({status: 'approved'}),
    );
    expect(pendingNavigation.goBack).toHaveBeenCalledTimes(1);
  });

  it('does not record a trusted outcome when the respond-result signature is invalid', async () => {
    mockCryptoService.verify.mockResolvedValue(false);
    const pendingNavigation = {
      navigate: jest.fn(),
      goBack: jest.fn(),
      canGoBack: jest.fn().mockReturnValue(true),
    };
    const pendingRoute = {
      key: 'PendingAuth-key',
      name: 'PendingAuth' as const,
      params: {enrollmentId, initialAttempt: pendingAttempt},
    };

    const pendingProps = {
      navigation: pendingNavigation as never,
      route: pendingRoute as never,
    };

    let pendingTree: renderer.ReactTestRenderer;
    await renderer.act(async () => {
      pendingTree = renderer.create(<PendingAuthScreen {...pendingProps} />);
    });

    const approve = pendingTree!.root.find(
      node => node.props.testID === 'ezkey.e2e.pendingAuth.approve',
    );
    await renderer.act(async () => {
      await approve.props.onPress();
    });

    expect(mockSetRecentAuthResult).not.toHaveBeenCalled();
    expect(pendingNavigation.goBack).not.toHaveBeenCalled();
    expect(
      pendingTree!.root.findAll(
        node => node.props.testID === 'ezkey.e2e.pendingAuth.globalErrorState',
      ).length,
    ).toBeGreaterThan(0);
  });
});
