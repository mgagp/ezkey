# Grill Me — 2026-07-17 (Desktop authenticator reference app)

## Session control

| Field | Value |
|-------|-------|
| **Backlog** | `I-2026-07-17-desktop-authenticator-reference-app` |
| **Lane** | `A` (new idea) |
| **Status** | `complete` |
| **Date** | `2026-07-17` |
| **Captured by** | Marc |

## Context

- Idea captured to add an installable desktop participant for Ezkey enrollment and authentication approvals.
- Baseline direction: preserve mobile protocol parity and demo-device visual familiarity while keeping security posture explicit.
- Goal of this session: settle first-cut boundaries and promotion conditions for tracer-bullet readiness.

## Settled decisions (operator-confirmed 2026-07-17)

| ID | Decision |
|----|----------|
| G1 | Target posture is **both** demo/evaluation and everyday operator use from the start. |
| G2 | First-cut platform scope is **cross-platform**: Windows + macOS + Linux. |
| G3 | Desktop stack is **not locked yet**; run a short comparative pass before TB promotion. |
| G4 | Security posture for first cut is **protocol parity + minimal local hardening** (without over-claiming mobile hardware-backed guarantees). |
| G5 | First executable slice must be **end-to-end minimal**: bind + verify + one approval response path. |
| G6 | Promotion evidence minimum: payload/signature unit tests + local smoke run on desktop app + explicit security posture documentation. |
| G7 | UX direction is **reference-first**: use mobile and Demo Device as primary visual/workflow anchors, adapt to operator needs, and avoid a new UX paradigm unless clearly necessary. |
| G8 | Demo Device elements that are useful only for development/test should be removed or down-prioritized in desktop operator mode. |

## UX quality criterion (accepted)

Desktop first cut is considered acceptable when:

1. equivalent protocol actions are presented with recognizably similar visual grouping versus mobile/demo-device,
2. bind/verify/pending/respond flow ordering remains aligned,
3. operator-facing screens do not expose avoidable development/test noise.

## Open questions to resolve before TB

1. Stack decision for first implementation cut (JavaFX vs Tauri vs Electron) with explicit rationale and delivery cost. **Comparative recommendation available** in `2026-07-17-desktop-authenticator-stack-mini-comparison.md` (Tauri primary, Electron fallback).
2. Minimal local hardening baseline for desktop key material (storage, process posture, device/user lock assumptions).
3. Distribution packaging baseline for all three platforms (artifact format and install story).
4. Whether approval polling cadence remains user-initiated only for first cut (parity with mobile posture).

## Resolution update (2026-07-18)

Following operator confirmation:

1. Stack decision is resolved: Tauri selected for first cut.
2. Hardening baseline direction is resolved:
   - encryption at rest mandatory,
   - non-exportability best effort,
   - explicit posture signaling mandatory,
   - runtime downgrade warning mandatory,
   - additional hardening deferred as post-MVP evolution.
3. Packaging posture is partially resolved:
   - Windows-first priority confirmed,
   - macOS vs Linux post-Windows sequence intentionally left for later decision.

See linked artifacts for canonical wording:

- `2026-07-17-desktop-authenticator-stack-mini-comparison.md`
- `2026-07-18-desktop-authenticator-capability-matrix-v0.md`

## Promotion recommendation

Move the idea from `captured` to `ready` and create `TB-*` after:

1. stack comparison note is completed and first stack is selected,
2. hardening baseline is written and reviewed,
3. first-slice validation plan is confirmed across unit + manual smoke + docs,
4. UX adequacy check against mobile/demo-device reference is documented.

## Links

- Backlog idea: `../ideas/I-2026-07-17-desktop-authenticator-reference-app.md`
- Stack comparison: `2026-07-17-desktop-authenticator-stack-mini-comparison.md`
- Signature payload canon: `../../../docs/ENROLLMENT_SIGNATURE_PAYLOAD.md`
- Signature payload canon: `../../../docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md`
- Related prior idea: `../ideas/I-2026-06-18-demo-device-qr-auth-url-parity.md`
