import React from 'react';
import {Text} from 'react-native';
import renderer from 'react-test-renderer';
import {SettingsScreen} from '../app/screens/Settings';
import {getByRole, queryAllByRole} from '../test-utils/a11yQueries';

describe('SettingsScreen', () => {
  it('renders the release notes entry and routes to the release notes screen', async () => {
    const navigate = jest.fn();
    let tree: renderer.ReactTestRenderer;

    await renderer.act(async () => {
      tree = renderer.create(
        <SettingsScreen
          navigation={{navigate} as never}
          route={{key: 'Settings-key', name: 'Settings'} as never}
        />,
      );
    });

    const textContent = tree!.root.findAllByType(Text).map(node => node.props.children).flat().join(' ');
    expect(textContent).toContain("What's new");
    expect(textContent).toContain('What this release includes');

    const releaseNotesItem = tree!.root.findAll(
      node =>
        typeof node.props.onPress === 'function' &&
        node.findAllByType(Text).some(textNode => textNode.props.children === "What's new"),
    )[0];

    expect(releaseNotesItem).toBeDefined();

    await renderer.act(async () => {
      releaseNotesItem!.props.onPress();
    });

    expect(navigate).toHaveBeenCalledWith('ReleaseNotes');
  });

  it('renders the language entry and routes to the language screen', async () => {
    const navigate = jest.fn();
    let tree: renderer.ReactTestRenderer;

    await renderer.act(async () => {
      tree = renderer.create(
        <SettingsScreen
          navigation={{navigate} as never}
          route={{key: 'Settings-key', name: 'Settings'} as never}
        />,
      );
    });

    const textContent = tree!.root.findAllByType(Text).map(node => node.props.children).flat().join(' ');
    expect(textContent).toContain('Language');
    expect(textContent).toContain('English by default; French available');

    const languageItem = tree!.root.findAll(
      node =>
        typeof node.props.onPress === 'function' &&
        node.findAllByType(Text).some(textNode => textNode.props.children === 'Language'),
    )[0];

    expect(languageItem).toBeDefined();

    await renderer.act(async () => {
      languageItem!.props.onPress();
    });

    expect(navigate).toHaveBeenCalledWith('Language');
  });

  it('renders the coming soon entry and routes to the coming soon screen', async () => {
    const navigate = jest.fn();
    let tree: renderer.ReactTestRenderer;

    await renderer.act(async () => {
      tree = renderer.create(
        <SettingsScreen
          navigation={{navigate} as never}
          route={{key: 'Settings-key', name: 'Settings'} as never}
        />,
      );
    });

    const textContent = tree!.root.findAllByType(Text).map(node => node.props.children).flat().join(' ');
    expect(textContent).toContain('Coming soon');
    expect(textContent).toContain('What we plan next for this app');

    const comingSoonItem = tree!.root.findAll(
      node =>
        typeof node.props.onPress === 'function' &&
        node.findAllByType(Text).some(textNode => textNode.props.children === 'Coming soon'),
    )[0];

    expect(comingSoonItem).toBeDefined();

    await renderer.act(async () => {
      comingSoonItem!.props.onPress();
    });

    expect(navigate).toHaveBeenCalledWith('ComingSoon');
  });

  it('renders the security entry and routes to the security screen', async () => {
    const navigate = jest.fn();
    let tree: renderer.ReactTestRenderer;

    await renderer.act(async () => {
      tree = renderer.create(
        <SettingsScreen
          navigation={{navigate} as never}
          route={{key: 'Settings-key', name: 'Settings'} as never}
        />,
      );
    });

    const textContent = tree!.root.findAllByType(Text).map(node => node.props.children).flat().join(' ');
    expect(textContent).toContain('Security');
    expect(textContent).toContain('No extra confirmation before responding');

    const securityItem = tree!.root.findAll(
      node =>
        typeof node.props.onPress === 'function' &&
        node.findAllByType(Text).some(textNode => textNode.props.children === 'Security'),
    )[0];

    expect(securityItem).toBeDefined();

    await renderer.act(async () => {
      securityItem!.props.onPress();
    });

    expect(navigate).toHaveBeenCalledWith('Security');
  });

  it('exposes every settings row as a button named by its label and subtitle', async () => {
    let tree: renderer.ReactTestRenderer;
    await renderer.act(async () => {
      tree = renderer.create(
        <SettingsScreen
          navigation={{navigate: jest.fn()} as never}
          route={{key: 'Settings-key', name: 'Settings'} as never}
        />,
      );
    });

    const rows = queryAllByRole(tree!.root, 'button');
    expect(rows.length).toBeGreaterThanOrEqual(6);
    expect(getByRole(tree!.root, 'button', /^Language, /)).toBeDefined();
    // The decorative chevron must not be part of the spoken name (#1155).
    rows.forEach(row => expect(row.props.accessibilityLabel).not.toContain('›'));
  });
});
