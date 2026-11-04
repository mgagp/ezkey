/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: appearance
 * Description: Applies the Appearance setting to React Native and to the native side, so the
 *              next cold start matches before JavaScript runs (#1156, #1188): Android per-app night
 *              mode (splash and system dialogs); iOS UserDefaults read by SceneDelegate (the iOS
 *              launch screen itself always follows the system).
 * @since 2026
 */

import {Appearance, NativeModules} from 'react-native';
import {
  AppearancePreference,
  appearanceStorage,
} from '../storage/appearanceStorage';

type NativeShape = {
  setApplicationNightMode(mode: AppearancePreference): void;
};

const native = NativeModules.EzkeyAppearanceModule as NativeShape | undefined;

/**
 * Applies a preference for the current session. `useColorScheme()` updates immediately; the
 * native module also keeps it for the next cold start (Android per-app night mode, iOS
 * UserDefaults).
 *
 * Fail-open: a native failure is logged and the in-app theme still switches.
 *
 * @param preference Light, Dark, or System default
 * @since 2026
 */
export const applyAppearance = (preference: AppearancePreference): void => {
  Appearance.setColorScheme(preference === 'system' ? 'unspecified' : preference);
  if (native?.setApplicationNightMode == null) {
    return;
  }
  try {
    native.setApplicationNightMode(preference);
  } catch (error) {
    console.warn('[appearance] Failed to save the native appearance:', error);
  }
};

/**
 * Applies the saved preference at startup, before the first themed screen renders.
 *
 * @since 2026
 */
export const initializeAppearance = async (): Promise<void> => {
  applyAppearance(await appearanceStorage.getAppearance());
};

/**
 * Saves and applies a preference chosen in Settings.
 *
 * @param preference Light, Dark, or System default
 * @since 2026
 */
export const changeAppearance = async (preference: AppearancePreference): Promise<void> => {
  applyAppearance(preference);
  await appearanceStorage.setAppearance(preference);
};
