# Method Log — `ML-2026-07-13` Mobile F2a production-clean gate gap

## Metadata

- **ID:** `ML-2026-07-13-mobile-f2a-production-clean-gate-gap`
- **Lane:** `E` (methodology feedback) + product fix on mobile F2a
- **Related:** GitHub [#254](https://github.com/mgagp/ezkey/issues/254), `TB-2026-0002`,
  `TSP-2026-06-26-mobile-real-device-churn-harness`
- **Decision:** [`../../../methodology/decisions/2026-07-13-test-only-surfaces-need-mechanical-gates.md`](../../../methodology/decisions/2026-07-13-test-only-surfaces-need-mechanical-gates.md)
- **Status:** `integrated` (2026-07-13)

## What happened

After rebasing the mobile F2a / Maestro / `mobile-doctor-curated` stack onto main, a focused
revision asked whether production-intent builds stayed clean and whether harness code would
survive skeptical human and over-zealous agent scrutiny without false alarms or false comfort.

Findings:

- Acceptance and docs said release-unavailable / `__DEV__` gate.
- Code used env+ack only (deliberately avoiding `__DEV__` for offline debug APKs).
- No release preflight; local `.env` commonly had bypass/trace enabled.
- Doctor suppressions and Semgrep pack had no “intentional harness” contract to point at.

## Honest feedback on the method

The plan + issue + TSP path **did** capture the right intent. It did **not** create a durable
check that the implementation still matched “unavailable in release.” That is a discoverability
and validation-shape gap, not a reason to invent heavy new ceremony.

Fix shape chosen: mechanical gate + release preflight + one contract doc + methodology decision
stating the general rule for future test-only surfaces.

## Pointers for cold readers

- Contract: `ezkey_mobile/docs/MOBILE_TEST_AUTOMATION_PRODUCTION_CLEAN.md`
- Agent entry: `ezkey_mobile/AGENTS.md` § Production-clean test automation
