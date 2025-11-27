package org.ezkey;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Spring Boot application configuration for JPA tests.
 *
 * <p>This class is required by @DataJpaTest annotations to bootstrap Spring Boot context.
 * It provides minimal configuration needed for repository tests that use H2 in-memory database.
 *
 * @since 2025
 */
@SpringBootApplication
public class TestApplication {}

