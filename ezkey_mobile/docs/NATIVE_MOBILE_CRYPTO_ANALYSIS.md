# Analyse: Approches Natives pour Cryptographie Mobile

## Question Fondamentale

Quelle est l'approche **native, naturelle et recommandée** pour la gestion de clés cryptographiques dans les applications mobiles Android et iOS ?

## Approches Natives Recommandées

### 1. Android Keystore - Approche Native

**Ce que Android Keystore supporte NATIVEMENT :**

#### ✅ Clés Asymétriques (KeyPairGenerator)
- **EC (Elliptic Curve)** : P-256, P-384, P-521
- **RSA** : 2048, 3072, 4096 bits
- **Hardware-backed** : StrongBox si disponible
- **Non-extractable** : Par défaut pour hardware-backed

#### ✅ Clés Symétriques (KeyGenerator)
- **AES** : 128, 192, 256 bits
- **Hardware-backed** : StrongBox si disponible
- **Modes** : GCM, CBC, CTR, ECB
- **Limitations** : IV auto-généré pour hardware-backed

#### ❌ Ce que Android Keystore NE supporte PAS
- **Ed25519** : Pas de support natif
- **HKDF direct** : Pas d'API native pour HKDF avec hardware-backed keys
- **Extraction de bytes** : Clés hardware-backed non-extractables

### 2. iOS Keychain / Secure Enclave - Approche Native

**Ce que iOS supporte NATIVEMENT :**

#### ✅ Clés Asymétriques (SecKeyGeneratePair)
- **EC (Elliptic Curve)** : P-256, P-384, P-521
- **RSA** : 2048, 3072, 4096 bits
- **Secure Enclave** : Support natif pour EC P-256
- **Non-extractable** : Par défaut pour Secure Enclave

#### ✅ Clés Symétriques
- **AES** : Via CryptoKit ou CommonCrypto
- **Secure Enclave** : Support limité pour clés symétriques

#### ❌ Ce que iOS NE supporte PAS
- **Ed25519** : Pas de support natif (nécessite CryptoKit ou bibliothèque tierce)
- **HKDF direct** : Pas d'API native avec Secure Enclave

## Approche Native Recommandée pour Ezkey

### Option A: EC P-256 (Recommandée - Native)

**Pourquoi EC P-256 est l'approche native :**

1. **Support Natif Android** :
   ```kotlin
   KeyPairGenerator.getInstance("EC", "AndroidKeyStore").apply {
       val spec = KeyGenParameterSpec.Builder(
           "enrollment_$enrollmentId",
           KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
       )
           .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1")) // P-256
           .setDigests(KeyProperties.DIGEST_SHA256)
           .setIsStrongBoxBacked(true) // Hardware-backed
           .build()
       initialize(spec)
   }
   ```

2. **Support Natif iOS** :
   ```swift
   let attributes: [String: Any] = [
       kSecAttrKeyType as String: kSecAttrKeyTypeECSECPrimeRandom,
       kSecAttrKeySizeInBits as String: 256,
       kSecAttrTokenID as String: kSecAttrTokenIDSecureEnclave,
       kSecPrivateKeyAttrs as String: [
           kSecAttrIsPermanent as String: true,
           kSecAttrApplicationTag as String: "enrollment_\(enrollmentId)"
       ]
   ]
   var error: Unmanaged<CFError>?
   let privateKey = SecKeyCreateRandomKey(attributes as CFDictionary, &error)
   ```

3. **Avantages** :
   - ✅ **Native** : Support direct par Android/iOS
   - ✅ **Hardware-backed** : StrongBox/Secure Enclave natif
   - ✅ **Pas de dérivation** : Génération directe par enrollment
   - ✅ **Simple** : Pas besoin de HKDF ou master seed
   - ✅ **Sécurisé** : Clés non-extractables, hardware-protected

4. **Backend Compatible** :
   - BouncyCastle supporte EC P-256 nativement
   - Format standard : X.509 pour public key, PKCS#8 pour private key
   - Signature : ECDSA avec SHA-256

### Option B: Ed25519 avec Stockage Chiffré (Alternative)

**Si on veut garder Ed25519 :**

1. **Générer Ed25519 en software** (BouncyCastle)
2. **Chiffrer la clé privée** avec une clé AES hardware-backed
3. **Stocker la clé chiffrée** dans Android Keystore ou Keychain
4. **Déchiffrer à la volée** pour signer

**Avantages** :
- ✅ Ed25519 (plus compact, plus rapide)
- ✅ Hardware-backed pour la clé de chiffrement

**Inconvénients** :
- ⚠️ Plus complexe (chiffrement/déchiffrement)
- ⚠️ Clé privée Ed25519 en mémoire lors du déchiffrement
- ⚠️ Pas de support natif hardware-backed pour Ed25519

### Option C: EC P-256 avec Dérivation (Non Recommandée)

**Pourquoi éviter** :
- ❌ Complexité inutile
- ❌ Pas de support natif pour HKDF avec hardware-backed
- ❌ Nécessite workarounds (master seed, chiffrement, etc.)

## Recommandation Finale

### ✅ **Option A: EC P-256 Native**

**Pourquoi c'est la meilleure approche :**

1. **Simplicité** :
   - Génération directe dans Android Keystore / iOS Secure Enclave
   - Pas de dérivation, pas de master seed, pas de chiffrement
   - Une clé par enrollment, stockée directement dans le hardware

2. **Sécurité Native** :
   - Hardware-backed par défaut (StrongBox/Secure Enclave)
   - Clés non-extractables
   - Protection matérielle intégrée

3. **Compatibilité Backend** :
   - BouncyCastle supporte EC P-256 nativement
   - Format standard (X.509/PKCS#8)
   - ECDSA avec SHA-256 (standard)

4. **Performance** :
   - Opérations natives optimisées
   - Pas de surcharge de dérivation/chiffrement

### Implémentation Proposée

#### Android (Kotlin)
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
        .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
        .setDigests(KeyProperties.DIGEST_SHA256)
        .setIsStrongBoxBacked(true) // Hardware-backed
        .build()
    
    keyPairGenerator.initialize(spec)
    return keyPairGenerator.generateKeyPair()
}
```

#### iOS (Swift)
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

#### Backend (Java/Spring Boot)
```java
// BouncyCastle supporte EC P-256 nativement
KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp256r1");
keyGen.initialize(ecSpec);
KeyPair keyPair = keyGen.generateKeyPair();

// Signature ECDSA avec SHA-256
Signature signature = Signature.getInstance("SHA256withECDSA");
signature.initSign(keyPair.getPrivate());
signature.update(data.getBytes(StandardCharsets.UTF_8));
byte[] sigBytes = signature.sign();
```

## Comparaison: Ed25519 vs EC P-256

| Critère | Ed25519 | EC P-256 |
|---------|---------|----------|
| **Support Natif Android** | ❌ Non | ✅ Oui |
| **Support Natif iOS** | ❌ Non | ✅ Oui |
| **Hardware-Backed** | ❌ Non | ✅ Oui (StrongBox/Secure Enclave) |
| **Taille Clé Privée** | 32 bytes | 32 bytes (équivalent) |
| **Taille Clé Publique** | 32 bytes | 65 bytes (non-compressed) |
| **Taille Signature** | 64 bytes | 64-72 bytes (variable) |
| **Performance** | ⚡ Plus rapide | ⚡ Rapide |
| **Sécurité** | ✅ Excellent | ✅ Excellent (NIST recommandé) |
| **Simplicité Mobile** | ⚠️ Complexe | ✅ Simple (native) |
| **Compatibilité Backend** | ✅ BouncyCastle | ✅ Standard Java |

## Conclusion

**Recommandation : Utiliser EC P-256 (Option A)**

**Raisons** :
1. ✅ Approche native pour Android et iOS
2. ✅ Hardware-backed natif (StrongBox/Secure Enclave)
3. ✅ Simplicité maximale (pas de dérivation, pas de master seed)
4. ✅ Compatibilité backend garantie (BouncyCastle)
5. ✅ Sécurité équivalente à Ed25519
6. ✅ Standard industriel (NIST recommandé)

**Migration depuis Ed25519** :
- Backend : Ajouter support EC P-256 (déjà supporté par BouncyCastle)
- Mobile : Remplacer génération Ed25519 par EC P-256 native
- API : Accepter les deux formats pendant transition (si nécessaire)

