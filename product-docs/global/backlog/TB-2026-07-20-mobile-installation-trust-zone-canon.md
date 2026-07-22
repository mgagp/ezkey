# Tracer Bullet Brief — `TB-2026-07-20-mobile-installation-trust-zone-canon` Installation trust-zone canon

## Metadata

- **ID:** `TB-2026-07-20-mobile-installation-trust-zone-canon`
- **Status:** `done`
- **Related idea:** `I-2026-07-20-mobile-installation-trust-zone-canon`
- **Lane:** `D`
- **Posture:** `single-pass`
- **GitHub issue:** _(none yet)_
- **Created at:** `2026-07-20`
- **Updated at:** `2026-07-20`
- **Closed at:** `2026-07-20`
- **Captured by:** Marc (Grill Me MOB-011, 2026-07-20)

## Objective

Lock and implement the mobile **installation trust-zone canon**: normalized Auth URL as installation
identity, enrollment **belongs to** that zone in docs and local data structures, with tests — so the
follow-on identity/crypto TB can assume a coherent model.

## Boundaries in scope

- `ezkey_mobile/docs/MOBILE_DATA_MODEL.md` (and thin related conceptual doc touch-ups)
- TypeScript types / helpers for `Installation` and enrollment ownership posture
- `normalizeInstallationId` / `validateAuthUrl` contract tests
- Code and comment vocabulary that still frames installation as mere UI subordinate metadata

## Out of scope

- Keystore alias / seal key / `StoredEnrollment.id` collision remediations (next TB)
- MOB-013 / MOB-016 behavior changes
- Auth API / OpenAPI
- MOB-012
- iOS parity gate

## First executable slice

1. Rewrite data-model canon: installation = trust zone; enrollments belong to it; identity anchor =
   normalized Auth URL (no installation UUID).
2. Realign local structures/types so ownership is explicit end-to-end without forcing a premature
   Keystore cut (nested persistence OK if refactor-friendly).
3. Extend normalization tests for equivalence classes already product-critical (case, `:443`,
   trailing slash, distinct paths).
4. Cross-link assessment MOB-011 and the follow-on idea/TB.

## Rollback or fallback posture

- Docs/types-only revert is cheap; no production fleet.
- If structure changes prove too entangled with Keystore handles, stop and fold remaining structure
  work into the follow-on TB rather than shipping a lying half-model.

## Critical flows

- **Nominal:** Two enrollments with equivalent normalized Auth URLs group as one installation; two
  distinct normalized URLs are two trust zones in the model.
- **Exception:** Invalid Auth URL does not become an installation id; branding/`instance-info` never
  replaces URL identity.

## Evidence plan

| Layer | Required |
| --- | --- |
| Unit | `urlValidation` / installation identity tests |
| Docs | `MOBILE_DATA_MODEL.md` posture updated; no “subordinate as product truth” contradiction |
| Traceability | Links from I/TB ↔ pass-2 campaign ↔ assessment MOB-011 |
| Manual (optional) | Home still groups by installation after type/structure touch-ups |

## Quality gates

- **Analysis gate:** Grill Me 2026-07-20 decisions recorded on related I-* and campaign.
- **Design gate:** No Auth API contract change; no installation UUID.
- **Implementation gate:** Mobile unit tests for normalization green; docs match code posture.

## Exit criteria

1. Canon docs state enrollment belongs to installation trust zone (normalized URL). ✅
2. Local structures/types no longer encode the contradictory “installation is only decoration” posture. ✅
3. Normalization contract covered by tests for the agreed equivalence classes. ✅
4. Follow-on `TB-2026-07-20-mobile-installation-scoped-enrollment-identity` unblocked. ✅

## Closeout (2026-07-20)

- Docs: `MOBILE_DATA_MODEL.md`, thin `MOBILE_API_MAPPINGS.md` association section
- Code: `resolveEnrollmentTrustZoneId`; hydrate no longer falls back to enrollment `id` as trust-zone id
- Types: `Installation` / `EnrollmentSummary` JSDoc ownership posture
- Tests: `urlValidation` + `installationMetadata` (28 passing)

## Links

- Idea: [`ideas/I-2026-07-20-mobile-installation-trust-zone-canon.md`](ideas/I-2026-07-20-mobile-installation-trust-zone-canon.md)
- Follow-on TB: [`TB-2026-07-20-mobile-installation-scoped-enrollment-identity.md`](TB-2026-07-20-mobile-installation-scoped-enrollment-identity.md)
- Campaign: [`../hygiene/mobile-protocol-security/2026-07-19-pass-2.md`](../hygiene/mobile-protocol-security/2026-07-19-pass-2.md)
