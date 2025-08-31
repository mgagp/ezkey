"use strict";
/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * SDK: EzkeyClient
 * Description: Main client for Ezkey JavaScript/TypeScript SDK providing unified access to admin and auth APIs
 */
Object.defineProperty(exports, "__esModule", { value: true });
exports.EzkeyClient = void 0;
const config_1 = require("./config");
const admin_api_1 = require("./admin-api");
const auth_api_1 = require("./auth-api");
/**
 * Main client for Ezkey JavaScript/TypeScript SDK.
 * Provides unified access to both Admin API and Auth API operations
 * through a simple, easy-to-use interface.
 */
class EzkeyClient {
    constructor(config) {
        this.config = config || (0, config_1.createDefaultConfig)();
        this.adminAPI = new admin_api_1.EzkeyAdminAPI(this.config);
        this.authAPI = new auth_api_1.EzkeyAuthAPI(this.config);
    }
    /**
     * Creates a new Ezkey client with default configuration.
     */
    static create() {
        return new EzkeyClient();
    }
    /**
     * Creates a new Ezkey client with custom API URLs.
     */
    static createWithUrls(adminApiUrl, authApiUrl) {
        return new EzkeyClient({ adminApiUrl, authApiUrl });
    }
    /**
     * Gets the Admin API client for managing integrations, enrollments, and auth attempts.
     */
    admin() {
        return this.adminAPI;
    }
    /**
     * Gets the Auth API client for device enrollment and authentication flows.
     */
    auth() {
        return this.authAPI;
    }
    /**
     * Gets the current configuration.
     */
    getConfig() {
        return this.config;
    }
}
exports.EzkeyClient = EzkeyClient;
//# sourceMappingURL=client.js.map