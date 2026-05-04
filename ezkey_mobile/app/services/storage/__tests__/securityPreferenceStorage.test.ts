jest.mock('@react-native-async-storage/async-storage', () => ({
  __esModule: true,
  default: {
    getItem: jest.fn(),
    setItem: jest.fn(),
    removeItem: jest.fn(),
  },
}));

import AsyncStorage from '@react-native-async-storage/async-storage';
import {
  DEFAULT_SECURITY_LEVEL,
  normalizeSecurityLevel,
  securityPreferenceStorage,
} from '../securityPreferenceStorage';

const mockAsyncStorage = jest.mocked(AsyncStorage);

describe('securityPreferenceStorage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockAsyncStorage.getItem.mockResolvedValue(null);
    mockAsyncStorage.setItem.mockResolvedValue();
    mockAsyncStorage.removeItem.mockResolvedValue();
  });

  it('returns the default security level when no preference exists', async () => {
    await expect(securityPreferenceStorage.getSecurityLevel()).resolves.toBe(
      DEFAULT_SECURITY_LEVEL,
    );
  });

  it('returns the default security level when storage contains an unsupported value', async () => {
    mockAsyncStorage.getItem.mockResolvedValue('biometric-only');

    await expect(securityPreferenceStorage.getSecurityLevel()).resolves.toBe(
      DEFAULT_SECURITY_LEVEL,
    );
  });

  it('persists supported security levels', async () => {
    await securityPreferenceStorage.setSecurityLevel('confirm-before-approvals');

    expect(mockAsyncStorage.setItem).toHaveBeenCalledWith(
      'ezkey-mobile/preferences/security-level',
      'confirm-before-approvals',
    );
  });

  it('normalizes missing or invalid values to the default level', () => {
    expect(normalizeSecurityLevel()).toBe(DEFAULT_SECURITY_LEVEL);
    expect(normalizeSecurityLevel('high-assurance')).toBe(DEFAULT_SECURITY_LEVEL);
    expect(normalizeSecurityLevel('standard')).toBe('standard');
    expect(normalizeSecurityLevel('confirm-before-approvals')).toBe(
      'confirm-before-approvals',
    );
  });
});