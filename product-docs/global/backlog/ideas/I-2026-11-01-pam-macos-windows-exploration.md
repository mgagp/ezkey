# Backlog Idea — `I-2026-11-01-pam-macos-windows-exploration` ezkey-pam host-grade level and Mac-native integration

## Metadata

- **ID:** `I-2026-11-01-pam-macos-windows-exploration`
- **Status:** `active`
- **Priority:** `P1`
- **Created at:** `2026-11-01`
- **Updated at:** `2026-11-04`
- **Last reviewed at:** `2026-11-04`
- **Component tags:** `pam`, `docs`, `lab:experimental`
- **Captured by:** Marc
- **GitHub issue:** `#1112` (source of truth — do not duplicate the issue body here)

## Intent

Revised 2026-11-04 with Marc. Bring `ezkey-pam` to a credible **host-grade** level, with a
**Mac-native** SSH integration as the new ground, stopping just before signing and distribution.
The exercise is also meant to surface signals that harden the whole platform.

## Problem and value

- **Problem:** `ezkey-pam` is a Linux lab module, not yet operable on a real host, and nothing
  exists for the Mac.
- **Expected value:** An honest view of a possible product offer, plus platform signals of the kind
  earlier labs have surfaced.

## Scope

See the roadmap in [#1112](https://github.com/mgagp/ezkey/issues/1112) and the reasoning in
[`plans/pam_host_grade_macos.plan.md`](../../../../plans/pam_host_grade_macos.plan.md).

- **In scope:** OS-neutral host-grade contract (Docker Linux, disposable Lightsail, macOS VM); F0;
  portable C; Mac-native SSH integration; `sudo` evaluated separately.
- **Out of scope:** signing, notarization, packages, publication; login window, screen saver,
  FileVault; `failmode=safe`; multi-OS SDK. Windows moves to
  [#1204](https://github.com/mgagp/ezkey/issues/1204), on hold.
- **Relaxed:** this work no longer waits for #955 to be stable, and must not slow it down.

## Key assumptions

- Tracking detail and sequencing live in [#1112](https://github.com/mgagp/ezkey/issues/1112);
  this idea is the backlog pointer only.
- Host-grade is not a release promise: `ezkey-pam` stays outside the operable-release path until
  Marc decides otherwise at the end of this roadmap.

## Risks and exceptions

- Host auth changes can lock out operators: nothing on the maintainer's Mac before validation on a
  VM and Marc's go-ahead; spare admin without MFA; tested rollback.
- Apple's `sshd` may refuse a module without a Developer ID signature. F0 measures it; that would be
  a finding to bring back to Marc, not a scope change.

## Promotion notes

Promoted from `captured` to `active` on 2026-11-04 (Marc). No tracer bullet: the issues in #1112 are
the bounded slices.

## Links

- Source of truth: [#1112](https://github.com/mgagp/ezkey/issues/1112)
- Related, no longer a prerequisite: [#955](https://github.com/mgagp/ezkey/issues/955)
- Plan: [`plans/pam_host_grade_macos.plan.md`](../../../../plans/pam_host_grade_macos.plan.md)
- Related (done): [`I-2026-10-13-pam-tmp-log-hardening`](I-2026-10-13-pam-tmp-log-hardening.md)
- Module posture: [`ezkey-pam/README.md`](../../../../ezkey-pam/README.md),
  [`ezkey-pam/STATUS.md`](../../../../ezkey-pam/STATUS.md)
