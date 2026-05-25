# Ezkey CLI Testing Guide

**Strategy, Implementation, and Best Practices**

## Table of Contents

1. [Testing Philosophy](#testing-philosophy)
2. [Test Architecture](#test-architecture)
3. [Testing Strategy Decision Matrix](#testing-strategy-decision-matrix)
4. [Integration Testing Strategy](#integration-testing-strategy)
5. [Running Tests](#running-tests)
6. [Writing Tests](#writing-tests)
7. [Best Practices](#best-practices)

---

## Testing Philosophy

**Goal:** Validate CLI functionality against real Docker stack after upgrades, not continuous testing.

**Principles:**
- ✅ **Real-world**: Tests against actual running services (not mocks)
- ✅ **Independent**: Each test can run standalone
- ✅ **Idempotent**: Tests can be run multiple times safely
- ✅ **Pragmatic**: Focus on critical paths, not exhaustive coverage
- ✅ **Simple**: Easy to run manually when needed

---

## Test Architecture

### Test Suites

The project has **two distinct functional test suites**:

1. **`ezkey-tests/`** - Java functional tests for backend platform
2. **`ezkey-cli-python/tests/integration/`** - Python functional tests for CLI

### Test Structure

```
ezkey-cli-python/
├── tests/
│   ├── unit/
│   │   ├── test_pagination_utils.py   # Pagination helpers (CLI + TUI lists)
│   │   └── test_tui_smoke.py          # TUI imports, config token, AuthManager mocks
│   ├── integration/
│   │   ├── conftest.py                # Shared fixtures (Docker stack setup)
│   │   ├── test_admin_api_basic.py    # Admin API CLI smoke
│   │   └── test_bootstrap_extraction.py
│   └── util/                          # Test helpers (docker, database, bootstrap)
└── pytest.ini                         # Default discovery: tests/integration
```

---

## Testing Strategy Decision Matrix

### Principle: Right Suite for the Right Test

```
┌─────────────────────────────────────────────────────────────┐
│ GOLDEN RULE                                                 │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  If test validates API BEHAVIOR                             │
│  → ezkey-tests (Java)                                       │
│                                                              │
│  If test validates CLI EXPERIENCE                           │
│  → ezkey-cli-python/tests (Python)                         │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### Decision Table

| Criterion | ezkey-tests (Java) | CLI Tests (Python) |
|-----------|-------------------|-------------------|
| **Objective** | Validate backend and APIs | Validate CLI user experience |
| **Language** | Java (JUnit 5) | Python (pytest) |
| **Scope** | API REST, business logic, DB | CLI interface, user workflow |
| **Focus** | HTTP responses, status codes, data | Commands, arguments, output, UX |
| **User** | Integration developers, SDK | Developers and ops using CLI |
| **Environment** | Full Docker stack | Docker stack + CLI installed on host |

### Test in `ezkey-tests/` IF:

#### 1. API Behavior Validation
- Tests HTTP API responses
- Validates status codes (200, 400, 403, 404, etc.)
- Verifies JSON response structure
- Tests HTTP headers
- Validates API errors

#### 2. Backend Business Logic
- Tests business rules
- Validates server-side validations
- Tests authorization logic
- Verifies tenant isolation
- Tests complex backend workflows

#### 3. Backend Integration Tests
- Tests interaction between modules (admin-api ↔ auth-api ↔ crypto-api)
- Validates distributed transactions
- Tests data consistency
- Verifies database state

#### 4. Performance/Load Tests
- Tests API performance
- Validates pagination
- Tests heavy queries
- Verifies timeouts

#### 5. Security Tests
- Tests API authentication
- Validates tokens
- Tests rate limits
- Verifies security validations

### Test in `ezkey-cli-python/tests/` IF:

#### 1. CLI Interface Validation
- Tests CLI commands
- Validates arguments and options
- Verifies console output
- Tests exit codes
- Validates CLI error messages

**Example:**
```python
def test_admin_integration_list_output_format():
    """Tests list command output format."""
    runner = CliRunner()
    result = runner.invoke(cli, ["admin", "integration", "list"])
    
    # Validates CLI experience
    assert result.exit_code == 0
    assert "┌" in result.output  # Table formatting
    assert "│" in result.output
    assert "Integration Name" in result.output
```

#### 2. CLI User Workflow
- Tests command sequences
- Validates user interactions
- Tests interactive prompts
- Verifies batch vs interactive mode

#### 3. CLI Configuration
- Tests config management
- Validates config files
- Tests environment variables
- Verifies profiles

#### 4. CLI Output Formats
- Tests JSON output
- Validates tables
- Tests colors/formatting
- Verifies verbosity

#### 5. CLI Errors and Messages
- Tests user error messages
- Validates help (--help)
- Tests command suggestions
- Verifies exit codes

### Decision Tree

```
New functional test to implement
│
├─► Question 1: Does it test a REST API directly?
│   │
│   ├─► YES → ezkey-tests
│   │
│   └─► NO → Question 2
│
├─► Question 2: Does it test CLI output/behavior?
│   │
│   ├─► YES → CLI Tests
│   │
│   └─► NO → Question 3
│
├─► Question 3: Is it a backend business logic test?
│   │
│   ├─► YES → ezkey-tests
│   │
│   └─► NO → Question 4
│
├─► Question 4: Is it a user workflow via CLI?
│   │
│   ├─► YES → CLI Tests
│   │
│   └─► NO → Question 5
│
└─► Question 5: Does it test backend integration (API ↔ API)?
    │
    ├─► YES → ezkey-tests
    │
    └─► NO → Discuss with team
```

---

## Integration Testing Strategy

### Key Design Decisions

1. **Use Existing Docker Stack**: Leverage `docker/docker-compose.yml`
2. **pytest for Structure**: Use pytest for test organization, focus on integration
3. **Idempotent Fixtures**: Each test cleans up after itself or uses unique IDs
4. **Independent Tests**: No test depends on another test's state
5. **Real HTTP Calls**: Use `click.testing.CliRunner` against real APIs

### Docker Stack Fixture

```python
# tests/integration/conftest.py
import pytest
import subprocess
import time
import requests
from pathlib import Path

DOCKER_COMPOSE_PATH = Path(__file__).parent.parent.parent.parent / "docker" / "docker-compose.yml"

@pytest.fixture(scope="session")
def docker_stack():
    """Ensure Docker stack is running and healthy."""
    # Check if stack is already running
    result = subprocess.run(
        ["docker", "compose", "-f", str(DOCKER_COMPOSE_PATH), "ps", "--format", "json"],
        capture_output=True,
        text=True
    )
    
    services_running = len([line for line in result.stdout.splitlines() if line.strip()])
    
    if services_running < 4:  # postgres, admin-api, auth-api, crypto-api
        # Start stack
        subprocess.run(
            ["docker", "compose", "-f", str(DOCKER_COMPOSE_PATH), "up", "-d"],
            check=True
        )
        
        # Wait for services to be healthy
        _wait_for_services()
    
    yield {
        "admin_url": "http://localhost:9080",
        "auth_url": "http://localhost:8080",
        "crypto_url": "http://localhost:9090"
    }
    
    # Don't tear down - keep stack running for other tests/manual use

def _wait_for_services():
    """Wait for all services to be healthy."""
    services = [
        ("http://localhost:9080/actuator/health", "admin-api"),
        ("http://localhost:8080/actuator/health", "auth-api"),
        ("http://localhost:9090/actuator/health", "crypto-api"),
    ]
    
    max_wait = 120  # 2 minutes
    start_time = time.time()
    
    for url, name in services:
        while time.time() - start_time < max_wait:
            try:
                response = requests.get(url, timeout=2)
                if response.status_code == 200:
                    break
            except:
                pass
            time.sleep(2)
        else:
            raise RuntimeError(f"Service {name} did not become healthy")
```

### Idempotent Test Helpers

```python
# tests/integration/helpers.py
import uuid

def unique_name(prefix: str = "test") -> str:
    """Generate unique test name with timestamp."""
    return f"{prefix}-{uuid.uuid4().hex[:8]}"

def cleanup_integration(cli_runner, admin_url: str, integration_id: int):
    """Safely delete integration (idempotent)."""
    result = cli_runner.invoke(
        cli,
        ["--admin-url", admin_url, "admin", "integration", "delete", "--id", str(integration_id)],
        input="y\n"  # Confirm deletion
    )
    # Ignore errors - might already be deleted

def cleanup_enrollment(cli_runner, admin_url: str, enrollment_id: int):
    """Safely delete enrollment (idempotent)."""
    result = cli_runner.invoke(
        cli,
        ["--admin-url", admin_url, "admin", "enrollment", "delete", "--id", str(enrollment_id)],
        input="y\n"
    )
    # Ignore errors - might already be deleted
```

### Independent Test Examples

```python
# tests/integration/test_admin_api.py
import pytest
from click.testing import CliRunner
from ezkey_cli.main import cli
from .helpers import unique_name, cleanup_integration

class TestAdminIntegrationCommands:
    """Test Admin API integration commands - each test is independent."""
    
    def test_list_integrations(self, docker_stack, cli_config):
        """Test listing integrations - read-only, always safe."""
        runner = CliRunner(env={"EZKEY_CONFIG": str(cli_config)})
        result = runner.invoke(cli, ["admin", "integration", "list"])
        
        assert result.exit_code == 0
        # Should return JSON array (even if empty)
        assert "[" in result.output or "[]" in result.output
    
    def test_create_and_delete_integration(self, docker_stack, cli_config):
        """Test creating and deleting integration - idempotent cleanup."""
        runner = CliRunner(env={"EZKEY_CONFIG": str(cli_config)})
        integration_name = unique_name("integration")
        
        # Create integration
        create_data = f'''{{
            "i18n": [
                {{
                    "language": "en",
                    "name": "{integration_name}",
                    "description": "Test integration"
                }}
            ]
        }}'''
        
        result = runner.invoke(
            cli,
            ["admin", "integration", "create", "--data", create_data]
        )
        
        assert result.exit_code == 0
        # Extract integration ID from response
        import json
        response_data = json.loads(result.output)
        integration_id = response_data.get("id")
        assert integration_id is not None
        
        # Cleanup - idempotent
        try:
            cleanup_integration(runner, docker_stack["admin_url"], integration_id)
        except:
            pass  # Already deleted or error - doesn't matter
```

### Idempotency Patterns

#### Pattern 1: Unique Names
```python
name = f"test-{uuid.uuid4().hex[:8]}"  # Always unique
```

#### Pattern 2: Cleanup in Finally
```python
try:
    # Test code
    pass
finally:
    cleanup(resource_id)  # Always runs, even on failure
```

#### Pattern 3: Check Before Create
```python
# Check if resource exists
if resource_exists(id):
    delete(id)  # Clean up first
create(id)  # Then create fresh
```

#### Pattern 4: Read-Only Tests
```python
# Tests that only read are naturally idempotent
def test_list_integrations():
    # Can run multiple times safely
    pass
```

---

## Running Tests

### Unit tests (no Docker)

```bash
cd ezkey-cli-python
pip install -e .
pip install -r requirements-test.txt
pytest tests/unit/ -v
```

TUI-related unit coverage lives in `tests/unit/test_tui_smoke.py` (imports, bearer token config, legacy migration, mocked `AuthManager`).

### Manual Execution — integration (after upgrade)

```bash
# Ensure Docker stack is running
cd docker
docker compose up -d

# Wait for services to be healthy
cd ../ezkey-cli-python

# Run all integration tests
pytest tests/integration/ -v

# Run specific test file
pytest tests/integration/test_admin_api.py -v

# Run specific test
pytest tests/integration/test_admin_api.py::TestAdminIntegrationCommands::test_list_integrations -v

# Run with output
pytest tests/integration/ -v -s
```

### When to Run Tests

- ✅ After CLI code changes
- ✅ After Admin API changes
- ✅ After upgrading dependencies
- ✅ Before major releases
- ✅ When debugging CLI issues

### When NOT to Run Tests

- ❌ On every commit (too slow)
- ❌ In development loop (use manual testing)
- ❌ For unit-level changes (use unit tests)

---

## Writing Tests

### Test Structure

```python
import pytest
from click.testing import CliRunner
from ezkey_cli.main import cli

class TestMyFeature:
    """Test my feature - each test is independent."""
    
    def test_basic_functionality(self, docker_stack, cli_config):
        """Test basic functionality - read-only, always safe."""
        runner = CliRunner(env={"EZKEY_CONFIG": str(cli_config)})
        result = runner.invoke(cli, ["my", "command"])
        
        assert result.exit_code == 0
        assert "expected output" in result.output
    
    def test_with_cleanup(self, docker_stack, cli_config):
        """Test with resource cleanup."""
        runner = CliRunner(env={"EZKEY_CONFIG": str(cli_config)})
        
        # Create resource
        resource_id = create_resource()
        
        try:
            # Test with resource
            result = runner.invoke(cli, ["my", "command", "--id", str(resource_id)])
            assert result.exit_code == 0
        finally:
            # Cleanup
            cleanup_resource(resource_id)
```

### Checklist for New Test

- [ ] **Independence**: Test doesn't depend on other tests
- [ ] **Idempotency**: Test can run multiple times safely
- [ ] **Cleanup**: Resources are cleaned up (try/finally or unique names)
- [ ] **Assertions**: Clear assertions on expected behavior
- [ ] **Documentation**: Docstring explains what's being tested

---

## Best Practices

### For ezkey-tests (Java)

**DO:**
- ✅ Test REST APIs directly
- ✅ Validate business logic
- ✅ Verify database state
- ✅ Test tenant isolation
- ✅ Validate security rules
- ✅ Test backend performance

**AVOID:**
- ❌ Parse CLI output
- ❌ Test user-facing output formats
- ❌ Validate CLI error messages
- ❌ Test CLI configuration

### For CLI Tests (Python)

**DO:**
- ✅ Test CLI commands
- ✅ Validate user output
- ✅ Verify exit codes
- ✅ Test CLI configuration
- ✅ Validate interactive experience
- ✅ Test output formats

**AVOID:**
- ❌ Test REST APIs directly (except for helpers)
- ❌ Validate backend business logic
- ❌ Verify database state (except helper opportunistically)
- ❌ Test backend performance

### Exceptions and Special Cases

#### 1. Opportunistic Helpers (CLI Tests)

CLI tests **CAN** use DB/API directly via helpers for:
- Test setup (create data)
- State verification (confirm operation)
- Cleanup (cleanup after test)

**But** the test itself must validate the CLI, not the API.

```python
# OK: Helper uses DB for setup
def test_integration_get(database_helper):
    # Setup via helper (not the focus of test)
    integration_id = database_helper.create_integration()
    
    # TEST: Validate the CLI
    result = subprocess.run(["ezkey", "admin", "integration", "get", "--id", str(integration_id)])
    
    assert result.returncode == 0
    assert f"ID: {integration_id}" in result.stdout
```

#### 2. End-to-End Tests (Potentially Both)

For complete workflows involving backend AND CLI:

```
Backend workflow (ezkey-tests):
└─> Tests API ↔ API ↔ DB

CLI workflow (CLI tests):
└─> Tests CLI ↔ API ↔ User
```

---

## Test Coverage Strategy

### Critical Paths (Must Test)

1. **Admin API**:
   - ✅ Integration CRUD (create, read, list, delete)
   - ✅ Enrollment CRUD
   - ✅ Encryption key listing and rotation
   - ✅ API key creation and revocation
   - ✅ Admin authentication (login/logout)

2. **Auth API**:
   - ✅ Enrollment binding
   - ✅ Enrollment verification
   - ✅ Auth attempt pending check
   - ✅ Auth attempt response

3. **Crypto API**:
   - ✅ Proof token generation
   - ✅ Key pair generation
   - ✅ Sign/validate operations

### Nice-to-Have (Test When Time Permits)

- Audit log queries
- Re-encryption operations
- Recovery workflows
- Complex multi-step workflows

---

## Benefits of This Approach

1. ✅ **Real-World Validation**: Tests actual API behavior
2. ✅ **Independent**: Each test stands alone
3. ✅ **Idempotent**: Safe to run multiple times
4. ✅ **Simple**: Easy to understand and maintain
5. ✅ **Pragmatic**: Focus on what matters
6. ✅ **Leverages Existing Infrastructure**: Uses Docker stack you already have

---

## Maintenance

### Adding New Tests

1. Identify scope (backend vs CLI)
2. Use decision matrix to choose suite
3. Follow idempotency patterns
4. Add to appropriate test file
5. Run locally to verify
6. Document in this guide if needed

### Updating Existing Tests

1. Ensure independence is maintained
2. Verify idempotency still works
3. Update cleanup if needed
4. Run full test suite to verify no breakage

---

## Resources

- [pytest Documentation](https://docs.pytest.org/)
- [click.testing Documentation](https://click.palletsprojects.com/en/8.1.x/testing/)
- [Ezkey Tests Guide](../ezkey-tests/guides/TEST_PHILOSOPHY.md)
- [Docker Compose Documentation](https://docs.docker.com/compose/)

---

**Status**: ✅ Strategy Defined | 🚀 Implementation In Progress

**Maintainer**: Ezkey Contributors
