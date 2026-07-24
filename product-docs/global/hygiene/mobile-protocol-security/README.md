# Mobile protocol / crypto security - campaign notes

**Method:** this folder is the first topic-lane **instance** of **`assessment-curated`**.  
Canon: [`../assessment-curated/README.md`](../assessment-curated/README.md). Skill:
[`.cursor/skills/assessment-curated/SKILL.md`](../../../../.cursor/skills/assessment-curated/SKILL.md).

Lightweight HITL decision track for punctual **non-intrusive** mobile protocol and crypto
assessments of the Android-first Ezkey reference app and Auth API continuity.

This folder is peripheral to product vision / ADR / backlog execution. It records per-campaign
triage so cold sessions can see why a finding was fixed, deferred, suppressed, or skipped without
creating one `I-*`, `TB-*`, or GitHub issue per item.

## Scope

Use this folder for:

- white-box mobile / protocol / crypto assessment follow-ups,
- Keystore / StrongBox / sealed-secrets / local-auth claim integrity,
- Auth API bind/verify/pending/respond continuity as it affects the mobile client,
- documentation and positioning honesty around hardware and local confirmation.

Do **not** use this folder for live API DAST (`security-pentest-curated`), Admin UI React Doctor,
or a *new* assessment topic (create another lane folder under `hygiene/` and follow
`assessment-curated`).

## Contents

| Path | Role |
| --- | --- |
| [`TEMPLATE.md`](TEMPLATE.md) | Copy for each new campaign / lot (lane-specific); prefer method templates under [`../assessment-curated/`](../assessment-curated/) for new lanes |
| `YYYY-MM-DD-pass-N.md` | Dated instance — **canonical remediation register** after closeout |

## Operating contract

Follow `assessment-curated` ceremony. Local reminders:

1. Produce or update a formal assessment under `docs/security/`.
2. Propose a small lot (usually 3–6 findings).
3. Review **one finding at a time** with the operator (briefing + code citations + options).
4. Record decisions as `fix`, `defer`, `suppress`, or `skip` with short rationale.
5. While a finding is **in flight**, optional ephemeral cold-agent handoff under
   `product-docs/global/backlog/handoffs/`. On closeout: consolidate into this campaign note and
   **delete** the handoff.
6. Escalate only material protocol redesigns into `I-*` / `TB-*` + labeled GitHub issue.

## Current pass

| Pass | Status | Notes |
| --- | --- | --- |
| [`2026-07-19-pass-2.md`](2026-07-19-pass-2.md) | **Open** | MOB-011/012/013/014/016 Fixed; MOB-015 still open |
| [`2026-07-16-pass-1.md`](2026-07-16-pass-1.md) | **Closed** | Lot A remediations done; Lot B documentary / claim-honesty verified 2026-07-19 (assessment §13); do not reopen without new evidence |

## Related

- Assessment (2026-07 + pass-2 §14): [`../../../docs/security/mobile-protocol-crypto-assessment-2026-07.md`](../../../docs/security/mobile-protocol-crypto-assessment-2026-07.md)
- Prior mobile assessment: [`../../../docs/security/mobile-security-assessment-2026-05.md`](../../../docs/security/mobile-security-assessment-2026-05.md)
- Comparable hygiene lanes: [`../security-pentest/README.md`](../security-pentest/README.md), [`../java-doctor/README.md`](../java-doctor/README.md)
- Hygiene vs program: [`../../methodology/decisions/2026-06-06-methodological-closeout-vs-code-hygiene.md`](../../methodology/decisions/2026-06-06-methodological-closeout-vs-code-hygiene.md)
