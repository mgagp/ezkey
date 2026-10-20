"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

CLI Component: TLS verification helpers
Description: Resolve requests verify= settings (default on, optional CA bundle, loopback-only insecure)
"""

from __future__ import annotations

import os
import sys
import warnings
from typing import Optional, Union
from urllib.parse import urlparse

import requests

# Hostnames allowed for --insecure / EZKEY_CLI_INSECURE (loopback only).
LOOPBACK_HOSTS = frozenset({"localhost", "127.0.0.1", "::1"})

SslVerify = Union[bool, str]

INSECURE_WARN_MESSAGE = (
    "WARNING: TLS certificate verification is DISABLED (--insecure / "
    "EZKEY_CLI_INSECURE). Only allowed for loopback hosts "
    "(localhost, 127.0.0.1, ::1). Do not use against remote hosts."
)


class InsecureTlsNotAllowedError(ValueError):
  """Raised when --insecure is requested for a non-loopback URL."""


def is_loopback_host(host: Optional[str]) -> bool:
  """
  Return True if host is a loopback name or address.

  Args:
      host: Hostname or IP from a URL (may include zone id for IPv6).

  Returns:
      True when host is localhost, 127.0.0.1, or ::1.
  """
  if not host:
    return False
  normalized = host.strip().lower()
  # urlparse may leave brackets on IPv6 literals.
  if normalized.startswith("[") and normalized.endswith("]"):
    normalized = normalized[1:-1]
  # Drop IPv6 zone identifier if present.
  if "%" in normalized:
    normalized = normalized.split("%", 1)[0]
  return normalized in LOOPBACK_HOSTS


def is_loopback_url(url: str) -> bool:
  """
  Return True if the URL targets a loopback host.

  Args:
      url: Absolute or scheme-relative URL.

  Returns:
      True when the hostname is loopback.
  """
  if not url:
    return False
  parsed = urlparse(url if "://" in url else f"https://{url}")
  return is_loopback_host(parsed.hostname)


def _env_flag_true(name: str) -> bool:
  """Return True when environment variable is a common truthy string."""
  value = os.environ.get(name, "").strip().lower()
  return value in {"1", "true", "yes", "on"}


def resolve_ca_bundle(
    ca_bundle: Optional[str] = None,
) -> Optional[str]:
  """
  Resolve a CA bundle path from an explicit argument or environment.

  Precedence: explicit ``ca_bundle`` > ``EZKEY_CA_BUNDLE`` > ``REQUESTS_CA_BUNDLE``.

  Args:
      ca_bundle: Optional path from ``--ca-bundle``.

  Returns:
      Absolute or relative path string, or None to use system defaults.
  """
  if ca_bundle:
    return ca_bundle
  for env_name in ("EZKEY_CA_BUNDLE", "REQUESTS_CA_BUNDLE"):
    env_value = os.environ.get(env_name, "").strip()
    if env_value:
      return env_value
  return None


def resolve_insecure_requested(insecure: Optional[bool] = None) -> bool:
  """
  Resolve whether insecure TLS was explicitly requested.

  Never inferred from "dev mode" or hostname alone. Precedence: explicit
  ``insecure=True/False`` > ``EZKEY_CLI_INSECURE``.

  Args:
      insecure: Explicit flag from ``--insecure`` (None = consult env only).

  Returns:
      True when verification disable was explicitly requested.
  """
  if insecure is True:
    return True
  if insecure is False:
    return False
  return _env_flag_true("EZKEY_CLI_INSECURE")


def emit_insecure_warning(stream=None) -> None:
  """
  Emit a visible warning for disabled TLS verification.

  Writes to stderr and emits a Python ``UserWarning`` so TUI/log captures see it.

  Args:
      stream: Optional text stream (defaults to ``sys.stderr``).
  """
  out = stream if stream is not None else sys.stderr
  print(INSECURE_WARN_MESSAGE, file=out)
  warnings.warn(INSECURE_WARN_MESSAGE, UserWarning, stacklevel=2)


def resolve_ssl_verify(
    url: str,
    *,
    insecure: Optional[bool] = None,
    ca_bundle: Optional[str] = None,
    warn: bool = True,
) -> SslVerify:
  """
  Resolve the value to pass as ``requests`` ``verify=``.

  Default is ``True`` (system CAs). A CA bundle path is preferred over
  disabling verification. ``--insecure`` / ``EZKEY_CLI_INSECURE`` is allowed
  only for loopback hosts and always emits a warning when accepted.

  Args:
      url: Target API URL used to enforce the loopback restriction.
      insecure: Explicit ``--insecure`` (None = env only).
      ca_bundle: Explicit ``--ca-bundle`` path (None = env / system).
      warn: When True, print/warn on accepted insecure mode.

  Returns:
      ``True``, a CA bundle path string, or ``False`` (insecure loopback only).

  Raises:
      InsecureTlsNotAllowedError: When insecure is requested for a non-loopback host.
  """
  bundle = resolve_ca_bundle(ca_bundle)
  if bundle:
    return bundle

  if resolve_insecure_requested(insecure):
    if not is_loopback_url(url):
      raise InsecureTlsNotAllowedError(
          "--insecure / EZKEY_CLI_INSECURE is only allowed for loopback hosts "
          f"(localhost, 127.0.0.1, ::1); refused for URL: {url}"
      )
    if warn:
      emit_insecure_warning()
    return False

  return True


def ssl_verify_from_config(config, url: Optional[str] = None) -> SslVerify:
  """
  Resolve SSL verify settings stored on a ConfigManager-like object.

  Expects optional keys ``sslCaBundle`` (str) and ``sslInsecure`` (bool).
  Uses ``adminUrl`` from config when ``url`` is omitted.

  Args:
      config: Object with ``get(key, default=None)``.
      url: URL for loopback checks; defaults to ``adminUrl``.

  Returns:
      Value suitable for ``requests`` ``verify=``.
  """
  target = url or config.get("adminUrl", "http://localhost:9080")
  return resolve_ssl_verify(
      target,
      insecure=config.get("sslInsecure"),
      ca_bundle=config.get("sslCaBundle"),
  )


def requests_get(url: str, *, verify: SslVerify, **kwargs):
  """
  ``requests.get`` with TLS verify on by default.

  The ``verify=False`` branch is only reachable after ``resolve_ssl_verify``
  accepted an explicit loopback-only insecure opt-in (#802).
  """
  if verify is False:
    # Intentionally disabled: loopback-only --insecure / EZKEY_CLI_INSECURE (#802).
    # codeql[py/request-without-cert-validation]
    return requests.get(url, verify=False, **kwargs)
  return requests.get(url, verify=verify, **kwargs)


def requests_post(url: str, *, verify: SslVerify, **kwargs):
  """
  ``requests.post`` with TLS verify on by default.

  The ``verify=False`` branch is only reachable after ``resolve_ssl_verify``
  accepted an explicit loopback-only insecure opt-in (#802).
  """
  if verify is False:
    # Intentionally disabled: loopback-only --insecure / EZKEY_CLI_INSECURE (#802).
    # codeql[py/request-without-cert-validation]
    return requests.post(url, verify=False, **kwargs)
  return requests.post(url, verify=verify, **kwargs)


def requests_put(url: str, *, verify: SslVerify, **kwargs):
  """``requests.put`` with the same TLS policy as ``requests_post``."""
  if verify is False:
    # Intentionally disabled: loopback-only --insecure / EZKEY_CLI_INSECURE (#802).
    # codeql[py/request-without-cert-validation]
    return requests.put(url, verify=False, **kwargs)
  return requests.put(url, verify=verify, **kwargs)


def requests_delete(url: str, *, verify: SslVerify, **kwargs):
  """``requests.delete`` with the same TLS policy as ``requests_post``."""
  if verify is False:
    # Intentionally disabled: loopback-only --insecure / EZKEY_CLI_INSECURE (#802).
    # codeql[py/request-without-cert-validation]
    return requests.delete(url, verify=False, **kwargs)
  return requests.delete(url, verify=verify, **kwargs)


def apply_session_verify(session, verify: SslVerify) -> None:
  """
  Apply resolved TLS verify settings to a ``requests.Session``.

  Args:
      session: ``requests.Session`` instance.
      verify: Resolved verify value (``True``, CA path, or ``False``).
  """
  if verify is False:
    # Intentionally disabled: loopback-only --insecure / EZKEY_CLI_INSECURE (#802).
    # codeql[py/request-without-cert-validation]
    session.verify = False
  else:
    session.verify = verify
