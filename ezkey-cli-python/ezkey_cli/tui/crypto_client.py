"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

TUI Module: Crypto API Client
Description: HTTP client for Crypto API operations
"""

import logging
from typing import Optional, Dict, Any

import requests

log = logging.getLogger(__name__)


class CryptoApiClient:
  """HTTP client for Ezkey Crypto API (no authentication)."""

  def __init__(self, crypto_url: str, verify_ssl: bool = True):
    """
    Initialize crypto API client.

    Args:
        crypto_url: Base URL for Crypto API (e.g., http://localhost:9090)
        verify_ssl: Whether to verify SSL certificates
    """
    self.crypto_url = crypto_url.rstrip('/')
    self.verify_ssl = verify_ssl
    self.last_error_message = None
    self.last_status_code = None

  def _get(self, endpoint: str) -> Optional[Dict[str, Any]]:
    """Make GET request to Crypto API."""
    try:
      self.last_error_message = None
      self.last_status_code = None
      url = f"{self.crypto_url}{endpoint}"
      log.info(f"GET {url}")

      response = requests.get(url, timeout=10, verify=self.verify_ssl)
      self.last_status_code = response.status_code

      response.raise_for_status()
      return response.json() if response.text else {}

    except Exception as e:
      self.last_error_message = str(e)
      log.error(f"Crypto GET {endpoint} failed: {e}")
      return None

  def _post(self, endpoint: str, data: Dict[str, Any]) -> Optional[Dict[str, Any]]:
    """Make POST request to Crypto API."""
    try:
      self.last_error_message = None
      self.last_status_code = None
      url = f"{self.crypto_url}{endpoint}"
      log.info(f"POST {url}")

      response = requests.post(url, json=data, timeout=10, verify=self.verify_ssl)
      self.last_status_code = response.status_code

      response.raise_for_status()
      return response.json() if response.text else {}

    except Exception as e:
      self.last_error_message = str(e)
      log.error(f"Crypto POST {endpoint} failed: {e}")
      return None

  def generate_proof_token(self) -> Optional[Dict[str, Any]]:
    """Generate a proof token."""
    return self._get("/api/v1/crypto/prooftoken")

  def generate_keypair(self) -> Optional[Dict[str, Any]]:
    """Generate an EC P-256 key pair."""
    return self._get("/api/v1/crypto/keypair")

  def sign(self, data: str, private_key: str) -> Optional[Dict[str, Any]]:
    """Sign data with private key."""
    return self._post("/api/v1/crypto/sign", {
        "data": data,
        "privateKey": private_key
    })

  def validate(self, data: str, signature: str, public_key: str) -> Optional[Dict[str, Any]]:
    """Validate signature with public key."""
    return self._post("/api/v1/crypto/validate", {
        "data": data,
        "signature": signature,
        "publicKey": public_key
    })

  def encrypt(self, plaintext: str) -> Optional[Dict[str, Any]]:
    """Encrypt plaintext value."""
    return self._post("/api/v1/crypto/encrypt", {
        "plaintext": plaintext
    })

  def decrypt(self, encrypted_value: str) -> Optional[Dict[str, Any]]:
    """Decrypt encrypted value."""
    return self._post("/api/v1/crypto/decrypt", {
        "encryptedValue": encrypted_value
    })
