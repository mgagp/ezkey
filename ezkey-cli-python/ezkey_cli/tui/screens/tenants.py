"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

TUI Module: Tenants Screen
Description: Screen for listing tenants
"""

from textual.screen import Screen
from textual.widgets import Header, Footer, DataTable, Label
from textual.binding import Binding
from textual.containers import Vertical
from textual.events import Key
from pathlib import Path
from datetime import datetime
from typing import Optional, List, Dict, Any
import logging

log = logging.getLogger(__name__)


class TenantsScreen(Screen):
  """Tenants list screen."""

  BINDINGS = [
      Binding("h", "show_home", "Home"),
      Binding("f", "filter", "Filter"),
      Binding("c", "create", "Create"),
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
    self._all_tenants: List[Dict[str, Any]] = []

  def compose(self):
    """Compose the tenants screen."""
    yield Header(show_clock=True)
    with Vertical(id="content"):
      yield Label("Tenants", id="title")
      yield Label("", id="status_label")
      yield Label("", id="page_info")
      yield DataTable(id="table")
    yield Footer()

  def on_mount(self) -> None:
    """Called when screen is mounted."""
    table = self.query_one("#table", DataTable)
    table.add_columns(
        "ID",
        "Name",
        "Active",
        "Created"
    )
    table.cursor_type = "row"
    self._apply_page_size(auto=True)
    self._load_tenants()

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
    table = self.query_one("#table", DataTable)
    row_key = event.row_key
    row_data = table.get_row(row_key)

    if row_data:
      tenant_id = int(row_data[0])
      from .tenant_detail import TenantDetailScreen
      self.app.push_screen(TenantDetailScreen(tenant_id))

  def _load_tenants(self) -> None:
    api_client = self.app.api_client
    if not api_client:
      self._set_status("No API client available")
      return

    response = api_client.get_tenants()
    if response is None:
      self._set_status("Failed to load tenants")
      table = self.query_one("#table", DataTable)
      table.clear()
      if api_client.last_auth_error and hasattr(self.app, "handle_auth_error"):
        self.app.handle_auth_error()
      return

    if not isinstance(response, list):
      self._set_status("Unexpected tenants response")
      return

    self._set_status("")
    self._all_tenants = response

    filtered = self._apply_client_side_filters(self._all_tenants)
    sorted_tenants = self._apply_sort(filtered)

    self.total_elements = len(sorted_tenants)
    self.total_pages = max(1, (self.total_elements + self.page_size - 1) // self.page_size)
    self.current_page = min(self.current_page, self.total_pages - 1)

    start = self.current_page * self.page_size
    end = start + self.page_size
    page_items = sorted_tenants[start:end]

    table = self.query_one("#table", DataTable)
    table.clear()

    for item in page_items:
      tenant_id = item.get("tenantId", "")
      name = item.get("tenantName", "")
      active = "✓" if item.get("active") else "✗"
      created_at = item.get("createdAt", "")
      table.add_row(str(tenant_id), str(name), str(active), str(created_at))

    page_label = self.query_one("#page_info", Label)
    if self._has_client_side_filters() or self.filters.get("sort"):
      page_label.update(
          "Page "
          f"{self.current_page + 1} / {self.total_pages}"
          f" · Showing {len(page_items)} of {self.total_elements}"
      )
    else:
      page_label.update(
          f"Page {self.current_page + 1} / {self.total_pages} · Total {self.total_elements}"
      )

  def _apply_client_side_filters(self, content: list) -> list:
    if not content:
      return []

    name_filter = (self.filters.get("name") or "").strip().lower()
    tenant_id_filter = (self.filters.get("tenant_id") or "").strip()
    active_filter = (self.filters.get("active") or "").strip().lower()
    created_after = self._parse_datetime(self.filters.get("created_after"))
    created_before = self._parse_datetime(self.filters.get("created_before"))

    def matches(item: dict) -> bool:
      if name_filter:
        name = (item.get("tenantName") or "").lower()
        if name_filter not in name:
          return False
      if tenant_id_filter:
        item_id = item.get("tenantId")
        if str(item_id) != tenant_id_filter:
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

  def _apply_sort(self, content: list) -> list:
    sort_value = (self.filters.get("sort") or "createdAt,desc").strip()
    parts = [p.strip() for p in sort_value.split(",")]
    field = parts[0] if parts else "createdAt"
    direction = parts[1].lower() if len(parts) > 1 else "desc"
    reverse = direction == "desc"

    def sort_key(item: dict):
      if field == "tenantId":
        return item.get("tenantId") or 0
      if field == "tenantName":
        return (item.get("tenantName") or "").lower()
      if field == "active":
        return 1 if item.get("active") else 0
      if field == "createdAt":
        parsed = self._parse_datetime(item.get("createdAt"))
        return parsed or datetime.min
      return item.get(field) or ""

    try:
      return sorted(content, key=sort_key, reverse=reverse)
    except Exception:
      return content

  def _parse_datetime(self, value: str) -> Optional[datetime]:
    if not value or not isinstance(value, str):
      return None
    try:
      if value.endswith("Z"):
        value = value.replace("Z", "+00:00")
      return datetime.fromisoformat(value)
    except Exception:
      return None

  def _has_client_side_filters(self) -> bool:
    return any(
      self.filters.get(key)
      for key in ("name", "tenant_id", "active", "created_after", "created_before")
    )

  def _set_status(self, message: str) -> None:
    status_label = self.query_one("#status_label", Label)
    status_label.update(message)

  def _apply_page_size(self, auto: bool = False) -> None:
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
    config = getattr(self.app, "config", None)
    if not config:
      return
    config.set("pageSize", self.page_size)
    local_config_path = Path.cwd() / "ezkey.json"
    if local_config_path.exists():
      config.save(global_config=False)
    config.save(global_config=True)

  def _get_saved_page_size(self, config) -> int:
    if not config:
      return 0
    saved = config.get("pageSize")
    if isinstance(saved, int):
      return saved if saved > 0 else 0
    if isinstance(saved, str) and saved.isdigit():
      return int(saved)
    return 0

  def _max_rows(self) -> int:
    table = self.query_one("#table", DataTable)
    if table.size.height <= 0:
      return 0
    return max(10, table.size.height - 2)

  def _is_table_ready(self) -> bool:
    table = self.query_one("#table", DataTable)
    return table.size.height > 0

  def _cap_page_size_to_viewport(self) -> None:
    max_rows = self._max_rows()
    if max_rows and self.page_size > max_rows:
      self.page_size = max_rows

  def action_show_home(self) -> None:
    self.app.pop_screen()

  def action_filter(self) -> None:
    from .tenant_filter import TenantFilterModal

    def on_filter_result(filters: dict) -> None:
      if filters is not None:
        self.filters = filters
        self.current_page = 0
        self._load_tenants()

    self.app.push_screen(TenantFilterModal(current_filters=self.filters), on_filter_result)

  def action_create(self) -> None:
    from .tenant_create import CreateTenantModal

    def on_created(result: bool) -> None:
      if result:
        self._load_tenants()

    self.app.push_screen(CreateTenantModal(), on_created)

  def action_refresh(self) -> None:
    self._load_tenants()

  def action_next_page(self) -> None:
    if self.total_pages and self.current_page + 1 < self.total_pages:
      self.current_page += 1
      self._load_tenants()

  def action_prev_page(self) -> None:
    if self.current_page > 0:
      self.current_page -= 1
      self._load_tenants()

  def action_first_page(self) -> None:
    if self.current_page != 0:
      self.current_page = 0
      self._load_tenants()

  def action_last_page(self) -> None:
    if self.total_pages and self.current_page + 1 < self.total_pages:
      self.current_page = self.total_pages - 1
      self._load_tenants()

  def action_increase_page_size(self) -> None:
    self.page_size = min(self.page_size + 5, self._max_rows())
    self.page_size_mode = "manual"
    self.current_page = 0
    self._persist_page_size()
    self._load_tenants()

  def action_decrease_page_size(self) -> None:
    self.page_size = max(self.page_size - 5, 5)
    self.page_size_mode = "manual"
    self.current_page = 0
    self._persist_page_size()
    self._load_tenants()

  def action_quit(self) -> None:
    self.app.exit()
