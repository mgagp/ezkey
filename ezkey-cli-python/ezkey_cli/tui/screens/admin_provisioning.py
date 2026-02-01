"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

TUI Module: Admin Provisioning Screen
Description: Screen for listing administrators
"""

from textual.screen import Screen
from textual.widgets import Header, Footer, DataTable, Label
from textual.binding import Binding
from textual.containers import Vertical
from textual.events import Key
from pathlib import Path
import logging
from datetime import datetime
from typing import Optional

log = logging.getLogger(__name__)


class AdminProvisioningScreen(Screen):
  """Administrators list screen."""

  BINDINGS = [
      Binding("h", "show_home", "Home"),
      Binding("c", "create_admin", "Create"),
      Binding("f", "filter", "Filter"),
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

  def compose(self):
    """Compose the admins screen."""
    yield Header(show_clock=True)
    with Vertical(id="content"):
      yield Label("Admins", id="title")
      yield Label("", id="status_label")
      yield Label("", id="page_info")
      yield DataTable(id="table")
    yield Footer()

  def on_mount(self) -> None:
    """Called when screen is mounted."""
    log.debug("AdminProvisioningScreen mounted")
    table = self.query_one("#table", DataTable)
    table.add_columns(
        "ID",
        "Username",
        "Type",
        "Tenant",
        "Active",
        "Created"
    )
    table.cursor_type = "row"
    self._apply_page_size(auto=True)
    self._load_admins()

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

  def on_data_table_row_selected(self, event: DataTable.RowSelected) -> None:
    """Handle row selection (Enter key)."""
    table = self.query_one("#table", DataTable)
    row_key = event.row_key
    row_data = table.get_row(row_key)

    if row_data:
      admin_id = int(row_data[0])
      from .admin_provisioning_detail import AdminProvisioningDetailScreen
      detail_data = {
          "adminId": admin_id,
          "username": row_data[1],
          "adminType": row_data[2],
          "tenantId": row_data[3],
          "active": row_data[4],
          "createdAt": row_data[5],
      }
      self.app.push_screen(AdminProvisioningDetailScreen(detail_data))

  def _load_admins(self) -> None:
    """Load admins list from API."""
    api_client = self.app.api_client
    if not api_client:
      self._set_status("No API client available")
      return

    response = api_client.get_admins(
        page=self.current_page,
        size=self.page_size,
        sort=self.filters.get("sort")
    )

    if not response:
      log.warning("No admins response")
      self._set_status("Failed to load admins")
      table = self.query_one("#table", DataTable)
      table.clear()
      if api_client.last_auth_error and hasattr(self.app, "handle_auth_error"):
        self.app.handle_auth_error()
      return

    self._set_status("")

    content = response.get("content", [])
    filtered = self._apply_client_side_filters(content)
    page_info = self._extract_page_info(response)
    self.total_elements = page_info.get("totalElements", 0)
    self.total_pages = page_info.get("totalPages", 0)
    self.current_page = page_info.get("number", self.current_page)

    table = self.query_one("#table", DataTable)
    table.clear()

    for item in filtered:
      admin_id = item.get("adminId", "")
      username = item.get("username", "")
      admin_type = item.get("adminType", "")
      tenant_id = item.get("tenantId", "")
      active = "✓" if item.get("active") else "✗"
      created_at = item.get("createdAt", "")

      table.add_row(
          str(admin_id),
          str(username),
          str(admin_type),
          str(tenant_id),
          str(active),
          str(created_at)
      )

    page_label = self.query_one("#page_info", Label)
    if self._has_client_side_filters():
      page_label.update(
          "Page "
          f"{self.current_page + 1} / {max(self.total_pages, 1)}"
          f" · Showing {len(filtered)} of {self.total_elements}"
      )
    else:
      page_label.update(
          f"Page {self.current_page + 1} / {max(self.total_pages, 1)} · Total {self.total_elements}"
      )

  def _apply_client_side_filters(self, content: list) -> list:
    """Apply client-side filters to admins list."""
    if not content:
      return []

    username_filter = (self.filters.get("username") or "").strip().lower()
    admin_type_filter = (self.filters.get("admin_type") or "").strip().upper()
    tenant_id_filter = (self.filters.get("tenant_id") or "").strip()
    active_filter = (self.filters.get("active") or "").strip().lower()
    created_after = self._parse_datetime(self.filters.get("created_after"))
    created_before = self._parse_datetime(self.filters.get("created_before"))

    def matches(item: dict) -> bool:
      if username_filter:
        username = (item.get("username") or "").lower()
        if username_filter not in username:
          return False
      if admin_type_filter:
        if (item.get("adminType") or "").upper() != admin_type_filter:
          return False
      if tenant_id_filter:
        item_tenant = item.get("tenantId")
        if str(item_tenant) != tenant_id_filter:
          return False
      if active_filter in ("true", "false"):
        expected = active_filter == "true"
        if bool(item.get("active")) != expected:
          return False
      created_at = self._parse_datetime(item.get("createdAt"))
      if created_after and (not created_at or created_at < created_after):
        return False
      if created_before and (not created_at or created_at > created_before):
        return False
      return True

    return [item for item in content if matches(item)]

  def _parse_datetime(self, value: str) -> Optional[datetime]:
    """Parse ISO-8601 datetime with optional Z suffix."""
    if not value or not isinstance(value, str):
      return None
    try:
      if value.endswith("Z"):
        value = value.replace("Z", "+00:00")
      return datetime.fromisoformat(value)
    except Exception:
      return None

  def _has_client_side_filters(self) -> bool:
    """Check if any client-side filters are active."""
    return any(
        self.filters.get(key)
        for key in ("username", "admin_type", "tenant_id", "active", "created_after", "created_before")
    )

  def _extract_page_info(self, response: dict) -> dict:
    """Extract page info from response (supports Spring Page format)."""
    if not isinstance(response, dict):
      return {}
    if isinstance(response.get("page"), dict):
      return response.get("page")
    return {
        "totalElements": response.get("totalElements", 0),
        "totalPages": response.get("totalPages", 0),
        "number": response.get("number", self.current_page),
    }

  def _set_status(self, message: str) -> None:
    """Set status label text."""
    status_label = self.query_one("#status_label", Label)
    status_label.update(message)

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

  def action_show_home(self) -> None:
    """Switch to home screen."""
    self.app.pop_screen()

  def action_filter(self) -> None:
    """Open filter modal."""
    from .admin_provisioning_filter import AdminProvisioningFilterModal

    def on_filter_result(filters: dict) -> None:
      if filters is not None:
        self.filters = filters
        self.current_page = 0
        self._load_admins()

    self.app.push_screen(AdminProvisioningFilterModal(current_filters=self.filters), on_filter_result)

  def action_create_admin(self) -> None:
    """Open create admin modal."""
    from .admin_provisioning_create import CreateAdminModal

    def on_create_result(result: dict | bool) -> None:
      if result and isinstance(result, dict):
        # Admin created successfully, refresh the list
        log.info(f"Admin created: {result.get('admin_id')}")
        self.current_page = 0
        self._load_admins()
      elif result:
        # Generic success, refresh list
        self.current_page = 0
        self._load_admins()

    self.app.push_screen(CreateAdminModal(), on_create_result)

  def action_refresh(self) -> None:
    """Refresh admin list."""
    self._load_admins()

  def action_next_page(self) -> None:
    """Go to next page."""
    if self.total_pages and self.current_page + 1 < self.total_pages:
      self.current_page += 1
      self._load_admins()

  def action_prev_page(self) -> None:
    """Go to previous page."""
    if self.current_page > 0:
      self.current_page -= 1
      self._load_admins()

  def action_first_page(self) -> None:
    """Go to first page."""
    if self.current_page != 0:
      self.current_page = 0
      self._load_admins()

  def action_last_page(self) -> None:
    """Go to last page."""
    if self.total_pages and self.current_page + 1 < self.total_pages:
      self.current_page = self.total_pages - 1
      self._load_admins()

  def action_increase_page_size(self) -> None:
    """Increase page size."""
    self.page_size = min(self.page_size + 5, self._max_rows())
    self.page_size_mode = "manual"
    self.current_page = 0
    self._persist_page_size()
    self._load_admins()

  def action_decrease_page_size(self) -> None:
    """Decrease page size."""
    self.page_size = max(self.page_size - 5, 5)
    self.page_size_mode = "manual"
    self.current_page = 0
    self._persist_page_size()
    self._load_admins()

  def action_quit(self) -> None:
    """Quit the application."""
    self.app.exit()
