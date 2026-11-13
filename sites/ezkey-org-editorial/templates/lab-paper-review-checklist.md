# Lab Paper — review checklist

Use before English publication (and again before a French mirror). Method: [`../LAB-PAPERS.md`](../LAB-PAPERS.md).

## Readability first

- [ ] 1,500–2,500 words; reading time on the header matches
- [ ] Key findings (3–4 bullets) say the point without the rest of the paper
- [ ] A reader outside the project can follow it without opening a PR
- [ ] One scope box; the disclaimer is not repeated in the body
- [ ] Mistakes and reframings have their place
- [ ] No hypotheses written after the fact, no appendix kept for completeness

## Truth

- [ ] Every fact is one we stand behind (git, tests, CI, logs, Marc, co-author retrospectives)
- [ ] No `[TO VERIFY]` / `[À VÉRIFIER]` marker left
- [ ] Uncertainty said once, in prose, where it matters
- [ ] No fabricated consensus; disagreements attributed
- [ ] No falsely precise metrics

## Security and public surface

- [ ] No secrets, tokens, credentials
- [ ] No internal hosts, real IPs, or exploitable configuration
- [ ] No security guarantee or production-readiness claim

## Identity and co-authors

- [ ] Identifier `EZK-LAB-YYYY-NNN` is unique; status is honest
- [ ] Affiliation is exactly **Ezkey Lab**; labels avoid “Research” / “Recherche”
- [ ] Main co-authors only, from `product-docs/global/team-roles.md`; “K” not listed
- [ ] Quotes short; raw answers stay in `lab-notes/`
- [ ] Transparency line present

## Publish steps (English)

- [ ] Card on `/lab.html` (id, status, date, reading time, ~2-line abstract, authors)
- [ ] Mention in that month’s monthly digest
- [ ] URLs added/bumped in `sites/ezkey-org/sitemap.xml`
- [ ] Draft front matter updated (`status`, published paths)
- [ ] Draft remains under `sites/ezkey-org-editorial/` (never in the deploy folder)

## Publish steps (French, after go)

- [ ] Mirror on `/fr/labo.html` and FR paper page
- [ ] Sitemap FR URLs updated
- [ ] No FR-only facts absent from the English paper

## Tone

- [ ] Sincere and bold in ideas; neither jokey nor solemn
- [ ] No hero narrative; bots presented plainly as AI collaborators
- [ ] Final reader test: you want to finish it
