import { Command } from 'commander';
import { ConfigManager } from '../config/config-manager';
export declare class AuthCommand {
    private config;
    private httpClient;
    constructor(config: ConfigManager);
    getCommand(): Command;
    private bindEnrollment;
    private verifyEnrollment;
    private checkPending;
    private respondToAuthAttempt;
    private outputResponse;
}
//# sourceMappingURL=auth.d.ts.map