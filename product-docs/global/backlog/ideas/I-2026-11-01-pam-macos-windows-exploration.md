# Backlog Idea — `I-2026-11-01-pam-macos-windows-exploration` ezkey-pam on macOS and Windows (exploration)

## Metadata

- **ID:** `I-2026-11-01-pam-macos-windows-exploration`
- **Status:** `captured`
- **Priority:** `P3`
- **Created at:** `2026-11-01`
- **Updated at:** `2026-11-01`
- **Last reviewed at:** `2026-11-01`
- **Component tags:** `pam`, `docs`, `lab:experimental`
- **Captured by:** Marc
- **GitHub issue:** `#1112` (source of truth — do not duplicate the issue body here)

## Intent

Explore whether `ezkey-pam` (or a related host-auth adapter) can be studied on **macOS** and
**Windows** as a lab-only feasibility track: verdict first, then a gated single-target SSH
prototype, then experimentation and a written report.

## Problem and value

- **Problem:** `ezkey-pam` today is a Linux personal-lab module; multi-OS support is an explicit
  non-goal in module status notes. Operators may still want a dated feasibility answer for macOS
  and Windows without implying a product commitment.
- **Expected value:** A corpus-dated F0 feasibility note with a **verdict per target**, then (only
  after Marc's gate) a disposable-VM SSH-first prototype on one target, then E1 experimentation
  and report — without slowing the Linux PAM lab or the public alpha path.

## Scope

- **In scope (phased):**
  - **F0 — feasibility** in a dated corpus document; one verdict per target (macOS OpenPAM /
    sshd / sudo / loginwindow / SIP & signing / C reuse; Windows paths that are **not** PAM —
    Credential Provider, `AuthorizedKeysCommand`, `ForceCommand`, or a gateway).
  - **Marc's gate** before any prototype.
  - **P1 — prototype** on a **single** chosen target, SSH first, on a disposable VM/machine.
  - **E1 — experimentation** and written report.
  - Dead-end rule: after **two failed approaches**, stop and report.
  - Anti-lockout: never on the graphical session or primary workstation before E1; keep a
    non-MFA fallback admin account; document and test rollback.
  - **One lab at a time** — no prototype before [#955](https://github.com/mgagp/ezkey/issues/955)
    is stable.
- **Out of scope:**
  - Multi-OS SDK.
  - Packaging or notarization.
  - `failmode=safe`.
  - Replacing existing authentication.
  - Anything that would delay #955 or the alpha posture.

## Key assumptions

- Tracking detail and sequencing live in [#1112](https://github.com/mgagp/ezkey/issues/1112);
  this idea is the backlog pointer only.
- Linux `ezkey-pam` remains experimental; this exploration does not promote PAM onto the
  operable-release path.

## Risks and exceptions

- Host auth changes can lock out operators — anti-lockout rules above are mandatory before P1/E1.
- Windows has no PAM path for OpenSSH; F0 must treat that as a different adapter class, not a
  port of the Linux module.

## Promotion notes

Stay `captured` until F0 exists and Marc's gate is recorded. Promote toward a tracer bullet only
after a single target is chosen and #955 is stable enough for a disposable-lab prototype.

## Links

- Source of truth: [#1112](https://github.com/mgagp/ezkey/issues/1112)
- Prerequisite lab stability: [#955](https://github.com/mgagp/ezkey/issues/955)
- Related (done): [`I-2026-10-13-pam-tmp-log-hardening`](I-2026-10-13-pam-tmp-log-hardening.md)
- Module posture: [`ezkey-pam/README.md`](../../../../ezkey-pam/README.md),
  [`ezkey-pam/STATUS.md`](../../../../ezkey-pam/STATUS.md)
