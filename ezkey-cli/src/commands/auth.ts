/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * CLI Component: Auth Command
 * Description: Auth API commands for enrollment binding/verification and auth attempt handling
 */

import { Command } from 'commander';
import { ConfigManager } from '../config/config-manager';
import { HttpClient } from '../utils/http-client';
import { JsonUtils } from '../utils/json-utils';
import chalk from 'chalk';

export class AuthCommand {
    private config: ConfigManager;
    private httpClient: HttpClient;

    constructor(config: ConfigManager) {
        this.config = config;
        this.httpClient = new HttpClient(config);
    }

    public getCommand(): Command {
        const cmd = new Command('auth')
            .description('Auth API commands for enrollment and authentication flows');

        // Enrollment commands
        const enrollmentCmd = new Command('enrollment')
            .description('Enrollment binding and verification commands');

        enrollmentCmd
            .command('bind')
            .description('Bind device to enrollment')
            .requiredOption('--id <id>', 'Enrollment ID')
            .option('--language <lang>', 'Accept-Language header', 'en')
            .action(async (options) => {
                await this.bindEnrollment(parseInt(options.id), options.language);
            });

        enrollmentCmd
            .command('verify')
            .description('Verify enrollment completion')
            .requiredOption('--enrollment-id <id>', 'Enrollment ID')
            .requiredOption('--challenge-response <response>', 'Challenge response')
            .requiredOption('--public-key <key>', 'Device public key')
            .requiredOption('--token-signed <signature>', 'Signed enrollment proof token')
            .option('--data <json>', 'JSON data (or @filename for file input)')
            .action(async (options) => {
                await this.verifyEnrollment(options);
            });

        // Auth attempt commands
        const authAttemptCmd = new Command('auth-attempt')
            .description('Authentication attempt handling commands');

        authAttemptCmd
            .command('pending')
            .description('Check for pending authentication requests')
            .requiredOption('--enrollment-id <id>', 'Enrollment ID')
            .requiredOption('--device-token <token>', 'Device proof token')
            .requiredOption('--device-token-signed <signature>', 'Signed device proof token')
            .option('--data <json>', 'JSON data (or @filename for file input)')
            .action(async (options) => {
                await this.checkPending(options);
            });

        authAttemptCmd
            .command('respond')
            .description('Respond to authentication attempt')
            .requiredOption('--id <id>', 'Auth attempt ID')
            .requiredOption('--accepted <boolean>', 'Accept or deny (true/false)')
            .requiredOption('--token-signed <signature>', 'Signed auth attempt proof token')
            .option('--challenge-response <response>', 'Challenge response (if required)')
            .option('--data <json>', 'JSON data (or @filename for file input)')
            .action(async (options) => {
                await this.respondToAuthAttempt(options);
            });

        // Add subcommands
        cmd.addCommand(enrollmentCmd);
        cmd.addCommand(authAttemptCmd);

        return cmd;
    }

    // Enrollment methods
    private async bindEnrollment(id: number, language: string): Promise<void> {
        const url = `${this.config.get('authUrl')}/api/v1/enrollments/bind/${id}`;
        const headers = {
            'Accept-Language': language
        };
        
        const response = await this.httpClient.get(url, { headers });
        this.outputResponse(response);
    }

    private async verifyEnrollment(options: any): Promise<void> {
        const url = `${this.config.get('authUrl')}/api/v1/enrollments/verify`;
        
        let data: any = {};
        
        if (options.data) {
            data = JsonUtils.processInput(options.data);
        } else {
            // Build from command line options
            data = {
                enrollmentId: parseInt(options.enrollmentId),
                challengeResponse: parseInt(options.challengeResponse),
                devicePublicKey: options.publicKey,
                enrollmentProofTokenSigned: options.tokenSigned
            };
        }

        const response = await this.httpClient.post(url, data);
        this.outputResponse(response);
    }

    // Auth attempt methods
    private async checkPending(options: any): Promise<void> {
        const enrollmentId = parseInt(options.enrollmentId);
        const url = `${this.config.get('authUrl')}/api/v1/auth-attempts/pending/${enrollmentId}`;
        
        let data: any = {};
        
        if (options.data) {
            data = JsonUtils.processInput(options.data);
        } else {
            // Build from command line options
            data = {
                enrollmentId: enrollmentId,
                deviceProofToken: options.deviceToken,
                deviceProofTokenSigned: options.deviceTokenSigned
            };
        }

        const response = await this.httpClient.post(url, data);
        
        // Special handling for 204 No Content (no pending requests)
        if (response.status === 204) {
            console.log(chalk.green('No pending authentication requests'));
        } else {
            this.outputResponse(response);
        }
    }

    private async respondToAuthAttempt(options: any): Promise<void> {
        const authAttemptId = parseInt(options.id);
        const url = `${this.config.get('authUrl')}/api/v1/auth-attempts/respond/${authAttemptId}`;
        
        let data: any = {};
        
        if (options.data) {
            data = JsonUtils.processInput(options.data);
        } else {
            // Build from command line options
            data = {
                authAttemptId: authAttemptId,
                authAttemptProofTokenSignedByDevice: options.tokenSigned,
                authAttemptAccepted: options.accepted.toLowerCase() === 'true'
            };
            
            if (options.challengeResponse) {
                data.authAttemptChallengeResponse = parseInt(options.challengeResponse);
            }
        }

        const response = await this.httpClient.post(url, data);
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