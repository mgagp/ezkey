/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: appLanguage
 * Description: Bridge to the system per-app language (Android 13+ Settings → App languages, iOS
 *              Settings → Ezkey → Language) and to the phone's own language list (#1157).
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
 * Whether the system exposes a per-app language for Ezkey (iOS, Android 13 / API 33 and later).
 *
 * @returns true when the system setting can be read and written
 * @since 2026
 */
export const supportsSystemAppLanguage = (): boolean =>
  native != null && (Platform.OS === 'ios' || Number(Platform.Version) >= 33);

/**
 * Reads the per-app language chosen in system settings.
 *
 * @returns a language tag, or null when the app follows the phone
 * @since 2026
 */
export const readSystemAppLanguage = async (): Promise<string | null> => {
  const tag = await native?.getApplicationLanguage();
  return tag ? tag : null;
};

/**
 * Writes the per-app language so system settings show the same choice as the app.
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
  if (native?.getSystemLanguages != null) {
    try {
      return await native.getSystemLanguages();
    } catch (error) {
      console.warn('[appLanguage] Failed to read the device languages:', error);
    }
  }
  return [Intl.DateTimeFormat().resolvedOptions().locale];
};
