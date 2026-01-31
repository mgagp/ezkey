"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

TUI Module: Quick Re-Authentication Screen
Description: Fast re-auth when token is expired and username is known
"""

from textual.screen import ModalScreen
from textual.widgets import Static, Button, Label
from textual.containers import Vertical, Horizontal
import logging
from datetime import datetime, timezone

log = logging.getLogger(__name__)


class QuickReAuthScreen(ModalScreen):
  """Quick re-auth modal when token is expired and username is known."""

  CSS = """
  QuickReAuthScreen {
      align: center middle;
  }

  #reauth_container {
      width: 75;
      height: auto;
      border: solid $accent;
      padding: 1 2;
      background: $surface;
  }

  #title {
      color: $accent;
      margin: 0 0 1 0;
      text-align: center;
      width: 1fr;
  }

  #message {
      margin: 0 0 1 0;
      text-align: center;
      width: 1fr;
  }

  #username_display {
      margin: 0 0 1 0;
      text-align: center;
      color: $text-muted;
      width: 1fr;
  }

  #button_row {
      margin: 1 0 0 0;
      height: auto;
  }

  Button {
      margin: 0 1;
  }

  #status_label {
      margin: 1 0 0 0;
      text-align: center;
      color: $error;
      width: 1fr;
  }

  .success {
      color: $success;
  }

  .waiting {
      color: $warning;
  }
  """

  def __init__(self, config, admin_url: str, api_client):
    """
    Initialize quick re-auth screen.

    Args:
        config: ConfigManager instance
        admin_url: Admin API URL
        api_client: ApiClient instance to update after re-auth
    """
    super().__init__()
    self.config = config
    self.admin_url = admin_url
    self.api_client = api_client
    self.auth_manager = None
    self.auth_attempt_id = None

  def compose(self):
    """Compose the quick re-auth screen."""
    with Vertical(id="reauth_container"):
      yield Label("⏱️ Token Expired", id="title")
      yield Label(
          "Your authentication token has expired.\n"
          "Your credentials are still valid.",
          id="message"
      )

      username = self.config.get_admin_username()
      if username:
        yield Label(f"Logged in as: {username}", id="username_display")
      else:
        yield Label("(No username stored)", id="username_display")

      yield Label("", id="status_label")

      with Horizontal(id="button_row"):
        yield Button("Re-authenticate", variant="primary", id="reauth_btn")
        yield Button("Logout", variant="default", id="logout_btn")

  def on_button_pressed(self, event: Button.Pressed) -> None:
    """Handle button press."""
    if event.button.id == "reauth_btn":
      self.action_reauthenticate()
    elif event.button.id == "logout_btn":
      if hasattr(self.app, "logout_and_exit"):
        self.app.logout_and_exit()
      else:
        self.app.exit()

  def action_reauthenticate(self) -> None:
    """Start quick re-authentication (1-click approve flow)."""
    username = self.config.get_admin_username()
    if not username:
      self._show_error("No username stored")
      return

    self.query_one("#reauth_btn", Button).disabled = True
    status = self.query_one("#status_label", Label)
    status.update("⏳ Initiating authentication...")

    self._start_authentication(username)

  def _start_authentication(self, username: str) -> None:
    """Start the authentication process via AuthManager."""
    try:
      from ezkey_cli.auth.auth_manager import AuthManager

      log.debug(f"Starting quick re-auth for user: {username}")

      self.auth_manager = AuthManager(
          self.admin_url,
          verify_ssl=False  # Dev mode
      )

      # Call login endpoint
      login_response = self.auth_manager.login(
          username=username,
          challenge=False,
          timeout=360
      )

      if not login_response:
        self._show_error("Authentication initiation failed")
        return

      log.debug(f"Login response: {login_response}")

      # Check if authentication failed
      if login_response.get('success') == False:
        error_msg = login_response.get('message', 'Authentication failed')
        self._show_error(error_msg)
        return

      # Check if single-call succeeded
      if login_response.get('success') and login_response.get('token'):
        token = login_response.get('token')
        self._save_token(token, login_response)  # Pass full response
        return

      # Check if challenge mode
      if (login_response.get('status') == 'pending' and
          login_response.get('authAttemptId')):

        self.auth_attempt_id = login_response.get('authAttemptId')
        challenge_code = login_response.get('challengeCode')

        # Show challenge code and wait
        status = self.query_one("#status_label", Label)
        status.update(f"📱 Challenge Code: {challenge_code}\n⏳ Waiting for device approval...")
        status.set_class(True, "waiting")

        # Wait for challenge completion
        self._wait_for_challenge()
        return

      self._show_error("Unexpected authentication response")

    except Exception as e:
      log.error(f"Quick re-auth error: {e}", exc_info=True)
      self._show_error(f"Error: {str(e)[:50]}")

  def _wait_for_challenge(self) -> None:
    """Poll for challenge completion."""
    if not self.auth_attempt_id or not self.auth_manager:
      self._show_error("Challenge session lost")
      return

    try:
      # Poll for up to 5 minutes
      for attempt in range(30):
        challenge_code = self.config.get_admin_username()  # Use any identifier
        challenge_response = self.auth_manager.wait_for_challenge(
            auth_attempt_id=self.auth_attempt_id,
            challenge_code=challenge_code or "quick-reauth",
            timeout=10
        )

        if challenge_response and challenge_response.get('success'):
          token = challenge_response.get('token')
          if token:
            self._save_token(token, challenge_response)  # Pass full response
            return

      # Timeout
      self._show_error("Challenge timeout - please try again")

    except Exception as e:
      log.error(f"Challenge wait error: {e}", exc_info=True)
      self._show_error(f"Error: {str(e)[:50]}")

  def _save_token(self, token: str, login_response: dict = None) -> None:
    """Save token and update session time."""
    try:
      self.config.set_bearer_token(token)
      self.config.set_last_auth_time()  # Reset session timeout

      # Save metadata from login response if available
      if login_response:
        admin_type = login_response.get('adminType')
        if admin_type:
          self.config.set_admin_type(admin_type)
          log.debug(f"Saved admin type: {admin_type}")

        expires_at = login_response.get('expiresAt')
        if expires_at:
          self.config.set_token_expires_at(expires_at)
          log.debug(f"Saved token expiration: {expires_at}")

      self.config.save(global_config=True)

      # Update API client
      self.api_client.bearer_token = token
      self.api_client.headers["Authorization"] = f"Bearer {token}"

      log.info("Token renewed and session reset")

      # Show success
      status_label = self.query_one("#status_label", Label)
      status_label.update("✓ Re-authenticated successfully!")
      status_label.set_class(False, "waiting")
      status_label.set_class(True, "success")

      # Close modal and show dashboard
      self.app.pop_screen()
      self.app.push_screen("home")

    except Exception as e:
      log.error(f"Token save error: {e}", exc_info=True)
      self._show_error(f"Error saving token: {str(e)[:50]}")

  def _show_error(self, message: str) -> None:
    """Show error message and reset."""
    status_label = self.query_one("#status_label", Label)
    # Display full error message without truncation
    status_label.update(message)
    status_label.set_class(False, "waiting")
    self.query_one("#reauth_btn", Button).disabled = False
