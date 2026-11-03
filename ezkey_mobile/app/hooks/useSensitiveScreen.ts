/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: useSensitiveScreen
 * Description: Keeps the sensitive-screen window protections on only while the calling screen
 *              is focused, so screenshots elsewhere in the app keep working (#1154).
 * @since 2026
 */

import {useEffect} from 'react';
import {setSensitiveScreen} from '../services/security/sensitiveScreen';

type FocusAwareNavigation = {
  addListener(event: 'focus' | 'blur', callback: () => void): () => void;
  isFocused(): boolean;
};

/**
 * Enables the protections on focus and clears them on blur or unmount.
 *
 * @param navigation React Navigation prop of the calling screen.
 * @since 2026
 */
export const useSensitiveScreen = (navigation: FocusAwareNavigation): void => {
  useEffect(() => {
    if (navigation.isFocused()) {
      setSensitiveScreen(true);
    }
    const unsubscribeFocus = navigation.addListener('focus', () => setSensitiveScreen(true));
    const unsubscribeBlur = navigation.addListener('blur', () => setSensitiveScreen(false));
    return () => {
      unsubscribeFocus();
      unsubscribeBlur();
      setSensitiveScreen(false);
    };
  }, [navigation]);
};
