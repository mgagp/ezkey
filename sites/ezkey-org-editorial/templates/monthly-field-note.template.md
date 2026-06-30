---
status: draft
audience: "Developers and maintainers following the project's month-over-month activity and direction"
planned_slug_en: "field-note-YYYY-MM-monthly-activity.html"
planned_slug_fr: "field-note-YYYY-MM-monthly-activity.html"
planned_canonical_en: "https://ezkey.org/field-note-YYYY-MM-monthly-activity.html"
planned_canonical_fr: "https://ezkey.org/fr/field-note-YYYY-MM-monthly-activity.html"
---

<!-- ezkey-org:exclude-start
TEMPLATE — monthly field note (activity digest sub-type of the field-notes lane).

How to use this template:
- Copy it to sites/ezkey-org-editorial/fr/draft-field-note-YYYY-MM-monthly-activity.md
- Author the body in English first (canonical), then produce the French mirror at publish time.
- This is NOT a brain-dump note: light section headers ARE allowed for this sub-type.
- It is a distilled SUMMARY, never a commit-by-commit dump.
- Omit any section that has no real content this month — never render empty sections.
- Keep the subject palette stable across months so readers can compare month over month,
  but only include the subjects that actually saw activity.
- Tone: sober, factual, concrete. No inflated or marketing language. Honest about scope.
- Target length: a comfortable scan (roughly 1 short executive paragraph + 3-6 short
  subject blocks + an optional closing). Longer than a day note, but still distilled.

Subject palette (include only the active ones, in this order):
- Documentation & methodology
- Code hygiene (segment if useful: dependencies, React Doctor, mobile upgrades, tooling)
- Admin UI
- Mobile app
- Backend & API contracts
- The rest worth mentioning: SDK, test/Docker infrastructure, public site, security/integrity

See the procedure and distillation rules in .cursor/skills/monthly-field-note/SKILL.md
ezkey-org:exclude-end -->

<!-- Meta line rendered on the note page: "Monthly field note · <Month> <Year>" -->
<!-- Eyebrow label: a 2-4 word theme for the month -->

# <Short evocative title capturing the month's dominant theme>

<One-line tagline summarizing the month in plain language.>

## In short

<2-4 sentences: the overall trend, where the energy went, the rhythm/intensity of the
month, and the one or two dominant subjects. This is the executive summary — a reader
should grasp the shape of the month from this block alone.>

## Documentation & methodology

<1-3 sentences. What moved in product-docs, methodology, design principles, or written
artifacts. Omit this section entirely if there was no meaningful activity.>

## Code hygiene

<1-3 sentences. Dependency upgrades, React Doctor passes, mobile stack upgrades, tooling
coherence across sub-projects. Segment only if the distinction adds signal. Omit if quiet.>

## Admin UI

<1-3 sentences. Operator-facing changes; keep the Global Admin vs Tenant Admin split in
mind when it matters. Omit if quiet.>

## Mobile app

<1-3 sentences. ezkey_mobile work. Omit if quiet.>

## Backend & API contracts

<1-3 sentences. Core domain, the three APIs, DTO/contract changes, generated specs,
Postman parity. Omit if quiet.>

## The rest worth mentioning

<Only if there was notable activity elsewhere: SDK, test/Docker infrastructure, public
site, security/integrity. One short line each. Omit the whole section if nothing qualifies.>

## Where this is heading

<Optional closing, 1-3 sentences. A trend you notice, an open question, or a tentative
direction for next month. Consistent with the field-note habit of ending on an opening.
Omit if the digest reads complete without it.>
