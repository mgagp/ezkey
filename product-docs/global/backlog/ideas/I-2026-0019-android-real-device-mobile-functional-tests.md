# Backlog Idea — `I-2026-0019` Android real-device mobile functional tests

## Metadata

- **ID:** `I-2026-0019`
- **Status:** `active`
- **Priority:** `P1`
- **Created at:** `2026-05-08`
- **Updated at:** `2026-06-26`
- **Last reviewed at:** `2026-06-26`
- **Phase tags:** `P1-operability`, `P2-hardening`
- **Component tags:** `ezkey-mobile`, `ezkey-tests`, `auth-api`, `admin-api`, `docker`
- **GitHub issues:** [#239](https://github.com/mgagp/ezkey/issues/239) (F1), [#254](https://github.com/mgagp/ezkey/issues/254) (F2a). Umbrella [#179](https://github.com/mgagp/ezkey/issues/179) superseded by #239 for execution tracking.
- **Captured by:** Marc

## Intent

Introduce a pragmatic, repeatable Android real-device functional test capability for `ezkey_mobile`, using the local clean-start Docker stack and a physically connected phone as the trusted execution target. The goal is not to replace unit tests or existing API functional tests, but to add a missing confidence layer for the exact backend-to-phone trust path that matters most in Ezkey: enrollment, pending retrieval, respond, and repeated operational churn on a real Android device.

## Problem and value

- **Problem:** The mobile app is part of Ezkey's cryptographic trust chain, yet the current automated coverage is concentrated in unit tests and API functional tests. That leaves a confidence gap around real Android key storage, real UI flows, real device signing, and intermittent issues that only show up on phone hardware during `pending` / `respond`.
- **Operator-observed intermittent (motivation for churn):** In some sessions, the **first** check-pending from the phone fails after an auth attempt is started from Admin UI; creating a **new** attempt (after cancelling the stuck one) sometimes succeeds. Automation priority is to build a **shortest pragmatic path** (hybrid enrollment + JUnit-driven attempt creation + Maestro consumption) and then **volume/variance** (seeded randomization over bounded dimensions) so rare failures land with **correlated artifacts** (`auth_attempt_id`, Maestro XML, logcat), not to encode one brittle “repro script” as truth.
- **Expected value:** Faster detection of mobile-to-backend regressions, stronger confidence in release candidates, a reusable repro path for intermittent pending-signature or timeout issues, and a healthier long-term habit similar to Ezkey's existing operational and functional testing posture.

## Current direction

- **Default posture:** Android-first, open-source-first, real physical device on the local clean-start stack.
- **Primary UI driver candidate:** Maestro.
- **Mirroring/debug companion:** `scrcpy`, explicitly treated as a developer-ergonomics tool rather than the pass/fail framework.
- **Backend orchestration posture:** keep repeated state setup and steady-state verification close to `ezkey-tests` and JUnit rather than rebuilding that logic in the mobile automation layer.
- **Initialization model:** hybrid. Allow a light human-assisted first-run step if needed; automate the steady state.

## Scope

- **In scope:**
  - Android only in the first phase.
  - Real physical device connected through `adb`.
  - Real app build installed on the device.
  - Real local Docker stack as the backend baseline.
  - Functional flows covering `bind`, `verify`, `pending`, and `respond`.
  - A steady-state or churn-style path where the backend repeatedly creates auth attempts and the phone repeatedly handles them.
  - Optional operator-visible screen mirroring during local execution.
  - Bash-first execution scripts and artifact capture.
- **Out of scope:**
  - iOS parity in the initial slice.
  - Replacing the existing `ezkey-tests` API suites.
  - Broad, fragile UI coverage of every screen.
  - Cloud-device farms or paid SaaS as the default starting point.

## Key assumptions

- A local clean-start stack remains the reference environment for meaningful end-to-end validation.
- Android is the current reference-strength mobile platform, so it is the correct first target.
- The most valuable first automation slice is a small number of high-signal workflows, not a large UI suite.
- Real-device automation should complement, not collapse into, the existing Java functional and churn test layers.

## Risks and exceptions

- UI automation on a real phone can become flaky if selectors depend on localized copy or visually unstable layouts. The mobile app will likely need a small testability pass with stable identifiers for critical elements.
- Full unattended enrollment may require careful handling of QR/bootstrap material; the first iteration may reasonably mix automated steady state with a light human-assisted initialization step.
- Rate limiting can make churn scenarios noisy unless the stack runs in permissive `docker-test` mode for the relevant flows.
- Tooling choice matters: the wrong framework could add too much accidental complexity relative to the value of the first slice.

## Promotion notes

Promoted to tracer bullet pilot on `2026-05-08`: **`TB-2026-0002`** (`product-docs/global/backlog/TB-2026-0002-android-real-device-functional-pilot.md`). Execution and exit criteria are owned by that brief.

**Execution (`2026-05-08`):** Maestro flows, Bash runner, and `ezkey.e2e.*` testIDs landed under `ezkey_mobile/maestro/` and `ezkey_mobile/scripts/run-real-device-pilot-maestro.sh`. **TB exit #2 validated on hardware** (single pending/respond slice).

**Execution (`2026-06-26`):** Documentation alignment for F1 churn harness and F2a enrollment bypass. Test plan slice [`TSP-2026-06-26-mobile-real-device-churn-harness.md`](../test-plans/TSP-2026-06-26-mobile-real-device-churn-harness.md). Phase A hardware validation and JUnit integration **pending** (device session deferred).

**Next execution slice:** **F1 auth churn harness** (Phase A → B) — see [`I-2026-05-31-mobile-android-stack-followups`](I-2026-05-31-mobile-android-stack-followups.md) and `TB-2026-0002` F1 section. **F2a** enrollment bootstrap in progress ([#254](https://github.com/mgagp/ezkey/issues/254)); **F2b** full QR automation remains future.

## Links

- Tracer bullet pilot: `TB-2026-0002`
- Direction note: `V-2026-0011`
- Source incubation plan: `.cursor/plans/ezkey_mobile_android_real_device_automation.plan.md`
- Related documentation: `ezkey_mobile/docs/MOBILE_FUNCTIONAL_FLOWS.md`, `ezkey_mobile/docs/MOBILE_STACK_AND_ARCHITECTURE.md`, `ezkey-tests/README.md`, `docker/README.md`, `docs/ENDPOINT.md`
- Related diagnostic context: `ezkey_mobile/MOBILE_PENDING_DEBUG_PLAN.md`
- Churn + evidence design (next phase): `ezkey_mobile/docs/MOBILE_REAL_DEVICE_CHURN_AND_EVIDENCE.md`
- Test plan slice: [`TSP-2026-06-26-mobile-real-device-churn-harness.md`](../test-plans/TSP-2026-06-26-mobile-real-device-churn-harness.md)
- Related operational pattern: `ezkey-tests/scripts/run-operational-churn.sh`
- GitHub: [#239](https://github.com/mgagp/ezkey/issues/239), [#254](https://github.com/mgagp/ezkey/issues/254)
