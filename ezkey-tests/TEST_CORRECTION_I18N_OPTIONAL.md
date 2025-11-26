# Test Correction - i18n is Optional for Integrations

## Issue Summary

The test `testCreateIntegrationMissingI18nReturns400` was incorrectly assuming that `i18n` is a required field when creating integrations. However, the implementation shows that `i18n` is **optional**.

## Root Cause Analysis

### Source of Confusion

1. **Documentation (ENDPOINT.md)**: The example always showed `i18n` present, which led to the assumption it was required.

2. **Code Source Reality**:
   - `IntegrationCreateRequestDto` javadoc clearly states: **"Optional internationalization data"** (line 21, 32, 41)
   - No validation annotations (`@NotNull`, `@NotEmpty`) on `i18n` field
   - System Integration is created **without** i18n and **without** logo

3. **System Integration Use Case**:
   - The System Integration (used for global admin authentication) is created programmatically without i18n
   - This is intentional: `systemIntegration.setLogo(null); // No logo for system integration`
   - System Integration doesn't need i18n because it's not displayed to end users

## Evidence from Code

### IntegrationCreateRequestDto.java
```java
/**
 * <p>This DTO contains the data required to create a new Integration through the admin API.
 * Integrations represent applications or systems that will be protected by Ezkey MFA. It includes
 * basic integration information and optional internationalization data for multi-language support.
 *
 * <p><b>Fields:</b>
 * <ul>
 *   <li><b>i18n:</b> Optional internationalization data for multi-language support
 * </ul>
 *
 * @param i18n Optional list of internationalization entries containing localized name and
 *     description for different languages
 */
public record IntegrationCreateRequestDto(
    String logo,
    List<IntegrationI18nCreateDto> i18n) {}  // No @NotNull annotation
```

### AdminBootstrapService.java
```java
// Create System Integration
Integration systemIntegration = new Integration();
systemIntegration.setLogo(null); // No logo for system integration
systemIntegration.setActive(true);
// No i18n data set - System Integration doesn't need it
integrationRepository.save(systemIntegration);
```

## Changes Made

### 1. Test Correction
- **File**: `IntegrationManagementSecurityTest.java`
- **Change**: Renamed and updated `testCreateIntegrationMissingI18nReturns400` to `testCreateIntegrationWithoutI18nSucceeds`
- **New Behavior**: Test now verifies that creating an integration without i18n succeeds (201) and that the created integration has empty/null i18n

### 2. Documentation Update
- **File**: `docs/ENDPOINT.md`
- **Change**: Added clarification that both `logo` and `i18n` are optional
- **Added**: Example showing creation without i18n (System Integration use case)
- **Clarified**: Regular integrations typically include i18n, but System Integration does not

## Rationale

### Why i18n is Optional

1. **System Integration**: The System Integration is a special internal integration used for global admin authentication. It doesn't need i18n because:
   - It's not displayed to end users
   - It's created programmatically during bootstrap
   - It's marked with `isSystemIntegration=true`

2. **Regular Integrations**: Regular integrations (created via API) typically **should** include i18n for:
   - Multi-language support in mobile apps
   - User-facing display of integration names/descriptions
   - Better user experience

3. **Flexibility**: Making i18n optional provides flexibility for:
   - System integrations
   - Testing scenarios
   - Future use cases where i18n might not be needed

## Impact

- **Tests**: Now correctly reflect that i18n is optional
- **Documentation**: Clarifies when i18n is needed vs optional
- **Consistency**: Aligns tests and documentation with actual implementation

## Related Files

- `ezkey-tests/src/test/java/org/ezkey/tests/security/integration/IntegrationManagementSecurityTest.java`
- `docs/ENDPOINT.md`
- `ezkey-admin-api/src/main/java/org/ezkey/integration/dto/IntegrationCreateRequestDto.java`
- `ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminBootstrapService.java`

