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
 * <pre>{@code
 * @Tag(TestTags.FAST)
 * @Tag(TestTags.ENCRYPTION)
 * public class KeyRotationBasicTest { }
 * }</pre>
 *
 * <h3>On Test Methods (Override)</h3>
 * <pre>{@code
 * @Tag(TestTags.SLOW)  // Class-level default
 * public class KeyRotationSyncWindowTest {
 *
 *     @Test
 *     void testSlowOperation() { }  // Inherits @Tag("slow")
 *
 *     @Test
 *     @Tag(TestTags.FAST)  // Override for this specific test
 *     void testQuickValidation() { }
 * }
 * }</pre>
 *
 * <h2>Maven Execution</h2>
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
 * <ul>
 *   <li>Apply tags at class level for the majority behavior</li>
 *   <li>Override at method level only for exceptions</li>
 *   <li>A test can have multiple tags (e.g., slow + encryption)</li>
 *   <li>Tags are metadata for filtering, not organizational structure</li>
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
   * <ul>
   *   <li>CI pipeline on every commit</li>
   *   <li>Pre-push hooks</li>
   *   <li>Quick local validation</li>
   * </ul>
   *
   * <p>This is the <b>default category</b> for CI execution.
   */
  public static final String FAST = "fast";

  /**
   * Slow tests that may take 30+ seconds to complete.
   *
   * <p>These tests are suitable for:
   * <ul>
   *   <li>Nightly CI builds</li>
   *   <li>Pre-release validation</li>
   *   <li>Manual execution when needed</li>
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
   * <ul>
   *   <li>System load (CPU contention)</li>
   *   <li>Thread scheduling</li>
   *   <li>Network latency</li>
   *   <li>Clock precision</li>
   * </ul>
   *
   * <p>May be flaky under heavy load. Consider running in isolation
   * or with retry logic in CI.
   */
  public static final String TIME_DEPENDENT = "time-dependent";

  /**
   * Critical smoke tests for quick validation of core functionality.
   *
   * <p>Smoke tests should:
   * <ul>
   *   <li>Cover the most critical paths</li>
   *   <li>Be fast (under 10 seconds total)</li>
   *   <li>Fail fast if something is fundamentally broken</li>
   * </ul>
   *
   * <p>Use for:
   * <ul>
   *   <li>Post-deployment validation</li>
   *   <li>Quick sanity checks</li>
   *   <li>Health check automation</li>
   * </ul>
   */
  public static final String SMOKE = "smoke";

  // ==================== Feature Categories ====================

  /**
   * Tests related to encryption, decryption, and key management.
   *
   * <p>Includes:
   * <ul>
   *   <li>Key rotation</li>
   *   <li>Re-encryption</li>
   *   <li>Keyset synchronization</li>
   *   <li>Encryption at rest</li>
   * </ul>
   */
  public static final String ENCRYPTION = "encryption";

  /**
   * Tests related to enrollment flow.
   *
   * <p>Includes:
   * <ul>
   *   <li>Enrollment creation</li>
   *   <li>Device binding</li>
   *   <li>Enrollment verification</li>
   *   <li>Enrollment lifecycle</li>
   * </ul>
   */
  public static final String ENROLLMENT = "enrollment";

  /**
   * Tests related to authentication flow.
   *
   * <p>Includes:
   * <ul>
   *   <li>Authentication attempt creation</li>
   *   <li>Challenge-response validation</li>
   *   <li>Token generation</li>
   *   <li>Session management</li>
   * </ul>
   */
  public static final String AUTHENTICATION = "authentication";

  /**
   * Tests related to API key management.
   *
   * <p>Includes:
   * <ul>
   *   <li>API key creation</li>
   *   <li>API key rotation</li>
   *   <li>API key revocation</li>
   *   <li>API key authentication</li>
   * </ul>
   */
  public static final String API_KEY = "api-key";

  /**
   * Tests related to admin operations.
   *
   * <p>Includes:
   * <ul>
   *   <li>Admin authentication</li>
   *   <li>Admin token management</li>
   *   <li>Administrative API operations</li>
   * </ul>
   */
  public static final String ADMIN = "admin";

  /**
   * Tests related to integration management.
   *
   * <p>Includes:
   * <ul>
   *   <li>Integration creation</li>
   *   <li>Integration configuration</li>
   *   <li>Integration lifecycle</li>
   * </ul>
   */
  public static final String INTEGRATION = "integration";

  // ==================== Infrastructure Categories ====================

  /**
   * Tests that require specific database state or perform database operations.
   *
   * <p>These tests may:
   * <ul>
   *   <li>Require clean database state</li>
   *   <li>Modify database directly via SQL</li>
   *   <li>Verify database constraints</li>
   * </ul>
   */
  public static final String DATABASE = "database";

  /**
   * Tests that verify cross-instance behavior (Admin API ↔ Auth API).
   *
   * <p>These tests validate:
   * <ul>
   *   <li>Data consistency across services</li>
   *   <li>Keyset synchronization</li>
   *   <li>Distributed operations</li>
   * </ul>
   */
  public static final String CROSS_INSTANCE = "cross-instance";
}

