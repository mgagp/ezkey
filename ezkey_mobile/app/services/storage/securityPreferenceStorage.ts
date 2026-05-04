import AsyncStorage from '@react-native-async-storage/async-storage';

export const SECURITY_LEVELS = ['standard', 'confirm-before-approvals'] as const;

export type SecurityLevel = (typeof SECURITY_LEVELS)[number];

export const DEFAULT_SECURITY_LEVEL: SecurityLevel = 'standard';

const SECURITY_PREFERENCE_KEY = 'ezkey-mobile/preferences/security-level';

const isSecurityLevel = (value: string): value is SecurityLevel =>
  SECURITY_LEVELS.includes(value as SecurityLevel);

class SecurityPreferenceStorage {
  async getSecurityLevel(): Promise<SecurityLevel> {
    try {
      const value = await AsyncStorage.getItem(SECURITY_PREFERENCE_KEY);
      if (value && isSecurityLevel(value)) {
        return value;
      }
    } catch (error) {
      console.warn('[securityPreferenceStorage] Failed to read security preference:', error);
    }

    return DEFAULT_SECURITY_LEVEL;
  }

  async setSecurityLevel(securityLevel: SecurityLevel) {
    try {
      await AsyncStorage.setItem(SECURITY_PREFERENCE_KEY, securityLevel);
    } catch (error) {
      console.warn(
        '[securityPreferenceStorage] Failed to persist security preference:',
        error,
      );
    }
  }
}

export const securityPreferenceStorage = new SecurityPreferenceStorage();

export const normalizeSecurityLevel = (value?: string): SecurityLevel =>
  value && isSecurityLevel(value) ? value : DEFAULT_SECURITY_LEVEL;