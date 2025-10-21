/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Utility: IntegrationAccessControl
 * Description: Security utility for detecting and controlling API key authentication.
 */

package org.ezkey.admin.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * Security utility for detecting and controlling API key authentication.
 *
 * <p>This utility class provides methods to identify whether a request is authenticated with an API
 * key versus a bearer token (admin user), enabling fine-grained access control based on
 * authentication method.
 *
 * <p><b>Use Cases:</b>
 *
 * <ul>
 *   <li>Restrict API keys from enrollment management operations
 *   <li>Allow API keys for authentication attempt operations only
 *   <li>Enforce different access patterns for M2M vs human administrators
 * </ul>
 *
 * <p><b>Authentication Detection:</b>
 *
 * <p>API key authentication is identified by the presence of the ROLE_ADMIN authority when set by
 * the ApiKeyAuthenticationFilter. The principal for API keys follows the pattern
 * "integration_{id}", while bearer token authentication uses different principal formats.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see ApiKeyAuthenticationFilter
 */
@Component
public class IntegrationAccessControl {

    /**
     * Role assigned to API key authenticated requests.
     *
     * <p>This role is set by ApiKeyAuthenticationFilter when a request is successfully
     * authenticated using API key credentials via HTTP Basic Auth.
     */
    private static final String ROLE_API_KEY = "ROLE_ADMIN";

    /**
     * Prefix used for integration-based principals.
     *
     * <p>API key authentication uses principals in the format "integration_{id}" to distinguish
     * them from bearer token authentication.
     */
    private static final String INTEGRATION_PRINCIPAL_PREFIX = "integration_";

    /**
     * Checks if the current authentication is from an API key.
     *
     * <p>Determines if the request is authenticated using an API key (machine-to-machine) rather
     * than a bearer token (human administrator). This allows for different access control rules
     * based on authentication method.
     *
     * <p><b>Detection Logic:</b>
     *
     * <ol>
     *   <li>Check if authentication exists and is authenticated
     *   <li>Verify the principal starts with "integration_" prefix
     *   <li>Confirm ROLE_ADMIN authority is present
     * </ol>
     *
     * <p><b>Usage Example:</b>
     *
     * <pre>{@code
     * Authentication auth = SecurityContextHolder.getContext().getAuthentication();
     * if (accessControl.isApiKey(auth)) {
     *     throw new ResponseStatusException(HttpStatus.FORBIDDEN,
     *         "API keys cannot manage enrollments");
     * }
     * }</pre>
     *
     * @param authentication the authentication object from the security context
     * @return true if authenticated with an API key, false otherwise
     */
    public boolean isApiKey(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof String)) {
            return false;
        }

        String principalStr = (String) principal;

        // API keys use "integration_{id}" as principal
        if (!principalStr.startsWith(INTEGRATION_PRINCIPAL_PREFIX)) {
            return false;
        }

        // Verify ROLE_ADMIN is present (set by ApiKeyAuthenticationFilter)
        boolean hasAdminRole =
                authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .anyMatch(ROLE_API_KEY::equals);

        return hasAdminRole;
    }
}
