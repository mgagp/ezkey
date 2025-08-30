import { Command } from 'commander';
import { ConfigManager } from '../config/config-manager';
export declare class OpenApiCommand {
    private config;
    private httpClient;
    constructor(config: ConfigManager);
    getCommand(): Command;
    private refreshSpecs;
    private refreshAdminSpec;
    private refreshAuthSpec;
    private downloadAndSaveSpec;
    private findProjectRoot;
}
//# sourceMappingURL=openapi.d.ts.map