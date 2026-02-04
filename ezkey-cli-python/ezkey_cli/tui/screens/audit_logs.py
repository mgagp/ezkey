"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

TUI Module: Audit Logs Screen
Description: Screen for querying audit logs
"""

from textual.screen import Screen
from textual.widgets import Header, Footer, DataTable, Label
from textual.binding import Binding
from textual.containers import Vertical
from pathlib import Path
import logging
from textual.events import Key

log = logging.getLogger(__name__)


class AuditLogsScreen(Screen):
  """Audit logs screen (read-only)."""

  BINDINGS = [
      Binding("h", "show_home", "Home"),
      Binding("f", "filter", "Filter"),
      Binding("shift+f", "toggle_follow", "Follow"),
      Binding("r", "refresh", "Refresh"),
      Binding("n", "next_page", "Next"),
      Binding("p", "prev_page", "Prev"),
      Binding("pageup", "prev_page", "Prev"),
      Binding("pagedown", "next_page", "Next"),
      Binding("home", "first_page", "First"),
      Binding("end", "last_page", "Last"),
      Binding("+", "increase_page_size", "More"),
      Binding("-", "decrease_page_size", "Less"),
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
      margin: 1 0 0 0;
  }

  #status_label {
      height: auto;
      margin: 0 0 1 0;
      color: $error;
  }
  """

  def __init__(self, *args, **kwargs):
    super().__init__(*args, **kwargs)
    self.current_page = 0
    self.page_size = 20
    self.page_size_mode = "auto"
    self.total_pages = 0
    self.total_elements = 0
    self.filters = {}
    self.follow_enabled = False
    self.follow_timer = None

  def compose(self):
    """Compose the audit logs screen."""
    yield Header(show_clock=True)
    with Vertical(id="content"):
      yield Label("Audit Logs", id="title")
      yield Label("", id="status_label")
      yield Label("Follow: OFF", id="follow_status")
      yield Label("", id="page_info")
      yield DataTable(id="table")
    yield Footer()

  def on_mount(self) -> None:
    """Called when screen is mounted."""
    log.debug("AuditLogsScreen mounted")
    table = self.query_one("#table", DataTable)
    table.add_columns(
        "ID",
        "Created",
        "Type",
        "Status",
        "API",
        "Admin",
        "Enrollment"
    )
    table.cursor_type = "row"
    self._apply_page_size(auto=True)
    self._load_audit_logs()
    self._update_follow_label()

  def on_key(self, event: Key) -> None:
    """Handle paging keys regardless of focus."""
    key = event.key.lower()
    if key in ("pageup", "pgup"):
      self.action_prev_page()
      event.stop()
    elif key in ("pagedown", "pgdn"):
      self.action_next_page()
      event.stop()
    elif key == "home":
      self.action_first_page()
      event.stop()
    elif key == "end":
      self.action_last_page()
      event.stop()

  def on_resize(self) -> None:
    """Auto-adjust page size to available space."""
    if self.page_size_mode == "auto":
      self._apply_page_size(auto=True)
    else:
      self._cap_page_size_to_viewport()

  def on_unmount(self) -> None:
    """Stop follow timer on unmount."""
    self._stop_follow()

  def _load_audit_logs(self) -> None:
    """Load audit logs from API."""
    api_client = self.app.api_client
    if not api_client:
      log.warning("No API client available")
      self._set_status("No API client available")
      return

    response = api_client.get_audit_logs(
        page=self.current_page,
        size=self.page_size,
        event_type=self.filters.get("event_type"),
        event_status=self.filters.get("event_status"),
        api_name=self.filters.get("api_name"),
        enrollment_id=self.filters.get("enrollment_id"),
        admin_id=self.filters.get("admin_id"),
        sort=self.filters.get("sort")
    )

    if not response:
      log.warning("No audit logs response")
      self._set_status("Failed to load audit logs")
      table = self.query_one("#table", DataTable)
      table.clear()
      if api_client.last_auth_error and hasattr(self.app, "handle_auth_error"):
        self.app.handle_auth_error()
      return

    self._set_status("")

    content = response.get("content", [])
    content = self._apply_client_side_filters(content)
    page_info = response.get("page", {}) if isinstance(response.get("page"), dict) else {}
    self.total_elements = page_info.get("totalElements", 0)
    self.total_pages = page_info.get("totalPages", 0)
    self.current_page = page_info.get("number", self.current_page)

    table = self.query_one("#table", DataTable)
    table.clear()

    for item in content:
      audit_log_id = item.get("auditLogId", "")
      created_at = item.get("createdAt", "")
      event_type = item.get("eventType", "")
      event_status = item.get("eventStatus", "")
      api_name = item.get("apiName", "")
      admin_id = item.get("adminId", "")
      enrollment_id = item.get("enrollmentId", "")

      table.add_row(
          str(audit_log_id),
          str(created_at),
          str(event_type),
          str(event_status),
          str(api_name),
          str(admin_id),
          str(enrollment_id)
      )

    page_label = self.query_one("#page_info", Label)
    page_label.update(
        f"Page {self.current_page + 1} / {max(self.total_pages, 1)} · Total {self.total_elements}"
    )

  def _apply_client_side_filters(self, content: list) -> list:
    """Apply filters locally as a safety net."""
    event_type = self.filters.get("event_type")
    event_status = self.filters.get("event_status")
    api_name = self.filters.get("api_name")
    enrollment_id = self.filters.get("enrollment_id")
    admin_id = self.filters.get("admin_id")

    def matches(item: dict) -> bool:
      if event_type and str(item.get("eventType", "")) != event_type:
        return False
      if event_status and str(item.get("eventStatus", "")) != event_status:
        return False
      if api_name and str(item.get("apiName", "")) != api_name:
        return False
      if enrollment_id is not None and item.get("enrollmentId") != enrollment_id:
        return False
      if admin_id is not None and item.get("adminId") != admin_id:
        return False
      return True

    return [item for item in content if matches(item)]

  def _set_status(self, message: str) -> None:
    """Set status label text."""
    status_label = self.query_one("#status_label", Label)
    status_label.update(message)

  def _update_follow_label(self) -> None:
    """Update follow status label."""
    label = self.query_one("#follow_status", Label)
    label.update("Follow: ON" if self.follow_enabled else "Follow: OFF")

  def _start_follow(self) -> None:
    """Start auto-refresh timer."""
    if self.follow_timer:
      self.follow_timer.stop()
    self.follow_timer = self.set_interval(5.0, self._load_audit_logs)

  def _stop_follow(self) -> None:
    """Stop auto-refresh timer."""
    if self.follow_timer:
      self.follow_timer.stop()
      self.follow_timer = None

  def _apply_page_size(self, auto: bool = False) -> None:
    """Apply page size from config or auto-size."""
    config = getattr(self.app, "config", None)
    if auto:
      saved = self._get_saved_page_size(config)
      if saved:
        if self._is_table_ready():
          self.page_size = min(saved, self._max_rows())
        else:
          self.page_size = saved
        self.page_size_mode = "manual"
        return

      rows = self._max_rows()
      if rows != self.page_size:
        self.page_size = rows
        self.page_size_mode = "auto"

  def _max_rows(self) -> int:
    """Maximum rows that fit in the current viewport."""
    table = self.query_one("#table", DataTable)
    if table.size.height <= 0:
      return 0
    return max(10, table.size.height - 2)

  def _is_table_ready(self) -> bool:
    """Check if table has a measured size."""
    table = self.query_one("#table", DataTable)
    return table.size.height > 0

  def _cap_page_size_to_viewport(self) -> None:
    """Clamp manual page size to viewport if needed."""
    max_rows = self._max_rows()
    if max_rows and self.page_size > max_rows:
      self.page_size = max_rows

  def _persist_page_size(self) -> None:
    """Persist page size to config."""
    config = getattr(self.app, "config", None)
    if not config:
      return
    config.set("pageSize", self.page_size)
    local_config_path = Path.cwd() / "ezkey.json"
    if local_config_path.exists():
      config.save(global_config=False)
    config.save(global_config=True)

  def _get_saved_page_size(self, config) -> int:
    """Get saved page size from config, with type normalization."""
    if not config:
      return 0
    saved = config.get("pageSize")
    if isinstance(saved, int):
      return saved if saved > 0 else 0
    if isinstance(saved, str) and saved.isdigit():
      return int(saved)
    return 0

  def action_increase_page_size(self) -> None:
    """Increase page size."""
    self.page_size = min(self.page_size + 5, self._max_rows())
    self.page_size_mode = "manual"
    self.current_page = 0
    self._persist_page_size()
    self._load_audit_logs()

  def action_decrease_page_size(self) -> None:
    """Decrease page size."""
    self.page_size = max(self.page_size - 5, 5)
    self.page_size_mode = "manual"
    self.current_page = 0
    self._persist_page_size()
    self._load_audit_logs()

  def action_show_home(self) -> None:
    """Switch to home screen."""
    log.debug("Switching to home screen")
    self.app.pop_screen()

  def action_filter(self) -> None:
    """Open filter modal."""
    log.debug("Opening audit log filter modal")
    from .audit_log_filter import AuditLogFilterModal

    def on_filter_result(filters: dict) -> None:
      if filters is not None:
        log.info(f"Audit log filters applied: {filters}")
        self.filters = filters
        self.current_page = 0
        self._load_audit_logs()

    self.app.push_screen(AuditLogFilterModal(current_filters=self.filters), on_filter_result)

  def action_refresh(self) -> None:
    """Refresh audit log list."""
    log.debug("Refreshing audit logs")
    self._load_audit_logs()

  def action_toggle_follow(self) -> None:
    """Toggle follow (auto-refresh)."""
    self.follow_enabled = not self.follow_enabled
    if self.follow_enabled:
      self._start_follow()
    else:
      self._stop_follow()
    self._update_follow_label()

  def action_next_page(self) -> None:
    """Go to next page."""
    if self.total_pages and self.current_page + 1 < self.total_pages:
      self.current_page += 1
      self._load_audit_logs()

  def action_prev_page(self) -> None:
    """Go to previous page."""
    if self.current_page > 0:
      self.current_page -= 1
      self._load_audit_logs()

  def action_first_page(self) -> None:
    """Go to first page."""
    if self.current_page != 0:
      self.current_page = 0
      self._load_audit_logs()

  def action_last_page(self) -> None:
    """Go to last page."""
    if self.total_pages and self.current_page + 1 < self.total_pages:
      self.current_page = self.total_pages - 1
      self._load_audit_logs()

  def action_quit(self) -> None:
    """Quit the application."""
    self.app.exit()
