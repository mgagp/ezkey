# Backlog Idea — `I-2026-11-01-pam-macos-windows-exploration` ezkey-pam macOS and Windows exploration

## Metadata

- **ID:** `I-2026-11-01-pam-macos-windows-exploration`
- **Status:** `captured`
- **Priority:** `P3`
- **Created at:** `2026-11-01`
- **Updated at:** `2026-11-01`
- **Last reviewed at:** `2026-11-01`
- **Component tags:** `pam`, `docs`, `lab:experimental`
- **Captured by:** Marc
- **GitHub issue:** [`#1112`](https://github.com/mgagp/ezkey/issues/1112) (source of truth — do not duplicate at length)

## Intent

Explore whether `ezkey-pam` can reach **macOS** and **Windows** as a lab surface: feasibility first,
then a single-target SSH-first prototype on a disposable VM, then experimentation and report —
without slowing community PAM (#955) or the public alpha.

## Problem and value

- **Problem:** `ezkey-pam` today is a Linux-oriented personal lab. Multi-OS reach is listed as a
  non-goal in module status; evaluators may still ask what is feasible on macOS and Windows.
- **Expected value:** A dated feasibility verdict per target, then (only after Marc's gate) a
  bounded prototype and experiment report — or an honest dead-end — without expanding product
  surface prematurely.

## Scope (summary)

Full steps, rules, and out-of-scope list live in
[#1112](https://github.com/mgagp/ezkey/issues/1112). Short form only:

1. **F0 — Feasibility** in a dated corpus note; one verdict per target (macOS OpenPAM / sshd /
   sudo / loginwindow / SIP & signing / C reuse; Windows OpenSSH is not PAM — Credential Provider,
   `AuthorizedKeysCommand`, `ForceCommand`, or a gateway).
2. **Marc's gate** before any prototype.
3. **P1 — Prototype** on a **single** target, SSH first, on a disposable VM/machine.
4. **E1 — Experimentation** and written report.

**Rules:** dead-end after two failed approaches (stop and report); anti-lockout (no graphical
session / primary workstation before E1; spare admin without MFA; documented, tested rollback);
**one lab at a time** — no prototype before [#955](https://github.com/mgagp/ezkey/issues/955) is
stable.

**Out of scope:** multi-OS SDK; packaging / notarization; `failmode=safe`; replacing existing auth;
anything that slows #955 or alpha.

## Promotion notes

Stay `captured` until F0 exists and Marc gates P1. Do not open a tracer bullet for prototype work
before that gate and #955 stability.

## Links

- Tracking issue (source of truth): [#1112](https://github.com/mgagp/ezkey/issues/1112)
- Prerequisite / sequencing: [#955](https://github.com/mgagp/ezkey/issues/955) (community PAM intro)
- Module posture: [`ezkey-pam/README.md`](../../../../ezkey-pam/README.md),
  [`ezkey-pam/STATUS.md`](../../../../ezkey-pam/STATUS.md)
- Related hardening (closed):
  [`I-2026-10-13-pam-tmp-log-hardening`](I-2026-10-13-pam-tmp-log-hardening.md)
