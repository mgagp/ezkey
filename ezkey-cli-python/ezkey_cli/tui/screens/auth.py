"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

TUI Module: Authentication Screen
Description: Screen for passwordless authentication
"""

from textual.screen import Screen
from textual.widgets import Header, Footer, Static
from textual.containers import Vertical, Horizontal
from textual.binding import Binding
from textual import work
import logging

log = logging.getLogger(__name__)


class AuthScreen(Screen):
  """Authentication screen for admin login."""

  BINDINGS = [
      Binding("q", "quit", "Quit", show=False),
  ]

  def compose(self):
    """Compose the authentication screen."""
    yield Header(show_clock=False)
    yield Static("Authentication Screen (Placeholder)", id="content")
    yield Footer()

  def on_mount(self) -> None:
    """Called when screen is mounted."""
    log.debug("AuthScreen mounted")
