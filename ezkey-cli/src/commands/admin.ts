/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * CLI Component: Admin Command
 * Description: Admin API commands for integrations, enrollments, and auth attempts
 */

import { Command } from 'commander';
import { ConfigManager } from '../config/config-manager';
import { HttpClient } from '../utils/http-client';
import { JsonUtils } from '../utils/json-utils';
import chalk from 'chalk';

export class AdminCommand {
    private config: ConfigManager;
    private httpClient: HttpClient;

    constructor(config: ConfigManager) {
        this.config = config;
        this.httpClient = new HttpClient(config);
    }

    public getCommand(): Command {
        const cmd = new Command('admin')
            .description('Admin API commands for managing integrations, enrollments, and auth attempts');

        // Integration commands
        const integrationCmd = new Command('integration')
            .description('Integration management commands');

        integrationCmd
            .command('list')
            .description('List all integrations')
            .action(async () => {
                await this.listIntegrations();
            });

        integrationCmd
            .command('get')
            .description('Get integration by ID')
            .requiredOption('--id <id>', 'Integration ID')
            .action(async (options) => {
                await this.getIntegration(parseInt(options.id));
            });

        integrationCmd
            .command('create')
            .description('Create a new integration')
            .option('--logo <url>', 'Logo URL or path')
            .option('--data <json>', 'JSON data (or @filename for file input)')
            .action(async (options) => {
                await this.createIntegration(options);
            });

        integrationCmd
            .command('delete')
            .description('Delete an integration')
            .requiredOption('--id <id>', 'Integration ID')
            .action(async (options) => {
                await this.deleteIntegration(parseInt(options.id));
            });

        // Enrollment commands
        const enrollmentCmd = new Command('enrollment')
            .description('Enrollment management commands');

        enrollmentCmd
            .command('list')
            .description('List all enrollments')
            .action(async () => {
                await this.listEnrollments();
            });

        enrollmentCmd
            .command('get')
            .description('Get enrollment by ID')
            .requiredOption('--id <id>', 'Enrollment ID')
            .action(async (options) => {
                await this.getEnrollment(parseInt(options.id));
            });

        enrollmentCmd
            .command('create')
            .description('Create a new enrollment')
            .requiredOption('--integration-id <id>', 'Integration ID')
            .requiredOption('--name <name>', 'Enrollment name')
            .option('--challenge-required', 'Require challenge for auth attempts')
            .option('--data <json>', 'JSON data (or @filename for file input)')
            .action(async (options) => {
                await this.createEnrollment(options);
            });

        enrollmentCmd
            .command('delete')
            .description('Delete an enrollment')
            .requiredOption('--id <id>', 'Enrollment ID')
            .action(async (options) => {
                await this.deleteEnrollment(parseInt(options.id));
            });

        // Auth attempt commands
        const authAttemptCmd = new Command('auth-attempt')
            .description('Authentication attempt management commands');

        authAttemptCmd
            .command('list')
            .description('List all auth attempts')
            .action(async () => {
                await this.listAuthAttempts();
            });

        authAttemptCmd
            .command('get')
            .description('Get auth attempt by ID')
            .requiredOption('--id <id>', 'Auth attempt ID')
            .action(async (options) => {
                await this.getAuthAttempt(parseInt(options.id));
            });

        authAttemptCmd
            .command('create')
            .description('Create a new auth attempt')
            .requiredOption('--enrollment-id <id>', 'Enrollment ID')
            .option('--challenge-requested', 'Request challenge for this attempt')
            .option('--data <json>', 'JSON data (or @filename for file input)')
            .action(async (options) => {
                await this.createAuthAttempt(options);
            });

        authAttemptCmd
            .command('wait')
            .description('Wait for auth attempt completion')
            .requiredOption('--id <id>', 'Auth attempt ID')
            .option('--timeout <seconds>', 'Timeout in seconds', '30')
            .option('--polling <seconds>', 'Polling interval in seconds', '2')
            .action(async (options) => {
                await this.waitForAuthAttempt(parseInt(options.id), options);
            });

        // Add subcommands
        cmd.addCommand(integrationCmd);
        cmd.addCommand(enrollmentCmd);
        cmd.addCommand(authAttemptCmd);

        return cmd;
    }

    // Integration methods
    private async listIntegrations(): Promise<void> {
        const url = `${this.config.get('adminUrl')}/api/v1/integrations`;
        const response = await this.httpClient.get(url);
        this.outputResponse(response);
    }

    private async getIntegration(id: number): Promise<void> {
        const url = `${this.config.get('adminUrl')}/api/v1/integrations/${id}`;
        const response = await this.httpClient.get(url);
        this.outputResponse(response);
    }

    private async createIntegration(options: any): Promise<void> {
        const url = `${this.config.get('adminUrl')}/api/v1/integrations`;
        
        let data: any = {};
        
        if (options.data) {
            data = JsonUtils.processInput(options.data);
        } else {
            // Build from command line options
            if (options.logo) {
                data.logo = options.logo;
            }
            // Note: i18n data would need to be provided via --data option
            if (Object.keys(data).length === 0) {
                console.error('Error: No data provided. Use --data option or provide specific fields.');
                return;
            }
        }

        const response = await this.httpClient.post(url, data);
        this.outputResponse(response);
    }

    private async deleteIntegration(id: number): Promise<void> {
        const url = `${this.config.get('adminUrl')}/api/v1/integrations/${id}`;
        const response = await this.httpClient.delete(url);
        this.outputResponse(response);
    }

    // Enrollment methods
    private async listEnrollments(): Promise<void> {
        const url = `${this.config.get('adminUrl')}/api/v1/enrollments`;
        const response = await this.httpClient.get(url);
        this.outputResponse(response);
    }

    private async getEnrollment(id: number): Promise<void> {
        const url = `${this.config.get('adminUrl')}/api/v1/enrollments/${id}`;
        const response = await this.httpClient.get(url);
        this.outputResponse(response);
    }

    private async createEnrollment(options: any): Promise<void> {
        const url = `${this.config.get('adminUrl')}/api/v1/enrollments`;
        
        let data: any = {};
        
        if (options.data) {
            data = JsonUtils.processInput(options.data);
        } else {
            // Build from command line options
            data = {
                integrationId: parseInt(options.integrationId),
                name: options.name,
                authAttemptChallengeRequired: !!options.challengeRequired
            };
        }

        const response = await this.httpClient.post(url, data);
        this.outputResponse(response);
    }

    private async deleteEnrollment(id: number): Promise<void> {
        const url = `${this.config.get('adminUrl')}/api/v1/enrollments/${id}`;
        const response = await this.httpClient.delete(url);
        this.outputResponse(response);
    }

    // Auth attempt methods
    private async listAuthAttempts(): Promise<void> {
        const url = `${this.config.get('adminUrl')}/api/v1/auth-attempts`;
        const response = await this.httpClient.get(url);
        this.outputResponse(response);
    }

    private async getAuthAttempt(id: number): Promise<void> {
        const url = `${this.config.get('adminUrl')}/api/v1/auth-attempts/${id}`;
        const response = await this.httpClient.get(url);
        this.outputResponse(response);
    }

    private async createAuthAttempt(options: any): Promise<void> {
        const url = `${this.config.get('adminUrl')}/api/v1/auth-attempts`;
        
        let data: any = {};
        
        if (options.data) {
            data = JsonUtils.processInput(options.data);
        } else {
            // Build from command line options
            data = {
                enrollmentId: parseInt(options.enrollmentId),
                challengeRequested: !!options.challengeRequested
            };
        }

        const response = await this.httpClient.post(url, data);
        this.outputResponse(response);
    }

    private async waitForAuthAttempt(id: number, options: any): Promise<void> {
        const url = `${this.config.get('adminUrl')}/api/v1/auth-attempts/${id}/wait`;
        const params = {
            timeout: options.timeout,
            polling: options.polling
        };
        
        console.log(chalk.yellow(`Waiting for auth attempt ${id} to complete...`));
        const response = await this.httpClient.get(url, { params });
        this.outputResponse(response);
    }

    private outputResponse(response: any): void {
        if (response.success) {
            if (response.data !== undefined) {
                console.log(JsonUtils.formatOutput(response.data, this.config.get('prettyPrint')));
            } else {
                console.log(chalk.green('Success'));
            }
        } else {
            console.error(chalk.red('Error:'), response.error);
            if (response.data) {
                console.error(JsonUtils.formatOutput(response.data, this.config.get('prettyPrint')));
            }
            process.exit(1);
        }
    }
}