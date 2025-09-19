# PENDING Security Improvement - Implementation Plan

## Overview

This document outlines the implementation plan to address the critical enrollment ID enumeration vulnerability in the PENDING endpoint by adding `enrollmentProofToken` authentication to the request body and removing the enrollment ID from the URL path.

## Security Context

**Current Vulnerability**: 
- PENDING endpoint uses predictable enrollment IDs in URL path: `/api/v1/auth-attempts/pending/{enrollmentId}`
- Enables automated enumeration attacks to discover active enrollments
- **Risk Level**: 🔴 Critical

**Proposed Solution**:
- Remove enrollment ID from URL path
- Add `enrollmentProofToken` to request body for enrollment authentication
- Endpoint becomes: `/api/v1/auth-attempts/pending`
- Eliminates enumeration while maintaining security through cryptographic proof

## Implementation Phases

### Phase 1: Core API Implementation

#### 1.1 Update Auth API DTO (ezkey-auth-api)

**File**: `src/main/java/org/ezkey/auth/dto/request/AuthAttemptPendingRequestDto.java`

**Changes Required**:
```java
/**
 * Request DTO for retrieving pending authentication attempts.
 * Updated to include enrollmentProofToken for secure enrollment identification.
 * 
 * @since 2025
 */
public class AuthAttemptPendingRequestDto {
    
    /**
     * The enrollment ID for internal processing.
     * Note: This field is validated against the enrollmentProofToken for security.
     */
    @NotNull(message = "Enrollment ID is required")
    private Integer enrollmentId;
    
    /**
     * Cryptographic proof token that authenticates the enrollment.
     * This token replaces URL-based enrollment identification to prevent enumeration attacks.
     */
    @NotBlank(message = "Enrollment proof token is required")
    private String enrollmentProofToken;
    
    /**
     * Device-generated unique proof token for anti-replay protection.
     */
    @NotBlank(message = "Device proof token is required")
    private String deviceProofToken;
    
    /**
     * Cryptographic signature of the device proof token using device private key.
     */
    @NotBlank(message = "Device proof token signature is required")
    private String deviceProofTokenSigned;
    
    // Constructors, getters, setters, equals, hashCode
}
```

**Javadoc Updates**:
- Update class documentation to explain security improvement
- Document new `enrollmentProofToken` field purpose
- Add security notes about enumeration prevention

#### 1.2 Update Auth API Controller (ezkey-auth-api)

**File**: `src/main/java/org/ezkey/auth/controller/AuthAttemptController.java`

**Changes Required**:
```java
/**
 * Retrieve pending authentication attempts for a mobile device.
 * 
 * Security Enhancement: Enrollment identification moved from URL path to request body
 * using enrollmentProofToken to prevent enumeration attacks.
 * 
 * @param request the pending request containing enrollment proof token and device authentication
 * @return pending authentication attempt details or 204 No Content if none pending
 * @throws IllegalArgumentException if enrollment proof token is invalid or enrollment not found
 * @since 2025
 */
@PostMapping("/pending")
@Operation(
    summary = "Get pending authentication attempt",
    description = "Retrieve pending authentication attempts using secure enrollment proof token. " +
                 "This endpoint prevents enumeration attacks by requiring cryptographic proof of enrollment ownership.",
    responses = {
        @ApiResponse(responseCode = "200", description = "Pending authentication attempt found"),
        @ApiResponse(responseCode = "204", description = "No pending authentication attempts"),
        @ApiResponse(responseCode = "400", description = "Invalid request or enrollment proof token"),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    }
)
public ResponseEntity<AuthAttemptPendingResponseDto> pending(
        @Valid @RequestBody AuthAttemptPendingRequestDto request,
        HttpServletRequest httpRequest) {
    
    logger.info("Processing pending request for enrollment with proof token");
    
    try {
        AuthAttemptPendingResponseDto response = authAttemptService.pending(request, httpRequest);
        return ResponseEntity.ok(response);
    } catch (NoSuchElementException e) {
        logger.debug("No pending authentication attempts found");
        return ResponseEntity.noContent().build();
    }
}
```

**Key Changes**:
- Remove `@PathVariable("enrollmentId") Integer id` parameter
- Update `@PostMapping` from `/pending/{enrollmentId}` to `/pending`
- Update OpenAPI documentation to reflect security improvement
- Update method signature to use request body for enrollment identification

#### 1.3 Update Core Service Logic (ezkey-core)

**File**: `src/main/java/org/ezkey/core/service/AuthAttemptService.java`

**Method**: `pending(AuthAttemptPendingRequest request, HttpServletRequest httpRequest)`

**Changes Required**:
```java
/**
 * Process pending authentication attempt request using secure enrollment proof token.
 * 
 * Security Enhancement: Validates enrollment ownership through cryptographic proof token
 * instead of relying on URL-based enrollment ID, preventing enumeration attacks.
 * 
 * @param request the pending request with enrollment proof token
 * @param httpRequest HTTP request for rate limiting and security monitoring
 * @return pending authentication attempt details
 * @throws IllegalArgumentException if enrollment proof token is invalid
 * @throws NoSuchElementException if no pending authentication attempts exist
 * @since 2025
 */
@Transactional(readOnly = true)
public AuthAttemptPendingResponse pending(AuthAttemptPendingRequest request, HttpServletRequest httpRequest) {
    
    // Rate limiting check (existing logic)
    rateLimitService.checkRateLimit("pending", getClientIP(httpRequest));
    
    // NEW: Find enrollment by proof token instead of ID
    Enrollment enrollment = enrollmentRepository.findByEnrollmentProofTokenAndActive(
        request.getEnrollmentProofToken(), true)
        .orElseThrow(() -> {
            logger.warn("Invalid enrollment proof token provided");
            return new IllegalArgumentException("Authentication request failed");
        });
    
    // Validate that the provided enrollment ID matches the proof token
    if (!enrollment.getEnrollmentId().equals(request.getEnrollmentId())) {
        logger.warn("Enrollment ID mismatch with proof token for enrollment: {}", enrollment.getEnrollmentId());
        throw new IllegalArgumentException("Authentication request failed");
    }
    
    // Continue with existing logic for device proof token validation...
    // (rest of the method remains the same)
}
```

**Repository Enhancement** (if needed):
```java
// In EnrollmentRepository.java
/**
 * Find active enrollment by proof token.
 * Used for secure enrollment identification in PENDING requests.
 * 
 * @param enrollmentProofToken the cryptographic proof token
 * @param active whether the enrollment is active
 * @return enrollment if found and active
 * @since 2025
 */
Optional<Enrollment> findByEnrollmentProofTokenAndActive(String enrollmentProofToken, Boolean active);
```

#### 1.4 Update Unit Tests

**File**: `src/test/java/org/ezkey/auth/controller/AuthAttemptControllerTest.java`

**Test Updates Required**:
```java
@Test
@DisplayName("Should return pending authentication attempt with valid enrollment proof token")
void testPending_Success() {
    // Arrange
    AuthAttemptPendingRequestDto request = new AuthAttemptPendingRequestDto();
    request.setEnrollmentId(123);
    request.setEnrollmentProofToken("valid-proof-token");
    request.setDeviceProofToken("device-token");
    request.setDeviceProofTokenSigned("device-signature");
    
    // Act & Assert
    mockMvc.perform(post("/api/v1/auth-attempts/pending")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());
}

@Test
@DisplayName("Should return 400 when enrollment proof token is missing")
void testPending_MissingProofToken() {
    // Arrange
    AuthAttemptPendingRequestDto request = new AuthAttemptPendingRequestDto();
    request.setEnrollmentId(123);
    // Missing enrollmentProofToken
    request.setDeviceProofToken("device-token");
    request.setDeviceProofTokenSigned("device-signature");
    
    // Act & Assert
    mockMvc.perform(post("/api/v1/auth-attempts/pending")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
}

@Test
@DisplayName("Should return 400 when enrollment proof token is invalid")
void testPending_InvalidProofToken() {
    // Arrange
    AuthAttemptPendingRequestDto request = new AuthAttemptPendingRequestDto();
    request.setEnrollmentId(123);
    request.setEnrollmentProofToken("invalid-proof-token");
    request.setDeviceProofToken("device-token");
    request.setDeviceProofTokenSigned("device-signature");
    
    when(authAttemptService.pending(any(), any()))
        .thenThrow(new IllegalArgumentException("Authentication request failed"));
    
    // Act & Assert
    mockMvc.perform(post("/api/v1/auth-attempts/pending")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
}
```

**File**: `src/test/java/org/ezkey/core/service/AuthAttemptServiceTest.java`

**Service Test Updates**:
```java
@Test
@DisplayName("Should find enrollment by proof token and validate pending request")
void testPending_ValidProofToken() {
    // Arrange
    AuthAttemptPendingRequest request = new AuthAttemptPendingRequest();
    request.setEnrollmentId(123);
    request.setEnrollmentProofToken("valid-proof-token");
    
    Enrollment enrollment = new Enrollment();
    enrollment.setEnrollmentId(123);
    enrollment.setEnrollmentProofToken("valid-proof-token");
    
    when(enrollmentRepository.findByEnrollmentProofTokenAndActive("valid-proof-token", true))
        .thenReturn(Optional.of(enrollment));
    
    // Act & Assert
    assertDoesNotThrow(() -> authAttemptService.pending(request, mockHttpRequest));
}

@Test
@DisplayName("Should throw exception when enrollment proof token not found")
void testPending_ProofTokenNotFound() {
    // Arrange
    AuthAttemptPendingRequest request = new AuthAttemptPendingRequest();
    request.setEnrollmentProofToken("non-existent-token");
    
    when(enrollmentRepository.findByEnrollmentProofTokenAndActive("non-existent-token", true))
        .thenReturn(Optional.empty());
    
    // Act & Assert
    assertThrows(IllegalArgumentException.class, 
        () -> authAttemptService.pending(request, mockHttpRequest));
}
```

#### 1.5 Update Javadoc Documentation

**Files to Update**:
- `AuthAttemptController.java` - Update method documentation
- `AuthAttemptService.java` - Update service method documentation
- `AuthAttemptPendingRequestDto.java` - Add comprehensive field documentation
- `AuthAttemptPendingResponseDto.java` - Update if needed

**Documentation Standards**:
- Explain security enhancement rationale
- Document new field purposes and validation rules
- Include `@since 2025` tags for new/modified elements
- Add security notes about enumeration prevention

### Phase 2: Documentation and Demo Updates

#### 2.1 Update Project Documentation

**Files to Review and Update**:

**2.1.1 README.md**
- Update API endpoint examples to show new PENDING format
- Update security section to mention enumeration protection
- Update quick start examples with correct request format

**2.1.2 docs/ENDPOINT.md**
- Update PENDING endpoint specification:
  - Change URL from `/pending/{enrollmentId}` to `/pending`
  - Update request body to include `enrollmentProofToken`
  - Update security notes about enumeration prevention
  - Add migration notes for existing implementations

**2.1.3 PRD.txt**
- Review if security enhancement aligns with product principles
- Update if any security-related sections need modification

**2.1.4 docs/features/AUTH_SECURITY.md**
- Update vulnerability status from 🔴 Critical to ✅ Resolved
- Document the implemented solution
- Update risk assessment matrix
- Add implementation validation notes

#### 2.2 Update Demo Device Application

**File**: `ezkey-demo-device/src/main/java/org/ezkey/demo/device/service/AuthAttemptService.java`

**Changes Required**:
```java
/**
 * Poll for pending authentication attempts using secure enrollment proof token.
 * Updated to include enrollmentProofToken in request body for security enhancement.
 * 
 * @param enrollmentId the enrollment ID
 * @param enrollmentProofToken the cryptographic proof token for enrollment authentication
 * @return pending authentication attempt or null if none
 * @since 2025
 */
public AuthAttemptPendingResponse checkPending(Integer enrollmentId, String enrollmentProofToken) {
    
    // Load enrollment data to get proof token if not provided
    if (enrollmentProofToken == null) {
        EnrollmentData enrollmentData = loadEnrollmentData(enrollmentId);
        enrollmentProofToken = enrollmentData.getEnrollmentProofToken();
    }
    
    // Create request with proof token
    AuthAttemptPendingRequest request = new AuthAttemptPendingRequest();
    request.setEnrollmentId(enrollmentId);
    request.setEnrollmentProofToken(enrollmentProofToken); // NEW: Add proof token
    request.setDeviceProofToken(generateDeviceProofToken());
    request.setDeviceProofTokenSigned(signDeviceProofToken(request.getDeviceProofToken()));
    
    // Updated URL - no enrollment ID in path
    String url = authApiBaseUrl + "/api/v1/auth-attempts/pending";
    
    try {
        ResponseEntity<AuthAttemptPendingResponse> response = restTemplate.postForEntity(
            url, request, AuthAttemptPendingResponse.class);
        
        return response.getStatusCode() == HttpStatus.OK ? response.getBody() : null;
    } catch (Exception e) {
        logger.warn("Failed to check pending authentication: {}", e.getMessage());
        return null;
    }
}
```

**Enrollment Data Loading**:
```java
/**
 * Load enrollment data from local storage.
 * Updated to ensure enrollmentProofToken is available for PENDING requests.
 */
private EnrollmentData loadEnrollmentData(Integer enrollmentId) {
    try {
        String filename = String.format("data/enrollments/%d.json", enrollmentId);
        String content = Files.readString(Paths.get(filename));
        return objectMapper.readValue(content, EnrollmentData.class);
    } catch (IOException e) {
        throw new RuntimeException("Failed to load enrollment data: " + enrollmentId, e);
    }
}
```

## Implementation Checklist

### Phase 1: Core Implementation
- [ ] Update `AuthAttemptPendingRequestDto` to include `enrollmentProofToken`
- [ ] Modify `AuthAttemptController.pending()` method signature and mapping
- [ ] Update `AuthAttemptService.pending()` to use proof token for enrollment lookup
- [ ] Add/update repository method `findByEnrollmentProofTokenAndActive()`
- [ ] Update all unit tests for controller and service layers
- [ ] Update Javadoc documentation for all modified classes and methods
- [ ] Verify compilation and basic functionality

### Phase 2: Integration and Documentation
- [ ] Update `README.md` with new endpoint format
- [ ] Update `docs/ENDPOINT.md` with complete API specification changes
- [ ] Review and update `PRD.txt` if needed
- [ ] Update `docs/features/AUTH_SECURITY.md` vulnerability status
- [ ] Modify `ezkey-demo-device` to use new PENDING format
- [ ] Update demo device enrollment data loading logic
- [ ] Verify demo device functionality with new API format

## Testing Strategy

### Security Validation
1. **Enumeration Attack Prevention**:
   - Verify that sequential ID requests to `/pending` return 404/400
   - Confirm that only valid proof tokens return authentication attempts
   - Test with invalid/expired proof tokens

2. **Functionality Verification**:
   - Test normal authentication flow with proof token
   - Verify error handling for missing/invalid proof tokens
   - Confirm rate limiting still functions correctly

3. **Integration Testing**:
   - Test demo device with updated PENDING format
   - Verify end-to-end authentication flow
   - Validate OpenAPI specification accuracy

## Security Impact

**Before**: 
- Enrollment IDs exposed in URL path
- Predictable sequential enumeration possible
- High risk of user base discovery

**After**:
- Enrollment identification through cryptographic proof token
- No predictable enumeration vectors
- Maintained authentication security while eliminating enumeration risk

## Migration Notes

**Breaking Change**: This is a breaking change for existing API consumers.

**Migration Steps for API Consumers**:
1. Update request URL from `/pending/{enrollmentId}` to `/pending`
2. Add `enrollmentProofToken` to request body
3. Update request content-type to `application/json` if not already
4. Test with updated API format

**Backward Compatibility**: Not maintained due to security requirements.

## Success Criteria

- [ ] Enumeration attacks no longer possible on PENDING endpoint
- [ ] All existing functionality maintained
- [ ] Demo applications work with updated API
- [ ] Unit and integration tests pass
- [ ] Documentation accurately reflects new API format
- [ ] Security vulnerability marked as resolved

---

**Document Version**: 1.0  
**Created**: 2025-01-17  
**Implementation Target**: Phase 1 - 2 days, Phase 2 - 3 days  
**Security Priority**: Critical - Immediate implementation required
