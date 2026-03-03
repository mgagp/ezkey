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
import time

log = logging.getLogger(__name__)


class ApiClient:
  """HTTP client for Ezkey Admin API with bearer token auth."""

  def __init__(
      self,
      admin_url: str,
      bearer_token: str,
      verify_ssl: bool = True,
      admin_health_url: Optional[str] = None
  ):
    """
    Initialize API client.

    Args:
        admin_url: Base URL for Admin API (e.g., http://localhost:9080)
        bearer_token: Bearer token for authentication
        verify_ssl: Whether to verify SSL certificates
        admin_health_url: Base URL for Admin API Actuator (e.g., http://localhost:9081)
    """
    self.admin_url = admin_url.rstrip('/')
    self.admin_health_url = admin_health_url.rstrip('/') if admin_health_url else None
    self.bearer_token = bearer_token
    self.verify_ssl = verify_ssl
    self.headers = {
        "Authorization": f"Bearer {bearer_token}",
        "Content-Type": "application/json"
    }
    self.last_auth_error = False
    self.last_status_code = None
    self.last_error_message = None

  def _extract_error_message(self, response: requests.Response) -> str:
    """
    Extract error message from HTTP response.

    Supports multiple formats:
    - RFC 7807 Problem Details (detail, title fields)
    - Legacy API error (message field)
    - Plain text or generic message

    Args:
        response: HTTP response object

    Returns:
        Human-readable error message
    """
    try:
      data = response.json()
      # Try RFC 7807 'detail' field first (most specific)
      if "detail" in data and data["detail"]:
        return str(data["detail"])
      # Try 'title' field as fallback
      if "title" in data and data["title"]:
        return str(data["title"])
      # Try legacy 'message' field
      if "message" in data and data["message"]:
        return str(data["message"])
      # If JSON but no recognized fields, return full response
      return str(data)
    except (ValueError, TypeError):
      # Not JSON, use raw text
      pass

    # Return status text or generic message
    if response.text:
      return response.text.strip()
    return f"HTTP {response.status_code}"

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
      self.last_auth_error = False
      self.last_status_code = None
      self.last_error_message = None
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
        self.last_auth_error = True
        self.last_status_code = response.status_code
        self.last_error_message = response.text
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
    """
    try:
      self.last_auth_error = False
      self.last_status_code = None
      self.last_error_message = None
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

      # Handle authentication/authorization errors
      if response.status_code == 401 or response.status_code == 403:
        error_msg = self._extract_error_message(response)
        log.error(f"Authorization failed ({response.status_code}): {error_msg}")
        self.last_auth_error = True
        self.last_status_code = response.status_code
        self.last_error_message = error_msg
        return None

      # Handle other 4xx/5xx errors (return None, store error message)
      if response.status_code >= 400:
        error_msg = self._extract_error_message(response)
        log.error(f"API error ({response.status_code}): {error_msg}")
        self.last_status_code = response.status_code
        self.last_error_message = error_msg
        return None

      # Success case
      result = response.json() if response.text else {}
      log.debug(f"Response data: {result}")
      return result

    except requests.exceptions.RequestException as e:
      error_msg = f"Request failed: {str(e)}"
      log.error(f"POST {endpoint} failed: {e}", exc_info=True)
      self.last_status_code = None
      self.last_error_message = error_msg
      return None
    except Exception as e:
      error_msg = f"Unexpected error: {str(e)}"
      log.error(f"POST {endpoint} unexpected error: {e}", exc_info=True)
      self.last_status_code = None
      self.last_error_message = error_msg
      return None

  def _delete(self, endpoint: str) -> bool:
    """
    Make DELETE request to API.

    Args:
        endpoint: API endpoint (e.g., /api/v1/integrations/123)

    Returns:
        True if successful, False otherwise
    """
    try:
      self.last_auth_error = False
      self.last_status_code = None
      self.last_error_message = None
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
        self.last_auth_error = True
        self.last_status_code = response.status_code
        self.last_error_message = response.text
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

  def get_integrations(
      self,
      page: int = 0,
      size: int = 100,
      name: str = None,
      active: bool = None,
      tenant_id: Optional[int] = None
  ) -> Optional[Dict[str, Any]]:
    """
    Get list of integrations with optional filters.

    Args:
        page: Page number (0-based)
        size: Page size
        name: Filter by name (partial match)
        active: Filter by active status
        tenant_id: Filter by tenant ID (GlobalAdmin only; ignored for TenantAdmin)

    Returns:
        Response with 'content' array and 'page' metadata
    """
    params = {"page": page, "size": size}
    if name:
      params["integrationName"] = name
    if active is not None:
      params["active"] = active
    if tenant_id is not None:
      params["tenantId"] = tenant_id

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

  def get_auth_attempts_list(
      self,
      page: int = 0,
      size: int = 20,
      status: str = None,
      enrollment_id: int = None,
      integration_id: int = None,
      created_after: str = None,
      created_before: str = None,
      sort: str = None
  ) -> Optional[Dict[str, Any]]:
    """
    Get auth attempts with pagination and filters.

    Returns:
        Response with 'content' array and 'page' metadata
    """
    params = {"page": page, "size": size}
    if status:
      params["status"] = status
    if enrollment_id is not None:
      params["enrollmentId"] = enrollment_id
    if integration_id is not None:
      params["integrationId"] = integration_id
    if created_after:
      params["createdAfter"] = created_after
    if created_before:
      params["createdBefore"] = created_before
    if sort:
      params["sort"] = sort

    return self._get("/api/v1/auth-attempts", params=params)

  def get_auth_attempt_by_id(self, auth_attempt_id: int) -> Optional[Dict[str, Any]]:
    """Get auth attempt by ID."""
    return self._get(f"/api/v1/auth-attempts/{auth_attempt_id}")

  def cancel_auth_attempt(self, auth_attempt_id: int) -> Optional[Dict[str, Any]]:
    """Cancel auth attempt by ID."""
    try:
      return self._post(f"/api/v1/auth-attempts/{auth_attempt_id}/cancel", data={})
    except Exception as e:
      log.error(f"Cancel auth attempt failed: {e}")
      return None

  def get_admins(
      self,
      page: int = 0,
      size: int = 20,
      sort: str = None
  ) -> Optional[Dict[str, Any]]:
    """Get administrators list with pagination and sorting."""
    params = {"page": page, "size": size}
    if sort:
      params["sort"] = sort
    return self._get("/api/v1/admins", params=params)

  def get_admin_onboarding(self, admin_id: int) -> Optional[Dict[str, Any]]:
    """Get onboarding credentials for admin."""
    return self._get(f"/api/v1/admins/{admin_id}/onboarding")

  def deactivate_admin(self, admin_id: int) -> bool:
    """Deactivate an administrator by ID."""
    response = self._post(f"/api/v1/admins/{admin_id}/deactivate", data={})
    return response is not None

  def get_tenants(self) -> Optional[List[Dict[str, Any]]]:
    """List tenants (GlobalAdmin only)."""
    return self._get("/api/v1/tenants")

  def get_tenant(self, tenant_id: int) -> Optional[Dict[str, Any]]:
    """Get tenant by ID."""
    return self._get(f"/api/v1/tenants/{tenant_id}")

  def create_tenant(self, payload: Dict[str, Any]) -> Optional[Dict[str, Any]]:
    """Create a tenant."""
    return self._post("/api/v1/tenants", data=payload)

  def deactivate_tenant(self, tenant_id: int) -> bool:
    """Deactivate a tenant by ID."""
    response = self._post(f"/api/v1/tenants/{tenant_id}/deactivate", data={})
    return response is not None

  def get_api_keys(self) -> Optional[List[Dict[str, Any]]]:
    """List all API keys visible to the admin."""
    return self._get("/api/v1/api-keys")

  def get_api_keys_for_integration(self, integration_id: int) -> Optional[List[Dict[str, Any]]]:
    """List API keys for a specific integration."""
    return self._get(f"/api/v1/api-keys/integration/{integration_id}")

  def get_api_key(self, key_id: int) -> Optional[Dict[str, Any]]:
    """Get API key by ID."""
    return self._get(f"/api/v1/api-keys/{key_id}")

  def create_api_key(self, payload: Dict[str, Any]) -> Optional[Dict[str, Any]]:
    """Create API key."""
    return self._post("/api/v1/api-keys", data=payload)

  def revoke_api_key(self, key_id: int) -> bool:
    """Revoke API key by ID."""
    return self._delete(f"/api/v1/api-keys/{key_id}")

  def get_encryption_keys(self) -> Optional[List[Dict[str, Any]]]:
    """List all encryption keys."""
    return self._get("/api/v1/encryption-keys")

  def get_primary_encryption_key(self) -> Optional[Dict[str, Any]]:
    """Get current primary encryption key."""
    return self._get("/api/v1/encryption-keys/primary")

  def get_encryption_key(self, key_id: int) -> Optional[Dict[str, Any]]:
    """Get encryption key by ID."""
    return self._get(f"/api/v1/encryption-keys/{key_id}")

  def rotate_encryption_key(self) -> Optional[Dict[str, Any]]:
    """Trigger manual encryption key rotation."""
    return self._post("/api/v1/encryption-keys/rotate", data={})

  def get_reencryption_batches(self) -> Optional[List[Dict[str, Any]]]:
    """List all re-encryption batches."""
    return self._get("/api/v1/encryption-keys/reencryption-batches")

  def resume_reencryption_batch(self, batch_id: int) -> Optional[Dict[str, Any]]:
    """Resume a failed or paused re-encryption batch."""
    return self._post(
        f"/api/v1/encryption-keys/reencryption-batches/{batch_id}/resume",
        data={}
    )

  def trigger_full_reencryption(self) -> Optional[Dict[str, Any]]:
    """Trigger full re-encryption process."""
    return self._post("/api/v1/encryption-keys/reencrypt/trigger", data={})

  def trigger_reencryption_for_key(self, key_id: int) -> Optional[Dict[str, Any]]:
    """Trigger re-encryption for a specific key."""
    return self._post(f"/api/v1/encryption-keys/{key_id}/reencrypt", data={})

  def create_reencryption_batches(self) -> Optional[Dict[str, Any]]:
    """Create re-encryption batches without processing."""
    return self._post("/api/v1/encryption-keys/reencrypt/create-batches", data={})

  def get_audit_logs(
      self,
      page: int = 0,
      size: int = 20,
      event_type: str = None,
      event_status: str = None,
      api_name: str = None,
      enrollment_id: int = None,
      admin_id: int = None,
      sort: str = None
  ) -> Optional[Dict[str, Any]]:
    """
    Get audit logs with pagination and filters.

    Returns:
        Response with 'content' array and 'page' metadata
    """
    params = {"page": page, "size": size}
    if event_type:
      params["eventType"] = event_type
    if event_status:
      params["eventStatus"] = event_status
    if api_name:
      params["apiName"] = api_name
    if enrollment_id is not None:
      params["enrollmentId"] = enrollment_id
    if admin_id is not None:
      params["adminId"] = admin_id
    if sort:
      params["sort"] = sort

    return self._get("/api/v1/audit-logs", params=params)

  def get_api_keys(self) -> Optional[List[Dict[str, Any]]]:
    """Get all API keys visible to the current admin."""
    return self._get("/api/v1/api-keys")

  def get_health(self) -> Dict[str, Any]:
    """Check Admin API health and return status with latency."""
    base_url = self.admin_health_url or self.admin_url
    url = f"{base_url}/actuator/health"
    start = time.perf_counter()
    try:
      response = requests.get(
          url,
          headers=self.headers,
          timeout=5,
          verify=self.verify_ssl
      )
      latency_ms = int((time.perf_counter() - start) * 1000)
      ok = response.status_code == 200
      data = response.json() if response.text else {}
      return {
          "ok": ok,
          "status_code": response.status_code,
          "latency_ms": latency_ms,
          "data": data
      }
    except Exception as e:
      latency_ms = int((time.perf_counter() - start) * 1000)
      log.warning(f"Health check failed: {e}")
      return {
          "ok": False,
          "status_code": None,
          "latency_ms": latency_ms,
          "data": {}
      }

  def get_dashboard_stats(self) -> Optional[Dict[str, int]]:
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
    if self.last_auth_error:
      return None
    if integrations_resp:
      stats["integrations"] = self._extract_total_elements(integrations_resp)

    # Get enrollments count
    enrollments_resp = self.get_enrollments(size=1)
    if self.last_auth_error:
      return None
    if enrollments_resp:
      stats["enrollments"] = self._extract_total_elements(enrollments_resp)

    # Get auth attempts (24h)
    auth_attempts_resp = self.get_auth_attempts(size=1000, hours=24)
    if self.last_auth_error:
      return None
    if auth_attempts_resp:
      total = self._extract_total_elements(auth_attempts_resp)
      stats["auth_attempts_24h"] = total

      # Count failed attempts
      content = auth_attempts_resp.get("content", [])
      failed = sum(1 for attempt in content if attempt.get("status") in ["REJECTED", "EXPIRED", "FAILED"])
      stats["auth_failed_24h"] = failed

    return stats

  def get_dashboard_snapshot(self) -> Optional[Dict[str, Any]]:
    """
    Get dashboard snapshot data for the home screen.

    Returns:
        Dict with keys: status, actions, activity, security, meta
    """
    now = datetime.now(timezone.utc)
    created_after_24h = (now - timedelta(hours=24)).isoformat()
    created_before_5m = (now - timedelta(minutes=5)).isoformat()
    created_before_24h = now - timedelta(hours=24)

    status = {
        "integrations_total": 0,
        "integrations_active": 0,
        "integrations_inactive": 0,
        "enrollments_total": 0,
        "enrollments_created": 0,
        "enrollments_bound": 0,
        "enrollments_verified": 0,
        "enrollments_invalid": 0,
        "auth_total_24h": 0,
        "auth_accepted_24h": 0,
        "auth_failed_24h": 0,
        "auth_pending_24h": 0
    }

    actions = {
        "pending_over_5m": 0,
        "enrollments_created_over_24h": 0,
        "api_keys_expiring_30d": 0
    }

    activity = []
    security = {
        "login_failures_24h": 0,
        "recoveries_7d": 0,
        "keys_revoked_7d": 0
    }

    integrations_resp = self.get_integrations(size=1)
    if self.last_auth_error:
      return None
    if integrations_resp:
      status["integrations_total"] = self._extract_total_elements(integrations_resp)

    integrations_active_resp = self.get_integrations(size=1, active=True)
    if self.last_auth_error:
      return None
    if integrations_active_resp:
      status["integrations_active"] = self._extract_total_elements(integrations_active_resp)

    integrations_inactive_resp = self.get_integrations(size=1, active=False)
    if self.last_auth_error:
      return None
    if integrations_inactive_resp:
      status["integrations_inactive"] = self._extract_total_elements(integrations_inactive_resp)

    enrollments_resp = self.get_enrollments(size=1)
    if self.last_auth_error:
      return None
    if enrollments_resp:
      status["enrollments_total"] = self._extract_total_elements(enrollments_resp)

    for key, value in (
        ("enrollments_created", "CREATED"),
        ("enrollments_bound", "BOUND"),
        ("enrollments_verified", "VERIFIED"),
        ("enrollments_invalid", "INVALID")
    ):
      resp = self.get_enrollments(size=1, status=value)
      if self.last_auth_error:
        return None
      if resp:
        status[key] = self._extract_total_elements(resp)

    auth_total_resp = self.get_auth_attempts_list(size=1, created_after=created_after_24h)
    if self.last_auth_error:
      return None
    if auth_total_resp:
      status["auth_total_24h"] = self._extract_total_elements(auth_total_resp)

    auth_accepted_resp = self.get_auth_attempts_list(
        size=1,
        status="ACCEPTED",
        created_after=created_after_24h
    )
    if self.last_auth_error:
      return None
    if auth_accepted_resp:
      status["auth_accepted_24h"] = self._extract_total_elements(auth_accepted_resp)

    auth_pending_resp = self.get_auth_attempts_list(
        size=1,
        status="PENDING",
        created_after=created_after_24h
    )
    if self.last_auth_error:
      return None
    if auth_pending_resp:
      status["auth_pending_24h"] = self._extract_total_elements(auth_pending_resp)

    auth_rejected_resp = self.get_auth_attempts_list(
        size=1,
        status="REJECTED",
        created_after=created_after_24h
    )
    if self.last_auth_error:
      return None
    auth_invalid_resp = self.get_auth_attempts_list(
        size=1,
        status="INVALID",
        created_after=created_after_24h
    )
    if self.last_auth_error:
      return None
    auth_expired_resp = self.get_auth_attempts_list(
        size=1,
        status="EXPIRED",
        created_after=created_after_24h
    )
    if self.last_auth_error:
      return None

    status["auth_failed_24h"] = (
        self._extract_total_elements(auth_rejected_resp)
        + self._extract_total_elements(auth_invalid_resp)
        + self._extract_total_elements(auth_expired_resp)
    )

    pending_over_5m_resp = self.get_auth_attempts_list(
        size=1,
        status="PENDING",
        created_after=created_after_24h,
        created_before=created_before_5m
    )
    if self.last_auth_error:
      return None
    if pending_over_5m_resp:
      actions["pending_over_5m"] = self._extract_total_elements(pending_over_5m_resp)

    enrollments_old_resp = self.get_enrollments(
      size=1,
      status="CREATED",
      created_before=created_before_24h
    )
    if self.last_auth_error:
      return None
    if enrollments_old_resp:
      actions["enrollments_created_over_24h"] = self._extract_total_elements(enrollments_old_resp)

    api_keys_resp = self.get_api_keys()
    if self.last_auth_error:
      return None
    if isinstance(api_keys_resp, list):
      actions["api_keys_expiring_30d"] = self._count_api_keys_expiring(api_keys_resp, now, 30)

    audit_logs_resp = self.get_audit_logs(page=0, size=50, sort="createdAt,desc")
    if self.last_auth_error:
      return None
    if audit_logs_resp:
      audit_entries = audit_logs_resp.get("content", [])
      activity = self._format_audit_activity(audit_entries, limit=10)
      security = self._compute_security_snapshot(audit_entries, now)

    health = self.get_health()

    return {
      "status": status,
      "actions": actions,
      "activity": activity,
      "security": security,
      "meta": {
        "last_refresh": now.isoformat(),
        "health": health
      }
    }

  def _parse_datetime(self, value: Optional[str]) -> Optional[datetime]:
    """Parse ISO-8601 datetime with optional Z suffix."""
    if not value or not isinstance(value, str):
      return None
    try:
      if value.endswith("Z"):
        value = value.replace("Z", "+00:00")
      return datetime.fromisoformat(value)
    except Exception:
      return None

  def _count_api_keys_expiring(
      self,
      api_keys: List[Dict[str, Any]],
      now: datetime,
      days: int
  ) -> int:
    """Count active API keys expiring within the next N days."""
    threshold = now + timedelta(days=days)
    count = 0
    for item in api_keys:
      if not isinstance(item, dict):
        continue
      if item.get("active") is False:
        continue
      if item.get("revokedAt"):
        continue
      expires_at = self._parse_datetime(item.get("expiresAt"))
      if expires_at and now <= expires_at <= threshold:
        count += 1
    return count

  def _format_audit_activity(
      self,
      entries: List[Dict[str, Any]],
      limit: int = 10
  ) -> List[str]:
    """Format recent audit entries for the activity panel."""
    activity = []
    for entry in entries[:limit]:
      created_at = self._parse_datetime(entry.get("createdAt"))
      time_str = created_at.strftime("%m-%d %H:%M") if created_at else "--:--"
      event_type = entry.get("eventType", "") or "UNKNOWN"
      event_status = entry.get("eventStatus", "") or "-"
      admin_id = entry.get("adminId")
      integration_id = entry.get("integrationId")
      context_parts = []
      if admin_id is not None:
        context_parts.append(f"admin:{admin_id}")
      if integration_id is not None:
        context_parts.append(f"integ:{integration_id}")
      context = f" {' '.join(context_parts)}" if context_parts else ""
      activity.append(f"{time_str} {event_type} {event_status}{context}")
    if not activity:
      return ["No recent activity"]
    return activity

  def _compute_security_snapshot(
      self,
      entries: List[Dict[str, Any]],
      now: datetime
  ) -> Dict[str, int]:
    """Compute security snapshot metrics from audit entries."""
    snapshot = {
        "login_failures_24h": 0,
        "recoveries_7d": 0,
        "keys_revoked_7d": 0
    }

    since_24h = now - timedelta(hours=24)
    since_7d = now - timedelta(days=7)

    for entry in entries:
      created_at = self._parse_datetime(entry.get("createdAt"))
      if not created_at:
        continue
      event_type = (entry.get("eventType") or "").upper()

      if created_at >= since_24h and "LOGIN_FAILURE" in event_type:
        snapshot["login_failures_24h"] += 1

      if created_at >= since_7d and "RECOVER" in event_type:
        snapshot["recoveries_7d"] += 1

      if created_at >= since_7d and "API_KEY" in event_type and (
          "REVOKE" in event_type or "REVOKED" in event_type
      ):
        snapshot["keys_revoked_7d"] += 1

    return snapshot

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

  def create_global_admin(self, payload: Dict[str, Any]) -> Optional[Dict[str, Any]]:
    """
    Create a global administrator (peer admin).

    Only GlobalAdmin can create other GlobalAdmins.

    Args:
        payload: Dict with keys:
            - username (required): Unique username (3-50 chars)
            - email (required): Email address for SOC 2 compliance
            - firstName (required): First name
            - lastName (required): Last name

    Returns:
        Response with adminId, enrollmentId, and onboarding credentials
        or None on error

    Raises:
        Exception: With API error message if creation fails
    """
    return self._post("/api/v1/admins/global", data=payload)

  def create_tenant_admin(self, payload: Dict[str, Any]) -> Optional[Dict[str, Any]]:
    """
    Create a tenant administrator (peer admin).

    GlobalAdmin can create TenantAdmins for any tenant.
    TenantAdmin can create TenantAdmins for their own tenant only.

    Args:
        payload: Dict with keys:
            - username (required): Unique username (3-50 chars)
            - email (optional): Email address (recommended)
            - firstName (optional): First name
            - lastName (optional): Last name
            - tenantId (required): Tenant ID (not System Tenant)

    Returns:
        Response with adminId, enrollmentId, and admin info
        or None on error

    Raises:
        Exception: With API error message if creation fails
    """
    return self._post("/api/v1/admins/tenant", data=payload)

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
