# Ezkey Core Configuration

This document describes the configuration options available for the ezkey-core module.

## Overview

The ezkey-core module provides centralized configuration through `EzkeyCoreProperties` class, which allows applications using ezkey-core to configure cryptographic and authentication parameters.

## Configuration Properties

### Prefix: `ezkey.core`

All configuration properties are prefixed with `ezkey.core`.

### Crypto Configuration

#### `ezkey.core.crypto.rsa-key-size`
- **Type:** `int`
- **Default:** `2048`
- **Minimum:** `2048`
- **Description:** RSA key size in bits for key pair generation. Must be at least 2048 bits for security compliance.

#### `ezkey.core.crypto.rsa-algorithm`
- **Type:** `String`
- **Default:** `"RSA"`
- **Description:** RSA algorithm name for key generation and operations.

#### `ezkey.core.crypto.signature-algorithm`
- **Type:** `String`
- **Default:** `"SHA256withRSA"`
- **Description:** Digital signature algorithm for signing and verification.

#### `ezkey.core.crypto.minimum-key-size`
- **Type:** `int`
- **Default:** `2048`
- **Minimum:** `2048`
- **Description:** Minimum RSA key size for validation. Keys smaller than this will be rejected.

### Authentication Attempt Configuration

#### `ezkey.core.auth-attempt.challenge-digits`
- **Type:** `int`
- **Default:** `2`
- **Range:** `1-6`
- **Description:** Number of digits for challenge codes. Range is 1-6 digits.

#### `ezkey.core.auth-attempt.ttl-seconds`
- **Type:** `int`
- **Default:** `120`
- **Range:** `30-600`
- **Description:** Time-to-live for authentication attempts in seconds. Range is 30 seconds to 10 minutes.

## Usage Examples

### application.properties

```properties
# Cryptographic configuration
ezkey.core.crypto.rsa-key-size=2048
ezkey.core.crypto.rsa-algorithm=RSA
ezkey.core.crypto.signature-algorithm=SHA256withRSA
ezkey.core.crypto.minimum-key-size=2048

# Authentication attempt configuration
ezkey.core.auth-attempt.challenge-digits=2
ezkey.core.auth-attempt.ttl-seconds=120
```

### application.yml

```yaml
ezkey:
  core:
    crypto:
      rsa-key-size: 2048
      rsa-algorithm: RSA
      signature-algorithm: SHA256withRSA
      minimum-key-size: 2048
    auth-attempt:
      challenge-digits: 2
      ttl-seconds: 120
```

## Security Considerations

1. **RSA Key Size:** Never use RSA keys smaller than 2048 bits in production environments.
2. **Challenge Digits:** More digits provide better security but may impact user experience.
3. **TTL Configuration:** Shorter TTL values improve security but may cause timeouts for slow users.

## Migration from @Value

If you were previously using `@Value` annotations for ezkey-core configuration, you can now use the centralized properties:

**Before:**
```java
@Value("${ezkey.auth-attempt.challenge-digits:2}")
private int challengeDigits;
```

**After:**
```java
private final EzkeyCoreProperties ezkeyCoreProperties;

// Usage:
int challengeDigits = ezkeyCoreProperties.getAuthAttempt().getChallengeDigits();
```

## Validation

All configuration properties include validation annotations:
- `@Min` and `@Max` for numeric ranges
- `@NotBlank` for string values
- Custom validation messages for better error reporting

Invalid configuration will cause application startup to fail with descriptive error messages.
