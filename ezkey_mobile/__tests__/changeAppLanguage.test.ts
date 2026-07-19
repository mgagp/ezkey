import {changeAppLanguage, i18n} from '../app/i18n';
import {localeStorage} from '../app/services/storage/localeStorage';

describe('changeAppLanguage', () => {
  let setLocaleSpy: jest.SpiedFunction<typeof localeStorage.setLocale>;
  let changeLanguageSpy: jest.SpiedFunction<typeof i18n.changeLanguage>;

  beforeEach(() => {
    setLocaleSpy = jest.spyOn(localeStorage, 'setLocale').mockResolvedValue(undefined);
    changeLanguageSpy = jest.spyOn(i18n, 'changeLanguage').mockResolvedValue(i18n.t);
    Object.defineProperty(i18n, 'isInitialized', {configurable: true, value: true});
    Object.defineProperty(i18n, 'language', {configurable: true, writable: true, value: 'en'});
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  it('persists the locale and applies it in-process', async () => {
    const next = await changeAppLanguage('fr');

    expect(next).toBe('fr');
    expect(setLocaleSpy).toHaveBeenCalledWith('fr');
    expect(changeLanguageSpy).toHaveBeenCalledWith('fr');
  });

  it('does not call changeLanguage when the locale is already active', async () => {
    Object.defineProperty(i18n, 'language', {configurable: true, writable: true, value: 'fr'});

    await changeAppLanguage('fr');

    expect(setLocaleSpy).toHaveBeenCalledWith('fr');
    expect(changeLanguageSpy).not.toHaveBeenCalled();
  });
});
