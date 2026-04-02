/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: EnrollmentUpdateServiceTest
 * Description: Unit tests for enrollment metadata partial update (PATCH).
 */

package org.ezkey.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.ezkey.admin.dto.request.EnrollmentUpdateRequestDto;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Unit tests for {@link EnrollmentUpdateService}.
 *
 * @author Ezkey contributors
 * @since 2025
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Enrollment Update Service Tests")
class EnrollmentUpdateServiceTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Mock private EnrollmentRepository enrollmentRepository;

  @InjectMocks private EnrollmentUpdateService enrollmentUpdateService;

  private Enrollment enrollment;

  @BeforeEach
  void setUp() {
    enrollment = new Enrollment();
    enrollment.setEnrollmentId(1);
    enrollment.setIntegrationId(10);
    enrollment.setEnrollmentName("Original Name");
    enrollment.setStatus(EnrollmentStatus.VERIFIED);
    enrollment.setActive(true);
    enrollment.setVersion(0L);
    enrollment.setContactEmail("original@example.com");
    enrollment.setAuthAttemptChallengeRequired(false);
    enrollment.setRevokedAt(null);
  }

  private static JsonNode changeForField(JsonNode changesArray, String field) {
    for (JsonNode entry : changesArray) {
      if (field.equals(entry.path("field").asString())) {
        return entry;
      }
    }
    throw new AssertionError("No change entry for field: " + field);
  }

  @Test
  @DisplayName("updateEnrollment - happy path applies non-null fields")
  void updateEnrollment_happyPath_appliesNonNullFields() throws Exception {
    when(enrollmentRepository.findById(1)).thenReturn(Optional.of(enrollment));
    when(enrollmentRepository.findByIntegrationIdAndEnrollmentNameAndStatusAndEnrollmentIdNot(
            eq(10), eq("New Name"), eq(EnrollmentStatus.VERIFIED), eq(1)))
        .thenReturn(Collections.emptyList());
    when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(i -> i.getArgument(0));

    EnrollmentUpdateRequestDto request =
        new EnrollmentUpdateRequestDto(
            0L, "New Name", "new@example.com", null, null, true, null, null, null);

    EnrollmentUpdateOutcome outcome = enrollmentUpdateService.updateEnrollment(1, request);

    assertThat(outcome.enrollment().getEnrollmentName()).isEqualTo("New Name");
    assertThat(outcome.enrollment().getContactEmail()).isEqualTo("new@example.com");
    assertThat(outcome.enrollment().getAuthAttemptChallengeRequired()).isTrue();

    JsonNode root = objectMapper.readTree(outcome.auditEventDetailsJson());
    assertThat(root.get("enrollment_id").asInt()).isEqualTo(1);
    assertThat(root.get("integration_id").asInt()).isEqualTo(10);
    JsonNode changes = root.get("changes");
    assertThat(changes.isArray()).isTrue();
    assertThat(changes.size()).isEqualTo(3);
    assertThat(changeForField(changes, "enrollmentName").path("previous").asString())
        .isEqualTo("Original Name");
    assertThat(changeForField(changes, "enrollmentName").path("new").asString())
        .isEqualTo("New Name");
    assertThat(changeForField(changes, "contactEmail").path("previous").asString())
        .isEqualTo("original@example.com");
    assertThat(changeForField(changes, "contactEmail").path("new").asString())
        .isEqualTo("new@example.com");
    assertThat(changeForField(changes, "authAttemptChallengeRequired").get("previous").asBoolean())
        .isFalse();
    assertThat(changeForField(changes, "authAttemptChallengeRequired").get("new").asBoolean())
        .isTrue();
    verify(enrollmentRepository).save(enrollment);
  }

  @Test
  @DisplayName("updateEnrollment - applies userIdentifier and audit lists change")
  void updateEnrollment_userIdentifier_auditContainsField() throws Exception {
    when(enrollmentRepository.findById(1)).thenReturn(Optional.of(enrollment));
    when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(i -> i.getArgument(0));

    EnrollmentUpdateRequestDto request =
        new EnrollmentUpdateRequestDto(0L, null, null, null, null, null, "app-user-99", null, null);

    EnrollmentUpdateOutcome outcome = enrollmentUpdateService.updateEnrollment(1, request);

    assertThat(outcome.enrollment().getUserIdentifier()).isEqualTo("app-user-99");

    JsonNode root = objectMapper.readTree(outcome.auditEventDetailsJson());
    assertThat(root.get("enrollment_id").asInt()).isEqualTo(1);
    assertThat(root.get("integration_id").asInt()).isEqualTo(10);
    JsonNode changes = root.get("changes");
    assertThat(changes.size()).isEqualTo(1);
    JsonNode userIdChange = changeForField(changes, "userIdentifier");
    assertThat(userIdChange.get("previous").isNull()).isTrue();
    assertThat(userIdChange.path("new").asString()).isEqualTo("app-user-99");
  }

  @Test
  @DisplayName("updateEnrollment - stale version throws ObjectOptimisticLockingFailureException")
  void updateEnrollment_staleVersion_throws409() {
    when(enrollmentRepository.findById(1)).thenReturn(Optional.of(enrollment));

    EnrollmentUpdateRequestDto request =
        new EnrollmentUpdateRequestDto(99L, "New Name", null, null, null, null, null, null, null);

    assertThatThrownBy(() -> enrollmentUpdateService.updateEnrollment(1, request))
        .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    verify(enrollmentRepository, never()).save(any());
  }

  @Test
  @DisplayName("updateEnrollment - not found throws ResourceNotFoundException")
  void updateEnrollment_notFound_throws404() {
    when(enrollmentRepository.findById(999)).thenReturn(Optional.empty());

    EnrollmentUpdateRequestDto request =
        new EnrollmentUpdateRequestDto(null, "New Name", null, null, null, null, null, null, null);

    assertThatThrownBy(() -> enrollmentUpdateService.updateEnrollment(999, request))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("Enrollment")
        .hasMessageContaining("999");
  }

  @Test
  @DisplayName("updateEnrollment - revoked enrollment throws IllegalArgumentException")
  void updateEnrollment_revoked_throws400() {
    enrollment.setRevokedAt(OffsetDateTime.now());
    when(enrollmentRepository.findById(1)).thenReturn(Optional.of(enrollment));

    EnrollmentUpdateRequestDto request =
        new EnrollmentUpdateRequestDto(0L, "New Name", null, null, null, null, null, null, null);

    assertThatThrownBy(() -> enrollmentUpdateService.updateEnrollment(1, request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("revoked");
  }

  @Test
  @DisplayName("updateEnrollment - inactive enrollment throws IllegalArgumentException")
  void updateEnrollment_inactive_throws400() {
    enrollment.setActive(false);
    when(enrollmentRepository.findById(1)).thenReturn(Optional.of(enrollment));

    EnrollmentUpdateRequestDto request =
        new EnrollmentUpdateRequestDto(0L, "New Name", null, null, null, null, null, null, null);

    assertThatThrownBy(() -> enrollmentUpdateService.updateEnrollment(1, request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("inactive");
  }

  @Test
  @DisplayName("updateEnrollment - duplicate name throws IllegalArgumentException")
  void updateEnrollment_duplicateName_throws400() {
    when(enrollmentRepository.findById(1)).thenReturn(Optional.of(enrollment));
    Enrollment other = new Enrollment();
    other.setEnrollmentId(2);
    when(enrollmentRepository.findByIntegrationIdAndEnrollmentNameAndStatusAndEnrollmentIdNot(
            eq(10), eq("Existing Name"), eq(EnrollmentStatus.VERIFIED), eq(1)))
        .thenReturn(List.of(other));

    EnrollmentUpdateRequestDto request =
        new EnrollmentUpdateRequestDto(
            0L, "Existing Name", null, null, null, null, null, null, null);

    assertThatThrownBy(() -> enrollmentUpdateService.updateEnrollment(1, request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("already exists");
  }

  @Test
  @DisplayName("updateEnrollment - expiresAt in past throws IllegalArgumentException")
  void updateEnrollment_expiresAtInPast_throws400() {
    when(enrollmentRepository.findById(1)).thenReturn(Optional.of(enrollment));
    OffsetDateTime past = OffsetDateTime.now().minusDays(1);

    EnrollmentUpdateRequestDto request =
        new EnrollmentUpdateRequestDto(0L, null, null, null, past, null, null, null, null);

    assertThatThrownBy(() -> enrollmentUpdateService.updateEnrollment(1, request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("future");
  }

  @Test
  @DisplayName("updateEnrollment - clearContactEmail clears contact email")
  void updateEnrollment_clearContactEmail_clears() throws Exception {
    when(enrollmentRepository.findById(1)).thenReturn(Optional.of(enrollment));
    when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(i -> i.getArgument(0));

    EnrollmentUpdateRequestDto request =
        new EnrollmentUpdateRequestDto(0L, null, null, null, null, null, null, true, null);

    EnrollmentUpdateOutcome outcome = enrollmentUpdateService.updateEnrollment(1, request);

    assertThat(outcome.enrollment().getContactEmail()).isNull();
    JsonNode root = objectMapper.readTree(outcome.auditEventDetailsJson());
    JsonNode changes = root.get("changes");
    assertThat(changes.size()).isEqualTo(1);
    assertThat(changeForField(changes, "contactEmail").path("previous").asString())
        .isEqualTo("original@example.com");
    assertThat(changeForField(changes, "contactEmail").get("new").isNull()).isTrue();
  }

  @Test
  @DisplayName("updateEnrollment - clearExpiresAt clears expiresAt")
  void updateEnrollment_clearExpiresAt_clears() throws Exception {
    enrollment.setExpiresAt(OffsetDateTime.now().plusDays(7));
    when(enrollmentRepository.findById(1)).thenReturn(Optional.of(enrollment));
    when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(i -> i.getArgument(0));

    EnrollmentUpdateRequestDto request =
        new EnrollmentUpdateRequestDto(0L, null, null, null, null, null, null, null, true);

    EnrollmentUpdateOutcome outcome = enrollmentUpdateService.updateEnrollment(1, request);

    assertThat(outcome.enrollment().getExpiresAt()).isNull();
    JsonNode root = objectMapper.readTree(outcome.auditEventDetailsJson());
    JsonNode changes = root.get("changes");
    assertThat(changes.size()).isEqualTo(1);
    assertThat(changeForField(changes, "expiresAt").path("previous").asString()).isNotBlank();
    assertThat(changeForField(changes, "expiresAt").get("new").isNull()).isTrue();
  }

  @Test
  @DisplayName("updateEnrollment - invalid contact email throws IllegalArgumentException")
  void updateEnrollment_invalidEmail_throws400() {
    when(enrollmentRepository.findById(1)).thenReturn(Optional.of(enrollment));

    EnrollmentUpdateRequestDto request =
        new EnrollmentUpdateRequestDto(
            0L, null, "not-an-email", null, null, null, null, null, null);

    assertThatThrownBy(() -> enrollmentUpdateService.updateEnrollment(1, request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid email");
    verify(enrollmentRepository, never()).save(any());
  }
}
