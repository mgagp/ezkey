"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

CLI Component: Docker Helper
Description: Helper for accessing Docker logs and container state in integration tests

Provides methods to query Docker container logs and state, enabling opportunistic
verification of application behavior beyond CLI output.

@since 2025
"""

import logging
import subprocess
from typing import Optional

logger = logging.getLogger(__name__)


class DockerHelper:
    """Helper class for accessing Docker logs and container state."""

    def get_logs(self, container_name: str, lines: int = 100, since: Optional[str] = None) -> str:
        """
        Get container logs.

        Args:
            container_name: Name of the Docker container
            lines: Number of recent log lines to retrieve (default: 100)
            since: Time duration (e.g., "1m", "5m", "1h") to get logs since

        Returns:
            Container logs as string, empty string if error
        """
        cmd = ["docker", "logs", "--tail", str(lines), container_name]

        if since:
            cmd.extend(["--since", since])

        try:
            process = subprocess.run(
                cmd,
                capture_output=True,
                text=True,
                check=False,
            )

            if process.returncode != 0:
                logger.warning("Failed to get logs from %s: %s", container_name, process.stderr)
                return ""

            # Combine stdout and stderr (Docker logs outputs to stderr by default)
            return process.stdout + process.stderr

        except Exception as e:
            logger.warning("Failed to get logs from %s: %s", container_name, e)
            return ""

    def get_log_line_count(self, container_name: str) -> int:
        """
        Get total log line count for container.

        Args:
            container_name: Name of the Docker container

        Returns:
            Number of log lines, 0 if error
        """
        try:
            process = subprocess.run(
                ["docker", "logs", container_name],
                capture_output=True,
                text=True,
                check=False,
            )

            if process.returncode != 0:
                return 0

            return len((process.stdout + process.stderr).splitlines())

        except Exception:
            return 0

    def is_container_running(self, container_name: str) -> bool:
        """
        Check if container is running.

        Args:
            container_name: Name of the Docker container

        Returns:
            True if container is running, False otherwise
        """
        try:
            process = subprocess.run(
                ["docker", "ps", "--filter", f"name={container_name}", "--format", "{{.Names}}"],
                capture_output=True,
                text=True,
                check=False,
            )

            return container_name in process.stdout

        except Exception:
            return False

    def get_container_health(self, container_name: str) -> Optional[str]:
        """
        Get container health status.

        Args:
            container_name: Name of the Docker container

        Returns:
            Health status (healthy, unhealthy, starting, none) or None if error
        """
        try:
            process = subprocess.run(
                ["docker", "inspect", "--format", "{{.State.Health.Status}}", container_name],
                capture_output=True,
                text=True,
                check=False,
            )

            if process.returncode != 0:
                return None

            return process.stdout.strip()

        except Exception:
            return None

