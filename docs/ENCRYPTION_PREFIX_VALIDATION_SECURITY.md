# Validation and Security Considerations for ENC:keyID: Format

## Overview

This document elaborates on the validation and security aspects that should be considered when implementing the `ENC:keyID:` format for encrypted database fields. Each aspect addresses specific security concerns and attack vectors.

---

## 1. Validation Stricte du Format keyID (Strict Key ID Format Validation)

### What It Means

The key ID extracted from the prefix must be validated to ensure it:
- Is numeric (contains only digits)
- Falls within a valid range (Tink key IDs are typically unsigned 64-bit integers: 0 to 18,446,744,073,709,551,615)
- Matches an actual key ID in the current keyset

### Why It Matters

**Attack Scenario 1: SQL Injection via Key ID**
```sql
-- Malicious input could attempt SQL injection
-- If key ID is used directly in SQL queries without validation
SELECT * FROM ezkey_enrollment 
WHERE integration_private_key LIKE 'ENC:123 OR 1=1--:%'
```

**Attack Scenario 2: Format Confusion**
```
-- Attacker could inject invalid characters
ENC:abc123:base64data  -- Non-numeric key ID
ENC:-1:base64data      -- Negative key ID
ENC:999999999999999999999999:base64data  -- Overflow
```

**Attack Scenario 3: Key ID Enumeration**
```
-- Attacker could probe for valid key IDs
ENC:0:base64data
ENC:1:base64data
ENC:2:base64data
...
```

### Implementation Requirements

```java
// Validation logic needed:
1. Extract key ID from prefix: "ENC:1234567:"
2. Validate it's numeric: only digits 0-9
3. Validate range: 0 <= keyId <= Long.MAX_VALUE (or Tink's max)
4. Optionally verify key ID exists in keyset (for new format only)
```

### Security Benefits

- **Prevents injection attacks**: Invalid key IDs are rejected before processing
- **Early detection**: Malformed data is caught at parsing stage
- **Audit trail**: Invalid attempts can be logged for security monitoring
- **Data integrity**: Ensures only valid Tink key IDs are stored

### Performance Impact

- **Minimal**: Simple string parsing and numeric validation
- **Early failure**: Invalid data rejected before expensive decryption attempts

---

## 2. Protection Against Malicious Prefix Manipulation

### What It Means

Protect against attackers who might:
- Modify the key ID in stored encrypted values
- Inject malicious characters into the prefix
- Create malformed prefixes to cause parsing errors or bypass validation

### Why It Matters

**Attack Scenario 1: Key ID Tampering**
```
-- Original encrypted value:
ENC:1234567:AbCdEf123...

-- Attacker modifies key ID in database:
ENC:9999999:AbCdEf123...  -- Changed key ID

-- Impact: 
- Future queries for key rotation might miss this record
- Re-encryption batch jobs might skip it
- Data becomes "orphaned" from key rotation process
```

**Attack Scenario 2: Prefix Injection**
```
-- Attacker injects malicious prefix:
ENC:1234567:ENC:1234567:AbCdEf123...  -- Double prefix

-- Impact:
- Parsing logic might fail or behave unexpectedly
- Could bypass validation checks
- Might cause application errors
```

**Attack Scenario 3: Format Confusion Attack**
```
-- Attacker creates ambiguous format:
ENC:1234567:ENC:base64data  -- Nested prefix

-- Impact:
- Parsing might extract wrong key ID
- Decryption might fail or use wrong key
```

### Implementation Requirements

```java
// Protection mechanisms needed:
1. Strict format validation: ENC:\d+:[Base64]+
2. Reject any value that doesn't match exact pattern
3. Log suspicious patterns for security monitoring
4. Never trust user-provided key IDs (always get from keyset)
5. Validate Base64 portion is valid Base64 before attempting decryption
```

### Security Benefits

- **Data integrity**: Ensures stored values maintain correct format
- **Prevents bypass**: Malformed data cannot bypass security checks
- **Audit capability**: Suspicious patterns can be detected and logged
- **Fail-safe**: Invalid data is rejected rather than causing undefined behavior

### Performance Impact

- **Low**: Pattern matching and validation before processing
- **Prevents expensive operations**: Invalid data rejected before decryption attempts

---

## 3. Validation de l'Intégrité du Format Complet (Complete Format Integrity Validation)

### What It Means

Before attempting decryption, validate that:
- The entire format is structurally correct: `ENC:keyID:base64data`
- The key ID portion is valid (as per #1)
- The Base64 portion is valid Base64 encoding
- The Base64 portion has minimum expected length (Tink ciphertext has minimum size)
- The format matches exactly one of the supported patterns (legacy `ENC:` or new `ENC:keyID:`)

### Why It Matters

**Attack Scenario 1: Truncated Data**
```
-- Attacker truncates encrypted value:
ENC:1234567:AbC  -- Incomplete Base64

-- Impact:
- Base64 decoding fails
- Decryption attempt might throw exception
- Could cause application errors or information leakage
```

**Attack Scenario 2: Invalid Base64**
```
-- Attacker injects invalid Base64:
ENC:1234567:AbCdEf!@#$%  -- Invalid Base64 characters

-- Impact:
- Base64 decoder throws exception
- Error messages might leak information
- Application might crash or behave unexpectedly
```

**Attack Scenario 3: Format Ambiguity**
```
-- Ambiguous format (could be legacy or new):
ENC:1234567  -- Missing colon separator

-- Impact:
- Parser might misinterpret format
- Wrong decryption path might be taken
- Data might be treated as plaintext incorrectly
```

**Attack Scenario 4: Empty Components**
```
-- Empty key ID or Base64:
ENC::AbCdEf123  -- Empty key ID
ENC:1234567:    -- Empty Base64

-- Impact:
- Parsing might succeed but with invalid data
- Decryption will fail but error handling might be inconsistent
```

### Implementation Requirements

```java
// Complete validation needed:
1. Pattern matching: ^ENC:(\d+):([A-Za-z0-9+/=]+)$  (new format)
2. Pattern matching: ^ENC:([A-Za-z0-9+/=]+)$       (legacy format)
3. Validate Base64 is valid Base64 encoding
4. Validate Base64 length >= minimum ciphertext size (Tink has minimum)
5. Validate key ID is within valid range (for new format)
6. Reject any format that doesn't match exactly
```

### Security Benefits

- **Early validation**: Invalid data caught before expensive operations
- **Consistent behavior**: All code paths handle invalid data the same way
- **Error prevention**: Prevents exceptions from reaching application code
- **Audit capability**: Invalid attempts can be logged with full context

### Performance Impact

- **Moderate**: Pattern matching and Base64 validation
- **Worth it**: Prevents expensive decryption attempts on invalid data
- **Early exit**: Invalid data rejected quickly

---

## Comparison Table

| Aspect | Security Risk Level | Performance Impact | Implementation Complexity | Priority |
|--------|-------------------|-------------------|-------------------------|----------|
| **1. Strict Key ID Validation** | Medium | Low | Low | High |
| **2. Prefix Manipulation Protection** | High | Low | Medium | Critical |
| **3. Complete Format Integrity** | High | Moderate | Medium | Critical |

---

## Recommended Implementation Strategy

### Phase 1: Critical (Must Have)
- **Complete Format Integrity Validation** (#3)
  - Ensures all encrypted values are in valid format
  - Prevents application errors from malformed data
  - Foundation for all other validations

### Phase 2: High Priority
- **Prefix Manipulation Protection** (#2)
  - Protects against data tampering
  - Ensures key rotation queries work correctly
  - Maintains data integrity

### Phase 3: Important
- **Strict Key ID Validation** (#1)
  - Prevents SQL injection (if key ID used in queries)
  - Ensures only valid Tink key IDs are stored
  - Enables proper key rotation tracking

---

## Example Implementation Pattern

```java
// Pseudo-code for complete validation
public class EncryptedValueValidator {
    
    // Pattern for new format: ENC:keyID:base64
    private static final Pattern NEW_FORMAT = 
        Pattern.compile("^ENC:(\\d+):([A-Za-z0-9+/=]+)$");
    
    // Pattern for legacy format: ENC:base64
    private static final Pattern LEGACY_FORMAT = 
        Pattern.compile("^ENC:([A-Za-z0-9+/=]+)$");
    
    // Minimum Tink ciphertext size (approximate, adjust based on algorithm)
    private static final int MIN_CIPHERTEXT_BASE64_LENGTH = 20;
    
    public ValidationResult validate(String encryptedValue) {
        // 1. Check for null/empty
        if (encryptedValue == null || encryptedValue.isEmpty()) {
            return ValidationResult.invalid("Empty value");
        }
        
        // 2. Try new format first
        Matcher newMatcher = NEW_FORMAT.matcher(encryptedValue);
        if (newMatcher.matches()) {
            String keyIdStr = newMatcher.group(1);
            String base64 = newMatcher.group(2);
            
            // Validate key ID
            long keyId = validateKeyId(keyIdStr);
            if (keyId < 0) {
                return ValidationResult.invalid("Invalid key ID: " + keyIdStr);
            }
            
            // Validate Base64
            if (!isValidBase64(base64)) {
                return ValidationResult.invalid("Invalid Base64 encoding");
            }
            
            // Validate minimum length
            if (base64.length() < MIN_CIPHERTEXT_BASE64_LENGTH) {
                return ValidationResult.invalid("Ciphertext too short");
            }
            
            return ValidationResult.valid(keyId, base64, Format.NEW);
        }
        
        // 3. Try legacy format
        Matcher legacyMatcher = LEGACY_FORMAT.matcher(encryptedValue);
        if (legacyMatcher.matches()) {
            String base64 = legacyMatcher.group(1);
            
            // Validate Base64
            if (!isValidBase64(base64)) {
                return ValidationResult.invalid("Invalid Base64 encoding");
            }
            
            // Validate minimum length
            if (base64.length() < MIN_CIPHERTEXT_BASE64_LENGTH) {
                return ValidationResult.invalid("Ciphertext too short");
            }
            
            return ValidationResult.valid(null, base64, Format.LEGACY);
        }
        
        // 4. Not encrypted format
        return ValidationResult.notEncrypted();
    }
    
    private long validateKeyId(String keyIdStr) {
        try {
            long keyId = Long.parseLong(keyIdStr);
            // Tink key IDs are unsigned, but Java long is signed
            // Valid range: 0 to Long.MAX_VALUE
            if (keyId < 0 || keyId > Long.MAX_VALUE) {
                return -1;
            }
            return keyId;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
    
    private boolean isValidBase64(String base64) {
        try {
            Base64.getDecoder().decode(base64);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
```

---

## Security Best Practices Summary

1. **Never trust user input**: Always validate format before processing
2. **Fail securely**: Reject invalid data rather than attempting to process it
3. **Log suspicious patterns**: Invalid attempts should be logged for security monitoring
4. **Validate early**: Check format before expensive operations (decryption)
5. **Be strict**: Reject ambiguous or malformed data
6. **Maintain backward compatibility**: Support legacy `ENC:` format but validate it strictly

---

## Questions to Consider

1. **Should we validate that the key ID exists in the current keyset?**
   - **Pro**: Ensures data integrity
   - **Con**: Requires keyset access during validation, might be expensive
   - **Recommendation**: Validate format only; key existence checked during decryption

2. **How should we handle validation failures?**
   - **Option A**: Throw exception (fail-fast)
   - **Option B**: Log warning and return original value (backward compatibility)
   - **Recommendation**: Fail-fast for new format, warn for legacy format

3. **Should validation be synchronous or can it be deferred?**
   - **Recommendation**: Synchronous validation before decryption attempts
   - **Rationale**: Prevents expensive operations on invalid data

