# Mobile protocol / crypto security - campaign notes

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

Do **not** use this folder for live API DAST (`security-pentest-curated`) or Admin UI React Doctor.

## Contents

| Path | Role |
| --- | --- |
| [`TEMPLATE.md`](TEMPLATE.md) | Copy for each new campaign / lot |
| `YYYY-MM-DD-pass-N.md` | Dated instance with assessment link, lot, decisions, and rationale |

## Operating contract

1. Produce or update a formal assessment under `docs/security/`.
2. Propose a small lot (usually 3–6 findings).
3. Review **one finding at a time** with the operator (no bulk options matrix as the primary vehicle).
4. Record decisions as `fix`, `defer`, `suppress`, or `skip` with short rationale.
5. For each **accepted** fix, create one cold-agent handoff under
   `product-docs/global/backlog/handoffs/`.
6. Escalate only material protocol redesigns into `I-*` / `TB-*` + labeled GitHub issue.

## Related

- Assessment (2026-07): [`../../../docs/security/mobile-protocol-crypto-assessment-2026-07.md`](../../../docs/security/mobile-protocol-crypto-assessment-2026-07.md)
- Prior mobile assessment: [`../../../docs/security/mobile-security-assessment-2026-05.md`](../../../docs/security/mobile-security-assessment-2026-05.md)
- Comparable hygiene lanes: [`../security-pentest/README.md`](../security-pentest/README.md), [`../java-doctor/README.md`](../java-doctor/README.md)
- Hygiene vs program: [`../../methodology/decisions/2026-06-06-methodological-closeout-vs-code-hygiene.md`](../../methodology/decisions/2026-06-06-methodological-closeout-vs-code-hygiene.md)
