/**
 * Centralized theme for ezkey_mobile_app.
 * Ezkey blue palette, typography, and spacing.
 */

export const colors = {
  background: '#0b0d11',
  surface: '#151923',
  surfaceElevated: '#1c2230',
  surfaceMuted: '#0f1628',

  primary: '#3076DF',
  primaryLight: '#5a9cf7',
  primaryMuted: '#5a7aa8',

  textPrimary: '#f4f7ff',
  textSecondary: '#c2c8d5',
  textMuted: '#9aa3b6',
  textOnPrimary: '#ffffff',
  textAccent: '#d6e6ff',

  success: '#61d095',
  error: '#ff6666',
  errorLight: '#ff7878',
  warning: '#f5a623',

  border: 'rgba(54, 115, 223, 0.15)',
  borderStrong: 'rgba(54, 115, 223, 0.2)',
  borderFocus: 'rgba(54, 115, 223, 0.5)',
  borderFocusStrong: 'rgba(54, 115, 223, 0.85)',

  overlay: 'rgba(0, 0, 0, 0.6)',
  errorBg: 'rgba(255, 120, 120, 0.15)',
  cardHeaderBg: 'rgba(18, 39, 92, 0.6)',
} as const;

export const spacing = {
  xs: 4,
  sm: 8,
  md: 12,
  lg: 16,
  xl: 20,
  xxl: 24,
} as const;

export const typography = {
  fontSize: {
    xs: 11,
    sm: 12,
    base: 14,
    md: 15,
    lg: 16,
    xl: 18,
    xxl: 20,
    title: 24,
  },
  fontWeight: {
    normal: '400' as const,
    medium: '500' as const,
    semibold: '600' as const,
    bold: '700' as const,
  },
} as const;

export const borderRadius = {
  sm: 8,
  md: 10,
  lg: 12,
  xl: 16,
} as const;
