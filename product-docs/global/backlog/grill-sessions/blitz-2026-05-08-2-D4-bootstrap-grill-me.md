# Grill Me — Blitz 2026-05-08-2, D4 (bootstrap — activation-code default)

## Session control

| Field | Value |
|-------|--------|
| **Blitz source** | `product-docs/global/backlog/blitz-archive/blitz-2026-05-08-2.md` (D4) |
| **Backlog** | `I-2026-0011` |
| **Status** | `complete` |
| **Date** | `2026-05-19` |

## Settled decisions (operator-confirmed)

| ID | Decision |
|----|----------|
| D4-1 | **Real install default:** `ACTIVATION_CODE` (security-first) — no full credential dump on first run |
| D4-2 | **clean-start / Docker QA:** keep QR/convenience ceremony via **explicit flag** (dev path) |
| D4-3 | **Retire** recovery-code bootstrap entry mode — superseded by activation-code |
| D4-4 | Full JSON / IMMEDIATE export: **opt-in** for local functional tests + demo device mount only |
| D4-5 | Extend **`ezkey.admin.mfa.bootstrap.*`** — no profile-aware platform code (`V-2026-0010`) |
| D4-6 | Functional tests: clean-start retains demo-device path without changing production default |
| D4-7 | Document clearly: real install vs clean-start vs EXP in product-docs + CONFIGURATION.md |

## Links

- [`../ideas/I-2026-0011-bootstrap-activation-code-default.md`](../ideas/I-2026-0011-bootstrap-activation-code-default.md)
- Plan context: `plans/admin_onboarding_security_posture_rfc.plan.md`
