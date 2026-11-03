/**
 * WCAG 2.2 AA text contrast (≥ 4.5:1) for both palettes (#1156).
 *
 * Translucent fills are composited over the surface they sit on, the way they render.
 */
import {palettes, ThemeColors} from '../app/config/theme';

type Rgb = [number, number, number];

const parse = (value: string): {rgb: Rgb; alpha: number} => {
  const hex = /^#([0-9a-f]{6})$/i.exec(value);
  if (hex) {
    const h = hex[1];
    return {rgb: [0, 2, 4].map(i => parseInt(h.slice(i, i + 2), 16)) as Rgb, alpha: 1};
  }
  const rgba = /^rgba\((\d+),\s*(\d+),\s*(\d+),\s*([\d.]+)\)$/.exec(value);
  if (rgba) {
    return {rgb: [+rgba[1], +rgba[2], +rgba[3]], alpha: +rgba[4]};
  }
  throw new Error(`Unsupported color ${value}`);
};

const over = (top: string, bottom: Rgb): Rgb => {
  const {rgb, alpha} = parse(top);
  return rgb.map((c, i) => c * alpha + bottom[i] * (1 - alpha)) as Rgb;
};

const luminance = (rgb: Rgb): number => {
  const [r, g, b] = rgb.map(v => {
    const c = v / 255;
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
};

const ratio = (a: Rgb, b: Rgb): number => {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
};

type Key = keyof ThemeColors;

const TEXT_ON_SURFACES: Key[] = [
  'textPrimary',
  'textSecondary',
  'textMuted',
  'textSubtle',
  'textBody',
  'textAccent',
  'textInfoTitle',
  'primaryLight',
  'primaryMuted',
  'success',
  'error',
  'errorLight',
  'errorSoft',
  'warning',
  'caution',
  'harnessAccentText',
];
const SURFACES: Key[] = ['background', 'surface', 'surfaceElevated', 'surfaceInfo'];

/** [text, fill, fill underlay] */
const TEXT_ON_FILLS: [Key, Key, Key][] = [
  ['textOnPrimary', 'primary', 'surface'],
  ['textOnSuccess', 'success', 'surface'],
  ['textOnDanger', 'danger', 'surface'],
  ['error', 'errorBg', 'background'],
  ['errorLight', 'errorBg', 'background'],
  ['caution', 'cautionBg', 'surface'],
  ['caution', 'cautionBgStrong', 'surface'],
  ['success', 'successBg', 'surface'],
  ['textBody', 'primaryTint', 'surface'],
  ['harnessWarningText', 'harnessWarningBg', 'surface'],
  ['harnessAccentText', 'harnessAccentBg', 'surface'],
];

describe.each(Object.entries(palettes))('%s palette', (_name, colors) => {
  const solid = (key: Key): Rgb => over(colors[key], [255, 255, 255]);

  it.each(TEXT_ON_SURFACES.flatMap(text => SURFACES.map(surface => [text, surface])))(
    '%s on %s is at least 4.5:1',
    (text, surface) => {
      const bg = solid(surface as Key);
      expect(ratio(over(colors[text as Key], bg), bg)).toBeGreaterThanOrEqual(4.5);
    },
  );

  it.each(TEXT_ON_FILLS)('%s on %s (over %s) is at least 4.5:1', (text, fill, underlay) => {
    const bg = over(colors[fill], solid(underlay));
    expect(ratio(over(colors[text], bg), bg)).toBeGreaterThanOrEqual(4.5);
  });
});
