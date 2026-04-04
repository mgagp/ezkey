/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */
package org.ezkey.audit.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.integration.domain.repository.IntegrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditEntityFkResolverTest {

  @Mock private IntegrationRepository integrationRepository;
  @Mock private EnrollmentRepository enrollmentRepository;

  private AuditEntityFkResolver resolver;

  @BeforeEach
  void setUp() {
    resolver = new AuditEntityFkResolver(integrationRepository, enrollmentRepository);
  }

  @Test
  void integrationIdForAuditOrNull_returnsNullWhenMissing() {
    when(integrationRepository.existsById(99999)).thenReturn(false);
    assertNull(resolver.integrationIdForAuditOrNull(99999));
  }

  @Test
  void integrationIdForAuditOrNull_returnsIdWhenPresent() {
    when(integrationRepository.existsById(1)).thenReturn(true);
    assertEquals(1, resolver.integrationIdForAuditOrNull(1));
  }

  @Test
  void integrationIdForAuditOrNull_nullInput() {
    assertNull(resolver.integrationIdForAuditOrNull(null));
  }

  @Test
  void enrollmentIdForAuditOrNull_returnsNullWhenMissing() {
    when(enrollmentRepository.existsById(88888)).thenReturn(false);
    assertNull(resolver.enrollmentIdForAuditOrNull(88888));
  }

  @Test
  void enrollmentIdForAuditOrNull_returnsIdWhenPresent() {
    when(enrollmentRepository.existsById(2)).thenReturn(true);
    assertEquals(2, resolver.enrollmentIdForAuditOrNull(2));
  }

  @Test
  void enrollmentIdForAuditOrNull_nullInput() {
    assertNull(resolver.enrollmentIdForAuditOrNull(null));
  }
}
