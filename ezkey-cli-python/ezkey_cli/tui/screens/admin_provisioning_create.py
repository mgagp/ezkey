"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

TUI Module: Admin Provisioning Create Modal
Description: Modal form for creating new administrators (GlobalAdmin or TenantAdmin)
"""

from textual.screen import ModalScreen
from textual.widgets import Button, Input, Label, RadioButton, RadioSet
from textual.binding import Binding
from textual.containers import Vertical, Horizontal, Container
from textual.validation import Length
from typing import Optional, Dict, Any
import logging

log = logging.getLogger(__name__)


class CreateAdminModal(ModalScreen):
  """Modal screen for creating a new administrator."""

  BINDINGS = [
      Binding("escape", "cancel", "Cancel"),
  ]

  CSS = """
  CreateAdminModal {
      align: center middle;
  }

  #modal_container {
      width: 80;
      height: auto;
      border: thick $primary;
      background: $surface;
      padding: 1 2;
  }

  #title {
      margin-bottom: 1;
      text-align: center;
  }

  .form_row {
      height: auto;
      margin: 0 0;
      width: 1fr;
  }

  .label {
      width: 1fr;
      color: $text-muted;
  }

  Input {
      width: 1fr;
  }

  #admin_type_section {
      padding: 0 0;
      margin: 0 0;
      height: auto;
  }

  RadioSet {
      height: auto;
      margin: 0 0 1 0;
      padding: 0 0;
  }

  RadioButton {
      margin: 0 0;
      padding: 0 0;
      height: 1;
  }

  #tenant_section {
      padding: 0;
      margin: 0 0;
      border: none;
      display: none;
      height: auto;
  }

  #tenant_section.visible {
      display: block;
  }

  #button_row {
      height: auto;
      margin-top: 1;
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

  #info_label {
      color: $warning;
      margin: 1 0;
      display: none;
      width: 1fr;
  }
  """

  def __init__(self, *args, **kwargs):
    super().__init__(*args, **kwargs)
    self.form_data = {}
    self._error_message = ""
    self._admin_type = "GLOBAL_ADMIN"  # Default

  def compose(self):
    """Compose the modal."""
    with Container(id="modal_container"):
      yield Label("Create New Administrator", id="title")

      # Admin Type Selection
      with Vertical(id="admin_type_section"):
        yield Label("Administrator Type:", classes="label")
        with RadioSet(id="admin_type_radio"):
          yield RadioButton("Global Admin (system-wide access)", id="type_global", value=True)
          yield RadioButton("Tenant Admin (tenant-scoped access)", id="type_tenant", value=False)

      # Common fields
      with Vertical(classes="form_row"):
        yield Label("Username:", classes="label")
        yield Input(
            placeholder="e.g., john.doe",
            id="username",
            validators=[Length(3, 50)]
        )

      with Vertical(classes="form_row"):
        yield Label("Email:", classes="label")
        yield Input(
            placeholder="e.g., john.doe@example.com",
            id="email",
            validators=[Length(1, 255)]
        )

      with Vertical(classes="form_row"):
        yield Label("First Name:", classes="label")
        yield Input(
            placeholder="e.g., John",
            id="first_name",
            validators=[Length(1, 100)]
        )

      with Vertical(classes="form_row"):
        yield Label("Last Name:", classes="label")
        yield Input(
            placeholder="e.g., Doe",
            id="last_name",
            validators=[Length(1, 100)]
        )

      # Tenant selection (TenantAdmin only)
      with Vertical(id="tenant_section"):
        yield Label("Tenant ID:", classes="label")
        yield Input(
            placeholder="e.g., 2",
            id="tenant_id",
            validators=[Length(1, 20)]
        )

      yield Label("", id="error_label")
      yield Label("", id="info_label")

      with Horizontal(id="button_row"):
        yield Button("Create", variant="primary", id="create_btn")
        yield Button("Cancel", variant="default", id="cancel_btn")

  def on_mount(self) -> None:
    """Initialize modal when mounted."""
    self._update_tenant_visibility()

  def on_radio_set_changed(self, event: RadioSet.Changed) -> None:
    """Handle admin type selection change."""
    pressed_id = event.pressed.id

    if pressed_id == "type_global":
      self._admin_type = "GLOBAL_ADMIN"
    elif pressed_id == "type_tenant":
      self._admin_type = "TENANT_ADMIN"

    self._update_tenant_visibility()

  def _update_tenant_visibility(self) -> None:
    """Show/hide tenant selection based on admin type."""
    tenant_section = self.query_one("#tenant_section")

    if self._admin_type == "TENANT_ADMIN":
      tenant_section.add_class("visible")
    else:
      tenant_section.remove_class("visible")

  def on_button_pressed(self, event: Button.Pressed) -> None:
    """Handle button press."""
    if event.button.id == "create_btn":
      self._create_admin()
    elif event.button.id == "cancel_btn":
      self.action_cancel()

  def _create_admin(self) -> None:
    """Create administrator via API."""
    # Get form values
    username = self.query_one("#username", Input).value.strip()
    email = self.query_one("#email", Input).value.strip()
    first_name = self.query_one("#first_name", Input).value.strip()
    last_name = self.query_one("#last_name", Input).value.strip()

    self._show_error("")
    self._show_info("")

    # Validate required fields
    if not username:
      self._show_error("Username is required")
      return

    if not email:
      self._show_error("Email is required")
      return

    if not first_name:
      self._show_error("First Name is required")
      return

    if not last_name:
      self._show_error("Last Name is required")
      return

    # Build payload
    payload = {
        "username": username,
        "email": email,
        "firstName": first_name,
        "lastName": last_name,
    }

    # Get API client
    api_client = self.app.api_client
    if not api_client:
      self._show_error("No API client available")
      return

    try:
      # Call appropriate API endpoint
      if self._admin_type == "GLOBAL_ADMIN":
        log.info("Creating GlobalAdmin")
        response = api_client.create_global_admin(payload)
      else:
        # Get tenant ID from input
        tenant_input = self.query_one("#tenant_id", Input)
        tenant_id_str = tenant_input.value.strip()

        if not tenant_id_str:
          self._show_error("Tenant ID is required for Tenant Admin")
          return

        try:
          tenant_id = int(tenant_id_str)
        except ValueError:
          self._show_error("Tenant ID must be a number")
          return

        payload["tenantId"] = tenant_id
        log.info(f"Creating TenantAdmin for tenant {tenant_id}")
        response = api_client.create_tenant_admin(payload)

      if response:
        admin_id = response.get("adminId")
        enrollment_id = response.get("enrollmentId")
        log.info(f"Admin created: {admin_id} (enrollment: {enrollment_id})")

        # Retrieve onboarding credentials
        self._fetch_and_display_credentials(admin_id, enrollment_id)
      else:
        self._show_error("Failed to create administrator (no response from API)")

    except Exception as e:
      error_msg = str(e)
      log.error(f"Error creating admin: {e}")
      self._show_error(error_msg)

  def _fetch_and_display_credentials(self, admin_id: int, enrollment_id: int) -> None:
    """Fetch onboarding credentials and dismiss with result."""
    api_client = self.app.api_client
    if not api_client:
      self._show_error("No API client available for fetching credentials")
      return

    try:
      # Fetch onboarding credentials
      credentials = api_client.get_admin_onboarding(admin_id)

      if credentials:
        log.info(f"Credentials retrieved for admin {admin_id}")

        # Dismiss with credentials data
        result = {
            "admin_id": admin_id,
            "enrollment_id": enrollment_id,
            "credentials": credentials,
            "admin_type": self._admin_type,
        }
        self.dismiss(result)
      else:
        self._show_error("Failed to retrieve onboarding credentials")

    except Exception as e:
      log.error(f"Error fetching credentials: {e}")
      self._show_error(f"Failed to fetch credentials: {str(e)}")

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

  def _show_info(self, message: str) -> None:
    """Show info/warning message in modal."""
    info_label = self.query_one("#info_label", Label)
    if message:
      info_label.update(f"ℹ️ {message}")
      info_label.display = True
    else:
      info_label.update("")
      info_label.display = False

  def action_cancel(self) -> None:
    """Cancel and close modal."""
    log.debug("Create admin cancelled")
    self.dismiss(False)
