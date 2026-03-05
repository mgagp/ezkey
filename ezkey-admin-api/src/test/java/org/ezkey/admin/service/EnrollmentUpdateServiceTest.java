/*
 * Ezkey - Open Source MFA/Passkey Alternative
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

/**
 * Unit tests for {@link EnrollmentUpdateService}.
 *
 * @author Ezkey contributors
 * @since 2025
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Enrollment Update Service Tests")
class EnrollmentUpdateServiceTest {

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

  @Test
  @DisplayName("updateEnrollment - happy path applies non-null fields")
  void updateEnrollment_happyPath_appliesNonNullFields() {
    when(enrollmentRepository.findById(1)).thenReturn(Optional.of(enrollment));
    when(enrollmentRepository.findByIntegrationIdAndEnrollmentNameAndStatusAndEnrollmentIdNot(
            eq(10), eq("New Name"), eq(EnrollmentStatus.VERIFIED), eq(1)))
        .thenReturn(Collections.emptyList());
    when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(i -> i.getArgument(0));

    EnrollmentUpdateRequestDto request =
        new EnrollmentUpdateRequestDto(0L, "New Name", "new@example.com", null, true);

    Enrollment result = enrollmentUpdateService.updateEnrollment(1, request);

    assertThat(result.getEnrollmentName()).isEqualTo("New Name");
    assertThat(result.getContactEmail()).isEqualTo("new@example.com");
    assertThat(result.getAuthAttemptChallengeRequired()).isTrue();
    verify(enrollmentRepository).save(enrollment);
  }

  @Test
  @DisplayName("updateEnrollment - stale version throws ObjectOptimisticLockingFailureException")
  void updateEnrollment_staleVersion_throws409() {
    when(enrollmentRepository.findById(1)).thenReturn(Optional.of(enrollment));

    EnrollmentUpdateRequestDto request =
        new EnrollmentUpdateRequestDto(99L, "New Name", null, null, null);

    assertThatThrownBy(() -> enrollmentUpdateService.updateEnrollment(1, request))
        .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    verify(enrollmentRepository, never()).save(any());
  }

  @Test
  @DisplayName("updateEnrollment - not found throws ResourceNotFoundException")
  void updateEnrollment_notFound_throws404() {
    when(enrollmentRepository.findById(999)).thenReturn(Optional.empty());

    EnrollmentUpdateRequestDto request =
        new EnrollmentUpdateRequestDto(null, "New Name", null, null, null);

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
        new EnrollmentUpdateRequestDto(0L, "New Name", null, null, null);

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
        new EnrollmentUpdateRequestDto(0L, "New Name", null, null, null);

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
        new EnrollmentUpdateRequestDto(0L, "Existing Name", null, null, null);

    assertThatThrownBy(() -> enrollmentUpdateService.updateEnrollment(1, request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("already exists");
  }

  @Test
  @DisplayName("updateEnrollment - expiresAt in past throws IllegalArgumentException")
  void updateEnrollment_expiresAtInPast_throws400() {
    when(enrollmentRepository.findById(1)).thenReturn(Optional.of(enrollment));
    OffsetDateTime past = OffsetDateTime.now().minusDays(1);

    EnrollmentUpdateRequestDto request = new EnrollmentUpdateRequestDto(0L, null, null, past, null);

    assertThatThrownBy(() -> enrollmentUpdateService.updateEnrollment(1, request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("future");
  }
}
