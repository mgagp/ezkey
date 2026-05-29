# Ezkey Method · Local Explorer

A small local micro-site that serves the Ezkey methodology corpus as a navigable web app: tree on the left, rendered Markdown in the center, in-document TOC on the right.

This is a **local-only** developer tool. It is not deployed and not part of any product build.

## Scope (Phase 1)

Serves four public-facing sources:

- `methodology/` — the full methodology pack (workflow, values, tracer bullets, quality gates, …).
- `templates/` — artifact templates (vision notes, backlog ideas, tracer bullet briefs, …).
- `skills/` — a derived public skills layer generated from `.cursor/skills/` during site preparation.
- `glossary.md` — single-file glossary.

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
- **Phase 4 (future)** — Static pre-build for Cloudflare Pages exposure.

## Architecture note

`server.js` exposes pure functions (`buildTree`, `renderDoc`, `extractToc`) on top of which Express is a thin transport layer. This keeps the future static pre-build (Phase 4) trivial: a `build.js` script can reuse the same functions without Express.

Before the site serves or builds the corpus, it also prepares a small derived public skills layer under `product-docs/site/.generated/skills/`. The source of truth remains `.cursor/skills/`; the generated layer exists only to improve site discoverability and public explanation.
