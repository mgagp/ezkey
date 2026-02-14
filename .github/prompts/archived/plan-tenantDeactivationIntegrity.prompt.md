## Plan: Enforce Tenant Deactivation Integrity Rules

When a global admin deactivates a tenant, only `tenant.active = false` is persisted today — no cascading enforcement exists. This plan introduces layered guards following the established RFC 9457 (`ProblemDetail`) error pattern already used for admin deactivation, and prepares ground for future CLI/TUI impact.

### Steps

1. **Extract tenant deactivation into a service layer** — Move logic from `TenantController.deactivateTenant()` into a new `TenantService.deactivateTenant(Integer tenantId, AdminPrincipal principal)` method. Add business rules: block deactivation of the system tenant (`isSystemTenant = true`), set `active = false`, and call `adminTokenService.revokeTokensForTenant(tenantId)` to bulk-revoke active tokens — aligning with what the Postman docs already claim. The controller becomes a thin delegate, like `AdminProvisioningService.deactivateAdmin()` is for `AdminController`.

2. **Create RFC 9457 exceptions for tenant operations** — Following the pattern of `AdminNotAllowedException` and `AdminLimitException` in `ezkey-admin-api/.../exception/`, create:
   - `TenantNotAllowedException` (system tenant deactivation attempt) → type `https://ezkey.io/problems/tenant-not-allowed`, title `"Tenant Operation Not Allowed"`
   - `TenantInactiveException` (operations on inactive tenant) → type `https://ezkey.io/problems/tenant-inactive`, title `"Tenant Inactive"`

   Register both in `GlobalExceptionHandler` with `ProblemDetail` responses, using `HttpServletRequest` for the `path` property — exactly like the existing admin handlers.

3. **Add tenant-active check at admin login** — In `AdminAuthService.authenticate()`, after the `admin.getActive()` check, verify `admin.getTenant().getActive()`. Throw `AuthenticationException` with message *"Your tenant has been deactivated. Contact your Ezkey administrator."* This blocks new token issuance.

4. **Add tenant-active check in bearer token validation** — In `AdminTokenValidationService.validateToken()`, after loading the token, check `tenant.getActive()`. If inactive, reject the token as invalid. This makes already-issued tokens instantly ineffective without physical DB deletion — the bulk revocation in step 1 is a belt-and-suspenders complement.

5. **Add tenant-active check in API key authentication** — In `ApiKeyValidationService.validateApiKey()`, after validating the key itself, load the parent integration's tenant and verify `tenant.getActive()`. Reject with an appropriate error if inactive. This freezes all SDK/device-facing operations (enrollments, auth attempts) for that tenant.

6. **Add tenant-active guards on write operations** — Add a reusable `TenantService.ensureTenantActive(Integer tenantId)` that throws `TenantInactiveException`. Call it in:
   - `IntegrationService.createIntegration()` — block new integrations
   - `EnrollmentService.createEnrollment()` — block new enrollments
   - `ApiKeyService.createApiKey()` — block new API keys

   **No explicit cascade** to integrations/enrollments — the tenant `active` flag acts as a master switch at runtime, avoiding a complex reactivation problem.

7. **Add unit and integration tests** — Following the pattern in `AdminProvisioningServiceTest`:
   - `TenantServiceTest`: system tenant protection, token revocation on deactivation, already-inactive idempotency
   - `AdminAuthServiceTest`: login blocked when tenant inactive
   - `ApiKeyValidationServiceTest`: API key rejected when tenant inactive
   - Controller-level tests verifying RFC 9457 `ProblemDetail` response shape (`type`, `title`, `detail`, `path` fields)

### Further Considerations

1. **CLI/TUI impact (future phase)** — The TUI's `ApiClient.handle_error()` already reads RFC 9457 `detail` fields correctly, but the tenant detail screen uses a generic error message instead of the API error detail — this will need fixing. The CLI's `error_handler.py` only reads legacy `message`/`code` fields and does **not** parse RFC 9457 — this is a known gap to address in the CLI phase.
2. **Token bulk revocation** — Verify that `AdminTokenService` supports (or can be extended with) a `revokeTokensForTenant(Integer tenantId)` method; the existing `revokeAllTokensForAdmin(Integer adminId)` can serve as the pattern — it will need a query by `tenantId` instead.
3. **Tenant reactivation** — The inverse operation (`POST /tenants/{id}/activate`) should exist for symmetry; since integrations/enrollments are not explicitly cascaded to inactive, reactivation simply sets `active = true` and everything resumes — no complex "restore" logic needed.
