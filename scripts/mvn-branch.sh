#!/bin/bash
# Maven wrapper that injects a branch-specific revision qualifier to prevent artifact
# collisions between different git branches when building locally.
#
# Product version formula is CI-friendly ${revision}${changelist} only (ADR-0017 / #1211).
# This script keeps changelist as-is and appends -<branch> to revision on feature branches.
#
# Usage: ./scripts/mvn-branch.sh [maven-args...]
# Example: ./scripts/mvn-branch.sh clean install

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=lib/product-version.sh
source "${ROOT}/scripts/lib/product-version.sh"

IFS=$'\t' read -r BASE_REVISION _changelist < <(ezkey_read_pom_ci_versions "${ROOT}/pom.xml")

BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "")

if [ -z "$BRANCH" ] || [ "$BRANCH" = "HEAD" ]; then
  SHA=$(git rev-parse --short HEAD 2>/dev/null || echo "unknown")
  QUALIFIER="-${SHA}"
else
  SANITIZED=$(echo "$BRANCH" | sed 's/[^A-Za-z0-9_.-]/-/g' | sed 's/--*/-/g' | sed 's/^-\|-$//g')
  if [ "$SANITIZED" = "main" ] || [ "$SANITIZED" = "master" ]; then
    QUALIFIER=""
  else
    QUALIFIER="-${SANITIZED}"
  fi
fi

if [ -n "$QUALIFIER" ]; then
  exec mvn "-Drevision=${BASE_REVISION}${QUALIFIER}" "$@"
fi
exec mvn "$@"
