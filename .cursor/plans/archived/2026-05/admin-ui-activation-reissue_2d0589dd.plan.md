---
name: admin-ui-activation-reissue
overview: Add a focused recovery path for pending administrator activation by letting a Global Admin reissue a fresh activation code and invalidate the previous unused one. Keep the admin record and lifecycle intact, and surface the action from the Admin UI admin-detail workflow. **Delivered and validated.**
status: completed
completedAt: "2026-05-03"
todos:
  - id: backend-endpoint
    content: Add a Global Admin-only endpoint and service path to reissue activation codes for eligible pending admins while invalidating previous unused codes.
    status: completed
  - id: ui-action
    content: Add the pending-activation detail action, confirmation dialog, and one-time code display in the Admin UI.
    status: completed
  - id: docs-validation
    content: Update endpoint/Postman documentation and add focused backend + UI validation for the new recovery path, treating the controller change as an automatic Postman-update trigger.
    status: completed
  - id: analysis-workflow
    content: Strengthen analysis workflow guidance so lifecycle-sensitive work must read the lifecycle governance document first, and controller changes must trigger contract review plus Postman updates by default.
    status: completed
isProject: false
---

## Plan status — completed

All scoped work was implemented and **test validated** (clean-start stack, contract refresh workflow, Operator UI behaviour). Closing this archived plan record.

---

# Pending Activation Recovery Plan

## Recommended direction
Implement a focused **"Reissue activation code"** path for administrators still in `PENDING_ACTIVATION`.

Default semantics:
- keep the existing admin account and history intact;
- issue a **new** one-time activation code;
- immediately invalidate any previous unused activation code for that admin;
- allow the action only while the admin is still pending activation, active, and has **no** first enrollment;
- restrict the action to **Global Admin**.

This matches the current lifecycle model better than deactivation or broad onboarding reset, and it closes the operational dead-end you hit without widening the surface unnecessarily.

This plan should also treat your observation as a workflow improvement opportunity: lifecycle-sensitive analysis in Ezkey should explicitly start from [C:/github/ezkey/docs/LIFECYCLE_GOVERNANCE.md](C:/github/ezkey/docs/LIFECYCLE_GOVERNANCE.md), not only from endpoint contracts or local code behavior.

## Backend changes
Use the existing deferred-onboarding model in [C:/github/ezkey/ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminProvisioningService.java](C:/github/ezkey/ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminProvisioningService.java), where activation-code creation already exists and activation consumption already flips the admin from `PENDING_ACTIVATION` to `ACTIVE`.

Planned work:
- add a new Admin API mutation such as `POST /api/v1/admins/{id}/activation-code/regenerate` in [C:/github/ezkey/ezkey-admin-api/src/main/java/org/ezkey/admin/controller/AdminProvisioningController.java](C:/github/ezkey/ezkey-admin-api/src/main/java/org/ezkey/admin/controller/AdminProvisioningController.java);
- add service logic in [C:/github/ezkey/ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminProvisioningService.java](C:/github/ezkey/ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminProvisioningService.java) to:
  - load and authorize the target admin,
  - verify `active = true`, `lifecycleStatus = PENDING_ACTIVATION`, and `enrollment == null`,
  - deactivate any existing active activation token(s) for that admin,
  - reuse the existing activation-code issuance path to mint a fresh code and expiry;
- add audit logging for successful reissue and rejected attempts, aligned with current activation/recovery-code audit style;
- validate the proposal and guardrails against [C:/github/ezkey/docs/LIFECYCLE_GOVERNANCE.md](C:/github/ezkey/docs/LIFECYCLE_GOVERNANCE.md), especially the separation between admin identity lifecycle and admin MFA enrollment lifecycle;
- update request/response docs in [C:/github/ezkey/docs/ENDPOINT.md](C:/github/ezkey/docs/ENDPOINT.md) and the impacted Postman collection under [C:/github/ezkey/postman/collections/v2.1/EZ Key Admin Provisioning admin.postman_collection.json](C:/github/ezkey/postman/collections/v2.1/EZ Key Admin Provisioning admin.postman_collection.json).

## Admin UI changes
Extend the admin detail workflow in [C:/github/ezkey/ezkey-admin-ui/src/pages/admins.tsx](C:/github/ezkey/ezkey-admin-ui/src/pages/admins.tsx), which already detects `PENDING_ACTIVATION` and currently shows only the informational notice plus normal actions.

Planned work:
- show a new action button for eligible pending admins, next to the existing detail actions;
- open a confirmation dialog that explains:
  - a new code will be shown once,
  - the previous unused code stops working immediately,
  - the admin remains pending until the new code is consumed;
- after success, show the new code in the same one-time-save pattern already used in the create-admin success dialog;
- add EN/FR copy in [C:/github/ezkey/ezkey-admin-ui/src/locales/en/admins.json](C:/github/ezkey/ezkey-admin-ui/src/locales/en/admins.json) and [C:/github/ezkey/ezkey-admin-ui/src/locales/fr/admins.json](C:/github/ezkey/ezkey-admin-ui/src/locales/fr/admins.json).

## Validation
Targeted automated coverage is worth it here because this is an operator recovery workflow with security-sensitive state.

Planned validation:
- backend service/controller tests for success, wrong status, existing enrollment, inactive admin, and old-token invalidation;
- a focused Admin UI test for button visibility and successful reissue flow on pending admins;
- broader browser/device-backed Playwright coverage is probably **not** the first priority unless the implementation also changes the login-side activation journey in [C:/github/ezkey/ezkey-admin-ui/src/components/feature/login-activation-section.tsx](C:/github/ezkey/ezkey-admin-ui/src/components/feature/login-activation-section.tsx).

## Analysis workflow improvement
Use this change to harden the repo guidance for future lifecycle-sensitive analysis.

Planned work:
- add an explicit instruction in [C:/github/ezkey/AGENTS.md](C:/github/ezkey/AGENTS.md) that any analysis touching entity relationships, lifecycle semantics, eligibility chains, or parent-child operational effects must read [C:/github/ezkey/docs/LIFECYCLE_GOVERNANCE.md](C:/github/ezkey/docs/LIFECYCLE_GOVERNANCE.md) first;
- add an explicit repo-wide instruction in [C:/github/ezkey/AGENTS.md](C:/github/ezkey/AGENTS.md) that controller changes are presumed to affect the API contract and therefore require impacted Postman collections to be reviewed and updated in the same change set;
- treat `docs/LIFECYCLE_GOVERNANCE.md` as the default source of truth for cross-entity operational reasoning, with `docs/ENDPOINT.md` and module-local code as supporting contract/implementation detail.

## Manual check
After implementation, a short exploratory pass should be enough:
- create a tenant admin with `ACTIVATION_CODE`;
- open the admin detail page as Global Admin;
- reissue the code;
- confirm the old code is rejected and the new code completes activation normally.