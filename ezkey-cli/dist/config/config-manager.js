"use strict";
/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * CLI Component: Configuration Manager
 * Description: Manages CLI configuration with hierarchical overrides (CLI > current dir > home dir)
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
Object.defineProperty(exports, "__esModule", { value: true });
exports.ConfigManager = void 0;
const fs = __importStar(require("fs"));
const path = __importStar(require("path"));
const os = __importStar(require("os"));
class ConfigManager {
    constructor() {
        this.config = {};
        this.loadConfig();
    }
    /**
     * Load configuration with hierarchical precedence:
     * 1. Current working directory
     * 2. Home directory (~/.ezkey/)
     * 3. Default values
     */
    loadConfig() {
        // Default configuration
        this.config = {
            adminUrl: 'http://localhost:9080',
            authUrl: 'http://localhost:8080',
            simUrl: 'http://localhost:8080',
            prettyPrint: true,
            timeout: 30000
        };
        // Load from home directory
        const homeConfigPath = path.join(os.homedir(), ConfigManager.HOME_CONFIG_DIR, ConfigManager.CONFIG_FILENAME);
        this.mergeConfigFromFile(homeConfigPath);
        // Load from current directory (higher precedence)
        const currentConfigPath = path.join(process.cwd(), ConfigManager.CONFIG_FILENAME);
        this.mergeConfigFromFile(currentConfigPath);
    }
    mergeConfigFromFile(filePath) {
        try {
            if (fs.existsSync(filePath)) {
                const fileContent = fs.readFileSync(filePath, 'utf-8');
                const fileConfig = JSON.parse(fileContent);
                this.config = { ...this.config, ...fileConfig };
            }
        }
        catch (error) {
            // Silently ignore invalid config files
            console.warn(`Warning: Could not load config from ${filePath}`);
        }
    }
    /**
     * Get configuration value
     */
    get(key) {
        return this.config[key];
    }
    /**
     * Set configuration value (runtime only)
     */
    set(key, value) {
        this.config[key] = value;
    }
    /**
     * Get the full configuration object
     */
    getAll() {
        return { ...this.config };
    }
    /**
     * Save configuration to a specific location
     */
    save(location = 'home') {
        let configPath;
        if (location === 'home') {
            const configDir = path.join(os.homedir(), ConfigManager.HOME_CONFIG_DIR);
            if (!fs.existsSync(configDir)) {
                fs.mkdirSync(configDir, { recursive: true });
            }
            configPath = path.join(configDir, ConfigManager.CONFIG_FILENAME);
        }
        else {
            configPath = path.join(process.cwd(), ConfigManager.CONFIG_FILENAME);
        }
        fs.writeFileSync(configPath, JSON.stringify(this.config, null, 2), 'utf-8');
    }
    /**
     * Override configuration with command line arguments
     */
    override(overrides) {
        this.config = { ...this.config, ...overrides };
    }
}
exports.ConfigManager = ConfigManager;
ConfigManager.CONFIG_FILENAME = 'ezkey.json';
ConfigManager.HOME_CONFIG_DIR = '.ezkey';
//# sourceMappingURL=config-manager.js.map