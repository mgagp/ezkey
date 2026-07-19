# Methodology Explorer · Site Pipeline

A small site pipeline that serves a documentation-first methodology corpus as a navigable web app:
tree on the left, rendered Markdown in the center, in-document TOC on the right.

This started as a local developer tool and now also serves as the source for the published static
methodology explorer. In this repository, that explorer is published from the Ezkey source
project, but the site is intended to present the methodology as a method-first surface rather than
as project documentation wearing a site shell.

## Scope (Phase 1)

Serves four public-facing sources:

- `methodology/` — the full methodology pack (workflow, values, tracer bullets, quality gates, …).
- `templates/` — artifact templates (vision notes, backlog ideas, tracer bullet briefs, …).
- `skills/` — a curated, derived public skills layer generated from selected `.cursor/skills/` during site preparation.
- `glossary.md` — single-file glossary.

Publication boundary:

- Publish the method, not the source project's active delivery corpus.
- Keep instantiated Ezkey delivery artifacts under `product-docs/global/`, `product-docs/components/`,
  and editor-local `.cursor/` assets out of the public explorer.
- Public docs may mention source-project hooks as context, but they must not depend on those
  surfaces as live public links.
- The build audits public Markdown links and fails if a published document escapes that boundary.

The next intended public-distribution extension is a generated **download pack** so the same site
pipeline can produce both the hosted explorer and a local adoption archive.

The generated archive lives under `product-docs/site/.generated/downloads/` during local build
preparation and under `product-docs/site/dist/downloads/` in the static output.

## Quick start

```bash
cd product-docs/site
npm install
npm start
```

Then open <http://localhost:4321>.

Use `PORT=5000 npm start` to override the port.

## Roadmap

This is **Phase 1** (MVP visual foundation). See [.github/prompts/plan-methodologyMicroSite.prompt.md](../../.github/prompts/plan-methodologyMicroSite.prompt.md) for the full plan:

- **Phase 1** — 3-pane layout, tree navigation, Markdown rendering, in-document TOC.
- **Phase 2** — Pedagogical layer: workflow-phase ribbon, persona-driven wizard tracks (Discover / Apply / Present), Mermaid rendering, glossary tooltips.
- **Phase 3** — Polish: `Ctrl+K` search, presentation mode, cognitive workflow map, step permalinks.
- **Phase 4** — Static pre-build for Cloudflare Pages exposure.
- **Phase 5 (next likely extension)** — Generated download pack for local reuse, before any
  installer-style automation.

## Architecture note

`server.js` exposes pure functions (`buildTree`, `renderDoc`, `extractToc`) on top of which Express is a thin transport layer. This keeps the future static pre-build (Phase 4) trivial: a `build.js` script can reuse the same functions without Express.

Before the site serves or builds the corpus, it also prepares a small, explicitly allowlisted
public skills layer under `product-docs/site/.generated/skills/`. The source of truth remains
`.cursor/skills/`; the generated layer exists only to improve site discoverability and public
explanation without publishing every source-project automation.

The same build pipeline is the preferred place to add a methodology download pack, because that
keeps packaging explicit, small, and tied to already-curated public inputs.
