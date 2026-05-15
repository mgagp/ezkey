# Backlog Idea — `I-2026-0019` Android real-device mobile functional tests

## Metadata

- **ID:** `I-2026-0019`
- **Status:** `active`
- **Priority:** `P1`
- **Created at:** `2026-05-08`
- **Updated at:** `2026-05-08`
- **Last reviewed at:** `2026-05-08`
- **Phase tags:** `P1-operability`, `P2-hardening`
- **Component tags:** `ezkey-mobile`, `ezkey-tests`, `auth-api`, `admin-api`, `docker`

## Intent

Introduce a pragmatic, repeatable Android real-device functional test capability for `ezkey_mobile`, using the local clean-start Docker stack and a physically connected phone as the trusted execution target. The goal is not to replace unit tests or existing API functional tests, but to add a missing confidence layer for the exact backend-to-phone trust path that matters most in Ezkey: enrollment, pending retrieval, respond, and repeated operational churn on a real Android device.

## Problem and value

- **Problem:** The mobile app is part of Ezkey's cryptographic trust chain, yet the current automated coverage is concentrated in unit tests and API functional tests. That leaves a confidence gap around real Android key storage, real UI flows, real device signing, and intermittent issues that only show up on phone hardware during `pending` / `respond`.
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

Promoted to tracer bullet pilot on `2026-05-08`: **`TB-2026-0002`** (`product-docs/global/backlog/ideas/TB-2026-0002-android-real-device-functional-pilot.md`). Execution and exit criteria are owned by that brief.

**Execution (`2026-05-08`):** Maestro flows, Bash runner, and `ezkey.e2e.*` testIDs landed under `ezkey_mobile/maestro/` and `ezkey_mobile/scripts/run-real-device-pilot-maestro.sh`. Exit criteria in the TB still apply until validated on hardware.

## Links

- Tracer bullet pilot: `TB-2026-0002`
- Direction note: `V-2026-0011`
- Source incubation plan: `.cursor/plans/ezkey_mobile_android_real_device_automation.plan.md`
- Related documentation: `ezkey_mobile/docs/MOBILE_FUNCTIONAL_FLOWS.md`, `ezkey_mobile/docs/MOBILE_STACK_AND_ARCHITECTURE.md`, `ezkey-tests/README.md`, `docker/README.md`, `docs/ENDPOINT.md`
- Related diagnostic context: `ezkey_mobile/MOBILE_PENDING_DEBUG_PLAN.md`
- Related operational pattern: `ezkey-tests/scripts/run-operational-churn.sh`
