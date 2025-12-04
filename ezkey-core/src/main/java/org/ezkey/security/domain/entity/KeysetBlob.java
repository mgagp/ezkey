/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Entity: KeysetBlob
 * Description: JPA entity for storing encrypted Tink keyset as database blob.
 */

package org.ezkey.security.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;

/**
 * JPA entity for storing encrypted Tink keyset as database blob.
 *
 * <p>This entity provides centralized keyset storage for distributed deployments where file-based
 * sharing is not available (cloud, Kubernetes). The database serves as the source of truth for the
 * keyset across all application instances.
 *
 * <p><b>Single-Row Design:</b> This table contains exactly one row (id=1) enforced by database
 * constraint. The keyset data is encrypted with the master key before storage.
 *
 * <p><b>Synchronization:</b> All instances check last_updated_at to detect changes and reload the
 * keyset when needed. The version field provides optimistic locking for concurrent updates.
 *
 * <p><b>Database Table:</b> ezkey_keyset_blob
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Entity
@Table(name = "ezkey_keyset_blob")
public class KeysetBlob {

  /** Fixed ID (always 1) for single-row table design. */
  public static final int SINGLETON_ID = 1;

  /**
   * Primary key, always 1 (single-row table enforced by database constraint).
   *
   * <p>This design ensures only one keyset exists in the database at any time.
   */
  @Id
  @Column(name = "id", nullable = false)
  private Integer id = SINGLETON_ID;

  /**
   * Encrypted Tink keyset JSON blob.
   *
   * <p>The keyset is encrypted with the master key using AES-256-GCM before storage. This ensures
   * the keyset remains secure even if database is compromised.
   */
  @Column(name = "keyset_data", nullable = false, columnDefinition = "BYTEA")
  private byte[] keysetData;

  /**
   * Timestamp of last keyset update.
   *
   * <p>Used for change detection and synchronization. Instances compare this timestamp with their
   * cached version to detect when reload is needed.
   */
  @Column(name = "last_updated_at", nullable = false)
  private OffsetDateTime lastUpdatedAt;

  /**
   * Identifier of who/what updated the keyset.
   *
   * <p>Values: SYSTEM (scheduled rotation job), or admin username (manual rotation).
   */
  @Column(name = "updated_by", nullable = false, length = 100)
  private String updatedBy = "SYSTEM";

  /**
   * Optimistic locking version for concurrent update protection.
   *
   * <p>Prevents concurrent modifications from multiple instances.
   */
  @Version
  @Column(name = "version", nullable = false)
  private Long version = 1L;

  /** Default constructor for JPA. */
  public KeysetBlob() {
    this.id = SINGLETON_ID;
    this.lastUpdatedAt = OffsetDateTime.now();
  }

  /**
   * Constructor for creating a new keyset blob record.
   *
   * @param keysetData the encrypted keyset data
   * @param updatedBy who created/updated the keyset
   */
  public KeysetBlob(byte[] keysetData, String updatedBy) {
    this.id = SINGLETON_ID;
    this.keysetData = keysetData;
    this.updatedBy = updatedBy;
    this.lastUpdatedAt = OffsetDateTime.now();
  }

  // Getters and setters

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public byte[] getKeysetData() {
    return keysetData;
  }

  public void setKeysetData(byte[] keysetData) {
    this.keysetData = keysetData;
    this.lastUpdatedAt = OffsetDateTime.now();
  }

  public OffsetDateTime getLastUpdatedAt() {
    return lastUpdatedAt;
  }

  public void setLastUpdatedAt(OffsetDateTime lastUpdatedAt) {
    this.lastUpdatedAt = lastUpdatedAt;
  }

  public String getUpdatedBy() {
    return updatedBy;
  }

  public void setUpdatedBy(String updatedBy) {
    this.updatedBy = updatedBy;
  }

  public Long getVersion() {
    return version;
  }

  public void setVersion(Long version) {
    this.version = version;
  }
}
