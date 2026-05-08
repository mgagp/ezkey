# Backlog Idea — `I-2026-0017` Profile elaboration template, skill, and per-client document workflow

## Metadata

- **ID:** `I-2026-0017`
- **Status:** `captured`
- **Priority:** `P1`
- **Created at:** `2026-05-08`
- **Updated at:** `2026-05-08`
- **Last reviewed at:** `2026-05-08`
- **Phase tags:** `P1-operability`
- **Component tags:** `docs (product-docs)`, `methodology`, `skills`

## Intent

Realize Phase 2 of `V-2026-0010`: design and build the per-installation profile elaboration workflow. The output is a structured document **per installation/client** that captures the environment characteristics, the applicable configuration parameters traversed from the existing `CONFIGURATION.md` corpus, and the operational profile rationale. The workflow is AI-assisted (a dedicated skill conducts the elaboration session) and produces a versionable, pragmatic artefact suitable for sharing with the client as a basis for discussion or contract.

## Problem and value

- **Problem:** Today, when an Ezkey operator deploys for a real PME or specific client, there is no structured way to elaborate the installation's operational profile. The Clean Start script handles dev/QA/test contexts well, but production-leaning installations require deliberate decisions about environment, capacity, redundancy, security posture, and parameter selection. Without structure, those decisions are ad hoc, hard to revisit, hard to audit, and hard to share with the client.
- **Expected value:** A repeatable, pragmatic per-installation profile elaboration that captures decisions and rationale in a single artefact. Reduces operator effort, increases auditability, creates the input contract for the future generator (`I-2026-0018`), and aligns operational reality with the platform's intended posture.

## Scope

- **In scope:**
  - Define the **profile elaboration document template** — sections, tables, parameter inclusion patterns, naming and slug convention per installation.
  - Define the **AI-assisted skill** that conducts the elaboration session: questionnaire flow, environment questions (Cloudflare, other proxies, redundancy needs, memory capacity, trusted proxy, network topology, etc.), and the bridge into the existing `CONFIGURATION.md` corpus to surface parameters relevant to the installation's context.
  - Define the **storage location** for instantiated documents (per-client slug, structured location in the repo or alternative).
  - Define the **validation step** at the end of the session — operator review and explicit confirmation before the document is finalized and versioned.
  - Pilot the workflow with at least one realistic case (a real PME context or a representative simulated case) to validate the approach end to end.
- **Out of scope:**
  - The generator skill that turns the document into runtime artefacts (`I-2026-0018`).
  - Modifications to existing `CONFIGURATION.md` files (assumed sufficient as input; gaps surfaced during piloting can be addressed separately).
  - Profile-aware code in the platform (explicitly excluded by `V-2026-0010`).

## Key assumptions

- The existing `docs/configuration/README.md` index and module-level `CONFIGURATION.md` files form a sufficient corpus for the skill to traverse and surface relevant parameters per installation context.
- A single template is enough for the diversity of PME contexts at this stage; specialization per industry, scale, or compliance regime can come later if needed.
- The skill's Q&A design can be iterated based on pilot feedback without redesigning the template itself.
- Token-budget hygiene matters: the skill should not scan the full repo on every invocation; rely on the curated `CONFIGURATION.md` corpus and targeted reads, consistent with the broader skill-budget posture.

## Risks and exceptions

- The Q&A may bloat into "every conceivable question"; keep it focused on what materially affects the runtime profile.
- The bridge into `CONFIGURATION.md` may surface parameters that are not yet documented or are inconsistently structured; the pilot will reveal these gaps.
- The template may calcify too early; the first pilot should be treated as a learning artefact, not a final form.
- Without clear ownership of the document format, drift could occur once `I-2026-0018` (generator) starts depending on the format. Co-versioning policy must be agreed in `I-2026-0018`'s scope.

## Promotion notes

Move to `triaged` once the template structure and the skill scope are sketched. Promote to `incubating` once the pilot run is completed. Promote to `ready` for a tracer bullet once the format is validated and the storage location is decided.

## Links

- Vision: `V-2026-0010` (3-phase plan for per-installation profile elaboration).
- Companion: `I-2026-0018` (Phase 3 generator that depends on this Phase 2 output).
- Related reading: `docs/configuration/README.md` (configuration index), per-module `CONFIGURATION.md` files.
- Related principles: `#1` (simplicity), `#2` (essential vs accidental), `#5` (operator-first).
