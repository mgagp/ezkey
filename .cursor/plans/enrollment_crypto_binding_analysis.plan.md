---
name: Enrollment crypto binding analysis
overview: Cryptographic binding for enrollment (bind/verify) vs auth (pending/respond); threat model; canonical payloads; pragmatic recommendations updated for pre-production (breaking changes acceptable, minimal field impact).
todos:
  - id: spec-enrollment-payloads
    content: Specify canonical strings for bind response, verify request, verify response in docs (parallel to AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md)
  - id: implement-unified-enrollment
    content: If approved, implement bind response signing + verify extended device payload + verify response signing in one protocol pass (no versioning needed pre-GA)
  - id: impact-codebase
    content: Core services/DTOs, Auth API, mobile + demo device, tests; maintainer runs update-specs after clean start
isProject: true
---

# Enrollment vs authentication — cryptographic binding (analysis)

## Development phase and breaking-change posture

**Current situation:** Active development; **no production deployments** that constrain the API. **Breaking changes are acceptable** when they improve the protocol, because there is **no migration burden on the field** — the main "cost" is implementation and documentation in-repo (mobile, demo device, tests), not dual-version support or customer cutovers.

**Implication for value evaluation:**

- The **marginal cost** of shipping a **stronger verify request** (extended device-signed canonical payload instead of raw `enrollmentProofToken` only) is **much lower now** than after GA: no API versioning, no deprecation window, no parallel code paths for legacy clients.
- The **right time** to align enrollment with the same "bind attributes into signatures" discipline as auth is **before** external integrators depend on stable wire formats.
- **Value assessment** should weight **protocol correctness and market narrative** (solid, unified crypto story) heavily and **downweight** "avoid breaking clients" — which is near-zero risk on the ground today.

This does **not** mean adding complexity for its own sake; it means **optional** hardening that was deferred as "breaking" becomes **reasonable to bundle** into the current development track.

---

## Current state (summary)

### Authentication (reference pattern)

| Step | Signed by | Payload (conceptually) |
|------|-----------|-------------------------|
| Pending response | Integration | `proofToken\|challengeRequired\|contextTitle\|contextMessage` |
| Respond request | Device | `proofToken\|accepted` |
| Respond response | Integration | `proofToken\|authAttemptId\|result\|message` |

Pending **request** signs only `deviceProofToken` (minimal device commitment); heavy binding is on integration-signed payloads and respond.

### Enrollment (gaps)

| Step | Signed today | Gap |
|------|--------------|-----|
| Bind response | Nothing | TOFU: `integrationPublicKey` not integrity-protected beyond TLS |
| Verify request | Raw `enrollmentProofToken` only | Challenge, enrollment id, SPKI not in signed bytes (server validates separately); tier already documented as outside signature |
| Verify response | Nothing | No integration attestation of success |

---

## Threat model (unchanged)

- **Bind response signing** remains the **highest** incremental value: closes "fake integration key" under broken TLS at enrollment time.
- **Verify request extended payload** is **defense-in-depth** + **parity** with respond-style commitment; **more justified pre-GA** when breaking change cost is ~zero externally.
- **Verify response signing** adds outcome integrity and symmetry with respond result.

---

## Recommended direction (updated for pre-production)

1. **Specify** one canonical document (next to `docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md`) for enrollment: bind response, verify request, verify response — **UTF-8, `|`, NFC on agreed user-facing fields only**.

2. **Implement as a single protocol revision** (no API version flag required if nothing is in production):
   - Integration-signed **bind** response (payload includes enrollment id, proof token, integration public key, metadata as agreed).
   - Device-signed **verify** request over a canonical string including at least `enrollmentProofToken`, `enrollmentId`, `challengeResponse`, `devicePublicKey` (exact format TBD in spec).
   - Integration-signed **verify** response (e.g. proof token + enrollment id + result enum + message).

3. **Bind request** remains unsigned by device (no device key yet); secret + TLS + server-side checks stay sufficient unless a future attestation story is in scope.

---

## What stays pragmatic (80-20 within "do it now")

- **Key attestation** / server-verified `devicePrivateKeyStorageTier` is still out of scope unless explicitly prioritized — orthogonal to canonical string binding.
- **Do not** gold-plate bind request without a clear threat model.

---

## Parity note

Auth pending request does **not** concatenate all body fields into one device signature — so "full" parity is about **where user trust and integration identity are established**, not signing every JSON field on every request. Enrollment's **priority** remains integration-signed **bind** first; extended verify + signed verify response fit naturally in a **pre-GA** bundle.
