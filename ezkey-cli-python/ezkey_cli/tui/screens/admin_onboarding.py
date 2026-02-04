"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

TUI Module: Admin Onboarding Credentials Screen
Description: Screen for displaying admin onboarding credentials (enrollmentProofToken, challenge, recovery codes)
"""

from textual.screen import Screen
from textual.widgets import Header, Footer, Label, Button, Static
from textual.binding import Binding
from textual.containers import Vertical, Horizontal, Container
import logging
from typing import Optional, List, Dict, Any

log = logging.getLogger(__name__)


class AdminOnboardingScreen(Screen):
  """Screen for displaying admin onboarding credentials."""

  BINDINGS = [
      Binding("q", "quit", "Back"),
      Binding("ctrl+c", "copy_token", "Copy Token"),
  ]

  CSS = """
  Screen {
      layout: vertical;
      background: $surface;
  }

  #content {
      height: 1fr;
      padding: 2 4;
      overflow: auto;
  }

  #title {
      margin-bottom: 1;
      text-align: center;
  }

  .section {
      border: solid $accent;
      padding: 1 2;
      margin: 1 0;
      height: auto;
  }

  .section_title {
      color: $accent;
      margin-bottom: 1;
      text-align: left;
  }

  .credential_field {
      margin: 1 0;
      height: auto;
  }

  .field_label {
      color: $text-muted;
  }

  .credential_value {
      color: $warning;
      background: $panel;
      padding: 0 2;
      margin-top: 0;
      width: 1fr;
  }

  .recovery_codes {
      background: $panel;
      padding: 1 2;
      margin-top: 1;
      width: 1fr;
  }

  .recovery_code {
      color: $warning;
      margin: 0 0;
  }

  #button_row {
      height: auto;
      margin-top: 2;
      align: center middle;
  }

  Button {
      margin: 0 1;
  }

  #warning_section {
      border: solid $error;
      background: rgba(255, 0, 0, 0.1);
      padding: 1 2;
      margin: 2 0;
  }

  .warning_title {
      color: $error;
      margin-bottom: 1;
  }

  .warning_text {
      color: $error;
      margin: 0 0;
  }
  """

  def __init__(self, admin_data: Dict[str, Any], *args, **kwargs):
    super().__init__(*args, **kwargs)
    self.admin_data = admin_data
    self.admin_id = admin_data.get("admin_id")
    self.enrollment_id = admin_data.get("enrollment_id")
    self.credentials = admin_data.get("credentials", {})
    self.admin_type = admin_data.get("admin_type", "GLOBAL_ADMIN")

  def compose(self):
    """Compose the onboarding screen."""
    yield Header(show_clock=True)

    with Vertical(id="content"):
      yield Label("✅ Administrator Created Successfully!", id="title")

      # Summary section
      with Vertical(classes="section"):
        yield Label("Admin Details", classes="section_title")
        yield Label(f"Admin ID: {str(self.admin_id)}", classes="credential_field")
        yield Label(f"Enrollment ID: {str(self.enrollment_id)}", classes="credential_field")
        yield Label(f"Type: {str(self.admin_type)}", classes="credential_field")

      # Enrollment Proof Token
      token = self.credentials.get("enrollmentProofToken", "")
      if token:
        with Vertical(classes="section"):
          yield Label("Enrollment Proof Token", classes="section_title")
          yield Label(
              "Use this token to bind the device. Save it securely!",
              classes="field_label"
          )
          yield Label(str(token), classes="credential_value")

      # Challenge Code
      challenge = self.credentials.get("enrollmentChallenge", "")
      if challenge:
        with Vertical(classes="section"):
          yield Label("Enrollment Challenge Code", classes="section_title")
          yield Label(
              "6-digit code to enter on the device",
              classes="field_label"
          )
          yield Label(str(challenge), classes="credential_value")

      # Recovery Codes
      recovery_codes = self.credentials.get("recoveryCodes", [])
      if recovery_codes and isinstance(recovery_codes, list):
        with Vertical(classes="section"):
          yield Label("Recovery Codes (106-bit entropy)", classes="section_title")
          yield Label(
              "Single-use codes for emergency access. Save these securely in a password manager!",
              classes="field_label"
          )
          with Vertical(classes="recovery_codes"):
            for i, code in enumerate(recovery_codes, 1):
              yield Label(f"{i:2d}. {code}", classes="recovery_code")

      # Critical warning
      with Vertical(id="warning_section"):
        yield Label("⚠️ CRITICAL SECURITY NOTICE", classes="warning_title")
        yield Label(
            "✓ Copy enrollment proof token and challenge code now",
            classes="warning_text"
        )
        yield Label(
            "✓ Save recovery codes in secure password manager immediately",
            classes="warning_text"
        )
        yield Label(
            "✓ Recovery codes are SINGLE-USE ONLY",
            classes="warning_text"
        )
        yield Label(
            "✓ These credentials cannot be retrieved later",
            classes="warning_text"
        )

    with Horizontal(id="button_row"):
      yield Button("Back to Admins", variant="primary", id="back_btn")

    yield Footer()

  def on_button_pressed(self, event: Button.Pressed) -> None:
    """Handle button press."""
    if event.button.id == "back_btn":
      self.app.pop_screen()

  def action_copy_token(self) -> None:
    """Copy token to clipboard (basic implementation)."""
    token = self.credentials.get("enrollmentProofToken", "")
    if token:
      log.info(f"Token to copy (length: {len(token)})")
      # Note: Textual doesn't have built-in clipboard support
      # In production, this would need external library or OS-specific code
