/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * SDK: EzkeyClient
 * Description: Main client for Ezkey Java SDK providing unified access to admin and auth APIs
 */

package org.ezkey.sdk;

/**
 * Main client for Ezkey Java SDK.
 * <p>
 * This class provides unified access to both Admin API and Auth API operations
 * through a simple, easy-to-use interface. It encapsulates the complexity of 
 * the underlying generated API clients.
 * </p>
 * 
 * @since 2025
 */
public class EzkeyClient {
    private final EzkeyConfig config;
    private final EzkeyAdminAPI adminAPI;
    private final EzkeyAuthAPI authAPI;
    
    /**
     * Creates a new Ezkey client with the specified configuration.
     *
     * @param config the configuration containing API endpoints
     */
    public EzkeyClient(EzkeyConfig config) {
        this.config = config;
        this.adminAPI = new EzkeyAdminAPI(config);
        this.authAPI = new EzkeyAuthAPI(config);
    }
    
    /**
     * Creates a new Ezkey client with default configuration.
     *
     * @return a client with default localhost configuration
     */
    public static EzkeyClient create() {
        return new EzkeyClient(EzkeyConfig.defaultConfig());
    }
    
    /**
     * Creates a new Ezkey client with custom API URLs.
     *
     * @param adminApiUrl the Admin API base URL
     * @param authApiUrl the Auth API base URL
     * @return a client with the specified configuration
     */
    public static EzkeyClient create(String adminApiUrl, String authApiUrl) {
        return new EzkeyClient(new EzkeyConfig(adminApiUrl, authApiUrl));
    }
    
    /**
     * Gets the Admin API client for managing integrations, enrollments, and auth attempts.
     *
     * @return the admin API client
     */
    public EzkeyAdminAPI admin() {
        return adminAPI;
    }
    
    /**
     * Gets the Auth API client for device enrollment and authentication flows.
     *
     * @return the auth API client
     */
    public EzkeyAuthAPI auth() {
        return authAPI;
    }
    
    /**
     * Gets the current configuration.
     *
     * @return the configuration
     */
    public EzkeyConfig getConfig() {
        return config;
    }
}