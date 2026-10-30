# Lab Paper — review checklist

Use before English publication (and again before a French mirror). Method: [`../LAB-PAPERS.md`](../LAB-PAPERS.md).

## Identity and placement

- [ ] Identifier `EZK-LAB-YYYY-NNN` is unique and recorded on the hub card
- [ ] Status is honest (`draft` vs `published`)
- [ ] Affiliation is exactly **Ezkey Lab** (no whimsical affiliation)
- [ ] Labels avoid “Research” / “Recherche”
- [ ] English published first; French only after Marc’s explicit go

## Truth and sources

- [ ] Every factual claim traces to git, tests, CI, logs, or Marc’s dictation
- [ ] Gaps marked `[TO VERIFY]` / `[MISSING DATA]` (or FR equivalents) — none quietly invented
- [ ] No fabricated consensus; disagreements attributed
- [ ] No falsely precise metrics; formulas only when they serve the argument
- [ ] Limitations section present when results are claimed

## Security and public surface

- [ ] No secrets, tokens, credentials
- [ ] No internal hosts, real IPs, or exploitable configuration
- [ ] Examples anonymized where needed
- [ ] Paper does **not** claim a security guarantee or production readiness

## Structure and presentation

- [ ] Empty guide sections omitted
- [ ] Key findings (3–5 bullets) appear before the abstract
- [ ] Abstract is short and self-contained
- [ ] Tables/figures numbered; wide tables scroll horizontally in HTML
- [ ] Side TOC present; collapsible on mobile
- [ ] Appendices collapsed by default
- [ ] No PDF export offered
- [ ] “Who did what” list present (not CRediT)
- [ ] Transparency note: AI co-authors disclosed; Marc holds editorial responsibility

## Co-authors

- [ ] Participant list validated by Marc
- [ ] Every named co-author has evidence (name → evidence table)
- [ ] Only names from `product-docs/global/team-roles.md`
- [ ] “K” not listed as a co-author
- [ ] Co-author notes synthesized; raw answers kept in editorial exclude blocks only

## Publish steps (English)

- [ ] Card on `/lab.html` (abstract-centered: id, status, date, reading time, ~2-line abstract, authors)
- [ ] Mention in that month’s monthly digest
- [ ] URLs added/bumped in `sites/ezkey-org/sitemap.xml`
- [ ] Draft front matter updated (`status`, published paths, `source_of_truth`)
- [ ] Draft remains under `sites/ezkey-org-editorial/` (never in the deploy folder)

## Publish steps (French, after go)

- [ ] Mirror on `/fr/labo.html` and FR paper page
- [ ] Sitemap FR URLs updated
- [ ] No FR-only facts that are absent from the English paper

## Tone

- [ ] Sincere and bold in ideas; neither jokey nor solemn
- [ ] No hero narrative; bots presented plainly as AI collaborators
- [ ] Final reader test: seriousness and pleasure without pretension
