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
from pathlib import Path

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

  #debug_output {
      margin: 1 0 0 0;
      border: solid $primary;
      height: 10;
      width: 1fr;
      padding: 0 1;
      overflow: auto;
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

      # Username input (step 1)
      yield Label("Username:", classes="label")
      yield Input(placeholder="Enter admin username", id="username_input")

      # Challenge section (step 2)
      with Vertical(id="challenge_section"):
        yield Label("", id="challenge_code", classes="label")
        yield Label("Waiting for device approval...", id="challenge_status")

      yield Label("", id="status_label")

      # Debug output
      yield Label("", id="debug_output")

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
    debug_output = self.query_one("#debug_output", Label)
    
    def add_debug(msg: str):
      """Add debug message to UI."""
      current = debug_output.renderable
      if isinstance(current, str) and current:
        debug_output.update(current + "\n" + msg)
      else:
        debug_output.update(msg)
    
    try:
      from ezkey_cli.auth.auth_manager import AuthManager

      add_debug("[1] Starting auth...")

      self.auth_manager = AuthManager(
          self.admin_url,
          verify_ssl=False
      )

      # Step 1: Call login
      add_debug("[2] POST /api/v1/admin/auth/login")
      
      login_response = self.auth_manager.login(
          username=self.username,
          challenge=False,
          non_blocking=True,
          timeout=360
      )

      add_debug(f"[3] Response: {login_response}")

      if not login_response:
        add_debug("[4] ERROR: Empty response")
        self._show_error("Authentication initiation failed")
        return

      success = login_response.get('success')
      token = login_response.get('token')
      status = login_response.get('status')
      auth_id = login_response.get('authAttemptId')
      ch_code = login_response.get('challengeCode')

      add_debug(f"[5] Parsed: success={success}, status={status}, authAttemptId={auth_id}, code={ch_code}")

      # Case 1: Success with token
      if success and token:
        add_debug("[6] Token received - saving...")
        self._save_token(token, login_response)
        return

      # Case 2: Pending
      if status == 'pending' and auth_id:
        add_debug(f"[7] Pending - showing UI...")
        
        self.auth_attempt_id = auth_id
        self.challenge_code = ch_code
        
        # Show UI
        self.query_one("#username_input", Input).display = False
        
        if ch_code:
          add_debug(f"[8] Challenge code: {ch_code}")
          self.query_one("#challenge_section").display = True
          self.query_one("#challenge_code", Label).update(f"📱 Challenge Code: {ch_code}")
          self.query_one("#status_label", Label).update("⏳ Waiting for device to verify code...")
        else:
          add_debug("[9] No code - waiting for device...")
          self.query_one("#status_label", Label).update("⏳ Waiting for device approval...")
        
        # Step 2: Call wait_for_challenge
        add_debug(f"[10] POST /api/v1/admin/auth/passwordless-wait")
        
        wait_response = self.auth_manager.wait_for_challenge(
            auth_attempt_id=auth_id,
            challenge_code=ch_code,
            timeout=360
        )

        add_debug(f"[11] Response: {wait_response}")

        if wait_response and wait_response.get('success') and wait_response.get('token'):
          add_debug("[12] Success - token received")
          self._save_token(wait_response.get('token'), wait_response)
          return

        if wait_response and not wait_response.get('success'):
          add_debug(f"[13] Rejected")
          msg = wait_response.get('message', 'Device rejected')
          self._show_error(f"❌ {msg}")
          return

        add_debug(f"[14] Unexpected response")
        self._show_error("Unexpected response")
        return

      # Case 3: Error
      if success == False:
        add_debug(f"[15] Error response")
        error_msg = login_response.get('message', 'Authentication failed')
        self._show_error(error_msg)
        return

      add_debug(f"[16] Unknown response")
      self._show_error("Unexpected authentication response")

    except Exception as e:
      add_debug(f"[ERROR] {str(e)}")
      log.error(f"Authentication error: {e}", exc_info=True)
      self._show_error(f"Error: {str(e)[:50]}")
        return

      # Case 2: Pending (needs wait_for_challenge call)
      if status == 'pending' and auth_id:
        debug_file.write_text(debug_file.read_text() + f"[7] PENDING - authAttemptId={auth_id}, challengeCode={ch_code}\n")
        
        self.auth_attempt_id = auth_id
        self.challenge_code = ch_code
        
        # Show UI immediately
        self.query_one("#username_input", Input).display = False
        
        if ch_code:
          debug_file.write_text(debug_file.read_text() + f"[8] Showing challenge code: {ch_code}\n")
          self.query_one("#challenge_section").display = True
          self.query_one("#challenge_code", Label).update(f"📱 Challenge Code: {ch_code}")
          self.query_one("#status_label", Label).update("⏳ Waiting for device to verify code...")
        else:
          debug_file.write_text(debug_file.read_text() + "[9] No challenge code - waiting for device approval\n")
          self.query_one("#status_label", Label).update("⏳ Waiting for device approval...")
        
        # Step 2: Call wait_for_challenge
        debug_file.write_text(debug_file.read_text() + f"[10] Calling wait_for_challenge with authAttemptId={auth_id}, challengeCode={ch_code}\n")
        
        wait_response = self.auth_manager.wait_for_challenge(
            auth_attempt_id=auth_id,
            challenge_code=ch_code,
            timeout=360
        )

        debug_file.write_text(debug_file.read_text() + f"[11] Wait response: {wait_response}\n")

        if wait_response and wait_response.get('success') and wait_response.get('token'):
          debug_file.write_text(debug_file.read_text() + "[12] APPROVED - Token received\n")
          self._save_token(wait_response.get('token'), wait_response)
          return

        if wait_response and not wait_response.get('success'):
          debug_file.write_text(debug_file.read_text() + f"[13] REJECTED - success=false\n")
          msg = wait_response.get('message', 'Device rejected the authentication request')
          self._show_error(f"❌ {msg}")
          return

        debug_file.write_text(debug_file.read_text() + f"[14] Unexpected wait response: {wait_response}\n")
        msg = wait_response.get('message', 'Unexpected response') if wait_response else 'No response from server'
        self._show_error(f"Authentication failed: {msg}")
        return

    except Exception as e:
      debug_file.write_text(debug_file.read_text() + f"[ERROR] Exception: {str(e)}\n")
      log.error(f"Authentication error: {e}", exc_info=True)
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

      from pathlib import Path
      local_config_path = Path.cwd() / "ezkey.json"
      if local_config_path.exists():
        self.config.save(global_config=False)
      else:
        # Create local config for TUI instance isolation
        self.config.save(global_config=False)

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

