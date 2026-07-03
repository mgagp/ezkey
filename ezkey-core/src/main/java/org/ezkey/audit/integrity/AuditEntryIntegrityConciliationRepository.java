/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Repository: AuditEntryIntegrityConciliationRepository
 * Description: Persistence for per-entry audit integrity conciliation rows.
 */

package org.ezkey.audit.integrity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link AuditEntryIntegrityConciliation}.
 *
 * @since 2026
 */
@Repository
public interface AuditEntryIntegrityConciliationRepository
    extends JpaRepository<AuditEntryIntegrityConciliation, Long> {

  Optional<AuditEntryIntegrityConciliation> findByAuditLogIdAndStatus(
      Long auditLogId, AuditEntryIntegrityConciliationStatus status);

  List<AuditEntryIntegrityConciliation> findByAuditLogIdInAndStatus(
      Collection<Long> auditLogIds, AuditEntryIntegrityConciliationStatus status);
}
