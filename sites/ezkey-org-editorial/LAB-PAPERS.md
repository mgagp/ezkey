# Ezkey Lab Papers — publication method

Lab Papers are a third publication type on ezkey.org. They sit between reflective experience reports (Articles) and factual monthly digests. A Lab Paper is an account of an Ezkey experimental sub-project. The form nods to a research article without claiming academic status.

This document is the method canon for Lab Papers. The first real paper (PAM) is out of scope here and gets its own issue.

Authority: [GitHub issue #999](https://github.com/mgagp/ezkey/issues/999) (Marc-approved plan, 2026-10-23).

---

## 1. Context

Ezkey is a **lab project**: freer experimentation and learning, without certification or a paying sponsor. The site already publishes:

- **Experience reports** (Articles). Marc dictates; an agent drafts; then revision and bilingual publication.
- **Monthly digests**, from an established template.

### Sharing rule with experience reports

| Type | Owns |
| ---- | ---- |
| **Lab Paper** | What happened and what was measured |
| **Experience report** (Article) | What it taught and what it means |

The two cite each other. A sub-project (for example PAM) may yield a paper + experience-report pair.

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
- **Top of each paper:** a **Key findings** box (3–5 bullets) before the abstract.
- **Reading:** side table of contents (collapsible on mobile); horizontally scrollable tables; collapsed appendices.
- **No PDF export.**

### Language

Write **English first**. French only after Marc’s explicit go.

---

## 3. Structure (lightweight formalism)

Sections are a **guide**. Omit any section with nothing true to say.

### Header

- Title and authors: Marc first, then bot co-signers with roles from [`product-docs/global/team-roles.md`](../../product-docs/global/team-roles.md).
- Affiliation: **Ezkey Lab**.
- Date, identifier `EZK-LAB-YYYY-NNN`, status `draft` or `published`, keywords.
- Key findings box, then a short self-contained abstract.

### Body

1. Introduction and motivation
2. Hypotheses (stated so they can be checked)
3. Prior state
4. Protocol
5. Course of work and iterations (including Marc ↔ agent misalignments)
6. Results (numbered tables and figures)
7. Discussion, with “Co-author note — [Name]” callouts
8. **Limitations**
9. Lessons learned
10. Next steps

### Closing

- **Who did what** — short list (do **not** use CRediT).
- References: commits, PRs, issues, docs — links into the public repo.
- Collapsed appendices.
- Transparency note: co-authors are AI agents; Marc holds editorial responsibility.

No falsely precise metrics. A formula appears only when it truly serves the argument.

### Register and tone

The research-paper shape is a respectful nod to research culture, not a claim to belong there. Content is sincere, free, and bold in what it explores — neither jokey nor solemn.

- No deliberate humor: no “funny” acknowledgments, no whimsical affiliation. Affiliation is simply **Ezkey Lab**.
- No inflated formalism: no academic jargon for show; no metrics or formulas that imitate rigor without carrying it. Template sections are a guide; omit empty ones.
- No glorification: no lone-hero lab narrative. Named bot co-signers are collaborators, presented plainly and transparently (they are AI agents), never to fake a large team or institution.
- Boldness lives in the ideas explored, not in the tone. An ambitious hypothesis is reported with the same frankness as a failure.
- Final test: a reader should sense someone experimenting with seriousness and pleasure, without trying to appear more than they are.

---

## 4. Writing rules

- **Truth first.** Facts come from real sources: git, tests, CI, logs, or Marc’s dictation. Gaps are marked `[TO VERIFY]` or `[MISSING DATA]` (French drafts may use `[À VÉRIFIER]` / `[DONNÉE MANQUANTE]`).
- **No fabricated consensus.** Real disagreement stays and is attributed.
- **Security.** No secrets, tokens, internal hosts, real IPs, or exploitable configuration. Examples are anonymized.
- **Honest status.** The paper states what is experimental, what became operable, and what did not.

---

## 5. Co-author retrospective round

Run like a team retrospective:

1. **Identify real participants** from Git history, PRs, issues, reviews, and status docs. Produce a name → evidence table; list nobody without evidence. Only names from `team-roles.md` are eligible. (“K” from walk-gate templates is **excluded** — Marc, 2026-10-23.)
2. **Marc validates** the participant list.
3. **Questionnaire** (same for each; Mathieu sends):
   - contributions that landed, with references;
   - disagreements;
   - process blind spots (for example missed perspective on time accounting that drifted display vs integration);
   - an 80–150 word co-author note.
4. **Synthesis.** Raw answers go in an editorial exclude block in the draft, then are synthesized into the paper.

---

## 6. Workflow

1. Marc dictates raw material.
2. Agent gathers facts and proposes a paper plan.
3. Marc validates the plan.
4. Co-author retrospective round.
5. English draft.
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
| [`../ezkey-org/lab.html`](../ezkey-org/lab.html) | Public EN hub |
| [`../ezkey-org/fr/labo.html`](../ezkey-org/fr/labo.html) | Public FR hub |
| [`../ezkey-org/lab-paper-template-example.html`](../ezkey-org/lab-paper-template-example.html) | Layout sample only (`noindex`; omitted from `sitemap.xml`). Real published papers are indexed and listed in the sitemap. |
| [`../ezkey-org/AGENTS.md`](../ezkey-org/AGENTS.md) | Agent guidelines for the static site |
| [`../../product-docs/global/team-roles.md`](../../product-docs/global/team-roles.md) | Eligible co-author names and roles |
