#!/usr/bin/env bash
# Stop Gradle daemons and remove caches to fix "Could not move temporary workspace" on Windows.
# Does NOT run gradlew clean (that would recreate .gradle and hit the same lock issue).
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/android"
echo "Stopping Gradle daemons..."
./gradlew --stop 2>/dev/null || true
if [[ -d ".gradle" ]]; then
  echo "Removing android/.gradle cache..."
  rm -rf .gradle
fi
if [[ -d "app/build" ]]; then
  echo "Removing android/app/build..."
  rm -rf app/build
fi
echo "Clean done. Run: npm run android:jdk17"
