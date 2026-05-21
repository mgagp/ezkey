# Grill Me — Blitz 2026-05-08-1, D7 (email strategy)

## Session control

| Field | Value |
|-------|--------|
| **Blitz source** | `product-docs/global/backlog/blitz-archive/blitz-2026-05-08-1.md` (D7) |
| **Vision** | `V-2026-0005` |
| **Backlog (R1 slice)** | `I-2026-0023` |
| **Status** | `complete` |
| **Date** | `2026-05-19` |

## Settled decisions

### R1 scope — all workflows optional

R1 does **not** mandate email for any workflow. It **normalizes** the operational choice:

| Workflow | R1 email |
|----------|----------|
| Enrolment (QR / invite material) | **Optional** — operator-triggered |
| Admin activation code | **Optional** — operator-triggered |
| Other (recovery, notifications, …) | **Optional** / later |

R1 delivers: configuration model, UI affordances, disclaimer repositioning, contextual help — not automated outbound mail.

### Operator-initiated only (D7-2)

- **No** automatic send on workflow completion in R1.
- Operator explicitly chooses **Send by email** (PME volume, simplicity).
- Automation deferred unless real need emerges.

### SMTP absent (D7-3) — integrated delivery posture

- Keep current **on-screen display** behaviour.
- Disclaimer changes **nature**: not “something more sophisticated is coming”, but **assumed product positioning** given current configuration.
- **Integrated mode** includes operator using **external channels manually** (drag-drop QR image into their own email, paste challenge code into Teams, etc.) — outside Admin UI / Ezkey send pipeline but **valid** for lightweight deployments.
- Contextual help should document this pattern.

### SMTP present (D7-4, D7-5) — configured channel posture

- Workflow presents **email first** (operator action).
- **On-screen QR remains available** — minimized (existing pattern); both paths **explicit**.
- Disclaimer stays **educational**: explains **why** the UI looks this way under current settings.

### Deployment postures for documentation (D7-6)

Two **documentation postures** (align with `V-2026-0010` per-installation elaboration — not platform code profiles):

| Posture | When | Operator experience |
|---------|------|---------------------|
| **`integrated-delivery`** | SMTP not configured (or operator declines send) | Sensitive material on screen (minimized where applicable); disclaimer states integrated posture; help explains manual external channels are acceptable |
| **`smtp-assisted-delivery`** | SMTP configured | Email-first operator action; minimized on-screen fallback; disclaimer educates on configured posture |

**Confirmed slugs (2026-05-19):** `integrated-delivery`, `smtp-assisted-delivery` — acceptable R1 baseline; may evolve in `I-2026-0017` profile templates.

### Roles (D7-7)

- **Global Admin only** for SMTP configuration and channel posture (TI analogy — `operator-alignment-guide.md`).
- Tenant Admin behaviour unchanged for this slice unless later scoped.

### Email content (D7-8)

- R1 body: **QR code visible** in email + **short instructional text** (what to do next).
- Minimal; evolve later (templates, branding).

### SMTP failure (D7-9)

- Show **clear SMTP error** message.
- **No extra workflow branch** — on-screen QR already available (minimized); operator naturally falls back to display / copy / drag-drop.
- Aligns with integrated-mode manual channel story.

### Principle (D7-10)

> **Ezkey supports email send when configured, but remains fully operational when email is unavailable or send fails** — on-screen and manual external channels stay valid.

Pairs with **Design Principle #14** (no pre-optimizing automation) and **operator-alignment-guide** (not core business; keep light).

## Links

- [`../blitz-archive/blitz-2026-05-08-1.md`](../blitz-archive/blitz-2026-05-08-1.md)
- [`../../vision/product-orientation-notes.md`](../../vision/product-orientation-notes.md) (`V-2026-0005`)
- [`../ideas/I-2026-0023-email-channel-r1-optional-operator-send.md`](../ideas/I-2026-0023-email-channel-r1-optional-operator-send.md)
- [`../../operator-alignment-guide.md`](../../operator-alignment-guide.md)
- Related: `V-2026-0010`, `V-2026-0007` (SMS peripheral contrast)
