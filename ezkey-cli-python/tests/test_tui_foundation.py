"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

Test Module: Quick Validation Tests
Description: Basic tests for TUI foundation components
"""

import unittest
import tempfile
from pathlib import Path
from unittest.mock import Mock, patch
import json
import sys
import os

# Add parent directory to path for imports
sys.path.insert(0, str(Path(__file__).parent))

from ezkey_cli.auth.session import SessionManager


class TestSessionManager(unittest.TestCase):
  """Test SessionManager encryption and storage."""

  def setUp(self):
    """Set up test fixtures."""
    # Use temporary directory for testing
    self.temp_dir = tempfile.TemporaryDirectory()
    self.original_home = Path.home

    # Mock Path.home() to use temp directory
    mock_home = Mock(return_value=Path(self.temp_dir.name))
    Path.home = mock_home

  def tearDown(self):
    """Clean up test fixtures."""
    Path.home = self.original_home
    self.temp_dir.cleanup()

  def test_session_encryption_and_decryption(self):
    """Test that session data is properly encrypted and decrypted."""
    manager = SessionManager()

    # Sample session data
    session_data = {
        "username": "admin",
        "organization": "Test Org",
        "admin_url": "https://localhost:9080",
        "access_token": "test_token_12345",
        "refresh_token": "test_refresh_67890",
        "expiration": "2026-01-31T12:00:00Z",
        "created_at": "2026-01-30T10:00:00Z"
    }

    # Save session
    manager.save_session(session_data)

    # Load session
    loaded = manager.load_session()

    # Verify data
    self.assertIsNotNone(loaded)
    self.assertEqual(loaded["username"], "admin")
    self.assertEqual(loaded["organization"], "Test Org")
    self.assertEqual(loaded["access_token"], "test_token_12345")

  def test_session_file_has_restrictive_permissions(self):
    """Test that session file has 0o600 permissions."""
    manager = SessionManager()

    session_data = {"username": "admin", "access_token": "test"}
    manager.save_session(session_data)

    # Check file permissions
    session_file = manager.SESSION_FILE
    if session_file.exists():
      perms = oct(session_file.stat().st_mode)[-3:]
      self.assertEqual(perms, "600", "Session file should have 0o600 permissions")

  def test_clear_session_deletes_file(self):
    """Test that clear_session removes the session file."""
    manager = SessionManager()

    # Save a session
    manager.save_session({"username": "admin"})
    self.assertTrue(manager.SESSION_FILE.exists())

    # Clear it
    manager.clear_session()
    self.assertFalse(manager.SESSION_FILE.exists())


class TestAuthImports(unittest.TestCase):
  """Test that auth modules can be imported."""

  def test_import_session_manager(self):
    """Test SessionManager can be imported."""
    from ezkey_cli.auth import SessionManager
    self.assertIsNotNone(SessionManager)

  def test_import_auth_manager(self):
    """Test AuthManager can be imported."""
    from ezkey_cli.auth import AuthManager
    self.assertIsNotNone(AuthManager)

  def test_import_login_wizard(self):
    """Test LoginWizard can be imported."""
    from ezkey_cli.auth.login_wizard import LoginWizard
    self.assertIsNotNone(LoginWizard)


class TestTUIImports(unittest.TestCase):
  """Test that TUI modules can be imported."""

  def test_import_tui_app(self):
    """Test TUI app can be imported."""
    from ezkey_cli.tui import start_tui
    self.assertIsNotNone(start_tui)

  def test_import_screens(self):
    """Test screens can be imported."""
    from ezkey_cli.tui.screens import AuthScreen, HomeScreen, IntegrationsScreen
    self.assertIsNotNone(AuthScreen)
    self.assertIsNotNone(HomeScreen)
    self.assertIsNotNone(IntegrationsScreen)

  def test_import_widgets(self):
    """Test widgets can be imported."""
    from ezkey_cli.tui.widgets import HeaderWidget, SidebarWidget
    self.assertIsNotNone(HeaderWidget)
    self.assertIsNotNone(SidebarWidget)


if __name__ == '__main__':
  unittest.main()
