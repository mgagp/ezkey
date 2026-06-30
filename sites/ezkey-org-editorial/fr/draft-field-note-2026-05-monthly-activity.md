---
published_html_en: /field-note-2026-05-monthly-activity.html
published_html_fr: /fr/field-note-2026-05-monthly-activity.html
published_date: 2026-06-30
html_amended_post_publish: false
source_of_truth: html
status: published
audience: "Developers and maintainers following the project's month-over-month activity and direction"
planned_slug_en: "field-note-2026-05-monthly-activity.html"
planned_slug_fr: "field-note-2026-05-monthly-activity.html"
planned_canonical_en: "https://ezkey.org/field-note-2026-05-monthly-activity.html"
planned_canonical_fr: "https://ezkey.org/fr/field-note-2026-05-monthly-activity.html"
---

<!-- ezkey-org:exclude-start
Monthly activity digest — May 2026.
Source: git log --no-merges 2026-05-01..2026-06-01, distilled by subject and intent.
Body authored in English first (canonical); French mirror generated from this source.
ezkey-org:exclude-end -->

<!-- Meta line: "Monthly field note · May 2026" -->
<!-- Eyebrow label: "May 2026 in review" -->

# Back to foundations, with the public face taking shape

A distilled look at a consolidation month — method and specification upfront, the mobile app ready for testers, and ezkey.org gaining its API portal and methodology explorer.

## In short

May deliberately traded feature velocity for foundations. The dominant energy went into making the project's working method explicit and publishable — skills, enriched views, a downloadable pack, and clearer boundaries between methodology and delivery artifacts. In parallel, the public site gained its API portal, methodology explorer, and evaluator paths for the experimental preview, while the mobile app crossed into internal testing with bilingual support and tighter security. A steady undercurrent of dependency maintenance ran throughout, including a move to Spring Boot 4.0 and the Admin UI's Vite 8 upgrade.

## Documentation & methodology

The working method matured from informal practice into something versionable and explorable: minimum viable method, workflow skills, plan incubation, GitHub issue discipline, and design judgment principles all landed in the same month. The methodology gained a public enriched view with a navigation wizard, dark mode, and offline download — together with an explicit publication policy separating what ships as method product from internal delivery records. Backlog and component documentation grew in parallel, and terminology was tightened (milestone language replacing phase tags, clearer rate-limit and testing posture).

## Code hygiene

Dependency upkeep was continuous rather than episodic: Dependabot was configured project-wide, Spring Boot moved to 4.0.6, and the Admin UI toolchain jumped to Vite 8 with a long chain of Orval upgrades. A React Doctor pass targeted lint and polish in the admin console. Mobile CI aligned on Yarn 4 via Corepack, and license compliance scripts improved.

## Admin UI

Beyond toolchain upgrades, the console gained audit log timestamps suited to incident correlation and a lint cleanup pass that tightened context usage. The work was preparatory rather than sweeping — the heavy operator-list enrichment would come the following month.

## Mobile app

This was the month's strongest product thread. The app reached internal Android testing — a complete enrollment-to-authentication flow for outside testers — and shipped bilingual English/French UI. Security work included sealed Android storage for long-lived enrollment values and a dedicated security preferences screen. Operator-facing flows improved: enrollment wizard cleanup, last-activity tracking, inline authentication feedback, reusable PIN input, and refactored hooks for pending auth and enrollment. Late May opened a deliberate stack modernization track (React Native 0.85, ESLint 9, CI validation gates).

## Backend & API contracts

Backend changes supported operator and mobile workflows: an enrollment creation API, activation-code reissue for pending admins, and admin management with tenant context. Audit logging was sharpened for correlation. Spring Boot 4.0.6 landed across the stack. Public API reference pages on ezkey.org were finalized alongside internal contract documentation.

## The rest worth mentioning

- **Public site:** API portal, guides hub, experimental preview signup and guided tour, several new essays, site navigation and visual identity refinements.
- **Infrastructure:** Cloudflare preview cleanup automation; Docker and Vite build improvements for methodology publishing.
- **SDK:** Generated clients kept current with Orval bumps.
- **Tooling:** A unified documentation system was established at repository level.

## Where this is heading

May reads as an intentional pivot — investing in explicit design and method before pushing more surface area. The mobile app is now in testers' hands; the public site can explain APIs and methodology; the internal method is no longer tribal knowledge. June's natural follow-through is making that foundation operational day to day (hygiene rhythm, richer operator views) rather than inventing new lanes.
