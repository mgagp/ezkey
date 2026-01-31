"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

TUI Module: Crypto Validate Screen
Description: Validate signatures using public key
"""

from textual.widgets import Header, Footer, Label, Button, TextArea
from textual.containers import Vertical, Horizontal
from textual.binding import Binding
import logging

from .crypto_common import CryptoActionScreen

log = logging.getLogger(__name__)


class CryptoValidateScreen(CryptoActionScreen):
  """Screen for validating signatures."""

  BINDINGS = CryptoActionScreen.BINDINGS + [
      Binding("r", "run", "Validate"),
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
      yield Label("Validate Signature (EC P-256)")
      yield Label("", id="status_label")
      with Horizontal():
        with Vertical(id="input_panel"):
          yield Label("Data")
          yield TextArea(id="data_input")
          yield Label("Signature")
          yield TextArea(id="signature_input")
          yield Label("Public Key")
          yield TextArea(id="public_key_input")
        with Vertical(id="output_panel"):
          yield Label("Result")
          yield TextArea(id="result_output")
      with Horizontal(id="button_row"):
        yield Button("Validate", id="validate_btn", variant="primary")
        yield Button("Back", id="back_btn")
    yield Footer()

  def on_button_pressed(self, event: Button.Pressed) -> None:
    if event.button.id == "validate_btn":
      self.action_run()
    elif event.button.id == "back_btn":
      self.action_back()

  def action_run(self) -> None:
    data = self.query_one("#data_input", TextArea).text.strip()
    signature = self.query_one("#signature_input", TextArea).text.strip()
    public_key = self.query_one("#public_key_input", TextArea).text.strip()
    if not data or not signature or not public_key:
      self._set_status("Data, signature, and public key are required")
      return
    client = self._get_crypto_client()
    if not client:
      return
    response = client.validate(data, signature, public_key)
    if not response:
      self._set_status(client.last_error_message or "Failed to validate signature")
      return
    valid = response.get("valid")
    message = response.get("message", "")
    algorithm = response.get("algorithm", "")
    result_text = f"valid: {valid}\nmessage: {message}\nalgorithm: {algorithm}"
    self.query_one("#result_output", TextArea).text = result_text
    self._set_status("Validation complete")
