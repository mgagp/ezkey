# R-2026-0003 — Mobile iOS Implementation Plans Retrofit

## Metadata

- **ID:** R-2026-0003
- **Status:** integrated
- **Source type:** plan
- **Sources:**
  - `plans/mobile_ios_phase_1_current_state_audit.plan.md`
  - `plans/mobile_ios_phase_2_apple_stack_baseline.plan.md`
  - `plans/mobile_ios_phase_3_contract_first_mock_shell.plan.md`
  - `plans/mobile_ios_phase_4_native_crypto_parity.plan.md`
  - `plans/mobile_ios_phase_5_simulator_integration_and_permissions.plan.md`
  - `plans/mobile_ios_phase_6_real_device_bind_verify.plan.md`
  - `plans/mobile_ios_phase_7_auth_flow_parity_and_hardening.plan.md`
- **Created at:** 2026-05-24
- **Updated at:** 2026-05-24
- **Sources annotated:** yes
- **Capture context:** All 7 plans were authored before the formal methodology was established. All todos are in `pending` status — no execution has been recorded. The operator confirmed (2026-05-24) that the existing iOS-specific code in `ezkey_mobile/ios/` is embryonic, predates all major Android refactoring, and should be treated as deleted for the purpose of the iOS rebuild.

## Search scope

- Searched: `plans/`, `.cursor/plans/`, `ezkey_mobile/docs/`, `product-docs/components/mobile/`, `product-docs/global/`, GitHub issues
- Found relevant: 7 `mobile_ios_phase_*.plan.md` files under `plans/`; supporting context in `ezkey_mobile/docs/MOBILE_CRYPTO_REFERENCE.md`, `NATIVE_MODULES.md`, `MOBILE_STACK_AND_ARCHITECTURE.md`
- Excluded: historical source `.cursor/plans/auth_api_spki_pinning_recovery_analysis.plan.md` (retired 2026-07-25; iOS mentioned for Phase 5 of pinning, already covered by R-2026-0001); no GitHub issues found on the iOS topic

## Extracted decisions and invariants

### D1 — Android-first posture (confirmed)
The mobile product is Android-first. iOS is a planned later phase. This is already recorded in `ezkey_mobile/AGENTS.md` and `product-docs/components/mobile/stack-and-architecture.md`.

### D2 — Fresh iOS native rebuild on shared React Native foundation (confirmed)
The target strategy is **not** a full app rewrite from zero. The shared React Native surface (TypeScript, navigation, screens, generated DTOs, flow logic) is reused. What is rebuilt fresh is the **iOS native layer** (crypto, QR, bridge, Keychain integration).

### D3 — Existing iOS-specific code is treated as deleted (new — confirmed 2026-05-24)
The existing `ezkey_mobile/ios/` content is embryonic and predates major Android refactoring. Starting from the iOS-specific native code is not viable. The rebuild starts from a clean iOS native project scaffold, not from what currently exists.

### D4 — Contract-first approach using Auth API DTOs
The iPhone app must use the same generated Auth API DTOs and flow semantics as the Android reference. No divergent contract universe.

### D5 — Simulator-first development, then real device
Development proceeds on simulator first. Simulator limitations are documented explicitly. Real device validation (Phase 6) is triggered specifically for hardware-gated behaviors (Secure Enclave, camera QR).

### D6 — Minimum iOS deployment target: 15.1 (to be confirmed)
Phase 2 sets the default at iOS 15.1. This requires toolchain confirmation during Phase 2 execution.

### D7 — EC P-256, Secure Enclave path, conservative reporting
iOS crypto must align with the Ezkey payload and key-format contract (EC P-256, CSPRNG-backed proof token). Secure Enclave availability must be reported conservatively. Simulator cannot prove Secure Enclave behavior. This extends ADR-MOB-0002.

### D8 — Development-only QR injection path on simulator
A temporary, development-only QR payload injection mechanism is acceptable for Phase 3 to unblock simulator progress. It must be explicitly annotated and removed before production.

### D9 — Certificate pinning: iOS deferred
Already recorded in `R-2026-0001`. Android pinning is validated first; iOS mirrors the model in a later phase. Not re-recorded here.

## Phase sequence extracted

| Phase | Focus | Status at extraction |
|-------|-------|---------------------|
| 1 | Audit current iOS state, keep/rewrite/defer decision | Superseded — operator decision to start fresh (D3) |
| 2 | Apple stack baseline (Xcode, CocoaPods, iOS 15.1 min) | Pending |
| 3 | Contract-first mock shell on simulator | Pending |
| 4 | Native crypto parity (EC P-256, Secure Enclave) | Pending |
| 5 | Simulator integration and permissions | Pending |
| 6 | Real device — bind and verify | Pending |
| 7 | Auth flow parity and hardening | Pending |

Note: Phase 1 is effectively resolved by D3. The first execution slice (TB-*) starts at Phase 2.

## Changes applied

- [x] `product-docs/components/mobile/design-decisions.md` — added ADR-MOB-0005 (start fresh on iOS native)
- [x] `product-docs/global/backlog/ideas/I-2026-0027-mobile-ios-implementation.md` — created, status `ready`
- [x] `product-docs/global/backlog/TB-2026-0004-mobile-ios-phase2-apple-stack-baseline.md` — created
- [x] `plans/mobile_ios_phase_*.plan.md` (7 files) — annotated with `retrofitted_by: R-2026-0003`
- [ ] `product-docs/components/mobile/stack-and-architecture.md` — deferred; iOS baseline section update belongs in Phase 2 execution, not retrofit

## Gaps and residual uncertainty

- Exact Xcode version and CocoaPods compatibility with React Native 0.85.2 — to be resolved in Phase 2.
- Whether `react-native-vision-camera` iOS support at current version requires any config changes — to be checked in Phase 3.
- Certificate pinning iOS mechanics — deferred to a later phase, already tracked in R-2026-0001.

## Next retrofit batch candidate

None identified for this topic. The 7 plans are fully mined. Future iOS phases will produce new canonical docs directly rather than requiring additional retrofit.
