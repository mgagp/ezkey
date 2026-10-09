#!/usr/bin/env bash
#
# stack-health-check — live-stack health spot-check after a known workload.
# Reuses javamelody-curated for collector extraction, then scores JavaMelody +
# container logs against config/quality-gate/thresholds.json.
#
# Usage (Git Bash on Windows, Linux, or macOS), from repo root:
#   ./scripts/stack-health-check.sh
#   ./scripts/stack-health-check.sh --output-dir logs/quality-gate/.../health
#   ./scripts/stack-health-check.sh --skip-javamelody   # reuse existing dump
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

export LC_ALL="${LC_ALL:-C.UTF-8}"
export LANG="${LANG:-C.UTF-8}"

KEYWORD="stack-health-check"
OUTPUT_DIR=""
SKIP_JM=0
COLLECTOR_URL="http://localhost:8088"

usage() {
  cat <<'EOF'
Usage: ./scripts/stack-health-check.sh [options]

  --output-dir DIR     Write health artifacts here (default: logs/quality-gate/health-<utc>)
  --skip-javamelody    Do not re-dump the collector; reuse logs/javamelody/
  --collector-url URL  JavaMelody collector (default http://localhost:8088)
  -h, --help           Show this help

Keyword: stack-health-check (also invoked by quality-gate)
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --output-dir)
      OUTPUT_DIR="${2:-}"
      shift 2
      ;;
    --skip-javamelody)
      SKIP_JM=1
      shift
      ;;
    --collector-url)
      COLLECTOR_URL="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -z "$OUTPUT_DIR" ]]; then
  OUTPUT_DIR="$REPO_ROOT/logs/quality-gate/health-$(date -u +"%Y%m%dT%H%M%SZ")"
fi
mkdir -p "$OUTPUT_DIR"
JM_COPY_DIR="$OUTPUT_DIR/javamelody"
mkdir -p "$JM_COPY_DIR"

redact_line() {
  # Never print secrets; redact common credential patterns.
  sed -E \
    -e 's/(password|passwd|pwd)(=|:[[:space:]]*)[^[:space:],;]+/\1\2***REDACTED***/Ig' \
    -e 's/(token|bearer|api[_-]?key|secret|authorization)(=|:[[:space:]]*|[[:space:]]+)[^[:space:],;]+/\1\2***REDACTED***/Ig' \
    -e 's/Bearer[[:space:]]+[A-Za-z0-9._~+\/=-]+/Bearer ***REDACTED***/Ig'
}

echo "[$KEYWORD] output: $OUTPUT_DIR"

if [[ "$SKIP_JM" -eq 0 ]]; then
  echo "[$KEYWORD] dumping JavaMelody via javamelody-curated…"
  ./scripts/javamelody-curated.sh --collector-url "$COLLECTOR_URL" --min-hits 1 --top 15 \
    >"$OUTPUT_DIR/javamelody-curated.log" 2>&1 || {
    echo "[$KEYWORD] WARNING: javamelody-curated exited non-zero (see javamelody-curated.log)" >&2
  }
else
  echo "[$KEYWORD] --skip-javamelody: reusing existing logs/javamelody/"
fi

if [[ -d "$REPO_ROOT/logs/javamelody" ]]; then
  cp -a "$REPO_ROOT/logs/javamelody/." "$JM_COPY_DIR/" 2>/dev/null || true
fi

echo "[$KEYWORD] scanning container status and logs…"
LOG_SCAN_JSON="$OUTPUT_DIR/container-logs.json"
LOG_SCAN_MD="$OUTPUT_DIR/container-logs.md"
TMP_SERVICES="$OUTPUT_DIR/.services.tmp"
docker ps -a --filter "name=ezkey" --format '{{.Names}}' >"$TMP_SERVICES" || true

python3 - "$TMP_SERVICES" "$LOG_SCAN_JSON" "$LOG_SCAN_MD" <<'PY'
import json, os, re, subprocess, sys
from collections import Counter

names_path, out_json, out_md = sys.argv[1], sys.argv[2], sys.argv[3]
names = [n.strip() for n in open(names_path, encoding="utf-8") if n.strip()]

SECRET_RE = re.compile(
    r"(password|passwd|pwd|token|bearer|api[_-]?key|secret|authorization)\s*[=:]\s*\S+",
    re.I,
)
BEARER_RE = re.compile(r"Bearer\s+[A-Za-z0-9._~+/=-]+", re.I)

def redact(s: str) -> str:
    s = SECRET_RE.sub(lambda m: m.group(1) + "=***REDACTED***", s)
    s = BEARER_RE.sub("Bearer ***REDACTED***", s)
    return s

def signature(line: str) -> str:
    s = redact(line.strip())
    s = re.sub(r"\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}:\d{2}(?:\.\d+)?Z?", "<TS>", s)
    s = re.sub(r"\b\d{2}:\d{2}:\d{2}(?:\.\d+)?\b", "<TIME>", s)
    s = re.sub(r"\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b", "<UUID>", s, flags=re.I)
    s = re.sub(r"\b\d{5,}\b", "<N>", s)
    s = re.sub(r"\s+", " ", s)
    return s[:240]

ERROR_HINT = re.compile(r"\b(ERROR|Exception|OutOfMemoryError|FATAL)\b", re.I)
WARN_HINT = re.compile(r"\bWARN(ING)?\b", re.I)

def is_oneshot(name: str) -> bool:
    n = name.lower()
    return any(s in n for s in ("migration", "db-grants", "bootstrap-init"))

services = []
for name in names:
    inspect = {}
    try:
        raw = subprocess.check_output(
            ["docker", "inspect", name],
            text=True,
            stderr=subprocess.DEVNULL,
        )
        inspect = json.loads(raw)[0]
    except Exception:
        pass
    state = inspect.get("State", {})
    health = (state.get("Health") or {}).get("Status")
    restarts = int(inspect.get("RestartCount") or 0)
    oom = bool(state.get("OOMKilled"))
    # Docker often leaves OOMKilled=false after SIGKILL(137) from the host OOM killer.
    exit_code = state.get("ExitCode")
    status = state.get("Status")
    if exit_code == 137:
        oom = True

    try:
        logs = subprocess.check_output(
            ["docker", "logs", "--tail", "4000", name],
            stderr=subprocess.STDOUT,
            text=True,
            errors="replace",
        )
    except Exception as exc:
        logs = f"<log-fetch-failed: {exc}>"

    err_c = Counter()
    warn_c = Counter()
    err_total = 0
    warn_total = 0
    for line in logs.splitlines():
        if ERROR_HINT.search(line):
            err_total += 1
            err_c[signature(line)] += 1
        elif WARN_HINT.search(line):
            warn_total += 1
            warn_c[signature(line)] += 1

    oneshot = is_oneshot(name)
    services.append(
        {
            "name": name,
            "status": status,
            "health": health,
            "restarts": restarts,
            "oomKilled": oom,
            "exitCode": exit_code,
            "oneshot": oneshot,
            "errorCount": err_total,
            "warnCount": warn_total,
            "errorSignatures": [
                {"signature": k, "count": v} for k, v in err_c.most_common(30)
            ],
            "warnSignatures": [
                {"signature": k, "count": v} for k, v in warn_c.most_common(20)
            ],
        }
    )

from datetime import datetime, timezone
payload = {
    "generatedAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
    "serviceCount": len(services),
    "services": services,
    "totals": {
        "errorLines": sum(s["errorCount"] for s in services),
        "warnLines": sum(s["warnCount"] for s in services),
        "oomKills": sum(1 for s in services if s["oomKilled"]),
        "unhealthy": sum(
            1
            for s in services
            if (not s.get("oneshot")) and s.get("health") == "unhealthy"
        ),
        "maxRestarts": max((s["restarts"] for s in services if not s.get("oneshot")), default=0),
    },
}
with open(out_json, "w", encoding="utf-8") as f:
    json.dump(payload, f, indent=2)
    f.write("\n")

lines = ["# Container log scan", "", f"Services: {len(services)}", ""]
for s in services:
    lines.append(
        f"## {s['name']} — status={s.get('status')} health={s.get('health')} "
        f"restarts={s['restarts']} oom={s['oomKilled']} errors={s['errorCount']} warns={s['warnCount']}"
    )
    for sig in s["errorSignatures"][:8]:
        lines.append(f"- ERROR ×{sig['count']}: {sig['signature']}")
    lines.append("")
with open(out_md, "w", encoding="utf-8") as f:
    f.write("\n".join(lines) + "\n")
print(f"scanned {len(services)} ezkey containers")
PY

rm -f "$TMP_SERVICES"

JM_JSON="$REPO_ROOT/logs/javamelody/javamelody.curated.json"
if [[ ! -f "$JM_JSON" && -f "$JM_COPY_DIR/javamelody.curated.json" ]]; then
  JM_JSON="$JM_COPY_DIR/javamelody.curated.json"
fi

echo "[$KEYWORD] scoring against thresholds…"
node "$SCRIPT_DIR/stack-health-check.mjs" \
  --output-dir "$OUTPUT_DIR" \
  --jm-json "$JM_JSON" \
  --log-scan "$LOG_SCAN_JSON" \
  --thresholds "$REPO_ROOT/config/quality-gate/thresholds.json" \
  --allowlist "$REPO_ROOT/config/quality-gate/log-allowlist.txt"

echo "[$KEYWORD] done → $OUTPUT_DIR"
