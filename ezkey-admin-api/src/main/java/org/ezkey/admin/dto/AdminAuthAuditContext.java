/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.admin.dto;

/**
 * Immutable audit context for admin login MFA flows (username, tenant, admin id for audit rows).
 *
 * @param username admin username
 * @param adminId admin primary key
 * @param tenantId tenant id or null for global admin
 */
public record AdminAuthAuditContext(String username, Integer adminId, Integer tenantId) {}
