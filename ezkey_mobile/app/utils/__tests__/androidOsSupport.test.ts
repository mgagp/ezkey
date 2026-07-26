import {
  ANDROID_PRODUCT_MIN_SDK,
  isAndroidOsSupported,
  resolveAndroidApiLevel,
} from '../androidOsSupport';

describe('androidOsSupport', () => {
  it('exports the product floor aligned with minSdk 31', () => {
    expect(ANDROID_PRODUCT_MIN_SDK).toBe(31);
  });

  describe('resolveAndroidApiLevel', () => {
    it('returns null on non-Android platforms', () => {
      expect(resolveAndroidApiLevel('ios', '17.0')).toBeNull();
      expect(resolveAndroidApiLevel('web', 31)).toBeNull();
    });

    it('returns numeric Android API levels', () => {
      expect(resolveAndroidApiLevel('android', 30)).toBe(30);
      expect(resolveAndroidApiLevel('android', 31)).toBe(31);
    });

    it('parses string Android API levels when present', () => {
      expect(resolveAndroidApiLevel('android', '30')).toBe(30);
    });

    it('returns null for unparsable Android versions', () => {
      expect(resolveAndroidApiLevel('android', 'api-level')).toBeNull();
    });
  });

  describe('isAndroidOsSupported', () => {
    it('allows non-Android platforms (iOS out of scope)', () => {
      expect(isAndroidOsSupported('ios', '15.0')).toBe(true);
    });

    it('blocks Android below the product floor', () => {
      expect(isAndroidOsSupported('android', 30)).toBe(false);
      expect(isAndroidOsSupported('android', 24)).toBe(false);
    });

    it('allows Android at or above the product floor', () => {
      expect(isAndroidOsSupported('android', 31)).toBe(true);
      expect(isAndroidOsSupported('android', 35)).toBe(true);
    });

    it('fails open when Android API level cannot be parsed', () => {
      expect(isAndroidOsSupported('android', 'api-level')).toBe(true);
    });
  });
});
