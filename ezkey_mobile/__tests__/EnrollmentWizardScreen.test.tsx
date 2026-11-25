/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Enrollment wizard controls are reachable by role for TalkBack / VoiceOver (#1155).
 */

import React from 'react';
import renderer from 'react-test-renderer';
import {AccessibilityInfo} from 'react-native';
import {EnrollmentWizardScreen} from '../app/screens/EnrollmentWizard';
import {useEnrollmentWizard} from '../app/hooks/useEnrollmentWizard';
import {getByRole} from '../test-utils/a11yQueries';

jest.mock('../app/hooks/useEnrollmentWizard', () => ({
  useEnrollmentWizard: jest.fn(),
}));

const mockUseEnrollmentWizard = jest.mocked(useEnrollmentWizard);

const wizardState = (overrides: Partial<ReturnType<typeof useEnrollmentWizard>> = {}) =>
  ({
    draft: undefined,
    isSubmitting: false,
    bindError: undefined,
    cameraError: undefined,
    enrollmentChallenge: '',
    challengeError: undefined,
    scannerVisible: false,
    controlledBypassAvailable: false,
    controlledBypassUsed: false,
    controlledBypassSeedInput: '',
    serverHost: undefined,
    primaryLabel: 'Open scanner',
    secondaryLabel: 'Cancel',
    primaryDisabled: false,
    secondaryDisabled: false,
    hasDraft: false,
    setEnrollmentChallenge: jest.fn(),
    setChallengeError: jest.fn(),
    handlePrimary: jest.fn(),
    handleSecondary: jest.fn(),
    handleBack: jest.fn(),
    handleQrScanned: jest.fn(),
    setControlledBypassSeedInput: jest.fn(),
    handleControlledBypass: jest.fn(),
    handleScannerDismiss: jest.fn(),
    ...overrides,
  }) as ReturnType<typeof useEnrollmentWizard>;

const render = async () => {
  let tree!: renderer.ReactTestRenderer;
  await renderer.act(async () => {
    tree = renderer.create(
      <EnrollmentWizardScreen
        navigation={{goBack: jest.fn(), popToTop: jest.fn()} as never}
        route={{key: 'EnrollmentWizard-key', name: 'EnrollmentWizard'} as never}
      />,
    );
  });
  return tree;
};

describe('EnrollmentWizardScreen accessibility', () => {
  afterEach(() => jest.restoreAllMocks());

  it('exposes the scan step actions as buttons', async () => {
    mockUseEnrollmentWizard.mockReturnValue(wizardState());
    const tree = await render();

    expect(getByRole(tree.root, 'button', 'Open scanner')).toBeDefined();
    expect(getByRole(tree.root, 'button', 'Cancel')).toBeDefined();
    expect(getByRole(tree.root, 'button', 'Back')).toBeDefined();
  });

  it('announces a bind error when it appears', async () => {
    const announce = jest.spyOn(AccessibilityInfo, 'announceForAccessibility');
    mockUseEnrollmentWizard.mockReturnValue(wizardState());
    const tree = await render();
    expect(announce).not.toHaveBeenCalled();

    mockUseEnrollmentWizard.mockReturnValue(wizardState({bindError: 'QR code expired'}));
    await renderer.act(async () => {
      tree.update(
        <EnrollmentWizardScreen
          navigation={{goBack: jest.fn(), popToTop: jest.fn()} as never}
          route={{key: 'EnrollmentWizard-key', name: 'EnrollmentWizard'} as never}
        />,
      );
    });

    expect(announce).toHaveBeenCalledWith('QR code expired');
  });
});

describe('EnrollmentWizardScreen challenge stage server (#1185)', () => {
  const draft = {
    id: 'local-1',
    integrationId: '7',
    integrationName: 'Acme portal',
    enrollmentProofToken: 'token-abc',
    integrationPublicKey: 'pubkey',
  } as ReturnType<typeof useEnrollmentWizard>['draft'];

  const serverHostNodes = (tree: renderer.ReactTestRenderer) =>
    tree.root.findAll(
      node =>
        node.props.testID === 'ezkey.e2e.enrollmentWizard.serverHost' &&
        typeof node.type === 'string',
    );

  it('shows the server host next to the challenge input', async () => {
    mockUseEnrollmentWizard.mockReturnValue(
      wizardState({draft, hasDraft: true, serverHost: 'auth.example.com:8443'}),
    );
    const tree = await render();

    const nodes = serverHostNodes(tree);
    expect(nodes).toHaveLength(1);
    expect(nodes[0].props.children).toBe('auth.example.com:8443');
  });

  it('omits the server row when no host is resolved', async () => {
    mockUseEnrollmentWizard.mockReturnValue(wizardState({draft, hasDraft: true}));
    const tree = await render();

    expect(serverHostNodes(tree)).toHaveLength(0);
  });
});
