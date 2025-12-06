# CLI Integration Tests

Integration tests for Ezkey CLI against real Docker stack.

## Philosophy

These tests follow an **opportunistic testing** approach:
- Tests run against real Docker services (not mocks)
- Tests can verify state in database directly (faster than API calls)
- Tests can check application logs for behavior verification
- Tests are independent and idempotent

## Setup

### Prerequisites

1. Docker stack must be running:
   ```bash
   cd docker
   docker compose up -d
   ```

2. Install test dependencies:
   ```bash
   pip install -r requirements-test.txt
   ```

### Running Tests

```bash
# Run all integration tests
pytest tests/integration/ -v

# Run specific test file
pytest tests/integration/test_admin_api_basic.py -v

# Run specific test
pytest tests/integration/test_admin_api_basic.py::TestAdminIntegrationCommands::test_list_integrations_readonly -v

# Run with output
pytest tests/integration/ -v -s
```

## Test Structure

- `conftest.py` - pytest fixtures (Docker stack setup, CLI config, bootstrap credentials)
- `test_bootstrap_extraction.py` - Bootstrap credentials extraction test (foundational)
- `test_admin_api_basic.py` - Basic Admin API tests
- `util/` - Helper utilities (DatabaseHelper, DockerHelper, BootstrapHelper)

## Bootstrap Credentials

The tests use `BootstrapHelper` to extract initial admin enrollment credentials from Docker logs.
This is similar to the Java `BootstrapCredentialsExtractor` used in functional tests.

**Credentials are cached** in `.ezkey-test/bootstrap-credentials.json` for reuse across test runs.

**Credentials include:**
- Enrollment ID
- Enrollment Proof Token
- Enrollment Challenge Code
- Recovery Codes (if available)

## Writing Tests

### Example: Test with Opportunistic Verification

```python
def test_create_integration_with_verification(docker_stack, cli_config, database_helper, docker_helper, admin_credentials):
    runner = CliRunner(env={"EZKEY_CONFIG": str(cli_config)})
    
    # 1. Create via CLI
    result = runner.invoke(cli, ["admin", "integration", "create", ...])
    assert result.exit_code == 0
    
    # 2. Verify in database (opportunistic)
    integration_id = extract_id(result.output)
    db_status = database_helper.get_integration_active_status(integration_id)
    assert db_status == "true"
    
    # 3. Verify in logs (opportunistic)
    logs = docker_helper.get_logs("ezkey-admin-api", lines=50)
    assert "integration" in logs.lower()
    
    # 4. Cleanup (idempotent)
    database_helper.delete_integration(integration_id)
```

### Example: Using Bootstrap Credentials

```python
def test_with_admin_credentials(admin_credentials):
    # admin_credentials provides:
    # - enrollment_id
    # - enrollment_proof_token
    # - enrollment_challenge_code
    # - recovery_codes
    
    if admin_credentials.recovery_codes:
        # Use recovery code for authentication
        recovery_code = admin_credentials.recovery_codes[0]
        # ... use recovery code for login
```

## Test Principles

1. **Independent**: Each test can run standalone
2. **Idempotent**: Tests can be run multiple times safely
3. **Opportunistic**: Use DB/logs for verification when appropriate
4. **Real-world**: Test against actual Docker services

## Current Status

✅ **Working:**
- Bootstrap credentials extraction from Docker logs
- Database helper for opportunistic DB queries
- Docker helper for log access
- Basic test structure

⏳ **In Progress:**
- Admin token creation (requires full bootstrap flow)
- Authenticated CLI tests (using recovery codes for now)

## Next Steps

1. Implement full bootstrap flow (bind device, verify enrollment)
2. Add admin token creation/management
3. Expand test coverage for all CLI commands
4. Add workflow tests (end-to-end scenarios)
