"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

TUI Module: Header Widget
Description: Reusable header widget for screens with user context
"""

from textual.widgets import Static, Header
from textual.containers import Container, Horizontal
from textual.reactive import reactive
from rich.text import Text
from datetime import datetime, timezone
import logging

log = logging.getLogger(__name__)


class ContextHeader(Static):
  """
  Enhanced header widget showing Ezkey branding, user context, and time.

  Displays in a single line:
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

  def render(self) -> Text:
    """Render the header with context information."""
    # Build center context string
    center_parts = []
    if self.username and self.username != "unknown":
      center_parts.append(self.username)
    if self.scope and self.scope != "unknown":
      center_parts.append(self.scope)
    if self.organization:
      center_parts.append(self.organization)

    center = " | ".join(center_parts) if center_parts else ""

    # Build the full line
    left_str = "Ezkey Admin TUI"
    right_str = self.current_time

    # Create a single line with proper spacing
    # We'll estimate terminal width (typically 80-120)
    total_width = 120
    left_len = len(left_str)
    right_len = len(right_str)
    center_len = len(center)

    # Calculate available space for center
    available = total_width - left_len - right_len - 6  # 6 for spacing

    # Format center with proper length
    if center_len > available:
      center_display = center[:available - 2] + ".."
    else:
      center_display = center

    # Calculate padding
    padding_before = 2
    padding_after = total_width - left_len - padding_before - len(center_display) - right_len - 1

    # Build result
    result = Text()
    result.append(left_str, style="bold cyan")
    result.append(" " * padding_before)
    if center_display:
      result.append(center_display, style="dim green")
    result.append(" " * max(1, padding_after))
    result.append(right_str, style="dim yellow")

    return result

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

