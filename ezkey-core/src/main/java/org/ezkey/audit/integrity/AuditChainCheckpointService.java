/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: AuditChainCheckpointService
 * Description: Service for audit chain checkpoint search with dynamic filters.
 */

package org.ezkey.audit.integrity;

import jakarta.persistence.criteria.Predicate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for audit chain checkpoint search.
 *
 * <p>Provides paginated search over checkpoints with optional filters: window range (window_start),
 * entry count range, checkpoint type, and created_at range. Used by the chain-checkpoints search
 * API (Global Admin only) to support SEAL range selection and Declare Gap (anchor checkpoint) use
 * cases.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2026
 */
@Service
public class AuditChainCheckpointService {

  private final AuditChainCheckpointRepository checkpointRepository;

  public AuditChainCheckpointService(AuditChainCheckpointRepository checkpointRepository) {
    this.checkpointRepository = checkpointRepository;
  }

  /**
   * Finds checkpoints matching the given filters with pagination and sort.
   *
   * <p>All filter parameters are optional. When unsorted, default sort is {@code windowStart}
   * ascending (chronological).
   *
   * @param windowStartAfter window_start >= value (inclusive); null to omit
   * @param windowStartBefore window_start &lt; value (exclusive); null to omit
   * @param entryCountMin entry_count >= value; null to omit
   * @param entryCountMax entry_count <= value; null to omit
   * @param checkpointType exact match on checkpoint_type; null to omit
   * @param createdAfter created_at >= value (inclusive); null to omit
   * @param createdBefore created_at &lt; value (exclusive); null to omit
   * @param pageable pagination and sort (default sort: windowStart,asc when unsorted)
   * @return page of checkpoints
   */
  @Transactional(readOnly = true)
  public Page<AuditChainCheckpoint> findCheckpoints(
      OffsetDateTime windowStartAfter,
      OffsetDateTime windowStartBefore,
      Integer entryCountMin,
      Integer entryCountMax,
      String checkpointType,
      OffsetDateTime createdAfter,
      OffsetDateTime createdBefore,
      Pageable pageable) {

    Specification<AuditChainCheckpoint> spec =
        (root, query, cb) -> {
          List<Predicate> predicates = new ArrayList<>();

          Optional.ofNullable(windowStartAfter)
              .ifPresent(v -> predicates.add(cb.greaterThanOrEqualTo(root.get("windowStart"), v)));
          Optional.ofNullable(windowStartBefore)
              .ifPresent(v -> predicates.add(cb.lessThan(root.get("windowStart"), v)));

          Optional.ofNullable(entryCountMin)
              .ifPresent(v -> predicates.add(cb.greaterThanOrEqualTo(root.get("entryCount"), v)));
          Optional.ofNullable(entryCountMax)
              .ifPresent(v -> predicates.add(cb.lessThanOrEqualTo(root.get("entryCount"), v)));

          Optional.ofNullable(checkpointType)
              .ifPresent(v -> predicates.add(cb.equal(root.get("checkpointType"), v)));

          Optional.ofNullable(createdAfter)
              .ifPresent(v -> predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), v)));
          Optional.ofNullable(createdBefore)
              .ifPresent(v -> predicates.add(cb.lessThan(root.get("createdAt"), v)));

          if (pageable.getSort().isUnsorted()) {
            query.orderBy(cb.asc(root.get("windowStart")));
          }

          return cb.and(predicates.toArray(new Predicate[0]));
        };

    return checkpointRepository.findAll(spec, pageable);
  }
}
