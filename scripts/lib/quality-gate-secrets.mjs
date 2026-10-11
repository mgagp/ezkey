/**
 * Shared redaction + secret detection for quality-gate container log scans.
 * One pattern set drives both sanitize() and detectSecrets().
 *
 * Cloud agent tooling; Git Bash best-effort, untested on macOS.
 */

/** Values already masked by product or prior redaction — never score as a hit. */
export const MASKED_VALUE_RE =
  /^(?:\*{2,}REDACTED\*{2,}|\*{3,}|\[REDACTED\]|<redacted>|xxx+|…+)$/i;

/** Non-secret placeholders after key= / key: (e.g. challenge: true boolean flag). */
export const NON_SECRET_VALUE_RE = /^(?:true|false|null|undefined|none|n\/a|-)$/i;

/** Label-value form only — "proof token: ID=1", not ID=5abc. */
export const ID_LABEL_VALUE_RE = /^ID=\d+$/i;

/**
 * Long opaque tokens: mix of letters and digits, no dots, not CamelCase identifiers.
 * Excludes Java class/package names (DataIntegrityViolationException, org.springframework…).
 * Canonical UUIDs are masked before this rule runs (see applyRedactions).
 */
export const OPAQUE_TOKEN_RE = /\b(?=[A-Za-z0-9_-]*[A-Za-z])(?=[A-Za-z0-9_-]*\d)[A-Za-z0-9_-]{20,}\b/g;

/** Canonical UUID (8-4-4-4-12 hex). Masked before opaque-token so they are not RED. */
export const CANONICAL_UUID_SOURCE =
  String.raw`\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b`;

/**
 * Uname-style CPU/OS triples (Postgres "on x86_64-pc-linux-musl", …) — not secrets.
 *
 * @param {string} s
 * @returns {boolean}
 */
export function isArchTripleToken(s) {
  return /^(?:x86_64|amd64|aarch64|arm64|i[3-6]86)(?:-[A-Za-z0-9_]+)+$/.test(String(s || ''));
}

/**
 * Opaque lookalike that is a jar artifact stem / git SHA context — not a secret.
 *
 * @param {string} match opaque candidate
 * @param {string} before up to ~16 chars before match
 * @param {string} after up to ~16 chars after match
 * @returns {boolean}
 */
export function isJarGitArchOpaqueContext(match, before, after) {
  const b = String(before || '');
  const a = String(after || '');
  // /tmp/foo.jar · ~[spring-data-commons-4.1.1.jar!/:4.1.1]
  if (/^\.jar\b/.test(a) || /^\.\d+(?:\.\d+)+\.jar\b/.test(a)) return true;
  // git: <hexsha>
  if (/git:\s*$/i.test(b) && /^[0-9a-f]+$/i.test(match)) return true;
  if (isArchTripleToken(match)) return true;
  return false;
}

/** Known allowlist requires= tokens (typos must not make an entry unconditional). */
export const KNOWN_ALLOWLIST_REQUIRES = new Set(['COMPOSE_RETRY_FIRED']);

/** Credential key stem (proofToken, api_key, challenge code, passphrase, …). */
export const SECRET_KEY_STEM =
  String.raw`\w*(?:token|secret|password|passwd|pwd|passphrase|private[_-]?key|challenge\W*code|api[-_]?key|access[_-]?code|authorization|bearer|otp|pin)`;

/** Low-PII identity keys — AMBER, not credential RED. */
export const USERNAME_KEY_RE = /^(?:username|user)$/i;

/** Gate phase names valid in allowlist phase fields (plus `*`). */
export const KNOWN_ALLOWLIST_PHASES = new Set([
  '*',
  'preflight',
  'unit-tests',
  'clean-start',
  'post-clean-health',
  'functional-tests',
  'elective-tests',
  'playwright',
  'churn-init',
  'churn',
  'health',
]);

/**
 * @param {string} value
 * @returns {boolean}
 */
export function isMaskedOrNonSecretValue(value) {
  if (value == null) return true;
  let v = String(value).trim();
  v = v.replace(/^["']+|["']+$/g, '');
  // Mask check BEFORE stripping trailing punctuation — "***REDACTED***)," must stay masked.
  if (MASKED_VALUE_RE.test(v)) return true;
  v = v.replace(/[),;.]+$/g, '');
  if (!v) return true;
  if (MASKED_VALUE_RE.test(v)) return true;
  if (NON_SECRET_VALUE_RE.test(v)) return true;
  if (ID_LABEL_VALUE_RE.test(v)) return true;
  return false;
}

/** HTTP status after bare `code=` / `code:` — not a secret (e.g. code=200). */
export function isHttpStatusCodeKeyValue(key, value) {
  if (!/^code$/i.test(String(key || ''))) return false;
  const v = String(value || '')
    .trim()
    .replace(/[),;.]+$/g, '');
  return /^[1-5]\d{2}$/.test(v);
}

/**
 * Bare `challenge:` prose after #751 (e.g. "with challenge: returning auth attempt info").
 * Still scores numeric / opaque challenge codes (challenge: 42, challenge=ABCD1234).
 *
 * @param {string} key
 * @param {string} value
 * @returns {boolean}
 */
export function isChallengeProseValue(key, value) {
  if (!/^challenge$/i.test(String(key || ''))) return false;
  const v = String(value || '')
    .trim()
    .replace(/[),;.]+$/g, '');
  // Pure alphabetic tokens are English glue, not challenge codes.
  return /^[A-Za-z]+$/.test(v);
}

/**
 * Known log message → product logger.
 * Paths are best-effort hints only — do not invent line numbers or fabricate paths
 * that are not in the product tree.
 */
export const PRODUCT_LOG_SOURCES = [
  {
    match: /Enrollment Proof Token:/i,
    logger: 'o.e.admin.service.AdminBootstrapService',
    path: 'ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminBootstrapService.java',
    kindHint: 'kv-secret',
  },
  {
    match: /Enrollment Challenge Code:|Challenge Code:/i,
    logger: 'o.e.admin.service.AdminBootstrapService',
    path: 'ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminBootstrapService.java',
    kindHint: 'kv-secret',
  },
  {
    match: /RECOVERY CODES|^\s*\d+\.\s+\d{4}(?:-\d{4}){3,}/i,
    logger: 'o.e.admin.service.AdminBootstrapService',
    path: 'ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminBootstrapService.java',
    kindHint: 'recovery-code',
  },
  {
    match: /Global Admin Created:/i,
    logger: 'o.e.admin.service.AdminBootstrapService',
    path: 'ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminBootstrapService.java',
    kindHint: 'email',
  },
  {
    match: /Updated global admin:|Global admin (already exists|initialized)/i,
    logger: 'o.e.a.service.InitialGlobalAdminService',
    path: 'ezkey-admin-api/src/main/java/org/ezkey/admin/service/InitialGlobalAdminService.java',
    kindHint: 'email',
  },
  {
    match: /Passwordless with challenge: returning auth attempt info \(challenge:/i,
    logger: 'o.ezkey.admin.service.AdminAuthService',
    path: 'ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminAuthService.java',
    kindHint: 'kv-secret',
  },
  {
    match: /Passwordless auth attempt created \(ID:.*, challenge:/i,
    logger: 'o.ezkey.admin.service.AdminAuthService',
    path: 'ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminAuthService.java',
    kindHint: 'kv-secret',
    note: 'Logs boolean challengeRequested — skip when value is boolean',
  },
];

/**
 * Shared kv redaction for "key=val", key: val, and quoted values.
 *
 * @param {string} text
 * @param {(kind: string, severity: 'RED'|'AMBER', sample: string) => void} note
 * @returns {string}
 */
function redactKvForms(text, note) {
  const keys = `${SECRET_KEY_STEM}|challenge|code|username|user`;
  const apply = (out, re, wrap) =>
    out.replace(re, (full, key, val) => {
      if (isMaskedOrNonSecretValue(val)) return full;
      if (isHttpStatusCodeKeyValue(key, val)) return full;
      if (isChallengeProseValue(key, val)) return full;
      if (/^bearer$/i.test(val) || /^basic$/i.test(val)) return full;
      if (USERNAME_KEY_RE.test(key)) {
        note('kv-username', 'AMBER', val);
      } else {
        note('kv-secret', 'RED', val);
      }
      return wrap(key);
    });
  let out = text;
  out = apply(out, new RegExp(`\\b(${keys})\\b\\s*[=:]\\s*"([^"]*)"`, 'gi'), (k) => `${k}="***REDACTED***"`);
  out = apply(out, new RegExp(`\\b(${keys})\\b\\s*[=:]\\s*'([^']*)'`, 'gi'), (k) => `${k}='***REDACTED***'`);
  out = apply(out, new RegExp(`\\b(${keys})\\b\\s*[=:]\\s*(\\S+)`, 'gi'), (k) => `${k}=***REDACTED***`);
  return out;
}

/**
 * Apply the single redaction pattern set. Bearer / Basic run before kv.
 *
 * @param {string} rawLine
 * @returns {{ text: string, changes: Array<{ kind: string, severity: 'RED'|'AMBER', sample: string }> }}
 */
export function applyRedactions(rawLine) {
  let out = String(rawLine ?? '');
  /** @type {Array<{ kind: string, severity: 'RED'|'AMBER', sample: string }>} */
  const changes = [];

  const note = (kind, severity, sample) => {
    if (isMaskedOrNonSecretValue(sample)) return;
    changes.push({ kind, severity, sample: String(sample).slice(0, 24) });
  };

  // 1) Bearer first (before kv authorization=… eats the keyword)
  out = out.replace(/Bearer\s+([A-Za-z0-9._~+/=-]+)/gi, (full, tok) => {
    note('bearer', 'RED', tok);
    return 'Bearer ***REDACTED***';
  });

  // 1b) Authorization: Basic <base64> only (header form; case-sensitive — avoids
  // "Using basic configuration" / "HTTP Basic authentication is not configured").
  out = out.replace(/Authorization:\s*Basic\s+([A-Za-z0-9+/=]+)/g, (full, b64) => {
    note('basic', 'RED', b64);
    return full.replace(b64, '***REDACTED***');
  });

  // 2) Email → AMBER low-PII
  out = out.replace(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/g, (m) => {
    note('email', 'AMBER', m);
    return '<EMAIL>';
  });

  // 3) IPv4 / IPv6 → AMBER
  out = out.replace(
    /\b(?:(?:25[0-5]|2[0-4]\d|[01]?\d?\d)\.){3}(?:25[0-5]|2[0-4]\d|[01]?\d?\d)\b/g,
    (m) => {
      note('ip', 'AMBER', m);
      return '<IPV4>';
    },
  );
  out = out.replace(/\b(?:[0-9a-fA-F]{0,4}:){2,7}[0-9a-fA-F]{0,4}\b/g, (m) => {
    // HH:MM:SS timestamps are not IPv6 — require :: or a hex letter.
    if (!(m.includes('::') || /[a-fA-F]/.test(m))) return m;
    note('ip', 'AMBER', m);
    return '<IPV6>';
  });

  // 4) JWT
  out = out.replace(/\beyJ[\w-]+\.[\w-]+\.[\w-]+\b/g, (m) => {
    note('jwt', 'RED', m);
    return '<JWT>';
  });

  // 4b) Path token /t/<32 hex>
  out = out.replace(/\/t\/([a-fA-F0-9]{32})\b/g, (full, tok) => {
    note('path-token', 'RED', tok);
    return '/t/***REDACTED***';
  });

  // 4c) JSESSIONID cookie
  out = out.replace(/\bJSESSIONID\s*=\s*([^\s;]+)/gi, (full, val) => {
    if (isMaskedOrNonSecretValue(val)) return full;
    note('cookie', 'RED', val);
    return 'JSESSIONID=***REDACTED***';
  });

  // 5) JSON "key":"value" (compound keys + username AMBER)
  out = out.replace(
    new RegExp(
      `"(${SECRET_KEY_STEM}|challenge|code|key|username|user)"\\s*:\\s*"([^"]*)"`,
      'gi',
    ),
    (full, key, val) => {
      if (isMaskedOrNonSecretValue(val)) return full;
      if (isHttpStatusCodeKeyValue(key, val)) return full;
      if (USERNAME_KEY_RE.test(key)) {
        note('json-username', 'AMBER', val);
      } else {
        note('json-secret', 'RED', val);
      }
      return `"${key}":"***REDACTED***"`;
    },
  );

  // 6) CLI --…-token / --…-password "value with spaces" / =value
  out = out.replace(
    /(--[\w-]*(?:token|secret|password|passphrase|api[-_]?key|private[_-]?key|otp|pin)[\w-]*)(?:\s+"([^"]+)"|\s+'([^']+)'|=(\S+)|(?:\s+)(\S+))/gi,
    (full, flag, dq, sq, eq, bare) => {
      const val = dq || sq || eq || bare || '';
      if (isMaskedOrNonSecretValue(val)) return full;
      note('cli-secret', 'RED', val);
      if (dq != null) return `${flag} "***REDACTED***"`;
      if (sq != null) return `${flag} '***REDACTED***'`;
      if (eq != null) return `${flag}=***REDACTED***`;
      return `${flag} ***REDACTED***`;
    },
  );

  // 7) Header-style X-Api-Key: value
  out = out.replace(
    /\b(X-Api-Key|api[_-]?key)\b\s*[:=]\s*(\S+)/gi,
    (full, key, val) => {
      if (isMaskedOrNonSecretValue(val)) return full;
      note('header-secret', 'RED', val);
      return `${key}=***REDACTED***`;
    },
  );

  // 8) kv / labeled forms (quoted + bare) — shared helper
  out = redactKvForms(out, note);

  // Phrase forms: "Enrollment Proof Token: …", "Enrollment Challenge Code: N"
  out = out.replace(
    /\b(Enrollment\s+Proof\s+Token|Enrollment\s+Challenge\s+Code|Challenge\s+Code)\b\s*:\s*(\S+)/gi,
    (full, key, val) => {
      if (isMaskedOrNonSecretValue(val)) return full;
      note('kv-secret', 'RED', val);
      return `${key}=***REDACTED***`;
    },
  );

  // 9) Bootstrap recovery codes (digit groups) — stay RED
  out = out.replace(/\b(\d{4}(?:-\d{4}){7})\b/g, (m) => {
    note('recovery-code', 'RED', m);
    return '***REDACTED***';
  });

  // 9b) Canonical UUIDs → <UUID> before opaque-token (authAttemptId etc. are not secrets)
  out = out.replace(new RegExp(CANONICAL_UUID_SOURCE, 'gi'), '<UUID>');

  // 10) Opaque long tokens (letters+digits, not CamelCase / dotted packages).
  // Skip jar stems, git: hex SHAs, and uname arch triples (scanner false positives).
  out = out.replace(OPAQUE_TOKEN_RE, (m, offset, whole) => {
    if (MASKED_VALUE_RE.test(m)) return m;
    if (m === '<UUID>') return m;
    const before = String(whole).slice(Math.max(0, offset - 16), offset);
    const after = String(whole).slice(offset + m.length, offset + m.length + 16);
    if (isJarGitArchOpaqueContext(m, before, after)) return m;
    note('opaque-token', 'RED', m);
    return '<TOKEN>';
  });

  return { text: out, changes };
}

/**
 * Sanitize a line for reports / signatures (same patterns as detectSecrets).
 *
 * @param {string} rawLine
 * @returns {string}
 */
export function sanitize(rawLine) {
  return applyRedactions(rawLine).text;
}

/**
 * Detect secret/PII: RED when redaction changes the line (except masked/boolean).
 *
 * @param {string} rawLine
 * @returns {{ kinds: string[], severity: 'RED'|'AMBER'|null, valuePresent: boolean, matchedValues: string[] }}
 */
export function detectSecrets(rawLine) {
  const { text, changes } = applyRedactions(rawLine);
  if (text === String(rawLine ?? '') || !changes.length) {
    return { kinds: [], severity: null, valuePresent: false, matchedValues: [] };
  }
  const kinds = [...new Set(changes.map((c) => c.kind))];
  const hasRed = changes.some((c) => c.severity === 'RED');
  return {
    kinds,
    severity: hasRed ? 'RED' : 'AMBER',
    valuePresent: hasRed,
    matchedValues: changes.map((c) => c.sample),
  };
}

/**
 * @param {string} rawLine
 * @returns {{ path: string, lines: string, logger: string, note?: string }|null}
 */
export function resolveProductSource(rawLine) {
  for (const entry of PRODUCT_LOG_SOURCES) {
    if (entry.match.test(rawLine)) {
      return {
        path: entry.path,
        lines: '',
        logger: entry.logger,
        note: entry.note,
      };
    }
  }
  const m = rawLine.match(/\s([a-z](?:\.[a-zA-Z0-9]+){2,})\s+:\s/);
  return m ? { path: 'unknown', lines: '', logger: m[1] } : null;
}

/**
 * @param {string} rawLine
 * @returns {string|null}
 */
export function extractLogger(rawLine) {
  const matches = [
    ...String(rawLine).matchAll(/\]\s+([a-zA-Z][a-zA-Z0-9_.$]*)\s{2,}:\s/g),
  ];
  if (!matches.length) return null;
  return matches[matches.length - 1][1];
}

/**
 * Signature for allowlist matching / dedup (timestamps, threads, uuids collapsed).
 *
 * @param {string} line
 * @returns {string}
 */
export function signature(line) {
  let s = sanitize(line.trim());
  s = s.replace(/\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}:\d{2}(?:\.\d+)?Z?/g, '<TS>');
  s = s.replace(/\b\d{2}:\d{2}:\d{2}(?:\.\d+)?\b/g, '<TIME>');
  // Collapse Spring thread names so one statement → one finding (not one per exec-N).
  // Include left-truncated forms (e.g. [io-9080-exec-10] from [nio-9080-exec-10]).
  s = s.replace(
    /\[\s*(?:main|scheduling-\d+|Thread-\d+|task-\d+|[^\]]*exec-\d+)\s*\]/gi,
    '[THREAD]',
  );
  // UUIDs already become <UUID> in sanitize; keep a pass for already-sanitized inputs.
  s = s.replace(new RegExp(CANONICAL_UUID_SOURCE, 'gi'), '<UUID>');
  s = s.replace(/\b\d{5,}\b/g, '<N>');
  s = s.replace(/\s+/g, ' ');
  return s.slice(0, 240);
}

/**
 * Validate allowlist regex: must be full ^…$ and must not use ^.* pseudo-anchors.
 *
 * @param {string} pattern
 * @returns {void}
 * @throws {Error}
 */
export function assertAllowlistRegexAnchored(pattern) {
  if (!pattern.startsWith('^') || !pattern.endsWith('$')) {
    throw new Error(
      `Allowlist regex must be fully anchored with ^…$ (got: ${pattern})`,
    );
  }
  if (/^\^\.\*/.test(pattern)) {
    throw new Error(
      `Allowlist regex rejects ^.* pseudo-anchors; anchor to a real prefix (got: ${pattern})`,
    );
  }
}

/**
 * Reject unknown allowlist phase names (silent inert entries are a footgun).
 *
 * @param {string} phase
 * @returns {void}
 * @throws {Error}
 */
export function assertKnownAllowlistPhase(phase) {
  if (!KNOWN_ALLOWLIST_PHASES.has(phase)) {
    throw new Error(
      `Allowlist unknown phase "${phase}" (blocking). Known: ${[...KNOWN_ALLOWLIST_PHASES].join(', ')}`,
    );
  }
}

/**
 * Reject unknown requires= tokens (a typo must not make the entry unconditional).
 *
 * @param {string|null|undefined} requiresField comma-separated requires= value
 * @returns {void}
 * @throws {Error}
 */
export function assertKnownAllowlistRequires(requiresField) {
  if (requiresField == null || requiresField === '') return;
  for (const req of String(requiresField)
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)) {
    if (!KNOWN_ALLOWLIST_REQUIRES.has(req)) {
      throw new Error(
        `Allowlist unknown requires="${req}" (blocking). Known: ${[...KNOWN_ALLOWLIST_REQUIRES].join(', ')}`,
      );
    }
  }
}

/** docker logs --timestamps prefix: 2026-10-09T20:38:19.219626050Z<space> */
const DOCKER_TS_PREFIX_RE =
  /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z)\s+(.*)$/s;

/** Spring Boot ISO timestamp at line start (after optional docker prefix). */
const SPRING_TS_RE =
  /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z)\b/;

/**
 * Strip `docker logs --timestamps` prefix; return body + parsed emission time.
 *
 * @param {string} rawLine
 * @returns {{ body: string, emittedAt: Date|null }}
 */
export function splitDockerLogLine(rawLine) {
  const line = String(rawLine ?? '');
  const m = DOCKER_TS_PREFIX_RE.exec(line);
  if (m) {
    const emittedAt = parseIsoDate(m[1]);
    return { body: m[2], emittedAt };
  }
  const spring = SPRING_TS_RE.exec(line.trim());
  if (spring) {
    return { body: line, emittedAt: parseIsoDate(spring[1]) };
  }
  return { body: line, emittedAt: null };
}

/**
 * Emission timestamp for allowlist windows: docker prefix, else Spring ISO in body.
 *
 * @param {string} rawLine
 * @returns {Date|null}
 */
export function parseLogTimestamp(rawLine) {
  return splitDockerLogLine(rawLine).emittedAt;
}

/**
 * @param {string} iso
 * @returns {Date|null}
 */
export function parseIsoDate(iso) {
  if (!iso) return null;
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? null : d;
}

/**
 * Load phase-windows.tsv: phase\\tstart_iso\\tend_iso
 *
 * @param {string} text
 * @returns {Map<string, { start: Date, end: Date }>}
 */
export function parsePhaseWindows(text) {
  /** @type {Map<string, { start: Date, end: Date }>} */
  const map = new Map();
  for (const line of String(text ?? '').split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const [phase, startIso, endIso] = trimmed.split('\t').map((s) => s.trim());
    if (!phase || !startIso || !endIso) continue;
    const start = parseIsoDate(startIso);
    const end = parseIsoDate(endIso);
    if (!start || !end) continue;
    map.set(phase, { start, end });
  }
  return map;
}

/**
 * True when emittedAt falls inside the union of named phase windows.
 * Phase `*` always matches. Without a windows map, scoped phases fail closed.
 *
 * @param {Date|null|undefined} emittedAt
 * @param {string[]} phaseNames
 * @param {Map<string, { start: Date, end: Date }>|null|undefined} windows
 * @returns {boolean}
 */
export function isEmittedInPhaseWindows(emittedAt, phaseNames, windows) {
  const phases = phaseNames || [];
  if (!phases.length) return false;
  if (phases.includes('*')) return true;
  if (!windows || windows.size === 0) {
    return false;
  }
  if (!emittedAt || Number.isNaN(emittedAt.getTime())) return false;
  const t = emittedAt.getTime();
  for (const name of phases) {
    const w = windows.get(name);
    if (!w) continue;
    if (t >= w.start.getTime() && t <= w.end.getTime()) return true;
  }
  return false;
}

/**
 * @param {string} entryService
 * @param {string} serviceName
 * @returns {boolean}
 */
export function serviceNameMatches(entryService, serviceName) {
  if (entryService === '*') return true;
  const svcPat = entryService.endsWith('*')
    ? entryService.slice(0, -1)
    : entryService;
  return String(serviceName || '').includes(svcPat);
}

/**
 * #750 bootstrap enrollment channel — allowlist by emission-time phase window.
 * Entries must declare kind=secret-channel. Recovery codes never match.
 *
 * @param {{ kinds?: string[], logger?: string|null, service?: string, preview?: string, emittedAt?: Date|null }} hit
 * @param {Array<{ hygieneBad?: boolean, re?: RegExp|null, kind?: string|null, service: string, phases: string[] }>} allowlist
 * @param {Map<string, { start: Date, end: Date }>|null|undefined} windows
 * @returns {boolean}
 */
export function isSecretChannelAllowlisted(hit, allowlist, windows) {
  if ((hit.kinds || []).includes('recovery-code')) return false;
  const logger = hit.logger || '';
  if (!/AdminBootstrapService/i.test(logger)) return false;
  const emittedAt =
    hit.emittedAt instanceof Date
      ? hit.emittedAt
      : parseIsoDate(hit.emittedAt == null ? '' : String(hit.emittedAt));
  // Subject starts with logger so allowlist regex can ^anchor without ^.*
  const subject = `${logger} ${signature(hit.preview || '')}`;
  for (const entry of allowlist || []) {
    if (entry.hygieneBad || !entry.re || entry.kind !== 'secret-channel') continue;
    if (!serviceNameMatches(entry.service, hit.service || '')) continue;
    if (!isEmittedInPhaseWindows(emittedAt, entry.phases, windows)) {
      continue;
    }
    if (entry.re.test(subject)) return true;
  }
  return false;
}
