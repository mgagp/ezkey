#!/usr/bin/env bash
# ios-test — build the iOS app for the simulator and run the hosted XCTest target (macOS only).
# Same command locally and in CI (.github/workflows/ezkey-mobile-ios.yml). Metro is not needed.
# Prerequisites: yarn install, then (cd ios && bundle exec pod install).
#
# Usage: scripts/ios-test.sh
# Env:   IOS_SIM_DESTINATION  xcodebuild -destination (default: newest iOS runtime, first iPhone)
#        DERIVED_DATA_PATH    default ios/build/DerivedData (gitignored)
#        RESULT_BUNDLE_PATH   optional .xcresult output (CI uploads it on failure)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT/ios"

if [[ ! -d EzkeyMobile.xcworkspace ]]; then
  echo "ios-test: EzkeyMobile.xcworkspace missing; run (cd ios && bundle exec pod install)" >&2
  exit 2
fi

DESTINATION="${IOS_SIM_DESTINATION:-}"
if [[ -z "$DESTINATION" ]]; then
  # Simulator names change with every Xcode (iPhone 17 Pro, iPhone 18 Pro, ...): pick by runtime.
  UDID="$(xcrun simctl list devices available -j | jq -r '
    .devices
    | to_entries
    | map(select(.key | test("SimRuntime\\.iOS-")))
    | map(.key |= (capture("iOS-(?<v>[0-9-]+)$").v | split("-") | map(tonumber)))
    | sort_by(.key) | reverse
    | map(.value[] | select(.name | startswith("iPhone")))
    | first // empty
    | .udid')"
  if [[ -z "$UDID" ]]; then
    echo "ios-test: no available iPhone simulator (install an iOS runtime in Xcode)" >&2
    exit 2
  fi
  DESTINATION="platform=iOS Simulator,id=$UDID"
fi

ARGS=(
  test
  -workspace EzkeyMobile.xcworkspace
  -scheme EzkeyMobile
  -destination "$DESTINATION"
  -derivedDataPath "${DERIVED_DATA_PATH:-$ROOT/ios/build/DerivedData}"
  -quiet
)
if [[ -n "${RESULT_BUNDLE_PATH:-}" ]]; then
  rm -rf "$RESULT_BUNDLE_PATH"
  ARGS+=(-resultBundlePath "$RESULT_BUNDLE_PATH")
fi

xcodebuild -version
echo "ios-test: destination $DESTINATION"
xcodebuild "${ARGS[@]}"
echo "ios-test: XCTest passed"
