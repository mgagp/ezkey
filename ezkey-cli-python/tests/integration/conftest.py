"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

CLI Component: Integration Test Fixtures
Description: pytest fixtures for Docker stack setup and CLI configuration
"""

import logging
import subprocess
import time
from pathlib import Path

import pytest
import requests

logger = logging.getLogger(__name__)

# Path to docker-compose.yml
DOCKER_COMPOSE_PATH = Path(__file__).parent.parent.parent.parent / "docker" / "docker-compose.yml"

# Service URLs (assuming default ports)
ADMIN_API_URL = "http://localhost:9080"
AUTH_API_URL = "http://localhost:8080"
CRYPTO_API_URL = "http://localhost:9090"


def _wait_for_service(url: str, service_name: str, max_wait: int = 120) -> bool:
    """
    Wait for a service to become healthy.

    Args:
        url: Health check URL
        service_name: Name of the service (for logging)
        max_wait: Maximum wait time in seconds

    Returns:
        True if service became healthy, False otherwise
    """
    start_time = time.time()

    while time.time() - start_time < max_wait:
        try:
            response = requests.get(url, timeout=2)
            if response.status_code == 200:
                logger.info("Service %s is healthy", service_name)
                return True
        except requests.exceptions.RequestException:
            pass

        time.sleep(2)

    logger.error("Service %s did not become healthy within %d seconds", service_name, max_wait)
    return False


def _check_docker_stack_running() -> bool:
    """Check if Docker stack is already running."""
    try:
        result = subprocess.run(
            ["docker", "compose", "-f", str(DOCKER_COMPOSE_PATH), "ps", "--format", "json"],
            capture_output=True,
            text=True,
            check=False,
        )

        # Count running services (should have at least postgres, admin-api, auth-api, crypto-api)
        running_services = [line for line in result.stdout.splitlines() if line.strip()]
        return len(running_services) >= 4

    except Exception as e:
        logger.warning("Failed to check Docker stack status: %s", e)
        return False


@pytest.fixture(scope="session")
def docker_stack():
    """
    Ensure Docker stack is running and healthy.

    This fixture:
    - Checks if stack is already running
    - Starts stack if needed
    - Waits for all services to be healthy
    - Does NOT tear down stack after tests (keeps it running for reuse)
    """
    logger.info("Setting up Docker stack fixture...")

    # Check if stack is already running
    if not _check_docker_stack_running():
        logger.info("Docker stack not running, starting services...")
        try:
            subprocess.run(
                ["docker", "compose", "-f", str(DOCKER_COMPOSE_PATH), "up", "-d"],
                check=True,
                capture_output=True,
            )
            logger.info("Docker stack started, waiting for services to be healthy...")
        except subprocess.CalledProcessError as e:
            pytest.fail(f"Failed to start Docker stack: {e}")

    # Wait for all services to be healthy
    services = [
        (f"{ADMIN_API_URL}/actuator/health", "admin-api"),
        (f"{AUTH_API_URL}/actuator/health", "auth-api"),
        (f"{CRYPTO_API_URL}/actuator/health", "crypto-api"),
    ]

    for url, name in services:
        if not _wait_for_service(url, name):
            pytest.fail(f"Service {name} did not become healthy")

    yield {
        "admin_url": ADMIN_API_URL,
        "auth_url": AUTH_API_URL,
        "crypto_url": CRYPTO_API_URL,
    }

    # Don't tear down - keep stack running for other tests/manual use
    logger.info("Docker stack fixture completed (stack left running)")


@pytest.fixture
def cli_config(docker_stack, tmp_path):
    """
    Create temporary CLI config file pointing to Docker stack.

    Args:
        docker_stack: Docker stack fixture
        tmp_path: pytest temporary directory fixture

    Returns:
        Path to temporary config file
    """
    config_file = tmp_path / "ezkey.json"
    config_content = f"""{{
    "adminUrl": "{docker_stack["admin_url"]}",
    "authUrl": "{docker_stack["auth_url"]}",
    "cryptoUrl": "{docker_stack["crypto_url"]}"
}}"""
    config_file.write_text(config_content)
    return config_file


@pytest.fixture
def database_helper():
    """Provide DatabaseHelper instance for opportunistic DB access."""
    from tests.util.database_helper import DatabaseHelper

    return DatabaseHelper()


@pytest.fixture
def docker_helper():
    """Provide DockerHelper instance for log access."""
    from tests.util.docker_helper import DockerHelper

    return DockerHelper()


@pytest.fixture
def bootstrap_helper():
    """Provide BootstrapHelper instance for credential extraction."""
    from tests.util.bootstrap_helper import BootstrapHelper

    return BootstrapHelper()


@pytest.fixture
def admin_credentials(bootstrap_helper):
    """
    Provide bootstrap credentials for admin enrollment.

    Extracts credentials from Docker logs (cached if available).
    """
    return bootstrap_helper.load_or_extract_credentials()

