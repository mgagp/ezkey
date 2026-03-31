/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: urlValidation
 * Description: Utilities for validating and normalizing Auth API URLs extracted from QR code payloads.
 * @since 2025
 */

const DEV_HTTP_HOSTS = ['localhost', '127.0.0.1', '10.0.2.2'];
const URL_PATTERN = /^(https?):\/\/([^/?#\s]+)(\/[^?#\s]*)?$/;

/**
 * Validates that the given string is a well-formed Auth API URL.
 * HTTPS required except for development loopback hosts.
 *
 * @param value Raw URL string (e.g. from a QR code payload).
 * @return Normalized URL without a trailing slash, or `undefined` when invalid.
 */
export const validateAuthUrl = (value: string | undefined | null): string | undefined => {
  if (!value || typeof value !== 'string') {
    return undefined;
  }
  const trimmed = value.trim();
  if (!trimmed) return undefined;
  const match = trimmed.match(URL_PATTERN);
  if (!match) return undefined;
  const scheme = match[1];
  const host = match[2];
  const path = match[3];
  const hostname = host.split(':')[0];
  const isHttps = scheme === 'https';
  const isDevHttp = scheme === 'http' && DEV_HTTP_HOSTS.includes(hostname);
  if (!isHttps && !isDevHttp) return undefined;
  let normalized = `${scheme}://${host}`;
  if (path && path !== '/') {
    normalized += path.replace(/\/+$/, '');
  }
  return normalized;
};
