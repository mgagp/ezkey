"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

CLI Component: Secure File Writer
Description: Atomic owner-only (mode 600) file writes for credentials
"""

from __future__ import annotations

import errno
import logging
import os
import re
import tempfile
from pathlib import Path
from typing import Optional, Union

log = logging.getLogger(__name__)

PathLike = Union[str, Path]

# Safe for .env sourcing: no quotes, spaces, shell metacharacters, or newlines.
_API_KEY_CREDENTIAL_RE = re.compile(r"^[A-Za-z0-9_-]+$")


class SecureFileExistsError(FileExistsError):
    """Raised when the target path already exists and overwrite is not allowed."""


class SecureFileSymlinkError(OSError):
    """Raised when the target path is a symbolic link (never followed or replaced)."""


class SecureFileParentError(OSError):
    """Raised when the parent directory is missing or not writable."""


class ApiKeyCredentialFormatError(ValueError):
    """Raised when ikey/skey contain characters unsafe for .env sourcing."""


def absolute_path_no_follow(path: PathLike) -> Path:
    """
    Absolute path without resolving the final component through symlinks.

    Uses ``os.path.abspath`` (normalizes ``.`` / ``..``) rather than
    ``Path.resolve()``, which would follow a final symlink.
    """
    expanded = Path(path).expanduser()
    return Path(os.path.abspath(str(expanded)))


def ensure_owner_only_dir(path: PathLike, *, warn: bool = True) -> Path:
    """
    Ensure ``path`` exists as a directory with POSIX mode ``0o700``.

    Args:
        path: Directory to create or harden.
        warn: When True, log a warning if chmod fails instead of silent pass.

    Returns:
        Absolute path to the directory.
    """
    dest = absolute_path_no_follow(path)
    dest.mkdir(mode=0o700, exist_ok=True)
    try:
        os.chmod(dest, 0o700)
    except (AttributeError, NotImplementedError, OSError) as exc:
        if warn:
            log.warning(
                "Could not set directory mode 0700 on %s (%s); "
                "on Windows ACLs are inherited from the parent folder",
                dest,
                type(exc).__name__,
            )
    return dest


def validate_api_key_credentials(integration_key: str, secret_key: str) -> None:
    """
    Refuse credentials that are unsafe to embed in a sourced ``.env`` file.

    Args:
        integration_key: Public integration key (ikey).
        secret_key: Secret key (skey). Must not be logged by the caller.

    Raises:
        ApiKeyCredentialFormatError: When either value fails the charset check.
    """
    if not integration_key or not _API_KEY_CREDENTIAL_RE.fullmatch(integration_key):
        raise ApiKeyCredentialFormatError(
            "Integration key has an unexpected format; refusing to write .env"
        )
    if not secret_key or not _API_KEY_CREDENTIAL_RE.fullmatch(secret_key):
        raise ApiKeyCredentialFormatError(
            "Secret key has an unexpected format; refusing to write .env"
        )


def format_api_key_env(integration_key: str, secret_key: str) -> str:
    """
    Build a minimal ``.env`` body for an Ezkey API key pair.

    Validates charset before formatting. Caller must not print ``secret_key``.

    Args:
        integration_key: Public integration key (ikey).
        secret_key: Secret key (skey).

    Returns:
        Newline-terminated ``.env`` text.
    """
    validate_api_key_credentials(integration_key, secret_key)
    return (
        f"EZKEY_INTEGRATION_KEY={integration_key}\n"
        f"EZKEY_SECRET_KEY={secret_key}\n"
    )


class SecureFileSlot:
    """
    Exclusive open destination prepared before secret content is known.

    For create-only paths the destination fd is held open (``O_EXCL``).
    For overwrite, a same-directory temp file is held and ``os.replace``-d
    on successful ``commit``.
    """

    def __init__(
        self,
        dest: Path,
        fd: int,
        *,
        temp_path: Optional[Path] = None,
        warn_chmod: bool = True,
    ) -> None:
        self.dest = dest
        self._fd = fd
        self._temp_path = temp_path
        self._warn_chmod = warn_chmod
        self._closed = False

    def commit(self, content: str) -> Path:
        """
        Write ``content`` through the held fd and finalize the destination.

        On failure, aborts (closes and unlinks the empty/partial file).

        Args:
            content: Exact UTF-8 file body.

        Returns:
            Destination path (last component not symlink-resolved).
        """
        if self._closed:
            raise OSError("SecureFileSlot already closed")

        data = content.encode("utf-8")
        try:
            os.write(self._fd, data)
            _fchmod_owner_only(self._fd, warn=self._warn_chmod)
            os.close(self._fd)
            self._fd = -1
            self._closed = True

            if self._temp_path is not None:
                os.replace(self._temp_path, self.dest)
                self._temp_path = None
                _chmod_owner_only(self.dest, warn=self._warn_chmod)
            return self.dest
        except Exception:
            self.abort()
            raise

    def abort(self) -> None:
        """Close the fd and remove any empty/partial file created for this slot."""
        if self._fd >= 0:
            try:
                os.close(self._fd)
            except OSError:
                pass
            self._fd = -1
        self._closed = True

        to_remove = self._temp_path if self._temp_path is not None else self.dest
        self._temp_path = None
        if to_remove is not None:
            try:
                to_remove.unlink(missing_ok=True)
            except OSError:
                pass


def prepare_secret_file(
    path: PathLike,
    *,
    overwrite: bool = False,
    warn_chmod: bool = True,
) -> SecureFileSlot:
    """
    Validate ``path`` and open it exclusively before secret content exists.

    Does not create parent directories. Rejects an existing path that is a
    symlink, including when ``overwrite`` is True (never follow or replace
    through a link). Without ``overwrite``, refuses any existing path.

    Args:
        path: Destination path.
        overwrite: When True, stage to a temp file then replace (non-symlink only).
        warn_chmod: Log a warning when mode 600 cannot be applied.

    Returns:
        A :class:`SecureFileSlot` ready for ``commit``.

    Raises:
        SecureFileExistsError: Target exists and ``overwrite`` is False.
        SecureFileSymlinkError: Target path is a symbolic link.
        SecureFileParentError: Parent missing or not writable.
        OSError: Underlying open failures.
    """
    dest = absolute_path_no_follow(path)
    parent = dest.parent

    if not parent.is_dir():
        raise SecureFileParentError(
            f"Parent directory does not exist or is not a directory: {parent}"
        )
    if not os.access(parent, os.W_OK | os.X_OK):
        raise SecureFileParentError(f"Parent directory is not writable: {parent}")

    if dest.is_symlink():
        raise SecureFileSymlinkError(
            f"Refusing to write through or replace a symbolic link: {dest}"
        )

    flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL
    if hasattr(os, "O_NOFOLLOW"):
        flags |= os.O_NOFOLLOW
    if hasattr(os, "O_CLOEXEC"):
        flags |= os.O_CLOEXEC

    if not overwrite:
        if dest.exists() or dest.is_symlink():
            # exists() follows; is_symlink() catches dangling links.
            if dest.is_symlink():
                raise SecureFileSymlinkError(
                    f"Refusing to write through or replace a symbolic link: {dest}"
                )
            raise SecureFileExistsError(f"Refusing to overwrite existing file: {dest}")
        try:
            fd = os.open(str(dest), flags, 0o600)
        except FileExistsError as exc:
            raise SecureFileExistsError(
                f"Refusing to overwrite existing file: {dest}"
            ) from exc
        except OSError as exc:
            # ELOOP when O_NOFOLLOW hits a symlink; EEXIST for races.
            if exc.errno == errno.ELOOP or dest.is_symlink():
                raise SecureFileSymlinkError(
                    f"Refusing to write through or replace a symbolic link: {dest}"
                ) from exc
            if exc.errno == errno.EEXIST:
                raise SecureFileExistsError(
                    f"Refusing to overwrite existing file: {dest}"
                ) from exc
            raise
        return SecureFileSlot(dest, fd, warn_chmod=warn_chmod)

    # overwrite: reject symlink target name; stage via exclusive temp then replace.
    if dest.is_symlink():
        raise SecureFileSymlinkError(
            f"Refusing to write through or replace a symbolic link: {dest}"
        )

    fd, tmp_name = tempfile.mkstemp(prefix=f".{dest.name}.", dir=str(parent))
    tmp_path = Path(tmp_name)
    try:
        _fchmod_owner_only(fd, warn=warn_chmod)
    except Exception:
        try:
            os.close(fd)
        except OSError:
            pass
        try:
            tmp_path.unlink(missing_ok=True)
        except OSError:
            pass
        raise
    return SecureFileSlot(dest, fd, temp_path=tmp_path, warn_chmod=warn_chmod)


def write_secret_file(
    path: PathLike,
    content: str,
    *,
    overwrite: bool = False,
    warn_chmod: bool = True,
) -> Path:
    """
    Prepare then commit ``content`` to ``path`` with POSIX mode ``0o600``.

    Convenience wrapper for callers that already hold the full body
    (e.g. config save). Prefer :func:`prepare_secret_file` when the secret
    is obtained after the slot must be reserved.
    """
    slot = prepare_secret_file(path, overwrite=overwrite, warn_chmod=warn_chmod)
    return slot.commit(content)


def _fchmod_owner_only(fd: int, *, warn: bool = True) -> None:
    """Best-effort owner-only mode on an open file descriptor."""
    try:
        os.fchmod(fd, 0o600)
    except (AttributeError, NotImplementedError, OSError) as exc:
        if warn:
            log.warning(
                "Could not set file mode 0600 on open fd (%s); "
                "on Windows ACLs are inherited from the parent folder",
                type(exc).__name__,
            )


def _chmod_owner_only(path: Path, *, warn: bool = True) -> None:
    """Best-effort owner-only mode on a path."""
    try:
        os.chmod(path, 0o600)
    except (AttributeError, NotImplementedError, OSError) as exc:
        if warn:
            log.warning(
                "Could not set file mode 0600 on %s (%s); "
                "on Windows ACLs are inherited from the parent folder",
                path,
                type(exc).__name__,
            )
