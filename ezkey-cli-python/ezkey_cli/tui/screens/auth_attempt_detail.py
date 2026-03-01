"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

TUI Module: Auth Attempt Detail Screen
Description: Detail view for a single auth attempt
"""

from textual.screen import Screen
from textual.widgets import Header, Footer, Static, Label
from textual.binding import Binding
from textual.containers import Vertical
import logging

log = logging.getLogger(__name__)


class AuthAttemptDetailScreen(Screen):
  """Auth attempt detail screen."""

  BINDINGS = [
      Binding("escape", "back", "Back"),
      Binding("h", "back", "Back"),
      Binding("c", "cancel", "Cancel"),
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

  def __init__(self, auth_attempt_id: int, *args, **kwargs):
    super().__init__(*args, **kwargs)
    self.auth_attempt_id = auth_attempt_id
    self.auth_attempt_data = None

  def compose(self):
    """Compose the detail screen."""
    yield Header(show_clock=True)
    with Vertical(id="content"):
      yield Label(f"Auth Attempt #{self.auth_attempt_id}", id="title")
      yield Static("Loading...", id="detail_content", classes="section")
    yield Footer()

  def on_mount(self) -> None:
    """Called when screen is mounted."""
    log.debug(f"AuthAttemptDetailScreen mounted for ID {self.auth_attempt_id}")
    self._load_auth_attempt()

  def _load_auth_attempt(self) -> None:
    """Load auth attempt details from API."""
    api_client = self.app.api_client
    if not api_client:
      self._show_error("No API client available")
      return

    response = api_client.get_auth_attempt_by_id(self.auth_attempt_id)

    if not response:
      if api_client.last_auth_error and hasattr(self.app, "handle_auth_error"):
        self.app.handle_auth_error()
        return
      self._show_error(f"Failed to load auth attempt {self.auth_attempt_id}")
      return

    self.auth_attempt_data = response
    self._render_detail()

  def _render_detail(self) -> None:
    """Render auth attempt details."""
    if not self.auth_attempt_data:
      return

    data = self.auth_attempt_data
    content = []

    content.append(f"Auth Attempt ID: {data.get('authAttemptId', 'N/A')}")
    content.append(f"Enrollment ID: {data.get('enrollmentId', 'N/A')}")
    content.append(f"Status: {data.get('authAttemptStatus', 'N/A')}")
    content.append(f"Challenge: {data.get('authAttemptChallenge', 'N/A')}")
    content.append(f"Proof Token: {data.get('authAttemptProofToken', 'N/A')}")
    content.append(f"Created At: {data.get('createdAt', 'N/A')}")
    content.append(f"Expires At: {data.get('expiresAt', 'N/A')}")

    detail_widget = self.query_one("#detail_content", Static)
    detail_widget.update("\n".join(content))

  def _show_error(self, message: str) -> None:
    """Show error message."""
    detail_widget = self.query_one("#detail_content", Static)
    detail_widget.update(f"❌ Error: {message}")

  def action_back(self) -> None:
    """Go back to list."""
    log.debug("Returning to auth attempts list")
    self.app.pop_screen()

  def action_refresh(self) -> None:
    """Refresh auth attempt data."""
    log.debug("Refreshing auth attempt detail")
    self._load_auth_attempt()

  def action_cancel(self) -> None:
    """Cancel auth attempt with confirmation."""
    log.debug(f"Cancelling auth attempt {self.auth_attempt_id}")
    from .confirmation_modal import ConfirmationModal

    def on_confirm(confirmed: bool) -> None:
      if confirmed:
        self._perform_cancel()

    self.app.push_screen(
        ConfirmationModal(
            title="Cancel Auth Attempt",
            message="Are you sure you want to cancel this auth attempt?\n"
                    "Only PENDING or READ attempts can be cancelled."
        ),
        on_confirm
    )

  def _perform_cancel(self) -> None:
    """Perform cancel API call."""
    api_client = self.app.api_client
    if not api_client:
      self._show_error("No API client available")
      return

    response = api_client.cancel_auth_attempt(self.auth_attempt_id)
    if response:
      log.info(f"Auth attempt {self.auth_attempt_id} cancelled successfully")
      detail_widget = self.query_one("#detail_content", Static)
      detail_widget.update("✓ Auth attempt cancelled. Returning to list...")
      self.app.set_timer(1.0, lambda: self.app.pop_screen())
    else:
      if api_client.last_auth_error and hasattr(self.app, "handle_auth_error"):
        self.app.handle_auth_error()
      self._show_error(f"Failed to cancel auth attempt {self.auth_attempt_id}")

  def action_quit(self) -> None:
    """Quit the application."""
    self.app.exit()
