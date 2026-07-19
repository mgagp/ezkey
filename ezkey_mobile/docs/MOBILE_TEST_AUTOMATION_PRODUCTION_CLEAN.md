# Mobile test automation — production-clean contract

## Purpose

Keep Ezkey Mobile **publishable and scrutiny-resistant** while allowing Maestro / real-device
harnesses and punctual static analysis (`mobile-doctor-curated`).

This note is the single contract for:

- what may exist in a **production / release** binary,
- what is allowed only on **debug** builds with explicit opt-in,
- how humans and cold agents should treat harness code during review and doctor passes.

Related: GitHub [#254](https://github.com/mgagp/ezkey/issues/254) (F2a), `TB-2026-0002`,
[`maestro/README.md`](../maestro/README.md), methodology decision
`product-docs/methodology/decisions/2026-07-13-test-only-surfaces-need-mechanical-gates.md`.

## Non-negotiables

1. **Release builds must not expose test harness UI or test-only diagnostics.**
2. **Stating “debug-only” in a plan or README is not enough** — the gate must be enforced in code
   and, for release scripts, checked before assemble.
3. **Bind/verify (and pending/respond) cryptographic trust must not be weakened** by automation
   helpers. F2a only substitutes the *camera/QR capture* step.
4. **Static analysis should stay green-by-design** for intentional harness code: either the pattern
   is clearly gated and documented here, or it is suppressed with a written reason under
   `config/mobile-doctor/suppressions.json`.

## What is OK in every build (including release)

| Surface | Why |
| --- | --- |
| Stable `testID` / accessibility ids (`ezkey.e2e.*`) | Needed for automation; not a product shortcut |
| Maestro YAML, reports, host scripts under `maestro/` / `scripts/` | Not shipped inside the APK |
| Doctor / Semgrep / Detekt tooling | Dev workstation only |

## What must never be active in release

| Surface | Gate |
| --- | --- |
| F2a controlled enrollment seed bypass UI/action | Native **debug** build (`BuildConfig.DEBUG` via `readIsDebugBuild()`) **and** `EZKEY_ENROLLMENT_SEED_BYPASS_ENABLED` **and** ack `F2A_TEST_ONLY` |
| `EZKEY_PENDING_AUTH_FLOW_TRACE` respond-path console tracing | Default false; release preflight forbids true |
| `EZKEY_PENDING_AUTH_DEBUG_PANEL` support debug panel | Default false; release preflight forbids true |

**Important:** do **not** equate this with React Native `__DEV__`. Offline-capable debug APKs on
this project often run with `__DEV__ === false` while remaining Gradle `debug` (`BuildConfig.DEBUG
=== true`). F2a availability follows **native debug build type**, not `__DEV__`.

Pure gate helper: `app/utils/controlledEnrollmentBypass.ts`.
Native constant: `EzkeyCryptoModule` → `isDebugBuild`.

## Release preflight

`scripts/build-install-release-clean.sh` sources
`scripts/assert-release-production-clean-env.sh`, which fails if the active `.env` (or `ENVFILE`)
still enables the forbidden flags above.

App code remains the last line of defense: even a mis-baked env cannot show F2a UI on a release
binary because `isDebugBuild` is false.

## How to run automation safely

1. Use a **debug** install (`./scripts/build-install-debug-clean.sh`).
2. Set F2a / trace flags only in local `.env` (gitignored); rebuild native after changes
   (`react-native-config`).
3. Prefer churn without recovery for steady-state; use F2a seed bypass only for enrollment
   bootstrap (explicit opt-in).
4. Never “temporarily” leave bypass/trace enabled when preparing a Play / release candidate.

## Scrutiny resistance (human + agent)

When `mobile-doctor-curated` or a skeptical review hits F2a / Maestro-related strings:

1. Read this contract first.
2. Confirm the **mechanical gate** still matches the table above (code + release preflight).
3. Do **not** treat the mere presence of bypass *source* as a P1 if release cannot activate it.
4. Do treat as P1: missing debug-build check, env-only gate, release script skipping preflight,
   or docs that still claim a `__DEV__`-only gate.

## Discoverability

| Reader | Entry |
| --- | --- |
| Cold agent in `ezkey_mobile/` | `AGENTS.md` § Production-clean test automation |
| Docs index | `docs/README.md` → this file |
| Maestro operators | `maestro/README.md` § Controlled Seed Bypass (F2a) |
| Method / “why did plans miss this?” | methodology decision `2026-07-13-test-only-surfaces-need-mechanical-gates.md` |
