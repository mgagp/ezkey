# V-2026-0006 — Mobile certificate pinning posture: Ezkey middle path

- **Date:** `2026-05-08`
- **Status:** `under-review`
- **Captured by:** Marc

## Intent

Define an Ezkey-specific certificate pinning posture that is stronger than platform TLS-only traffic
while staying below the complexity and assurance ceremony of passkey/FIDO-grade ecosystems.

Target direction:

- SPKI pinning (`sha256(SPKI DER)`) at installation scope,
- TOFU bootstrap at enrollment,
- native mobile enforcement in steady state,
- narrow Auth API trust-recovery path for certificate/key rotation,
- explicit user confirmation before trust replacement,
- Android-first rollout.

## Strategic positioning (product line in the sand)

Ezkey keeps an explicit complexity boundary:

- We do not attempt to replicate the full formal assurance stack found around passkeys/WebAuthn.
- We do not claim server-verified StrongBox attestation or equivalent certification-grade proof.
- We do use StrongBox when available and surface the signal in backend data as client-reported
  metadata.

This is intentional and opinionated: Ezkey is a proportionate middle path for backend-first teams.
It aims to be stronger than passwords and classic TOTP/SMS flows for relevant contexts, without
claiming top-tier formal assurance equivalence.

Adopters retain the final risk decision for their organization: if they require stronger formal
guarantees, they should choose that higher-assurance route.

## Why this remains under review

The orientation is stable, but the final protocol and operational shape are still being refined.
Each review cycle may add constraints or simplify the ceremony while preserving the same product
line in the sand.

Open convergence items:

1. Final request/response canonical payload for Auth API trust recovery.
2. Pin-set discovery/versioning semantics in Cloudflare-backed deployments.
3. Audit surface for trust refresh attempts and accepted pin replacements.
4. iOS timing and parity strategy after Android validation.

## Non-goals and non-claims

- Not passkey/FIDO2 compatibility.
- Not full PKI or complete attestation-chain equivalence.
- Not a claim that StrongBox tier is cryptographically proven server-side today.
- Not maximal-security ceremony at any complexity cost.

## Canon trajectory

This vision is now rematerialized into:

- Retrofit slice: `R-2026-0001` (source extraction and historical rationale).
- Backlog idea: `I-2026-07-25-mobile-certificate-pinning-middle-path` (ongoing analysis and
  strategic positioning).

The original working plan was a point-in-time analysis artifact and has been retired after this
canonical rematerialization.

## Related artifacts

- `R-2026-0001` — Mobile certificate pinning (SPKI + TOFU + Ezkey-authenticated recovery)
- `I-2026-07-25-mobile-certificate-pinning-middle-path`
- `docs/MOBILE_DEVELOPER_GUIDE.md` (StrongBox trust-model boundary)
- `docs/SECURITY_POSTURE.md` (honest claims and non-claims)
