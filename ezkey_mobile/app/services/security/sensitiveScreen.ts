/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: sensitiveScreen
 * Description: Bridge to the Android window protections used on the Approve / Deny screen
 *              (FLAG_SECURE, hidden overlays, touches filtered when obscured). Fail-open (#1154).
 * @since 2026
 */

import {NativeModules, Platform} from 'react-native';

type NativeShape = {
  setSensitiveScreen(enabled: boolean): void;
};

const native = NativeModules.EzkeyWindowSecurityModule as NativeShape | undefined;

/**
 * Turns the sensitive-screen window protections on or off. No-op on iOS or when the native
 * module is missing; a native failure never blocks the approval flow.
 *
 * @param enabled true while the pending-auth screen is focused.
 * @since 2026
 */
export const setSensitiveScreen = (enabled: boolean): void => {
  if (Platform.OS !== 'android' || native?.setSensitiveScreen == null) {
    return;
  }
  try {
    native.setSensitiveScreen(enabled);
  } catch {
    // Fail-open: defense in depth only; the Keystore signature carries the guarantee.
  }
};
