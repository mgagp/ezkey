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
      // PendingAuthScreen registers beforeRemove → Auth API deny on leave (#831).
      addListener: jest.fn(() => jest.fn()),
      dispatch: jest.fn(),
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

  it('does not send leave-deny while approve respond is in flight (#833 m2)', async () => {
    let resolveRespond!: (value: {
      authAttemptId: number;
      authAttemptResult: 'APPROVED';
      authAttemptMessage: string;
      authAttemptProofTokenResultSignedByIntegration: string;
    }) => void;
    mockAuthAttemptsApi.respond.mockImplementation(
      () =>
        new Promise(resolve => {
          resolveRespond = resolve as typeof resolveRespond;
        }),
    );

    const beforeRemoveHandlers: Array<(e: {preventDefault: () => void; data: {action: unknown}}) => void> =
      [];
    const pendingNavigation = {
      navigate: jest.fn(),
      goBack: jest.fn(),
      canGoBack: jest.fn().mockReturnValue(true),
      addListener: jest.fn((event: string, handler: (e: never) => void) => {
        if (event === 'beforeRemove') {
          beforeRemoveHandlers.push(handler as never);
        }
        return jest.fn();
      }),
      dispatch: jest.fn(),
    };
    const pendingRoute = {
      key: 'PendingAuth-key',
      name: 'PendingAuth' as const,
      params: {enrollmentId, initialAttempt: pendingAttempt},
    };

    let pendingTree: renderer.ReactTestRenderer;
    await renderer.act(async () => {
      pendingTree = renderer.create(
        <PendingAuthScreen
          navigation={pendingNavigation as never}
          route={pendingRoute as never}
        />,
      );
    });

    const approve = pendingTree!.root.find(
      node => node.props.testID === 'ezkey.e2e.pendingAuth.approve',
    );
    // Start approve but leave respond hanging (in-flight).
    await renderer.act(async () => {
      // Fire-and-forget: respond() is intentionally left unresolved below.
      approve.props.onPress();
    });
    // Allow microtasks up to the hung respond() call.
    await renderer.act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });

    expect(mockAuthAttemptsApi.respond).toHaveBeenCalledTimes(1);
    expect(beforeRemoveHandlers.length).toBeGreaterThan(0);

    const preventDefault = jest.fn();
    await renderer.act(async () => {
      beforeRemoveHandlers[0]!({
        preventDefault,
        data: {action: {type: 'GO_BACK'}},
      });
      await Promise.resolve();
    });

    // Leave must not start a second respond(false) while approve is in flight.
    expect(preventDefault).not.toHaveBeenCalled();
    expect(mockAuthAttemptsApi.respond).toHaveBeenCalledTimes(1);
    expect(mockAuthAttemptsApi.respond).toHaveBeenCalledWith(
      expect.objectContaining({authAttemptAccepted: true, authAttemptId: '42'}),
      'https://auth.example.com',
    );

    await renderer.act(async () => {
      resolveRespond({
        authAttemptId: 42,
        authAttemptResult: 'APPROVED',
        authAttemptMessage: 'Approved',
        authAttemptProofTokenResultSignedByIntegration: 'respond-result-sig',
      });
      await Promise.resolve();
    });
  });

  it('keeps Approve disabled until the 2-digit challenge is complete, then approves in one press (#1082)', async () => {
    const challengeAttempt = {...pendingAttempt, challengeRequired: true};
    const pendingNavigation = {
      navigate: jest.fn(),
      goBack: jest.fn(),
      canGoBack: jest.fn().mockReturnValue(true),
      addListener: jest.fn(() => jest.fn()),
      dispatch: jest.fn(),
    };
    const pendingRoute = {
      key: 'PendingAuth-key',
      name: 'PendingAuth' as const,
      params: {enrollmentId, initialAttempt: challengeAttempt},
    };

    let pendingTree: renderer.ReactTestRenderer;
    await renderer.act(async () => {
      pendingTree = renderer.create(
        <PendingAuthScreen
          navigation={pendingNavigation as never}
          route={pendingRoute as never}
        />,
      );
    });

    const findApprove = () =>
      pendingTree!.root.find(node => node.props.testID === 'ezkey.e2e.pendingAuth.approve');
    const findDeny = () =>
      pendingTree!.root.find(node => node.props.testID === 'ezkey.e2e.pendingAuth.deny');
    const typeChallenge = async (text: string) => {
      const input = pendingTree!.root.find(
        node => node.props.keyboardType === 'number-pad' && node.props.onChangeText != null,
      );
      await renderer.act(async () => {
        input.props.onChangeText(text);
      });
    };

    expect(findApprove().props.disabled).toBe(true);
    expect(findDeny().props.disabled).toBe(false);

    await typeChallenge('4');
    expect(findApprove().props.disabled).toBe(true);

    await typeChallenge('42');
    expect(findApprove().props.disabled).toBe(false);

    await renderer.act(async () => {
      await findApprove().props.onPress();
    });

    expect(mockAuthAttemptsApi.respond).toHaveBeenCalledTimes(1);
    expect(mockAuthAttemptsApi.respond).toHaveBeenCalledWith(
      expect.objectContaining({
        authAttemptAccepted: true,
        authAttemptChallengeResponse: '42',
      }),
      'https://auth.example.com',
    );
  });

  it('does not record a trusted outcome when the respond-result signature is invalid', async () => {
    mockCryptoService.verify.mockResolvedValue(false);
    const pendingNavigation = {
      navigate: jest.fn(),
      goBack: jest.fn(),
      canGoBack: jest.fn().mockReturnValue(true),
      addListener: jest.fn(() => jest.fn()),
      dispatch: jest.fn(),
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
