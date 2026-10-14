/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.audit.integrity;

import java.util.List;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.domain.repository.AuditLogEntryHmacView;

/**
 * Test helper implementing {@link AuditLogEntryHmacView}.
 *
 * @since 2026
 */
record TestEntryHmacView(Long auditLogId, String entryHmac) implements AuditLogEntryHmacView {

  @Override
  public Long getAuditLogId() {
    return auditLogId;
  }

  @Override
  public String getEntryHmac() {
    return entryHmac;
  }

  static AuditLogEntryHmacView from(AuditLog entry) {
    return new TestEntryHmacView(entry.getAuditLogId(), entry.getEntryHmac());
  }

  static List<AuditLogEntryHmacView> fromAll(List<AuditLog> entries) {
    return entries.stream().map(TestEntryHmacView::from).toList();
  }
}
