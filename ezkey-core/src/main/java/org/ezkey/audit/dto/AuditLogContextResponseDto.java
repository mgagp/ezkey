/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AuditLogContextResponseDto
 * Description: Response payload for a bounded audit-log neighborhood around an anchor event.
 */

package org.ezkey.audit.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Response payload for a bounded audit-log neighborhood around an anchor event.
 *
 * <p>This DTO is used by the opinionated investigation flow that lets an operator inspect the
 * immediate context around a single audit event without invoking the full list-search model.
 *
 * @since 2025
 */
public class AuditLogContextResponseDto {

  private Long anchorAuditLogId;
  private boolean hasMoreBefore;
  private boolean hasMoreAfter;
  private List<AuditLogResponseDto> items = new ArrayList<>();

  public Long getAnchorAuditLogId() {
    return anchorAuditLogId;
  }

  public void setAnchorAuditLogId(Long anchorAuditLogId) {
    this.anchorAuditLogId = anchorAuditLogId;
  }

  public boolean isHasMoreBefore() {
    return hasMoreBefore;
  }

  public void setHasMoreBefore(boolean hasMoreBefore) {
    this.hasMoreBefore = hasMoreBefore;
  }

  public boolean isHasMoreAfter() {
    return hasMoreAfter;
  }

  public void setHasMoreAfter(boolean hasMoreAfter) {
    this.hasMoreAfter = hasMoreAfter;
  }

  public List<AuditLogResponseDto> getItems() {
    return items;
  }

  public void setItems(List<AuditLogResponseDto> items) {
    this.items = items;
  }
}
