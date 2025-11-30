# ðŸ” Cryptographic Guide - Ezkey

## ðŸ“‹ **Overview**

This document defines the strict cryptographic requirements for Ezkey implementation. The system uses Ed25519 for all digital signatures, providing production-grade security with compact keys and signatures.

## ðŸŽ¯ **Objective**

Ensure cryptographic compatibility between all implementations (Java backend, mobile apps) by defining precise and unambiguous specifications for Ed25519 signatures.

## ðŸ”§ **Cryptographic Specifications**

### **1. Ed25519 Key Generation**

#### **Algorithm**
- **Type**: Ed25519 (pure Ed25519, not EdDSA)
- **Private Key**: 32 bytes (256 bits) seed
- **Public Key**: 32 bytes (256 bits)
- **Signature**: 64 bytes (512 bits)

#### **Key Formats**

##### **Private Key (32 bytes raw, Base64 encoded)**
```java
// Java format using BouncyCastle
Ed25519KeyPairGenerator keyGen = new Ed25519KeyPairGenerator();
keyGen.init(new Ed25519KeyGenerationParameters(secureRandom));
AsymmetricCipherKeyPair keyPair = keyGen.generateKeyPair();
Ed25519PrivateKeyParameters privateKey = 
    (Ed25519PrivateKeyParameters) keyPair.getPrivate();
String privateKeyBase64 = Base64.getEncoder().encodeToString(
    privateKey.getEncoded()
);
```

##### **Public Key (32 bytes raw, Base64 encoded)**
```java
// Java format using BouncyCastle
Ed25519PublicKeyParameters publicKey = 
    (Ed25519PublicKeyParameters) keyPair.getPublic();
String publicKeyBase64 = Base64.getEncoder().encodeToString(
    publicKey.getEncoded()
);
```

**Note**: Ed25519 keys are stored as raw 32-byte values, Base64 encoded for transmission. The backend also supports PKCS#8/X.509 encoded keys for compatibility.

### **2. Ed25519 Signature**

#### **Algorithm**
- **Name**: Ed25519
- **Type**: Pure Ed25519 (not EdDSA)
- **Signature Size**: 64 bytes (512 bits)

#### **Signature Process**

1. **Data Encoding**
   ```java
   byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
   ```

2. **Signing**
   ```java
   Ed25519PrivateKeyParameters privateKey = 
       new Ed25519PrivateKeyParameters(keyBytes, 0);
   Ed25519Signer signer = new Ed25519Signer();
   signer.init(true, privateKey);
   signer.update(dataBytes, 0, dataBytes.length);
   byte[] signature = signer.generateSignature();
   ```

3. **Base64 Encoding**
   ```java
   String signatureBase64 = Base64.getEncoder().encodeToString(signature);
   ```

#### **Verification Process**

1. **Signature Decoding**
   ```java
   byte[] signatureBytes = Base64.getDecoder().decode(signatureBase64);
   ```

2. **Verification**
   ```java
   Ed25519PublicKeyParameters publicKey = 
       new Ed25519PublicKeyParameters(keyBytes, 0);
   Ed25519Signer verifier = new Ed25519Signer();
   verifier.init(false, publicKey); // false = verification mode
   verifier.update(data.getBytes(StandardCharsets.UTF_8), 0, dataBytes.length);
   boolean valid = verifier.verifySignature(signatureBytes);
   ```

### **3. Mutual Cryptographic Authentication**

Ezkey implements mutual cryptographic authentication where both the backend and mobile app cryptographically verify each other's authenticity:

#### **Backend â†’ Mobile Authentication**
- Backend signs `authAttemptProofToken` with `integration_private_key` (Ed25519)
- Mobile verifies signature using stored `integration_public_key` (Ed25519)
- This ensures the mobile app can trust that authentication requests come from the legitimate backend

#### **Mobile â†’ Backend Authentication**
- Mobile signs responses with `device_private_key` (Ed25519)
- Backend verifies signature using stored `device_public_key` (Ed25519)
- This ensures the backend can trust that responses come from the legitimate device

### **4. Key Management**

#### **Backend Integration Keys**
- Generated per enrollment during enrollment creation
- Ed25519 key pair (32 bytes private key seed, 32 bytes public key)
- Direct generation using BouncyCastle (no derivation needed)
- Stored in database: `integration_private_key` (encrypted at rest), `integration_public_key` (plaintext)

#### **Mobile Device Keys (Phase 2)**
- Root key: 256-bit symmetric key stored in hardware-backed storage (Secure Enclave/StrongBox)
- HKDF-SHA-256 derivation: `seed = HKDF(root_key, info = "enrollment:<enrollment_id>:signing")`
- Ed25519 key pair: Generated from 32-byte seed
- Only `device_public_key` sent to backend during enrollment verify

**Note**: Phase 1 (backend + demo device) uses direct Ed25519 key generation without HKDF. HKDF derivation is mobile-specific and will be implemented in Phase 2.

### **5. Proof Tokens**

#### **Enrollment Proof Token**
- **Format**: Base64 URL-safe without padding
- **Structure**: `randomBytes.timestamp.salt`
- **Size**: 32 bytes random + timestamp + 16 bytes salt

#### **Device Proof Token**
- **Format**: Base64 URL-safe
- **Size**: 32 bytes random
- **Usage**: Unique authentication

## ðŸ“ **Implementation Guide by Language**

### **Java (Reference - Backend)**

```java
// SignatureService.java - Reference implementation
public String generateSignature(String data, String base64PrivateKey) {
    byte[] keyBytes = Base64.getDecoder().decode(base64PrivateKey);
    // Handle both raw 32-byte keys and PKCS#8 encoded keys
    Ed25519PrivateKeyParameters privateKeyParams;
    if (keyBytes.length == 32) {
        privateKeyParams = new Ed25519PrivateKeyParameters(keyBytes, 0);
    } else {
        // Extract from PKCS#8 format
        PrivateKeyInfo privateKeyInfo = PrivateKeyInfo.getInstance(keyBytes);
        byte[] rawKey = privateKeyInfo.getPrivateKey().getOctets();
        privateKeyParams = new Ed25519PrivateKeyParameters(rawKey, 0);
    }
    Ed25519Signer signer = new Ed25519Signer();
    signer.init(true, privateKeyParams);
    byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
    signer.update(dataBytes, 0, dataBytes.length);
    byte[] signature = signer.generateSignature();
    return Base64.getEncoder().encodeToString(signature);
}
```

### **Mobile App (Phase 2)**

#### **Android Native Module (Kotlin)**
```kotlin
// Root key generation and HKDF derivation
fun generateRootKey(): ByteArray {
    // Generate 256-bit symmetric key
    // Store in Android Keystore (StrongBox if available)
}

fun deriveEd25519KeyPair(enrollmentId: String): Ed25519KeyPair {
    val rootKey = loadRootKey()
    val seed = hkdfSha256(rootKey, "enrollment:$enrollmentId:signing")
    // Generate Ed25519 key pair from seed
    // Return key pair
}

fun signData(data: String, privateKeySeed: ByteArray): String {
    // Sign using Ed25519 with private key seed
    // Return Base64-encoded signature
}
```

#### **iOS Native Module (Swift)**
```swift
// Root key generation and HKDF derivation
func generateRootKey() -> Data {
    // Generate 256-bit symmetric key
    // Store in iOS Keychain (Secure Enclave if available)
}

func deriveEd25519KeyPair(enrollmentId: String) -> Ed25519KeyPair {
    let rootKey = loadRootKey()
    let seed = hkdfSha256(rootKey, "enrollment:\(enrollmentId):signing")
    // Generate Ed25519 key pair from seed using CryptoKit
    // Return key pair
}

func signData(data: String, privateKeySeed: Data) -> String {
    // Sign using Ed25519 with private key seed
    // Return Base64-encoded signature
}
```

## ðŸ§ª **Compatibility Tests**

### **Cross-Validation Test**
```bash
# Generate keys and signatures in each language
# Verify that Java backend can validate signatures from mobile apps
# Verify that mobile apps can validate backend signatures
```

### **Test Vectors**
```json
{
  "data": "test-enrollment-proof-token-123",
  "privateKey": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
  "publicKey": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
  "expectedSignature": "base64-encoded-64-byte-signature"
}
```

## âš ï¸ **Pitfalls to Avoid**

### **1. Key Format**
- âŒ Never use incorrect key sizes (must be 32 bytes for Ed25519)
- âŒ Never confuse Ed25519 with EdDSA
- âœ… Always use 32-byte raw keys or proper PKCS#8/X.509 encoding

### **2. Signature Algorithm**
- âŒ Never use EdDSA instead of pure Ed25519
- âŒ Never use incorrect signature size (must be 64 bytes)
- âœ… Always use Ed25519 with 64-byte signatures

### **3. Key Derivation (Mobile Only)**
- âŒ Never derive keys incorrectly (must use HKDF-SHA-256)
- âŒ Never reuse root key directly as signing key
- âœ… Always derive Ed25519 keys from root key using HKDF

### **4. Encoding**
- âŒ Never ignore UTF-8 encoding for data
- âŒ Never use incorrect Base64 encoding
- âœ… Always use StandardCharsets.UTF_8 for data
- âœ… Always use standard Base64 encoding for keys and signatures

## ðŸ” **Validation and Debugging**

### **Mobile Diagnostics (React Native)**

1. **Open the Diagnostics screen**  
   - The home header has a `Diagnostics` button.  
   - The screen lists every stored enrollment with its secure alias.

2. **Run a self-test**  
   - Tap `Run self-test`; the app generates a payload `ezkey-mobile-diagnostic:<timestamp>` and signs it with the device key.  
   - The UI shows:
     - Alias used for signing  
     - Current public key (Base64 encoded, 32 bytes raw)  
     - Signature (Base64 encoded, 64 bytes raw)  
     - Any error (e.g., "No secure alias stored")
   - You can copy these values to reproduce the verification with `SignatureService`.

3. **Interpreting results**
   - **Success** â†’ Keys exist and signatures match the backend expectations.  
   - **Error: No secure alias stored** â†’ The enrollment was wiped (clear app data, new device, recovery). Re-enroll the device.  
   - **Other errors** â†’ The native bridge failed (e.g., Secure Enclave unavailable). Re-run on a device that supports key generation or fall back to mock provider.

4. **Resetting the environment**
   - **Android**: Settings â†’ Apps â†’ Ezkey Mobile â†’ Storage â†’ "Clear storage".  
   - **iOS**: Delete the app.  
   - Reinstall/rebind the device afterwards to restore keys.

### **Required Debug Logs**
```java
logger.debug("Data to sign: {}", data);
logger.debug("Data length: {}", data.length());
logger.debug("Private key length: {}", privateKeyBase64.length());
logger.debug("Signature length: {}", signatureBase64.length());
logger.debug("Signature: {}", signatureBase64);
```

### **Validation Tests**
1. **Generation Test**: Verify keys are 32 bytes (Ed25519)
2. **Signature Test**: Verify signature is 64 bytes (Ed25519)
3. **Validation Test**: Verify backend can validate signature
4. **Cross Test**: Verify compatibility between backend and mobile

## ðŸ“š **Reference Resources**

### **Standards**
- [RFC 8032 - Ed25519](https://tools.ietf.org/html/rfc8032)
- [RFC 5869 - HKDF](https://tools.ietf.org/html/rfc5869)

### **Reference Implementations**
- [Java SignatureService.java](../ezkey-core/src/main/java/org/ezkey/signature/SignatureService.java)
- [Java SignatureServiceTest.java](../ezkey-core/src/test/java/org/ezkey/signature/SignatureServiceTest.java)

### **Testing Tools**
- [BouncyCastle](https://www.bouncycastle.org/) for Java Ed25519 support
- [Base64 Decoder](https://www.base64decode.org/) for validation

## ðŸŽ¯ **Implementation Checklist**

### **Before Starting**
- [ ] Understand Ed25519 key format (32 bytes raw)
- [ ] Understand Ed25519 signature format (64 bytes)
- [ ] Understand HKDF-SHA-256 derivation (mobile only)
- [ ] Have access to testing tools

### **During Implementation**
- [ ] Generate real Ed25519 keys (32 bytes)
- [ ] Use correct Ed25519 signature algorithm
- [ ] Implement 64-byte signatures correctly
- [ ] Use UTF-8 for data encoding
- [ ] Use Base64 for key/signature encoding
- [ ] Implement HKDF derivation for mobile (Phase 2)

### **After Implementation**
- [ ] Test with test vectors
- [ ] Validate with Java SignatureService
- [ ] Test cross-validation between backend and mobile
- [ ] Document language-specific details

## ðŸš€ **Onboarding for New Developers**

### **Recommended Steps**
1. **Read this document** entirely
2. **Study SignatureService.java** as reference
3. **Implement tests** before implementation
4. **Validate with Java backend** at each step
5. **Document language-specific** details

### **Questions to Ask Yourself**
- Are my keys 32 bytes (Ed25519)?
- Am I using pure Ed25519 (not EdDSA)?
- Are my signatures 64 bytes?
- Can the Java backend validate my signatures?
- Can I validate Java backend signatures?
- Am I using HKDF correctly for mobile key derivation (Phase 2)?

---

**Note**: This document must be updated whenever Ezkey's cryptographic specifications are modified.
