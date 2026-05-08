# Backlog Idea — `I-2026-0009` Global controllers registry as documentation artifact

## Metadata

- **ID:** `I-2026-0009`
- **Status:** `captured`
- **Priority:** `P2`
- **Created at:** `2026-05-08`
- **Updated at:** `2026-05-08`
- **Last reviewed at:** `2026-05-08`
- **Phase tags:** `P1-operability`
- **Component tags:** `docs (product-docs)`, `admin-api`, `auth-api`, `integration-api`, `crypto-api`

## Intent

Introduce a single canonical registry that lists every REST controller across the API surface with high-level functional characteristics — purpose, audience, expected volume profile, sensitivity, rate-limit posture, error-model variant. The goal is to enable global reasoning about the API surface during analysis, design, tracer-bullet planning, and decisions like rate-limit baseline (`I-2026-0008`), display strategy (`I-2026-0014`), volume limits (`I-2026-0015`), or versioning (`V-2026-0008`).

## Problem and value

- **Problem:** Today, reasoning about the API surface as a whole requires either re-discovery from code or sampling component docs. There is no single artifact that gives a one-page operator-level view of "what does each controller do, and what is its risk and volume profile". This makes cross-cutting decisions harder than they need to be.
- **Expected value:** Lift the methodology one notch — bounded global view of the API surface that supports vision-level decisions (deployment profiles, rate-limit baselines, versioning) without requiring full code exploration each time. Aligns with `Design Principle #9` (one canonical place per concept) and `#13` (open-source transparency).

## Scope

- **In scope:**
  - Define the registry schema (per-controller row: ID, module, base path, purpose, audience, expected volume profile, sensitivity, rate-limit posture, references).
  - Author the initial registry covering all current controllers in `admin-api`, `auth-api`, `integration-api`, `crypto-api`.
  - Place the registry under `product-docs/global/` (canonical home).
  - Reference the registry from cross-cutting decisions and the methodology.
- **Out of scope:**
  - Replacing component-level functional flow docs (the registry is a summary, not a substitute).
  - Auto-generation from code at this stage (manual curation first; tooling later if ROI emerges).

## Key assumptions

- Manual curation is acceptable for the initial registry; the API surface size is bounded enough.
- Once initial, drift can be controlled by a lightweight skill (see promotion notes) instead of automated codebase scanning.

## Risks and exceptions

- Drift between the registry and the actual code if not maintained.
- Over-engineering if the schema becomes too rich; keep it operator-level (one screen of information per controller maximum).

## Promotion notes

- Move to `triaged` after the schema is agreed.
- Promote to `incubating` and then `ready` once the initial draft is filled in for at least one module.
- **Future skill candidate:** `ezkey-controllers-registry-sync` — a lightweight check that validates the registry against the actual REST controllers (stub diff), at reasonable token cost (no global codebase scan; targeted file reads only). Track this as an enhancement once the registry exists, not before. The intent is to keep the AI-assisted methodology efficient (not a token-devouring scanner) while preserving documentation quality, per the operator's stated values.

## Links

- Companion: `I-2026-0008` (rate-limit baseline — would consume registry data), `I-2026-0014` (display strategy), `I-2026-0015` (volume limits).
- Related cross-cutting decisions: `V-2026-0002` (deployment profiles), `V-2026-0008` (Auth API versioning).
- Related principles: `#5` (operator-first applied to internal navigation), `#9` (one canonical place per concept), `#13` (transparency).
