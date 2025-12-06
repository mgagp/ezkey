"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

CLI Component: Bootstrap Helper
Description: Extract bootstrap credentials from Docker logs and manage admin tokens for tests

This is a Python port of the Java BootstrapCredentialsExtractor and AdminBootstrapService.
Extracts initial global admin enrollment credentials from Docker container logs and
provides admin tokens for CLI tests.

Usage Context: Use for:
- Extracting bootstrap credentials from Docker logs
- Obtaining admin tokens for authenticated CLI tests
- Caching credentials and tokens for reuse across test runs

@since 2025
"""

import hashlib
import json
import logging
import re
import subprocess
from dataclasses import dataclass
from pathlib import Path
from typing import List, Optional

import requests

logger = logging.getLogger(__name__)

# Paths for credential caching
CREDENTIALS_FILE_PATH = Path(".ezkey-test/bootstrap-credentials.json")
TOKEN_FILE_PATH = Path(".ezkey-test/admin-token.json")

# Docker container name
DOCKER_CONTAINER_NAME = "ezkey-admin-api"

# Admin username (from Docker environment)
ADMIN_USERNAME = "admin.docker"

# API URLs (from Docker stack)
ADMIN_API_URL = "http://localhost:9080"
AUTH_API_URL = "http://localhost:8080"
CRYPTO_API_URL = "http://localhost:9090"


@dataclass
class BootstrapCredentials:
    """Bootstrap credentials extracted from Docker logs."""

    enrollment_id: int
    enrollment_proof_token: str
    enrollment_challenge_code: int
    recovery_codes: List[str]


class BootstrapHelper:
    """Helper for extracting bootstrap credentials and managing admin tokens."""

    # Patterns for parsing logs (matching Java version)
    ENROLLMENT_ID_PATTERN = re.compile(r"Enrollment ID:\s*(\d+)")
    ENROLLMENT_PROOF_TOKEN_PATTERN = re.compile(
        r"Enrollment Proof Token:\s*([^\r\n]+)", re.MULTILINE
    )
    ENROLLMENT_CHALLENGE_PATTERN = re.compile(r"Enrollment Challenge Code:\s*(\d+)")
    # Recovery codes format: "1. CODE-CODE-CODE-CODE-CODE-CODE-CODE-CODE" or "1. CODE"
    RECOVERY_CODE_PATTERN = re.compile(r"\s+\d+\.\s*([A-Z0-9\-]+)")

    def extract_credentials(self) -> BootstrapCredentials:
        """
        Extract bootstrap credentials from Docker container logs.

        Reads logs from the Admin API container, parses the bootstrap credentials section,
        and returns the extracted information.

        Returns:
            BootstrapCredentials with extracted information

        Raises:
            RuntimeError: If credentials cannot be extracted
        """
        logger.info("Extracting bootstrap credentials from Docker container logs...")

        try:
            # Read logs from Docker container
            logs = self._read_docker_logs()

            # Parse credentials from logs
            credentials = self._parse_credentials(logs)

            # Save to file for future use
            self._save_credentials_to_file(credentials)

            logger.info("✅ Bootstrap credentials extracted successfully")
            logger.debug("  Enrollment ID: %d", credentials.enrollment_id)
            logger.debug(
                "  Enrollment Proof Token: %s...",
                credentials.enrollment_proof_token[: min(20, len(credentials.enrollment_proof_token))],
            )

            return credentials

        except Exception as e:
            raise RuntimeError(
                f"Failed to extract bootstrap credentials from Docker logs: {e}"
            ) from e

    def load_or_extract_credentials(self) -> BootstrapCredentials:
        """
        Load bootstrap credentials from saved file if available, otherwise extract from logs.

        Checks if credentials file exists and loads it, otherwise extracts from logs.
        Validates that the token has the correct format (3 parts separated by dots).

        Returns:
            BootstrapCredentials from file or logs
        """
        if CREDENTIALS_FILE_PATH.exists():
            try:
                logger.info("Loading bootstrap credentials from file: %s", CREDENTIALS_FILE_PATH)
                credentials = self._load_credentials_from_file()

                # Validate token format before using cached credentials
                token = credentials.enrollment_proof_token
                if token:
                    parts = token.split(".")
                    if len(parts) != 3:
                        logger.warning(
                            "Cached token has invalid format (expected 3 parts, got %d). Re-extracting from logs.",
                            len(parts),
                        )
                        # Delete invalid cache file and re-extract
                        try:
                            CREDENTIALS_FILE_PATH.unlink()
                            logger.info("Deleted invalid credentials cache file")
                        except Exception as e:
                            logger.warning("Failed to delete invalid cache file: %s", e)
                        return self.extract_credentials()

                return credentials

            except Exception as e:
                logger.warning("Failed to load credentials from file, extracting from logs: %s", e)

        return self.extract_credentials()

    def get_admin_token(self) -> str:
        """
        Get admin bearer token for authenticated CLI tests.

        This method:
        1. Tries to load cached token from file
        2. Validates token is still valid
        3. If invalid or missing, extracts credentials and creates new token
        4. Caches token for future use

        Returns:
            Admin bearer token

        Raises:
            RuntimeError: If token cannot be obtained
        """
        # Try to load cached token first
        logger.info("Checking for cached admin token...")
        cached_token = self._load_token_from_file()

        if cached_token and self._is_token_valid(cached_token):
            logger.info("✅ Using validated cached admin token from file")
            return cached_token

        # Token invalid or missing - need to create new one
        logger.info("No valid cached token found, will create new token")
        if cached_token:
            # Clear invalid cache
            try:
                TOKEN_FILE_PATH.unlink()
            except Exception:
                pass

        # For now, we'll use a simpler approach: extract credentials and use them
        # Full bootstrap (bind/verify) would require crypto operations which is complex
        # Instead, we'll use the credentials to login via CLI or API
        logger.warning(
            "⚠️  Full bootstrap not yet implemented. "
            "For now, tests requiring authentication should use recovery codes or manual login."
        )
        raise RuntimeError(
            "Admin token creation requires full bootstrap flow (not yet implemented in Python). "
            "Use recovery codes or manual login for authenticated tests."
        )

    def _read_docker_logs(self) -> str:
        """
        Read Docker container logs.

        Returns:
            Log content as string

        Raises:
            RuntimeError: If log reading fails
        """
        logger.debug("Reading logs from Docker container: %s", DOCKER_CONTAINER_NAME)

        try:
            # Docker logs outputs to stderr by default, so we need to capture both
            process = subprocess.run(
                ["docker", "logs", DOCKER_CONTAINER_NAME],
                capture_output=True,
                text=True,
                encoding="utf-8",
                errors="replace",  # Replace encoding errors instead of failing
                check=False,
            )

            if process.returncode != 0:
                raise RuntimeError(f"Docker logs command failed with exit code: {process.returncode}")

            # Combine stdout and stderr (Docker logs outputs to stderr by default)
            stdout = process.stdout or ""
            stderr = process.stderr or ""
            logs = stdout + stderr
            
            # Debug: Log a sample to see what we got
            logger.debug("Logs length: %d chars", len(logs))
            if logs:
                sample_start = max(0, len(logs) - 1000)  # Last 1000 chars
                logger.debug("Logs sample (last 1000 chars): %s", logs[sample_start:])
            
            return logs

        except Exception as e:
            raise RuntimeError(f"Failed to read Docker logs: {e}") from e

    def _parse_credentials(self, logs: str) -> BootstrapCredentials:
        """
        Parse credentials from log content.

        Args:
            logs: Log content to parse

        Returns:
            BootstrapCredentials with extracted information

        Raises:
            RuntimeError: If required information cannot be found
        """
        logger.debug("Parsing credentials from logs...")

        # Find the bootstrap credentials section
        # Try multiple patterns to handle encoding issues with emojis
        credentials_start = -1
        patterns = [
            "GLOBAL ADMIN PASSWORDLESS ENROLLMENT",
            "GLOBAL ADMIN",
            "ENROLLMENT CREDENTIALS",
            "Enrollment ID:",
        ]
        
        for pattern in patterns:
            credentials_start = logs.find(pattern)
            if credentials_start != -1:
                logger.debug("Found credentials section using pattern: %s", pattern)
                break
        
        if credentials_start == -1:
            # Try case-insensitive search
            logs_lower = logs.lower()
            for pattern in patterns:
                credentials_start = logs_lower.find(pattern.lower())
                if credentials_start != -1:
                    logger.debug("Found credentials section using case-insensitive pattern: %s", pattern)
                    break
        
        if credentials_start == -1:
            raise RuntimeError(
                "Bootstrap credentials section not found in logs. "
                "Ensure Admin API has completed bootstrap. "
                f"Searched for patterns: {patterns}"
            )

        # Extract the credentials section (next 5000 chars should be enough)
        credentials_section = logs[credentials_start : min(credentials_start + 5000, len(logs))]

        # Extract enrollment ID
        enrollment_id_match = self.ENROLLMENT_ID_PATTERN.search(credentials_section)
        if not enrollment_id_match:
            raise RuntimeError("Enrollment ID not found in logs")
        enrollment_id = int(enrollment_id_match.group(1))

        # Extract enrollment proof token
        token_match = self.ENROLLMENT_PROOF_TOKEN_PATTERN.search(credentials_section)
        if not token_match:
            logger.error("Failed to find enrollment proof token in logs. Credentials section preview:")
            logger.error(credentials_section[: min(500, len(credentials_section))])
            raise RuntimeError("Enrollment Proof Token not found in logs")
        enrollment_proof_token = token_match.group(1).strip()

        # Validate token format: should have 2 dots (3 parts: random.timestamp.salt)
        parts = enrollment_proof_token.split(".")
        if len(parts) != 3:
            logger.error(
                "Invalid proof token format: expected 3 parts separated by dots, got %d parts",
                len(parts),
            )
            logger.error("Token parts: %s", parts)
            raise RuntimeError(
                f"Invalid proof token format: expected format 'randomPart.timestamp.saltPart', "
                f"got: {enrollment_proof_token[:min(100, len(enrollment_proof_token))]}"
            )

        # Extract enrollment challenge code
        challenge_match = self.ENROLLMENT_CHALLENGE_PATTERN.search(credentials_section)
        if not challenge_match:
            raise RuntimeError("Enrollment Challenge Code not found in logs")
        enrollment_challenge_code = int(challenge_match.group(1))

        # Extract recovery codes
        recovery_codes = []
        recovery_start = credentials_section.find("RECOVERY CODES")
        if recovery_start != -1:
            # Look for recovery codes section (may span multiple lines)
            recovery_section = credentials_section[
                recovery_start : min(recovery_start + 2000, len(credentials_section))
            ]
            logger.debug("Recovery codes section preview: %s", recovery_section[:500])
            recovery_matches = self.RECOVERY_CODE_PATTERN.findall(recovery_section)
            recovery_codes = recovery_matches
            logger.debug("Found %d recovery codes in logs: %s", len(recovery_codes), recovery_codes[:3] if recovery_codes else "none")

        if not recovery_codes:
            logger.warning("No recovery codes found in logs (may be normal if already bound)")

        return BootstrapCredentials(
            enrollment_id=enrollment_id,
            enrollment_proof_token=enrollment_proof_token,
            enrollment_challenge_code=enrollment_challenge_code,
            recovery_codes=recovery_codes,
        )

    def _save_credentials_to_file(self, credentials: BootstrapCredentials) -> None:
        """Save credentials to JSON file."""
        CREDENTIALS_FILE_PATH.parent.mkdir(parents=True, exist_ok=True)

        credentials_data = {
            "enrollmentId": credentials.enrollment_id,
            "enrollmentProofToken": credentials.enrollment_proof_token,
            "enrollmentChallengeCode": credentials.enrollment_challenge_code,
            "recoveryCodes": credentials.recovery_codes,
        }

        with open(CREDENTIALS_FILE_PATH, "w", encoding="utf-8") as f:
            json.dump(credentials_data, f, indent=2)

        logger.debug("Credentials saved to: %s", CREDENTIALS_FILE_PATH)

    def _load_credentials_from_file(self) -> BootstrapCredentials:
        """Load credentials from JSON file."""
        with open(CREDENTIALS_FILE_PATH, "r", encoding="utf-8") as f:
            data = json.load(f)

        return BootstrapCredentials(
            enrollment_id=data["enrollmentId"],
            enrollment_proof_token=data["enrollmentProofToken"],
            enrollment_challenge_code=data["enrollmentChallengeCode"],
            recovery_codes=data.get("recoveryCodes", []),
        )

    def _load_token_from_file(self) -> Optional[str]:
        """Load admin token from cache file."""
        if not TOKEN_FILE_PATH.exists():
            return None

        try:
            with open(TOKEN_FILE_PATH, "r", encoding="utf-8") as f:
                data = json.load(f)
                return data.get("token")
        except Exception as e:
            logger.warning("Failed to load token from file: %s", e)
            return None

    def _is_token_valid(self, token: str) -> bool:
        """
        Validate a token by making a test request to the Admin API.

        Args:
            token: Admin bearer token to validate

        Returns:
            True if token is valid, False otherwise
        """
        try:
            response = requests.get(
                f"{ADMIN_API_URL}/api/v1/integrations",
                headers={"Authorization": f"Bearer {token}"},
                timeout=2,
            )
            return response.status_code == 200
        except Exception as e:
            logger.debug("Token validation failed: %s", e)
            return False

