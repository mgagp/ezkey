/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: sensitiveScreen
 * Description: Bridge to the window protections used on the Approve / Deny screen. Android:
 *              FLAG_SECURE, hidden overlays, touches filtered when obscured. iOS: a cover for the
 *              app switcher and for screen recording or mirroring. Fail-open (#1154).
 * @since 2026
 */

import i18n from 'i18next';
import {NativeModules} from 'react-native';

type NativeShape = {
  setSensitiveScreen(enabled: boolean): void;
  /** iOS only: text of the cover shown during a screen capture. */
  setCaptureMessage?(message: string): void;
};

const native = NativeModules.EzkeyWindowSecurityModule as NativeShape | undefined;

/**
 * Turns the sensitive-screen window protections on or off. No-op when the native module is
 * missing; a native failure never blocks the approval flow.
 *
 * @param enabled true while the pending-auth screen is focused.
 * @since 2026
 */
export const setSensitiveScreen = (enabled: boolean): void => {
  if (native?.setSensitiveScreen == null) {
    return;
  }
  try {
    if (enabled) {
      native.setCaptureMessage?.(i18n.t('pendingAuth.captureBlocked'));
    }
    native.setSensitiveScreen(enabled);
  } catch {
    // Fail-open: defense in depth only; the enrollment key signature carries the guarantee.
  }
};
