#!/usr/bin/env bash
#
# Divergence check: every SPRING_APPLICATION_JSON in the JavaMelody overlays must
# equal docker/javamelody/init-parameters.base.json plus application-name.
# Catches silent drift across the 9 embedded copies (3 files × 3 app names).
#
# Usage (from repo root):
#   ./scripts/ops/check-javamelody-spring-json.sh
#
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BASE_FILE="${REPO_ROOT}/docker/javamelody/init-parameters.base.json"
COMPOSE_FILES=(
  "${REPO_ROOT}/docker/docker-compose.javamelody.yml"
  "${REPO_ROOT}/docker/docker-compose.ha.javamelody.yml"
  "${REPO_ROOT}/experimental-hybrid/lightsail/docker-compose.javamelody.yml"
)
EXPECTED_NAMES=(admin-api auth-api integration-api)

if ! command -v python3 >/dev/null 2>&1; then
  echo "FAIL: python3 required" >&2
  exit 2
fi
if [[ ! -f "$BASE_FILE" ]]; then
  echo "FAIL: missing ${BASE_FILE}" >&2
  exit 1
fi

fail=0
echo "JavaMelody SPRING_APPLICATION_JSON divergence check"
echo "  base: ${BASE_FILE}"

python3 - "$BASE_FILE" "${COMPOSE_FILES[@]}" <<'PY'
import json, re, sys
from pathlib import Path

base_path = Path(sys.argv[1])
compose_paths = [Path(p) for p in sys.argv[2:]]
base = json.loads(base_path.read_text(encoding="utf-8"))
expected_names = {"admin-api", "auth-api", "integration-api"}
# Collect unique JSON payloads (anchors may repeat the same literal).
found_by_name: dict[str, list[tuple[str, dict]]] = {n: [] for n in expected_names}
fail = 0

json_re = re.compile(
    r"SPRING_APPLICATION_JSON:\s*>-\s*\n\s*(\{.*?\})\s*$",
    re.MULTILINE,
)
# Also match single-line form
json_re_line = re.compile(r"SPRING_APPLICATION_JSON:\s*['\"]?(\{.*?\})['\"]?\s*$", re.MULTILINE)

for path in compose_paths:
    text = path.read_text(encoding="utf-8")
    payloads = []
    for m in json_re.finditer(text):
        payloads.append(m.group(1))
    if not payloads:
        for m in json_re_line.finditer(text):
            payloads.append(m.group(1))
    if not payloads:
        print(f"FAIL: no SPRING_APPLICATION_JSON in {path}", file=sys.stderr)
        fail = 1
        continue
    seen_names = set()
    for raw in payloads:
        try:
            obj = json.loads(raw)
        except json.JSONDecodeError as e:
            print(f"FAIL: invalid JSON in {path}: {e}", file=sys.stderr)
            fail = 1
            continue
        init = obj.get("javamelody", {}).get("init-parameters")
        if not isinstance(init, dict):
            print(f"FAIL: {path}: missing javamelody.init-parameters", file=sys.stderr)
            fail = 1
            continue
        name = init.get("application-name")
        if name not in expected_names:
            print(f"FAIL: {path}: unexpected application-name={name!r}", file=sys.stderr)
            fail = 1
            continue
        shared = {k: v for k, v in init.items() if k != "application-name"}
        if shared != base:
            print(f"FAIL: {path} ({name}): init-parameters diverge from base", file=sys.stderr)
            only_base = {k: base[k] for k in base.keys() - shared.keys()}
            only_here = {k: shared[k] for k in shared.keys() - base.keys()}
            changed = {
                k: (base.get(k), shared.get(k))
                for k in base.keys() & shared.keys()
                if base[k] != shared[k]
            }
            if only_base:
                print(f"  missing vs base: {only_base}", file=sys.stderr)
            if only_here:
                print(f"  extra vs base: {only_here}", file=sys.stderr)
            if changed:
                print(f"  changed: {changed}", file=sys.stderr)
            fail = 1
            continue
        seen_names.add(name)
        found_by_name[name].append((str(path), init))
        print(f"  OK  {path.name} application-name={name}")
    missing = expected_names - seen_names
    if missing:
        print(f"FAIL: {path}: missing application-name(s) {sorted(missing)}", file=sys.stderr)
        fail = 1

sys.exit(fail)
PY
