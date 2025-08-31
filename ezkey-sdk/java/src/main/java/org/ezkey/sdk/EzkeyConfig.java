/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * SDK: EzkeyConfig
 * Description: Configuration class for Ezkey Java SDK
 */

package org.ezkey.sdk;

/**
 * Configuration class for Ezkey Java SDK.
 * <p>
 * This class contains the configuration needed to connect to Ezkey APIs,
 * including admin and auth API endpoints.
 * </p>
 * 
 * @since 2025
 */
public class EzkeyConfig {
    private final String adminApiUrl;
    private final String authApiUrl;
    
    /**
     * Creates a new configuration with the specified API URLs.
     *
     * @param adminApiUrl the base URL for the Admin API (typically port 9080)
     * @param authApiUrl the base URL for the Auth API (typically port 8080)
     */
    public EzkeyConfig(String adminApiUrl, String authApiUrl) {
        this.adminApiUrl = adminApiUrl;
        this.authApiUrl = authApiUrl;
    }
    
    /**
     * Creates a new configuration with default localhost URLs.
     *
     * @return a configuration with default URLs
     */
    public static EzkeyConfig defaultConfig() {
        return new EzkeyConfig("http://localhost:9080", "http://localhost:8080");
    }
    
    /**
     * Gets the Admin API base URL.
     *
     * @return the admin API URL
     */
    public String getAdminApiUrl() {
        return adminApiUrl;
    }
    
    /**
     * Gets the Auth API base URL.
     *
     * @return the auth API URL
     */
    public String getAuthApiUrl() {
        return authApiUrl;
    }
}