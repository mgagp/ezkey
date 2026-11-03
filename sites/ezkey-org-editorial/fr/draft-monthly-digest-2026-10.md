---
status: draft
audience: "Developers and maintainers following the project's month-over-month activity and direction"
planned_slug_en: "monthly-digest-2026-10.html"
planned_slug_fr: "monthly-digest-2026-10.html"
planned_canonical_en: "https://ezkey.org/monthly-digest-2026-10.html"
planned_canonical_fr: "https://ezkey.org/fr/monthly-digest-2026-10.html"
---

<!-- ezkey-org:exclude-start
Source: git log --no-merges on origin/main for [2026-10-01, 2026-11-01): 302 commits, of which 120 Dependabot.
Path footprint (overlapping): product-docs 88, Admin UI 86, mobile 68, docs 63, .github 49, scripts 33,
backend modules ~75 combined, ezkey-pam 14, sites 14, SDK 11, demo device 11.
Product-intention pass (SKILL.md § Product-intention pass): the month served two intentions —
make the public alpha safer to try, and get the mobile app ready for outside testers.
ezkey-org:exclude-end -->

<!-- Meta line: "Monthly digest · October 2026" -->
<!-- Eyebrow label: "October 2026 in review" -->

# A safer alpha, ready for its first testers

October made the public alpha harder to misuse and easier to verify, and prepared the ground for people outside the project to try it.

## In short

The month served two intentions. The first was to make the alpha safer to try: most of the energy went into security work that a visitor never sees but would want — nothing sensitive in logs, code scanning as a routine, encryption keys kept in one place that fails closed. Every pull request now runs automated checks. The second was to get the mobile app ready for outside testers, through Google Play's internal track and a demo app they can sign in to without an account. On the side, a lab experiment let AI agents harden an SSH login module.

## Documentation & methodology

The security policy was rewritten to say only what is true for a lab alpha, with private vulnerability reporting through GitHub. Working conventions — what "ready to merge" means, who reviews what — were written down, along with an adversarial review step for risky plans. A new project value asks that information sit where a person is already looking, rather than being repeated on another screen. Several ideas were framed for later: an in-app feedback channel, announcements from Global Admins, and a multi-tenant demo app for testers.

## Code hygiene

Pull requests gained a single automated gate covering the Admin UI, the Java backend and mobile, plus an on-demand quality gate that runs the full suite against a highly available stack. Dependency updates moved to short daily passes, then their volume was reduced by grouping updates and merging safe patches automatically. Platform upgrades (Node, Java, databases, base images) now sit in a register with their end-of-life dates.

## Admin UI

Integrity became easier to read at a glance for Global Admins. Quiet periods with nothing to report collapse instead of filling the screen, and the last verdict shows up front with a small dashboard. Long verifications run in the background, and the screen catches up reliably when they finish.

## Mobile app

The Android build was stabilised for Google Play's current requirements, and builds now reach the internal testing track with readable release names. The app moved to a newer React Native line. Its expected behaviour was written down as a contract, together with a short guide for welcoming outside testers.

## Backend & API contracts

Every API now cleans text supplied by callers before writing it to a log, and recovery codes, login challenges and credentials stay out of logs entirely. Encryption keys, managed with Google Tink, are now stored only in the database, rotated safely even under concurrent writes, and refuse to decrypt rather than guess when something is wrong. Integrators can set how long an approval request stays open and get a clear "expired" outcome. A device response with an invalid signature is now rejected without changing anything.

## The rest worth mentioning

- **SSH login lab:** AI agents reviewed and hardened an experimental Linux login module through about ten versions; the experiment became the first Lab Paper, still a draft, in a new Lab Papers collection.
- **Community instance:** ezkey.online gained performance monitoring reachable only through a private tunnel, memory limits, and a checklist for upgrading without losing data.
- **SDKs and tools:** the experimental Dart SDK now covers the full device protocol; the Python CLI verifies TLS by default and can write a new API key to a protected file without displaying it.
- **Site:** two articles, a technical trace of enrollment and authentication, and an essay on vibe coding.

## Where this is heading

The groundwork for outside testers is in place. Next comes inviting them, listening to what they find, and keeping security triage a quiet routine rather than a campaign.

<!-- ezkey-org:exclude-start
Index excerpt — draft only; published into monthly-digest.html / RSS, NOT into the detail page HTML.
ezkey-org:exclude-end -->

## Index excerpt (draft only)

Security work you don't see but would want, automated checks on every change, and a mobile app ready for its first outside testers.

## Index excerpt FR (draft only)

Un travail de sécurité invisible mais souhaitable, des vérifications automatiques sur chaque changement, et une app mobile prête pour ses premiers testeurs externes.
