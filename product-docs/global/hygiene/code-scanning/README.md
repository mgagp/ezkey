# Code scanning (CodeQL SAST) — campaign notes

Lightweight hygiene lane for **GitHub CodeQL / code-scanning** triage and config decisions.

This folder is peripheral to product vision / ADR / backlog execution. It records per-pass SAST
triage (fix / dismiss / defer / config) so cold sessions can see *why* an alert was acted on —
without inventing `I-*` / `TB-*` / GitHub issues per routine item. Aligns with
[`../../methodology/README.md`](../../methodology/README.md) § *Three rules worth keeping*
(hygiene vs program).

## Scope

Use this folder for:

- CodeQL alert triage campaign notes (`YYYY-MM-DD-pass-N.md`),
- decisions that land primarily as dismissals, `paths-ignore`, or model-pack barriers,
- pointers into the durable policy in [`../../../../docs/SECURITY_POSTURE.md`](../../../../docs/SECURITY_POSTURE.md)
  § Code scanning policy.

Do **not** use this folder for live DAST / API fuzzing — that stays in
[`../security-pentest/`](../security-pentest/) (`security-pentest-curated`). SCA companion tools
stay out unless methodology explicitly merges lanes.

## Contents

| Path | Role |
| --- | --- |
| `YYYY-MM-DD-pass-N.md` | Dated triage pass (alerts, lots, dismiss list, decision owners) |
| First instance | [`2026-10-13-pass-01.md`](2026-10-13-pass-01.md) — initial open-alert triage |

## Operating contract

1. Pull open alerts (`state=open`) and triage against source on `main`.
2. Classify: fix lot / false positive dismiss / won't fix / used in tests / defer with issue.
3. Record decisions here; durable rules go in `docs/SECURITY_POSTURE.md` (and `AGENTS.md` when
   merge-ready or logging mandate wording changes) in the same PR.
4. Do not invent `I-*` / `TB-*` per alert — escalate only when the operator funds a program-sized
   redesign (or a deferred hardening idea such as PAM `/tmp`).

## Related

- Policy: [`../../../../docs/SECURITY_POSTURE.md`](../../../../docs/SECURITY_POSTURE.md) § Code scanning policy
- Config: [`.github/codeql/codeql-config.yml`](../../../../.github/codeql/codeql-config.yml),
  [`.github/workflows/codeql.yml`](../../../../.github/workflows/codeql.yml)
- LogSanitizer production wrap: [#801](https://github.com/mgagp/ezkey/issues/801)
- CLI TLS deferral: [#802](https://github.com/mgagp/ezkey/issues/802)
- Sibling DAST lane: [`../security-pentest/README.md`](../security-pentest/README.md)
