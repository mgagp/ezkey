#!/usr/bin/env bash
#
# CI: seed the BuildKit cache mount that docker/Dockerfile uses for Maven (id=maven-cache) from
# the Maven repository already restored on the runner (actions/setup-java or actions/cache).
#
# Why: the image build runs `mvn dependency:go-offline` inside Docker with an empty cache, so every
# CI run downloads the whole dependency tree from Maven Central. GitHub-hosted runners share IP
# addresses that Central sometimes throttles with HTTP 429; the very first request (the Spring Boot
# BOM) then fails and the image build stops. Seen on 2026-11-23 on main (scheduled run, BOM 4.1.2)
# and twice on #1458 (BOM 4.2.0), while the backend job passed thanks to its restored cache.
#
# Fail-open by design: with no restored repository it prints a notice and exits 0, and the image
# build downloads from Central exactly as before. Our own org/ezkey artifacts are not copied; the
# image build compiles them in its reactor.
#
# Usage (repo root):
#   bash scripts/ci-seed-docker-maven-cache.sh [maven-repository-dir]   # default ~/.m2/repository
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO_DIR="${1:-${HOME}/.m2/repository}"

if [[ ! -d "${REPO_DIR}" ]] || [[ -z "$(ls -A "${REPO_DIR}" 2>/dev/null)" ]]; then
  echo "::notice::No Maven repository at ${REPO_DIR}; image builds will download from Maven Central."
  exit 0
fi

# Same base image as the Dockerfile build stage: already pulled for the build, and one place to pin.
BASE_IMAGE="$(awk '/^FROM .* AS build$/ { print $2; exit }' "${ROOT}/docker/Dockerfile")"
if [[ -z "${BASE_IMAGE}" ]]; then
  echo "::error::Could not find the 'AS build' stage in docker/Dockerfile." >&2
  exit 1
fi

# BuildKit pitfalls checked on 2026-11-23: `--no-cache` also discards cache mounts, and
# `--output type=cacheonly` skips the RUN entirely (nothing to export). So: a normal build, a nonce
# build arg so the RUN never comes from the layer cache, and a throwaway tag removed afterwards.
SEED_TAG="ezkey-ci-maven-seed:local"
echo "==> Seeding BuildKit cache id=maven-cache from ${REPO_DIR} ($(du -sh "${REPO_DIR}" | cut -f1))"
docker buildx build --progress=plain --load --tag "${SEED_TAG}" \
  --build-arg "BASE_IMAGE=${BASE_IMAGE}" --build-arg "SEED_NONCE=$(date +%s)-$$" \
  -f - "${REPO_DIR}" <<'EOF'
# syntax=docker/dockerfile:1
ARG BASE_IMAGE
FROM ${BASE_IMAGE}
ARG SEED_NONCE
RUN --mount=type=cache,target=/root/.m2,id=maven-cache,sharing=shared \
    --mount=type=bind,target=/seed \
    echo "seed ${SEED_NONCE}" && \
    mkdir -p /root/.m2/repository && \
    tar -C /seed --exclude=./org/ezkey -cf - . | tar -C /root/.m2/repository -xf - && \
    echo "seeded: $(du -sh /root/.m2/repository | cut -f1)"
EOF
docker image rm "${SEED_TAG}" >/dev/null 2>&1 || true
