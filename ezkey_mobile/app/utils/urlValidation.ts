/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: urlValidation
 * Description: Utilities for validating and normalizing Auth API URLs extracted from QR code payloads.
 * Security Context: Enforces HTTPS-only connections in production to prevent man-in-the-middle attacks.
 *                   HTTP is tolerated only for development loopback addresses (localhost, 10.0.2.2).
 * @since 2025
 */

/**
 * Hosts that are allowed to use plain HTTP during development.
 *
 * - `localhost` / `127.0.0.1`: Standard loopback for local development.
 * - `10.0.2.2`: Android emulator alias for the host machine.
 */
const DEV_HTTP_HOSTS = ['localhost', '127.0.0.1', '10.0.2.2'];

/**
 * Pattern that captures the scheme, host (with optional port), and optional path from a URL.
 *
 * Uses a simple regex approach instead of the WHATWG `URL` constructor to guarantee compatibility
 * with all React Native / Hermes runtime versions.
 */
const URL_PATTERN = /^(https?):\/\/([^/?#\s]+)(\/[^?#\s]*)?$/i;

const DEFAULT_HTTPS_PORT = '443';

type ParsedHost = {
  hostname: string;
  port?: string;
};

function parseHost(value: string): ParsedHost | undefined {
  const parts = value.split(':');
  if (parts.length === 1) {
    return {hostname: parts[0]};
  }
  if (parts.length !== 2 || !parts[1]) {
    return undefined;
  }

  return {
    hostname: parts[0],
    port: parts[1],
  };
}

function normalizePath(path: string | undefined): string | undefined {
  if (!path || path === '/') {
    return undefined;
  }

  const normalizedPath = path.replace(/\/+$/, '');
  return normalizedPath || undefined;
}

function normalizeHost(scheme: string, rawHost: string): string | undefined {
  const parsedHost = parseHost(rawHost);
  if (!parsedHost) {
    return undefined;
  }

  const hostname = parsedHost.hostname.toLowerCase();
  if (!hostname) {
    return undefined;
  }

  if (!parsedHost.port) {
    return hostname;
  }

  if (scheme === 'https' && parsedHost.port === DEFAULT_HTTPS_PORT) {
    return hostname;
  }

  return `${hostname}:${parsedHost.port}`;
}

/**
 * Validates that the given string is a well-formed Auth API URL.
 *
 * Rules:
 * 1. The value must match the standard `http(s)://host(:port)(/path)` pattern.
 * 2. The scheme must be `https` — except for development loopback hosts where `http` is tolerated.
 * 3. Trailing slashes are stripped for consistency.
 *
 * Uses regex-based parsing instead of `new URL()` to avoid Hermes runtime compatibility issues.
 *
 * @param value Raw URL string (e.g. from a QR code payload).
 * @return Normalized URL without a trailing slash, or `undefined` when the input is invalid.
 * @since 2025
 */
export const validateAuthUrl = (value: string | undefined | null): string | undefined => {
  if (!value || typeof value !== 'string') {
    return undefined;
  }

  const trimmed = value.trim();
  if (!trimmed) {
    return undefined;
  }

  const match = trimmed.match(URL_PATTERN);
  if (!match) {
    return undefined;
  }

  const scheme = match[1].toLowerCase(); // 'http' or 'https'
  const host = match[2];   // 'example.com' or 'example.com:8080'
  const path = match[3];   // '/some/path' or undefined

  const parsedHost = parseHost(host);
  if (!parsedHost) {
    return undefined;
  }

  const hostname = parsedHost.hostname.toLowerCase();

  const isHttps = scheme === 'https';
  const isDevHttp = scheme === 'http' && DEV_HTTP_HOSTS.includes(hostname);

  if (!isHttps && !isDevHttp) {
    return undefined;
  }

  const normalizedHost = normalizeHost(scheme, host);
  if (!normalizedHost) {
    return undefined;
  }

  // Rebuild normalized URL, stripping trailing slashes for consistent Axios baseURL usage.
  let normalized = `${scheme}://${normalizedHost}`;
  const normalizedPath = normalizePath(path);
  if (normalizedPath) {
    normalized += normalizedPath;
  }

  return normalized;
};

/**
 * Builds the canonical installation trust-zone identity from a raw Auth API URL.
 *
 * Product posture: one Ezkey installation = one normalized Auth URL. There is no
 * installation UUID. Branding labels never participate in this identity.
 * Equivalence classes: host case, trailing slash, implicit HTTPS port 443;
 * distinct non-empty paths remain distinct trust zones.
 *
 * @param value Raw Auth API URL.
 * @return Canonical installation identifier or undefined when invalid.
 * @since 2025
 */
export const normalizeInstallationId = (
  value: string | undefined | null,
): string | undefined => validateAuthUrl(value);
