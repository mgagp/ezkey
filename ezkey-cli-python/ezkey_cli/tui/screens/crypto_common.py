"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

TUI Module: Crypto Common
Description: Shared helpers for Crypto screens
"""

from textual.screen import Screen
from textual.widgets import Label
from textual.binding import Binding
import logging

log = logging.getLogger(__name__)


class CryptoActionScreen(Screen):
  """Base class for crypto action screens."""

  BINDINGS = [
      Binding("b", "back", "Back"),
      Binding("h", "home", "Home"),
      Binding("q", "quit", "Quit"),
  ]

  def _get_crypto_client(self):
    client = getattr(self.app, "crypto_client", None)
    if not client:
      self._set_status("No Crypto API client available")
      return None
    return client

  def _set_status(self, message: str) -> None:
    label = self.query_one("#status_label", Label)
    label.update(message)

  def _copy_to_clipboard(self, text: str) -> None:
    if not text:
      self._set_status("Nothing to copy")
      return
    if hasattr(self.app, "copy_to_clipboard"):
      try:
        self.app.copy_to_clipboard(text)
        self._set_status("Copied to clipboard")
        return
      except Exception as e:
        log.warning("Copy to clipboard failed: %s", e)
    self._set_status("Clipboard not available")

  def action_back(self) -> None:
    self.app.pop_screen()

  def action_home(self) -> None:
    self.app.pop_screen()
    self.app.push_screen("home")

  def action_quit(self) -> None:
    self.app.exit()
