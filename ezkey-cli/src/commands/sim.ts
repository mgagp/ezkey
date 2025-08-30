/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * CLI Component: Sim Command
 * Description: Simulation API commands for cryptographic operations and testing
 */

import { Command } from 'commander';
import { ConfigManager } from '../config/config-manager';
import { HttpClient } from '../utils/http-client';
import { JsonUtils } from '../utils/json-utils';
import chalk from 'chalk';

export class SimCommand {
    private config: ConfigManager;
    private httpClient: HttpClient;

    constructor(config: ConfigManager) {
        this.config = config;
        this.httpClient = new HttpClient(config);
    }

    public getCommand(): Command {
        const cmd = new Command('sim')
            .description('Simulation API commands for cryptographic operations and testing');

        cmd
            .command('prooftoken')
            .description('Generate a cryptographically secure proof token')
            .action(async () => {
                await this.generateProofToken();
            });

        cmd
            .command('keypair')
            .description('Generate RSA key pair')
            .option('--key-size <size>', 'Key size in bits (1024-4096)', '2048')
            .action(async (options) => {
                await this.generateKeyPair(parseInt(options.keySize));
            });

        cmd
            .command('sign')
            .description('Sign data with RSA private key')
            .requiredOption('--data <data>', 'Data to sign (or @filename for file input)')
            .requiredOption('--private-key <key>', 'RSA private key (or @filename for file input)')
            .option('--json <json>', 'JSON data (or @filename for file input)')
            .action(async (options) => {
                await this.signData(options);
            });

        cmd
            .command('validate')
            .description('Validate signature against original data')
            .requiredOption('--data <data>', 'Original data (or @filename for file input)')
            .requiredOption('--signature <sig>', 'Signature to validate (or @filename for file input)')
            .requiredOption('--public-key <key>', 'RSA public key (or @filename for file input)')
            .option('--json <json>', 'JSON data (or @filename for file input)')
            .action(async (options) => {
                await this.validateSignature(options);
            });

        return cmd;
    }

    private async generateProofToken(): Promise<void> {
        const url = `${this.config.get('simUrl')}/api/v1/sim/prooftoken`;
        const response = await this.httpClient.get(url);
        this.outputResponse(response);
    }

    private async generateKeyPair(keySize: number): Promise<void> {
        // Validate key size
        if (keySize < 1024 || keySize > 4096) {
            console.error(chalk.red('Error: Key size must be between 1024 and 4096 bits'));
            return;
        }

        const url = `${this.config.get('simUrl')}/api/v1/sim/keypair`;
        const params = { keySize };
        
        const response = await this.httpClient.get(url, { params });
        this.outputResponse(response);
    }

    private async signData(options: any): Promise<void> {
        const url = `${this.config.get('simUrl')}/api/v1/sim/sign`;
        
        let data: any = {};
        
        if (options.json) {
            data = JsonUtils.processInput(options.json);
        } else {
            // Process individual parameters
            let dataToSign: string;
            let privateKey: string;

            // Handle data input (direct string or file)
            if (options.data.startsWith('@')) {
                const filePath = options.data.substring(1);
                try {
                    const fs = require('fs');
                    dataToSign = fs.readFileSync(filePath, 'utf-8');
                } catch (error) {
                    console.error(chalk.red(`Error reading data file: ${error instanceof Error ? error.message : String(error)}`));
                    return;
                }
            } else {
                dataToSign = options.data;
            }

            // Handle private key input (direct string or file)
            if (options.privateKey.startsWith('@')) {
                const filePath = options.privateKey.substring(1);
                try {
                    const fs = require('fs');
                    privateKey = fs.readFileSync(filePath, 'utf-8').trim();
                } catch (error) {
                    console.error(chalk.red(`Error reading private key file: ${error instanceof Error ? error.message : String(error)}`));
                    return;
                }
            } else {
                privateKey = options.privateKey;
            }

            data = {
                data: dataToSign,
                privateKey: privateKey
            };
        }

        const response = await this.httpClient.post(url, data);
        this.outputResponse(response);
    }

    private async validateSignature(options: any): Promise<void> {
        const url = `${this.config.get('simUrl')}/api/v1/sim/validate`;
        
        let data: any = {};
        
        if (options.json) {
            data = JsonUtils.processInput(options.json);
        } else {
            // Process individual parameters
            let originalData: string;
            let signature: string;
            let publicKey: string;

            // Handle data input (direct string or file)
            if (options.data.startsWith('@')) {
                const filePath = options.data.substring(1);
                try {
                    const fs = require('fs');
                    originalData = fs.readFileSync(filePath, 'utf-8');
                } catch (error) {
                    console.error(chalk.red(`Error reading data file: ${error instanceof Error ? error.message : String(error)}`));
                    return;
                }
            } else {
                originalData = options.data;
            }

            // Handle signature input (direct string or file)
            if (options.signature.startsWith('@')) {
                const filePath = options.signature.substring(1);
                try {
                    const fs = require('fs');
                    signature = fs.readFileSync(filePath, 'utf-8').trim();
                } catch (error) {
                    console.error(chalk.red(`Error reading signature file: ${error instanceof Error ? error.message : String(error)}`));
                    return;
                }
            } else {
                signature = options.signature;
            }

            // Handle public key input (direct string or file)
            if (options.publicKey.startsWith('@')) {
                const filePath = options.publicKey.substring(1);
                try {
                    const fs = require('fs');
                    publicKey = fs.readFileSync(filePath, 'utf-8').trim();
                } catch (error) {
                    console.error(chalk.red(`Error reading public key file: ${error instanceof Error ? error.message : String(error)}`));
                    return;
                }
            } else {
                publicKey = options.publicKey;
            }

            data = {
                data: originalData,
                signature: signature,
                publicKey: publicKey
            };
        }

        const response = await this.httpClient.post(url, data);
        
        // Special formatting for validation results
        if (response.success && response.data) {
            const isValid = response.data.valid;
            const message = response.data.message;
            
            if (isValid) {
                console.log(chalk.green('✓ Signature is valid'));
            } else {
                console.log(chalk.red('✗ Signature is invalid'));
            }
            
            if (message) {
                console.log(`Message: ${message}`);
            }
            
            if (this.config.get('prettyPrint')) {
                console.log('\nFull response:');
                console.log(JsonUtils.formatOutput(response.data, true));
            }
        } else {
            this.outputResponse(response);
        }
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