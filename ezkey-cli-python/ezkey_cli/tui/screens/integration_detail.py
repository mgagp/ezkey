"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

TUI Module: Integration Detail Screen
Description: Detail view for a single integration
"""

from textual.screen import Screen
from textual.widgets import Header, Footer, Static, Label
from textual.binding import Binding
from textual.containers import Vertical, Horizontal
import logging

log = logging.getLogger(__name__)


class IntegrationDetailScreen(Screen):
  """Integration detail screen with full information."""

  BINDINGS = [
      Binding("escape", "back", "Back"),
      Binding("h", "back", "Back"),
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

  def __init__(self, integration_id: int, *args, **kwargs):
    super().__init__(*args, **kwargs)
    self.integration_id = integration_id
    self.integration_data = None

  def compose(self):
    """Compose the detail screen."""
    yield Header(show_clock=True)
    with Vertical(id="content"):
      yield Label(f"Integration #{self.integration_id}", id="title")
      yield Static("Loading...", id="detail_content", classes="section")
    yield Footer()

  def on_mount(self) -> None:
    """Called when screen is mounted."""
    log.debug(f"IntegrationDetailScreen mounted for ID {self.integration_id}")
    self._load_integration()

  def _load_integration(self) -> None:
    """Load integration details from API."""
    api_client = self.app.api_client
    if not api_client:
      self._show_error("No API client available")
      return

    # Call GET /api/v1/integrations/{id}
    url = f"/api/v1/integrations/{self.integration_id}"
    response = api_client._get(url)

    if not response:
      self._show_error(f"Failed to load integration {self.integration_id}")
      return

    self.integration_data = response
    self._render_detail()

  def _render_detail(self) -> None:
    """Render integration details."""
    if not self.integration_data:
      return

    data = self.integration_data
    content = []

    # Basic info
    content.append(f"ID: {data.get('id', 'N/A')}")
    content.append(f"Code: {data.get('code', 'N/A')}")
    content.append(f"Tenant ID: {data.get('tenantId', 'N/A')}")
    content.append(f"Active: {'✓' if data.get('active') else '✗'}")
    content.append(f"Created: {data.get('createdAt', 'N/A')}")
    content.append("")
    content.append(f"Name: {data.get('name') or 'N/A'}")
    content.append(f"Description: {data.get('description') or 'N/A'}")
    content.append("")

    detail_widget = self.query_one("#detail_content", Static)
    detail_widget.update("\n".join(content))

  def _show_error(self, message: str) -> None:
    """Show error message."""
    detail_widget = self.query_one("#detail_content", Static)
    detail_widget.update(f"❌ Error: {message}")

  def action_back(self) -> None:
    """Go back to list."""
    log.debug("Returning to integrations list")
    self.app.pop_screen()

  def action_refresh(self) -> None:
    """Refresh integration data."""
    log.debug("Refreshing integration detail")
    self._load_integration()

  def action_quit(self) -> None:
    """Quit the application."""
    self.app.exit()
