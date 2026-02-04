"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

TUI Module: Integration Create Modal
Description: Modal form for creating new integrations
"""

from textual.screen import ModalScreen
from textual.widgets import Button, Input, Label, Static
from textual.binding import Binding
from textual.containers import Vertical, Horizontal, Container
from textual.validation import Length
import logging

log = logging.getLogger(__name__)


class CreateIntegrationModal(ModalScreen):
  """Modal screen for creating a new integration."""

  BINDINGS = [
      Binding("escape", "cancel", "Cancel"),
  ]

  CSS = """
  CreateIntegrationModal {
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
    self.form_data = {}
    self._error_message = ""

  def compose(self):
    """Compose the modal."""
    with Container(id="modal_container"):
      yield Label("Create New Integration", id="title")

      with Vertical(classes="form_row"):
        yield Label("Name (EN):", classes="label")
        yield Input(placeholder="e.g., ACME Portal", id="name_en", validators=[Length(1, 100)])

      with Vertical(classes="form_row"):
        yield Label("Description (EN):", classes="label")
        yield Input(placeholder="e.g., Main authentication portal", id="desc_en", validators=[Length(0, 500)])

      with Vertical(classes="form_row"):
        yield Label("Logo URL (optional):", classes="label")
        yield Input(placeholder="https://example.com/logo.png", id="logo")

      yield Label("", id="error_label")

      with Horizontal(id="button_row"):
        yield Button("Create", variant="primary", id="create_btn")
        yield Button("Cancel", variant="default", id="cancel_btn")

  def on_button_pressed(self, event: Button.Pressed) -> None:
    """Handle button press."""
    if event.button.id == "create_btn":
      self._create_integration()
    elif event.button.id == "cancel_btn":
      self.action_cancel()

  def _create_integration(self) -> None:
    """Create integration via API."""
    # Get form values
    name_en = self.query_one("#name_en", Input).value.strip()
    desc_en = self.query_one("#desc_en", Input).value.strip()
    logo = self.query_one("#logo", Input).value.strip()

    # Clear previous error
    self._show_error("")

    # Validate
    if not name_en:
      self._show_error("Name is required")
      return

    # Build payload
    payload = {
        "i18n": [
            {
                "language": "en",
                "name": name_en,
                "description": desc_en or ""
            }
        ]
    }

    if logo:
      payload["logo"] = logo

    # Call API
    api_client = self.app.api_client
    if not api_client:
      self._show_error("No API client available")
      return

    try:
      response = api_client._post("/api/v1/integrations", payload)

      if response:
        log.info(f"Integration created: {response.get('id')}")
        self.dismiss(True)  # Return True to indicate success
      else:
        self._show_error("Failed to create integration (no response from API)")
    except Exception as e:
      error_msg = str(e)
      log.error(f"Error creating integration: {e}")
      self._show_error(error_msg)

  def _show_error(self, message: str) -> None:
    """Show error message in modal."""
    self._error_message = message
    error_label = self.query_one("#error_label", Label)
    if message:
      error_label.update(f"❌ {message}")
      error_label.display = True
    else:
      error_label.update("")
      error_label.display = False

  def action_cancel(self) -> None:
    """Cancel and close modal."""
    log.debug("Create integration cancelled")
    self.dismiss(False)
