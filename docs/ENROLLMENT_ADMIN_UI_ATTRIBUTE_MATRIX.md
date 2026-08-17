# Enrollment attribute matrix (Admin UI vs backend)

This document maps fields persisted on `ezkey_enrollment` to how they are exposed and edited in the Admin UI and API. Source entity: `ezkey-core/.../entity/Enrollment.java`.


| Attribute                                    | Persisted | Admin GET list/detail | Editable via PATCH | Notes                                              |
| -------------------------------------------- | --------- | --------------------- | ------------------ | -------------------------------------------------- |
| `enrollmentId`                               | Yes       | Yes                   | No                 | Primary key                                        |
| `version`                                    | Yes       | Yes (API)             | Sent on PATCH      | Optimistic locking; optional display for operators |
| `integrationId`                              | Yes       | Yes                   | No                 | Scope / FK                                         |
| `enrollmentName`                             | Yes       | Yes                   | Yes                | Unique per integration for VERIFIED                |
| `enrollment_status`                          | Yes       | Yes                   | No                 | Lifecycle; use revoke/deactivate/reactivate flows  |
| `enrollment_active`                          | Yes       | Yes                   | No                 | Soft toggle via deactivate/reactivate              |
| `enrollment_challenge`                       | Yes       | Yes                   | No                 | Binding challenge; not arbitrary editable          |
| `enrollment_proof_token` (encrypted)         | Yes       | Yes (masked UI)       | No                 | Sensitive; recovery/admin reset regenerates        |
| `enrollment_proof_token_hash`                | Yes       | No                    | No                 | Internal                                           |
| `auth_attempt_challenge_required`            | Yes       | Yes                   | Yes                | Policy per enrollment                              |
| `integration_private_key` (encrypted)        | Yes       | No                    | No                 | Never exposed                                      |
| `integration_public_key`                     | Yes       | Yes                   | No                 | Cryptographic binding                              |
| `device_public_key`                          | Yes       | Yes                   | No                 | Change implies rebind; revoke + create new enrollment |
| `device_public_key_hash`                     | Yes       | No                    | No                 | Internal uniqueness                                |
| `device_private_key_storage_tier`            | Yes       | Yes                   | No                 | Client-reported at verify (`NONE`/`STANDARD`/`STRONG`); not independently attested — see `docs/MOBILE_DEVELOPER_GUIDE.md` |
| `created_at`                                 | Yes       | Yes                   | No                 | Audit / sorting                                    |
| `expires_at`                                 | Yes       | Yes                   | Yes                | Invitation window; clear supported via PATCH       |
| `verified_at`                                | Yes       | Yes                   | No                 | Set on verify                                      |
| `created_by_admin_id`                        | Yes       | Yes                   | No                 | Provenance                                         |
| `last_used_at`                               | Yes       | Yes                   | No                 | Operational                                        |
| `deactivated_at` / `deactivated_by_admin_id` | Yes       | Yes                   | No                 | Set by deactivate flow                             |
| `revoked_at` / `revoked_by_admin_id`         | Yes       | Yes                   | No                 | Set by revoke flow                                 |
| `contact_email`                              | Yes       | Yes                   | Yes                | Clear supported via PATCH                          |
| `user_identifier`                            | Yes       | Yes                   | Yes                | Trim; empty clears to null                         |


**Workflow-specific (separate from generic metadata PATCH)**

- **Admin MFA device loss**: `POST /api/v1/admin/enrollments/reset` with recovery token — see `docs/ENDPOINT.md` and `docs/ADMIN_UI_RECOVERY.md`.
- **End-user enrollment rebind / new device**: use **revoke** then **create** a new enrollment (no dedicated reset API); see `docs/ENROLLMENT_WORKFLOW_GAPS.md`.

