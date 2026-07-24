# Backlog Idea — `I-2026-07-20-mobile-installation-trust-zone-canon` Mobile installation trust-zone canon

## Metadata

- **ID:** `I-2026-07-20-mobile-installation-trust-zone-canon`
- **Status:** `done`
- **Priority:** `P1`
- **Created at:** `2026-07-20`
- **Updated at:** `2026-07-20`
- **Last reviewed at:** `2026-07-20`
- **Closed at:** `2026-07-20`
- **Progression markers:** `P1-operability`
- **Component tags:** `mobile`
- **Lane:** `D`
- **Captured by:** Marc (Grill Me on MOB-011 handoff, 2026-07-20)
- **GitHub issue:** _(none yet)_

## Intent

Make the mobile app’s conceptual and data-model posture match the product claim: an Ezkey
**installation** is a trust zone identified by a **normalized Auth API URL**, and every enrollment
**belongs to** that trust zone. This is the foundation for collision-free multi-installation on one
phone; it does not yet change Keystore alias derivation (that is the follow-on idea).

## Problem and value

- **Problem:** Home already groups enrollments by installation, but `MOBILE_DATA_MODEL.md` and parts
  of the code still treat installation as subordinate presentation metadata on an enrollment whose
  primary local identity is the server `enrollmentId`. That framing mismatch enabled MOB-011
  (enrollment-id-only crypto/storage handles). Multi-installation was always the product intent;
  crypto/storage design was under-scoped relative to the UI trust-zone boundary.
- **Expected value:**
  - A documented, tested canon: `installation = normalizeInstallationId(authUrl)`.
  - End-to-end vocabulary and structures: enrollment belongs to installation trust zone.
  - A clean prerequisite so installation-scoped local enrollment identity (MOB-011 and related)
    can be implemented as a coherent second activity, not a band-aid.

## Scope

- **In scope:**
  - Canon documentation (`ezkey_mobile/docs/MOBILE_DATA_MODEL.md` and related mobile conceptual docs).
  - Data-model / TypeScript structure realignment so installation ownership is explicit and
    consistent (pragmatic nested persistence allowed if it does not twist short-term refactorability).
  - Vocabulary fixes wherever code or docs still imply “installation subordinate to enrollment” as
    the product posture.
  - Tests for URL normalization / installation identity contract (extend existing
    `urlValidation` / `normalizeInstallationId` coverage as needed).
  - Traceability links to the MOB-011 program and pass-2 campaign.
- **Out of scope:**
  - Keystore alias / sealed-secret logical key / collection-id collision fix (see
    `I-2026-07-20-mobile-installation-scoped-enrollment-identity`).
  - MOB-013 silent `ensureEnrollmentKeyPair` and MOB-016 orphan cleanup (same follow-on idea).
  - Auth API / OpenAPI contract changes.
  - Installation UUID (rejected: trust normalized URL).
  - Temporary ban on multi-enroll UX.
  - iOS parity as a release gate (Android-first).
  - MOB-012 unlocked-device gate (separate hygiene handoff).

## Key assumptions

- Product claim: one app may hold enrollments for multiple independent Ezkey installations; trust
  zones must not encroach on each other.
- `normalizeInstallationId` / `validateAuthUrl` remain the uniqueness contract (host case, trailing
  slash, implicit `:443`, distinct non-empty paths).
- No production customer fleet; greenfield cutover risk is acceptable for a clean design.
- Activity sequencing: this idea **before** installation-scoped enrollment identity work.

## Risks and exceptions

- Partial structure changes without finishing the follow-on identity/crypto slice leave a documented
  “belongs to” model while Keystore still collides — mitigate by keeping this idea bounded to canon
  + model readiness, and starting the follow-on immediately after.
- Over-normalizing distinct path-based Auth base URLs (or under-normalizing equivalents) — rely on
  explicit tests and the existing validation rules.
- Scope creep into full Keystore migration inside this idea — keep out of scope explicit.

## Promotion notes

Grill Me 2026-07-20 locked the product posture. Tracer bullet
[`TB-2026-07-20-mobile-installation-trust-zone-canon`](../TB-2026-07-20-mobile-installation-trust-zone-canon.md)
closed 2026-07-20. Next: execute
[`TB-2026-07-20-mobile-installation-scoped-enrollment-identity`](../TB-2026-07-20-mobile-installation-scoped-enrollment-identity.md).

## Links

- Assessment: [`docs/security/mobile-protocol-crypto-assessment-2026-07.md`](../../../../docs/security/mobile-protocol-crypto-assessment-2026-07.md) §14.2 MOB-011
- Campaign: [`product-docs/global/hygiene/mobile-protocol-security/2026-07-19-pass-2.md`](../../hygiene/mobile-protocol-security/2026-07-19-pass-2.md)
  (ephemeral MOB-011 analysis handoff deleted after promotion)
- Follow-on idea: [`I-2026-07-20-mobile-installation-scoped-enrollment-identity`](I-2026-07-20-mobile-installation-scoped-enrollment-identity.md)
- Mobile data model: [`ezkey_mobile/docs/MOBILE_DATA_MODEL.md`](../../../../ezkey_mobile/docs/MOBILE_DATA_MODEL.md)
