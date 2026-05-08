# Backlog Idea — `I-2026-0018` Profile generator skill: elaboration document to runtime configs

## Metadata

- **ID:** `I-2026-0018`
- **Status:** `captured`
- **Priority:** `P2`
- **Created at:** `2026-05-08`
- **Updated at:** `2026-05-08`
- **Last reviewed at:** `2026-05-08`
- **Phase tags:** `P1-operability`, `P3-distribution`
- **Component tags:** `docs (product-docs)`, `methodology`, `skills`, `infra`

## Intent

Realize Phase 3 of `V-2026-0010`: design and build the AI-assisted skill that takes a Phase 2 elaboration document (from `I-2026-0017`) as input and produces the runtime configuration artefacts needed to deploy that profile — `application.properties` per Ezkey backend, Docker Compose profile files, environment files, and any other supporting artefact identified during piloting. Output must be **auditable, predictable, and reproducible** from the same input.

## Problem and value

- **Problem:** Even with a high-quality elaboration document (Phase 2 output), translating it into the actual runtime artefacts is currently a manual exercise — error-prone, hard to audit, and slow. Without a generator, the elaboration's value as a contract is reduced because the gap between intent and deployment remains entirely operator-managed.
- **Expected value:** Closed loop between elaboration and deployment. The same input (the Phase 2 document) deterministically produces the same artefacts. Operators iterate on the elaboration and re-generate without manual edits drifting between versions. The audit trail from intent to runtime is preserved.

## Scope

- **In scope:**
  - Define the **input contract** with `I-2026-0017` (which fields of the elaboration document drive which generation outputs).
  - Define the **generator skill** behavior: traversal of the elaboration document, mapping rules from elaboration sections to artefact fields, deterministic output.
  - Define the **artefact set produced**: `application.properties` per backend (`admin-api`, `auth-api`, `integration-api`), `docker-compose.yml` (or compose profiles), env files, any other supporting artefact identified during piloting.
  - Define the **idempotence and versioning posture**: re-running the generator on the same input produces the same output; output version aligns with the elaboration document's version.
  - Pilot the generation against the elaboration document produced by `I-2026-0017`'s pilot, end to end.
- **Out of scope:**
  - The actual deployment (running `docker compose up`); generation stops at producing the artefacts.
  - Bespoke migration logic between profile versions (separate problem if it emerges later).
  - Profile-aware code in the platform (explicitly excluded by `V-2026-0010`).

## Key assumptions

- The Phase 2 elaboration document format is stable enough to define a generation contract; the format is fully owned by `I-2026-0017`.
- The mapping from elaboration sections to runtime artefacts is mostly mechanical; LLM-assisted reasoning is needed only at the edges (interpretation of free-text sections, naming conventions).
- A single generator skill suffices initially; specialization per artefact type can be added later if needed.
- Generated artefacts are drafts that operators may inspect, override, or extend; the generator is not a black box.

## Risks and exceptions

- The generator may become a hidden authority over what artefacts look like — operators must retain visibility into generation rules and the ability to refine output without forking the skill.
- Drift between the elaboration document format and the generator (one evolves, the other lags) — co-versioning policy must be clear from day one and stated explicitly in this item's promotion criteria.
- Edge cases (clusters, custom networking, external secret management) may require manual touch-ups; the generator should produce drafts that operators can refine, not unmovable artefacts.

## Promotion notes

**Blocked on `I-2026-0017` reaching at least `incubating`** (the elaboration format must be stable before the generation contract is meaningful). Move to `triaged` after `I-2026-0017` is `triaged`. Promote to `incubating` once the elaboration format is piloted and stable.

## Links

- Vision: `V-2026-0010` (3-phase plan).
- Dependency: `I-2026-0017` (Phase 2 elaboration; this item is its consumer).
- Related principles: `#1` (simplicity), `#2` (essential vs accidental — generator stays mechanical), `#13` (open-source transparency — generated artefacts are inspectable).
