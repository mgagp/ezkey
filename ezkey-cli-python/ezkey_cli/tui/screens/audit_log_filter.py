"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

TUI Module: Audit Log Filter Modal
Description: Modal for filtering audit logs
"""

from textual.screen import ModalScreen
from textual.widgets import Button, Input, Label
from textual.containers import Vertical, Horizontal, Container
import logging

log = logging.getLogger(__name__)


class AuditLogFilterModal(ModalScreen):
  """Modal screen for filtering audit logs."""

  CSS = """
  AuditLogFilterModal {
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
  }

  .label {
      width: 24;
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
      yield Label("Filter Audit Logs", id="title")

      with Vertical(classes="form_row"):
        yield Label("Event Type:", classes="label")
        yield Input(
            value=self.current_filters.get("event_type", ""),
            placeholder="e.g., AUTH_ATTEMPT_CREATED",
            id="filter_event_type"
        )

      with Vertical(classes="form_row"):
        yield Label("Event Status:", classes="label")
        yield Input(
            value=self.current_filters.get("event_status", ""),
            placeholder="e.g., SUCCESS",
            id="filter_event_status"
        )

      with Vertical(classes="form_row"):
        yield Label("API Name:", classes="label")
        yield Input(
            value=self.current_filters.get("api_name", ""),
            placeholder="e.g., ADMIN",
            id="filter_api_name"
        )

      with Vertical(classes="form_row"):
        yield Label("Enrollment ID:", classes="label")
        yield Input(
            value=str(self.current_filters.get("enrollment_id", "")),
            placeholder="e.g., 123",
            id="filter_enrollment_id"
        )

      with Vertical(classes="form_row"):
        yield Label("Admin ID:", classes="label")
        yield Input(
            value=str(self.current_filters.get("admin_id", "")),
            placeholder="e.g., 1",
            id="filter_admin_id"
        )

      with Vertical(classes="form_row"):
        yield Label("Sort (field,dir):", classes="label")
        yield Input(
            value=self.current_filters.get("sort", "createdAt,desc"),
            placeholder="e.g., createdAt,desc",
            id="filter_sort"
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
    event_type = self.query_one("#filter_event_type", Input).value.strip().upper()
    event_status = self.query_one("#filter_event_status", Input).value.strip().upper()
    api_name = self.query_one("#filter_api_name", Input).value.strip().upper()
    enrollment_id_value = self.query_one("#filter_enrollment_id", Input).value.strip()
    admin_id_value = self.query_one("#filter_admin_id", Input).value.strip()
    sort_value = self.query_one("#filter_sort", Input).value.strip()

    filters = {}
    if event_type:
      filters["event_type"] = event_type
    if event_status:
      filters["event_status"] = event_status
    if api_name:
      filters["api_name"] = api_name
    if enrollment_id_value:
      if enrollment_id_value.isdigit():
        filters["enrollment_id"] = int(enrollment_id_value)
      else:
        log.warning("Enrollment ID filter must be numeric")
    if admin_id_value:
      if admin_id_value.isdigit():
        filters["admin_id"] = int(admin_id_value)
      else:
        log.warning("Admin ID filter must be numeric")
    if sort_value:
      filters["sort"] = sort_value

    self.dismiss(filters)
