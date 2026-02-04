"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

TUI Module: API Key Create Modal
Description: Modal form for creating API keys
"""

from textual.screen import ModalScreen
from textual.widgets import Button, Input, Label
from textual.binding import Binding
from textual.containers import Vertical, Horizontal, Container
import logging

log = logging.getLogger(__name__)


class CreateApiKeyModal(ModalScreen):
  """Modal screen for creating API keys."""

  BINDINGS = [
      Binding("escape", "cancel", "Cancel"),
  ]

  CSS = """
  CreateApiKeyModal {
      align: center middle;
  }

  #modal_container {
      width: 75;
      height: auto;
      border: thick $primary;
      background: $surface;
      padding: 1 2;
  }

  .form_row {
      height: auto;
      margin: 1 0;
      width: 1fr;
  }

  .label {
      width: 1fr;
      color: $text-muted;
  }

  Input {
      width: 1fr;
  }

  #button_row {
      height: auto;
      margin-top: 2;
      align: center middle;
  }

  Button {
      margin: 0 1;
  }

  #error_label {
      color: $error;
      margin: 1 0;
      display: none;
      width: 1fr;
  }
  """

  def __init__(self, *args, **kwargs):
    super().__init__(*args, **kwargs)
    self._error_message = ""

  def compose(self):
    """Compose the modal."""
    with Container(id="modal_container"):
      yield Label("Create API Key", id="title")

      with Vertical(classes="form_row"):
        yield Label("Integration ID:", classes="label")
        yield Input(placeholder="e.g., 1", id="integration_id")

      with Vertical(classes="form_row"):
        yield Label("Description (optional):", classes="label")
        yield Input(placeholder="e.g., Admin Server", id="description")

      with Vertical(classes="form_row"):
        yield Label("Expires At (optional, ISO 8601):", classes="label")
        yield Input(placeholder="e.g., 2026-12-31T23:59:59Z", id="expires_at")

      with Vertical(classes="form_row"):
        yield Label("IP Whitelist (comma-separated):", classes="label")
        yield Input(placeholder="e.g., 10.0.0.0/8, 192.168.1.10", id="ip_whitelist")

      yield Label("", id="error_label")

      with Horizontal(id="button_row"):
        yield Button("Create", variant="primary", id="create_btn")
        yield Button("Cancel", variant="default", id="cancel_btn")

  def on_button_pressed(self, event: Button.Pressed) -> None:
    """Handle button press."""
    if event.button.id == "create_btn":
      self._create_api_key()
    elif event.button.id == "cancel_btn":
      self.action_cancel()

  def _create_api_key(self) -> None:
    """Create API key via API."""
    integration_id_value = self.query_one("#integration_id", Input).value.strip()
    description = self.query_one("#description", Input).value.strip()
    expires_at = self.query_one("#expires_at", Input).value.strip()
    ip_whitelist_value = self.query_one("#ip_whitelist", Input).value.strip()

    self._show_error("")

    if not integration_id_value.isdigit():
      self._show_error("Integration ID must be numeric")
      return

    payload = {
        "integrationId": int(integration_id_value)
    }
    if description:
      payload["description"] = description
    if expires_at:
      payload["expiresAt"] = expires_at
    if ip_whitelist_value:
      ip_list = [ip.strip() for ip in ip_whitelist_value.split(",") if ip.strip()]
      if ip_list:
        payload["ipWhitelist"] = ip_list

    api_client = self.app.api_client
    if not api_client:
      self._show_error("No API client available")
      return

    try:
      response = api_client.create_api_key(payload)
      if response:
        self.dismiss({
            "created": True,
            "response": response
        })
      else:
        self._show_error("Failed to create API key (no response)")
    except Exception as e:
      log.error(f"Error creating API key: {e}")
      self._show_error(str(e))

  def _show_error(self, message: str) -> None:
    """Show error message in modal."""
    error_label = self.query_one("#error_label", Label)
    if message:
      error_label.update(f"❌ {message}")
      error_label.display = True
    else:
      error_label.update("")
      error_label.display = False

  def action_cancel(self) -> None:
    """Cancel and close modal."""
    self.dismiss({"created": False})
