---
status: published
audience: "Developers and maintainers following the project's month-over-month activity and direction"
published_html_en: /monthly-digest-2026-03.html
published_html_fr: /fr/monthly-digest-2026-03.html
published_date: 2026-07-01
html_amended_post_publish: false
source_of_truth: html
planned_slug_en: "monthly-digest-2026-03.html"
planned_slug_fr: "monthly-digest-2026-03.html"
planned_canonical_en: "https://ezkey.org/monthly-digest-2026-03.html"
planned_canonical_fr: "https://ezkey.org/fr/monthly-digest-2026-03.html"
---

<!-- ezkey-org:exclude-start
Monthly activity digest — March 2026.
Source: git log --no-merges 2026-03-01..2026-04-01, distilled by subject and intent.
Index excerpt preserved verbatim from legacy hand-written update (operator request).
Body authored in English first (canonical); French mirror generated from this source.
ezkey-org:exclude-end -->

<!-- Meta line: "Monthly digest · March 2026" -->
<!-- Eyebrow label: "March 2026 in review" -->

# The console takes shape, with mobile and contracts in motion

A distilled look at March — the admin interface nearing feature completeness, the mobile app rewriting, and backend contracts hardening under heavier QA.

## In short

March was an admin console and backend consolidation month. Energy went into paginated operator lists, dashboard widgets, demo mode, audit log integrity tooling, enrollment and admin lifecycle operations, and migrating the console to generated API clients. The Integration API replaced the older machine-to-machine naming; integration signatures moved toward Ed25519. Mobile work continued as a rewrite with Android build improvements. Documentation and branding caught up with the product's cryptographic MFA positioning while QA pressure surfaced more issues to fix before release.

## Documentation & methodology

Written artifacts tracked the product's shift toward cryptographic MFA positioning, with terminology and crypto references cleaned up across docs. Plans landed for load testing, operational churn testing, admin recovery codes, token security, and strategic security reviews. Migration inventories documented the move from hand-written API hooks to Orval-generated clients.

## Code hygiene

The admin console migration to Orval-generated hooks progressed across audit logs, encryption keys, login, and paginated lists. Docker builds gained OpenAPI spec and client generation steps. Partition management and distributed test tagging improved backend hygiene.

## Admin UI

This was the month's dominant thread. Server-side pagination and filtering arrived for tenants, integrations, API keys, enrollments, and re-encryption batches. A dashboard overview with auth health and pending-attempt widgets gave operators a landing view. Demo mode with themed presets supported development and walkthroughs. Contextual help, prev/next detail navigation, expandable related entities, and date-range presets made lists and audit screens more usable. Login recovery, remember-username, and clearer error mapping tightened the operator sign-in path.

## Mobile app

Work continued on the deliberate rewrite — including Android JDK 17 support and general app improvements — while the admin-side product moved faster. The month reads as parallel tracks rather than a finished mobile milestone.

## Backend & API contracts

The machine-to-machine API was renamed and reframed as the Integration API, with contracts, Postman collections, and integration-signed respond flows updated accordingly. Enrollment lifecycle grew substantially: partial metadata updates, expiration and cleanup, bulk deactivation and reactivation, and guardrails when deleting enrollments tied to admin MFA. Admin management, tenant activation, recovery audit trails, and scheduled auth-attempt expiry rounded out operator workflows. Re-encryption gained optimistic locking; rate limiting and trusted-proxy client IP resolution hardened edge behavior. Error responses moved toward ProblemDetail-shaped payloads across several APIs.

## The rest worth mentioning

- **Crypto & security:** Ed25519 replaced ECDSA for integration signatures; encryption key management and rotation documentation expanded.
- **TUI / demo device:** Read-only TUI highlights and demo-device enrollment writing improved test and demo flows.
- **Infrastructure:** Public instance metadata endpoint, churn testing framework hooks, and non-root Docker user setup.

## Where this is heading

March reads as depth before polish — the console's shape became clear while mobile rewrite and release QA still had runway. The natural follow-through is hardening, session security, and Play readiness rather than inventing new surface area.

## Index excerpt (draft only — preserved verbatim on index)

The admin UI is about 90% complete. The mobile app is being rewritten, and the crypto APIs are under review. QA is getting more thorough and surfacing many issues to fix before a release. Progress is steady, and the product is starting to take shape.

## Index excerpt FR (draft only — preserved verbatim on index)

L'interface d'administration est complète à environ 90 %. L'application mobile est en cours de réécriture, et les API cryptographiques sont revues. L'assurance qualité se renforce et met au jour de nombreux points à traiter avant d'envisager une sortie. Les progrès sont réguliers et le produit prend vraiment forme.
