#!/usr/bin/env python3
"""
Compare PostgreSQL schemas from two databases (e.g. reference vs candidate migrations).

Uses `pg_dump --schema-only` and normalizes output by removing PostgreSQL 18+
`\\restrict` / `\\unrestrict` lines (dump-specific tokens, not schema DDL).

Exit codes:
  0 - schemas match after normalization
  1 - mismatch or error

Usage:
  python scripts/schema_compare.py \\
    --ref-url postgresql://postgres:ezkey@localhost:5433/ezkey_db \\
    --new-url postgresql://postgres:ezkey@localhost:5434/ezkey_db

Requires `pg_dump` on PATH (e.g. from PostgreSQL client tools), or set PG_DUMP_CMD
to a full command prefix such as:
  docker run --rm -i -e PGPASSWORD=ezkey postgres:18-alpine pg_dump
"""

from __future__ import annotations

import argparse
import os
import subprocess
import sys
import tempfile
from pathlib import Path


def _pg_dump_schema(url: str, pg_dump_cmd: list[str] | None) -> str:
    if pg_dump_cmd:
        cmd = list(pg_dump_cmd) + ["--schema-only", url]
    else:
        bin_name = os.environ.get("PG_DUMP", "pg_dump")
        cmd = [bin_name, "--schema-only", url]
    result = subprocess.run(
        cmd,
        capture_output=True,
        text=True,
        check=False,
        encoding="utf-8",
        errors="replace",
    )
    if result.returncode != 0:
        raise RuntimeError(
            f"pg_dump failed ({result.returncode}): {result.stderr or result.stdout}"
        )
    return result.stdout


def _normalize_dump(sql: str) -> str:
    lines = []
    for line in sql.splitlines():
        if line.startswith("\\restrict") or line.startswith("\\unrestrict"):
            continue
        lines.append(line)
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--ref-url",
        required=True,
        help="Reference database URL (e.g. postgresql://user:pass@host:port/db)",
    )
    parser.add_argument(
        "--new-url",
        required=True,
        help="Candidate database URL to compare against reference",
    )
    parser.add_argument(
        "--pg-dump",
        nargs="+",
        help="Optional pg_dump command prefix (e.g. docker run --rm -i postgres:18-alpine pg_dump). "
        "If omitted, uses PG_DUMP env or plain `pg_dump`.",
    )
    parser.add_argument(
        "--keep-dumps",
        action="store_true",
        help="Print paths of written temp files on mismatch (for debugging)",
    )
    args = parser.parse_args()

    try:
        ref_sql = _normalize_dump(_pg_dump_schema(args.ref_url, args.pg_dump))
        new_sql = _normalize_dump(_pg_dump_schema(args.new_url, args.pg_dump))
    except Exception as e:
        print(f"ERROR: {e}", file=sys.stderr)
        return 1

    if ref_sql == new_sql:
        print("OK: schemas are identical after normalization (restrict lines stripped).")
        return 0

    with tempfile.NamedTemporaryFile(
        mode="w", suffix="_ref.sql", delete=False, encoding="utf-8"
    ) as f:
        f.write(ref_sql)
        ref_path = f.name
    with tempfile.NamedTemporaryFile(
        mode="w", suffix="_new.sql", delete=False, encoding="utf-8"
    ) as f:
        f.write(new_sql)
        new_path = f.name

    print("ERROR: schema dumps differ after normalization.", file=sys.stderr)
    print(f"  Reference dump: {ref_path}", file=sys.stderr)
    print(f"  Candidate dump: {new_path}", file=sys.stderr)
    try:
        diff = subprocess.run(
            ["diff", "-u", ref_path, new_path],
            capture_output=True,
            text=True,
        )
        if diff.stdout:
            print(diff.stdout[:200000], file=sys.stderr)
        elif diff.stderr:
            print(diff.stderr, file=sys.stderr)
    except FileNotFoundError:
        print("(Install `diff` or compare files manually.)", file=sys.stderr)

    if not args.keep_dumps:
        try:
            Path(ref_path).unlink(missing_ok=True)
            Path(new_path).unlink(missing_ok=True)
        except OSError:
            pass
    return 1


if __name__ == "__main__":
    sys.exit(main())
