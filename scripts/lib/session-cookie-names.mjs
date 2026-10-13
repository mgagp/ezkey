/**
 * Cross-cutting guard for distinct localhost servlet session cookie names (#799 / #800).
 *
 * Discovers Spring Boot modules that depend on spring-boot-starter-webmvc and asserts each
 * sets a non-default, unique server.servlet.session.cookie.name in its canonical properties.
 *
 * Canonical file: {module}/config/application.properties when present (API modules ship
 * external config/), else {module}/src/main/resources/application.properties (demos in JAR).
 */
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { join, relative } from 'node:path';

const COOKIE_PROP = 'server.servlet.session.cookie.name';
const DEFAULT_COOKIE = 'JSESSIONID';
const WEBMVC_ARTIFACT = 'spring-boot-starter-webmvc';

/**
 * @param {string} repoRoot absolute repo root
 * @returns {{ module: string, propertiesPath: string, cookieName: string }[]}
 */
export function collectServletSessionCookieNames(repoRoot) {
  const modules = discoverWebmvcModules(repoRoot);
  const rows = [];
  for (const moduleDir of modules) {
    const propertiesPath = resolveCanonicalProperties(moduleDir);
    if (!propertiesPath) {
      throw new Error(
        `${relative(repoRoot, moduleDir)}: webmvc module has no config/application.properties ` +
          `or src/main/resources/application.properties`,
      );
    }
    const cookieName = readProperty(propertiesPath, COOKIE_PROP);
    if (!cookieName) {
      throw new Error(
        `${relative(repoRoot, propertiesPath)}: missing ${COOKIE_PROP} (#799)`,
      );
    }
    rows.push({
      module: relative(repoRoot, moduleDir),
      propertiesPath: relative(repoRoot, propertiesPath),
      cookieName,
    });
  }
  return rows;
}

/**
 * @param {string} repoRoot
 * @returns {{ ok: true, rows: ReturnType<typeof collectServletSessionCookieNames> }
 *   | { ok: false, errors: string[], rows: ReturnType<typeof collectServletSessionCookieNames> }}
 */
export function assertDistinctServletSessionCookieNames(repoRoot) {
  const rows = collectServletSessionCookieNames(repoRoot);
  const errors = [];
  const seen = new Map();

  for (const row of rows) {
    if (row.cookieName.toUpperCase() === DEFAULT_COOKIE) {
      errors.push(
        `${row.propertiesPath}: ${COOKIE_PROP}=${row.cookieName} is the Spring default; ` +
          `set a distinct EZKEY_* name (#799)`,
      );
    }
    const prior = seen.get(row.cookieName);
    if (prior) {
      errors.push(
        `duplicate ${COOKIE_PROP}=${row.cookieName} in ${prior} and ${row.propertiesPath}`,
      );
    } else {
      seen.set(row.cookieName, row.propertiesPath);
    }
  }

  if (rows.length < 2) {
    errors.push(
      `expected at least two webmvc modules with cookie names; found ${rows.length}`,
    );
  }

  return errors.length === 0 ? { ok: true, rows } : { ok: false, errors, rows };
}

/**
 * @param {string} repoRoot
 * @returns {string[]} absolute module directories
 */
function discoverWebmvcModules(repoRoot) {
  const found = [];
  for (const entry of readdirSync(repoRoot, { withFileTypes: true })) {
    if (!entry.isDirectory() || entry.name.startsWith('.')) {
      continue;
    }
    const moduleDir = join(repoRoot, entry.name);
    const pomPath = join(moduleDir, 'pom.xml');
    if (!existsSync(pomPath)) {
      continue;
    }
    const pom = readFileSync(pomPath, 'utf8');
    if (pom.includes(WEBMVC_ARTIFACT)) {
      found.push(moduleDir);
    }
  }
  found.sort();
  return found;
}

/**
 * @param {string} moduleDir
 * @returns {string | null} absolute path
 */
function resolveCanonicalProperties(moduleDir) {
  const configProps = join(moduleDir, 'config', 'application.properties');
  if (existsSync(configProps)) {
    return configProps;
  }
  const jarProps = join(moduleDir, 'src', 'main', 'resources', 'application.properties');
  if (existsSync(jarProps)) {
    return jarProps;
  }
  return null;
}

/**
 * @param {string} filePath
 * @param {string} key
 * @returns {string | null}
 */
function readProperty(filePath, key) {
  const text = readFileSync(filePath, 'utf8');
  const escaped = key.replace(/\./g, '\\.');
  const re = new RegExp(`^${escaped}=(.*)$`, 'm');
  const match = text.match(re);
  if (!match) {
    return null;
  }
  const value = match[1].trim();
  return value.length > 0 ? value : null;
}
