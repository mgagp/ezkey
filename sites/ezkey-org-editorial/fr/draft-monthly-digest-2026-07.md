---
published_html_en: /monthly-digest-2026-07.html
published_html_fr: /fr/monthly-digest-2026-07.html
published_date: 2026-07-31
html_amended_post_publish: false
source_of_truth: html
status: published
audience: "Developers and maintainers following the project's month-over-month activity and direction"
planned_slug_en: "monthly-digest-2026-07.html"
planned_slug_fr: "monthly-digest-2026-07.html"
planned_canonical_en: "https://ezkey.org/monthly-digest-2026-07.html"
planned_canonical_fr: "https://ezkey.org/fr/monthly-digest-2026-07.html"
---

<!-- ezkey-org:exclude-start
Monthly activity digest — July 2026.
Source: git log --no-merges 2026-07-01..2026-08-01, distilled by subject and intent.
Editorial posture: summarize the nature and intent of the work, not internal tracking
notations. Internal codes (tier labels, wave/cluster codenames, security-finding IDs,
issue/PR numbers, HTTP status codes) are translated into plain meaning or dropped.
Body authored in English first (canonical); French mirror generated from this source.

Editorial note (not for HTML): light nuance only in "Where this is heading" — documentation
consolidation may grow larger; a fuller essay may follow. Inspiration (not named in HTML):
Boris Cherny / Claude Code ablation talk on YC Startup School.
ezkey-org:exclude-end -->

<!-- Meta line: "Monthly digest · July 2026" -->
<!-- Eyebrow label: "July 2026 in review" -->

# Hardening, hygiene systems, and documentation under review

A distilled look at July: security closed in volume, maintenance turned into lanes, and the written corpus began a quieter consolidation.

## In short

July was intense and security-heavy. A structured challenge closed many concrete weaknesses across recovery, authorization, actuators, crypto paths, and fail-closed posture. In parallel, routine hygiene became institutionalized — dedicated curated passes for Java, the admin console, mobile, and dependency triage — so maintenance is no longer a burst habit. Documentation volume was high, but much of it was consolidation: retiring stale notes, promoting durable decisions, and tightening what is loaded by default. That review has only started; it may take a larger share of attention ahead.

## Documentation & methodology

The working method reached another published revision. Cold-start guidance was tiered around product intent so less material is loaded by default. Ephemeral plans, completed handoffs, and a distinct method-log artifact type were retired or rematerialized into canon; durable decisions were extracted from aging notes. Assessment lanes for deep security reviews took shape, and a public essay on continuous code hygiene was published. The intent of the consolidation work is corpus health — less duplication and scatter — not a thicker methodological stack.

## Code hygiene

Hygiene stopped being ad hoc. The month institutionalized curated passes — static analysis shortlists with human triage for Java and the admin console, a sibling mobile pipeline, and a weekly Dependabot triage habit with risk tiers and closeout evidence. Dependency and toolchain bumps continued across Maven, Admin UI, and mobile, alongside cleanup of obsolete native-image surface and adaptations to newer Checkstyle and Jackson APIs. The public continuous-hygiene essay matches the practice: small lots, explicit decisions, no zero-warning campaigns.

## Admin UI

Operator-facing work continued on audit integrity investigation: clearer summaries, timeline polish, and verification flows that distinguish checking the trail from running a broader validation. Contextual help was rewritten as a coherent corpus with role-aware rendering. Smaller quality passes improved dialog accessibility (portal rendering), dashboard enrollment status clarity, alerts list readability, and deep-link hygiene from the curated React pass.

## Mobile app

Mobile security and platform hardening dominated. Device cryptography and enrollment storage were tightened (Keystore lifecycle, installation-scoped secrets, fail-closed pending responses, production-clean release gates). The app now refuses unsupported Android versions and adopts Android 12+ as the support floor. Stack maintenance continued (camera, navigation, Orval), language switching no longer requires a restart, and a mobile doctor-curated hygiene pipeline landed alongside stronger real-device test harnesses.

## Backend & API contracts

Security and integrity were the headline. Beyond the challenge closures, work completed more of the audit-trail verification story for operators, sealed peripheral audit inserts more carefully, encrypted sensitive API-key material at rest, and rejected weak ECDSA signatures. Re-encryption discovery moved to indexed key identifiers, keyset access was modernized, and PostgreSQL roles were split so migration credentials are not what the APIs run as day to day. Fail-fast posture for required encryption and trusted-proxy configuration was strengthened for Docker and evaluation deployments.

## The rest worth mentioning

- **Public site:** continuous code-hygiene essay published; methodology explorer kept in step with the method revision.
- **Demo device & labs:** signature parity fixes and session rehydration for claimed pending auth; operational diagnostic scripts for the evaluation environment.
- **Infrastructure:** Lightsail image sync helper for host mounts; pentest-curated runner bootstrap.
- **SDK:** patch-level client and generator dependency hygiene.

## Where this is heading

September's operable-release target remains the horizon. The durable habits of July — security challenge discipline, curated hygiene lanes, integrity that operators can actually investigate — should continue as rhythm, not spectacle. One nuance: the documentation consolidation begun this month may grow into a larger share of the work, as frontier models make heavier corrective scaffolding less necessary and more of a drag — a thread that may deserve its own essay later.

## Index excerpt (draft only)

Security closed in volume, hygiene turned into lanes, and documentation consolidation began — with more of that review likely ahead.

## Index excerpt FR (draft only)

La s&eacute;curit&eacute; a &eacute;t&eacute; ferm&eacute;e en volume, l'hygi&egrave;ne est devenue des voies r&eacute;guli&egrave;res, et la consolidation documentaire a commenc&eacute; — avec probablement une plus grande place &agrave; venir.
