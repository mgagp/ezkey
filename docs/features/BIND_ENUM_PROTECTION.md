# Bind Endpoint Enumeration Protection

## Overview

This document describes the implementation of protection against enumeration attacks on the enrollment bind endpoint in the auth-api. The current `GET /api/v1/enrollments/bind/{enrollmentId}` endpoint is vulnerable to enumeration attacks where attackers can systematically test enrollment IDs to discover valid enrollments and obtain sensitive enrollment proof tokens.

## Problem Statement

### Current Vulnerability

The enrollment bind endpoint currently uses a simple path parameter approach:

```java
@GetMapping("/bind/{enrollmentId}")
public ResponseEntity<EnrollmentBindResponseDto> bind(@PathVariable("enrollmentId") Integer enrollmentId, ...)
```

**Security Issues:**
1. **Enumeration Attack**: Attackers can test sequential enrollment IDs (1, 2, 3, 4...)
2. **Information Disclosure**: Valid enrollment IDs return sensitive data including `enrollmentProofToken`
3. **Token Exposure**: The `enrollmentProofToken` is exposed to unauthorized parties
4. **Attack Surface**: Easy to automate and scale attacks

### Impact Assessment

- **High Risk**: Enumeration reveals valid enrollment IDs and proof tokens
- **Attack Vector**: Automated testing of enrollment IDs
- **Data Exposure**: Sensitive enrollment metadata and cryptographic tokens
- **Compliance**: Potential violation of security best practices

## Current Enrollment Flow

### 1. Enrollment Creation (Admin-API)
```java
// EnrollmentService.create()
enrollment.setEnrollmentProofToken(signatureService.generateProofToken()); // Generates unique token
```

### 2. Enrollment Bind (Auth-API) - VULNERABLE
```java
// GET /api/v1/enrollments/bind/{enrollmentId}
// Returns: enrollmentProofToken, integrationPublicKey, integration metadata
response.setEnrollmentProofToken(enrollment.getEnrollmentProofToken());
```

### 3. Enrollment Verify (Auth-API)
```java
// POST /api/v1/enrollments/verify
// Device signs the enrollmentProofToken with its private key
request.setEnrollmentProofTokenSigned(signature);
```

## Proposed Solution

### Security Principle: Proof Token Authentication

Transform the enrollment ID from a public identifier to a hidden parameter, requiring the `enrollmentProofToken` as authentication for access.

### Implementation Approach

**Before (Vulnerable):**
```http
GET /api/v1/enrollments/bind/123
```

**After (Secure):**
```http
POST /api/v1/enrollments/bind
Content-Type: application/json

{
  "enrollmentId": 123,
  "enrollmentProofToken": "abc123-def456-ghi789"
}
```

### Security Benefits

1. **Eliminates Enumeration**: Impossible to guess valid tokens
2. **Access Control**: Only parties with valid tokens can access enrollment data
3. **Token Protection**: Proof tokens are not exposed in URLs or logs
4. **Audit Trail**: All access attempts are logged with token validation
5. **Rate Limiting**: Enhanced protection through existing rate limiting system

## Implementation Details

### 1. Controller Changes

#### Current Implementation
```java
@GetMapping("/bind/{enrollmentId}")
public ResponseEntity<EnrollmentBindResponseDto> bind(
    @PathVariable("enrollmentId") Integer enrollmentId,
    @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
    // Vulnerable implementation
}
```

#### Secure Implementation
```java
@PostMapping("/bind")
@Operation(summary = "Initiate device binding with enrollment proof token", 
           description = "Retrieves enrollment binding information using secure enrollment proof token")
@ApiResponses(value = {
    @ApiResponse(responseCode = "200", description = "Enrollment binding information retrieved successfully"),
    @ApiResponse(responseCode = "400", description = "Invalid enrollment ID, proof token, or enrollment expired"),
    @ApiResponse(responseCode = "409", description = "Enrollment already bound or proof token already used"),
    @ApiResponse(responseCode = "500", description = "Internal server error")
})
public ResponseEntity<EnrollmentBindResponseDto> bind(@RequestBody EnrollmentBindRequestDto request) {
    // Validation: enrollmentId + enrollmentProofToken required
    if (request.getEnrollmentId() == null || request.getEnrollmentProofToken() == null || request.getEnrollmentProofToken().trim().isEmpty()) {
        throw new IllegalArgumentException("Enrollment ID and enrollment proof token are required");
    }
    
    EnrollmentBindRequest bindRequest = new EnrollmentBindRequest();
    bindRequest.setEnrollmentId(request.getEnrollmentId());
    bindRequest.setEnrollmentProofToken(request.getEnrollmentProofToken());
    bindRequest.setLanguage(request.getLanguage());
    
    EnrollmentBindResponse response = enrollmentService.bind(bindRequest);
    return ResponseEntity.ok(enrollmentMapper.toEnrollmentBindResponseDto(response));
}
```

### 2. DTO Updates

#### New EnrollmentBindRequestDto
```java
@Schema(description = "Request DTO for enrollment binding initiation with proof token")
public class EnrollmentBindRequestDto {
    
    /**
     * The enrollment ID to bind to the mobile device.
     * Must reference an existing enrollment created through the admin API.
     */
    @Schema(description = "Enrollment ID to bind to the mobile device", 
            example = "123", 
            required = true)
    private Integer enrollmentId;
    
    /**
     * The enrollment proof token for authentication.
     * Must match the proof token generated during enrollment creation.
     */
    @Schema(description = "Enrollment proof token for authentication", 
            example = "abc123-def456-ghi789", 
            required = true)
    private String enrollmentProofToken;
    
    /**
     * Preferred language for internationalization.
     */
    @Schema(description = "Preferred language for i18n fields", 
            example = "en")
    private String language;
    
    // Getters and setters...
}
```

### 3. Service Layer Changes

#### Enhanced EnrollmentService.bind()
```java
public EnrollmentBindResponse bind(EnrollmentBindRequest request) {
    // 1. Retrieve enrollment with proof token validation
    Enrollment enrollment = enrollmentRepository
        .findByEnrollmentIdAndEnrollmentProofToken(request.getEnrollmentId(), request.getEnrollmentProofToken())
        .orElseThrow(() -> new ResourceNotFoundException("Enrollment", request.getEnrollmentId()));
    
    // 2. Verify enrollment state
    if (!enrollment.getStatus().equals(EnrollmentStatus.CREATED)) {
        throw new IllegalStateException("Enrollment already bound or invalid state");
    }
    
    // 3. Mark as BOUND (read-once guarantee)
    enrollment.setStatus(EnrollmentStatus.BOUND);
    enrollmentRepository.save(enrollment);
    
    // 4. Build and return response
    return buildBindResponse(enrollment);
}
```

### 4. Repository Updates

#### New Repository Method
```java
public interface EnrollmentRepository extends JpaRepository<Enrollment, Integer> {
    
    /**
     * Find enrollment by ID and proof token for secure binding.
     * 
     * @param enrollmentId the enrollment ID
     * @param enrollmentProofToken the enrollment proof token
     * @return Optional enrollment if found and token matches
     */
    Optional<Enrollment> findByEnrollmentIdAndEnrollmentProofToken(Integer enrollmentId, String enrollmentProofToken);
}
```

### 5. Rate Limiting Configuration

#### Enhanced Rate Limiting
```properties
# Rate limiting for bind endpoint (very restrictive)
ezkey.rate-limit.bind.enabled=true
ezkey.rate-limit.bind.requests=5
ezkey.rate-limit.bind.window-minutes=10
ezkey.rate-limit.bind.key-strategy=client-ip
```

#### Rate Limit Filter Updates
```java
// Add bind endpoint to rate limiting
private boolean shouldApplyRateLimit(String requestUri, String requestMethod) {
    if (!"POST".equals(requestMethod)) { return false; }
    if (requestUri.contains(AuthAttemptController.FULL_PATH_PENDING)) { return true; }
    if (requestUri.contains(EnrollmentController.FULL_PATH_VERIFY)) { return true; }
    if (requestUri.contains(EnrollmentController.FULL_PATH_BIND)) { return true; } // NEW
    return false;
}
```

## Implementation Steps

### Phase 1: Core Implementation

1. **Update ezkey-core**
   - [ ] Add `findByEnrollmentIdAndEnrollmentProofToken()` method to `EnrollmentRepository`
   - [ ] Update `EnrollmentBindRequest` domain object to include `enrollmentProofToken`
   - [ ] Modify `EnrollmentService.bind()` method for token validation
   - [ ] Use existing `ResourceNotFoundException` for invalid enrollment/token combinations

2. **Update ezkey-auth-api**
   - [ ] Change `EnrollmentController.bind()` from GET to POST
   - [ ] Create new `EnrollmentBindRequestDto` with proof token field
   - [ ] Update `EnrollmentAuthMapper` for new DTO mapping
   - [ ] Add rate limiting configuration for bind endpoint
   - [ ] Update `RateLimitFilter` to include bind endpoint

3. **Update ezkey-admin-api**
   - [ ] Ensure enrollment creation properly generates proof tokens
   - [ ] Verify admin endpoints return proof tokens in responses
   - [ ] Update any admin DTOs that expose proof tokens

### Phase 2: Documentation Updates

4. **Update Project Documentation**
   - [ ] Update `README.md` with new bind endpoint format
   - [ ] Update `docs/ENDPOINT.md` with new POST /bind specification
   - [ ] Add security section explaining enumeration protection
   - [ ] Update API documentation with new request/response format

5. **Update OpenAPI Specifications**
   - [ ] Regenerate OpenAPI specs for auth-api with new bind endpoint
   - [ ] Update endpoint documentation with security considerations
   - [ ] Add examples showing new request format

### Phase 3: Testing and Validation

6. **Postman Testing (User Action Required)**
   - [ ] **USER ACTION**: Update Postman collection with new bind endpoint
   - [ ] **USER ACTION**: Test enrollment flow with new POST /bind format
   - [ ] **USER ACTION**: Verify enumeration protection works
   - [ ] **USER ACTION**: Test rate limiting on bind endpoint
   - [ ] **USER ACTION**: Validate error responses for invalid tokens

### Phase 4: Demo Application Updates

7. **Update ezkey-demo-device**
   - [ ] **USER ACTION**: Update demo device to use new POST /bind endpoint
   - [ ] **USER ACTION**: Modify QR code generation to include proof tokens
   - [ ] **USER ACTION**: Update enrollment flow to send proof token in request
   - [ ] **USER ACTION**: Test end-to-end enrollment with new format

8. **Integration Testing**
   - [ ] **USER ACTION**: Test complete enrollment flow: create → bind → verify
   - [ ] **USER ACTION**: Verify mobile app compatibility
   - [ ] **USER ACTION**: Test error scenarios and edge cases

## Security Considerations

### Token Security
- **Unique Generation**: Each enrollment gets a unique proof token
- **Cryptographic Strength**: Tokens generated using secure random methods
- **Usage Tracking**: Tokens can be marked as used after bind operation
- **Expiration**: Consider adding token expiration for enhanced security

### Rate Limiting
- **Strict Limits**: Very restrictive rate limiting on bind endpoint
- **IP-based**: Rate limiting based on client IP address
- **Monitoring**: Log all rate limiting events for security monitoring

### Error Handling
- **Uniform Responses**: Same error response for invalid enrollment IDs and tokens
- **No Information Disclosure**: Avoid revealing whether enrollment exists
- **Audit Logging**: Log all bind attempts for security analysis

## Migration Strategy

### Backward Compatibility
- **Breaking Change**: This is a breaking change requiring mobile app updates
- **Version Management**: Consider API versioning for gradual migration
- **Deprecation Notice**: Provide advance notice of endpoint changes

### Rollout Plan
1. **Development**: Implement in development environment
2. **Testing**: Comprehensive testing with demo applications
3. **Staging**: Deploy to staging for integration testing
4. **Production**: Deploy with mobile app updates ready

## Monitoring and Alerting

### Security Metrics
- **Failed Bind Attempts**: Monitor for enumeration attack patterns
- **Rate Limiting Events**: Track rate limiting triggers
- **Token Validation Failures**: Monitor for potential attacks
- **Unusual Access Patterns**: Detect automated enumeration attempts

### Alerting
- **High Failure Rate**: Alert on high bind failure rates
- **Rate Limit Breaches**: Alert on repeated rate limit violations
- **Suspicious Patterns**: Alert on systematic enrollment ID testing

## Future Enhancements

### Additional Security Measures
1. **Token Expiration**: Add time-based expiration to proof tokens
2. **Usage Limits**: Limit number of bind attempts per token
3. **Device Fingerprinting**: Enhanced client identification
4. **CAPTCHA Integration**: Human verification for suspicious activity

### Performance Optimizations
1. **Caching**: Cache enrollment lookups for performance
2. **Database Indexing**: Optimize database queries for token lookups
3. **Connection Pooling**: Optimize database connections

## Conclusion

This implementation provides comprehensive protection against enumeration attacks on the enrollment bind endpoint while maintaining the existing security model and user experience. The solution leverages existing proof tokens to create a secure authentication mechanism that prevents unauthorized access to enrollment data.

The implementation follows Ezkey's core values of **security**, **simplicité**, and **rigueur** by providing a robust security solution without adding unnecessary complexity to the system.

---

**Document Version**: 1.0  
**Last Updated**: January 2025  
**Author**: Ezkey Security Team  
**Review Status**: Pending Implementation
