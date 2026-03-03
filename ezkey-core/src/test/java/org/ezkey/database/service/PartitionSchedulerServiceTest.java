/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: PartitionSchedulerServiceTest
 * Description: Unit tests for partition creation scheduler (audit_log composite and auth_attempt).
 */

package org.ezkey.database.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for {@link PartitionSchedulerService}.
 *
 * <p>Verifies that the scheduler invokes create_monthly_partition for both ezkey_auth_attempt and
 * ezkey_audit_log with correct partition names. For ezkey_audit_log the function creates the
 * monthly partition plus LIST(api_name) sub-partitions (_admin, _auth, _m2m).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Partition scheduler service")
class PartitionSchedulerServiceTest {

  @Mock private EntityManager entityManager;
  @Mock private jakarta.persistence.Query nativeQuery;

  private PartitionSchedulerService service;

  @BeforeEach
  void setUp() {
    service = new PartitionSchedulerService();
    ReflectionTestUtils.setField(service, "entityManager", entityManager);
  }

  @Test
  @DisplayName("createNextMonthPartitions invokes create_monthly_partition for auth_attempt and audit_log")
  void createNextMonthPartitions_CallsFunctionForBothTables() {
    when(entityManager.createNativeQuery(any(String.class))).thenReturn(nativeQuery);
    when(nativeQuery.setParameter(any(String.class), any())).thenReturn(nativeQuery);
    when(nativeQuery.getSingleResult()).thenReturn(Boolean.TRUE);

    service.createNextMonthPartitions();

    verify(entityManager, org.mockito.Mockito.times(2)).createNativeQuery(any(String.class));

    ArgumentCaptor<String> tableCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> partitionCaptor = ArgumentCaptor.forClass(String.class);
    verify(nativeQuery, org.mockito.Mockito.times(2)).setParameter(eq("tableName"), tableCaptor.capture());
    verify(nativeQuery, org.mockito.Mockito.times(2)).setParameter(eq("partitionName"), partitionCaptor.capture());

    assertEquals("ezkey_auth_attempt", tableCaptor.getAllValues().get(0));
    assertEquals("ezkey_audit_log", tableCaptor.getAllValues().get(1));
    assertTrue(partitionCaptor.getAllValues().get(0).startsWith("ezkey_auth_attempt_"));
    assertTrue(partitionCaptor.getAllValues().get(1).startsWith("ezkey_audit_log_"));
    assertTrue(Pattern.matches("ezkey_auth_attempt_\\d{4}_\\d{2}", partitionCaptor.getAllValues().get(0)));
    assertTrue(Pattern.matches("ezkey_audit_log_\\d{4}_\\d{2}", partitionCaptor.getAllValues().get(1)));
  }
}
