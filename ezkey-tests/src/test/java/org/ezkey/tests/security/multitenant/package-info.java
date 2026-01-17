/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

/**
 * Multi-tenant cross-isolation security tests.
 *
 * <p>This package contains critical security tests (P0) that validate tenant isolation boundaries.
 * These tests ensure that TenantAdmin users cannot access resources belonging to other tenants.
 *
 * <h2>Multi-Tenant Philosophy</h2>
 *
 * <p>Ezkey implements a pragmatic multi-tenant architecture with the "No Impersonation" principle:
 *
 * <ul>
 *   <li><b>GlobalAdmin</b>: VIEWS all resources (cross-tenant) but CREATES only in System Tenant
 *   <li><b>TenantAdmin</b>: VIEWS and CREATES only within their own tenant
 *   <li><b>No Cross-Tenant Creation</b>: Administrators cannot create resources for other tenants
 * </ul>
 *
 * <p><b>Key Documentation:</b>
 *
 * <ul>
 *   <li>{@code ../../../../reference/MULTI_TENANT.md} - Complete philosophy and matrices
 *   <li>{@code ../../../../AGENTS.md} - Quick reference for AI agents
 *   <li>{@code docs/testing/MULTI_TENANT_TEST_PLAN.md} - Detailed test plan
 *   <li>{@code docs/testing/TENANT_PERMISSIONS_TEST_STRATEGY.md} - Permission matrices
 * </ul>
 *
 * <h2>Test Classes</h2>
 *
 * <ul>
 *   <li>{@link org.ezkey.tests.security.multitenant.TenantCrossIsolationSecurityTest} - P0
 *       cross-tenant isolation tests
 *   <li>{@link org.ezkey.tests.security.multitenant.TenantBoundaryPermissionsSecurityTest} - P1
 *       permission boundary tests
 * </ul>
 *
 * <h2>Expected Behavior Matrix</h2>
 *
 * <table border="1">
 * <caption>Cross-Tenant Access Behavior</caption>
 * <tr>
 * <th>Operation</th>
 * <th>GlobalAdmin</th>
 * <th>TenantAdmin A</th>
 * <th>TenantAdmin B</th>
 * </tr>
 * <tr>
 * <td>GET /integrations</td>
 * <td>All tenants</td>
 * <td>Only Tenant A</td>
 * <td>Only Tenant B</td>
 * </tr>
 * <tr>
 * <td>GET /integrations/{B}</td>
 * <td>200 OK</td>
 * <td>403/404</td>
 * <td>200 OK</td>
 * </tr>
 * <tr>
 * <td>POST /integrations</td>
 * <td>System Tenant (1)</td>
 * <td>Tenant A</td>
 * <td>Tenant B</td>
 * </tr>
 * <tr>
 * <td>GET /api-keys</td>
 * <td>All tenants</td>
 * <td>Only Tenant A</td>
 * <td>Only Tenant B</td>
 * </tr>
 * </table>
 *
 * <h2>Critical Test Scenarios</h2>
 *
 * <p><b>P0 - Critical Isolation:</b>
 *
 * <ul>
 *   <li>TenantAdmin A cannot GET Integration B (403/404)
 *   <li>TenantAdmin A cannot GET API Key B (403/404)
 *   <li>TenantAdmin A cannot GET Enrollment B (403/404)
 *   <li>TenantAdmin A list endpoints show only Tenant A resources
 * </ul>
 *
 * <p><b>P0 - GlobalAdmin Visibility:</b>
 *
 * <ul>
 *   <li>GlobalAdmin can GET any integration (all tenants)
 *   <li>GlobalAdmin list endpoints show all tenants
 *   <li>GlobalAdmin creates integrations in System Tenant only
 * </ul>
 *
 * <h2>Test Data Strategy</h2>
 *
 * <p>Tests use <b>unique data creation</b> for idempotence:
 *
 * <pre>{@code
 * String uniqueSuffix = String.valueOf(System.currentTimeMillis());
 * tenantAId = createTenant("Tenant A " + uniqueSuffix);
 * tenantBId = createTenant("Tenant B " + uniqueSuffix);
 * }</pre>
 *
 * <p>No cleanup required - tests can run multiple times without conflicts.
 *
 * <h2>Common Pitfalls</h2>
 *
 * <ul>
 *   <li>⚠️ <b>Using GlobalAdmin token for tenant resources</b>: Always use TenantAdmin tokens to
 *       create tenant-specific resources in isolation tests
 *   <li>⚠️ <b>Expecting 403 on list endpoints</b>: TenantAdmin listings return filtered results,
 *       not 403
 *   <li>⚠️ <b>Missing tenantId in DTOs</b>: Some responses omit tenantId - use indirect assertions
 *       (presence/absence of resource IDs)
 * </ul>
 *
 * @see org.ezkey.tests.security.tenant.MultiTenantGlobalAdminTest
 * @since 2025
 */
package org.ezkey.tests.security.multitenant;
