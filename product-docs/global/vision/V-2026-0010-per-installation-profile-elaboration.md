# V-2026-0010 — Per-installation profile elaboration via AI-assisted methodology and generation

- **Date:** `2026-05-08`
- **Status:** `draft`
- **Supersedes:** `V-2026-0002` (original transversal "deployment profiles" platform concept;
  reformulated during the 2026-05-08 grilling session into the 3-phase plan below).
- **Intent:** Realize the "deployment profile" need through a 3-phase orthogonal approach that
  keeps the platform free of profile-aware code.
  - **Phase 1 — Clean Start status quo (acknowledged, no new platform work).** The existing
    `clean-start.sh` script with default options plus activable parameters **is** the dev/QA/test
    profile mechanism today. Phase 1 = editorial clarification in operator documentation that this
    is the chosen mechanism for those contexts. Future-direction note (out of current scope):
    possibility of grouping parameters into named presets within the script, evolving toward
    profile-like compositions for that audience.
  - **Phase 2 — Profile elaboration session (per installation).** Template plus AI-assisted skill
    plus methodology that pilots a structured Q&A session producing a per-installation/per-client
    profile elaboration document. Inputs: an environment questionnaire (Cloudflare, other proxies,
    redundancy needs, capacity, trusted proxy, network topology) and a guided traversal of the
    existing `CONFIGURATION.md` corpus to surface parameters relevant to the client's context.
    Output: a focused, pragmatic, versionable document suitable for sharing with the client as a
    basis for discussion or contract.
  - **Phase 3 — Profile generation from elaboration document.** AI-assisted skill that takes a
    Phase 2 document as input and produces the runtime artefacts needed to deploy that profile —
    `application.properties` per backend, Docker Compose profile files, environment files.
    Auditable, deterministic, no magic.
- **Signals:** Reformulation born from the 2026-05-08 grilling of `V-2026-0002`. The original
  framing risked profile-aware platform code (accidental complexity per `Design Principle #2`).
  The 3-phase reframing keeps profile knowledge external to the platform, aligning with `#1`
  simplicity, `#2` essential vs accidental, and `#5` operator-first. The existing `clean-start.sh`
  mechanism and the colocated `CONFIGURATION.md` corpus are durable foundations that this approach
  builds on rather than replaces.
- **Potential impact:** **Phase 1:** documentation clarification only. **Phase 2:** new template,
  new skill, per-client elaboration document workflow, cross-cutting traversal of
  `docs/configuration/README.md` and module-level `CONFIGURATION.md` corpus. **Phase 3:** new
  generator skill with a stable input contract from Phase 2; produces config artefacts that
  operators run with Docker Compose. **Cross-impact:** items that previously cited `V-2026-0002`
  (`V-2026-0003`, `V-2026-0005`, `V-2026-0007`, `I-2026-0011`) keep their references valid
  because the archived note remains findable; their links may be redirected to `V-2026-0010`
  opportunistically when those items are next worked.
- **Next step:** Phase 2 actionable promoted into `I-2026-0017` (template + skill design), Phase
  3 actionable promoted into `I-2026-0018` (generator). Phase 1 needs only a small documentation
  clarification, no dedicated `I-*`. After `I-2026-0017` produces a stable elaboration format,
  `I-2026-0018` becomes ready for execution.

## Related artifacts

- `V-2026-0002` — Deployment profiles (archived; superseded by this note)
- `I-2026-0017` — Profile elaboration template and skill
- `I-2026-0018` — Profile generator skill
- `I-2026-0011` — Bootstrap clean-start: activation-code mode as default
