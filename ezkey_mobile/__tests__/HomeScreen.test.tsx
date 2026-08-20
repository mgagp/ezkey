/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Home screen smoke: list Maestro ids, wizard/detail navigation, MOB-015 unusable banner.
 * Pull-model: this screen must not call claimPendingAttempt (asserted via absent mock).
 */

import React from 'react';
import {Text} from 'react-native';
import renderer from 'react-test-renderer';
import {useNavigation} from '@react-navigation/native';
import {HomeScreen} from '../app/screens/Home';
import {
  useDeleteEnrollment,
  useEnrollments,
  useRefreshInstallationMetadata,
} from '../app/hooks/useEnrollments';
import {useEnrollmentStore} from '../app/state/enrollmentStore';

jest.mock('../app/hooks/useEnrollments', () => ({
  useEnrollments: jest.fn(),
  useDeleteEnrollment: jest.fn(),
  useRefreshInstallationMetadata: jest.fn(),
}));

jest.mock('@react-navigation/native', () => ({
  useNavigation: jest.fn(),
}));

jest.mock('react-native-safe-area-context', () => ({
  useSafeAreaInsets: () => ({top: 0, bottom: 0, left: 0, right: 0}),
}));

jest.mock('../app/services/play/playUpdate', () => ({
  checkFlexiblePlayUpdate: jest.fn().mockResolvedValue({available: false}),
  getDismissedPlayUpdateVersionCode: jest.fn(),
  setDismissedPlayUpdateVersionCode: jest.fn(),
  startFlexiblePlayUpdate: jest.fn(),
}));

const mockUseEnrollments = jest.mocked(useEnrollments);
const mockUseDeleteEnrollment = jest.mocked(useDeleteEnrollment);
const mockUseRefreshInstallationMetadata = jest.mocked(useRefreshInstallationMetadata);
const mockUseNavigation = jest.mocked(useNavigation);
const mockNavigate = jest.fn();

const healthyEnrollment = {
  id: 'iaaaaaaaaaaaaaaaa_e1',
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
  createdAt: '2026-04-20T10:00:00.000Z',
  lastActivityAt: '2026-04-21T10:00:00.000Z',
};

describe('HomeScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    useEnrollmentStore.getState().clear();
    mockUseNavigation.mockReturnValue({navigate: mockNavigate} as never);
    mockUseDeleteEnrollment.mockReturnValue({isPending: false, mutate: jest.fn()} as never);
    mockUseRefreshInstallationMetadata.mockReturnValue({
      isPending: false,
      mutate: jest.fn(),
    } as never);
    mockUseEnrollments.mockReturnValue({
      data: {enrollments: [], broken: [], collectionError: false},
      isLoading: false,
      refetch: jest.fn(),
    } as never);
  });

  it('renders grouped list Maestro ids for a healthy enrollment', async () => {
    mockUseEnrollments.mockReturnValue({
      data: {enrollments: [healthyEnrollment], broken: [], collectionError: false},
      isLoading: false,
      refetch: jest.fn(),
    } as never);

    let tree: renderer.ReactTestRenderer;
    await renderer.act(async () => {
      tree = renderer.create(<HomeScreen />);
    });

    expect(
      tree!.root.findAll(node => node.props.testID === 'ezkey.e2e.home.root').length,
    ).toBeGreaterThan(0);
    expect(
      tree!.root.findAll(
        node => node.props.testID === `ezkey.e2e.home.enrollment.${healthyEnrollment.id}`,
      ).length,
    ).toBeGreaterThan(0);
  });

  it('navigates to EnrollmentDetail and records the selected enrollment', async () => {
    mockUseEnrollments.mockReturnValue({
      data: {enrollments: [healthyEnrollment], broken: [], collectionError: false},
      isLoading: false,
      refetch: jest.fn(),
    } as never);

    let tree: renderer.ReactTestRenderer;
    await renderer.act(async () => {
      tree = renderer.create(<HomeScreen />);
    });

    const row = tree!.root.find(
      node => node.props.testID === `ezkey.e2e.home.enrollment.${healthyEnrollment.id}`,
    );

    await renderer.act(async () => {
      row.props.onPress();
    });

    expect(useEnrollmentStore.getState().selectedId).toBe(healthyEnrollment.id);
    expect(mockNavigate).toHaveBeenCalledWith('EnrollmentDetail', {
      enrollmentId: healthyEnrollment.id,
    });
  });

  it('opens the enrollment wizard from the add FAB', async () => {
    let tree: renderer.ReactTestRenderer;
    await renderer.act(async () => {
      tree = renderer.create(<HomeScreen />);
    });

    const fab = tree!.root.find(node => node.props.testID === 'ezkey.e2e.home.fabAddEnrollment');

    await renderer.act(async () => {
      fab.props.onPress();
    });

    expect(mockNavigate).toHaveBeenCalledWith('EnrollmentWizard');
  });

  it('shows the unusable-local banner when the collection is corrupt', async () => {
    mockUseEnrollments.mockReturnValue({
      data: {enrollments: [], broken: [], collectionError: true},
      isLoading: false,
      refetch: jest.fn(),
    } as never);

    let tree: renderer.ReactTestRenderer;
    await renderer.act(async () => {
      tree = renderer.create(<HomeScreen />);
    });

    expect(
      tree!.root.findAll(node => node.props.testID === 'ezkey.e2e.home.unusableLocalData').length,
    ).toBeGreaterThan(0);
    const textContent = tree!.root
      .findAllByType(Text)
      .map(node => node.props.children)
      .flat()
      .join(' ');
    expect(textContent).toContain('Saved enrollment data is unusable');
  });

  it('shows the unusable-local banner when every row is broken', async () => {
    mockUseEnrollments.mockReturnValue({
      data: {
        enrollments: [],
        collectionError: false,
        broken: [
          {
            id: healthyEnrollment.id,
            reason: 'secret_rehydration_failed',
            metadata: healthyEnrollment,
          },
        ],
      },
      isLoading: false,
      refetch: jest.fn(),
    } as never);

    let tree: renderer.ReactTestRenderer;
    await renderer.act(async () => {
      tree = renderer.create(<HomeScreen />);
    });

    expect(
      tree!.root.findAll(node => node.props.testID === 'ezkey.e2e.home.unusableLocalData').length,
    ).toBeGreaterThan(0);
  });
});
