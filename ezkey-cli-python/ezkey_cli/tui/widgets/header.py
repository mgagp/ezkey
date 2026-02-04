"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

TUI Module: Header Widget
Description: Reusable header widget for screens
"""

from textual.widgets import Static
from rich.text import Text


class HeaderWidget(Static):
  """Custom header widget for admin console."""

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
