/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Component: IntegrationAccessControl
 * Description: Authorization helper for verifying API key access to integration resources.
 */

package org.ezkey.admin.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Authorization helper for verifying API key access to integration resources.
 *
 * <p>This component implements integration-scoped authorization checks for API keys, ensuring that
 * API keys can only access resources (enrollments, auth attempts) belonging to their associated
 * integration. Bearer tokens (human administrators) are exempt from these checks and have full
 * access.
 *
 * <p><b>Authorization Model:</b>
 *
 * <ul>
 *   <li><b>API Keys:</b> Restricted to resources of their integration only
 *   <li><b>Bearer Tokens:</b> Full access across all integrations (human administrators)
 *   <li><b>Principle of Least Privilege:</b> API keys get minimum necessary access
 * </ul>
 *
 * <p><b>Usage in Controllers:</b>
 *
 * <pre>{@code
 * @PostMapping
 * public ResponseEntity<?> createAuthAttempt(@RequestBody CreateAuthAttemptRequest req) {
 *     Enrollment enrollment = enrollmentService.findById(req.enrollmentId());
 *     accessControl.verifyAccess(
 *         SecurityContextHolder.getContext().getAuthentication(),
 *         enrollment.getIntegrationId()
 *     );
 *     // ... rest of logic
 * }
 * }</pre>
 *
 * <p><b>Security Features:</b>
 *
 * <ul>
 *   <li><b>Transparent for Bearer Tokens:</b> Admin tokens bypass checks automatically
 *   <li><b>Non-invasive:</b> Single line per sensitive endpoint
 *   <li><b>Fail-secure:</b> Denies access on any validation failure
 *   <li><b>Comprehensive Logging:</b> All access decisions are logged
 * </ul>
 *
 * <p><b>Error Handling:</b> Throws {@link ResponseStatusException} with HTTP 403 Forbidden when
 * access is denied. This provides clear feedback while preventing information leakage about valid
 * integration IDs.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see ApiKeyPrincipal
 * @see ApiKeyAuthenticationFilter
 */
@Component
public class IntegrationAccessControl {

    private static final Logger logger = LoggerFactory.getLogger(IntegrationAccessControl.class);

    /**
     * Verifies that the current authentication has access to the specified integration.
     *
     * <p>This method checks if the authenticated principal is an API key and, if so, verifies that
     * the API key's integration matches the required integration. Bearer tokens (admin users) are
     * allowed access to all integrations.
     *
     * <p><b>Authorization Rules:</b>
     *
     * <ul>
     *   <li>If authentication is {@link ApiKeyPrincipal}, verify integration ID matches
     *   <li>If authentication is any other type (Bearer token), allow access
     *   <li>If authentication is null or invalid, deny access
     * </ul>
     *
     * @param authentication the current authentication object from security context
     * @param requiredIntegrationId the integration ID that must be accessible
     * @throws ResponseStatusException with HTTP 403 if access is denied
     */
    public void verifyAccess(Authentication authentication, Integer requiredIntegrationId) {
        // Handle null authentication
        if (authentication == null) {
            logger.warn("❌ Access denied: No authentication present");
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Authentication required to access this integration");
        }

        // Check if this is API key authentication (scoped)
        if (authentication instanceof ApiKeyPrincipal apiKeyPrincipal) {
            Integer apiKeyIntegrationId = apiKeyPrincipal.getIntegrationId();

            // Verify API key integration matches required integration
            if (!apiKeyIntegrationId.equals(requiredIntegrationId)) {
                logger.warn(
                        "❌ Access denied: API key for integration {} attempted to access integration {}",
                        apiKeyIntegrationId,
                        requiredIntegrationId);
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Access denied: This API key cannot access resources from this integration");
            }

            logger.debug(
                    "✅ Access granted: API key for integration {} accessing own resources",
                    apiKeyIntegrationId);
        } else {
            // Bearer token (admin user) - full access
            logger.debug(
                    "✅ Access granted: Bearer token accessing integration {}",
                    requiredIntegrationId);
        }
    }

    /**
     * Checks if the current authentication is an API key (as opposed to a bearer token).
     *
     * <p>This is useful for conditional logic where behavior differs between API keys and admin
     * tokens.
     *
     * @param authentication the current authentication object
     * @return true if authentication is an API key, false otherwise
     */
    public boolean isApiKey(Authentication authentication) {
        return authentication instanceof ApiKeyPrincipal;
    }

    /**
     * Gets the integration ID from an API key authentication, or null for bearer tokens.
     *
     * <p>This is useful for audit logging or conditional logic based on the authentication type.
     *
     * @param authentication the current authentication object
     * @return the integration ID for API keys, or null for bearer tokens
     */
    public Integer getIntegrationId(Authentication authentication) {
        if (authentication instanceof ApiKeyPrincipal apiKeyPrincipal) {
            return apiKeyPrincipal.getIntegrationId();
        }
        return null;
    }
}
