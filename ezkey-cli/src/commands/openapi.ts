/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * CLI Component: OpenAPI Command
 * Description: Commands to refresh OpenAPI specifications for demo applications
 */

import { Command } from 'commander';
import { ConfigManager } from '../config/config-manager';
import { HttpClient } from '../utils/http-client';
import chalk from 'chalk';
import * as fs from 'fs';
import * as path from 'path';

export class OpenApiCommand {
    private config: ConfigManager;
    private httpClient: HttpClient;

    constructor(config: ConfigManager) {
        this.config = config;
        this.httpClient = new HttpClient(config);
    }

    public getCommand(): Command {
        const cmd = new Command('openapi')
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

    private async refreshSpecs(options: any): Promise<void> {
        const projectRoot = this.findProjectRoot();
        if (!projectRoot) {
            console.error(chalk.red('Error: Could not find ezkey project root'));
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

        console.log(chalk.green('OpenAPI specification refresh completed'));
    }

    private async refreshAdminSpec(projectRoot: string): Promise<void> {
        console.log(chalk.blue('Refreshing Admin API specification for demo-app-acme...'));
        
        const adminUrl = `${this.config.get('adminUrl')}/v3/api-docs`;
        const targetPath = path.join(projectRoot, 'ezkey-demo-app-acme', 'openapi-spec.json');
        
        await this.downloadAndSaveSpec(adminUrl, targetPath, 'Admin API');
    }

    private async refreshAuthSpec(projectRoot: string): Promise<void> {
        console.log(chalk.blue('Refreshing Auth API specification for demo-device...'));
        
        const authUrl = `${this.config.get('authUrl')}/v3/api-docs`;
        const targetPath = path.join(projectRoot, 'ezkey-demo-device', 'openapi-spec.json');
        
        await this.downloadAndSaveSpec(authUrl, targetPath, 'Auth API');
    }

    private async downloadAndSaveSpec(url: string, targetPath: string, apiName: string): Promise<void> {
        try {
            // Create backup of existing file
            if (fs.existsSync(targetPath)) {
                const backupPath = targetPath + '.backup';
                fs.copyFileSync(targetPath, backupPath);
                console.log(chalk.gray(`Created backup: ${path.basename(backupPath)}`));
            }

            // Download new specification
            console.log(chalk.gray(`Downloading from: ${url}`));
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
            console.log(chalk.green(`✓ Updated ${apiName} specification: ${path.basename(targetPath)}`));

            // Remove backup on success
            const backupPath = targetPath + '.backup';
            if (fs.existsSync(backupPath)) {
                fs.unlinkSync(backupPath);
            }

        } catch (error) {
            console.error(chalk.red(`✗ Failed to update ${apiName} specification:`), error instanceof Error ? error.message : String(error));
            
            // Restore backup if it exists
            const backupPath = targetPath + '.backup';
            if (fs.existsSync(backupPath)) {
                fs.copyFileSync(backupPath, targetPath);
                fs.unlinkSync(backupPath);
                console.log(chalk.yellow('Restored previous specification from backup'));
            }
            
            throw error;
        }
    }

    private findProjectRoot(): string | null {
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