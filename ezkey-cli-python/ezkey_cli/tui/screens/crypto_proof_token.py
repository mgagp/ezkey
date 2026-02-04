"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

TUI Module: Crypto Proof Token Screen
Description: Generate proof tokens via Crypto API
"""

from textual.widgets import Header, Footer, Label, Button, TextArea
from textual.containers import Vertical, Horizontal
from textual.binding import Binding
import logging

from .crypto_common import CryptoActionScreen

log = logging.getLogger(__name__)


class CryptoProofTokenScreen(CryptoActionScreen):
  """Screen for generating proof tokens."""

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
      yield Label("Generate Proof Token")
      yield Label("", id="status_label")
      with Vertical(id="output"):
        yield Label("Proof Token")
        yield TextArea(id="proof_token_output")
      with Horizontal(id="button_row"):
        yield Button("Generate", id="generate_btn", variant="primary")
        yield Button("Copy", id="copy_btn")
        yield Button("Back", id="back_btn")
    yield Footer()

  def on_button_pressed(self, event: Button.Pressed) -> None:
    if event.button.id == "generate_btn":
      self.action_run()
    elif event.button.id == "copy_btn":
      self.action_copy()
    elif event.button.id == "back_btn":
      self.action_back()

  def action_run(self) -> None:
    client = self._get_crypto_client()
    if not client:
      return
    response = client.generate_proof_token()
    if not response:
      self._set_status(client.last_error_message or "Failed to generate proof token")
      return
    token = response.get("proofToken", "")
    output = self.query_one("#proof_token_output", TextArea)
    output.text = token
    self._set_status("Proof token generated")

  def action_copy(self) -> None:
    output = self.query_one("#proof_token_output", TextArea)
    self._copy_to_clipboard(output.text)
