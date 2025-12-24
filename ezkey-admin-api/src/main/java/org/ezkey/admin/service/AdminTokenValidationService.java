/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: AdminTokenValidationService
 * Description: Service for validating admin bearer tokens with proper transaction management.
 */

package org.ezkey.admin.service;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.ezkey.integration.domain.entity.AdminToken;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.repository.AdminTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for validating admin bearer tokens.
 *
 * <p>This service provides transaction-aware token validation to avoid lazy loading issues when
 * accessing JPA entity relationships.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Service
public class AdminTokenValidationService {

  private static final Logger logger = LoggerFactory.getLogger(AdminTokenValidationService.class);

  private final AdminTokenRepository tokenRepository;

  public AdminTokenValidationService(AdminTokenRepository tokenRepository) {
    this.tokenRepository = tokenRepository;
  }

  /**
   * Validates a bearer token and returns the associated admin if valid.
   *
   * <p>This method is transaction-aware and properly handles JPA entity relationships.
   *
   * @param token the bearer token to validate
   * @return Optional containing the admin if token is valid, empty otherwise
   * @deprecated Use validateTokenWithRelations instead to get AdminToken with tenant/integration
   *     loaded
   */
  @Deprecated
  @Transactional(readOnly = true)
  public Optional<EzkeyAdmin> validateToken(String token) {
    Optional<AdminToken> tokenOpt = validateTokenWithRelations(token);
    return tokenOpt.map(AdminToken::getAdmin);
  }

  /**
   * Validates a bearer token and returns the AdminToken with all relations loaded if valid.
   *
   * <p>This method eagerly loads admin, tenant, and integration to support AdminPrincipal creation
   * without lazy loading issues.
   *
   * @param token the bearer token to validate
   * @return Optional containing the AdminToken with relations if token is valid, empty otherwise
   */
  @Transactional(readOnly = true)
  public Optional<AdminToken> validateTokenWithRelations(String token) {
    try {
      logger.debug(
          "🔍 Validating bearer token: {}...", token.substring(0, Math.min(10, token.length())));

      Optional<AdminToken> tokenOptional =
          tokenRepository.findByBearerTokenAndActiveTrueWithRelations(token);

      if (tokenOptional.isPresent()) {
        AdminToken adminToken = tokenOptional.get();

        // Check if token is expired
        if (adminToken.getExpiresAt().isAfter(OffsetDateTime.now())) {
          EzkeyAdmin admin = adminToken.getAdmin();

          // Force loading of admin properties within transaction
          admin.getUsername();
          admin.getAdminType();
          admin.getActive();

          logger.debug("✅ Token validated successfully for admin: {}", admin.getUsername());
          return Optional.of(adminToken);
        } else {
          logger.warn("❌ Token expired for: {}", token.substring(0, Math.min(10, token.length())));
        }
      } else {
        logger.warn("❌ Invalid token: {}", token.substring(0, Math.min(10, token.length())));
      }
    } catch (Exception e) {
      logger.error("❌ Error validating token: {}", e.getMessage());
    }

    return Optional.empty();
  }

  /**
   * Updates the last used timestamp for a token.
   *
   * <p>This method requires a separate transaction for the update operation.
   *
   * @param token the bearer token to update
   */
  @Transactional
  public void updateTokenLastUsed(String token) {
    try {
      Optional<AdminToken> tokenOptional = tokenRepository.findByBearerTokenAndActiveTrue(token);
      if (tokenOptional.isPresent()) {
        AdminToken adminToken = tokenOptional.get();
        adminToken.setLastUsedAt(OffsetDateTime.now());
        tokenRepository.save(adminToken);
        logger.debug("✅ Updated last used timestamp for token");
      }
    } catch (Exception e) {
      logger.error("❌ Error updating token timestamp: {}", e.getMessage());
    }
  }
}
