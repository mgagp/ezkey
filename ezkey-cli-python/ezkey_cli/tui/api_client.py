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

  def _post(self, endpoint: str, data: Dict[str, Any]) -> Optional[Dict[str, Any]]:
    """
    Make POST request to API.

    Args:
        endpoint: API endpoint (e.g., /api/v1/integrations)
        data: JSON payload

    Returns:
        Response JSON or None on error

    Raises:
        Exception: With API error message if request fails
    """
    try:
      url = f"{self.admin_url}{endpoint}"
      log.info(f"POST {url}")
      log.debug(f"Payload: {data}")

      response = requests.post(
          url,
          headers=self.headers,
          json=data,
          timeout=10,
          verify=self.verify_ssl
      )

      log.info(f"Response status: {response.status_code}")

      if response.status_code == 401 or response.status_code == 403:
        log.error(f"Authorization failed ({response.status_code}): {response.text}")
        raise Exception(f"Authorization failed ({response.status_code})")

      # Handle 4xx errors with API error response
      if 400 <= response.status_code < 500:
        try:
          error_data = response.json()
          error_msg = error_data.get("message", response.text)
          log.error(f"API error ({response.status_code}): {error_msg}")
          raise Exception(error_msg)
        except ValueError:
          # Not JSON, use raw text
          log.error(f"API error ({response.status_code}): {response.text}")
          raise Exception(f"API error: {response.text}")

      response.raise_for_status()
      result = response.json() if response.text else {}
      log.debug(f"Response data: {result}")
      return result

    except requests.exceptions.RequestException as e:
      log.error(f"POST {endpoint} failed: {e}", exc_info=True)
      raise Exception(f"Request failed: {str(e)}")
    except Exception as e:
      # Re-raise exception (already logged above)
      if "Authorization failed" in str(e) or "API error" in str(e) or "Request failed" in str(e):
        raise
      log.error(f"POST {endpoint} unexpected error: {e}", exc_info=True)
      raise Exception(f"Unexpected error: {str(e)}")

  def _delete(self, endpoint: str) -> bool:
    """
    Make DELETE request to API.

    Args:
        endpoint: API endpoint (e.g., /api/v1/integrations/123)

    Returns:
        True if successful, False otherwise
    """
    try:
      url = f"{self.admin_url}{endpoint}"
      log.info(f"DELETE {url}")

      response = requests.delete(
          url,
          headers=self.headers,
          timeout=10,
          verify=self.verify_ssl
      )

      log.info(f"Response status: {response.status_code}")

      if response.status_code == 401 or response.status_code == 403:
        log.error(f"Authorization failed ({response.status_code}): {response.text}")
        return False

      if response.status_code in [200, 204]:
        log.debug("Delete successful")
        return True

      response.raise_for_status()
      return True

    except Exception as e:
      log.error(f"DELETE {endpoint} failed: {e}", exc_info=True)
      return False

  def logout(self) -> bool:
    """Logout and invalidate the current bearer token."""
    try:
      url = f"{self.admin_url}/api/v1/admin/auth/logout"
      log.info(f"POST {url}")

      response = requests.post(
          url,
          headers=self.headers,
          timeout=10,
          verify=self.verify_ssl
      )

      log.info(f"Logout response status: {response.status_code}")
      return response.status_code == 200

    except Exception as e:
      log.error(f"Logout failed: {e}", exc_info=True)
      return False

  def get_integrations(self, page: int = 0, size: int = 100, name: str = None, active: bool = None) -> Optional[Dict[str, Any]]:
    """
    Get list of integrations with optional filters.

    Args:
        page: Page number (0-based)
        size: Page size
        name: Filter by name (partial match)
        active: Filter by active status

    Returns:
        Response with 'content' array and 'page' metadata
    """
    params = {"page": page, "size": size}
    if name:
      params["integrationName"] = name
    if active is not None:
      params["active"] = active

    return self._get("/api/v1/integrations", params=params)

  def get_enrollments(
      self,
      page: int = 0,
      size: int = 100,
      enrollment_name: str = None,
      status: str = None,
      integration_id: int = None,
      active: bool = None,
      created_after: Optional[datetime] = None,
      created_before: Optional[datetime] = None,
      sort: str = None
  ) -> Optional[Dict[str, Any]]:
    """
    Get list of enrollments with optional filters.

    Returns:
        Response with 'content' array and 'page' metadata
    """
    params = {"page": page, "size": size}

    if enrollment_name:
      params["enrollmentName"] = enrollment_name
    if status:
      params["status"] = status
    if integration_id is not None:
      params["integrationId"] = integration_id
    if active is not None:
      params["active"] = active
    if created_after is not None:
      params["createdAfter"] = created_after.isoformat()
    if created_before is not None:
      params["createdBefore"] = created_before.isoformat()
    if sort:
      params["sort"] = sort

    return self._get("/api/v1/enrollments", params=params)

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
        totalElements value or content length or 0
    """
    if not response or not isinstance(response, dict):
      return 0

    # Try top-level totalElements
    total = response.get("totalElements")
    if isinstance(total, int):
      return total

    # Try within page object
    page = response.get("page")
    if isinstance(page, dict):
      total = page.get("totalElements")
      if isinstance(total, int):
        return total

    # Fallback: count content items if totalElements not available
    content = response.get("content", [])
    if isinstance(content, list):
      return len(content)

    return 0

  def validate_token(self) -> bool:
    """
    Validate if bearer token is still valid.

    Calls /api/v1/integrations?page=0&size=1 with current token.
    This is a read-only endpoint that requires authentication.

    Returns:
        True if token is valid, False if expired/invalid
    """
    try:
      url = f"{self.admin_url}/api/v1/integrations?page=0&size=1"
      log.debug(f"Validating token via GET {url}")

      response = requests.get(
          url,
          headers=self.headers,
          timeout=5,
          verify=self.verify_ssl
      )

      log.info(f"Token validation response status: {response.status_code}")

      if response.status_code == 200:
        log.debug("Token validation successful - token is valid")
        return True
      elif response.status_code in [401, 403]:
        log.warning(f"Token validation failed ({response.status_code}) - token expired or invalid")
        return False
      else:
        log.error(f"Token validation error: HTTP {response.status_code}")
        return False

    except Exception as e:
      log.error(f"Token validation exception: {e}", exc_info=True)
      return False
