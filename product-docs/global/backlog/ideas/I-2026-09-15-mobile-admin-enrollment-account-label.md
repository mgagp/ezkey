# Backlog Idea — `I-2026-09-15` Mobile admin enrollment account label (bind/pending)

## Metadata

- **ID:** `I-2026-09-15-mobile-admin-enrollment-account-label`
- **Status:** `done`
- **Priority:** `P1`
- **Lane:** `A` — delivery (unparked for pre-release identity integrity, 2026-09-15)
- **Created at:** `2026-09-15`
- **Updated at:** `2026-09-15`
- **Last reviewed at:** `2026-09-15`
- **Component tags:** `auth-api`, `admin-api`, `mobile`, `docs`
- **Captured by:** Marc
- **GitHub issue:** _(none — shipped in the mobile identity display-grid slice)_

## Intent

When one phone holds **two admin enrollments that a person cannot tell apart** (same
person-first `enrollmentName`, same installation), give the authenticator a **dedicated,
non-parsed** label for the operator role — `adminType` on Auth bind, persisted locally.
Do **not** stuff Global/Tenant Admin into `enrollmentName`, and do **not** parse that
blob on mobile.

## Why this is shipping

Pre-release integrity: the authenticator must show which hat the user is wearing on
every screen. Person-first `enrollmentName` stays. Role is a dedicated bind field,
shown as a localized Role line for every system-integration (admin MFA) enrollment.

Canon: [`ezkey_mobile/docs/MOBILE_POSITIONING.md`](../../../../ezkey_mobile/docs/MOBILE_POSITIONING.md)
§ Enrollment identity vocabulary.

## Contract

- Bind: `isSystemIntegration` (boolean) and optional `adminType`
  (`GLOBAL_ADMIN` | `TENANT_ADMIN`), both included in the signed bind payload
  (`docs/ENROLLMENT_SIGNATURE_PAYLOAD.md`).
- Persist both on the local enrollment record. Pending reuses storage.
- Mobile: Purpose = localized Administration for system integrations; Role line
  for every admin MFA enrollment; never in the hero.
- Fail-closed uniqueness stays on `enrollmentName`; the new fields are display-only
  after bind signature verification.
- OpenAPI refresh via live `/api-docs` (never hand-edit `specs/`).

## Out of scope

- Parsing `Global Admin MFA - Marie Dupont (marie.dupont)` on the client.
- Putting role or username in the hero.
- Showing which business tenant a Tenant Admin manages on the MFA card.
- Hard-coding English “Administration” as the stored system integration name.

## Current shipped baseline (do not regress)

- Generator: `org.ezkey.admin.util.AdminEnrollmentDisplayNames`
- Callers: `AdminBootstrapService`, `AdminProvisioningService`
- Mobile display: `ezkey_mobile/app/utils/enrollmentDisplay.ts`

## Links

- Product intent / operator split:
  [`../../product-intent.md`](../../product-intent.md),
  [`../../operator-alignment-guide.md`](../../operator-alignment-guide.md)
- Mobile positioning (authenticator, not admin console):
  [`../../../../ezkey_mobile/docs/MOBILE_POSITIONING.md`](../../../../ezkey_mobile/docs/MOBILE_POSITIONING.md)
- Bind DTO:
  `ezkey-auth-api/.../enrollment/dto/EnrollmentBindResponseDto.java`
