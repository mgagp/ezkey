import { Command } from 'commander';
import { ConfigManager } from '../config/config-manager';
export declare class ConfigureCommand {
    private config;
    constructor(config: ConfigManager);
    getCommand(): Command;
    private setConfig;
    private getConfig;
    private resetConfig;
    private interactiveConfig;
}
//# sourceMappingURL=configure.d.ts.map