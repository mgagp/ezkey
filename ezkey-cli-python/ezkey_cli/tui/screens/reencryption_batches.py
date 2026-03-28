"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

TUI Module: Re-encryption Batches Screen
Description: Screen for listing re-encryption batches
"""

from textual.screen import Screen
from textual.widgets import Header, Footer, DataTable, Label
from textual.binding import Binding
from textual.containers import Vertical
from textual.events import Key
from pathlib import Path
import logging
import math

log = logging.getLogger(__name__)


class ReencryptionBatchesScreen(Screen):
  """Re-encryption batches list screen."""

  BINDINGS = [
      Binding("h", "show_home", "Home"),
      Binding("k", "show_keys", "Keys"),
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
    self.all_batches = []

  def compose(self):
    """Compose the batches screen."""
    yield Header(show_clock=True)
    with Vertical(id="content"):
      yield Label("Re-encryption Batches", id="title")
      yield Label("", id="status_label")
      yield Label("", id="page_info")
      yield DataTable(id="table")
    yield Footer()

  def on_mount(self) -> None:
    """Called when screen is mounted."""
    log.debug("ReencryptionBatchesScreen mounted")
    table = self.query_one("#table", DataTable)
    table.add_columns(
        "Batch",
        "Status",
        "Table",
        "Column",
        "Old Key",
        "New Key",
        "Progress",
        "Done/Total",
        "Failed",
        "Started",
        "Completed"
    )
    table.cursor_type = "row"
    self._apply_page_size(auto=True)
    self._load_batches()

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
      batch_id = int(row_data[0])
      from .reencryption_batch_detail import ReencryptionBatchDetailScreen
      self.app.push_screen(ReencryptionBatchDetailScreen(batch_id))

  def _load_batches(self) -> None:
    """Load batches from API (server-side pagination)."""
    api_client = self.app.api_client
    if not api_client:
      self._set_status("No API client available")
      return

    params = {
        "page": self.current_page,
        "size": self.page_size,
        "sort": "createdAt,desc",
    }
    response = api_client.get_reencryption_batches(params)
    if not response:
      log.warning("No re-encryption batches response")
      self._set_status("Failed to load batches")
      table = self.query_one("#table", DataTable)
      table.clear()
      if api_client.last_auth_error and hasattr(self.app, "handle_auth_error"):
        self.app.handle_auth_error()
      return

    self._set_status("")

    if isinstance(response, dict) and "content" in response:
      self.all_batches = response.get("content") or []
      page_info = response.get("page") or {}
      self.total_elements = page_info.get("totalElements", 0)
      tp = page_info.get("totalPages")
      self.total_pages = tp if tp is not None else 0
      if page_info.get("number") is not None:
        self.current_page = page_info.get("number", self.current_page)
    elif isinstance(response, list):
      self.all_batches = response
      self.total_elements = len(self.all_batches)
      self.total_pages = max(1, math.ceil(self.total_elements / self.page_size))
      if self.current_page >= self.total_pages:
        self.current_page = max(self.total_pages - 1, 0)
    else:
      self.all_batches = []
      self.total_elements = 0
      self.total_pages = 0

    self._render_page()

  def _render_page(self) -> None:
    """Render current page of batches (already one server page in all_batches)."""
    page_items = self.all_batches

    table = self.query_one("#table", DataTable)
    table.clear()

    for item in page_items:
      batch_id = item.get("batchId", "")
      status = item.get("status", "")
      target_table = item.get("targetTable", "")
      target_column = item.get("targetColumn", "")
      old_key_id = item.get("oldKeyId", "")
      new_key_id = item.get("newKeyId", "")
      progress = item.get("progressPct", "")
      records_done = item.get("recordsDone", "")
      records_total = item.get("recordsTotal", "")
      records_failed = item.get("recordsFailed", "")
      started_at = item.get("startedAt", "")
      completed_at = item.get("completedAt", "")

      table.add_row(
          str(batch_id),
          str(status),
          str(target_table),
          str(target_column),
          str(old_key_id),
          str(new_key_id),
          str(progress),
          f"{records_done}/{records_total}",
          str(records_failed),
          str(started_at),
          str(completed_at)
      )

    page_label = self.query_one("#page_info", Label)
    denom = max(self.total_pages, 1)
    page_label.update(
        f"Page {self.current_page + 1} / {denom} · Total {self.total_elements}"
    )

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

  def action_show_keys(self) -> None:
    """Return to encryption keys screen."""
    self.app.push_screen("encryption_keys")

  def action_refresh(self) -> None:
    """Refresh batches list."""
    self._load_batches()

  def action_next_page(self) -> None:
    """Go to next page."""
    if self.total_pages and self.current_page + 1 < self.total_pages:
      self.current_page += 1
      self._load_batches()

  def action_prev_page(self) -> None:
    """Go to previous page."""
    if self.current_page > 0:
      self.current_page -= 1
      self._load_batches()

  def action_first_page(self) -> None:
    """Go to first page."""
    if self.current_page != 0:
      self.current_page = 0
      self._load_batches()

  def action_last_page(self) -> None:
    """Go to last page."""
    if self.total_pages and self.current_page + 1 < self.total_pages:
      self.current_page = self.total_pages - 1
      self._load_batches()

  def action_increase_page_size(self) -> None:
    """Increase page size and persist."""
    self.page_size_mode = "manual"
    self.page_size = max(1, self.page_size + 5)
    self._cap_page_size_to_viewport()
    self._persist_page_size()
    self.current_page = 0
    self._load_batches()

  def action_decrease_page_size(self) -> None:
    """Decrease page size and persist."""
    self.page_size_mode = "manual"
    self.page_size = max(5, self.page_size - 5)
    self._persist_page_size()
    self.current_page = 0
    self._load_batches()

  def action_quit(self) -> None:
    """Quit the application."""
    self.app.exit()
