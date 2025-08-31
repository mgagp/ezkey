/**
 * Configuration interface for Ezkey JavaScript/TypeScript SDK.
 * Contains the configuration needed to connect to Ezkey APIs.
 */
export interface EzkeyConfig {
    /** Base URL for the Admin API (typically port 9080) */
    adminApiUrl: string;
    /** Base URL for the Auth API (typically port 8080) */
    authApiUrl: string;
}
/**
 * Creates a default configuration with localhost URLs.
 * @returns Configuration with default URLs
 */
export declare function createDefaultConfig(): EzkeyConfig;
//# sourceMappingURL=config.d.ts.map