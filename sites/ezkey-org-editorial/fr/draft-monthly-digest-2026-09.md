---
status: draft
audience: "Developers and maintainers following the project's month-over-month activity and direction"
planned_slug_en: "monthly-digest-2026-09.html"
planned_slug_fr: "monthly-digest-2026-09.html"
planned_canonical_en: "https://ezkey.org/monthly-digest-2026-09.html"
planned_canonical_fr: "https://ezkey.org/fr/monthly-digest-2026-09.html"
---

<!-- ezkey-org:exclude-start
Monthly activity digest — September 2026.
Source: git log --no-merges origin/main 2026-09-01..2026-10-01, plus open/in-flight PRs
that represent real September work (temporary evaluator console, PAM lab eval,
post-quantum posture map, orphan enrollment note, tenant SQL isolation assessment,
enrollment uniqueness exploration, editorial draft).
Compiled on 2026-09-26.
Editorial posture: product-intention pass — lead with why it matters (posture,
operator benefit, honesty of the public face). Drop internal tokens (TB-*, I-*,
wave/cluster names, sec-finding IDs, tier labels, raw issue/PR numbers, HTTP codes).
Industry tech names (Cloudflare, Bruno, Spring Boot, React Native) are fine.
Body authored in English first (canonical); French mirror at publish time.
ezkey-org:exclude-end -->

<!-- Meta line: "Monthly digest · September 2026" -->
<!-- Eyebrow label: "September 2026 in review" -->

# Public alpha honesty, integrity operators can trust

September opened Ezkey as a public alpha people can try, named the limits without spin, and gave Global Admins a first-class way to watch the integrity of the audit trail.

## In short

Ezkey stopped sounding like an internal lab experiment and started speaking as a public alpha: source under MIT, a community instance on ezkey.online, and plain wording that this is laboratory work — no SLA, no production claim, and no Duo, Okta, or Keycloak equivalence. Much of the month’s energy went into making that honesty stick on the site and in operator tools. Global Admins gained a dedicated Integrity surface so sealed audit material can be inspected without hunting through side paths. Hygiene and dependency triage kept the stack current without becoming the story.

## Documentation & methodology

The team closed the old “operable release” framing and named what is actually live: a discreet public alpha, Android in closed testing only, and evaluator signup off by default. Product writing and ezkey.org dropped leftover lab hostnames in favor of alpha community language, with clear versioning and deploy traceability so readers can see what is running. A craft essay on steering work by intention landed alongside a lighter two-stage plan-hardening pattern for risky changes. Longer-horizon notes in flight map post-quantum protocol posture and how enrollments should behave when identities are reused.

## Code hygiene

Weekly dependency triage continued across the Java stack, Admin UI, mobile, and the SDK. Mobile moved to a newer React Native line; codegen and tooling pins stayed aligned across surfaces. Curated static-analysis and Dependabot habits kept maintenance visible without turning the month into an upgrade log.

## Admin UI

Integrity moved from a side concern to a Global Admin destination: clearer modes, progressive disclosure, and honest copy when an installation runs without full integrity monitoring. Longer sealing and investigation work can run without freezing the console. Tenant Admins see their tenant name in the header. Passwordless login failures no longer imply that a password was wrong. In flight: a temporary evaluator console so someone can explore the Admin UI for a bounded window without enrolling a device first — still gated and off by default.

## Mobile app

React Native advanced; Settings copy was rewritten for end users; Home can group enrollments in a clearer identity grid. Closed-testing builds are labeled public alpha so Play testers are not sold a production story. Still open: what happens when an admin deletes and recreates an enrollment while the phone still holds the old local binding — documented as an alpha edge case, not yet a product fix.

## Backend & API contracts

Least-privilege database grants tightened further; schema auto-update stayed off on running APIs. Passwordless wait sessions were hardened against replay and session abuse. Inactive key rotation and integrity validation fail closed when continuing would quietly weaken the guarantee. Security probing deepened with rate-limit and method-handling fixes. OpenAPI specs were refreshed for the public edge, including Cloudflare Schema Validation packaging for the community host.

## The rest worth mentioning

- **Community ops:** Lightsail bootstrap path, Caddy routing, and Cloudflare schema packaging so ezkey.online can be stood up and kept honest as an alpha community host — not a production promise.
- **Lab-only SSH MFA module:** hygiene and an Amazon Linux builder for internal dogfood; explicitly off the public alpha path.
- **White-box isolation review:** a curated look at how service-layer SQL respects tenant boundaries — findings recorded for later remediations, not silently assumed fixed.
- **SDK & agent tooling:** JavaScript client builds unblocked on current TypeScript; Cloud Agent environments gained a fuller local stack so review work starts closer to reality.

## Where this is heading

August aimed at an operable September. What landed instead is clearer: a public alpha with honest bounds, Integrity operators can inspect, and a community people can try — without pretending the product is finished. Next attention belongs to finishing evaluator access that stays temporary and honest, closing obvious alpha rough edges on mobile, and keeping the public face aligned with what the stack actually does.

<!-- ezkey-org:exclude-start
Index excerpt — draft only; published into monthly-digest.html / RSS, NOT into the detail page HTML.
ezkey-org:exclude-end -->

## Index excerpt (draft only)

Public alpha honesty on ezkey.online, Integrity for Global Admins, and a community people can try — without production or SLA claims.

## Index excerpt FR (draft only)

Alpha publique honn&ecirc;te sur ezkey.online, Int&eacute;grit&eacute; pour les admins globaux, et une communaut&eacute; qu'on peut essayer — sans promesse de production ni de SLA.
