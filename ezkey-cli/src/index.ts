/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * CLI Component: Main Entry Point
 * Description: Main CLI application with command routing and global options
 */

import { Command } from 'commander';
import { ConfigManager } from './config/config-manager';
import { AdminCommand } from './commands/admin';
import { AuthCommand } from './commands/auth';
import { SimCommand } from './commands/sim';
import { DatabaseCommand } from './commands/database';
import { OpenApiCommand } from './commands/openapi';
import { ConfigureCommand } from './commands/configure';

// Import package.json for version
const packageJson = require('../package.json');

export class EzkeyCli {
    private program: Command;
    private config: ConfigManager;

    constructor() {
        this.config = new ConfigManager();
        this.program = new Command();
        this.setupCommands();
    }

    private setupCommands(): void {
        this.program
            .name('ezkey')
            .description('Ezkey CLI - Command line interface for Ezkey MFA system')
            .version(packageJson.version);

        // Global options
        this.program
            .option('--admin-url <url>', 'Admin API URL', this.config.get('adminUrl'))
            .option('--auth-url <url>', 'Auth API URL', this.config.get('authUrl'))
            .option('--sim-url <url>', 'Sim API URL', this.config.get('simUrl'))
            .option('--no-pretty', 'Disable pretty printing of JSON output')
            .option('--timeout <ms>', 'Request timeout in milliseconds', this.config.get('timeout'))
            .option('--verbose', 'Enable verbose output')
            .hook('preAction', (thisCommand) => {
                // Override config with command line options
                const options = thisCommand.opts();
                this.config.override({
                    adminUrl: options.adminUrl,
                    authUrl: options.authUrl, 
                    simUrl: options.simUrl,
                    prettyPrint: options.pretty !== false,
                    timeout: options.timeout ? parseInt(options.timeout) : undefined
                });
            });

        // Add main command groups
        this.program.addCommand(new AdminCommand(this.config).getCommand());
        this.program.addCommand(new AuthCommand(this.config).getCommand());
        this.program.addCommand(new SimCommand(this.config).getCommand());
        this.program.addCommand(new DatabaseCommand(this.config).getCommand());
        this.program.addCommand(new OpenApiCommand(this.config).getCommand());
        this.program.addCommand(new ConfigureCommand(this.config).getCommand());

        // Add help examples
        this.program.addHelpText('after', `
Examples:
  $ ezkey configure --admin-url http://localhost:9080 --auth-url http://localhost:8080
  $ ezkey admin integration create --name "Test App" --logo "logo.png"
  $ ezkey admin integration list
  $ ezkey auth enrollment bind --id 123
  $ ezkey sim keypair --key-size 2048
  $ ezkey database migrate
  $ ezkey openapi refresh --all
  $ ezkey version

For more help on a specific command:
  $ ezkey <command> --help
        `);
    }

    public async run(argv?: string[]): Promise<void> {
        try {
            await this.program.parseAsync(argv);
        } catch (error) {
            console.error('Error:', error instanceof Error ? error.message : String(error));
            process.exit(1);
        }
    }
}

// CLI entry point
if (require.main === module) {
    const cli = new EzkeyCli();
    cli.run(process.argv);
}