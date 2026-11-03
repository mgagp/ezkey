import React from 'react';
import renderer from 'react-test-renderer';
import {NativeModules, Platform} from 'react-native';
import {useSensitiveScreen} from '../useSensitiveScreen';

type Listener = () => void;

const createNavigation = (focused: boolean) => {
  const listeners: Record<string, Listener[]> = {focus: [], blur: []};
  return {
    listeners,
    isFocused: jest.fn(() => focused),
    addListener: jest.fn((event: 'focus' | 'blur', callback: Listener) => {
      listeners[event].push(callback);
      return () => {
        listeners[event] = listeners[event].filter(cb => cb !== callback);
      };
    }),
  };
};

const Probe: React.FC<{navigation: ReturnType<typeof createNavigation>}> = ({navigation}) => {
  useSensitiveScreen(navigation);
  return null;
};

describe('useSensitiveScreen', () => {
  const originalOs = Platform.OS;
  const nativeSet = NativeModules.EzkeyWindowSecurityModule.setSensitiveScreen as jest.Mock;

  beforeEach(() => {
    Object.defineProperty(Platform, 'OS', {configurable: true, writable: true, value: 'android'});
    nativeSet.mockReset();
  });

  afterEach(() => {
    Object.defineProperty(Platform, 'OS', {configurable: true, writable: true, value: originalOs});
  });

  it('enables protections when mounted focused and clears them on unmount', async () => {
    const navigation = createNavigation(true);
    let tree: renderer.ReactTestRenderer;
    await renderer.act(async () => {
      tree = renderer.create(<Probe navigation={navigation} />);
    });

    expect(nativeSet).toHaveBeenLastCalledWith(true);

    await renderer.act(async () => {
      tree!.unmount();
    });

    expect(nativeSet).toHaveBeenLastCalledWith(false);
    expect(navigation.listeners.focus).toHaveLength(0);
    expect(navigation.listeners.blur).toHaveLength(0);
  });

  it('follows blur and focus so other screens stay capturable', async () => {
    const navigation = createNavigation(true);
    await renderer.act(async () => {
      renderer.create(<Probe navigation={navigation} />);
    });

    navigation.listeners.blur.forEach(cb => cb());
    expect(nativeSet).toHaveBeenLastCalledWith(false);

    navigation.listeners.focus.forEach(cb => cb());
    expect(nativeSet).toHaveBeenLastCalledWith(true);
  });

  it('does not enable protections when mounted unfocused', async () => {
    const navigation = createNavigation(false);
    await renderer.act(async () => {
      renderer.create(<Probe navigation={navigation} />);
    });

    expect(nativeSet).not.toHaveBeenCalled();
  });

  it('is a no-op on iOS', async () => {
    Object.defineProperty(Platform, 'OS', {configurable: true, writable: true, value: 'ios'});
    const navigation = createNavigation(true);
    await renderer.act(async () => {
      renderer.create(<Probe navigation={navigation} />);
    });

    expect(nativeSet).not.toHaveBeenCalled();
  });
});
