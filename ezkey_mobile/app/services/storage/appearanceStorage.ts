/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: appearanceStorage
 * Description: Persists the Appearance setting (Light / Dark / System default, #1156).
 * @since 2026
 */

import AsyncStorage from '@react-native-async-storage/async-storage';

export const APPEARANCE_PREFERENCES = ['system', 'light', 'dark'] as const;

export type AppearancePreference = (typeof APPEARANCE_PREFERENCES)[number];

/** Android guidance: follow the system unless the user picks a theme. */
export const DEFAULT_APPEARANCE: AppearancePreference = 'system';

const APPEARANCE_PREFERENCE_KEY = 'ezkey-mobile/preferences/appearance';

const isAppearancePreference = (value: string): value is AppearancePreference =>
  APPEARANCE_PREFERENCES.includes(value as AppearancePreference);

class AppearanceStorage {
  /**
   * Reads the saved preference.
   *
   * @returns the saved preference, or {@link DEFAULT_APPEARANCE} when unset or unreadable
   * @since 2026
   */
  async getAppearance(): Promise<AppearancePreference> {
    try {
      const value = await AsyncStorage.getItem(APPEARANCE_PREFERENCE_KEY);
      if (value && isAppearancePreference(value)) {
        return value;
      }
    } catch (error) {
      console.warn('[appearanceStorage] Failed to read appearance preference:', error);
    }

    return DEFAULT_APPEARANCE;
  }

  /**
   * Saves the preference. A write failure is logged; the choice still applies for this session.
   *
   * @param appearance preference to save
   * @since 2026
   */
  async setAppearance(appearance: AppearancePreference): Promise<void> {
    try {
      await AsyncStorage.setItem(APPEARANCE_PREFERENCE_KEY, appearance);
    } catch (error) {
      console.warn('[appearanceStorage] Failed to persist appearance preference:', error);
    }
  }
}

export const appearanceStorage = new AppearanceStorage();
