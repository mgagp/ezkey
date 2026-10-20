"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

Test Component: TLS verification policy (#802)
Description: Default verify=True, --ca-bundle forwarding, loopback-only --insecure
"""

from __future__ import annotations

import ast
from pathlib import Path
from unittest.mock import Mock, patch

import pytest

from ezkey_cli.auth.auth_manager import AuthManager
from ezkey_cli.config.config_manager import ConfigManager
from ezkey_cli.utils.http_client import HttpClient
from ezkey_cli.utils.tls import (
  INSECURE_WARN_MESSAGE,
  InsecureTlsNotAllowedError,
  is_loopback_url,
  resolve_ssl_verify,
  ssl_verify_from_config,
)


class TestLoopbackDetection:
  """Loopback host detection used to gate --insecure."""

  @pytest.mark.parametrize(
      "url",
      [
          "https://localhost:9080",
          "http://127.0.0.1:9080",
          "https://[::1]:9080",
          "localhost:9080",
      ],
  )
  def test_loopback_urls(self, url):
    assert is_loopback_url(url) is True

  @pytest.mark.parametrize(
      "url",
      [
          "https://admin.example.com",
          "https://192.168.1.10:9080",
          "https://docker:9080",
          "",
      ],
  )
  def test_non_loopback_urls(self, url):
    assert is_loopback_url(url) is False


class TestResolveSslVerify:
  """resolve_ssl_verify precedence and guards."""

  def test_default_is_true(self, monkeypatch):
    monkeypatch.delenv("EZKEY_CLI_INSECURE", raising=False)
    monkeypatch.delenv("EZKEY_CA_BUNDLE", raising=False)
    monkeypatch.delenv("REQUESTS_CA_BUNDLE", raising=False)
    assert resolve_ssl_verify("https://admin.example.com") is True

  def test_ca_bundle_argument(self, monkeypatch, tmp_path):
    monkeypatch.delenv("EZKEY_CLI_INSECURE", raising=False)
    bundle = tmp_path / "ca.pem"
    bundle.write_text("dummy", encoding="utf-8")
    assert resolve_ssl_verify(
        "https://admin.example.com",
        ca_bundle=str(bundle),
    ) == str(bundle)

  def test_ca_bundle_env_ezkey(self, monkeypatch, tmp_path):
    monkeypatch.delenv("EZKEY_CLI_INSECURE", raising=False)
    monkeypatch.delenv("REQUESTS_CA_BUNDLE", raising=False)
    bundle = tmp_path / "ezkey-ca.pem"
    bundle.write_text("dummy", encoding="utf-8")
    monkeypatch.setenv("EZKEY_CA_BUNDLE", str(bundle))
    assert resolve_ssl_verify("https://localhost:9080") == str(bundle)

  def test_ca_bundle_env_requests(self, monkeypatch, tmp_path):
    monkeypatch.delenv("EZKEY_CLI_INSECURE", raising=False)
    monkeypatch.delenv("EZKEY_CA_BUNDLE", raising=False)
    bundle = tmp_path / "requests-ca.pem"
    bundle.write_text("dummy", encoding="utf-8")
    monkeypatch.setenv("REQUESTS_CA_BUNDLE", str(bundle))
    assert resolve_ssl_verify("https://localhost:9080") == str(bundle)

  def test_ca_bundle_preferred_over_insecure(self, monkeypatch, tmp_path):
    bundle = tmp_path / "ca.pem"
    bundle.write_text("dummy", encoding="utf-8")
    result = resolve_ssl_verify(
        "https://localhost:9080",
        insecure=True,
        ca_bundle=str(bundle),
        warn=False,
    )
    assert result == str(bundle)

  def test_insecure_loopback_accepted_with_warning(self, monkeypatch, capsys):
    monkeypatch.delenv("EZKEY_CA_BUNDLE", raising=False)
    monkeypatch.delenv("REQUESTS_CA_BUNDLE", raising=False)
    with pytest.warns(UserWarning, match="TLS certificate verification is DISABLED"):
      result = resolve_ssl_verify("https://localhost:9080", insecure=True)
    assert result is False
    err = capsys.readouterr().err
    assert "TLS certificate verification is DISABLED" in err

  def test_insecure_non_loopback_refused(self, monkeypatch):
    monkeypatch.delenv("EZKEY_CA_BUNDLE", raising=False)
    monkeypatch.delenv("REQUESTS_CA_BUNDLE", raising=False)
    with pytest.raises(InsecureTlsNotAllowedError, match="loopback"):
      resolve_ssl_verify("https://admin.example.com", insecure=True, warn=False)

  def test_insecure_env_loopback(self, monkeypatch):
    monkeypatch.delenv("EZKEY_CA_BUNDLE", raising=False)
    monkeypatch.delenv("REQUESTS_CA_BUNDLE", raising=False)
    monkeypatch.setenv("EZKEY_CLI_INSECURE", "1")
    assert resolve_ssl_verify("http://127.0.0.1:9080", warn=False) is False

  def test_hostname_alone_does_not_disable_verify(self, monkeypatch):
    """Dev-mode hostname inference must not disable TLS (#802)."""
    monkeypatch.delenv("EZKEY_CLI_INSECURE", raising=False)
    monkeypatch.delenv("EZKEY_CA_BUNDLE", raising=False)
    monkeypatch.delenv("REQUESTS_CA_BUNDLE", raising=False)
    assert resolve_ssl_verify("https://localhost:9080") is True
    assert resolve_ssl_verify("https://docker:9080") is True


class TestAuthManagerTls:
  """AuthManager passes resolved verify= to requests."""

  @patch("ezkey_cli.utils.tls.requests.post")
  def test_login_default_verify_true(self, mock_post):
    mock_response = Mock()
    mock_response.text = '{"token": "t"}'
    mock_response.json.return_value = {"token": "t"}
    mock_post.return_value = mock_response

    manager = AuthManager("https://localhost:9080")
    manager.login("admin")

    assert mock_post.call_args.kwargs["verify"] is True

  @patch("ezkey_cli.utils.tls.requests.post")
  def test_login_ca_bundle_forwarded(self, mock_post, tmp_path):
    mock_response = Mock()
    mock_response.text = "{}"
    mock_response.json.return_value = {}
    mock_post.return_value = mock_response

    bundle = str(tmp_path / "ca.pem")
    manager = AuthManager("https://localhost:9080", verify_ssl=bundle)
    manager.login("admin")

    assert mock_post.call_args.kwargs["verify"] == bundle

  @patch("ezkey_cli.utils.tls.requests.post")
  def test_wait_default_verify_true(self, mock_post):
    mock_response = Mock()
    mock_response.text = '{"token": "t"}'
    mock_response.json.return_value = {"token": "t"}
    mock_post.return_value = mock_response

    manager = AuthManager("https://localhost:9080")
    manager.wait_for_challenge(1, waiter_secret="s")

    assert mock_post.call_args.kwargs["verify"] is True


class TestHttpClientTls:
  """CLI HttpClient (login/recover) applies TLS settings from config."""

  def test_session_verify_default_true(self, tmp_path, monkeypatch):
    home = tmp_path / "home"
    home.mkdir()
    work = tmp_path / "work"
    work.mkdir()
    monkeypatch.setattr(Path, "home", lambda: home)
    monkeypatch.chdir(work)
    monkeypatch.delenv("EZKEY_CLI_INSECURE", raising=False)
    monkeypatch.delenv("EZKEY_CA_BUNDLE", raising=False)
    monkeypatch.delenv("REQUESTS_CA_BUNDLE", raising=False)

    config = ConfigManager()
    client = HttpClient(config)
    assert client.session.verify is True

  def test_session_verify_ca_bundle(self, tmp_path, monkeypatch):
    home = tmp_path / "home"
    home.mkdir()
    work = tmp_path / "work"
    work.mkdir()
    monkeypatch.setattr(Path, "home", lambda: home)
    monkeypatch.chdir(work)
    monkeypatch.delenv("EZKEY_CLI_INSECURE", raising=False)

    bundle = tmp_path / "ca.pem"
    bundle.write_text("dummy", encoding="utf-8")
    config = ConfigManager()
    config.set("sslCaBundle", str(bundle))
    client = HttpClient(config)
    assert client.session.verify == str(bundle)

  def test_session_insecure_non_loopback_refused(self, tmp_path, monkeypatch):
    home = tmp_path / "home"
    home.mkdir()
    work = tmp_path / "work"
    work.mkdir()
    monkeypatch.setattr(Path, "home", lambda: home)
    monkeypatch.chdir(work)
    monkeypatch.delenv("EZKEY_CA_BUNDLE", raising=False)
    monkeypatch.delenv("REQUESTS_CA_BUNDLE", raising=False)

    config = ConfigManager()
    config.set("adminUrl", "https://admin.example.com")
    config.set("sslInsecure", True)
    with pytest.raises(InsecureTlsNotAllowedError):
      HttpClient(config)


class TestTuiReauthUsesSharedVerify:
  """TUI reauth screens thread api_client.verify_ssl into AuthManager."""

  @patch("ezkey_cli.tui.screens.reauth.AuthManager", create=True)
  def test_reauth_threads_verify(self, _unused):
    from ezkey_cli.auth.auth_manager import AuthManager as RealAuthManager
    from ezkey_cli.tui.screens.reauth import ReAuthScreen

    api_client = Mock()
    api_client.verify_ssl = "/custom/ca.pem"
    config = Mock()
    config.is_login_blocking.return_value = False

    screen = ReAuthScreen(config, "https://localhost:9080", api_client, mode="login")
    screen.username = "admin"
    screen._show_error = Mock()

    with patch(
        "ezkey_cli.auth.auth_manager.AuthManager",
        wraps=RealAuthManager,
    ) as mock_am_cls:
      with patch.object(RealAuthManager, "login", return_value=None):
        screen._start_authentication()

    mock_am_cls.assert_called_once_with(
        "https://localhost:9080",
        verify_ssl="/custom/ca.pem",
    )
    assert screen.auth_manager.verify_ssl == "/custom/ca.pem"

  def test_quick_reauth_threads_verify(self):
    from ezkey_cli.auth.auth_manager import AuthManager as RealAuthManager
    from ezkey_cli.tui.screens.quick_reauth import QuickReAuthScreen

    api_client = Mock()
    api_client.verify_ssl = True
    config = Mock()
    config.get_admin_username.return_value = "admin"

    screen = QuickReAuthScreen(config, "https://localhost:9080", api_client)
    screen._show_error = Mock()
    screen._save_token = Mock()

    with patch(
        "ezkey_cli.auth.auth_manager.AuthManager",
        wraps=RealAuthManager,
    ) as mock_am_cls:
      with patch.object(
          RealAuthManager,
          "login",
          return_value={"success": True, "token": "t"},
      ):
        screen._start_authentication("admin")

    mock_am_cls.assert_called_once_with(
        "https://localhost:9080",
        verify_ssl=True,
    )
    assert screen.auth_manager.verify_ssl is True


class TestNoSilentVerifyFalse:
  """AST guard: no verify=False / verify_ssl=False outside the intentional TLS helper."""

  ALLOWED_FILES = {
      "ezkey_cli/utils/tls.py",
  }

  def test_no_hardcoded_verify_false_outside_tls_helper(self):
    root = Path(__file__).resolve().parents[2] / "ezkey_cli"
    offenders = []

    for path in root.rglob("*.py"):
      rel = path.relative_to(root.parent).as_posix()
      if rel in self.ALLOWED_FILES:
        continue
      tree = ast.parse(path.read_text(encoding="utf-8"), filename=str(path))
      for node in ast.walk(tree):
        if isinstance(node, ast.keyword) and node.arg in {"verify", "verify_ssl"}:
          if isinstance(node.value, ast.Constant) and node.value.value is False:
            offenders.append(f"{rel}:{node.lineno}")
        if isinstance(node, ast.Assign):
          for target in node.targets:
            if (
                isinstance(target, ast.Attribute)
                and target.attr == "verify"
                and isinstance(node.value, ast.Constant)
                and node.value.value is False
            ):
              offenders.append(f"{rel}:{node.lineno}")

    assert offenders == [], f"Hard-coded verify=False outside tls helper: {offenders}"


class TestSslVerifyFromConfig:
  """ConfigManager keys for TLS."""

  def test_from_config_defaults(self, monkeypatch):
    monkeypatch.delenv("EZKEY_CLI_INSECURE", raising=False)
    monkeypatch.delenv("EZKEY_CA_BUNDLE", raising=False)
    monkeypatch.delenv("REQUESTS_CA_BUNDLE", raising=False)
    config = Mock()
    config.get.side_effect = lambda key, default=None: {
        "adminUrl": "https://localhost:9080",
        "sslInsecure": None,
        "sslCaBundle": None,
    }.get(key, default)
    assert ssl_verify_from_config(config) is True

  def test_warn_message_constant(self):
    assert "loopback" in INSECURE_WARN_MESSAGE.lower()
