# Backlog Idea — `I-2026-07-05-enrollment-integration-key-cycling` Enrollment integration key cycling via auth-exchange hooks

## Metadata

- **ID:** `I-2026-07-05-enrollment-integration-key-cycling`
- **Status:** `incubating`
- **Vision:** [`V-2026-07-05-enrollment-integration-key-cycling-posture`](../../vision/V-2026-07-05-enrollment-integration-key-cycling-posture.md)
- **Priority:** `P3`
- **Created at:** `2026-07-05`
- **Updated at:** `2026-07-05`
- **Last reviewed at:** `2026-07-05`
- **Progression markers:** `P4-compliance-readiness`
- **Component tags:** `auth-api`, `core`, `mobile`, `demo-device`, `docs`, `crypto`
- **Lane:** `A`
- **Captured by:** Marc

## Intent

Explore and eventually implement **controlled rotation** of per-enrollment **integration Ed25519
signing keys** — moving from generate-once-for-life toward **policy-gated shorter lifetimes** — by
**hooking orderly key refresh into the existing pending / respond protocol** when a real
authentication exchange completes, with resilience for timeouts, denials, network loss, and partial
failure. Default remains static-long; cycling is opt-in and phased.

## Problem and value

- **Problem:** Integration private keys live as long as the enrollment. For harvest-now /
  forge-later threat models, long-lived Ed25519 integration keys maximize exposure of signable
  protocol material tied to one key epoch. Proof tokens are ephemeral; integration keys are not.
- **Expected value:**
  - Shorter effective signing-key windows without a big-bang PQC migration.
  - Uses validated cryptographic exchanges as the **delivery channel** for new public keys.
  - Gradual, policy-controlled hardening with auditability and enrollment-safe failure modes.
  - Honest security narrative: **window reduction**, not post-quantum algorithm replacement.

## Scope

- **In scope (phased program):**
  - **Phase 0 (design):** ADR + component design pack — rotation triggers, grace window, wire
    fields, audit events, mobile persistence rules, rollback.
  - **Phase 1 (policy model):** Configuration for opt-in cycling (min interval, max key age,
    enable flag) without protocol change — documentation and operator intent only.
  - **Phase 2 (protocol):** Extension to pending / respond (or respond-only handoff — see grill) to
    propose and commit next integration public key with **old-key grace period**.
  - **Phase 3 (clients):** Mobile, Demo Device, SDKs, golden tests, Postman / OpenAPI refresh.
- **Out of scope (default):**
  - Replacing Ed25519 with PQC (ML-DSA, hybrids) — separate future program.
  - Device key (EC P-256 Keystore) rotation — different constraint surface.
  - Rotation on every pending poll — rejected (see grill D2).
  - September 2026 operable-release structural work.
  - Breaking VERIFIED enrollments on failed rotation attempts.

## Key assumptions

- Shorter integration key epochs **materially reduce** HNDL value even if algorithm stays Ed25519.
- The pending / respond path can carry a **cryptographically ordered** key handoff without
  unnecessary protocol sprawl.
- A **two-phase commit** (propose → device confirms by verifying under new key) with **old-key grace**
  is required for resilience.
- Operators need **static-long default** plus optional policy for gradual adoption.

## Risks and exceptions

- **Rotation thrashing:** Too-frequent policy or pending-driven rotation wastes mobile storage /
  confuses verification — mitigated by min interval and anchor on **terminal auth completion** (grill).
- **Partial handoff:** User denies, timeout, MITM, datacenter outage mid-rotation — old key must
  remain valid; state machine must be explicit and audited.
- **Client skew:** Mobile stores stale `integrationPublicKey` — needs version or fingerprint in
  payload and fail-closed verification rules.
- **Complexity creep:** Must stay simpler than full PQC migration; phase gates enforced.
- **Overclaim:** Must not market as "post-quantique" — window management only.

## Candidate mechanism (non-final — for design pack)

Illustrative direction from operator brainstorming; **not** committed implementation:

1. When policy says rotation is due **and** an auth attempt reaches a **terminal state** with a
   valid pending/respond chain, backend includes **`nextIntegrationPublicKey`** (and algorithm) in
   respond payload, signed by **current** integration key.
2. Mobile verifies signature under **current** key, persists **next** key atomically after
   successful verification.
3. Backend activates new key only after grace rules satisfied; **current** key verifies until epoch
   closes or attempt counter exhausted.
4. If respond never arrives or rotation aborts, **no key change** — enrollment unchanged.

Design pack must validate or replace this sketch.

## Settled decisions (grill 2026-07-05)

| ID | Summary |
| -- | ------- |
| D1 | Problem framing **valid** — shortens exposure window; **not** PQ algorithm fix |
| D2 | **Do not** rotate on every pending poll or bare pending-with-request alone |
| D3 | Prefer rotation anchor on **terminal auth attempt completion** (design pack: APPROVED-only vs all terminals) |
| D4 | Require **min interval** between rotations and optional **max key age** |
| D5 | **Two-phase handoff + grace period** — old key remains valid until device-confirmed commit |
| D6 | **Static-long default**; cycling **opt-in** via policy |
| D7 | Failed / aborted rotation **must not** break VERIFIED enrollment |
| D8 | **Audit trail** for propose, commit, abort, rollback |
| D9 | Wire changes tie to **protocol capability versioning** (`I-2026-0025`) when needed |
| D10 | **No execution before post–R1** except Phase 0 design / ADR |

Full rationale: [`2026-07-05-enrollment-integration-key-cycling-grill-me.md`](../grill-sessions/2026-07-05-enrollment-integration-key-cycling-grill-me.md).

## Open questions (for design pack)

1. Rotate after **APPROVED only** vs any terminal outcome (DENIED, EXPIRED)?
2. Grace window: time-based, attempt-count-based, or both?
3. Store key epoch / `integrationKeyId` on enrollment row vs separate table?
4. Operator-visible rotation status in Admin UI — R1 of feature or later?
5. Demo Device and CLI parity timeline.

## Promotion notes

Move toward `ready` / `TB-*` when:

- Component design pack approved (state machine, wire fields, failure matrix).
- Phase 0 ADR merged.
- First slice scoped: e.g. respond-only handoff + mobile persist + audit event + golden test.

## Priority posture

- **P3** — crypto evolution; **not** on [`operational-readiness-prioritization-2026-09.md`](../../operational-readiness-prioritization-2026-09.md) compass.
- **Incubating** after grill; execution deferred post–R1 (D10).

## Links

- Vision: [`V-2026-07-05-enrollment-integration-key-cycling-posture`](../../vision/V-2026-07-05-enrollment-integration-key-cycling-posture.md)
- Parent PQ map (2026-09-15): [`V-2026-09-15-post-quantum-crypto-posture`](../../vision/V-2026-09-15-post-quantum-crypto-posture.md),
  [`I-2026-09-15-post-quantum-protocol-evolution`](I-2026-09-15-post-quantum-protocol-evolution.md)
  — this idea stays the **window-management** child, not algorithm replacement.
- Grill: [`2026-07-05-enrollment-integration-key-cycling-grill-me.md`](../grill-sessions/2026-07-05-enrollment-integration-key-cycling-grill-me.md)
- Crypto canon: [`docs/CRYPTO.md`](../../../docs/CRYPTO.md), [`docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md`](../../../docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md)
- Lifecycle: [`docs/LIFECYCLE_GOVERNANCE.md`](../../../docs/LIFECYCLE_GOVERNANCE.md)
- Protocol versioning: [`I-2026-0025`](I-2026-0025-auth-api-protocol-capability-versioning.md)
- Contrast: encryption key rotation (platform at-rest) — [`docs/REENCRYPTION_OPERATIONS.md`](../../../docs/REENCRYPTION_OPERATIONS.md) — different domain
