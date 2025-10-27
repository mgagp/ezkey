/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * SDK: EzkeyAdminAPI
 * Description: Wrapper for Ezkey Admin API providing simplified access to integrations, enrollments, and auth attempts
 */

package org.ezkey.sdk;

import org.ezkey.sdk.admin.generated.api.AuthAttemptsApi;
import org.ezkey.sdk.admin.generated.api.EnrollmentsApi;
import org.ezkey.sdk.admin.generated.api.IntegrationsApi;
import org.ezkey.sdk.admin.generated.client.ApiClient;
import org.ezkey.sdk.admin.generated.client.ApiException;
import org.ezkey.sdk.admin.generated.model.*;

import java.util.List;

/**
 * Wrapper for Ezkey Admin API.
 * <p>
 * This class provides simplified access to the Admin API operations for managing
 * integrations, enrollments, and authentication attempts. It wraps the generated
 * OpenAPI client to provide a more user-friendly interface.
 * </p>
 * 
 * @since 2025
 */
public class EzkeyAdminAPI {
    private final ApiClient apiClient;
    private final IntegrationsApi integrationsApi;
    private final EnrollmentsApi enrollmentsApi;
    private final AuthAttemptsApi authAttemptsApi;
    
    /**
     * Creates a new Admin API wrapper with the specified configuration.
     *
     * @param config the configuration containing API endpoints
     */
    public EzkeyAdminAPI(EzkeyConfig config) {
        this.apiClient = new ApiClient();
        this.apiClient.setBasePath(config.getAdminApiUrl());
        
        this.integrationsApi = new IntegrationsApi(apiClient);
        this.enrollmentsApi = new EnrollmentsApi(apiClient);
        this.authAttemptsApi = new AuthAttemptsApi(apiClient);
    }
    
    // Integration Management
    
    /**
     * Creates a new integration.
     *
     * @param logo the logo URL for the integration
     * @param name the integration name
     * @param description the integration description
     * @return the created integration response
     * @throws EzkeyException if the API call fails
     */
    public IntegrationCreateResponseDto createIntegration(String logo, String name, String description) throws EzkeyException {
        try {
            // Create i18n entry using record constructor
            IntegrationI18nCreateDto i18n = new IntegrationI18nCreateDto("en", name, description);
            
            // Create request using record constructor
            IntegrationCreateRequestDto request = new IntegrationCreateRequestDto(logo, java.util.Arrays.asList(i18n));
            
            return integrationsApi.create(request);
        } catch (ApiException e) {
            throw new EzkeyException("Failed to create integration", e);
        }
    }
    
    /**
     * Gets all integrations.
     *
     * @return list of all integrations
     * @throws EzkeyException if the API call fails
     */
    public List<IntegrationResponseDto> getAllIntegrations() throws EzkeyException {
        try {
            return integrationsApi.getAll();
        } catch (ApiException e) {
            throw new EzkeyException("Failed to get integrations", e);
        }
    }
    
    /**
     * Gets an integration by ID.
     *
     * @param integrationId the integration ID
     * @return the integration details
     * @throws EzkeyException if the API call fails
     */
    public IntegrationResponseDto getIntegration(Integer integrationId) throws EzkeyException {
        try {
            return integrationsApi.getById(integrationId);
        } catch (ApiException e) {
            throw new EzkeyException("Failed to get integration", e);
        }
    }
    
    /**
     * Deletes an integration.
     *
     * @param integrationId the integration ID to delete
     * @throws EzkeyException if the API call fails
     */
    public void deleteIntegration(Integer integrationId) throws EzkeyException {
        try {
            integrationsApi.delete(integrationId);
        } catch (ApiException e) {
            throw new EzkeyException("Failed to delete integration", e);
        }
    }
    
    // Enrollment Management
    
    /**
     * Creates a new enrollment.
     *
     * @param integrationId the integration ID
     * @param name the enrollment name (e.g., "John's iPhone")
     * @param challengeRequired whether challenge is required for auth attempts
     * @return the created enrollment response
     * @throws EzkeyException if the API call fails
     */
    public EnrollmentCreateResponseDto createEnrollment(Integer integrationId, String name, Boolean challengeRequired) throws EzkeyException {
        try {
            EnrollmentCreateRequestDto request = new EnrollmentCreateRequestDto();
            request.setIntegrationId(integrationId);
            request.setName(name);
            request.setAuthAttemptChallengeRequired(challengeRequired);
            
            return enrollmentsApi.create1(request);
        } catch (ApiException e) {
            throw new EzkeyException("Failed to create enrollment", e);
        }
    }
    
    /**
     * Gets all enrollments.
     *
     * @return list of all enrollments
     * @throws EzkeyException if the API call fails
     */
    public List<EnrollmentResponseDto> getAllEnrollments() throws EzkeyException {
        try {
            return enrollmentsApi.getAll1();
        } catch (ApiException e) {
            throw new EzkeyException("Failed to get enrollments", e);
        }
    }
    
    /**
     * Gets an enrollment by ID.
     *
     * @param enrollmentId the enrollment ID
     * @return the enrollment details
     * @throws EzkeyException if the API call fails
     */
    public EnrollmentResponseDto getEnrollment(Integer enrollmentId) throws EzkeyException {
        try {
            return enrollmentsApi.getById1(enrollmentId);
        } catch (ApiException e) {
            throw new EzkeyException("Failed to get enrollment", e);
        }
    }
    
    /**
     * Deletes an enrollment.
     *
     * @param enrollmentId the enrollment ID to delete
     * @throws EzkeyException if the API call fails
     */
    public void deleteEnrollment(Integer enrollmentId) throws EzkeyException {
        try {
            enrollmentsApi.delete1(enrollmentId);
        } catch (ApiException e) {
            throw new EzkeyException("Failed to delete enrollment", e);
        }
    }
    
    // Auth Attempt Management
    
    /**
     * Creates a new authentication attempt.
     *
     * @param enrollmentId the enrollment ID
     * @param challengeRequested whether challenge is requested
     * @return the created auth attempt response
     * @throws EzkeyException if the API call fails
     */
    public AuthAttemptCreateResponseDto createAuthAttempt(Integer enrollmentId, Boolean challengeRequested) throws EzkeyException {
        try {
            AuthAttemptCreateRequestDto request = new AuthAttemptCreateRequestDto()
                .enrollmentId(enrollmentId)
                .challengeRequested(challengeRequested);
            
            return authAttemptsApi.create2(request);
        } catch (ApiException e) {
            throw new EzkeyException("Failed to create auth attempt", e);
        }
    }
    
    /**
     * Gets all authentication attempts.
     *
     * @return list of all auth attempts
     * @throws EzkeyException if the API call fails
     */
    public List<AuthAttemptDto> getAllAuthAttempts() throws EzkeyException {
        try {
            return authAttemptsApi.getAll2();
        } catch (ApiException e) {
            throw new EzkeyException("Failed to get auth attempts", e);
        }
    }
    
    /**
     * Gets an authentication attempt by ID.
     *
     * @param authAttemptId the auth attempt ID
     * @return the auth attempt details
     * @throws EzkeyException if the API call fails
     */
    public AuthAttemptDto getAuthAttempt(Integer authAttemptId) throws EzkeyException {
        try {
            return authAttemptsApi.getById2(authAttemptId);
        } catch (ApiException e) {
            throw new EzkeyException("Failed to get auth attempt", e);
        }
    }
    
    /**
     * Waits for authentication response with default timeout (30s) and polling (2s).
     *
     * @param authAttemptId the auth attempt ID
     * @return the wait response containing final status
     * @throws EzkeyException if the API call fails
     */
    public AuthAttemptWaitResponseDto waitForResponse(Integer authAttemptId) throws EzkeyException {
        return waitForResponse(authAttemptId, "30", "2");
    }
    
    /**
     * Waits for authentication response with custom timeout and polling.
     *
     * @param authAttemptId the auth attempt ID
     * @param timeout maximum wait duration in seconds
     * @param polling polling interval in seconds
     * @return the wait response containing final status
     * @throws EzkeyException if the API call fails
     */
    public AuthAttemptWaitResponseDto waitForResponse(Integer authAttemptId, String timeout, String polling) throws EzkeyException {
        try {
            return authAttemptsApi.waitForResponse(authAttemptId, timeout, polling);
        } catch (ApiException e) {
            throw new EzkeyException("Failed to wait for auth response", e);
        }
    }
    
    /**
     * Deletes an authentication attempt.
     *
     * @param authAttemptId the auth attempt ID to delete
     * @throws EzkeyException if the API call fails
     */
    public void deleteAuthAttempt(Integer authAttemptId) throws EzkeyException {
        try {
            authAttemptsApi.delete2(authAttemptId);
        } catch (ApiException e) {
            throw new EzkeyException("Failed to delete auth attempt", e);
        }
    }
}