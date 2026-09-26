/*
 * Ezkey - Open Source Cryptographic MFA Platform
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
import org.ezkey.admin.config.AdminTokenRotationProperties;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.integration.domain.AdminTokenPurpose;
import org.ezkey.integration.domain.entity.AdminToken;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.repository.AdminTokenRepository;
import org.ezkey.security.SensitiveDataHasher;
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
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
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
  private final AdminTokenRotationProperties rotationProperties;

  public AdminTokenValidationService(
      AdminTokenRepository tokenRepository, AdminTokenRotationProperties rotationProperties) {
    this.tokenRepository = tokenRepository;
    this.rotationProperties = rotationProperties;
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
      String hash = SensitiveDataHasher.sha256Hex(token);
      if (hash == null) {
        return Optional.empty();
      }
      logger.debug("🔍 Validating bearer token (hash prefix): {}...", hash.substring(0, 8));

      Optional<AdminToken> tokenOptional =
          tokenRepository.findByBearerTokenHashAndActiveTrueWithRelations(hash);

      if (tokenOptional.isPresent()) {
        AdminToken adminToken = tokenOptional.get();

        // SEC-021: recovery tokens must not authenticate as Admin API sessions
        if (adminToken.getTokenPurpose() == AdminTokenPurpose.RECOVERY) {
          logger.warn(
              "❌ Token rejected: recovery-purpose token cannot authenticate ordinary Admin API"
                  + " requests");
          return Optional.empty();
        }

        // Check if token is expired
        if (adminToken.getExpiresAt().isAfter(OffsetDateTime.now())) {
          EzkeyAdmin admin = adminToken.getAdmin();

          // Force loading of admin properties within transaction
          admin.getUsername();
          admin.getAdminType();
          admin.getActive();

          // Check if admin's tenant is still active
          if (adminToken.getTenant() != null && !adminToken.getTenant().getActive()) {
            logger.warn("❌ Token rejected: tenant inactive for admin: {}", admin.getUsername());
            return Optional.empty();
          }

          // Check if the admin's enrollment is still active.
          // Defence-in-depth: EnrollmentRevocationService already invalidates tokens
          // immediately on revocation, but this guard catches any window where the
          // enrollment was deactivated without explicit token revocation.
          // BOOTSTRAP sessions may exist before first enrollment (PENDING_ACTIVATION).
          Enrollment enrollment = admin.getEnrollment();
          if (enrollment != null && !Boolean.TRUE.equals(enrollment.getActive())) {
            logger.warn("❌ Token rejected: enrollment inactive for admin: {}", admin.getUsername());
            return Optional.empty();
          }

          logger.debug("✅ Token validated successfully for admin: {}", admin.getUsername());
          return Optional.of(adminToken);
        } else {
          logger.warn("❌ Token expired");
        }
      } else {
        logger.warn("❌ Invalid token");
      }
    } catch (Exception e) { // CHECKSTYLE IGNORE IllegalCatch
      logger.error("❌ Error validating token: {}", e.getMessage());
    }

    return Optional.empty();
  }

  /**
   * Updates the last used timestamp and, for non-recovery tokens, extends expiration (sliding
   * expiration).
   *
   * <p>Looks up the token by bearer hash, then delegates to {@link
   * #updateTokenLastUsed(AdminToken)}. Prefer the entity overload on the authenticated request hot
   * path when {@link #validateTokenWithRelations(String)} already loaded the row (JavaMelody A1 /
   * JM-001: avoid a second SELECT).
   *
   * @param token the bearer token to update
   * @return updated expiration timestamp when the token was found and updated
   */
  @Transactional
  public Optional<OffsetDateTime> updateTokenLastUsed(String token) {
    try {
      String hash = SensitiveDataHasher.sha256Hex(token);
      if (hash == null) {
        return Optional.empty();
      }
      Optional<AdminToken> tokenOptional = tokenRepository.findByBearerTokenHashAndActiveTrue(hash);
      if (tokenOptional.isPresent()) {
        return updateTokenLastUsed(tokenOptional.get());
      }
    } catch (Exception e) { // CHECKSTYLE IGNORE IllegalCatch
      logger.error("❌ Error updating token timestamp: {}", e.getMessage());
    }
    return Optional.empty();
  }

  /**
   * Updates last used and sliding expiration on an already-loaded {@link AdminToken}.
   *
   * <p>Does not re-query by bearer hash. For SESSION tokens, expiration is extended to now +
   * expirationHours. Recovery tokens update {@code lastUsedAt} only (SEC-021: never extend).
   *
   * @param adminToken the loaded token entity (must be managed or attachable for save)
   * @return updated expiration timestamp when the update succeeds; empty if {@code adminToken} is
   *     null or the update fails
   */
  @Transactional
  public Optional<OffsetDateTime> updateTokenLastUsed(AdminToken adminToken) {
    if (adminToken == null) {
      return Optional.empty();
    }
    try {
      OffsetDateTime now = OffsetDateTime.now();
      adminToken.setLastUsedAt(now);
      // Sliding expiration for SESSION tokens only.
      // SEC-021: never extend RECOVERY. V-2026-09-26: never extend BOOTSTRAP (absolute 8h).
      if (adminToken.getTokenPurpose() == AdminTokenPurpose.SESSION) {
        int hours = Math.max(1, rotationProperties.getExpirationHours());
        adminToken.setExpiresAt(now.plusHours(hours));
      }
      tokenRepository.save(adminToken);
      logger.debug("✅ Updated last used and expiration for token");
      return Optional.ofNullable(adminToken.getExpiresAt());
    } catch (Exception e) { // CHECKSTYLE IGNORE IllegalCatch
      logger.error("❌ Error updating token timestamp: {}", e.getMessage());
    }
    return Optional.empty();
  }
}
