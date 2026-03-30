/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: EnrollmentUpdateService
 * Description: Business logic for partial update of enrollment metadata (PATCH).
 */

package org.ezkey.admin.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.ezkey.admin.dto.request.EnrollmentUpdateRequestDto;
import org.ezkey.audit.util.AuditDetailsBuilder;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for partial update of enrollment metadata.
 *
 * <p>Implements PATCH semantics for enrollment metadata: enrollmentName, contactEmail, expiresAt,
 * authAttemptChallengeRequired, userIdentifier. Only active, non-revoked VERIFIED enrollments can
 * be updated.
 *
 * <p><b>Constraints:</b>
 *
 * <ul>
 *   <li>Enrollment must be VERIFIED and active (not revoked, not deactivated)
 *   <li>enrollmentName: must be unique per (integration_id, name) for VERIFIED status
 *   <li>expiresAt: must be in the future if provided; null = no expiration
 * </ul>
 *
 * <p><b>Optimistic Locking:</b> Uses {@code version} from the request to detect concurrent
 * modifications. On mismatch, throws {@link ObjectOptimisticLockingFailureException} (409
 * Conflict).
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Service
public class EnrollmentUpdateService {

  private static final Logger logger = LoggerFactory.getLogger(EnrollmentUpdateService.class);

  /**
   * Practical email shape check for non-blank contact emails (Bean Validation {@code @Email} is
   * applied in service so PATCH can accept clears without failing validation on absent fields).
   */
  private static final Pattern CONTACT_EMAIL_PATTERN =
      Pattern.compile("^[\\w.+-]+@[\\w.-]+\\.[a-zA-Z]{2,}$");

  private final EnrollmentRepository enrollmentRepository;

  /**
   * Constructs the EnrollmentUpdateService with required dependencies.
   *
   * @param enrollmentRepository repository for enrollment persistence
   */
  public EnrollmentUpdateService(EnrollmentRepository enrollmentRepository) {
    this.enrollmentRepository = enrollmentRepository;
  }

  /**
   * Partially updates an enrollment's metadata.
   *
   * <p>Only non-null fields in the request are applied. The enrollment must be VERIFIED and active.
   *
   * @param enrollmentId the enrollment ID to update
   * @param request the partial update request
   * @return outcome with updated entity and JSON for audit {@code event_details}
   * @throws ResourceNotFoundException if enrollment not found
   * @throws IllegalArgumentException if enrollment is not updatable (revoked, inactive) or
   *     validation fails (name uniqueness, expiresAt in past)
   * @throws ObjectOptimisticLockingFailureException if version mismatch (stale)
   */
  @Transactional
  public EnrollmentUpdateOutcome updateEnrollment(
      Integer enrollmentId, EnrollmentUpdateRequestDto request) {
    Enrollment enrollment =
        enrollmentRepository
            .findById(enrollmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Enrollment", enrollmentId));

    // Only active, non-revoked VERIFIED enrollments can be updated
    if (enrollment.getStatus() != EnrollmentStatus.VERIFIED) {
      throw new IllegalArgumentException(
          "Enrollment cannot be updated: status is "
              + enrollment.getStatus()
              + ". Only VERIFIED enrollments can be updated.");
    }
    if (!Boolean.TRUE.equals(enrollment.getActive())) {
      throw new IllegalArgumentException(
          "Enrollment cannot be updated: enrollment is inactive. Reactivate before updating.");
    }
    if (enrollment.getRevokedAt() != null) {
      throw new IllegalArgumentException(
          "Enrollment cannot be updated: enrollment has been permanently revoked.");
    }

    // Optimistic lock check
    if (request.version() != null && !request.version().equals(enrollment.getVersion())) {
      throw new ObjectOptimisticLockingFailureException(Enrollment.class, enrollmentId);
    }

    String previousName = enrollment.getEnrollmentName();
    String previousContactEmail = enrollment.getContactEmail();
    OffsetDateTime previousExpiresAt = enrollment.getExpiresAt();
    Boolean previousChallengeRequired = enrollment.getAuthAttemptChallengeRequired();
    String previousUserIdentifier = enrollment.getUserIdentifier();

    // Validate and apply enrollmentName (uniqueness per integration for VERIFIED)
    if (request.enrollmentName() != null) {
      List<Enrollment> existing =
          enrollmentRepository.findByIntegrationIdAndEnrollmentNameAndStatusAndEnrollmentIdNot(
              enrollment.getIntegrationId(),
              request.enrollmentName(),
              EnrollmentStatus.VERIFIED,
              enrollmentId);
      if (!existing.isEmpty()) {
        throw new IllegalArgumentException(
            "Enrollment name '"
                + request.enrollmentName()
                + "' already exists for this integration. Choose a different name.");
      }
      enrollment.setEnrollmentName(request.enrollmentName());
    }

    // Apply contactEmail (clear flag, explicit blank, or new value)
    if (Boolean.TRUE.equals(request.clearContactEmail())) {
      enrollment.setContactEmail(null);
    } else if (request.contactEmail() != null) {
      String trimmed = request.contactEmail().trim();
      if (trimmed.isEmpty()) {
        enrollment.setContactEmail(null);
      } else {
        if (!isValidContactEmail(trimmed)) {
          throw new IllegalArgumentException("Invalid email format");
        }
        enrollment.setContactEmail(trimmed);
      }
    }

    // Apply expiresAt: non-null instant wins; else optional clear
    if (request.expiresAt() != null) {
      OffsetDateTime now = OffsetDateTime.now();
      if (!request.expiresAt().isAfter(now)) {
        throw new IllegalArgumentException(
            "expiresAt must be in the future. Got: " + request.expiresAt());
      }
      enrollment.setExpiresAt(request.expiresAt());
    } else if (Boolean.TRUE.equals(request.clearExpiresAt())) {
      enrollment.setExpiresAt(null);
    }

    // Apply authAttemptChallengeRequired
    if (request.authAttemptChallengeRequired() != null) {
      enrollment.setAuthAttemptChallengeRequired(request.authAttemptChallengeRequired());
    }

    if (request.userIdentifier() != null) {
      String trimmed = request.userIdentifier().trim();
      enrollment.setUserIdentifier(trimmed.isEmpty() ? null : trimmed);
    }

    enrollment = enrollmentRepository.save(enrollment);
    logger.info(
        "Enrollment {} metadata updated (integrationId: {})",
        enrollmentId,
        enrollment.getIntegrationId());

    String auditJson =
        buildAuditEventDetailsJson(
            enrollmentId,
            enrollment.getIntegrationId(),
            request,
            previousName,
            previousContactEmail,
            previousExpiresAt,
            previousChallengeRequired,
            previousUserIdentifier,
            enrollment);

    return new EnrollmentUpdateOutcome(enrollment, auditJson);
  }

  private static String buildAuditEventDetailsJson(
      Integer enrollmentId,
      Integer integrationId,
      EnrollmentUpdateRequestDto request,
      String previousName,
      String previousContactEmail,
      OffsetDateTime previousExpiresAt,
      Boolean previousChallengeRequired,
      String previousUserIdentifier,
      Enrollment updated) {

    AuditDetailsBuilder builder = AuditDetailsBuilder.builder();
    builder.custom("enrollment_id", enrollmentId);
    builder.custom("integration_id", integrationId);

    List<Map<String, Object>> changes = new ArrayList<>();

    if (request.enrollmentName() != null
        && !Objects.equals(previousName, updated.getEnrollmentName())) {
      changes.add(changeEntry("enrollmentName", previousName, updated.getEnrollmentName()));
    }
    boolean contactEmailTouched =
        Boolean.TRUE.equals(request.clearContactEmail()) || request.contactEmail() != null;
    if (contactEmailTouched && !Objects.equals(previousContactEmail, updated.getContactEmail())) {
      changes.add(changeEntry("contactEmail", previousContactEmail, updated.getContactEmail()));
    }
    boolean expiresAtTouched =
        request.expiresAt() != null || Boolean.TRUE.equals(request.clearExpiresAt());
    if (expiresAtTouched && !Objects.equals(previousExpiresAt, updated.getExpiresAt())) {
      changes.add(
          changeEntry(
              "expiresAt",
              previousExpiresAt != null ? previousExpiresAt.toString() : null,
              updated.getExpiresAt() != null ? updated.getExpiresAt().toString() : null));
    }
    if (request.authAttemptChallengeRequired() != null
        && !Objects.equals(previousChallengeRequired, updated.getAuthAttemptChallengeRequired())) {
      changes.add(
          changeEntry(
              "authAttemptChallengeRequired",
              previousChallengeRequired,
              updated.getAuthAttemptChallengeRequired()));
    }
    if (request.userIdentifier() != null
        && !Objects.equals(previousUserIdentifier, updated.getUserIdentifier())) {
      changes.add(
          changeEntry("userIdentifier", previousUserIdentifier, updated.getUserIdentifier()));
    }

    builder.custom("changes", changes);
    return builder.toJson();
  }

  private static boolean isValidContactEmail(String email) {
    return email != null && email.length() <= 255 && CONTACT_EMAIL_PATTERN.matcher(email).matches();
  }

  private static Map<String, Object> changeEntry(String field, Object previous, Object newValue) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("field", field);
    row.put("previous", previous);
    row.put("new", newValue);
    return row;
  }
}
