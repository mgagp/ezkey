"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

TUI Module: Enrollments Screen
Description: Screen for managing enrollments
"""

from textual.screen import Screen
from textual.widgets import Header, Footer, DataTable, Label
from textual.binding import Binding
from textual.containers import Vertical
import logging

log = logging.getLogger(__name__)


class EnrollmentsScreen(Screen):
  """Enrollments management screen."""

  BINDINGS = [
      Binding("h", "show_home", "Home"),
      Binding("c", "create", "Create"),
      Binding("f", "filter", "Filter"),
      Binding("r", "refresh", "Refresh"),
      Binding("n", "next_page", "Next"),
      Binding("p", "prev_page", "Prev"),
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

  #table {
      height: 1fr;
  }

  #page_info {
      height: auto;
      margin: 1 0;
  }
  """

  def __init__(self, *args, **kwargs):
    super().__init__(*args, **kwargs)
    self.current_page = 0
    self.page_size = 25
    self.total_pages = 0
    self.total_elements = 0
    self.filters = {}

  def compose(self):
    """Compose the enrollments screen."""
    yield Header(show_clock=True)
    with Vertical(id="content"):
      yield Label("Enrollments", id="title")
      yield Label("", id="page_info")
      yield DataTable(id="table")
    yield Footer()

  def on_mount(self) -> None:
    """Called when screen is mounted."""
    log.debug("EnrollmentsScreen mounted")
    table = self.query_one("#table", DataTable)
    table.add_columns("ID", "Name", "Status", "Active", "Integration", "Challenge")
    table.cursor_type = "row"
    self._load_enrollments()

  def on_data_table_row_selected(self, event: DataTable.RowSelected) -> None:
    """Handle row selection (Enter key)."""
    table = self.query_one("#table", DataTable)
    row_key = event.row_key
    row_data = table.get_row(row_key)

    if row_data:
      enrollment_id = int(row_data[0])
      log.debug(f"Opening detail for enrollment {enrollment_id}")
      from .enrollment_detail import EnrollmentDetailScreen
      self.app.push_screen(EnrollmentDetailScreen(enrollment_id))

  def _load_enrollments(self) -> None:
    """Load enrollments list from API."""
    api_client = self.app.api_client
    if not api_client:
      log.warning("No API client available")
      return

    response = api_client.get_enrollments(
        page=self.current_page,
        size=self.page_size,
        enrollment_name=self.filters.get("name"),
        status=self.filters.get("status"),
        integration_id=self.filters.get("integration_id"),
        active=self.filters.get("active")
    )
    if not response:
      log.warning("No enrollments response")
      return

    content = response.get("content", [])
    page_info = response.get("page", {}) if isinstance(response.get("page"), dict) else {}
    self.total_elements = page_info.get("totalElements", 0)
    self.total_pages = page_info.get("totalPages", 0)
    self.current_page = page_info.get("number", self.current_page)

    table = self.query_one("#table", DataTable)
    table.clear()

    for item in content:
      enrollment_id = item.get("enrollmentId", "")
      name = item.get("enrollmentName", "")
      status = item.get("enrollmentStatus", "")
      active = "✓" if item.get("enrollmentActive") else "✗"
      integration_id = item.get("integrationId", "")
      challenge = item.get("enrollmentChallenge", "")

      table.add_row(
          str(enrollment_id),
          name,
          status,
          active,
          str(integration_id),
          str(challenge)
      )

    page_label = self.query_one("#page_info", Label)
    page_label.update(
        f"Page {self.current_page + 1} / {max(self.total_pages, 1)} · Total {self.total_elements}"
    )

  def action_show_home(self) -> None:
    """Switch to home screen."""
    log.debug("Switching to home screen")
    self.app.pop_screen()

  def action_create(self) -> None:
    """Create new enrollment."""
    log.debug("Creating new enrollment")
    from .enrollment_create import CreateEnrollmentModal

    def on_create_result(created: bool) -> None:
      if created:
        log.info("Enrollment created, refreshing list")
        self._load_enrollments()

    self.app.push_screen(CreateEnrollmentModal(), on_create_result)

  def action_filter(self) -> None:
    """Open filter modal."""
    log.debug("Opening enrollment filter modal")
    from .enrollment_filter import EnrollmentFilterModal

    def on_filter_result(filters: dict) -> None:
      if filters is not None:
        log.info(f"Enrollment filters applied: {filters}")
        self.filters = filters
        self.current_page = 0
        self._load_enrollments()

    self.app.push_screen(EnrollmentFilterModal(current_filters=self.filters), on_filter_result)

  def action_refresh(self) -> None:
    """Refresh enrollments list."""
    log.debug("Refreshing enrollments list")
    self._load_enrollments()

  def action_next_page(self) -> None:
    """Go to next page."""
    if self.total_pages and self.current_page + 1 < self.total_pages:
      self.current_page += 1
      self._load_enrollments()

  def action_prev_page(self) -> None:
    """Go to previous page."""
    if self.current_page > 0:
      self.current_page -= 1
      self._load_enrollments()

  def action_quit(self) -> None:
    """Quit the application."""
    self.app.exit()
