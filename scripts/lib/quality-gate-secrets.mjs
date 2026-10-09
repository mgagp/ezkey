/**
 * Secret / PII detection for quality-gate container log scans.
 * Shared by stack-health-check.mjs and unit tests.
 */

/** Values already masked by product or prior redaction — never score as a hit. */
export const MASKED_VALUE_RE =
  /^(?:\*{2,}REDACTED\*{2,}|\*{3,}|\[REDACTED\]|<redacted>|xxx+|…+)$/i;

/** Non-secret placeholders after key= / key: (e.g. challenge: true boolean flag). */
export const NON_SECRET_VALUE_RE = /^(?:true|false|null|undefined|none|n\/a|-)$/i;

/** "proof token: ID=1" style — identifier label, not the token value. */
export const ID_LABEL_VALUE_RE = /^ID=\d+/i;

/**
 * @param {string} value raw captured value (may include trailing punctuation)
 * @returns {boolean}
 */
export function isMaskedOrNonSecretValue(value) {
  if (value == null) return true;
  let v = String(value).trim();
  // Strip wrapping quotes; then trailing punctuation from log formatting (true))
  // but keep bracketed tokens like [REDACTED] intact for the mask check.
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
 * Map known log message fragments → product source (for run reports).
 * Do not invent paths; only entries verified in source.
 */
export const PRODUCT_LOG_SOURCES = [
  {
    match: /Enrollment Proof Token:/i,
    logger: 'o.e.admin.service.AdminBootstrapService',
    path: 'ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminBootstrapService.java',
    lines: '508,538',
    kindHint: 'kv-secret',
  },
  {
    match: /Enrollment Challenge Code:/i,
    logger: 'o.e.admin.service.AdminBootstrapService',
    path: 'ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminBootstrapService.java',
    lines: '509',
    kindHint: 'kv-secret',
  },
  {
    match: /Global Admin Created:/i,
    logger: 'o.e.admin.service.AdminBootstrapService',
    path: 'ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminBootstrapService.java',
    lines: '500',
    kindHint: 'email',
  },
  {
    match: /Updated global admin:|Global admin (already exists|initialized)/i,
    logger: 'o.e.a.service.InitialGlobalAdminService',
    path: 'ezkey-admin-api/src/main/java/org/ezkey/admin/service/InitialGlobalAdminService.java',
    lines: '148',
    kindHint: 'email',
  },
  {
    match: /Passwordless with challenge: returning auth attempt info \(challenge:/i,
    logger: 'o.ezkey.admin.service.AdminAuthService',
    path: 'ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminAuthService.java',
    lines: '239',
    kindHint: 'kv-secret',
  },
  {
    match: /Passwordless auth attempt created \(ID:.*, challenge:/i,
    logger: 'o.ezkey.admin.service.AdminAuthService',
    path: 'ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminAuthService.java',
    lines: '218',
    kindHint: 'kv-secret',
    note: 'Logs boolean challengeRequested (true/false), not the challenge code — skip when value is boolean',
  },
];

/**
 * Detect secret/PII patterns in a raw container log line.
 *
 * @param {string} rawLine
 * @returns {{ kinds: string[], severity: 'RED'|'AMBER', valuePresent: boolean, matchedValues: string[] }}
 */
export function detectSecrets(rawLine) {
  const line = String(rawLine ?? '');
  const kinds = [];
  const matchedValues = [];
  let severity = null;

  // Email → low-PII AMBER (docker-dev bootstrap admin), pending Christophe
  const emailRe = /[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/g;
  let em;
  while ((em = emailRe.exec(line)) !== null) {
    if (!isMaskedOrNonSecretValue(em[0])) {
      kinds.push('email');
      matchedValues.push(em[0]);
      severity = severity === 'RED' ? 'RED' : 'AMBER';
    }
  }

  // JWT → RED
  const jwtRe = /\beyJ[\w-]+\.[\w-]+\.[\w-]+\b/g;
  let jm;
  while ((jm = jwtRe.exec(line)) !== null) {
    if (!isMaskedOrNonSecretValue(jm[0])) {
      kinds.push('jwt');
      matchedValues.push('<JWT>');
      severity = 'RED';
    }
  }

  // JSON "key":"value"
  const jsonRe =
    /"(password|token|secret|challenge|code|key|accessCode)"\s*:\s*"([^"]*)"/gi;
  let js;
  while ((js = jsonRe.exec(line)) !== null) {
    if (!isMaskedOrNonSecretValue(js[2])) {
      kinds.push('json-secret');
      matchedValues.push(js[2].slice(0, 12));
      severity = 'RED';
    }
  }

  // kv key=value / key: value
  const kvRe =
    /\b(password|token|secret|challenge|accessCode)\b\s*[=:]\s*(\S+)/gi;
  let kv;
  while ((kv = kvRe.exec(line)) !== null) {
    const val = kv[2];
    if (isMaskedOrNonSecretValue(val)) continue;
    kinds.push('kv-secret');
    matchedValues.push(val.slice(0, 16));
    severity = 'RED';
  }

  if (!kinds.length) {
    return { kinds: [], severity: null, valuePresent: false, matchedValues: [] };
  }
  return {
    kinds: [...new Set(kinds)],
    severity: severity || 'AMBER',
    valuePresent: severity === 'RED',
    matchedValues,
  };
}

/**
 * Attach product source hint when the message matches a known log statement.
 *
 * @param {string} rawLine
 * @returns {{ path: string, lines: string, logger: string, note?: string }|null}
 */
export function resolveProductSource(rawLine) {
  for (const entry of PRODUCT_LOG_SOURCES) {
    if (entry.match.test(rawLine)) {
      return {
        path: entry.path,
        lines: entry.lines,
        logger: entry.logger,
        note: entry.note,
      };
    }
  }
  // Fall back to Spring abbreviated logger in the line
  const m = rawLine.match(/\s([a-z](?:\.[a-zA-Z0-9]+){2,})\s+:\s/);
  return m ? { path: 'unknown', lines: '?', logger: m[1] } : null;
}

/**
 * Parse Spring logger from a typical Boot log line.
 *
 * @param {string} rawLine
 * @returns {string|null}
 */
export function extractLogger(rawLine) {
  // Spring Boot: `] o.e.admin.service.AdminBootstrapService  : message`
  const matches = [...String(rawLine).matchAll(/\]\s+([a-zA-Z][a-zA-Z0-9_.$]*)\s{2,}:\s/g)];
  if (!matches.length) return null;
  return matches[matches.length - 1][1];
}
