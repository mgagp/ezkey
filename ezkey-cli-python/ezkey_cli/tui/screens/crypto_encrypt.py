"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

TUI Module: Crypto Encrypt Screen
Description: Encrypt plaintext values via Crypto API
"""

from textual.widgets import Header, Footer, Label, Button, TextArea
from textual.containers import Vertical, Horizontal
from textual.binding import Binding
import logging

from .crypto_common import CryptoActionScreen

log = logging.getLogger(__name__)


class CryptoEncryptScreen(CryptoActionScreen):
  """Screen for encrypting plaintext."""

  BINDINGS = CryptoActionScreen.BINDINGS + [
      Binding("r", "run", "Encrypt"),
      Binding("c", "copy", "Copy"),
  ]

  CSS = """
  Screen {
      layout: vertical;
      background: $surface;
  }

  #content {
      height: 1fr;
      padding: 1 2;
  }

  #input_panel, #output_panel {
      border: solid $primary;
      padding: 1 2;
      height: auto;
      width: 1fr;
  }

  TextArea {
      height: 5;
  }

  #button_row {
      height: auto;
      margin-top: 1;
  }
  """

  def compose(self):
    yield Header(show_clock=True)
    with Vertical(id="content"):
      yield Label("Encrypt Plaintext")
      yield Label("", id="status_label")
      with Horizontal():
        with Vertical(id="input_panel"):
          yield Label("Plaintext")
          yield TextArea(id="plaintext_input")
        with Vertical(id="output_panel"):
          yield Label("Encrypted Value")
          yield TextArea(id="encrypted_output")
      with Horizontal(id="button_row"):
        yield Button("Encrypt", id="encrypt_btn", variant="primary")
        yield Button("Copy", id="copy_btn")
        yield Button("Back", id="back_btn")
    yield Footer()

  def on_button_pressed(self, event: Button.Pressed) -> None:
    if event.button.id == "encrypt_btn":
      self.action_run()
    elif event.button.id == "copy_btn":
      self.action_copy()
    elif event.button.id == "back_btn":
      self.action_back()

  def action_run(self) -> None:
    plaintext = self.query_one("#plaintext_input", TextArea).text.strip()
    if not plaintext:
      self._set_status("Plaintext is required")
      return
    client = self._get_crypto_client()
    if not client:
      return
    response = client.encrypt(plaintext)
    if not response:
      self._set_status(client.last_error_message or "Failed to encrypt value")
      return
    encrypted_value = response.get("encryptedValue", "")
    encryption_successful = response.get("encryptionSuccessful")
    error_message = response.get("errorMessage")
    result = encrypted_value
    if encryption_successful is False and error_message:
      result = f"{encrypted_value}\nerror: {error_message}"
    self.query_one("#encrypted_output", TextArea).text = result
    self._set_status("Encryption complete")

  def action_copy(self) -> None:
    encrypted_value = self.query_one("#encrypted_output", TextArea).text
    self._copy_to_clipboard(encrypted_value)
