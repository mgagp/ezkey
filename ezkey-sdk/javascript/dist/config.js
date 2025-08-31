"use strict";
/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * SDK: EzkeyConfig
 * Description: Configuration class for Ezkey JavaScript/TypeScript SDK
 */
Object.defineProperty(exports, "__esModule", { value: true });
exports.createDefaultConfig = createDefaultConfig;
/**
 * Creates a default configuration with localhost URLs.
 * @returns Configuration with default URLs
 */
function createDefaultConfig() {
    return {
        adminApiUrl: 'http://localhost:9080',
        authApiUrl: 'http://localhost:8080'
    };
}
//# sourceMappingURL=config.js.map