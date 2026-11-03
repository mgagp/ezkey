import React from 'react';
import {Appearance, NativeModules, StyleSheet} from 'react-native';
import renderer from 'react-test-renderer';
import {AppearanceScreen} from '../app/screens/Appearance';
import {appearanceStorage} from '../app/services/storage/appearanceStorage';
import {darkColors, lightColors} from '../app/config/theme';
import {ThemeProvider} from '../app/theme';
import {getByRole, queryAllByRole} from '../test-utils/a11yQueries';

describe('AppearanceScreen', () => {
  let setAppearanceSpy: jest.SpiedFunction<typeof appearanceStorage.setAppearance>;
  let setColorSchemeSpy: jest.SpiedFunction<typeof Appearance.setColorScheme>;

  beforeEach(() => {
    jest.clearAllMocks();
    jest.spyOn(appearanceStorage, 'getAppearance').mockResolvedValue('system');
    setAppearanceSpy = jest.spyOn(appearanceStorage, 'setAppearance').mockResolvedValue();
    setColorSchemeSpy = jest.spyOn(Appearance, 'setColorScheme').mockImplementation(jest.fn());
  });

  const render = async () => {
    let tree: renderer.ReactTestRenderer;
    await renderer.act(async () => {
      tree = renderer.create(<AppearanceScreen />);
    });
    return tree!;
  };

  const press = (tree: renderer.ReactTestRenderer, label: string) =>
    tree.root
      .findAll(node => typeof node.props.onPress === 'function' && node.props.accessibilityLabel === label)[0]
      .props.onPress();

  it('offers System default, Light and Dark, with System default selected by default', async () => {
    const tree = await render();

    const options = queryAllByRole(tree.root, 'radio');
    expect(options.map(option => option.props.accessibilityLabel)).toEqual([
      'System default',
      'Light',
      'Dark',
    ]);
    expect(getByRole(tree.root, 'radio', 'System default').props.accessibilityState).toEqual({
      checked: true,
    });
  });

  it('applies and saves the chosen theme immediately', async () => {
    const tree = await render();

    await renderer.act(async () => {
      press(tree, 'Light');
    });

    expect(setColorSchemeSpy).toHaveBeenCalledWith('light');
    expect(NativeModules.EzkeyAppearanceModule.setApplicationNightMode).toHaveBeenCalledWith('light');
    expect(setAppearanceSpy).toHaveBeenCalledWith('light');
    expect(getByRole(tree.root, 'radio', 'Light').props.accessibilityState).toEqual({checked: true});
  });

  it('maps System default to the unspecified React Native override', async () => {
    jest.spyOn(appearanceStorage, 'getAppearance').mockResolvedValue('dark');
    const tree = await render();

    await renderer.act(async () => {
      press(tree, 'System default');
    });

    expect(setColorSchemeSpy).toHaveBeenCalledWith('unspecified');
    expect(NativeModules.EzkeyAppearanceModule.setApplicationNightMode).toHaveBeenCalledWith('system');
  });

  it('does nothing when reselecting the current theme', async () => {
    const tree = await render();

    await renderer.act(async () => {
      press(tree, 'System default');
    });

    expect(setColorSchemeSpy).not.toHaveBeenCalled();
    expect(setAppearanceSpy).not.toHaveBeenCalled();
  });
});

describe('ThemeProvider', () => {
  const backgroundUnder = async (scheme: 'light' | 'dark' | null) => {
    jest.spyOn(require('react-native'), 'useColorScheme').mockReturnValue(scheme);
    let tree: renderer.ReactTestRenderer;
    await renderer.act(async () => {
      tree = renderer.create(
        <ThemeProvider>
          <AppearanceScreen />
        </ThemeProvider>,
      );
    });
    const screen = tree!.root.findAll(node => node.props.accessibilityLabel === 'Appearance')[0];
    return StyleSheet.flatten(screen.props.style)?.backgroundColor;
  };

  beforeEach(() => {
    jest.spyOn(appearanceStorage, 'getAppearance').mockResolvedValue('system');
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  it('renders the light palette when the color scheme is light', async () => {
    await expect(backgroundUnder('light')).resolves.toBe(lightColors.background);
  });

  it('keeps the dark palette for dark or unknown color schemes', async () => {
    await expect(backgroundUnder('dark')).resolves.toBe(darkColors.background);
    await expect(backgroundUnder(null)).resolves.toBe(darkColors.background);
  });
});
