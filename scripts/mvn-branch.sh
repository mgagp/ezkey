#!/bin/bash
# Maven wrapper that injects a branch-specific revision qualifier to prevent artifact
# collisions between different git branches when building locally.
#
# Product version formula is CI-friendly ${revision}${changelist} only (ADR-0017 / #1211).
# Off main/master this script always keeps -SNAPSHOT (e.g. 0.2.0-<branch>-SNAPSHOT).
# Empty changelist off main is refused — never emit a release-looking local coordinate.
#
# Usage: ./scripts/mvn-branch.sh [maven-args...]
# Example: ./scripts/mvn-branch.sh clean install

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=lib/product-version.sh
source "${ROOT}/scripts/lib/product-version.sh"

IFS=$'\t' read -r BASE_REVISION POM_CHANGELIST < <(ezkey_read_pom_ci_versions "${ROOT}/pom.xml")

BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "")
SHORT_SHA=$(git rev-parse --short HEAD 2>/dev/null || echo "unknown")

IFS=$'\t' read -r RESOLVED_REVISION RESOLVED_CHANGELIST MODE < <(
  ezkey_resolve_mvn_branch_versions "${BASE_REVISION}" "${POM_CHANGELIST}" "${BRANCH:-HEAD}" "${SHORT_SHA}"
)

if [[ "${MODE}" == "passthrough" ]]; then
  exec mvn "$@"
fi

exec mvn "-Drevision=${RESOLVED_REVISION}" "-Dchangelist=${RESOLVED_CHANGELIST}" "$@"
