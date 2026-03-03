"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

TUI Module: Filter Modal
Description: Modal for setting search/filter criteria
"""

from textual.screen import ModalScreen
from textual.widgets import Button, Input, Label, Static, Checkbox
from textual.containers import Vertical, Horizontal, Container
import logging

log = logging.getLogger(__name__)


class FilterModal(ModalScreen):
  """Modal screen for filtering integrations."""

  CSS = """
  FilterModal {
      align: center middle;
  }

  #modal_container {
      width: 60;
      height: auto;
      border: thick $primary;
      background: $surface;
      padding: 1 2;
  }

  .form_row {
      height: auto;
      margin: 1 0;
  }

  .label {
      width: 20;
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
  """

  def __init__(self, current_filters=None, *args, **kwargs):
    super().__init__(*args, **kwargs)
    self.current_filters = current_filters or {}

  def compose(self):
    """Compose the modal."""
    with Container(id="modal_container"):
      yield Label("Filter Integrations", id="title")

      with Vertical(classes="form_row"):
        yield Label("Name (partial match):", classes="label")
        yield Input(
            value=self.current_filters.get("name", ""),
            placeholder="e.g., ACME",
            id="filter_name"
        )

      with Vertical(classes="form_row"):
        yield Label("Tenant ID (GlobalAdmin only):", classes="label")
        tid = self.current_filters.get("tenant_id")
        yield Input(
            value=str(tid) if tid is not None else "",
            placeholder="e.g., 1 or 2",
            id="filter_tenant_id"
        )

      with Vertical(classes="form_row"):
        yield Checkbox(
            "Active Only",
            value=self.current_filters.get("active", False),
            id="filter_active"
        )

      with Horizontal(id="button_row"):
        yield Button("Apply", variant="primary", id="apply_btn")
        yield Button("Clear", variant="warning", id="clear_btn")
        yield Button("Cancel", variant="default", id="cancel_btn")

  def on_button_pressed(self, event: Button.Pressed) -> None:
    """Handle button press."""
    if event.button.id == "apply_btn":
      self._apply_filters()
    elif event.button.id == "clear_btn":
      self.dismiss({})
    elif event.button.id == "cancel_btn":
      self.dismiss(None)

  def _apply_filters(self) -> None:
    """Gather and return filter values."""
    name = self.query_one("#filter_name", Input).value.strip()
    tenant_id_str = self.query_one("#filter_tenant_id", Input).value.strip()
    active = self.query_one("#filter_active", Checkbox).value

    filters = {}
    if name:
      filters["name"] = name
    if tenant_id_str:
      try:
        filters["tenant_id"] = int(tenant_id_str)
      except ValueError:
        pass  # Skip invalid tenant ID
    if active:
      filters["active"] = True

    self.dismiss(filters)
