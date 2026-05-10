"""Argument parsing for python -m protocol_lab."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

from .config import load_config
from .redact import redact_steps_jsonl
from .render import render_files
from .runner import run_scenario


def main(argv: list[str] | None = None) -> int:
  p = argparse.ArgumentParser(
    prog="protocol_lab",
    description="Ezkey didactic protocol runner (Postman + Crypto API parity).",
  )
  sub = p.add_subparsers(dest="cmd", required=True)

  run_p = sub.add_parser("run", help="Execute scenario against live stack")
  run_p.add_argument("--config", "-c", type=Path, required=True)
  run_p.add_argument("--artifacts-dir", "-a", type=Path, required=True)
  run_p.add_argument("--dry-run", action="store_true")
  run_p.add_argument("--skip-admin-wait", action="store_true")

  rnd_p = sub.add_parser(
    "render",
    help="Fill Markdown template from steps.jsonl + summary.json",
  )
  rnd_p.add_argument("--template", "-t", type=Path, required=True)
  rnd_p.add_argument("--artifacts-dir", "-a", type=Path, required=True)
  rnd_p.add_argument("--output", "-o", type=Path, required=True)
  rnd_p.add_argument(
    "--publish-secrets",
    action="store_true",
    help="Include bearer tokens, private keys, and full signatures in Markdown (unsafe outside a lab)",
  )
  rnd_p.add_argument(
    "--full-transcript",
    action="store_true",
    help="Alias for --publish-secrets; use for clean-start didactic articles (complete protocol material)",
  )

  rdx_p = sub.add_parser("redact", help="Write steps.redacted.jsonl beside steps.jsonl")
  rdx_p.add_argument("--artifacts-dir", "-a", type=Path, required=True)
  rdx_p.add_argument(
    "--fields",
    help="Comma-separated extra JSON keys (case-insensitive) to redact",
  )

  args = p.parse_args(argv)
  try:
    if args.cmd == "run":
      cfg = load_config(args.config)
      run_scenario(
        cfg,
        args.artifacts_dir,
        dry_run=args.dry_run,
        skip_admin_wait=args.skip_admin_wait,
      )
      print(f"Artifacts written under {args.artifacts_dir}")
    elif args.cmd == "render":
      reveal = args.publish_secrets or getattr(args, "full_transcript", False)
      render_files(
        args.template,
        args.artifacts_dir,
        args.output,
        publish_secrets=reveal,
      )
      print(f"Rendered {args.output}")
    elif args.cmd == "redact":
      out = redact_steps_jsonl(args.artifacts_dir, extra_fields=args.fields)
      print(f"Written {out}")
    return 0
  except Exception as exc:  # noqa: BLE001 — CLI envelope
    print(f"protocol_lab error: {exc}", file=sys.stderr)
    return 1


if __name__ == "__main__":
  raise SystemExit(main())
