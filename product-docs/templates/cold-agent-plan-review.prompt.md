# Prompt Template — Cold-Agent Plan Hardening & Review

Use this prompt in a **new session** (ideally with a fresh context and potentially a distinct
reasoning model) to critically review, stress-test, and harden an existing artifact before the
next gate or before handing it off to an autonomous execution agent.

**Aliases for the same pattern** (do not invent a parallel method): `critical-review`,
`critical review`, `revue critique`, `adversarial review`, `plan hardening`,
`cold-agent plan review`, Stage 2 fresh-session review. Canon:
[`../methodology/README.md`](../methodology/README.md) § *The two-stage plan hardening pattern*.
Skill entry point (thin launcher only): [`.cursor/skills/critical-review/SKILL.md`](../../.cursor/skills/critical-review/SKILL.md).

**Targets:** vision note (`V-*`), **G1 tracer-bullet brief** (before craft/security gate),
execution plan, tracer bullet, or a PR that carries one of those. Apply before G1 for briefs —
not only after vision merge.

---

```markdown
I am submitting this artifact for your critical / adversarial review (plan hardening Stage 2):
@path/to/vision-brief-plan-or-pr.md

I expect you to evaluate it rigorously against our product intent and harden it so that the next
gate (or an autonomous cold agent with zero conversational memory) can proceed without hidden
assumptions.

## Compass (read briefly first)

- `AGENTS.md` (§ Cold-start, § Project values)
- `product-docs/global/product-intent.md`
- `product-docs/global/design-principles.md` (especially **§17** fail-open vs fail-closed)
- `product-docs/global/operator-alignment-guide.md` (adopter posture: Ezkey is never the
  adopter's core business, 3-second decision test)
- `product-docs/methodology/README.md` (values compass, three rules, two-stage pattern)
- Then only what the target needs (security posture, lifecycle, module `AGENTS.md`, host/deploy docs).

## Review dimensions

1. **Alignment with Product Intent & Normative Posture:**
   - Compare against product intent, design principles (80/20, one canonical place, stay in stack,
     §17 fail-open/fail-closed), and security/lifecycle posture.
   - Verify the diagnosis and proposed direction solve the root cause rather than patching a
     symptom or leaving default paths unprotected.
   - Challenge the premise: is the problem real and correctly sized? Simplest alternative not
     considered? What happens if we do nothing?

2. **Brief / vision gates — mandatory specialist angles (retro #726):**
   When the target is a **vision** or **G1 brief**, explicitly invite (and report on) these angles;
   name gaps even if the artifact is silent. Definitions must match the brief template (owned by
   the parallel #726 discoverability docs PR — do not redefine them differently here):
   - **Vision lock trace:** a **table** mapping each vision lock → brief section, or an explicit
     deferral + reason. An **unmapped lock blocks G1**.
   - **Pre-existing conditions touched** (runtime paths only; docs-only briefs exempt): a
     `path:line` inventory of waits/long-polls and cadences; client/SDK timeouts; proxy timeouts
     (Cloudflare, Caddy); TTLs; concurrency; rate limits; dependency loggers and effective log
     level; error-detection contract (classify by **exception type**, not message; fail-open/
     fail-closed per `design-principles.md` §17); existing user-facing copy on the path.
   - **Operability / real tester journey** (Julie): end-user or cohort path on a real device or
     clean-start stack — not only happy-path admin chrome; also a **keep / adapt / hide** pass on
     existing UI content for a new mode, and **visual composition**.
   - **Craft / pre-existing conditions & concurrency** (Patrick): challenge the inventory above
     (races, CAS, shared state, dual writers, timeout stacking).
   - **Security** (Christophe): secrets, enumeration, rate limits, session, claim honesty.
   - **Host / deploy** (Edgar): ≤5 lines marked `[live]` or `[repo]`: IP chain and trusted
     headers, ports, proxy timeouts, runtime output of all containers for "never logged"
     properties, deploy pitfalls. A header-trust point marked `[repo]` **blocks GO** until proven
     live.

3. **Cold-Agent Autonomy & Missing Specifics:**
   - Identify edge cases, hidden assumptions, or ambiguities that would cause an autonomous agent
     to stall, guess, or take invalid shortcuts.
   - Specify exact implementation details: Flyway versions/naming, transactional boundaries,
     repository queries (e.g. atomic CAS), DTO field types, Checkstyle/lint constraints, and
     strict tooling commands (e.g. never hand-editing generated OpenAPI specs).

4. **Proportionate Proof Ladder & Collateral Invariance:**
   - **RED Baseline:** An automated characterization test that reproduces the defect/gap before
     any code is changed.
   - **Positive & Negative Tests:** Both nominal success paths and explicit fail-closed rejection
     on invalid inputs or unauthorized actors.
   - **Collateral Invariance & State Survival (Crucial):** Explicitly verify that rejections,
     collisions, or attack attempts leave legitimate existing state completely unharmed:
     - *Security/Auth:* Legitimate sessions, tokens, or credentials remain active and operational
       post-attack (e.g. exercising an authenticated administrative mutation post-rejection).
     - *Concurrency/CAS:* Competing operations failing a lock or CAS condition do not abort,
       deadlock, or corrupt the winning operation.
     - *Lifecycle/Entities:* Rejected lifecycle transitions (e.g. 409 Conflict, 400 Bad Request)
       do not leave records in an inconsistent, partially mutated, or corrupted state.
   - **Live Functional Verification:** A realistic end-to-end test on the running stack
     (clean-start Docker, Demo Device, Admin UI, or Bruno collections) with observable evidence.

5. **Review Protocol (HITL):**
   - Do NOT edit the artifact immediately.
   - First, provide your critical evaluation, identified gaps, and proposed amendments in a
     concise summary (prefer few strong findings; max ~10; no style nits).
   - Wait for feedback so we can align (`amend` / `defer` / `skip` per finding) before amending
     the file.
```

---

## When to Use This Pattern

Trigger this two-stage review when:
- **G1 briefs** after vision lock (and optionally visions before merge when risk is high).
- **Security & Cryptography:** Authentication, token issuance, session lifecycles, key rotation,
  or permission checks.
- **Concurrency & State Integrity:** Database migrations on partitioned tables, atomic CAS,
  distributed locks (ShedLock), or queue consumers.
- **Cross-Component Workflows:** Slices spanning DB schema → Core → REST APIs → OpenAPI → Admin UI
  → Mobile/Demo Device / host.
- **Full Delegation:** When the goal is an uninterrupted, autonomous execution run by a cold agent.

**Not for:** routine hygiene, Dependabot, or when a `*-curated` lane already owns the signal.
