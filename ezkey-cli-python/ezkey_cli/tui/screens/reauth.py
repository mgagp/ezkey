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
      padding: 0 1;
      background: $surface;
  }

  #title {
      color: $warning;
      margin: 0 0 1 0;
      height: 1;
      text-align: center;
      width: 1fr;
  }

  #message {
      margin: 0 0 1 0;
      height: auto;
      text-align: center;
      width: 1fr;
  }

  Label {
      height: 1;
      margin: 0;
  }

  #username_input {
      height: 3;
      margin: 0 0 1 0;
  }

  #challenge_section {
      margin: 1 0 0 0;
      display: none;
      width: 1fr;
      height: auto;
  }

  #challenge_code {
      height: 1;
      margin: 0 0 1 0;
  }

  #challenge_status {
      height: 1;
      margin: 0;
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
      height: 1;
  }

  .success {
      color: $success;
  }

  .waiting {
      color: $warning;
  }
  """

  current_step = reactive("username")

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
    self.challenge_code = None

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

      # Username input
      yield Label("Username:", classes="label")
      yield Input(placeholder="Enter admin username", id="username_input")

      # Challenge section
      with Vertical(id="challenge_section"):
        yield Label("", id="challenge_code", classes="label")
        yield Label("Confirm on device", id="challenge_status")

      yield Label("", id="status_label")

      with Horizontal(id="button_row"):
        yield Button("Verify in Ezkey App", variant="primary", id="reauth_btn")
        yield Button("Fetch confirmation from device", variant="primary", id="wait_btn", disabled=True)
        yield Button("Quit", variant="default", id="quit_btn")

  def on_button_pressed(self, event: Button.Pressed) -> None:
    """Handle button press."""
    if event.button.id == "reauth_btn":
      self.action_reauthenticate()
    elif event.button.id == "wait_btn":
      self._do_wait_for_device()
    elif event.button.id == "quit_btn":
      self.app.exit()

  def on_mount(self) -> None:
    """Focus username input on mount and pre-fill if available."""
    username_input = self.query_one("#username_input", Input)

    saved_username = self.config.get_admin_username()
    if saved_username:
      username_input.value = saved_username
      log.debug(f"Username pre-filled from config: {saved_username}")

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
    """Call login API and display challenge code or wait for device based on config."""
    try:
      from ezkey_cli.auth.auth_manager import AuthManager

      self.auth_manager = AuthManager(
          self.admin_url,
          verify_ssl=False
      )

      # Determine login mode from config
      login_blocking = self.config.is_login_blocking()
      log.info(f"Login mode: {'blocking' if login_blocking else 'non-blocking'}")

      # Call login
      login_response = self.auth_manager.login(
          username=self.username,
          challenge=False,
          non_blocking=True,
          timeout=360
      )

      log.info(f"Login response: {login_response}")

      if not login_response:
        self._show_error("Login failed")
        return

      # Get fields from response
      auth_attempt_id = login_response.get('authAttemptId')
      ch_code = login_response.get('challengeCode')

      # Save for non-blocking mode
      self.auth_attempt_id = auth_attempt_id
      self.challenge_code = ch_code

      # Hide username input
      self.query_one("#username_input", Input).display = False
      self.query_one("#reauth_btn", Button).disabled = True

      # Show challenge section and display the code
      self.query_one("#challenge_section").display = True
      if ch_code:
        self.query_one("#challenge_code", Label).update(f"📱 Challenge Code: {ch_code}")
        log.info(f"Displayed challenge code: {ch_code}")
      else:
        self.query_one("#challenge_code", Label).update("Waiting for device approval...")
        log.info("No challenge code in response")

      # Handle two modes
      if login_blocking:
        # BLOCKING MODE: Immediately wait for device approval
        log.info("Entering blocking wait mode...")
        self._do_wait_for_device_blocking()
      else:
        # NON-BLOCKING MODE: Show button and wait for user to click
        self.query_one("#wait_btn", Button).disabled = False
        log.info("Waiting for user to click wait button...")

    except Exception as e:
      log.error(f"Error: {e}", exc_info=True)
      self._show_error(f"Error: {str(e)[:50]}")

  def _do_wait_for_device_blocking(self) -> None:
    """Wait for device approval synchronously (blocking mode)."""
    try:
      if not self.auth_attempt_id:
        self._show_error("No auth attempt ID")
        return

      log.info(f"Waiting for device approval... (authAttemptId={self.auth_attempt_id})")

      wait_response = self.auth_manager.wait_for_challenge(
          auth_attempt_id=self.auth_attempt_id,
          challenge_code=self.challenge_code,
          timeout=360
      )

      log.info(f"Device response: {wait_response}")

      # Check if device approved
      if wait_response and wait_response.get('success') and wait_response.get('token'):
        token = wait_response.get('token')
        log.info("Device approved! Saving token...")
        self._save_token(token, wait_response)
        return

      # Device rejected or error
      if wait_response and not wait_response.get('success'):
        msg = wait_response.get('message', 'Device rejected')
        log.error(f"Device rejected: {msg}")
        self._show_error(f"❌ {msg}")
        return

      log.error("Unexpected wait response")
      self._show_error("No response from device")

    except Exception as e:
      log.error(f"Device wait error: {e}", exc_info=True)
      self._show_error(f"Error: {str(e)[:50]}")

  def _do_wait_for_device(self) -> None:
    """Wait for device approval (called when user clicks the wait button)."""
    try:
      if not self.auth_attempt_id:
        self._show_error("No auth attempt ID")
        return

      log.info(f"Waiting for device approval... (authAttemptId={self.auth_attempt_id})")
      self.query_one("#wait_btn", Button).disabled = True

      wait_response = self.auth_manager.wait_for_challenge(
          auth_attempt_id=self.auth_attempt_id,
          challenge_code=self.challenge_code,
          timeout=360
      )

      log.info(f"Device response: {wait_response}")

      # Check if device approved
      if wait_response and wait_response.get('success') and wait_response.get('token'):
        token = wait_response.get('token')
        log.info("Device approved! Saving token...")
        self._save_token(token, wait_response)
        return

      # Device rejected or error
      if wait_response and not wait_response.get('success'):
        msg = wait_response.get('message', 'Device rejected')
        log.error(f"Device rejected: {msg}")
        self._show_error(f"❌ {msg}")
        self.query_one("#wait_btn", Button).disabled = False
        return

      log.error("Unexpected wait response")
      self._show_error("No response from device")
      self.query_one("#wait_btn", Button).disabled = False

    except Exception as e:
      log.error(f"Device wait error: {e}", exc_info=True)
      self._show_error(f"Error: {str(e)[:50]}")
      self.query_one("#wait_btn", Button).disabled = False

  def _save_token(self, token: str, login_response: dict = None) -> None:
    """Save token and show success."""
    try:
      self.config.set_bearer_token(token)
      self.config.set_admin_username(self.username)
      self.config.set_last_auth_time()

      if login_response:
        admin_type = login_response.get('adminType')
        if admin_type:
          self.config.set_admin_type(admin_type)
          log.debug(f"Saved admin type: {admin_type}")

        expires_at = login_response.get('expiresAt')
        if expires_at:
          self.config.set_token_expires_at(expires_at)
          log.debug(f"Saved token expiration: {expires_at}")

      from pathlib import Path
      local_config_path = Path.cwd() / "ezkey.json"
      if local_config_path.exists():
        self.config.save(global_config=False)
      else:
        self.config.save(global_config=False)

      self.api_client.bearer_token = token
      self.api_client.headers["Authorization"] = f"Bearer {token}"

      log.info("Token and username saved, ApiClient updated")

      status_label = self.query_one("#status_label", Label)
      status_label.update("✓ Re-authentication successful!")
      status_label.set_class(False, "waiting")
      status_label.set_class(True, "success")

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

    self.current_step = "failed"
    self.query_one("#username_input", Input).display = True
    self.query_one("#challenge_section").display = False
    self.query_one("#reauth_btn", Button).disabled = False
