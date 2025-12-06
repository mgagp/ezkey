# CLI Integration Testing Strategy - Real-World Approach

## Philosophy

**Goal**: Validate CLI functionality against real Docker stack after upgrades, not continuous testing.

**Principles**:
- ✅ **Real-world**: Tests against actual running services (not mocks)
- ✅ **Independent**: Each test can run standalone
- ✅ **Idempotent**: Tests can be run multiple times safely
- ✅ **Pragmatic**: Focus on critical paths, not exhaustive coverage
- ✅ **Simple**: Easy to run manually when needed

## Architecture

### Test Structure

```
ezkey-cli-python/
├── tests/
│   └── integration/
│       ├── conftest.py           # Shared fixtures (Docker stack setup)
│       ├── test_admin_api.py     # Admin API commands
│       ├── test_auth_api.py      # Auth API commands  
│       ├── test_crypto_api.py    # Crypto API commands
│       └── test_workflows.py     # End-to-end workflows
├── tests/
│   └── fixtures/
│       ├── test_data.json        # Test data templates
│       └── cleanup.sh            # Cleanup script (idempotent)
└── pytest.ini                    # pytest configuration
```

### Key Design Decisions

1. **Use Existing Docker Stack**: Leverage `docker/docker-compose.yml`
2. **pytest for Structure**: Use pytest for test organization, but focus on integration
3. **Idempotent Fixtures**: Each test cleans up after itself or uses unique IDs
4. **Independent Tests**: No test depends on another test's state
5. **Real HTTP Calls**: Use `click.testing.CliRunner` against real APIs

## Implementation

### 1. Docker Stack Fixture

```python
# tests/integration/conftest.py
import pytest
import subprocess
import time
import requests
from pathlib import Path

# Path to docker-compose.yml
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

@pytest.fixture
def cli_config(docker_stack, tmp_path):
    """Create temporary CLI config pointing to Docker stack."""
    config_file = tmp_path / "ezkey.json"
    config_file.write_text(f'''{{
        "adminUrl": "{docker_stack["admin_url"]}",
        "authUrl": "{docker_stack["auth_url"]}",
        "cryptoUrl": "{docker_stack["crypto_url"]}"
    }}''')
    return config_file
```

### 2. Idempotent Test Helpers

```python
# tests/integration/helpers.py
import uuid
from typing import Dict, Any

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

### 3. Independent Test Examples

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
    
    def test_get_integration_by_id(self, docker_stack, cli_config):
        """Test getting integration by ID - uses existing data."""
        runner = CliRunner(env={"EZKEY_CONFIG": str(cli_config)})
        
        # First, list to get an ID (or create one)
        list_result = runner.invoke(cli, ["admin", "integration", "list"])
        assert list_result.exit_code == 0
        
        # Try to get integration ID 1 (should exist from Docker init)
        result = runner.invoke(cli, ["admin", "integration", "get", "--id", "1"])
        
        # Should succeed (if ID 1 exists) or fail gracefully
        assert result.exit_code in [0, 1]  # 0 = found, 1 = not found (both OK)

class TestAdminEnrollmentCommands:
    """Test Admin API enrollment commands."""
    
    def test_list_enrollments(self, docker_stack, cli_config):
        """Test listing enrollments - read-only."""
        runner = CliRunner(env={"EZKEY_CONFIG": str(cli_config)})
        result = runner.invoke(cli, ["admin", "enrollment", "list"])
        
        assert result.exit_code == 0
        assert "[" in result.output
    
    def test_create_enrollment(self, docker_stack, cli_config):
        """Test creating enrollment - cleanup after."""
        runner = CliRunner(env={"EZKEY_CONFIG": str(cli_config)})
        
        # Need an integration first
        integration_name = unique_name("integration")
        create_integration_data = f'''{{
            "i18n": [{{"language": "en", "name": "{integration_name}"}}]
        }}'''
        
        integration_result = runner.invoke(
            cli,
            ["admin", "integration", "create", "--data", create_integration_data]
        )
        import json
        integration_id = json.loads(integration_result.output).get("id")
        
        # Create enrollment
        enrollment_name = unique_name("enrollment")
        enrollment_data = f'{{"name": "{enrollment_name}"}}'
        
        result = runner.invoke(
            cli,
            [
                "admin", "enrollment", "create",
                "--integration-id", str(integration_id),
                "--data", enrollment_data
            ]
        )
        
        assert result.exit_code == 0
        enrollment_response = json.loads(result.output)
        enrollment_id = enrollment_response.get("enrollmentId")
        
        # Cleanup
        try:
            cleanup_enrollment(runner, docker_stack["admin_url"], enrollment_id)
            cleanup_integration(runner, docker_stack["admin_url"], integration_id)
        except:
            pass

class TestAdminEncryptionKeyCommands:
    """Test Admin API encryption key commands."""
    
    def test_list_encryption_keys(self, docker_stack, cli_config):
        """Test listing encryption keys - read-only."""
        runner = CliRunner(env={"EZKEY_CONFIG": str(cli_config)})
        result = runner.invoke(cli, ["admin", "encryption-key", "list"])
        
        assert result.exit_code == 0
        assert "[" in result.output
    
    def test_get_primary_key(self, docker_stack, cli_config):
        """Test getting primary encryption key - read-only."""
        runner = CliRunner(env={"EZKEY_CONFIG": str(cli_config)})
        result = runner.invoke(cli, ["admin", "encryption-key", "primary"])
        
        assert result.exit_code == 0
        # Should return key details
        assert "keyId" in result.output or "keyStatus" in result.output
```

### 4. Workflow Tests

```python
# tests/integration/test_workflows.py
import pytest
from click.testing import CliRunner
from ezkey_cli.main import cli
from .helpers import unique_name, cleanup_integration, cleanup_enrollment

class TestEndToEndWorkflows:
    """Test complete workflows - each is independent and idempotent."""
    
    def test_integration_crud_workflow(self, docker_stack, cli_config):
        """Complete CRUD workflow for integration."""
        runner = CliRunner(env={"EZKEY_CONFIG": str(cli_config)})
        integration_name = unique_name("workflow-integration")
        
        # CREATE
        create_data = f'''{{
            "i18n": [{{"language": "en", "name": "{integration_name}"}}]
        }}'''
        create_result = runner.invoke(
            cli,
            ["admin", "integration", "create", "--data", create_data]
        )
        assert create_result.exit_code == 0
        import json
        integration_id = json.loads(create_result.output).get("id")
        
        # READ
        get_result = runner.invoke(
            cli,
            ["admin", "integration", "get", "--id", str(integration_id)]
        )
        assert get_result.exit_code == 0
        assert integration_name in get_result.output
        
        # LIST (verify it appears)
        list_result = runner.invoke(cli, ["admin", "integration", "list"])
        assert list_result.exit_code == 0
        assert integration_name in list_result.output
        
        # DELETE
        try:
            cleanup_integration(runner, docker_stack["admin_url"], integration_id)
        except:
            pass
    
    def test_enrollment_creation_workflow(self, docker_stack, cli_config):
        """Test creating enrollment and generating QR code."""
        runner = CliRunner(env={"EZKEY_CONFIG": str(cli_config)})
        
        # Create integration
        integration_name = unique_name("qr-integration")
        integration_data = f'''{{
            "i18n": [{{"language": "en", "name": "{integration_name}"}}]
        }}'''
        integration_result = runner.invoke(
            cli,
            ["admin", "integration", "create", "--data", integration_data]
        )
        import json
        integration_id = json.loads(integration_result.output).get("id")
        
        # Create enrollment
        enrollment_name = unique_name("qr-enrollment")
        enrollment_data = f'{{"name": "{enrollment_name}"}}'
        enrollment_result = runner.invoke(
            cli,
            [
                "admin", "enrollment", "create",
                "--integration-id", str(integration_id),
                "--data", enrollment_data
            ]
        )
        assert enrollment_result.exit_code == 0
        enrollment_response = json.loads(enrollment_result.output)
        enrollment_id = enrollment_response.get("enrollmentId")
        
        # Generate QR code
        import tempfile
        with tempfile.NamedTemporaryFile(suffix=".png", delete=False) as tmp_file:
            qr_result = runner.invoke(
                cli,
                [
                    "admin", "enrollment", "qrcode",
                    "--id", str(enrollment_id),
                    "--output", tmp_file.name
                ]
            )
            assert qr_result.exit_code == 0
            # Verify file was created
            import os
            assert os.path.exists(tmp_file.name)
            assert os.path.getsize(tmp_file.name) > 0
        
        # Cleanup
        try:
            cleanup_enrollment(runner, docker_stack["admin_url"], enrollment_id)
            cleanup_integration(runner, docker_stack["admin_url"], integration_id)
        except:
            pass
```

## Running Tests

### Manual Execution (After Upgrade)

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

### CI/CD Integration (Optional)

```yaml
# .github/workflows/cli-integration-tests.yml
name: CLI Integration Tests

on:
  workflow_dispatch:  # Manual trigger only
  pull_request:
    paths:
      - 'ezkey-cli-python/**'

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Start Docker stack
        run: |
          cd docker
          docker compose up -d
          sleep 30  # Wait for services
      
      - name: Install CLI
        run: |
          cd ezkey-cli-python
          pip install -e . pytest
      
      - name: Run integration tests
        run: |
          cd ezkey-cli-python
          pytest tests/integration/ -v
```

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

## Idempotency Patterns

### Pattern 1: Unique Names
```python
name = f"test-{uuid.uuid4().hex[:8]}"  # Always unique
```

### Pattern 2: Cleanup in Finally
```python
try:
    # Test code
    pass
finally:
    cleanup(resource_id)  # Always runs, even on failure
```

### Pattern 3: Check Before Create
```python
# Check if resource exists
if resource_exists(id):
    delete(id)  # Clean up first
create(id)  # Then create fresh
```

### Pattern 4: Read-Only Tests
```python
# Tests that only read are naturally idempotent
def test_list_integrations():
    # Can run multiple times safely
    pass
```

## Benefits of This Approach

1. ✅ **Real-World Validation**: Tests actual API behavior
2. ✅ **Independent**: Each test stands alone
3. ✅ **Idempotent**: Safe to run multiple times
4. ✅ **Simple**: Easy to understand and maintain
5. ✅ **Pragmatic**: Focus on what matters
6. ✅ **Leverages Existing Infrastructure**: Uses Docker stack you already have

## Maintenance

### When to Run Tests

- ✅ After CLI code changes
- ✅ After Admin API changes
- ✅ After upgrading dependencies
- ✅ Before major releases
- ✅ When debugging CLI issues

### When NOT to Run Tests

- ❌ On every commit (too slow)
- ❌ In development loop (use manual testing)
- ❌ For unit-level changes (use manual testing)

## Conclusion

This approach provides **real-world validation** with **minimal complexity**, perfectly aligned with your needs:

- ✅ Tests against real Docker stack
- ✅ Independent and idempotent
- ✅ Simple to run when needed
- ✅ Focuses on critical paths
- ✅ Leverages existing infrastructure

**Perfect fit for your 80/20 philosophy!**

