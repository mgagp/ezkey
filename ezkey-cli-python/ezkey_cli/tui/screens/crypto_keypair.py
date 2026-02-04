"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

TUI Module: Crypto Key Pair Screen
Description: Generate EC P-256 key pairs via Crypto API
"""

from textual.widgets import Header, Footer, Label, Button, TextArea
from textual.containers import Vertical, Horizontal
from textual.binding import Binding
import logging

from .crypto_common import CryptoActionScreen

log = logging.getLogger(__name__)


class CryptoKeyPairScreen(CryptoActionScreen):
  """Screen for generating EC P-256 key pairs."""

  BINDINGS = CryptoActionScreen.BINDINGS + [
      Binding("r", "run", "Generate"),
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

  #output {
      border: solid $primary;
      padding: 1 2;
      height: auto;
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
      yield Label("Generate Key Pair (EC P-256)")
      yield Label("", id="status_label")
      with Vertical(id="output"):
        yield Label("Public Key")
        yield TextArea(id="public_key_output")
        yield Label("Private Key")
        yield TextArea(id="private_key_output")
      with Horizontal(id="button_row"):
        yield Button("Generate", id="generate_btn", variant="primary")
        yield Button("Copy Public", id="copy_public_btn")
        yield Button("Copy Private", id="copy_private_btn")
        yield Button("Back", id="back_btn")
    yield Footer()

  def on_button_pressed(self, event: Button.Pressed) -> None:
    if event.button.id == "generate_btn":
      self.action_run()
    elif event.button.id == "copy_public_btn":
      self._copy_to_clipboard(self.query_one("#public_key_output", TextArea).text)
    elif event.button.id == "copy_private_btn":
      self._copy_to_clipboard(self.query_one("#private_key_output", TextArea).text)
    elif event.button.id == "back_btn":
      self.action_back()

  def action_run(self) -> None:
    client = self._get_crypto_client()
    if not client:
      return
    response = client.generate_keypair()
    if not response:
      self._set_status(client.last_error_message or "Failed to generate key pair")
      return
    self.query_one("#public_key_output", TextArea).text = response.get("publicKey", "")
    self.query_one("#private_key_output", TextArea).text = response.get("privateKey", "")
    self._set_status("Key pair generated")
