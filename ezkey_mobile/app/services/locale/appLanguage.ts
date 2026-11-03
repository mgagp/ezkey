/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: appLanguage
 * Description: Bridge to the Android 13+ per-app language (system Settings → App languages) and
 *              to the phone's own language list (#1157).
 * @since 2026
 */

import {NativeModules, Platform} from 'react-native';
import {LanguagePreference} from '../storage/localeStorage';

type NativeShape = {
  getApplicationLanguage(): Promise<string | null>;
  setApplicationLanguage(tag: string): Promise<void>;
  getSystemLanguages(): Promise<string[]>;
};

const native = NativeModules.EzkeyLocaleModule as NativeShape | undefined;

/**
 * Whether Android exposes a per-app language for Ezkey (Android 13 / API 33 and later).
 *
 * @returns true when the system setting can be read and written
 * @since 2026
 */
export const supportsSystemAppLanguage = (): boolean =>
  Platform.OS === 'android' && Number(Platform.Version) >= 33 && native != null;

/**
 * Reads the per-app language chosen in Android settings.
 *
 * @returns a language tag, or null when Android is set to the system default
 * @since 2026
 */
export const readSystemAppLanguage = async (): Promise<string | null> => {
  const tag = await native?.getApplicationLanguage();
  return tag ? tag : null;
};

/**
 * Writes the per-app language so Android settings show the same choice as the app.
 *
 * @param preference `system` clears the per-app language
 * @since 2026
 */
export const writeSystemAppLanguage = async (preference: LanguagePreference): Promise<void> => {
  await native?.setApplicationLanguage(preference === 'system' ? '' : preference);
};

/**
 * The phone's language list, unaffected by the per-app override.
 *
 * @returns language tags in preference order
 * @since 2026
 */
export const readDeviceLanguages = async (): Promise<string[]> => {
  if (Platform.OS === 'android' && native?.getSystemLanguages != null) {
    try {
      return await native.getSystemLanguages();
    } catch (error) {
      console.warn('[appLanguage] Failed to read the device languages:', error);
    }
  }
  return [Intl.DateTimeFormat().resolvedOptions().locale];
};
