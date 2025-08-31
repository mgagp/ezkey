/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * SDK: EzkeyAuthAPI
 * Description: Wrapper for Ezkey Auth API providing simplified access to enrollment and authentication flows
 */

package org.ezkey.sdk;

import org.ezkey.sdk.auth.generated.api.AuthAttemptControllerApi;
import org.ezkey.sdk.auth.generated.api.EnrollmentControllerApi;
import org.ezkey.sdk.auth.generated.client.ApiClient;
import org.ezkey.sdk.auth.generated.client.ApiException;
import org.ezkey.sdk.auth.generated.model.*;

/**
 * Wrapper for Ezkey Auth API.
 * <p>
 * This class provides simplified access to the Auth API operations for device
 * enrollment and authentication flows. It wraps the generated OpenAPI client
 * to provide a more user-friendly interface.
 * </p>
 * 
 * @since 2025
 */
public class EzkeyAuthAPI {
    private final ApiClient apiClient;
    private final EnrollmentControllerApi enrollmentApi;
    private final AuthAttemptControllerApi authAttemptApi;
    
    /**
     * Creates a new Auth API wrapper with the specified configuration.
     *
     * @param config the configuration containing API endpoints
     */
    public EzkeyAuthAPI(EzkeyConfig config) {
        this.apiClient = new ApiClient();
        this.apiClient.setBasePath(config.getAuthApiUrl());
        
        this.enrollmentApi = new EnrollmentControllerApi(apiClient);
        this.authAttemptApi = new AuthAttemptControllerApi(apiClient);
    }
    
    // Enrollment Operations
    
    /**
     * Binds a device to an enrollment.
     *
     * @param enrollmentId the enrollment ID to bind
     * @return the enrollment binding information
     * @throws EzkeyException if the API call fails
     */
    public EnrollmentBindResponseDto bindEnrollment(Integer enrollmentId) throws EzkeyException {
        return bindEnrollment(enrollmentId, "en");
    }
    
    /**
     * Binds a device to an enrollment with language preference.
     *
     * @param enrollmentId the enrollment ID to bind
     * @param acceptLanguage the preferred language
     * @return the enrollment binding information
     * @throws EzkeyException if the API call fails
     */
    public EnrollmentBindResponseDto bindEnrollment(Integer enrollmentId, String acceptLanguage) throws EzkeyException {
        try {
            return enrollmentApi.bind(enrollmentId, acceptLanguage);
        } catch (ApiException e) {
            throw new EzkeyException("Failed to bind enrollment", e);
        }
    }
    
    /**
     * Verifies and completes the enrollment process.
     *
     * @param enrollmentId the enrollment ID
     * @param challengeResponse the challenge response from user
     * @param devicePublicKey the device's public key
     * @param enrollmentProofTokenSigned the signed enrollment proof token
     * @return the verification response
     * @throws EzkeyException if the API call fails
     */
    public EnrollmentVerifyResponseDto verifyEnrollment(Integer enrollmentId, Integer challengeResponse, 
            String devicePublicKey, String enrollmentProofTokenSigned) throws EzkeyException {
        try {
            EnrollmentVerifyRequestDto request = new EnrollmentVerifyRequestDto();
            request.setEnrollmentId(enrollmentId);
            request.setChallengeResponse(challengeResponse);
            request.setDevicePublicKey(devicePublicKey);
            request.setEnrollmentProofTokenSigned(enrollmentProofTokenSigned);
            
            return enrollmentApi.verify(request);
        } catch (ApiException e) {
            throw new EzkeyException("Failed to verify enrollment", e);
        }
    }
    
    // Authentication Operations
    
    /**
     * Checks for pending authentication requests.
     *
     * @param enrollmentId the enrollment ID to check
     * @param deviceProofToken the device proof token
     * @param deviceProofTokenSigned the signed device proof token
     * @return the pending authentication details (null if no pending requests)
     * @throws EzkeyException if the API call fails
     */
    public AuthAttemptPendingResponseDto checkPendingAuth(Integer enrollmentId, String deviceProofToken, 
            String deviceProofTokenSigned) throws EzkeyException {
        try {
            AuthAttemptPendingRequestDto request = new AuthAttemptPendingRequestDto();
            request.setEnrollmentId(enrollmentId);
            request.setDeviceProofToken(deviceProofToken);
            request.setDeviceProofTokenSigned(deviceProofTokenSigned);
            
            return authAttemptApi.pending(enrollmentId, request);
        } catch (ApiException e) {
            // Handle 204 No Content as no pending requests
            if (e.getCode() == 204) {
                return null;
            }
            throw new EzkeyException("Failed to check pending auth", e);
        }
    }
    
    /**
     * Responds to an authentication attempt.
     *
     * @param authAttemptId the auth attempt ID
     * @param authAttemptProofTokenSigned the signed auth attempt proof token
     * @param authAttemptAccepted whether to accept or deny the authentication
     * @return the response result
     * @throws EzkeyException if the API call fails
     */
    public AuthAttemptRespondResponseDto respondToAuth(Integer authAttemptId, String authAttemptProofTokenSigned, 
            Boolean authAttemptAccepted) throws EzkeyException {
        return respondToAuth(authAttemptId, authAttemptProofTokenSigned, authAttemptAccepted, null);
    }
    
    /**
     * Responds to an authentication attempt with challenge response.
     *
     * @param authAttemptId the auth attempt ID
     * @param authAttemptProofTokenSigned the signed auth attempt proof token
     * @param authAttemptAccepted whether to accept or deny the authentication
     * @param challengeResponse the challenge response (if required)
     * @return the response result
     * @throws EzkeyException if the API call fails
     */
    public AuthAttemptRespondResponseDto respondToAuth(Integer authAttemptId, String authAttemptProofTokenSigned, 
            Boolean authAttemptAccepted, Integer challengeResponse) throws EzkeyException {
        try {
            AuthAttemptRespondRequestDto request = new AuthAttemptRespondRequestDto();
            request.setAuthAttemptId(authAttemptId);
            request.setAuthAttemptProofTokenSignedByDevice(authAttemptProofTokenSigned);
            request.setAuthAttemptAccepted(authAttemptAccepted);
            request.setAuthAttemptChallengeResponse(challengeResponse);
            
            return authAttemptApi.respond(authAttemptId, request);
        } catch (ApiException e) {
            throw new EzkeyException("Failed to respond to auth attempt", e);
        }
    }
}