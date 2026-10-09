# Prompt Template — Cold-Agent Plan Hardening & Review

Use this prompt in a **new session** (ideally with a fresh context and potentially a distinct reasoning model) to critically review, stress-test, and harden an existing plan or tracer bullet before handing it off to an autonomous execution agent.

Stage 2 is tooled by the `critical-review` keyword (adversarial review); see
[`product-docs/global/hygiene/critical-review/README.md`](../global/hygiene/critical-review/README.md).
This file remains the **autonomous-execution hardening** angle included by reference from that prompt.

---

```markdown
I am submitting this plan for your critical review: @path/to/plan-or-tracer-bullet.md

I expect you to evaluate it rigorously against our product intent and harden it so that an autonomous cold agent (with zero conversational memory) can execute it flawlessly end to end.

Please structure your review along the following dimensions:

1. **Alignment with Product Intent & Normative Posture:**
   - Compare the plan against our canonical product intent, design principles (e.g., fail-closed at boundaries, 80/20 simplicity), and security/lifecycle posture.
   - Verify that the diagnosis, the observed behavior, and the proposed fix genuinely solve the root cause rather than patching a symptom or leaving default paths unprotected.

2. **Cold-Agent Autonomy & Missing Specifics:**
   - Identify edge cases, hidden assumptions, or ambiguities that would cause an autonomous agent to stall, guess, or take invalid shortcuts.
   - Specify exact implementation details: exact database migrations (Flyway versions/naming), transactional boundaries, repository queries (e.g. atomic CAS), DTO field types, Checkstyle/lint constraints, and strict tooling commands (e.g. never hand-editing generated OpenAPI specs).

3. **Proportionate Proof Ladder & Collateral Invariance:**
   - **RED Baseline:** An automated characterization test that reproduces the defect/gap before any code is changed.
   - **Positive & Negative Tests:** Both nominal success paths and explicit fail-closed rejection on invalid inputs or unauthorized actors.
   - **Collateral Invariance & State Survival (Crucial):** Explicitly verify that rejections, collisions, or attack attempts leave legitimate existing state completely unharmed:
     - *Security/Auth:* Legitimate sessions, tokens, or credentials remain active and operational post-attack (e.g. exercising an authenticated administrative mutation post-rejection).
     - *Concurrency/CAS:* Competing operations failing a lock or CAS condition do not abort, deadlock, or corrupt the winning operation.
     - *Lifecycle/Entities:* Rejected lifecycle transitions (e.g. 409 Conflict, 400 Bad Request) do not leave records in an inconsistent, partially mutated, or corrupted state.
   - **Pre-existing path conditions:** Inventory pre-existing timeouts, waits, rate limits, and error paths on the touched path (`path:line`), and verify the change does not break them.
   - **Live Functional Verification:** A realistic end-to-end test on the running stack (clean-start Docker, Demo Device, Admin UI, or Bruno collections) with observable evidence (logs, DB state, audit records).

4. **Review Protocol (HITL):**
   - Do NOT edit the plan immediately.
   - First, provide your critical evaluation, identified gaps, and proposed amendments in a concise summary.
   - Wait for my feedback so we can align on the exact strategy before amending the plan file.
```

---

## When to Use This Pattern

Trigger this two-stage review when:
- **Security & Cryptography:** Authentication, token issuance, session lifecycles, key rotation, or permission checks.
- **Concurrency & State Integrity:** Database migrations on partitioned tables, atomic CAS operations, distributed locks (ShedLock), or queue consumers.
- **Cross-Component Workflows:** Slices spanning DB schema $\to$ Core service $\to$ REST APIs $\to$ OpenAPI specs $\to$ Admin UI $\to$ Mobile/Demo Device.
- **Full Delegation:** When the goal is an uninterrupted, autonomous execution run by a cold agent.
