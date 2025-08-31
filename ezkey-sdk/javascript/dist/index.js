"use strict";
/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * SDK: Main exports
 * Description: Main entry point for Ezkey JavaScript/TypeScript SDK
 */
Object.defineProperty(exports, "__esModule", { value: true });
exports.EzkeyException = exports.createDefaultConfig = exports.EzkeyAuthAPI = exports.EzkeyAdminAPI = exports.EzkeyClient = void 0;
// Main client
var client_1 = require("./client");
Object.defineProperty(exports, "EzkeyClient", { enumerable: true, get: function () { return client_1.EzkeyClient; } });
// API wrappers
var admin_api_1 = require("./admin-api");
Object.defineProperty(exports, "EzkeyAdminAPI", { enumerable: true, get: function () { return admin_api_1.EzkeyAdminAPI; } });
var auth_api_1 = require("./auth-api");
Object.defineProperty(exports, "EzkeyAuthAPI", { enumerable: true, get: function () { return auth_api_1.EzkeyAuthAPI; } });
// Configuration
var config_1 = require("./config");
Object.defineProperty(exports, "createDefaultConfig", { enumerable: true, get: function () { return config_1.createDefaultConfig; } });
// Exception handling
var exception_1 = require("./exception");
Object.defineProperty(exports, "EzkeyException", { enumerable: true, get: function () { return exception_1.EzkeyException; } });
//# sourceMappingURL=index.js.map