# Guide de Référence Cryptographique Mobile - Ezkey

## 📋 Vue d'Ensemble

Ce document définit l'approche cryptographique native recommandée pour l'application mobile Ezkey, basée sur les capacités natives d'Android Keystore et iOS Secure Enclave.

## 🎯 Objectif

Utiliser les capacités cryptographiques **natives** des plateformes mobiles pour garantir :
- ✅ Protection hardware-backed (StrongBox/Secure Enclave)
- ✅ Simplicité d'implémentation
- ✅ Compatibilité backend
- ✅ Sécurité maximale

## 🔐 Approche Cryptographique Recommandée

### **EC P-256 (Elliptic Curve P-256)**

**Pourquoi EC P-256 est l'approche native :**

1. **Support Natif Android** :
   - Android Keystore supporte EC P-256 directement
   - StrongBox (hardware-backed) disponible pour EC P-256
   - Pas besoin de bibliothèques tierces

2. **Support Natif iOS** :
   - iOS Secure Enclave supporte EC P-256 directement
   - Protection matérielle intégrée
   - API native (`SecKeyCreateRandomKey`)

3. **Simplicité** :
   - Génération directe d'une clé par enrollment
   - Pas de dérivation HKDF nécessaire
   - Pas de master seed à gérer
   - Pas de chiffrement/déchiffrement

4. **Sécurité** :
   - Clés non-extractables (hardware-backed)
   - Protection matérielle (StrongBox/Secure Enclave)
   - Standard NIST recommandé

## 📱 Implémentation Android

### Génération de Clé par Enrollment

```kotlin
fun generateEnrollmentKeyPair(enrollmentId: String): KeyPair {
    val keyPairGenerator = KeyPairGenerator.getInstance(
        KeyProperties.KEY_ALGORITHM_EC, 
        "AndroidKeyStore"
    )
    
    val spec = KeyGenParameterSpec.Builder(
        "enrollment_$enrollmentId",
        KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
    )
        .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1")) // P-256
        .setDigests(KeyProperties.DIGEST_SHA256)
        .setIsStrongBoxBacked(true) // Hardware-backed si disponible
        .setUserAuthenticationRequired(false)
        .build()
    
    keyPairGenerator.initialize(spec)
    return keyPairGenerator.generateKeyPair()
}
```

### Récupération de la Clé Publique

```kotlin
fun getPublicKey(enrollmentId: String): ByteArray {
    val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    val entry = keyStore.getEntry("enrollment_$enrollmentId", null) as? KeyStore.PrivateKeyEntry
    val publicKey = entry?.certificate?.publicKey as? ECPublicKey
    return publicKey?.encoded ?: throw IllegalStateException("Key not found")
}
```

### Signature avec Clé Privée

```kotlin
fun sign(enrollmentId: String, data: String): ByteArray {
    val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    val entry = keyStore.getEntry("enrollment_$enrollmentId", null) as? KeyStore.PrivateKeyEntry
    val privateKey = entry?.privateKey ?: throw IllegalStateException("Key not found")
    
    val signature = Signature.getInstance("SHA256withECDSA")
    signature.initSign(privateKey)
    signature.update(data.toByteArray(StandardCharsets.UTF_8))
    return signature.sign()
}
```

### Spécifications Techniques

- **Algorithme** : EC (Elliptic Curve)
- **Courbe** : secp256r1 (P-256)
- **Signature** : ECDSA avec SHA-256
- **Format Clé Publique** : X.509 (65 bytes non-compressed)
- **Format Signature** : ASN.1 DER (variable, ~70 bytes)
- **Encodage Transport** : Base64

## 🍎 Implémentation iOS (À Implémenter)

### Génération de Clé par Enrollment

```swift
func generateEnrollmentKeyPair(enrollmentId: String) throws -> SecKey {
    let attributes: [String: Any] = [
        kSecAttrKeyType as String: kSecAttrKeyTypeECSECPrimeRandom,
        kSecAttrKeySizeInBits as String: 256,
        kSecAttrTokenID as String: kSecAttrTokenIDSecureEnclave,
        kSecPrivateKeyAttrs as String: [
            kSecAttrIsPermanent as String: true,
            kSecAttrApplicationTag as String: "enrollment_\(enrollmentId).data"
        ],
        kSecPublicKeyAttrs as String: [
            kSecAttrIsPermanent as String: true,
            kSecAttrApplicationTag as String: "enrollment_\(enrollmentId).public"
        ]
    ]
    
    var error: Unmanaged<CFError>?
    guard let privateKey = SecKeyCreateRandomKey(attributes as CFDictionary, &error) else {
        throw error!.takeRetainedValue() as Error
    }
    
    return privateKey
}
```

### Récupération de la Clé Publique

```swift
func getPublicKey(enrollmentId: String) throws -> Data {
    let query: [String: Any] = [
        kSecClass as String: kSecClassKey,
        kSecAttrApplicationTag as String: "enrollment_\(enrollmentId).public",
        kSecAttrKeyType as String: kSecAttrKeyTypeECSECPrimeRandom,
        kSecReturnData as String: true
    ]
    
    var result: AnyObject?
    let status = SecItemCopyMatching(query as CFDictionary, &result)
    guard status == errSecSuccess, let data = result as? Data else {
        throw NSError(domain: "KeyError", code: Int(status))
    }
    
    return data
}
```

### Signature avec Clé Privée

```swift
func sign(enrollmentId: String, data: String) throws -> Data {
    let query: [String: Any] = [
        kSecClass as String: kSecClassKey,
        kSecAttrApplicationTag as String: "enrollment_\(enrollmentId).data",
        kSecAttrKeyType as String: kSecAttrKeyTypeECSECPrimeRandom,
        kSecReturnRef as String: true
    ]
    
    var result: AnyObject?
    let status = SecItemCopyMatching(query as CFDictionary, &result)
    guard status == errSecSuccess, let privateKey = result as! SecKey? else {
        throw NSError(domain: "KeyError", code: Int(status))
    }
    
    let dataToSign = data.data(using: .utf8)!
    var error: Unmanaged<CFError>?
    guard let signature = SecKeyCreateSignature(
        privateKey,
        .ecdsaSignatureMessageX962SHA256,
        dataToSign as CFData,
        &error
    ) as Data? else {
        throw error!.takeRetainedValue() as Error
    }
    
    return signature
}
```

## 🔄 Migration Backend

### Contexte

Le backend a récemment migré de **RSA 2048 vers Ed25519**, mais cette approche n'est **pas compatible** avec les capacités natives des plateformes mobiles :

- ❌ Ed25519 n'est pas supporté nativement par Android Keystore
- ❌ Ed25519 n'est pas supporté nativement par iOS Secure Enclave
- ❌ Nécessite des bibliothèques tierces (BouncyCastle) sur mobile
- ❌ Pas de protection hardware-backed native

### Stratégie de Migration Backend

#### Phase 1: Support Dual (EC P-256 + Ed25519)

**Objectif** : Maintenir la compatibilité avec les implémentations existantes tout en ajoutant le support EC P-256.

**Actions** :
1. Ajouter support EC P-256 dans `SignatureService`
2. Accepter les deux formats dans les endpoints :
   - `devicePublicKey` : EC P-256 (X.509) ou Ed25519 (32 bytes raw)
   - `enrollmentProofTokenSigned` : ECDSA-SHA256 ou Ed25519
3. Détection automatique du format basée sur la taille/clé publique

**Implémentation Backend** :

```java
// SignatureService.java - Ajouter support EC P-256
public String generateSignatureECP256(String data, PrivateKey privateKey) {
    try {
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign(privateKey);
        signature.update(data.getBytes(StandardCharsets.UTF_8));
        byte[] sigBytes = signature.sign();
        return Base64.getEncoder().encodeToString(sigBytes);
    } catch (Exception e) {
        throw new RuntimeException("EC P-256 signature generation failed", e);
    }
}

public boolean verifySignatureECP256(String data, String signatureBase64, PublicKey publicKey) {
    try {
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initVerify(publicKey);
        signature.update(data.getBytes(StandardCharsets.UTF_8));
        byte[] sigBytes = Base64.getDecoder().decode(signatureBase64);
        return signature.verify(sigBytes);
    } catch (Exception e) {
        throw new RuntimeException("EC P-256 signature verification failed", e);
    }
}
```

#### Phase 2: Migration Progressive

**Stratégie** :
1. **Nouveaux enrollments** : Utiliser EC P-256 uniquement
2. **Enrollments existants** : Continuer à supporter Ed25519
3. **Dépréciation** : Marquer Ed25519 comme déprécié dans la documentation

**Timeline** :
- **Q1 2025** : Support dual (EC P-256 + Ed25519)
- **Q2 2025** : Migration mobile vers EC P-256
- **Q3 2025** : Dépréciation Ed25519 (nouveaux enrollments uniquement EC P-256)
- **Q4 2025** : Support Ed25519 pour compatibilité legacy uniquement

#### Phase 3: Standardisation EC P-256

**Objectif** : EC P-256 devient le standard unique pour tous les nouveaux enrollments.

**Actions** :
1. Mettre à jour `docs/CRYPTO.md` pour refléter EC P-256 comme standard
2. Mettre à jour les tests pour utiliser EC P-256
3. Documenter la migration depuis Ed25519

## 📊 Comparaison: Ed25519 vs EC P-256

| Critère | Ed25519 | EC P-256 |
|---------|---------|----------|
| **Support Natif Android** | ❌ Non | ✅ Oui |
| **Support Natif iOS** | ❌ Non | ✅ Oui |
| **Hardware-Backed** | ❌ Non | ✅ Oui (StrongBox/Secure Enclave) |
| **Taille Clé Privée** | 32 bytes | 32 bytes (équivalent) |
| **Taille Clé Publique** | 32 bytes | 65 bytes (non-compressed) |
| **Taille Signature** | 64 bytes | ~70 bytes (ASN.1 DER) |
| **Performance** | ⚡ Plus rapide | ⚡ Rapide |
| **Sécurité** | ✅ Excellent | ✅ Excellent (NIST recommandé) |
| **Simplicité Mobile** | ⚠️ Complexe | ✅ Simple (native) |
| **Compatibilité Backend** | ✅ BouncyCastle | ✅ Standard Java |

## 🔒 Spécifications Techniques

### Format Clé Publique EC P-256

**Format** : X.509 SubjectPublicKeyInfo (ASN.1 DER)
**Taille** : 91 bytes (non-compressed) ou 65 bytes (compressed)
**Encodage Transport** : Base64

**Exemple** :
```
-----BEGIN PUBLIC KEY-----
MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE...
-----END PUBLIC KEY-----
```

### Format Signature EC P-256

**Format** : ASN.1 DER (ECDSA signature)
**Taille** : Variable (~70 bytes)
**Algorithme** : ECDSA avec SHA-256
**Encodage Transport** : Base64

**Structure ASN.1** :
```
SEQUENCE {
    r INTEGER,
    s INTEGER
}
```

## ✅ Checklist de Migration

### Mobile (Android)

- [ ] Implémenter génération EC P-256 dans `EzkeyCryptoModule.kt`
- [ ] Implémenter récupération clé publique EC P-256
- [ ] Implémenter signature ECDSA-SHA256
- [ ] Mettre à jour `nativeCrypto.ts` pour exposer méthodes EC P-256
- [ ] Mettre à jour `cryptoService.ts` pour utiliser EC P-256
- [ ] Supprimer code Ed25519/HKDF
- [ ] Tests unitaires et intégration

### Mobile (iOS)

- [ ] Implémenter génération EC P-256 dans `EzkeyCryptoModule.swift`
- [ ] Implémenter récupération clé publique EC P-256
- [ ] Implémenter signature ECDSA-SHA256
- [ ] Mettre à jour bridge React Native
- [ ] Tests unitaires et intégration

### Backend

- [ ] Ajouter support EC P-256 dans `SignatureService`
- [ ] Ajouter détection automatique format (EC P-256 vs Ed25519)
- [ ] Mettre à jour `EnrollmentVerifyService` pour accepter EC P-256
- [ ] Mettre à jour `AuthAttemptService` pour vérifier EC P-256
- [ ] Mettre à jour schéma base de données si nécessaire
- [ ] Tests unitaires et intégration
- [ ] Documentation API mise à jour

## 📚 Références

- [Android Keystore System](https://developer.android.com/training/articles/keystore)
- [iOS Secure Enclave](https://developer.apple.com/documentation/security/certificate_key_and_trust_services/keys/storing_keys_in_the_secure_enclave)
- [NIST SP 800-186: Recommendations for Discrete Logarithm-Based Cryptography](https://csrc.nist.gov/publications/detail/sp/800-186/final)
- [RFC 5480: Elliptic Curve Cryptography Subject Public Key Info](https://tools.ietf.org/html/rfc5480)

## 🔄 Historique des Versions

- **2025-01-XX** : Migration depuis Ed25519 vers EC P-256 pour compatibilité mobile native
- **2024-XX-XX** : Migration initiale depuis RSA 2048 vers Ed25519 (erreur de conception)

