/**
 * Ezkey logo component using react-native-svg.
 * Renders a key-style mark in Ezkey primary blue (#3076DF).
 */

import React from 'react';
import Svg, {Path} from 'react-native-svg';
import {colors} from '../config/theme';

type EzkeyLogoProps = {
  width?: number;
  height?: number;
  color?: string;
};

/**
 * Stylized "key" shape in Ezkey blue for app branding.
 */
export const EzkeyLogo: React.FC<EzkeyLogoProps> = ({
  width = 120,
  height = 120,
  color = colors.primary,
}) => (
  <Svg width={width} height={height} viewBox="0 0 100 100" fill="none">
    <Path
      d="M50 10 L50 50 L20 50 L20 60 L50 60 L50 90 L60 90 L60 60 L90 60 L90 50 L60 50 L60 10 Z"
      fill={color}
    />
    <Path
      d="M30 25 A10 10 0 1 1 30 45 A10 10 0 1 1 30 25 Z"
      fill={color}
    />
  </Svg>
);
