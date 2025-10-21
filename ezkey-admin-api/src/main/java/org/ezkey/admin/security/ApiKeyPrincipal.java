/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Security: ApiKeyPrincipal
 * Description: Authentication principal for API key authentication with integration scoping.
 */

package org.ezkey.admin.security;

import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

/**
 * Authentication principal for API key authentication with integration scoping.
 *
 * <p>This class extends the standard authentication token to include integration ID information,
 * enabling integration-scoped authorization checks. This implements the principle of least
 * privilege by restricting API keys to only access resources belonging to their associated
 * integration.
 *
 * <p><b>Security Model:</b>
 *
 * <ul>
 *   <li><b>Integration Scoping:</b> Each API key is bound to a specific integration
 *   <li><b>Resource Isolation:</b> API keys can only access enrollments and auth attempts for
 *       their integration
 *   <li><b>Bearer Token Bypass:</b> Bearer tokens (human admins) have full access across all
 *       integrations
 * </ul>
 *
 * <p><b>Usage in Controllers:</b>
 *
 * <pre>{@code
 * Authentication auth = SecurityContextHolder.getContext().getAuthentication();
 * if (auth instanceof ApiKeyPrincipal apiKey) {
 *     Integer integrationId = apiKey.getIntegrationId();
 *     // Verify resource belongs to this integration
 * }
 * }</pre>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see ApiKeyAuthenticationFilter
 * @see IntegrationAccessControl
 */
public class ApiKeyPrincipal extends AbstractAuthenticationToken {

    private final String apiKeyId;
    private final Integer integrationId;
    private final String integrationName;

    /**
     * Constructs a new ApiKeyPrincipal with integration scoping.
     *
     * @param apiKeyId the API key identifier (for audit logging)
     * @param integrationId the integration ID this API key belongs to
     * @param integrationName the integration name (for display/logging)
     * @param authorities the granted authorities for this API key
     */
    public ApiKeyPrincipal(
            String apiKeyId,
            Integer integrationId,
            String integrationName,
            Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.apiKeyId = apiKeyId;
        this.integrationId = integrationId;
        this.integrationName = integrationName;
        setAuthenticated(true);
    }

    /**
     * Gets the API key identifier.
     *
     * @return the API key ID
     */
    public String getApiKeyId() {
        return apiKeyId;
    }

    /**
     * Gets the integration ID this API key is scoped to.
     *
     * <p>This ID should be used for authorization checks to ensure the API key only accesses
     * resources belonging to its integration.
     *
     * @return the integration ID
     */
    public Integer getIntegrationId() {
        return integrationId;
    }

    /**
     * Gets the integration name for logging and display purposes.
     *
     * @return the integration name
     */
    public String getIntegrationName() {
        return integrationName;
    }

    /**
     * Returns null as credentials are not stored after authentication.
     *
     * @return null
     */
    @Override
    public Object getCredentials() {
        return null;
    }

    /**
     * Returns the principal identifier for logging and audit purposes.
     *
     * <p>Format: "api_key:integration_{integrationId}"
     *
     * @return the principal identifier
     */
    @Override
    public Object getPrincipal() {
        return "api_key:integration_" + integrationId;
    }

    @Override
    public String toString() {
        return "ApiKeyPrincipal{"
                + "integrationId="
                + integrationId
                + ", integrationName='"
                + integrationName
                + '\''
                + ", authorities="
                + getAuthorities()
                + '}';
    }
}
