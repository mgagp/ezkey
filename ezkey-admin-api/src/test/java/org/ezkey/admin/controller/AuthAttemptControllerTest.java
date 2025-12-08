/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AuthAttemptControllerTest
 * Description: Unit tests for AuthAttemptController (excluding ownership tests)
 */

package org.ezkey.admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import org.ezkey.admin.security.RateLimitService;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.authattempt.domain.AuthAttemptStatus;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.dto.AuthAttemptDto;
import org.ezkey.authattempt.mapper.AuthAttemptMapper;
import org.ezkey.authattempt.service.AuthAttemptService;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Unit tests for AuthAttemptController.
 *
 * <p>Focuses on search and retrieval operations. Ownership tests are in
 * AuthAttemptControllerOwnershipTest.
 *
 * @since 2025
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthAttemptController Tests")
class AuthAttemptControllerTest {

  @Mock private AuthAttemptService authAttemptService;
  @Mock private AuthAttemptMapper authAttemptMapper;
  @Mock private AuditLogService auditLogService;
  @Mock private RateLimitService rateLimitService;
  @Mock private EnrollmentRepository enrollmentRepository;

  private AuthAttemptController controller;

  @BeforeEach
  void setUp() {
    controller =
        new AuthAttemptController(
            authAttemptService,
            authAttemptMapper,
            auditLogService,
            rateLimitService,
            enrollmentRepository);
  }

  @Test
  @DisplayName("search() - Should return paginated results with filters")
  void search_ShouldReturnPaginatedResults_WhenFiltersProvided() {
    // Arrange
    AuthAttemptStatus status = AuthAttemptStatus.PENDING;
    Integer enrollmentId = 123;
    Integer integrationId = 456;
    OffsetDateTime now = OffsetDateTime.now();
    Pageable pageable = PageRequest.of(0, 10);

    AuthAttempt authAttempt = new AuthAttempt();
    authAttempt.setAuthAttemptId(1); // Use Integer
    List<AuthAttempt> attempts = List.of(authAttempt);
    Page<AuthAttempt> attemptPage = new PageImpl<>(attempts, pageable, 1);

    // Create DTO using record constructor
    AuthAttemptDto dto =
        new AuthAttemptDto(
            1, // authAttemptId
            enrollmentId, // enrollmentId
            status, // status
            123456, // challenge
            "token", // proof token
            now, // createdAt
            now.plusMinutes(5) // expiresAt
            );

    when(authAttemptService.findByFilters(
            eq(status), eq(enrollmentId), eq(integrationId), eq(now), eq(now), any(Pageable.class)))
        .thenReturn(attemptPage);
    when(authAttemptMapper.toDto(authAttempt)).thenReturn(dto);

    // Act
    ResponseEntity<Page<AuthAttemptDto>> response =
        controller.search(status, enrollmentId, integrationId, now, now, pageable);

    // Assert
    assertNotNull(response);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals(1, response.getBody().getTotalElements());
    assertEquals(dto, response.getBody().getContent().get(0));

    verify(authAttemptService)
        .findByFilters(
            eq(status), eq(enrollmentId), eq(integrationId), eq(now), eq(now), any(Pageable.class));
  }

  @Test
  @DisplayName("search() - Should return all results when no filters provided")
  void search_ShouldReturnAllResults_WhenNoFiltersProvided() {
    // Arrange
    Pageable pageable = PageRequest.of(0, 20);

    AuthAttempt authAttempt = new AuthAttempt();
    List<AuthAttempt> attempts = List.of(authAttempt);
    Page<AuthAttempt> attemptPage = new PageImpl<>(attempts, pageable, 1);

    // Create DTO using record constructor
    AuthAttemptDto dto =
        new AuthAttemptDto(
            1, // authAttemptId
            100, // enrollmentId
            AuthAttemptStatus.PENDING, // status
            123456, // challenge
            "token", // proof token
            OffsetDateTime.now(), // createdAt
            OffsetDateTime.now().plusMinutes(5) // expiresAt
            );

    when(authAttemptService.findByFilters(
            eq(null), eq(null), eq(null), eq(null), eq(null), any(Pageable.class)))
        .thenReturn(attemptPage);
    when(authAttemptMapper.toDto(authAttempt)).thenReturn(dto);

    // Act
    ResponseEntity<Page<AuthAttemptDto>> response =
        controller.search(null, null, null, null, null, pageable);

    // Assert
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(1, response.getBody().getTotalElements());
    verify(authAttemptService)
        .findByFilters(eq(null), eq(null), eq(null), eq(null), eq(null), any(Pageable.class));
  }

  @Test
  @DisplayName("search() - Should respect pagination parameters")
  void search_ShouldRespectPaginationParameters() {
    // Arrange
    Pageable pageable = PageRequest.of(2, 50);

    AuthAttempt authAttempt = new AuthAttempt();
    List<AuthAttempt> attempts = List.of(authAttempt);
    Page<AuthAttempt> attemptPage = new PageImpl<>(attempts, pageable, 1);

    AuthAttemptDto dto =
        new AuthAttemptDto(
            1, // authAttemptId
            100, // enrollmentId
            AuthAttemptStatus.PENDING, // status
            123456, // challenge
            "token", // proof token
            OffsetDateTime.now(), // createdAt
            OffsetDateTime.now().plusMinutes(5) // expiresAt
            );

    when(authAttemptService.findByFilters(
            eq(null), eq(null), eq(null), eq(null), eq(null), any(Pageable.class)))
        .thenReturn(attemptPage);
    when(authAttemptMapper.toDto(authAttempt)).thenReturn(dto);

    // Act
    controller.search(null, null, null, null, null, pageable);

    // Assert
    verify(authAttemptService)
        .findByFilters(eq(null), eq(null), eq(null), eq(null), eq(null), eq(pageable));
  }

  @Test
  @DisplayName("search() - Should support dynamic sorting")
  void search_ShouldSupportDynamicSorting() {
    // Arrange
    Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "authAttemptId"));

    AuthAttempt authAttempt = new AuthAttempt();
    List<AuthAttempt> attempts = List.of(authAttempt);
    Page<AuthAttempt> attemptPage = new PageImpl<>(attempts, pageable, 1);

    AuthAttemptDto dto =
        new AuthAttemptDto(
            1, // authAttemptId
            100, // enrollmentId
            AuthAttemptStatus.PENDING, // status
            123456, // challenge
            "token", // proof token
            OffsetDateTime.now(), // createdAt
            OffsetDateTime.now().plusMinutes(5) // expiresAt
            );

    when(authAttemptService.findByFilters(
            eq(null), eq(null), eq(null), eq(null), eq(null), any(Pageable.class)))
        .thenReturn(attemptPage);
    when(authAttemptMapper.toDto(authAttempt)).thenReturn(dto);

    // Act
    ResponseEntity<Page<AuthAttemptDto>> response =
        controller.search(null, null, null, null, null, pageable);

    // Assert
    assertEquals(HttpStatus.OK, response.getStatusCode());
    verify(authAttemptService)
        .findByFilters(eq(null), eq(null), eq(null), eq(null), eq(null), eq(pageable));
  }
}
