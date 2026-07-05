# Vision Note — `V-2026-07-05-enrollment-integration-key-cycling-posture` Enrollment integration key cycling for shorter-lived signing exposure

## Metadata

- **ID:** `V-2026-07-05-enrollment-integration-key-cycling-posture`
- **Status:** `under-review`
- **Lane:** `A`
- **Created at:** `2026-07-05`
- **Updated at:** `2026-07-05`
- **Captured by:** Marc

## Intent

Evolve Ezkey's **per-enrollment integration signing key** (Ed25519, server-held) from a **generate-once,
use-for-life** model toward a **controlled, shorter-lived** model — using the existing bind → verify →
pending → respond cryptographic exchange path as the natural carrier for orderly key refresh when
policy allows. Goal: **reduce harvest-now / forge-later exposure** and improve classical and
**window-management** security posture without claiming full post-quantum resistance on Ed25519.

## Motivation

Post-quantum pre-analysis (2026-07-05) established:

- **Proof tokens** and symmetric crypto are already strong for their lifetime.
- **Integration and device signatures** (Ed25519 / EC P-256) remain **pre-quantum**; the main
  operational concern for long-lived keys is **recorded traffic + later forgery** once a CRQC exists.

Integration keys today are created at enrollment provisioning and used for bind, pending, and
respond-result signatures for the **entire VERIFIED enrollment lifetime** — the longest-lived
asymmetric secret in the MFA chain. Proof tokens, by contrast, are ephemeral.

**Hypothesis:** Shortening integration key exposure through **policy-gated cycling** hooked to
authenticated exchange flows is a **legitimate, proportionate** hardening step — not a substitute for
future PQC algorithms, but a **complexity-controlled gradual improvement** aligned with Ezkey's
security product identity.

## Potential impact

- **Milestone:** Post–September 2026 operable release (`R1`) or later protocol evolution slice —
  **not** on current operability compass.
- **Components:** `auth-api`, `core`, `mobile`, `demo-device`, `docs`, `crypto`; optional
  [`I-2026-0025`](../backlog/ideas/I-2026-0025-auth-api-protocol-capability-versioning.md) for
  capability negotiation.
- **User segments:** Operators with long-lived enrollments; security evaluators; future compliance
  narratives (honest window reduction, not PQ certification).

## Signals and constraints

- **Default posture preserved:** Static-long integration key remains the **default**; cycling is
  **opt-in** via policy (tenant / enrollment / deployment profile) until proven.
- **Enrollment is the security anchor** ([`docs/LIFECYCLE_GOVERNANCE.md`](../../../docs/LIFECYCLE_GOVERNANCE.md)) —
  failed rotation must **never** break a VERIFIED enrollment or device association.
- **Protocol contract:** Mobile stores `integrationPublicKey` on secure path and verifies pending /
  respond-result signatures ([`docs/CRYPTO.md`](../../../docs/CRYPTO.md),
  [`docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md`](../../../docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md)).
  Any rotation requires an **ordered handoff** the device can cryptographically validate.
- **Not PQ-complete:** Cycling Ed25519 keys does **not** defeat Shor; it **shrinks the recorded
  material's value** per key epoch. Do not extend recovery-code PQ wording to this feature.

## Promotion criteria

Promote to active design / execution when:

1. Post–R1 capacity or explicit crypto-hardening milestone is allocated.
2. Grill decisions D1–D10 accepted (see grill session).
3. Component design pack defines rotation anchor, grace window, failure modes, and audit events.
4. Capability versioning path agreed if wire format changes (`I-2026-0025`).

## Related documents

- [`I-2026-07-05-enrollment-integration-key-cycling`](../backlog/ideas/I-2026-07-05-enrollment-integration-key-cycling.md)
- [`2026-07-05-enrollment-integration-key-cycling-grill-me.md`](../backlog/grill-sessions/2026-07-05-enrollment-integration-key-cycling-grill-me.md)
- [`docs/CRYPTO.md`](../../../docs/CRYPTO.md)
- [`docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md`](../../../docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md)
- [`docs/LIFECYCLE_GOVERNANCE.md`](../../../docs/LIFECYCLE_GOVERNANCE.md)
- [`operational-readiness-prioritization-2026-09.md`](../operational-readiness-prioritization-2026-09.md)
- Prior analytical context: post-quantum status review (2026-07-05 operator session; not yet a
  standalone canon doc)
