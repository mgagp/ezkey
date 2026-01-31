"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

TUI Module: Enrollment Detail Screen
Description: Detail view for a single enrollment
"""

from textual.screen import Screen
from textual.widgets import Header, Footer, Static, Label
from textual.binding import Binding
from textual.containers import Vertical
import logging

log = logging.getLogger(__name__)


class EnrollmentDetailScreen(Screen):
  """Enrollment detail screen with full information."""

  BINDINGS = [
      Binding("escape", "back", "Back"),
      Binding("h", "back", "Back"),
      Binding("d", "delete", "Delete"),
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

  def __init__(self, enrollment_id: int, *args, **kwargs):
    super().__init__(*args, **kwargs)
    self.enrollment_id = enrollment_id
    self.enrollment_data = None

  def compose(self):
    """Compose the detail screen."""
    yield Header(show_clock=True)
    with Vertical(id="content"):
      yield Label(f"Enrollment #{self.enrollment_id}", id="title")
      yield Static("Loading...", id="detail_content", classes="section")
    yield Footer()

  def on_mount(self) -> None:
    """Called when screen is mounted."""
    log.debug(f"EnrollmentDetailScreen mounted for ID {self.enrollment_id}")
    self._load_enrollment()

  def _load_enrollment(self) -> None:
    """Load enrollment details from API."""
    api_client = self.app.api_client
    if not api_client:
      self._show_error("No API client available")
      return

    url = f"/api/v1/enrollments/{self.enrollment_id}"
    response = api_client._get(url)

    if not response:
      self._show_error(f"Failed to load enrollment {self.enrollment_id}")
      return

    self.enrollment_data = response
    self._render_detail()

  def _render_detail(self) -> None:
    """Render enrollment details."""
    if not self.enrollment_data:
      return

    data = self.enrollment_data
    content = []

    content.append(f"Enrollment ID: {data.get('enrollmentId', 'N/A')}")
    content.append(f"Integration ID: {data.get('integrationId', 'N/A')}")
    content.append(f"Name: {data.get('enrollmentName', 'N/A')}")
    content.append(f"Status: {data.get('enrollmentStatus', 'N/A')}")
    content.append(f"Active: {'✓' if data.get('enrollmentActive') else '✗'}")
    content.append(f"Challenge: {data.get('enrollmentChallenge', 'N/A')}")
    content.append(f"Proof Token: {data.get('enrollmentProofToken', 'N/A')}")
    content.append(
        f"Auth Challenge Required: {data.get('authAttemptChallengeRequired', 'N/A')}"
    )
    content.append("")
    content.append("Integration Public Key:")
    content.append(str(data.get('integrationPublicKey') or 'N/A'))
    content.append("")
    content.append("Device Public Key:")
    content.append(str(data.get('devicePublicKey') or 'N/A'))

    detail_widget = self.query_one("#detail_content", Static)
    detail_widget.update("\n".join(content))

  def _show_error(self, message: str) -> None:
    """Show error message."""
    detail_widget = self.query_one("#detail_content", Static)
    detail_widget.update(f"❌ Error: {message}")

  def action_back(self) -> None:
    """Go back to list."""
    log.debug("Returning to enrollments list")
    self.app.pop_screen()

  def action_refresh(self) -> None:
    """Refresh enrollment data."""
    log.debug("Refreshing enrollment detail")
    self._load_enrollment()

  def action_delete(self) -> None:
    """Delete enrollment with confirmation."""
    log.debug(f"Deleting enrollment {self.enrollment_id}")
    from .confirmation_modal import ConfirmationModal

    name = ""
    if self.enrollment_data:
      name = self.enrollment_data.get("enrollmentName") or "Unknown"

    def on_confirm(confirmed: bool) -> None:
      if confirmed:
        self._perform_delete()

    self.app.push_screen(
        ConfirmationModal(
            title="Delete Enrollment",
            message=f"Are you sure you want to delete '{name}'?\nThis action cannot be undone."
        ),
        on_confirm
    )

  def _perform_delete(self) -> None:
    """Perform the actual DELETE API call."""
    api_client = self.app.api_client
    if not api_client:
      self._show_error("No API client available")
      return

    url = f"/api/v1/enrollments/{self.enrollment_id}"
    success = api_client._delete(url)

    if success:
      log.info(f"Enrollment {self.enrollment_id} deleted successfully")
      detail_widget = self.query_one("#detail_content", Static)
      detail_widget.update("✓ Enrollment deleted. Returning to list...")
      self.app.set_timer(1.0, lambda: self.app.pop_screen())
    else:
      self._show_error(f"Failed to delete enrollment {self.enrollment_id}")

  def action_quit(self) -> None:
    """Quit the application."""
    self.app.exit()
