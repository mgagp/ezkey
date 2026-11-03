/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: useAccessibilityAnnouncement
 * Description: Speaks status messages (results, errors) through TalkBack / VoiceOver when they
 *              appear or change, per WCAG 4.1.3 Status Messages (#1155).
 * @since 2026
 */

import {useEffect, useRef} from 'react';
import {AccessibilityInfo} from 'react-native';

/**
 * Announces `message` whenever it changes to a non-empty value after the first render. The value
 * present at mount is not announced, so reopening a screen does not repeat an old result.
 *
 * @param message Status text to announce; `undefined` or empty means nothing to say.
 * @param changeKey Optional discriminator so an identical message (e.g. a second "Rejected")
 *                  is announced again when the underlying event changes.
 * @since 2026
 */
export const useAccessibilityAnnouncement = (
  message: string | undefined,
  changeKey?: string | number,
): void => {
  const previous = useRef<string | undefined>(undefined);
  const mounted = useRef(false);
  const current = message ? `${changeKey ?? ''}|${message}` : undefined;

  useEffect(() => {
    if (!mounted.current) {
      mounted.current = true;
      previous.current = current;
      return;
    }
    if (current !== previous.current) {
      previous.current = current;
      if (message) {
        AccessibilityInfo.announceForAccessibility(message);
      }
    }
  }, [current, message]);
};
