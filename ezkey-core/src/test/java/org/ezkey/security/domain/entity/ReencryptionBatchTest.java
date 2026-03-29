/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.security.domain.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link ReencryptionBatch} progress accounting. */
class ReencryptionBatchTest {

  @Test
  @DisplayName("updateProgress keeps initial records_total when processed count is lower")
  void updateProgress_preservesTotalWhenUnderInitialEstimate() {
    ReencryptionBatch batch = new ReencryptionBatch();
    batch.setRecordsTotal(100);
    batch.updateProgress(40, 5, 0);
    assertEquals(100, batch.getRecordsTotal());
    assertEquals(40, batch.getRecordsDone());
    assertEquals(5, batch.getRecordsFailed());
    assertEquals(new BigDecimal("40.00"), batch.getProgressPct());
  }

  @Test
  @DisplayName("updateProgress raises records_total when concurrent work exceeds initial count")
  void updateProgress_raisesTotalWhenProcessedExceedsInitialEstimate() {
    ReencryptionBatch batch = new ReencryptionBatch();
    batch.setRecordsTotal(511);
    batch.updateProgress(520, 0, 0);
    assertEquals(520, batch.getRecordsTotal());
    assertEquals(520, batch.getRecordsDone());
    assertEquals(new BigDecimal("100.00"), batch.getProgressPct());
  }
}
