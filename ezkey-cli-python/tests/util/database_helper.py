"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

CLI Component: Database Helper
Description: Helper for opportunistic database access in CLI integration tests

This is a Python port of the Java DatabaseHelper used in functional tests.
Provides methods to query and update PostgreSQL database directly via docker exec,
avoiding API authentication requirements when appropriate.

Usage Context: Use for:
- State verification (faster than API calls)
- Finding existing entities before creating new ones
- Cleanup operations
- Resetting state for test idempotence

Database Configuration: Assumes PostgreSQL container named "ezkey-postgres"
with database "ezkey_db" and user "postgres".

@since 2025
"""

import logging
import subprocess
from typing import List, Optional

logger = logging.getLogger(__name__)


class DatabaseHelper:
    """Helper class for opportunistic database access in CLI integration tests."""

    DOCKER_CONTAINER = "ezkey-postgres"
    DATABASE = "ezkey_db"
    USER = "postgres"

    def execute_query(self, sql_query: str) -> List[str]:
        """
        Execute SQL query and return results as list of strings (one per row).

        Args:
            sql_query: SQL query to execute

        Returns:
            List of result rows (trimmed strings), empty list if no results or error
        """
        logger.debug("Executing SQL query: %s", sql_query)

        try:
            process = subprocess.run(
                [
                    "docker",
                    "exec",
                    self.DOCKER_CONTAINER,
                    "psql",
                    "-U",
                    self.USER,
                    "-d",
                    self.DATABASE,
                    "-t",
                    "-A",
                    "-c",
                    sql_query,
                ],
                capture_output=True,
                text=True,
                check=False,
            )

            if process.returncode != 0:
                logger.warning("SQL query failed with exit code: %d", process.returncode)
                logger.debug("Error output: %s", process.stderr)
                return []

            # Parse results (one per line, trimmed)
            results = [line.strip() for line in process.stdout.splitlines() if line.strip()]
            logger.debug("Query returned %d rows", len(results))
            return results

        except Exception as e:
            logger.warning("Failed to execute SQL query: %s", e)
            return []

    def execute_query_single_value(self, sql_query: str) -> Optional[str]:
        """
        Execute SQL query and return first result value as string.

        Args:
            sql_query: SQL query to execute (should return single value)

        Returns:
            First result value, or None if no results or error
        """
        results = self.execute_query(sql_query)
        return results[0] if results else None

    def execute_update(self, sql_statement: str) -> bool:
        """
        Execute SQL update/delete/insert statement.

        Args:
            sql_statement: SQL statement to execute

        Returns:
            True if successful, False otherwise
        """
        logger.debug("Executing SQL update: %s", sql_statement)

        try:
            process = subprocess.run(
                [
                    "docker",
                    "exec",
                    self.DOCKER_CONTAINER,
                    "psql",
                    "-U",
                    self.USER,
                    "-d",
                    self.DATABASE,
                    "-c",
                    sql_statement,
                ],
                capture_output=True,
                text=True,
                check=False,
            )

            if process.returncode != 0:
                logger.error("SQL update failed with exit code: %d: %s", process.returncode, process.stderr)
                return False

            logger.debug("SQL update successful")
            return True

        except Exception as e:
            logger.error("Failed to execute SQL update: %s", e)
            return False

    def get_enrollment_status(self, enrollment_id: int) -> Optional[str]:
        """
        Get enrollment status from database.

        Args:
            enrollment_id: Enrollment ID to check

        Returns:
            Enrollment status (CREATED, BOUND, VERIFIED, INVALID) or None if not found
        """
        sql_query = f"SELECT enrollment_status FROM ezkey_enrollment WHERE enrollment_id = {enrollment_id};"
        return self.execute_query_single_value(sql_query)

    def get_enrollment_proof_token_hash(self, enrollment_id: int) -> Optional[str]:
        """
        Get enrollment proof token hash from database.

        Args:
            enrollment_id: Enrollment ID to check

        Returns:
            Enrollment proof token hash (SHA-256 hex) or None if not found
        """
        sql_query = (
            f"SELECT enrollment_proof_token_hash FROM ezkey_enrollment "
            f"WHERE enrollment_id = {enrollment_id};"
        )
        return self.execute_query_single_value(sql_query)

    def find_integration_id_by_name(self, integration_name: str) -> Optional[int]:
        """
        Find integration ID by name (i18n).

        Note: This is a simple lookup. For more complex queries, use execute_query directly.

        Args:
            integration_name: Integration name to find

        Returns:
            Integration ID, or None if not found
        """
        # SQL injection protection
        safe_name = integration_name.replace("'", "''")
        sql_query = (
            f"SELECT i.integration_id FROM ezkey_integration i "
            f"JOIN ezkey_integration_i18n i18n ON i.integration_id = i18n.integration_id "
            f"WHERE i18n.integration_i18n_name = '{safe_name}' LIMIT 1;"
        )
        result = self.execute_query_single_value(sql_query)
        return int(result) if result else None

    def delete_integration(self, integration_id: int) -> bool:
        """
        Delete integration and all related data (cascade).

        Warning: This deletes all related enrollments, API keys, etc. Use with caution.

        Args:
            integration_id: Integration ID to delete

        Returns:
            True if successful, False otherwise
        """
        sql_delete = f"DELETE FROM ezkey_integration WHERE integration_id = {integration_id};"
        return self.execute_update(sql_delete)

    def get_integration_active_status(self, integration_id: int) -> Optional[str]:
        """
        Get integration active status from database.

        Args:
            integration_id: Integration ID to check

        Returns:
            Active status ('true' or 'false') or None if not found
        """
        sql_query = f"SELECT active FROM ezkey_integration WHERE integration_id = {integration_id};"
        return self.execute_query_single_value(sql_query)

