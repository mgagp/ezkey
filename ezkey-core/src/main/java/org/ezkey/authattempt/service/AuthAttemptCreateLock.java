/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Component: AuthAttemptCreateLock
 * Description: Serializes authentication-attempt creates for one enrollment.
 */

package org.ezkey.authattempt.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

/**
 * Transaction-scoped PostgreSQL advisory lock for authentication-attempt creation.
 *
 * <p>{@code ezkey_auth_attempt} is partitioned by {@code created_at}, so a unique constraint must
 * include that column and cannot enforce one live attempt per enrollment. Locking the enrollment
 * row would share a lock order with device accept (attempt row, then enrollment). This lock
 * serializes creates for one enrollment inside the current transaction and is released on commit or
 * rollback.
 *
 * @since 2026
 */
@Component
public class AuthAttemptCreateLock {

  /**
   * First key of {@code pg_advisory_xact_lock(int, int)}. Namespaces auth-attempt create locks away
   * from any other advisory lock in this database.
   */
  public static final int AUTH_ATTEMPT_CREATE_LOCK_CLASS_ID = 992_001;

  @PersistenceContext private EntityManager entityManager;

  /**
   * Blocks until this transaction holds the create lock for {@code enrollmentId}.
   *
   * @param enrollmentId enrollment whose creates must be serialized
   */
  public void lockEnrollment(Integer enrollmentId) {
    entityManager
        .createNativeQuery("SELECT pg_advisory_xact_lock(CAST(?1 AS integer), CAST(?2 AS integer))")
        .setParameter(1, AUTH_ATTEMPT_CREATE_LOCK_CLASS_ID)
        .setParameter(2, enrollmentId)
        .getResultList();
  }
}
