# Backlog Idea — `I-2026-09-15-post-quantum-protocol-evolution` Post-quantum evolution of the Ezkey protocol (umbrella)

## Metadata

- **ID:** `I-2026-09-15-post-quantum-protocol-evolution`
- **Status:** `incubating`
- **Priority:** `P3`
- **Created at:** `2026-09-15`
- **Updated at:** `2026-11-03`
- **Last reviewed at:** `2026-11-03`
- **Component tags:** `auth-api`, `core`, `mobile`, `demo-device`, `sdk`, `crypto-api`, `infra`, `docs`
- **Captured by:** Marc
- **GitHub issue:** `#1165` (Path 3 child). Paths 1–2 follow-up issues to be opened.

## Intent

Own the map and the watch list for Ezkey protocol crypto against a future quantum computer, so
that each slice starts at the right time and no slice is sold as "Ezkey is post-quantum". The
order is:

1. honest claims;
2. verify hybrid TLS;
3. trim crypto exposure in the Admin API;
4. lay down algorithm identifiers before the first adopter;
5. hybrid signatures, a medium-term slice gated by mobile OS adoption.

## Problem and value

- **Problem:** device EC P-256 and integration Ed25519 are pre-quantum. Today the risk is a
  **migration debt with a horizon**, not a present weakness:
  - no adopters;
  - clean starts;
  - NIST IR 8547 deprecates ECDSA/EdDSA after 2030 and disallows them after 2035.

  The protocol also lacks a device algorithm identifier. The Admin API returns crypto material
  that plays no operator role.
- **Expected value:**
  - one discoverable owner instead of scattered post-quantum remarks;
  - slices that start on explicit triggers;
  - agility hooks laid down while protocol changes are still free (no adopters).

## Scope

- **In scope (map in [`V-2026-09-15`](../../vision/V-2026-09-15-post-quantum-crypto-posture.md) § Evolution paths):**
  - Path 0: honest claims. **Done** in #1158.
  - Path 1: TLS hybrid key-exchange probe and ledger record (ops).
  - Path 2: Admin API / Admin UI crypto exposure trim, after characterization.
  - Path 3: device key algorithm identifier, fail-closed (#1165).
  - Path 4: hybrid signatures. Design both sides, implement integration first. **Medium term**,
    with a wake-up rule.
  - Path 5: enrollment re-key ceremony. Documentation only until the first adopter.
- **Out of scope:**
  - per-authentication key rotation as a post-quantum strategy (rejected);
  - application-layer ML-KEM (rejected);
  - Grover-motivated lengthening of API keys or recovery codes (removed; ordinary hygiene if ever
    wanted);
  - raising mobile minimum OS versions for post-quantum reasons alone.

## Key assumptions

- TLS 1.3 stays mandatory.
- Until the first adopter, an algorithm change is a flag day: clean start + re-enrollment + one
  mobile release.
- Server-side ML-DSA is JDK-native (JDK 24+). Mobile ML-DSA is platform-native only on iOS 26+ and
  Android API 37+. Ezkey targets iOS 15.5 and minSdk 31.

## Risks and exceptions

- **Overclaim:**
  - "post-quantum" because codes are long or keys rotate;
  - "hybrid integration signatures" read as protecting `APPROVED`, when that still rests on the
    device P-256.
- **Supply chain:** the Android ML-DSA verifier (Bouncy Castle vs JS vs native) needs a written
  security-owner opinion. No independent audit of `@noble/post-quantum` was found (2026-11-03).
- **Enrollment invariant:** a failed algorithm upgrade must not unbind a VERIFIED enrollment
  ([`docs/LIFECYCLE_GOVERNANCE.md`](../../../../docs/LIFECYCLE_GOVERNANCE.md)).
- **Duplicate children:** extend `I-2026-07-05` for cycling and `I-2026-0025` for capability
  negotiation; do not fork them.

## Promotion notes

- Paths 1–3 go through GitHub issues with the normal fix → test → community redeploy cycle.
- Path 4 moves to a design pack (`TB-*` or ADR) only when the V-note wake-up rule fires:
  - mobile minimum OS can reach iOS 26 / API 37 for the device side;
  - an acceptable Android verifier exists for the integration side;
  - a first regulated adopter asks;
  - the 2028 review arrives.
- Watch list re-checked at each quarterly major-upgrade review.
- Stay off the release priority compass until a path is funded.

## Links

- Vision (map, decisions, feasibility, watch list): [`V-2026-09-15-post-quantum-crypto-posture`](../../vision/V-2026-09-15-post-quantum-crypto-posture.md)
- Honest claims: [`docs/SECURITY_POSTURE.md`](../../../../docs/SECURITY_POSTURE.md) (#1158)
- Device algorithm identifier: #1165
- Integration key cycling: [`I-2026-07-05-enrollment-integration-key-cycling`](I-2026-07-05-enrollment-integration-key-cycling.md)
- Protocol capabilities: [`I-2026-0025`](I-2026-0025-auth-api-protocol-capability-versioning.md)
- Proof-token hash-only: [`I-2026-0032`](I-2026-0032-proof-token-hash-only-storage.md)
- ADR-0006: [`architecture-decisions.md`](../../architecture-decisions.md)
- Crypto canon: [`docs/CRYPTO.md`](../../../../docs/CRYPTO.md)
