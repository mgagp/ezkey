/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Controller: ApiKeyController
 * Description: REST controller for API key management operations.
 */

package org.ezkey.admin.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;
import org.ezkey.admin.dto.request.ApiKeyCreateRequestDto;
import org.ezkey.admin.dto.response.ApiKeyCreateResponseDto;
import org.ezkey.admin.dto.response.ApiKeyResponseDto;
import org.ezkey.admin.security.AdminOperationsRateLimitService;
import org.ezkey.exception.RateLimitExceededException;
import org.ezkey.integration.domain.entity.ApiKey;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.service.ApiKeyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for API key management operations.
 *
 * <p>This controller provides endpoints for the complete lifecycle of API keys including creation,
 * listing, retrieval, and revocation. All operations are protected by admin authentication.
 *
 * <p><b>Available Operations:</b>
 *
 * <ul>
 *   <li><b>POST /api/v1/api-keys:</b> Create new API key pair
 *   <li><b>GET /api/v1/api-keys/integration/{integrationId}:</b> List keys for integration
 *   <li><b>GET /api/v1/api-keys/{keyId}:</b> Get specific key details
 *   <li><b>DELETE /api/v1/api-keys/{keyId}:</b> Revoke API key
 * </ul>
 *
 * <p><b>Security:</b>
 *
 * <ul>
 *   <li>All endpoints require admin authentication (Bearer token)
 *   <li>Secret keys shown ONLY ONCE during creation
 *   <li>Comprehensive audit logging for all operations
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see ApiKeyService
 */
@RestController
@RequestMapping("/api/v1/api-keys")
@Tag(
    name = "API Keys",
    description = "API key management for machine-to-machine (M2M) authentication")
public class ApiKeyController {

  private static final Logger logger = LoggerFactory.getLogger(ApiKeyController.class);

  private final ApiKeyService apiKeyService;
  private final AdminOperationsRateLimitService adminOpsRateLimitService;

  /**
   * Constructs a new ApiKeyController.
   *
   * @param apiKeyService the API key service
   * @param adminOpsRateLimitService the admin operations rate limiting service
   */
  public ApiKeyController(
      ApiKeyService apiKeyService, AdminOperationsRateLimitService adminOpsRateLimitService) {
    this.apiKeyService = apiKeyService;
    this.adminOpsRateLimitService = adminOpsRateLimitService;
  }

  /**
   * Creates a new API key pair for an integration.
   *
   * <p>This endpoint generates a new API key pair consisting of a public integration key and a
   * private secret key. The secret key is shown ONLY ONCE in the response and must be saved
   * immediately.
   *
   * <p><b>Important:</b> The secret key cannot be retrieved later. If lost, create a new API key.
   *
   * <p><b>Security Features:</b>
   *
   * <ul>
   *   <li>Secret key generated with SecureRandom (160 bits entropy)
   *   <li>BCrypt hashed for storage
   *   <li>Optional IP whitelist for production use
   *   <li>Optional expiration for automatic rotation
   * </ul>
   *
   * @param request the API key creation request
   * @return ResponseEntity containing the new API key pair with secret shown once
   */
  @PreAuthorize("hasRole('ADMIN')")
  @PostMapping
  @Operation(
      summary = "Create new API key pair",
      description =
          "Generates a new API key pair for machine-to-machine authentication. "
              + "The secret key is shown ONLY ONCE and cannot be retrieved later.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "201",
            description = "API key created successfully - SAVE THE SECRET KEY IMMEDIATELY",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiKeyCreateResponseDto.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - admin token required"),
        @ApiResponse(responseCode = "404", description = "Integration not found or inactive"),
        @ApiResponse(
            responseCode = "409",
            description = "Maximum active keys limit reached for integration")
      })
  public ResponseEntity<ApiKeyCreateResponseDto> createApiKey(
      @Valid @RequestBody ApiKeyCreateRequestDto request) {

    logger.info(
        "Creating API key for integration: {} with description: '{}'",
        request.integrationId(),
        request.description());

    // Get authenticated admin from security context
    EzkeyAdmin currentAdmin = getCurrentAdmin();

    // Check rate limiting for admin operations
    String adminId = currentAdmin.getUsername();
    if (!adminOpsRateLimitService.canCreateApiKey(adminId)) {
      throw new RateLimitExceededException("API_KEY_CREATE", 5, 0, 15);
    }

    try {
      // Create API key via service
      ApiKeyService.ApiKeyCreationResult result =
          apiKeyService.createApiKey(
              request.integrationId(),
              currentAdmin,
              request.description(),
              request.expiresAt(),
              request.ipWhitelist());

      // Map to response DTO
      ApiKeyCreateResponseDto response =
          new ApiKeyCreateResponseDto(
              result.getApiKeyId(),
              result.getIntegrationKey(),
              result.getSecretKey(), // Plain text secret - SHOWN ONCE
              result.getDescription(),
              result.getCreatedAt(),
              result.getExpiresAt(),
              result.getIpWhitelist(),
              "IMPORTANT: Save the secret key now. It will not be shown again.");

      logger.info(
          "API key created successfully - ID: {}, Integration: {}, Admin: {}",
          result.getApiKeyId(),
          request.integrationId(),
          currentAdmin.getUsername());

      // Record successful operation for rate limiting
      adminOpsRateLimitService.recordCreateApiKey(adminId);

      return ResponseEntity.status(HttpStatus.CREATED).body(response);

    } catch (IllegalArgumentException e) {
      logger.warn("API key creation failed - Bad request: {}", e.getMessage());
      return ResponseEntity.badRequest().build();
    } catch (IllegalStateException e) {
      logger.warn("API key creation failed - Conflict: {}", e.getMessage());
      return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }
  }

  /**
   * Lists all active API keys for a specific integration.
   *
   * <p>This endpoint returns all active (non-revoked) API keys for the specified integration. The
   * secret keys are never included in the response for security.
   *
   * @param integrationId the integration ID
   * @return ResponseEntity containing list of API keys
   */
  @PreAuthorize("hasRole('ADMIN')")
  @GetMapping("/integration/{integrationId}")
  @Operation(
      summary = "List active API keys for integration",
      description =
          "Returns all active API keys for the specified integration. "
              + "Secret keys are never included in responses.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "List of active API keys",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiKeyResponseDto.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized - admin token required")
      })
  public ResponseEntity<List<ApiKeyResponseDto>> listApiKeys(
      @Parameter(description = "Integration ID", example = "123") @PathVariable("integrationId")
          Integer integrationId) {

    logger.debug("Listing API keys for integration: {}", integrationId);

    List<ApiKey> apiKeys = apiKeyService.listActiveApiKeys(integrationId);

    List<ApiKeyResponseDto> response =
        apiKeys.stream().map(this::mapToResponseDto).collect(Collectors.toList());

    logger.info("Found {} active API keys for integration: {}", response.size(), integrationId);

    return ResponseEntity.ok(response);
  }

  /**
   * Gets details of a specific API key.
   *
   * <p>This endpoint returns the details of a specific API key. The secret key is never included in
   * the response.
   *
   * @param keyId the API key ID
   * @return ResponseEntity containing the API key details
   */
  @PreAuthorize("hasRole('ADMIN')")
  @GetMapping("/{keyId}")
  @Operation(
      summary = "Get API key details",
      description = "Returns details of a specific API key. Secret key is never included.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "API key details",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiKeyResponseDto.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized - admin token required"),
        @ApiResponse(responseCode = "404", description = "API key not found")
      })
  public ResponseEntity<ApiKeyResponseDto> getApiKey(
      @Parameter(description = "API key ID", example = "42") @PathVariable("keyId") Integer keyId) {

    logger.debug("Retrieving API key: {}", keyId);

    return apiKeyService
        .getApiKey(keyId)
        .map(this::mapToResponseDto)
        .map(ResponseEntity::ok)
        .orElseGet(
            () -> {
              logger.warn("API key not found: {}", keyId);
              return ResponseEntity.notFound().build();
            });
  }

  /**
   * Revokes an API key immediately.
   *
   * <p>This endpoint performs immediate revocation of an API key, making it unusable for
   * authentication. The key is preserved for audit purposes with revocation metadata.
   *
   * <p><b>Use Cases:</b>
   *
   * <ul>
   *   <li>Compromised key security incident
   *   <li>Key rotation cleanup (after new key is deployed)
   *   <li>Decommissioning an application
   * </ul>
   *
   * @param keyId the API key ID to revoke
   * @return ResponseEntity with no content on success
   */
  @PreAuthorize("hasRole('ADMIN')")
  @DeleteMapping("/{keyId}")
  @Operation(
      summary = "Revoke API key",
      description =
          "Immediately revokes an API key, making it unusable for authentication. "
              + "The key is preserved for audit purposes.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "204", description = "API key revoked successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - admin token required"),
        @ApiResponse(responseCode = "404", description = "API key not found")
      })
  public ResponseEntity<Void> revokeApiKey(
      @Parameter(description = "API key ID to revoke", example = "42") @PathVariable("keyId")
          Integer keyId) {

    logger.info("Revoking API key: {}", keyId);

    // Get authenticated admin
    EzkeyAdmin currentAdmin = getCurrentAdmin();

    boolean revoked = apiKeyService.revokeApiKey(keyId, currentAdmin);

    if (revoked) {
      logger.info(
          "API key revoked successfully: {} by admin: {}", keyId, currentAdmin.getUsername());
      return ResponseEntity.noContent().build();
    } else {
      logger.warn("API key not found for revocation: {}", keyId);
      return ResponseEntity.notFound().build();
    }
  }

  /**
   * Maps an ApiKey entity to a response DTO.
   *
   * <p>Note: Secret key is never included in the mapping for security.
   *
   * @param apiKey the API key entity
   * @return the response DTO
   */
  private ApiKeyResponseDto mapToResponseDto(ApiKey apiKey) {
    String revokedByUsername = null;
    if (apiKey.getRevokedByAdmin() != null) {
      revokedByUsername = apiKey.getRevokedByAdmin().getUsername();
    }

    return new ApiKeyResponseDto(
        apiKey.getApiKeyId(),
        apiKey.getIntegration().getId(),
        apiKey.getIntegrationKey(),
        apiKey.getDescription(),
        apiKey.getActive(),
        apiKey.getCreatedAt(),
        apiKey.getExpiresAt(),
        apiKey.getLastUsedAt(),
        apiKey.getIpWhitelist(),
        apiKey.getRevokedAt(),
        revokedByUsername);
  }

  /**
   * Gets the currently authenticated admin from security context.
   *
   * <p>This method extracts the EzkeyAdmin from the Spring Security context. In a real
   * implementation, this would load the full admin entity from the database.
   *
   * @return the authenticated admin
   * @throws IllegalStateException if no authentication found
   */
  private EzkeyAdmin getCurrentAdmin() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication == null || !authentication.isAuthenticated()) {
      throw new IllegalStateException("No authenticated admin found");
    }

    // TODO: Load full EzkeyAdmin entity from database using authentication principal
    // For now, create a minimal admin object for testing
    EzkeyAdmin admin = new EzkeyAdmin();
    admin.setUsername(authentication.getName());
    admin.setAdminId(1); // TODO: Get real admin ID from database

    return admin;
  }
}
