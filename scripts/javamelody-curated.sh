#!/usr/bin/env bash
#
# javamelody-curated — extract and rank JavaMelody counters from a live collector
# (or a frozen raw dump). Report-only — not a CI gate.
#
# Usage (Git Bash on Windows, Linux, or macOS), from repo root:
#   ./scripts/javamelody-curated.sh
#   ./scripts/javamelody-curated.sh --offline
#   ./scripts/javamelody-curated.sh --collector-url http://localhost:8088 --period tout
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

KEYWORD="javamelody-curated"
OUTPUT_DIR="$REPO_ROOT/logs/javamelody"
RAW_DIR="$OUTPUT_DIR/raw"
COLLECTOR_URL="http://localhost:8088"
PERIOD="tout"
OFFLINE=0
MIN_HITS=50
TOP=15

APPS=(admin-api auth-api integration-api)
GRAPHS=(usedMemory cpu gc httpHitsRate sqlHitsRate activeThreads activeConnections usedConnections waitingConnections httpMeanTimes sqlMeanTimes)

usage() {
  cat <<'EOF'
Usage: ./scripts/javamelody-curated.sh [options]

  --collector-url URL  Collector UI (default http://localhost:8088)
  --period PERIOD      JavaMelody period (default tout)
  --offline            Curate existing logs/javamelody/raw/ only (no HTTP)
  --min-hits N         Drop request names below this hit count (default 50)
  --top N              Rows per family in the curated tables (default 15)
  -h, --help           Show this help

Outputs under logs/javamelody/ (gitignored). Keyword: javamelody-curated
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --collector-url)
      COLLECTOR_URL="${2:-}"
      shift 2
      ;;
    --period)
      PERIOD="${2:-}"
      shift 2
      ;;
    --offline)
      OFFLINE=1
      shift
      ;;
    --min-hits)
      MIN_HITS="${2:-}"
      shift 2
      ;;
    --top)
      TOP="${2:-}"
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

mkdir -p "$RAW_DIR"

dump_url() {
  local out="$1"
  local url="$2"
  local code
  code="$(curl -sS -o "$out" -w "%{http_code}" "$url" || true)"
  if [[ "$code" != "200" ]]; then
    echo "[$KEYWORD] WARNING: HTTP $code for $url (saved $out)" >&2
    return 1
  fi
  return 0
}

if [[ "$OFFLINE" -eq 0 ]]; then
  echo "[$KEYWORD] dumping collector XML ($COLLECTOR_URL period=$PERIOD)…"
  captured_at="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
  for app in "${APPS[@]}"; do
    dump_url "$RAW_DIR/collector-${app}.xml" \
      "${COLLECTOR_URL}/?application=${app}&format=xml&period=${PERIOD}" || true
  done

  echo "[$KEYWORD] dumping lastValue graphs…"
  for app in "${APPS[@]}"; do
    for graph in "${GRAPHS[@]}"; do
      dump_url "$RAW_DIR/lastvalue-${app}-${graph}.txt" \
        "${COLLECTOR_URL}/?application=${app}&part=lastValue&graph=${graph}" || true
    done
  done

  echo "[$KEYWORD] attempting direct management-port XML (backup)…"
  dump_url "$RAW_DIR/direct-admin-api.xml" \
    "http://localhost:9081/actuator/monitoring?format=xml" || true
  dump_url "$RAW_DIR/direct-auth-api.xml" \
    "http://localhost:8085/actuator/monitoring?format=xml" || true
  dump_url "$RAW_DIR/direct-integration-api.xml" \
    "http://localhost:7081/actuator/monitoring?format=xml" || true

  cat > "$RAW_DIR/snapshot-meta.json" <<EOF
{
  "keyword": "${KEYWORD}",
  "capturedAt": "${captured_at}",
  "collectorUrl": "${COLLECTOR_URL}",
  "period": "${PERIOD}",
  "apps": ["admin-api", "auth-api", "integration-api"]
}
EOF
else
  echo "[$KEYWORD] --offline: using existing $RAW_DIR"
  [[ -f "$RAW_DIR/collector-admin-api.xml" ]] || {
    echo "[$KEYWORD] ERROR: missing $RAW_DIR/collector-admin-api.xml" >&2
    exit 1
  }
fi

echo "[$KEYWORD] curating…"
node "$SCRIPT_DIR/javamelody-curated.mjs" \
  --raw-dir "$RAW_DIR" \
  --output-dir "$OUTPUT_DIR" \
  --min-hits "$MIN_HITS" \
  --top "$TOP"

echo "[$KEYWORD] done."
echo "  Curated Markdown: $OUTPUT_DIR/javamelody.curated.md"
echo "  Curated JSON:     $OUTPUT_DIR/javamelody.curated.json"
