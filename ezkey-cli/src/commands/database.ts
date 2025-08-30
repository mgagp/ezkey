/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * CLI Component: Database Command
 * Description: Database migration commands integrating with existing flyway functionality
 */

import { Command } from 'commander';
import { ConfigManager } from '../config/config-manager';
import chalk from 'chalk';
import { spawn } from 'child_process';
import * as path from 'path';
import * as fs from 'fs';

export class DatabaseCommand {
    private config: ConfigManager;

    constructor(config: ConfigManager) {
        this.config = config;
    }

    public getCommand(): Command {
        const cmd = new Command('database')
            .alias('db')
            .description('Database migration commands using Flyway');

        cmd
            .command('migrate')
            .description('Run database migrations')
            .option('--java-path <path>', 'Path to Java executable', this.config.get('javaPath') || 'java')
            .option('--ezkey-core-jar <path>', 'Path to ezkey-core JAR file', this.config.get('ezkeyCorePath'))
            .option('--info', 'Show migration info instead of migrating')
            .option('--repair', 'Repair the migration metadata table')
            .action(async (options) => {
                await this.runMigration(options);
            });

        cmd
            .command('info')
            .description('Show migration information')
            .option('--java-path <path>', 'Path to Java executable', this.config.get('javaPath') || 'java')
            .option('--ezkey-core-jar <path>', 'Path to ezkey-core JAR file', this.config.get('ezkeyCorePath'))
            .action(async (options) => {
                await this.runMigration({ ...options, info: true });
            });

        cmd
            .command('repair')
            .description('Repair migration metadata table')
            .option('--java-path <path>', 'Path to Java executable', this.config.get('javaPath') || 'java')
            .option('--ezkey-core-jar <path>', 'Path to ezkey-core JAR file', this.config.get('ezkeyCorePath'))
            .action(async (options) => {
                await this.runMigration({ ...options, repair: true });
            });

        return cmd;
    }

    private async runMigration(options: any): Promise<void> {
        try {
            // Determine the method to use for migration
            if (options.ezkeyCorePath && fs.existsSync(options.ezkeyCorePath)) {
                // Use provided JAR file
                await this.runWithJar(options);
            } else {
                // Use the existing script approach
                await this.runWithScript(options);
            }
        } catch (error) {
            console.error(chalk.red('Migration failed:'), error instanceof Error ? error.message : String(error));
            process.exit(1);
        }
    }

    private async runWithJar(options: any): Promise<void> {
        console.log(chalk.blue('Running migration with provided JAR...'));
        
        const args = ['-jar', options.ezkeyCorePath];
        
        if (options.info) {
            args.push('--info');
        } else if (options.repair) {
            args.push('--repair');
        } else {
            args.push('--migrate');
        }

        return new Promise((resolve, reject) => {
            const child = spawn(options.javaPath, args, {
                stdio: 'inherit',
                cwd: path.dirname(options.ezkeyCorePath)
            });

            child.on('close', (code) => {
                if (code === 0) {
                    resolve();
                } else {
                    reject(new Error(`Migration process exited with code ${code}`));
                }
            });

            child.on('error', (error) => {
                reject(new Error(`Failed to start migration process: ${error.message}`));
            });
        });
    }

    private async runWithScript(options: any): Promise<void> {
        console.log(chalk.blue('Running migration with existing script...'));
        
        // Find the project root (where the scripts directory is)
        const projectRoot = this.findProjectRoot();
        if (!projectRoot) {
            throw new Error('Could not find ezkey project root. Please specify --ezkey-core-jar or run from project directory.');
        }

        const scriptPath = path.join(projectRoot, 'scripts', 'ezkey-flyway.sh');
        
        if (!fs.existsSync(scriptPath)) {
            throw new Error(`Migration script not found at ${scriptPath}`);
        }

        const args: string[] = [];
        
        if (options.info) {
            args.push('--info');
        } else if (options.repair) {
            args.push('--repair');
        } else {
            args.push('--migrate');
        }

        return new Promise((resolve, reject) => {
            const child = spawn('bash', [scriptPath, ...args], {
                stdio: 'inherit',
                cwd: projectRoot
            });

            child.on('close', (code) => {
                if (code === 0) {
                    resolve();
                } else {
                    reject(new Error(`Migration script exited with code ${code}`));
                }
            });

            child.on('error', (error) => {
                reject(new Error(`Failed to start migration script: ${error.message}`));
            });
        });
    }

    private findProjectRoot(): string | null {
        let currentDir = process.cwd();
        
        // Look for scripts directory or pom.xml to identify project root
        while (currentDir !== path.dirname(currentDir)) {
            const scriptsDir = path.join(currentDir, 'scripts');
            const pomFile = path.join(currentDir, 'pom.xml');
            const ezkeyCore = path.join(currentDir, 'ezkey-core');
            
            if (fs.existsSync(scriptsDir) && fs.existsSync(pomFile) && fs.existsSync(ezkeyCore)) {
                return currentDir;
            }
            
            currentDir = path.dirname(currentDir);
        }
        
        return null;
    }
}