import React from 'react';
import {Text} from 'react-native';
import renderer from 'react-test-renderer';
import {ReleaseNotesScreen} from '../app/screens/ReleaseNotes';

describe('ReleaseNotesScreen', () => {
  it('renders the official release content', async () => {
    let tree: renderer.ReactTestRenderer;

    await renderer.act(async () => {
      tree = renderer.create(<ReleaseNotesScreen />);
    });

    const textContent = tree!.root.findAllByType(Text).map(node => node.props.children).flat().join(' ');

    expect(textContent).toContain('Ezkey Mobile for Android');
    expect(textContent).toContain('ezkey.org');
    expect(textContent).toContain('Requirements');
    expect(textContent).toContain('Android 12');
    expect(textContent).toContain('keys used on this phone');
    expect(textContent).not.toContain('Coming next');
    expect(textContent).not.toContain('Certificate pinning');
    expect(textContent).not.toContain('analytics');
    expect(textContent).not.toContain('experimental');
    expect(textContent).not.toContain('info@ezkey.org');
    expect(textContent).not.toContain('activation code');
  });
});