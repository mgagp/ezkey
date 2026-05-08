# Backlog Idea — `I-2026-0010` Phone-to-phone enrollment transfer ceremony

## Metadata

- **ID:** `I-2026-0010`
- **Status:** `captured`
- **Priority:** `P2`
- **Created at:** `2026-05-08`
- **Updated at:** `2026-05-08`
- **Last reviewed at:** `2026-05-08`
- **Phase tags:** `P1-operability`, `P3-distribution`
- **Component tags:** `mobile`, `auth-api`, `admin-api`, `admin-ui`

## Intent

Provide a self-service mobile flow where a user with a new phone can transfer all enrollments from the previous phone in a controlled, audited ceremony. The ceremony uses the trusted backend as the common element, a six-digit user-supplied challenge to prove the same person holds both devices, an optional QR code to convey new-device identity, and per-step audit and state transitions. At completion, the new phone has the equivalent enrollments, and the original phone is deactivated and wiped after final confirmation.

## Problem and value

- **Problem:** Today, a user replacing a phone has no autonomous, self-hosted path to migrate enrollments. The available options are operator-assisted re-enrollment or relying on out-of-band material such as recovery codes. This contradicts the Ezkey value of operator and end-user autonomy in self-hosted contexts.
- **Expected value:** A clean, self-contained ceremony that fits the Ezkey DNA — self-hosted, autonomous, auditable — with operator surface limited to lifecycle observability rather than per-user assistance.

## Scope

- **In scope:**
  - Mobile UX: settings entry point on the original phone to initiate, mirrored entry point on the new phone.
  - Auth API surface: minimal endpoints for ceremony initiation, six-digit challenge mediation, per-step state transitions, finalization, and post-finalization wipe confirmation on the original phone.
  - QR-code conveyance of new-device identity (optional aid).
  - Audit chain: every step (initiation, challenge mediation, state transitions, final completion, wipe) recorded.
  - Edge cases: ceremony abandonment, network failure mid-flow, tamper attempts (challenge mismatch), capability divergence between phones.
- **Out of scope:**
  - Cross-tenant or cross-installation migration.
  - Migration of ancillary mobile data beyond enrollment material (preferences, etc.).
  - Operator-driven assisted migration (separate flow).

## Key assumptions

- The backend remains the trusted element; the user's possession of the original phone is the authority for the transfer.
- The six-digit challenge is sufficient to bind both endpoints to the same human in the same session.
- Enrollment material can be re-bound to a new device key without altering integration-side identity (this needs validation against current enrollment crypto binding).

## Risks and exceptions

- Audit must cleanly express the transfer event and the wipe/deactivation of the original phone, without ambiguity that could be exploited.
- A bad actor with temporary access to the original phone could initiate the transfer; the six-digit challenge mitigates by requiring concurrent access to the new phone but does not eliminate the risk; consider an additional confirmation delay or notification to the operator.
- Interaction with `V-2026-0001` (per-enrollment local-auth posture): a new phone may have different capabilities; the transfer must respect per-enrollment policy or surface incompatibility cleanly.

## Promotion notes

Move to `incubating` once the Auth API surface is sketched and audit semantics are explicit. The grilling pass should focus on edge cases (abandonment, tampering, capability divergence between phones).

## Links

- Adjacent vision: `V-2026-0001` (per-enrollment local-auth posture) — capability divergence between phones must be reconciled.
- Related backlog: `I-2026-0001` (per-enrollment local-auth discovery).
- Related principles: `#3` (backend-first integrity), `#5` (operator-first / user-first), `#11` (lifecycle without surprise), `#12` (security as posture).
