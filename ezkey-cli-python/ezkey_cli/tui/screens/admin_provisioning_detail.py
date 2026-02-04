"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

TUI Module: Admin Provisioning Detail Screen
Description: Detail view for a single admin
"""

from textual.screen import Screen
from textual.widgets import Header, Footer, Static, Label, Button
from textual.binding import Binding
from textual.containers import Vertical, Horizontal
import logging

log = logging.getLogger(__name__)


class AdminProvisioningDetailScreen(Screen):
  """Admin detail screen."""

  BINDINGS = [
      Binding("escape", "back", "Back"),
      Binding("h", "back", "Back"),
      Binding("o", "onboarding", "Onboarding"),
      Binding("r", "refresh", "Refresh"),
      Binding("q", "quit", "Quit"),
  ]

  CSS = """
  Screen {
      layout: vertical;
      background: $surface;
  }

  #content {
      height: 1fr;
      padding: 1 2;
  }

  .section {
      border: solid $primary;
      padding: 1 2;
      margin: 1 0;
      height: auto;
  }

  .label {
      color: $text-muted;
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
      color: $warning;
    }
  """

  def __init__(self, admin_data: dict, *args, **kwargs):
    super().__init__(*args, **kwargs)
    self.admin_data = admin_data or {}
    self.admin_id = self.admin_data.get("adminId")
    self.onboarding_data = None

  def compose(self):
    """Compose the detail screen."""
    yield Header(show_clock=True)
    with Vertical(id="content"):
      yield Label(f"Admin #{self.admin_id}", id="title")
      yield Static("Loading...", id="detail_content", classes="section")
      yield Label("", id="status_label")
      with Horizontal(id="button_row"):
        yield Button("Copy Enrollment ID", id="copy_enrollment_id")
        yield Button("Copy Proof Token", id="copy_proof_token")
        yield Button("Copy Challenge", id="copy_challenge")
    yield Footer()

  def on_button_pressed(self, event: Button.Pressed) -> None:
    """Handle copy buttons for onboarding credentials."""
    if event.button.id == "copy_enrollment_id":
      self._copy_onboarding_value("enrollmentId")
    elif event.button.id == "copy_proof_token":
      self._copy_onboarding_value("enrollmentProofToken")
    elif event.button.id == "copy_challenge":
      self._copy_onboarding_value("enrollmentChallenge")

  def on_mount(self) -> None:
    """Called when screen is mounted."""
    self._render_detail()
    self._set_copy_buttons_visible(False)

  def _render_detail(self) -> None:
    """Render admin details."""
    data = self.admin_data or {}
    content = []

    content.append(f"Admin ID: {data.get('adminId', 'N/A')}")
    content.append(f"Username: {data.get('username', 'N/A')}")
    content.append(f"Type: {data.get('adminType', 'N/A')}")
    content.append(f"Tenant ID: {data.get('tenantId', 'N/A')}")
    content.append(f"Active: {data.get('active', 'N/A')}")
    content.append(f"Created At: {data.get('createdAt', 'N/A')}")

    if self.onboarding_data:
      content.append("")
      content.append("Onboarding Credentials (save securely):")
      content.append(f"Enrollment ID: {self.onboarding_data.get('enrollmentId', 'N/A')}")
      content.append(f"Enrollment Proof Token: {self.onboarding_data.get('enrollmentProofToken', 'N/A')}")
      content.append(f"Enrollment Challenge: {self.onboarding_data.get('enrollmentChallenge', 'N/A')}")

    detail_widget = self.query_one("#detail_content", Static)
    detail_widget.update("\n".join(content))

  def _show_error(self, message: str) -> None:
    """Show error message."""
    detail_widget = self.query_one("#detail_content", Static)
    detail_widget.update(f"❌ Error: {message}")

  def _copy_onboarding_value(self, key: str) -> None:
    """Copy onboarding value to clipboard."""
    status = self.query_one("#status_label", Label)
    if not self.onboarding_data:
      status.update("Onboarding credentials not loaded")
      return
    value = self.onboarding_data.get(key)
    if value is None:
      status.update("Value not available")
      return
    self._copy_to_clipboard(str(value))

  def _copy_to_clipboard(self, text: str) -> None:
    """Copy text to clipboard and show status."""
    status = self.query_one("#status_label", Label)
    if not text:
      status.update("Nothing to copy")
      return

    try:
      import pyperclip

      pyperclip.copy(text)
      status.update("Copied to clipboard")
      return
    except Exception as e:
      log.warning(f"Clipboard copy failed (pyperclip): {e}")

    try:
      import tkinter as tk

      root = tk.Tk()
      root.withdraw()
      root.clipboard_clear()
      root.clipboard_append(text)
      root.update()
      root.destroy()
      status.update("Copied to clipboard")
    except Exception as e:
      log.error(f"Clipboard copy failed (tkinter): {e}")
      status.update("Copy failed: clipboard unavailable")

  def _set_copy_buttons_visible(self, visible: bool) -> None:
    """Show or hide onboarding copy buttons."""
    self.query_one("#copy_enrollment_id", Button).display = visible
    self.query_one("#copy_proof_token", Button).display = visible
    self.query_one("#copy_challenge", Button).display = visible

  def action_back(self) -> None:
    """Go back to list."""
    self.app.pop_screen()

  def action_refresh(self) -> None:
    """Refresh view."""
    self._render_detail()

  def action_onboarding(self) -> None:
    """Load onboarding credentials for this admin."""
    if not self.admin_id:
      self._show_error("Missing admin ID")
      return

    api_client = self.app.api_client
    if not api_client:
      self._show_error("No API client available")
      return

    response = api_client.get_admin_onboarding(self.admin_id)
    if not response:
      if api_client.last_auth_error and hasattr(self.app, "handle_auth_error"):
        self.app.handle_auth_error()
        return
      self._show_error(f"Failed to load onboarding for admin {self.admin_id}")
      return

    self.onboarding_data = response
    self._render_detail()
    self._set_copy_buttons_visible(True)

  def action_quit(self) -> None:
    """Quit the application."""
    self.app.exit()
