/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

/**
 * Integration and tenant domain (multi-tenant administration boundaries).
 *
 * <p>This package and its subpackages model tenants, integrations, administrators, and API keys. It
 * contains the rules that define how administrators interact with resources in a multi-tenant Ezkey
 * deployment.
 *
 * <h2>Multi-tenant contract</h2>
 *
 * <ul>
 *   <li><b>Tenant scoping:</b> read/list operations may be scoped by tenant ID. When a tenant ID is
 *       provided, results must be restricted to that tenant. When omitted (nullable parameter),
 *       operations are considered global (e.g., GlobalAdmin visibility use-cases).
 *   <li><b>No impersonation:</b> administrators must not create resources for other tenants.
 *       Creation is restricted to the administrator's allowed scope (system tenant for GlobalAdmin,
 *       own tenant for TenantAdmin).
 * </ul>
 *
 * <h2>Special system resources</h2>
 *
 * <p>Ezkey includes special system-level resources (e.g., a system tenant and a system integration)
 * used for instance-level administration. Code using these concepts should document how the system
 * tenant/integration are identified and ensure the convention is consistent across modules.
 *
 * <h2>Key entry points</h2>
 *
 * <ul>
 *   <li>{@link org.ezkey.integration.service.IntegrationService}
 *   <li>{@link org.ezkey.integration.service.ApiKeyService}
 *   <li>{@link org.ezkey.integration.domain.repository.IntegrationRepository}
 *   <li>{@link org.ezkey.integration.domain.repository.TenantRepository}
 *   <li>{@link org.ezkey.integration.domain.repository.ApiKeyRepository}
 *   <li>{@link org.ezkey.integration.domain.entity.Tenant}
 *   <li>{@link org.ezkey.integration.domain.entity.Integration}
 *   <li>{@link org.ezkey.integration.domain.entity.ApiKey}
 *   <li>{@link org.ezkey.integration.domain.entity.EzkeyAdmin}
 * </ul>
 *
 * <h2>References</h2>
 *
 * <ul>
 *   <li>{@code docs/plan/} and {@code docs/testing/} (multi-tenant strategy and test plans)
 * </ul>
 *
 * @since 2025
 */
package org.ezkey.integration;
