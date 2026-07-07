/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.ezkey.security.domain.entity.ReencryptionBatch;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ReencryptionBatchParallelRunner mutex keys")
class ReencryptionBatchParallelRunnerTest {

  @Test
  @DisplayName("Enrollment batches use table-only lock")
  void mutexKey_enrollment_tableOnly() {
    ReencryptionBatch b = new ReencryptionBatch();
    b.setTargetTable("ezkey_enrollment");
    b.setTargetColumn("integration_private_key");
    assertEquals("ezkey_enrollment", ReencryptionBatchParallelRunner.mutexKeyForBatch(b));
  }

  @Test
  @DisplayName("Auth attempt without sharding uses table-only lock")
  void mutexKey_authAttempt_unsharded_tableOnly() {
    ReencryptionBatch b = new ReencryptionBatch();
    b.setTargetTable("ezkey_auth_attempt");
    b.setTargetColumn("auth_attempt_proof_token");
    b.setShardCount(null);
    assertEquals("ezkey_auth_attempt", ReencryptionBatchParallelRunner.mutexKeyForBatch(b));
  }

  @Test
  @DisplayName("Auth attempt with shard_count 1 uses table-only lock")
  void mutexKey_authAttempt_shardCountOne_tableOnly() {
    ReencryptionBatch b = new ReencryptionBatch();
    b.setTargetTable("ezkey_auth_attempt");
    b.setTargetColumn("auth_attempt_proof_token");
    b.setShardCount(1);
    b.setShardIndex(0);
    assertEquals("ezkey_auth_attempt", ReencryptionBatchParallelRunner.mutexKeyForBatch(b));
  }

  @Test
  @DisplayName("Auth attempt with sharding uses table|column|shardIndex lock")
  void mutexKey_authAttempt_sharded_includesColumnAndShard() {
    ReencryptionBatch b = new ReencryptionBatch();
    b.setTargetTable("ezkey_auth_attempt");
    b.setTargetColumn("auth_attempt_proof_token");
    b.setShardCount(4);
    b.setShardIndex(2);
    assertEquals(
        "ezkey_auth_attempt|auth_attempt_proof_token|2",
        ReencryptionBatchParallelRunner.mutexKeyForBatch(b));
  }
}
