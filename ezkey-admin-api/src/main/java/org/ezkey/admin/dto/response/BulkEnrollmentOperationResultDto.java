/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: BulkEnrollmentOperationResultDto
 * Description: Compact result summary for integration-scoped bulk enrollment lifecycle actions.
 */

package org.ezkey.admin.dto.response;

/**
 * Response summary for integration bulk enrollment lifecycle operations.
 *
 * <p>This DTO is returned by integration-scoped bulk lifecycle endpoints such as revoke-all,
 * deactivate-all, and reactivate-all. It allows the Admin UI and operators to distinguish between a
 * successful state change and a successful no-op without treating a no-op as an error.
 *
 * @param affectedCount number of enrollments whose state changed
 * @param skippedCount number of enrollments skipped by defensive guards (for example self-guard)
 * @param noOp {@code true} when the request succeeded but changed no enrollment state
 * @author Ezkey contributors
 * @since 2025
 */
public record BulkEnrollmentOperationResultDto(int affectedCount, int skippedCount, boolean noOp) {}
