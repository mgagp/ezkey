import React from 'react';
import {Linking, Platform, SectionList, Text} from 'react-native';
import renderer from 'react-test-renderer';
import {LicenseDetailScreen, LicensesScreen} from '../app/screens/Licenses';
import {licenseGroupsFor} from '../app/screens/Licenses/licenseData';

const textOf = (tree: renderer.ReactTestRenderer) =>
  tree.root
    .findAllByType(Text)
    .map(node => node.props.children)
    .flat()
    .join(' ');

describe('LicensesScreen (#1109)', () => {
  const originalOs = Platform.OS;

  afterEach(() => {
    Object.defineProperty(Platform, 'OS', {value: originalOs, configurable: true});
  });

  it('groups bundled JS, Android libraries, and vendor terms, then opens a row', async () => {
    Object.defineProperty(Platform, 'OS', {value: 'android', configurable: true});
    const navigate = jest.fn();
    let tree: renderer.ReactTestRenderer;

    await renderer.act(async () => {
      tree = renderer.create(
        <LicensesScreen
          navigation={{navigate} as never}
          route={{key: 'Licenses-key', name: 'Licenses'} as never}
        />,
      );
    });

    const sections = tree!.root.findByType(SectionList).props.sections as Array<{title: string}>;
    expect(sections.map(section => section.title)).toEqual([
      'App libraries',
      'Android libraries',
      'Vendor components',
    ]);
    expect(textOf(tree!)).not.toContain('yarn license:app-data');

    const axiosRow = tree!.root.findAll(
      node =>
        typeof node.props.onPress === 'function' &&
        node.findAllByType(Text).some(textNode => textNode.props.children === 'axios'),
    )[0];
    expect(axiosRow).toBeDefined();

    await renderer.act(async () => {
      axiosRow!.props.onPress();
    });

    expect(navigate).toHaveBeenCalledWith('LicenseDetail', {
      id: expect.stringMatching(/^js:axios@/),
    });
  });

  it('keeps transitive bundled packages and splits vendor terms from open source', () => {
    const groups = licenseGroupsFor('android');

    expect(groups.app.map(entry => entry.name)).toEqual(
      expect.arrayContaining(['react-native', 'axios', '@react-navigation/core']),
    );
    expect(groups.app.every(entry => (entry.textIds ?? []).length > 0)).toBe(true);
    expect(groups.native.some(entry => entry.id === 'android:org.conscrypt:conscrypt-android')).toBe(
      true,
    );
    expect(groups.native.every(entry => !entry.proprietary)).toBe(true);
    expect(groups.vendor.length).toBeGreaterThan(0);
    expect(groups.vendor.every(entry => (entry.termsUrls ?? []).length > 0)).toBe(true);
  });

  it('omits Android-only JS (ML Kit scanner) and Android natives on iOS', () => {
    const groups = licenseGroupsFor('ios');

    expect(groups.app.some(entry => entry.name === 'react-native-vision-camera-barcode-scanner')).toBe(
      false,
    );
    expect(groups.native.some(entry => entry.source === 'android')).toBe(false);
    expect(groups.vendor).toHaveLength(0);
  });
});

describe('LicenseDetailScreen (#1109)', () => {
  it('shows the full bundled license text', async () => {
    const entry = licenseGroupsFor('android').app.find(item => item.name === 'axios')!;
    let tree: renderer.ReactTestRenderer;

    await renderer.act(async () => {
      tree = renderer.create(
        <LicenseDetailScreen
          navigation={{} as never}
          route={{key: 'LicenseDetail-key', name: 'LicenseDetail', params: {id: entry.id}} as never}
        />,
      );
    });

    const content = textOf(tree!);
    expect(content).toContain('axios');
    expect(content).toContain(entry.version);
    expect(content).toContain('Permission is hereby granted');
  });

  it('links vendor components to their terms instead of a license text', async () => {
    const openURL = jest.spyOn(Linking, 'openURL').mockResolvedValue(undefined);
    const vendor = licenseGroupsFor('android').vendor[0];
    let tree: renderer.ReactTestRenderer;

    await renderer.act(async () => {
      tree = renderer.create(
        <LicenseDetailScreen
          navigation={{} as never}
          route={{key: 'LicenseDetail-key', name: 'LicenseDetail', params: {id: vendor.id}} as never}
        />,
      );
    });

    expect(textOf(tree!)).toContain('not an open source license');
    const termsLink = tree!.root.findAll(
      node =>
        typeof node.props.onPress === 'function' &&
        node.findAllByType(Text).some(textNode => textNode.props.children === 'Read the terms'),
    )[0];

    await renderer.act(async () => {
      termsLink!.props.onPress();
    });

    expect(openURL).toHaveBeenCalledWith(vendor.termsUrls![0]);
    openURL.mockRestore();
  });
});
