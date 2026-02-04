"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

TUI Module: Enrollment Filter Modal
Description: Modal for filtering enrollments
"""

from textual.screen import ModalScreen
from textual.widgets import Button, Input, Label, Checkbox
from textual.containers import Vertical, Horizontal, Container
import logging

log = logging.getLogger(__name__)


class EnrollmentFilterModal(ModalScreen):
  """Modal screen for filtering enrollments."""

  CSS = """
  EnrollmentFilterModal {
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
      yield Label("Filter Enrollments", id="title")

      with Vertical(classes="form_row"):
        yield Label("Name (partial match):", classes="label")
        yield Input(
            value=self.current_filters.get("name", ""),
            placeholder="e.g., John's iPhone",
            id="filter_name"
        )

      with Vertical(classes="form_row"):
        yield Label("Status (CREATED/BOUND/VERIFIED/INVALID):", classes="label")
        yield Input(
            value=self.current_filters.get("status", ""),
            placeholder="e.g., VERIFIED",
            id="filter_status"
        )

      with Vertical(classes="form_row"):
        yield Label("Integration ID:", classes="label")
        yield Input(
            value=str(self.current_filters.get("integration_id", "")),
            placeholder="e.g., 1",
            id="filter_integration_id"
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
    status = self.query_one("#filter_status", Input).value.strip().upper()
    integration_id_value = self.query_one("#filter_integration_id", Input).value.strip()
    active = self.query_one("#filter_active", Checkbox).value

    filters = {}
    if name:
      filters["name"] = name
    if status:
      filters["status"] = status
    if integration_id_value:
      if integration_id_value.isdigit():
        filters["integration_id"] = int(integration_id_value)
      else:
        log.warning("Integration ID filter must be numeric")
    if active:
      filters["active"] = True

    self.dismiss(filters)
