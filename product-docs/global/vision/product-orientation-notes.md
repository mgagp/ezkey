# Product Orientation Notes — Vision Index

## Purpose

This file is a **lightweight index** of vision notes (`V-*`). Each entry is a standalone file in
this directory. Do not add content here — create individual `V-YYYY-MM-DD-<slug>.md` files
instead.

For the multi-branch update rule, see
[`../../methodology/multi-branch-workflow.md`](../../methodology/multi-branch-workflow.md):
this index is updated only post-merge on `main`, never on feature branches.

## Active vision notes

| ID | Title | Status | Date | File |
|----|-------|--------|------|------|
| `V-2026-0001` | Enrollment-scoped local-auth posture for mobile respond | `under-review` | `2026-05-07` | [V-2026-0001](V-2026-0001-mobile-local-auth-per-enrollment.md) |
| `V-2026-0003` | API-key acceptance posture across Admin API and Integration API | `under-review` | `2026-05-08` | [V-2026-0003](V-2026-0003-api-key-acceptance-posture.md) |
| `V-2026-0004` | Integrity validation strategy: rolling windows, retroactive batches, dashboard transparency | `under-review` | `2026-05-08` | [V-2026-0004](V-2026-0004-integrity-validation-strategy.md) |
| `V-2026-0005` | Email integration strategy and deployment-profile cohabitation | `under-review` | `2026-05-08` | [V-2026-0005](V-2026-0005-email-integration-strategy.md) |
| `V-2026-0006` | Mobile certificate pinning posture | `under-review` | `2026-05-08` | [V-2026-0006](V-2026-0006-mobile-certificate-pinning.md) |
| `V-2026-0007` | SMS integration strategy and SPI protocol | `under-review` | `2026-05-08` | [V-2026-0007](V-2026-0007-sms-integration-spi.md) |
| `V-2026-0008` | Auth API versioning for mobile protocol evolution | `under-review` | `2026-05-08` | [V-2026-0008](V-2026-0008-auth-api-protocol-versioning.md) |
| `V-2026-0009` | Lightweight observability posture: Java Melody first | `under-review` | `2026-05-08` | [V-2026-0009](V-2026-0009-java-melody-observability.md) |
| `V-2026-0010` | Per-installation profile elaboration via AI-assisted methodology | `draft` | `2026-05-08` | [V-2026-0010](V-2026-0010-per-installation-profile-elaboration.md) |
| `V-2026-0011` | Android-first real-device mobile validation | `under-review` | `2026-05-08` | [V-2026-0011](V-2026-0011-android-real-device-validation.md) |
| `V-2026-0012` | Dedicated batch backend (future): optional split from Admin API | `draft` | `2026-05-19` | [V-2026-0012](V-2026-0012-dedicated-batch-backend-future.md) |
| `V-2026-0013` | Meta-resolution over long windows (future, exceptional) | `draft` | `2026-05-19` | [V-2026-0013](V-2026-0013-meta-resolution-long-windows.md) |
| `V-2026-06-28` | Audit archive export SPI: vendor-neutral immutable retention and cryptographic batch detachment | `under-review` | `2026-06-28` | [V-2026-06-28](V-2026-06-28-audit-archive-export-spi.md) |
| `V-2026-07-18-authentication-wait-evolution` | Authentication wait evolution | `draft` | `2026-07-18` | [V-2026-07-18](V-2026-07-18-authentication-wait-evolution.md) |

## Recently promoted

| ID | Title | Promoted date | Canonical destination |
|----|-------|---------------|-----------------------|
| `V-2026-09-26-public-alpha-posture-closeout` | Public alpha posture closeout (lab · discrete · alpha ≠ prod) | `2026-09-26` | Live cold-start / next-P0 compass; supersedes September operational-readiness as gate |
| `V-2026-09-22-exp1-to-ezkey-online-alpha` | EXP1 → ezkey.online alpha community instance | `2026-09-22` | Domain/host locks; site PR #609; community ledger |
| `V-2026-0014` | API documentation exposure and portal posture | `2026-05-22` | `I-2026-0026` → `TB-2026-0003` |

## Archived

| ID | Title | Reason |
|----|-------|--------|
| `V-2026-0002` | Deployment profiles for Ezkey installations | Superseded by `V-2026-0010` |
