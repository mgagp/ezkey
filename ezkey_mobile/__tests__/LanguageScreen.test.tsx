import React from 'react';
import {Alert, Text} from 'react-native';
import renderer from 'react-test-renderer';
import {LanguageScreen} from '../app/screens/Language';
import {changeAppLanguage, i18n} from '../app/i18n';
import {localeStorage} from '../app/services/storage/localeStorage';

jest.mock('../app/i18n', () => ({
  ...jest.requireActual('../app/i18n'),
  changeAppLanguage: jest.fn(),
  i18n: {
    language: 'en',
    resolvedLanguage: 'en',
  },
}));

const mockChangeAppLanguage = jest.mocked(changeAppLanguage);
let getLocaleSpy: jest.SpiedFunction<typeof localeStorage.getLocale>;

describe('LanguageScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockChangeAppLanguage.mockResolvedValue('fr');
    getLocaleSpy = jest.spyOn(localeStorage, 'getLocale').mockResolvedValue('en');
    i18n.language = 'en';
    i18n.resolvedLanguage = 'en';
  });

  it('shows English as the current language initially', async () => {
    let tree: renderer.ReactTestRenderer;

    await renderer.act(async () => {
      tree = renderer.create(<LanguageScreen />);
    });

    const textContent = tree!.root.findAllByType(Text).map(node => node.props.children).flat().join(' ');

    expect(textContent).toContain('English');
    expect(textContent).toContain('Current language');
    expect(textContent).toContain('French');
  });

  it('hydrates the persisted locale on mount', async () => {
    getLocaleSpy.mockResolvedValue('fr');

    let tree: renderer.ReactTestRenderer;

    await renderer.act(async () => {
      tree = renderer.create(<LanguageScreen />);
    });

    const textContent = tree!.root.findAllByType(Text).map(node => node.props.children).flat().join(' ');

    expect(textContent).toContain('French');
    expect(textContent).toContain('Current language');
  });

  it('persists the selected language without a restart alert', async () => {
    const alertSpy = jest.spyOn(Alert, 'alert').mockImplementation(jest.fn());
    let tree: renderer.ReactTestRenderer;

    await renderer.act(async () => {
      tree = renderer.create(<LanguageScreen />);
    });

    const frenchButton = tree!.root.findAll(
      node =>
        typeof node.props.onPress === 'function' &&
        node.props.accessibilityLabel === 'French',
    )[0];

    expect(frenchButton).toBeDefined();

    await renderer.act(async () => {
      frenchButton!.props.onPress();
    });

    expect(mockChangeAppLanguage).toHaveBeenCalledWith('fr');
    expect(alertSpy).not.toHaveBeenCalled();

    alertSpy.mockRestore();
  });

  it('does not persist when reselecting the current language', async () => {
    getLocaleSpy.mockResolvedValue('fr');
    const alertSpy = jest.spyOn(Alert, 'alert').mockImplementation(jest.fn());
    let tree: renderer.ReactTestRenderer;

    await renderer.act(async () => {
      tree = renderer.create(<LanguageScreen />);
    });

    const frenchButton = tree!.root.findAll(
      node =>
        typeof node.props.onPress === 'function' &&
        node.props.accessibilityLabel === 'French',
    )[0];

    await renderer.act(async () => {
      frenchButton!.props.onPress();
    });

    expect(mockChangeAppLanguage).not.toHaveBeenCalled();
    expect(alertSpy).not.toHaveBeenCalled();

    alertSpy.mockRestore();
  });
});
