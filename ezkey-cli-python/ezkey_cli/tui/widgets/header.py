"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

TUI Module: Header Widget
Description: Reusable header widget for screens with user context
"""

from textual.widgets import Static, Header
from textual.containers import Container
from textual.reactive import reactive
from rich.text import Text
from datetime import datetime, timezone
import logging

log = logging.getLogger(__name__)


class ContextHeader(Static):
  """
  Enhanced header widget showing Ezkey branding, user context, and time.

  Displays:
  - Left: "Ezkey Admin TUI"
  - Center: username | scope | organization (if available)
  - Right: current time (HH:MM:SS)
  """

  username = reactive("unknown")
  scope = reactive("unknown")
  organization = reactive("")
  current_time = reactive("")

  DEFAULT_CSS = """
  ContextHeader {
      height: 1;
      background: $boost;
      color: $text;
      border-bottom: solid $primary;
      padding: 0 1;
  }
  """

  def __init__(self):
    """Initialize the context header."""
    super().__init__()
    self._update_time()

  def render(self) -> str:
    """Render the header with context information."""
    # Left part: title
    left_str = "Ezkey Admin TUI"

    # Center part: user context
    center_parts = []
    if self.username and self.username != "unknown":
      center_parts.append(self.username)
    if self.scope and self.scope != "unknown":
      center_parts.append(self.scope)
    if self.organization:
      center_parts.append(self.organization)

    center = " | ".join(center_parts) if center_parts else ""
    right_str = self.current_time

    # Calculate spacing
    total_width = 80
    available_width = total_width - len(left_str) - len(right_str) - 4
    center_str = center
    if len(center_str) > available_width:
      center_str = center_str[:available_width - 2] + ".."

    padding = max(1, available_width - len(center_str))

    result = Text()
    result.append(left_str, style="bold cyan")
    result.append(" ")
    result.append(center_str, style="dim green")
    result.append(" " * padding)
    result.append(right_str, style="dim yellow")

    return str(result)

  def on_mount(self) -> None:
    """Set up the header when mounted."""
    # Load user info from app config if available
    if hasattr(self.app, "config"):
      config = self.app.config
      self.username = config.get_admin_username() or "unknown"
      self.scope = config.get_admin_type() or "unknown"
      self.organization = config.get("organization") or ""

    # Start time update
    self.set_interval(1.0, self._update_time)

  def _update_time(self) -> None:
    """Update the current time display."""
    now = datetime.now(timezone.utc)
    self.current_time = now.strftime("%H:%M:%S")


class HeaderWidget(Static):
  """Legacy custom header widget for backward compatibility."""

  def __init__(self, title: str = "Ezkey Admin Console", user: str = None):
    """
    Initialize header widget.

    Args:
        title: Header title
        user: Optional username to display
    """
    super().__init__()
    self.title = title
    self.user = user

  def render(self) -> str:
    """Render the header."""
    header_text = Text(f"  {self.title}", style="bold cyan")

    if self.user:
      header_text.append_text(Text(f" [{self.user}]", style="dim green"))

    return str(header_text)

