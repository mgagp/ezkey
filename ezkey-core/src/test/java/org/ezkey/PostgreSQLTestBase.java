/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test Base: PostgreSQLTestBase
 * Description: Base class for PostgreSQL-specific tests using TestContainers.
 */

package org.ezkey;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for PostgreSQL-specific tests using TestContainers.
 *
 * <p>This class provides a shared PostgreSQL container for tests that require PostgreSQL-specific
 * features like FOR NO KEY UPDATE locking. It uses Spring Boot's service connection feature to
 * automatically configure the test datasource.
 *
 * <p><b>Usage:</b> Extend this class for tests that need PostgreSQL-specific functionality:
 *
 * <pre>{@code
 * @SpringBootTest(classes = TestApplication.class)
 * @ActiveProfiles("test")
 * class MyPostgreSQLTest extends PostgreSQLTestBase {
 *   // Test methods here
 * }
 * }</pre>
 *
 * <p><b>Features:</b>
 *
 * <ul>
 *   <li><b>Shared Container:</b> Single PostgreSQL container for all tests in the class
 *   <li><b>Service Connection:</b> Automatic datasource configuration via Spring Boot
 *   <li><b>PostgreSQL 17:</b> Uses latest stable PostgreSQL version
 *   <li><b>Test Profile:</b> Automatically uses "test" profile for configuration
 * </ul>
 *
 * <p><b>Performance:</b> The container is started once per test class and reused for all test
 * methods, providing good performance while ensuring PostgreSQL-specific features are available.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see org.testcontainers.containers.PostgreSQLContainer
 * @see org.springframework.boot.testcontainers.service.connection.ServiceConnection
 */
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
@org.springframework.test.context.TestPropertySource(
    properties = {
      "spring.flyway.enabled=true",
      "spring.flyway.locations=classpath:db/migration",
      "spring.jpa.hibernate.ddl-auto=validate"
    })
public abstract class PostgreSQLTestBase {

  /**
   * PostgreSQL container for tests requiring PostgreSQL-specific features.
   *
   * <p>This container provides a real PostgreSQL database instance for testing features that are
   * not available in H2, such as:
   *
   * <ul>
   *   <li><b>FOR NO KEY UPDATE:</b> PostgreSQL-specific row locking
   *   <li><b>Advanced Indexing:</b> PostgreSQL-specific index features
   *   <li><b>Custom Functions:</b> PostgreSQL-specific SQL functions
   *   <li><b>Concurrency Testing:</b> Real database concurrency behavior
   * </ul>
   *
   * <p><b>Configuration:</b> Uses PostgreSQL 17 (latest stable version) with default settings
   * optimized for testing. The container is automatically configured as a Spring Boot service
   * connection, so no manual datasource configuration is required.
   *
   * <p><b>Singleton Pattern:</b> The container is started once and shared across all test classes
   * to avoid connection pool issues. This significantly improves test performance and reliability.
   */
  @ServiceConnection static PostgreSQLContainer<?> postgres;

  static {
    postgres =
        new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("ezkey_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(false);
    postgres.start();
  }
}
