# Normative Posture

Ezkey is security-sensitive MFA infrastructure. Operational discipline that a self-hoster's GRC
team — or a future auditor — could map onto **SOC 2 Trust Service Criteria** is a product quality.
SOC 2 is the **reference vocabulary**, not a certification project and not a claim.

This page is the single canon for that stance. Honest security claims live in
[`docs/SECURITY_POSTURE.md`](../../docs/SECURITY_POSTURE.md). Git history holds the retired
18-month SOC 2 preparation cluster (`docs/SOC2_PREPARATION.md` and siblings).

## Who this is for

- **Internal maintainers** — why a well-defined normative posture still matters, and why it must
  not become a parallel program.
- **Evaluators and adopters** — we align practices; we do not ship a SOC 2 Type I/II report or
  claim standards equivalence.
- **Cold agents** — how to interpret "SOC 2" in analysis, design, and implementation without
  inventing a compliance roadmap.

## Priority

Current funded work is **continuous product and code hygiene** under an intention-first laboratory
/ public-alpha posture — not a September P0 release gate. See
[`vision/V-2026-09-26-laboratory-alpha-posture-lock.md`](vision/V-2026-09-26-laboratory-alpha-posture-lock.md).
Historical Waves A–D ordering lives in
[`operational-readiness-prioritization-2026-09.md`](operational-readiness-prioritization-2026-09.md)
(superseded as live compass). That history already stated that operable does **not** mean full
SOC 2 certification.

Normative posture **orients** design. It does not outrank the live posture lock, invent control
coverage percentages, or justify ceremony that does not improve the product.

## What we aim for

Designs and operator paths that stay mappable:

- Durable, operator-visible evidence for sensitive actions (audit trail, reason/justification where
  the product already requires it).
- Least privilege and the Global Admin / Tenant Admin split.
- Documented key lifecycle and encryption-at-rest posture.
- Vulnerability disclosure ([`SECURITY.md`](../../SECURITY.md)) and dependency hygiene (existing
  lanes such as `dependabot-curated`).
- An honest threat model — tamper-evident, not tamper-proof; fail-open vs fail-closed named at
  real boundaries.

Roadmap milestone `P4-compliance-readiness` is this quality as **operational discipline**
(provisioning procedures, recovery-code lifecycle, stable audit artifacts). It is not "start an
auditor engagement."

## What we do not do

- No SOC 2 Type I or Type II, no ISO 27001, no attestation-equivalence claim.
- No Comply, Probo, or other compliance-as-code farm as a project goal.
- No auditor evidence repository, mock-audit program, or 12–18 month control-coverage roadmap.
- No "working towards SOC 2 certification" language in public or operator-facing material.

## Product terms and future mapping

Living code and operator docs use the **product terms** below. They do **not** cite Trust Service
Criteria at the call site. This table is the reverse-lookup for a later gap analysis: grep the
product term, then map here. It is **not** a control-coverage claim.

| Product term | Typical TSC (reference only) | Primary Ezkey locus |
| --- | --- | --- |
| Identifiable operator identity | CC6.1 | Initial Global Admin bootstrap; Global Admin email / first / last name |
| Least privilege / role split | CC6.1, CC6.2 | Global Admin vs Tenant Admin; object checks on tenant-owned rows |
| Rapid access removal | CC6.3, CC6.5 | Admin / tenant deactivate; enrollment revoke; token invalidation |
| Encryption-at-rest key lifecycle | CC6.6, CC6.7, CC6.8 | Tink keyset, rotation, re-encryption batches |
| Operator-visible audit trail | CC7.2 | `ezkey_audit_log`; who / what / when on sensitive actions |
| Tamper-evident monitoring | CC7.2 | Per-entry HMAC, checkpoints, conciliation as explanation not rewrite |
| Reason / justification on sensitive change | CC6.3, CC8.1 | Deactivate, revoke, encryption-key and chain ops |
| License / dependency hygiene | CC7.1, CC8.1 | OSS license review; `dependabot-curated` |

Do **not** copy the TSC column into Javadoc, properties, Flyway comments, or validation messages.

## Agent translation

When a request, comment, or older doc says "for SOC 2," the real requirement is almost always
**product terms**:

- Keep a durable operator-visible narrative; do not silently erase it.
- Record who did what, with reason/justification on sensitive ops.
- Respect role split and least privilege.
- Name fail-open vs fail-closed at the actual integrity/availability boundary.

Implement that in Ezkey's existing model (audit log, alerts, lifecycle, Admin UI). Do **not**
create SOC 2 roadmaps, policy template farms, Comply setup, or certification checklists. Do **not**
reintroduce CC IDs or “required for SOC 2 compliance” at the call site — use the product term and,
if a mapping is needed, link this page.

The hygiene lanes already in the repo (`java-doctor-curated`, `dependabot-curated`,
`security-pentest-curated`, `assessment-curated`) **are** the continuous discipline. Do not invent
a parallel compliance program.

## Related documents

- [`product-intent.md`](product-intent.md) — product identity and non-goals.
- [`roadmap.md`](roadmap.md) — milestone `P4-compliance-readiness`.
- [`design-principles.md`](design-principles.md) — especially security as posture, not a bolted-on
  feature.
- [`docs/SECURITY_POSTURE.md`](../../docs/SECURITY_POSTURE.md) — honest claims and threat model.
- [`vision/V-2026-09-26-laboratory-alpha-posture-lock.md`](vision/V-2026-09-26-laboratory-alpha-posture-lock.md)
  — live laboratory / public-alpha posture lock.
- [`operational-readiness-prioritization-2026-09.md`](operational-readiness-prioritization-2026-09.md)
  — historical Waves A–D release-order record.
