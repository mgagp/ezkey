import {NativeModules, Platform} from 'react-native';
import {
  changeAppLanguage,
  getLanguagePreference,
  i18n,
  resolveLocale,
  syncAppLanguage,
} from '../app/i18n';
import {localeStorage} from '../app/services/storage/localeStorage';

const nativeLocale = NativeModules.EzkeyLocaleModule as {
  getApplicationLanguage: jest.Mock;
  setApplicationLanguage: jest.Mock;
  getSystemLanguages: jest.Mock;
};

const setPlatform = (os: string, version: number) => {
  Object.defineProperty(Platform, 'OS', {configurable: true, writable: true, value: os});
  Object.defineProperty(Platform, 'Version', {configurable: true, writable: true, value: version});
};

describe('app language', () => {
  const originalOs = Platform.OS;
  const originalVersion = Platform.Version;
  let setPreferenceSpy: jest.SpiedFunction<typeof localeStorage.setPreference>;
  let getPreferenceSpy: jest.SpiedFunction<typeof localeStorage.getPreference>;
  let clearPreferenceSpy: jest.SpiedFunction<typeof localeStorage.clearPreference>;
  let changeLanguageSpy: jest.SpiedFunction<typeof i18n.changeLanguage>;

  beforeEach(() => {
    jest.clearAllMocks();
    getPreferenceSpy = jest.spyOn(localeStorage, 'getPreference').mockResolvedValue(null);
    setPreferenceSpy = jest.spyOn(localeStorage, 'setPreference').mockResolvedValue(undefined);
    clearPreferenceSpy = jest.spyOn(localeStorage, 'clearPreference').mockResolvedValue(undefined);
    changeLanguageSpy = jest.spyOn(i18n, 'changeLanguage').mockResolvedValue(i18n.t);
    Object.defineProperty(i18n, 'isInitialized', {configurable: true, value: true});
    Object.defineProperty(i18n, 'language', {configurable: true, writable: true, value: 'en'});
    nativeLocale.getApplicationLanguage.mockResolvedValue('');
    nativeLocale.getSystemLanguages.mockResolvedValue(['en-US']);
  });

  afterEach(() => {
    setPlatform(originalOs, originalVersion as number);
    jest.restoreAllMocks();
  });

  describe('without a system per-app language (Android 12, iOS)', () => {
    beforeEach(() => setPlatform('android', 31));

    it('persists the choice in the app and applies it in-process', async () => {
      const next = await changeAppLanguage('fr');

      expect(next).toBe('fr');
      expect(setPreferenceSpy).toHaveBeenCalledWith('fr');
      expect(nativeLocale.setApplicationLanguage).not.toHaveBeenCalled();
      expect(changeLanguageSpy).toHaveBeenCalledWith('fr');
    });

    it('does not call changeLanguage when the locale is already active', async () => {
      Object.defineProperty(i18n, 'language', {configurable: true, writable: true, value: 'fr'});

      await changeAppLanguage('fr');

      expect(changeLanguageSpy).not.toHaveBeenCalled();
    });

    it('follows the phone when nothing was chosen', async () => {
      await expect(getLanguagePreference()).resolves.toBe('system');
    });
  });

  describe('System default', () => {
    it('follows a French phone', async () => {
      nativeLocale.getSystemLanguages.mockResolvedValue(['fr-CA', 'en-US']);
      setPlatform('android', 34);

      await expect(resolveLocale('system')).resolves.toBe('fr');
    });

    it('falls back to English for a language Ezkey does not ship', async () => {
      nativeLocale.getSystemLanguages.mockResolvedValue(['es-ES']);
      setPlatform('android', 34);

      await expect(resolveLocale('system')).resolves.toBe('en');
    });
  });

  describe('with the Android 13+ per-app language', () => {
    beforeEach(() => setPlatform('android', 34));

    it('writes the choice to Android settings instead of the app store', async () => {
      await changeAppLanguage('fr');

      expect(nativeLocale.setApplicationLanguage).toHaveBeenCalledWith('fr');
      expect(setPreferenceSpy).not.toHaveBeenCalled();
    });

    it('clears the per-app language for System default', async () => {
      await changeAppLanguage('system');

      expect(nativeLocale.setApplicationLanguage).toHaveBeenCalledWith('');
    });

    it('reads a language chosen in Android settings', async () => {
      nativeLocale.getApplicationLanguage.mockResolvedValue('fr');

      await expect(getLanguagePreference()).resolves.toBe('fr');
    });

    it('pushes an older in-app choice to Android settings once, then clears it', async () => {
      getPreferenceSpy.mockResolvedValue('fr');

      await expect(getLanguagePreference()).resolves.toBe('fr');
      expect(nativeLocale.setApplicationLanguage).toHaveBeenCalledWith('fr');
      expect(clearPreferenceSpy).toHaveBeenCalled();
    });

    it('applies a change made in Android settings when the app returns', async () => {
      nativeLocale.getApplicationLanguage.mockResolvedValue('fr');

      await expect(syncAppLanguage()).resolves.toBe('fr');
      expect(changeLanguageSpy).toHaveBeenCalledWith('fr');
    });

    it('falls back to the app store when the native call fails', async () => {
      getPreferenceSpy.mockResolvedValue('fr');
      nativeLocale.getApplicationLanguage.mockRejectedValue(new Error('boom'));
      jest.spyOn(console, 'warn').mockImplementation(jest.fn());

      await expect(getLanguagePreference()).resolves.toBe('fr');
    });
  });
});
