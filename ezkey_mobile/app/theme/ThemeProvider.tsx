/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: ThemeProvider
 * Description: Exposes the active color palette, following the color scheme the app resolves from
 *              the Appearance setting (Light / Dark / System default, #1156).
 * @since 2026
 */

import React, {createContext, useContext, useMemo} from 'react';
import {useColorScheme} from 'react-native';
import {ColorSchemeName, palettes, ThemeColors} from '../config/theme';

/**
 * Active theme values.
 *
 * @param scheme resolved color scheme
 * @param colors palette for that scheme
 * @since 2026
 */
export type Theme = {scheme: ColorSchemeName; colors: ThemeColors};

/** Dark until a provider resolves the scheme, which keeps tests and early renders unchanged. */
const ThemeContext = createContext<Theme>({scheme: 'dark', colors: palettes.dark});

/**
 * Maps a React Native color scheme to an Ezkey palette. Anything but an explicit `light` keeps
 * the dark palette, the historical look of the app.
 *
 * @param scheme value from `useColorScheme()` / `Appearance.getColorScheme()`
 * @returns the palette scheme to render
 * @since 2026
 */
export const resolveScheme = (scheme: string | null | undefined): ColorSchemeName =>
  scheme === 'light' ? 'light' : 'dark';

/**
 * Provides the palette for the current color scheme. `useColorScheme()` already reflects the
 * in-app override set through `Appearance.setColorScheme`, and the system scheme otherwise.
 *
 * @param props.children subtree that reads the theme
 * @since 2026
 */
export const ThemeProvider: React.FC<{children: React.ReactNode}> = ({children}) => {
  const scheme = resolveScheme(useColorScheme());
  const value = useMemo(() => ({scheme, colors: palettes[scheme]}), [scheme]);
  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
};

/**
 * Returns the active theme.
 *
 * @returns scheme and palette
 * @since 2026
 */
export const useTheme = (): Theme => useContext(ThemeContext);
