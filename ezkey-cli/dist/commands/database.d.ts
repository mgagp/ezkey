import { Command } from 'commander';
import { ConfigManager } from '../config/config-manager';
export declare class DatabaseCommand {
    private config;
    constructor(config: ConfigManager);
    getCommand(): Command;
    private runMigration;
    private runWithJar;
    private runWithScript;
    private findProjectRoot;
}
//# sourceMappingURL=database.d.ts.map