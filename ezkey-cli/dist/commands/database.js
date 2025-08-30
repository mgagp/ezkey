"use strict";
/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * CLI Component: Database Command
 * Description: Database migration commands integrating with existing flyway functionality
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
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.DatabaseCommand = void 0;
const commander_1 = require("commander");
const chalk_1 = __importDefault(require("chalk"));
const child_process_1 = require("child_process");
const path = __importStar(require("path"));
const fs = __importStar(require("fs"));
class DatabaseCommand {
    constructor(config) {
        this.config = config;
    }
    getCommand() {
        const cmd = new commander_1.Command('database')
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
    async runMigration(options) {
        try {
            // Determine the method to use for migration
            if (options.ezkeyCorePath && fs.existsSync(options.ezkeyCorePath)) {
                // Use provided JAR file
                await this.runWithJar(options);
            }
            else {
                // Use the existing script approach
                await this.runWithScript(options);
            }
        }
        catch (error) {
            console.error(chalk_1.default.red('Migration failed:'), error instanceof Error ? error.message : String(error));
            process.exit(1);
        }
    }
    async runWithJar(options) {
        console.log(chalk_1.default.blue('Running migration with provided JAR...'));
        const args = ['-jar', options.ezkeyCorePath];
        if (options.info) {
            args.push('--info');
        }
        else if (options.repair) {
            args.push('--repair');
        }
        else {
            args.push('--migrate');
        }
        return new Promise((resolve, reject) => {
            const child = (0, child_process_1.spawn)(options.javaPath, args, {
                stdio: 'inherit',
                cwd: path.dirname(options.ezkeyCorePath)
            });
            child.on('close', (code) => {
                if (code === 0) {
                    resolve();
                }
                else {
                    reject(new Error(`Migration process exited with code ${code}`));
                }
            });
            child.on('error', (error) => {
                reject(new Error(`Failed to start migration process: ${error.message}`));
            });
        });
    }
    async runWithScript(options) {
        console.log(chalk_1.default.blue('Running migration with existing script...'));
        // Find the project root (where the scripts directory is)
        const projectRoot = this.findProjectRoot();
        if (!projectRoot) {
            throw new Error('Could not find ezkey project root. Please specify --ezkey-core-jar or run from project directory.');
        }
        const scriptPath = path.join(projectRoot, 'scripts', 'ezkey-flyway.sh');
        if (!fs.existsSync(scriptPath)) {
            throw new Error(`Migration script not found at ${scriptPath}`);
        }
        const args = [];
        if (options.info) {
            args.push('--info');
        }
        else if (options.repair) {
            args.push('--repair');
        }
        else {
            args.push('--migrate');
        }
        return new Promise((resolve, reject) => {
            const child = (0, child_process_1.spawn)('bash', [scriptPath, ...args], {
                stdio: 'inherit',
                cwd: projectRoot
            });
            child.on('close', (code) => {
                if (code === 0) {
                    resolve();
                }
                else {
                    reject(new Error(`Migration script exited with code ${code}`));
                }
            });
            child.on('error', (error) => {
                reject(new Error(`Failed to start migration script: ${error.message}`));
            });
        });
    }
    findProjectRoot() {
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
exports.DatabaseCommand = DatabaseCommand;
//# sourceMappingURL=database.js.map