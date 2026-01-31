"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

TUI Module: Encryption Keys Screen
Description: Screen for listing encryption keys and managing rotation
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


class EncryptionKeysScreen(Screen):
  """Encryption keys list screen."""

  BINDINGS = [
      Binding("h", "show_home", "Home"),
      Binding("b", "show_batches", "Batches"),
      Binding("o", "rotate", "Rotate"),
      Binding("x", "trigger_full_reencrypt", "Full Reencrypt"),
      Binding("c", "create_batches", "Create Batches"),
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
    self.all_keys = []

  def compose(self):
    """Compose the encryption keys screen."""
    yield Header(show_clock=True)
    with Vertical(id="content"):
      yield Label("Encryption Keys", id="title")
      yield Label("", id="status_label")
      yield Label("", id="page_info")
      yield DataTable(id="table")
    yield Footer()

  def on_mount(self) -> None:
    """Called when screen is mounted."""
    log.debug("EncryptionKeysScreen mounted")
    table = self.query_one("#table", DataTable)
    table.add_columns(
        "ID",
        "Status",
        "Algorithm",
        "Introduced",
        "Primary At",
        "Disabled At",
        "Encrypted",
        "Reencrypted",
        "Created By",
        "Notes"
    )
    table.cursor_type = "row"
    self._apply_page_size(auto=True)
    self._load_keys()

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
      key_id = int(row_data[0])
      from .encryption_key_detail import EncryptionKeyDetailScreen
      self.app.push_screen(EncryptionKeyDetailScreen(key_id))

  def _load_keys(self) -> None:
    """Load encryption keys from API."""
    api_client = self.app.api_client
    if not api_client:
      self._set_status("No API client available")
      return

    response = api_client.get_encryption_keys()
    if not response:
      log.warning("No encryption keys response")
      self._set_status("Failed to load encryption keys")
      table = self.query_one("#table", DataTable)
      table.clear()
      if api_client.last_auth_error and hasattr(self.app, "handle_auth_error"):
        self.app.handle_auth_error()
      return

    self._set_status("")

    if isinstance(response, list):
      self.all_keys = response
    else:
      self.all_keys = []

    self.total_elements = len(self.all_keys)
    self.total_pages = max(1, math.ceil(self.total_elements / self.page_size))
    if self.current_page >= self.total_pages:
      self.current_page = max(self.total_pages - 1, 0)

    self._render_page()

  def _render_page(self) -> None:
    """Render current page of keys."""
    start = self.current_page * self.page_size
    end = start + self.page_size
    page_items = self.all_keys[start:end]

    table = self.query_one("#table", DataTable)
    table.clear()

    for item in page_items:
      key_id = item.get("keyId", "")
      status = item.get("keyStatus", "")
      algorithm = item.get("algorithm", "")
      introduced_at = item.get("introducedAt", "")
      promoted_at = item.get("promotedPrimaryAt", "")
      disabled_at = item.get("disabledAt", "")
      records_encrypted = item.get("recordsEncrypted", "")
      records_reencrypted = item.get("recordsReencrypted", "")
      created_by = item.get("createdBy", "")
      notes = item.get("notes", "")

      table.add_row(
          str(key_id),
          str(status),
          str(algorithm),
          str(introduced_at),
          str(promoted_at),
          str(disabled_at),
          str(records_encrypted),
          str(records_reencrypted),
          str(created_by),
          str(notes)
      )

    page_label = self.query_one("#page_info", Label)
    page_label.update(
        f"Page {self.current_page + 1} / {max(self.total_pages, 1)} · Total {self.total_elements}"
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

  def action_show_batches(self) -> None:
    """Open re-encryption batches screen."""
    from .reencryption_batches import ReencryptionBatchesScreen
    self.app.push_screen(ReencryptionBatchesScreen())

  def action_rotate(self) -> None:
    """Rotate encryption keys with confirmation."""
    from .confirmation_modal import ConfirmationModal

    def on_confirm(confirmed: bool) -> None:
      if confirmed:
        self._perform_rotate()

    self.app.push_screen(
        ConfirmationModal(
            title="Rotate Encryption Keys",
            message="Rotate the primary encryption key now?\n"
            "This creates a new PRIMARY key and demotes the old one."
        ),
        on_confirm
    )

  def _perform_rotate(self) -> None:
    """Perform rotation API call."""
    api_client = self.app.api_client
    if not api_client:
      self._set_status("No API client available")
      return

    try:
      response = api_client.rotate_encryption_key()
    except Exception as e:
      if api_client.last_auth_error and hasattr(self.app, "handle_auth_error"):
        self.app.handle_auth_error()
      self._set_status(str(e))
      return

    if not response:
      if api_client.last_auth_error and hasattr(self.app, "handle_auth_error"):
        self.app.handle_auth_error()
      self._set_status("Failed to rotate keys")
      return

    new_id = response.get("newPrimaryKeyId")
    message = response.get("message", "Rotation completed")
    self._set_status(f"✓ {message} (new primary: {new_id})")
    self._load_keys()

  def action_trigger_full_reencrypt(self) -> None:
    """Trigger full re-encryption with confirmation."""
    from .confirmation_modal import ConfirmationModal

    def on_confirm(confirmed: bool) -> None:
      if confirmed:
        self._perform_full_reencrypt()

    self.app.push_screen(
        ConfirmationModal(
            title="Trigger Full Re-encryption",
            message="Trigger full re-encryption now?\n"
            "This may process a large amount of data."
        ),
        on_confirm
    )

  def _perform_full_reencrypt(self) -> None:
    """Perform full re-encryption API call."""
    api_client = self.app.api_client
    if not api_client:
      self._set_status("No API client available")
      return

    try:
      response = api_client.trigger_full_reencryption()
    except Exception as e:
      if api_client.last_auth_error and hasattr(self.app, "handle_auth_error"):
        self.app.handle_auth_error()
      self._set_status(str(e))
      return

    if not response:
      if api_client.last_auth_error and hasattr(self.app, "handle_auth_error"):
        self.app.handle_auth_error()
      self._set_status("Failed to trigger re-encryption")
      return

    message = response.get("message", "Re-encryption triggered")
    created = response.get("batchesCreated")
    processed = response.get("batchesProcessed")
    failed = response.get("batchesFailed")
    self._set_status(
        f"✓ {message} (created {created}, processed {processed}, failed {failed})"
    )

  def action_create_batches(self) -> None:
    """Create re-encryption batches with confirmation."""
    from .confirmation_modal import ConfirmationModal

    def on_confirm(confirmed: bool) -> None:
      if confirmed:
        self._perform_create_batches()

    self.app.push_screen(
        ConfirmationModal(
            title="Create Re-encryption Batches",
            message="Create re-encryption batches now?\n"
            "Batches will be processed by the scheduler."
        ),
        on_confirm
    )

  def _perform_create_batches(self) -> None:
    """Perform batch creation API call."""
    api_client = self.app.api_client
    if not api_client:
      self._set_status("No API client available")
      return

    try:
      response = api_client.create_reencryption_batches()
    except Exception as e:
      if api_client.last_auth_error and hasattr(self.app, "handle_auth_error"):
        self.app.handle_auth_error()
      self._set_status(str(e))
      return

    if not response:
      if api_client.last_auth_error and hasattr(self.app, "handle_auth_error"):
        self.app.handle_auth_error()
      self._set_status("Failed to create batches")
      return

    created = response.get("batchesCreated")
    message = response.get("message", "Batches created")
    self._set_status(f"✓ {message} (created {created})")

  def action_refresh(self) -> None:
    """Refresh key list."""
    self._load_keys()

  def action_next_page(self) -> None:
    """Go to next page."""
    if self.total_pages and self.current_page + 1 < self.total_pages:
      self.current_page += 1
      self._render_page()

  def action_prev_page(self) -> None:
    """Go to previous page."""
    if self.current_page > 0:
      self.current_page -= 1
      self._render_page()

  def action_first_page(self) -> None:
    """Go to first page."""
    if self.current_page != 0:
      self.current_page = 0
      self._render_page()

  def action_last_page(self) -> None:
    """Go to last page."""
    if self.total_pages and self.current_page + 1 < self.total_pages:
      self.current_page = self.total_pages - 1
      self._render_page()

  def action_increase_page_size(self) -> None:
    """Increase page size and persist."""
    self.page_size_mode = "manual"
    self.page_size = max(1, self.page_size + 5)
    self._cap_page_size_to_viewport()
    self._persist_page_size()
    self._load_keys()

  def action_decrease_page_size(self) -> None:
    """Decrease page size and persist."""
    self.page_size_mode = "manual"
    self.page_size = max(5, self.page_size - 5)
    self._persist_page_size()
    self._load_keys()

  def action_quit(self) -> None:
    """Quit the application."""
    self.app.exit()
