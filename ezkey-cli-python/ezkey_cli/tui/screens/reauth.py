"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

TUI Module: Re-Authentication Screen
Description: Modal screen for re-authenticating when token is expired
"""

from textual.screen import ModalScreen
from textual.widgets import Header, Footer, Static, Button, Label, Input
from textual.containers import Vertical, Horizontal
from textual.reactive import reactive
import logging

log = logging.getLogger(__name__)


class ReAuthScreen(ModalScreen):
  """Modal to authenticate or re-authenticate."""

  CSS = """
  ReAuthScreen {
      align: center middle;
  }

  #reauth_container {
      width: 75;
      height: auto;
      border: solid $primary;
      padding: 1 2;
      background: $surface;
  }

  #title {
      color: $warning;
      margin: 0 0 1 0;
      text-align: center;
      width: 1fr;
  }

  #message {
      margin: 0 0 1 0;
      text-align: center;
      width: 1fr;
  }

  #challenge_section {
      margin: 1 0;
      display: none;
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

  current_step = reactive("username")  # username, waiting, failed

  def __init__(self, config, admin_url: str, api_client, mode: str = "reauth"):
    """
    Initialize re-auth screen.

    Args:
        config: ConfigManager instance
        admin_url: Admin API URL
        api_client: ApiClient instance to update after re-auth
        mode: "login" for initial login, "reauth" for token refresh
    """
    super().__init__()
    self.config = config
    self.admin_url = admin_url
    self.api_client = api_client
    self.mode = mode
    self.username = ""
    self.auth_manager = None
    self.auth_attempt_id = None

  def compose(self):
    """Compose the re-auth screen."""
    with Vertical(id="reauth_container"):
      title = "🔐 Login" if self.mode == "login" else "🔐 Token Expired"
      message = (
          "Enter your username to authenticate."
          if self.mode == "login"
          else "Your authentication token has expired.\n"
               "Please enter your username to re-authenticate."
      )

      yield Label(title, id="title")
      yield Label(message, id="message")

      # Username input (step 1)
      yield Label("Username:", classes="label")
      yield Input(placeholder="Enter admin username", id="username_input")

      # Challenge section (step 2)
      with Vertical(id="challenge_section"):
        yield Label("", id="challenge_code", classes="label")
        yield Label("Waiting for device approval...", id="challenge_status")

      yield Label("", id="status_label")

      with Horizontal(id="button_row"):
        yield Button("Re-authenticate", variant="primary", id="reauth_btn")
        yield Button("Quit", variant="default", id="quit_btn")

  def on_button_pressed(self, event: Button.Pressed) -> None:
    """Handle button press."""
    if event.button.id == "reauth_btn":
      self.action_reauthenticate()
    elif event.button.id == "quit_btn":
      self.app.exit()

  def on_mount(self) -> None:
    """Focus username input on mount and pre-fill if available."""
    username_input = self.query_one("#username_input", Input)

    # Pre-fill username from config if available
    saved_username = self.config.get_admin_username()
    if saved_username:
      username_input.value = saved_username
      log.debug(f"Username pre-filled from config: {saved_username}")
    else:
      log.debug("No saved username in config - user will need to enter it")

    username_input.focus()

  def action_reauthenticate(self) -> None:
    """Start re-authentication process."""
    username = self.query_one("#username_input", Input).value.strip()

    if not username:
      status = self.query_one("#status_label", Label)
      status.update("❌ Username cannot be empty")
      return

    self.username = username
    self.current_step = "waiting"
    self._start_authentication()

  def _start_authentication(self) -> None:
    """Start the authentication process via AuthManager."""
    try:
      from ezkey_cli.auth.auth_manager import AuthManager

      log.debug(f"Starting authentication for user: {self.username}")

      self.auth_manager = AuthManager(
          self.admin_url,
          verify_ssl=False  # Dev mode
      )

      # Call login endpoint
      login_response = self.auth_manager.login(
          username=self.username,
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

        # Show challenge UI
        self._show_challenge(challenge_code)

        # Wait for challenge completion
        self._wait_for_challenge()
        return

      self._show_error("Unexpected authentication response")

    except Exception as e:
      log.error(f"Authentication error: {e}", exc_info=True)
      self._show_error(f"Error: {str(e)[:50]}")

  def _show_challenge(self, challenge_code: str) -> None:
    """Show challenge code and waiting message."""
    # Hide username input
    self.query_one("#username_input", Input).display = False

    # Show challenge section
    challenge_section = self.query_one("#challenge_section")
    challenge_section.display = True

    code_label = self.query_one("#challenge_code", Label)
    code_label.update(f"📱 Challenge Code: {challenge_code}")

    status_label = self.query_one("#status_label", Label)
    status_label.update("⏳ Waiting for device approval...")
    status_label.set_class(True, "waiting")

  def _wait_for_challenge(self) -> None:
    """Poll for challenge completion."""
    if not self.auth_attempt_id or not self.auth_manager:
      self._show_error("Challenge session lost")
      return

    try:
      # Poll for up to 5 minutes
      for attempt in range(30):
        challenge_response = self.auth_manager.wait_for_challenge(
            auth_attempt_id=self.auth_attempt_id,
            challenge_code=self.query_one("#challenge_code", Label).renderable.split(": ")[1] if ": " in str(self.query_one("#challenge_code", Label).renderable) else "",
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
    """Save token and show success."""
    try:
      self.config.set_bearer_token(token)
      self.config.set_admin_username(self.username)  # Also save username
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

      log.info("Token and username saved, ApiClient updated")

      # Show success
      status_label = self.query_one("#status_label", Label)
      status_label.update("✓ Re-authentication successful!")
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
    status_label.update(message)
    status_label.set_class(False, "waiting")
    status_label.set_class(True, "error")

    # Reset UI
    self.current_step = "failed"
    self.query_one("#username_input", Input).display = True
    self.query_one("#challenge_section").display = False
    self.query_one("#reauth_btn", Button).disabled = False

