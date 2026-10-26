#!/usr/bin/env bash
# Build ezkey-lab-hello.hpi via Docker (maven:3.9-eclipse-temurin-21 + BuildKit m2 cache).
# Host needs Docker only — Rootbeer / Git Bash on Windows: no local JDK 21.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=lib/docker-plugin-build.sh
source "${ROOT}/scripts/lib/docker-plugin-build.sh"

ezkey_jenkins_docker_build_plugin "${ROOT}" "ezkey-lab-hello"
