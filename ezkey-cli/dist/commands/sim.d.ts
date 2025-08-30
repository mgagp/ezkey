import { Command } from 'commander';
import { ConfigManager } from '../config/config-manager';
export declare class SimCommand {
    private config;
    private httpClient;
    constructor(config: ConfigManager);
    getCommand(): Command;
    private generateProofToken;
    private generateKeyPair;
    private signData;
    private validateSignature;
    private outputResponse;
}
//# sourceMappingURL=sim.d.ts.map