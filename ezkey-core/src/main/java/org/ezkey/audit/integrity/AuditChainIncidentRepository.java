/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Repository: AuditChainIncidentRepository
 * Description: Persistence for audit-chain heartbeat operational incidents.
 */

package org.ezkey.audit.integrity;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link AuditChainIncident}.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2026
 */
@Repository
public interface AuditChainIncidentRepository extends JpaRepository<AuditChainIncident, Long> {

  Optional<AuditChainIncident> findFirstByStatusOrderByCreatedAtDesc(
      AuditChainIncidentStatus status);

  Page<AuditChainIncident> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
