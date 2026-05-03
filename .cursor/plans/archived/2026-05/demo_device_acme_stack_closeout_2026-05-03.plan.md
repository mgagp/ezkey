# Plan: Demo Device automation, JDK workstation rule, ACME Business Approval challenge — close-out

**Status**

- `.cursor/plans/archived/2026-05`
- **Completed**
- **Validated** (maintainer confirmed functional behaviour, including ACME Business Approval with challenge-required enrollments)

## Overview

Consolidated **clean-start demo stack** improvements: harden the **Spring Boot Demo Device** for realistic flows and Playwright consumption, document the **canonical JDK 25** path on the maintainer Windows workstation for reproducible Maven builds, fix follow-on Java compile/Javadoc issues in the demo modules, and align **ACME Business Approval Demo** with enrollment policies that require a **numeric challenge** (e.g. two digits) by surfacing the server-returned challenge in the dashboard UI.

## Objectives (executed)

1. **Demo Device (`ezkey-demo-device`)** — Tenant grouping for enrollment list UX, stable `data-testid` and semantic navigation where appropriate, WebJar/bootstrap alignment, English user-facing copy consistency, optional Playwright env for targeting a specific enrollment; Admin UI e2e helpers aligned with Demo Device hooks.
2. **Tooling / agent guidance** — Always-applied Cursor rule for **`JAVA_HOME` = `C:\Tools\jdk-25.0.3+9`** (Git Bash: `/c/Tools/jdk-25.0.3+9`); `scripts/build.sh` JDK autodetect prefers that path; cross-link from `maven-build.mdc`.
3. **Build hygiene** — Reactor compile with JDK 25; `EnrollmentTenantGrouper` Javadoc for `TenantGroupViewModel` record; public `normalizeTenantName` + controller call site for bind success view.
4. **ACME demo (`ezkey-demo-app-acme`)** — `BusinessApprovalStartResponse` includes optional `authAttemptChallenge`; dashboard shows a discreet strip under the REQUEST / CHECK STATUS row when the Integration API returns a challenge (enrollment-mandatory or per-request), without changing the existing inline status line layout.

## Implemented scope (summary)

| Area | Summary |
|------|---------|
| Demo Device | `EnrollmentTenantGrouper`, template/nav/testid hardening, QR import UX note, `plan_demo_device.md` note |
| Admin UI e2e | `EZKEY_DEMO_DEVICE_TEST_ENROLLMENT_ID`, `auth-flow.ts` targeting, `README.md` |
| Cursor rules | `jdk-25-windows-workstation.mdc`, `maven-build.mdc` link |
| Scripts | `build.sh` JDK path probe order |
| ACME | `BusinessApprovalController` + `dashboard.html` challenge strip + JS helpers |

## Relevant files (non-exhaustive)

- `ezkey-demo-device/src/main/java/org/ezkey/demo/device/view/EnrollmentTenantGrouper.java`
- `ezkey-demo-device/src/main/java/org/ezkey/demo/device/controller/EzkeyAppController.java`
- `ezkey-demo-device/src/main/resources/templates/phone/**/*.html`
- `ezkey-demo-device/src/main/resources/static/js/enrollment-qr-import.js`
- `ezkey-admin-ui/e2e/support/test-env.ts`, `ezkey-admin-ui/e2e/support/auth-flow.ts`, `ezkey-admin-ui/README.md`
- `.cursor/rules/jdk-25-windows-workstation.mdc`, `.cursor/rules/maven-build.mdc`
- `scripts/build.sh`
- `ezkey-demo-app-acme/src/main/java/org/ezkey/demo/acme/controller/BusinessApprovalController.java`
- `ezkey-demo-app-acme/src/main/resources/templates/dashboard.html`

## Operational notes

- **Docker:** `demo-app-acme` image rebuild and `docker compose up` may recreate dependent services (`integration-api`, `admin-api`, `migration`) per Compose dependencies; expect a short stack wave after image updates.
- **OpenAPI:** Demo Device generated DTOs follow the repo’s spec refresh workflow; do not hand-edit generated specs.

## Close-out

- Maintainer confirmed **end-to-end ACME Business Approval** with challenge-required enrollment works after UI and API response alignment.
- Plan archived under `.cursor/plans/archived/2026-05/` on **2026-05-03**.
