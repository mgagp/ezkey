import React from 'react';
import {Alert, Text} from 'react-native';
import renderer from 'react-test-renderer';
import {LanguageScreen} from '../app/screens/Language';
import {changeAppLanguage, getLanguagePreference} from '../app/i18n';

jest.mock('../app/i18n', () => ({
  ...jest.requireActual('../app/i18n'),
  changeAppLanguage: jest.fn(),
  getLanguagePreference: jest.fn(),
}));

const mockChangeAppLanguage = jest.mocked(changeAppLanguage);
const mockGetLanguagePreference = jest.mocked(getLanguagePreference);

const render = async () => {
  let tree: renderer.ReactTestRenderer;
  await renderer.act(async () => {
    tree = renderer.create(<LanguageScreen />);
  });
  return tree!;
};

const option = (tree: renderer.ReactTestRenderer, label: string) =>
  tree.root.findAll(
    node => typeof node.props.onPress === 'function' && node.props.accessibilityLabel === label,
  )[0];

describe('LanguageScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockChangeAppLanguage.mockResolvedValue('fr');
    mockGetLanguagePreference.mockResolvedValue('system');
  });

  it('offers System default, English and French, with System default current by default', async () => {
    const tree = await render();

    const textContent = tree.root.findAllByType(Text).map(node => node.props.children).flat().join(' ');
    expect(textContent).toContain('System default');
    expect(textContent).toContain('English');
    expect(textContent).toContain('French');
    expect(option(tree, 'System default').props.accessibilityState).toEqual({
      checked: true,
      disabled: false,
    });
  });

  it('shows the saved language as current', async () => {
    mockGetLanguagePreference.mockResolvedValue('fr');

    const tree = await render();

    expect(option(tree, 'French').props.accessibilityState).toEqual({checked: true, disabled: false});
  });

  it('applies the selected language without a restart alert', async () => {
    const alertSpy = jest.spyOn(Alert, 'alert').mockImplementation(jest.fn());
    const tree = await render();

    await renderer.act(async () => {
      option(tree, 'French').props.onPress();
    });

    expect(mockChangeAppLanguage).toHaveBeenCalledWith('fr');
    expect(alertSpy).not.toHaveBeenCalled();
    alertSpy.mockRestore();
  });

  it('does not apply anything when reselecting the current language', async () => {
    mockGetLanguagePreference.mockResolvedValue('fr');
    const tree = await render();

    await renderer.act(async () => {
      option(tree, 'French').props.onPress();
    });

    expect(mockChangeAppLanguage).not.toHaveBeenCalled();
  });
});
