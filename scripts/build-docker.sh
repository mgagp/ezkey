#!/usr/bin/env bash
#
# Docker-only equivalent of scripts/build.sh: Spotless apply (bind-mounted checkout), then
# BuildKit-backed reactor validation (see docker/Dockerfile target build-validation).
#
# Usage:
#   ./scripts/build-docker.sh [--check-only] [--no-cache] [--diagnose-only]
#
# Options:
#   --check-only     Skip spotless:apply; only run validation (Spotless check + Checkstyle + ...).
#   --no-cache       Pass --no-cache to docker build (validator image); cold layers.
#   --diagnose-only  Print Docker environment and exit (no Maven).
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

MAVEN_IMAGE="${MAVEN_IMAGE:-maven:3.9-eclipse-temurin-25}"
# Named volume for host bind-mounted spotless:apply (separate from BuildKit's id=maven-cache cache).
SPOTLESS_VOLUME="${EZKEY_SPOTLESS_MAVEN_VOLUME:-ezkey-maven-spotless-cache}"
DOCKERFILE_REL="docker/Dockerfile"
VALIDATION_TARGET="build-validation"

CHECK_ONLY=0
NO_CACHE=0
DIAGNOSE_ONLY=0

for arg in "$@"; do
  case "$arg" in
    --check-only) CHECK_ONLY=1 ;;
    --no-cache) NO_CACHE=1 ;;
    --diagnose-only) DIAGNOSE_ONLY=1 ;;
    *) echo "Unknown option: $arg" >&2; exit 1 ;;
  esac
done

compose_cmd() {
  if docker compose version >/dev/null 2>&1; then
    echo "docker compose"
  else
    echo "docker-compose"
  fi
}

echo "Docker build environment diagnostics"
echo "  repo_root=$REPO_ROOT"
echo "  maven_image=$MAVEN_IMAGE"
echo "  spotless_maven_volume=$SPOTLESS_VOLUME"
echo "  validation_target=$VALIDATION_TARGET"
echo "  docker=$(command -v docker || true)"
echo "  compose=$(compose_cmd)"
if docker buildx version >/dev/null 2>&1; then
  echo "  buildx=$(docker buildx version | head -n 1)"
else
  echo "  buildx=<not available>"
fi

if ! docker info >/dev/null 2>&1; then
  echo "Error: Docker is not running or not accessible." >&2
  exit 1
fi

if [ "$DIAGNOSE_ONLY" -eq 1 ]; then
  exit 0
fi

export DOCKER_BUILDKIT=1
export COMPOSE_DOCKER_CLI_BUILD=1

cd "$REPO_ROOT"

run_spotless_apply() {
  echo ""
  echo "=========================================="
  echo "  Spotless apply (bind-mounted workspace)"
  echo "=========================================="
  if ! docker volume inspect "$SPOTLESS_VOLUME" >/dev/null 2>&1; then
    echo "Creating volume $SPOTLESS_VOLUME (Maven cache for formatter runs)"
    docker volume create "$SPOTLESS_VOLUME" >/dev/null
  fi
  # Git Bash converts Docker args like "/workspace"; disable MSYS path conversion.
  MSYS_NO_PATHCONV=1 \
    docker run --rm \
      -u root \
      -v "${REPO_ROOT}:/workspace" \
      -v "${SPOTLESS_VOLUME}:/root/.m2" \
      -w /workspace \
      "$MAVEN_IMAGE" \
      mvn -B spotless:apply
}

run_validation_build() {
  local extra=()
  if [ "$NO_CACHE" -eq 1 ]; then
    extra+=(--no-cache)
  fi
  echo ""
  echo "=========================================="
  echo "  Reactor validation (docker build target)"
  echo "=========================================="
  docker build "${extra[@]}" \
    -f "$DOCKERFILE_REL" \
    --target "$VALIDATION_TARGET" \
    "$REPO_ROOT"
}

if [ "$CHECK_ONLY" -eq 0 ]; then
  run_spotless_apply
fi
run_validation_build

echo ""
echo "✅ build-docker.sh completed successfully"
