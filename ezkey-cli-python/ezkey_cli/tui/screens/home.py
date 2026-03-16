"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

TUI Module: Home Dashboard Screen
Description: Main dashboard showing status overview
"""

from textual.screen import Screen
from textual.widgets import Header, Footer, Static, Label
from textual.containers import Vertical, Horizontal
from textual.binding import Binding
from textual.reactive import reactive
import logging
from typing import List, Dict, Any, Optional
from datetime import datetime, timezone

log = logging.getLogger(__name__)


class StatusPanel(Static):
  """Status overview panel."""

  # Reactive attributes for live updates
  integrations_total = reactive(0)
  integrations_active = reactive(0)
  integrations_inactive = reactive(0)
  enrollments_total = reactive(0)
  enrollments_verified = reactive(0)
  enrollments_bound = reactive(0)
  enrollments_created = reactive(0)
  enrollments_invalid = reactive(0)
  auth_total_24h = reactive(0)
  auth_accepted_24h = reactive(0)
  auth_failed_24h = reactive(0)
  auth_pending_24h = reactive(0)
  auth_failure_rate = reactive(0.0)

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
        f"Integrations: {self.integrations_total}"
        f"  (active {self.integrations_active} / inactive {self.integrations_inactive})\n"
        f"Enrollments: {self.enrollments_total}"
        f"  (VER {self.enrollments_verified} / BND {self.enrollments_bound}"
        f" / CRT {self.enrollments_created} / INV {self.enrollments_invalid})\n"
        f"Auth Attempts (24h): {self.auth_total_24h}"
        f"  (ACC {self.auth_accepted_24h} / FAIL {self.auth_failed_24h}"
        f" / PEND {self.auth_pending_24h})\n"
        f"Failure rate: {self.auth_failure_rate:.1f}%"
    )

  def update_stats(self, stats: dict) -> None:
    """Update statistics from API."""
    if not stats or not isinstance(stats, dict):
      # If stats is None or invalid, use defaults
      stats = {}

    self.integrations_total = stats.get("integrations_total", 0) or 0
    self.integrations_active = stats.get("integrations_active", 0) or 0
    self.integrations_inactive = stats.get("integrations_inactive", 0) or 0
    self.enrollments_total = stats.get("enrollments_total", 0) or 0
    self.enrollments_verified = stats.get("enrollments_verified", 0) or 0
    self.enrollments_bound = stats.get("enrollments_bound", 0) or 0
    self.enrollments_created = stats.get("enrollments_created", 0) or 0
    self.enrollments_invalid = stats.get("enrollments_invalid", 0) or 0
    self.auth_total_24h = stats.get("auth_total_24h", 0) or 0
    self.auth_accepted_24h = stats.get("auth_accepted_24h", 0) or 0
    self.auth_failed_24h = stats.get("auth_failed_24h", 0) or 0
    self.auth_pending_24h = stats.get("auth_pending_24h", 0) or 0
    if self.auth_total_24h > 0:
      self.auth_failure_rate = (self.auth_failed_24h / self.auth_total_24h) * 100
    else:
      self.auth_failure_rate = 0.0


class ActionPanel(Static):
  """Action required panel."""

  pending_over_5m = reactive(0)
  enrollments_created_over_24h = reactive(0)
  api_keys_expiring_30d = reactive(0)

  DEFAULT_CSS = """
  ActionPanel {
      border: solid $warning;
      padding: 1 2;
      height: auto;
  }
  """

  def render(self) -> str:
    """Render the action required panel."""
    return (
        f"🚨 Action Required\n\n"
        f"Pending >5m: {self.pending_over_5m}\n"
        f"Enrollments CREATED >24h: {self.enrollments_created_over_24h}\n"
        f"API keys expiring <30d: {self.api_keys_expiring_30d}"
    )

  def update_actions(self, actions: Dict[str, Any]) -> None:
    """Update action counts from API."""
    if not actions or not isinstance(actions, dict):
      actions = {}
    self.pending_over_5m = actions.get("pending_over_5m", 0) or 0
    self.enrollments_created_over_24h = actions.get("enrollments_created_over_24h", 0) or 0
    self.api_keys_expiring_30d = actions.get("api_keys_expiring_30d", 0) or 0


class ActivityPanel(Static):
  """Recent activity feed panel."""

  activities = reactive([])

  DEFAULT_CSS = """
  ActivityPanel {
      border: solid $accent;
      padding: 1 2;
      height: auto;
  }
  """

  def render(self) -> str:
    """Render the activity panel."""
    items = self.activities or ["No recent activity"]
    body = "\n".join(f"• {item}" for item in items)
    return f"📋 Recent Activity\n\n{body}"

  def update_activity(self, activities: List[str]) -> None:
    """Update recent activity items."""
    if not activities or not isinstance(activities, list):
      self.activities = ["No recent activity"]
      return
    self.activities = activities


class SecurityPanel(Static):
  """Security snapshot panel."""

  login_failures_24h = reactive(0)
  recoveries_7d = reactive(0)
  keys_revoked_7d = reactive(0)

  DEFAULT_CSS = """
  SecurityPanel {
      border: solid $error;
      padding: 1 2;
      height: auto;
  }
  """

  def render(self) -> str:
    """Render the security panel."""
    return (
        f"🔐 Security Snapshot\n\n"
        f"Login failures (24h): {self.login_failures_24h}\n"
        f"Recoveries (7d): {self.recoveries_7d}\n"
        f"Keys revoked (7d): {self.keys_revoked_7d}"
    )

  def update_security(self, security: Dict[str, Any]) -> None:
    """Update security metrics."""
    if not security or not isinstance(security, dict):
      security = {}
    self.login_failures_24h = security.get("login_failures_24h", 0) or 0
    self.recoveries_7d = security.get("recoveries_7d", 0) or 0
    self.keys_revoked_7d = security.get("keys_revoked_7d", 0) or 0


class QuickActionsPanel(Static):
  """Quick actions panel."""

  DEFAULT_CSS = """
  QuickActionsPanel {
      border: solid $primary;
      padding: 1 2;
      height: auto;
  }
  """

  def render(self) -> str:
    """Render quick actions (read-only investigation)."""
    return (
        "⚡ Quick Actions (read-only)\n\n"
        "A - Audit Logs\n"
        "T - Auth Attempts\n"
        "E - Enrollments\n"
        "I - Integrations\n"
        "N - Tenants\n"
        "R - Refresh"
    )


class InfoPanel(Static):
  """Info panel for API endpoint, session status, scope, and username."""

  username_line = reactive("User: unknown")
  api_url_line = reactive("Admin API: unknown")
  session_line = reactive("Session: unknown")
  scope_line = reactive("Scope: unknown")

  DEFAULT_CSS = """
  InfoPanel {
      border: solid $accent;
      padding: 1 2;
      height: auto;
  }
  """

  def render(self) -> str:
    """Render info panel."""
    return (
        "ℹ️ Context\n\n"
        f"{self.username_line}\n"
        f"{self.api_url_line}\n"
        f"{self.session_line}\n"
        f"{self.scope_line}"
    )


class HomeScreen(Screen):
  """Home dashboard screen with status overview."""

  BINDINGS = [
      Binding("a", "show_audit", "Audit Logs"),
      Binding("t", "show_auth_attempts", "Auth Attempts"),
      Binding("e", "show_enrollments", "Enrollments"),
      Binding("i", "show_integrations", "Integrations"),
      Binding("n", "show_tenants", "Tenants"),
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

    #row_top {
      height: auto;
    }

    #row_mid {
      height: 1fr;
    }

    #row_bottom {
      height: auto;
    }

  StatusPanel {
      width: 1fr;
      height: auto;
      margin: 1 1 1 0;
  }

    ActionPanel {
      width: 1fr;
      height: auto;
      margin: 1 0 1 1;
  }

    ActivityPanel {
      width: 1fr;
      height: 1fr;
      margin: 1 0;
    }

    SecurityPanel {
      width: 1fr;
      height: auto;
      margin: 1 1 1 0;
    }

    QuickActionsPanel {
      width: 1fr;
      height: auto;
      margin: 1 0 1 1;
    }

      InfoPanel {
        width: 1fr;
        height: auto;
        margin: 1 0;
      }
  """

  def compose(self):
    """Compose the home screen."""
    yield Header(show_clock=True)

    with Vertical(id="main_container"):
      yield Label("Welcome to Ezkey Admin Console")
      yield Label("", id="status_label")
      with Horizontal(id="row_top"):
        yield StatusPanel(id="status_panel")
        yield ActionPanel(id="action_panel")
      with Horizontal(id="row_mid"):
        yield ActivityPanel(id="activity_panel")
      with Horizontal(id="row_bottom"):
        yield SecurityPanel(id="security_panel")
        yield QuickActionsPanel(id="quick_actions_panel")
      yield InfoPanel(id="info_panel")

    yield Footer()

  def on_mount(self) -> None:
    """Called when screen is mounted - load data from API."""
    log.debug("HomeScreen mounted")

    # Initialize info panel with config values
    info_panel = self.query_one("#info_panel", InfoPanel)
    info_panel.username_line = self._format_username()
    info_panel.api_url_line = self._format_api_url()
    info_panel.session_line = self._format_session_status()
    info_panel.scope_line = self._format_scope_status()

    self._load_dashboard_data()
    refresh_seconds = self._get_refresh_seconds()
    self.set_interval(refresh_seconds, self._refresh_dashboard)

  def _refresh_dashboard(self) -> None:
    """Periodic refresh of dashboard data."""
    self._load_dashboard_data()

  def _load_dashboard_data(self) -> None:
    """Load dashboard data from unified Dashboard API (same as Admin UI)."""
    try:
      api_client = self.app.api_client

      if not api_client:
        log.warning("No API client available")
        self._set_status("No API client available")
        return

      overview = api_client.get_dashboard_overview()
      if overview is None and api_client.last_auth_error:
        log.warning("Dashboard auth error")
        if hasattr(self.app, "handle_auth_error"):
          self.app.handle_auth_error()
        return

      if not overview:
        self._set_status("Failed to load dashboard data")
        return

      pending_count = api_client.get_auth_attempts_pending_count()
      if pending_count is None and api_client.last_auth_error:
        pending_count = 0

      snapshot = self._snapshot_from_overview(overview, pending_count or 0)
      self._set_status("")

      status_panel = self.query_one("#status_panel", StatusPanel)
      status_panel.update_stats(snapshot.get("status", {}))

      action_panel = self.query_one("#action_panel", ActionPanel)
      action_panel.update_actions(snapshot.get("actions", {}))

      activity_panel = self.query_one("#activity_panel", ActivityPanel)
      activity_panel.update_activity(snapshot.get("activity", []))

      security_panel = self.query_one("#security_panel", SecurityPanel)
      security_panel.update_security(snapshot.get("security", {}))

      info_panel = self.query_one("#info_panel", InfoPanel)
      info_panel.api_url_line = self._format_api_url()
      info_panel.session_line = self._format_session_status()
      info_panel.scope_line = self._format_scope_status()

      log.debug("Dashboard overview loaded")

    except Exception as e:
      log.error(f"Failed to load dashboard data: {e}")
      self._set_status("Failed to load dashboard data")

  def _snapshot_from_overview(
      self,
      overview: Dict[str, Any],
      pending_count: int
  ) -> Dict[str, Any]:
    """Build panel snapshot from GET /api/v1/dashboard/overview + pending count."""
    status: Dict[str, Any] = {
        "integrations_total": 0,
        "integrations_active": 0,
        "integrations_inactive": 0,
        "enrollments_total": 0,
        "enrollments_verified": 0,
        "enrollments_bound": 0,
        "enrollments_created": 0,
        "enrollments_invalid": 0,
        "auth_total_24h": 0,
        "auth_accepted_24h": 0,
        "auth_failed_24h": 0,
        "auth_pending_24h": 0,
    }
    actions: Dict[str, Any] = {
        "pending_over_5m": 0,
        "enrollments_created_over_24h": 0,
        "api_keys_expiring_30d": 0,
    }
    activity: List[str] = []
    security: Dict[str, Any] = {
        "login_failures_24h": 0,
        "recoveries_7d": 0,
        "keys_revoked_7d": 0,
    }

    ints = overview.get("integrations") or {}
    if isinstance(ints, dict):
      status["integrations_total"] = int(ints.get("total") or 0)
      status["integrations_active"] = int(ints.get("active") or 0)
      status["integrations_inactive"] = int(ints.get("inactive") or 0)

    enr = overview.get("enrollments") or {}
    if isinstance(enr, dict):
      status["enrollments_total"] = int(enr.get("total") or 0)
      status["enrollments_verified"] = int(enr.get("verified") or 0)
      status["enrollments_bound"] = int(enr.get("bound") or 0)
      status["enrollments_created"] = int(enr.get("created") or 0)

    auth = overview.get("auth24h") or {}
    if isinstance(auth, dict):
      status["auth_total_24h"] = int(auth.get("total") or 0)
      status["auth_accepted_24h"] = int(auth.get("accepted") or 0)
      status["auth_failed_24h"] = int(auth.get("rejected") or 0)
    status["auth_pending_24h"] = pending_count

    actions["pending_over_5m"] = pending_count

    recent = overview.get("recentActivity") or []
    if isinstance(recent, list):
      activity = self._format_recent_activity(recent, limit=10)
    if not activity:
      activity = ["No recent activity"]

    return {
        "status": status,
        "actions": actions,
        "activity": activity,
        "security": security,
    }

  def _format_recent_activity(self, items: List[Dict[str, Any]], limit: int = 10) -> List[str]:
    """Format dashboard recentActivity items for the activity panel."""
    lines = []
    for item in items[:limit]:
      if not isinstance(item, dict):
        continue
      created = item.get("createdAt")
      time_str = "--:--"
      if created:
        try:
          dt = datetime.fromisoformat(str(created).replace("Z", "+00:00"))
          time_str = dt.strftime("%m-%d %H:%M")
        except (ValueError, TypeError):
          pass
      event_type = (item.get("eventType") or "UNKNOWN").strip()
      event_status = (item.get("eventStatus") or "-").strip()
      admin_id = item.get("adminId")
      parts = [time_str, event_type, event_status]
      if admin_id is not None:
        parts.append(f"admin:{admin_id}")
      lines.append(" ".join(str(p) for p in parts))
    return lines

  def _set_status(self, message: str) -> None:
    """Set status label text."""
    status_label = self.query_one("#status_label", Label)
    status_label.update(message)

  def _get_refresh_seconds(self) -> int:
    """Get dashboard refresh interval from config."""
    config = getattr(self.app, "config", None)
    if not config:
      return 30
    value = config.get("dashboardRefreshSeconds", 30)
    try:
      seconds = int(value)
      return max(10, min(seconds, 300))
    except (TypeError, ValueError):
      return 30

  def _format_api_url(self) -> str:
    """Format Admin API URL label."""
    config = getattr(self.app, "config", None)
    if not config:
      return "Admin API: unknown"
    admin_url = config.get("adminUrl")
    if not admin_url:
      return "Admin API: unknown"
    return f"Admin API: {admin_url}"

  def _format_username(self) -> str:
    """Format username label."""
    config = getattr(self.app, "config", None)
    if not config:
      return "User: unknown"
    username = config.get_admin_username()
    if not username:
      return "User: unknown"
    return f"User: {username}"

  def _format_session_status(self) -> str:
    """Format token/session expiry label."""
    config = getattr(self.app, "config", None)
    if not config:
      return "Session: unknown"
    expires_at = config.get_token_expires_at()
    if not expires_at:
      return "Session: no expiry info"
    parsed = self._parse_datetime(expires_at)
    if not parsed:
      return f"Session: expires at {expires_at}"
    remaining = int((parsed - datetime.now(timezone.utc)).total_seconds())
    if remaining <= 0:
      return "Session: expired"
    return f"Session: expires in {self._format_duration(remaining)}"

  def _format_scope_status(self) -> str:
    """Format admin scope label."""
    config = getattr(self.app, "config", None)
    if not config:
      return "Scope: unknown"
    admin_type = config.get_admin_type() or "UNKNOWN"
    if admin_type == "TENANT_ADMIN":
      return "Scope: TENANT (tenant-scoped)"
    if admin_type == "GLOBAL_ADMIN":
      return "Scope: GLOBAL"
    return f"Scope: {admin_type}"

  def _parse_datetime(self, value: str) -> Optional[datetime]:
    """Parse ISO-8601 datetime with optional Z suffix."""
    if not value or not isinstance(value, str):
      return None
    try:
      if value.endswith("Z"):
        value = value.replace("Z", "+00:00")
      return datetime.fromisoformat(value)
    except Exception:
      return None

  def _format_duration(self, seconds: int) -> str:
    """Format seconds into compact duration string."""
    if seconds < 60:
      return f"{seconds}s"
    minutes, sec = divmod(seconds, 60)
    if minutes < 60:
      return f"{minutes}m"
    hours, minutes = divmod(minutes, 60)
    if hours < 24:
      return f"{hours}h {minutes}m"
    days, hours = divmod(hours, 24)
    return f"{days}d {hours}h"

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
    self.app.push_screen("audit_logs")

  def action_show_auth_attempts(self) -> None:
    """Switch to auth attempts screen."""
    log.debug("Switching to auth attempts screen")
    self.app.push_screen("auth_attempts")

  def action_show_tenants(self) -> None:
    """Switch to tenants screen."""
    log.debug("Switching to tenants screen")
    self.app.push_screen("tenants")

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
