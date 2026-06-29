/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Repository: ScheduledJobLastRunRepository
 * Description: Spring Data JPA repository for scheduled job last-run rows.
 */

package org.ezkey.audit.integrity;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link ScheduledJobLastRun} rows.
 *
 * @since 2026
 */
public interface ScheduledJobLastRunRepository
    extends JpaRepository<ScheduledJobLastRun, ScheduledJobKey> {}
