"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

Auth Module: Authentication Manager
Description: Handles passwordless authentication via Ezkey API
"""

from typing import Optional, Dict, Any
import requests
import logging

log = logging.getLogger(__name__)


class AuthManager:
  """
  Manages passwordless authentication via Ezkey Admin API.

  Delegates to the same API endpoints as the CLI (`ezkey admin auth login`).
  """

  def __init__(self, admin_url: str, verify_ssl: bool = True):
    """
    Initialize auth manager.

    Args:
        admin_url: Base URL for Admin API (e.g., https://localhost:9080)
        verify_ssl: Whether to verify SSL certificates (False for development)
    """
    self.admin_url = admin_url.rstrip('/')
    self.verify_ssl = verify_ssl

  def login(
      self,
      username: str,
      challenge: bool = False,
      timeout: int = 360
  ) -> Optional[Dict[str, Any]]:
    """
    Authenticate admin user using passwordless login.

    This is the main entry point - same as CLI: `ezkey admin auth login --username <username>`

    Args:
        username: Admin username
        challenge: If True, use two-step mode (returns challenge code, requires separate wait)
        timeout: Timeout in seconds (default 6 minutes for device approval window)

    Returns:
        Response dict with 'token' (on success) or 'authAttemptId'/'challengeCode' (on challenge),
        or None if failed
    """
    try:
      url = f"{self.admin_url}/api/v1/admin/auth/login"
      payload = {
          "username": username,
          "challengeRequested": challenge
      }

      response = requests.post(
          url,
          json=payload,
          timeout=timeout,
          verify=self.verify_ssl
      )

      # Return response data regardless of status (let caller handle errors)
      return response.json() if response.text else None

    except Exception as e:
      log.error("Login failed: %s", e)
      return None

  def wait_for_challenge(
      self,
      auth_attempt_id: int,
      challenge_code: Optional[int] = None,
      timeout: int = 360
  ) -> Optional[Dict[str, Any]]:
    """
    Wait for device to approve challenge (two-step auth).

    Used after login() returns challengeCode in two-step mode.

    Args:
        auth_attempt_id: Auth attempt ID from challenge response
        challenge_code: Challenge code (optional)
        timeout: Timeout in seconds

    Returns:
        Response dict with 'token' on success, or None if failed
    """
    try:
      url = f"{self.admin_url}/api/v1/admin/auth/passwordless-wait"
      params = {
          "authAttemptId": auth_attempt_id,
          "timeout": timeout
      }

      if challenge_code is not None:
        params["challengeCode"] = challenge_code

      response = requests.get(
          url,
          params=params,
          timeout=timeout + 5,
          verify=self.verify_ssl
      )

      return response.json() if response.text else None

    except Exception as e:
      log.error("Wait for challenge failed: %s", e)
      return None
  def logout(self) -> None:
    """Clear current session."""
    self.session_manager.clear_session()
    log.debug("User logged out")
