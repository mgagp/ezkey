# Ezkey Mobile Positioning

## Purpose and Reading Scope

This document positions Ezkey Mobile as the reference end-user mobile participant in the Ezkey ecosystem.
It explains what the app is for, who it serves, what it is not, and how its scope relates to the shared Ezkey
backend-first product thesis.

It is intentionally short and product-facing. Detailed API semantics, cryptographic wording, and implementation
mapping remain in the root-level shared docs and the other mobile conceptual documents.

## What Ezkey Mobile Is

Ezkey Mobile is the reference end-user app for Ezkey's mobile side of the cryptographic MFA chain. It is not a thin
notification shell. It is the device participant that completes enrollment and later approves or denies authentication
requests through a protocol that remains backend-first.

Core characteristics:

- a cross-platform React Native mobile app for real enrollment and approval flows,
- a secure participant in the Ezkey cryptographic chain,
- a user-facing surface designed to stay simple and deliberate,
- a demonstration-quality product surface for prospective adopters,
- a reference implementation that helps coding agents and contributors understand how the Auth API is consumed in practice.

## What Ezkey Mobile Is Not

Ezkey Mobile is intentionally not:

- an admin console,
- a browser-first or passkey-style authentication product,
- a FIDO2 or WebAuthn compatibility layer,
- a feature-rich identity platform client,
- a background-polling push approval shell,
- a mobile app that claims stronger platform-hardware guarantees than the current implementation can prove,
- the canonical source of shared Ezkey protocol truth.

## Target Users and Operator Context

| Audience | Primary goal | Practical expectation | Notes |
| --- | --- | --- | --- |
| End user | Enroll a trusted device and approve or deny sign-in requests | Clear trust signals, little protocol jargon, predictable actions | Primary audience for the runtime UX. |
| Security / IT evaluator | Validate that the mobile flow is credible and aligned with the backend protocol | Evidence that the app follows the documented bind/verify/pending/respond chain | Secondary audience during pilots and internal rollout. |
| Demo operator / developer advocate | Showcase Ezkey's end-user experience in demos | Reliable, understandable flow that reflects the backend-first thesis | Important because the app also helps explain the platform. |
| Contributor or coding agent | Understand the intended mobile product boundary quickly | A simple conceptual map of what the app is trying to do and not do | This is a documentation audience rather than an end user. |

## Product Value in the Ezkey Ecosystem

| Stakeholder | Problem | Mobile value | Supporting Ezkey capability |
| --- | --- | --- | --- |
| End user | Needs a trusted device to approve sign-ins without reading backend internals | Simple enrollment and explicit approve/deny flow | Auth API plus local device crypto |
| Evaluator | Needs to see that Ezkey is more than backend endpoints | Concrete end-user surface that demonstrates the protocol chain | Documented bind/verify and pending/respond lifecycle |
| Operator | Needs a device participant that reflects backend trust boundaries | Mobile UI that does not hide or contradict the backend-first model | Shared crypto and endpoint contract |
| Project | Needs a coherent platform story, not only API surfaces | Mobile app completes the product narrative and demoability | Backend-first MFA with a mobile participant |

## v1 Scope Boundaries

| Included | Excluded | Notes |
| --- | --- | --- |
| QR-driven enrollment | Manual multi-step provisioning UX | Enrollment entry is intentionally narrow in v1. |
| Approve / deny authentication requests | Broad account management | Mobile stays focused on the auth participant role. |
| Local persistence of enrollments | Multi-instance environment switching | The app targets one effective Ezkey instance at a time per enrollment set. |
| User-initiated polling | Background polling and push notifications | Important trust and UX constraint. |
| English-only runtime copy | Full localization system | Forward-compatible later, not current scope. |
| Readable installation/integration context | Full admin metadata management | Display is pragmatic, not administrative. |

Current implementation note: the codebase now includes a `DangerZone` screen for destructive local actions, which is
broader than the original PRD's read-only v1 positioning for destructive management. That deviation should be treated
as an explicit product reality, not hidden.

## Enrollment identity vocabulary

The authenticator must let a person tell **which hat they are wearing** on every screen. One noun has one meaning.
Field-to-screen mapping lives in [MOBILE_API_MAPPINGS.md](MOBILE_API_MAPPINGS.md); helper:
`app/utils/enrollmentDisplay.ts`.

| Noun | Meaning | Shown when |
| --- | --- | --- |
| **Installation** | Ezkey instance / trust zone (for example Unicorn Farm). Zone chrome only. | Home group header; Detail/Pending only when it is distinct from the hero. |
| **Purpose** | What this enrollment signs in to. Regular: integration name (Ride Booking). Admin MFA: localized **Administration**. Never repeat the installation name. | Home card title; Wizard card title; Detail/Pending secondary when distinct from the zone. |
| **Account** | `enrollmentName` — person for admin MFA; operator-chosen name for a regular enrollment. Never call this Device. | Hero on Detail; Wizard Account row; Home subtitle; Pending when there is no request `contextTitle`. |
| **Role** | Localized Global Admin or Tenant Admin. French UI keeps the English loanword **tenant** (`Admin tenant`), not *locataire* — same convention as Admin UI (`TB-2026-07-26`). Never in the hero. Never stuffed into `enrollmentName`. | Every system-integration (admin MFA) enrollment, on Home, Wizard, Detail, Pending, and Danger Zone. Hidden for regular integrations. |
| **Device** | This phone. | Destructive copy and Security settings only. |

Collision rule: the same string must not appear as two hierarchy levels.

### Decision grid

Design density for **one hat per person**, which is the real-life default. A tester phone that stacks Global Admin + Tenant Admin + a regular integration is an integrity check, not the layout target. Do not put every field on every Home card to make that stack scannable.

**Regular integration user** (Ride Booking QR, or an integration such as admin1)

- Wizard: purpose = integration name; account = enrollment name; hide tenant if it equals the installation; show tenant **description** under the tenant name when both are present and distinct; no Device row; challenge copy is QR-adjacent, not “admin console”.
- Home: installation shell stays the Ezkey instance (Unicorn Farm). Cards stay Purpose + Account. The **business tenant** is a section inside that shell — eyebrow `Tenant`, name, and description — only when the tenant name differs from the installation **and** the group is not admin-MFA-only.
- Detail / pending: hero account (or pending `contextTitle`); secondary = purpose if distinct from the zone; tenant name + description when they add information the installation does not already say.

**Global Admin MFA**

- Purpose = Administration (localized).
- Account = person.
- Role line = Global Admin (localized).
- Do not show the system tenant or a business tenant they do not enroll against. Home: no tenant section for an admin-MFA-only group.

**Tenant Admin MFA** (MFA is still on the system integration)

- Same as Global Admin, role line = Tenant Admin.
- Do **not** put their managed business tenant on this MFA card. Administration + Tenant Admin is the honest story. Which tenant they **operate** in the Admin console is Admin UI chrome, not authenticator chrome.

**Mix on one phone** (integrity case, not the density target)

- Regular vs admin: purpose (Ride Booking vs Administration).
- Global vs Tenant: role line.
- Same person name twice: role and/or the existing ` (username)` uniqueness suffix. No client parser of old blobs.
- Regular enrollments of a distinct business tenant sit in that tenant section; admin MFA stays under the installation with no extra folder.

**EXP1 / generic branding**

- Host hint when the installation name equals the host. No full URL.

Settings and About stay unchanged unless they reprint enrollment labels.

## Relationship to Shared Ezkey Positioning Docs

| Document | Why it remains canonical | How this document uses it |
| --- | --- | --- |
| [../../docs/PROJECT_POSITIONING.md](../../docs/PROJECT_POSITIONING.md) | Shared Ezkey thesis, what the platform is, and what it is not | This document narrows that thesis to the mobile participant and end-user product surface. |
| [../../docs/CRYPTO.md](../../docs/CRYPTO.md) | Shared cryptographic wording and guarantees | This document avoids restating algorithmic guarantees beyond positioning-level language. |
| [../PRD.md](../PRD.md) | Mobile scope source and design decisions | This document distills the mobile vision and scope into a shorter positioning view. |
| [MOBILE_STACK_AND_ARCHITECTURE.md](MOBILE_STACK_AND_ARCHITECTURE.md) | Technical shape of the mobile app | This document stays at the product and conceptual boundary level. |
