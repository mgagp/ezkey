"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

Test Component: api-key create --out-file (#1043)
Description: Pre-open before POST, mode 600, no secret on stdout/stderr, failure paths
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
  ApiKeyCredentialFormatError,
  SecureFileExistsError,
  SecureFileParentError,
  SecureFileSymlinkError,
  absolute_path_no_follow,
  format_api_key_env,
  prepare_secret_file,
  validate_api_key_credentials,
  write_secret_file,
)

# Placeholders only — never real credentials, hosts, or paths from a live env.
PLACEHOLDER_IKEY = "ezkey_ikey_TESTPLACEHOLDER0001"
PLACEHOLDER_SKEY = "ezkey_skey_TESTPLACEHOLDER0001SECRETVALUE"
PLACEHOLDER_API_KEY_ID = 42


def _creation_payload(**overrides):
  data = {
      "apiKeyId": PLACEHOLDER_API_KEY_ID,
      "integrationKey": PLACEHOLDER_IKEY,
      "secretKey": PLACEHOLDER_SKEY,
      "description": "placeholder-key",
      "createdAt": "2026-01-01T00:00:00Z",
      "expiresAt": None,
      "ipWhitelist": None,
      "warning": "IMPORTANT: Save the secret key now. It will not be shown again.",
  }
  data.update(overrides)
  return data


def _posix_mode(path: Path) -> int:
  return stat.S_IMODE(path.stat().st_mode)


def _combined(result) -> str:
  return (result.stdout or "") + (result.stderr or "")


class TestSecureFileWriter:
  """Direct unit coverage for atomic mode-600 writes."""

  def test_write_creates_mode_600_and_content(self, tmp_path):
    dest = tmp_path / "creds.env"
    body = format_api_key_env(PLACEHOLDER_IKEY, PLACEHOLDER_SKEY)
    written = write_secret_file(dest, body, overwrite=False)

    assert written == absolute_path_no_follow(dest)
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

  def test_prepare_rejects_symlink_even_with_force(self, tmp_path):
    target = tmp_path / "real.env"
    target.write_text("target\n", encoding="utf-8")
    link = tmp_path / "link.env"
    link.symlink_to(target)

    with pytest.raises(SecureFileSymlinkError):
      prepare_secret_file(link, overwrite=True)
    assert target.read_text(encoding="utf-8") == "target\n"

  def test_prepare_rejects_dangling_symlink(self, tmp_path):
    link = tmp_path / "dangling.env"
    link.symlink_to(tmp_path / "missing.env")

    with pytest.raises(SecureFileSymlinkError):
      prepare_secret_file(link, overwrite=False)

  def test_prepare_rejects_missing_parent(self, tmp_path):
    dest = tmp_path / "missing-parent" / "creds.env"
    with pytest.raises(SecureFileParentError):
      prepare_secret_file(dest, overwrite=False)

  def test_abort_removes_partial_file(self, tmp_path):
    dest = tmp_path / "partial.env"
    slot = prepare_secret_file(dest, overwrite=False)
    assert dest.exists()
    slot.abort()
    assert not dest.exists()

  def test_credential_charset_rejects_injection(self):
    with pytest.raises(ApiKeyCredentialFormatError):
      validate_api_key_credentials(PLACEHOLDER_IKEY, 'bad;value\nEVIL=1')
    with pytest.raises(ApiKeyCredentialFormatError):
      format_api_key_env('ikey with space', PLACEHOLDER_SKEY)


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
      mock_http = mock_http_cls.return_value
      mock_http.post.return_value = mock_response
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
    combined = _combined(result)
    assert PLACEHOLDER_SKEY not in combined
    assert PLACEHOLDER_IKEY in combined
    assert f"API Key ID: {PLACEHOLDER_API_KEY_ID}" in combined
    assert str(absolute_path_no_follow(out)) in combined or str(out) in combined
    assert mock_http.post.call_count == 1

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
      mock_http = mock_http_cls.return_value
      mock_http.post.return_value = mock_response
      result = runner.invoke(
          create_api_key,
          ["--integration-id", "1", "--out-file", str(out)],
          obj=self._runner_ctx(tmp_path),
          catch_exceptions=False,
      )

    assert result.exit_code == 1
    combined = _combined(result)
    assert PLACEHOLDER_SKEY not in combined
    assert mock_http.post.call_count == 0
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
    combined = _combined(result)
    assert PLACEHOLDER_SKEY not in combined
    assert f"API Key ID: {PLACEHOLDER_API_KEY_ID}" in combined
    content = out.read_text(encoding="utf-8")
    assert f"EZKEY_SECRET_KEY={PLACEHOLDER_SKEY}" in content
    if os.name == "posix":
      assert _posix_mode(out) == 0o600

  def test_unwritable_parent_skips_post(self, tmp_path):
    parent = tmp_path / "locked"
    parent.mkdir()
    out = parent / "api-key.env"
    if os.name != "posix":
      pytest.skip("chmod parent lock requires POSIX")
    parent.chmod(0o500)

    runner = CliRunner()
    try:
      with patch("ezkey_cli.commands.admin.HttpClient") as mock_http_cls:
        mock_http = mock_http_cls.return_value
        mock_http.post.return_value = ApiResponse(
            success=True, data=_creation_payload(), status=201
        )
        result = runner.invoke(
            create_api_key,
            ["--integration-id", "1", "--out-file", str(out)],
            obj=self._runner_ctx(tmp_path),
            catch_exceptions=False,
        )
    finally:
      parent.chmod(0o700)

    assert result.exit_code == 1
    combined = _combined(result)
    assert PLACEHOLDER_SKEY not in combined
    assert mock_http.post.call_count == 0

  def test_missing_credentials_after_post_shows_api_key_id(self, tmp_path):
    out = tmp_path / "api-key.env"
    payload = _creation_payload(secretKey=None)

    runner = CliRunner()
    with patch("ezkey_cli.commands.admin.HttpClient") as mock_http_cls:
      mock_http = mock_http_cls.return_value
      mock_http.post.return_value = ApiResponse(success=True, data=payload, status=201)
      result = runner.invoke(
          create_api_key,
          ["--integration-id", "1", "--out-file", str(out)],
          obj=self._runner_ctx(tmp_path),
          catch_exceptions=False,
      )

    assert result.exit_code == 1
    combined = _combined(result)
    assert PLACEHOLDER_SKEY not in combined
    assert f"API Key ID: {PLACEHOLDER_API_KEY_ID}" in combined
    assert "revoke" in combined.lower()
    assert mock_http.post.call_count == 1
    assert not out.exists()

  def test_dangling_symlink_skips_post(self, tmp_path):
    out = tmp_path / "dangling.env"
    out.symlink_to(tmp_path / "missing.env")

    runner = CliRunner()
    with patch("ezkey_cli.commands.admin.HttpClient") as mock_http_cls:
      mock_http = mock_http_cls.return_value
      mock_http.post.return_value = ApiResponse(
          success=True, data=_creation_payload(), status=201
      )
      result = runner.invoke(
          create_api_key,
          ["--integration-id", "1", "--out-file", str(out)],
          obj=self._runner_ctx(tmp_path),
          catch_exceptions=False,
      )

    assert result.exit_code == 1
    combined = _combined(result)
    assert PLACEHOLDER_SKEY not in combined
    assert mock_http.post.call_count == 0

  def test_symlink_to_existing_with_force_skips_post(self, tmp_path):
    target = tmp_path / "real.env"
    target.write_text("keep-target\n", encoding="utf-8")
    out = tmp_path / "link.env"
    out.symlink_to(target)

    runner = CliRunner()
    with patch("ezkey_cli.commands.admin.HttpClient") as mock_http_cls:
      mock_http = mock_http_cls.return_value
      mock_http.post.return_value = ApiResponse(
          success=True, data=_creation_payload(), status=201
      )
      result = runner.invoke(
          create_api_key,
          ["--integration-id", "1", "--out-file", str(out), "--force"],
          obj=self._runner_ctx(tmp_path),
          catch_exceptions=False,
      )

    assert result.exit_code == 1
    combined = _combined(result)
    assert PLACEHOLDER_SKEY not in combined
    assert mock_http.post.call_count == 0
    assert target.read_text(encoding="utf-8") == "keep-target\n"

  def test_bad_credential_charset_after_post_shows_api_key_id(self, tmp_path):
    out = tmp_path / "api-key.env"
    payload = _creation_payload(secretKey="bad value with spaces!")

    runner = CliRunner()
    with patch("ezkey_cli.commands.admin.HttpClient") as mock_http_cls:
      mock_http = mock_http_cls.return_value
      mock_http.post.return_value = ApiResponse(success=True, data=payload, status=201)
      result = runner.invoke(
          create_api_key,
          ["--integration-id", "1", "--out-file", str(out)],
          obj=self._runner_ctx(tmp_path),
          catch_exceptions=False,
      )

    assert result.exit_code == 1
    combined = _combined(result)
    assert "bad value with spaces!" not in combined
    assert f"API Key ID: {PLACEHOLDER_API_KEY_ID}" in combined
    assert "revoke" in combined.lower()
    assert mock_http.post.call_count == 1
    assert not out.exists()


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
    raw = path.read_text(encoding="utf-8")
    assert PLACEHOLDER_IKEY in raw
    if os.name == "posix":
      assert _posix_mode(path) == 0o600
      assert _posix_mode(home / ".ezkey") == 0o700

  def test_save_mode_600_under_umask_zero(self, tmp_path, monkeypatch):
    if os.name != "posix":
      pytest.skip("umask mode check requires POSIX")

    home = tmp_path / "home"
    home.mkdir()
    monkeypatch.setattr(Path, "home", lambda: home)

    old_umask = os.umask(0)
    try:
      config = ConfigManager()
      config.set_api_key(PLACEHOLDER_IKEY, PLACEHOLDER_SKEY)
      config.save(global_config=True)
    finally:
      os.umask(old_umask)

    path = home / ".ezkey" / "ezkey.json"
    assert _posix_mode(path) == 0o600
    assert _posix_mode(home / ".ezkey") == 0o700
