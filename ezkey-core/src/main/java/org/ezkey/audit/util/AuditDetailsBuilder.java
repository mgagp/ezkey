/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Utility: AuditDetailsBuilder
 * Description: Builder utility for creating structured JSON for audit event_details.
 */

package org.ezkey.audit.util;

import java.util.HashMap;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Utility for building structured JSON for audit event_details.
 *
 * <p>Ensures consistent format across all key rotation audits. Provides type-safe methods for
 * building JSON objects that will be stored in the audit log event_details column.
 *
 * <p><b>Usage Example:</b>
 *
 * <pre>{@code
 * String details = AuditDetailsBuilder.builder()
 *     .encryptionKeyId(1234567890L)
 *     .algorithm("AES256_GCM")
 *     .keysetBackup("keyset-backup-2025-12-03.json.encrypted")
 *     .toJson();
 * }</pre>
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
public class AuditDetailsBuilder {

  private final Map<String, Object> details = new HashMap<>();
  private static final ObjectMapper AUDIT_DETAILS_OBJECT_MAPPER = new ObjectMapper();

  /**
   * Create a new builder instance.
   *
   * @return new builder instance
   */
  public static AuditDetailsBuilder builder() {
    return new AuditDetailsBuilder();
  }

  /**
   * Add encryption key ID to details.
   *
   * @param keyId the encryption key ID
   * @return this builder for method chaining
   */
  public AuditDetailsBuilder encryptionKeyId(Long keyId) {
    details.put("encryption_key_id", keyId);
    return this;
  }

  /**
   * Add previous primary key ID to details.
   *
   * @param keyId the previous primary key ID
   * @return this builder for method chaining
   */
  public AuditDetailsBuilder previousPrimaryKeyId(Long keyId) {
    details.put("previous_primary_key_id", keyId);
    return this;
  }

  /**
   * Add algorithm to details.
   *
   * @param algorithm the encryption algorithm (e.g., AES256_GCM)
   * @return this builder for method chaining
   */
  public AuditDetailsBuilder algorithm(String algorithm) {
    details.put("algorithm", algorithm);
    return this;
  }

  /**
   * Add keyset backup path to details.
   *
   * @param backupPath the backup file path
   * @return this builder for method chaining
   */
  public AuditDetailsBuilder keysetBackup(String backupPath) {
    details.put("keyset_backup", backupPath);
    return this;
  }

  /**
   * Add triggered by information to details.
   *
   * @param triggeredBy who/what triggered the operation (e.g., SCHEDULED_JOB, admin username)
   * @return this builder for method chaining
   */
  public AuditDetailsBuilder triggeredBy(String triggeredBy) {
    details.put("triggered_by", triggeredBy);
    return this;
  }

  /**
   * Add re-encryption batch ID to details.
   *
   * @param batchId the re-encryption batch ID
   * @return this builder for method chaining
   */
  public AuditDetailsBuilder reencryptionBatchId(Integer batchId) {
    details.put("reencryption_batch_id", batchId);
    return this;
  }

  /**
   * Add target table name to details.
   *
   * @param targetTable the target table name
   * @return this builder for method chaining
   */
  public AuditDetailsBuilder targetTable(String targetTable) {
    details.put("target_table", targetTable);
    return this;
  }

  /**
   * Add target column name to details.
   *
   * @param targetColumn the target column name
   * @return this builder for method chaining
   */
  public AuditDetailsBuilder targetColumn(String targetColumn) {
    details.put("target_column", targetColumn);
    return this;
  }

  /**
   * Add old key ID to details.
   *
   * @param keyId the old encryption key ID (source)
   * @return this builder for method chaining
   */
  public AuditDetailsBuilder oldKeyId(Long keyId) {
    details.put("old_key_id", keyId);
    return this;
  }

  /**
   * Add new key ID to details.
   *
   * @param keyId the new encryption key ID (target)
   * @return this builder for method chaining
   */
  public AuditDetailsBuilder newKeyId(Long keyId) {
    details.put("new_key_id", keyId);
    return this;
  }

  /**
   * Add records total count to details.
   *
   * @param recordsTotal total number of records to process
   * @return this builder for method chaining
   */
  public AuditDetailsBuilder recordsTotal(Integer recordsTotal) {
    details.put("records_total", recordsTotal);
    return this;
  }

  /**
   * Add records done count to details.
   *
   * @param recordsDone number of records successfully processed
   * @return this builder for method chaining
   */
  public AuditDetailsBuilder recordsDone(Integer recordsDone) {
    details.put("records_done", recordsDone);
    return this;
  }

  /**
   * Add records failed count to details.
   *
   * @param recordsFailed number of records that failed processing
   * @return this builder for method chaining
   */
  public AuditDetailsBuilder recordsFailed(Integer recordsFailed) {
    details.put("records_failed", recordsFailed);
    return this;
  }

  /**
   * Add progress percentage to details.
   *
   * @param progressPct progress percentage (0.00-100.00)
   * @return this builder for method chaining
   */
  public AuditDetailsBuilder progressPct(Double progressPct) {
    details.put("progress_pct", progressPct);
    return this;
  }

  /**
   * Add duration in milliseconds to details.
   *
   * @param durationMs operation duration in milliseconds
   * @return this builder for method chaining
   */
  public AuditDetailsBuilder durationMs(Long durationMs) {
    details.put("duration_ms", durationMs);
    return this;
  }

  /**
   * Add throughput (records per second) to details.
   *
   * @param throughputRecPerSec throughput in records per second
   * @return this builder for method chaining
   */
  public AuditDetailsBuilder throughputRecPerSec(Double throughputRecPerSec) {
    details.put("throughput_records_per_sec", throughputRecPerSec);
    return this;
  }

  /**
   * Add last record ID to details.
   *
   * @param lastRecordId last successfully processed record ID (resume point)
   * @return this builder for method chaining
   */
  public AuditDetailsBuilder lastRecordId(Long lastRecordId) {
    details.put("last_record_id", lastRecordId);
    return this;
  }

  /**
   * Add retry count to details.
   *
   * @param retryCount number of retry attempts
   * @return this builder for method chaining
   */
  public AuditDetailsBuilder retryCount(Integer retryCount) {
    details.put("retry_count", retryCount);
    return this;
  }

  /**
   * Add error type to details.
   *
   * @param errorType type of error (e.g., DECRYPTION_FAILED, INVALID_FORMAT)
   * @return this builder for method chaining
   */
  public AuditDetailsBuilder errorType(String errorType) {
    details.put("error_type", errorType);
    return this;
  }

  /**
   * Add error summary to details.
   *
   * @param errorSummary summary of error for troubleshooting
   * @return this builder for method chaining
   */
  public AuditDetailsBuilder errorSummary(String errorSummary) {
    details.put("error_summary", errorSummary);
    return this;
  }

  /**
   * Add custom field to details.
   *
   * <p>Use this for fields not covered by specific methods. Prefer using specific methods when
   * available for consistency.
   *
   * @param key the field key
   * @param value the field value
   * @return this builder for method chaining
   */
  public AuditDetailsBuilder custom(String key, Object value) {
    details.put(key, value);
    return this;
  }

  /**
   * Convert the details map to JSON string.
   *
   * @return JSON string representation of the details
   * @throws IllegalStateException if JSON serialization fails
   */
  public String toJson() {
    try {
      return AUDIT_DETAILS_OBJECT_MAPPER.writeValueAsString(details);
    } catch (JacksonException e) {
      throw new IllegalStateException("Failed to serialize audit details to JSON", e);
    }
  }

  /**
   * Get the details map (for testing or advanced use cases).
   *
   * @return the details map
   */
  public Map<String, Object> getDetails() {
    return new HashMap<>(details);
  }
}
