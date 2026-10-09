#!/usr/bin/env bash
#
# stack-health-check — thin wrapper: dump JavaMelody into OUTPUT_DIR, then
# score via stack-health-check.mjs (log scan + redaction + thresholds).
#
# Usage (Git Bash / Linux / macOS), from repo root:
#   ./scripts/stack-health-check.sh
#   ./scripts/stack-health-check.sh --output-dir logs/quality-gate/.../health
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

KEYWORD="stack-health-check"
OUTPUT_DIR=""
SKIP_JM=0
COLLECTOR_URL="http://127.0.0.1:8088"
COMPOSE_RETRY_FIRED="${COMPOSE_RETRY_FIRED:-0}"

usage() {
  cat <<'EOF'
Usage: ./scripts/stack-health-check.sh [options]

  --output-dir DIR     Write health artifacts here (default: logs/quality-gate/health-<utc>)
  --skip-javamelody    Reuse existing dump under OUTPUT_DIR/javamelody/ (or logs/javamelody/)
  --collector-url URL  JavaMelody collector (default http://127.0.0.1:8088)
  -h, --help           Show this help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --output-dir) OUTPUT_DIR="${2:-}"; shift 2 ;;
    --skip-javamelody) SKIP_JM=1; shift ;;
    --collector-url) COLLECTOR_URL="${2:-}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; usage >&2; exit 2 ;;
  esac
done

if [[ -z "$OUTPUT_DIR" ]]; then
  OUTPUT_DIR="$REPO_ROOT/logs/quality-gate/health-$(date -u +"%Y%m%dT%H%M%SZ")"
fi
mkdir -p "$OUTPUT_DIR/javamelody"
JM_DIR="$OUTPUT_DIR/javamelody"

echo "[$KEYWORD] output: $OUTPUT_DIR"

JM_OK=0
if [[ "$SKIP_JM" -eq 0 ]]; then
  echo "[$KEYWORD] dumping JavaMelody into $JM_DIR …"
  # javamelody-curated writes under logs/javamelody/; copy into OUTPUT_DIR for freshness.
  if ./scripts/javamelody-curated.sh --collector-url "$COLLECTOR_URL" --min-hits 1 --top 15 \
      >"$OUTPUT_DIR/javamelody-curated.log" 2>&1; then
    JM_OK=1
  else
    echo "[$KEYWORD] javamelody-curated failed (see javamelody-curated.log)" >&2
  fi
  if [[ -d "$REPO_ROOT/logs/javamelody" ]]; then
    cp -a "$REPO_ROOT/logs/javamelody/." "$JM_DIR/" 2>/dev/null || true
  fi
else
  echo "[$KEYWORD] --skip-javamelody"
  if [[ -d "$REPO_ROOT/logs/javamelody" && ! -f "$JM_DIR/javamelody.curated.json" ]]; then
    cp -a "$REPO_ROOT/logs/javamelody/." "$JM_DIR/" 2>/dev/null || true
  fi
  [[ -f "$JM_DIR/javamelody.curated.json" ]] && JM_OK=1
fi

export COMPOSE_RETRY_FIRED
node "$SCRIPT_DIR/stack-health-check.mjs" \
  --output-dir "$OUTPUT_DIR" \
  --jm-dir "$JM_DIR" \
  --jm-ok "$JM_OK" \
  --thresholds "$REPO_ROOT/config/quality-gate/thresholds.json" \
  --allowlist "$REPO_ROOT/config/quality-gate/log-allowlist.txt"

echo "[$KEYWORD] done → $OUTPUT_DIR"
