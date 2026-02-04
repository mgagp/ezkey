"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

TUI Module: Tenant Create Modal
Description: Modal form for creating tenants
"""

from textual.screen import ModalScreen
from textual.widgets import Button, Input, Label
from textual.containers import Vertical, Horizontal, Container
from textual.validation import Length
import logging

log = logging.getLogger(__name__)


class CreateTenantModal(ModalScreen):
  """Modal screen for creating a tenant."""

  BINDINGS = [
      ("escape", "cancel", "Cancel"),
  ]

  CSS = """
  CreateTenantModal {
      align: center middle;
  }

  #modal_container {
      width: 70;
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
      yield Label("Create Tenant", id="title")

      with Vertical(classes="form_row"):
        yield Label("Tenant Name:", classes="label")
        yield Input(
            placeholder="e.g., Acme Corporation",
            id="tenant_name",
            validators=[Length(3, 100)]
        )

      with Vertical(classes="form_row"):
        yield Label("Description (optional):", classes="label")
        yield Input(
            placeholder="e.g., Acme Corp's Ezkey tenant",
            id="tenant_description",
            validators=[Length(0, 500)]
        )

      yield Label("", id="error_label")

      with Horizontal(id="button_row"):
        yield Button("Create", variant="primary", id="create_btn")
        yield Button("Cancel", variant="default", id="cancel_btn")

  def on_button_pressed(self, event: Button.Pressed) -> None:
    """Handle button press."""
    if event.button.id == "create_btn":
      self._create_tenant()
    elif event.button.id == "cancel_btn":
      self.action_cancel()

  def _create_tenant(self) -> None:
    """Create tenant via API."""
    name = self.query_one("#tenant_name", Input).value.strip()
    description = self.query_one("#tenant_description", Input).value.strip()

    self._show_error("")

    if not name:
      self._show_error("Tenant name is required")
      return

    payload = {
        "tenantName": name,
        "tenantDescription": description or None
    }

    api_client = self.app.api_client
    if not api_client:
      self._show_error("No API client available")
      return

    try:
      response = api_client.create_tenant(payload)
      if response:
        log.info("Tenant created: %s", response.get("tenantId"))
        self.dismiss(True)
      else:
        self._show_error("Failed to create tenant")
    except Exception as e:
      log.error("Error creating tenant: %s", e)
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
    self.dismiss(False)
