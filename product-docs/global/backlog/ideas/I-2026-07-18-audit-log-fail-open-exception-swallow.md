# Backlog Idea — `I-2026-07-18` Audit-log write fail-open (exception swallow) and delivery honesty

## Metadata

- **ID:** `I-2026-07-18-audit-log-fail-open-exception-swallow`
- **Status:** `captured`
- **Priority:** `P2`
- **Created at:** `2026-07-18`
- **Updated at:** `2026-07-18`
- **Last reviewed at:** `2026-07-18`
- **Progression markers:** `P2-hardening`
- **Component tags:** `core`, `audit`, `admin-api`, `auth-api`, `integration-api`, `docs`, `security`
- **Lane:** `D`
- **Captured by:** Marc (discovered during design of `I-2026-07-18-audit-log-peripheral-insert-only-hmac-sequence`)

## Intent

Make Ezkey’s audit **delivery** posture explicit, honest, and improvable: today
[`AuditLogService.log()`](../../../../ezkey-core/src/main/java/org/ezkey/audit/service/AuditLogService.java)
absorbs all write/seal failures so business operations never fail because audit persistence failed
(**fail-open**). That availability trade-off is deliberate and reasonable, but it means integrity
controls that verify **present** rows cannot prove that every security-relevant operation left a
corresponding audit event. Capture the phenomenon, its consequences, and a bounded improvement path
(policy, observability, and messaging) for a later session — without conflating it with the
peripheral INSERT-only HMAC seal work.

## Problem and value

- **Problem:** `log()` runs in `PROPAGATION_REQUIRES_NEW` and wraps the write in a broad
  `catch (Exception)` that logs at ERROR and returns. Callers continue. Effects:
  1. A successful business operation can leave **no** audit row (omission at source).
  2. Per-entry HMAC, chain checkpoints, nightly/retroactive validation, and integrity electives
     protect **tamper-evidence of what was stored** — they do **not** detect “operation happened,
     row never inserted.”
  3. Checkpoint **heartbeat** fail-closed protects against prolonged **chain/scheduler** stall; it
     does **not** guarantee per-operation audit ack (isolated INSERT failures while other traffic
     still audits can go unnoticed except for application ERROR logs).
  4. Product/Javadoc wording such as “comprehensive audit trails” overstates delivery certainty
     relative to this fail-open path.
- **Expected value:** Discoverable backlog record of the trade-off; later grill can decide which
  events (if any) should **fail-closed**, which stay fail-open, what durable signals (metric/alert)
  replace log-only visibility, and how public/security docs stay **honest without overclaim**.
  Avoids forgetting a real integrity-adjacent gap while the INSERT-only grant hardening proceeds.

## Observed behaviour (code)

```text
business TX ──► AuditLogService.log()
                    │
                    ├─ REQUIRES_NEW TX: allocate / insert / seal (atomic today)
                    │     success → commit sealed row
                    │     failure → rollback that TX only
                    │
                    └─ catch Exception → logger.error → return (caller continues)
```

- **Availability intent:** audit must not take down MFA / admin / integration paths.
- **Integrity intent (orthogonal):** once a row exists and is sealed, tamper is detectable.
- **Gap:** delivery is best-effort; omission is possible and not integrity-alerted today.

## Scope

- **In scope (later session):**
  - Policy design: which event classes are fail-open vs fail-closed (or “fail-closed after N”).
  - Observability: counter/alert on absorbed audit write failures (beyond ephemeral ERROR logs).
  - Optional tests: “operation succeeded → corresponding signed audit row present” for critical
    paths (not only elective HMAC-on-existing-rows).
  - Honest messaging: `SECURITY_POSTURE`, integrity honest-line, and service Javadoc — **targeted**,
    not a corpus-wide wording hunt.
- **Out of scope (this capture / sibling work):**
  - Changing fail-open behaviour inside
    [`TB-2026-07-18-audit-log-insert-only-hmac-seal`](../TB-2026-07-18-audit-log-insert-only-hmac-seal.md)
    (that TB improves **immutability of sealed rows** and peripheral grants; it does **not** fix
    delivery).
  - Moving HMAC into the database or requiring audit success for every endpoint without grill.
  - Witch-hunt rewrites of every historical “audit trail” phrase in old feature docs.

## Key assumptions

- Fail-open remains a **defensible default** for many paths until a deliberate policy says otherwise.
- Existing integrity investment (HMAC, chain, heartbeat, nightly batch) is **more than reasonable**
  for what it covers; this idea does not dismiss that work — it names a **different** guarantee
  (delivery vs tamper-evidence).
- Priority **P2** is correct: material and discoverable, not an emergency P1, not a deferrable P3.

## Risks and exceptions

- Treating this as “integrity is broken” would mis-state the system; over-correcting to global
  fail-closed could harm availability of core MFA.
- Under-stating it leaves external readers with an inflated completeness narrative.
- Silent swallow + grant mistakes during INSERT-only migration could look like “no audit” without
  API failures — already called out as a **required** elective gate on the sibling TB.

## Promotion notes

Promote toward `ready` / `TB-*` after a dedicated grill that settles:

1. event-class fail-open vs fail-closed matrix (or explicit “keep fail-open everywhere +
   observability only”);
2. minimum durable signal (metric + alert vs log-only);
3. whether any critical path needs an operation→row presence test;
4. the **minimal** honesty edit set (posture doc + service Javadoc first).

Status stays `captured` until that session; do not fold into the INSERT-only TB.

## Links

- **Elevated design principle (product-wide):** [`../../design-principles.md`](../../design-principles.md) §17 — *Name fail-open vs fail-closed at critical boundaries*
- Method-level mirror: [`../../../methodology/design-judgment-principles.md`](../../../methodology/design-judgment-principles.md) §12
- Sibling (orthogonal): [`I-2026-07-18-audit-log-peripheral-insert-only-hmac-sequence`](I-2026-07-18-audit-log-peripheral-insert-only-hmac-sequence.md)
- Sibling TB: [`../TB-2026-07-18-audit-log-insert-only-hmac-seal.md`](../TB-2026-07-18-audit-log-insert-only-hmac-seal.md)
- Honesty compass: [`../../integrity-assurance-honest-line.md`](../../integrity-assurance-honest-line.md)
- Public/operator posture: [`../../../../docs/SECURITY_POSTURE.md`](../../../../docs/SECURITY_POSTURE.md)
- Code: `org.ezkey.audit.service.AuditLogService#log`
