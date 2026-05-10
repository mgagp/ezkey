"""Fill Markdown templates with HTTP transcripts from artifacts."""

from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any

_ARTIFACT_REF = re.compile(
  r"<<<ARTIFACT\s+([a-z0-9_.]+)\s+(request|response)>>>\s*"
)

_SUMMARY_TTL_REF = re.compile(r"<<<\s*SUMMARY_TTL\s*>>>\s*")


def _scrub_headers(
  hdrs: dict[str, str],
  *,
  publish_secrets: bool,
) -> dict[str, str]:
  out = dict(hdrs)
  if not publish_secrets:
    au = out.get("Authorization")
    if au:
      out["Authorization"] = "Bearer ***REDACTED***"
  return out


def _scrub_json(obj: Any, *, publish_secrets: bool) -> Any:
  if publish_secrets:
    return obj
  if isinstance(obj, dict):
    red = {}
    for k, v in obj.items():
      lk = k.lower()
      if lk in ("privatekey",):
        red[k] = "***REDACTED***"
      elif lk.endswith("signature") or lk.endswith("signed"):
        if isinstance(v, str) and len(v) > 12:
          red[k] = v[:8] + "…REDACTED"
        else:
          red[k] = "***REDACTED***"
      else:
        red[k] = _scrub_json(v, publish_secrets=publish_secrets)
    tok = red.get("enrollmentProofToken")
    if isinstance(tok, str) and len(tok) > 8:
      red["enrollmentProofToken"] = tok[:4] + "…REDACTED"
    tok2 = red.get("deviceProofToken")
    if isinstance(tok2, str) and len(tok2) > 8:
      red["deviceProofToken"] = tok2[:4] + "…REDACTED"
    tok3 = red.get("enrollment_proof_token")
    if isinstance(tok3, str) and len(tok3) > 8:
      red["enrollment_proof_token"] = tok3[:4] + "…REDACTED"
    return red
  if isinstance(obj, list):
    return [_scrub_json(v, publish_secrets=publish_secrets) for v in obj]
  return obj


def _format_http_block(
  row: dict[str, Any],
  section: str,
  *,
  publish_secrets: bool,
) -> str:
  http = row.get("http")
  if not http:
    return "_No HTTP transcript for this step._\n"
  if section == "request":
    payload = {
      "method": http.get("method") or "?",
      "url": http.get("url"),
      "request_headers": _scrub_headers(
        dict(http.get("request_headers") or {}),
        publish_secrets=publish_secrets,
      ),
      "request_body": _scrub_json(
        http.get("request_body"),
        publish_secrets=publish_secrets,
      ),
    }
    return "```json\n" + json.dumps(payload, indent=2, ensure_ascii=False) + "\n```\n"
  if section == "response":
    payload = {
      "status_code": http.get("status_code"),
      "response_body": _scrub_json(
        http.get("response_body"),
        publish_secrets=publish_secrets,
      ),
    }
    return "```json\n" + json.dumps(payload, indent=2, ensure_ascii=False) + "\n```\n"
  return ""


def render_markdown(
  template_text: str,
  steps_by_id: dict[str, dict[str, Any]],
  *,
  summary: dict[str, Any] | None = None,
  publish_secrets: bool = False,
) -> str:
  """Substitute placeholders: <<<ARTIFACT step_id request|response>>> ; <<<SUMMARY_TTL>>>.

  SUMMARY_TTL inserts auth-attempt TTL object from artifacts/summary.json.
  """

  def replacer(m: re.Match[str]) -> str:
    step_id = m.group(1)
    part = m.group(2)
    row = steps_by_id.get(step_id)
    if row is None:
      return f"_Missing step `{step_id}` in steps.jsonl._\n"
    return _format_http_block(row, part, publish_secrets=publish_secrets)

  body = _ARTIFACT_REF.sub(replacer, template_text)
  ttl = (
    summary.get("auth_attempt_ttl", {}) if isinstance(summary, dict) else {}
  )
  ttl_block = (
    "```json\n" + json.dumps(ttl, indent=2, ensure_ascii=False) + "\n```\n"
  )
  body = _SUMMARY_TTL_REF.sub(ttl_block, body)
  return body


def load_summary(artifacts_dir: Path) -> dict[str, Any] | None:
  path = artifacts_dir / "summary.json"
  if not path.exists():
    return None
  return json.loads(path.read_text(encoding="utf-8"))


def render_files(
  template_path: Path,
  artifacts_dir: Path,
  output_path: Path,
  *,
  publish_secrets: bool = False,
) -> None:
  from .runner import load_steps_jsonl

  template = template_path.read_text(encoding="utf-8")
  steps_by_id = load_steps_jsonl(artifacts_dir / "steps.jsonl")
  summary = load_summary(artifacts_dir)
  out = render_markdown(
    template,
    steps_by_id,
    summary=summary,
    publish_secrets=publish_secrets,
  )
  output_path.parent.mkdir(parents=True, exist_ok=True)
  output_path.write_text(out, encoding="utf-8")
