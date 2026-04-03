/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: AdminRecoveryService
 * Description: Service for admin account recovery using single-use recovery codes.
 */

package org.ezkey.admin.service;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.ezkey.admin.config.AdminRecoveryProperties;
import org.ezkey.admin.constants.AdminAuditConstants;
import org.ezkey.admin.exception.AuthenticationException;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.integration.domain.entity.AdminToken;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.repository.AdminTokenRepository;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.ezkey.security.SensitiveDataHasher;
import org.ezkey.signature.SignatureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for admin account recovery using single-use recovery codes.
 *
 * <p>This service manages the generation, validation, and rotation of recovery codes that provide
 * emergency access when an administrator's device is lost or unavailable. Recovery codes are
 * single-use and grant limited temporary access for enrollment re-binding.
 *
 * <p><b>Security Model:</b>
 *
 * <ul>
 *   <li>5 recovery codes per admin by default (configurable)
 *   <li>Format: XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX (32 digits)
 *   <li>BCrypt hashed storage (same security as passwords)
 *   <li>Single-use: Code removed from array after successful use
 *   <li>Limited access: Recovery token valid 30 minutes, enrollment binding only
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Service
@Transactional
public class AdminRecoveryService {

  private static final Logger logger = LoggerFactory.getLogger(AdminRecoveryService.class);

  private static final String RECOVERY_CODE_CHARS =
      "0123456789"; // Digits only (106-bit entropy with 32 digits)
  private final EzkeyAdminRepository adminRepository;
  private final AdminTokenRepository tokenRepository;
  private final EnrollmentRepository enrollmentRepository;
  private final SignatureService signatureService;
  private final BCryptPasswordEncoder passwordEncoder;
  private final AdminRecoveryProperties recoveryProperties;
  private final SecureRandom secureRandom;

  public AdminRecoveryService(
      EzkeyAdminRepository adminRepository,
      AdminTokenRepository tokenRepository,
      EnrollmentRepository enrollmentRepository,
      SignatureService signatureService,
      BCryptPasswordEncoder passwordEncoder,
      AdminRecoveryProperties recoveryProperties) {
    this.adminRepository = adminRepository;
    this.tokenRepository = tokenRepository;
    this.enrollmentRepository = enrollmentRepository;
    this.signatureService = signatureService;
    this.passwordEncoder = passwordEncoder;
    this.recoveryProperties = recoveryProperties;
    this.secureRandom = new SecureRandom();
  }

  /**
   * Generate recovery codes for an administrator.
   *
   * <p>Generates the configured number of single-use recovery codes in format
   * XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX. Each code contains 32 digits providing 106 bits of
   * entropy (paranoia-level security). Codes are cryptographically secure and BCrypt hashed before
   * storage.
   *
   * @return RecoveryCodesResult containing plain codes (for display) and hashed codes (for storage)
   */
  public RecoveryCodesResult generateRecoveryCodes() {
    List<String> plainCodes = new ArrayList<>();
    List<String> hashedCodes = new ArrayList<>();

    int codesCount = recoveryProperties.getCodesCount();
    for (int i = 0; i < codesCount; i++) {
      String plainCode = generateSingleRecoveryCode();
      String hashedCode = passwordEncoder.encode(plainCode);

      plainCodes.add(plainCode);
      hashedCodes.add(hashedCode);
    }

    logger.info("✅ Generated {} recovery codes", codesCount);

    return new RecoveryCodesResult(plainCodes, hashedCodes);
  }

  /**
   * Generate a single recovery code in format XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX.
   *
   * <p>Uses SecureRandom for cryptographic security. Format uses digits only (0-9) for unambiguous
   * entry. 32 digits provide 106 bits of entropy - paranoia-level security resistant to brute force
   * and quantum attacks.
   *
   * <p>Security: 10^32 combinations = ~3 × 10^18 years at 1M attempts/sec
   *
   * @return recovery code string (e.g., "1234-5678-9012-3456-7890-1234-5678-9012")
   */
  private String generateSingleRecoveryCode() {
    StringBuilder code = new StringBuilder();

    // Generate 8 groups of 4 digits (32 total)
    for (int segment = 0; segment < 8; segment++) {
      if (segment > 0) {
        code.append("-");
      }
      for (int i = 0; i < 4; i++) {
        int index = secureRandom.nextInt(RECOVERY_CODE_CHARS.length());
        code.append(RECOVERY_CODE_CHARS.charAt(index));
      }
    }

    return code.toString();
  }

  /**
   * Validate recovery code and issue temporary recovery token.
   *
   * <p>Validates the recovery code against the admin's stored hashed codes. If valid, marks the
   * code as used (removes from array) and issues a temporary token with limited permissions
   * (enrollment binding only).
   *
   * @param username the administrator username
   * @param recoveryCode the plain recovery code to validate
   * @return temporary recovery token (30-minute validity)
   * @throws AuthenticationException if validation fails
   */
  public String validateRecoveryCode(String username, String recoveryCode) {
    logger.info("🔑 Recovery code validation attempt for admin: {}", username);

    // 1. Find admin
    EzkeyAdmin admin =
        adminRepository
            .findByUsername(username)
            .orElseThrow(() -> new AuthenticationException("Invalid credentials"));

    if (!admin.getActive()) {
      throw new AuthenticationException("Account is inactive");
    }

    // 2. Check if admin has recovery codes
    if (admin.getRecoveryCodes() == null || admin.getRecoveryCodes().length == 0) {
      logger.warn("❌ No recovery codes available for admin: {}", username);
      throw new AuthenticationException("No recovery codes available for this account");
    }

    // 3. Validate recovery code against hashed codes
    List<String> remainingCodes = new ArrayList<>();
    boolean codeFound = false;

    for (String hashedCode : admin.getRecoveryCodes()) {
      if (!codeFound && passwordEncoder.matches(recoveryCode, hashedCode)) {
        // Code matched - mark as used (don't add to remaining codes)
        codeFound = true;
        logger.info("✅ Recovery code validated for admin: {}", username);
      } else {
        // Keep unused codes
        remainingCodes.add(hashedCode);
      }
    }

    if (!codeFound) {
      logger.warn("❌ Invalid recovery code for admin: {}", username);
      throw new AuthenticationException("Invalid recovery code");
    }

    // 4. Update admin with remaining codes (single-use enforcement)
    admin.setRecoveryCodes(remainingCodes.toArray(new String[0]));
    adminRepository.save(admin);

    logger.warn(
        "🔑 Recovery code used for admin: {} ({} codes remaining)",
        username,
        remainingCodes.size());

    // 5. Generate temporary recovery bearer token (limited permissions, configurable duration)
    String recoveryToken =
        AdminAuditConstants.RECOVERY_TOKEN_PREFIX + UUID.randomUUID().toString().replace("-", "");
    String tokenHash = SensitiveDataHasher.sha256Hex(recoveryToken);
    if (tokenHash == null) {
      throw new IllegalStateException("Recovery token hash could not be computed");
    }
    OffsetDateTime expiresAt =
        OffsetDateTime.now().plusMinutes(recoveryProperties.getTempTokenDurationMinutes());

    AdminToken token = new AdminToken(tokenHash, admin, admin.getAdminType().name(), expiresAt);
    token.setTenant(admin.getTenant());
    token.setIntegration(admin.getIntegration());
    token.setCreatedAt(OffsetDateTime.now());
    token.setActive(true);
    tokenRepository.save(token);

    logger.info(
        "✅ Recovery token issued for admin: {} (expires: {}, {} recovery codes remaining)",
        username,
        expiresAt,
        remainingCodes.size());

    return recoveryToken;
  }

  /**
   * Validate recovery token and return associated admin.
   *
   * <p>Validates that the token is a recovery token (prefix check), exists in database, is active,
   * and not expired.
   *
   * @param recoveryToken the recovery token to validate
   * @return the associated admin if token is valid
   * @throws org.ezkey.admin.exception.AuthenticationException if token is invalid
   */
  public EzkeyAdmin validateRecoveryToken(String recoveryToken) {
    logger.debug(
        "🔍 Validating recovery token: {}...",
        recoveryToken.substring(0, Math.min(15, recoveryToken.length())));

    // 1. Verify it's a recovery token
    if (!recoveryToken.startsWith(AdminAuditConstants.RECOVERY_TOKEN_PREFIX)) {
      logger.warn("❌ Invalid token format (not a recovery token)");
      throw new org.ezkey.admin.exception.AuthenticationException("Invalid recovery token");
    }

    // 2. Find recovery token in bearer tokens table (lookup by hash)
    String tokenHash = SensitiveDataHasher.sha256Hex(recoveryToken);
    if (tokenHash == null) {
      throw new org.ezkey.admin.exception.AuthenticationException("Invalid recovery token");
    }
    AdminToken token =
        tokenRepository
            .findByBearerTokenHashAndActiveTrue(tokenHash)
            .orElseThrow(
                () ->
                    new org.ezkey.admin.exception.AuthenticationException(
                        "Invalid or expired recovery token"));

    // 3. Check expiration
    if (token.getExpiresAt().isBefore(OffsetDateTime.now())) {
      logger.warn("❌ Recovery token expired for admin: {}", token.getAdmin().getUsername());
      throw new org.ezkey.admin.exception.AuthenticationException("Recovery token expired");
    }

    // 4. Verify it's a recovery token
    if (!recoveryToken.startsWith(AdminAuditConstants.RECOVERY_TOKEN_PREFIX)) {
      logger.warn("❌ Token is not a recovery token");
      throw new org.ezkey.admin.exception.AuthenticationException("Invalid recovery token");
    }

    logger.debug("✅ Recovery token validated for admin: {}", token.getAdmin().getUsername());

    return token.getAdmin();
  }

  /**
   * Rotate recovery codes for an administrator.
   *
   * <p>Generates a new configured set of recovery codes, invalidating all previous codes. This is
   * intended for explicit operator-driven regeneration.
   *
   * @param admin the administrator to rotate codes for
   * @return RecoveryCodesResult with new plain codes (for display)
   */
  public RecoveryCodesResult rotateRecoveryCodes(EzkeyAdmin admin) {
    logger.info("🔄 Rotating recovery codes for admin: {}", admin.getUsername());

    RecoveryCodesResult result = generateRecoveryCodes();
    admin.setRecoveryCodes(result.getHashedCodes().toArray(new String[0]));
    adminRepository.save(admin);

    logger.info("✅ Recovery codes rotated for admin: {}", admin.getUsername());

    return result;
  }

  /**
   * Reset enrollment after device loss - unbind old device and generate new credentials.
   *
   * <p>This method unbinds the old device (clears device_public_key) and generates new enrollment
   * credentials (proof token and challenge) so the administrator can bind a new replacement device.
   *
   * <p><b>Security:</b> This operation invalidates the old device immediately, preventing a lost or
   * stolen device from being used for authentication.
   *
   * @param enrollmentId the enrollment ID to reset
   * @param admin the administrator who owns the enrollment (from recovery token)
   * @return the reset enrollment with new credentials
   * @throws org.ezkey.admin.exception.AuthenticationException if enrollment doesn't belong to admin
   */
  public Enrollment resetEnrollment(Integer enrollmentId, EzkeyAdmin admin) {
    logger.warn("🔄 Resetting enrollment {} for admin: {}", enrollmentId, admin.getUsername());

    // 1. Fetch enrollment
    Enrollment enrollment =
        enrollmentRepository
            .findById(enrollmentId)
            .orElseThrow(() -> new IllegalArgumentException("Enrollment not found"));

    // 2. Verify admin owns this enrollment (security check)
    if (admin.getEnrollment() == null
        || !admin.getEnrollment().getEnrollmentId().equals(enrollmentId)) {
      logger.error(
          "❌ Admin {} attempted to reset enrollment {} which doesn't belong to them",
          admin.getUsername(),
          enrollmentId);
      throw new org.ezkey.admin.exception.AuthenticationException(
          "You don't have permission to reset this enrollment");
    }

    // 3. Reset enrollment (unbind device)
    enrollment.setDevicePublicKey(null); // Unbind old device
    enrollment.setStatus(EnrollmentStatus.CREATED); // Back to initial state

    // 4. Generate new credentials
    String newProofToken = signatureService.generateProofToken();
    Integer newChallenge = signatureService.generateSecureChallenge(6);

    enrollment.setEnrollmentProofToken(newProofToken);
    enrollment.setEnrollmentChallenge(newChallenge);

    // 5. Save enrollment
    enrollmentRepository.save(enrollment);

    logger.warn(
        "✅ Enrollment reset successful (ID: {}, old device unbound, new credentials generated)",
        enrollmentId);
    logger.warn("🔐 New proof token: {}", newProofToken);
    logger.warn("🔐 New challenge: {}", newChallenge);

    return enrollment;
  }

  /** Result object containing plain and hashed recovery codes. */
  public static class RecoveryCodesResult {
    private final List<String> plainCodes;
    private final List<String> hashedCodes;

    public RecoveryCodesResult(List<String> plainCodes, List<String> hashedCodes) {
      this.plainCodes = plainCodes;
      this.hashedCodes = hashedCodes;
    }

    public List<String> getPlainCodes() {
      return plainCodes;
    }

    public List<String> getHashedCodes() {
      return hashedCodes;
    }
  }
}
