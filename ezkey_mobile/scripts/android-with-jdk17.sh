#!/usr/bin/env bash
# Run React Native Android with JDK 17/21 (required for RN 0.85 Android toolchain).
MOBILE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=resolve-android-jdk.sh
source "${MOBILE_ROOT}/scripts/resolve-android-jdk.sh"
exec npx react-native run-android "$@"
