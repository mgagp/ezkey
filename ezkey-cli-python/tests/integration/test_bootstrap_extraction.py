"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

CLI Component: Bootstrap Extraction Test
Description: Test bootstrap credentials extraction from Docker logs

This is the foundational test that validates we can extract bootstrap credentials,
which are needed for all authenticated CLI tests.
"""

import logging

import pytest

logger = logging.getLogger(__name__)


class TestBootstrapExtraction:
    """Test bootstrap credentials extraction from Docker logs."""

    def test_extract_bootstrap_credentials(self, bootstrap_helper):
        """
        Test extracting bootstrap credentials from Docker logs.

        This validates that:
        1. Docker container logs are accessible
        2. Bootstrap credentials section exists in logs
        3. Credentials can be parsed correctly
        4. Token format is valid (3 parts separated by dots)
        """
        logger.info("Testing bootstrap credentials extraction...")

        credentials = bootstrap_helper.load_or_extract_credentials()

        # Validate credentials structure
        assert credentials.enrollment_id is not None
        assert credentials.enrollment_id > 0, "Enrollment ID should be positive"
        assert credentials.enrollment_proof_token is not None
        assert len(credentials.enrollment_proof_token) > 0, "Proof token should not be empty"

        # Validate token format: should have 1 dot (2 parts: randomPart.saltPart)
        token_parts = credentials.enrollment_proof_token.split(".")
        assert len(token_parts) == 2, f"Token should have 2 parts, got {len(token_parts)}"

        # Validate challenge code
        assert credentials.enrollment_challenge_code is not None
        assert credentials.enrollment_challenge_code > 0, "Challenge code should be positive"
        assert len(str(credentials.enrollment_challenge_code)) == 6, "Challenge code should be 6 digits"

        logger.info("✅ Bootstrap credentials extracted successfully:")
        logger.info("   Enrollment ID: %d", credentials.enrollment_id)
        logger.info("   Challenge Code: %d", credentials.enrollment_challenge_code)
        logger.info("   Recovery Codes: %d", len(credentials.recovery_codes))

        # Verify credentials are cached
        credentials2 = bootstrap_helper.load_or_extract_credentials()
        assert credentials2.enrollment_id == credentials.enrollment_id
        assert credentials2.enrollment_proof_token == credentials.enrollment_proof_token

        logger.info("✅ Credentials caching works correctly")

