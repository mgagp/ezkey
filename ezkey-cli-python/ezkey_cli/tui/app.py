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
from ..auth.login_wizard import LoginWizard
from .screens import AuthScreen, HomeScreen
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
      # Token exists, start with home screen
      self._start_app_with_token(token)
    else:
      # No token, run login wizard
      self._run_login_wizard()

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

  def _run_login_wizard(self) -> None:
    """Run the interactive login wizard."""
    wizard = LoginWizard()
    success = wizard.run(config=self.config)

    if success:
      # Login successful, start app with token
      token = self.token_manager.load_token()
      if token:
        self._start_app_with_token(token)
    else:
      # Login failed
      import click
      click.echo()
      click.echo(click.style("Login cancelled.", fg="red"))

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

  SCREENS = {"home": HomeScreen}

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
    """Called when app is mounted."""
    self.push_screen("home")


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
