---
status: published
audience: "Developers and maintainers following the project's month-over-month activity and direction"
published_html_en: /monthly-digest-2026-01.html
published_html_fr: /fr/monthly-digest-2026-01.html
published_date: 2026-07-01
html_amended_post_publish: false
source_of_truth: html
planned_slug_en: "monthly-digest-2026-01.html"
planned_slug_fr: "monthly-digest-2026-01.html"
planned_canonical_en: "https://ezkey.org/monthly-digest-2026-01.html"
planned_canonical_fr: "https://ezkey.org/fr/monthly-digest-2026-01.html"
---

<!-- ezkey-org:exclude-start
Monthly activity digest — January 2026.
Source: git log --no-merges 2026-01-01..2026-02-01, distilled by subject and intent.
PHASE 1 ONLY: operator review before publication. No legacy index paragraph exists for this month.
Operator editorial notes (2026-07-01):
- TUI was the intended admin tool, not a provisional "inspect before paint" layer; web stack (React, etc.) felt premature to take on.
- Docs consolidation = pragmatic short-term pass after frenetic multi-session plan output; not a methodological pause.
Cross-reference: painting-an-admin-ui-with-ai.html § "From a Python TUI to a React admin UI".
ezkey-org:exclude-end -->

<!-- Meta line: "Monthly digest · January 2026" -->
<!-- Eyebrow label: "January 2026 in review" -->

# Admin by terminal first — the TUI choice, doc consolidation, and platform groundwork

A distilled look at January — a text-based admin console built as the real operator surface, a pragmatic documentation consolidation after a frenetic build rhythm, and JDK 25 / Spring Boot 4 groundwork across the stack.

## In short

January was a platform month centered on how to administer Ezkey without committing to a full web UI stack yet. The dominant thread was an interactive Python TUI — tenants, integrations, API keys, auth attempts, audit logs, encryption keys, re-encryption batches — built as the intended administration tool, not as a temporary inspection layer before a graphical console. The need for an operator interface was clear; a classic web stack with React and its ecosystem did not feel ready to assume. In parallel, the stack moved on JDK 25, Spring Boot 4 migration prep, dependency hygiene, and CI-friendly Maven versioning. The Crypto API gained encrypt/decrypt endpoints; auth flows matured with challenge codes, attempt cancellation, and clearer reject/expired semantics. Docker bootstrap, health checks, and a demo ACME application made the running stack easier to stand up. Documentation saw a large consolidation pass — after a frenetic rhythm of sequential plans, sometimes two or three sessions at once, had produced a sprawling corpus — a short-term pragmatic tidy rather than stopping delivery for a full methodological reset. Functional and multi-tenant tests expanded. The month reads as choosing the terminal as admin, hardening the platform, and clearing doc debt before February would open the graphical console path.

## Documentation & methodology

A broad consolidation removed obsolete plans and merged development, token, and testing guides — not housekeeping for its own sake, but a pragmatic response to a document corpus that had grown fast and messy under parallel build sessions. New written artifacts still landed: database partitioning strategy, keyset synchronization mechanism analysis, CLI functional testing strategy, and Spring profiles configuration. Postman collections tracked the emerging Crypto API. Methodology gains were deferred; the pass was meant to restore clarity without pausing momentum.

## Code hygiene

JDK 25 migration landed across services and Docker base images. Spring Boot 4 migration work began with error-handling and configuration updates. Dependency versions moved for PMD, ZXing, Bucket4j, Maven plugins, and RestAssured. CI-friendly versioning entered the Maven POMs. Checkstyle was adjusted for Google Java Format compatibility. THIRD-PARTY.txt was refreshed.

## Admin UI

No graphical admin UI this month. Administration was meant to happen through the text TUI — the first operator surface, and at the time the chosen one. A full web UI stack still felt like too large a bet while backend depth, crypto, and operability work remained the priority. Retrospectively, the limits of a text console would push toward a React admin later in the year; in January, the TUI was the plan, not a stepping stone.

## Mobile app

Quiet in January based on Git activity for this window.

## Backend & API contracts

Auth attempts gained cancellation, challenge-code support, DTO/mapper separation between Admin and Auth APIs, and fixes for reject-versus-expired confusion on the wait path. The Crypto API added encrypt and decrypt endpoints with updated Postman coverage. Enrollment uniqueness constraints for verified enrollments landed with plans and tests. Tenant management endpoints and OpenAPI specs were refreshed. Bootstrap credentials export and profile-specific encryption configuration strengthened first-run and key-storage behavior.

## The rest worth mentioning

- **CLI / TUI:** The month's headline — an interactive admin TUI with screens for integrations (with filter modal), audit logs, auth attempts, API keys, crypto validation, encryption keys, re-encryption batches, and tenant management; mouse support when available; pagination utilities; CLI tests and Docker entrypoint wiring; admin onboarding and creation modals toward month-end.
- **Demo & Docker:** Demo ACME application structure with backend authentication; Docker and Windows configuration files; PowerShell stack scripts; improved health checks and management ports.
- **Testing:** Multi-tenant isolation tests, functional testing documentation and resilience improvements, enrollment uniqueness integration coverage.

## Where this is heading

January bet on the terminal as the admin console. Platform and contract work kept pace underneath. The graphical admin path — first scoped as tenant UI, later unified — would open in February once the text surface had proven the operating model but also its ceiling for non-developer operators.

<!-- ezkey-org:exclude-start
Index excerpt — PROPOSED (no legacy paragraph for January; revised after operator editorial input).
ezkey-org:exclude-end -->

## Index excerpt (draft only — proposed, no legacy source)

The admin console started as a text TUI — the chosen operator surface, not a web UI bet yet — alongside JDK 25 groundwork, Crypto API endpoints, and a pragmatic documentation consolidation.

## Index excerpt FR (draft only — proposed, no legacy source)

La console admin a démarré comme un TUI texte — la surface opérateur retenue, pas encore un pari UI web — avec préparation JDK 25, endpoints Crypto API, et une consolidation documentaire pragmatique.
