---
status: published
audience: "Developers and maintainers following the project's month-over-month activity and direction"
published_html_en: /monthly-digest-2026-04.html
published_html_fr: /fr/monthly-digest-2026-04.html
published_date: 2026-06-30
html_amended_post_publish: false
source_of_truth: html
planned_slug_en: "monthly-digest-2026-04.html"
planned_slug_fr: "monthly-digest-2026-04.html"
planned_canonical_en: "https://ezkey.org/monthly-digest-2026-04.html"
planned_canonical_fr: "https://ezkey.org/fr/monthly-digest-2026-04.html"
---

<!-- ezkey-org:exclude-start
Monthly activity digest — April 2026.
Source: git log --no-merges 2026-04-01..2026-05-01, distilled by subject and intent.
Index excerpt preserved verbatim from legacy hand-written update (operator request).
Body authored in English first (canonical); French mirror generated from this source.
ezkey-org:exclude-end -->

<!-- Meta line: "Monthly digest · April 2026" -->
<!-- Eyebrow label: "April 2026 in review" -->

# Play submission in sight, with hardening across the stack

A distilled look at April — the mobile app moving toward Google Play, operator console security tightening, and lifecycle governance landing across backend and docs.

## In short

April converged on making Ezkey credible as a technology preview. The mobile app advanced through pre-release planning, production security hardening, and package cleanup on the path to Google Play submission. The admin console gained HttpOnly sessions, CSRF protection, dashboard widgets with audit drill-downs, and its first browser-based test suite. Backend work deepened lifecycle governance, standardized error responses, re-encryption and recovery flows, and audit log operability. The public site published several guides and essays while deployment and preview workflows matured.

## Documentation & methodology

Lifecycle governance became a first-class reference — how entities relate, when operations block, and how admin identity differs from enrollment. Supporting docs covered SQL injection posture, mobile Auth API client strategy, JPA transaction design, and audit reason semantics. Several long-form articles and evaluator guides landed on ezkey.org, including testing strategy and mobile signing walkthroughs.

## Code hygiene

Dependency upkeep continued across backend, mobile, and tooling. A Docker-only build validation path appeared, preview deployment cleanup was automated, and local Maven configuration was made explicitly workstation-specific. Mobile CI and debug build documentation were tightened after stack upgrades.

## Admin UI

Operator-facing security moved forward: sessions carried by HttpOnly cookies, mutating requests protected by CSRF tokens, and session expiry aligned with the authentication cycle. Dashboard widgets surfaced enrollment aggregates with links into the audit trail. Tenant timezone handling arrived in the console and API. Error messages gained an internationalization strategy, audit log screens simplified gap declaration, and Playwright browser tests were introduced for regression coverage.

## Mobile app

This was the month's defining product thread. Work focused on pre-release readiness — production security hardening for Android, privacy compliance planning, namespace cleanup, and enrollment flows refined for canonical payloads, device key storage tiers, and Ed25519 signing. The app gained QR-based enrollment import for demo flows, improved navigation and theming, and clearer handling of pending authentication. Policy surfacing experiments came and went as the UX was simplified ahead of submission.

## Backend & API contracts

Core security was extracted into a dedicated module. Entity eligibility checks, integration retirement, recovery-code regeneration, and bootstrap recovery-first mode made lifecycle rules enforceable in code. Re-encryption gained parallel processing and clearer key semantics. APIs moved toward ProblemDetail-shaped errors with a shared catalog. Audit logging expanded — event families, auth-attempt expiry records, contextual navigation around anchor events, and operational heartbeat incident handling. Auth API contracts and Postman collections were refactored for session cookies, instance metadata, and enrollment alignment.

## The rest worth mentioning

- **Public site:** privacy policy pages, homepage security copy refresh, Cloudflare static-site restructure, and new craft essays on AI-assisted development.
- **Infrastructure & tests:** experimental hybrid deployment scripts, CORS configuration for the Admin API, and API client unit tests.
- **SDK:** kept in step with contract refactors where applicable.

## Where this is heading

April reads as a pre-preview consolidation — less about new lanes, more about making the existing stack defensible for outside eyes. The natural follow-through is publishing the method and public API story more deliberately while keeping the mobile submission and hardening tracks on rhythm.

## Index excerpt (draft only — preserved verbatim on index)

The Ezkey Android app is being submitted to Google Play as a technology preview for early adopters. The source code is currently kept private while we finalize hardening and documentation. The current plan is to open the repository publicly in September 2026, with the first official release, subject to the usual pre-release work.

## Index excerpt FR (draft only — preserved verbatim on index)

L'application Android Ezkey est soumise à Google Play comme préversion technologique destinée aux premiers adoptants. Le code source est pour l'instant privé, le temps de finaliser le durcissement et la documentation. L'objectif actuel est d'ouvrir le dépôt publiquement en septembre 2026, avec la première version officielle, sous réserve du travail de préversion habituel.
