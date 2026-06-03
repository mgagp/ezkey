/*
 * Ezkey Method · methodology version reader
 *
 * Reads product-docs/methodology-version.properties — the canonical SemVer
 * for the publishable methodology product (independent of the Ezkey monorepo
 * Maven version).
 */

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const VERSION_FILE = path.resolve(__dirname, '..', 'methodology-version.properties');

/**
 * @returns {{ version: string, released: string|null }}
 */
export function readMethodologyVersion() {
  const result = { version: '0.0.0', released: null };
  try {
    const raw = fs.readFileSync(VERSION_FILE, 'utf8');
    for (const line of raw.split('\n')) {
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith('#')) continue;
      const eq = trimmed.indexOf('=');
      if (eq < 0) continue;
      const key = trimmed.slice(0, eq).trim();
      const value = trimmed.slice(eq + 1).trim();
      if (key === 'version') result.version = value;
      if (key === 'released') result.released = value || null;
    }
  } catch {
    /* file missing — fall back to defaults */
  }
  return result;
}
