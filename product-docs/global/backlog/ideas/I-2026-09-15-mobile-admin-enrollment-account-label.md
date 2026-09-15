# Backlog Idea — `I-2026-09-15` Mobile admin enrollment account label (bind/pending)

## Metadata

- **ID:** `I-2026-09-15-mobile-admin-enrollment-account-label`
- **Status:** `parked`
- **Priority:** `P3`
- **Lane:** `D` — post-delivery evolution (deferred from mobile identity UX, 2026-09-15)
- **Created at:** `2026-09-15`
- **Updated at:** `2026-09-15`
- **Last reviewed at:** `2026-09-15`
- **Component tags:** `auth-api`, `admin-api`, `mobile`, `docs`
- **Captured by:** Marc
- **GitHub issue:** _(none — canon sufficient until the trigger below is observed)_

## Intent

When one phone holds **two admin enrollments that a person cannot tell apart** (same
person-first `enrollmentName`, same installation), give the authenticator a **dedicated,
non-parsed** label for the operator role — for example `accountLabel` / `adminType` on
Auth bind (and pending if the card still needs it). Do **not** stuff Global/Tenant Admin
into `enrollmentName`, and do **not** parse that blob on mobile.

## Why this is parked

2026-09-15 product decision: mobile identity is **person-first**. Admin MFA enrollments
are generated as `{firstName} {lastName}`, with ` ({username})` only when the VERIFIED
uniqueness constraint on the system integration requires it. Role, username, and the
word “MFA” do not belong in the authenticator hero.

That covers the common case (one Global Admin or one Tenant Admin on the phone, Unicorn
Farm / all-in-one SME). Global vs Tenant remains **Admin UI chrome** (`adminType` on
`AdminResponseDto`), not an authenticator title.

## Trigger (unpark when this is real)

Unpark when **any** of these is observed in use, not as speculative completeness:

- The same person has **both** a Global Admin and a Tenant Admin MFA enrollment on one
  device, and Home/Detail/Pending cannot tell them apart.
- An admin MFA enrollment and a **business** enrollment share the same person-first
  name under the same installation, and the user cannot choose the right card.
- An explicit product decision to show a short secondary line (“Global Admin” /
  “Tenant Admin”) even without collision.

Search vocabulary for cold agents: `accountLabel`, `adminType`, bind `enrollmentName`,
`Global Admin MFA`, dual admin on one phone, person-first enrollment display.

## Suggested shape (not committed)

- Optional Auth API field on **bind** (persisted locally with the enrollment), e.g.
  `accountLabel` (`Global Admin` / `Tenant Admin`) or a stable `adminType` enum.
  Pending can reuse the stored enrollment; a pending-only field is a last resort.
- Mobile: secondary line **only when it is distinct** from hero and installation
  (same collision rule as `buildEnrollmentIdentityDisplay` /
  `buildPendingRequestTitles`).
- Fail-closed uniqueness stays on `enrollmentName`; the new field is display-only.
- OpenAPI refresh via live `/api-docs` (never hand-edit `specs/`).

## Out of scope

- Parsing `Global Admin MFA - Marie Dupont (marie.dupont)` on the client.
- Putting role or username in the hero by default.
- A new mobile API surface beyond bind (and pending only if bind is insufficient).

## Current shipped baseline (do not regress)

- Generator: `org.ezkey.admin.util.AdminEnrollmentDisplayNames`
- Callers: `AdminBootstrapService`, `AdminProvisioningService`
- Mobile display: `ezkey_mobile/app/utils/enrollmentDisplay.ts` (shows `enrollmentName`
  as-is; leftover pre-change blobs remain unparsed)

## Links

- Product intent / operator split:
  [`../../product-intent.md`](../../product-intent.md),
  [`../../operator-alignment-guide.md`](../../operator-alignment-guide.md)
- Mobile positioning (authenticator, not admin console):
  [`../../../../ezkey_mobile/docs/MOBILE_POSITIONING.md`](../../../../ezkey_mobile/docs/MOBILE_POSITIONING.md)
- Bind DTO (where a future field would land):
  `ezkey-auth-api/.../enrollment/dto/EnrollmentBindResponseDto.java`
