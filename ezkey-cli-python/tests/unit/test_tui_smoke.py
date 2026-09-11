"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

Test Component: TUI smoke and foundation unit tests
Description: Imports, ConfigManager bearer token, legacy migration, AuthManager HTTP calls
"""

import json
import sys
from pathlib import Path
from unittest.mock import Mock, patch

import pytest

from ezkey_cli.auth.auth_manager import AuthManager
from ezkey_cli.config.config_manager import ConfigManager
from ezkey_cli.tui.app import EzkeyAdminApp


@pytest.fixture
def isolated_config(tmp_path, monkeypatch):
  """ConfigManager with home and cwd under a temporary directory."""
  home = tmp_path / "home"
  home.mkdir()
  work = tmp_path / "work"
  work.mkdir()
  monkeypatch.setattr(Path, "home", lambda: home)
  monkeypatch.chdir(work)
  return ConfigManager()


class TestTuiImports:
  """Smoke imports for the read-only TUI surface."""

  def test_import_tui_entrypoint(self):
    from ezkey_cli.tui import start_tui

    assert callable(start_tui)

  def test_import_in_scope_screens(self):
    from ezkey_cli.tui.screens import (
      HomeScreen,
      IntegrationsScreen,
      EnrollmentsScreen,
      AuditLogsScreen,
      AuthAttemptsScreen,
      TenantsScreen,
      ReAuthScreen,
      QuickReAuthScreen,
    )

    for screen_cls in (
      HomeScreen,
      IntegrationsScreen,
      EnrollmentsScreen,
      AuditLogsScreen,
      AuthAttemptsScreen,
      TenantsScreen,
      ReAuthScreen,
      QuickReAuthScreen,
    ):
      assert screen_cls is not None

  def test_import_widgets(self):
    from ezkey_cli.tui.widgets import HeaderWidget, SidebarWidget

    assert HeaderWidget is not None
    assert SidebarWidget is not None


class TestConfigManagerBearerToken:
  """Bearer token persistence via shared CLI config."""

  def test_set_get_and_clear_bearer_token(self, isolated_config):
    isolated_config.set_bearer_token("test-token-123")
    assert isolated_config.get("bearerToken") == "test-token-123"

    isolated_config.clear_bearer_token()
    assert isolated_config.get("bearerToken") is None

  def test_save_and_reload_bearer_token(self, isolated_config):
    isolated_config.set_bearer_token("persisted-token")
    isolated_config.set_admin_username("admin.docker")
    isolated_config.save(global_config=True)

    reloaded = ConfigManager()
    assert reloaded.get("bearerToken") == "persisted-token"
    assert reloaded.get_admin_username() == "admin.docker"

  def test_token_expiration_fields(self, isolated_config):
    isolated_config.set_token_expires_at("2026-12-31T23:59:59Z")
    assert isolated_config.get_token_expires_at() == "2026-12-31T23:59:59Z"

    isolated_config.clear_token_expires_at()
    assert isolated_config.get_token_expires_at() is None


class TestLegacyBearerTokenMigration:
  """Legacy ~/.ezkey/admin/bearer-token migration into ConfigManager."""

  def test_migrates_legacy_file_to_config(self, tmp_path, monkeypatch):
    home = tmp_path / "home"
    home.mkdir()
    work = tmp_path / "work"
    work.mkdir()
    legacy_dir = home / ".ezkey" / "admin"
    legacy_dir.mkdir(parents=True)
    legacy_file = legacy_dir / "bearer-token"
    legacy_file.write_text("legacy-token-xyz", encoding="utf-8")

    monkeypatch.setattr(Path, "home", lambda: home)
    monkeypatch.chdir(work)

    config = ConfigManager()
    app = EzkeyAdminApp(config)
    token = app._load_bearer_token()

    assert token == "legacy-token-xyz"
    assert config.get("bearerToken") == "legacy-token-xyz"
    assert not legacy_file.exists()

    global_config = home / ".ezkey" / "ezkey.json"
    assert global_config.exists()
    saved = json.loads(global_config.read_text(encoding="utf-8"))
    assert saved["bearerToken"] == "legacy-token-xyz"


class TestAuthManager:
  """Passwordless auth HTTP delegation (mocked requests)."""

  @patch("ezkey_cli.auth.auth_manager.requests.post")
  def test_login_posts_expected_payload(self, mock_post):
    mock_response = Mock()
    mock_response.text = '{"authAttemptId": 42, "challengeCode": 7}'
    mock_response.json.return_value = {"authAttemptId": 42, "challengeCode": 7}
    mock_post.return_value = mock_response

    manager = AuthManager("http://localhost:9080", verify_ssl=False)
    result = manager.login("admin.docker", non_blocking=True)

    assert result == {"authAttemptId": 42, "challengeCode": 7}
    mock_post.assert_called_once_with(
      "http://localhost:9080/api/v1/admin/auth/login",
      json={
        "username": "admin.docker",
        "challengeRequested": False,
        "nonBlocking": True,
      },
      timeout=360,
      verify=False,
    )

  @patch("ezkey_cli.auth.auth_manager.requests.post")
  def test_wait_for_challenge_posts_expected_payload(self, mock_post):
    mock_response = Mock()
    mock_response.text = '{"token": "approved-token"}'
    mock_response.json.return_value = {"token": "approved-token"}
    mock_post.return_value = mock_response

    manager = AuthManager("http://localhost:9080", verify_ssl=False)
    result = manager.wait_for_challenge(42, challenge_code=7, waiter_secret="ws-secret")

    assert result == {"token": "approved-token"}
    mock_post.assert_called_once_with(
      "http://localhost:9080/api/v1/admin/auth/passwordless-wait",
      json={"authAttemptId": 42, "challengeCode": 7, "waiterSecret": "ws-secret"},
      timeout=365,
      verify=False,
    )

  @patch("ezkey_cli.auth.auth_manager.requests.post")
  def test_wait_for_challenge_non_blocking_sends_waiter_secret_without_challenge(self, mock_post):
    mock_response = Mock()
    mock_response.text = '{"token": "approved-token"}'
    mock_response.json.return_value = {"token": "approved-token"}
    mock_post.return_value = mock_response

    manager = AuthManager("http://localhost:9080", verify_ssl=False)
    manager.wait_for_challenge(42, waiter_secret="ws-secret")

    assert mock_post.call_args.kwargs["json"] == {"authAttemptId": 42, "waiterSecret": "ws-secret"}

  @patch("ezkey_cli.auth.auth_manager.requests.post", side_effect=TimeoutError("network"))
  def test_login_returns_none_on_request_failure(self, _mock_post):
    manager = AuthManager("http://localhost:9080", verify_ssl=False)
    assert manager.login("admin.docker") is None
