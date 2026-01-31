"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

TUI Module: API Client
Description: HTTP client for Admin API with bearer token authentication
"""

import requests
import logging
from typing import Optional, Dict, Any, List
from datetime import datetime, timedelta, timezone

log = logging.getLogger(__name__)


class ApiClient:
  """HTTP client for Ezkey Admin API with bearer token auth."""

  def __init__(self, admin_url: str, bearer_token: str, verify_ssl: bool = True):
    """
    Initialize API client.

    Args:
        admin_url: Base URL for Admin API (e.g., http://localhost:9080)
        bearer_token: Bearer token for authentication
        verify_ssl: Whether to verify SSL certificates
    """
    self.admin_url = admin_url.rstrip('/')
    self.bearer_token = bearer_token
    self.verify_ssl = verify_ssl
    self.headers = {
        "Authorization": f"Bearer {bearer_token}",
        "Content-Type": "application/json"
    }

  def _get(self, endpoint: str, params: Optional[Dict] = None) -> Optional[Dict[str, Any]]:
    """
    Make GET request to API.

    Args:
        endpoint: API endpoint (e.g., /api/v1/integrations)
        params: Optional query parameters

    Returns:
        Response JSON or None on error
    """
    try:
      url = f"{self.admin_url}{endpoint}"
      log.info(f"GET {url}")

      # Log bearer token info (first and last 10 chars for security)
      if self.bearer_token:
        token_preview = (
            self.bearer_token[:10] + "..." + self.bearer_token[-10:]
            if len(self.bearer_token) > 20
            else self.bearer_token[:10] + "..."
        )
        log.debug(f"Bearer token: {token_preview} (length: {len(self.bearer_token)})")

      response = requests.get(
          url,
          headers=self.headers,
          params=params,
          timeout=10,
          verify=self.verify_ssl
      )

      log.info(f"Response status: {response.status_code}")

      if response.status_code == 404:
        log.warning(f"Endpoint not found: {endpoint}")
        return None

      if response.status_code == 401 or response.status_code == 403:
        log.error(f"Authorization failed ({response.status_code}): {response.text}")
        return None

      response.raise_for_status()
      data = response.json()
      log.debug(f"Response data: {data}")
      return data

    except Exception as e:
      log.error(f"GET {endpoint} failed: {e}", exc_info=True)
      return None

  def get_integrations(self, page: int = 0, size: int = 100) -> Optional[Dict[str, Any]]:
    """
    Get list of integrations.

    Returns:
        Response with 'content' array and 'totalElements'
    """
    return self._get("/api/v1/integrations", params={"page": page, "size": size})

  def get_enrollments(self, page: int = 0, size: int = 100) -> Optional[Dict[str, Any]]:
    """
    Get list of enrollments.

    Returns:
        Response with 'content' array and 'totalElements'
    """
    return self._get("/api/v1/enrollments", params={"page": page, "size": size})

  def get_auth_attempts(
      self,
      page: int = 0,
      size: int = 100,
      hours: Optional[int] = None
  ) -> Optional[Dict[str, Any]]:
    """
    Get auth attempts, optionally filtered by time.

    Args:
        page: Page number
        size: Page size
        hours: Optional filter for last N hours (e.g., 24)

    Returns:
        Response with 'content' array and 'totalElements'
    """
    params = {"page": page, "size": size}
    if hours is not None:
      created_after = datetime.now(timezone.utc) - timedelta(hours=hours)
      params["createdAfter"] = created_after.isoformat()

    return self._get("/api/v1/auth-attempts", params=params)

  def get_dashboard_stats(self) -> Dict[str, int]:
    """
    Get dashboard statistics (counts for all resources).

    Returns:
        Dict with keys: integrations, enrollments, auth_attempts_24h, auth_failed_24h
    """
    stats = {
        "integrations": 0,
        "enrollments": 0,
        "auth_attempts_24h": 0,
        "auth_failed_24h": 0
    }

    # Get integrations count
    integrations_resp = self.get_integrations(size=1)
    if integrations_resp:
      stats["integrations"] = self._extract_total_elements(integrations_resp)

    # Get enrollments count
    enrollments_resp = self.get_enrollments(size=1)
    if enrollments_resp:
      stats["enrollments"] = self._extract_total_elements(enrollments_resp)

    # Get auth attempts (24h)
    auth_attempts_resp = self.get_auth_attempts(size=1000, hours=24)
    if auth_attempts_resp:
      total = self._extract_total_elements(auth_attempts_resp)
      stats["auth_attempts_24h"] = total

      # Count failed attempts
      content = auth_attempts_resp.get("content", [])
      failed = sum(1 for attempt in content if attempt.get("status") in ["REJECTED", "EXPIRED", "FAILED"])
      stats["auth_failed_24h"] = failed

    return stats

  def _extract_total_elements(self, response: Dict[str, Any]) -> int:
    """
    Extract totalElements from paged responses.

    Args:
        response: API response dict

    Returns:
        totalElements value or 0
    """
    total = response.get("totalElements")
    if isinstance(total, int):
      return total

    page = response.get("page")
    if isinstance(page, dict):
      total = page.get("totalElements")
      if isinstance(total, int):
        return total

    return 0
