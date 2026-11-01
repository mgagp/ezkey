# Ezkey Lab Papers — publication method

Lab Papers are a third publication type on ezkey.org. They sit between reflective experience reports (Articles) and factual monthly digests. A Lab Paper is a short, readable account of an Ezkey experiment: what we tried, what we saw, what we got wrong, what we learned.

**The look of a paper, the voice of a lab notebook.** The research-paper nod lives in the layout: header, identifier, key-findings box, abstract, numbered sections, captions, references. The text itself is a plain, honest account. Rigour here means honesty, not exhaustiveness. We are not a research lab and do not imitate one.

This document is the method canon for Lab Papers.

Authority: [GitHub issue #999](https://github.com/mgagp/ezkey/issues/999) (Marc-approved plan, 2026-10-23). Reframe of 2026-11-01 (Marc): the first PAM draft grew to ~6,500 words of sourcing, caveats and appendices; papers are now short and readable, in line with the rest of ezkey.org (refs #999).

---

## 1. Context

Ezkey is a **lab project**: serious in its approach, free in what it explores, without certification or a paying sponsor. The site already publishes:

- **Experience reports** (Articles). Marc dictates; an agent drafts; then revision and bilingual publication.
- **Monthly digests**, from an established template.

### Sharing rule with experience reports

| Type | Owns |
| ---- | ---- |
| **Lab Paper** | What we tried, what we observed, what we learned from it |
| **Experience report** (Article) | What it means to Marc, beyond the experiment |

The two may cite each other. A sub-project (for example PAM) may yield a paper + experience-report pair.

---

## 2. Site placement and naming

### Placement

- **Separate collection** — not folded into Articles. **No tenth primary-nav tab** until roughly three published papers (promotion threshold).
- Hub pages: `/lab.html` (EN) and `/fr/labo.html` (FR). Light LaTeX-inspired identity; short intro that covers (1) what the lab is, (2) the role of bot co-authors, (3) that nothing here is a security guarantee.
- A third group **Lab papers** on the Articles index links to the collection. Reciprocal **Distinct from…** sentences appear on Articles and on Lab.
- On **Trust**, one sober factual sentence: Lab Papers document experiments and sit outside the claims perimeter of that page.
- When a paper publishes, mention it in that month’s monthly digest.
- Nav label when promoted: **Lab** / **Labo**. Page title: **Lab Papers** / **Cahiers du labo**.
- Avoid the words “Research” / “Recherche” in labels and titles.

Medium-term note (out of scope): possible nav regrouping into **Evaluate** vs **Writing** clusters — see #999.

### Presentation

- **List card** centered on the abstract: identifier, status, date, reading time, ~2-line abstract, authors in small type.
- **Top of each paper:** a **Key findings** box (3–4 bullets) before the abstract.
- **Reading:** side table of contents (collapsible on mobile); horizontally scrollable tables.
- **No appendix by default.** **No PDF export.**

### Language

Write **English first**. French only after Marc’s explicit go.

---

## 3. Structure (light)

**Length target: 1,500–2,500 words** (about 6–10 minutes), in line with Articles. Sections are a guide; omit any that has nothing to say.

### Header

- Title, then authors: Marc first, then the **main** bot co-authors with their role from [`product-docs/global/team-roles.md`](../../product-docs/global/team-roles.md). Not exhaustive.
- Affiliation: **Ezkey Lab**. Date, identifier `EZK-LAB-YYYY-NNN`, status `draft` or `published`, a few keywords, reading time.
- **Key findings** (3–4 bullets), then a self-contained **abstract** of about 100 words.
- One **scope box** — what this is / what it is not. Said once, not repeated.

### Body

1. Why we tried it
2. What we set up
3. What happened
4. What we observed
5. What we got wrong, and how we reframed
6. What we take away
7. Where we stopped

At most one or two tables in the body. Co-author voices appear as **short quotes** inside the sections where they matter — one per main co-author at most, none required.

### Closing

- **References** — a short list of public repo links (PRs, issues, docs) a reader may want to open.
- **Transparency** — one line: co-authors are AI agents; Marc holds editorial responsibility; nothing here is a security guarantee.

### Register and tone

The research-paper shape is a respectful nod to research culture, not a claim to belong there. The text reads like a lab notebook written for others: first person plural, concrete, frank about mistakes, pleasant to read.

- No deliberate humor: no “funny” acknowledgments, no whimsical affiliation. Affiliation is simply **Ezkey Lab**.
- No imitation of rigour: no hypotheses written after the fact, no metrics or formulas that look scientific without carrying the argument, no appendix tables kept for completeness.
- No glorification: no lone-hero narrative. Bot co-authors are collaborators, presented plainly as AI agents, never to fake a team or institution.
- Boldness lives in the ideas explored, not in the tone. A failure or a reframing is reported with the same frankness as a success.
- Final test: a reader should finish the paper, sense someone experimenting with seriousness and pleasure, and not feel lectured.

---

## 4. Writing rules

- **True, not exhaustive.** Keep only facts we stand behind (git, tests, CI, logs, Marc’s dictation, co-author retrospectives). A doubtful fact is checked or cut — **no `[TO VERIFY]` marker survives into a publishable draft.**
- **Say uncertainty once, in prose.** “From memory”, “we did not measure it”, “this is a judgment” — where it matters, not on every sentence.
- **Links over provenance.** Link the PRs a reader would open; do not cite a source for every claim or reconcile internal labels in the text.
- **One scope statement.** The disclaimer (no audit, no certification, no production readiness, no equivalence claims) lives in the scope box and the transparency line only.
- **Mistakes have a place.** Reframings and errors get their own section; they are the substance of a lab that iterates.
- **No fabricated consensus.** Real disagreement stays and is attributed.
- **Security.** No secrets, tokens, internal hosts, real IPs, or exploitable configuration.

Raw material (retrospectives, syntheses, figures) stays under `lab-notes/`, outside the published text.

---

## 5. Co-author input (optional)

When a paper benefits from co-author voices, run a short retrospective:

1. Pick the main participants from Git history, PRs and issues. Only names from `team-roles.md` are eligible. (“K” from walk-gate templates is **excluded** — Marc, 2026-10-23.)
2. Ask a few questions: what landed, what surprised you, where it stopped paying off, one or two sentences you would want quoted.
3. Keep raw answers in `lab-notes/`. Quote briefly in the paper. Marc’s editorial validation is enough; there is no separate “pending validation” round.

---

## 6. Workflow

1. Marc dictates raw material.
2. Agent gathers facts and proposes a short plan (key findings + section outline).
3. Marc validates the plan.
4. Optional co-author retrospective.
5. English draft, within the length target.
6. Marc review + checklist.
7. English publication: Lab card, monthly-digest mention, sitemap.
8. French after an explicit go.

---

## Related paths

| Path | Role |
| ---- | ---- |
| [`templates/lab-paper.en.template.md`](templates/lab-paper.en.template.md) | English draft template |
| [`templates/lab-paper.fr.template.md`](templates/lab-paper.fr.template.md) | French draft template (after Marc’s go) |
| [`templates/lab-paper-review-checklist.md`](templates/lab-paper-review-checklist.md) | Pre-publish review checklist |
| [`lab-notes/`](lab-notes/) | Raw retrospectives and syntheses (not published) |
| [`../ezkey-org/lab.html`](../ezkey-org/lab.html) | Public EN hub |
| [`../ezkey-org/fr/labo.html`](../ezkey-org/fr/labo.html) | Public FR hub |
| [`../ezkey-org/lab-paper-template-example.html`](../ezkey-org/lab-paper-template-example.html) | Layout sample only (`noindex`; omitted from `sitemap.xml`). Real published papers are indexed and listed in the sitemap. |
| [`../ezkey-org/AGENTS.md`](../ezkey-org/AGENTS.md) | Agent guidelines for the static site |
| [`../../product-docs/global/team-roles.md`](../../product-docs/global/team-roles.md) | Eligible co-author names and roles |
