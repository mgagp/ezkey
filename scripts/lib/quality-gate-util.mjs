/**
 * Tiny shared helpers for quality-gate Node scripts.
 * Cloud agent tooling; Git Bash best-effort, untested on macOS.
 */

import { existsSync, readFileSync } from 'node:fs';

/**
 * @param {string} path
 * @returns {unknown|null}
 */
export function loadJson(path) {
  if (!existsSync(path)) return null;
  try {
    return JSON.parse(readFileSync(path, 'utf8'));
  } catch {
    return null;
  }
}

/**
 * @param {string[]} argv
 * @param {Record<string, string>} flagMap e.g. { '--run-dir': 'runDir' }
 * @returns {Record<string, string>}
 */
export function parseArgs(argv, flagMap) {
  const out = {};
  for (let i = 0; i < argv.length; i += 1) {
    const key = argv[i];
    if (flagMap[key]) {
      out[flagMap[key]] = argv[i + 1];
      i += 1;
    }
  }
  return out;
}
