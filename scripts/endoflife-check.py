#!/usr/bin/env python3
# Copyright (c) 2026 Ezkey. SPDX-License-Identifier: Apache-2.0
"""Monthly endoflife.date check for the major-upgrades register.

Reads product-docs/global/hygiene/major-upgrades/registry.yaml, queries
https://endoflife.date for each entry with an eol_slug, and reports products
within WARN_DAYS of EOL (or already EOL). Optionally opens/updates one standing
GitHub issue labeled ``hygiene``.

Non-blocking by design: exit 0 when the check completes (findings open/update
an issue). Exit 2 only on usage/config errors.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from datetime import date, datetime, timedelta
from pathlib import Path
from typing import Any

try:
    import yaml
except ImportError:  # pragma: no cover - runner installs PyYAML
    print("FAIL: PyYAML required (pip install pyyaml)", file=sys.stderr)
    sys.exit(2)

REPO_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_REGISTRY = (
    REPO_ROOT / "product-docs/global/hygiene/major-upgrades/registry.yaml"
)
WARN_DAYS = 90
API_BASE = "https://endoflife.date/api"
ISSUE_MARKER = "<!-- major-upgrades-review -->"
ISSUE_TITLE = "Major upgrades — endoflife.date monthly check"

# Registry eol_slug → endoflife.date API product id when they differ.
SLUG_ALIASES = {
    "openjdk": "temurin",  # Ezkey runs Eclipse Temurin; openjdk slug 404s
    "alpine": "alpine-linux",
}


def _parse_date(value: Any) -> date | None:
    if value is None or value is False:
        return None
    if isinstance(value, date) and not isinstance(value, datetime):
        return value
    if isinstance(value, datetime):
        return value.date()
    if isinstance(value, str):
        try:
            return date.fromisoformat(value[:10])
        except ValueError:
            return None
    return None


def _http_get_json(url: str) -> Any:
    req = urllib.request.Request(
        url,
        headers={"Accept": "application/json", "User-Agent": "ezkey-endoflife-check"},
    )
    with urllib.request.urlopen(req, timeout=30) as resp:  # noqa: S310 — fixed HTTPS API
        return json.loads(resp.read().decode("utf-8"))


def _resolve_slug(slug: str) -> str:
    return SLUG_ALIASES.get(slug, slug)


def _find_cycle(cycles: list[dict[str, Any]], current: str) -> dict[str, Any] | None:
    current_s = str(current)
    for row in cycles:
        cycle = str(row.get("cycle", ""))
        if cycle == current_s:
            return row
        # Allow "4.1" to match when API uses the same minor line.
        if current_s.startswith(cycle + ".") or cycle.startswith(current_s + "."):
            return row
    return None


def _check_sources(entry: dict[str, Any], repo: Path) -> list[str]:
    drift: list[str] = []
    for src in entry.get("sources") or []:
        rel = src.get("file")
        expect = src.get("expect")
        if not rel or expect is None:
            continue
        path = repo / rel
        if not path.is_file():
            drift.append(f"{entry.get('id')}: missing file {rel}")
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        if str(expect) not in text:
            drift.append(
                f"{entry.get('id')}: expect {expect!r} not found in {rel}"
            )
    return drift


def evaluate_registry(
    registry_path: Path,
    *,
    today: date,
    warn_days: int = WARN_DAYS,
) -> tuple[list[dict[str, Any]], list[str], list[dict[str, Any]]]:
    """Return (eol_findings, drift_findings, all_checked_rows)."""
    raw = yaml.safe_load(registry_path.read_text(encoding="utf-8"))
    if not isinstance(raw, list):
        raise SystemExit("FAIL: registry.yaml must be a list of entries")

    deadline = today + timedelta(days=warn_days)
    eol_findings: list[dict[str, Any]] = []
    drift_findings: list[str] = []
    checked: list[dict[str, Any]] = []

    for entry in raw:
        if not isinstance(entry, dict):
            continue
        entry_id = entry.get("id", "?")
        slug = entry.get("eol_slug")
        current = entry.get("current")
        drift_findings.extend(_check_sources(entry, REPO_ROOT))

        if not slug or current is None:
            checked.append(
                {
                    "id": entry_id,
                    "component": entry.get("component"),
                    "current": current,
                    "eol_slug": slug,
                    "status": "manual-follow",
                }
            )
            continue

        api_slug = _resolve_slug(str(slug))
        url = f"{API_BASE}/{api_slug}.json"
        try:
            cycles = _http_get_json(url)
        except urllib.error.HTTPError as exc:
            checked.append(
                {
                    "id": entry_id,
                    "component": entry.get("component"),
                    "current": current,
                    "eol_slug": slug,
                    "api_slug": api_slug,
                    "status": f"api-error HTTP {exc.code}",
                }
            )
            continue
        except OSError as exc:
            checked.append(
                {
                    "id": entry_id,
                    "component": entry.get("component"),
                    "current": current,
                    "eol_slug": slug,
                    "api_slug": api_slug,
                    "status": f"api-error {exc}",
                }
            )
            continue

        if not isinstance(cycles, list):
            checked.append(
                {
                    "id": entry_id,
                    "status": "api-error unexpected payload",
                }
            )
            continue

        row = _find_cycle(cycles, str(current))
        if row is None:
            checked.append(
                {
                    "id": entry_id,
                    "component": entry.get("component"),
                    "current": current,
                    "api_slug": api_slug,
                    "status": "cycle-not-found",
                }
            )
            eol_findings.append(
                {
                    "id": entry_id,
                    "component": entry.get("component"),
                    "current": current,
                    "api_slug": api_slug,
                    "reason": "cycle not found on endoflife.date",
                    "eol": None,
                }
            )
            continue

        api_eol = _parse_date(row.get("eol"))
        registry_eol = _parse_date(entry.get("eol"))
        decision = str(entry.get("decision") or "keep")
        # Per major-upgrades README signal table: follow: / accept-risk do not
        # raise EOL proximity unless the recorded eol date itself drifts.
        suppress_proximity = decision.startswith("follow:") or decision == "accept-risk"

        status = "ok"
        reason = None
        if api_eol is not None and api_eol <= today:
            status = "eol"
            reason = f"EOL on {api_eol.isoformat()} (already past)"
        elif api_eol is not None and api_eol <= deadline:
            status = "within-warn"
            days_left = (api_eol - today).days
            reason = (
                f"EOL on {api_eol.isoformat()} "
                f"({days_left} day(s) left; warn window {warn_days}d)"
            )

        eol_date_drift = (
            api_eol is not None
            and registry_eol is not None
            and api_eol != registry_eol
        )
        if eol_date_drift:
            drift_findings.append(
                f"{entry_id}: registry eol {registry_eol} != API eol {api_eol}"
            )

        if reason and suppress_proximity and not eol_date_drift:
            status = f"{status}-suppressed({decision})"
            reason = None

        checked.append(
            {
                "id": entry_id,
                "component": entry.get("component"),
                "current": current,
                "api_slug": api_slug,
                "api_eol": api_eol.isoformat() if api_eol else None,
                "registry_eol": registry_eol.isoformat() if registry_eol else None,
                "decision": decision,
                "status": status,
            }
        )
        if reason:
            eol_findings.append(
                {
                    "id": entry_id,
                    "component": entry.get("component"),
                    "current": current,
                    "api_slug": api_slug,
                    "eol": api_eol.isoformat() if api_eol else None,
                    "reason": reason,
                    "decision": decision,
                }
            )

    return eol_findings, drift_findings, checked


def _format_issue_body(
    *,
    today: date,
    eol_findings: list[dict[str, Any]],
    drift_findings: list[str],
    checked: list[dict[str, Any]],
) -> str:
    lines = [
        ISSUE_MARKER,
        "",
        f"_Generated by `.github/workflows/endoflife-check.yml` on {today.isoformat()}_.",
        "",
        "Source register: "
        "`product-docs/global/hygiene/major-upgrades/registry.yaml`.",
        "Warn window: **90 days** before EOL (or already EOL).",
        "",
        "## EOL findings",
        "",
    ]
    if not eol_findings:
        lines.append("_None — no registered cycle is within 90 days of EOL._")
    else:
        lines.append("| id | component | current | EOL | reason |")
        lines.append("|----|-----------|---------|-----|--------|")
        for f in eol_findings:
            lines.append(
                f"| `{f['id']}` | {f.get('component', '')} | `{f.get('current')}` "
                f"| {f.get('eol') or '—'} | {f.get('reason', '')} |"
            )

    lines.extend(["", "## Source / eol drift", ""])
    if not drift_findings:
        lines.append("_None._")
    else:
        for d in drift_findings:
            lines.append(f"- {d}")

    lines.extend(
        [
            "",
            "## Checked entries",
            "",
            "| id | current | API slug | API EOL | status |",
            "|----|---------|----------|---------|--------|",
        ]
    )
    for c in checked:
        lines.append(
            f"| `{c.get('id')}` | `{c.get('current')}` | "
            f"{c.get('api_slug') or c.get('eol_slug') or '—'} | "
            f"{c.get('api_eol') or '—'} | {c.get('status')} |"
        )

    lines.extend(
        [
            "",
            "## Next steps",
            "",
            "- Quarterly register walk (Jan / Apr / Jul / Oct Monday full "
            "`dependabot-curated`) — see "
            "`product-docs/global/hygiene/major-upgrades/README.md`.",
            "- Do **not** treat this issue as a CI failure; it is signal only.",
            "",
        ]
    )
    return "\n".join(lines)


def _gh_api(
    method: str,
    path: str,
    *,
    token: str,
    body: dict[str, Any] | None = None,
) -> Any:
    url = f"https://api.github.com{path}"
    data = None if body is None else json.dumps(body).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=data,
        method=method,
        headers={
            "Accept": "application/vnd.github+json",
            "Authorization": f"Bearer {token}",
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "ezkey-endoflife-check",
            "Content-Type": "application/json",
        },
    )
    with urllib.request.urlopen(req, timeout=60) as resp:  # noqa: S310
        raw = resp.read().decode("utf-8")
        return json.loads(raw) if raw else None


def _find_standing_issue(repo: str, token: str) -> dict[str, Any] | None:
    # Search open + closed so we can reopen when findings return.
    q = (
        f'repo:{repo} "{ISSUE_MARKER}" in:body '
        f'label:hygiene author:app/github-actions'
    )
    result = _gh_api(
        "GET",
        f"/search/issues?q={urllib.parse.quote(q)}&per_page=5",
        token=token,
    )
    items = (result or {}).get("items") or []
    if not items:
        # Fallback: any hygiene-labeled issue with the marker (human-created once).
        q2 = f'repo:{repo} "{ISSUE_MARKER}" in:body label:hygiene'
        result2 = _gh_api(
            "GET",
            f"/search/issues?q={urllib.parse.quote(q2)}&per_page=5",
            token=token,
        )
        items = (result2 or {}).get("items") or []
    if not items:
        return None
    # Prefer open; else most recently updated.
    items_sorted = sorted(
        items,
        key=lambda i: (0 if i.get("state") == "open" else 1, i.get("updated_at", "")),
        reverse=False,
    )
    open_items = [i for i in items_sorted if i.get("state") == "open"]
    return open_items[0] if open_items else items_sorted[0]


def sync_issue(
    *,
    repo: str,
    token: str,
    body: str,
    has_findings: bool,
) -> str:
    existing = _find_standing_issue(repo, token)
    if has_findings:
        if existing:
            number = existing["number"]
            patch: dict[str, Any] = {
                "title": ISSUE_TITLE,
                "body": body,
                "state": "open",
            }
            _gh_api("PATCH", f"/repos/{repo}/issues/{number}", token=token, body=patch)
            # Ensure label present.
            _gh_api(
                "POST",
                f"/repos/{repo}/issues/{number}/labels",
                token=token,
                body={"labels": ["hygiene"]},
            )
            return f"updated issue #{number}"
        created = _gh_api(
            "POST",
            f"/repos/{repo}/issues",
            token=token,
            body={
                "title": ISSUE_TITLE,
                "body": body,
                "labels": ["hygiene"],
            },
        )
        return f"opened issue #{created['number']}"

    # No EOL findings: if a standing issue exists, update body and close.
    if existing and existing.get("state") == "open":
        number = existing["number"]
        _gh_api(
            "PATCH",
            f"/repos/{repo}/issues/{number}",
            token=token,
            body={
                "title": ISSUE_TITLE,
                "body": body,
                "state": "closed",
                "state_reason": "completed",
            },
        )
        return f"closed issue #{number} (no EOL findings)"
    return "no issue action (no EOL findings)"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--registry",
        type=Path,
        default=DEFAULT_REGISTRY,
        help="Path to registry.yaml",
    )
    parser.add_argument(
        "--warn-days",
        type=int,
        default=WARN_DAYS,
        help="Days before EOL that trigger a finding (default 90)",
    )
    parser.add_argument(
        "--today",
        type=str,
        default=None,
        help="Override today (YYYY-MM-DD) for tests",
    )
    parser.add_argument(
        "--json",
        action="store_true",
        help="Print machine-readable summary to stdout",
    )
    parser.add_argument(
        "--update-issue",
        action="store_true",
        help="Open/update/close the standing hygiene GitHub issue",
    )
    args = parser.parse_args(argv)

    today = date.fromisoformat(args.today) if args.today else date.today()
    if not args.registry.is_file():
        print(f"FAIL: registry not found: {args.registry}", file=sys.stderr)
        return 2

    eol_findings, drift_findings, checked = evaluate_registry(
        args.registry, today=today, warn_days=args.warn_days
    )
    # Issue opens on EOL proximity only (not source drift alone).
    has_eol = len(eol_findings) > 0
    body = _format_issue_body(
        today=today,
        eol_findings=eol_findings,
        drift_findings=drift_findings,
        checked=checked,
    )

    summary = {
        "date": today.isoformat(),
        "warn_days": args.warn_days,
        "eol_findings": eol_findings,
        "drift_findings": drift_findings,
        "checked_count": len(checked),
        "has_eol_findings": has_eol,
    }

    if args.json:
        print(json.dumps(summary, indent=2))
    else:
        print(f"endoflife-check {today.isoformat()} warn={args.warn_days}d")
        print(f"  checked: {len(checked)}")
        print(f"  EOL findings: {len(eol_findings)}")
        for f in eol_findings:
            print(f"    - {f['id']}: {f['reason']}")
        print(f"  drift findings: {len(drift_findings)}")
        for d in drift_findings:
            print(f"    - {d}")

    if args.update_issue:
        token = os.environ.get("GITHUB_TOKEN") or os.environ.get("GH_TOKEN")
        repo = os.environ.get("GITHUB_REPOSITORY")
        if not token or not repo:
            print(
                "FAIL: --update-issue needs GITHUB_TOKEN and GITHUB_REPOSITORY",
                file=sys.stderr,
            )
            return 2
        action = sync_issue(
            repo=repo, token=token, body=body, has_findings=has_eol
        )
        print(f"  issue: {action}")
        summary["issue_action"] = action

    # Always exit 0 on successful check (non-blocking).
    return 0


if __name__ == "__main__":
    sys.exit(main())
