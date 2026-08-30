---
published_html_en: /monthly-digest-2026-08.html
published_html_fr: /fr/monthly-digest-2026-08.html
published_date: 2026-08-30
html_amended_post_publish: false
source_of_truth: html
status: published
audience: "Developers and maintainers following the project's month-over-month activity and direction"
planned_slug_en: "monthly-digest-2026-08.html"
planned_slug_fr: "monthly-digest-2026-08.html"
planned_canonical_en: "https://ezkey.org/monthly-digest-2026-08.html"
planned_canonical_fr: "https://ezkey.org/fr/monthly-digest-2026-08.html"
---

<!-- ezkey-org:exclude-start
Monthly activity digest — August 2026.
Source: git log --no-merges origin/main 2026-08-01..2026-09-01, distilled by subject and intent.
Compiled on 2026-08-30, so the last day or two of the month may not be reflected.
Editorial posture: summarize the nature and intent of the work, not internal tracking
notations. Internal codes (tier labels, lane codenames, security-finding IDs, issue/PR
numbers, HTTP status codes) are translated into plain meaning or dropped.
Body authored in English first (canonical); French mirror generated at publish time.

Footprint read (qualitative, overlapping counts): product-docs 67, docs 42, admin-ui 30,
mobile 28, admin-api 26, core 24, .cursor 22, auth-api 14, scripts 13, sdk 12, tests 12,
.github 12, bruno 10, docker 10, site 9.

Editorial note (not for HTML): July's closing line predicted documentation consolidation
would grow. August went past consolidation into deletion — worth naming explicitly as a
follow-through, without turning the digest into an essay.
ezkey-org:exclude-end -->

<!-- Meta line: "Monthly digest · August 2026" -->
<!-- Eyebrow label: "August 2026 in review" -->

# Radical ablation, adversarial testing, and strict least privilege

A distilled look at August: the written corpus was cut hard, security testing turned adversarial, and services surrendered unnecessary privileges.

## In short

August was defined by simplification and privilege reduction. The methodology corpus was condensed from dozens of workflow documents, skills, and templates into a single core guide plus four templates, supported by ongoing pruning of living plans. Security transitioned from theoretical reasoning to active probing through three scanner-driven passes with human triage, while machine credentials and encryption key materialization were locked down to dedicated services. Mobile established a layered test pyramid and a real-device test harness. Meanwhile, the admin console remained quiet, focusing on dependency maintenance and clearer operator indicators.

## Documentation & methodology

The dominant priority was deliberate deletion. The methodology corpus was reduced to one core document and four templates on the principle of removing context first and observing what an AI agent actually needs—relying on Git history as the sole archive. Every coupling point was rewired simultaneously, and the public explorer was republished on the slimmed-down foundation. Two curated pruning lanes now continuously retire stale planning prompts and non-canonical documents. In the same spirit of precision, aspirational planning language around SOC 2 audit readiness was reframed into a plain, grounded statement of actual operational discipline—avoiding any suggestion of a formal certification roadmap while upholding honest security claims.

## Code hygiene

Weekly dependency triage maintained a steady rhythm across the Java stack, admin console, SDK, and mobile, incorporating a Spring Boot point release and centralizing shared library versions in the parent build configuration. To improve failure transparency, broad exception catching was prohibited and systematically remediated across modules, backed by dedicated tests to permanently lock error handling boundaries. Transaction boundaries were audited and restricted strictly to state-mutating operations, and Windows-specific script wrappers were eliminated in favor of a single, portable Bash contract.

## Admin UI

A quieter maintenance period centered on operational transparency. Key rotation UI feedback was refined to accurately represent its asynchronous background execution (replacing premature completion indicators with active progress states while the operation finalizes), shard counters were switched to human-readable indices, and the completion estimate for key draining was corrected. An audit integrity workflow fix now prevents closed investigation views from inadvertently reopening. The remaining effort addressed baseline dependency updates, version pinning, and resolving high-severity security advisories.

## Mobile app

Reliability and protocol integrity took center stage. The mobile application introduced an explicit layered test contract covering critical enrollment, verification, and installation-scoped key isolation—ensuring cryptographic protocol regressions are caught in continuous integration rather than on physical devices. A dedicated campaign runner now automates validation against real hardware. On the user experience side, experimental messaging was retired, in-app updates were enabled for the app store channel, and client-side error handling was tightened to interpret only structured API errors explicitly emitted by Ezkey. Routine platform upgrades continued across React Native, camera, and navigation libraries, alongside the first curated mobile static analysis pass.

## Backend & API contracts

The core focus was strict privilege separation across architectural boundaries. Machine-to-machine (M2M) API keys are now authenticated exclusively by the Integration API; the Admin API rejects them outright, removing a legacy administrative bypass. Similarly, database-level write permissions for encryption keysets were restricted solely to the Admin service, eliminating a startup deadlock where a read-only role could abort the boot sequence. Tenant data isolation was hardened so cross-tenant queries never leak enrollment existence. In the cryptographic tier, key storage transitioned to a standardized envelope format, and re-encryption mechanics were corrected across shard calculations and worker scheduling. Lastly, mobile devices can now fetch signed instance metadata, and OpenAPI schemas were packaged for automated Cloudflare Schema Validation on the edge.

## The rest worth mentioning

- **Security testing:** three scanner-driven passes with logged human decisions—an unauthenticated surface baseline, containerized vulnerability scans verifying reverse proxy headers, and schema-driven fuzzing that led to hiding interactive API docs behind the edge proxy. One finding resolved an unhandled server error on missing request fields.
- **API testing & edge validation:** exploratory API collections migrated from Postman to Git-native Bruno collections with CLI test suites, paired with automated packaging and deployment of OpenAPI schemas to Cloudflare Schema Validation rules.
- **Observability & performance:** an opt-in metrics collector for local container environments, accompanied by an analysis routine to profile resource consumption after realistic workloads.
- **Infrastructure & public site:** restored multi-node high-availability parity, streamlined clean-start scripts, and published a bilingual technical article detailing the methodology ablation.

## Where this is heading

July's digest anticipated that documentation consolidation would require significant attention. August went a step further, demonstrating that radical simplification delivers far higher clarity than mere reorganization. With the September operable-release target immediately ahead, focus now pivots entirely to the runtime surface: finalizing mobile app store readiness, completing the remaining privilege restrictions, and verifying that day-one operational paths perform reliably in production.

<!-- ezkey-org:exclude-start
Index excerpt — draft only; published into monthly-digest.html / RSS, NOT into the detail page HTML.
ezkey-org:exclude-end -->

## Index excerpt (draft only)

Radical simplification of the written corpus, three scanner-driven security passes, strict least-privilege boundaries for machine credentials and encryption keys, and a layered test pyramid for mobile.

## Index excerpt FR (draft only)

&Eacute;puration radicale du corpus &eacute;crit, trois passes de s&eacute;curit&eacute; outill&eacute;es, cloisonnement strict des identifiants machine et des cl&eacute;s de chiffrement, et une v&eacute;ritable pyramide de tests pour le mobile.
