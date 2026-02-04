/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Utility: TestTags
 * Description: Constants for JUnit 5 test tags used to categorize and filter tests.
 */

package org.ezkey.tests.tags;

/**
 * Test category tags for organizing and filtering functional E2E tests.
 *
 * <p>These tags enable flexible test execution by categorizing tests based on their
 * characteristics. Tests can have multiple tags, and Maven profiles can filter by tag.
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>On Test Classes</h3>
 *
 * <pre>
 * {
 *   &#64;code
 *   &#64;Tag(TestTags.FAST)
 *   @Tag(TestTags.ENCRYPTION)
 *   public class KeyRotationBasicTest {
 *   }
 * }
 * </pre>
 *
 * <h3>On Test Methods (Override)</h3>
 *
 * <pre>
 * {
 *   &#64;code
 *   &#64;Tag(TestTags.SLOW) // Class-level default
 *   public class KeyRotationSyncWindowTest {
 *
 *     &#64;Test
 *     void testSlowOperation() {
 *     } // Inherits @Tag("slow")
 *
 *     @Test
 *     &#64;Tag(TestTags.FAST) // Override for this specific test
 *     void testQuickValidation() {
 *     }
 *   }
 * }
 * </pre>
 *
 * <h2>Maven Execution</h2>
 *
 * <pre>
 * # Run fast tests only (default)
 * mvn test -pl ezkey-tests
 *
 * # Run slow tests only
 * mvn test -pl ezkey-tests -P slow-tests
 *
 * # Run all tests
 * mvn test -pl ezkey-tests -P all-tests
 *
 * # Run smoke tests
 * mvn test -pl ezkey-tests -P smoke-tests
 *
 * # Ad-hoc filtering (without profiles)
 * mvn test -pl ezkey-tests -Dgroups="encryption"
 * mvn test -pl ezkey-tests -DexcludedGroups="slow,time-dependent"
 * </pre>
 *
 * <h2>Tag Guidelines</h2>
 *
 * <ul>
 *   <li>Apply tags at class level for the majority behavior
 *   <li>Override at method level only for exceptions
 *   <li>A test can have multiple tags (e.g., slow + encryption)
 *   <li>Tags are metadata for filtering, not organizational structure
 * </ul>
 *
 * @since 2025
 */
public final class TestTags {

  private TestTags() {
    // Utility class - prevent instantiation
  }

  // ==================== Speed Categories ====================

  /**
   * Fast tests that complete in under 5 seconds.
   *
   * <p>These tests are suitable for:
   *
   * <ul>
   *   <li>CI pipeline on every commit
   *   <li>Pre-push hooks
   *   <li>Quick local validation
   * </ul>
   *
   * <p>This is the <b>default category</b> for CI execution.
   */
  public static final String FAST = "fast";

  /**
   * Slow tests that may take 30+ seconds to complete.
   *
   * <p>These tests are suitable for:
   *
   * <ul>
   *   <li>Nightly CI builds
   *   <li>Pre-release validation
   *   <li>Manual execution when needed
   * </ul>
   *
   * <p>Excluded from default CI execution to keep feedback loop fast.
   */
  public static final String SLOW = "slow";

  // ==================== Behavior Categories ====================

  /**
   * Tests that depend on timing, delays, or time-based logic.
   *
   * <p>These tests may be sensitive to:
   *
   * <ul>
   *   <li>System load (CPU contention)
   *   <li>Thread scheduling
   *   <li>Network latency
   *   <li>Clock precision
   * </ul>
   *
   * <p>May be flaky under heavy load. Consider running in isolation or with retry logic in CI.
   */
  public static final String TIME_DEPENDENT = "time-dependent";

  /**
   * Critical smoke tests for quick validation of core functionality.
   *
   * <p>Smoke tests should:
   *
   * <ul>
   *   <li>Cover the most critical paths
   *   <li>Be fast (under 10 seconds total)
   *   <li>Fail fast if something is fundamentally broken
   * </ul>
   *
   * <p>Use for:
   *
   * <ul>
   *   <li>Post-deployment validation
   *   <li>Quick sanity checks
   *   <li>Health check automation
   * </ul>
   */
  public static final String SMOKE = "smoke";

  // ==================== Feature Categories ====================

  /**
   * Tests related to encryption, decryption, and key management.
   *
   * <p>Includes:
   *
   * <ul>
   *   <li>Key rotation
   *   <li>Re-encryption
   *   <li>Keyset synchronization
   *   <li>Encryption at rest
   * </ul>
   */
  public static final String ENCRYPTION = "encryption";

  /**
   * Tests related to enrollment flow.
   *
   * <p>Includes:
   *
   * <ul>
   *   <li>Enrollment creation
   *   <li>Device binding
   *   <li>Enrollment verification
   *   <li>Enrollment lifecycle
   * </ul>
   */
  public static final String ENROLLMENT = "enrollment";

  /**
   * Tests related to authentication flow.
   *
   * <p>Includes:
   *
   * <ul>
   *   <li>Authentication attempt creation
   *   <li>Challenge-response validation
   *   <li>Token generation
   *   <li>Session management
   * </ul>
   */
  public static final String AUTHENTICATION = "authentication";

  /**
   * Tests related to API key management.
   *
   * <p>Includes:
   *
   * <ul>
   *   <li>API key creation
   *   <li>API key rotation
   *   <li>API key revocation
   *   <li>API key authentication
   * </ul>
   */
  public static final String API_KEY = "api-key";

  /**
   * Tests related to admin operations.
   *
   * <p>Includes:
   *
   * <ul>
   *   <li>Admin authentication
   *   <li>Admin token management
   *   <li>Administrative API operations
   * </ul>
   */
  public static final String ADMIN = "admin";

  /**
   * Tests related to integration management.
   *
   * <p>Includes:
   *
   * <ul>
   *   <li>Integration creation
   *   <li>Integration configuration
   *   <li>Integration lifecycle
   * </ul>
   */
  public static final String INTEGRATION = "integration";

  // ==================== Infrastructure Categories ====================

  /**
   * Tests that require specific database state or perform database operations.
   *
   * <p>These tests may:
   *
   * <ul>
   *   <li>Require clean database state
   *   <li>Modify database directly via SQL
   *   <li>Verify database constraints
   * </ul>
   */
  public static final String DATABASE = "database";

  /**
   * Tests that verify cross-instance behavior (Admin API ↔ Auth API).
   *
   * <p>These tests validate:
   *
   * <ul>
   *   <li>Data consistency across services
   *   <li>Keyset synchronization
   *   <li>Distributed operations
   * </ul>
   */
  public static final String CROSS_INSTANCE = "cross-instance";

  /**
   * Tests related to multi-tenant isolation and permissions.
   *
   * <p>Includes:
   *
   * <ul>
   *   <li>Cross-tenant isolation
   *   <li>Tenant boundary permissions
   *   <li>TenantAdmin scope restrictions
   *   <li>Data leakage prevention
   * </ul>
   */
  public static final String MULTI_TENANT = "multi-tenant";

  /**
   * Tests related to security and authorization.
   *
   * <p>Includes:
   *
   * <ul>
   *   <li>Authentication failures (401)
   *   <li>Authorization failures (403)
   *   <li>Access control validation
   *   <li>Security boundaries
   * </ul>
   */
  public static final String SECURITY = "security";
}
