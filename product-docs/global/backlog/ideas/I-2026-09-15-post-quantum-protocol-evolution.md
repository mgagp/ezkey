# Backlog Idea — `I-2026-09-15-post-quantum-protocol-evolution` Post-quantum evolution of the Ezkey protocol (umbrella)

## Metadata

- **ID:** `I-2026-09-15-post-quantum-protocol-evolution`
- **Status:** `captured`
- **Vision:** [`V-2026-09-15-post-quantum-crypto-posture`](../../vision/V-2026-09-15-post-quantum-crypto-posture.md)
- **Priority:** `P3`
- **Created at:** `2026-09-15`
- **Updated at:** `2026-09-15`
- **Last reviewed at:** `2026-09-15`
- **Progression markers:** `P4-compliance-readiness`
- **Component tags:** `auth-api`, `core`, `mobile`, `demo-device`, `docs`, `crypto`, `infra`, `security`
- **Lane:** `A`
- **Captured by:** Marc

## Intent

Hold the **post–operable-release** program for Ezkey protocol crypto in a world with cryptographically relevant quantum computers: honest claims first, then transport hybrid KEM, then classical window management, then capability-negotiated hybrid signatures. This idea is a **map owner**, not a single tracer bullet. It must not be sold as “make Ezkey post-quantum in the next milestone.”

## Problem and value

- **Problem:** Device EC P-256 and integration Ed25519 keys are **generate-once per enrollment**. Under harvest-now / forge-later, public keys (protocol-visible by design) become forging tools once Shor-capable machines exist. TLS already protects ordinary on-path attackers, which is why this is **not** an existential product gap for the current SME audience — but the identity layer has no PQ story, and recovery-code copy overclaims quantum resistance.
- **Expected value:**
  - One discoverable parent for future slices instead of scattered “PQ” remarks.
  - Clear ordering so key cycling, capability versioning, and TLS ops are not mistaken for algorithm replacement.
  - Honest evaluator narrative aligned with [`docs/SECURITY_POSTURE.md`](../../../../docs/SECURITY_POSTURE.md).

## Scope

- **In scope (program, unfunded):**
  - Path 0 honesty (SECURITY_POSTURE, recovery-code Javadoc) after grill of the vision note.
  - Path 1 hybrid TLS at the edge (ops runbook, not Auth API).
  - Path 2 classical epoch rotation — **child already exists:** [`I-2026-07-05-enrollment-integration-key-cycling`](I-2026-07-05-enrollment-integration-key-cycling.md). Device-side epochs only if a later design pack beats re-enrollment.
  - Path 3 symmetric leftovers (API-key entropy, optional recovery-code encoding, `I-2026-0032` Tier 1).
  - Path 4 capability negotiation + hybrid signatures (integration first, device when hardware allows) — depends on [`I-2026-0025`](I-2026-0025-auth-api-protocol-capability-versioning.md).
- **Out of scope (default):**
  - Per-authentication device or integration key rotation as the PQ strategy (rejected in the vision note; integration per-pending already rejected in the 2026-07-05 grill).
  - Application-layer ML-KEM wrapping of pending/respond (Path 5 — likely reject).
  - September 2026 operable-release structural work.
  - `assessment-curated` hygiene lots per surface.

## Key assumptions

- TLS 1.3 remains mandatory; Ezkey will not become a “works on cleartext HTTP” protocol.
- Android/iOS hardware-backed PQC signing will lag server-side JDK ML-DSA; integration hybrid can precede device hybrid.
- Shor on published public keys is the dominant protocol PQ threat; Grover on 256-bit proof tokens and AES-256 is not.
- Window management without algorithm change is **HNDL mitigation**, not NIST PQC.

## Risks and exceptions

- **Overclaim:** any UI or README that says “post-quantum” because codes are 32 digits or keys rotate.
- **Complexity creep:** hybrid signatures + dual verification + Keystore constraints can exceed the 80/20 budget; capability versioning must exist first.
- **Enrollment invariant:** failed rotation or algorithm upgrade must not unbind a VERIFIED enrollment ([`docs/LIFECYCLE_GOVERNANCE.md`](../../../../docs/LIFECYCLE_GOVERNANCE.md)).
- **Duplicate children:** do not open a second integration-cycling idea; extend `I-2026-07-05`.

## Promotion notes

Move toward `incubating` when the vision note is grilled (or explicitly accepted) and at least one funded slice is chosen (honesty-only vs TLS ops vs cycling Phase 0 vs capability sketch).

Move toward `ready` / `TB-*` only for a **named path** with a design pack — never for “the whole PQ program.”

Stay **off** [`operational-readiness-prioritization-2026-09.md`](../../operational-readiness-prioritization-2026-09.md).

## Links

- Vision (analysis-design map): [`V-2026-09-15-post-quantum-crypto-posture`](../../vision/V-2026-09-15-post-quantum-crypto-posture.md)
- Integration key cycling: [`I-2026-07-05-enrollment-integration-key-cycling`](I-2026-07-05-enrollment-integration-key-cycling.md)
- Protocol capabilities: [`I-2026-0025`](I-2026-0025-auth-api-protocol-capability-versioning.md)
- Proof-token hash-only: [`I-2026-0032`](I-2026-0032-proof-token-hash-only-storage.md)
- Pinning (transport integrity, not PQ): [`I-2026-07-25-mobile-certificate-pinning-middle-path`](I-2026-07-25-mobile-certificate-pinning-middle-path.md)
- ADR-0006: [`architecture-decisions.md`](../../architecture-decisions.md)
- Crypto canon: [`docs/CRYPTO.md`](../../../../docs/CRYPTO.md)
