"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

TUI Module: Encryption Key Detail Screen
Description: Detail view for a single encryption key
"""

from textual.screen import Screen
from textual.widgets import Header, Footer, Static, Label
from textual.binding import Binding
from textual.containers import Vertical
import logging

log = logging.getLogger(__name__)


class EncryptionKeyDetailScreen(Screen):
  """Encryption key detail screen."""

  BINDINGS = [
      Binding("escape", "back", "Back"),
      Binding("h", "back", "Back"),
      Binding("e", "reencrypt", "Reencrypt"),
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

  def __init__(self, key_id: int, *args, **kwargs):
    super().__init__(*args, **kwargs)
    self.key_id = key_id
    self.key_data = None

  def compose(self):
    """Compose the detail screen."""
    yield Header(show_clock=True)
    with Vertical(id="content"):
      yield Label(f"Encryption Key #{self.key_id}", id="title")
      yield Static("Loading...", id="detail_content", classes="section")
    yield Footer()

  def on_mount(self) -> None:
    """Called when screen is mounted."""
    self._load_key()

  def _load_key(self) -> None:
    """Load encryption key details from API."""
    api_client = self.app.api_client
    if not api_client:
      self._show_error("No API client available")
      return

    response = api_client.get_encryption_key(self.key_id)
    if not response:
      if api_client.last_auth_error and hasattr(self.app, "handle_auth_error"):
        self.app.handle_auth_error()
        return
      self._show_error(f"Failed to load encryption key {self.key_id}")
      return

    self.key_data = response
    self._render_detail()

  def _render_detail(self) -> None:
    """Render encryption key details."""
    data = self.key_data or {}
    content = []

    content.append(f"Key ID: {data.get('keyId', 'N/A')}")
    content.append(f"Status: {data.get('keyStatus', 'N/A')}")
    content.append(f"Algorithm: {data.get('algorithm', 'N/A')}")
    content.append(f"Introduced At: {data.get('introducedAt', 'N/A')}")
    content.append(f"Primary At: {data.get('promotedPrimaryAt', 'N/A')}")
    content.append(f"Disabled At: {data.get('disabledAt', 'N/A')}")
    content.append(f"Records Encrypted: {data.get('recordsEncrypted', 'N/A')}")
    content.append(f"Records Reencrypted: {data.get('recordsReencrypted', 'N/A')}")
    content.append(f"Created By: {data.get('createdBy', 'N/A')}")
    content.append(f"Notes: {data.get('notes', 'N/A')}")

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

  def action_reencrypt(self) -> None:
    """Trigger re-encryption for this key with confirmation."""
    from .confirmation_modal import ConfirmationModal

    def on_confirm(confirmed: bool) -> None:
      if confirmed:
        self._perform_reencrypt()

    self.app.push_screen(
        ConfirmationModal(
            title="Re-encrypt Key",
            message="Trigger re-encryption for this key?\n"
            "Key must not be PRIMARY."
        ),
        on_confirm
    )

  def _perform_reencrypt(self) -> None:
    """Perform key-specific re-encryption API call."""
    api_client = self.app.api_client
    if not api_client:
      self._show_error("No API client available")
      return

    try:
      response = api_client.trigger_reencryption_for_key(self.key_id)
    except Exception as e:
      if api_client.last_auth_error and hasattr(self.app, "handle_auth_error"):
        self.app.handle_auth_error()
      self._show_error(str(e))
      return

    if not response:
      if api_client.last_auth_error and hasattr(self.app, "handle_auth_error"):
        self.app.handle_auth_error()
      self._show_error(f"Failed to re-encrypt key {self.key_id}")
      return

    message = response.get("message", "Re-encryption triggered")
    batches_created = response.get("batchesCreated")
    batches_processed = response.get("batchesProcessed")
    batches_failed = response.get("batchesFailed")

    detail_widget = self.query_one("#detail_content", Static)
    detail_widget.update(
        "✓ "
        f"{message}\n"
        f"Batches created: {batches_created}\n"
        f"Batches processed: {batches_processed}\n"
        f"Batches failed: {batches_failed}"
    )

  def action_quit(self) -> None:
    """Quit the application."""
    self.app.exit()
