"""Load scenario YAML with environment-variable overrides."""

from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Mapping

import yaml


def _deep_merge(base: dict[str, Any], override: Mapping[str, Any]) -> dict[str, Any]:
  out = dict(base)
  for k, v in override.items():
    if k in out and isinstance(out[k], dict) and isinstance(v, Mapping):
      out[k] = _deep_merge(out[k], v)  # type: ignore[arg-type]
    else:
      out[k] = v  # type: ignore[assignment]
  return out


@dataclass
class ScenarioConfig:
  """Resolved scenario after env overrides."""

  raw: dict[str, Any]

  @property
  def admin_base(self) -> str:
    return str(self.raw["base_urls"]["admin"]).rstrip("/")

  @property
  def auth_base(self) -> str:
    return str(self.raw["base_urls"]["auth"]).rstrip("/")

  @property
  def crypto_base(self) -> str:
    return str(self.raw["base_urls"]["crypto"]).rstrip("/")

  @property
  def admin_token(self) -> str:
    t = (
      os.environ.get("EZKEY_ADMIN_TOKEN") or self.raw.get("admin_bearer_token") or ""
    ).strip()
    if t.lower().startswith("bearer "):
      t = t[7:].strip()
    if not t:
      raise ValueError(
        "Missing admin bearer token: set EZKEY_ADMIN_TOKEN or admin_bearer_token in YAML"
      )
    return t

  @property
  def integration_id(self) -> int:
    return int(self.raw["integration_id"])

  def enrollment_create_body(self) -> dict[str, Any]:
    enr = self.raw.get("enrollment") or {}
    return {
      "integrationId": self.integration_id,
      "name": enr.get("name", "Protocol lab enrollment"),
      "authAttemptChallengeRequired": bool(
        enr.get("auth_attempt_challenge_required", False)
      ),
    }

  @property
  def respond_accepted(self) -> bool:
    sc = self.raw.get("scenario") or {}
    return bool(sc.get("respond_accepted", True))

  @property
  def auth_challenge_requested(self) -> bool:
    sc = self.raw.get("scenario") or {}
    return bool(sc.get("auth_challenge_requested", False))

  @property
  def device_storage_tier(self) -> str:
    return str(self.raw.get("device_private_key_storage_tier", "STANDARD"))

  @property
  def wait_timeout(self) -> int:
    w = self.raw.get("wait") or {}
    return int(w.get("timeout_seconds", 90))

  @property
  def wait_poll(self) -> int:
    w = self.raw.get("wait") or {}
    return int(w.get("poll_seconds", 2))


def load_config(path: Path) -> ScenarioConfig:
  """Load YAML and apply env overrides for base URLs."""
  text = path.read_text(encoding="utf-8")
  data = yaml.safe_load(text)
  if not isinstance(data, dict):
    raise ValueError(f"Config must be a YAML mapping: {path}")
  overrides: dict[str, Any] = {}
  if os.environ.get("EZKEY_ADMIN_BASE_URL"):
    overrides.setdefault("base_urls", {})[
      "admin"
    ] = os.environ["EZKEY_ADMIN_BASE_URL"].rstrip("/")
  if os.environ.get("EZKEY_AUTH_BASE_URL"):
    overrides.setdefault("base_urls", {})[
      "auth"
    ] = os.environ["EZKEY_AUTH_BASE_URL"].rstrip("/")
  if os.environ.get("EZKEY_CRYPTO_BASE_URL"):
    overrides.setdefault("base_urls", {})[
      "crypto"
    ] = os.environ["EZKEY_CRYPTO_BASE_URL"].rstrip("/")
  merged = _deep_merge(data, overrides) if overrides else data
  return ScenarioConfig(raw=merged)
