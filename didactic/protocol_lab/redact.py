"""Recursive redaction for sharing artifacts."""

from __future__ import annotations

import copy
import json
from pathlib import Path
from typing import Any


DEFAULT_FIELDS = frozenset(
  {
    "authorization",
    "privatekey",
    "deviceprooftokensigned",
    "enrollmentprooftokensigned",
    "admin_bearer_token",
    "bearertoken",
  }
)


def redact_structure(
  obj: Any,
  extra_fields: frozenset[str] | None = None,
  *,
  token: str = "***REDACTED***",
) -> Any:
  """Return a deep-copied JSON-like structure with matching keys replaced."""
  fields = frozenset({s.lower() for s in (extra_fields or frozenset())} | DEFAULT_FIELDS)

  def walk(x: Any) -> Any:
    if isinstance(x, dict):
      out: dict[str, Any] = {}
      for k, v in x.items():
        if k.lower() in fields:
          out[k] = token
        else:
          out[k] = walk(v)
      return out
    if isinstance(x, list):
      return [walk(i) for i in x]
    return x

  return walk(copy.deepcopy(obj))


def redact_steps_jsonl(artifacts_dir: Path, *, extra_fields: str | None) -> Path:
  path = artifacts_dir / "steps.jsonl"
  if not path.exists():
    raise FileNotFoundError(path)
  ex: frozenset[str] | None = None
  if extra_fields:
    ex = frozenset({s.strip().lower() for s in extra_fields.split(",") if s.strip()})
  out_path = artifacts_dir / "steps.redacted.jsonl"
  with path.open(encoding="utf-8") as fin, out_path.open("w", encoding="utf-8") as fout:
    for raw in fin:
      raw = raw.strip()
      if not raw:
        continue
      row = json.loads(raw)
      fout.write(
        json.dumps(redact_structure(row, ex), ensure_ascii=False) + "\n"
      )
  return out_path
