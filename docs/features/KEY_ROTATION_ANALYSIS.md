# Ezkey Key Rotation Analysis - Complete Security Assessment

## Executive Summary

This document provides a comprehensive analysis of key rotation capabilities for Ezkey, including security expert evaluation, implementation recommendations, and detailed technical specifications. Key rotation is identified as a **critical security requirement** for production-grade MFA systems.

---

## 1. Context and Objectives

### 1.1 Project Context

Ezkey is an open-source MFA/Passkey alternative that implements a synchronous, cryptographic authentication solution using RSA-2048 key pairs. The system currently manages three types of cryptographic elements:

- **Integration Keys**: RSA-2048 key pairs stored per enrollment
- **Device Keys**: RSA-2048 public keys from mobile devices  
- **Proof Tokens**: Cryptographic tokens for enrollment binding

### 1.2 Analysis Objectives

1. **Evaluate Security Scenarios**: Assess real-world scenarios requiring key rotation
2. **Design Rotation Mechanisms**: Define secure rotation workflows
3. **Establish Confirmation Protocols**: Implement post-rotation validation
4. **Define Implementation Strategy**: Create phased rollout plan

---

## 2. Security Expert Evaluation

### 2.1 Scenario 1: Backend Compromise - Integration Keys + Enrollment Token

#### **Proposed Scenario**
- Backend has been compromised
- Emergency communication plan with mobile users
- Auth attempt contains `enrollmentProofTokenReplacement` + `integrationPublicKeyReplacement`

#### **Security Expert Assessment: ✅ CRITICAL REQUIREMENT**

**Why This Scenario is Essential:**
1. **Real-world Incident Response**: Backend compromise is a common attack vector
2. **Zero-trust Recovery**: Compromised backend means all stored keys are potentially exposed
3. **Emergency Response Capability**: Organizations need rapid key rotation mechanisms
4. **Compliance Requirements**: Many security frameworks mandate key rotation capabilities

**Security Benefits:**
- **Immediate Mitigation**: Allows instant response to security incidents
- **Compromise Isolation**: Prevents attackers from using stolen keys
- **Audit Trail**: Creates clear record of security incident response
- **User Communication**: Provides secure channel for incident notification

### 2.2 Scenario 2: Device Compromise - Device Key Rotation

#### **Proposed Scenario**
- Mobile device has been compromised
- Verify DTO contains `devicePublicKeyReplacement`

#### **Security Expert Assessment: ✅ CRITICAL NEED**

**Why This Scenario is Essential:**
1. **Mobile Device Vulnerability**: Phones are frequently lost, stolen, or compromised
2. **Key Material Exposure**: Device compromise exposes private keys
3. **Continuous Authentication**: Users need ability to rotate compromised device keys
4. **Incident Recovery**: Enables users to recover from device security incidents

**Security Benefits:**
- **User-Controlled Recovery**: Users can respond to device compromise independently
- **Minimal Admin Intervention**: Reduces support burden for device incidents
- **Immediate Response**: No waiting for admin assistance
- **Security Hygiene**: Encourages good security practices

---

## 3. Terminology Analysis and Recommendations

### 3.1 Current Ezkey Naming Conventions

**Existing DTO Patterns:**
- `enrollmentProofToken` - enrollment proof token
- `authAttemptProofToken` - authentication attempt proof token
- `deviceProofToken` - device proof token
- `integrationPublicKey` - integration public key
- `devicePublicKey` - device public key

### 3.2 Recommended Naming Convention

**Pattern: `{component}Rotated`**

| Current Pattern | Proposed | Recommended | Justification |
|----------------|----------|-------------|---------------|
| `enrollmentProofToken` | `enrollmentProofTokenReplacement` | `enrollmentProofTokenRotated` | "Rotated" is more precise than "Replacement" |
| `integrationPublicKey` | `integrationPublicKeyReplacement` | `integrationPublicKeyRotated` | Aligns with security industry standards |
| `devicePublicKey` | `devicePublicKeyReplacement` | `devicePublicKeyRotated` | Consistent with rotation terminology |

**Rationale:**
- **Industry Standard**: "Rotation" is the established term in cybersecurity
- **Consistency**: Aligns with existing Ezkey naming patterns
- **Clarity**: Clearly indicates the purpose (rotation vs replacement)
- **Future-proof**: Allows for additional rotation metadata if needed

---

## 4. Technical Implementation Specifications

### 4.1 Backend Compromise Scenario - Implementation

#### **Emergency Rotation Auth Attempt:**
```java
// Emergency rotation auth attempt
{
  "enrollmentId": 123,
  "challengeRequested": false,
  "emergencyRotation": true,
  "enrollmentProofTokenRotated": "EZK-NEW123-ABC456",
  "integrationPublicKeyRotated": "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...",
  "rotationReason": "BACKEND_COMPROMISE"
}
```

#### **Critical Security Controls:**
1. **Atomic Updates**: Both `enrollmentProofToken` and `integrationPublicKey` must be updated simultaneously
2. **Immediate Invalidation**: Old keys must be immediately invalidated
3. **Audit Logging**: Complete audit trail of rotation event
4. **Emergency Authentication**: Special auth attempt type for emergency rotation
5. **User Notification**: Clear communication to affected users

### 4.2 Device Compromise Scenario - Implementation

#### **Device Key Rotation in Verify DTO:**
```java
// Device key rotation in verify DTO
{
  "enrollmentId": 456,
  "challengeResponse": 987654,
  "devicePublicKey": "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...",
  "enrollmentProofTokenSigned": "eyJhbGciOiJSUzI1NiJ9...",
  "devicePublicKeyRotated": "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...",
  "rotationReason": "DEVICE_COMPROMISE"
}
```

#### **Critical Security Controls:**
1. **Device Verification**: Prove ownership of new device key
2. **Old Key Invalidation**: Immediately invalidate compromised device key
3. **Enrollment Status Update**: Update enrollment to reflect new device
4. **User Confirmation**: Require explicit user confirmation of rotation

---

## 5. Post-Rotation Confirmation Methodology

### 5.1 Critical Security Requirement

**Problem Identified:**
- **Rotation without Validation**: A key rotation without confirmation of usage can create an inconsistent state
- **Dangerous Intermediate State**: Between rotation and first successful usage, the system is in a vulnerable state
- **Impossible Rollback**: Without confirmation, impossible to know if rotation succeeded

**Risk Scenarios:**
1. **Rotation Successful but Device Incompatible**: New key is installed but device cannot use it
2. **Partial Rotation**: Only part of cryptographic elements has been updated
3. **Communication Interrupted**: Device has not received the new key
4. **Inconsistent State**: Mix of old and new keys in the system

### 5.2 Database Schema for Confirmation Tracking

#### **New Fields in ezkey_enrollment:**
```sql
-- Add rotation tracking columns
ALTER TABLE ezkey_enrollment ADD COLUMN rotation_pending BOOLEAN DEFAULT FALSE;
ALTER TABLE ezkey_enrollment ADD COLUMN rotation_initiated_at TIMESTAMP;
ALTER TABLE ezkey_enrollment ADD COLUMN rotation_confirmed_at TIMESTAMP;
ALTER TABLE ezkey_enrollment ADD COLUMN rotation_attempts_count INT DEFAULT 0;
ALTER TABLE ezkey_enrollment ADD COLUMN rotation_failure_count INT DEFAULT 0;
```

#### **Rotation Audit Table:**
```sql
-- Add rotation audit table
CREATE TABLE ezkey_enrollment_rotation_audit (
    rotation_audit_id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    enrollment_id INT NOT NULL REFERENCES ezkey_enrollment(enrollment_id),
    rotation_type VARCHAR(20) NOT NULL CHECK (rotation_type IN ('ENROLLMENT_TOKEN', 'INTEGRATION_KEY', 'DEVICE_KEY', 'EMERGENCY_ROTATION')),
    rotation_reason VARCHAR(50) NOT NULL,
    rotation_initiated_at TIMESTAMP NOT NULL,
    rotation_confirmed_at TIMESTAMP,
    rotation_status VARCHAR(20) NOT NULL CHECK (rotation_status IN ('PENDING', 'CONFIRMED', 'FAILED', 'TIMEOUT')),
    old_value_hash TEXT, -- Hash of old value for audit
    new_value_hash TEXT, -- Hash of new value for audit
    confirmation_auth_attempt_id INT REFERENCES ezkey_auth_attempt(auth_attempt_id),
    created_by VARCHAR(50) -- 'USER', 'ADMIN', 'SYSTEM'
);
```

### 5.3 Post-Rotation Confirmation Workflow

#### **Phase 1: Rotation Initiation**
```java
// 1. Mark rotation as pending
enrollment.setRotationPending(true);
enrollment.setRotationInitiatedAt(LocalDateTime.now());
enrollment.setRotationAttemptsCount(0);
enrollment.setRotationFailureCount(0);

// 2. Update cryptographic elements
enrollment.setEnrollmentProofToken(newToken);
enrollment.setIntegrationPublicKey(newPublicKey);

// 3. Create audit record
createRotationAuditRecord(enrollment, "PENDING");
```

#### **Phase 2: Confirmation Detection**
```java
// In AuthAttemptService.respond() - Detect first success post-rotation
if (enrollment.isRotationPending() && 
    authAttempt.getAuthAttemptStatus() == AuthAttemptStatus.ACCEPTED) {
    
    // Confirm the rotation
    enrollment.setRotationPending(false);
    enrollment.setRotationConfirmedAt(LocalDateTime.now());
    enrollment.setRotationAttemptsCount(enrollment.getRotationAttemptsCount() + 1);
    
    // Update audit
    updateRotationAuditRecord(enrollment, "CONFIRMED", authAttempt.getAuthAttemptId());
    
    logger.info("Rotation confirmed for enrollment {} after successful auth attempt {}", 
               enrollment.getEnrollmentId(), authAttempt.getAuthAttemptId());
}
```

#### **Phase 3: Failure Handling**
```java
// In AuthAttemptService.respond() - Detect failures post-rotation
if (enrollment.isRotationPending() && 
    (authAttempt.getAuthAttemptStatus() == AuthAttemptStatus.INVALID ||
     authAttempt.getAuthAttemptStatus() == AuthAttemptStatus.REJECTED)) {
    
    enrollment.setRotationFailureCount(enrollment.getRotationFailureCount() + 1);
    
    // If too many failures, mark as failed
    if (enrollment.getRotationFailureCount() >= MAX_ROTATION_FAILURES) {
        enrollment.setRotationPending(false);
        updateRotationAuditRecord(enrollment, "FAILED", authAttempt.getAuthAttemptId());
        
        // Optional: Rollback to old keys
        rollbackRotation(enrollment);
    }
}
```

### 5.4 Rotation Status Management

#### **New Enum RotationStatus:**
```java
public enum RotationStatus {
    PENDING,        // Rotation initiated, waiting for confirmation
    CONFIRMED,      // First success post-rotation observed
    FAILED,         // Too many failures, rotation abandoned
    TIMEOUT,        // Timeout reached without confirmation
    ROLLBACK        // Rollback to old keys
}
```

#### **Security Controls:**
```java
// Rotation timeout after 24h
if (enrollment.isRotationPending() && 
    enrollment.getRotationInitiatedAt().isBefore(LocalDateTime.now().minusHours(24))) {
    
    enrollment.setRotationPending(false);
    updateRotationAuditRecord(enrollment, "TIMEOUT", null);
    
    // Notify admin
    notifyAdminRotationTimeout(enrollment);
}

// Maximum 3 failures before automatic rollback
private static final int MAX_ROTATION_FAILURES = 3;

// Maximum 1 rotation per day
if (enrollment.getRotationInitiatedAt().isAfter(LocalDateTime.now().minusDays(1))) {
    throw new IllegalStateException("Rotation already initiated within 24h");
}
```

---

## 6. API Design Specifications

### 6.1 Emergency Rotation Endpoint

```java
@PostMapping("/api/v1/auth-attempts/emergency-rotation")
@Operation(summary = "Create emergency rotation authentication attempt")
public ResponseEntity<AuthAttemptCreateResponseDto> createEmergencyRotation(
    @Valid @RequestBody AuthAttemptEmergencyRotationRequestDto request) {
    // Implementation for emergency key rotation
}
```

### 6.2 Device Rotation Endpoint

```java
@PostMapping("/api/v1/enrollments/{id}/rotate-device-key")
@Operation(summary = "Rotate device public key")
public ResponseEntity<EnrollmentResponseDto> rotateDeviceKey(
    @PathVariable Integer id,
    @Valid @RequestBody DeviceKeyRotationRequestDto request) {
    // Implementation for device key rotation
}
```

### 6.3 Rotation Status Endpoint

```java
@GetMapping("/api/v1/enrollments/{id}/rotation-status")
@Operation(summary = "Get enrollment rotation status")
public ResponseEntity<RotationStatusDto> getRotationStatus(@PathVariable Integer id) {
    return ResponseEntity.ok(RotationStatusDto.builder()
        .rotationPending(enrollment.isRotationPending())
        .rotationInitiatedAt(enrollment.getRotationInitiatedAt())
        .rotationConfirmedAt(enrollment.getRotationConfirmedAt())
        .rotationAttemptsCount(enrollment.getRotationAttemptsCount())
        .rotationFailureCount(enrollment.getRotationFailureCount())
        .build());
}
```

### 6.4 Rollback Endpoint

```java
@PostMapping("/api/v1/enrollments/{id}/rollback-rotation")
@Operation(summary = "Rollback failed rotation")
public ResponseEntity<Void> rollbackRotation(@PathVariable Integer id) {
    // Rollback to old keys if rotation failed
    enrollmentService.rollbackRotation(id);
    return ResponseEntity.ok().build();
}
```

---

## 7. Security Risk Assessment

### 7.1 Risks of NOT Implementing Key Rotation

**High Risk Scenarios:**
1. **Compromised Backend**: No recovery mechanism from backend compromise
2. **Lost/Stolen Devices**: Users unable to recover from device incidents
3. **Compliance Violations**: Many frameworks require key rotation capabilities
4. **Incident Response Delays**: Manual processes slow down security response

### 7.2 Risks of Implementing Key Rotation

**Medium Risk Scenarios:**
1. **Implementation Complexity**: Increased attack surface
2. **User Confusion**: Users may accidentally trigger rotations
3. **Key Management**: Complexity of managing multiple key versions
4. **Audit Complexity**: More complex audit trails

**Mitigation Strategies:**
- **Clear UI/UX**: Intuitive interfaces for rotation
- **Admin Controls**: Administrative oversight of rotation events
- **Comprehensive Testing**: Thorough testing of rotation flows
- **Documentation**: Clear user and admin documentation

---

## 8. Implementation Priority Assessment

### 8.1 Priority 1: Device Key Rotation (High Priority)
**Rationale:**
- **User Impact**: Directly affects user experience and security
- **Frequency**: Device compromise is common
- **Implementation Complexity**: Moderate complexity
- **Business Value**: High - enables user self-service

### 8.2 Priority 2: Backend Compromise Recovery (Critical Priority)
**Rationale:**
- **Security Impact**: Critical for incident response
- **Frequency**: Low but high impact
- **Implementation Complexity**: High complexity
- **Business Value**: Critical - enables incident response

### 8.3 Priority 3: Integration Key Rotation (Medium Priority)
**Rationale:**
- **Admin Impact**: Affects administrative operations
- **Frequency**: Low
- **Implementation Complexity**: Low complexity
- **Business Value**: Medium - administrative convenience

---

## 9. Implementation Roadmap

### 9.1 Phase 1: Device Key Rotation (3-4 weeks)
**Scope:**
- [ ] Add rotation tracking fields to database schema
- [ ] Implement device key rotation in verify endpoint
- [ ] Add rotation audit logging
- [ ] Create user-facing rotation flows
- [ ] Implement post-rotation confirmation logic
- [ ] Add rotation status monitoring

**Deliverables:**
- Database migration with rotation fields
- Device key rotation API endpoints
- Rotation confirmation workflow
- Basic rotation status monitoring

### 9.2 Phase 2: Emergency Rotation (4-5 weeks)
**Scope:**
- [ ] Implement emergency rotation auth attempts
- [ ] Add admin controls for emergency rotation
- [ ] Create incident response procedures
- [ ] Implement rollback mechanisms
- [ ] Add comprehensive audit trails
- [ ] Create admin monitoring dashboard

**Deliverables:**
- Emergency rotation API endpoints
- Admin emergency rotation interface
- Incident response documentation
- Rollback and recovery procedures

### 9.3 Phase 3: Integration Key Rotation (2-3 weeks)
**Scope:**
- [ ] Implement integration key rotation
- [ ] Add admin interface for key management
- [ ] Complete audit and monitoring
- [ ] Add rotation metrics and reporting
- [ ] Create comprehensive documentation

**Deliverables:**
- Integration key rotation APIs
- Admin key management interface
- Comprehensive audit system
- Complete documentation suite

---

## 10. Security Validation Requirements

### 10.1 Pre-Implementation Security Review
- [ ] Security review of rotation mechanisms
- [ ] Penetration testing of rotation flows
- [ ] Audit trail validation
- [ ] User experience testing
- [ ] Cryptographic validation

### 10.2 Post-Implementation Security Monitoring
- [ ] Continuous monitoring of rotation events
- [ ] Regular security assessments
- [ ] User feedback collection
- [ ] Incident response testing
- [ ] Performance impact analysis

---

## 11. Security Expert Conclusion

### 11.1 Overall Assessment: ✅ HIGHLY RECOMMENDED

**Key Rotation is Essential for Ezkey because:**

1. **Security Best Practice**: Industry standard for cryptographic systems
2. **Incident Response**: Critical capability for security incidents
3. **User Experience**: Enables self-service recovery from device issues
4. **Compliance**: Required by many security frameworks
5. **Risk Mitigation**: Reduces impact of compromise scenarios

### 11.2 Critical Success Factors

**Security Requirements:**
- **Atomic Updates**: Related key rotations must be atomic
- **Confirmation Mandatory**: Post-rotation validation is non-negotiable
- **Audit Trails**: Complete audit trails for all rotation events
- **Rollback Capability**: Ability to rollback failed rotations
- **Monitoring**: Real-time monitoring of rotation status

**Implementation Requirements:**
- **Phased Approach**: Implement in phases to manage complexity
- **Comprehensive Testing**: Thorough testing of all rotation flows
- **User Education**: Clear documentation and user guidance
- **Admin Training**: Training for administrators on rotation procedures

### 11.3 Final Recommendations

**Terminology:**
- Use `{component}Rotated` pattern (e.g., `enrollmentProofTokenRotated`)
- Maintain consistency with existing Ezkey naming conventions
- Document rotation terminology clearly

**Implementation Approach:**
- Start with device key rotation (highest user impact)
- Implement comprehensive audit logging
- Create clear user interfaces for rotation
- Establish incident response procedures

**Security Considerations:**
- Implement atomic updates for related key rotations
- Add comprehensive audit trails
- Create emergency response procedures
- Establish monitoring and alerting

---

## 12. Appendices

### 12.1 DTO Specifications

#### **AuthAttemptEmergencyRotationRequestDto:**
```java
public class AuthAttemptEmergencyRotationRequestDto {
    private Integer enrollmentId;
    private Boolean challengeRequested;
    private Boolean emergencyRotation;
    private String enrollmentProofTokenRotated;
    private String integrationPublicKeyRotated;
    private String rotationReason;
}
```

#### **DeviceKeyRotationRequestDto:**
```java
public class DeviceKeyRotationRequestDto {
    private String devicePublicKeyRotated;
    private String rotationReason;
    private String challengeResponse;
    private String enrollmentProofTokenSigned;
}
```

#### **RotationStatusDto:**
```java
public class RotationStatusDto {
    private Boolean rotationPending;
    private LocalDateTime rotationInitiatedAt;
    private LocalDateTime rotationConfirmedAt;
    private Integer rotationAttemptsCount;
    private Integer rotationFailureCount;
    private RotationStatus status;
}
```

### 12.2 Database Migration Script

```sql
-- Add rotation tracking to ezkey_enrollment
ALTER TABLE ezkey_enrollment ADD COLUMN rotation_pending BOOLEAN DEFAULT FALSE;
ALTER TABLE ezkey_enrollment ADD COLUMN rotation_initiated_at TIMESTAMP;
ALTER TABLE ezkey_enrollment ADD COLUMN rotation_confirmed_at TIMESTAMP;
ALTER TABLE ezkey_enrollment ADD COLUMN rotation_attempts_count INT DEFAULT 0;
ALTER TABLE ezkey_enrollment ADD COLUMN rotation_failure_count INT DEFAULT 0;

-- Create rotation audit table
CREATE TABLE ezkey_enrollment_rotation_audit (
    rotation_audit_id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    enrollment_id INT NOT NULL REFERENCES ezkey_enrollment(enrollment_id),
    rotation_type VARCHAR(20) NOT NULL CHECK (rotation_type IN ('ENROLLMENT_TOKEN', 'INTEGRATION_KEY', 'DEVICE_KEY', 'EMERGENCY_ROTATION')),
    rotation_reason VARCHAR(50) NOT NULL,
    rotation_initiated_at TIMESTAMP NOT NULL,
    rotation_confirmed_at TIMESTAMP,
    rotation_status VARCHAR(20) NOT NULL CHECK (rotation_status IN ('PENDING', 'CONFIRMED', 'FAILED', 'TIMEOUT')),
    old_value_hash TEXT,
    new_value_hash TEXT,
    confirmation_auth_attempt_id INT REFERENCES ezkey_auth_attempt(auth_attempt_id),
    created_by VARCHAR(50)
);

-- Add indexes for performance
CREATE INDEX idx_enrollment_rotation_pending ON ezkey_enrollment(rotation_pending);
CREATE INDEX idx_rotation_audit_enrollment ON ezkey_enrollment_rotation_audit(enrollment_id);
CREATE INDEX idx_rotation_audit_status ON ezkey_enrollment_rotation_audit(rotation_status);
```

---

*Document created: January 21, 2025*  
*Version: 1.0*  
*Status: Complete Security Assessment*  
*Next Review: After Phase 1 Implementation*
