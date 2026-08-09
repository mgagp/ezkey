# Methodology Explorer · Site Pipeline

A small site pipeline that serves a documentation-first methodology corpus as a navigable web app:
tree on the left, rendered Markdown in the center, in-document TOC on the right.

This started as a local developer tool and now also serves as the source for the published static
methodology explorer (`methodology.ezkey.org`). Since the 2026-08 radical ablation (methodology
v2.0.0), the corpus it publishes is deliberately small — one core document, four templates, a
glossary, and release notes — and the site reflects that: a single guided reading track, search,
and a download pack. The former skills layer, workflow-phase ribbon, cognitive map, and rich view
were removed along with the corpus content they navigated.

## Scope

Serves three public-facing sources:

- `methodology/` — the core methodology document and its release notes.
- `templates/` — the four artifact templates (vision note, backlog idea, tracer-bullet brief,
  architecture decision).
- `glossary.md` — single-file glossary.

Publication boundary:

- Publish the method, not the source project's active delivery corpus.
- Keep instantiated Ezkey delivery artifacts under `product-docs/global/`, `product-docs/components/`,
  and editor-local `.cursor/` assets out of the public explorer.
- Public docs may mention source-project hooks as context, but they must not depend on those
  surfaces as live public links.
- The build audits public Markdown links and fails if a published document escapes that boundary.

The build also produces a generated **download pack** (reference copy + workspace starter) under
`product-docs/site/.generated/downloads/` during preparation and `product-docs/site/dist/downloads/`
in the static output.

## Quick start

```bash
cd product-docs/site
npm install
npm start
```

Then open <http://localhost:4321>.

Use `PORT=5000 npm start` to override the port.

Static build for Cloudflare Pages:

```bash
npm run build
```

Deploy with `scripts/cloudflare/deploy-methodology-preview.sh` /
`deploy-methodology-production.sh` from the repository root.

## Architecture note

`server.js` exposes pure functions (`buildTree`, `renderDoc`, `extractToc`) on top of which Express
is a thin transport layer; `build.js` reuses the same functions to emit the fully static `dist/`
tree. The navigation tree is derived from the filesystem, so the site follows the corpus as it
evolves — the guided track (`tracks.json`) and glossary tooltips (`indices.js`) are the only
hand-curated navigation data.
