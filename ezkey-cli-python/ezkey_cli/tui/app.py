"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

TUI Module: Textual App Entry Point
Description: Main Textual application for admin console
"""

from textual.app import App, ComposeResult
from textual.widgets import Header, Footer, Static
from textual.containers import Container, Vertical
from textual.screen import Screen

from textual import work
import logging
from pathlib import Path
from typing import Optional

from ..config import ConfigManager
from .screens import AuthScreen, HomeScreen, IntegrationsScreen, EnrollmentsScreen, AuditLogsScreen, AuthAttemptsScreen, AdminProvisioningScreen, ApiKeysScreen
from .api_client import ApiClient

log = logging.getLogger(__name__)


class EzkeyAdminApp:
  """Main Textual application for Ezkey admin console."""

  def __init__(self, config: ConfigManager):
    """
    Initialize admin app.

    Args:
        config: Configuration manager instance
    """
    self.config = config

  def run(self) -> bool:
    """
    Run the admin TUI.

    Returns:
        True if app completed successfully
    """
    from textual.app import App

    # Check for existing bearer token
    token = self._load_bearer_token()

    if token:
      # Token exists, start with token
      self._start_app_with_token(token)
    else:
      # No token, start app and show login screen
      self._start_app_with_token("")

  def _load_bearer_token(self) -> Optional[str]:
    """
    Load bearer token from shared CLI config, with legacy migration.

    Returns:
        Bearer token string, or None if not found
    """
    token = self.config.get('bearerToken')
    if token:
      return token

    legacy_path = Path.home() / ".ezkey" / "admin" / "bearer-token"
    if not legacy_path.exists():
      return None

    try:
      legacy_token = legacy_path.read_text(encoding="utf-8").strip()
      if not legacy_token:
        return None

      self.config.set_bearer_token(legacy_token)

      local_config_path = Path.cwd() / "ezkey.json"
      if local_config_path.exists():
        self.config.save(global_config=False)
        log.info("Migrated bearer token to local config (./ezkey.json)")
      else:
        self.config.save(global_config=True)
        log.info("Migrated bearer token to global config (~/.ezkey/ezkey.json)")

      try:
        legacy_path.unlink()
        log.info("Removed legacy bearer-token file")
      except Exception as e:
        log.warning("Failed to remove legacy bearer-token file: %s", e)

      return legacy_token
    except Exception as e:
      log.error("Failed to migrate legacy bearer-token: %s", e)
      return None

  def _start_app_with_token(self, token: str) -> None:
    """
    Start the main Textual app with a valid bearer token.

    Args:
        token: Valid bearer token
    """
    from textual.app import App

    # Get admin URL from config
    admin_url = self.config.get('adminUrl', 'http://localhost:9080')

    # Create and run Textual app
    app = EzkeyAdminTUI(self.config, token, admin_url)
    app.run()


class EzkeyAdminTUI(App):
  """Textual UI application."""

  SCREENS = {
      "home": HomeScreen,
      "integrations": IntegrationsScreen,
      "enrollments": EnrollmentsScreen,
      "audit_logs": AuditLogsScreen,
      "auth_attempts": AuthAttemptsScreen,
      "admins": AdminProvisioningScreen,
      "api_keys": ApiKeysScreen
  }

  def __init__(self, config: ConfigManager, token: str, admin_url: str):
    """
    Initialize Textual app.

    Args:
        config: Configuration manager
        token: Bearer token for API requests
        admin_url: Admin API base URL
    """
    super().__init__()
    self.config = config
    self.bearer_token = token
    self.admin_url = admin_url

    # Detect dev mode (localhost/docker) - disable SSL verification
    is_dev = any(x in admin_url.lower() for x in ['localhost', '127.0.0.1', 'docker'])

    # Create API client
    self.api_client = ApiClient(admin_url, token, verify_ssl=not is_dev)

  def on_mount(self) -> None:
    """Called when app is mounted - Smart Startup flow.

    Strategy:
    1. Check token expiration via expiresAt from API
    2. If expired + username saved -> QuickReAuthScreen (1-click)
    3. If expired + no username -> ReAuthScreen (full)
    4. If valid -> Dashboard (0-click)
    """
    if not self.bearer_token:
      log.info("No token found - showing login screen")
      from .screens import ReAuthScreen
      self.push_screen(ReAuthScreen(
          self.config,
          self.admin_url,
          self.api_client,
          mode="login"
      ))
      return

    if self._is_token_expired():
      log.warning("Token is expired")
      username = self.config.get_admin_username()

      if username:
        log.info("Showing quick re-auth for user: %s", username)
        from .screens import QuickReAuthScreen
        self.push_screen(QuickReAuthScreen(self.config, self.admin_url, self.api_client))
      else:
        log.info("Showing full re-auth (no username stored)")
        from .screens import ReAuthScreen
        self.push_screen(ReAuthScreen(self.config, self.admin_url, self.api_client))
      return

    log.debug("Token valid - showing home screen")
    self.push_screen("home")

  def logout_and_exit(self) -> None:
    """Logout, clear local auth data, and exit."""
    try:
      if self.api_client:
        self.api_client.logout()
    finally:
      self.config.clear_bearer_token()
      self.config.clear_recovery_token()
      self.config.clear_token_expires_at()
      self.config.clear_admin_type()
      self.config.clear_last_auth_time()
      local_config_path = Path.cwd() / "ezkey.json"
      if local_config_path.exists():
        self.config.save(global_config=False)
      self.config.save(global_config=True)
      self.exit()

  def handle_auth_error(self) -> None:
    """Handle authorization failures by prompting re-auth."""
    log.warning("Authorization failed - prompting re-auth")
    from .screens import ReAuthScreen
    self.push_screen(ReAuthScreen(self.config, self.admin_url, self.api_client))

  def _is_token_expired(self) -> bool:
    """Check if token has expired based on tokenExpiresAt from API."""
    expires_at = self.config.get_token_expires_at()
    if not expires_at:
      log.warning("No token expiration stored - treating as valid")
      return False

    try:
      from datetime import datetime, timezone
      expiry_time = datetime.fromisoformat(expires_at.replace('Z', '+00:00'))
      is_expired = datetime.now(timezone.utc) >= expiry_time
      log.debug("Token expiration check: %s, expired=%s", expires_at, is_expired)
      return is_expired
    except (ValueError, TypeError) as e:
      log.error("Could not parse tokenExpiresAt '%s': %s", expires_at, e)
      return False


def start_tui(config: ConfigManager = None) -> None:
  """
  Entry point for TUI mode.

  Args:
      config: Optional configuration manager (uses default if None)
  """
  if config is None:
    config = ConfigManager()

  app = EzkeyAdminApp(config)
  app.run()
