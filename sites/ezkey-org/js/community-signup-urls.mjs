/**
 * URL allowlisting for community-signup pages (EN/FR).
 *
 * Hardens CodeQL js/client-side-url-redirect (#114, #115): query `?api=` and
 * response fields adminUiUrl / guidedTourUrl must not honor arbitrary hosts or
 * javascript: schemes.
 *
 * Decision: code-scanning triage Lot B, approved Marc 2026-10-13 19:40 (#TBD issue).
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

/**
 * @param {string} hostname
 * @returns {boolean}
 */
function isLoopbackHost(hostname) {
  const h = String(hostname || '').toLowerCase();
  return h === 'localhost' || h === '127.0.0.1' || h === '[::1]' || h === '::1';
}

/**
 * @param {string} hostname
 * @param {readonly string[]} allowlist
 * @returns {boolean}
 */
function hostAllowed(hostname, allowlist) {
  const h = String(hostname || '').toLowerCase();
  return allowlist.some((entry) => entry.toLowerCase() === h);
}

/**
 * True when protocol/host are acceptable for a given allowlist.
 * Rejects javascript:, data:, and non-allowlisted hosts.
 *
 * @param {string} raw
 * @param {readonly string[]} allowlist
 * @param {{ allowHttpLoopback?: boolean }} [options]
 * @returns {boolean}
 */
export function isAllowedHttpUrl(raw, allowlist, options = {}) {
  const allowHttpLoopback = options.allowHttpLoopback !== false;
  if (raw == null || typeof raw !== 'string') {
    return false;
  }
  const trimmed = raw.trim();
  if (!trimmed) {
    return false;
  }
  let url;
  try {
    url = new URL(trimmed);
  } catch {
    return false;
  }
  const protocol = url.protocol.toLowerCase();
  if (protocol === 'https:') {
    return hostAllowed(url.hostname, allowlist);
  }
  if (protocol === 'http:' && allowHttpLoopback && isLoopbackHost(url.hostname)) {
    return hostAllowed(url.hostname, allowlist);
  }
  return false;
}

/**
 * Resolves the evaluator-signup API URL from an optional `?api=` base.
 * Non-allowlisted or invalid values fall back to the production default.
 *
 * @param {string | null | undefined} apiParam raw `?api=` value (base URL)
 * @param {string} [defaultApiUrl]
 * @returns {string}
 */
export function resolveEvaluatorSignupApiUrl(apiParam, defaultApiUrl = DEFAULT_API_URL) {
  if (apiParam == null || String(apiParam).trim() === '') {
    return defaultApiUrl;
  }
  const base = String(apiParam).trim().replace(/\/+$/, '');
  if (!isAllowedHttpUrl(base, API_HOST_ALLOWLIST)) {
    return defaultApiUrl;
  }
  return base + '/api/v1/public/evaluator-signup';
}

/**
 * Returns the candidate navigation URL only when it passes the link allowlist;
 * otherwise returns the default (so javascript: and foreign hosts are ignored).
 *
 * @param {string | null | undefined} candidate
 * @param {string} defaultUrl
 * @returns {string}
 */
export function resolveNavigationUrl(candidate, defaultUrl) {
  if (candidate == null || String(candidate).trim() === '') {
    return defaultUrl;
  }
  if (!isAllowedHttpUrl(String(candidate), LINK_HOST_ALLOWLIST)) {
    return defaultUrl;
  }
  return String(candidate).trim();
}

export { DEFAULT_API_URL };
