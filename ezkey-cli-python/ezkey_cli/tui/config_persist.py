"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

TUI Module: Config persistence
Description: Save ezkey.json from the TUI and report a refused save instead of crashing
"""

import logging
from typing import Optional

from ..utils.secure_file import SecureFileSymlinkError

log = logging.getLogger(__name__)

SESSION_ONLY_SUFFIX = "Changes are kept for this session only."
_NOTIFIED_ATTR = "_ezkey_config_save_error_notified"


def try_save_config(config, *, global_config: bool) -> Optional[str]:
  """
  Save ``config``; return an operator message instead of raising on failure.

  ``ConfigManager.save`` refuses a symlinked ``ezkey.json`` (fail-closed:
  nothing is written). Other ``OSError`` (read-only file, full disk) are
  reported the same way so the TUI never stops on a config write (#1048).

  Returns:
      None when the file was written, otherwise the reason it was not.
  """
  try:
    config.save(global_config=global_config)
    return None
  except SecureFileSymlinkError as exc:
    reason = str(exc)
  except OSError as exc:
    reason = f"Configuration was not saved: {exc}"
  log.error("Config not saved: %s", reason)
  return reason


def save_config(app, config, *, global_config: bool) -> bool:
  """
  Save ``config`` and show a refused save as a TUI error notification.

  The TUI keeps its in-memory values. The notification is shown once per
  session; later refusals are only logged, so repeated page-size changes do
  not stack error toasts.

  Args:
      app: Textual app used for the notification.
      config: ConfigManager to persist.
      global_config: True for ``~/.ezkey/ezkey.json``, False for ``./ezkey.json``.

  Returns:
      True when the file was written, False when the save failed.
  """
  reason = try_save_config(config, global_config=global_config)
  if reason is None:
    return True
  if app is not None and not getattr(app, _NOTIFIED_ATTR, False):
    setattr(app, _NOTIFIED_ATTR, True)
    app.notify(f"{reason} {SESSION_ONLY_SUFFIX}", severity="error", timeout=10)
  return False
