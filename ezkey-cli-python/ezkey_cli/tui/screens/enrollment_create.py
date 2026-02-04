"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

TUI Module: Enrollment Create Modal
Description: Modal form for creating new enrollments
"""

from textual.screen import ModalScreen
from textual.widgets import Button, Input, Label, Checkbox
from textual.binding import Binding
from textual.containers import Vertical, Horizontal, Container
from textual.validation import Length
import logging

log = logging.getLogger(__name__)


class CreateEnrollmentModal(ModalScreen):
  """Modal screen for creating a new enrollment."""

  BINDINGS = [
      Binding("escape", "cancel", "Cancel"),
  ]

  CSS = """
  CreateEnrollmentModal {
      align: center middle;
  }

  #modal_container {
      width: 75;
      height: auto;
      border: thick $primary;
      background: $surface;
      padding: 1 2;
  }

  .form_row {
      height: auto;
      margin: 1 0;
      width: 1fr;
  }

  .label {
      width: 1fr;
      color: $text-muted;
  }

  Input {
      width: 1fr;
  }

  #button_row {
      height: auto;
      margin-top: 2;
      align: center middle;
  }

  Button {
      margin: 0 1;
  }

  #error_label {
      color: $error;
      margin: 1 0;
      display: none;
      width: 1fr;
  }
  """

  def __init__(self, *args, **kwargs):
    super().__init__(*args, **kwargs)
    self._error_message = ""

  def compose(self):
    """Compose the modal."""
    with Container(id="modal_container"):
      yield Label("Create New Enrollment", id="title")

      with Vertical(classes="form_row"):
        yield Label("Integration ID:", classes="label")
        yield Input(placeholder="e.g., 1", id="integration_id", validators=[Length(1, 10)])

      with Vertical(classes="form_row"):
        yield Label("Enrollment Name:", classes="label")
        yield Input(placeholder="e.g., John's iPhone", id="enrollment_name", validators=[Length(1, 100)])

      with Vertical(classes="form_row"):
        yield Checkbox("Auth Challenge Required", value=False, id="challenge_required")

      yield Label("", id="error_label")

      with Horizontal(id="button_row"):
        yield Button("Create", variant="primary", id="create_btn")
        yield Button("Cancel", variant="default", id="cancel_btn")

  def on_button_pressed(self, event: Button.Pressed) -> None:
    """Handle button press."""
    if event.button.id == "create_btn":
      self._create_enrollment()
    elif event.button.id == "cancel_btn":
      self.action_cancel()

  def _create_enrollment(self) -> None:
    """Create enrollment via API."""
    integration_id_value = self.query_one("#integration_id", Input).value.strip()
    enrollment_name = self.query_one("#enrollment_name", Input).value.strip()
    challenge_required = self.query_one("#challenge_required", Checkbox).value

    # Clear previous error
    self._show_error("")

    if not integration_id_value.isdigit():
      self._show_error("Integration ID must be a number")
      return

    if not enrollment_name:
      self._show_error("Enrollment name is required")
      return

    payload = {
        "integrationId": int(integration_id_value),
        "name": enrollment_name,
        "authAttemptChallengeRequired": challenge_required
    }

    api_client = self.app.api_client
    if not api_client:
      self._show_error("No API client available")
      return

    try:
      response = api_client._post("/api/v1/enrollments", payload)

      if response:
        enrollment_id = response.get("enrollmentId")
        log.info(f"Enrollment created: {enrollment_id}")
        self.dismiss(True)
      else:
        self._show_error("Failed to create enrollment (no response from API)")
    except Exception as e:
      error_msg = str(e)
      log.error(f"Error creating enrollment: {e}")
      self._show_error(error_msg)

  def _show_error(self, message: str) -> None:
    """Show error message in modal."""
    self._error_message = message
    error_label = self.query_one("#error_label", Label)
    if message:
      error_label.update(f"❌ {message}")
      error_label.display = True
    else:
      error_label.update("")
      error_label.display = False

  def action_cancel(self) -> None:
    """Cancel and close modal."""
    log.debug("Create enrollment cancelled")
    self.dismiss(False)
