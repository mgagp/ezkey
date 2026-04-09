---
name: cloudflare site structure
overview: Propose a durable naming and repository structure for the current static Ezkey site and future Cloudflare-assisted operations, while keeping the initial setup pragmatic and aligned with the current repo.
todos:
  - id: confirm-folder-model
    content: Adopt `sites/ezkey-org/` as the new home for the current static public site and retire the `ezkey-teaser` name.
    status: pending
  - id: define-cloudflare-docs-home
    content: Use `docs/cloudflare/` as the documentation and assistant-planning area for Cloudflare workflows, constraints, and future deployment notes.
    status: pending
  - id: document-preview-promotion-flow
    content: "Write a short workflow doc for the intended agent dialogue: edit site content, deploy preview, validate manually, then promote to production."
    status: pending
  - id: keep-automation-deferred
    content: Do not create Cloudflare scripts until there is at least one repeated deployment or DNS action worth standardising.
    status: pending
  - id: separate-time-horizons
    content: Structure the future docs into clear sections for current reality, short term, medium term, and longer-term possibilities.
    status: pending
isProject: false
---

# Cloudflare And Site Structure

## Recommended Direction

I recommend **retiring the name `ezkey-teaser`** and adopting a small three-part structure:

- `**sites/ezkey-org/**` for the current static public site now living in [ezkey-teaser/index.html](ezkey-teaser/index.html).
- `**docs/cloudflare/**` for assistant-facing runbooks, decisions, scope, and future plans.
- `**scripts/cloudflare/**` only when there is a real repeated automation need; do not create it yet just to reserve space.

This gives you a stable naming model:

- `teaser` is time-bound and will age badly once the page becomes an ongoing public mini blog.
- `ezkey-org` is deployment-target oriented, clear for humans, and works whether the page stays simple or grows.
- `sites/` gives you a clean bucket for public-facing static assets without polluting the repo root with one-off folders.

## Why This Fits The Current Reality

The current [ezkey-teaser/index.html](ezkey-teaser/index.html) is a **fully static, self-contained page** with inline CSS and inline SVG, plus a sibling logo file. It is not tied to Maven, the Admin UI build, or any existing app pipeline. That makes it a good fit for a dedicated static-site area rather than a product module.

The repo already has natural anchors for the Cloudflare side:

- [docs/OPERATIONAL.md](docs/OPERATIONAL.md) already discusses Cloudflare headers, proxying, and rate-limiting context.
- [docs/admin-ui-security.md](docs/admin-ui-security.md) already anticipates split deployment with UI on Cloudflare and API elsewhere.
- The Cloudflare MCP available to you is broad and generic enough to support a future workflow based on “search API surface, then execute API calls”, so the repo should document usage patterns rather than mirror Cloudflare features folder-by-folder.

## Proposed Initial Structure

### 1. Public site source

Move the current teaser into:

- [sites/ezkey-org/index.html](sites/ezkey-org/index.html)
- [sites/ezkey-org/ezkey_logo.svg](sites/ezkey-org/ezkey_logo.svg)
- optional later: [sites/ezkey-org/README.md](sites/ezkey-org/README.md)

This folder becomes the single source for:

- the public landing page,
- the monthly mini blog updates,
- preview and production static deployments on Cloudflare.

### 2. Cloudflare documentation and assistant framing

Create a focused docs area:

- [docs/cloudflare/README.md](docs/cloudflare/README.md)
- [docs/cloudflare/ezkey-org-site.md](docs/cloudflare/ezkey-org-site.md)
- optional later: [docs/cloudflare/admin-ui-edge-plan.md](docs/cloudflare/admin-ui-edge-plan.md)

That docs area should capture, briefly and pragmatically:

- what is already real today,
- what the near-term agent workflow should be,
- what is medium-term target architecture,
- what is still exploratory or hypothetical.

### 3. Automation later, not now

Only add `scripts/cloudflare/` when you have at least one repeated action worth standardising, for example:

- deploy preview static site,
- promote validated preview to production,
- inspect or update a DNS record,
- list or verify edge rules.

Until then, keep the workflow documented first. That is the simpler and more maintainable path.

## Delivery Phases

## Short Term

- Rename and relocate `ezkey-teaser` into `sites/ezkey-org/`.
- Document the intended operator dialogue for content edits and Cloudflare deployment.
- Capture the minimal preview-to-production workflow you described:
  - edit [sites/ezkey-org/index.html](sites/ezkey-org/index.html)
  - deploy to a Cloudflare test URL
  - validate manually
  - promote to production
- Keep the deployment model explicitly free-tier friendly where possible.

## Medium Term

- Define the repository-side conventions for Cloudflare-assisted work:
  - where plans live,
  - where Cloudflare notes live,
  - what the agent is allowed to change automatically,
  - what still requires human validation.
- Add a short document for future Cloudflare responsibilities around:
  - DNS for public hostnames such as `api.ezkey.org`,
  - edge headers and CSP alignment,
  - basic edge security controls that fit the free tier,
  - rate-limiting and WAF opportunities when they become relevant.

## Longer Term

- Plan split hosting more explicitly:
  - public/static assets on Cloudflare,
  - Admin UI potentially on Cloudflare,
  - backend/API on AWS or another origin.
- Extend the Cloudflare docs with clearly labeled future topics only when decisions are real enough:
  - DNS routing,
  - edge security rules,
  - rate limiting,
  - JSON schema validation at the edge,
  - WAF posture within free-tier constraints.

## Guardrails For The Future Documentation

When we implement this plan, the initial docs should stay brief and factual:

- distinguish **current reality**, **near-term approved direction**, and **future possibilities** in separate sections,
- avoid inventing Cloudflare features you have not decided to use,
- keep one source of truth for edge/security guidance and cross-link back to [docs/OPERATIONAL.md](docs/OPERATIONAL.md) and [docs/admin-ui-security.md](docs/admin-ui-security.md),
- prefer workflow-oriented language for the assistant, not broad platform theory.

## Naming Recommendation Summary

My primary recommendation is:

- rename `**ezkey-teaser`** to `**sites/ezkey-org`**.

If you want a slightly more product-language-oriented variant, the only alternative I would seriously consider is:

- `**sites/public-site`**.

Between the two, `**sites/ezkey-org`** is better because it stays clear if you later add another public site, docs microsite, or preview target.
