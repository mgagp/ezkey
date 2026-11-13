/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Enrollment Detail owns the user-initiated pending pull (no background polling).
 */

import React from 'react';
import {AccessibilityInfo, Alert, Text} from 'react-native';
import renderer from 'react-test-renderer';
import {EnrollmentDetailScreen} from '../app/screens/EnrollmentDetail';
import {
  useDeleteEnrollment,
  useEnrollments,
  useMarkEnrollmentPendingChecked,
} from '../app/hooks/useEnrollments';
import {claimPendingAttempt} from '../app/services/pendingAuth/claimPendingAttempt';
import {useEnrollmentStore} from '../app/state/enrollmentStore';
import {getByRole} from '../test-utils/a11yQueries';

jest.mock('../app/hooks/useEnrollments', () => ({
  useEnrollments: jest.fn(),
  useDeleteEnrollment: jest.fn(),
  useMarkEnrollmentPendingChecked: jest.fn(),
}));

jest.mock('../app/services/pendingAuth/claimPendingAttempt', () => ({
  claimPendingAttempt: jest.fn(),
}));

const mockUseEnrollments = jest.mocked(useEnrollments);
const mockUseDeleteEnrollment = jest.mocked(useDeleteEnrollment);
const mockUseMarkEnrollmentPendingChecked = jest.mocked(useMarkEnrollmentPendingChecked);
const mockClaimPendingAttempt = jest.mocked(claimPendingAttempt);

const enrollmentId = 'iaaaaaaaaaaaaaaaa_e1';

const healthyEnrollment = {
  id: enrollmentId,
  integrationId: '1',
  integrationName: 'Admin Console',
  tenantName: 'Tenant Red',
  enrollmentName: 'Pixel 7 Pro',
  installation: {
    id: 'https://auth.acme.example',
    authUrl: 'https://auth.acme.example',
    name: 'Acme EU',
    host: 'auth.acme.example',
  },
  enrollmentProofToken: 'token-1',
  enrollmentId: '1',
  integrationPublicKey: 'pubkey',
  createdAt: '2026-04-20T10:00:00.000Z',
  lastActivityAt: '2026-04-21T10:00:00.000Z',
};

const pendingAttempt = {
  authAttemptId: '42',
  authAttemptProofToken: 'attempt-token',
  authAttemptProofTokenSignedByIntegration: 'attempt-sig',
  challengeRequired: false,
  integrationName: 'Admin Console',
  tenantName: 'Tenant Red',
  createdAt: '2026-08-18T12:00:00.000Z',
};

function renderDetail() {
  const navigation = {
    navigate: jest.fn(),
    setOptions: jest.fn(),
    popToTop: jest.fn(),
  };
  const route = {
    key: 'EnrollmentDetail-key',
    name: 'EnrollmentDetail' as const,
    params: {enrollmentId},
  };
  return {navigation, route};
}

const mountedTrees: renderer.ReactTestRenderer[] = [];

async function renderScreen(
  navigation: ReturnType<typeof renderDetail>['navigation'],
  route: ReturnType<typeof renderDetail>['route'],
): Promise<renderer.ReactTestRenderer> {
  let tree!: renderer.ReactTestRenderer;
  await renderer.act(async () => {
    tree = renderer.create(
      <EnrollmentDetailScreen navigation={navigation as never} route={route as never} />,
    );
  });
  mountedTrees.push(tree);
  return tree;
}

describe('EnrollmentDetailScreen', () => {
  afterEach(() => {
    renderer.act(() => {
      while (mountedTrees.length > 0) {
        mountedTrees.pop()?.unmount();
      }
      useEnrollmentStore.getState().clear();
    });
  });

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
  });

  it('shows Check pending for a healthy enrollment', async () => {
    const {navigation, route} = renderDetail();
    const tree = await renderScreen(navigation, route);

    expect(
      tree!.root.findAll(node => node.props.testID === 'ezkey.e2e.enrollmentDetail.checkPending')
        .length,
    ).toBeGreaterThan(0);
  });

  it('shows enrollment as hero and omits URL, created date, and host hint when branded', async () => {
    const {navigation, route} = renderDetail();
    const tree = await renderScreen(navigation, route);

    expect(
      tree!.root.find(node => node.props.testID === 'ezkey.e2e.enrollmentDetail.hero').props
        .children,
    ).toBe('Pixel 7 Pro');
    expect(
      tree!.root.findAll(node => node.props.testID === 'ezkey.e2e.enrollmentDetail.hostHint')
        .length,
    ).toBe(0);

    const textContent = tree!.root
      .findAllByType(Text)
      .map(node => node.props.children)
      .flat()
      .join(' ');
    expect(textContent).toContain('Admin Console');
    expect(textContent).toContain('Tenant Red');
    expect(textContent).toContain('Acme EU');
    expect(textContent).not.toContain('https://auth.acme.example');
    expect(textContent).not.toContain('Created');
  });

  it('shows a host hint when installation branding fell back to the host', async () => {
    mockUseEnrollments.mockReturnValue({
      data: {
        enrollments: [
          {
            ...healthyEnrollment,
            integrationName: 'auth-api.ezkey.online',
            tenantName: 'auth-api.ezkey.online',
            installation: {
              id: 'https://auth-api.ezkey.online',
              authUrl: 'https://auth-api.ezkey.online',
              name: 'auth-api.ezkey.online',
              host: 'auth-api.ezkey.online',
            },
          },
        ],
        broken: [],
        collectionError: false,
      },
      isLoading: false,
      refetch: jest.fn(),
    } as never);

    const {navigation, route} = renderDetail();
    const tree = await renderScreen(navigation, route);

    expect(
      tree!.root.findAll(node => node.props.testID === 'ezkey.e2e.enrollmentDetail.hostHint')
        .length,
    ).toBeGreaterThan(0);
    const textContent = tree!.root
      .findAllByType(Text)
      .map(node => node.props.children)
      .flat()
      .join(' ');
    expect(textContent).toContain('auth-api.ezkey.online');
    expect(textContent).not.toContain('https://auth-api.ezkey.online');
  });

  it('shows latest response as status, not the stored title', async () => {
    await renderer.act(async () => {
      useEnrollmentStore.getState().setRecentAuthResult(enrollmentId, {
        status: 'approved',
        title: 'Unicorn Farm',
        message: 'Auth attempt completed',
        completedAt: '2026-09-15T12:00:00.000Z',
      });
    });

    const {navigation, route} = renderDetail();
    const tree = await renderScreen(navigation, route);

    expect(
      tree!.root.find(node => node.props.testID === 'ezkey.e2e.enrollmentDetail.recentActionStatus')
        .props.children,
    ).toBe('Approved');
    const textContent = tree!.root
      .findAllByType(Text)
      .map(node => node.props.children)
      .flat()
      .join(' ');
    expect(textContent).not.toContain('Unicorn Farm');
    expect(textContent).not.toContain('Auth attempt completed');
  });

  it('navigates to PendingAuth when claim returns an attempt', async () => {
    mockClaimPendingAttempt.mockResolvedValue({kind: 'attempt', attempt: pendingAttempt});
    const {navigation, route} = renderDetail();
    const tree = await renderScreen(navigation, route);

    const checkPending = tree!.root.find(
      node => node.props.testID === 'ezkey.e2e.enrollmentDetail.checkPending',
    );

    await renderer.act(async () => {
      await checkPending.props.onPress();
    });

    expect(navigation.navigate).toHaveBeenCalledWith('PendingAuth', {
      enrollmentId,
      initialAttempt: pendingAttempt,
    });
  });

  it('stays on Detail with feedback when there is no pending attempt', async () => {
    mockClaimPendingAttempt.mockResolvedValue({kind: 'none'});
    const {navigation, route} = renderDetail();
    const tree = await renderScreen(navigation, route);

    const checkPending = tree!.root.find(
      node => node.props.testID === 'ezkey.e2e.enrollmentDetail.checkPending',
    );

    await renderer.act(async () => {
      await checkPending.props.onPress();
    });

    expect(navigation.navigate).not.toHaveBeenCalled();
    const textContent = tree!.root
      .findAllByType(Text)
      .map(node => node.props.children)
      .flat()
      .join(' ');
    expect(textContent).toContain('No pending requests');
  });

  it('exposes Check pending as a button and announces the no-pending feedback', async () => {
    const announce = jest.spyOn(AccessibilityInfo, 'announceForAccessibility');
    mockClaimPendingAttempt.mockResolvedValue({kind: 'none'});
    const {navigation, route} = renderDetail();
    const tree = await renderScreen(navigation, route);

    expect(getByRole(tree.root, 'button', 'Check pending')).toBeDefined();
    expect(announce).not.toHaveBeenCalled();

    const checkPending = tree.root.find(
      node => node.props.testID === 'ezkey.e2e.enrollmentDetail.checkPending',
    );
    await renderer.act(async () => {
      await checkPending.props.onPress();
    });

    expect(announce).toHaveBeenCalledWith('No pending requests');
    announce.mockRestore();
  });

  it('announces a new response result but not the one present on open', async () => {
    const announce = jest.spyOn(AccessibilityInfo, 'announceForAccessibility');
    renderer.act(() => {
      useEnrollmentStore.getState().setRecentAuthResult(enrollmentId, {
        status: 'approved',
        completedAt: '2026-11-02T10:00:00.000Z',
      } as never);
    });
    const {navigation, route} = renderDetail();
    await renderScreen(navigation, route);
    expect(announce).not.toHaveBeenCalled();

    await renderer.act(async () => {
      useEnrollmentStore.getState().setRecentAuthResult(enrollmentId, {
        status: 'rejected',
        completedAt: '2026-11-02T10:05:00.000Z',
      } as never);
    });

    expect(announce).toHaveBeenCalledWith('Rejected');
    announce.mockRestore();
  });

  it('stays on Detail with feedback when claim fails closed', async () => {
    mockClaimPendingAttempt.mockResolvedValue({
      kind: 'fail_closed',
      reason: 'invalid_pending_signature',
    });
    const {navigation, route} = renderDetail();
    const tree = await renderScreen(navigation, route);

    const checkPending = tree!.root.find(
      node => node.props.testID === 'ezkey.e2e.enrollmentDetail.checkPending',
    );

    await renderer.act(async () => {
      await checkPending.props.onPress();
    });

    expect(navigation.navigate).not.toHaveBeenCalled();
    const textContent = tree!.root
      .findAllByType(Text)
      .map(node => node.props.children)
      .flat()
      .join(' ');
    expect(textContent).toContain('Invalid integration signature on pending response.');
  });

  it('does not offer Check pending for a broken enrollment', async () => {
    mockUseEnrollments.mockReturnValue({
      data: {
        enrollments: [],
        collectionError: false,
        broken: [
          {
            id: enrollmentId,
            reason: 'secret_rehydration_failed',
            metadata: healthyEnrollment,
          },
        ],
      },
      isLoading: false,
      refetch: jest.fn(),
    } as never);

    const {navigation, route} = renderDetail();
    const tree = await renderScreen(navigation, route);

    expect(
      tree!.root.findAll(node => node.props.testID === 'ezkey.e2e.enrollmentDetail.unusable').length,
    ).toBeGreaterThan(0);
    expect(
      tree!.root.findAll(node => node.props.testID === 'ezkey.e2e.enrollmentDetail.checkPending')
        .length,
    ).toBe(0);
    expect(mockClaimPendingAttempt).not.toHaveBeenCalled();
  });

  describe('enrollment no longer active on the server (PR #565 constat)', () => {
    const bindingFailed = Object.assign(new Error('Request failed with status code 400'), {
      response: {
        status: 400,
        data: {
          type: 'https://ezkey.io/problems/auth/auth-attempt-binding-failed',
          title: 'Bad Request',
          detail: 'Authentication request failed',
        },
      },
    });

    async function checkPendingWith(error: unknown) {
      mockClaimPendingAttempt.mockRejectedValue(error);
      const {navigation, route} = renderDetail();
      const tree = await renderScreen(navigation, route);
      const checkPending = tree.root.find(
        node => node.props.testID === 'ezkey.e2e.enrollmentDetail.checkPending',
      );
      await renderer.act(async () => {
        await checkPending.props.onPress();
      });
      return {navigation, tree};
    }

    const byTestId = (tree: renderer.ReactTestRenderer, testID: string) =>
      tree.root.findAll(node => node.props.testID === testID);

    it('explains the enrollment is no longer active instead of a generic failure', async () => {
      const {tree} = await checkPendingWith(bindingFailed);

      expect(byTestId(tree, 'ezkey.e2e.enrollmentDetail.noLongerActive').length).toBeGreaterThan(0);
      const textContent = tree.root
        .findAllByType(Text)
        .map(node => node.props.children)
        .flat()
        .join(' ');
      expect(textContent).toContain('This enrollment is no longer active');
      expect(textContent).not.toContain('Request failed');
    });

    it('removes the local copy only after the person confirms', async () => {
      const mutateAsync = jest.fn().mockResolvedValue(undefined);
      mockUseDeleteEnrollment.mockReturnValue({isPending: false, mutateAsync} as never);
      const alert = jest.spyOn(Alert, 'alert').mockImplementation(() => undefined);
      const {navigation, tree} = await checkPendingWith(bindingFailed);

      const [remove] = byTestId(tree, 'ezkey.e2e.enrollmentDetail.removeNoLongerActive');
      await renderer.act(async () => {
        remove.props.onPress();
      });
      expect(mutateAsync).not.toHaveBeenCalled();

      const buttons = alert.mock.calls[0][2] as Array<{style?: string; onPress?: () => unknown}>;
      const destructive = buttons.find(button => button.style === 'destructive');
      await renderer.act(async () => {
        await destructive?.onPress?.();
      });

      expect(mutateAsync).toHaveBeenCalledWith(enrollmentId);
      expect(navigation.popToTop).toHaveBeenCalled();
      alert.mockRestore();
    });

    it('keeps the generic retryable message for a transport error', async () => {
      const {tree} = await checkPendingWith(new Error('Network Error'));

      expect(byTestId(tree, 'ezkey.e2e.enrollmentDetail.noLongerActive').length).toBe(0);
      expect(byTestId(tree, 'ezkey.e2e.enrollmentDetail.removeNoLongerActive').length).toBe(0);
    });
  });
});
