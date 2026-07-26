/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.security;

import java.util.Map;
import org.ezkey.security.domain.entity.ReencryptionBatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** In-memory decrypt/re-encrypt for a single {@link Reencryptable} row (no persistence). */
@Component
public class ReencryptionRecordCipher {

  private static final Logger logger = LoggerFactory.getLogger(ReencryptionRecordCipher.class);

  private final EncryptionOperations encryptionOperations;

  public ReencryptionRecordCipher(EncryptionOperations encryptionOperations) {
    this.encryptionOperations = encryptionOperations;
  }

  /**
   * Result of re-encryption operation.
   *
   * @param reencrypted whether the record was re-encrypted
   * @param modifiedRecord the modified record (null if skipped)
   */
  public record ReencryptResult(boolean reencrypted, Reencryptable modifiedRecord) {}

  public boolean isCandidateForReencryption(ReencryptionBatch batch, Reencryptable record) {
    String column = batch.getTargetColumn();
    String oldKeyPrefix = "ENC:" + batch.getOldKey().getKeyId() + ":";
    String newKeyPrefix = "ENC:" + batch.getNewKey().getKeyId() + ":";

    Map<String, String> encryptedFields = record.getEncryptedFields();
    String encryptedValue = encryptedFields.get(column);

    if (encryptedValue == null) {
      logger.debug(
          "Skipping record {} ({}): encrypted field {} is null",
          record.getEntityId(),
          record.getTableName(),
          column);
      return false;
    }

    if (!encryptedValue.startsWith(oldKeyPrefix)) {
      logger.debug(
          "Skipping record {} ({}): encrypted field {} does not start with old key prefix {} (value"
              + " starts with: {})",
          record.getEntityId(),
          record.getTableName(),
          column,
          oldKeyPrefix,
          encryptedValue.length() > 20 ? encryptedValue.substring(0, 20) + "..." : encryptedValue);
      return false;
    }

    if (encryptedValue.startsWith(newKeyPrefix)) {
      logger.debug(
          "Skipping record {} ({}): already encrypted with new key {}",
          record.getEntityId(),
          record.getTableName(),
          batch.getNewKey().getKeyId());
      return false;
    }

    return true;
  }

  public ReencryptResult reencryptRecord(ReencryptionBatch batch, Reencryptable record) {
    if (!isCandidateForReencryption(batch, record)) {
      return new ReencryptResult(false, null);
    }

    String column = batch.getTargetColumn();
    Map<String, String> encryptedFields = record.getEncryptedFields();
    String encryptedValue = encryptedFields.get(column);

    String plaintext = encryptionOperations.decrypt(encryptedValue);
    String reencrypted = encryptionOperations.encrypt(plaintext);

    record.setEncryptedField(column, reencrypted);
    record.setEncryptionKeyId(column, batch.getNewKey().getKeyId());

    return new ReencryptResult(true, record);
  }
}
