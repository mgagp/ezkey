/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: AuthAttemptService
 * Description: Business service for managing Ezkey authentication attempts via Admin API.
 */

package org.ezkey.demo.acme.service;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.ezkey.demo.acme.generated.dto.AuthAttemptCreateRequestDto;
import org.ezkey.demo.acme.generated.dto.AuthAttemptCreateResponseDto;
import org.ezkey.demo.acme.generated.dto.AuthAttemptDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Business service for managing Ezkey authentication attempts via Admin API.
 *
 * <p>Provides high-level operations to list, create, retrieve and delete authentication attempts.
 * This demo service delegates to the Ezkey Admin API using a configured {@link WebClient}.
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Service
public class AuthAttemptService {

  private static final Logger logger = LoggerFactory.getLogger(AuthAttemptService.class);

  private final WebClient ezkeyAdminApiClient;

  /**
   * Constructs the authentication attempt service with required dependencies.
   *
   * @param ezkeyAdminApiClient the WebClient configured for Ezkey Admin API
   */
  public AuthAttemptService(@Qualifier("ezkeyAdminApiClient") WebClient ezkeyAdminApiClient) {
    this.ezkeyAdminApiClient = ezkeyAdminApiClient;
  }

  /**
   * Creates a new authentication attempt.
   *
   * <p>Delegates to the Ezkey Admin API to create an authentication attempt for the specified
   * enrollment. The enrollment must exist and be active.
   *
   * @param request the authentication attempt creation request
   * @return Mono containing the created authentication attempt response
   * @throws WebClientResponseException if the API call fails
   */
  public Mono<AuthAttemptCreateResponseDto> createAuthAttempt(AuthAttemptCreateRequestDto request) {
    logger.debug("Creating authentication attempt for enrollment: {}", request.getEnrollmentId());

    return ezkeyAdminApiClient
        .post()
        .uri("/api/v1/auth-attempts")
        .bodyValue(request)
        .retrieve()
        .bodyToMono(AuthAttemptCreateResponseDto.class)
        .doOnSuccess(
            response ->
                logger.info(
                    "Successfully created authentication attempt with ID: {}",
                    response.getAuthAttemptId()))
        .doOnError(
            error ->
                logger.error(
                    "Failed to create authentication attempt for enrollment {}: {}",
                    request.getEnrollmentId(),
                    error.getMessage()));
  }

  /**
   * Retrieves all authentication attempts.
   *
   * <p>Delegates to the Ezkey Admin API to retrieve all authentication attempts. Returns a reactive
   * stream of authentication attempt DTOs.
   *
   * @return Flux containing all authentication attempts
   * @throws WebClientResponseException if the API call fails
   */
  public Flux<AuthAttemptDto> getAllAuthAttempts() {
    logger.debug("Retrieving all authentication attempts");

    return ezkeyAdminApiClient
        .get()
        .uri("/api/v1/auth-attempts")
        .retrieve()
        .bodyToFlux(AuthAttemptDto.class)
        .doOnComplete(() -> logger.debug("Successfully retrieved all authentication attempts"))
        .doOnError(
            error ->
                logger.error("Failed to retrieve authentication attempts: {}", error.getMessage()));
  }

  /**
   * Retrieves all authentication attempts as a List (blocking).
   *
   * <p>Convenience method that converts the reactive Flux to a blocking List. Use this method when
   * you need a synchronous result.
   *
   * @return List containing all authentication attempts
   * @throws WebClientResponseException if the API call fails
   */
  public List<AuthAttemptDto> getAllAuthAttemptsSync() {
    return getAllAuthAttempts().collectList().block(Duration.ofSeconds(10));
  }

  /**
   * Retrieves authentication attempts filtered by enrollment ID.
   *
   * <p>Delegates to the Ezkey Admin API to retrieve authentication attempts for a specific
   * enrollment.
   *
   * @param enrollmentId the enrollment ID to filter by
   * @return Flux containing authentication attempts for the enrollment
   * @throws WebClientResponseException if the API call fails
   */
  public Flux<AuthAttemptDto> getAuthAttemptsByEnrollmentId(Integer enrollmentId) {
    logger.debug("Retrieving authentication attempts for enrollment: {}", enrollmentId);

    return ezkeyAdminApiClient
        .get()
        .uri("/api/v1/auth-attempts?enrollmentId={enrollmentId}", enrollmentId)
        .retrieve()
        .bodyToFlux(AuthAttemptDto.class)
        .doOnComplete(
            () ->
                logger.debug(
                    "Successfully retrieved authentication attempts for enrollment: {}",
                    enrollmentId))
        .doOnError(
            error ->
                logger.error(
                    "Failed to retrieve authentication attempts for enrollment {}: {}",
                    enrollmentId,
                    error.getMessage()));
  }

  /**
   * Retrieves authentication attempts filtered by enrollment ID as a List (blocking).
   *
   * <p>Convenience method that converts the reactive Flux to a blocking List. Use this method when
   * you need a synchronous result.
   *
   * @param enrollmentId the enrollment ID to filter by
   * @return List containing authentication attempts for the enrollment
   * @throws WebClientResponseException if the API call fails
   */
  public List<AuthAttemptDto> getAuthAttemptsByEnrollmentIdSync(Integer enrollmentId) {
    return getAuthAttemptsByEnrollmentId(enrollmentId).collectList().block(Duration.ofSeconds(10));
  }

  /**
   * Retrieves a specific authentication attempt by ID.
   *
   * <p>Delegates to the Ezkey Admin API to retrieve a specific authentication attempt.
   *
   * @param authAttemptId the authentication attempt ID
   * @return Mono containing the authentication attempt DTO
   * @throws WebClientResponseException if the API call fails or the attempt is not found
   */
  public Mono<AuthAttemptDto> getAuthAttemptById(Integer authAttemptId) {
    logger.debug("Retrieving authentication attempt with ID: {}", authAttemptId);

    return ezkeyAdminApiClient
        .get()
        .uri("/api/v1/auth-attempts/{id}", authAttemptId)
        .retrieve()
        .bodyToMono(AuthAttemptDto.class)
        .doOnSuccess(
            response ->
                logger.debug(
                    "Successfully retrieved authentication attempt with ID: {}", authAttemptId))
        .doOnError(
            error ->
                logger.error(
                    "Failed to retrieve authentication attempt with ID {}: {}",
                    authAttemptId,
                    error.getMessage()));
  }

  /**
   * Deletes an authentication attempt by ID.
   *
   * <p>Delegates to the Ezkey Admin API to delete a specific authentication attempt.
   *
   * @param authAttemptId the authentication attempt ID to delete
   * @return Mono<Void> indicating completion
   * @throws WebClientResponseException if the API call fails or the attempt is not found
   */
  public Mono<Void> deleteAuthAttempt(Integer authAttemptId) {
    logger.debug("Deleting authentication attempt with ID: {}", authAttemptId);

    return ezkeyAdminApiClient
        .delete()
        .uri("/api/v1/auth-attempts/{id}", authAttemptId)
        .retrieve()
        .bodyToMono(Void.class)
        .doOnSuccess(
            response ->
                logger.info(
                    "Successfully deleted authentication attempt with ID: {}", authAttemptId))
        .doOnError(
            error ->
                logger.error(
                    "Failed to delete authentication attempt with ID {}: {}",
                    authAttemptId,
                    error.getMessage()));
  }

  /**
   * Retrieves a specific authentication attempt by ID synchronously.
   *
   * <p>Convenience method that converts the reactive Mono to a blocking result. Use this method
   * when you need a synchronous result.
   *
   * @param authAttemptId the authentication attempt ID
   * @return the authentication attempt DTO or null if not found
   * @throws WebClientResponseException if the API call fails
   */
  public AuthAttemptDto getAuthAttemptSync(Integer authAttemptId) {
    return getAuthAttemptById(authAttemptId).block(Duration.ofSeconds(10));
  }

  /**
   * Executes a wait operation for an authentication attempt.
   *
   * <p>Delegates to the Ezkey Admin API to wait for authentication completion with the specified
   * timeout and polling parameters.
   *
   * @param authAttemptId the authentication attempt ID
   * @param timeout the maximum wait duration in seconds
   * @param polling the polling interval in seconds
   * @return Map containing the wait results
   * @throws WebClientResponseException if the API call fails
   */
  public Map<String, Object> waitForAuthAttempt(
      Integer authAttemptId, Integer timeout, Integer polling) {
    logger.debug(
        "Executing wait operation for auth attempt {} with timeout={}s, polling={}s",
        authAttemptId,
        timeout,
        polling);

    try {
      String uri =
          String.format(
              "/api/v1/auth-attempts/%d/wait?timeout=%d&polling=%d",
              authAttemptId, timeout, polling);

      Map<String, Object> result =
          ezkeyAdminApiClient
              .get()
              .uri(uri)
              .retrieve()
              .bodyToMono(Map.class)
              .block(Duration.ofSeconds(timeout + 10)); // Add buffer for network overhead

      if (result == null) {
        result = new HashMap<>();
        result.put("error", "No response received from wait operation");
      }

      logger.info("Wait operation completed for auth attempt {}: {}", authAttemptId, result);
      return result;

    } catch (Exception e) {
      logger.error("Wait operation failed for auth attempt {}: {}", authAttemptId, e.getMessage());
      Map<String, Object> errorResult = new HashMap<>();
      errorResult.put("error", "Wait operation failed: " + e.getMessage());
      return errorResult;
    }
  }
}
