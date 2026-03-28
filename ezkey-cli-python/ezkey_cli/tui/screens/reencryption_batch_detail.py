"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

TUI Module: Re-encryption Batch Detail Screen
Description: Detail view for a re-encryption batch
"""

from textual.screen import Screen
from textual.widgets import Header, Footer, Static, Label
from textual.binding import Binding
from textual.containers import Vertical
import logging

log = logging.getLogger(__name__)


class ReencryptionBatchDetailScreen(Screen):
  """Re-encryption batch detail screen."""

  BINDINGS = [
      Binding("escape", "back", "Back"),
      Binding("h", "back", "Back"),
      Binding("u", "resume", "Resume"),
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

  def __init__(self, batch_id: int, *args, **kwargs):
    super().__init__(*args, **kwargs)
    self.batch_id = batch_id
    self.batch_data = None

  def compose(self):
    """Compose the detail screen."""
    yield Header(show_clock=True)
    with Vertical(id="content"):
      yield Label(f"Re-encryption Batch #{self.batch_id}", id="title")
      yield Static("Loading...", id="detail_content", classes="section")
    yield Footer()

  def on_mount(self) -> None:
    """Called when screen is mounted."""
    self._load_batch()

  def _load_batch(self) -> None:
    """Load batch details from API."""
    api_client = self.app.api_client
    if not api_client:
      self._show_error("No API client available")
      return

    batch = api_client.get_reencryption_batch(self.batch_id)
    if not batch:
      if api_client.last_auth_error and hasattr(self.app, "handle_auth_error"):
        self.app.handle_auth_error()
        return
      self._show_error(f"Batch {self.batch_id} not found or failed to load")
      return

    self.batch_data = batch
    self._render_detail()

  def _render_detail(self) -> None:
    """Render batch details."""
    data = self.batch_data or {}
    content = []

    content.append(f"Batch ID: {data.get('batchId', 'N/A')}")
    content.append(f"Status: {data.get('status', 'N/A')}")
    content.append(f"Target Table: {data.get('targetTable', 'N/A')}")
    content.append(f"Target Column: {data.get('targetColumn', 'N/A')}")
    content.append(f"Old Key ID: {data.get('oldKeyId', 'N/A')}")
    content.append(f"New Key ID: {data.get('newKeyId', 'N/A')}")
    content.append(f"Records Total: {data.get('recordsTotal', 'N/A')}")
    content.append(f"Records Done: {data.get('recordsDone', 'N/A')}")
    content.append(f"Records Failed: {data.get('recordsFailed', 'N/A')}")
    content.append(f"Records Skipped: {data.get('recordsSkipped', 'N/A')}")
    content.append(f"Progress %: {data.get('progressPct', 'N/A')}")
    content.append(f"Started At: {data.get('startedAt', 'N/A')}")
    content.append(f"Completed At: {data.get('completedAt', 'N/A')}")
    content.append(f"Error Message: {data.get('errorMessage', 'N/A')}")
    content.append(f"Retry Count: {data.get('retryCount', 'N/A')}")

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
    self._load_batch()

  def action_resume(self) -> None:
    """Resume this batch with confirmation."""
    from .confirmation_modal import ConfirmationModal

    def on_confirm(confirmed: bool) -> None:
      if confirmed:
        self._perform_resume()

    self.app.push_screen(
        ConfirmationModal(
            title="Resume Re-encryption Batch",
            message="Resume this batch now?\n"
            "Use only for FAILED or PAUSED batches."
        ),
        on_confirm
    )

  def _perform_resume(self) -> None:
    """Perform batch resume API call."""
    api_client = self.app.api_client
    if not api_client:
      self._show_error("No API client available")
      return

    try:
      response = api_client.resume_reencryption_batch(self.batch_id)
    except Exception as e:
      if api_client.last_auth_error and hasattr(self.app, "handle_auth_error"):
        self.app.handle_auth_error()
      self._show_error(str(e))
      return

    if not response:
      if api_client.last_auth_error and hasattr(self.app, "handle_auth_error"):
        self.app.handle_auth_error()
      self._show_error(f"Failed to resume batch {self.batch_id}")
      return

    message = response.get("message", "Batch resumed")
    detail_widget = self.query_one("#detail_content", Static)
    detail_widget.update(f"✓ {message}\nReturning to list...")
    self.app.set_timer(1.0, lambda: self.app.pop_screen())

  def action_quit(self) -> None:
    """Quit the application."""
    self.app.exit()
