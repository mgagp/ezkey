#!/usr/bin/env bash
# Runs a Dart command inside the official dart image with ezkey_dart mounted at /work.
# Host requirement: Docker only.
#
# Usage: scripts/dart-docker.sh [--network NAME] [--mount HOST:CONTAINER[:ro]] [--] <dart args...>
#   scripts/dart-docker.sh pub get
#   scripts/dart-docker.sh test
#   scripts/dart-docker.sh --network ezkey_ezkey-network run bin/ezkey_e2e.dart
#
# Environment:
#   EZKEY_DART_IMAGE   image (default: dart:stable)
#   EZKEY_DART_PUB_CACHE_VOLUME  named volume for the pub cache (default: ezkey-dart-pub-cache)
#   EZKEY_*            forwarded into the container (stack URLs, options)
set -euo pipefail

MODULE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGE="${EZKEY_DART_IMAGE:-dart:stable}"
CACHE_VOLUME="${EZKEY_DART_PUB_CACHE_VOLUME:-ezkey-dart-pub-cache}"

docker_args=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --network)
      docker_args+=(--network "$2")
      shift 2
      ;;
    --mount)
      docker_args+=(-v "$2")
      shift 2
      ;;
    --)
      shift
      break
      ;;
    *)
      break
      ;;
  esac
done

if [[ $# -eq 0 ]]; then
  echo "usage: $0 [--network NAME] [--mount SRC:DST[:ro]] [--] <dart args...>" >&2
  exit 2
fi

uid="$(id -u)"
gid="$(id -g)"

# Named volumes are created root-owned; hand the pub cache to the calling user once.
docker run --rm -v "${CACHE_VOLUME}:/pub-cache" "${IMAGE}" \
  sh -c "chown ${uid}:${gid} /pub-cache" >/dev/null

env_args=()
while IFS='=' read -r name _; do
  [[ "$name" == EZKEY_* ]] && env_args+=(-e "$name")
done < <(env)

tty_args=()
if [[ -t 0 && -t 1 ]]; then
  tty_args=(-it)
fi

# MSYS_NO_PATHCONV keeps Git Bash on Windows from rewriting /work paths.
MSYS_NO_PATHCONV=1 exec docker run --rm ${tty_args[@]+"${tty_args[@]}"} \
  -u "${uid}:${gid}" \
  -e HOME=/tmp \
  -e PUB_CACHE=/pub-cache \
  ${env_args[@]+"${env_args[@]}"} \
  -v "${CACHE_VOLUME}:/pub-cache" \
  -v "${MODULE_DIR}:/work" \
  -w /work \
  ${docker_args[@]+"${docker_args[@]}"} \
  "${IMAGE}" dart "$@"
