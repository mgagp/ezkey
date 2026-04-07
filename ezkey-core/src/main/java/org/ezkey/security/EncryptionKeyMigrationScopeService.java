/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.security;

import org.ezkey.security.domain.entity.EncryptionKey;
import org.springframework.stereotype.Service;

/**
 * Computes migration-scope statistics for encryption keys using the same tracked targets as batch
 * creation and re-encryption ({@link
 * ReencryptionBatchCreationService#discoverReencryptableTargets()} and {@link
 * ReencryptionTargetQueryService#countRecordsEncryptedWithKey}).
 *
 * <p>Each unit is one row still encrypted with the key in a tracked column (ciphertext prefix
 * {@code ENC:{keyId}:%}). Summing across targets yields total ciphertext units for that key,
 * aligned with {@link KeyUsageVerificationService} "remaining" semantics and batch progress.
 */
@Service
public class EncryptionKeyMigrationScopeService {

  private final ReencryptionBatchCreationService batchCreationService;
  private final ReencryptionTargetQueryService targetQueryService;

  public EncryptionKeyMigrationScopeService(
      ReencryptionBatchCreationService batchCreationService,
      ReencryptionTargetQueryService targetQueryService) {
    this.batchCreationService = batchCreationService;
    this.targetQueryService = targetQueryService;
  }

  /**
   * Sums ciphertext units across all tracked (table, column) targets for the given key.
   *
   * @param keyId Tink key id
   * @return total count (non-negative)
   */
  public long sumCiphertextUnitsAcrossTrackedTargets(long keyId) {
    long total = 0L;
    for (ReencryptionBatchCreationService.Target target :
        batchCreationService.discoverReencryptableTargets()) {
      total +=
          targetQueryService.countRecordsEncryptedWithKey(target.table(), target.column(), keyId);
    }
    return total;
  }

  /**
   * Called when a key becomes {@link
   * org.ezkey.security.domain.entity.EncryptionKey.KeyStatus#ENABLED} after serving as primary (or
   * when syncing an ENABLED key from the keyset). Sets {@code records_encrypted} to the
   * migration-scope baseline (ciphertext units still using this key) and resets {@code
   * records_reencrypted} to zero so batch progress applies to this migration phase.
   *
   * @param key managed key row about to be saved as ENABLED
   */
  public void applyDemotionBaseline(EncryptionKey key) {
    long baseline = sumCiphertextUnitsAcrossTrackedTargets(key.getKeyId());
    key.setRecordsEncrypted(baseline);
    key.setRecordsReencrypted(0L);
  }
}
