# Capability matrix v0 — 2026-07-18 (Desktop authenticator)

## Session control

| Field | Value |
| ----- | ----- |
| **Backlog** | `I-2026-07-17-desktop-authenticator-reference-app` |
| **Lane** | `A` (new idea refinement) |
| **Status** | `complete` |
| **Date** | `2026-07-18` |
| **Captured by** | Copilot + Marc refinement |
| **Purpose** | Promotion aid for TB preparation |

## Scope and intent

Provide a first-cut, explicit capability baseline for Windows, macOS, Linux, and software
fallback so the MVP can be designed with honest security posture and predictable UX behavior.

Legend used in matrix:

- `Yes`: expected as first-cut default behavior.
- `Partial`: available in some environments or behind constraints.
- `No`: not expected in first cut.
- `TBD`: requires implementation spike before commitment.

## Capability matrix (first cut)

| Capability | Windows adapter (CNG/KSP) | macOS adapter (Security Framework) | Linux adapter (software-first) | Software fallback adapter | UX implication (first cut) |
| ---------- | ------------------------- | ---------------------------------- | ------------------------------ | ------------------------- | -------------------------- |
| Hardware-backed keys | Partial | Partial | No | No | Display trust badge only when capability is true; no global hardware claim. |
| Non-exportable private key | Yes | Yes | Partial | Partial | Export actions remain unavailable; messaging differs by trust level. |
| User verification gate (OS-level) | Partial | Partial | No | No | Step-up prompt only when available, else explicit fallback copy. |
| Biometric integration | Partial | Partial | No | No | Biometric toggle shown only when capability true. |
| Attestation evidence | No (v0) | No (v0) | No | No | Hide attestation-dependent UX in first cut. |
| Secure local storage class | Yes | Yes | Partial | Partial | Security posture panel must show storage class and confidence level. |
| Supported algorithms baseline | Yes (Ed25519/ECDSA target profile) | Yes (Ed25519/ECDSA target profile) | Yes (Ed25519/ECDSA target profile) | Yes (Ed25519/ECDSA target profile) | Keep algorithm choice internal for MVP; no user-facing selector. |

## Platform notes (first-cut assumptions)

### Windows

- Adapter uses CNG/KSP boundary, not direct TPM coupling in business code.
- Hardware-backed behavior depends on host configuration/provider routing.

### macOS

- Adapter uses Security Framework boundary.
- Secure Enclave usage is opportunistic and capability-driven, not assumed globally.

### Linux

- First cut targets secure software storage posture.
- Hardware-backed and biometric features are deferred unless a bounded spike proves stable.

### Software fallback

- Provides portability baseline for environments with no usable native hardening path.
- Must be explicitly labeled as lower trust than hardware-backed options.

## Normalized capability contract candidate

```text
CapabilityDescriptor {
  hardwareBacked: boolean
  nonExportablePrivateKey: boolean
  userVerification: enum { none, optional, required }
  biometric: boolean
  attestation: enum { none, basic, strong }
  secureStorageClass: enum { software, os-keystore, hardware-backed }
  supportedAlgorithms: string[]
}
```

## Promotion-oriented checks

1. Confirm each MVP use case (bind, verify, one approval response) can run with this matrix
   without platform-specific UI branching.
2. Approve capability-to-copy mapping for all `Partial` and `No` rows (honest operator messaging).
3. Validate fallback behavior when capabilities degrade at runtime (for example after OS policy
   change).
4. Lock first-cut algorithm profile in a short annex aligned with payload-signature canon.

## Strategic decisions (operator-confirmed posture, 2026-07-18)

1. **Non-exportability (Linux/software):** keep `Partial` in first cut where that reflects reality;
   do not block MVP on absolute guarantees. Prioritize quick wins and clear messaging over
   dogmatic uniformity.
2. **User verification:** use opportunistic enablement (`optional` when capability exists);
   absence must not block core flows in MVP. Always disclose effective protection level.
3. **Attestation:** keep a lightweight contract extension point from day one, but no functional
   attestation promise in MVP.
4. **Downgrade signal:** implement an explicit runtime posture warning when effective capability
   drops (for example `hardwareBacked` true->false, or `secureStorageClass` downgrade).

## Product-value guardrails behind these decisions

- Honest-by-default security narrative: no over-claim versus actual platform behavior.
- Progressive hardening: improve posture iteratively without blocking first operational value.
- User choice transparency: when desktop posture is weaker, keep the mobile participant as an
  explicit stronger-security alternative.

## MVP hardening baseline (operator-confirmed)

1. Encryption at rest for local sensitive material is mandatory in first cut.
2. Non-exportability is best effort by platform; no false universal guarantee.
3. Security posture signaling is mandatory (effective capability and storage class visibility).
4. Runtime downgrade warning is mandatory when effective posture decreases.
5. Additional hardening layers beyond this baseline are planned as post-MVP evolution.

## Packaging and distribution baseline (operator-confirmed)

1. Initial target market priority: Windows first.
2. macOS and Linux are still in scope, with post-Windows sequencing intentionally left open.
3. First implementation planning should optimize for Windows operability without blocking
   cross-platform architecture decisions.

## Residual implementation questions (non-blocking for promotion)

1. Exact UX copy and threshold policy for downgrade warnings.
2. Concrete Linux storage backend choice for first implementation cut.
3. Minimal telemetry/audit fields to record capability downgrades.
4. Post-Windows packaging order and acceptance criteria between macOS and Linux.

## Links

- Backlog idea: `../ideas/I-2026-07-17-desktop-authenticator-reference-app.md`
- Architecture synthesis: `2026-07-18-desktop-authenticator-contract-first-architecture-synthesis.md`
- Stack comparison: `2026-07-17-desktop-authenticator-stack-mini-comparison.md`
- Signature payload canon: `../../../docs/ENROLLMENT_SIGNATURE_PAYLOAD.md`
- Signature payload canon: `../../../docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md`
