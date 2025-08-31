import { EzkeyConfig } from './config';
import { EzkeyAdminAPI } from './admin-api';
import { EzkeyAuthAPI } from './auth-api';
/**
 * Main client for Ezkey JavaScript/TypeScript SDK.
 * Provides unified access to both Admin API and Auth API operations
 * through a simple, easy-to-use interface.
 */
export declare class EzkeyClient {
    private readonly config;
    private readonly adminAPI;
    private readonly authAPI;
    constructor(config?: EzkeyConfig);
    /**
     * Creates a new Ezkey client with default configuration.
     */
    static create(): EzkeyClient;
    /**
     * Creates a new Ezkey client with custom API URLs.
     */
    static createWithUrls(adminApiUrl: string, authApiUrl: string): EzkeyClient;
    /**
     * Gets the Admin API client for managing integrations, enrollments, and auth attempts.
     */
    admin(): EzkeyAdminAPI;
    /**
     * Gets the Auth API client for device enrollment and authentication flows.
     */
    auth(): EzkeyAuthAPI;
    /**
     * Gets the current configuration.
     */
    getConfig(): EzkeyConfig;
}
//# sourceMappingURL=client.d.ts.map