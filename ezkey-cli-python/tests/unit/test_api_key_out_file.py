"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

Test Component: api-key create --out-file (#1043)
Description: Mode 600 write, content match, no secret on stdout/stderr, overwrite refusal
"""

from __future__ import annotations

import os
import stat
from pathlib import Path
from unittest.mock import patch

import pytest
from click.testing import CliRunner

from ezkey_cli.commands.admin import create_api_key
from ezkey_cli.config.config_manager import ConfigManager
from ezkey_cli.utils.http_client import ApiResponse
from ezkey_cli.utils.secure_file import (
  SecureFileExistsError,
  format_api_key_env,
  write_secret_file,
)

# Placeholders only — never real credentials, hosts, or paths from a live env.
PLACEHOLDER_IKEY = "ezkey_ikey_TESTPLACEHOLDER0001"
PLACEHOLDER_SKEY = "ezkey_skey_TESTPLACEHOLDER0001SECRETVALUE"
PLACEHOLDER_API_KEY_ID = 42


def _creation_payload():
  return {
      "apiKeyId": PLACEHOLDER_API_KEY_ID,
      "integrationKey": PLACEHOLDER_IKEY,
      "secretKey": PLACEHOLDER_SKEY,
      "description": "placeholder-key",
      "createdAt": "2026-01-01T00:00:00Z",
      "expiresAt": None,
      "ipWhitelist": None,
      "warning": "IMPORTANT: Save the secret key now. It will not be shown again.",
  }


def _posix_mode(path: Path) -> int:
  return stat.S_IMODE(path.stat().st_mode)


class TestSecureFileWriter:
  """Direct unit coverage for atomic mode-600 writes."""

  def test_write_creates_mode_600_and_content(self, tmp_path):
    dest = tmp_path / "creds.env"
    body = format_api_key_env(PLACEHOLDER_IKEY, PLACEHOLDER_SKEY)
    written = write_secret_file(dest, body, overwrite=False)

    assert written == dest.resolve()
    assert dest.is_file()
    assert dest.read_text(encoding="utf-8") == body
    if os.name == "posix":
      assert _posix_mode(dest) == 0o600

  def test_write_refuses_overwrite_without_flag(self, tmp_path):
    dest = tmp_path / "creds.env"
    dest.write_text("existing\n", encoding="utf-8")

    with pytest.raises(SecureFileExistsError):
      write_secret_file(dest, format_api_key_env(PLACEHOLDER_IKEY, PLACEHOLDER_SKEY))

    assert dest.read_text(encoding="utf-8") == "existing\n"

  def test_write_force_replaces_atomically(self, tmp_path):
    dest = tmp_path / "creds.env"
    dest.write_text("stale\n", encoding="utf-8")
    body = format_api_key_env(PLACEHOLDER_IKEY, PLACEHOLDER_SKEY)

    write_secret_file(dest, body, overwrite=True)

    assert dest.read_text(encoding="utf-8") == body
    if os.name == "posix":
      assert _posix_mode(dest) == 0o600


class TestCreateApiKeyOutFile:
  """CLI integration via Click runner; HTTP mocked."""

  def _runner_ctx(self, tmp_path):
    config = ConfigManager()
    config.set("adminUrl", "http://placeholder.invalid:9080")
    config.set("prettyPrint", False)
    return {"config": config, "verbose": False, "pretty_print": False}

  def test_out_file_writes_credentials_and_hides_secret(self, tmp_path):
    out = tmp_path / "api-key.env"
    payload = _creation_payload()
    mock_response = ApiResponse(success=True, data=payload, status=201)

    runner = CliRunner()
    with patch("ezkey_cli.commands.admin.HttpClient") as mock_http_cls:
      mock_http_cls.return_value.post.return_value = mock_response
      result = runner.invoke(
          create_api_key,
          [
              "--integration-id",
              "1",
              "--description",
              "placeholder",
              "--out-file",
              str(out),
          ],
          obj=self._runner_ctx(tmp_path),
          catch_exceptions=False,
      )

    assert result.exit_code == 0, result.output
    combined = (result.stdout or "") + (result.stderr or "")
    assert PLACEHOLDER_SKEY not in combined
    assert PLACEHOLDER_IKEY in combined
    assert str(out.resolve()) in combined or str(out) in combined

    assert out.is_file()
    content = out.read_text(encoding="utf-8")
    assert f"EZKEY_INTEGRATION_KEY={PLACEHOLDER_IKEY}" in content
    assert f"EZKEY_SECRET_KEY={PLACEHOLDER_SKEY}" in content
    if os.name == "posix":
      assert _posix_mode(out) == 0o600

  def test_out_file_refuses_existing_without_force(self, tmp_path):
    out = tmp_path / "api-key.env"
    out.write_text("keep-me\n", encoding="utf-8")
    payload = _creation_payload()
    mock_response = ApiResponse(success=True, data=payload, status=201)

    runner = CliRunner()
    with patch("ezkey_cli.commands.admin.HttpClient") as mock_http_cls:
      mock_http_cls.return_value.post.return_value = mock_response
      result = runner.invoke(
          create_api_key,
          ["--integration-id", "1", "--out-file", str(out)],
          obj=self._runner_ctx(tmp_path),
          catch_exceptions=False,
      )

    assert result.exit_code == 1
    combined = (result.stdout or "") + (result.stderr or "")
    assert PLACEHOLDER_SKEY not in combined
    assert out.read_text(encoding="utf-8") == "keep-me\n"

  def test_out_file_force_overwrites(self, tmp_path):
    out = tmp_path / "api-key.env"
    out.write_text("stale\n", encoding="utf-8")
    payload = _creation_payload()
    mock_response = ApiResponse(success=True, data=payload, status=201)

    runner = CliRunner()
    with patch("ezkey_cli.commands.admin.HttpClient") as mock_http_cls:
      mock_http_cls.return_value.post.return_value = mock_response
      result = runner.invoke(
          create_api_key,
          ["--integration-id", "1", "--out-file", str(out), "--force"],
          obj=self._runner_ctx(tmp_path),
          catch_exceptions=False,
      )

    assert result.exit_code == 0, result.output
    combined = (result.stdout or "") + (result.stderr or "")
    assert PLACEHOLDER_SKEY not in combined
    content = out.read_text(encoding="utf-8")
    assert f"EZKEY_SECRET_KEY={PLACEHOLDER_SKEY}" in content
    if os.name == "posix":
      assert _posix_mode(out) == 0o600


class TestConfigSavePermissions:
  """--save-key path uses ConfigManager.save; harden owner-only mode."""

  def test_save_sets_mode_600(self, tmp_path, monkeypatch):
    home = tmp_path / "home"
    home.mkdir()
    monkeypatch.setattr(Path, "home", lambda: home)

    config = ConfigManager()
    config.set_api_key(PLACEHOLDER_IKEY, PLACEHOLDER_SKEY)
    config.save(global_config=True)

    path = home / ".ezkey" / "ezkey.json"
    assert path.is_file()
    # Must not assert secret presence in stdout; file content is intentional.
    raw = path.read_text(encoding="utf-8")
    assert PLACEHOLDER_IKEY in raw
    if os.name == "posix":
      assert _posix_mode(path) == 0o600
