import React from 'react';
import {Text} from 'react-native';
import renderer from 'react-test-renderer';

jest.mock('../app/services/crypto', () => ({
  cryptoService: {
    canUseProtectedSigning: jest.fn().mockResolvedValue(true),
    authenticateSecurityPreferenceDowngrade: jest.fn().mockResolvedValue(true),
  },
}));

jest.mock('../app/services/storage/securityPreferenceStorage', () => ({
  DEFAULT_SECURITY_LEVEL: 'standard',
  SECURITY_LEVELS: ['standard', 'confirm-before-approvals'],
  normalizeSecurityLevel: (value?: string) =>
    value === 'confirm-before-approvals' ? 'confirm-before-approvals' : 'standard',
  securityPreferenceStorage: {
    getSecurityLevel: jest.fn(),
    setSecurityLevel: jest.fn().mockResolvedValue(undefined),
  },
}));

import {SecurityScreen} from '../app/screens/Security';
import {cryptoService} from '../app/services/crypto';
import {securityPreferenceStorage} from '../app/services/storage/securityPreferenceStorage';

const mockedCryptoService = jest.mocked(cryptoService);
const mockedSecurityPreferenceStorage = jest.mocked(securityPreferenceStorage);

describe('SecurityScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedCryptoService.canUseProtectedSigning.mockResolvedValue(true);
    mockedCryptoService.authenticateSecurityPreferenceDowngrade.mockResolvedValue(true);
    mockedSecurityPreferenceStorage.setSecurityLevel.mockResolvedValue(undefined);
  });

  it('requires confirmation before downgrading from protected to standard', async () => {
    mockedSecurityPreferenceStorage.getSecurityLevel.mockResolvedValue(
      'confirm-before-approvals' as never,
    );

    let tree: renderer.ReactTestRenderer;
    await renderer.act(async () => {
      tree = renderer.create(<SecurityScreen />);
    });

    const standardItem = tree!.root.findAll(
      node =>
        typeof node.props.onPress === 'function' &&
        node.findAllByType(Text).some(textNode => textNode.props.children === 'Standard'),
    )[0];

    await renderer.act(async () => {
      standardItem.props.onPress();
    });

    expect(mockedCryptoService.authenticateSecurityPreferenceDowngrade).toHaveBeenCalledTimes(1);
    expect(mockedSecurityPreferenceStorage.setSecurityLevel).toHaveBeenCalledWith('standard');
  });

  it('does not require extra confirmation when upgrading to protected mode', async () => {
    mockedSecurityPreferenceStorage.getSecurityLevel.mockResolvedValue('standard' as never);

    let tree: renderer.ReactTestRenderer;
    await renderer.act(async () => {
      tree = renderer.create(<SecurityScreen />);
    });

    const protectedItem = tree!.root.findAll(
      node =>
        typeof node.props.onPress === 'function' &&
        node.findAllByType(Text).some(
          textNode => textNode.props.children === 'Confirm before approvals',
        ),
    )[0];

    await renderer.act(async () => {
      protectedItem.props.onPress();
    });

    expect(mockedCryptoService.authenticateSecurityPreferenceDowngrade).not.toHaveBeenCalled();
    expect(mockedSecurityPreferenceStorage.setSecurityLevel).toHaveBeenCalledWith(
      'confirm-before-approvals',
    );
  });

  it('renders the declarative security notice', async () => {
    mockedSecurityPreferenceStorage.getSecurityLevel.mockResolvedValue('standard' as never);

    let tree: renderer.ReactTestRenderer;
    await renderer.act(async () => {
      tree = renderer.create(<SecurityScreen />);
    });

    expect(() =>
      tree!.root.findByProps({children: 'This protection is currently declarative'}),
    ).not.toThrow();
    expect(() =>
      tree!.root.findByProps({
        children:
          'In the current version, the app requests local device confirmation before responding, but the backend does not yet receive a cryptographic proof that this local authentication was inseparably bound to the signature itself.',
      }),
    ).not.toThrow();
  });
});