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

# Subtraction, adversarial passes, and a narrower blast radius

A distilled look at August: the written corpus was cut hard, security testing turned adversarial, and the services lost privileges they never needed.

## In short

August was a subtraction month. The methodology corpus was condensed from dozens of workflow documents, dated decisions, skills, and templates into a single core document plus four templates, and two dedicated pruning lanes kept burning down stale plans and reports behind it. Security work moved from reasoning to probing: three scanner-driven passes with human triage, alongside a deliberate narrowing of which service may authenticate machines or write encryption keys. Mobile gained a real test pyramid and a real-device campaign runner. The admin console stayed comparatively quiet, mostly absorbing dependency upgrades.

## Documentation & methodology

The dominant theme was deletion, not accumulation. The methodology corpus was reduced to one core document and four templates, on the explicit principle of removing first and then observing what a cold agent actually needs — git history is the archive, so nothing was relocated into an archive folder. Every coupling point that referenced the removed material was rewired in the same move, and the public methodology explorer was republished on top of the reduced corpus. Two curated lanes were created to continue the work: one pruning living planning prompts and plans, another for written documents, both biased toward deleting or folding content when a canonical source already carries the signal. Separately, SOC 2 language was stripped from living code and documentation and collapsed into a plain statement of posture, so the project does not imply attestation it does not have.

## Code hygiene

Weekly dependency triage continued at a steady rhythm across the Java stack, the admin console, the SDK, and mobile — including a Spring Boot point upgrade, and version pins for shared libraries centralized in the parent build file rather than scattered across modules. A rule banning broad exception catching was activated and then remediated module by module, with tests added to lock the error contracts at those boundaries so the cleanup cannot silently regress. Transaction boundaries were reviewed and reduced to the places where they actually apply. Windows-only script wrappers were purged in favour of a single portable shell contract.

## Admin UI

A quieter month, mostly maintenance. Key rotation was relabelled as pending introduction so operators are not shown a capability that is not there yet, shard numbering became human-readable, and the estimated completion time for a drained key was corrected. One fix keeps an audit integrity investigation from being reopened after the operator leaves it. The rest was dependency floors, upgrade pinning, and clearing high-severity advisories.

## Mobile app

Testing was the centre of gravity. The app now has an explicit layered test contract, with coverage on the critical enrollment and verification paths, on the isolation of installation-scoped cryptography, and on the bind, verify, and pending-response workflows — so protocol regressions fail in continuous integration instead of on a device. A campaign runner drives real-device runs. On the product side, experimental messaging was retired and flexible in-app updates were adopted for the store channel; error handling now only interprets structured API errors that Ezkey actually emits. Stack maintenance continued across React Native, camera, and navigation, and the first curated mobile static-analysis pass was applied.

## Backend & API contracts

The theme was reducing what each service is allowed to do. Machine-to-machine credentials are now accepted by the integration service only; the admin service refuses them outright, first behind a configuration flag and then by removal. In the same spirit, only the admin service may materialize the encryption keyset — the peripheral services lost write access at the database level, which also removes a startup failure mode where a read-only role could kill the boot sequence. Enrollment material belonging to another tenant no longer reveals its own existence. Deeper in the crypto layer, the database key blob moved to a proper keyset envelope, and several re-encryption defects were fixed around shard counting, worker scheduling, and completion estimates. The mobile client can now receive a signed description of the instance it is enrolled with, and a host-neutral API description was packaged so the edge can validate request shapes.

## The rest worth mentioning

- **Security testing:** three scanner-driven passes with recorded human decisions — an unauthenticated baseline, a first-party vulnerability scanner wired through containers with regression checks on proxy headers, then an API-description-driven scan that led to hiding the interactive API documentation behind the public proxy. One of those runs surfaced a real server error on a missing request field, now rejected properly.
- **Tooling:** the exploratory API collections left Postman for a git-native equivalent, with command-line health suites so operators and agents no longer depend on a hybrid sync model.
- **Edge:** scripts to package, upload, inventory, and delete API schemas on the evaluation environment's edge protection, for both the auth and integration surfaces.
- **Observability:** an opt-in metrics collector for the local container stack, plus a small routine for extracting and ranking what it captures after known workloads.
- **Infrastructure:** high-availability stack parity restored, an unused sidecar dropped from the clean-start path, and a demo device health check repaired.
- **Public site:** an article on the ablation published in both locales.

## Where this is heading

Last month's closing note guessed that documentation consolidation would claim a larger share of the work. It did, and it went further than consolidation — the answer turned out to be removal rather than reorganization. With the September operable-release target now immediate, the balance should shift back toward the release surface itself: mobile store readiness, the last of the privilege-narrowing work, and the operator paths that need to hold on day one.

<!-- ezkey-org:exclude-start
Index excerpt — draft only; published into monthly-digest.html / RSS, NOT into the detail page HTML.
ezkey-org:exclude-end -->

## Index excerpt (draft only)

A hard cut to the written corpus, three scanner-driven security passes, a narrower blast radius for machine credentials and encryption keys, and a real test pyramid for mobile.

## Index excerpt FR (draft only)

Une coupe franche dans le corpus &eacute;crit, trois passes de s&eacute;curit&eacute; outill&eacute;es, un p&eacute;rim&egrave;tre resserr&eacute; pour les identifiants machine et les cl&eacute;s de chiffrement, et une vraie pyramide de tests pour le mobile.
