/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AuditChainCheckpointServiceTest
 * Description: Unit tests for AuditChainCheckpointService findCheckpoints.
 */

package org.ezkey.audit.integrity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
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
import org.springframework.data.jpa.domain.Specification;

/**
 * Unit tests for {@link AuditChainCheckpointService#findCheckpoints}.
 *
 * @since 2026
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuditChainCheckpointService Tests")
class AuditChainCheckpointServiceTest {

  @Mock private AuditChainCheckpointRepository checkpointRepository;

  private AuditChainCheckpointService service;

  private static final OffsetDateTime WINDOW_START =
      OffsetDateTime.of(2026, 3, 1, 10, 0, 0, 0, ZoneOffset.UTC);
  private static final OffsetDateTime WINDOW_END =
      OffsetDateTime.of(2026, 3, 1, 10, 5, 0, 0, ZoneOffset.UTC);

  @BeforeEach
  void setUp() {
    service = new AuditChainCheckpointService(checkpointRepository);
  }

  @Test
  @DisplayName("findCheckpoints with no filters returns page from repository")
  void findCheckpoints_noFilters_returnsPageFromRepository() {
    Pageable pageable = PageRequest.of(0, 20, Sort.by("windowStart").ascending());
    AuditChainCheckpoint checkpoint = createCheckpoint(1L, WINDOW_START, WINDOW_END);
    Page<AuditChainCheckpoint> repoPage = new PageImpl<>(List.of(checkpoint), pageable, 1);

    when(checkpointRepository.findAll(isA(Specification.class), eq(pageable))).thenReturn(repoPage);

    Page<AuditChainCheckpoint> result =
        service.findCheckpoints(null, null, null, null, null, null, null, pageable);

    assertNotNull(result);
    assertEquals(1, result.getTotalElements());
    assertEquals(1, result.getContent().size());
    assertEquals(1L, result.getContent().get(0).getCheckpointId());
    verify(checkpointRepository).findAll(isA(Specification.class), eq(pageable));
  }

  @Test
  @DisplayName("findCheckpoints with window and type filters calls repository with spec")
  void findCheckpoints_withFilters_callsRepositoryWithSpec() {
    Pageable pageable = PageRequest.of(0, 10);
    when(checkpointRepository.findAll(isA(Specification.class), eq(pageable)))
        .thenReturn(Page.empty(pageable));

    service.findCheckpoints(
        WINDOW_START, WINDOW_END.plusMinutes(5), 1, null, "REGULAR", null, null, pageable);

    verify(checkpointRepository).findAll(isA(Specification.class), eq(pageable));
  }

  @Test
  @DisplayName("findCheckpoints with createdAfter/createdBefore passes through")
  void findCheckpoints_createdRange_callsRepository() {
    OffsetDateTime createdAfter = WINDOW_START.minusHours(1);
    OffsetDateTime createdBefore = WINDOW_END.plusHours(1);
    Pageable pageable = PageRequest.of(0, 20);

    when(checkpointRepository.findAll(isA(Specification.class), eq(pageable)))
        .thenReturn(Page.empty(pageable));

    Page<AuditChainCheckpoint> result =
        service.findCheckpoints(
            null, null, null, null, null, createdAfter, createdBefore, pageable);

    assertEquals(0, result.getTotalElements());
    verify(checkpointRepository).findAll(isA(Specification.class), eq(pageable));
  }

  private static AuditChainCheckpoint createCheckpoint(
      long id, OffsetDateTime windowStart, OffsetDateTime windowEnd) {
    AuditChainCheckpoint c = new AuditChainCheckpoint();
    c.setCheckpointId(id);
    c.setWindowStart(windowStart);
    c.setWindowEnd(windowEnd);
    c.setEntryCount(2);
    c.setFirstEntryId(100L);
    c.setLastEntryId(101L);
    c.setEntriesDigest("digest");
    c.setPrevChainHmac("prev");
    c.setChainHmac("chain");
    c.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
    c.setCheckpointType("REGULAR");
    c.setNotes(null);
    return c;
  }
}
