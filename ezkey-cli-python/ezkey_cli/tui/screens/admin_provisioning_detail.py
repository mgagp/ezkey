"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

TUI Module: Admin Provisioning Detail Screen
Description: Detail view for a single admin
"""

from textual.screen import Screen
from textual.widgets import Header, Footer, Static, Label
from textual.binding import Binding
from textual.containers import Vertical
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
    yield Footer()

  def on_mount(self) -> None:
    """Called when screen is mounted."""
    self._render_detail()

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

  def action_quit(self) -> None:
    """Quit the application."""
    self.app.exit()
