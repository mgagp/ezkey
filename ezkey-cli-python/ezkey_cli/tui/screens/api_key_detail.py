"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

TUI Module: API Key Detail Screen
Description: Detail view for a single API key
"""

from textual.screen import Screen
from textual.widgets import Header, Footer, Static, Label
from textual.binding import Binding
from textual.containers import Vertical
import logging

log = logging.getLogger(__name__)


class ApiKeyDetailScreen(Screen):
  """API key detail screen."""

  BINDINGS = [
      Binding("escape", "back", "Back"),
      Binding("h", "back", "Back"),
      Binding("d", "revoke", "Revoke"),
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

  def __init__(self, api_key_id: int, *args, **kwargs):
    super().__init__(*args, **kwargs)
    self.api_key_id = api_key_id
    self.api_key_data = None

  def compose(self):
    """Compose the detail screen."""
    yield Header(show_clock=True)
    with Vertical(id="content"):
      yield Label(f"API Key #{self.api_key_id}", id="title")
      yield Static("Loading...", id="detail_content", classes="section")
    yield Footer()

  def on_mount(self) -> None:
    """Called when screen is mounted."""
    self._load_key()

  def _load_key(self) -> None:
    """Load API key details from API."""
    api_client = self.app.api_client
    if not api_client:
      self._show_error("No API client available")
      return

    response = api_client.get_api_key(self.api_key_id)
    if not response:
      if api_client.last_auth_error and hasattr(self.app, "handle_auth_error"):
        self.app.handle_auth_error()
        return
      self._show_error(f"Failed to load API key {self.api_key_id}")
      return

    self.api_key_data = response
    self._render_detail()

  def _render_detail(self) -> None:
    """Render API key details."""
    data = self.api_key_data or {}
    content = []

    content.append(f"API Key ID: {data.get('apiKeyId', 'N/A')}")
    content.append(f"Integration ID: {data.get('integrationId', 'N/A')}")
    content.append(f"Integration Key: {data.get('integrationKey', 'N/A')}")
    content.append(f"Description: {data.get('description', 'N/A')}")
    content.append(f"Active: {data.get('active', 'N/A')}")
    content.append(f"Created At: {data.get('createdAt', 'N/A')}")
    content.append(f"Expires At: {data.get('expiresAt', 'N/A')}")
    content.append(f"Last Used At: {data.get('lastUsedAt', 'N/A')}")
    content.append(f"IP Whitelist: {data.get('ipWhitelist', 'N/A')}")
    content.append(f"Revoked At: {data.get('revokedAt', 'N/A')}")
    content.append(f"Revoked By: {data.get('revokedByUsername', 'N/A')}")

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
    self._load_key()

  def action_revoke(self) -> None:
    """Revoke API key with confirmation."""
    from .confirmation_modal import ConfirmationModal

    def on_confirm(confirmed: bool) -> None:
      if confirmed:
        self._perform_revoke()

    self.app.push_screen(
        ConfirmationModal(
            title="Revoke API Key",
            message="Are you sure you want to revoke this API key?\n"
            "This action cannot be undone."
        ),
        on_confirm
    )

  def _perform_revoke(self) -> None:
    """Perform revoke API call."""
    api_client = self.app.api_client
    if not api_client:
      self._show_error("No API client available")
      return

    success = api_client.revoke_api_key(self.api_key_id)
    if success:
      detail_widget = self.query_one("#detail_content", Static)
      detail_widget.update("✓ API key revoked. Returning to list...")
      self.app.set_timer(1.0, lambda: self.app.pop_screen())
    else:
      if api_client.last_auth_error and hasattr(self.app, "handle_auth_error"):
        self.app.handle_auth_error()
      self._show_error(f"Failed to revoke API key {self.api_key_id}")

  def action_quit(self) -> None:
    """Quit the application."""
    self.app.exit()
