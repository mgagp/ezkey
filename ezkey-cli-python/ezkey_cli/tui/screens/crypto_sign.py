"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

TUI Module: Crypto Sign Screen
Description: Sign data with EC P-256 private key
"""

from textual.widgets import Header, Footer, Label, Button, TextArea
from textual.containers import Vertical, Horizontal
from textual.binding import Binding
import logging

from .crypto_common import CryptoActionScreen

log = logging.getLogger(__name__)


class CryptoSignScreen(CryptoActionScreen):
  """Screen for signing data."""

  BINDINGS = CryptoActionScreen.BINDINGS + [
      Binding("r", "run", "Sign"),
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
      yield Label("Sign Data (EC P-256)")
      yield Label("", id="status_label")
      with Horizontal():
        with Vertical(id="input_panel"):
          yield Label("Data")
          yield TextArea(id="data_input")
          yield Label("Private Key")
          yield TextArea(id="private_key_input")
        with Vertical(id="output_panel"):
          yield Label("Signature")
          yield TextArea(id="signature_output")
      with Horizontal(id="button_row"):
        yield Button("Sign", id="sign_btn", variant="primary")
        yield Button("Copy Signature", id="copy_btn")
        yield Button("Back", id="back_btn")
    yield Footer()

  def on_button_pressed(self, event: Button.Pressed) -> None:
    if event.button.id == "sign_btn":
      self.action_run()
    elif event.button.id == "copy_btn":
      self.action_copy()
    elif event.button.id == "back_btn":
      self.action_back()

  def action_run(self) -> None:
    data = self.query_one("#data_input", TextArea).text.strip()
    private_key = self.query_one("#private_key_input", TextArea).text.strip()
    if not data or not private_key:
      self._set_status("Data and private key are required")
      return
    client = self._get_crypto_client()
    if not client:
      return
    response = client.sign(data, private_key)
    if not response:
      self._set_status(client.last_error_message or "Failed to sign data")
      return
    signature = response.get("signature", "")
    self.query_one("#signature_output", TextArea).text = signature
    self._set_status("Data signed")

  def action_copy(self) -> None:
    signature = self.query_one("#signature_output", TextArea).text
    self._copy_to_clipboard(signature)
