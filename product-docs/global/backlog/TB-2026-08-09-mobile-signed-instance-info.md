# Tracer Bullet Brief — `TB-2026-08-09-mobile-signed-instance-info` Signed enrolled instance-info

## Metadata

- **ID:** `TB-2026-08-09-mobile-signed-instance-info`
- **Status:** `under-review`
- **Related idea:** `I-2026-08-09-mobile-signed-instance-info-integrity`
- **GitHub issue:** none
- **GitHub branch:** `feature/tb-2026-08-09-mobile-signed-instance-info`
- **GitHub PR:** none
- **Created at:** `2026-08-09`
- **Updated at:** `2026-08-09`
- **Captured by:** Marc

## Objective

Prove end-to-end that an enrolled mobile client can obtain installation branding from the Auth API
via `POST /api/v1/enrollments/instance-info`, verify an integration Ed25519 signature over the
canonical `INSTANCE_INFO` payload, and apply branding only when verification succeeds — closing the
MITM rewrite channel on the enrolled refresh path without changing the public unsigned GET.

## Boundaries in scope

- Auth API: `POST /api/v1/enrollments/instance-info` (request `enrollmentProofToken`; response
  branding fields + `enrollmentId` + `instanceInfoPayloadSignedByIntegration`)
- `ezkey-core`: canonical payload builder + integration signing (same primitives as bind/verify)
- Docs: `docs/ENDPOINT.md`, `docs/ENROLLMENT_SIGNATURE_PAYLOAD.md` (or sibling section), Postman
  Auth collection when coding
- Mobile: wizard post-verify + Home stale/incomplete refresh use signed POST; verify with stored
  `integrationPublicKey` before `buildInstallation` / persist
- OpenAPI refresh + Orval regen when the Auth contract lands (clean-start → `update-specs` →
  `yarn generate:api`)
- Unit tests: Java payload/sign/verify; mobile payload rebuild + verify + one refresh-path test

## Out of scope

- User-visible honesty copy (“server-advertised label”)
- Branding-change confirmation dialog
- Certificate pinning (`I-2026-07-25-mobile-certificate-pinning-middle-path`)
- New instance-level signing key
- Changing or removing `GET /api/v1/public/instance-info`
- Device-proof / ECDSA on the request
- Contact/support fields on any instance-info DTO
- Admin API signed parity (public GET on Admin stays as today)

## First executable slice

1. Add canonical payload helper + Auth API POST that resolves enrollment by proof-token hash,
   loads org branding config, signs with the enrollment’s integration private key, returns DTO.
2. Document payload and endpoint; extend Postman.
3. Mobile: replace unsigned GET on enrolled paths with signed POST; fail-closed on signature
   failure; fail-open on network/HTTP; never fall back to unsigned GET.
4. Tests green for payload + one mobile refresh path; Maven baseline + mobile unit tests for the
   touched surface.

## Rollback or fallback posture

- Feature is additive (new POST). If mobile wiring is risky, keep shipping the Auth endpoint behind
  docs but do not switch wizard/Home until verify path is solid.
- Revert is: mobile returns to previous unsigned GET (reopens MITM channel — acceptable only as
  temporary rollback); Auth endpoint can remain unused.

## Critical flows

- **Nominal (wizard):** After verify succeeds, POST instance-info with `enrollmentProofToken` →
  verify signature → persist installation branding.
- **Nominal (Home):** Stale/incomplete trust zone → one signed POST per installation (use any
  enrollment’s proof token for that zone) → verify → update metadata for enrollments in the zone.
- **Exception — bad signature:** Keep last good branding (or host-only); do not apply; log/warn.
- **Exception — network/HTTP error:** Keep last good branding; do not call unsigned GET.
- **Exception — unknown/invalid proof token:** Auth API returns existing anti-enumeration /
  not-found posture consistent with bind (do not leak enrollment existence beyond current norms).

## Evidence plan

| Layer | Required |
| --- | --- |
| Unit (Java) | Canonical payload string + integration sign/verify round-trip |
| Unit (mobile) | Payload rebuild + verify failure does not apply branding; refresh path uses POST |
| Contract | ENDPOINT + enrollment signature payload docs; OpenAPI/Postman updated in the coding change set |
| Traceability | I-* ↔ TB links; index rows |
| Manual (optional) | Clean-start enroll → Home shows branding; tampering not applicable without MITM harness |

## Quality gates

- **Analysis gate:** Grill 2026-08-09 D1/D2 recorded on parent idea (closed).
- **Design gate:** Payload and fail-closed rules match idea Settled decisions; no instance key; GET
  public retained.
- **Implementation gate:** `./scripts/build.sh` green for Java touch; mobile unit tests for touched
  files green; specs refreshed if stack available when coding.

## Exit criteria

1. Auth API `POST /api/v1/enrollments/instance-info` returns integration-signed branding for a valid
   enrollment proof token.
2. Mobile wizard post-verify and Home refresh use that path and verify before apply.
3. Signature failure never replaces stored branding; enrolled path never falls back to unsigned GET.
4. Docs and generated Auth OpenAPI/Postman describe the same contract.
5. Parent idea follow-up (honesty UI + branding-change confirm) remains explicitly deferred, not
   silently dropped.

## Canonical contract (frozen)

```text
{enrollmentProofToken}|{enrollmentId}|INSTANCE_INFO|{authApiPublicBaseUrl}|{instanceName}|{instanceDescription}|{aboutUrl}
```

Request: `{ "enrollmentProofToken": "..." }`  
Response fields: `enrollmentId`, `authApiPublicBaseUrl`, `instanceName`, `instanceDescription`,
`aboutUrl`, `instanceInfoPayloadSignedByIntegration`.

## Links

- Idea:
  [`ideas/I-2026-08-09-mobile-signed-instance-info-integrity.md`](ideas/I-2026-08-09-mobile-signed-instance-info-integrity.md)
- Payload style reference:
  [`docs/ENROLLMENT_SIGNATURE_PAYLOAD.md`](../../../docs/ENROLLMENT_SIGNATURE_PAYLOAD.md)
- Public GET (retained):
  [`docs/ENDPOINT.md`](../../../docs/ENDPOINT.md) § Public instance metadata
- Adjacent:
  [`ideas/I-2026-07-25-mobile-certificate-pinning-middle-path.md`](ideas/I-2026-07-25-mobile-certificate-pinning-middle-path.md),
  [`ideas/I-2026-08-02-mobile-installation-version-and-compat-discovery.md`](ideas/I-2026-08-02-mobile-installation-version-and-compat-discovery.md)
