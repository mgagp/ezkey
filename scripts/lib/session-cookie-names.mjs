/**
 * Cross-cutting guard for distinct localhost servlet session cookie names (#799 / #800 / #807).
 *
 * Discovers Spring Boot modules that depend on spring-boot-starter-webmvc and asserts each
 * sets a non-default, unique server.servlet.session.cookie.name in its canonical properties.
 *
 * Canonical directory: {module}/config/ when application.properties is present (API modules),
 * else {module}/src/main/resources/ (demos in JAR). Within that directory the guard reads
 * application.properties then application-*.properties (sorted), last assignment wins — same
 * direction as Spring Boot for a single file and for profile overlays (#807).
 *
 * Separators: `key=value`, `key = value`, and `key: value` (optional spaces) are accepted.
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
    const propertiesDir = resolveCanonicalPropertiesDir(moduleDir);
    if (!propertiesDir) {
      throw new Error(
        `${relative(repoRoot, moduleDir)}: webmvc module has no config/application.properties ` +
          `or src/main/resources/application.properties`,
      );
    }
    const { cookieName, propertiesPath } = readCookieNameFromPropertiesDir(propertiesDir);
    if (!cookieName) {
      throw new Error(
        `${relative(repoRoot, propertiesDir)}: missing ${COOKIE_PROP} (#799)`,
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
 * @returns {string | null} absolute directory that holds application*.properties
 */
function resolveCanonicalPropertiesDir(moduleDir) {
  const configProps = join(moduleDir, 'config', 'application.properties');
  if (existsSync(configProps)) {
    return join(moduleDir, 'config');
  }
  const jarProps = join(moduleDir, 'src', 'main', 'resources', 'application.properties');
  if (existsSync(jarProps)) {
    return join(moduleDir, 'src', 'main', 'resources');
  }
  return null;
}

/**
 * Read cookie name from a properties directory. Last assignment across
 * application.properties then sorted application-*.properties wins (#807).
 *
 * @param {string} propertiesDir
 * @returns {{ cookieName: string | null, propertiesPath: string }}
 */
export function readCookieNameFromPropertiesDir(propertiesDir) {
  const files = listPropertiesFiles(propertiesDir);
  let cookieName = null;
  let propertiesPath = join(propertiesDir, 'application.properties');
  for (const filePath of files) {
    const value = readPropertyLastWins(filePath, COOKIE_PROP);
    if (value != null) {
      cookieName = value;
      propertiesPath = filePath;
    }
  }
  return { cookieName, propertiesPath };
}

/**
 * @param {string} propertiesDir
 * @returns {string[]} absolute paths in Spring-ish overlay order
 */
function listPropertiesFiles(propertiesDir) {
  const base = join(propertiesDir, 'application.properties');
  const ordered = [];
  if (existsSync(base)) {
    ordered.push(base);
  }
  const profiles = readdirSync(propertiesDir)
    .filter((name) => /^application-.+\.properties$/i.test(name))
    .sort();
  for (const name of profiles) {
    ordered.push(join(propertiesDir, name));
  }
  return ordered;
}

/**
 * @param {string} filePath
 * @param {string} key
 * @returns {string | null} last non-empty value for key, or null
 */
export function readPropertyLastWins(filePath, key) {
  const text = readFileSync(filePath, 'utf8');
  let last = null;
  for (const rawLine of text.split(/\r?\n/)) {
    const line = rawLine.trim();
    if (line.length === 0 || line.startsWith('#') || line.startsWith('!')) {
      continue;
    }
    const parsed = parsePropertyLine(line);
    if (parsed && parsed.key === key) {
      const value = parsed.value.trim();
      last = value.length > 0 ? value : null;
    }
  }
  return last;
}

/**
 * Accept `key=value`, `key = value`, and `key: value` (#807 separator hardening).
 *
 * @param {string} line trimmed non-comment line
 * @returns {{ key: string, value: string } | null}
 */
export function parsePropertyLine(line) {
  const match = /^([^=:\s][^=:]*?)\s*[=:]\s*(.*)$/.exec(line);
  if (!match) {
    return null;
  }
  return { key: match[1].trim(), value: match[2] };
}
