/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * CLI Component: Configuration Manager
 * Description: Manages CLI configuration with hierarchical overrides (CLI > current dir > home dir)
 */

import * as fs from 'fs';
import * as path from 'path';
import * as os from 'os';

export interface EzkeyConfig {
    adminUrl?: string;
    authUrl?: string;
    simUrl?: string;
    javaPath?: string;
    ezkeyCorePath?: string;
    prettyPrint?: boolean;
    timeout?: number;
}

export class ConfigManager {
    private static readonly CONFIG_FILENAME = 'ezkey.json';
    private static readonly HOME_CONFIG_DIR = '.ezkey';

    private config: EzkeyConfig = {};

    constructor() {
        this.loadConfig();
    }

    /**
     * Load configuration with hierarchical precedence:
     * 1. Current working directory
     * 2. Home directory (~/.ezkey/)
     * 3. Default values
     */
    private loadConfig(): void {
        // Default configuration
        this.config = {
            adminUrl: 'http://localhost:9080',
            authUrl: 'http://localhost:8080',
            simUrl: 'http://localhost:8080',
            prettyPrint: true,
            timeout: 30000
        };

        // Load from home directory
        const homeConfigPath = path.join(
            os.homedir(),
            ConfigManager.HOME_CONFIG_DIR,
            ConfigManager.CONFIG_FILENAME
        );
        this.mergeConfigFromFile(homeConfigPath);

        // Load from current directory (higher precedence)
        const currentConfigPath = path.join(process.cwd(), ConfigManager.CONFIG_FILENAME);
        this.mergeConfigFromFile(currentConfigPath);
    }

    private mergeConfigFromFile(filePath: string): void {
        try {
            if (fs.existsSync(filePath)) {
                const fileContent = fs.readFileSync(filePath, 'utf-8');
                const fileConfig = JSON.parse(fileContent);
                this.config = { ...this.config, ...fileConfig };
            }
        } catch (error) {
            // Silently ignore invalid config files
            console.warn(`Warning: Could not load config from ${filePath}`);
        }
    }

    /**
     * Get configuration value
     */
    get(key: keyof EzkeyConfig): any {
        return this.config[key];
    }

    /**
     * Set configuration value (runtime only)
     */
    set(key: keyof EzkeyConfig, value: any): void {
        this.config[key] = value;
    }

    /**
     * Get the full configuration object
     */
    getAll(): EzkeyConfig {
        return { ...this.config };
    }

    /**
     * Save configuration to a specific location
     */
    save(location: 'home' | 'current' = 'home'): void {
        let configPath: string;
        
        if (location === 'home') {
            const configDir = path.join(os.homedir(), ConfigManager.HOME_CONFIG_DIR);
            if (!fs.existsSync(configDir)) {
                fs.mkdirSync(configDir, { recursive: true });
            }
            configPath = path.join(configDir, ConfigManager.CONFIG_FILENAME);
        } else {
            configPath = path.join(process.cwd(), ConfigManager.CONFIG_FILENAME);
        }

        fs.writeFileSync(configPath, JSON.stringify(this.config, null, 2), 'utf-8');
    }

    /**
     * Override configuration with command line arguments
     */
    override(overrides: Partial<EzkeyConfig>): void {
        this.config = { ...this.config, ...overrides };
    }
}