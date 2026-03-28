/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: EncryptionKeyControllerTest
 * Description: Unit tests for EncryptionKeyController listKeys (paginated) endpoint.
 */

package org.ezkey.admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.security.KeyRotationService;
import org.ezkey.security.ReencryptionService;
import org.ezkey.security.domain.entity.EncryptionKey;
import org.ezkey.security.domain.entity.EncryptionKey.KeyStatus;
import org.ezkey.security.domain.entity.ReencryptionBatch;
import org.ezkey.security.domain.entity.ReencryptionBatch.BatchStatus;
import org.ezkey.security.domain.repository.EncryptionKeyRepository;
import org.ezkey.security.domain.repository.ReencryptionBatchRepository;
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
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Unit tests for EncryptionKeyController listKeys endpoint (paginated list with optional keyStatus
 * filter).
 *
 * <p><b>Test Coverage:</b>
 *
 * <ul>
 *   <li><b>listKeys:</b> Returns paginated response (content + page); optional keyStatus filter
 *       passed to repository; invalid keyStatus ignored (all keys returned)
 * </ul>
 *
 * @author Ezkey contributors
 * @since 2025
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EncryptionKeyController listKeys and listBatches Tests")
@SuppressWarnings("unchecked")
class EncryptionKeyControllerTest {

  @Mock private EncryptionKeyRepository keyRepository;
  @Mock private ReencryptionBatchRepository batchRepository;
  @Mock private KeyRotationService rotationService;
  @Mock private ReencryptionService reencryptionService;
  @Mock private AuditLogService auditLogService;

  private EncryptionKeyController controller;

  private EncryptionKey key1;
  private EncryptionKey key2;
  private ReencryptionBatch batch1;

  @BeforeEach
  void setUp() {
    controller =
        new EncryptionKeyController(
            keyRepository, batchRepository, rotationService, reencryptionService, auditLogService);

    OffsetDateTime now = OffsetDateTime.now();
    key1 = new EncryptionKey(100L, KeyStatus.PRIMARY, "AES256_GCM", now, "SYSTEM");
    key1.setPromotedPrimaryAt(now);
    key1.setRecordsEncrypted(50L);
    key1.setRecordsReencrypted(0L);

    key2 = new EncryptionKey(101L, KeyStatus.ENABLED, "AES256_GCM", now.minusDays(1), "SYSTEM");
    key2.setRecordsEncrypted(100L);
    key2.setRecordsReencrypted(50L);

    batch1 =
        new ReencryptionBatch(
            "ezkey_enrollment", "integration_private_key", key2, key1, 10, "SYSTEM");
    batch1.setBatchId(1);
    batch1.setStatus(BatchStatus.PENDING);
    batch1.setProgressPct(BigDecimal.ZERO);
  }

  @Test
  @DisplayName("Returns paginated list with content and metadata")
  void returnsPaginatedList() {
    Pageable pageable = PageRequest.of(0, 20);
    Page<EncryptionKey> repoPage = new PageImpl<>(java.util.List.of(key1, key2), pageable, 2);

    when(keyRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(repoPage);

    ResponseEntity<Page<EncryptionKeyController.EncryptionKeyResponse>> response =
        controller.listKeys(null, pageable);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    Page<EncryptionKeyController.EncryptionKeyResponse> body = response.getBody();
    assertNotNull(body);
    assertEquals(2, body.getTotalElements());
    assertEquals(2, body.getContent().size());
    assertEquals(100L, body.getContent().get(0).keyId());
    assertEquals("PRIMARY", body.getContent().get(0).keyStatus());
    assertEquals(101L, body.getContent().get(1).keyId());
    assertEquals("ENABLED", body.getContent().get(1).keyStatus());
    verify(keyRepository).findAll(any(Specification.class), eq(pageable));
  }

  @Test
  @DisplayName("Passes keyStatus filter to repository")
  void passesKeyStatusFilter() {
    Pageable pageable = PageRequest.of(0, 20);
    Page<EncryptionKey> repoPage = new PageImpl<>(java.util.List.of(key1), pageable, 1);

    when(keyRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(repoPage);

    ResponseEntity<Page<EncryptionKeyController.EncryptionKeyResponse>> response =
        controller.listKeys("PRIMARY", pageable);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals(1, response.getBody().getContent().size());
    assertEquals(100L, response.getBody().getContent().get(0).keyId());
    verify(keyRepository).findAll(any(Specification.class), eq(pageable));
  }

  @Test
  @DisplayName("Invalid keyStatus is ignored and all keys returned")
  void invalidKeyStatusIgnored() {
    Pageable pageable = PageRequest.of(0, 20);
    Page<EncryptionKey> repoPage = new PageImpl<>(java.util.List.of(key1, key2), pageable, 2);

    when(keyRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(repoPage);

    ResponseEntity<Page<EncryptionKeyController.EncryptionKeyResponse>> response =
        controller.listKeys("INVALID_STATUS", pageable);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals(2, response.getBody().getContent().size());
    verify(keyRepository).findAll(any(Specification.class), eq(pageable));
  }

  @Test
  @DisplayName("Returns empty page when no keys match")
  void returnsEmptyPage() {
    Pageable pageable = PageRequest.of(0, 20);
    Page<EncryptionKey> repoPage = new PageImpl<>(java.util.List.of(), pageable, 0);

    when(keyRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(repoPage);

    ResponseEntity<Page<EncryptionKeyController.EncryptionKeyResponse>> response =
        controller.listKeys(null, pageable);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals(0, response.getBody().getTotalElements());
    assertEquals(0, response.getBody().getContent().size());
  }

  @Test
  @DisplayName("listBatches returns paginated page with content and metadata")
  void listBatchesReturnsPaginatedPage() {
    Pageable pageable = PageRequest.of(0, 20);
    Page<ReencryptionBatch> repoPage = new PageImpl<>(java.util.List.of(batch1), pageable, 1);

    when(batchRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(repoPage);

    ResponseEntity<Page<EncryptionKeyController.ReencryptionBatchResponse>> response =
        controller.listBatches(null, null, null, null, null, null, null, pageable);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    Page<EncryptionKeyController.ReencryptionBatchResponse> body = response.getBody();
    assertNotNull(body);
    assertEquals(1, body.getTotalElements());
    assertEquals(1, body.getContent().size());
    assertEquals(1, body.getContent().get(0).batchId());
    assertEquals("PENDING", body.getContent().get(0).status());
    assertEquals("ezkey_enrollment", body.getContent().get(0).targetTable());
    verify(batchRepository).findAll(any(Specification.class), eq(pageable));
  }

  @Test
  @DisplayName("listBatches passes status filter to repository")
  void listBatchesPassesStatusFilter() {
    Pageable pageable = PageRequest.of(0, 20);
    Page<ReencryptionBatch> repoPage = new PageImpl<>(java.util.List.of(batch1), pageable, 1);

    when(batchRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(repoPage);

    ResponseEntity<Page<EncryptionKeyController.ReencryptionBatchResponse>> response =
        controller.listBatches("PENDING", null, null, null, null, null, null, pageable);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals(1, response.getBody().getContent().size());
    verify(batchRepository).findAll(any(Specification.class), eq(pageable));
  }

  @Test
  @DisplayName("listBatches ignores invalid status enum")
  void listBatchesInvalidStatusIgnored() {
    Pageable pageable = PageRequest.of(0, 20);
    Page<ReencryptionBatch> repoPage = new PageImpl<>(java.util.List.of(batch1), pageable, 1);

    when(batchRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(repoPage);

    ResponseEntity<Page<EncryptionKeyController.ReencryptionBatchResponse>> response =
        controller.listBatches("NOT_A_STATUS", null, null, null, null, null, null, pageable);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals(1, response.getBody().getContent().size());
    verify(batchRepository).findAll(any(Specification.class), eq(pageable));
  }
}
