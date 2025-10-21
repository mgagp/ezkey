/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: IntegrationAccessControlTest
 * Description: Unit tests for integration access control.
 */

package org.ezkey.admin.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

/**
 * Unit tests for IntegrationAccessControl.
 *
 * <p>Tests the authorization logic for API key integration scoping, ensuring that API keys can only
 * access resources belonging to their associated integration while bearer tokens have full access.
 *
 * @author Ezkey contributors
 * @since 2025
 */
class IntegrationAccessControlTest {

    private IntegrationAccessControl accessControl;

    @BeforeEach
    void setUp() {
        accessControl = new IntegrationAccessControl();
    }

    @Test
    void testVerifyAccess_ApiKeyWithMatchingIntegration_Success() {
        // Given: API key for integration 1
        ApiKeyPrincipal apiKeyAuth =
                new ApiKeyPrincipal(
                        "key123",
                        1,
                        "Integration #1",
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_API_KEY")));

        // When: Accessing integration 1
        // Then: Should succeed without exception
        assertDoesNotThrow(() -> accessControl.verifyAccess(apiKeyAuth, 1));
    }

    @Test
    void testVerifyAccess_ApiKeyWithDifferentIntegration_Forbidden() {
        // Given: API key for integration 1
        ApiKeyPrincipal apiKeyAuth =
                new ApiKeyPrincipal(
                        "key123",
                        1,
                        "Integration #1",
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_API_KEY")));

        // When: Attempting to access integration 2
        // Then: Should throw forbidden exception
        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> accessControl.verifyAccess(apiKeyAuth, 2));

        assertTrue(exception.getMessage().contains("Access denied"));
    }

    @Test
    void testVerifyAccess_BearerToken_FullAccess() {
        // Given: Bearer token (admin user)
        Authentication bearerAuth =
                new UsernamePasswordAuthenticationToken(
                        "admin_user",
                        null,
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")));

        // When: Accessing any integration
        // Then: Should succeed without exception
        assertDoesNotThrow(() -> accessControl.verifyAccess(bearerAuth, 1));
        assertDoesNotThrow(() -> accessControl.verifyAccess(bearerAuth, 2));
        assertDoesNotThrow(() -> accessControl.verifyAccess(bearerAuth, 999));
    }

    @Test
    void testVerifyAccess_NullAuthentication_Forbidden() {
        // Given: Null authentication
        // When: Attempting to access any integration
        // Then: Should throw forbidden exception
        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> accessControl.verifyAccess(null, 1));

        assertTrue(exception.getMessage().contains("Authentication required"));
    }

    @Test
    void testIsApiKey_WithApiKeyPrincipal_ReturnsTrue() {
        // Given: API key authentication
        ApiKeyPrincipal apiKeyAuth =
                new ApiKeyPrincipal(
                        "key123",
                        1,
                        "Integration #1",
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_API_KEY")));

        // When: Checking if it's an API key
        // Then: Should return true
        assertTrue(accessControl.isApiKey(apiKeyAuth));
    }

    @Test
    void testIsApiKey_WithBearerToken_ReturnsFalse() {
        // Given: Bearer token authentication
        Authentication bearerAuth =
                new UsernamePasswordAuthenticationToken(
                        "admin_user",
                        null,
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")));

        // When: Checking if it's an API key
        // Then: Should return false
        assertFalse(accessControl.isApiKey(bearerAuth));
    }

    @Test
    void testGetIntegrationId_WithApiKey_ReturnsIntegrationId() {
        // Given: API key for integration 42
        ApiKeyPrincipal apiKeyAuth =
                new ApiKeyPrincipal(
                        "key123",
                        42,
                        "Integration #42",
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_API_KEY")));

        // When: Getting integration ID
        Integer integrationId = accessControl.getIntegrationId(apiKeyAuth);

        // Then: Should return correct integration ID
        assertEquals(42, integrationId);
    }

    @Test
    void testGetIntegrationId_WithBearerToken_ReturnsNull() {
        // Given: Bearer token authentication
        Authentication bearerAuth =
                new UsernamePasswordAuthenticationToken(
                        "admin_user",
                        null,
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")));

        // When: Getting integration ID
        Integer integrationId = accessControl.getIntegrationId(bearerAuth);

        // Then: Should return null
        assertNull(integrationId);
    }

    @Test
    void testGetIntegrationId_WithNull_ReturnsNull() {
        // Given: Null authentication
        // When: Getting integration ID
        Integer integrationId = accessControl.getIntegrationId(null);

        // Then: Should return null
        assertNull(integrationId);
    }
}
