"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

Auth Module: Bearer Token Manager
Description: Manages bearer token persistence for admin CLI/TUI
"""

import os
from pathlib import Path
from typing import Optional
import logging

log = logging.getLogger(__name__)


class TokenManager:
  """
  Manages admin bearer token persistence.

  Stores bearer token in ~/.ezkey/admin/bearer-token with restricted permissions.
  """

  TOKEN_DIR = Path.home() / ".ezkey" / "admin"
  TOKEN_FILE = TOKEN_DIR / "bearer-token"

  def __init__(self):
    """Initialize token manager."""
    self.TOKEN_DIR.mkdir(parents=True, exist_ok=True)

  def save_token(self, token: str) -> bool:
    """
    Save bearer token to disk.

    Args:
        token: Bearer token string

    Returns:
        True if saved successfully
    """
    try:
      with open(self.TOKEN_FILE, 'w') as f:
        f.write(token.strip())

      os.chmod(self.TOKEN_FILE, 0o600)
      log.debug("Bearer token saved to %s", self.TOKEN_FILE)
      return True

    except Exception as e:
      log.error("Failed to save bearer token: %s", e)
      return False

  def load_token(self) -> Optional[str]:
    """
    Load bearer token from disk.

    Returns:
        Bearer token string, or None if not found
    """
    if not self.TOKEN_FILE.exists():
      return None

    try:
      with open(self.TOKEN_FILE, 'r') as f:
        token = f.read().strip()

      if token:
        log.debug("Bearer token loaded from %s", self.TOKEN_FILE)
        return token

      return None

    except Exception as e:
      log.error("Failed to load bearer token: %s", e)
      return None

  def clear_token(self) -> None:
    """Delete bearer token file."""
    if self.TOKEN_FILE.exists():
      self.TOKEN_FILE.unlink()
      log.debug("Bearer token cleared")

  def has_token(self) -> bool:
    """Check if bearer token exists."""
    return self.TOKEN_FILE.exists() and self.load_token() is not None
