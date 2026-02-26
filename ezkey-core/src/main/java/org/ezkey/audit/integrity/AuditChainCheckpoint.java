/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Entity: AuditChainCheckpoint
 * Description: JPA entity for audit log chain checkpoint records.
 */

package org.ezkey.audit.integrity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * JPA entity representing a periodic chain checkpoint for audit log completeness proof.
 *
 * <p>Each checkpoint covers a fixed time window (default 5 minutes) and contains:
 *
 * <ul>
 *   <li>An HMAC digest of all audit entries in that window ({@code entries_digest})
 *   <li>A chain HMAC linking to the previous checkpoint ({@code chain_hmac})
 * </ul>
 *
 * <p>The chain of checkpoints provides evidence that no audit entries have been inserted, deleted,
 * or reordered between checkpoints -- a completeness proof that per-entry HMAC alone cannot
 * provide.
 *
 * <p><b>Database Table:</b> ezkey_audit_chain_checkpoint
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2026
 */
@Entity
@Table(name = "ezkey_audit_chain_checkpoint")
public class AuditChainCheckpoint {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "checkpoint_id")
  private Long checkpointId;

  @Column(name = "window_start", nullable = false)
  private OffsetDateTime windowStart;

  @Column(name = "window_end", nullable = false)
  private OffsetDateTime windowEnd;

  @Column(name = "entry_count", nullable = false)
  private int entryCount;

  @Column(name = "first_entry_id")
  private Long firstEntryId;

  @Column(name = "last_entry_id")
  private Long lastEntryId;

  @Column(name = "entries_digest", nullable = false, length = 88)
  private String entriesDigest;

  @Column(name = "prev_chain_hmac", length = 88)
  private String prevChainHmac;

  @Column(name = "chain_hmac", nullable = false, length = 88)
  private String chainHmac;

  @Column(
      name = "created_at",
      nullable = false,
      columnDefinition = "TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP")
  private OffsetDateTime createdAt;

  public AuditChainCheckpoint() {
    this.createdAt = OffsetDateTime.now();
  }

  // Getters and setters

  public Long getCheckpointId() {
    return checkpointId;
  }

  public void setCheckpointId(Long checkpointId) {
    this.checkpointId = checkpointId;
  }

  public OffsetDateTime getWindowStart() {
    return windowStart;
  }

  public void setWindowStart(OffsetDateTime windowStart) {
    this.windowStart = windowStart;
  }

  public OffsetDateTime getWindowEnd() {
    return windowEnd;
  }

  public void setWindowEnd(OffsetDateTime windowEnd) {
    this.windowEnd = windowEnd;
  }

  public int getEntryCount() {
    return entryCount;
  }

  public void setEntryCount(int entryCount) {
    this.entryCount = entryCount;
  }

  public Long getFirstEntryId() {
    return firstEntryId;
  }

  public void setFirstEntryId(Long firstEntryId) {
    this.firstEntryId = firstEntryId;
  }

  public Long getLastEntryId() {
    return lastEntryId;
  }

  public void setLastEntryId(Long lastEntryId) {
    this.lastEntryId = lastEntryId;
  }

  public String getEntriesDigest() {
    return entriesDigest;
  }

  public void setEntriesDigest(String entriesDigest) {
    this.entriesDigest = entriesDigest;
  }

  public String getPrevChainHmac() {
    return prevChainHmac;
  }

  public void setPrevChainHmac(String prevChainHmac) {
    this.prevChainHmac = prevChainHmac;
  }

  public String getChainHmac() {
    return chainHmac;
  }

  public void setChainHmac(String chainHmac) {
    this.chainHmac = chainHmac;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }
}
