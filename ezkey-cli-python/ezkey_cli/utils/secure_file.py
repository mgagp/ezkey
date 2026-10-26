"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

CLI Component: Secure File Writer
Description: Atomic owner-only (mode 600) file writes for credentials
"""

from __future__ import annotations

import os
import tempfile
from pathlib import Path
from typing import Union

PathLike = Union[str, Path]


class SecureFileExistsError(FileExistsError):
    """Raised when the target path already exists and overwrite is not allowed."""


def write_secret_file(
    path: PathLike,
    content: str,
    *,
    overwrite: bool = False,
) -> Path:
    """
    Write ``content`` to ``path`` with POSIX mode ``0o600``.

    Without ``overwrite``, the file is created with ``O_EXCL`` so an existing
    path is never truncated and the file is never world-readable even briefly.
    With ``overwrite``, content is written to a same-directory temp file
    (also mode ``0o600``) then ``os.replace``-d onto the target.

    Args:
        path: Destination path.
        content: Exact file body (UTF-8).
        overwrite: When True, replace an existing file atomically.

    Returns:
        Resolved destination path.

    Raises:
        SecureFileExistsError: Target exists and ``overwrite`` is False.
        OSError: Underlying open/write/replace failures.
    """
    dest = Path(path).expanduser()
    if not dest.is_absolute():
        dest = Path.cwd() / dest
    dest = dest.resolve(strict=False)

    parent = dest.parent
    parent.mkdir(parents=True, exist_ok=True)

    data = content.encode("utf-8")

    if not overwrite:
        flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL
        try:
            fd = os.open(dest, flags, 0o600)
        except FileExistsError as exc:
            raise SecureFileExistsError(
                f"Refusing to overwrite existing file: {dest}"
            ) from exc
        try:
            os.write(fd, data)
            _fchmod_owner_only(fd)
        finally:
            os.close(fd)
        return dest

    # Atomic replace: exclusive temp in the same directory, then os.replace.
    fd, tmp_name = tempfile.mkstemp(prefix=f".{dest.name}.", dir=str(parent))
    tmp_path = Path(tmp_name)
    try:
        os.write(fd, data)
        _fchmod_owner_only(fd)
        os.close(fd)
        fd = -1
        os.replace(tmp_path, dest)
        # replace keeps the temp inode's mode on POSIX; re-assert 600 on dest.
        _chmod_owner_only(dest)
    except Exception:
        if fd >= 0:
            try:
                os.close(fd)
            except OSError:
                pass
        try:
            tmp_path.unlink(missing_ok=True)
        except OSError:
            pass
        raise

    return dest


def format_api_key_env(
    integration_key: str,
    secret_key: str,
) -> str:
    """
    Build a minimal ``.env`` body for an Ezkey API key pair.

    Args:
        integration_key: Public integration key (ikey).
        secret_key: Secret key (skey). Caller must not print this value.

    Returns:
        Newline-terminated ``.env`` text.
    """
    return (
        f"EZKEY_INTEGRATION_KEY={integration_key}\n"
        f"EZKEY_SECRET_KEY={secret_key}\n"
    )


def _fchmod_owner_only(fd: int) -> None:
    """Best-effort owner-only mode on an open file descriptor."""
    try:
        os.fchmod(fd, 0o600)
    except (AttributeError, NotImplementedError, OSError):
        # Windows / non-POSIX: open(mode) already requested 0600 where supported.
        pass


def _chmod_owner_only(path: Path) -> None:
    """Best-effort owner-only mode on a path."""
    try:
        os.chmod(path, 0o600)
    except (AttributeError, NotImplementedError, OSError):
        pass
