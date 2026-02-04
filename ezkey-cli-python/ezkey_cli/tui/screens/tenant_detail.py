"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

TUI Module: Tenant Detail Screen
Description: Detail view for a single tenant
"""

from textual.screen import Screen
from textual.widgets import Header, Footer, Static, Label
from textual.binding import Binding
from textual.containers import Vertical
import logging

log = logging.getLogger(__name__)


class TenantDetailScreen(Screen):
  """Tenant detail screen."""

  BINDINGS = [
      Binding("escape", "back", "Back"),
      Binding("h", "back", "Back"),
      Binding("d", "deactivate", "Deactivate"),
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

  def __init__(self, tenant_id: int, *args, **kwargs):
    super().__init__(*args, **kwargs)
    self.tenant_id = tenant_id
    self.tenant_data = None

  def compose(self):
    """Compose the detail screen."""
    yield Header(show_clock=True)
    with Vertical(id="content"):
      yield Label(f"Tenant #{self.tenant_id}", id="title")
      yield Static("Loading...", id="detail_content", classes="section")
    yield Footer()

  def on_mount(self) -> None:
    """Called when screen is mounted."""
    self._load_tenant()

  def _load_tenant(self) -> None:
    """Load tenant details from API."""
    api_client = self.app.api_client
    if not api_client:
      self._show_error("No API client available")
      return

    response = api_client.get_tenant(self.tenant_id)
    if not response:
      self._show_error(f"Failed to load tenant {self.tenant_id}")
      return

    self.tenant_data = response
    self._render_detail()

  def _render_detail(self) -> None:
    if not self.tenant_data:
      return

    data = self.tenant_data
    content = []
    content.append(f"Tenant ID: {data.get('tenantId', 'N/A')}")
    content.append(f"Name: {data.get('tenantName', 'N/A')}")
    content.append(f"Description: {data.get('tenantDescription', 'N/A')}")
    content.append(f"Active: {'✓' if data.get('active') else '✗'}")
    content.append(f"Created At: {data.get('createdAt', 'N/A')}")

    detail_widget = self.query_one("#detail_content", Static)
    detail_widget.update("\n".join(content))

  def _show_error(self, message: str) -> None:
    detail_widget = self.query_one("#detail_content", Static)
    detail_widget.update(f"❌ Error: {message}")

  def action_back(self) -> None:
    self.app.pop_screen()

  def action_refresh(self) -> None:
    self._load_tenant()

  def action_deactivate(self) -> None:
    """Deactivate tenant with confirmation."""
    from .confirmation_modal import ConfirmationModal

    name = self.tenant_data.get("tenantName") if self.tenant_data else ""
    message = (
        f"Deactivate tenant '{name}'?\n"
        "This will revoke active tokens for this tenant."
    )

    def on_confirm(confirmed: bool) -> None:
      if confirmed:
        self._perform_deactivate()

    self.app.push_screen(
        ConfirmationModal(
            title="Deactivate Tenant",
            message=message
        ),
        on_confirm
    )

  def _perform_deactivate(self) -> None:
    api_client = self.app.api_client
    if not api_client:
      self._show_error("No API client available")
      return

    success = api_client.deactivate_tenant(self.tenant_id)
    if success:
      if self.tenant_data:
        self.tenant_data["active"] = False
      detail_widget = self.query_one("#detail_content", Static)
      detail_widget.update("✓ Tenant deactivated")
      self.app.set_timer(1.0, self._load_tenant)
    else:
      self._show_error("Failed to deactivate tenant")

  def action_quit(self) -> None:
    self.app.exit()
