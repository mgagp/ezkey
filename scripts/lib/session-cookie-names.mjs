/**
 * Cross-cutting guard for distinct localhost servlet session cookie names (#799 / #800 / #807).
 *
 * Discovers Spring Boot modules that depend on spring-boot-starter-webmvc and asserts each
 * sets a non-default, unique server.servlet.session.cookie.name in its canonical base
 * application.properties.
 *
 * Canonical directory: {module}/config/ when application.properties is present (API modules),
 * else {module}/src/main/resources/ (demos in JAR). Within a single file, last assignment wins.
 * Each application-*.properties profile is checked separately (base + that profile only) — not
 * by stacking every profile file (#807 m4).
 *
 * Separators: `key=value`, `key = value`, and `key: value` (optional spaces) are accepted.
 * Supports `\` line continuation and `\:` / `\=` escapes in values (#807 m5).
 */
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { basename, join, relative } from 'node:path';

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
    const basePath = join(propertiesDir, 'application.properties');
    const cookieName = readPropertyLastWins(basePath, COOKIE_PROP);
    if (!cookieName) {
      throw new Error(
        `${relative(repoRoot, basePath)}: missing ${COOKIE_PROP} (#799)`,
      );
    }
    rows.push({
      module: relative(repoRoot, moduleDir),
      propertiesPath: relative(repoRoot, basePath),
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
  const modules = discoverWebmvcModules(repoRoot);

  /** @type {Map<string, Map<string, { cookieName: string, propertiesPath: string }>>} */
  const byActivation = new Map();

  const ensureActivation = (name) => {
    if (!byActivation.has(name)) {
      byActivation.set(name, new Map());
    }
    return byActivation.get(name);
  };

  for (const moduleDir of modules) {
    const propertiesDir = resolveCanonicalPropertiesDir(moduleDir);
    if (!propertiesDir) {
      continue;
    }
    const moduleRel = relative(repoRoot, moduleDir);
    const basePath = join(propertiesDir, 'application.properties');
    const baseName = readPropertyLastWins(basePath, COOKIE_PROP);
    const baseRel = relative(repoRoot, basePath);

    // Default activation: base file only
    if (baseName) {
      ensureActivation('').set(moduleRel, {
        cookieName: baseName,
        propertiesPath: baseRel,
      });
    }

    // Each profile separately: effective = profile value if set, else base (#807 m4)
    for (const profilePath of listProfilePropertyFiles(propertiesDir)) {
      const profileKey = profileNameFromFile(profilePath);
      const profileValue = readPropertyLastWins(profilePath, COOKIE_PROP);
      const effective = profileValue != null ? profileValue : baseName;
      if (!effective) {
        continue;
      }
      const pathRel =
        profileValue != null ? relative(repoRoot, profilePath) : baseRel;
      ensureActivation(profileKey).set(moduleRel, {
        cookieName: effective,
        propertiesPath: pathRel,
      });
    }
  }

  for (const [activation, moduleMap] of byActivation) {
    const seen = new Map();
    const label = activation === '' ? 'default' : `profile=${activation}`;
    for (const [moduleRel, decl] of moduleMap) {
      if (decl.cookieName.toUpperCase() === DEFAULT_COOKIE) {
        errors.push(
          `${decl.propertiesPath} (${label}, module ${moduleRel}): ` +
            `${COOKIE_PROP}=${decl.cookieName} is the Spring default; ` +
            `set a distinct EZKEY_* name (#799)`,
        );
      }
      const prior = seen.get(decl.cookieName);
      if (prior) {
        errors.push(
          `duplicate ${COOKIE_PROP}=${decl.cookieName} under ${label} in ${prior} and ${decl.propertiesPath}`,
        );
      } else {
        seen.set(decl.cookieName, decl.propertiesPath);
      }
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
 * @param {string} propertiesDir
 * @returns {string[]} absolute paths of application-*.properties (sorted)
 */
function listProfilePropertyFiles(propertiesDir) {
  return readdirSync(propertiesDir)
    .filter((name) => /^application-.+\.properties$/i.test(name))
    .sort()
    .map((name) => join(propertiesDir, name));
}

/**
 * @param {string} profilePath
 * @returns {string} profile name (e.g. docker from application-docker.properties)
 */
function profileNameFromFile(profilePath) {
  const name = basename(profilePath);
  const match = /^application-(.+)\.properties$/i.exec(name);
  return match ? match[1] : name;
}

/**
 * @param {string} filePath
 * @param {string} key
 * @returns {string | null} last non-empty value for key, or null
 */
export function readPropertyLastWins(filePath, key) {
  if (!existsSync(filePath)) {
    return null;
  }
  const text = readFileSync(filePath, 'utf8');
  const logicalLines = joinContinuedLines(text.split(/\r?\n/));
  let last = null;
  for (const rawLine of logicalLines) {
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
 * Join Java Properties line continuations (odd number of trailing backslashes).
 *
 * @param {string[]} lines
 * @returns {string[]}
 */
export function joinContinuedLines(lines) {
  const out = [];
  let buf = '';
  for (const line of lines) {
    const trailing = countTrailingBackslashes(line);
    if (trailing % 2 === 1) {
      buf += line.slice(0, -1);
      continue;
    }
    buf += line;
    out.push(buf);
    buf = '';
  }
  if (buf.length > 0) {
    out.push(buf);
  }
  return out;
}

/**
 * @param {string} line
 * @returns {number}
 */
function countTrailingBackslashes(line) {
  let n = 0;
  for (let i = line.length - 1; i >= 0 && line[i] === '\\'; i--) {
    n++;
  }
  return n;
}

/**
 * Unescape Java Properties value escapes: `\:` `\\=` `\\` and common `\t` `\n` `\r`.
 *
 * @param {string} value
 * @returns {string}
 */
export function unescapePropertyValue(value) {
  let out = '';
  for (let i = 0; i < value.length; i++) {
    if (value[i] !== '\\' || i + 1 >= value.length) {
      out += value[i];
      continue;
    }
    const next = value[i + 1];
    if (next === ':' || next === '=' || next === '\\') {
      out += next;
      i++;
    } else if (next === 't') {
      out += '\t';
      i++;
    } else if (next === 'n') {
      out += '\n';
      i++;
    } else if (next === 'r') {
      out += '\r';
      i++;
    } else {
      out += next;
      i++;
    }
  }
  return out;
}

/**
 * Accept `key=value`, `key = value`, and `key: value` (#807 separator hardening).
 * Values may contain escaped `\:` / `\=` (#807 m5).
 *
 * @param {string} line trimmed non-comment logical line
 * @returns {{ key: string, value: string } | null}
 */
export function parsePropertyLine(line) {
  let i = 0;
  while (i < line.length && /\s/.test(line[i])) {
    i++;
  }
  if (i >= line.length) {
    return null;
  }
  let key = '';
  for (; i < line.length; i++) {
    if (line[i] === '\\' && i + 1 < line.length) {
      key += line[i + 1];
      i++;
      continue;
    }
    if (line[i] === '=' || line[i] === ':' || /\s/.test(line[i])) {
      break;
    }
    key += line[i];
  }
  while (i < line.length && /\s/.test(line[i])) {
    i++;
  }
  if (i >= line.length || (line[i] !== '=' && line[i] !== ':')) {
    return null;
  }
  i++;
  while (i < line.length && /\s/.test(line[i])) {
    i++;
  }
  const rawValue = line.slice(i);
  return { key: key.trim(), value: unescapePropertyValue(rawValue) };
}
