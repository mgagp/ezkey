#!/usr/bin/env bash
# Run React Native Android with project JDK 17 (resolve-android-jdk.sh; never JDK 25).
MOBILE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=resolve-android-jdk.sh
source "${MOBILE_ROOT}/scripts/resolve-android-jdk.sh"
exec npx react-native run-android "$@"
