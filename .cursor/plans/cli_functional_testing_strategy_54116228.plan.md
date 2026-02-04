---
name: CLI Functional Testing Strategy
overview: Establish a comprehensive functional testing strategy for the ezkey CLI (Python) from JUnit tests in ezkey-tests, including Docker container execution, CLI interaction mechanisms (expect vs alternatives), helper classes, and test organization with proper tagging.
todos:
  - id: create-cli-dockerfile
    content: Create Dockerfile for CLI test container in docker/cli-test/Dockerfile with Python, ezkey CLI, and pexpect
    status: pending
  - id: create-cli-helper
    content: Create CliTestHelper Java class in ezkey-tests with Docker exec and direct Python execution support
    status: pending
  - id: add-cli-test-tag
    content: Add CLI constant to TestTags.java for test categorization
    status: pending
  - id: create-basic-cli-tests
    content: Create CliBasicOperationsTest with version, help, and configuration tests
    status: pending
  - id: create-cli-auth-tests
    content: Create CliAuthenticationTest for admin login and token management
    status: pending
  - id: create-cli-enrollment-tests
    content: Create CliEnrollmentFlowTest for enrollment bind/verify via CLI
    status: pending
  - id: create-cli-integration-tests
    content: Create CliIntegrationManagementTest for integration CRUD operations via CLI
    status: pending
isProject: false
---

# CLI Functional Testing Strategy for Ezkey

## Overview

This plan establishes a functional testing strategy for the ezkey Python CLI from JUnit tests in `ezkey-tests`. The CLI will run in a Docker container and be controlled remotely via JUnit tests, leveraging the existing Docker stack infrastructure.

## Current State Analysis

### Existing Infrastructure

- **ezkey-tests** (Java/JUnit): Functional tests using RestAssured against Docker stack
- **ezkey-cli-python**: Python CLI with some integration tests (pytest)
- **Docker Stack**: Complete stack running on localhost (Admin API, Auth API, Crypto API)
- **Test Tags**: Existing tagging system in `TestTags.java` for test categorization

### CLI Characteristics

- Python-based CLI (ezkey-cli-python)
- Accessible via command line (`ezkey` command)
- **Hierarchical JSON configuration** (`ezkey.json` in current dir or `~/.ezkey/ezkey.json`)
- **Automatic token persistence** (tokens stored in config files after login/operations)
- **Configuration precedence**: CLI options > `./ezkey.json` > `~/.ezkey/ezkey.json` > defaults
- Some commands are interactive (e.g., admin login flow)

## Architecture Decision: expect vs Alternatives

### Recommendation: **pexpect** (Python) + Process Execution

**Why pexpect over expect (Linux tool):**

- **Cross-platform**: Works on Windows, Linux, Mac (expect is Linux/Unix only)
- **Python-native**: Aligns with CLI being Python
- **Better integration**: Can be called from Java ProcessBuilder
- **Modern**: Actively maintained, better error handling
- **No extra dependencies**: If we containerize the CLI, pexpect is already in Python ecosystem

**Alternative considered: Docker Exec with Non-Interactive Mode**

- Many CLI commands support `--non-interactive` or environment variables
- Simpler for automated testing
- Preferred when possible, fallback to pexpect for interactive flows

**Final Strategy:**

1. **Primary**: Use non-interactive mode for commands that support it (flags, env vars, config files)
2. **Fallback**: Use pexpect script wrapper for interactive commands
3. **Execution**: Run CLI commands via `docker exec` in a CLI container, or directly if Python is available in test environment

## Proposed Solution

### 1. CLI Docker Container

Create a dedicated Docker container for CLI testing that:

- Contains the ezkey CLI installed
- Has network access to the Docker stack (via docker network)
- Can be invoked via `docker exec` from JUnit tests
- Includes pexpect for interactive command support when needed

**Container Structure:**

```
ezkey-cli-test/
├── Dockerfile
├── requirements.txt (includes pexpect)
└── scripts/
    ├── cli-wrapper.sh (entrypoint)
    └── cli-expect-wrapper.py (pexpect wrapper for interactive commands)
```

### 2. JUnit Helper Class: CliTestHelper

Create `org.ezkey.tests.util.CliTestHelper` in `ezkey-tests`:

**Responsibilities:**

- Execute CLI commands via Docker container or local Python
- Capture stdout/stderr and return results
- Handle configuration injection (URLs, tokens)
- Parse JSON output from CLI
- Provide fluent API for common CLI operations

**Key Methods:**

```java
public class CliTestHelper {
  // Execute CLI command and return result
  CliExecutionResult execute(String... args);

  // Convenience methods for common operations
  CliExecutionResult adminIntegrationList();
  CliExecutionResult adminAuthLogin(String username);
  CliExecutionResult authEnrollmentBind(int enrollmentId, String token);

  // Configuration management
  void configure(String adminUrl, String authUrl, String cryptoUrl);
  void setAuthToken(String token);
}
```

### 3. CLI Execution Model

**Option A: Docker Exec (Recommended)**

- CLI container joins the Docker stack network
- JUnit tests execute: `docker exec ezkey-cli-test ezkey <args>`
- Pros: Isolated, consistent, no Python requirements on host
- Cons: Requires Docker, slightly slower

**Option B: Direct Python Execution (Fallback)**

- If Python available: `python -m ezkey_cli.main <args>`
- Pros: Faster, simpler
- Cons: Requires Python on host, less isolated

**Implementation:** Support both, prefer Docker exec, fallback to direct if container not available.

### 4. Test Tag: TestTags.CLI

Add to `TestTags.java`:

```java
/** Tests related to CLI functionality */
public static final String CLI = "cli";
```

### 5. Test Organization

**Recommendation: Keep in ezkey-tests**

**Rationale:**

- Existing infrastructure already supports cross-service testing
- CLI is part of the system, not a separate project
- Test tags provide sufficient organization
- No need for separate module yet

**Structure:**

```
ezkey-tests/src/test/java/org/ezkey/tests/
├── util/
│   └── CliTestHelper.java (NEW)
└── security/
    └── cli/ (NEW)
        ├── CliBasicOperationsTest.java
        ├── CliAuthenticationTest.java
        ├── CliEnrollmentFlowTest.java
        └── CliIntegrationManagementTest.java
```

## Implementation Steps

### Phase 1: Infrastructure Setup and Validation (✅ COMPLETED)

**Goal**: Establish the foundation with minimal complexity - infrastructure, organization, documentation, and a single trivial test to validate everything works.

**Decision on Profiling**: Tests respect the active profile of the already-running Docker stack. No profile changes are imposed from test execution. This allows tests to run in both production-like (rate-limited) and test (permissive) modes depending on how the stack was started.

#### Step 1: Create CLI Docker Container

- Create `docker/cli-test/Dockerfile` based on Python image
- Install ezkey CLI and dependencies (including pexpect)
- Configure entrypoint for CLI execution
- Add to `docker/docker-compose.yml` (optional, for convenience)

**Deliverable**: CLI can be executed in a Docker container via `docker exec`

#### Step 2: Create CliTestHelper Java Class (Basic Implementation)

- Implement Docker exec execution path (primary)
- Implement basic result capture (stdout, stderr, exit code)
- Add configuration file management (create temp `ezkey.json` files)
- Add basic result parsing (exit code validation, stdout access)

**Scope**: Keep it simple - just enough to execute commands and capture results. Advanced features can be added later.

**Deliverable**: `CliTestHelper` class with `execute()` method that can run CLI commands

#### Step 3: Add Test Tag

- Add `CLI` constant to `TestTags.java`

**Deliverable**: Tests can be tagged and filtered with `@Tag(TestTags.CLI)`

#### Step 4: Create Single Trivial Validation Test

- Create `CliBasicOperationsTest` with **ONE simple test**: `testCliVersion()`
- Test should execute `ezkey --version` and verify:
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      - Exit code is 0
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      - Output contains "ezkey" string
- This validates the entire infrastructure chain works

**Deliverable**: One passing test that proves the infrastructure works end-to-end

#### Step 5: Documentation

- Update `ezkey-tests/README.md` with CLI testing section
- Document how to run CLI tests
- Document CliTestHelper usage (basic examples)

**Deliverable**: Documentation that explains the CLI testing setup

#### Success Criteria for Phase 1

- ✅ CLI Docker container builds and runs in all three compose variants (standard, native, HA)
- ✅ Container is part of the default Docker stack experience (automatically started with clean-start)
- ✅ Interactive shell access via `docker compose exec cli-test bash`
- ✅ CliTestHelper can execute CLI commands via Docker exec
- ✅ CliTestHelper can create and inject configuration files into the container
- ✅ Test tag `CLI` exists and works in TestTags
- ✅ One trivial test (`testCliVersion`) passes and validates the infrastructure end-to-end
- ✅ Documentation updated with CLI testing basics
- ✅ Volume permissions properly configured for sequential test execution
- ✅ Profile neutrality: tests respect the active Docker stack profile (no forced changes)

**Status**: Phase 1 is COMPLETE. All infrastructure is in place and ready for functional test expansion.

---

### Phase 2-5: Future Test Implementation (Separate Plan)

Once Phase 1 infrastructure is validated, a dedicated test implementation plan will add comprehensive CLI test suites:

- **Phase 2**: Basic CLI Tests (version, help, configuration, integration list, etc.)
- **Phase 3**: Authentication Tests (admin login, token management, logout)
- **Phase 4**: Integration Flow Tests (enrollment bind/verify, CRUD operations)
- **Phase 5**: Advanced Testing (error handling, interactive commands, edge cases)

Each test suite will follow the same patterns:

- Use `@Tag(TestTags.CLI)` + `@Tag(TestTags.SLOW)` for categorization
- Leverage CliTestHelper for command execution
- Respect the active Docker stack profile (no changes imposed)
- Follow test independence, idempotence, and opportunistic resource usage
- Manage state in `.ezkey-test/` volume for sequential execution

## Technical Details

### CLI Configuration Strategy

Based on the CLI's hierarchical configuration system, the strategy leverages the JSON config file approach which is the most natural fit for the CLI architecture.

**CLI Configuration File Structure:**

The CLI uses `ezkey.json` files with hierarchical precedence:

1. **Command-line options** (highest priority)
2. **Current directory** (`./ezkey.json`)
3. **Home directory** (`~/.ezkey/ezkey.json`)
4. **Default values** (lowest priority)

**Configuration JSON Format:**

```json
{
  "adminUrl": "http://localhost:9080",
  "authUrl": "http://localhost:8080",
  "cryptoUrl": "http://localhost:8080",
  "prettyPrint": true,
  "timeout": 30000,
  "bearerToken": "...",        // Stored after admin login
  "recoveryToken": "...",      // Stored after recovery code use
  "integrationKey": "...",     // Stored after API key creation
  "secretKey": "..."           // Stored after API key creation
}
```

**Recommended Approach: Config File Injection (Preferred)**

**Why Config Files:**

- CLI is designed to use JSON config files natively
- Tokens are automatically persisted to config files after login/operations
- Supports all configuration parameters (URLs, tokens, API keys)
- Works consistently across all CLI commands
- Matches CLI's production usage patterns

**Implementation:**

```java
// Option 1: Current directory config file (via docker exec working directory)
CliExecutionResult result = cliHelper
    .withConfigFile(tempConfigPath)  // Mount or create ./ezkey.json
    .execute("admin", "integration", "list");

// Option 2: Home directory config (via volume mount)
CliExecutionResult result = cliHelper
    .withHomeConfig(configJson)  // Mount ~/.ezkey/ezkey.json
    .execute("admin", "integration", "list");

// Option 3: Command-line flags (for URL overrides only)
CliExecutionResult result = cliHelper
    .execute("--admin-url", "http://localhost:9080",
             "admin", "integration", "list");
```

**Token Management:**

```java
// Pre-load token into config file
String adminToken = authTokenManager.getAdminToken();
cliHelper.setAuthToken(adminToken);  // Adds bearerToken to config

// CLI automatically uses token from config for subsequent commands
CliExecutionResult result = cliHelper.execute("admin", "integration", "list");

// After login, CLI persists token to config automatically
cliHelper.execute("admin", "auth", "login", "--username", "admin");
// Token is now in config file for future commands
```

**Configuration File Creation in Tests:**

```java
public class CliTestHelper {
  // Create temporary config file with Docker stack URLs
  private Path createTempConfig(String adminUrl, String authUrl, String cryptoUrl) {
    Path configFile = tempDir.resolve("ezkey.json");
    Map<String, Object> config = Map.of(
        "adminUrl", adminUrl,
        "authUrl", authUrl,
        "cryptoUrl", cryptoUrl,
        "prettyPrint", false  // Easier parsing in tests
    );
    // Write JSON to file
    objectMapper.writeValue(configFile.toFile(), config);
    return configFile;
  }

  // Mount config file in Docker container or copy to container
  CliExecutionResult executeWithConfig(Path configFile, String... args) {
    // Option A: Mount as volume at /work/ezkey.json
    // Option B: Copy to container and set working directory
    // Option C: Mount at ~/.ezkey/ezkey.json
  }
}
```

**Fallback Options:**

1. **Command-line flags** for URL overrides (simpler but limited)
2. **Environment variables** (if CLI supports them, but currently uses config files)

**Recommendation:** Primary strategy uses config file injection via temporary files or volume mounts. This aligns with CLI's design and supports token persistence naturally.

### Result Parsing

```java
public class CliExecutionResult {
  private final int exitCode;
  private final String stdout;
  private final String stderr;
  private final boolean success;

  // Convenience methods
  public JsonNode json() { /* Parse stdout as JSON */ }
  public void assertSuccess() { /* Assert exitCode == 0 */ }
}
```

### Non-Interactive Mode

For commands that support it:

- `ezkey admin auth login --username admin --no-interactive`
- `EZKEY_ADMIN_TOKEN=xxx ezkey admin integration list`
- Use config files instead of prompts

## Example Test (Phase 1 - Minimal Validation)

```java
@Tag(TestTags.CLI)
@Tag(TestTags.FAST)
public class CliBasicOperationsTest extends AbstractSecurityTest {

  private CliTestHelper cliHelper;

  @BeforeEach
  void setUp() {
    cliHelper = new CliTestHelper(dockerStackConfig);
    // Configure with Docker stack URLs
    cliHelper.configure(
        dockerStackConfig.getAdminApiUrl(),
        dockerStackConfig.getAuthApiUrl(),
        dockerStackConfig.getCryptoApiUrl()
    );
  }

  @Test
  void testCliVersion() {
    // Trivial test to validate infrastructure works
    CliExecutionResult result = cliHelper.execute("--version");

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.exitCode()).isEqualTo(0);
    assertThat(result.stdout()).contains("ezkey");
  }
}
```

**Note**: This is the ONLY test in Phase 1. Its purpose is to validate that:

- CLI container is accessible
- CliTestHelper can execute commands
- Configuration injection works
- Result capture works
- Test execution infrastructure is functional

Additional tests will be added in a separate test implementation plan.

## Docker Compose Integration

Add CLI test container to `docker/docker-compose.yml` (optional, for convenience):

```yaml
  cli-test:
    build:
      context: ../ezkey-cli-python
      dockerfile: ../docker/cli-test/Dockerfile
    container_name: ezkey-cli-test
    networks:
                                                                                 - ezkey-network
    volumes:
                                                                                 - cli-config:/root/.ezkey  # Persistent config directory for tokens
                                                                                 - ./test-config:/work      # Temporary test configs (optional)
    working_dir: /work               # Default working directory for ./ezkey.json
    command: tail -f /dev/null       # Keep container running
    depends_on:
                                                                                 - admin-api
                                                                                 - auth-api
                                                                                 - crypto-api
```

**Configuration Strategy:**

- **Persistent config**: Volume `cli-config` mounted at `/root/.ezkey` for token persistence across test runs
- **Test configs**: Optional volume mount for temporary `./ezkey.json` files per test
- **Working directory**: Default to `/work` where test configs can be placed

## Implementation Decisions (Phase 1)

### 1. Docker Execution Strategy

- **Decision**: Docker-only execution (Linux) - no Python dependencies on host
- **Rationale**: Consistent cross-platform behavior, no pexpect Windows complications
- **Implementation**: All commands via `docker exec ezkey-cli-test ezkey ...`

### 2. Container Availability

- **Decision**: CLI container included in all Docker Compose variants (standard, native, HA)
- **Started**: Automatically with `clean-start.sh` / `clean-start.ps1`
- **Shell Access**: `docker compose exec cli-test bash` for interactive testing

### 3. Configuration Management

- **Strategy**: Config files (`/root/.ezkey/ezkey.json`)
- **Pre-loading**: CliTestHelper writes config before running commands
- **Token Persistence**: CLI automatically persists tokens to config after operations

### 4. Profile Neutrality

- **Decision**: Tests respect the active Docker stack profile
- **No Imposed Changes**: Tests work in both production-like (rate-limited) and test (permissive) modes
- **Flexibility**: Operator controls via `clean-start` flags (--native, --ha, --prod-safe)

### 5. Test Organization

- **Location**: `ezkey-tests/src/test/java/org/ezkey/tests/security/cli/`
- **Tags**: `@Tag(TestTags.CLI)` + `@Tag(TestTags.SLOW)` for filtering
- **Execution**: Maven profiles (default excludes slow, use `-P slow-tests` or `-P all-tests`)
- **CLI Tests**: Explicitly run with `-Dgroups=cli` or via slow-tests profile

### 6. Volume Permissions

- **CLI Config**: Mounted at `/root/.ezkey` with 0775 permissions
- **CLI Work**: Mounted at `/work` with 0775 permissions
- **Sequential Execution**: No parallelism required (tests run sequentially)
- **Persistence**: Volumes persist across test runs for test data reuse

## Summary: Phase 1 Implementation Complete

**Deliverables**:

1. CLI Docker container (Dockerfile, all compose variants)
2. CliTestHelper utility class for command execution
3. CliExecutionResult record for result handling
4. TestTags.CLI constant for test filtering
5. CliBasicOperationsTest with `testCliVersion()` validation
6. Docker compose updates (standard, native, HA)
7. Documentation (docker/README.md, ezkey-tests/README.md)
8. Volume configuration for sequential test execution

**Ready For**: Phases 2-5 test implementation (to be addressed in separate plan)

## Future Enhancements

- Performance benchmarking for CLI commands
- CLI output format validation (JSON schema)
- CLI error message validation
- CLI completion/shell integration testing
- Multi-language CLI testing (if other CLI implementations added)

