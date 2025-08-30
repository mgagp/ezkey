"use strict";
/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * CLI Component: OpenAPI Command
 * Description: Commands to refresh OpenAPI specifications for demo applications
 */
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.OpenApiCommand = void 0;
const commander_1 = require("commander");
const http_client_1 = require("../utils/http-client");
const chalk_1 = __importDefault(require("chalk"));
const fs = __importStar(require("fs"));
const path = __importStar(require("path"));
class OpenApiCommand {
    constructor(config) {
        this.config = config;
        this.httpClient = new http_client_1.HttpClient(config);
    }
    getCommand() {
        const cmd = new commander_1.Command('openapi')
            .description('OpenAPI specification management commands');
        cmd
            .command('refresh')
            .description('Refresh OpenAPI specifications for demo applications')
            .option('--all', 'Refresh all demo applications (default)')
            .option('--app', 'Refresh only demo-app-acme (admin API)')
            .option('--device', 'Refresh only demo-device (auth API)')
            .action(async (options) => {
            await this.refreshSpecs(options);
        });
        return cmd;
    }
    async refreshSpecs(options) {
        const projectRoot = this.findProjectRoot();
        if (!projectRoot) {
            console.error(chalk_1.default.red('Error: Could not find ezkey project root'));
            process.exit(1);
        }
        let refreshApp = options.app || options.all || (!options.app && !options.device);
        let refreshDevice = options.device || options.all || (!options.app && !options.device);
        if (refreshApp) {
            await this.refreshAdminSpec(projectRoot);
        }
        if (refreshDevice) {
            await this.refreshAuthSpec(projectRoot);
        }
        console.log(chalk_1.default.green('OpenAPI specification refresh completed'));
    }
    async refreshAdminSpec(projectRoot) {
        console.log(chalk_1.default.blue('Refreshing Admin API specification for demo-app-acme...'));
        const adminUrl = `${this.config.get('adminUrl')}/v3/api-docs`;
        const targetPath = path.join(projectRoot, 'ezkey-demo-app-acme', 'openapi-spec.json');
        await this.downloadAndSaveSpec(adminUrl, targetPath, 'Admin API');
    }
    async refreshAuthSpec(projectRoot) {
        console.log(chalk_1.default.blue('Refreshing Auth API specification for demo-device...'));
        const authUrl = `${this.config.get('authUrl')}/v3/api-docs`;
        const targetPath = path.join(projectRoot, 'ezkey-demo-device', 'openapi-spec.json');
        await this.downloadAndSaveSpec(authUrl, targetPath, 'Auth API');
    }
    async downloadAndSaveSpec(url, targetPath, apiName) {
        try {
            // Create backup of existing file
            if (fs.existsSync(targetPath)) {
                const backupPath = targetPath + '.backup';
                fs.copyFileSync(targetPath, backupPath);
                console.log(chalk_1.default.gray(`Created backup: ${path.basename(backupPath)}`));
            }
            // Download new specification
            console.log(chalk_1.default.gray(`Downloading from: ${url}`));
            const response = await this.httpClient.get(url);
            if (!response.success) {
                throw new Error(`Failed to download ${apiName} spec: ${response.error}`);
            }
            // Validate JSON
            if (!response.data || typeof response.data !== 'object') {
                throw new Error(`Invalid JSON response from ${apiName}`);
            }
            // Write new specification
            fs.writeFileSync(targetPath, JSON.stringify(response.data, null, 2), 'utf-8');
            console.log(chalk_1.default.green(`✓ Updated ${apiName} specification: ${path.basename(targetPath)}`));
            // Remove backup on success
            const backupPath = targetPath + '.backup';
            if (fs.existsSync(backupPath)) {
                fs.unlinkSync(backupPath);
            }
        }
        catch (error) {
            console.error(chalk_1.default.red(`✗ Failed to update ${apiName} specification:`), error instanceof Error ? error.message : String(error));
            // Restore backup if it exists
            const backupPath = targetPath + '.backup';
            if (fs.existsSync(backupPath)) {
                fs.copyFileSync(backupPath, targetPath);
                fs.unlinkSync(backupPath);
                console.log(chalk_1.default.yellow('Restored previous specification from backup'));
            }
            throw error;
        }
    }
    findProjectRoot() {
        let currentDir = process.cwd();
        // Look for characteristic project files
        while (currentDir !== path.dirname(currentDir)) {
            const pomFile = path.join(currentDir, 'pom.xml');
            const demoAppDir = path.join(currentDir, 'ezkey-demo-app-acme');
            const demoDeviceDir = path.join(currentDir, 'ezkey-demo-device');
            if (fs.existsSync(pomFile) && fs.existsSync(demoAppDir) && fs.existsSync(demoDeviceDir)) {
                return currentDir;
            }
            currentDir = path.dirname(currentDir);
        }
        return null;
    }
}
exports.OpenApiCommand = OpenApiCommand;
//# sourceMappingURL=openapi.js.map