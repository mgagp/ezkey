/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Guard: UI code takes colors from app/config/theme.ts only, so the Light / Dark / System
 * theme can swap one palette (#1156).
 */

import fs from 'fs';
import path from 'path';

const UI_DIRS = ['app/screens', 'app/components', 'app/navigation'];
const COLOR_LITERAL = /(['"`])(#[0-9a-fA-F]{3,8}|rgba?\(|hsla?\()/;

const sourceFiles = (dir: string): string[] =>
  fs.readdirSync(dir, {withFileTypes: true}).flatMap(entry => {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      return entry.name === '__tests__' ? [] : sourceFiles(full);
    }
    return /\.(ts|tsx)$/.test(entry.name) ? [full] : [];
  });

describe('color literals', () => {
  it('are not used in screens, components or navigation', () => {
    const root = path.join(__dirname, '..');
    const offenders = UI_DIRS.flatMap(dir => sourceFiles(path.join(root, dir))).flatMap(file =>
      fs
        .readFileSync(file, 'utf8')
        .split('\n')
        .map((line, index) => ({line, index}))
        .filter(({line}) => COLOR_LITERAL.test(line))
        .map(({line, index}) => `${path.relative(root, file)}:${index + 1}: ${line.trim()}`),
    );

    expect(offenders).toEqual([]);
  });
});
