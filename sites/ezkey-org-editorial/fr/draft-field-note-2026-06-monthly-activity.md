---
status: draft
audience: "Developers and maintainers following the project's month-over-month activity and direction"
planned_slug_en: "field-note-2026-06-monthly-activity.html"
planned_slug_fr: "field-note-2026-06-monthly-activity.html"
planned_canonical_en: "https://ezkey.org/field-note-2026-06-monthly-activity.html"
planned_canonical_fr: "https://ezkey.org/fr/field-note-2026-06-monthly-activity.html"
---

<!-- ezkey-org:exclude-start
Monthly activity digest — June 2026 (test generation, not published).
Source: git log --no-merges 2026-06-01..2026-07-01, distilled by subject and intent.
Editorial posture: summarize the nature and intent of the work, not internal tracking
notations. Internal codes (tier labels, wave/cluster codenames, security-finding IDs,
issue/PR numbers, HTTP status codes) are translated into plain meaning or dropped.
Body authored in English first (canonical); French mirror generated from this source.
ezkey-org:exclude-end -->

<!-- Meta line: "Monthly field note · June 2026" -->
<!-- Eyebrow label: "June 2026 in review" -->

# Hygiene, a more useful console, and a steadier method

A distilled look at where June's work actually went — across the admin interface, the backend, mobile, and the way the project is run.

## In short

June ran on two parallel efforts. The first made the admin console more useful day to day: its lists and detail screens now show meaningful names and context instead of bare identifiers. The second was the broad maintenance and upgrade wave described in the June 17 note — keeping dependencies current and modernizing both the backend (Spring Boot 4.1, Jackson 3) and the mobile stack. Alongside both, the way the project is run matured into a more deliberate, versioned method, and a few security weaknesses were closed.

## Documentation & methodology

The project's working method became more structured: it gained its first versioned releases, clearer rules for telling routine maintenance apart from tracked programs, and tighter discipline around how work is recorded and followed. On the product side, the month set priorities for the September operable-release target, sharpened an honest stance on what integrity guarantees the system genuinely offers, and opened a body of work around being able to verify that the audit trail has not been tampered with.

## Code hygiene

This was a defining thread of the month, and the direct continuation of the June 17 note. Automated dependency updates kept the admin console current (including a move to the latest React), and the discipline went well beyond version bumps — keeping lockfiles, version floors, and tooling consistent across the project's many sub-parts.

## Admin UI

Making the operator's daily view more informative was the single largest area of work. Across the main lists — API keys, enrollments, integrations, tenants — and the detail screens, related records now appear by name and context rather than as raw identifiers. Operators also gained quick inline actions (deactivating or reactivating an enrollment in place), a clearer distinction between enrolling an admin and signing one in, and the removal of an awkward older "more details" pattern. Some attention also went to responsiveness and accessibility.

## Mobile app

The mobile app went through a deliberate modernization: a major React Native and React upgrade, together with the surrounding toolchain (TypeScript, Babel, ESLint, navigation, and gesture handling). Beyond the upgrades, the app gained its own ongoing quality and dependency monitoring, a safeguard for Windows build-path limits on Android, and more precise error handling in the cryptography, authentication, and storage code.

## Backend & API contracts

Security was the headline: the month closed specific weaknesses in passwordless admin sign-in and in protection against request flooding. Work also began on letting operators trust the audit trail — including an automatic nightly check — and re-encrypting stored data became a background operation rather than a blocking one. On the developer-facing side, the public API references were reordered to read in the sequence a developer actually follows, their structure was made consistent, and the backend was aligned on current core libraries.

## The rest worth mentioning

- **Public site:** the field notes section itself launched on the site, and a new personal essay was published.
- **Infrastructure & tests:** the same framework upgrade (Spring Boot 4.1, Jackson 3) swept the test and demo components, and the browser-based test device now follows the right enrollment link from a scanned code.
- **SDK:** the generated client libraries were kept in step with the API changes.
- **Tooling:** the commit and release tooling became more reliable on Windows, and routine cleanup of preview deployments got easier.

## Where this is heading

June reads as a consolidation month — less brand-new feature surface, more depth, safety, and method. The open question from the June 17 note now has a partial answer: maintenance is becoming a system rather than an ad-hoc effort. The natural next step is turning that into a regular, sustainable rhythm instead of bursts at irregular intervals.
