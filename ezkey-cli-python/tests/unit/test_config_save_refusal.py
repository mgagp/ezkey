"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

Test Component: refused config save and interrupted POST (#1048)
Description: symlinked ezkey.json in CLI and TUI, --save-key exit code, Ctrl-C during POST
"""

from __future__ import annotations

import json
from pathlib import Path
from unittest.mock import MagicMock, PropertyMock, patch

import click
import pytest
from click.testing import CliRunner

from ezkey_cli.commands.admin import create_api_key
from ezkey_cli.config.config_manager import ConfigManager
from ezkey_cli.main import EzkeyGroup
from ezkey_cli.tui.app import EzkeyAdminApp, EzkeyAdminTUI
from ezkey_cli.tui.config_persist import save_config
from ezkey_cli.tui.screens.api_keys import ApiKeysScreen
from ezkey_cli.utils.http_client import ApiResponse
from ezkey_cli.utils.secure_file import SecureFileSymlinkError

# Placeholders only — never real credentials, hosts, or paths from a live env.
PLACEHOLDER_IKEY = "ezkey_ikey_TESTPLACEHOLDER0002"
PLACEHOLDER_SKEY = "ezkey_skey_TESTPLACEHOLDER0002SECRETVALUE"
TARGET_BODY = '{"placeholder": true}\n'


@pytest.fixture
def symlinked_home(tmp_path, monkeypatch):
  """Home whose ~/.ezkey/ezkey.json is a symlink; cwd has no local config."""
  home = tmp_path / "home"
  config_dir = home / ".ezkey"
  config_dir.mkdir(parents=True, mode=0o700)
  work = tmp_path / "work"
  work.mkdir()
  target = tmp_path / "elsewhere.json"
  target.write_text(TARGET_BODY, encoding="utf-8")
  (config_dir / "ezkey.json").symlink_to(target)
  monkeypatch.setattr(Path, "home", lambda: home)
  monkeypatch.chdir(work)
  return target


def _payload():
  return {
      "apiKeyId": 7,
      "integrationKey": PLACEHOLDER_IKEY,
      "secretKey": PLACEHOLDER_SKEY,
      "description": "placeholder-key",
      "createdAt": "2026-01-01T00:00:00Z",
  }


def _ctx(pretty_print=False):
  config = ConfigManager()
  config.set("adminUrl", "http://placeholder.invalid:9080")
  return {"config": config, "verbose": False, "pretty_print": pretty_print}


def _invoke_create(args, *, post_return=None, post_side_effect=None, pretty_print=False):
  runner = CliRunner()
  with patch("ezkey_cli.commands.admin.HttpClient") as mock_http_cls:
    mock_post = mock_http_cls.return_value.post
    if post_side_effect is not None:
      mock_post.side_effect = post_side_effect
    else:
      mock_post.return_value = post_return
    return runner.invoke(
        create_api_key,
        ["--integration-id", "1", *args],
        obj=_ctx(pretty_print),
        catch_exceptions=False,
    )


class TestSaveKeyExitCode:
  """--save-key that cannot write the config exits non-zero (decision on #1048)."""

  def test_printed_key_save_refused_exits_1(self, symlinked_home):
    result = _invoke_create(
        ["--save-key"],
        post_return=ApiResponse(success=True, data=_payload(), status=201),
        pretty_print=True,
    )

    assert result.exit_code == 1
    out = result.output
    assert "symbolic link" in out
    assert "created and shown above" in out
    assert "api-key revoke --id 7" in out
    assert "re-run" not in out
    # The key was still delivered, including the JSON block.
    assert f"Secret Key: {PLACEHOLDER_SKEY}" in out
    assert '"apiKeyId": 7' in out
    assert symlinked_home.read_text(encoding="utf-8") == TARGET_BODY

  def test_out_file_key_save_refused_exits_1(self, symlinked_home, tmp_path):
    out_file = tmp_path / "api-key.env"
    result = _invoke_create(
        ["--out-file", str(out_file), "--save-key"],
        post_return=ApiResponse(success=True, data=_payload(), status=201),
    )

    assert result.exit_code == 1
    assert "written to the file above" in result.output
    assert PLACEHOLDER_SKEY not in result.output
    assert PLACEHOLDER_SKEY in out_file.read_text(encoding="utf-8")
    assert symlinked_home.read_text(encoding="utf-8") == TARGET_BODY

  def test_save_key_success_exits_0(self, tmp_path, monkeypatch):
    home = tmp_path / "home"
    home.mkdir()
    monkeypatch.setattr(Path, "home", lambda: home)
    monkeypatch.chdir(tmp_path)

    result = _invoke_create(
        ["--save-key"],
        post_return=ApiResponse(success=True, data=_payload(), status=201),
    )

    assert result.exit_code == 0
    assert "API key saved to config" in result.output
    saved = json.loads((home / ".ezkey" / "ezkey.json").read_text(encoding="utf-8"))
    assert saved["integrationKey"] == PLACEHOLDER_IKEY


class TestInterruptDuringPost:
  """Ctrl-C during the POST leaves no reserved file or temp behind."""

  def test_create_only_slot_removed(self, tmp_path):
    out_file = tmp_path / "api-key.env"
    result = _invoke_create(["--out-file", str(out_file)], post_side_effect=KeyboardInterrupt)

    assert result.exit_code == 1  # click turns KeyboardInterrupt into Abort
    assert not out_file.exists()
    assert list(tmp_path.iterdir()) == []

  def test_force_temp_removed_and_previous_content_kept(self, tmp_path):
    out_file = tmp_path / "api-key.env"
    out_file.write_text("PREVIOUS=1\n", encoding="utf-8")
    result = _invoke_create(
        ["--out-file", str(out_file), "--force"], post_side_effect=KeyboardInterrupt
    )

    assert result.exit_code == 1
    assert out_file.read_text(encoding="utf-8") == "PREVIOUS=1\n"
    assert [p.name for p in tmp_path.iterdir()] == ["api-key.env"]


class TestCliRootGroup:
  """A refused save in any command is a one-line error and exit 1, not a traceback."""

  def test_symlink_error_becomes_click_error(self):
    @click.group(cls=EzkeyGroup)
    def root():
      pass

    @root.command()
    def boom():
      raise SecureFileSymlinkError("placeholder/ezkey.json is a symbolic link")

    result = CliRunner().invoke(root, ["boom"])

    assert result.exit_code == 1
    assert "Error: placeholder/ezkey.json is a symbolic link" in result.output
    assert not isinstance(result.exception, SecureFileSymlinkError)


class TestTuiSaveRefused:
  """TUI keeps running and tells the operator when ezkey.json is a symlink."""

  def test_helper_notifies_and_returns_false(self, symlinked_home):
    app = MagicMock(spec=["notify"])
    config = ConfigManager()
    config.set("pageSize", 25)

    assert save_config(app, config, global_config=True) is False
    message = app.notify.call_args.args[0]
    assert "symbolic link" in message
    assert "this session only" in message
    assert app.notify.call_args.kwargs["severity"] == "error"
    assert symlinked_home.read_text(encoding="utf-8") == TARGET_BODY

  def test_helper_notifies_once_per_session(self, symlinked_home):
    app = MagicMock(spec=["notify"])
    config = ConfigManager()

    for _ in range(3):
      assert save_config(app, config, global_config=True) is False
    assert app.notify.call_count == 1

  def test_helper_reports_other_os_errors(self):
    app = MagicMock(spec=["notify"])
    config = MagicMock()
    config.save.side_effect = PermissionError("placeholder: read-only")

    assert save_config(app, config, global_config=False) is False
    assert "read-only" in app.notify.call_args.args[0]

  def test_helper_saves_regular_file(self, tmp_path, monkeypatch):
    monkeypatch.setattr(Path, "home", lambda: tmp_path)
    app = MagicMock()
    config = ConfigManager()
    config.set("pageSize", 25)

    assert save_config(app, config, global_config=True) is True
    app.notify.assert_not_called()

  def test_screen_page_size_persist_does_not_raise(self, symlinked_home):
    app = MagicMock(spec=["notify", "config"])
    app.config = ConfigManager()
    screen = ApiKeysScreen.__new__(ApiKeysScreen)
    screen.page_size = 30

    with patch.object(ApiKeysScreen, "app", new_callable=PropertyMock, return_value=app):
      screen._persist_page_size()

    app.notify.assert_called_once()
    assert symlinked_home.read_text(encoding="utf-8") == TARGET_BODY

  def test_logout_exits_with_honest_message(self, symlinked_home):
    tui = EzkeyAdminTUI.__new__(EzkeyAdminTUI)
    tui.config = ConfigManager()
    tui.api_client = None

    with patch.object(EzkeyAdminTUI, "exit") as mock_exit:
      tui.logout_and_exit()

    message = mock_exit.call_args.kwargs["message"]
    assert "Logged out" in message
    assert "symbolic link" in message

  def test_legacy_migration_keeps_token_and_file(self, symlinked_home):
    legacy = Path.home() / ".ezkey" / "admin"
    legacy.mkdir(parents=True)
    legacy_file = legacy / "bearer-token"
    legacy_file.write_text("legacy-token-placeholder", encoding="utf-8")

    app = EzkeyAdminApp(ConfigManager())
    assert app._load_bearer_token() == "legacy-token-placeholder"
    assert legacy_file.exists()
    assert symlinked_home.read_text(encoding="utf-8") == TARGET_BODY


class TestSymlinkedConfigDirectory:
  """A symlinked ~/.ezkey directory (dotfile managers) is accepted."""

  def test_save_through_symlinked_parent_dir(self, tmp_path, monkeypatch):
    home = tmp_path / "home"
    home.mkdir()
    real_dir = tmp_path / "dotfiles-ezkey"
    real_dir.mkdir(mode=0o700)
    (home / ".ezkey").symlink_to(real_dir, target_is_directory=True)
    monkeypatch.setattr(Path, "home", lambda: home)

    config = ConfigManager()
    config.set_api_key(PLACEHOLDER_IKEY, PLACEHOLDER_SKEY)
    config.save(global_config=True)

    written = real_dir / "ezkey.json"
    assert written.is_file()
    assert not written.is_symlink()
    assert PLACEHOLDER_IKEY in written.read_text(encoding="utf-8")
