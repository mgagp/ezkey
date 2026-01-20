---
name: demo_device_tenant_grouping
overview: Group DemoDevice enrollments by tenant (simple header/divider UI) and extend Auth API bind response with minimal tenant metadata so DemoDevice can persist and render tenant grouping reliably.
todos:
  - id: inspect-demo-device-rendering
    content: Confirm DemoDevice grouping needs by reviewing `EzkeyAppController` + `phone/ezkey/home.html` and define grouped view model + sorting rules.
    status: pending
  - id: extend-auth-bind-response
    content: Extend `EnrollmentBindResponse` (core) and `EnrollmentBindResponseDto` (auth-api) to include `tenantId`, `tenantName`, `tenantDescription`, and populate from `Integration.tenant` during bind.
    status: completed
  - id: update-demo-device-storage-and-ui
    content: Persist tenant fields in `EnrollmentStoreService.Record`, store them during bind, and update `phone/ezkey/home.html` to render tenant headers + divider with grouped enrollments.
    status: in_progress
  - id: refresh-demo-device-openapi-generated-dtos
    content: Refresh `ezkey-demo-device/openapi-spec.json` from Auth API and regenerate `org.ezkey.demodevice.generated.dto` so DemoDevice can compile with the new bind response fields.
    status: in_progress
  - id: manual-verification
    content: Run the demo with multiple tenants/enrollments and verify grouping, sorting, and fallbacks.
    status: pending
---

## Goal

Improve `ezkey-demo-device` enrollment list readability when multiple tenants/enrollments exist by grouping enrollments **by tenant** in the DemoDevice UI, while ensuring the DemoDevice-local enrollment JSON contains enough tenant metadata. Prefer minimal API changes and keep demo UI simple.

## Current state (findings)

- DemoDevice lists enrollments from **local filesystem** storage: `ezkey-demo-device/src/main/java/org/ezkey/demo/device/service/EnrollmentStoreService.java` stores each enrollment as `data/enrollments/<enrollmentId>.json`.
- The UI template `ezkey-demo-device/src/main/resources/templates/phone/ezkey/home.html` currently iterates directly over `${enrollments}` (flat list) and shows integration branding + enrollment name.
- Tenant exists in domain: `org.ezkey.integration.domain.entity.Integration` has `tenant` (JPA relation) and `Tenant` has `tenantName` + `tenantDescription`.
- Auth API bind response currently returns integration metadata (name/description/logo) but **no tenant metadata**:
- Domain: `ezkey-core/src/main/java/org/ezkey/enrollment/domain/EnrollmentBindResponse.java`
- DTO: `ezkey-auth-api/src/main/java/org/ezkey/enrollment/dto/EnrollmentBindResponseDto.java`

## Decision

- **API source**: Extend Auth API `POST /api/v1/enrollments/bind` response to include tenant metadata.
- **UI**: Use the simplest grouping style: **header row + divider** per tenant (no boxes).
- **Tenant identifier**: Include `tenantId` in API as a stable grouping key (recommended), plus `tenantName` and `tenantDescription` for display.

## Proposed data shape

### 1) Auth API: extend bind response

Add the following fields to the bind response (domain + DTO):

- `tenantId` (Integer)
- `tenantName` (String)
- `tenantDescription` (String)

Populate them from `integration.getTenant()` during bind.

### 2) DemoDevice local enrollment record

Extend `EnrollmentStoreService.Record` to persist the tenant metadata for later grouping:

- `tenantId`
- `tenantName`
- `tenantDescription`

During bind in `EzkeyAppController.bindEnrollment(...)`, persist those fields from the bind API response.

### 3) DemoDevice view model for grouping

In `EzkeyAppController.appHome(...)`, replace the flat `enrollments` model attribute with a grouped structure:

- Either `List<TenantGroupViewModel>` where each group contains `tenantName`, `tenantDescription`, and `List<Record>`.
- Or `Map<String, List<Record>>` keyed by a stable key (prefer `tenantId` when present).

Sorting rules (simple, predictable):

- Sort tenant groups by `tenantName` (fallback: `tenantId`, then “Unknown tenant”).
- Within each group, sort enrollments by `integrationName`, then `enrollmentName`, then `enrollmentId`.

### 4) Template update

Update `ezkey-demo-device/src/main/resources/templates/phone/ezkey/home.html` to:

- Iterate groups
- Render a tenant header (name + optional description)
- Render a thin divider
- Render the existing enrollment “cards” under each group

Keep CSS changes minimal and inline (current file already uses inline CSS).

## API evolution details (server-side)

### Files likely to change

- `ezkey-core/src/main/java/org/ezkey/enrollment/domain/EnrollmentBindResponse.java`
- `ezkey-core/src/main/java/org/ezkey/enrollment/service/EnrollmentBindService.java` (set tenant fields when building response)
- `ezkey-auth-api/src/main/java/org/ezkey/enrollment/dto/EnrollmentBindResponseDto.java`
- `ezkey-auth-api/src/main/java/org/ezkey/enrollment/mapper/EnrollmentAuthMapper.java` (verify mapping is correct; MapStruct should map same-named properties automatically)
- Auth API OpenAPI output (via springdoc); DemoDevice’s `openapi-spec.json` will need refresh.

### Backward compatibility

- Adding fields to response is backward compatible for existing clients.
- DemoDevice will start persisting and rendering tenant info when available.

## DemoDevice OpenAPI generated DTOs

DemoDevice uses generated DTOs under `org.ezkey.demodevice.generated.dto` sourced from `ezkey-demo-device/openapi-spec.json`.
Plan:

- After Auth API is updated, refresh `ezkey-demo-device/openapi-spec.json` from Auth API `/v3/api-docs` (or run the existing script/process used in the repo).
- Regenerate DTOs via the module’s OpenAPI generator Maven config.

## Test/verification approach (manual)

- Create 2 tenants, 2 integrations (one per tenant), and multiple enrollments.
- Bind enrollments in DemoDevice; confirm `data/enrollments/*.json` includes tenant fields.
- Visit `http://localhost:8083/phone/ezkey` and confirm:
- Enrollments appear grouped under tenant headers
- Ordering is stable and readable
- Fallback behavior works if tenant fields are missing (single “Unknown tenant” group)

## Notes on tenantId importance

- `tenantName` is user-facing and great for display.
- `tenantId` is recommended as the stable grouping key (avoids edge cases and aligns with future mobile app direction).
