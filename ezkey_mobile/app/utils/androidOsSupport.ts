/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

import {Platform} from 'react-native';

/**
 * Product floor for Android (Android 12+). Keep aligned with
 * `android/build.gradle` `minSdkVersion` and
 * `docs/MOBILE_ANDROID_PLATFORM_SUPPORT.md`.
 */
export const ANDROID_PRODUCT_MIN_SDK = 31;

/**
 * Resolves the Android API level from React Native platform values.
 *
 * @param os Platform OS string (defaults to `Platform.OS`)
 * @param version Platform version (defaults to `Platform.Version`)
 * @returns API level on Android, or `null` on non-Android / unparsable values
 */
export function resolveAndroidApiLevel(
  os: typeof Platform.OS = Platform.OS,
  version: typeof Platform.Version = Platform.Version,
): number | null {
  if (os !== 'android') {
    return null;
  }
  if (typeof version === 'number' && Number.isFinite(version)) {
    return version;
  }
  const parsed = Number.parseInt(String(version), 10);
  return Number.isFinite(parsed) ? parsed : null;
}

/**
 * Returns whether the current (or injected) platform meets the Android product floor.
 * Non-Android platforms are treated as supported (iOS is out of scope for this gate).
 *
 * @param os Platform OS string (defaults to `Platform.OS`)
 * @param version Platform version (defaults to `Platform.Version`)
 * @returns `true` when MFA entry may proceed
 */
export function isAndroidOsSupported(
  os: typeof Platform.OS = Platform.OS,
  version: typeof Platform.Version = Platform.Version,
): boolean {
  const apiLevel = resolveAndroidApiLevel(os, version);
  if (apiLevel === null) {
    return true;
  }
  return apiLevel >= ANDROID_PRODUCT_MIN_SDK;
}
