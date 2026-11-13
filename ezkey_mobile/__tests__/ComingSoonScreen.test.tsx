import React from 'react';
import {Text} from 'react-native';
import renderer from 'react-test-renderer';
import {ComingSoonScreen} from '../app/screens/ComingSoon';

describe('ComingSoonScreen', () => {
  it('renders the near-term roadmap content', async () => {
    let tree: renderer.ReactTestRenderer;

    await renderer.act(async () => {
      tree = renderer.create(<ComingSoonScreen />);
    });

    const textContent = tree!.root.findAllByType(Text).map(node => node.props.children).flat().join(' ');

    expect(textContent).toContain('Coming soon');
    expect(textContent).toContain('Clearer approval rules');
    expect(textContent).toContain('Stronger connection checks');
    expect(textContent).not.toContain('Certificate pinning');
    expect(textContent).not.toContain('over-claiming perfection');
    expect(textContent).not.toContain('Language switching without restart');
  });
});
