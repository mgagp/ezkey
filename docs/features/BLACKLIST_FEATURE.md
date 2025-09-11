# Enrollment Blacklisting Feature

## 📋 Table of Contents

- [Overview](#overview)
- [Security Problem](#security-problem)
- [Feature Description](#feature-description)
- [Blacklisting Conditions](#blacklisting-conditions)
- [Technical Implementation](#technical-implementation)
- [Database Schema](#database-schema)
- [API Integration](#api-integration)
- [Configuration](#configuration)
- [Monitoring and Alerting](#monitoring-and-alerting)
- [Testing Strategy](#testing-strategy)
- [Deployment Considerations](#deployment-considerations)

---

## 🎯 Overview

The Enrollment Blacklisting feature provides proactive security protection for the Ezkey enrollment process by automatically detecting and blocking suspicious activities. This feature addresses critical security vulnerabilities while maintaining the project's core values of simplicity, transparency, and user control.

### Key Benefits

- **Proactive Security**: Automatically detects and prevents abuse attempts
- **Proportional Response**: Blacklist durations match the severity of the threat
- **Transparent Operations**: Clear logging and audit trails
- **Administrative Control**: Manual override capabilities for legitimate cases
- **Performance Protection**: Prevents resource exhaustion from malicious activities

---

## 🔒 Security Problem

### Current Vulnerabilities

The Ezkey enrollment process, while architecturally sound, faces several security challenges:

#### 1. **Excessive Binding Attempts**
- **Issue**: Attackers can repeatedly call `/bind/{enrollmentId}` to probe for valid enrollments
- **Impact**: Resource exhaustion, potential DoS, information disclosure
- **Current Protection**: Limited to Cloudflare WAF in production

#### 2. **Multiple Verification Attempts**
- **Issue**: Attackers can attempt verification with different public keys
- **Impact**: Potential bypass of enrollment controls, resource waste
- **Current Protection**: Challenge mechanism provides some protection

#### 3. **Automated Attack Patterns**
- **Issue**: Scripted attacks with regular timing patterns
- **Impact**: Scalable abuse, difficult to detect manually
- **Current Protection**: None

#### 4. **Replay Attack Attempts**
- **Issue**: Reuse of proof tokens with different cryptographic keys
- **Impact**: Potential enrollment hijacking
- **Current Protection**: None (proof tokens not invalidated)

#### 5. **Weak Cryptographic Keys**
- **Issue**: Attempts to use weak RSA keys (< 2048 bits)
- **Impact**: Potential cryptographic compromise
- **Current Protection**: Basic validation only

### Risk Assessment

| **Threat** | **Likelihood** | **Impact** | **Current Mitigation** | **Gap** |
|------------|----------------|------------|----------------------|---------|
| Binding Abuse | High | Medium | Cloudflare WAF | Application-level detection |
| Verification Abuse | Medium | High | Challenge mechanism | Pattern detection |
| Automated Attacks | High | Medium | None | Timing analysis |
| Replay Attacks | Medium | High | None | Token uniqueness |
| Weak Keys | Low | High | Basic validation | Strength enforcement |

---

## 🚀 Feature Description

### Core Functionality

The Enrollment Blacklisting feature implements a multi-layered security system that:

1. **Monitors** all enrollment-related activities in real-time
2. **Analyzes** patterns and behaviors for suspicious activities
3. **Automatically** blacklists enrollments that exhibit malicious behavior
4. **Provides** administrative tools for blacklist management
5. **Maintains** comprehensive audit trails for security analysis

### Design Principles

- **Fail-Safe**: When in doubt, err on the side of security
- **Proportional**: Blacklist duration matches threat severity
- **Transparent**: All actions are logged and auditable
- **Reversible**: Administrators can override blacklist decisions
- **Performant**: Minimal impact on legitimate enrollment flows

---

## 🎯 Blacklisting Conditions

### 🔴 Critical Conditions (Immediate Blacklist)

#### 1. Excessive Binding Attempts
```java
// Condition: 5+ binding attempts within 15 minutes
// Duration: 2 hours
// Rationale: Prevents brute force enumeration of enrollments
if (bindingAttempts >= 5 && timeWindow <= 15_MINUTES) {
    blacklistService.blacklistEnrollment(enrollmentId, 
        "excessive_binding_attempts", 
        Duration.ofHours(2));
}
```

#### 2. Multiple Verification Attempts with Different Keys
```java
// Condition: 3+ failed verification attempts with different public keys
// Duration: 24 hours
// Rationale: Indicates attempt to bypass enrollment controls
if (failedVerificationAttempts >= 3 && uniquePublicKeysUsed > 1) {
    blacklistService.blacklistEnrollment(enrollmentId, 
        "multiple_verification_attempts_with_different_keys", 
        Duration.ofHours(24));
}
```

#### 3. Repeated Invalid Signatures
```java
// Condition: 5+ invalid signatures with the same public key
// Duration: 6 hours
// Rationale: Indicates malicious key usage or implementation issues
if (invalidSignatures >= 5 && samePublicKey) {
    blacklistService.blacklistEnrollment(enrollmentId, 
        "repeated_invalid_signatures", 
        Duration.ofHours(6));
}
```

### 🟠 Suspicious Conditions (Temporary Blacklist)

#### 4. Automated Pattern Detection
```java
// Condition: Regular timing patterns (e.g., every 30 seconds exactly)
// Duration: 30 minutes
// Rationale: Indicates scripted/automated attacks
if (isAutomatedPattern(bindingTimestamps)) {
    blacklistService.blacklistEnrollment(enrollmentId, 
        "automated_binding_pattern", 
        Duration.ofMinutes(30));
}
```

#### 5. Proof Token Replay Attempts
```java
// Condition: Same proof token used with different public keys
// Duration: 12 hours
// Rationale: Attempt to replay enrollment tokens
if (proofTokenReuseDetected) {
    blacklistService.blacklistEnrollment(enrollmentId, 
        "proof_token_replay_attempt", 
        Duration.ofHours(12));
}
```

#### 6. Weak Cryptographic Keys
```java
// Condition: 3+ attempts with RSA keys < 2048 bits
// Duration: 4 hours
// Rationale: Attempt to use weak cryptography
if (weakKeyAttempts >= 3) {
    blacklistService.blacklistEnrollment(enrollmentId, 
        "weak_cryptographic_keys", 
        Duration.ofHours(4));
}
```

### 🟡 Surveillance Conditions (Alert)

#### 7. Enrollment Enumeration
```java
// Condition: 10+ attempts on non-existent enrollment IDs
// Duration: 1 hour
// Rationale: Attempt to discover valid enrollment IDs
if (nonExistentEnrollmentAttempts >= 10) {
    blacklistService.blacklistEnrollment(enrollmentId, 
        "enrollment_enumeration_attempt", 
        Duration.ofHours(1));
}
```

#### 8. Incorrect Challenge Responses
```java
// Condition: 3+ incorrect challenge responses
// Duration: 15 minutes
// Rationale: Indicates guessing or automated attempts
if (incorrectChallengeResponses >= 3) {
    blacklistService.blacklistEnrollment(enrollmentId, 
        "incorrect_challenge_responses", 
        Duration.ofMinutes(15));
}
```

### Blacklist Duration Matrix

| **Condition** | **Threshold** | **Duration** | **Severity** | **Rationale** |
|---------------|---------------|--------------|--------------|---------------|
| Excessive Binding | 5 attempts/15min | 2 hours | 🔴 Critical | Prevents enumeration |
| Multiple Verifications | 3 failures/1h | 24 hours | 🔴 Critical | Bypass attempt |
| Invalid Signatures | 5 failures | 6 hours | 🔴 Critical | Malicious key usage |
| Automated Pattern | 3+ regular intervals | 30 minutes | 🟠 Suspicious | Scripted attack |
| Replay Attack | 1 detection | 12 hours | 🟠 Suspicious | Token reuse |
| Weak Keys | 3 attempts | 4 hours | 🟠 Suspicious | Cryptographic weakness |
| Enumeration | 10 attempts | 1 hour | 🟡 Surveillance | Discovery attempt |
| Wrong Challenges | 3 failures | 15 minutes | 🟡 Surveillance | Guessing attempt |

---

## 🛠️ Technical Implementation

### Core Components

#### 1. Enrollment Blacklist Entity
```java
@Entity
@Table(name = "ezkey_enrollment_blacklist")
public class EnrollmentBlacklist {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "enrollment_id", nullable = false)
    private Integer enrollmentId;
    
    @Column(name = "blacklist_reason", nullable = false)
    private String reason;
    
    @Column(name = "blacklisted_at", nullable = false)
    private LocalDateTime blacklistedAt;
    
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
    
    @Column(name = "client_info")
    private String clientInfo; // IP + User-Agent for forensics
    
    @Column(name = "admin_override")
    private Boolean adminOverride = false;
    
    @Column(name = "override_reason")
    private String overrideReason;
    
    @Column(name = "override_by")
    private String overrideBy;
    
    @Column(name = "override_at")
    private LocalDateTime overrideAt;
}
```

#### 2. Blacklist Service
```java
@Service
@Transactional
public class EnrollmentBlacklistService {
    
    private final EnrollmentBlacklistRepository blacklistRepository;
    private final Logger logger = LoggerFactory.getLogger(EnrollmentBlacklistService.class);
    
    /**
     * Check if an enrollment is currently blacklisted
     */
    public boolean isBlacklisted(Integer enrollmentId) {
        return blacklistRepository.existsByEnrollmentIdAndExpiresAtAfter(
            enrollmentId, LocalDateTime.now());
    }
    
    /**
     * Blacklist an enrollment with specified reason and duration
     */
    public void blacklistEnrollment(Integer enrollmentId, String reason, 
                                  Duration duration, String clientInfo) {
        // Check if already blacklisted
        if (isBlacklisted(enrollmentId)) {
            logger.warn("Enrollment {} already blacklisted, extending duration", enrollmentId);
            extendBlacklistDuration(enrollmentId, duration);
            return;
        }
        
        EnrollmentBlacklist blacklist = new EnrollmentBlacklist();
        blacklist.setEnrollmentId(enrollmentId);
        blacklist.setReason(reason);
        blacklist.setBlacklistedAt(LocalDateTime.now());
        blacklist.setExpiresAt(LocalDateTime.now().plus(duration));
        blacklist.setClientInfo(clientInfo);
        
        blacklistRepository.save(blacklist);
        
        logger.warn("Enrollment {} blacklisted for reason: {} until {}", 
            enrollmentId, reason, blacklist.getExpiresAt());
    }
    
    /**
     * Manually override a blacklist (admin function)
     */
    public void overrideBlacklist(Integer enrollmentId, String reason, String adminUser) {
        Optional<EnrollmentBlacklist> blacklistOpt = blacklistRepository
            .findByEnrollmentIdAndExpiresAtAfter(enrollmentId, LocalDateTime.now());
        
        if (blacklistOpt.isPresent()) {
            EnrollmentBlacklist blacklist = blacklistOpt.get();
            blacklist.setAdminOverride(true);
            blacklist.setOverrideReason(reason);
            blacklist.setOverrideBy(adminUser);
            blacklist.setOverrideAt(LocalDateTime.now());
            blacklist.setExpiresAt(LocalDateTime.now()); // Immediate expiration
            
            blacklistRepository.save(blacklist);
            
            logger.info("Admin {} overrode blacklist for enrollment {}: {}", 
                adminUser, enrollmentId, reason);
        }
    }
    
    /**
     * Get blacklist history for an enrollment
     */
    public List<EnrollmentBlacklist> getBlacklistHistory(Integer enrollmentId) {
        return blacklistRepository.findByEnrollmentIdOrderByBlacklistedAtDesc(enrollmentId);
    }
    
    /**
     * Clean up expired blacklist entries
     */
    @Scheduled(fixedRate = 3600000) // Every hour
    public void cleanupExpiredBlacklists() {
        int deleted = blacklistRepository.deleteByExpiresAtBefore(LocalDateTime.now());
        if (deleted > 0) {
            logger.info("Cleaned up {} expired blacklist entries", deleted);
        }
    }
}
```

#### 3. Security Detection Service
```java
@Service
public class EnrollmentSecurityDetector {
    
    private final Map<Integer, List<EnrollmentAttempt>> attempts = new ConcurrentHashMap<>();
    private final EnrollmentBlacklistService blacklistService;
    private final Logger logger = LoggerFactory.getLogger(EnrollmentSecurityDetector.class);
    
    /**
     * Record a binding attempt
     */
    public void recordBindingAttempt(Integer enrollmentId, String clientInfo) {
        EnrollmentAttempt attempt = new EnrollmentAttempt(
            enrollmentId, "BIND", LocalDateTime.now(), clientInfo);
        
        List<EnrollmentAttempt> enrollmentAttempts = attempts.computeIfAbsent(
            enrollmentId, k -> new ArrayList<>());
        enrollmentAttempts.add(attempt);
        
        // Clean old attempts (older than 24 hours)
        enrollmentAttempts.removeIf(attempt -> 
            attempt.getTimestamp().isBefore(LocalDateTime.now().minusHours(24)));
        
        // Check for suspicious patterns
        checkBlacklistConditions(enrollmentId);
    }
    
    /**
     * Record a verification attempt
     */
    public void recordVerificationAttempt(Integer enrollmentId, String devicePublicKey, 
                                        boolean success, String clientInfo) {
        EnrollmentAttempt attempt = new EnrollmentAttempt(
            enrollmentId, "VERIFY", LocalDateTime.now(), clientInfo, devicePublicKey, success);
        
        List<EnrollmentAttempt> enrollmentAttempts = attempts.computeIfAbsent(
            enrollmentId, k -> new ArrayList<>());
        enrollmentAttempts.add(attempt);
        
        // Clean old attempts
        enrollmentAttempts.removeIf(attempt -> 
            attempt.getTimestamp().isBefore(LocalDateTime.now().minusHours(24)));
        
        checkBlacklistConditions(enrollmentId);
    }
    
    /**
     * Check all blacklist conditions for an enrollment
     */
    private void checkBlacklistConditions(Integer enrollmentId) {
        List<EnrollmentAttempt> attempts = this.attempts.get(enrollmentId);
        if (attempts == null) return;
        
        // Check excessive binding attempts
        if (isExcessiveBindingAttempts(attempts)) {
            blacklistService.blacklistEnrollment(enrollmentId, 
                "excessive_binding_attempts", 
                Duration.ofHours(2),
                getLatestClientInfo(attempts));
            return;
        }
        
        // Check multiple verification attempts
        if (isMultipleVerificationAttempts(attempts)) {
            blacklistService.blacklistEnrollment(enrollmentId, 
                "multiple_verification_attempts", 
                Duration.ofHours(24),
                getLatestClientInfo(attempts));
            return;
        }
        
        // Check automated patterns
        if (isAutomatedPattern(attempts)) {
            blacklistService.blacklistEnrollment(enrollmentId, 
                "automated_pattern", 
                Duration.ofMinutes(30),
                getLatestClientInfo(attempts));
            return;
        }
        
        // Check weak key attempts
        if (isWeakKeyAttempts(attempts)) {
            blacklistService.blacklistEnrollment(enrollmentId, 
                "weak_cryptographic_keys", 
                Duration.ofHours(4),
                getLatestClientInfo(attempts));
            return;
        }
    }
    
    /**
     * Detect excessive binding attempts (5+ in 15 minutes)
     */
    private boolean isExcessiveBindingAttempts(List<EnrollmentAttempt> attempts) {
        long bindingAttempts = attempts.stream()
            .filter(a -> "BIND".equals(a.getType()))
            .filter(a -> a.getTimestamp().isAfter(LocalDateTime.now().minusMinutes(15)))
            .count();
        
        return bindingAttempts >= 5;
    }
    
    /**
     * Detect multiple verification attempts with different keys
     */
    private boolean isMultipleVerificationAttempts(List<EnrollmentAttempt> attempts) {
        List<EnrollmentAttempt> failedVerifications = attempts.stream()
            .filter(a -> "VERIFY".equals(a.getType()))
            .filter(a -> !a.isSuccess())
            .filter(a -> a.getTimestamp().isAfter(LocalDateTime.now().minusHours(1)))
            .collect(Collectors.toList());
        
        if (failedVerifications.size() < 3) return false;
        
        long uniqueKeys = failedVerifications.stream()
            .map(EnrollmentAttempt::getDevicePublicKey)
            .filter(Objects::nonNull)
            .distinct()
            .count();
        
        return uniqueKeys > 1;
    }
    
    /**
     * Detect automated patterns (regular timing intervals)
     */
    private boolean isAutomatedPattern(List<EnrollmentAttempt> attempts) {
        List<EnrollmentAttempt> recentAttempts = attempts.stream()
            .filter(a -> a.getTimestamp().isAfter(LocalDateTime.now().minusMinutes(10)))
            .sorted(Comparator.comparing(EnrollmentAttempt::getTimestamp))
            .collect(Collectors.toList());
        
        if (recentAttempts.size() < 3) return false;
        
        // Calculate intervals between attempts
        List<Duration> intervals = new ArrayList<>();
        for (int i = 1; i < recentAttempts.size(); i++) {
            intervals.add(Duration.between(
                recentAttempts.get(i-1).getTimestamp(),
                recentAttempts.get(i).getTimestamp()));
        }
        
        // Check if intervals are too regular (within 5 seconds of each other)
        if (intervals.size() < 2) return false;
        
        Duration firstInterval = intervals.get(0);
        return intervals.stream().allMatch(interval -> 
            Math.abs(interval.toSeconds() - firstInterval.toSeconds()) <= 5);
    }
    
    /**
     * Detect weak key attempts
     */
    private boolean isWeakKeyAttempts(List<EnrollmentAttempt> attempts) {
        long weakKeyAttempts = attempts.stream()
            .filter(a -> "VERIFY".equals(a.getType()))
            .filter(a -> a.getTimestamp().isAfter(LocalDateTime.now().minusHours(1)))
            .filter(a -> a.getDevicePublicKey() != null)
            .filter(a -> isWeakKey(a.getDevicePublicKey()))
            .count();
        
        return weakKeyAttempts >= 3;
    }
    
    /**
     * Check if a public key is weak (< 2048 bits)
     */
    private boolean isWeakKey(String base64PublicKey) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(base64PublicKey);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PublicKey publicKey = kf.generatePublic(spec);
            
            if (publicKey instanceof RSAPublicKey) {
                RSAPublicKey rsaKey = (RSAPublicKey) publicKey;
                return rsaKey.getModulus().bitLength() < 2048;
            }
        } catch (Exception e) {
            logger.warn("Failed to analyze key strength: {}", e.getMessage());
        }
        return false;
    }
    
    private String getLatestClientInfo(List<EnrollmentAttempt> attempts) {
        return attempts.stream()
            .max(Comparator.comparing(EnrollmentAttempt::getTimestamp))
            .map(EnrollmentAttempt::getClientInfo)
            .orElse("unknown");
    }
}
```

#### 4. Enrollment Attempt Tracking
```java
public class EnrollmentAttempt {
    private Integer enrollmentId;
    private String type; // "BIND" or "VERIFY"
    private LocalDateTime timestamp;
    private String clientInfo; // IP + User-Agent
    private String devicePublicKey; // For verification attempts
    private boolean success; // For verification attempts
    
    // Constructors, getters, setters...
}
```

---

## 🗄️ Database Schema

### New Table: ezkey_enrollment_blacklist
```sql
-- Enrollment blacklist table for security protection
CREATE TABLE ezkey_enrollment_blacklist (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    enrollment_id INT NOT NULL REFERENCES ezkey_enrollment(enrollment_id) ON DELETE CASCADE,
    blacklist_reason VARCHAR(100) NOT NULL,
    blacklisted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    client_info VARCHAR(500), -- IP + User-Agent for forensics
    admin_override BOOLEAN DEFAULT FALSE,
    override_reason VARCHAR(255),
    override_by VARCHAR(100),
    override_at TIMESTAMP,
    CONSTRAINT unique_active_blacklist UNIQUE(enrollment_id, expires_at)
);

-- Add table comment
COMMENT ON TABLE ezkey_enrollment_blacklist IS 'Security blacklist for enrollments showing suspicious behavior. Automatically populated by security detection system and can be manually overridden by administrators.';

-- Add column comments
COMMENT ON COLUMN ezkey_enrollment_blacklist.id IS 'Primary key - unique identifier for blacklist entry';
COMMENT ON COLUMN ezkey_enrollment_blacklist.enrollment_id IS 'Foreign key to blacklisted enrollment';
COMMENT ON COLUMN ezkey_enrollment_blacklist.blacklist_reason IS 'Reason for blacklisting (e.g., excessive_binding_attempts, multiple_verification_attempts)';
COMMENT ON COLUMN ezkey_enrollment_blacklist.blacklisted_at IS 'Timestamp when enrollment was blacklisted';
COMMENT ON COLUMN ezkey_enrollment_blacklist.expires_at IS 'Timestamp when blacklist expires (NULL for permanent)';
COMMENT ON COLUMN ezkey_enrollment_blacklist.client_info IS 'Client information (IP + User-Agent) for forensic analysis';
COMMENT ON COLUMN ezkey_enrollment_blacklist.admin_override IS 'Flag indicating if blacklist was manually overridden by admin';
COMMENT ON COLUMN ezkey_enrollment_blacklist.override_reason IS 'Reason for admin override';
COMMENT ON COLUMN ezkey_enrollment_blacklist.override_by IS 'Admin user who performed the override';
COMMENT ON COLUMN ezkey_enrollment_blacklist.override_at IS 'Timestamp of admin override';

-- Indexes for performance
CREATE INDEX idx_enrollment_blacklist_enrollment_id ON ezkey_enrollment_blacklist(enrollment_id);
CREATE INDEX idx_enrollment_blacklist_expires_at ON ezkey_enrollment_blacklist(expires_at);
CREATE INDEX idx_enrollment_blacklist_active ON ezkey_enrollment_blacklist(enrollment_id, expires_at) WHERE expires_at > CURRENT_TIMESTAMP;
```

### Migration Script
```sql
-- Migration: V1__initial_schema.sql (Updated for pre-release)
-- Add enrollment blacklist table to existing initial schema

-- Add enrollment blacklist table for security protection
CREATE TABLE ezkey_enrollment_blacklist (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    enrollment_id INT NOT NULL REFERENCES ezkey_enrollment(enrollment_id) ON DELETE CASCADE,
    blacklist_reason VARCHAR(100) NOT NULL,
    blacklisted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    client_info VARCHAR(500),
    admin_override BOOLEAN DEFAULT FALSE,
    override_reason VARCHAR(255),
    override_by VARCHAR(100),
    override_at TIMESTAMP
);

-- Add indexes
CREATE INDEX idx_enrollment_blacklist_enrollment_id ON ezkey_enrollment_blacklist(enrollment_id);
CREATE INDEX idx_enrollment_blacklist_expires_at ON ezkey_enrollment_blacklist(expires_at);
CREATE INDEX idx_enrollment_blacklist_active ON ezkey_enrollment_blacklist(enrollment_id, expires_at) WHERE expires_at > CURRENT_TIMESTAMP;

-- Add comments
COMMENT ON TABLE ezkey_enrollment_blacklist IS 'Security blacklist for enrollments showing suspicious behavior';
COMMENT ON COLUMN ezkey_enrollment_blacklist.blacklist_reason IS 'Reason for blacklisting (e.g., excessive_binding_attempts)';
COMMENT ON COLUMN ezkey_enrollment_blacklist.client_info IS 'Client information for forensic analysis';
```

---

## 🔌 API Integration

### Controller Updates
```java
@RestController
@RequestMapping("/api/v1/enrollments")
@Tag(name = "Enrollments", description = "Mobile device enrollment operations")
public class EnrollmentController {
    
    private final EnrollmentService enrollmentService;
    private final EnrollmentAuthMapper enrollmentMapper;
    private final EnrollmentSecurityDetector securityDetector;
    private final EnrollmentBlacklistService blacklistService;
    
    /**
     * Bind device to enrollment with security monitoring
     */
    @GetMapping("/bind/{enrollmentId}")
    public ResponseEntity<EnrollmentBindResponseDto> bind(
            @PathVariable("enrollmentId") Integer enrollmentId,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            @RequestHeader(value = "CF-Connecting-IP", required = false) String cfConnectingIp,
            @RequestHeader(value = "X-Forwarded-For", required = false) String xForwardedFor) {
        
        // Build client info for security tracking
        // Prefer CF-Connecting-IP over X-Forwarded-For when using Cloudflare
        String clientIp = cfConnectingIp != null ? cfConnectingIp : xForwardedFor;
        String clientInfo = buildClientInfo(clientIp, userAgent);
        
        // Record the binding attempt
        securityDetector.recordBindingAttempt(enrollmentId, clientInfo);
        
        // Check if enrollment is blacklisted
        if (blacklistService.isBlacklisted(enrollmentId)) {
            logger.warn("Blacklisted enrollment access attempt: {} from {}", enrollmentId, clientInfo);
            return ResponseEntity.status(429).build(); // Too Many Requests
        }
        
        try {
            String language = (acceptLanguage != null && !acceptLanguage.isEmpty())
                ? acceptLanguage.split(",")[0].split("-")[0]
                : "en";
            
            EnrollmentBindRequest request = new EnrollmentBindRequest();
            request.setEnrollmentId(enrollmentId);
            request.setLanguage(language);
            
            EnrollmentBindResponse response = enrollmentService.bind(request);
            return ResponseEntity.ok(enrollmentMapper.toEnrollmentBindResponseDto(response));
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }
    
    /**
     * Verify enrollment with security monitoring
     */
    @PostMapping("/verify")
    public ResponseEntity<EnrollmentVerifyResponseDto> verify(
            @RequestBody EnrollmentVerifyRequestDto req,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            @RequestHeader(value = "CF-Connecting-IP", required = false) String cfConnectingIp,
            @RequestHeader(value = "X-Forwarded-For", required = false) String xForwardedFor) {
        
        // Prefer CF-Connecting-IP over X-Forwarded-For when using Cloudflare
        String clientIp = cfConnectingIp != null ? cfConnectingIp : xForwardedFor;
        String clientInfo = buildClientInfo(clientIp, userAgent);
        
        try {
            EnrollmentVerifyResponse response = enrollmentService.verify(
                enrollmentMapper.toEnrollmentVerifyRequest(req));
            
            // Record successful verification
            securityDetector.recordVerificationAttempt(
                req.getEnrollmentId(), 
                req.getDevicePublicKey(), 
                true, 
                clientInfo);
            
            return ResponseEntity.ok(enrollmentMapper.toEnrollmentVerifyResponseDto(response));
            
        } catch (IllegalArgumentException e) {
            // Record failed verification
            securityDetector.recordVerificationAttempt(
                req.getEnrollmentId(), 
                req.getDevicePublicKey(), 
                false, 
                clientInfo);
            
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException e) {
            // Record failed verification
            securityDetector.recordVerificationAttempt(
                req.getEnrollmentId(), 
                req.getDevicePublicKey(), 
                false, 
                clientInfo);
            
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }
    
    /**
     * Build client information string for security tracking
     * Note: CF-Connecting-IP is preferred over X-Forwarded-For when using Cloudflare
     * as X-Forwarded-For can be spoofed by attackers
     */
    private String buildClientInfo(String clientIp, String userAgent) {
        StringBuilder clientInfo = new StringBuilder();
        if (clientIp != null) {
            clientInfo.append(clientIp);
        } else {
            clientInfo.append("unknown-ip");
        }
        clientInfo.append(":");
        if (userAgent != null) {
            clientInfo.append(userAgent);
        } else {
            clientInfo.append("unknown-ua");
        }
        return clientInfo.toString();
    }
}
```

### Admin API Extensions
```java
@RestController
@RequestMapping("/api/v1/admin/blacklist")
@Tag(name = "Blacklist Management", description = "Administrative blacklist management")
public class BlacklistAdminController {
    
    private final EnrollmentBlacklistService blacklistService;
    
    /**
     * Get blacklist status for an enrollment
     */
    @GetMapping("/enrollment/{enrollmentId}")
    public ResponseEntity<BlacklistStatusDto> getBlacklistStatus(
            @PathVariable("enrollmentId") Integer enrollmentId) {
        
        boolean isBlacklisted = blacklistService.isBlacklisted(enrollmentId);
        List<EnrollmentBlacklist> history = blacklistService.getBlacklistHistory(enrollmentId);
        
        BlacklistStatusDto status = new BlacklistStatusDto();
        status.setEnrollmentId(enrollmentId);
        status.setBlacklisted(isBlacklisted);
        status.setHistory(history);
        
        return ResponseEntity.ok(status);
    }
    
    /**
     * Override a blacklist (admin function)
     */
    @PostMapping("/enrollment/{enrollmentId}/override")
    public ResponseEntity<Void> overrideBlacklist(
            @PathVariable("enrollmentId") Integer enrollmentId,
            @RequestBody BlacklistOverrideRequestDto request,
            @RequestHeader("X-Admin-User") String adminUser) {
        
        blacklistService.overrideBlacklist(enrollmentId, request.getReason(), adminUser);
        return ResponseEntity.ok().build();
    }
    
    /**
     * Get all active blacklists
     */
    @GetMapping("/active")
    public ResponseEntity<List<EnrollmentBlacklist>> getActiveBlacklists() {
        List<EnrollmentBlacklist> activeBlacklists = blacklistService.getActiveBlacklists();
        return ResponseEntity.ok(activeBlacklists);
    }
}
```

---

## ⚙️ Configuration

### Application Properties
```properties
# Enrollment Blacklist Configuration
ezkey.security.blacklist.enabled=true
ezkey.security.blacklist.cleanup-interval=3600000
ezkey.security.blacklist.max-attempts-memory=10000

# Blacklist Thresholds
ezkey.security.blacklist.binding.max-attempts=5
ezkey.security.blacklist.binding.time-window=900000
ezkey.security.blacklist.binding.duration=7200000

ezkey.security.blacklist.verification.max-failures=3
ezkey.security.blacklist.verification.time-window=3600000
ezkey.security.blacklist.verification.duration=86400000

ezkey.security.blacklist.automated.min-intervals=3
ezkey.security.blacklist.automated.max-variance=5000
ezkey.security.blacklist.automated.duration=1800000

ezkey.security.blacklist.weak-keys.max-attempts=3
ezkey.security.blacklist.weak-keys.duration=14400000

# Logging Configuration
ezkey.security.blacklist.log-level=WARN
ezkey.security.blacklist.audit-enabled=true
```

### Configuration Class
```java
@Configuration
@ConfigurationProperties(prefix = "ezkey.security.blacklist")
@Data
public class BlacklistConfiguration {
    
    private boolean enabled = true;
    private long cleanupInterval = 3600000; // 1 hour
    private int maxAttemptsMemory = 10000;
    
    private BindingConfig binding = new BindingConfig();
    private VerificationConfig verification = new VerificationConfig();
    private AutomatedConfig automated = new AutomatedConfig();
    private WeakKeysConfig weakKeys = new WeakKeysConfig();
    
    @Data
    public static class BindingConfig {
        private int maxAttempts = 5;
        private long timeWindow = 900000; // 15 minutes
        private long duration = 7200000; // 2 hours
    }
    
    @Data
    public static class VerificationConfig {
        private int maxFailures = 3;
        private long timeWindow = 3600000; // 1 hour
        private long duration = 86400000; // 24 hours
    }
    
    @Data
    public static class AutomatedConfig {
        private int minIntervals = 3;
        private long maxVariance = 5000; // 5 seconds
        private long duration = 1800000; // 30 minutes
    }
    
    @Data
    public static class WeakKeysConfig {
        private int maxAttempts = 3;
        private long duration = 14400000; // 4 hours
    }
}
```

---

## 📊 Monitoring and Alerting

### Metrics
```java
@Component
public class BlacklistMetrics {
    
    private final MeterRegistry meterRegistry;
    private final Counter blacklistCounter;
    private final Timer blacklistDuration;
    private final Gauge activeBlacklists;
    
    public BlacklistMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.blacklistCounter = Counter.builder("ezkey.blacklist.total")
            .description("Total number of blacklist actions")
            .tag("type", "enrollment")
            .register(meterRegistry);
        
        this.blacklistDuration = Timer.builder("ezkey.blacklist.duration")
            .description("Duration of blacklist actions")
            .register(meterRegistry);
        
        this.activeBlacklists = Gauge.builder("ezkey.blacklist.active")
            .description("Number of active blacklists")
            .register(meterRegistry, this, BlacklistMetrics::getActiveBlacklistCount);
    }
    
    public void recordBlacklist(String reason, Duration duration) {
        blacklistCounter.increment(Tags.of("reason", reason));
        blacklistDuration.record(duration);
    }
    
    private double getActiveBlacklistCount() {
        // Implementation to count active blacklists
        return 0.0; // Placeholder
    }
}
```

### Alerting Rules
```yaml
# Prometheus Alerting Rules
groups:
  - name: ezkey.blacklist
    rules:
      - alert: HighBlacklistRate
        expr: rate(ezkey_blacklist_total[5m]) > 10
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "High blacklist rate detected"
          description: "Blacklist rate is {{ $value }} per second"
      
      - alert: SuspiciousEnrollmentActivity
        expr: ezkey_blacklist_total{reason="excessive_binding_attempts"} > 5
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Suspicious enrollment activity detected"
          description: "Multiple enrollments showing excessive binding attempts"
      
      - alert: AutomatedAttackDetected
        expr: ezkey_blacklist_total{reason="automated_pattern"} > 3
        for: 1m
        labels:
          severity: warning
        annotations:
          summary: "Automated attack pattern detected"
          description: "Multiple enrollments showing automated attack patterns"
```

### Logging Configuration
```xml
<!-- logback-spring.xml -->
<configuration>
    <appender name="BLACKLIST" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/blacklist.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/blacklist.%d{yyyy-MM-dd}.%i.log</fileNamePattern>
            <maxFileSize>100MB</maxFileSize>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    
    <logger name="org.ezkey.security.blacklist" level="WARN" additivity="false">
        <appender-ref ref="BLACKLIST"/>
        <appender-ref ref="CONSOLE"/>
    </logger>
</configuration>
```

---

## 🧪 Testing Strategy

### Unit Tests
```java
@ExtendWith(MockitoExtension.class)
class EnrollmentSecurityDetectorTest {
    
    @Mock
    private EnrollmentBlacklistService blacklistService;
    
    @InjectMocks
    private EnrollmentSecurityDetector securityDetector;
    
    @Test
    @DisplayName("Should blacklist enrollment after excessive binding attempts")
    void testExcessiveBindingAttempts() {
        // Arrange
        Integer enrollmentId = 123;
        String clientInfo = "192.168.1.1:TestAgent";
        
        // Act - Record 5 binding attempts within 15 minutes
        for (int i = 0; i < 5; i++) {
            securityDetector.recordBindingAttempt(enrollmentId, clientInfo);
        }
        
        // Assert
        verify(blacklistService).blacklistEnrollment(
            eq(enrollmentId), 
            eq("excessive_binding_attempts"), 
            eq(Duration.ofHours(2)),
            eq(clientInfo));
    }
    
    @Test
    @DisplayName("Should detect automated patterns")
    void testAutomatedPatternDetection() {
        // Arrange
        Integer enrollmentId = 123;
        String clientInfo = "192.168.1.1:TestAgent";
        
        // Act - Record attempts with regular 30-second intervals
        LocalDateTime baseTime = LocalDateTime.now();
        for (int i = 0; i < 4; i++) {
            LocalDateTime attemptTime = baseTime.plusSeconds(i * 30);
            // Mock the timestamp for testing
            securityDetector.recordBindingAttempt(enrollmentId, clientInfo);
        }
        
        // Assert
        verify(blacklistService).blacklistEnrollment(
            eq(enrollmentId), 
            eq("automated_pattern"), 
            eq(Duration.ofMinutes(30)),
            eq(clientInfo));
    }
    
    @Test
    @DisplayName("Should detect multiple verification attempts with different keys")
    void testMultipleVerificationAttempts() {
        // Arrange
        Integer enrollmentId = 123;
        String clientInfo = "192.168.1.1:TestAgent";
        String key1 = "key1";
        String key2 = "key2";
        
        // Act - Record failed verification attempts with different keys
        securityDetector.recordVerificationAttempt(enrollmentId, key1, false, clientInfo);
        securityDetector.recordVerificationAttempt(enrollmentId, key2, false, clientInfo);
        securityDetector.recordVerificationAttempt(enrollmentId, key1, false, clientInfo);
        
        // Assert
        verify(blacklistService).blacklistEnrollment(
            eq(enrollmentId), 
            eq("multiple_verification_attempts"), 
            eq(Duration.ofHours(24)),
            eq(clientInfo));
    }
}
```

### Integration Tests
```java
@SpringBootTest
@ActiveProfiles("test")
class BlacklistIntegrationTest {
    
    @Autowired
    private EnrollmentBlacklistService blacklistService;
    
    @Autowired
    private EnrollmentSecurityDetector securityDetector;
    
    @Test
    @DisplayName("Should prevent blacklisted enrollment from binding")
    void testBlacklistedEnrollmentBlocking() {
        // Arrange
        Integer enrollmentId = 123;
        String clientInfo = "192.168.1.1:TestAgent";
        
        // Blacklist the enrollment
        blacklistService.blacklistEnrollment(enrollmentId, "test_reason", 
            Duration.ofHours(1), clientInfo);
        
        // Act & Assert
        assertTrue(blacklistService.isBlacklisted(enrollmentId));
    }
    
    @Test
    @DisplayName("Should allow admin override of blacklist")
    void testAdminOverride() {
        // Arrange
        Integer enrollmentId = 123;
        String clientInfo = "192.168.1.1:TestAgent";
        String adminUser = "admin@example.com";
        
        // Blacklist the enrollment
        blacklistService.blacklistEnrollment(enrollmentId, "test_reason", 
            Duration.ofHours(1), clientInfo);
        
        // Act - Admin override
        blacklistService.overrideBlacklist(enrollmentId, "Legitimate user", adminUser);
        
        // Assert
        assertFalse(blacklistService.isBlacklisted(enrollmentId));
    }
}
```

### Performance Tests
```java
@Test
@DisplayName("Should handle high volume of attempts efficiently")
void testHighVolumePerformance() {
    // Arrange
    int numberOfEnrollments = 1000;
    int attemptsPerEnrollment = 10;
    
    long startTime = System.currentTimeMillis();
    
    // Act - Simulate high volume
    for (int enrollmentId = 1; enrollmentId <= numberOfEnrollments; enrollmentId++) {
        for (int attempt = 0; attempt < attemptsPerEnrollment; attempt++) {
            securityDetector.recordBindingAttempt(enrollmentId, "test-client");
        }
    }
    
    long endTime = System.currentTimeMillis();
    long duration = endTime - startTime;
    
    // Assert - Should complete within reasonable time
    assertTrue(duration < 5000, "High volume processing took too long: " + duration + "ms");
}
```

---

## 🚀 Deployment Considerations

### Database Migration
```bash
# For pre-release: Update existing V1 migration
# Edit ezkey-core/src/main/resources/db/migration/V1__initial_schema.sql
# Add the blacklist table creation to the existing migration

# Run database migration (will update existing schema)
./scripts/ezkey-flyway.sh --migrate

# Verify migration
./scripts/ezkey-flyway.sh --info
```

**Important Note**: Since this is a pre-release project, the blacklist table should be added to the existing `V1__initial_schema.sql` migration rather than creating a new `V2` migration. This ensures that new deployments will include the blacklist functionality from the start.

### Configuration Updates
```bash
# Update application properties
cp config/application-prod.properties config/application-prod.properties.backup
echo "ezkey.security.blacklist.enabled=true" >> config/application-prod.properties
echo "ezkey.security.blacklist.cleanup-interval=3600000" >> config/application-prod.properties
```

### Monitoring Setup
```bash
# Deploy Prometheus alerts
kubectl apply -f monitoring/blacklist-alerts.yaml

# Deploy Grafana dashboard
kubectl apply -f monitoring/blacklist-dashboard.yaml
```

### Rollback Plan
```bash
# Disable blacklist feature
echo "ezkey.security.blacklist.enabled=false" >> config/application-prod.properties

# Restart application
kubectl rollout restart deployment/ezkey-auth-api
kubectl rollout restart deployment/ezkey-admin-api
```

---

## 📈 Success Metrics

### Security Metrics
- **Reduction in suspicious activities**: 90% decrease in excessive binding attempts
- **False positive rate**: < 1% of legitimate enrollments blacklisted
- **Response time**: < 100ms additional latency for enrollment operations
- **Detection accuracy**: 95% accuracy in identifying automated attacks

### Operational Metrics
- **Blacklist duration**: Average 2-4 hours for suspicious activities
- **Admin overrides**: < 5% of blacklists require manual override
- **System performance**: < 5% impact on enrollment throughput
- **Storage usage**: < 1GB additional storage for blacklist data

### Business Metrics
- **Enrollment success rate**: Maintained at > 99%
- **User experience**: No impact on legitimate enrollment flows
- **Security posture**: Improved protection against automated attacks
- **Compliance**: Enhanced audit trail for security events

---

## 🔄 Future Enhancements

### Phase 2 Features
- **Machine Learning**: AI-based pattern detection for advanced threats
- **Geolocation**: IP-based geographic analysis for suspicious activities
- **Device Fingerprinting**: Browser/device signature analysis
- **Risk Scoring**: Dynamic risk assessment based on multiple factors

### Phase 3 Features
- **Integration**: SIEM integration for enterprise security teams
- **API**: REST API for third-party security tools
- **Analytics**: Advanced reporting and trend analysis
- **Automation**: Self-healing blacklist management

---

## 📚 Related Documentation

- [Architecture Documentation](../ARCHITECTURE.md)
- [Security Implementation](../CRYPTO.md)
- [API Documentation](../ENDPOINT.md)
- [Development Guide](../DEVELOPMENT.md)
- [Monitoring Setup](../monitoring/README.md)

---

*This feature specification provides comprehensive security protection for the Ezkey enrollment process while maintaining the project's core values of simplicity, transparency, and user control. The implementation is designed to be robust, scalable, and maintainable.*
