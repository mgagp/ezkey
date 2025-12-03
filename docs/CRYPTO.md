# 🔐 Cryptographic Guide - Ezkey

## 📋 **Overview**

This document defines the strict cryptographic requirements for Ezkey implementation. The system uses **EC P-256 (secp256r1)** with **ECDSA-SHA256** for all digital signatures, providing production-grade security with native mobile hardware support.

## 🎯 **Objective**

Ensure cryptographic compatibility between all implementations (Java backend, mobile apps) by defining precise and unambiguous specifications for EC P-256 signatures.

## 🔧 **Cryptographic Specifications**

### **1. EC P-256 Key Generation**

#### **Algorithm**
- **Type**: EC P-256 (secp256r1) - Elliptic Curve P-256
- **Curve**: secp256r1 (NIST P-256)
- **Private Key Format**: PKCS#8 (ASN.1 DER encoded)
- **Public Key Format**: X.509 SubjectPublicKeyInfo (ASN.1 DER encoded)
- **Signature Algorithm**: ECDSA with SHA-256
- **Signature Format**: ASN.1 DER encoded (variable length, ~70 bytes Base64)

#### **Key Formats**

##### **Private Key (PKCS#8 format, Base64 encoded)**
```java
// Java format using BouncyCastle
ECKeyPairGenerator keyGen = new ECKeyPairGenerator();
ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp256r1");
keyGen.init(new ECKeyGenerationParameters(ecSpec, secureRandom));
AsymmetricCipherKeyPair keyPair = keyGen.generateKeyPair();
ECPrivateKeyParameters privateKey = (ECPrivateKeyParameters) keyPair.getPrivate();

// Encode as PKCS#8
PrivateKeyInfo privateKeyInfo = PrivateKeyInfoFactory.createPrivateKeyInfo(privateKey);
String privateKeyBase64 = Base64.getEncoder().encodeToString(privateKeyInfo.getEncoded());
```

##### **Public Key (X.509 format, Base64 encoded)**
```java
// Java format using BouncyCastle
ECPublicKeyParameters publicKey = (ECPublicKeyParameters) keyPair.getPublic();

// Encode as X.509 SubjectPublicKeyInfo
SubjectPublicKeyInfo publicKeyInfo = SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(publicKey);
String publicKeyBase64 = Base64.getEncoder().encodeToString(publicKeyInfo.getEncoded());
```

**Note**: EC P-256 keys are stored in standard PKCS#8 (private) and X.509 (public) formats, Base64 encoded for transmission. This ensures compatibility with native mobile hardware-backed keystores (Android Keystore, iOS Secure Enclave).

### **2. EC P-256 Signature (ECDSA-SHA256)**

#### **Algorithm**
- **Name**: ECDSA with SHA-256
- **Curve**: secp256r1 (EC P-256)
- **Hash Algorithm**: SHA-256
- **Signature Format**: ASN.1 DER encoded (variable length, typically ~70 bytes Base64)

#### **Signature Process**

1. **Data Encoding and Hashing**
   ```java
   byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
   MessageDigest digest = MessageDigest.getInstance("SHA-256");
   byte[] hash = digest.digest(dataBytes);
   ```

2. **Signing**
   ```java
   byte[] keyBytes = Base64.getDecoder().decode(base64PrivateKey);
   ECPrivateKeyParameters privateKeyParams = 
       (ECPrivateKeyParameters) PrivateKeyFactory.createKey(keyBytes);
   
   ECDSASigner signer = new ECDSASigner();
   signer.init(true, privateKeyParams);
   BigInteger[] signature = signer.generateSignature(hash);
   
   // Encode as ASN.1 DER
   byte[] derSignature = encodeDERSignature(signature[0], signature[1]);
   ```

3. **Base64 Encoding**
   ```java
   String signatureBase64 = Base64.getEncoder().encodeToString(derSignature);
   ```

#### **Verification Process**

1. **Signature Decoding**
   ```java
   byte[] signatureBytes = Base64.getDecoder().decode(signatureBase64);
   BigInteger[] signature = decodeDERSignature(signatureBytes);
   ```

2. **Verification**
   ```java
   byte[] keyBytes = Base64.getDecoder().decode(base64PublicKey);
   ECPublicKeyParameters publicKeyParams = 
       (ECPublicKeyParameters) PublicKeyFactory.createKey(keyBytes);
   
   byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
   MessageDigest digest = MessageDigest.getInstance("SHA-256");
   byte[] hash = digest.digest(dataBytes);
   
   ECDSASigner verifier = new ECDSASigner();
   verifier.init(false, publicKeyParams);
   boolean valid = verifier.verifySignature(hash, signature[0], signature[1]);
   ```

### **3. Mutual Cryptographic Authentication**

Ezkey implements mutual cryptographic authentication where both the backend and mobile app cryptographically verify each other's authenticity:

#### **Backend → Mobile Authentication**
- Backend signs `authAttemptProofToken` with `integration_private_key` (EC P-256, PKCS#8)
- Mobile verifies signature using stored `integration_public_key` (EC P-256, X.509)
- This ensures the mobile app can trust that authentication requests come from the legitimate backend

#### **Mobile → Backend Authentication**
- Mobile signs responses with `device_private_key` (EC P-256, hardware-backed)
- Backend verifies signature using stored `device_public_key` (EC P-256, X.509)
- This ensures the backend can trust that responses come from the legitimate device

### **4. Key Management**

#### **Backend Integration Keys**
- Generated per enrollment during enrollment creation
- EC P-256 key pair (PKCS#8 private key, X.509 public key)
- Direct generation using BouncyCastle (no derivation needed)
- Stored in database: `integration_private_key` (encrypted at rest), `integration_public_key` (plaintext)

#### **Mobile Device Keys**
- **Android**: Hardware-backed keys stored in Android Keystore (StrongBox preferred)
- **iOS**: Hardware-backed keys stored in Secure Enclave
- EC P-256 key pair generated natively by platform keystores
- Only `device_public_key` (X.509 format) sent to backend during enrollment verify
- Private keys are **non-extractable** and managed entirely by hardware security modules

**Note**: Mobile keys are generated directly by platform keystores (no HKDF derivation needed). This provides better security and performance compared to software-based key derivation.

### **5. Proof Tokens**

#### **Enrollment Proof Token**
- **Format**: Base64 URL-safe without padding
- **Structure**: `randomBytes.timestamp.salt`
- **Size**: 32 bytes random + timestamp + 16 bytes salt

#### **Device Proof Token**
- **Format**: Base64 URL-safe
- **Size**: 32 bytes random
- **Usage**: Unique authentication

## 💻 **Implementation Guide by Language**

### **Java (Reference - Backend)**

```java
// SignatureService.java - Reference implementation
public String generateSignature(String data, String base64PrivateKey) {
    byte[] keyBytes = Base64.getDecoder().decode(base64PrivateKey);
    ECPrivateKeyParameters privateKeyParams = 
        (ECPrivateKeyParameters) PrivateKeyFactory.createKey(keyBytes);
    
    ECDSASigner signer = new ECDSASigner();
    signer.init(true, privateKeyParams);
    
    byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] hash = digest.digest(dataBytes);
    
    BigInteger[] signature = signer.generateSignature(hash);
    byte[] derSignature = encodeDERSignature(signature[0], signature[1]);
    return Base64.getEncoder().encodeToString(derSignature);
}
```

### **Mobile App**

#### **Android Native Module (Kotlin)**
```kotlin
// Generate EC P-256 key pair using Android Keystore
fun generateEnrollmentKeyPair(enrollmentId: String): KeyPair {
    val keyGenParameterSpec = KeyGenParameterSpec.Builder(
        "ezkey_enrollment_$enrollmentId",
        KeyProperties.PURPOSE_SIGN
    )
        .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
        .setDigests(KeyProperties.DIGEST_SHA256)
        .setKeySize(256)
        .setIsStrongBoxBacked(true) // Use StrongBox if available
        .build()
    
    val keyPairGenerator = KeyPairGenerator.getInstance(
        KeyProperties.KEY_ALGORITHM_EC,
        "AndroidKeyStore"
    )
    keyPairGenerator.initialize(keyGenParameterSpec)
    return keyPairGenerator.generateKeyPair()
}

fun signData(data: String, keyAlias: String): String {
    val keyStore = KeyStore.getInstance("AndroidKeyStore")
    keyStore.load(null)
    val privateKey = keyStore.getKey(keyAlias, null) as PrivateKey
    
    val signature = Signature.getInstance("SHA256withECDSA")
    signature.initSign(privateKey)
    signature.update(data.toByteArray(StandardCharsets.UTF_8))
    val signatureBytes = signature.sign()
    
    return Base64.getEncoder().encodeToString(signatureBytes)
}
```

#### **iOS Native Module (Swift)**
```swift
// Generate EC P-256 key pair using Secure Enclave
func generateEnrollmentKeyPair(enrollmentId: String) throws -> SecKey {
    let attributes: [String: Any] = [
        kSecAttrKeyType as String: kSecAttrKeyTypeECSECPrimeRandom,
        kSecAttrKeySizeInBits as String: 256,
        kSecAttrTokenID as String: kSecAttrTokenIDSecureEnclave,
        kSecPrivateKeyAttrs as String: [
            kSecAttrIsPermanent as String: true,
            kSecAttrApplicationTag as String: "ezkey.enrollment.\(enrollmentId)".data(using: .utf8)!
        ]
    ]
    
    var error: Unmanaged<CFError>?
    guard let privateKey = SecKeyCreateRandomKey(attributes as CFDictionary, &error) else {
        throw error!.takeRetainedValue() as Error
    }
    
    return privateKey
}

func signData(data: String, privateKey: SecKey) throws -> String {
    guard let dataToSign = data.data(using: .utf8) else {
        throw CryptoError.invalidData
    }
    
    var error: Unmanaged<CFError>?
    guard let signature = SecKeyCreateSignature(
        privateKey,
        .ecdsaSignatureMessageX962SHA256,
        dataToSign as CFData,
        &error
    ) as Data? else {
        throw error!.takeRetainedValue() as Error
    }
    
    return signature.base64EncodedString()
}
```

## 🧪 **Compatibility Tests**

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
  "privateKey": "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQg...",
  "publicKey": "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE...",
  "expectedSignature": "base64-encoded-asn1-der-signature"
}
```

## ⚠️ **Pitfalls to Avoid**

### **1. Key Format**
- ❌ Never use raw 32-byte keys (Ed25519 format) - EC P-256 uses PKCS#8/X.509
- ❌ Never confuse EC P-256 with Ed25519
- ✅ Always use PKCS#8 format for private keys
- ✅ Always use X.509 format for public keys

### **2. Signature Algorithm**
- ❌ Never use Ed25519 or EdDSA - use ECDSA-SHA256
- ❌ Never forget to hash data with SHA-256 before signing (ECDSA requirement)
- ❌ Never use raw (r, s) signature format - use ASN.1 DER encoding
- ✅ Always use ECDSA-SHA256 with EC P-256
- ✅ Always encode signatures as ASN.1 DER

### **3. Key Derivation (Mobile)**
- ❌ Never derive keys manually - use platform keystores
- ❌ Never extract private keys from hardware-backed storage
- ✅ Always use Android Keystore or iOS Secure Enclave for key generation
- ✅ Always use native platform APIs for signing

### **4. Encoding**
- ❌ Never ignore UTF-8 encoding for data
- ❌ Never use incorrect Base64 encoding
- ❌ Never forget to hash data before ECDSA signing
- ✅ Always use StandardCharsets.UTF_8 for data
- ✅ Always use standard Base64 encoding for keys and signatures
- ✅ Always hash data with SHA-256 before ECDSA signing

## 🔍 **Validation and Debugging**

### **Mobile Diagnostics (React Native)**

1. **Open the Diagnostics screen**  
   - The home header has a `Diagnostics` button.  
   - The screen lists every stored enrollment with its secure alias.

2. **Run a self-test**  
   - Tap `Run self-test`; the app generates a payload `ezkey-mobile-diagnostic:<timestamp>` and signs it with the device key.  
   - The UI shows:
     - Alias used for signing  
     - Current public key (Base64 encoded, X.509 format)  
     - Signature (Base64 encoded, ASN.1 DER format)  
     - Any error (e.g., "No secure alias stored")
   - You can copy these values to reproduce the verification with `SignatureService`.

3. **Interpreting results**
   - **Success** → Keys exist and signatures match the backend expectations.  
   - **Error: No secure alias stored** → The enrollment was wiped (clear app data, new device, recovery). Re-enroll the device.  
   - **Other errors** → The native bridge failed (e.g., Secure Enclave unavailable). Re-run on a device that supports key generation.

4. **Resetting the environment**
   - **Android**: Settings → Apps → Ezkey Mobile → Storage → "Clear storage".  
   - **iOS**: Delete the app.  
   - Reinstall/rebind the device afterwards to restore keys.

### **Required Debug Logs**
```java
logger.debug("Data to sign: {}", data);
logger.debug("Data length: {}", data.length());
logger.debug("Private key length: {}", privateKeyBase64.length());
logger.debug("Public key length: {}", publicKeyBase64.length());
logger.debug("Signature length: {}", signatureBase64.length());
logger.debug("Signature: {}", signatureBase64);
```

### **Validation Tests**
1. **Generation Test**: Verify keys are valid PKCS#8/X.509 format (EC P-256)
2. **Signature Test**: Verify signature is valid ASN.1 DER format (ECDSA-SHA256)
3. **Validation Test**: Verify backend can validate signature
4. **Cross Test**: Verify compatibility between backend and mobile

## 📚 **Reference Resources**

### **Standards**
- [RFC 5480 - Elliptic Curve Cryptography Subject Public Key Info](https://tools.ietf.org/html/rfc5480)
- [NIST SP 800-186 - Recommendations for Discrete Logarithm-Based Cryptography: Elliptic Curve Domain Parameters](https://csrc.nist.gov/publications/detail/sp/800-186/final)
- [SEC 1: Elliptic Curve Cryptography](https://www.secg.org/sec1-v2.pdf)
- [RFC 3279 - Algorithms and Identifiers for the Internet X.509 Public Key Infrastructure](https://tools.ietf.org/html/rfc3279)

### **Reference Implementations**
- [Java SignatureService.java](../ezkey-core/src/main/java/org/ezkey/signature/SignatureService.java)
- [Java SignatureServiceTest.java](../ezkey-core/src/test/java/org/ezkey/signature/SignatureServiceTest.java)
- [Android EzkeyCryptoModule.kt](../ezkey_mobile/android/app/src/main/java/com/ezkeymobile/crypto/EzkeyCryptoModule.kt)

### **Testing Tools**
- [BouncyCastle](https://www.bouncycastle.org/) for Java EC P-256 support
- [Base64 Decoder](https://www.base64decode.org/) for validation
- [ASN.1 Decoder](https://lapo.it/asn1js/) for signature format validation

## 🎯 **Implementation Checklist**

### **Before Starting**
- [ ] Understand EC P-256 key format (PKCS#8/X.509)
- [ ] Understand ECDSA-SHA256 signature format (ASN.1 DER)
- [ ] Understand native mobile keystore APIs (Android Keystore, iOS Secure Enclave)
- [ ] Have access to testing tools

### **During Implementation**
- [ ] Generate real EC P-256 keys (PKCS#8/X.509 format)
- [ ] Use correct ECDSA-SHA256 signature algorithm
- [ ] Implement ASN.1 DER signature encoding correctly
- [ ] Hash data with SHA-256 before signing (ECDSA requirement)
- [ ] Use UTF-8 for data encoding
- [ ] Use Base64 for key/signature encoding
- [ ] Use native platform keystores for mobile (no manual derivation)

### **After Implementation**
- [ ] Test with test vectors
- [ ] Validate with Java SignatureService
- [ ] Test cross-validation between backend and mobile
- [ ] Document language-specific details

## 🚀 **Onboarding for New Developers**

### **Recommended Steps**
1. **Read this document** entirely
2. **Study SignatureService.java** as reference
3. **Review mobile native modules** (Android/iOS) for platform-specific details
4. **Implement tests** before implementation
5. **Validate with Java backend** at each step
6. **Document language-specific** details

### **Questions to Ask Yourself**
- Are my keys in PKCS#8/X.509 format (EC P-256)?
- Am I using ECDSA-SHA256 (not Ed25519)?
- Are my signatures ASN.1 DER encoded?
- Am I hashing data with SHA-256 before signing?
- Can the Java backend validate my signatures?
- Can I validate Java backend signatures?
- Am I using native platform keystores for mobile (not manual derivation)?

---

**Note**: This document must be updated whenever Ezkey's cryptographic specifications are modified.

**Migration Note**: Ezkey previously used Ed25519 but migrated to EC P-256 for better mobile hardware support and native platform integration. See [MOBILE_CRYPTO_REFERENCE.md](MOBILE_CRYPTO_REFERENCE.md) for migration details.
