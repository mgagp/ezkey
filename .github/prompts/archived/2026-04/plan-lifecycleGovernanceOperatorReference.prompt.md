## Plan: Ezkey Lifecycle Governance — Operator Reference Document

> **Status: COMPLETED — April 2026**

Produce a single English markdown reference document (`docs/LIFECYCLE_GOVERNANCE.md`) that gives an Ezkey operator the complete mental model of the domain entities, their relationships, lifecycle rules, guardrails, and real-world rationale. Recalibrate the exploratory analysis tables from the governance v1 into definitive reference tables reflecting implemented behavior. 4 Mermaid diagrams support key concepts visually. Balance conceptual completeness with operational brevity.

---

### Document Structure (6 sections)

**§1 — Introduction**
One paragraph: what this document is, who it's for (operator running Ezkey in production).

**§2 — The Ezkey Domain at a Glance**
High-level entity map with a **Mermaid entity-relationship diagram** (refined from v1 §5). One sentence per entity explaining what it is and why it exists. Establishes the hierarchy: Tenant → Integration → Enrollment / API Key, Admin → Tenant, Encryption Key as a cross-cutting protection asset. This is the "first 2 minutes" for a new operator.

**§3 — Core Concepts** (3 subsections)
- **Operational Status & Eligibility Chain**: what `operational` means, how it propagates top-down with no persistent cascades. **Mermaid flowchart** of the evaluation order (local → parent → tenant → guards).
- **Reversible vs. Irreversible Actions**: deactivate/reactivate vs. revoke/retire/delete — the operator's decision framework. Investigation defaults to reversible; terminal actions for confirmed compromise only.
- **Reason Policy**: when a reason is required (irreversible), when optional (reversible), why — anchored in real-world auditability.

**§4 — Entity Reference** (one subsection per entity, same template)
Each entity follows: **Purpose** → **Lifecycle** (Mermaid state diagram where useful) → **Available Actions** (compact table) → **Guardrails & Rationale** (why certain things are blocked, with real-world justification) → **Downstream/Upstream Impact**.

Entity order top-down:
- 4.1 Tenant — active/inactive toggle, master organizational switch, blast radius
- 4.2 Integration — ACTIVE/RETIRED (why INACTIVE was removed), retire vs delete, **Mermaid lifecycle diagram**
- 4.3 Enrollment — the most complex FSM, deactivate vs revoke distinction, delete constraints, **Mermaid state diagram**
- 4.4 API Key — revoke-only model, why no reactivation, why no delete
- 4.5 Admin — Global vs Tenant Admin, identity vs credential separation
- 4.6–4.8 Auth Attempt, Encryption Key, Recovery Codes (brief — secondary for daily operations)

**§5 — Operational Scenarios** (4–5 walkthroughs)
- Suspected credential compromise → deactivate → investigate → revoke or reactivate
- API key leak → revoke + rotate
- Decommissioning an application → retire integration → clean up → optionally delete
- Emergency tenant suspension → deactivate → everything blocked at runtime → reactivate
- Admin departure → deactivate admin → revoke MFA enrollment if confirmed

**§6 — Quick Reference** (two summary tables)
- Action Matrix: entity × action grid (Create / Deactivate / Reactivate / Revoke / Retire / Delete) with ✓/✗/conditional
- Reason Policy Matrix: entity × action → Required / Optional / N/A

---

### Mermaid Diagrams (4)

1. **Entity Relationship** (§2) — domain hierarchy with clear labels
2. **Eligibility Evaluation Chain** (§3) — local → parent → tenant → guards → allow/block
3. **Enrollment Lifecycle FSM** (§4.3) — PENDING → VERIFIED (active) ⇄ VERIFIED (inactive) → REVOKED
4. **Integration Lifecycle** (§4.2) — ACTIVE → RETIRED → (guarded) Delete

---

### Recalibration from Analysis to Reference

The v1 governance §7 tables were written in exploratory/decision mode ("Target rule", "Recommended", "Decision locked"). The new document:
- Removes all exploratory framing — presents as definitive system behavior
- Simplifies rationale to operator-facing language (not implementation justification)
- Compacts tables: fewer columns, more direct language
- Adds "Effect on children" column (was scattered across §7 and §8 in the analysis)
- Speaks in present tense: "this is how it works", never "we decided" or "we recommend"

---

### Source Material

- **Operator canon (analysis session closed):** [`docs/LIFECYCLE_GOVERNANCE.md`](../../../docs/LIFECYCLE_GOVERNANCE.md)
- **Transverse governance v1 (canonical decisions)**: `plans/entity_lifecycle_governance_transverse_v1.plan.md`
- **Backend implementation (Plan 1, shipped):** `docs/LIFECYCLE_GOVERNANCE.md` (operator) + `ezkey-core` `EntityEligibilityService` (`org.ezkey.service`).
- **Admin UI (Plan 2, shipped):** `ezkey-admin-ui/AGENTS.md` § Detail Danger Zone (`OperationalWarning` when native status looks healthy but `operational === false`). Operator canon: [`docs/LIFECYCLE_GOVERNANCE.md`](../../../docs/LIFECYCLE_GOVERNANCE.md).

---

### Steps

| Phase | Steps | Description |
|-------|-------|-------------|
| **1 — Scaffolding & Mental Model** | 1–3 | Create file, write §1–3 (intro, domain overview + Mermaid, core concepts + Mermaid) |
| **2 — Entity Reference** | 4–9 | Write §4.1–4.8, one entity at a time top-down, including Mermaid diagrams for Enrollment and Integration |
| **3 — Scenarios & Quick Ref** | 10–11 | Write §5 scenarios and §6 summary tables |
| **4 — Review & Polish** | 12–14 | Coherence check, cross-check vs. Plans 1 & 2, trim to length target |

---

### Length Target

~800–1200 lines of markdown (~15–20 printed pages). The mental model sections (§1–3) readable in ~10 min. Entity reference (§4) scannable by entity. Quick reference tables (§6) usable in 30 seconds for the "3 months later, first key rotation" scenario.

---

### Decisions

- **Single document, not two.** The mental model and operational reference fuse well — entity reference sections serve both purposes (model comprehension via structure/rationale; operational reference via action tables/guardrails). Splitting would force duplication.
- **Output**: `docs/LIFECYCLE_GOVERNANCE.md` — English, markdown, UTF-8 no BOM
- **No implementation details**: no Java class names, no API endpoints, no DTO field names. This is a domain/operational document.
- **Auth Attempt, Encryption Key, Recovery Codes**: brief subsections — secondary for daily operator work.
- **Exploratory language purged**: present tense about how the system works.

---

### Verification

1. Every entity from v1 §7 appears in the entity reference
2. Every locked decision from v1 §11 is reflected as definitive behavior
3. All 4 Mermaid diagrams render correctly
4. Action matrix (§6) consistent with per-entity tables (§4)
5. Reason policy matrix (§6) consistent with Plan 1 Phase G
6. No exploratory language remains ("proposed", "recommended", "candidate")
7. Length within 800–1200 lines
