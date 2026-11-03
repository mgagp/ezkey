# Mobile doctor-curated config

Pinned inputs for the `mobile-doctor-curated` punctual hygiene pass.

| Path | Role |
|------|------|
| [`suppressions.json`](suppressions.json) | Rule-level suppressions with written reasons |
| [`swiftlint-curated.yml`](swiftlint-curated.yml) | iOS lane: SwiftLint opt-in rules for this pass only (not CI) |

Analyzers (v1 Go):

1. **react-doctor** — React / RN structure and anti-patterns
2. **Semgrep** — `ezkey_mobile/semgrep/rules/mobile-security.yml`
3. **Detekt** — `ezkey_mobile/detekt/detekt.yml` (narrow)

iOS lane (Go 2026-11-03, #1161; macOS only, skipped with a notice elsewhere):

4. **SwiftLint, curated set** — `swiftlint-curated.yml` (correctness-leaning opt-in rules; the CI
   set in `ios/.swiftlint.yml` is already at zero)
5. **Periphery** — unused Swift code, read from the index store `yarn ios:test` leaves in
   `ios/build/DerivedData` (`DERIVED_DATA_PATH` overrides); run `yarn ios:test` first when Swift
   changed. Members reachable from JavaScript (`@objc`) are retained.
6. **Semgrep Swift rules** — same pack, `ezkey-swift-*` rules on `ios/EzkeyMobile`. Fixtures:
   `semgrep/rules/mobile-security.swift`, checked with
   `docker run --rm -v "$PWD:/src" -w /src semgrep/semgrep semgrep --test semgrep/rules`.

Evaluation: `product-docs/global/mobile-ios-doctor-curated-evaluation-2026-11-03.md`.

Entrypoint: `./scripts/mobile-doctor-curated.sh` or `yarn doctor:curated` from `ezkey_mobile/`.

Outputs (gitignored via root `logs/`): `logs/mobile-doctor/mobile-doctor.curated.md|json`.

Campaign notes: `product-docs/global/hygiene/mobile-doctor/`.

When triage hits Maestro / F2a / harness-adjacent findings, read
`docs/MOBILE_TEST_AUTOMATION_PRODUCTION_CLEAN.md` before escalating: intentional gated harness
code is not a default P1; a missing mechanical release gate is.
