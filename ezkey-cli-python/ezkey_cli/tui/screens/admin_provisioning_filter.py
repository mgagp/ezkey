"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

TUI Module: Admin Provisioning Filter Modal
Description: Modal for filtering and sorting admins list
"""

from textual.screen import ModalScreen
from textual.widgets import Button, Input, Label
from textual.containers import Vertical, Horizontal, Container
import logging

log = logging.getLogger(__name__)


class AdminProvisioningFilterModal(ModalScreen):
  """Modal screen for filtering admins list."""

  CSS = """
  AdminProvisioningFilterModal {
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
      width: 22;
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
      yield Label("Filter Admins", id="title")

      with Vertical(classes="form_row"):
        yield Label("Username contains:", classes="label")
        yield Input(
            value=self.current_filters.get("username", ""),
            placeholder="e.g., admin",
            id="filter_username"
        )

      with Vertical(classes="form_row"):
        yield Label("Admin type:", classes="label")
        yield Input(
            value=self.current_filters.get("admin_type", ""),
            placeholder="GLOBAL_ADMIN / TENANT_ADMIN",
            id="filter_admin_type"
        )

      with Vertical(classes="form_row"):
        yield Label("Tenant ID:", classes="label")
        yield Input(
            value=str(self.current_filters.get("tenant_id", "") or ""),
            placeholder="e.g., 1",
            id="filter_tenant_id"
        )

      with Vertical(classes="form_row"):
        yield Label("Active (true/false):", classes="label")
        yield Input(
            value=self.current_filters.get("active", ""),
            placeholder="true or false",
            id="filter_active"
        )

      with Vertical(classes="form_row"):
        yield Label("Created after (ISO):", classes="label")
        yield Input(
            value=self.current_filters.get("created_after", ""),
            placeholder="2025-01-01T00:00:00Z",
            id="filter_created_after"
        )

      with Vertical(classes="form_row"):
        yield Label("Created before (ISO):", classes="label")
        yield Input(
            value=self.current_filters.get("created_before", ""),
            placeholder="2025-12-31T23:59:59Z",
            id="filter_created_before"
        )

      with Vertical(classes="form_row"):
        yield Label("Sort (field,dir):", classes="label")
        yield Input(
            value=self.current_filters.get("sort", "createdAt,DESC"),
            placeholder="e.g., username,asc",
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
    filters = {}
    sort_value = self.query_one("#filter_sort", Input).value.strip()
    username = self.query_one("#filter_username", Input).value.strip()
    admin_type = self.query_one("#filter_admin_type", Input).value.strip()
    tenant_id = self.query_one("#filter_tenant_id", Input).value.strip()
    active = self.query_one("#filter_active", Input).value.strip()
    created_after = self.query_one("#filter_created_after", Input).value.strip()
    created_before = self.query_one("#filter_created_before", Input).value.strip()

    if sort_value:
      filters["sort"] = sort_value
    if username:
      filters["username"] = username
    if admin_type:
      filters["admin_type"] = admin_type
    if tenant_id:
      filters["tenant_id"] = tenant_id
    if active:
      filters["active"] = active
    if created_after:
      filters["created_after"] = created_after
    if created_before:
      filters["created_before"] = created_before

    self.dismiss(filters)
