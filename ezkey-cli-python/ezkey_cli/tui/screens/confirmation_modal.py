"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

TUI Module: Confirmation Modal
Description: Generic confirmation dialog for delete/critical actions
"""

from textual.screen import ModalScreen
from textual.widgets import Button, Label, Static
from textual.containers import Vertical, Horizontal, Container
import logging

log = logging.getLogger(__name__)


class ConfirmationModal(ModalScreen):
  """Generic confirmation modal for delete/critical actions."""

  CSS = """
  ConfirmationModal {
      align: center middle;
  }

  #modal_container {
      width: 50;
      height: auto;
      border: thick $error;
      background: $surface;
      padding: 1 2;
  }

  #message {
      width: 1fr;
      height: auto;
      margin: 1 0;
  }

  #button_row {
      height: auto;
      margin-top: 2;
      align: center middle;
  }

  Button {
      margin: 0 1;
  }
  """

  def __init__(self, title: str, message: str, *args, **kwargs):
    super().__init__(*args, **kwargs)
    self.title = title
    self.message = message

  def compose(self):
    """Compose the modal."""
    with Container(id="modal_container"):
      yield Label(self.title, id="title")
      yield Label(self.message, id="message")

      with Horizontal(id="button_row"):
        yield Button("Confirm", variant="error", id="confirm_btn")
        yield Button("Cancel", variant="default", id="cancel_btn")

  def on_button_pressed(self, event: Button.Pressed) -> None:
    """Handle button press."""
    if event.button.id == "confirm_btn":
      self.dismiss(True)
    elif event.button.id == "cancel_btn":
      self.dismiss(False)
