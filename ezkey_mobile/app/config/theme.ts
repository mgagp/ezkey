/**
 * Centralized theme for Ezkey Mobile — palettes, typography, spacing.
 * Aligns with publication-style Android patterns (consistent tokens, readable hierarchy).
 *
 * Components read the active palette through `useTheme()` / `createThemedStyles()` in
 * `app/theme`; both palettes share the same keys. Text tokens meet WCAG 2.2 AA (≥ 4.5:1) on
 * `background`, `surface` and `surfaceElevated` in both palettes (`__tests__/themeContrast.test.ts`).
 */

export const darkColors = {
  background: '#0b0d11',
  surface: '#151923',
  surfaceElevated: '#1c2230',
  surfaceMuted: '#0f1628',
  /** Wizard information card. */
  surfaceInfo: '#111620',
  /** Behind the camera preview, which is black in every theme. */
  cameraBackground: '#000000',

  /** White text on primary: 4.82:1 (WCAG AA, #1155). */
  primary: '#2E6FD6',
  /** Accent text, links, radio buttons and spinners. */
  primaryLight: '#5a9cf7',
  /** Small text on background / surface / surfaceElevated: ≥ 4.88:1 (#1155). */
  primaryMuted: '#7090C0',
  /** Faint primary fill (feedback banner, info card header). */
  primaryTint: 'rgba(54, 115, 223, 0.12)',

  textPrimary: '#f4f7ff',
  textSecondary: '#c2c8d5',
  textMuted: '#9aa3b6',
  /** Tertiary text and input placeholders. */
  textSubtle: '#7f8da6',
  /** Body copy inside cards and banners. */
  textBody: '#dfe6f7',
  textAccent: '#d6e6ff',
  textInfoTitle: '#cdd9f7',
  textOnPrimary: '#ffffff',
  textOnSuccess: '#0b0d11',
  textOnDanger: '#ffffff',
  /** Content drawn over the camera preview. */
  textOnCamera: '#ffffff',

  success: '#61d095',
  successBg: 'rgba(97, 208, 149, 0.14)',
  successBorderMuted: 'rgba(97, 208, 149, 0.35)',
  /** Disabled Approve label (exempt from contrast minimums). */
  successTextMuted: 'rgba(97, 208, 149, 0.45)',
  error: '#ff6666',
  errorLight: '#ff7878',
  /** "Rejected" result label. */
  errorSoft: '#ff8d8d',
  errorBg: 'rgba(255, 120, 120, 0.15)',
  warning: '#f5a623',
  /** Unusable / failed states that need attention but are not errors. */
  caution: '#f5c26b',
  cautionBg: 'rgba(245, 194, 107, 0.08)',
  cautionBgStrong: 'rgba(245, 194, 107, 0.12)',
  cautionBorder: 'rgba(245, 194, 107, 0.35)',
  cautionBorderStrong: 'rgba(245, 194, 107, 0.45)',
  /** Destructive buttons (remove, delete, Danger Zone); white text ≥ 4.5:1. */
  danger: '#c24b4b',
  dangerBg: 'rgba(255, 102, 102, 0.1)',
  dangerBgStrong: 'rgba(255, 102, 102, 0.12)',
  dangerBorder: 'rgba(255, 102, 102, 0.2)',
  dangerBorderStrong: 'rgba(255, 102, 102, 0.25)',

  border: 'rgba(54, 115, 223, 0.15)',
  borderCard: 'rgba(54, 115, 223, 0.18)',
  borderStrong: 'rgba(54, 115, 223, 0.2)',
  borderControl: 'rgba(54, 115, 223, 0.3)',
  borderFocus: 'rgba(54, 115, 223, 0.5)',
  borderFocusStrong: 'rgba(54, 115, 223, 0.85)',
  /** High-contrast outline (scanner Cancel). */
  borderContrast: '#f4f7ff',
  borderMuted: 'rgba(154, 163, 182, 0.3)',
  divider: 'rgba(255, 255, 255, 0.08)',
  dividerAccent: 'rgba(54, 115, 223, 0.1)',

  overlay: 'rgba(0, 0, 0, 0.6)',
  shadow: '#000000',

  /** Faint tint inside the home-screen installation frame (behind enrollment cards). */
  installationShellBg: 'rgba(48, 118, 223, 0.06)',

  /** F2a test-harness controls (debug builds only). */
  harnessAccent: '#4f8ff8',
  harnessAccentBg: 'rgba(79, 143, 248, 0.08)',
  harnessAccentBorder: 'rgba(79, 143, 248, 0.32)',
  harnessAccentText: '#9ec2ff',
  harnessWarningBg: 'rgba(242, 168, 67, 0.18)',
  harnessWarningBorder: 'rgba(242, 168, 67, 0.5)',
  harnessWarningText: '#ffd58f',
};

/** Semantic color tokens shared by both palettes. */
export type ThemeColors = {readonly [K in keyof typeof darkColors]: string};

export const lightColors: ThemeColors = {
  background: '#f3f5f9',
  surface: '#ffffff',
  surfaceElevated: '#e8edf5',
  surfaceMuted: '#edf1f7',
  surfaceInfo: '#ffffff',
  cameraBackground: '#000000',

  primary: '#2E6FD6',
  primaryLight: '#1f5bbd',
  primaryMuted: '#46669a',
  primaryTint: 'rgba(46, 111, 214, 0.1)',

  textPrimary: '#111827',
  textSecondary: '#374151',
  textMuted: '#4b5563',
  textSubtle: '#596273',
  textBody: '#1f2937',
  textAccent: '#1d4b94',
  textInfoTitle: '#1e3a6e',
  textOnPrimary: '#ffffff',
  textOnSuccess: '#ffffff',
  textOnDanger: '#ffffff',
  textOnCamera: '#ffffff',

  success: '#137a45',
  successBg: 'rgba(19, 122, 69, 0.1)',
  successBorderMuted: 'rgba(19, 122, 69, 0.35)',
  successTextMuted: 'rgba(19, 122, 69, 0.55)',
  error: '#b3261e',
  errorLight: '#b3261e',
  errorSoft: '#b3261e',
  errorBg: 'rgba(179, 38, 30, 0.08)',
  warning: '#8f5300',
  caution: '#855000',
  cautionBg: 'rgba(176, 112, 0, 0.08)',
  cautionBgStrong: 'rgba(176, 112, 0, 0.12)',
  cautionBorder: 'rgba(176, 112, 0, 0.35)',
  cautionBorderStrong: 'rgba(176, 112, 0, 0.45)',
  danger: '#b3261e',
  dangerBg: 'rgba(179, 38, 30, 0.06)',
  dangerBgStrong: 'rgba(179, 38, 30, 0.08)',
  dangerBorder: 'rgba(179, 38, 30, 0.2)',
  dangerBorderStrong: 'rgba(179, 38, 30, 0.3)',

  border: 'rgba(46, 111, 214, 0.18)',
  borderCard: 'rgba(46, 111, 214, 0.22)',
  borderStrong: 'rgba(46, 111, 214, 0.26)',
  borderControl: 'rgba(46, 111, 214, 0.4)',
  borderFocus: 'rgba(46, 111, 214, 0.6)',
  borderFocusStrong: 'rgba(46, 111, 214, 0.9)',
  borderContrast: '#111827',
  borderMuted: 'rgba(75, 85, 99, 0.3)',
  divider: 'rgba(17, 24, 39, 0.08)',
  dividerAccent: 'rgba(46, 111, 214, 0.12)',

  overlay: 'rgba(17, 24, 39, 0.5)',
  shadow: '#000000',

  installationShellBg: 'rgba(46, 111, 214, 0.05)',

  harnessAccent: '#1f5bbd',
  harnessAccentBg: 'rgba(31, 91, 189, 0.08)',
  harnessAccentBorder: 'rgba(31, 91, 189, 0.32)',
  harnessAccentText: '#1d4b94',
  harnessWarningBg: 'rgba(176, 112, 0, 0.12)',
  harnessWarningBorder: 'rgba(176, 112, 0, 0.45)',
  harnessWarningText: '#6b3f00',
};

export type ColorSchemeName = 'light' | 'dark';

export const palettes: Record<ColorSchemeName, ThemeColors> = {
  dark: darkColors,
  light: lightColors,
};

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
