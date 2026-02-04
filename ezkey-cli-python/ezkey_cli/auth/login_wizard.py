"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

Auth Module: Login Wizard
Description: Interactive setup wizard for admin TUI authentication
"""

import click
from typing import Optional
import logging
import urllib3
from pathlib import Path

from .auth_manager import AuthManager

# Disable SSL warnings for development mode
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

log = logging.getLogger(__name__)


class LoginWizard:
  """
  Interactive wizard for admin TUI login.

  Uses same Ezkey passwordless auth as CLI. Simple flow:
  1. Get admin API URL (auto-detected if localhost)
  2. Get username
  3. Call /api/v1/admin/auth/login (same as CLI)
  4. Handle single-call or challenge mode
  5. Save bearer token
  """

  def __init__(self):
    """Initialize login wizard."""
    self.admin_url: Optional[str] = None
    self.username: Optional[str] = None
    self.is_dev_mode: bool = False

  def run(self, config = None) -> bool:
    """
    Run the full login wizard.

    Args:
        config: Optional ConfigManager to save admin_url

    Returns:
        True if authentication successful and token saved
    """
    from ..config import ConfigManager

    if config is None:
      config = ConfigManager()

    self.config = config
    click.echo()
    click.echo(click.style("╔═══════════════════════════════════════╗", fg="cyan"))
    click.echo(click.style("║  Ezkey Admin Console - Login         ║", fg="cyan"))
    click.echo(click.style("╚═══════════════════════════════════════╝", fg="cyan"))
    click.echo()

    # Step 1: Get admin URL
    if not self._get_admin_url():
      return False
  # Save admin_url to config for future use
    self.config.set('adminUrl', self.admin_url)
    self.config.save(global_config=True)


    # Step 2: Get username
    if not self._get_username():
      return False

    # Step 3: Authenticate (single-call or challenge)
    if not self._authenticate():
      return False

    click.echo()
    click.echo(click.style("✓ Login successful! Bearer token saved.", fg="green"))
    return True

  def _get_admin_url(self) -> bool:
    """
    Prompt for admin API URL.

    Returns:
        True if valid URL obtained
    """
    click.echo(click.style("Step 1: Admin API URL", fg="yellow"))

    default_url = "http://localhost:9080"
    url = click.prompt(
        "Enter Admin API URL",
        default=default_url,
        type=str
    ).strip()

    # Normalize URL
    if not url.startswith(('http://', 'https://')):
      if url.startswith('localhost') or url.startswith('127.0.0.1'):
        url = f"http://{url}"
      else:
        url = f"https://{url}"

    self.is_dev_mode = self._is_development_mode(url)

    if self.is_dev_mode:
      click.echo(click.style("ℹ Development mode detected (localhost/docker)", fg="cyan"))

    # Try to connect
    try:
      import requests

      verify_ssl = not self.is_dev_mode
      response = requests.get(
          f"{url}/api/v1/health",
          timeout=5,
          verify=verify_ssl
      )

      # In dev mode, accept any response < 500
      if self.is_dev_mode and response.status_code < 500:
        self.admin_url = url
        click.echo(click.style("✓ Connected to Admin API", fg="green"))
        return True
      elif not self.is_dev_mode and response.status_code == 200:
        self.admin_url = url
        click.echo(click.style("✓ Connected to Admin API", fg="green"))
        return True

      # Connection check failed, but in dev mode offer to continue
      if self.is_dev_mode:
        click.echo(
            click.style(
                f"⚠ Connection check returned {response.status_code}",
                fg="yellow"
            )
        )
        if click.confirm("Continue anyway?", default=True):
          self.admin_url = url
          return True
      else:
        click.echo(
            click.style(
                f"✗ Failed to connect (HTTP {response.status_code})",
                fg="red"
            )
        )
        return False

    except Exception as e:
      click.echo(
          click.style(f"✗ Connection failed: {str(e)[:80]}", fg="red")
      )

      if self.is_dev_mode:
        click.echo("Note: In development mode, connection errors might be expected.")
        if click.confirm("Continue anyway?", default=False):
          self.admin_url = url
          return True

      return False

  def _get_username(self) -> bool:
    """
    Prompt for admin username.

    Returns:
        True if username provided
    """
    click.echo()
    click.echo(click.style("Step 2: Authentication", fg="yellow"))

    self.username = click.prompt("Enter admin username", type=str).strip()

    if not self.username:
      click.echo(click.style("✗ Username cannot be empty", fg="red"))
      return False

    return True

  def _authenticate(self) -> bool:
    """
    Perform passwordless authentication (same as CLI).

    Handles both single-call and challenge modes.

    Returns:
        True if authentication successful and token saved
    """
    click.echo()
    click.echo(click.style("Step 3: Passwordless Authentication", fg="yellow"))
    click.echo()

    auth_manager = AuthManager(self.admin_url, verify_ssl=not self.is_dev_mode)

    # Call /api/v1/admin/auth/login (same as CLI)
    click.echo("Initiating passwordless authentication...")
    login_response = auth_manager.login(
        username=self.username,
        challenge=False,  # Start with single-call
        timeout=360  # 6 minutes for device approval
    )

    if not login_response:
      click.echo(click.style("✗ Authentication initiation failed", fg="red"))
      return False

    # Log the response for debugging
    import logging
    logger = logging.getLogger(__name__)
    logger.debug(f"Login response keys: {login_response.keys() if login_response else 'None'}")
    logger.debug(f"Login response: {login_response}")

    # Check if successful (single-call mode)
    if login_response.get('success') and login_response.get('token'):
      token = login_response.get('token')

      # Log token info
      logger.debug(f"Token received - length: {len(token)}")
      logger.debug(f"Token preview: {token[:20]}...{token[-10:] if len(token) > 30 else ''}")

      click.echo(click.style("✓ Authentication successful!", fg="green"))

      # Save token with metadata
      logger.debug(f"Attempting to save token...")
      if self._save_bearer_token(token, login_response):  # Pass full response!
        return True
      else:
        click.echo(click.style("✗ Failed to save token", fg="red"))
        return False

    # Check if challenge mode (two-step)
    if (login_response.get('status') == 'pending' and
        login_response.get('authAttemptId') and
        login_response.get('challengeCode')):

      auth_attempt_id = login_response.get('authAttemptId')
      challenge_code = login_response.get('challengeCode')

      click.echo()
      click.echo(click.style("Challenge verification required", fg="yellow"))
      click.echo()
      click.echo(f"📱 Challenge code: {click.style(str(challenge_code), fg='cyan', bold=True)}")
      click.echo("   Enter this code on your enrolled device and approve.")
      click.echo()
      click.echo("⏳ Waiting for device approval (up to 5 minutes)...")
      click.echo()

      # Wait for challenge completion
      with click.progressbar(
          length=30,
          label="Waiting",
          show_percent=False,
          show_eta=False
      ) as bar:
        for i in range(30):
          challenge_response = auth_manager.wait_for_challenge(
              auth_attempt_id=auth_attempt_id,
              challenge_code=challenge_code,
              timeout=10
          )

          if challenge_response and challenge_response.get('success'):
            bar.update(30)
            break

          bar.update(1)

      if not challenge_response or not challenge_response.get('success'):
        click.echo()
        click.echo(click.style("✗ Authentication rejected or timed out", fg="red"))
        return False

      token = challenge_response.get('token')
      if token and self._save_bearer_token(token, challenge_response):  # Pass full response!
        click.echo()
        click.echo(click.style("✓ Authentication successful!", fg="green"))
        return True
      else:
        click.echo(click.style("✗ Failed to save token", fg="red"))
        return False

    # Unknown response
    click.echo(click.style("✗ Unexpected authentication response", fg="red"))
    return False

  def _save_bearer_token(self, token: str, login_response: dict = None) -> bool:
    """
    Save bearer token, username, admin type and expiration to config.

    Args:
        token: Bearer token string
        login_response: Full login response dict from API

    Returns:
        True if saved successfully
    """
    try:
      self.config.set_bearer_token(token)
      self.config.set_admin_username(self.username)
      self.config.set_last_auth_time()

      # Save additional metadata from login response if available
      if login_response:
        admin_type = login_response.get('adminType')
        if admin_type:
          self.config.set_admin_type(admin_type)
          log.debug(f"Saved admin type: {admin_type}")

        expires_at = login_response.get('expiresAt')
        if expires_at:
          self.config.set_token_expires_at(expires_at)
          log.debug(f"Saved token expiration: {expires_at}")

      local_config_path = Path.cwd() / "ezkey.json"
      if local_config_path.exists():
        self.config.save(global_config=False)
        click.echo(click.style("✓ Bearer token saved to local config (./ezkey.json)", fg="green"))
      else:
        self.config.save(global_config=True)
        click.echo(click.style("✓ Bearer token saved to global config (~/.ezkey/ezkey.json)", fg="green"))

      return True
    except Exception as e:
      log.error("Failed to save bearer token: %s", e)
      return False

  def _is_development_mode(self, url: str) -> bool:
    """
    Detect development mode (localhost or docker).

    Args:
        url: Admin API URL

    Returns:
        True if development mode
    """
    dev_indicators = [
        'localhost',
        '127.0.0.1',
        '0.0.0.0',
        'host.docker.internal',
        'docker.for.mac.localhost',
        'docker.for.win.localhost',
    ]

    return any(indicator in url.lower() for indicator in dev_indicators)

