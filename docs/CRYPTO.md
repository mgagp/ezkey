# 🔐 Cryptographic Guide - Ezkey

## 📋 **Overview**

This document defines the strict cryptographic requirements for Ezkey implementation. It is based on the analysis of `SignatureService.java` and the implementation errors observed in the Dart code.

## 🎯 **Objective**

Ensure cryptographic compatibility between all implementations (Java, Dart, JavaScript, etc.) by defining precise and unambiguous specifications.

## 🚨 **Lessons Learned from Dart Implementation**

### **❌ Errors Made**

1. **Mock Keys Instead of Real RSA Keys**
   - ❌ Use of hardcoded non-RSA keys
   - ❌ Format incompatible with Java PKCS#8/X.509
   - ❌ Incorrect key size

2. **Incompatible Signature Algorithm**
   - ❌ Simple hash instead of RSA-SHA256
   - ❌ No use of real RSA signatures
   - ❌ Incorrect signature format

3. **Lack of Understanding of Standards**
   - ❌ Non-compliance with ASN.1 formats
   - ❌ Incorrect Base64 encoding
   - ❌ Ignorance of Java specifications

### **✅ Applied Corrections**

1. **Real RSA Keys**
   - ✅ PKCS#8 format (private) and X.509 format (public)
   - ✅ 2048-bit size
   - ✅ Correct Base64 encoding

2. **RSA-SHA256 Signature**
   - ✅ SHA256withRSA algorithm
   - ✅ Java-compatible format
   - ✅ UTF-8 encoding for data

## 🔧 **Cryptographic Specifications**

### **1. RSA Key Generation**

#### **Algorithm**
- **Type**: RSA
- **Size**: 2048 bits
- **Public exponent**: 65537 (0x10001)

#### **Key Formats**

##### **Private Key (PKCS#8)**
```java
// Java format
KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
keyGen.initialize(2048);
KeyPair keyPair = keyGen.generateKeyPair();
String privateKeyBase64 = Base64.getEncoder().encodeToString(
    keyPair.getPrivate().getEncoded()
);
```

##### **Public Key (X.509)**
```java
// Java format
String publicKeyBase64 = Base64.getEncoder().encodeToString(
    keyPair.getPublic().getEncoded()
);
```

#### **ASN.1 Structure**

##### **PKCS#8 Private Key**
```
SEQUENCE {
  version INTEGER { v1(0) },
  algorithm SEQUENCE {
    algorithm OBJECT IDENTIFIER { rsaEncryption(1.2.840.113549.1.1.1) },
    parameters NULL
  },
  privateKey OCTET STRING {
    SEQUENCE {
      version INTEGER { v1(0) },
      modulus INTEGER,
      publicExponent INTEGER,
      privateExponent INTEGER,
      prime1 INTEGER,
      prime2 INTEGER,
      exponent1 INTEGER,
      exponent2 INTEGER,
      coefficient INTEGER
    }
  }
}
```

##### **X.509 Public Key**
```
SEQUENCE {
  algorithm SEQUENCE {
    algorithm OBJECT IDENTIFIER { rsaEncryption(1.2.840.113549.1.1.1) },
    parameters NULL
  },
  subjectPublicKeyInfo BIT STRING {
    SEQUENCE {
      modulus INTEGER,
      publicExponent INTEGER
    }
  }
}
```

### **2. RSA-SHA256 Signature**

#### **Algorithm**
- **Name**: SHA256withRSA
- **Hash**: SHA-256
- **Padding**: PKCS#1 v1.5

#### **Signature Process**

1. **Data Encoding**
   ```java
   byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
   ```

2. **Hash Creation**
   ```java
   MessageDigest digest = MessageDigest.getInstance("SHA-256");
   byte[] hash = digest.digest(dataBytes);
   ```

3. **RSA Signature**
   ```java
   Signature signature = Signature.getInstance("SHA256withRSA");
   signature.initSign(privateKey);
   signature.update(dataBytes);
   byte[] signed = signature.sign();
   ```

4. **Base64 Encoding**
   ```java
   String signatureBase64 = Base64.getEncoder().encodeToString(signed);
   ```

#### **Verification Process**

1. **Signature Decoding**
   ```java
   byte[] signatureBytes = Base64.getDecoder().decode(signatureBase64);
   ```

2. **Verification**
   ```java
   Signature signature = Signature.getInstance("SHA256withRSA");
   signature.initVerify(publicKey);
   signature.update(data.getBytes(StandardCharsets.UTF_8));
   boolean valid = signature.verify(signatureBytes);
   ```

### **3. Proof Tokens**

#### **Enrollment Proof Token**
- **Format**: Base64 URL-safe without padding
- **Structure**: `randomBytes.timestamp.salt`
- **Size**: 32 bytes random + timestamp + 16 bytes salt

#### **Device Proof Token**
- **Format**: Base64 URL-safe
- **Size**: 32 bytes random
- **Usage**: Unique authentication

## 📝 **Implementation Guide by Language**

### **Java (Reference)**
```java
// SignatureService.java - Reference implementation
public String generateSignature(String data, String base64PrivateKey) {
    byte[] keyBytes = Base64.getDecoder().decode(base64PrivateKey);
    PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
    KeyFactory kf = KeyFactory.getInstance("RSA");
    PrivateKey privateKey = kf.generatePrivate(spec);
    Signature signature = Signature.getInstance("SHA256withRSA");
    signature.initSign(privateKey);
    signature.update(data.getBytes(StandardCharsets.UTF_8));
    byte[] signed = signature.sign();
    return Base64.getEncoder().encodeToString(signed);
}
```

### **React Native (Native Modules)**
```javascript
// Android Native Module (Kotlin)
class SignatureService {
    fun signData(data: String, privateKeyBase64: String): String {
        val privateKeyBytes = Base64.getDecoder().decode(privateKeyBase64)
        val spec = PKCS8EncodedKeySpec(privateKeyBytes)
        val kf = KeyFactory.getInstance("RSA")
        val privateKey = kf.generatePrivate(spec)
        
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(privateKey)
        signature.update(data.toByteArray(StandardCharsets.UTF_8))
        val signed = signature.sign()
        return Base64.getEncoder().encodeToString(signed)
    }
}

// iOS Native Module (Swift)
class SignatureService {
    func signData(data: String, privateKeyBase64: String) -> String {
        // Implementation using CryptoKit
        // ...
    }
}
```

### **JavaScript/Node.js**
```javascript
const crypto = require('crypto');

function signData(data, privateKeyBase64) {
  // 1. Decode private key
  const privateKey = Buffer.from(privateKeyBase64, 'base64');
  
  // 2. Sign with RSA-SHA256
  const sign = crypto.createSign('RSA-SHA256');
  sign.update(data, 'utf8');
  const signature = sign.sign(privateKey, 'base64');
  
  return signature;
}
```

### **Python**
```python
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa, padding
import base64

def sign_data(data, private_key_base64):
    # 1. Decode private key
    private_key_bytes = base64.b64decode(private_key_base64)
    private_key = serialization.load_der_private_key(
        private_key_bytes, password=None
    )
    
    # 2. Sign with RSA-SHA256
    signature = private_key.sign(
        data.encode('utf-8'),
        padding.PKCS1v15(),
        hashes.SHA256()
    )
    
    # 3. Encode to Base64
    return base64.b64encode(signature).decode('utf-8')
```

## 🧪 **Compatibility Tests**

### **Cross-Validation Test**
```bash
# Generate keys and signatures in each language
# Verify that Java can validate signatures from other languages
# Verify that other languages can validate Java signatures
```

### **Test Vectors**
```json
{
  "data": "test-enrollment-proof-token-123",
  "privateKey": "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSj...",
  "publicKey": "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...",
  "expectedSignature": "base64-encoded-signature"
}
```

## ⚠️ **Pitfalls to Avoid**

### **1. Mock Keys**
- ❌ Never use hardcoded non-RSA keys
- ❌ Never use keys of incorrect size
- ✅ Always generate real RSA 2048-bit keys

### **2. Incorrect Algorithms**
- ❌ Never use simple hash
- ❌ Never use non-RSA algorithm
- ✅ Always use SHA256withRSA

### **3. Incorrect Formats**
- ❌ Never ignore ASN.1 formats
- ❌ Never use incorrect encoding
- ✅ Always respect PKCS#8/X.509

### **4. Incorrect Encoding**
- ❌ Never ignore UTF-8 encoding
- ❌ Never use incorrect Base64 encoding
- ✅ Always use StandardCharsets.UTF_8

## 🔍 **Validation and Debugging**

### **Required Debug Logs**
```java
logger.debug("Data to sign: {}", data);
logger.debug("Data length: {}", data.length());
logger.debug("Private key length: {}", privateKeyBase64.length());
logger.debug("Signature length: {}", signatureBase64.length());
logger.debug("Signature: {}", signatureBase64);
```

### **Validation Tests**
1. **Generation Test**: Verify keys are in correct format
2. **Signature Test**: Verify signature is valid
3. **Validation Test**: Verify Java can validate signature
4. **Cross Test**: Verify compatibility between languages

## 📚 **Reference Resources**

### **Standards**
- [RFC 8017 - PKCS#1](https://tools.ietf.org/html/rfc8017)
- [RFC 5280 - X.509](https://tools.ietf.org/html/rfc5280)
- [RFC 3447 - RSA](https://tools.ietf.org/html/rfc3447)

### **Reference Implementations**
- [Java SignatureService.java](../ezkey-core/src/main/java/org/ezkey/signature/SignatureService.java)
- [Java SignatureServiceTest.java](../ezkey-core/src/test/java/org/ezkey/signature/SignatureServiceTest.java)

### **Testing Tools**
- [OpenSSL](https://www.openssl.org/) for validation
- [ASN.1 Decoder](https://lapo.it/asn1js/) for debugging
- [Base64 Decoder](https://www.base64decode.org/) for validation

## 🎯 **Implementation Checklist**

### **Before Starting**
- [ ] Understand PKCS#8 and X.509 formats
- [ ] Understand SHA256withRSA algorithm
- [ ] Have access to testing tools

### **During Implementation**
- [ ] Generate real RSA 2048-bit keys
- [ ] Use correct ASN.1 format
- [ ] Implement SHA256withRSA correctly
- [ ] Use UTF-8 for data encoding
- [ ] Use Base64 for key/signature encoding

### **After Implementation**
- [ ] Test with test vectors
- [ ] Validate with Java SignatureService
- [ ] Test cross-validation
- [ ] Document language-specific details

## 🚀 **Onboarding for New Developers**

### **Recommended Steps**
1. **Read this document** entirely
2. **Study SignatureService.java** as reference
3. **Implement tests** before implementation
4. **Validate with Java** at each step
5. **Document language-specific** details

### **Questions to Ask Yourself**
- Are my keys in PKCS#8/X.509 format?
- Am I using SHA256withRSA?
- Can Java validate my signatures?
- Can I validate Java signatures?

---

**Note**: This document must be updated whenever Ezkey's cryptographic specifications are modified.
