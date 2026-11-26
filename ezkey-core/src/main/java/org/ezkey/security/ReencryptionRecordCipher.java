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
   * @param keyIdAligned whether only the {@code *_encryption_key_id} companion column was corrected
   *     to the key named by the ciphertext prefix (the row was already off the old key, #1431)
   * @param modifiedRecord the modified record (null if nothing changed)
   */
  public record ReencryptResult(
      boolean reencrypted, boolean keyIdAligned, Reencryptable modifiedRecord) {

    /** Whether the record must be saved. */
    public boolean changed() {
      return reencrypted || keyIdAligned;
    }
  }

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

  /**
   * Whether the row's key-id column disagrees with the key named by its ciphertext prefix while the
   * prefix is not the batch's old key (so the row needs no re-encryption, only its key-id column
   * corrected). Such rows come from a primary rotation during an earlier batch: counted on the
   * wrong key, they keep the old key from draining and under-count the key that really encrypted
   * them (#1431).
   *
   * @param batch the batch being processed
   * @param record the row
   * @return {@code true} when only the key-id column must be corrected
   */
  public boolean needsKeyIdAlignment(ReencryptionBatch batch, Reencryptable record) {
    String column = batch.getTargetColumn();
    Long prefixKeyId = prefixKeyId(record.getEncryptedFields().get(column));
    return prefixKeyId != null
        && !prefixKeyId.equals(batch.getOldKey().getKeyId())
        && !prefixKeyId.equals(record.getEncryptionKeyId(column));
  }

  /**
   * Re-encrypts the target column of one row under the current primary key, or corrects its key-id
   * column when the row is already off the old key.
   *
   * <p>The key-id column is always taken from the prefix of the ciphertext actually written, never
   * from {@code batch.newKey}: the primary can rotate while a batch runs, and {@link
   * EncryptionOperations#encrypt(String)} uses the primary current at that moment (#1431).
   *
   * @param batch the batch being processed
   * @param record the row, loaded under a write lock by the caller
   * @return what changed on the row
   */
  public ReencryptResult reencryptRecord(ReencryptionBatch batch, Reencryptable record) {
    String column = batch.getTargetColumn();
    if (!isCandidateForReencryption(batch, record)) {
      if (needsKeyIdAlignment(batch, record)) {
        record.setEncryptionKeyId(column, prefixKeyId(record.getEncryptedFields().get(column)));
        return new ReencryptResult(false, true, record);
      }
      return new ReencryptResult(false, false, null);
    }

    Map<String, String> encryptedFields = record.getEncryptedFields();
    String encryptedValue = encryptedFields.get(column);

    String plaintext = encryptionOperations.decrypt(encryptedValue);
    String reencrypted = encryptionOperations.encrypt(plaintext);
    Long writtenKeyId = prefixKeyId(reencrypted);
    if (writtenKeyId == null) {
      throw new IllegalStateException("Re-encrypted value has no key id prefix (fail-closed)");
    }
    if (writtenKeyId.equals(batch.getOldKey().getKeyId())) {
      // A node whose keyset still names the old key as primary: nothing would migrate, yet the row
      // would count as done. Fail the row so the batch cannot report a false success (#1431).
      throw new IllegalStateException(
          "Re-encryption wrote the old key again; keyset primary not current (fail-closed)");
    }

    record.setEncryptedField(column, reencrypted);
    record.setEncryptionKeyId(column, writtenKeyId);

    return new ReencryptResult(true, false, record);
  }

  private Long prefixKeyId(String value) {
    if (value == null || !encryptionOperations.isEncrypted(value)) {
      return null;
    }
    return encryptionOperations.parseKeyIdFromPrefix(value);
  }
}
