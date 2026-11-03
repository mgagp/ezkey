# Mobile doctor-curated, iOS lane — tool evaluation (2026-11-03)

## Purpose

Choose a few established, deterministic open source analyzers for a **punctual, curated hygiene
pass** on the iOS native layer of Ezkey Mobile (`ezkey_mobile/ios/`), in the same model as
`doctor-curated` (Admin UI), `java-doctor-curated` and `mobile-doctor-curated` (JS + Kotlin):
one script, suppressions with reasons, P1/P2/P3 shortlist, HITL campaign notes. **Not** a CI gate
and **not** a zero-warning campaign. GitHub issue: `#1161`.

This document is the Go / No-Go record for the tool shortlist before implementation.

## Surface (2026-11-03)

| Item | Size |
|------|------|
| Swift, app (`ios/EzkeyMobile/**`) | `AppDelegate`, `SceneDelegate`, `Crypto/` (module, key store, codec) |
| Swift, tests (`ios/EzkeyMobileTests/**`) | 3 XCTest files |
| Objective-C | `EzkeyCryptoModule.m`, `RCT_EXTERN_*` declarations only |
| Total | ~1,400 lines; changes rarely, but sits on the trust boundary (keys, signatures, local auth) |

## Baseline (do not duplicate)

| Layer | Tool | Where |
|-------|------|-------|
| Formatting + style | swift-format (Apple, Google Swift style defaults) | `yarn ios:lint`, CI `mobile-ios` |
| Code smells | SwiftLint 0.65.1 `--strict`, 3 opt-in rules (`empty_count`, `explicit_init`, `force_unwrapping`) | same |
| Behavior | Hosted XCTest (`yarn ios:test`) | CI `mobile-ios` |
| Supply chain / privacy | `Podfile.lock` freshness, `ios-privacy-check.sh` | CI `mobile-ios` |

Finding while reading the baseline: `ios/.swiftlint.yml` declares `analyzer_rules: [unused_import]`,
but `swiftlint analyze` never runs (it needs a compiler log), so that rule is dead configuration.

## Hard filters

- OSS, actively maintained, recognized in the Swift community
- Deterministic, report-friendly output (JSON / SARIF), no build failure
- A different angle from swift-format + SwiftLint `--strict` (no style restatement)
- Proportionate to ~1,400 lines: minutes to run, no server, no account
- Runs on the maintainer's Mac; macOS-only tools are acceptable for this lane (the iOS build is
  macOS-only anyway)

## Measured: SwiftLint with every opt-in rule

Probe on 2026-11-03 (`opt_in_rules: [all]`, project config otherwise): **1,100 findings**.
The top rules are style that contradicts swift-format or the project convention:
`indentation_width` 276, `contrasted_opening_brace` 204, `explicit_type_interface` 119,
`explicit_acl` 105, `multiline_arguments_brackets` 91, `prefer_nimble` 86 (we do not use Nimble),
`type_contents_order` 47. Turning everything on would be rule-count theatre.

The few correctness-leaning opt-in rules produce a small, reviewable set: `unneeded_escaping` 10,
`unused_parameter` 6, `discouraged_optional_collection` 3, `unneeded_throws_rethrows` 2,
`legacy_objc_type` 2, `implicitly_unwrapped_optional` 1 (24 findings, not yet triaged).

## Inventory

| Tool | Angle | Verdict | Why |
|------|-------|---------|-----|
| **SwiftLint, curated opt-in set** (separate config, curated pass only) | Correctness-leaning smells beyond the CI set | **Keep** | Already installed and pinned; the probe shows ~24 findings from ~6 rules once style rules are left out. Complexity / length rules (`cyclomatic_complexity`, `function_body_length`) stay at defaults. |
| **Periphery** (peripheryapp/periphery, `brew install periphery`) | Unused code: declarations, parameters, protocol conformances, from the compiler index store | **Keep, trial** | The reference Swift dead-code tool; active (3.7.x, 2026). Needs a build (index store): the curated script reuses `ios/build/DerivedData` from `yarn ios:test`. Bridge methods are called from JavaScript only, so `@objc` / RN-exported members must be retained (`--retain-objc-accessible`), or the shortlist fills with false positives: to verify on the first run. |
| **Semgrep CE, Ezkey pack extended to Swift** | Project-specific security footguns | **Keep, narrow** | Reuses the existing `semgrep/rules/mobile-security.yml` pack and scan script (same as the JS / Kotlin lanes). Semgrep CE parses Swift, but the framework-aware Swift rules are in the commercial tier, so only our own rules count: Keychain accessibility other than `…ThisDeviceOnly`, `kSecAttrSynchronizable`, non-CSPRNG randomness (`arc4random`, `random()`, `Int.random`) in `Crypto/`, `print` / `NSLog` / `os_log` of key or token material, biometrics-only `LAContext` policy (the contract is `.deviceOwnerAuthentication`). 4–6 rules. |
| `swiftlint analyze` (`unused_import`, `unused_declaration`) | Compiler-aware unused imports / declarations | **Defer** | Overlaps Periphery on declarations; needs a full compiler log (`xcodebuild … > log`), so a second build per pass. Revisit if Periphery is rejected. Either way, drop the dead `analyzer_rules` entry or wire it. |
| Clang static analyzer (`xcodebuild analyze`) | C / Objective-C path analysis | **Reject** | The only Objective-C file holds macro declarations; no Swift coverage. |
| MobSF | Binary (IPA) security scan | **Defer to release lane** | Docker, heavy, scans a built app; its plist / ATS checks belong to App Store release readiness, not to source hygiene. |
| SonarQube Community | Aggregator platform | **Reject** | Server and dashboards for 1,400 lines; same verdict as the 2026-07-11 evaluation. |
| Infer (Meta) | Interprocedural bugs | **Reject** | No Swift support. |

## Recommended shape

Extend **`mobile-doctor-curated`** with an iOS lane instead of creating a new keyword: same
script entry (`yarn doctor:curated`), same curator, same suppressions file, same campaign notes
folder (`product-docs/global/hygiene/mobile-doctor/`). New flags `--skip-swiftlint`,
`--skip-periphery`; the iOS lane is skipped with a notice off macOS, so the Windows / Linux pass
keeps working.

Priorities, mirroring the other lanes:

| Source | P1 | P2 | P3 |
|--------|----|----|----|
| Semgrep (Ezkey Swift rules) | `ERROR` | `WARNING` | — |
| Periphery | unused code in `Crypto/` | other unused code | redundant `public` / conformances |
| SwiftLint curated set | — | `unused_parameter`, `unneeded_throws_rethrows`, `implicitly_unwrapped_optional` | the rest |

Same operating rules as the parent lane: fuzzy signal → skip; crypto / Keychain code → "if it
ain't broken, don't fix it" unless the finding is a real defect.

## Working method after Go

1. Branch `chore/1161-ios-doctor-curated` (this note).
2. Wire the three tools into `scripts/mobile-doctor-curated.mjs` (raw outputs under
   `logs/mobile-doctor/raw/`), plus `config/mobile-doctor/swiftlint-curated.yml` and the Swift
   Semgrep rules, each rule with a positive and a negative fixture.
3. First run; record the noise level per tool. If Periphery still floods after the retention
   options, drop it (No-Go recorded here) rather than suppressing in bulk.
4. Docs: `ezkey_mobile/AGENTS.md` § Mobile doctor-curated pass, root `AGENTS.md` keyword section,
   `ezkey_mobile/docs/IOS_SETUP.md`.
5. First dated campaign note (HITL, one finding at a time), then fixes on their own hygiene PR.

## Go / No-Go checklist (operator)

- [ ] Extend `mobile-doctor-curated` (no new keyword)
- [ ] SwiftLint curated opt-in set — keep
- [ ] Periphery — trial on the first run
- [ ] Semgrep Ezkey Swift rules — keep, 4–6 rules
- [ ] `swiftlint analyze` — defer; remove the dead `analyzer_rules` entry
- [ ] MobSF — defer to the App Store release lane

## Links

- Parent evaluation: [`mobile-doctor-curated-evaluation-2026-07-11.md`](mobile-doctor-curated-evaluation-2026-07-11.md)
- Lane rules: [`../../ezkey_mobile/AGENTS.md`](../../ezkey_mobile/AGENTS.md) § Mobile doctor-curated pass
- iOS tooling: [`../../ezkey_mobile/docs/IOS_SETUP.md`](../../ezkey_mobile/docs/IOS_SETUP.md) § Swift code standards
- Sources: [Periphery](https://github.com/peripheryapp/periphery),
  [Semgrep Swift support](https://semgrep.dev/docs/languages/swift),
  [SwiftLint](https://github.com/realm/SwiftLint)
