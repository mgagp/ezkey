/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

/**
 * Multi-tenant basic operations and GlobalAdmin visibility tests.
 *
 * <p>This package contains tests that validate the multi-tenant infrastructure foundation, focusing
 * on GlobalAdmin behavior and basic tenant operations.
 *
 * <h2>Multi-Tenant Philosophy</h2>
 *
 * <p>Ezkey implements a pragmatic multi-tenant architecture with clear separation of viewing and
 * creating:
 *
 * <ul>
 *   <li><b>GlobalAdmin VIEWING</b>: Sees ALL resources across ALL tenants
 *   <li><b>GlobalAdmin CREATING</b>: Creates resources ONLY in System Tenant (tenantId: 1)
 *   <li><b>TenantAdmin VIEWING</b>: Sees ONLY own tenant resources (automatic filtering)
 *   <li><b>TenantAdmin CREATING</b>: Creates resources ONLY in own tenant (automatic assignment)
 * </ul>
 *
 * <p><b>Key Documentation:</b>
 *
 * <ul>
 *   <li>{@code ../../../../reference/MULTI_TENANT.md} - Complete philosophy and matrices
 *   <li>{@code ../../../../AGENTS.md} - Quick reference for AI agents
 *   <li>{@code docs/testing/MULTI_TENANT_TEST_PLAN.md} - Detailed test plan
 * </ul>
 *
 * <h2>Test Classes</h2>
 *
 * <ul>
 *   <li>{@link org.ezkey.tests.security.tenant.MultiTenantGlobalAdminTest} - GlobalAdmin visibility
 *       and cross-tenant read access
 *   <li>{@link org.ezkey.tests.security.tenant.TenantBasicOperationsTest} - Basic tenant CRUD
 *       operations
 * </ul>
 *
 * <h2>GlobalAdmin Behavior Matrix</h2>
 *
 * <table border="1">
 * <caption>GlobalAdmin Operations</caption>
 * <tr>
 * <th>Operation</th>
 * <th>GlobalAdmin</th>
 * <th>TenantAdmin</th>
 * <th>Notes</th>
 * </tr>
 * <tr>
 * <td>GET /integrations</td>
 * <td>All tenants</td>
 * <td>Own tenant only</td>
 * <td>List filtering</td>
 * </tr>
 * <tr>
 * <td>POST /integrations</td>
 * <td>System Tenant</td>
 * <td>Own tenant</td>
 * <td>Automatic assignment</td>
 * </tr>
 * <tr>
 * <td>GET /api-keys</td>
 * <td>All tenants</td>
 * <td>Own tenant only</td>
 * <td>List filtering</td>
 * </tr>
 * <tr>
 * <td>POST /tenants</td>
 * <td>✅ Allowed</td>
 * <td>❌ 403</td>
 * <td>GlobalAdmin only</td>
 * </tr>
 * </table>
 *
 * <h2>Test Focus</h2>
 *
 * <p><b>GlobalAdmin Visibility Tests:</b>
 *
 * <ul>
 *   <li>GlobalAdmin can view resources from all tenants
 *   <li>GlobalAdmin list endpoints return data from multiple tenants
 *   <li>GlobalAdmin can access any tenant's resources directly
 * </ul>
 *
 * <p><b>Tenant Creation Tests:</b>
 *
 * <ul>
 *   <li>GlobalAdmin can create tenants
 *   <li>Tenant creation enforces unique names
 *   <li>TenantAdmin creation for new tenants
 * </ul>
 *
 * <p><b>Complementary Tests:</b> See {@link org.ezkey.tests.security.multitenant} package for
 * cross-tenant isolation tests (TenantAdmin cannot access other tenants).
 *
 * <h2>Why Separate from Isolation Tests?</h2>
 *
 * <p>This package focuses on <b>positive cases</b> (GlobalAdmin can do X), while the {@code
 * multitenant} package focuses on <b>negative cases</b> (TenantAdmin cannot do X). This separation
 * improves test organization and makes failures easier to diagnose.
 *
 * <h2>Test Data Strategy</h2>
 *
 * <p>Tests use <b>unique data creation</b> for idempotence:
 *
 * <pre>{@code
 * String uniqueSuffix = String.valueOf(System.currentTimeMillis());
 * Integer tenantId = createTenant("Test Tenant " + uniqueSuffix);
 * String adminToken = createTenantAdmin("admin." + uniqueSuffix, tenantId);
 * }</pre>
 *
 * <p>No cleanup required - each test run creates fresh unique data.
 *
 * <h2>Common Patterns</h2>
 *
 * <p><b>Validating GlobalAdmin Sees All:</b>
 *
 * <pre>{@code
 * // Create resources in multiple tenants
 * Integer integrationA = createIntegration(tenantAdminAToken);
 * Integer integrationB = createIntegration(tenantAdminBToken);
 *
 * // GlobalAdmin lists all
 * List<Integer> allIntegrations = listIntegrations(globalAdminToken);
 * assertThat(allIntegrations).contains(integrationA, integrationB);
 * }</pre>
 *
 * @see org.ezkey.tests.security.multitenant
 * @since 2025
 */
package org.ezkey.tests.security.tenant;
