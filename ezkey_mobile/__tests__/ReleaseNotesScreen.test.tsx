import React from 'react';
import {Text} from 'react-native';
import renderer from 'react-test-renderer';
import {ReleaseNotesScreen} from '../app/screens/ReleaseNotes';

describe('ReleaseNotesScreen', () => {
  it('renders the experimental release content', async () => {
    let tree: renderer.ReactTestRenderer;

    await renderer.act(async () => {
      tree = renderer.create(<ReleaseNotesScreen />);
    });

    const textContent = tree!.root.findAllByType(Text).map(node => node.props.children).flat().join(' ');

    expect(textContent).toContain('First experimental release');
    expect(textContent).toContain('ezkey.org');
    expect(textContent).toContain('Experimental access');
    expect(textContent).toContain('info@ezkey.org');
    expect(textContent).toContain('limited experimental audience');
  });
});