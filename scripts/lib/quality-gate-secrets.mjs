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
 */
export const OPAQUE_TOKEN_RE = /\b(?=[A-Za-z0-9_-]*[A-Za-z])(?=[A-Za-z0-9_-]*\d)[A-Za-z0-9_-]{20,}\b/g;

/** Compound secret key stem (proofToken, api_key, challenge code, …). */
export const SECRET_KEY_STEM =
  String.raw`\w*(?:token|secret|password|passwd|pwd|challenge\W*code|api[-_]?key|access[_-]?code|authorization|bearer)`;

/**
 * @param {string} value
 * @returns {boolean}
 */
export function isMaskedOrNonSecretValue(value) {
  if (value == null) return true;
  let v = String(value).trim();
  v = v.replace(/^["']+|["']+$/g, '');
  if (MASKED_VALUE_RE.test(v)) return true;
  v = v.replace(/[),;.]+$/g, '');
  if (!v) return true;
  if (MASKED_VALUE_RE.test(v)) return true;
  if (NON_SECRET_VALUE_RE.test(v)) return true;
  if (ID_LABEL_VALUE_RE.test(v)) return true;
  return false;
}

/**
 * Known log message → product logger (path only; no hard-coded line numbers).
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
 * Apply the single redaction pattern set. Bearer runs first.
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

  // 2) Email → AMBER low-PII
  out = out.replace(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/g, (m) => {
    note('email', 'AMBER', m);
    return '<EMAIL>';
  });

  // 3) IPv4 / IPv6 → AMBER (redacted for signatures; low-PII, not credential RED)
  out = out.replace(
    /\b(?:(?:25[0-5]|2[0-4]\d|[01]?\d?\d)\.){3}(?:25[0-5]|2[0-4]\d|[01]?\d?\d)\b/g,
    (m) => {
      note('ip', 'AMBER', m);
      return '<IPV4>';
    },
  );
  out = out.replace(/\b(?:[0-9a-fA-F]{0,4}:){2,7}[0-9a-fA-F]{0,4}\b/g, (m) => {
    if (!(m.includes('::') || /[a-fA-F]/.test(m))) return m;
    note('ip', 'AMBER', m);
    return '<IPV6>';
  });

  // 4) JWT
  out = out.replace(/\beyJ[\w-]+\.[\w-]+\.[\w-]+\b/g, (m) => {
    note('jwt', 'RED', m);
    return '<JWT>';
  });

  // 5) JSON "key":"value" (compound keys)
  out = out.replace(
    new RegExp(`"(${SECRET_KEY_STEM}|challenge|code|key|username|user)"\\s*:\\s*"([^"]*)"`, 'gi'),
    (full, key, val) => {
      if (isMaskedOrNonSecretValue(val)) return full;
      note('json-secret', 'RED', val);
      return `"${key}":"***REDACTED***"`;
    },
  );

  // 6) CLI --…-token "value" / --…-token=value
  out = out.replace(
    /(--[\w-]*(?:token|secret|password|api[-_]?key)[\w-]*)(?:\s+"([^"]+)"|\s+'([^']+)'|=(\S+)|(?:\s+)(\S+))/gi,
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

  // 8) kv / labeled: compound keys, challenge code, Enrollment Proof Token, etc.
  // Skip when value already masked (e.g. after Bearer rule) so Authorization: Bearer *** stays.
  out = out.replace(
    new RegExp(
      `\\b(${SECRET_KEY_STEM}|challenge|code|username|user)\\b\\s*[=:]\\s*(\\S+)`,
      'gi',
    ),
    (full, key, val) => {
      if (isMaskedOrNonSecretValue(val)) return full;
      // Do not re-eat "Authorization: Bearer …" — Bearer rule already handled it.
      if (/^bearer$/i.test(val)) return full;
      note('kv-secret', 'RED', val);
      return `${key}=***REDACTED***`;
    },
  );

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

  // 10) Opaque long tokens (letters+digits, not CamelCase / dotted packages)
  out = out.replace(OPAQUE_TOKEN_RE, (m) => {
    if (MASKED_VALUE_RE.test(m)) return m;
    // Skip pure CamelCase-looking tokens without digit? OPAQUE already requires a digit.
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
 * Signature for allowlist matching (timestamps / uuids collapsed after sanitize).
 *
 * @param {string} line
 * @returns {string}
 */
export function signature(line) {
  let s = sanitize(line.trim());
  s = s.replace(/\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}:\d{2}(?:\.\d+)?Z?/g, '<TS>');
  s = s.replace(/\b\d{2}:\d{2}:\d{2}(?:\.\d+)?\b/g, '<TIME>');
  s = s.replace(
    /\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b/gi,
    '<UUID>',
  );
  s = s.replace(/\b\d{5,}\b/g, '<N>');
  s = s.replace(/\s+/g, ' ');
  return s.slice(0, 240);
}
