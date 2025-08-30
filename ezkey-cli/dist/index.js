"use strict";
/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * CLI Component: Main Entry Point
 * Description: Main CLI application with command routing and global options
 */
Object.defineProperty(exports, "__esModule", { value: true });
exports.EzkeyCli = void 0;
const commander_1 = require("commander");
const config_manager_1 = require("./config/config-manager");
const admin_1 = require("./commands/admin");
const auth_1 = require("./commands/auth");
const sim_1 = require("./commands/sim");
const database_1 = require("./commands/database");
const openapi_1 = require("./commands/openapi");
const configure_1 = require("./commands/configure");
// Import package.json for version
const packageJson = require('../package.json');
class EzkeyCli {
    constructor() {
        this.config = new config_manager_1.ConfigManager();
        this.program = new commander_1.Command();
        this.setupCommands();
    }
    setupCommands() {
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
        this.program.addCommand(new admin_1.AdminCommand(this.config).getCommand());
        this.program.addCommand(new auth_1.AuthCommand(this.config).getCommand());
        this.program.addCommand(new sim_1.SimCommand(this.config).getCommand());
        this.program.addCommand(new database_1.DatabaseCommand(this.config).getCommand());
        this.program.addCommand(new openapi_1.OpenApiCommand(this.config).getCommand());
        this.program.addCommand(new configure_1.ConfigureCommand(this.config).getCommand());
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
    async run(argv) {
        try {
            await this.program.parseAsync(argv);
        }
        catch (error) {
            console.error('Error:', error instanceof Error ? error.message : String(error));
            process.exit(1);
        }
    }
}
exports.EzkeyCli = EzkeyCli;
// CLI entry point
if (require.main === module) {
    const cli = new EzkeyCli();
    cli.run(process.argv);
}
//# sourceMappingURL=index.js.map