/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Entity: EncryptionKey
 * Description: JPA entity representing an encryption key in the Tink keyset.
 */

package org.ezkey.security.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * JPA entity representing an encryption key in the Tink keyset.
 *
 * <p>This entity tracks the lifecycle of encryption keys used for data encryption at rest. Each row
 * represents a key in the Tink keyset with metadata about its status, usage statistics, and
 * rotation history.
 *
 * <p><b>Key Lifecycle:</b>
 *
 * <ul>
 *   <li><b>PENDING:</b> New key waiting for synchronization window before becoming PRIMARY
 *   <li><b>PRIMARY:</b> Active key used for new encryption operations
 *   <li><b>ENABLED:</b> Available for decryption only (old keys kept for backward compatibility)
 *   <li><b>DISABLED:</b> Retired key no longer used (after all data re-encrypted)
 * </ul>
 *
 * <p><b>Database Table:</b> ezkey_encryption_key
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Entity
@Table(name = "ezkey_encryption_key")
public class EncryptionKey {

  /**
   * Tink keyset key ID (unsigned 64-bit integer).
   *
   * <p>This is the primary key matching Tink's KeysetInfo.getPrimaryKeyId(). It uniquely identifies
   * a key within the keyset.
   */
  @Id
  @Column(name = "key_id", nullable = false)
  private Long keyId;

  /**
   * Key status indicating its current role in the keyset.
   *
   * <p>Values:
   *
   * <ul>
   *   <li>PRIMARY: Active key used for new encryption operations
   *   <li>ENABLED: Available for decryption only (kept for backward compatibility)
   *   <li>DISABLED: Retired key no longer used
   * </ul>
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "key_status", nullable = false, length = 20)
  private KeyStatus keyStatus;

  /**
   * Encryption algorithm used by this key.
   *
   * <p>Values: AES256_GCM, CHACHA20_POLY1305
   */
  @Column(name = "algorithm", nullable = false, length = 50)
  private String algorithm;

  /**
   * Timestamp when this key was first added to the keyset.
   *
   * <p>This represents the key rotation event when the key was introduced.
   */
  @Column(name = "introduced_at", nullable = false)
  private OffsetDateTime introducedAt;

  /**
   * Timestamp when this key was promoted to PRIMARY status.
   *
   * <p>Nullable - only set for keys that became primary. Keys created as primary will have this set
   * to introducedAt.
   */
  @Column(name = "promoted_primary_at")
  private OffsetDateTime promotedPrimaryAt;

  /**
   * Timestamp when a PENDING key is scheduled to become PRIMARY.
   *
   * <p>Used for distributed synchronization: all instances wait until this time before the key is
   * promoted. Nullable - only set for PENDING keys.
   */
  @Column(name = "effective_at")
  private OffsetDateTime effectiveAt;

  /**
   * Timestamp when this key was disabled.
   *
   * <p>Nullable - only set when key is retired (status = DISABLED).
   */
  @Column(name = "disabled_at")
  private OffsetDateTime disabledAt;

  /**
   * Migration-scope baseline: total ciphertext units still using this key when it became {@link
   * EncryptionKey.KeyStatus#ENABLED} (demoted from {@link EncryptionKey.KeyStatus#PRIMARY} or
   * synced from the keyset).
   *
   * <p>Each unit is one tracked row/column ciphertext matching {@code ENC:{keyId}:%} (same
   * inventory as re-encryption batches). Zero while primary until demotion. Not used as the sole
   * signal for disablement; see prefix-based verification in {@code
   * org.ezkey.security.KeyUsageVerificationService}.
   */
  @Column(name = "records_encrypted", nullable = false)
  private Long recordsEncrypted = 0L;

  /**
   * Cumulative ciphertext units re-encrypted away from this key (completed batch {@code
   * records_done} summed per migration phase).
   *
   * <p>Reset to zero when the key is demoted to {@link EncryptionKey.KeyStatus#ENABLED} so progress
   * applies to the current migration. Progress vs {@link #recordsEncrypted} is informational; drain
   * uses prefix counts.
   */
  @Column(name = "records_reencrypted", nullable = false)
  private Long recordsReencrypted = 0L;

  /**
   * Timestamp of the last re-encryption batch that processed records encrypted with this key.
   *
   * <p>Used for monitoring re-encryption progress and determining when to disable old keys.
   */
  @Column(name = "last_reencrypt_at")
  private OffsetDateTime lastReencryptAt;

  /**
   * Identifier of who/what introduced this key.
   *
   * <p>Values: SYSTEM (scheduled rotation job), or admin username (manual rotation).
   */
  @Column(name = "created_by", nullable = false, length = 100)
  private String createdBy = "SYSTEM";

  /**
   * Optional notes for audit trail.
   *
   * <p>May contain reason for manual rotation, incident reference, or other audit information.
   */
  @Column(name = "notes", columnDefinition = "TEXT")
  private String notes;

  /**
   * Timestamp when this record was created.
   *
   * <p>Automatically set by database DEFAULT CURRENT_TIMESTAMP.
   */
  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  /** Default constructor for JPA. */
  public EncryptionKey() {
    this.createdAt = OffsetDateTime.now();
  }

  /**
   * Constructor for creating a new encryption key record.
   *
   * @param keyId the Tink key ID
   * @param keyStatus the initial key status
   * @param algorithm the encryption algorithm
   * @param introducedAt when the key was introduced
   * @param createdBy who created the key
   */
  public EncryptionKey(
      Long keyId,
      KeyStatus keyStatus,
      String algorithm,
      OffsetDateTime introducedAt,
      String createdBy) {
    this.keyId = keyId;
    this.keyStatus = keyStatus;
    this.algorithm = algorithm;
    this.introducedAt = introducedAt;
    this.createdBy = createdBy;
    this.createdAt = OffsetDateTime.now();
    this.recordsEncrypted = 0L;
    this.recordsReencrypted = 0L;
  }

  // Getters and setters

  public Long getKeyId() {
    return keyId;
  }

  public void setKeyId(Long keyId) {
    this.keyId = keyId;
  }

  public KeyStatus getKeyStatus() {
    return keyStatus;
  }

  public void setKeyStatus(KeyStatus keyStatus) {
    this.keyStatus = keyStatus;
  }

  public String getAlgorithm() {
    return algorithm;
  }

  public void setAlgorithm(String algorithm) {
    this.algorithm = algorithm;
  }

  public OffsetDateTime getIntroducedAt() {
    return introducedAt;
  }

  public void setIntroducedAt(OffsetDateTime introducedAt) {
    this.introducedAt = introducedAt;
  }

  public OffsetDateTime getPromotedPrimaryAt() {
    return promotedPrimaryAt;
  }

  public void setPromotedPrimaryAt(OffsetDateTime promotedPrimaryAt) {
    this.promotedPrimaryAt = promotedPrimaryAt;
  }

  public OffsetDateTime getEffectiveAt() {
    return effectiveAt;
  }

  public void setEffectiveAt(OffsetDateTime effectiveAt) {
    this.effectiveAt = effectiveAt;
  }

  public OffsetDateTime getDisabledAt() {
    return disabledAt;
  }

  public void setDisabledAt(OffsetDateTime disabledAt) {
    this.disabledAt = disabledAt;
  }

  public Long getRecordsEncrypted() {
    return recordsEncrypted;
  }

  public void setRecordsEncrypted(Long recordsEncrypted) {
    this.recordsEncrypted = recordsEncrypted;
  }

  public Long getRecordsReencrypted() {
    return recordsReencrypted;
  }

  public void setRecordsReencrypted(Long recordsReencrypted) {
    this.recordsReencrypted = recordsReencrypted;
  }

  public OffsetDateTime getLastReencryptAt() {
    return lastReencryptAt;
  }

  public void setLastReencryptAt(OffsetDateTime lastReencryptAt) {
    this.lastReencryptAt = lastReencryptAt;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(String createdBy) {
    this.createdBy = createdBy;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }

  /**
   * Enumeration of encryption key statuses.
   *
   * <p>Defines the lifecycle states of encryption keys in the keyset.
   */
  public enum KeyStatus {
    /** New key waiting for synchronization window before becoming PRIMARY. */
    PENDING,

    /** Active key used for new encryption operations. */
    PRIMARY,

    /** Available for decryption only (kept for backward compatibility). */
    ENABLED,

    /** Retired key no longer used (after all data re-encrypted). */
    DISABLED
  }
}
