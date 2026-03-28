# EZKey CLI Controller Review Notes

This file will be in UTF-8 without BOM.

## Overview
- Goal: Track CLI vs Admin API controller alignment (fields, types, required/optional), prioritized by controller.
- Status legend: ✅ aligned, ⚠️ minor mismatch, ❌ missing/incorrect.

## General CLI Review Notes
### Pagination & Search Parameters
- Observation: CLI likely does not consistently expose pagination/sorting or search filter parameters.
- Action: Schedule a focused review for controllers that support paging/search to add CLI flags.
- Controllers with pagination/search (Pageable):
	- AuditLogController
	- AuthAttemptController
	- EnrollmentController
	- IntegrationController
	- AdminProvisioningController

## Controller Review Queue (Priority)
1. AdminAuthController
2. AdminEnrollmentController
3. AdminProvisioningController
4. ApiKeyController
5. AuditLogController
6. (next) TBD

---

## AdminAuthController
**Scope:** Login, Passwordless Wait, Recover, Logout

**Status:** ✅ Mostly aligned, ⚠️ one mismatch

### Findings
- Login: ✅ CLI sends `username` (required) and `challengeRequested` (optional); handles `pending` and `approved` flows.
- Passwordless wait: ✅ CLI requires `authAttemptId` and `challengeCode` (ints), matches DTO requirements.
- Recover: ⚠️ CLI accepts recovery code without dashes (only validates 32 digits), while API requires dashed pattern `XXXX-XXXX-...`.
- Logout: ✅ CLI uses token via Authorization header; aligns with controller.

### Actions
- [ ] Align CLI client-side validation for recovery code with dashed format required by API (strict pattern).

---

## AdminEnrollmentController
**Scope:** Reset Enrollment

**Status:** ✅ aligned, ⚠️ token preference edge-case

### Findings
- Reset enrollment: ✅ CLI sends `enrollmentId` (required) to `/api/v1/admin/enrollments/reset`, matches DTO types and required fields.
- Auth: ⚠️ Controller requires recovery token. CLI checks `recoveryToken` exists, but if a bearer token is also present, `HttpClient` prefers bearer token. Config normally clears bearer when saving recovery token, but manual configs could cause a bearer to be sent instead.

### Actions
- [ ] Consider forcing recovery token usage for enrollment reset (override auth header or clear bearer in this flow).

---

## AdminProvisioningController
**Scope:** Create global admin, create tenant admin, list admins, onboarding credentials, onboarding QR code, deactivate admin

**Status:** ✅ partially integrated (list command implemented)

### Findings

**Controller Endpoints:**
1. POST /api/v1/admins/global - Create global admin (GlobalAdmin only)
2. POST /api/v1/admins/tenant - Create tenant admin (GlobalAdmin only)
3. GET /api/v1/admins - List admins (Pageable: page, size, sort)
   - Sortable fields: id, createdAt
4. GET /api/v1/admins/{id}/onboarding - Get onboarding credentials
5. GET /api/v1/admins/{id}/onboarding/qrcode - Get onboarding QR code (PNG)
6. POST /api/v1/admins/{id}/deactivate - Deactivate admin (GlobalAdmin only)

**CLI Commands:** `ezkey admin provisioning`

1. list command:
   - ✅ **IMPLEMENTED (2026-01-30)**: Pagination parameters (--page, --size)
   - ✅ **IMPLEMENTED (2026-01-30)**: Sorting option (--sort with field validation)
   - ✅ **IMPLEMENTED (2026-01-30)**: Summary display option (--summary)

**Sortable Fields:** id, createdAt (default)

### Actions

**Partially Complete - List command implemented, remaining endpoints not yet exposed:**
- ✅ **COMPLETED (2026-01-30)**: Implement `list` command with pagination/sorting
- [ ] Implement `create-global` command for POST /api/v1/admins/global (GlobalAdmin only)
- [ ] Implement `create-tenant` command for POST /api/v1/admins/tenant (GlobalAdmin only)
- [ ] Implement `onboarding` command to retrieve onboarding credentials
- [ ] Implement `qrcode` command to generate and display onboarding QR code
- [ ] Implement `deactivate` command for POST /api/v1/admins/{id}/deactivate (GlobalAdmin only)

---

## ApiKeyController
**Scope:** Create, list (all), list by integration, get, revoke

**Status:** ✅ mostly aligned, ⚠️ one missing endpoint

### Findings
- Create: ✅ CLI sends `integrationId` (required), `description` (optional in API but CLI requires), `expiresAt` (optional), `ipWhitelist` (optional). Response fields match.
- List by integration: ✅ CLI calls `/api/v1/api-keys/integration/{integrationId}`.
- Get: ✅ CLI calls `/api/v1/api-keys/{keyId}`.
- Revoke: ✅ CLI calls DELETE `/api/v1/api-keys/{keyId}`.
- List all: ⚠️ CLI has no command for GET `/api/v1/api-keys` (admin‑scoped list).

### Actions
- [ ] Add CLI command for list‑all API keys (admin‑scoped) or confirm it should be intentionally omitted.
- [ ] Consider making `description` optional in CLI to match API (currently required in CLI).

---

## AuditLogController
**Scope:** Query audit logs with filters + pagination

**Status:** ✅ fully aligned

### Findings
- Filters: ✅ CLI supports `eventType`, `eventStatus`, `apiName`, `enrollmentId`, `adminId`.
- Pagination: ✅ CLI exposes `page` and `size`.
- Sorting: ✅ **IMPLEMENTED (2026-01-30)**: CLI exposes `--sort` option (e.g., `createdAt,desc`).
- Summary: ✅ **IMPLEMENTED (2026-01-30)**: CLI displays pagination metadata with `--summary` flag.

**Sortable Fields:** auditLogId, createdAt (default), eventType, eventStatus, apiName

### Actions
**None - Full compliance achieved**

---

## AuthAttemptController
**Scope:** Search auth attempts, Get by ID, Create, Delete, Cancel, Wait

**Status:** ✅ mostly aligned, ⚠️ missing cancel endpoint, ⚠️ pagination/sorting incomplete

### Findings

**Controller Endpoints:**
1. GET `/api/v1/auth-attempts` - Search with filters + Pageable (page, size, sort)
   - Filters: status (AuthAttemptStatus enum), enrollmentId, integrationId, createdAfter, createdBefore
   - Default sort: createdAt,DESC

2. GET `/api/v1/auth-attempts/{id}` - Get by ID

3. POST `/api/v1/auth-attempts` - Create
   - Request DTO: enrollmentId (Integer, required), challengeRequested (Boolean, required)

4. DELETE `/api/v1/auth-attempts/{id}` - Delete

5. POST `/api/v1/auth-attempts/{id}/cancel` - Cancel attempt (marks as expired)

6. GET `/api/v1/auth-attempts/{id}/wait` - Wait for response
   - Query params: timeout (Integer, default 30, min 1, max 300), polling (Integer, default 2, min 1, max 60)

**CLI Commands:** `ezkey admin auth-attempt`

1. `list` command:
   - ✅ **IMPLEMENTED (2026-01-30)**: Pagination parameters (--page, --size)
   - ✅ **IMPLEMENTED (2026-01-30)**: Sorting option (--sort with field validation)
   - ✅ **IMPLEMENTED (2026-01-30)**: Summary display option (--summary)
   - ⚠️ Has `--enrollment-id` filter
   - ⚠️ Missing filters: status, integration-id, created-after, created-before

**Sortable Fields:** authAttemptId, createdAt (default), expiresAt, enrollmentId

2. `get` command:
   - ✅ Aligned: `--id` (required, type int)

3. `create` command:
   - ✅ `--enrollment-id` (required, type int) matches enrollmentId
   - ✅ `--challenge-requested` (flag) matches challengeRequested
   - ⚠️ `--data` option allows JSON input, but controller only expects enrollmentId + challengeRequested
   - Note: CLI sets challengeRequested to True when flag is present, but controller requires explicit boolean (not optional)

4. `delete` command:
   - ✅ Aligned: `--id` (required, type int)

5. `cancel` command:
   - ❌ **NOT IMPLEMENTED IN CLI** - Controller has POST `/api/v1/auth-attempts/{id}/cancel`

6. `wait` command:
   - ✅ `--id` (required, type int) matches path parameter
   - ✅ `--timeout` (default 30, type int) matches controller query param
   - ✅ `--polling` (default 2, type int) matches controller query param
   - Note: Controller validates timeout (1-300) and polling (1-60), CLI doesn't enforce these limits

### Actions

1. **Search/List command improvements:**
   - ✅ **COMPLETED (2026-01-30)**: Add pagination parameters: `--page` (default 0), `--size` (default 20)
   - ✅ **COMPLETED (2026-01-30)**: Add sorting option: `--sort` (e.g., `authAttemptId,asc` or `createdAt,desc`)
   - ✅ **COMPLETED (2026-01-30)**: Add summary display option: `--summary`
   - [ ] Add missing filters to `list` command: `--status`, `--integration-id`, `--created-after`, `--created-before`

2. **Create command:**
   - [ ] Verify that `--challenge-requested` flag behavior matches controller expectation (boolean required)
   - [ ] Consider removing or documenting the `--data` option since controller only accepts enrollmentId + challengeRequested

3. **Cancel command:**
   - [ ] Implement `ezkey admin auth-attempt cancel --id <id>` to expose POST `/api/v1/auth-attempts/{id}/cancel`

4. **Wait command:**
   - [ ] Add client-side validation for `--timeout` (1-300) and `--polling` (1-60) to match controller constraints
---

## EncryptionKeyController
**Scope:** Encryption key management, rotation, re-encryption batches, triggers

**Status:** ✅ fully aligned

### Findings

**Controller Endpoints:**
1. GET `/api/v1/encryption-keys` - List all encryption keys
2. GET `/api/v1/encryption-keys/primary` - Get current primary key
3. GET `/api/v1/encryption-keys/{keyId}` - Get key by ID (Long type)
4. POST `/api/v1/encryption-keys/rotate` - Manually trigger key rotation
5. GET `/api/v1/encryption-keys/reencryption-batches` - List re-encryption batches (paginated; optional filters: status, targetTable, targetColumn, oldKeyId, newKeyId, createdAfter, createdBefore)
6. POST `/api/v1/encryption-keys/reencryption-batches/{batchId}/resume` - Resume batch (Integer type)
7. POST `/api/v1/encryption-keys/reencrypt/trigger` - Trigger full re-encryption
8. POST `/api/v1/encryption-keys/{keyId}/reencrypt` - Trigger re-encryption for specific key (Long type)
9. POST `/api/v1/encryption-keys/reencrypt/create-batches` - Create batches without processing

**CLI Commands:** `ezkey admin encryption-key`

1. `list` command:
   - ✅ Calls GET `/api/v1/encryption-keys` - aligned

2. `get` command:
   - ✅ `--id` (required, type int) calls GET `/api/v1/encryption-keys/{keyId}` - aligned
   - Note: CLI uses `int` while controller expects `Long`, but compatible

3. `primary` command:
   - ✅ Calls GET `/api/v1/encryption-keys/primary` - aligned

4. `rotate` command:
   - ✅ Calls POST `/api/v1/encryption-keys/rotate` - aligned
   - ✅ Includes confirmation prompt and success feedback with new key ID

**CLI Re-encryption Subgroup:** `ezkey admin encryption-key reencrypt`

5. `trigger` command:
   - ✅ With `--key-id`: calls POST `/api/v1/encryption-keys/{keyId}/reencrypt` - aligned
   - ✅ Without `--key-id`: calls POST `/api/v1/encryption-keys/reencrypt/trigger` - aligned
   - ✅ Displays batches created/processed/failed statistics

6. `create-batches` command:
   - ✅ Calls POST `/api/v1/encryption-keys/reencrypt/create-batches` - aligned

7. `batches` command:
   - ✅ Calls GET `/api/v1/encryption-keys/reencryption-batches` with pagination (`--page`, `--size`, `--sort`) and the same optional filters as the API (`--status`, `--target-table`, `--target-column`, `--old-key-id`, `--new-key-id`, `--created-after`, `--created-before`); `--summary` shows page metadata - aligned

8. `resume` command:
   - ✅ `--batch-id` (required, type int) calls POST `/reencryption-batches/{batchId}/resume` - aligned
   - Note: CLI uses `int` while controller expects `Integer`, fully compatible

### Actions

**None - Full compliance achieved**

All endpoints are properly exposed in the CLI with correct parameter types, confirmation prompts where appropriate, and informative success/error feedback. The CLI provides excellent UX with formatted output for batch statistics and warnings about critical operations.
---

## EnrollmentController
**Scope:** Enrollment management - search, get by ID, create, delete, QR code generation

**Status:** ✅ pagination/sorting implemented, ⚠️ search filters incomplete

### Findings

**Controller Endpoints:**
1. GET /api/v1/enrollments - Search with filters + Pageable (page, size, sort)
   - Filters: status (EnrollmentStatus enum), integrationId, enrollmentName, active, createdAfter, createdBefore
   - Default sort: createdAt,DESC
   - Sortable fields: enrollmentId, enrollmentName, createdAt, integrationId, status

2. GET /api/v1/enrollments/{id} - Get by ID (Integer)

3. POST /api/v1/enrollments - Create
   - Request DTO: integrationId (Integer, required), name (String, required, not blank), authAttemptChallengeRequired (Boolean, optional)

4. DELETE /api/v1/enrollments/{id} - Delete (Integer)

5. GET /api/v1/enrollments/{id}/qrcode - Generate QR code
   - Returns PNG image (image/png)
   - QR content format: enrollmentId|enrollmentProofToken

**CLI Commands:** ezkey admin enrollment

1. list command:
   - ✅ Has --integration-id filter
   - ✅ **IMPLEMENTED (2026-01-30)**: Pagination parameters (--page, --size)
   - ✅ **IMPLEMENTED (2026-01-30)**: Sorting option (--sort with field validation)
   - ✅ **IMPLEMENTED (2026-01-30)**: Summary display option (--summary)
   - ⚠️ Missing filters: status, enrollment-name, active, created-after, created-before

2. get command:
   -  Aligned: --id (required, type int)

3. create command:
   -  --integration-id (required, type int) matches integrationId
   -  Uses --data for JSON input including
ame and uthAttemptChallengeRequired
   -  Controller requires
ame (NotNull, NotBlank) but CLI only allows it via --data, not as explicit option
   -  uthAttemptChallengeRequired is optional in controller but CLI doesn't provide explicit flag
   - Note: JSON data approach is flexible but less user-friendly than explicit options

4. delete command:
   -  Aligned: --id (required, type int)
   -  Confirmation prompt present

5. qrcode command:
   -  --id (required, type int) matches path parameter
   -  --output option for file path (defaults to enrollment-{id}.png)
   -  Handles binary PNG response correctly
   -  Sets proper Accept header (image/png,*/*)
   -  Saves to file with size feedback
   -  Provides helpful error messages for 400 (missing proof token) and 404 (not found)

**Note on reset command:** CLI has ezkey admin enrollment reset --id <id> but this is actually handled by **AdminEnrollmentController** (POST /api/v1/admin/enrollments/reset), not this EnrollmentController. Already reviewed separately.

### Actions

1. **Search/List command improvements:**
   - ✅ **COMPLETED (2026-01-30)**: Add pagination parameters: --page (default 0), --size (default 20)
   - ✅ **COMPLETED (2026-01-30)**: Add sorting option: --sort (e.g., enrollmentId,asc or createdAt,desc)
   - ✅ **COMPLETED (2026-01-30)**: Add --summary option for pagination metadata display
   - [ ] Add missing filters: --status, --enrollment-name, --active, --created-after, --created-before
   - ✅ **COMPLETED (2026-01-30)**: Created pagination_utils.py with reusable utilities
   - ✅ **COMPLETED (2026-01-30)**: Added comprehensive unit tests (34 tests passing)

2. **Create command improvements:**
   - [ ] Consider adding explicit --name option (required) for better UX
   - [ ] Consider adding explicit --challenge-required flag for uthAttemptChallengeRequired
   - [ ] Document JSON data structure if --data approach is preferred
   - [ ] Current approach works but is less discoverable for users

3. **QR code command:**
   - No actions needed - implementation is excellent with proper binary handling and user feedback

---

## IntegrationController
**Scope:** Integration management - search, get by ID, create, delete

**Status:**  mostly aligned,  search filters incomplete,  pagination/sorting missing

### Findings

**Controller Endpoints:**
1. GET /api/v1/integrations - Search with filters + Pageable (page, size, sort)
   - Filters: integrationName, active, createdAfter, createdBefore
   - Default sort: createdAt,DESC
   - Sortable fields: id, createdAt, active

2. GET /api/v1/integrations/{id} - Get by ID (Integer)

3. POST /api/v1/integrations - Create
   - Request DTO: logo (String, optional), i18n (List<IntegrationI18nCreateDto>, optional with @Valid)
   - Note: Both fields are optional - controller generates defaults if not provided

4. DELETE /api/v1/integrations/{id} - Delete (Integer)

**CLI Commands:** ezkey admin integration

1. list command:
   - ✅ **IMPLEMENTED (2026-01-30)**: Pagination parameters (--page, --size)
   - ✅ **IMPLEMENTED (2026-01-30)**: Sorting option (--sort with field validation)
   - ✅ **IMPLEMENTED (2026-01-30)**: Summary display option (--summary)
   - ⚠️ No filters exposed
   - ⚠️ Missing filters: integration-name, active, created-after, created-before

**Sortable Fields:** id, createdAt (default), active

2. get command:
   -  Aligned: --id (required, type int)

3. create command:
   -  Uses --logo (optional) and --data for JSON input
   -  Controller expects logo (String, optional) and i18n (List, optional)
   -  CLI requires either --data or --logo, but controller allows empty request (generates defaults)
   -  No explicit options for i18n entries (name, description, language)
   - Note: JSON data approach is flexible but less user-friendly for simple cases

4. delete command:
   -  Aligned: --id (required, type int)
   -  Confirmation prompt present
   -  Warning message about cascading deletes (enrollments, auth attempts, API keys)

### Actions

1. **Search/List command improvements:**
   - ✅ **COMPLETED (2026-01-30)**: Add pagination parameters: `--page` (default 0), `--size` (default 20)
   - ✅ **COMPLETED (2026-01-30)**: Add sorting option: `--sort` (e.g., id,asc or createdAt,desc)
   - ✅ **COMPLETED (2026-01-30)**: Add summary display option: `--summary`
   - [ ] Add missing filters: --integration-name, --active, --created-after, --created-before

2. **Create command improvements:**
   - [ ] Remove validation error when neither --logo nor --data provided (controller allows empty request)
   - [ ] Consider adding explicit options: --name, --description, --language for simple single-language integrations
   - [ ] Document JSON structure for multi-language i18n array in help text
   - [ ] Example: {"i18n": [{"language": "en", "name": "My App", "description": "My app description"}]}

3. **Delete command:**
   - No actions needed - well implemented with confirmation and clear warnings


---

## TenantController
**Scope:** Tenant management - create, list, get by ID, deactivate (GlobalAdmin only)

**Status:**  not integrated in CLI

### Findings

**Controller Endpoints:**
1. POST /api/v1/tenants - Create tenant (ROLE_GLOBAL_ADMIN only)
   - Request DTO: tenantName (String, required, 3-100 chars), tenantDescription (String, optional, max 500 chars)

2. GET /api/v1/tenants - List tenants (ROLE_GLOBAL_ADMIN only)
   - GlobalAdmin: sees all tenants
   - TenantAdmin: sees only their own tenant (but PreAuthorize restricts to GLOBAL_ADMIN)

3. GET /api/v1/tenants/{id} - Get tenant by ID (ROLE_GLOBAL_ADMIN only)
   - GlobalAdmin: can access any tenant
   - TenantAdmin: can only access their own tenant (but PreAuthorize restricts to GLOBAL_ADMIN)

4. POST /api/v1/tenants/{id}/deactivate - Deactivate tenant (ROLE_GLOBAL_ADMIN only)
   - Sets active flag to false
   - Preserves data for audit purposes

**CLI Commands:**
-  **NO CLI COMMANDS FOUND** - No tenant management group exists in CLI

### Actions

1. **Create tenant group:**
   - [ ] Add ezkey admin tenant command group for tenant management
   - [ ] Implement create command with --name (required) and --description (optional)
   - [ ] Add validation for name length (3-100 characters) and description (max 500)

2. **List tenants:**
   - [ ] Implement list command to show all tenants (GlobalAdmin) or own tenant (TenantAdmin)
   - [ ] Display tenant ID, name, description, created date, and active status

3. **Get tenant by ID:**
   - [ ] Implement get --id <id> command
   - [ ] Handle authorization (GlobalAdmin can see all, TenantAdmin only their own)

4. **Deactivate tenant:**
   - [ ] Implement deactivate --id <id> command with confirmation prompt
   - [ ] Add warning message about impact (tenant operations will be blocked)
   - [ ] GlobalAdmin only operation

5. **Security considerations:**
   - [ ] All commands should check for GlobalAdmin role and provide clear error if not authorized
   - [ ] Document that these are GlobalAdmin-only operations (except list/get for TenantAdmin's own tenant)

---

## Review Summary

**Controllers Reviewed:** 10/10 completed

**Status Update (2026-01-30):**
- ✅ **Pagination Rollout Phase 1 Complete**: Implemented pagination/sorting/summary for 4 key list commands
  - EnrollmentController: list command ✅
  - AuditLogController: list command ✅
  - AuthAttemptController: list command ✅
  - IntegrationController: list command ✅
  - AdminProvisioningController: list command ✅ (NEW - created provisioning command group)

**Overall Statistics:**
- Fully aligned: 2 (EncryptionKeyController, AuditLogController)
- Mostly aligned with pagination implemented: 5 (Enrollment, AuthAttempt, Integration, AdminProvisioning, EnrollmentController)
- Mostly aligned with minor issues: 2 (AdminAuthController, ApiKeyController)
- Partially integrated: 1 (AdminProvisioningController - list only)
- Not integrated: 1 (TenantController)

**Common Patterns Requiring Attention:**
1. ✅ **COMPLETED (2026-01-30)**: Pagination/Sorting - Now implemented in all major list commands
2. ⏳ **IN PROGRESS**: Search Filters - Still incomplete for some controllers
3. ⏳ **PENDING**: Missing Endpoints (cancel for AuthAttempt, provisioning CRUD for AdminProvisioning, tenant management)
4. ⏳ **PENDING**: Create Commands - Still often use generic --data instead of explicit options

**Priority Actions Remaining:**
1. Add missing search filters to list commands (status, names, date ranges)
2. Implement remaining provisioning endpoints (create-global, create-tenant, onboarding, qrcode, deactivate)
3. Add cancel endpoint for auth-attempt
4. Implement tenant management commands for GlobalAdmin
5. Consider improving create commands with explicit options instead of JSON-only approach

---

## Implementation Checklist (Ordered)

### Phase 1 — Improve existing controllers
- [x] **AuthAttemptController**: add missing list filters (`--status`, `--integration-id`, `--created-after`, `--created-before`)
- [x] **AuthAttemptController**: implement `cancel` command (`POST /api/v1/auth-attempts/{id}/cancel`)
- [x] **AuthAttemptController**: validate `--timeout` (1–300) and `--polling` (1–60)
- [x] **EnrollmentController**: add missing list filters (`--status`, `--enrollment-name`, `--active`, `--created-after`, `--created-before`)
- [x] **EnrollmentController**: add explicit `--name` and `--challenge-required` options to `create`
- [x] **IntegrationController**: add missing list filters (`--integration-name`, `--active`, `--created-after`, `--created-before`)
- [x] **IntegrationController**: allow empty `create` payload (no validation error when no `--logo`/`--data`)
- [x] **IntegrationController**: add explicit `--name`, `--description`, `--language` for simple i18n
- [x] **ApiKeyController**: add list-all command (`GET /api/v1/api-keys`)
- [x] **ApiKeyController**: make `description` optional in CLI
- [x] **AdminAuthController**: enforce dashed recovery code format validation

### Phase 2 — Complete missing controllers
- [x] **AdminProvisioningController**: implement `create-global`
- [x] **AdminProvisioningController**: implement `create-tenant`
- [x] **AdminProvisioningController**: implement `onboarding`
- [x] **AdminProvisioningController**: implement `qrcode`
- [x] **AdminProvisioningController**: implement `deactivate`
- [x] **TenantController**: add `tenant` command group (create, list, get, deactivate)

