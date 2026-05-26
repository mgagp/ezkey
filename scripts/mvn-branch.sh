#!/bin/bash
# Maven wrapper that injects branch-specific buildQualifier to prevent artifact collisions
# between different git branches when building locally.
#
# Usage: ./scripts/mvn-branch.sh [maven-args...]
# Example: ./scripts/mvn-branch.sh clean install

set -e

# Get current git branch name
BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "")

# Determine buildQualifier
if [ -z "$BRANCH" ] || [ "$BRANCH" = "HEAD" ]; then
  # Fallback to git SHA if branch name not available (detached HEAD)
  SHA=$(git rev-parse --short HEAD 2>/dev/null || echo "unknown")
  BUILD_QUALIFIER="-${SHA}"
else
  # Sanitize branch name: replace invalid characters with hyphens
  # Maven version format allows: [A-Za-z0-9_.-]
  SANITIZED=$(echo "$BRANCH" | sed 's/[^A-Za-z0-9_.-]/-/g' | sed 's/--*/-/g' | sed 's/^-\|-$//g')

  # Skip qualifier for main/master branches (standard behavior)
  if [ "$SANITIZED" = "main" ] || [ "$SANITIZED" = "master" ]; then
    BUILD_QUALIFIER=""
  else
    BUILD_QUALIFIER="-${SANITIZED}"
  fi
fi

# Execute Maven with buildQualifier injected
exec mvn "-DbuildQualifier=${BUILD_QUALIFIER}" "$@"
