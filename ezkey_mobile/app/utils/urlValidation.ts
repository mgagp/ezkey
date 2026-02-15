/*
 * Ezkey - Open Source MFA/Passkey Alternative
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
const URL_PATTERN = /^(https?):\/\/([^/?#\s]+)(\/[^?#\s]*)?$/;

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

  const scheme = match[1]; // 'http' or 'https'
  const host = match[2];   // 'example.com' or 'example.com:8080'
  const path = match[3];   // '/some/path' or undefined

  const hostname = host.split(':')[0];

  const isHttps = scheme === 'https';
  const isDevHttp = scheme === 'http' && DEV_HTTP_HOSTS.includes(hostname);

  if (!isHttps && !isDevHttp) {
    return undefined;
  }

  // Rebuild normalized URL, stripping trailing slashes for consistent Axios baseURL usage.
  let normalized = `${scheme}://${host}`;
  if (path && path !== '/') {
    normalized += path.replace(/\/+$/, '');
  }

  return normalized;
};
