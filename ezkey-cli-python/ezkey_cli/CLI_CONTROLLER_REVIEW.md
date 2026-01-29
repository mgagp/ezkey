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

**Status:** ❌ not integrated in CLI

### Findings
- No CLI commands found for provisioning or admin onboarding; no references to provisioning endpoints.
- Controller endpoints include: POST /api/v1/admins/global, POST /api/v1/admins/tenant, GET /api/v1/admins, GET /api/v1/admins/{id}/onboarding, GET /api/v1/admins/{id}/onboarding/qrcode, POST /api/v1/admins/{id}/deactivate.

### Actions
- [ ] Decide if provisioning endpoints should be exposed via CLI (global/tenant admin creation, list, onboarding credentials/QR).
- [ ] If yes, add CLI commands under `ezkey admin provisioning` (or similar) with role restrictions documented.

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

**Status:** ✅ aligned, ⚠️ sorting not exposed

### Findings
- Filters: ✅ CLI supports `eventType`, `eventStatus`, `apiName`, `enrollmentId`, `adminId`.
- Pagination: ✅ CLI exposes `page` and `size`.
- Sorting: ⚠️ Controller supports `sort` (field,direction), CLI does not expose `sort`.

### Actions
- [ ] Add `--sort` option to CLI (e.g., `createdAt,desc`) to match controller paging/sort contract.

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
   - ⚠️ Only has `--enrollment-id` filter
   - ❌ Missing filters: status, integration-id, created-after, created-before
   - ❌ Missing pagination parameters (--page, --size)
   - ❌ Missing sorting option (--sort)

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
   - [ ] Add missing filters to `list` command: `--status`, `--integration-id`, `--created-after`, `--created-before`
   - [ ] Add pagination parameters: `--page` (default 0), `--size` (default 20)
   - [ ] Add sorting option: `--sort` (e.g., `authAttemptId,asc` or `createdAt,desc`)

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
5. GET `/api/v1/encryption-keys/reencryption-batches` - List all re-encryption batches
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
   - ✅ Calls GET `/api/v1/encryption-keys/reencryption-batches` - aligned

8. `resume` command:
   - ✅ `--batch-id` (required, type int) calls POST `/reencryption-batches/{batchId}/resume` - aligned
   - Note: CLI uses `int` while controller expects `Integer`, fully compatible

### Actions

**None - Full compliance achieved**

All endpoints are properly exposed in the CLI with correct parameter types, confirmation prompts where appropriate, and informative success/error feedback. The CLI provides excellent UX with formatted output for batch statistics and warnings about critical operations.
---

## EnrollmentController
**Scope:** Enrollment management - search, get by ID, create, delete, QR code generation

**Status:**  mostly aligned,  search filters incomplete,  pagination/sorting missing

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
   -  Only has --integration-id filter
   -  Missing filters: status, enrollment-name, active, created-after, created-before
   -  Missing pagination parameters (--page, --size)
   -  Missing sorting option (--sort)

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
   - [ ] Add missing filters: --status, --enrollment-name, --active, --created-after, --created-before
   - [ ] Add pagination parameters: --page (default 0), --size (default 20)
   - [ ] Add sorting option: --sort (e.g., enrollmentId,asc or createdAt,desc)

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
   -  No filters exposed at all
   -  Missing filters: integration-name, active, created-after, created-before
   -  Missing pagination parameters (--page, --size)
   -  Missing sorting option (--sort)

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
   - [ ] Add missing filters: --integration-name, --active, --created-after, --created-before
   - [ ] Add pagination parameters: --page (default 0), --size (default 20)
   - [ ] Add sorting option: --sort (e.g., id,asc or createdAt,desc)

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
-  AdminAuthController - mostly aligned, minor recovery code format issue
-  AdminEnrollmentController - aligned, token preference edge case
-  AdminProvisioningController - not integrated in CLI
-  ApiKeyController - mostly aligned, missing list-all endpoint
-  AuditLogController - aligned, missing sort parameter
-  AuthAttemptController - mostly aligned, missing cancel endpoint, incomplete search/pagination
-  EncryptionKeyController - fully aligned (excellent!)
-  EnrollmentController - mostly aligned, incomplete search, UX issues in create
-  IntegrationController - mostly aligned, no filters in search, validation too strict in create
-  TenantController - not integrated in CLI

**Overall Statistics:**
- Fully aligned: 1 (EncryptionKeyController)
- Mostly aligned with minor issues: 6
- Not integrated: 2 (AdminProvisioningController, TenantController)

**Common Patterns Requiring Attention:**
1. **Pagination/Sorting**: Missing in many list commands (AuthAttempt, Enrollment, Integration)
2. **Search Filters**: Incomplete filter exposure in list commands
3. **Create Commands**: Often use generic --data instead of explicit options, reducing discoverability
4. **Missing Endpoints**: Cancel (AuthAttempt), provisioning operations, tenant management

**Priority Actions:**
1. Add pagination/sort parameters to all list commands with Pageable controllers
2. Expose all search filters for better query capabilities
3. Implement tenant management commands for GlobalAdmin operations
4. Consider adding explicit options for common create scenarios instead of JSON-only approach
5. Add missing endpoints (cancel, provisioning, list-all for API keys)

