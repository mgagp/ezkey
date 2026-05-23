# OpenAPI Portal Candidate Review

## Purpose

Compare the shortlisted open-source API documentation portal candidates against Ezkey's concrete posture:

- public portal on `ezkey.org`,
- read-only public reference,
- static Cloudflare Pages hosting,
- low accidental complexity,
- production-capable API group = `Admin API`, `Auth API`, optional `Integration API`,
- `Crypto API` excluded from the normal public portal posture.

This document supports `I-2026-0026` and is meant to prepare and record the final renderer / placement validation with the operator.

## Evaluation lens

The candidates are not being compared as generic OpenAPI renderers. They are compared as potential fits for Ezkey's public documentation posture.

Primary criteria:

- static-hosting fit on Cloudflare Pages;
- easy consumption of generated OpenAPI JSON;
- fit for a sober public read-only portal;
- tolerance for a partial API group (`Integration API` optional);
- multi-spec ergonomics without introducing a new app stack;
- open-source credibility and maintenance activity;
- low lock-in and low accidental complexity.

## Candidate review

### ReDoc CE

**Strengths**

- Very strong fit for a public **read-only** reference posture.
- Straightforward static HTML embedding and also supports prebuilt static output through Redocly CLI.
- Mature and widely recognized in API-documentation contexts.
- Its visual model naturally emphasizes documentation rather than public API execution.
- Matches Ezkey's need for a sober documentation section inside `ezkey.org`.

**Weak points**

- Multi-spec experience is less opinionated out of the box than newer portals; a small amount of site-level information architecture is still needed.
- Public interactivity is not its strong side, though that is not a requirement here.
- If Ezkey later wants a richer single-page multi-API portal, ReDoc may feel more static and segmented.

**Ezkey fit**

Very strong for the current posture. It aligns with the choice to keep the public portal read-only and to avoid a heavier frontend/documentation stack.

### Scalar

**Strengths**

- Modern, polished API reference with good support for multiple sources/specs.
- Easy static HTML embedding through CDN or package usage.
- Strong long-term flexibility if Ezkey later wants a more unified multi-API experience.
- Active project with strong momentum.

**Weak points**

- It brings more product surface than Ezkey currently needs for a sober read-only portal.
- Its strengths lean partly toward interactivity and richer API-explorer behavior, which are intentionally not central to the public posture.
- Slightly higher conceptual and configuration weight than the simplest ReDoc path.

**Ezkey fit**

Strong alternative. Especially attractive if Ezkey values a more modern multi-spec experience enough to accept a bit more portal complexity now.

### Swagger UI

**Strengths**

- Familiar, ubiquitous, and operationally simple.
- Static self-hosting is well understood.
- Direct continuity with Springdoc's embedded UI model.

**Weak points**

- The public result would look closer to exposed developer tooling than to a curated documentation portal.
- The visual and editorial fit with `ezkey.org` is weaker than ReDoc or Scalar.
- Its strongest posture is interactive API exploration, which is not the target public direction.

**Ezkey fit**

Useful as internal/operator tooling and as a baseline, but not the strongest candidate for the public `ezkey.org` portal.

### RapiDoc

**Strengths**

- Lightweight web-component model with easy static embedding.
- Flexible and simple to host.
- Can be attractive when a small self-contained renderer is preferred.

**Weak points**

- Lower ecosystem weight and mindshare than ReDoc, Scalar, or Swagger UI.
- Weaker default signal as the main public documentation face for a project like Ezkey.
- Less obviously advantageous than ReDoc for read-only posture or than Scalar for richer multi-spec posture.

**Ezkey fit**

Technically viable, but not the most credible default choice for Ezkey unless a specific implementation advantage appears during prototyping.

## Ranking

### Best fit for current Ezkey posture

1. **ReDoc CE**
2. **Scalar**
3. **Swagger UI**
4. **RapiDoc**

## Recommendation posture

### Selected default

Use **ReDoc CE** for Ezkey's first public portal iteration.

Why:

- it matches the public **read-only** posture directly;
- it keeps the implementation small and static-host-friendly;
- it feels more like curated product documentation than exposed runtime tooling;
- it preserves future freedom, because the OpenAPI documents remain the real durable asset.

### Strong fallback / alternative

Keep **Scalar** as the strongest alternative if, during validation, Ezkey decides that a more unified multi-spec portal experience is worth the additional weight.

## Operator validation (2026-05-21)

- **Renderer:** `ReDoc CE`
- **Portal posture:** public **read-only** reference
- **Placement:** inside the existing `ezkey.org` static site / Cloudflare Pages model
- **Information architecture:** one landing page plus one page per API
- **Public API group:** `Admin API`, `Auth API`, optional `Integration API`
- **Excluded from public portal:** `Crypto API`

## First portal shape

The selected first cut is intentionally simple:

1. one landing page on `ezkey.org` that explains the API group, exposure posture, and navigation;
2. one dedicated documentation page for `Admin API`;
3. one dedicated documentation page for `Auth API`;
4. one dedicated documentation page for `Integration API` when that surface is part of the deployment/documentation set.

Why this shape fits Ezkey now:

- it preserves a very low-complexity static-site implementation;
- it aligns naturally with `ReDoc CE`;
- it tolerates the optional nature of `Integration API`;
- it avoids prematurely forcing a heavier multi-spec portal shell;
- it keeps the first portal easy to reason about, review, and maintain.

## What this settles

1. Ezkey prefers the more sober, documentation-first posture over a richer public explorer.
2. The first public portal is explicitly allowed to be **one landing page plus one page per API**.
3. The current direction optimizes for best fit with `ezkey.org`, not for maximum portal sophistication in the first cut.

## Links

- Backlog: [`backlog/ideas/I-2026-0026-openapi-exposure-and-api-portal-posture.md`](backlog/ideas/I-2026-0026-openapi-exposure-and-api-portal-posture.md)
- Exposure matrix: [`openapi-exposure-matrix.md`](openapi-exposure-matrix.md)
- Vision: [`vision/product-orientation-notes.md`](vision/product-orientation-notes.md) (`V-2026-0014`)
