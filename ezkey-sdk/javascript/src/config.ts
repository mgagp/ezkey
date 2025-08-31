/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * SDK: EzkeyConfig
 * Description: Configuration class for Ezkey JavaScript/TypeScript SDK
 */

/**
 * Configuration interface for Ezkey JavaScript/TypeScript SDK.
 * Contains the configuration needed to connect to Ezkey APIs.
 */
export interface EzkeyConfig {
  /** Base URL for the Admin API (typically port 9080) */
  adminApiUrl: string;
  /** Base URL for the Auth API (typically port 8080) */
  authApiUrl: string;
}

/**
 * Creates a default configuration with localhost URLs.
 * @returns Configuration with default URLs
 */
export function createDefaultConfig(): EzkeyConfig {
  return {
    adminApiUrl: 'http://localhost:9080',
    authApiUrl: 'http://localhost:8080'
  };
}