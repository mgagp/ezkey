/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: playUpdate
 * Description: Fail-open Google Play flexible in-app update check. Never blocks MFA flows.
 * @since 2026
 */

import AsyncStorage from '@react-native-async-storage/async-storage';
import {NativeModules, Platform} from 'react-native';

const DISMISS_KEY = 'ezkey.playUpdate.dismissedVersionCode';

export type PlayFlexibleUpdateCheck = {
  available: boolean;
  availableVersionCode?: number;
};

type NativeShape = {
  checkFlexibleUpdate(): Promise<PlayFlexibleUpdateCheck>;
  startFlexibleUpdate(): Promise<boolean>;
};

const native = NativeModules.EzkeyPlayUpdateModule as NativeShape | undefined;

/**
 * Asks Play whether a flexible update is available. Returns `{available: false}` on iOS,
 * sideload, debug, missing module, or any thrown error (fail-open).
 *
 * @return Availability plus the Play `versionCode` when present.
 * @since 2026
 */
export const checkFlexiblePlayUpdate = async (): Promise<PlayFlexibleUpdateCheck> => {
  if (Platform.OS !== 'android' || native?.checkFlexibleUpdate == null) {
    return {available: false};
  }
  try {
    const result = await native.checkFlexibleUpdate();
    return {
      available: result?.available === true,
      availableVersionCode:
        typeof result?.availableVersionCode === 'number'
          ? result.availableVersionCode
          : undefined,
    };
  } catch {
    return {available: false};
  }
};

/**
 * Starts the Play flexible-update UI. Fail-open: returns false when Play cannot start the flow.
 *
 * @return Whether Play accepted the start request.
 * @since 2026
 */
export const startFlexiblePlayUpdate = async (): Promise<boolean> => {
  if (Platform.OS !== 'android' || native?.startFlexibleUpdate == null) {
    return false;
  }
  try {
    return (await native.startFlexibleUpdate()) === true;
  } catch {
    return false;
  }
};

/**
 * @return Previously dismissed Play `versionCode`, or null.
 * @since 2026
 */
export const getDismissedPlayUpdateVersionCode = async (): Promise<number | null> => {
  try {
    const raw = await AsyncStorage.getItem(DISMISS_KEY);
    if (raw == null || raw === '') {
      return null;
    }
    const parsed = Number.parseInt(raw, 10);
    return Number.isFinite(parsed) ? parsed : null;
  } catch {
    return null;
  }
};

/**
 * Remembers that the user postponed this Play `versionCode` so Home does not re-prompt until
 * Play offers a newer code.
 *
 * @param versionCode Play `availableVersionCode` that was dismissed.
 * @since 2026
 */
export const setDismissedPlayUpdateVersionCode = async (versionCode: number): Promise<void> => {
  try {
    await AsyncStorage.setItem(DISMISS_KEY, String(versionCode));
  } catch {
    // Fail-open: a persistence miss only means the prompt may appear again later.
  }
};
