#!/usr/bin/env bash
#
# java-doctor-curated — punctual Java static-analysis hygiene pass.
# Mirrors Admin UI doctor-curated: SpotBugs + narrow PMD + Semgrep → curated shortlist.
# Report-only — does not fail scripts/build.sh and is not a CI gate.
#
# Usage (Git Bash on Windows, Linux, or macOS), from repo root:
#   ./scripts/java-doctor-curated.sh
#   ./scripts/java-doctor-curated.sh --skip-compile
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

KEYWORD="java-doctor-curated"
OUTPUT_DIR="$REPO_ROOT/logs/java-doctor"
RAW_DIR="$OUTPUT_DIR/raw"
CONFIG_DIR="$REPO_ROOT/config/java-doctor"
SEMGREP_CONFIG="$CONFIG_DIR/semgrep.yml"
SEMGREP_IMAGE="${EZKEY_SEMGREP_IMAGE:-semgrep/semgrep:1.168.0}"

DEFAULT_MODULES=(
  ezkey-core
  ezkey-core-security
  ezkey-admin-api
  ezkey-auth-api
  ezkey-integration-api
)

SKIP_COMPILE=0
MODULES_CSV=""

usage() {
  cat <<'EOF'
Usage: ./scripts/java-doctor-curated.sh [--skip-compile] [--modules csv]

  --skip-compile   Skip mvn compile (use when target classes already exist)
  --modules csv    Comma-separated reactor modules (default: production APIs + core)

Outputs under logs/java-doctor/ (gitignored). Keyword: java-doctor-curated
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-compile)
      SKIP_COMPILE=1
      shift
      ;;
    --modules)
      MODULES_CSV="${2:-}"
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

if [[ -z "$MODULES_CSV" ]]; then
  MODULES_CSV="$(IFS=,; echo "${DEFAULT_MODULES[*]}")"
fi

# --- JDK probe (same posture as scripts/build.sh) ---
if [ -z "${JAVA_HOME:-}" ]; then
  for candidate in \
    "/c/Tools/jdk-25.0.3+9" \
    "/mnt/c/Tools/jdk-25.0.3+9" \
    "/c/Tools/jdk25" \
    "/mnt/c/Tools/jdk25"; do
    if [ -x "$candidate/bin/java" ] || [ -x "$candidate/bin/java.exe" ]; then
      export JAVA_HOME="$candidate"
      break
    fi
  done
fi

if [ -z "${JAVA_HOME:-}" ] && command -v /usr/libexec/java_home >/dev/null 2>&1; then
  if jdk25="$(/usr/libexec/java_home -v 25 2>/dev/null)"; then
    export JAVA_HOME="$jdk25"
  fi
fi

if [ -n "${JAVA_HOME:-}" ] && [ -d "$JAVA_HOME/bin" ]; then
  case ":$PATH:" in
    *":$JAVA_HOME/bin:"*) ;;
    *) export PATH="$JAVA_HOME/bin:$PATH" ;;
  esac
fi

command -v mvn >/dev/null 2>&1 || { echo "Maven (mvn) not found on PATH." >&2; exit 1; }
command -v node >/dev/null 2>&1 || { echo "Node.js (node) not found on PATH (needed for curator)." >&2; exit 1; }

mkdir -p "$RAW_DIR"
rm -f "$RAW_DIR"/*.xml "$RAW_DIR"/*.json 2>/dev/null || true

echo "[$KEYWORD] modules=$MODULES_CSV"
echo "[$KEYWORD] output=$OUTPUT_DIR"

if [[ "$SKIP_COMPILE" -eq 0 ]]; then
  echo "[$KEYWORD] compiling modules (SpotBugs needs classes)…"
  mvn -q compile -pl "$MODULES_CSV" -am -DskipTests
else
  echo "[$KEYWORD] skipping compile (--skip-compile)"
fi

echo "[$KEYWORD] SpotBugs (report-only)…"
mvn -q com.github.spotbugs:spotbugs-maven-plugin:spotbugs \
  -pl "$MODULES_CSV" \
  -Dspotbugs.failOnError=false \
  || echo "[$KEYWORD] WARNING: SpotBugs exited non-zero (continuing; report-only)"

echo "[$KEYWORD] PMD narrow ruleset (report-only)…"
mvn -q org.apache.maven.plugins:maven-pmd-plugin:pmd \
  -pl "$MODULES_CSV" \
  -Dpmd.failOnViolation=false \
  || echo "[$KEYWORD] WARNING: PMD exited non-zero (continuing; report-only)"

# Collect per-module XML into raw/
IFS=',' read -r -a MODULE_ARR <<< "$MODULES_CSV"
for module in "${MODULE_ARR[@]}"; do
  module="$(echo "$module" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')"
  [[ -z "$module" ]] && continue
  sb_xml="$REPO_ROOT/$module/target/spotbugsXml.xml"
  pmd_xml="$REPO_ROOT/$module/target/pmd.xml"
  if [[ -f "$sb_xml" ]]; then
    cp "$sb_xml" "$RAW_DIR/spotbugs-${module}.xml"
  fi
  if [[ -f "$pmd_xml" ]]; then
    cp "$pmd_xml" "$RAW_DIR/pmd-${module}.xml"
  fi
done

# --- Semgrep (local binary or Docker) ---
SEMGREP_JSON="$RAW_DIR/semgrep.json"
SEMGREP_PATHS=()
for module in "${MODULE_ARR[@]}"; do
  module="$(echo "$module" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')"
  src="$module/src/main/java"
  if [[ -d "$REPO_ROOT/$src" ]]; then
    SEMGREP_PATHS+=("$src")
  fi
done

if [[ ${#SEMGREP_PATHS[@]} -eq 0 ]]; then
  echo "[$KEYWORD] ERROR: no src/main/java paths found for modules." >&2
  exit 1
fi

run_semgrep_local() {
  semgrep scan \
    --config "$SEMGREP_CONFIG" \
    --json \
    --quiet \
    --metrics=off \
    --timeout 30 \
    --exclude '**/target/**' \
    --exclude '**/node_modules/**' \
    --exclude '**/.git/**' \
    "${SEMGREP_PATHS[@]}"
}

docker_mount_src() {
  local path="$1"
  if command -v cygpath >/dev/null 2>&1; then
    cygpath -w "$path"
  else
    # Git Bash /c/... → Docker Desktop accepts //c/... or C:/...
    echo "$path" | sed -e 's|^/c/|C:/|' -e 's|^/mnt/c/|C:/|'
  fi
}

prepare_semgrep_scan_dir() {
  # Slim tree under the repo drive (Docker Desktop cannot see MSYS /tmp mounts).
  SEMGREP_SCAN_DIR="$OUTPUT_DIR/scan-workspace"
  rm -rf "$SEMGREP_SCAN_DIR"
  mkdir -p "$SEMGREP_SCAN_DIR/config/java-doctor"
  cp "$SEMGREP_CONFIG" "$SEMGREP_SCAN_DIR/config/java-doctor/semgrep.yml"
  SEMGREP_DOCKER_PATHS=()
  for src_path in "${SEMGREP_PATHS[@]}"; do
    if [[ -d "$REPO_ROOT/$src_path" ]]; then
      mkdir -p "$SEMGREP_SCAN_DIR/$(dirname "$src_path")"
      cp -R "$REPO_ROOT/$src_path" "$SEMGREP_SCAN_DIR/$(dirname "$src_path")/"
      SEMGREP_DOCKER_PATHS+=("$src_path")
    fi
  done
}

cleanup_semgrep_scan_dir() {
  if [[ -n "${SEMGREP_SCAN_DIR:-}" ]] && [[ -d "$SEMGREP_SCAN_DIR" ]]; then
    rm -rf "$SEMGREP_SCAN_DIR"
  fi
}

run_semgrep_docker() {
  prepare_semgrep_scan_dir
  local mount_src
  mount_src="$(docker_mount_src "$SEMGREP_SCAN_DIR")"
  # Confine MSYS_NO_PATHCONV to Docker only — otherwise Node resolves /c/... as C:\c\...
  (
    export MSYS_NO_PATHCONV=1
    docker run --rm \
      -v "${mount_src}:/src:ro" \
      -w /src \
      "$SEMGREP_IMAGE" \
      semgrep scan \
        --config "/src/config/java-doctor/semgrep.yml" \
        --json \
        --quiet \
        --metrics=off \
        --timeout 30 \
        "${SEMGREP_DOCKER_PATHS[@]}"
  )
}

echo "[$KEYWORD] Semgrep (pinned pack)…"
SEMGREP_STATUS=0
trap cleanup_semgrep_scan_dir EXIT
if command -v semgrep >/dev/null 2>&1; then
  if ! run_semgrep_local > "$SEMGREP_JSON"; then
    SEMGREP_STATUS=$?
    echo "[$KEYWORD] WARNING: Semgrep exited non-zero (continuing; report-only)"
  fi
elif command -v docker >/dev/null 2>&1; then
  if ! run_semgrep_docker > "$SEMGREP_JSON"; then
    SEMGREP_STATUS=$?
    echo "[$KEYWORD] WARNING: Semgrep Docker exited non-zero (continuing; report-only)"
  fi
  cleanup_semgrep_scan_dir
  trap - EXIT
else
  echo "[$KEYWORD] ERROR: neither semgrep nor docker is available." >&2
  echo "Install Semgrep or Docker, then re-run." >&2
  exit 1
fi

if [[ ! -f "$SEMGREP_JSON" ]] || [[ ! -s "$SEMGREP_JSON" ]]; then
  echo "{\"results\":[],\"errors\":[{\"message\":\"semgrep.json missing or empty after invoke (status=${SEMGREP_STATUS})\"}]}" > "$SEMGREP_JSON"
fi

echo "[$KEYWORD] curating…"
node "$SCRIPT_DIR/java-doctor-curated.mjs"

echo "[$KEYWORD] done."
echo "  Curated Markdown: $OUTPUT_DIR/java-doctor.curated.md"
echo "  Curated JSON:     $OUTPUT_DIR/java-doctor.curated.json"
