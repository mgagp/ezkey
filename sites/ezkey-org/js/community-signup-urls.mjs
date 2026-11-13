/**
 * URL allowlisting for community-signup pages (EN/FR).
 *
 * Hardens CodeQL js/client-side-url-redirect (#114, #115): query `?api=` and
 * response fields adminUiUrl / guidedTourUrl must not honor arbitrary hosts or
 * javascript: schemes.
 *
 * Decision: code-scanning triage Lot B, approved Marc 2026-10-13 19:40.
 * Review m1 (PR #804): return normalized URL.href; reject userinfo.
 */

/** Hosts allowed for the `?api=` override (Admin API base). */
export const API_HOST_ALLOWLIST = Object.freeze([
  'admin-api.ezkey.online',
  'localhost',
  '127.0.0.1',
]);

/**
 * Hosts allowed for post-signup navigation links (adminUiUrl, guidedTourUrl).
 * Includes marketing/UI hosts returned by the live community Admin API; the
 * `?api=` list alone would reject legitimate https://admin-ui.ezkey.online and
 * https://ezkey.org/… values.
 */
export const LINK_HOST_ALLOWLIST = Object.freeze([
  'admin-ui.ezkey.online',
  'admin-api.ezkey.online',
  'ezkey.org',
  'www.ezkey.org',
  'localhost',
  '127.0.0.1',
]);

const DEFAULT_API_URL =
  'https://admin-api.ezkey.online/api/v1/public/evaluator-signup';

/** Control characters that must never appear in a candidate URL string. */
const FORBIDDEN_RAW_CHARS = /[\u0000\u0009\u000a\u000d]/;

/** Percent-encoded NUL / tab / LF / CR (case-insensitive). */
const FORBIDDEN_PERCENT_CONTROLS = /%(?:00|09|0a|0d)/i;

/**
 * @param {string} value
 * @returns {boolean}
 */
function containsForbiddenControls(value) {
  if (FORBIDDEN_RAW_CHARS.test(value) || FORBIDDEN_PERCENT_CONTROLS.test(value)) {
    return true;
  }
  try {
    return FORBIDDEN_RAW_CHARS.test(decodeURIComponent(value));
  } catch {
    return true;
  }
}

/**
 * @param {string} hostname
 * @returns {string}
 */
function normalizeHostname(hostname) {
  return String(hostname || '')
    .toLowerCase()
    .replace(/\.+$/, '');
}

/**
 * @param {string} hostname
 * @returns {boolean}
 */
function isLoopbackHost(hostname) {
  const h = normalizeHostname(hostname);
  return h === 'localhost' || h === '127.0.0.1' || h === '::1';
}

/**
 * @param {string} hostname
 * @param {readonly string[]} allowlist
 * @returns {boolean}
 */
function hostAllowed(hostname, allowlist) {
  const h = normalizeHostname(hostname);
  return allowlist.some((entry) => normalizeHostname(entry) === h);
}

/**
 * Rebuilds a URL without userinfo, with a normalized hostname (lowercase, no
 * trailing dots). Returns null when the host is empty after normalization.
 *
 * @param {URL} url
 * @returns {URL | null}
 */
function toNormalizedUrl(url) {
  const host = normalizeHostname(url.hostname);
  if (!host) {
    return null;
  }
  const protocol = url.protocol.toLowerCase();
  const hostPart = host.includes(':') ? `[${host}]` : host;
  const portPart = url.port ? `:${url.port}` : '';
  const path = url.pathname && url.pathname.length > 0 ? url.pathname : '/';
  try {
    return new URL(`${protocol}//${hostPart}${portPart}${path}${url.search}${url.hash}`);
  } catch {
    return null;
  }
}

/**
 * Parses and validates a candidate HTTP(S) URL against an allowlist.
 * Rejects userinfo, control characters, and non-allowlisted hosts.
 *
 * @param {string | null | undefined} raw
 * @param {readonly string[]} allowlist
 * @param {{ allowHttpLoopback?: boolean }} [options]
 * @returns {URL | null} normalized URL, or null when rejected
 */
export function parseAllowedHttpUrl(raw, allowlist, options = {}) {
  const allowHttpLoopback = options.allowHttpLoopback !== false;
  if (raw == null || typeof raw !== 'string') {
    return null;
  }
  if (containsForbiddenControls(raw)) {
    return null;
  }
  const trimmed = raw.trim();
  if (!trimmed || containsForbiddenControls(trimmed)) {
    return null;
  }
  let url;
  try {
    url = new URL(trimmed);
  } catch {
    return null;
  }
  if (url.username !== '' || url.password !== '') {
    return null;
  }
  if (
    containsForbiddenControls(url.href) ||
    containsForbiddenControls(url.pathname + url.search + url.hash)
  ) {
    return null;
  }
  const normalized = toNormalizedUrl(url);
  if (!normalized) {
    return null;
  }
  const protocol = normalized.protocol.toLowerCase();
  if (protocol === 'https:') {
    return hostAllowed(normalized.hostname, allowlist) ? normalized : null;
  }
  if (protocol === 'http:' && allowHttpLoopback && isLoopbackHost(normalized.hostname)) {
    return hostAllowed(normalized.hostname, allowlist) ? normalized : null;
  }
  return null;
}

/**
 * True when protocol/host are acceptable for a given allowlist.
 * Rejects javascript:, data:, userinfo, and non-allowlisted hosts.
 *
 * @param {string} raw
 * @param {readonly string[]} allowlist
 * @param {{ allowHttpLoopback?: boolean }} [options]
 * @returns {boolean}
 */
export function isAllowedHttpUrl(raw, allowlist, options = {}) {
  return parseAllowedHttpUrl(raw, allowlist, options) != null;
}

/**
 * Resolves the evaluator-signup API URL from an optional `?api=` base.
 * Non-allowlisted or invalid values fall back to the production default.
 * Allowed bases are returned as a normalized absolute URL (no userinfo).
 *
 * @param {string | null | undefined} apiParam raw `?api=` value (base URL)
 * @param {string} [defaultApiUrl]
 * @returns {string}
 */
export function resolveEvaluatorSignupApiUrl(apiParam, defaultApiUrl = DEFAULT_API_URL) {
  if (apiParam == null || String(apiParam).trim() === '') {
    return defaultApiUrl;
  }
  const url = parseAllowedHttpUrl(String(apiParam), API_HOST_ALLOWLIST);
  if (!url) {
    return defaultApiUrl;
  }
  const path = url.pathname.replace(/\/+$/, '');
  return `${url.origin}${path}/api/v1/public/evaluator-signup`;
}

/**
 * Returns the candidate navigation URL only when it passes the link allowlist;
 * otherwise returns the default (so javascript: and foreign hosts are ignored).
 * Allowed candidates are returned as normalized {@link URL#href}.
 *
 * @param {string | null | undefined} candidate
 * @param {string} defaultUrl
 * @returns {string}
 */
export function resolveNavigationUrl(candidate, defaultUrl) {
  if (candidate == null || String(candidate).trim() === '') {
    return defaultUrl;
  }
  const url = parseAllowedHttpUrl(String(candidate), LINK_HOST_ALLOWLIST);
  if (!url) {
    return defaultUrl;
  }
  return url.href;
}

export { DEFAULT_API_URL };
