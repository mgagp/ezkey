/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: createThemedStyles
 * Description: StyleSheet factory keyed by palette, so styles follow the theme without restart.
 * @since 2026
 */

import {StyleSheet} from 'react-native';
import {ThemeColors} from '../config/theme';
import {useTheme} from './ThemeProvider';

type NamedStyles = Parameters<typeof StyleSheet.create>[0];

/**
 * Builds a hook that returns `StyleSheet.create(factory(colors))` for the active palette. Each
 * palette is created once and cached, so switching themes costs one StyleSheet per screen.
 *
 * @param factory style definitions for a palette
 * @returns hook to call inside components
 * @since 2026
 */
export const createThemedStyles = <S extends NamedStyles>(
  factory: (colors: ThemeColors) => S & NamedStyles,
): (() => Readonly<S>) => {
  const cache = new Map<ThemeColors, Readonly<S>>();
  return () => {
    const {colors} = useTheme();
    const cached = cache.get(colors);
    if (cached != null) {
      return cached;
    }
    const styles = StyleSheet.create<S>(factory(colors));
    cache.set(colors, styles);
    return styles;
  };
};
