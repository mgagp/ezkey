/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

import React from 'react';
import {View, StyleSheet} from 'react-native';
import LogoSvg from '../../assets/images/logo.svg';

type EzkeyLogoProps = {
  /** Square logo size in density-independent pixels. */
  size?: number;
};

/**
 * Project logo from `assets/images/logo.svg` (kept in sync with repo root `logo.svg`).
 * Use on About and other branding surfaces.
 *
 * @since 2025
 */
export const EzkeyLogo: React.FC<EzkeyLogoProps> = ({size = 112}) => (
  <View
    style={[styles.wrap, {width: size, height: size}]}
    accessibilityRole="image"
    accessibilityLabel="Ezkey logo">
    <LogoSvg
      width={size}
      height={size}
      viewBox="0 0 2022 2022"
      preserveAspectRatio="xMidYMid meet"
      accessible={false}
    />
  </View>
);

const styles = StyleSheet.create({
  wrap: {
    alignSelf: 'center',
    alignItems: 'center',
    justifyContent: 'center',
  },
});
