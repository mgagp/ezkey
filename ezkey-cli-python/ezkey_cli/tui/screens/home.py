"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

TUI Module: Home Dashboard Screen
Description: Main dashboard showing status overview
"""

from textual.screen import Screen
from textual.widgets import Header, Footer, Static, Label
from textual.containers import Vertical, Horizontal, Container
from textual.binding import Binding
from textual.reactive import reactive
import logging

log = logging.getLogger(__name__)


class StatusPanel(Static):
  """Status overview panel."""

  # Reactive attributes for live updates
  integrations_count = reactive(0)
  enrollments_count = reactive(0)
  auth_attempts_count = reactive(0)
  auth_failed_count = reactive(0)

  DEFAULT_CSS = """
  StatusPanel {
      border: solid $primary;
      padding: 1 2;
      height: auto;
  }
  """

  def render(self) -> str:
    """Render the status panel."""
    return (
        f"📊 Status Overview\n\n"
        f"Integrations: {self.integrations_count}\n"
        f"Enrollments: {self.enrollments_count}\n"
        f"Auth Attempts (24h): {self.auth_attempts_count}\n"
        f"Failed: {self.auth_failed_count}"
    )

  def update_stats(self, stats: dict) -> None:
    """Update statistics from API."""
    if not stats or not isinstance(stats, dict):
      # If stats is None or invalid, use defaults
      stats = {
          "integrations": 0,
          "enrollments": 0,
          "auth_attempts_24h": 0,
          "auth_failed_24h": 0
      }

    self.integrations_count = stats.get("integrations", 0) or 0
    self.enrollments_count = stats.get("enrollments", 0) or 0
    self.auth_attempts_count = stats.get("auth_attempts_24h", 0) or 0
    self.auth_failed_count = stats.get("auth_failed_24h", 0) or 0


class ActivityPanel(Static):
  """Recent activity feed panel."""

  DEFAULT_CSS = """
  ActivityPanel {
      border: solid $accent;
      padding: 1 2;
      height: auto;
  }
  """

  def render(self) -> str:
    """Render the activity panel."""
    return "📋 Recent Activity\n\n" \
           "• Admin console started (now)"


class HomeScreen(Screen):
  """Home dashboard screen with status overview."""

  BINDINGS = [
      Binding("i", "show_integrations", "Integrations"),
      Binding("e", "show_enrollments", "Enrollments"),
      Binding("a", "show_audit", "Audit"),
      Binding("r", "refresh", "Refresh"),
      Binding("l", "logout", "Logout"),
      Binding("q", "quit", "Quit"),
  ]

  CSS = """
  Screen {
      layout: vertical;
      background: $surface;
  }

  #main_container {
      width: 1fr;
      height: 1fr;
  }

  StatusPanel {
      width: 1fr;
      height: auto;
      margin: 1 0;
  }

  ActivityPanel {
      width: 1fr;
      height: auto;
      margin: 1 0;
  }

  Label {
      width: 1fr;
      margin: 1 0;
  }
  """

  def compose(self):
    """Compose the home screen."""
    yield Header(show_clock=True)

    with Vertical(id="main_container"):
      yield Label("Welcome to Ezkey Admin Console")
      yield StatusPanel(id="status_panel")
      yield ActivityPanel()

    yield Footer()

  def on_mount(self) -> None:
    """Called when screen is mounted - load data from API."""
    log.debug("HomeScreen mounted")
    self._load_dashboard_data()
    self.set_interval(30, self._refresh_dashboard)

  def _refresh_dashboard(self) -> None:
    """Periodic refresh of dashboard data."""
    self._load_dashboard_data()

  def _load_dashboard_data(self) -> None:
    """Load dashboard data from API."""
    try:
      # Get ApiClient from app
      api_client = self.app.api_client

      if api_client:
        # Fetch stats
        stats = api_client.get_dashboard_stats()

        # Update status panel
        status_panel = self.query_one("#status_panel", StatusPanel)
        status_panel.update_stats(stats)

        log.debug(f"Dashboard stats loaded: {stats}")
      else:
        log.warning("No API client available")

    except Exception as e:
      log.error(f"Failed to load dashboard data: {e}")

  def action_show_integrations(self) -> None:
    """Switch to integrations screen."""
    log.debug("Switching to integrations screen")
    self.app.push_screen("integrations")

  def action_show_enrollments(self) -> None:
    """Switch to enrollments screen."""
    log.debug("Switching to enrollments screen")
    self.app.push_screen("enrollments")

  def action_show_audit(self) -> None:
    """Switch to audit log screen."""
    log.debug("Switching to audit screen")
    # TODO: Implement screen switching

  def action_refresh(self) -> None:
    """Manually refresh dashboard data."""
    log.debug("Manual refresh triggered")
    self._load_dashboard_data()

  def action_logout(self) -> None:
    """Logout and exit."""
    log.debug("Logging out")
    if hasattr(self.app, "logout_and_exit"):
      self.app.logout_and_exit()
    else:
      self.app.exit()

  def action_quit(self) -> None:
    """Quit the application."""
    self.app.exit()
