/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

/**
 * Audit log tenant visibility and scoping functional tests.
 *
 * <p>This package contains tests that validate tenant-scoped audit log visibility, ensuring that:
 *
 * <ul>
 *   <li>Audit log entries carry the correct {@code tenant_id} for all event outcomes (SUCCESS,
 *       FAILURE, ERROR)
 *   <li>TenantAdmin sees only audit logs from their own tenant
 *   <li>GlobalAdmin sees all audit logs and can filter by {@code tenantId}
 *   <li>System-level events ({@code tenant_id = NULL}) are excluded from TenantAdmin results
 * </ul>
 *
 * <h2>Test Classes</h2>
 *
 * <ul>
 *   <li>{@link org.ezkey.tests.security.audit.AuditLogTenantVisibilityTest} - End-to-end tests for
 *       audit log tenant visibility using auth attempt cancellation as the trigger operation
 * </ul>
 *
 * @see org.ezkey.tests.security.tenant
 * @see org.ezkey.tests.security.multitenant
 * @since 2025
 */
package org.ezkey.tests.security.audit;
