import { Command } from 'commander';
import { ConfigManager } from '../config/config-manager';
export declare class AdminCommand {
    private config;
    private httpClient;
    constructor(config: ConfigManager);
    getCommand(): Command;
    private listIntegrations;
    private getIntegration;
    private createIntegration;
    private deleteIntegration;
    private listEnrollments;
    private getEnrollment;
    private createEnrollment;
    private deleteEnrollment;
    private listAuthAttempts;
    private getAuthAttempt;
    private createAuthAttempt;
    private waitForAuthAttempt;
    private outputResponse;
}
//# sourceMappingURL=admin.d.ts.map