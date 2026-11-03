#!/usr/bin/env bash
# ios-privacy-check — iOS counterpart of the Android telemetry guard (#1147 / #1150), macOS only.
# Fails when the installed pods could send analytics or usage data, so the "no analytics" claim
# (About screen, store listing) stays true on iOS:
#   1. ios/Podfile.lock lists a known analytics / crash-reporting / telemetry SDK;
#   2. a privacy manifest (PrivacyInfo.xcprivacy: the app's own, and every pod's, including the
#      bundles inside prebuilt xcframeworks) declares tracking, tracking domains or collected data.
# Run after `bundle exec pod install` (CI job mobile-ios). Usage: scripts/ios-privacy-check.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT/ios"

if [[ ! -d Pods ]]; then
  echo "ios-privacy-check: ios/Pods missing; run (cd ios && bundle exec pod install)" >&2
  exit 2
fi

status=0

# 1. Known telemetry SDK pods (prefix match on the pod name).
DENIED='^(Firebase|GoogleDataTransport|GoogleAppMeasurement|FBSDK|FBAudienceNetwork|Crashlytics|Fabric|Sentry|Bugsnag|AppCenter|Amplitude|Mixpanel|Segment|Analytics|Datadog|NewRelic|Instabug|Flurry|Adjust|AppsFlyer|Branch|OneSignal)'
denied="$(sed -n 's/^  - \([^ /]*\).*/\1/p' Podfile.lock | sort -u | grep -E "$DENIED" || true)"
if [[ -n "$denied" ]]; then
  echo "ios-privacy-check: telemetry SDK pod(s) in Podfile.lock:" >&2
  echo "$denied" | sed 's/^/  /' >&2
  status=1
fi

# 2. Privacy manifests: no tracking, no tracking domains, no collected data types.
count=0
while IFS= read -r -d '' manifest; do
  count=$((count + 1))
  json="$(plutil -convert json -o - "$manifest")"
  tracking="$(jq -r '.NSPrivacyTracking // false' <<<"$json")"
  domains="$(jq -r '(.NSPrivacyTrackingDomains // []) | length' <<<"$json")"
  collected="$(jq -r '(.NSPrivacyCollectedDataTypes // []) | length' <<<"$json")"
  if [[ "$tracking" != "false" || "$domains" != "0" || "$collected" != "0" ]]; then
    echo "ios-privacy-check: $manifest declares tracking=$tracking," \
      "tracking domains=$domains, collected data types=$collected" >&2
    status=1
  fi
done < <(find EzkeyMobile Pods -name PrivacyInfo.xcprivacy -print0)

if [[ $count -eq 0 ]]; then
  echo "ios-privacy-check: no PrivacyInfo.xcprivacy found (expected the app's own)" >&2
  exit 2
fi
if [[ $status -eq 0 ]]; then
  echo "ios-privacy-check: no telemetry pod; $count privacy manifests declare no tracking or collection"
fi
exit $status
