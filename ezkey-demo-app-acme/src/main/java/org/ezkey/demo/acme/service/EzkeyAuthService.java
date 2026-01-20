/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: EzkeyAuthService
 * Description: Service for calling EZKey Admin API to create auth attempts and wait for completion.
 */

package org.ezkey.demo.acme.service;

import org.ezkey.demo.acme.config.AcmeProperties;
import org.ezkey.demo.acme.dto.AuthAttemptCreateRequest;
import org.ezkey.demo.acme.dto.AuthAttemptCreateResponse;
import org.ezkey.demo.acme.dto.AuthAttemptWaitResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Service for calling EZKey Admin API to create authentication attempts and wait for completion.
 *
 * <p>This service handles the M2M communication with Admin API using API Key authentication. It
 * creates auth attempts and polls for completion using the wait endpoint.
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Service
public class EzkeyAuthService {

  private static final Logger logger = LoggerFactory.getLogger(EzkeyAuthService.class);

  private final RestTemplate restTemplate;
  private final AcmeProperties properties;

  public EzkeyAuthService(RestTemplate ezkeyRestTemplate, AcmeProperties properties) {
    this.restTemplate = ezkeyRestTemplate;
    this.properties = properties;
  }

  /**
   * Creates a new authentication attempt for the given enrollment.
   *
   * @param enrollmentId the enrollment ID
   * @param challengeRequested whether a challenge code is requested
   * @return AuthAttemptCreateResponse containing authAttemptId
   * @throws EzkeyAuthException if the request fails
   */
  public AuthAttemptCreateResponse createAuthAttempt(
      Integer enrollmentId, Boolean challengeRequested) {
    String url = properties.getAdminApiUrl() + "/api/v1/auth-attempts";

    AuthAttemptCreateRequest request =
        new AuthAttemptCreateRequest(enrollmentId, challengeRequested);

    try {
      logger.info(
          "Creating auth attempt for enrollmentId={}, challengeRequested={}",
          enrollmentId,
          challengeRequested);
      logger.debug("Request body: enrollmentId={}, challengeRequested={}", 
          request.enrollmentId(), request.challengeRequested());

      ResponseEntity<AuthAttemptCreateResponse> response =
          restTemplate.postForEntity(url, request, AuthAttemptCreateResponse.class);

      if (response.getStatusCode() == HttpStatus.CREATED && response.getBody() != null) {
        logger.info(
            "Auth attempt created successfully: authAttemptId={}", response.getBody().authAttemptId());
        return response.getBody();
      } else {
        throw new EzkeyAuthException(
            "Unexpected response status: " + response.getStatusCode());
      }

    } catch (HttpClientErrorException e) {
      String responseBody = e.getResponseBodyAsString();
      logger.error(
          "Failed to create auth attempt: status={}, body={}", e.getStatusCode(), responseBody);
      
      // Provide more helpful error messages
      if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
        logger.error("Authentication failed - possible causes:");
        logger.error("  1. API key credentials are incorrect");
        logger.error("  2. Client IP is not in API key whitelist");
        logger.error("  3. API key has expired");
        logger.error("  4. API key is inactive/revoked");
        throw new EzkeyAuthException(
            "Authentication failed (401). Check API key credentials and IP whitelist. Response: " + responseBody, e);
      }
      
      throw new EzkeyAuthException(
          "Failed to create auth attempt: " + e.getMessage(), e);
    } catch (RestClientException e) {
      logger.error("Error calling Admin API: {}", url, e);
      throw new EzkeyAuthException("Error calling Admin API: " + e.getMessage(), e);
    }
  }

  /**
   * Waits for authentication attempt completion.
   *
   * <p>Polls the wait endpoint until the attempt is completed (approved/rejected) or timeout is
   * reached.
   *
   * @param authAttemptId the authentication attempt ID
   * @param timeoutSeconds timeout in seconds (default 30)
   * @param pollingSeconds polling interval in seconds (default 2)
   * @return AuthAttemptWaitResponse with final status
   * @throws EzkeyAuthException if the request fails or times out
   */
  public AuthAttemptWaitResponse waitForAuthAttempt(
      Integer authAttemptId, Integer timeoutSeconds, Integer pollingSeconds) {
    String url =
        properties.getAdminApiUrl()
            + "/api/v1/auth-attempts/"
            + authAttemptId
            + "/wait?timeout="
            + (timeoutSeconds != null ? timeoutSeconds : 30)
            + "&polling="
            + (pollingSeconds != null ? pollingSeconds : 2);

    try {
      logger.info("Waiting for auth attempt completion: authAttemptId={}", authAttemptId);

      ResponseEntity<AuthAttemptWaitResponse> response =
          restTemplate.getForEntity(url, AuthAttemptWaitResponse.class);

      if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
        AuthAttemptWaitResponse waitResponse = response.getBody();
        logger.info(
            "Auth attempt completed: authAttemptId={}, status={}, completed={}",
            authAttemptId,
            waitResponse.status(),
            waitResponse.completed());

        if (Boolean.TRUE.equals(waitResponse.timeoutReached())) {
          throw new EzkeyAuthException("Authentication timeout - user did not respond in time");
        }

        return waitResponse;
      } else {
        throw new EzkeyAuthException(
            "Unexpected response status: " + response.getStatusCode());
      }

    } catch (HttpClientErrorException e) {
      logger.error(
          "Failed to wait for auth attempt: status={}, body={}",
          e.getStatusCode(),
          e.getResponseBodyAsString());
      throw new EzkeyAuthException(
          "Failed to wait for auth attempt: " + e.getMessage(), e);
    } catch (RestClientException e) {
      logger.error("Error calling Admin API wait endpoint: {}", url, e);
      throw new EzkeyAuthException("Error calling Admin API: " + e.getMessage(), e);
    }
  }

  /**
   * Exception thrown when EZKey authentication operations fail.
   *
   * <p>This exception wraps various failure scenarios (API errors, timeouts, invalid responses).
   */
  public static class EzkeyAuthException extends RuntimeException {
    public EzkeyAuthException(String message) {
      super(message);
    }

    public EzkeyAuthException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}