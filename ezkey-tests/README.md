# Ezkey Functional Tests

Comprehensive BDD functional tests for Ezkey using Karate with embedded servers approach.

## Overview

This module provides end-to-end functional testing for Ezkey's Admin API and Auth API using:

- **Karate Framework**: BDD-style API testing with native parallel execution
- **Embedded Servers**: Admin API (port 9080) and Auth API (port 8080) start in-process
- **TestContainers**: PostgreSQL container managed automatically for database tests
- **Automatic Lifecycle**: Servers start before tests and stop after completion

## Architecture

```
ezkey-tests/
├── pom.xml                          # Maven configuration with dependencies
├── src/test/java/
│   └── org/ezkey/tests/
│       ├── EmbeddedServerManager.java   # Manages server lifecycle
│       └── KarateTestRunner.java        # JUnit runner with setup/teardown
└── src/test/resources/
    ├── karate-config.js             # Karate configuration
    ├── admin-test.properties        # Admin API test configuration
    ├── auth-test.properties         # Auth API test configuration
    └── karate/
        ├── admin-api/               # Admin API feature files
        │   ├── integration-management.feature
        │   └── enrollment-management.feature
        ├── auth-api/                # Auth API feature files
        │   └── enrollment-binding.feature
        ├── e2e/                     # End-to-end scenarios
        │   └── passwordless-login.feature
        └── parallel/                # Parallel execution tests
            └── concurrent-auth.feature
```

## Features

### 1. Automated Setup
- No manual server startup required
- PostgreSQL container starts automatically
- Database migrations run before tests
- Servers configured with test-specific settings

### 2. Test Isolation
- Each test run uses fresh database instances
- Automatic cleanup after tests complete
- No state carried over between test runs

### 3. CI/CD Ready
- No external dependencies required
- Self-contained test execution
- Consistent results across environments

### 4. Real Servers
- Tests run against actual Spring Boot applications
- Not mocks or stubs
- Validates real HTTP server behavior

### 5. Comprehensive Scenarios
- **Admin API Tests**: Integration and enrollment management
- **Auth API Tests**: Device binding and authentication
- **E2E Tests**: Complete passwordless login flows
- **Parallel Tests**: Concurrent operations and race condition validation

## Running Tests

### Prerequisites

- Java 21 or higher
- Maven 3.6+
- Docker (for TestContainers)

### Execute All Tests

```bash
# From project root
mvn test -pl ezkey-tests

# With verbose output
mvn test -pl ezkey-tests -X

# Run specific feature
mvn test -pl ezkey-tests -Dkarate.options="--tags @e2e"
```

### Execute from Tests Module

```bash
cd ezkey-tests
mvn test

# Run with specific tags
mvn test -Dkarate.options="--tags @admin-api"

# Parallel execution (configure in Runner)
mvn test -Dkarate.options="--threads 4"
```

## Test Configuration

### Environment Variables

Tests use system properties set by `KarateTestRunner`:

- `admin.url`: Admin API base URL (default: http://localhost:9080)
- `auth.url`: Auth API base URL (default: http://localhost:8080)
- `db.url`: PostgreSQL JDBC URL (set by TestContainers)

### Karate Configuration

The `karate-config.js` file provides shared configuration:

```javascript
{
  adminUrl: 'http://localhost:9080',
  authUrl: 'http://localhost:8080',
  adminBaseUrl: 'http://localhost:9080/api/v1',
  authBaseUrl: 'http://localhost:8080/api/v1',
  headers: {
    'Content-Type': 'application/json',
    'Accept': 'application/json'
  }
}
```

## Writing Tests

### Feature File Structure

```gherkin
Feature: Test Feature Name

  Background:
    * url adminBaseUrl

  Scenario: Test scenario description
    Given path '/endpoint'
    And request { "field": "value" }
    When method POST
    Then status 201
    And match response contains { id: '#number' }
```

### Common Patterns

#### Create an Integration

```gherkin
Given path '/integrations'
And request
  """
  {
    "logo": "https://example.com/logo.png",
    "active": true,
    "i18n": [
      {
        "lang": "en",
        "name": "Test Integration",
        "description": "Description"
      }
    ]
  }
  """
When method POST
Then status 201
And def integrationId = response.integrationId
```

#### Create an Enrollment

```gherkin
Given path '/enrollments'
And request
  """
  {
    "integrationId": #(integrationId),
    "name": "Test Device",
    "challengeRequired": false
  }
  """
When method POST
Then status 201
And def enrollmentId = response.enrollmentId
```

#### End-to-End Flow

```gherkin
# 1. Create integration
# 2. Create enrollment
# 3. Bind device (Auth API)
# 4. Verify enrollment (Auth API)
# 5. Create auth attempt (Admin API)
# 6. Check pending (Auth API)
# 7. Respond to auth (Auth API)
# 8. Verify completion (Admin API)
```

## Test Categories

### Admin API Tests

Located in `karate/admin-api/`:

- `integration-management.feature`: CRUD operations for integrations
- `enrollment-management.feature`: Enrollment lifecycle management

### Auth API Tests

Located in `karate/auth-api/`:

- `enrollment-binding.feature`: Device binding to enrollments

### E2E Tests

Located in `karate/e2e/`:

- `passwordless-login.feature`: Complete authentication flow

### Parallel Tests

Located in `karate/parallel/`:

- `concurrent-auth.feature`: Race condition validation

## Tagging System

Use tags to organize and filter tests:

- `@e2e`: End-to-end scenarios
- `@parallel`: Parallel execution tests
- `@ignore`: Skip these tests

Example:

```bash
# Run only e2e tests
mvn test -Dkarate.options="--tags @e2e"

# Run everything except ignored tests
mvn test -Dkarate.options="--tags ~@ignore"
```

## Troubleshooting

### Tests Fail to Start

**Problem**: Servers don't start or database connection fails

**Solutions**:
1. Ensure Docker is running (required for TestContainers)
2. Check that ports 8080 and 9080 are available
3. Verify Java 21 is being used

### Test Timeouts

**Problem**: Tests time out waiting for server startup

**Solutions**:
1. Increase timeout in `KarateTestRunner`
2. Check server logs for startup errors
3. Ensure sufficient system resources

### Database Migration Errors

**Problem**: Flyway migrations fail

**Solutions**:
1. Check migration scripts in `ezkey-core/src/main/resources/db/migration`
2. Verify PostgreSQL container is running
3. Check TestContainers logs

### Port Conflicts

**Problem**: Address already in use errors

**Solutions**:
1. Stop any running Ezkey servers
2. Use dynamic ports in test configuration
3. Check for zombie processes: `lsof -i :8080` or `lsof -i :9080`

## Performance Considerations

### Test Execution Time

- Initial startup: ~10-15 seconds (server initialization)
- Per test: ~100-500ms (API calls)
- Full suite: ~30-60 seconds (varies by test count)

### Optimization Tips

1. **Parallel Execution**: Enable in `KarateTestRunner`
2. **Database Cleanup**: Use transactions when possible
3. **Server Reuse**: Current implementation starts once per run
4. **Selective Testing**: Use tags to run specific suites

## Integration with CI/CD

### GitHub Actions Example

```yaml
- name: Run Functional Tests
  run: |
    export JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-amd64
    mvn test -pl ezkey-tests
```

### Maven Profile

```xml
<profile>
  <id>functional-tests</id>
  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-surefire-plugin</artifactId>
        <configuration>
          <includes>
            <include>**/KarateTestRunner.java</include>
          </includes>
        </configuration>
      </plugin>
    </plugins>
  </build>
</profile>
```

## Benefits Over Other Approaches

### vs MockMvc

- ✅ Tests real HTTP servers
- ✅ Validates race conditions
- ✅ Tests concurrent operations
- ✅ End-to-end validation

### vs Docker Compose

- ✅ Faster startup time
- ✅ No external dependencies
- ✅ Automatic cleanup
- ✅ Better IDE integration

### vs Manual Servers

- ✅ No manual setup
- ✅ Consistent environments
- ✅ Self-contained tests
- ✅ CI/CD friendly

## Future Enhancements

- [ ] Add performance benchmarking tests
- [ ] Implement chaos engineering scenarios
- [ ] Add security penetration tests
- [ ] Create load testing scenarios
- [ ] Add API contract testing
- [ ] Implement smoke test suite

## Contributing

When adding new tests:

1. Follow existing feature file structure
2. Use descriptive scenario names
3. Add appropriate tags
4. Include documentation comments
5. Test both success and error paths

## References

- [Karate Documentation](https://github.com/karatelabs/karate)
- [TestContainers Documentation](https://www.testcontainers.org/)
- [Spring Boot Testing](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing)

## License

This module is part of the Ezkey project and is licensed under the MIT License.
