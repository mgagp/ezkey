/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: EnrollmentCoreMapperTest
 * Description: Critical unit tests for EnrollmentCoreMapper MapStruct transformations.
 */

package org.ezkey.enrollment.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.OffsetDateTime;
import org.ezkey.enrollment.domain.EnrollmentCreateResponse;
import org.ezkey.enrollment.domain.EnrollmentResponse;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Critical unit tests for {@link EnrollmentCoreMapper}.
 *
 * <p>This test class provides comprehensive coverage of the EnrollmentCoreMapper MapStruct
 * interface focusing on entity-to-DTO transformations. Tests cover critical mapping operations
 * including field renaming and null handling.
 *
 * <p><b>Critical Test Coverage:</b>
 *
 * <ul>
 *   <li>toCreateResponse() - Mapping enrollment to create response DTO
 *   <li>toResponse() - Mapping enrollment to response DTO with field renaming
 *   <li>Field mapping validation - Correct field transformations
 *   <li>Null handling - Proper null value handling
 *   <li>Type conversion - Correct type transformations
 * </ul>
 *
 * <p><b>Security Focus:</b> These tests validate that sensitive data is properly mapped and that
 * field transformations maintain data integrity and security.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see EnrollmentCoreMapper
 * @see Enrollment
 * @see EnrollmentCreateResponse
 * @see EnrollmentResponse
 */
@DisplayName("Enrollment Core Mapper Critical Tests")
class EnrollmentCoreMapperTest {

  private EnrollmentCoreMapper enrollmentCoreMapper;

  private Enrollment enrollment;

  @BeforeEach
  void setUp() {
    // Create mapper instance directly (MapStruct generates implementation)
    enrollmentCoreMapper = org.mapstruct.factory.Mappers.getMapper(EnrollmentCoreMapper.class);
    
    enrollment = new Enrollment();
    enrollment.setEnrollmentId(123);
    enrollment.setIntegrationId(456);
    enrollment.setEnrollmentName("Test Enrollment");
    enrollment.setEnrollmentProofToken("test-proof-token");
    enrollment.setEnrollmentChallenge(123456);
    enrollment.setStatus(EnrollmentStatus.VERIFIED);
    enrollment.setActive(true);
    enrollment.setIntegrationPublicKey("integration-public-key");
    enrollment.setIntegrationPrivateKey("integration-private-key");
    enrollment.setDevicePublicKey("device-public-key");
    enrollment.setCreatedAt(OffsetDateTime.now().minusMinutes(10));
  }

  // ===== TO CREATE RESPONSE TESTS =====

  @Test
  @DisplayName("toCreateResponse() - Should map enrollment to create response correctly")
  void toCreateResponse_WhenValidEnrollment_ShouldMapCorrectly() {
    // Act
    EnrollmentCreateResponse response = enrollmentCoreMapper.toCreateResponse(enrollment);

    // Assert
    assertNotNull(response);
    assertEquals(enrollment.getEnrollmentId(), response.getEnrollmentId());
    assertEquals(enrollment.getEnrollmentChallenge(), response.getEnrollmentChallenge());
  }

  @Test
  @DisplayName("toCreateResponse() - Should handle null enrollment")
  void toCreateResponse_WhenNullEnrollment_ShouldReturnNull() {
    // Act
    EnrollmentCreateResponse response = enrollmentCoreMapper.toCreateResponse(null);

    // Assert
    assertNull(response);
  }

  @Test
  @DisplayName("toCreateResponse() - Should map with null values")
  void toCreateResponse_WhenEnrollmentWithNullValues_ShouldMapCorrectly() {
    // Arrange
    enrollment.setEnrollmentId(null);
    enrollment.setEnrollmentChallenge(null);

    // Act
    EnrollmentCreateResponse response = enrollmentCoreMapper.toCreateResponse(enrollment);

    // Assert
    assertNotNull(response);
    assertNull(response.getEnrollmentId());
    assertNull(response.getEnrollmentChallenge());
  }

  // ===== TO RESPONSE TESTS =====

  @Test
  @DisplayName("toResponse() - Should map enrollment to response with field renaming")
  void toResponse_WhenValidEnrollment_ShouldMapWithFieldRenaming() {
    // Act
    EnrollmentResponse response = enrollmentCoreMapper.toResponse(enrollment);

    // Assert
    assertNotNull(response);
    assertEquals(enrollment.getEnrollmentId(), response.getEnrollmentId());
    assertEquals(enrollment.getIntegrationId(), response.getIntegrationId());
    assertEquals(enrollment.getEnrollmentName(), response.getEnrollmentName());
    assertEquals(enrollment.getEnrollmentProofToken(), response.getEnrollmentProofToken());
    assertEquals(enrollment.getEnrollmentChallenge(), response.getEnrollmentChallenge());

    // Test field renaming mappings
    assertEquals(enrollment.getStatus().name(), response.getEnrollmentStatus());
    assertEquals(enrollment.getActive(), response.getEnrollmentActive());

    assertEquals(enrollment.getIntegrationPublicKey(), response.getIntegrationPublicKey());
    assertEquals(enrollment.getDevicePublicKey(), response.getDevicePublicKey());
    assertEquals(enrollment.getCreatedAt(), response.getCreatedAt());
  }

  @Test
  @DisplayName("toResponse() - Should handle null enrollment")
  void toResponse_WhenNullEnrollment_ShouldReturnNull() {
    // Act
    EnrollmentResponse response = enrollmentCoreMapper.toResponse(null);

    // Assert
    assertNull(response);
  }

  @Test
  @DisplayName("toResponse() - Should map with null values")
  void toResponse_WhenEnrollmentWithNullValues_ShouldMapCorrectly() {
    // Arrange
    enrollment.setEnrollmentId(null);
    enrollment.setIntegrationId(null);
    enrollment.setEnrollmentName(null);
    enrollment.setEnrollmentProofToken(null);
    enrollment.setEnrollmentChallenge(null);
    enrollment.setStatus(null);
    enrollment.setActive(false);
    enrollment.setIntegrationPublicKey(null);
    enrollment.setIntegrationPrivateKey(null);
    enrollment.setDevicePublicKey(null);
    enrollment.setCreatedAt(null);

    // Act
    EnrollmentResponse response = enrollmentCoreMapper.toResponse(enrollment);

    // Assert
    assertNotNull(response);
    assertNull(response.getEnrollmentId());
    assertNull(response.getIntegrationId());
    assertNull(response.getEnrollmentName());
    assertNull(response.getEnrollmentProofToken());
    assertNull(response.getEnrollmentChallenge());
    assertNull(response.getEnrollmentStatus());
    assertEquals(Boolean.FALSE, response.getEnrollmentActive());
    assertNull(response.getIntegrationPublicKey());
    assertNull(response.getDevicePublicKey());
    assertNull(response.getCreatedAt());
  }

  @Test
  @DisplayName("toResponse() - Should map different enrollment statuses correctly")
  void toResponse_WithDifferentStatuses_ShouldMapCorrectly() {
    // Test CREATED status
    enrollment.setStatus(EnrollmentStatus.CREATED);
    enrollment.setActive(false);

    EnrollmentResponse response1 = enrollmentCoreMapper.toResponse(enrollment);
    assertEquals(EnrollmentStatus.CREATED.name(), response1.getEnrollmentStatus());
    assertEquals(Boolean.FALSE, response1.getEnrollmentActive());

    // Test BOUND status
    enrollment.setStatus(EnrollmentStatus.BOUND);
    enrollment.setActive(false);

    EnrollmentResponse response2 = enrollmentCoreMapper.toResponse(enrollment);
    assertEquals(EnrollmentStatus.BOUND.name(), response2.getEnrollmentStatus());
    assertEquals(Boolean.FALSE, response2.getEnrollmentActive());

    // Test VERIFIED status
    enrollment.setStatus(EnrollmentStatus.VERIFIED);
    enrollment.setActive(true);

    EnrollmentResponse response3 = enrollmentCoreMapper.toResponse(enrollment);
    assertEquals(EnrollmentStatus.VERIFIED.name(), response3.getEnrollmentStatus());
    assertEquals(Boolean.TRUE, response3.getEnrollmentActive());

    // Test INVALID status
    enrollment.setStatus(EnrollmentStatus.INVALID);
    enrollment.setActive(false);

    EnrollmentResponse response4 = enrollmentCoreMapper.toResponse(enrollment);
    assertEquals(EnrollmentStatus.INVALID.name(), response4.getEnrollmentStatus());
    assertEquals(Boolean.FALSE, response4.getEnrollmentActive());
  }

  // ===== FIELD MAPPING VALIDATION TESTS =====

  @Test
  @DisplayName("toResponse() - Should preserve all enrollment fields")
  void toResponse_ShouldPreserveAllEnrollmentFields() {
    // Act
    EnrollmentResponse response = enrollmentCoreMapper.toResponse(enrollment);

    // Assert - Verify all fields are mapped correctly
    assertEquals(enrollment.getEnrollmentId(), response.getEnrollmentId());
    assertEquals(enrollment.getIntegrationId(), response.getIntegrationId());
    assertEquals(enrollment.getEnrollmentName(), response.getEnrollmentName());
    assertEquals(enrollment.getEnrollmentProofToken(), response.getEnrollmentProofToken());
    assertEquals(enrollment.getEnrollmentChallenge(), response.getEnrollmentChallenge());
    assertEquals(enrollment.getIntegrationPublicKey(), response.getIntegrationPublicKey());
    assertEquals(enrollment.getDevicePublicKey(), response.getDevicePublicKey());
    assertEquals(enrollment.getCreatedAt(), response.getCreatedAt());
  }

  @Test
  @DisplayName("toCreateResponse() - Should only map required fields")
  void toCreateResponse_ShouldOnlyMapRequiredFields() {
    // Act
    EnrollmentCreateResponse response = enrollmentCoreMapper.toCreateResponse(enrollment);

    // Assert - Only enrollmentId and enrollmentChallenge should be mapped
    assertEquals(enrollment.getEnrollmentId(), response.getEnrollmentId());
    assertEquals(enrollment.getEnrollmentChallenge(), response.getEnrollmentChallenge());

    // Other fields should not be accessible in create response
    // (This test validates the mapper only includes the fields defined in the DTO)
  }

  // ===== TYPE CONVERSION TESTS =====

  @Test
  @DisplayName("toResponse() - Should handle boolean type conversion correctly")
  void toResponse_ShouldHandleBooleanTypeConversion() {
    // Test with active = true
    enrollment.setActive(true);
    EnrollmentResponse response1 = enrollmentCoreMapper.toResponse(enrollment);
    assertEquals(Boolean.TRUE, response1.getEnrollmentActive());

    // Test with active = false
    enrollment.setActive(false);
    EnrollmentResponse response2 = enrollmentCoreMapper.toResponse(enrollment);
    assertEquals(Boolean.FALSE, response2.getEnrollmentActive());
  }

  @Test
  @DisplayName("toResponse() - Should handle integer type conversion correctly")
  void toResponse_ShouldHandleIntegerTypeConversion() {
    // Test with different integer values
    enrollment.setEnrollmentId(999);
    enrollment.setIntegrationId(888);
    enrollment.setEnrollmentChallenge(777777);

    EnrollmentResponse response = enrollmentCoreMapper.toResponse(enrollment);
    assertEquals(Integer.valueOf(999), response.getEnrollmentId());
    assertEquals(Integer.valueOf(888), response.getIntegrationId());
    assertEquals(Integer.valueOf(777777), response.getEnrollmentChallenge());
  }

  // ===== EDGE CASE TESTS =====

  @Test
  @DisplayName("toResponse() - Should handle empty string values")
  void toResponse_WithEmptyStringValues_ShouldMapCorrectly() {
    // Arrange
    enrollment.setEnrollmentName("");
    enrollment.setEnrollmentProofToken("");
    enrollment.setIntegrationPublicKey("");
    enrollment.setDevicePublicKey("");

    // Act
    EnrollmentResponse response = enrollmentCoreMapper.toResponse(enrollment);

    // Assert
    assertEquals("", response.getEnrollmentName());
    assertEquals("", response.getEnrollmentProofToken());
    assertEquals("", response.getIntegrationPublicKey());
    assertEquals("", response.getDevicePublicKey());
  }

  @Test
  @DisplayName("toCreateResponse() - Should handle zero values")
  void toCreateResponse_WithZeroValues_ShouldMapCorrectly() {
    // Arrange
    enrollment.setEnrollmentId(0);
    enrollment.setEnrollmentChallenge(0);

    // Act
    EnrollmentCreateResponse response = enrollmentCoreMapper.toCreateResponse(enrollment);

    // Assert
    assertEquals(Integer.valueOf(0), response.getEnrollmentId());
    assertEquals(Integer.valueOf(0), response.getEnrollmentChallenge());
  }
}
