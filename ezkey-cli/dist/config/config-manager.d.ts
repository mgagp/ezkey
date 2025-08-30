export interface EzkeyConfig {
    adminUrl?: string;
    authUrl?: string;
    simUrl?: string;
    javaPath?: string;
    ezkeyCorePath?: string;
    prettyPrint?: boolean;
    timeout?: number;
}
export declare class ConfigManager {
    private static readonly CONFIG_FILENAME;
    private static readonly HOME_CONFIG_DIR;
    private config;
    constructor();
    /**
     * Load configuration with hierarchical precedence:
     * 1. Current working directory
     * 2. Home directory (~/.ezkey/)
     * 3. Default values
     */
    private loadConfig;
    private mergeConfigFromFile;
    /**
     * Get configuration value
     */
    get(key: keyof EzkeyConfig): any;
    /**
     * Set configuration value (runtime only)
     */
    set(key: keyof EzkeyConfig, value: any): void;
    /**
     * Get the full configuration object
     */
    getAll(): EzkeyConfig;
    /**
     * Save configuration to a specific location
     */
    save(location?: 'home' | 'current'): void;
    /**
     * Override configuration with command line arguments
     */
    override(overrides: Partial<EzkeyConfig>): void;
}
//# sourceMappingURL=config-manager.d.ts.map