/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: a11yQueries
 * Description: Role-based queries for react-test-renderer trees, so tests reach controls the way
 *              TalkBack / VoiceOver do (role + accessible name) instead of by testID (#1155).
 * @since 2026
 */

import type {ReactTestInstance} from 'react-test-renderer';

const textOf = (node: ReactTestInstance): string =>
  node.children.map(child => (typeof child === 'string' ? child : textOf(child))).join('');

const accessibleName = (node: ReactTestInstance): string =>
  (typeof node.props.accessibilityLabel === 'string'
    ? node.props.accessibilityLabel
    : textOf(node)
  ).trim();

const matches = (value: string, name: string | RegExp): boolean =>
  typeof name === 'string' ? value === name : name.test(value);

/**
 * Host elements exposing `role`, optionally filtered by accessible name (label, else text).
 *
 * @param root Tree root (`tree.root`).
 * @param role Expected `accessibilityRole`.
 * @param name Exact accessible name or a pattern.
 * @return Matching host elements.
 * @since 2026
 */
export const queryAllByRole = (
  root: ReactTestInstance,
  role: string,
  name?: string | RegExp,
): ReactTestInstance[] =>
  root.findAll(
    node =>
      typeof node.type === 'string' &&
      node.props.accessibilityRole === role &&
      (name == null || matches(accessibleName(node), name)),
  );

/**
 * Single host element with `role` and `name`; throws when there is not exactly one.
 *
 * @param root Tree root (`tree.root`).
 * @param role Expected `accessibilityRole`.
 * @param name Exact accessible name or a pattern.
 * @return The matching host element.
 * @since 2026
 */
export const getByRole = (
  root: ReactTestInstance,
  role: string,
  name: string | RegExp,
): ReactTestInstance => {
  const found = queryAllByRole(root, role, name);
  if (found.length !== 1) {
    const roles = queryAllByRole(root, role).map(accessibleName);
    throw new Error(
      `Expected one ${role} named ${String(name)}, found ${found.length}. ${role}s: ${JSON.stringify(roles)}`,
    );
  }
  return found[0];
};
