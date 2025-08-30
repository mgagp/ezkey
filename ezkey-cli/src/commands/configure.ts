/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * CLI Component: Configure Command
 * Description: Configuration management commands for setting up CLI parameters
 */

import { Command } from 'commander';
import { ConfigManager } from '../config/config-manager';
import { JsonUtils } from '../utils/json-utils';
import chalk from 'chalk';
import inquirer from 'inquirer';

export class ConfigureCommand {
    private config: ConfigManager;

    constructor(config: ConfigManager) {
        this.config = config;
    }

    public getCommand(): Command {
        const cmd = new Command('configure')
            .description('Configuration management commands');

        cmd
            .command('set')
            .description('Set configuration values')
            .option('--admin-url <url>', 'Admin API URL')
            .option('--auth-url <url>', 'Auth API URL')
            .option('--sim-url <url>', 'Sim API URL')
            .option('--java-path <path>', 'Path to Java executable')
            .option('--ezkey-core-path <path>', 'Path to ezkey-core JAR file')
            .option('--timeout <ms>', 'Request timeout in milliseconds')
            .option('--pretty-print <boolean>', 'Enable pretty printing (true/false)')
            .option('--global', 'Save to global configuration (home directory)')
            .action(async (options) => {
                await this.setConfig(options);
            });

        cmd
            .command('get')
            .description('Get configuration values')
            .option('--key <key>', 'Specific configuration key to get')
            .action(async (options) => {
                await this.getConfig(options);
            });

        cmd
            .command('reset')
            .description('Reset configuration to defaults')
            .option('--global', 'Reset global configuration (home directory)')
            .option('--current', 'Reset current directory configuration')
            .action(async (options) => {
                await this.resetConfig(options);
            });

        cmd
            .command('interactive')
            .alias('init')
            .description('Interactive configuration setup')
            .option('--global', 'Save to global configuration (home directory)')
            .action(async (options) => {
                await this.interactiveConfig(options);
            });

        return cmd;
    }

    private async setConfig(options: any): Promise<void> {
        const updates: any = {};

        // Collect all provided options
        if (options.adminUrl) updates.adminUrl = options.adminUrl;
        if (options.authUrl) updates.authUrl = options.authUrl;
        if (options.simUrl) updates.simUrl = options.simUrl;
        if (options.javaPath) updates.javaPath = options.javaPath;
        if (options.ezkeyCorePath) updates.ezkeyCorePath = options.ezkeyCorePath;
        if (options.timeout) updates.timeout = parseInt(options.timeout);
        if (options.prettyPrint) updates.prettyPrint = options.prettyPrint.toLowerCase() === 'true';

        if (Object.keys(updates).length === 0) {
            console.error(chalk.red('Error: No configuration values provided'));
            return;
        }

        // Apply updates to current config
        Object.keys(updates).forEach(key => {
            this.config.set(key as any, updates[key]);
        });

        // Save configuration
        const location = options.global ? 'home' : 'current';
        this.config.save(location);

        console.log(chalk.green(`Configuration saved to ${location === 'home' ? 'home directory' : 'current directory'}`));
        
        // Show updated configuration
        console.log('\nUpdated configuration:');
        Object.keys(updates).forEach(key => {
            console.log(chalk.blue(`  ${key}:`), updates[key]);
        });
    }

    private async getConfig(options: any): Promise<void> {
        const config = this.config.getAll();

        if (options.key) {
            const value = this.config.get(options.key as any);
            if (value !== undefined) {
                console.log(value);
            } else {
                console.error(chalk.red(`Configuration key '${options.key}' not found`));
                process.exit(1);
            }
        } else {
            console.log(JsonUtils.formatOutput(config, true));
        }
    }

    private async resetConfig(options: any): Promise<void> {
        if (!options.global && !options.current) {
            // Ask user which config to reset
            const answers = await inquirer.prompt([
                {
                    type: 'list',
                    name: 'scope',
                    message: 'Which configuration do you want to reset?',
                    choices: [
                        { name: 'Current directory only', value: 'current' },
                        { name: 'Global (home directory)', value: 'global' },
                        { name: 'Both', value: 'both' }
                    ]
                }
            ]);

            options.current = answers.scope === 'current' || answers.scope === 'both';
            options.global = answers.scope === 'global' || answers.scope === 'both';
        }

        if (options.current) {
            const fs = require('fs');
            const path = require('path');
            const configPath = path.join(process.cwd(), 'ezkey.json');
            
            if (fs.existsSync(configPath)) {
                fs.unlinkSync(configPath);
                console.log(chalk.green('Current directory configuration reset'));
            }
        }

        if (options.global) {
            const fs = require('fs');
            const path = require('path');
            const os = require('os');
            const configPath = path.join(os.homedir(), '.ezkey', 'ezkey.json');
            
            if (fs.existsSync(configPath)) {
                fs.unlinkSync(configPath);
                console.log(chalk.green('Global configuration reset'));
            }
        }

        console.log(chalk.blue('Configuration has been reset to defaults'));
    }

    private async interactiveConfig(options: any): Promise<void> {
        console.log(chalk.blue('Ezkey CLI Interactive Configuration'));
        console.log(chalk.gray('Press Enter to keep current values\n'));

        const current = this.config.getAll();

        const answers = await inquirer.prompt([
            {
                type: 'input',
                name: 'adminUrl',
                message: 'Admin API URL:',
                default: current.adminUrl
            },
            {
                type: 'input',
                name: 'authUrl',
                message: 'Auth API URL:',
                default: current.authUrl
            },
            {
                type: 'input',
                name: 'simUrl',
                message: 'Sim API URL:',
                default: current.simUrl
            },
            {
                type: 'input',
                name: 'javaPath',
                message: 'Java executable path:',
                default: current.javaPath || 'java'
            },
            {
                type: 'input',
                name: 'ezkeyCorePath',
                message: 'Ezkey Core JAR path (optional):',
                default: current.ezkeyCorePath
            },
            {
                type: 'number',
                name: 'timeout',
                message: 'Request timeout (milliseconds):',
                default: current.timeout
            },
            {
                type: 'confirm',
                name: 'prettyPrint',
                message: 'Enable pretty printing of JSON output:',
                default: current.prettyPrint
            }
        ]);

        // Apply all answers to configuration
        Object.keys(answers).forEach(key => {
            if (answers[key] !== undefined && answers[key] !== '') {
                this.config.set(key as any, answers[key]);
            }
        });

        // Save configuration
        const location = options.global ? 'home' : 'current';
        this.config.save(location);

        console.log(chalk.green(`\nConfiguration saved to ${location === 'home' ? 'home directory' : 'current directory'}`));
        console.log(chalk.blue('You can now use the ezkey CLI with your configured settings!'));
    }
}