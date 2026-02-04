"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

TUI Module: API Key Created Modal
Description: Displays newly created API key secrets (shown once)
"""

from textual.screen import ModalScreen
from textual.widgets import Button, Label
from textual.containers import Vertical, Horizontal, Container
import logging

log = logging.getLogger(__name__)


class ApiKeyCreatedModal(ModalScreen):
  """Modal screen to display API key secrets once."""

  CSS = """
  ApiKeyCreatedModal {
      align: center middle;
  }

  #modal_container {
      width: 80;
      height: auto;
      border: thick $warning;
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

    #status_label {
      width: 1fr;
      height: auto;
      margin: 1 0 0 0;
      color: $success;
    }

  Button {
      margin: 0 1;
  }
  """

  def __init__(self, integration_key: str, secret_key: str, warning: str, *args, **kwargs):
    super().__init__(*args, **kwargs)
    self.integration_key = integration_key
    self.secret_key = secret_key
    self.warning = warning

  def compose(self):
    """Compose the modal."""
    message = (
        "✅ API key created. Save these credentials now:\n\n"
        f"Integration Key: {self.integration_key}\n"
        f"Secret Key: {self.secret_key}\n\n"
        f"{self.warning or 'Secret key is shown once and cannot be recovered.'}"
    )

    with Container(id="modal_container"):
      yield Label("API Key Created", id="title")
      yield Label(message, id="message")
      yield Label("", id="status_label")
      with Horizontal(id="button_row"):
        yield Button("Copy Integration", variant="default", id="copy_integration_btn")
        yield Button("Copy Secret", variant="default", id="copy_secret_btn")
        yield Button("Copy Both", variant="default", id="copy_both_btn")
        yield Button("OK", variant="primary", id="ok_btn")

  def on_button_pressed(self, event: Button.Pressed) -> None:
    """Handle button press."""
    if event.button.id == "copy_integration_btn":
      self._copy_to_clipboard(self.integration_key)
    elif event.button.id == "copy_secret_btn":
      self._copy_to_clipboard(self.secret_key)
    elif event.button.id == "copy_both_btn":
      self._copy_to_clipboard(
          f"integrationKey={self.integration_key}\nsecretKey={self.secret_key}"
      )
    elif event.button.id == "ok_btn":
      self.dismiss(True)

  def _copy_to_clipboard(self, text: str) -> None:
    """Copy text to clipboard and show status."""
    status = self.query_one("#status_label", Label)
    if not text:
      status.update("Nothing to copy")
      return

    try:
      import pyperclip

      pyperclip.copy(text)
      status.update("Copied to clipboard")
      return
    except Exception as e:
      log.warning(f"Clipboard copy failed (pyperclip): {e}")

    # Fallback to Tkinter clipboard
    try:
      import tkinter as tk

      root = tk.Tk()
      root.withdraw()
      root.clipboard_clear()
      root.clipboard_append(text)
      root.update()
      root.destroy()
      status.update("Copied to clipboard")
    except Exception as e:
      log.error(f"Clipboard copy failed (tkinter): {e}")
      status.update("Copy failed: clipboard unavailable")
