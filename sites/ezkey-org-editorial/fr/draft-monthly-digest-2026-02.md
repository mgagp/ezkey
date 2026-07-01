---
status: published
audience: "Developers and maintainers following the project's month-over-month activity and direction"
published_html_en: /monthly-digest-2026-02.html
published_html_fr: /fr/monthly-digest-2026-02.html
published_date: 2026-07-01
html_amended_post_publish: false
source_of_truth: html
planned_slug_en: "monthly-digest-2026-02.html"
planned_slug_fr: "monthly-digest-2026-02.html"
planned_canonical_en: "https://ezkey.org/monthly-digest-2026-02.html"
planned_canonical_fr: "https://ezkey.org/fr/monthly-digest-2026-02.html"
---

<!-- ezkey-org:exclude-start
Monthly activity digest — February 2026.
Source: git log --no-merges 2026-02-01..2026-03-01, distilled by subject and intent.
Index excerpt preserved verbatim from legacy hand-written update (operator request).
Body authored in English first (canonical); French mirror generated from this source.
ezkey-org:exclude-end -->

<!-- Meta line: "Monthly digest · February 2026" -->
<!-- Eyebrow label: "February 2026 in review" -->

# Construction in earnest — admin console bootstrap, audit integrity, and operator guardrails

A distilled look at February — the first tenant-scoped admin UI pages, HMAC-signed audit chains, and the operator lifecycle guardrails that set the tone for the technology preview.

## In short

February marked the shift from backend-only progress to visible operator-facing construction. Energy split between bootstrapping a tenant-scoped web admin console, hardening the audit trail with HMAC-signed chains and lifecycle sealing, and expanding admin and enrollment lifecycle operations — deactivation, non-blocking re-authentication, OAuth token patterns, and richer tenant identity fields. Spring Boot 4 and PostgreSQL 18 upgrades kept the stack current while the CLI and TUI gained context headers and improved error handling.

## Documentation & methodology

Written rationale clarified why enrollment credentials and access control sit across separate APIs. Planning artifacts outlined device simulation, SDK extraction, admin TUI, and tenant deactivation integrity. Postman environments and API docs tracked the evolving local-development workflow.

## Code hygiene

Spring Boot moved to 4.0.3; PostgreSQL Docker images to version 18. The SDK module entered the Maven reactor with refactored configuration records and a minimal JSON helper. Broad formatting and import cleanups ran through services and tests without changing product direction.

## Admin UI

This was the month's headline. The tenant admin UI project started from scratch — tenants, admin management, audit logs, enrollment detail with a test-authentication action, reusable data tables with sorting and page-size controls, and Docker wiring for local runs. Runtime API key configuration and login integration connected the console to live backends.

## Backend & API contracts

Audit logging gained HMAC integrity with chain checkpoints, sealing, gap declaration, tenant-scoped visibility, integration ID snapshots, and optional reason fields on entries. Admin deactivation arrived with structured error responses. Authentication flows picked up a non-blocking option and OAuth-style access/refresh tokens for the Admin API. Tenant and enrollment models enriched with governance and lifecycle fields; the Crypto API moved to EC P-256 key pairs. Exception handling consolidated around ProblemDetail-shaped validation and constraint errors.

## The rest worth mentioning

- **CLI / TUI:** Context headers showing operator identity, improved CLI/TUI mode handling, and RFC 9457-aware client error parsing.
- **Infrastructure:** Docker Compose gained init configuration volumes; Auth API port standardization to 8085.

## Where this is heading

February reads as foundation-laying — the admin console existed as a real surface, audit integrity became a first-class concern, and operator lifecycle guardrails started to appear. March's natural follow-through was depth: pagination, dashboards, and contract hardening across the console.

## Index excerpt (draft only — preserved verbatim on index)

Building an authentication system is challenging. Making it safe and reliable is harder. Ezkey will ship in phases; the first is due in the coming months. It will be a technology preview: it will show what the system can do, but it will **not** be production-ready. On the activity side: a web Admin UI began taking shape this month — initially scoped to tenant administration — alongside audit log integrity through HMAC-signed chains, admin deactivation, and a non-blocking authentication option. The construction had begun in earnest.

## Index excerpt FR (draft only — preserved verbatim on index)

Construire un système d'authentification, c'est exigeant. Le rendre sûr et fiable, c'est encore plus dur. Ezkey sera publié par phases ; la première est prévue dans les prochains mois. Ce sera une préversion technologique : elle montrera le potentiel du système mais ne sera **pas** prête pour la production. Côté activités : une interface d'administration web a commencé à prendre forme ce mois-ci — d'abord centrée sur l'administration de locataires — accompagnée d'une intégrité des logs d'audit par chaîne HMAC signée, de la désactivation d'administrateurs et d'une option d'authentification non bloquante. La construction avait vraiment commencé.
