#!/usr/bin/env bash
# ios-lint — Swift code standards gate for the iOS native layer (macOS only).
#   swift-format : Apple's formatter, Google Swift style defaults (ios/.swift-format)
#   SwiftLint    : code-smell rules on top (ios/.swiftlint.yml)
# Usage: scripts/ios-lint.sh          check only (non-zero exit on any finding)
#        scripts/ios-lint.sh --fix    format in place first, then check
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT/ios"

if ! xcrun --find swift-format >/dev/null 2>&1; then
  echo "ios-lint: swift-format not found (install Xcode 16+)" >&2
  exit 2
fi
if ! command -v swiftlint >/dev/null 2>&1; then
  echo "ios-lint: swiftlint not found (brew install swiftlint)" >&2
  exit 2
fi

FILES=()
while IFS= read -r file; do FILES+=("$file"); done < <(
  find EzkeyMobile EzkeyMobileTests -name '*.swift' | sort
)

if [[ "${1:-}" == "--fix" ]]; then
  xcrun swift-format format --in-place "${FILES[@]}"
fi

status=0
xcrun swift-format lint --strict "${FILES[@]}" || status=1
swiftlint lint --strict --quiet "${FILES[@]}" || status=1

if [[ $status -eq 0 ]]; then
  echo "ios-lint: ${#FILES[@]} Swift files clean (swift-format + SwiftLint, strict)"
fi
exit $status
