"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

TUI Module: Crypto Decrypt Screen
Description: Decrypt encrypted values via Crypto API
"""

from textual.widgets import Header, Footer, Label, Button, TextArea
from textual.containers import Vertical, Horizontal
from textual.binding import Binding
import logging

from .crypto_common import CryptoActionScreen

log = logging.getLogger(__name__)


class CryptoDecryptScreen(CryptoActionScreen):
  """Screen for decrypting encrypted values."""

  BINDINGS = CryptoActionScreen.BINDINGS + [
      Binding("r", "run", "Decrypt"),
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
      yield Label("Decrypt Value")
      yield Label("", id="status_label")
      with Horizontal():
        with Vertical(id="input_panel"):
          yield Label("Encrypted Value")
          yield TextArea(id="encrypted_input")
        with Vertical(id="output_panel"):
          yield Label("Plaintext")
          yield TextArea(id="plaintext_output")
      with Horizontal(id="button_row"):
        yield Button("Decrypt", id="decrypt_btn", variant="primary")
        yield Button("Copy", id="copy_btn")
        yield Button("Back", id="back_btn")
    yield Footer()

  def on_button_pressed(self, event: Button.Pressed) -> None:
    if event.button.id == "decrypt_btn":
      self.action_run()
    elif event.button.id == "copy_btn":
      self.action_copy()
    elif event.button.id == "back_btn":
      self.action_back()

  def action_run(self) -> None:
    encrypted_value = self.query_one("#encrypted_input", TextArea).text.strip()
    if not encrypted_value:
      self._set_status("Encrypted value is required")
      return
    client = self._get_crypto_client()
    if not client:
      return
    response = client.decrypt(encrypted_value)
    if not response:
      self._set_status(client.last_error_message or "Failed to decrypt value")
      return
    plaintext = response.get("plaintext", "")
    is_encrypted = response.get("isEncrypted")
    decryption_successful = response.get("decryptionSuccessful")
    error_message = response.get("errorMessage")
    result = f"{plaintext}"
    if decryption_successful is False and error_message:
      result = f"{plaintext}\nerror: {error_message}"
    result += f"\nisEncrypted: {is_encrypted}\n" \
              f"decryptionSuccessful: {decryption_successful}"
    self.query_one("#plaintext_output", TextArea).text = result
    self._set_status("Decryption complete")

  def action_copy(self) -> None:
    plaintext = self.query_one("#plaintext_output", TextArea).text
    self._copy_to_clipboard(plaintext)
