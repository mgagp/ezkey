"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

TUI Module: Crypto Home Screen
Description: Landing screen for Crypto API tools
"""

from textual.screen import Screen
from textual.widgets import Header, Footer, Label, Button
from textual.containers import Vertical, Horizontal
from textual.binding import Binding
import logging

log = logging.getLogger(__name__)


class CryptoHomeScreen(Screen):
  """Crypto tools home screen."""

  BINDINGS = [
      Binding("1", "proof_token", "Proof Token"),
      Binding("2", "key_pair", "Key Pair"),
      Binding("3", "sign", "Sign"),
      Binding("4", "validate", "Validate"),
      Binding("5", "encrypt", "Encrypt"),
      Binding("6", "decrypt", "Decrypt"),
      Binding("h", "home", "Home"),
      Binding("q", "quit", "Quit"),
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

  #menu {
      border: solid $accent;
      padding: 1 2;
      height: auto;
  }

  Button {
      width: 1fr;
      margin: 1 0;
  }
  """

  def compose(self):
    """Compose crypto home screen."""
    yield Header(show_clock=True)
    with Vertical(id="content"):
      yield Label("Crypto Tools")
      with Vertical(id="menu"):
        yield Button("1) Proof Token", id="proof_token")
        yield Button("2) Key Pair", id="key_pair")
        yield Button("3) Sign", id="sign")
        yield Button("4) Validate", id="validate")
        yield Button("5) Encrypt", id="encrypt")
        yield Button("6) Decrypt", id="decrypt")
    yield Footer()

  def on_button_pressed(self, event: Button.Pressed) -> None:
    """Handle menu selection."""
    button_id = event.button.id
    if button_id == "proof_token":
      self.action_proof_token()
    elif button_id == "key_pair":
      self.action_key_pair()
    elif button_id == "sign":
      self.action_sign()
    elif button_id == "validate":
      self.action_validate()
    elif button_id == "encrypt":
      self.action_encrypt()
    elif button_id == "decrypt":
      self.action_decrypt()

  def action_proof_token(self) -> None:
    self.app.push_screen("crypto_proof_token")

  def action_key_pair(self) -> None:
    self.app.push_screen("crypto_keypair")

  def action_sign(self) -> None:
    self.app.push_screen("crypto_sign")

  def action_validate(self) -> None:
    self.app.push_screen("crypto_validate")

  def action_encrypt(self) -> None:
    self.app.push_screen("crypto_encrypt")

  def action_decrypt(self) -> None:
    self.app.push_screen("crypto_decrypt")

  def action_home(self) -> None:
    self.app.pop_screen()
    self.app.push_screen("home")

  def action_quit(self) -> None:
    self.app.exit()
