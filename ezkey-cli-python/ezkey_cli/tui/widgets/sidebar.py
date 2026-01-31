"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

TUI Module: Sidebar Widget
Description: Navigation sidebar widget
"""

from textual.widgets import Static
from rich.text import Text


class SidebarWidget(Static):
  """Navigation sidebar widget."""

  def __init__(self):
    """Initialize sidebar widget."""
    super().__init__()

  def render(self) -> str:
    """Render the sidebar."""
    items = [
        "Dashboard",
        "Integrations",
        "Enrollments",
      "Auth Attempts",
      "Admins",
        "Audit Logs",
        "Settings",
    ]

    sidebar = Text()
    sidebar.append("Menu:\n", style="bold cyan")

    for item in items:
      sidebar.append(f"  • {item}\n", style="dim")

    return str(sidebar)
