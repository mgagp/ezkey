/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * SDK: EzkeyClient
 * Description: Main client for Ezkey JavaScript/TypeScript SDK providing unified access to admin and auth APIs
 */

import { EzkeyConfig, createDefaultConfig } from './config';
import { EzkeyAdminAPI } from './admin-api';
import { EzkeyAuthAPI } from './auth-api';

/**
 * Main client for Ezkey JavaScript/TypeScript SDK.
 * Provides unified access to both Admin API and Auth API operations
 * through a simple, easy-to-use interface.
 */
export class EzkeyClient {
  private readonly config: EzkeyConfig;
  private readonly adminAPI: EzkeyAdminAPI;
  private readonly authAPI: EzkeyAuthAPI;

  constructor(config?: EzkeyConfig) {
    this.config = config || createDefaultConfig();
    this.adminAPI = new EzkeyAdminAPI(this.config);
    this.authAPI = new EzkeyAuthAPI(this.config);
  }

  /**
   * Creates a new Ezkey client with default configuration.
   */
  static create(): EzkeyClient {
    return new EzkeyClient();
  }

  /**
   * Creates a new Ezkey client with custom API URLs.
   */
  static createWithUrls(adminApiUrl: string, authApiUrl: string): EzkeyClient {
    return new EzkeyClient({ adminApiUrl, authApiUrl });
  }

  /**
   * Gets the Admin API client for managing integrations, enrollments, and auth attempts.
   */
  admin(): EzkeyAdminAPI {
    return this.adminAPI;
  }

  /**
   * Gets the Auth API client for device enrollment and authentication flows.
   */
  auth(): EzkeyAuthAPI {
    return this.authAPI;
  }

  /**
   * Gets the current configuration.
   */
  getConfig(): EzkeyConfig {
    return this.config;
  }
}