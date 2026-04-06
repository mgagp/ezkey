# Plan: `ezkey_dart` — Bibliothèque Dart pour le protocole cryptographique EZKey

**TL;DR** — Package Dart pur dans `ezkey-worktree3/ezkey_dart/`, reproduisant la chaîne crypto EZKey de `ezkey_mobile/` (référence React Native / Kotlin Android). Unit-testé avec le Crypto API Docker comme oracle de validation. Exige une évolution préalable du Crypto API (prompt de délégation inclus).

---

## Pré-requis : Évolution Crypto API — à exécuter en premier

Le Crypto API doit exposer non seulement les endpoints Ed25519 `POST /sign-ed25519` et
`POST /verify-ed25519`, mais aussi un oracle de payload canonique via
`POST /payload-helper`. Le plan Dart vise à valider le vrai contrat cryptographique EZKey, donc il
faut couvrir les primitives Ed25519 **et** la reconstruction exacte des payloads Pending,
Respond et RespondResult avec NFC. La logique existe déjà dans `ezkey-core` via
`SignatureService` et `AuthAttemptSignaturePayload` — il s'agit surtout de l'exposer.

Ce pré-requis inclut aussi l'ajustement de la collection Postman crypto et l'harmonisation ciblée
de la collection Postman auth attempts, afin que les flux oracle utilisent bien Ed25519 côté
intégration et le helper de payload au lieu de scripts dispersés.

---

## Protocole cryptographique EZKey — Référence (ezkey_mobile/)

### Algorithmes

| Rôle | Algorithme | Format clé | Format signature |
|---|---|---|---|
| Device sign (Android) | EC P-256 ECDSA-SHA256 | X.509 SPKI DER → Base64 std (~88 chars) | ASN.1 DER → Base64 std |
| Integration verify | Ed25519 | Raw 32B → Base64URL no-pad (~43 chars) | Raw 64B → Base64URL no-pad |

### Payloads pipe-séparés (NFC normalisés)

- **Pending** : `"<proofToken>|<true|false>|<contextTitle>|<contextMessage>"`
- **Respond** : `"<proofToken>|<true|false>"`
- **RespondResult** : `"<proofToken>|<authAttemptId>|<result>|<message>"`

### Flot Enrollment (Bind → Verify)

1. `GET /integration-keypair` → `integrationPublicKey` (Base64URL raw 32B Ed25519) stocké localement
2. Générer paire EC P-256 device (Android Keystore / Dart keygen)
3. Signer `enrollmentProofToken` (UTF-8) avec clé device → signature DER Base64 std
4. `POST /enrollments/verify` : `{ enrollmentId, devicePublicKey: Base64(SPKI), enrollmentProofTokenSigned: Base64(DER), challengeResponse }`

### Flot Auth (Pending → Respond)

**Pending :**
1. Signer timestamp nonce (`Date.now().toString()`) avec clé device
2. `POST /auth-attempts/pending` → reçoit `authAttemptProofTokenSignedByIntegration`
3. Vérifier signature Ed25519 sur payload pending (obligatoire, abort si invalide)

**Respond :**
1. Construire `respondPayload = "<proofToken>|<true|false>"`
2. Signer avec clé device → `authAttemptProofTokenSignedByDevice`
3. `POST /auth-attempts/respond` → reçoit `authAttemptProofTokenResultSignedByIntegration`
4. Vérifier signature Ed25519 sur payload respondResult (obligatoire)

### Format clé publique Integration (Ed25519)

```
SPKI prefix (12 bytes) : 30 2a 30 05 06 03 2b 65 70 03 21 00
+ raw 32 bytes         = 44 bytes X.509 SubjectPublicKeyInfo (pour JCA Java/Kotlin)
```
Côté Dart : passer directement les 32 bytes raw au package `cryptography`.

---

## Crypto API actuel (oracle de validation — port 9090)

| Endpoint | Disponible |
|---|---|
| `GET /api/v1/crypto/prooftoken` | ✅ |
| `GET /api/v1/crypto/keypair` (EC P-256) | ✅ |
| `GET /api/v1/crypto/integration-keypair` (Ed25519 keygen) | ✅ |
| `POST /api/v1/crypto/sign` (EC P-256 ECDSA-SHA256) | ✅ |
| `POST /api/v1/crypto/validate` (EC P-256) | ✅ |
| `POST /api/v1/crypto/sign-ed25519` | ✅ pré-requis Dart |
| `POST /api/v1/crypto/verify-ed25519` | ✅ pré-requis Dart |
| `POST /api/v1/crypto/payload-helper` | ✅ pré-requis Dart |

---

## Environnement Dart

- Flutter installé à `C:\Tools\flutter` — inclut Dart SDK 3.11.4
- `dart` CLI : `C:\Tools\flutter\bin\dart`
- SDK constraint à utiliser : `'>=3.7.0 <4.0.0'` (NFC natif dispo depuis 3.7)
- Architecture : **pure Dart package** (pas Flutter plugin) → compatible CLI, server, Flutter, web

---

## Stack de dépendances

```yaml
dependencies:
  pointycastle: ^4.0.0    # EC P-256, ECDSA, ASN.1 DER, low-S, secure random, HKDF
  convert: ^3.1.2         # Base64URL / Base64 std (Dart team — zéro risque)
  cryptography: ^2.9.0    # Ed25519 sign/verify (API native, pur Dart)
  http: ^1.0.0            # Tests d'intégration oracle Crypto API

dev_dependencies:
  lints: ^5.0.0
  test: ^1.24.0
```

---

## Phase 0 — Scaffolding

1. `cd c:\github\ezkey-worktree3`
2. `C:\Tools\flutter\bin\dart create --template=package ezkey_dart`
3. Écraser `pubspec.yaml` avec les deps ci-dessus, `sdk: '>=3.7.0 <4.0.0'`
4. Créer `analysis_options.yaml` avec `package:lints/recommended.yaml`
5. Créer `scripts/dart-test.bat` — wrapper Windows pour `C:\Tools\flutter\bin\dart test %*`
6. `dart pub get`

---

## Phase 1 — Primitives cryptographiques (`lib/src/`)

### `encoding.dart`

- `base64UrlEncode(Uint8List)` / `base64UrlDecode(String)` — via `convert`, flexible (strip padding)
- `base64StdEncode(Uint8List)` / `base64StdDecode(String)` — via `convert`
- `const ed25519SpkiPrefix` — 12 bytes : `[0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00]`
- `Uint8List wrapEd25519Raw32ToSpki(Uint8List raw32)` — préfixe + raw32 → 44B SPKI (pour interop JCA)
- `Uint8List flexibleBase64Decode(String input)` — tente Base64URL puis Base64 std (même logique que Kotlin `IntegrationKeyVerifier`)

### `ec_p256.dart`

- `EcP256KeyPair generateEcP256KeyPair()` — via `pointycastle` `ECKeyGeneratorParameters`
- `String ecPublicKeyToSpkiBase64(ECPublicKey)` — encode X.509 SPKI DER → Base64 std
- `ECPublicKey ecPublicKeyFromSpkiBase64(String)` — décode Base64 std SPKI
- `String signEcdsaSha256(ECPrivateKey key, String data)` — signe UTF-8 bytes, retourne Base64 std DER
- `bool verifyEcdsaSha256(ECPublicKey key, String data, String signatureBase64Std)` — vérifie DER Base64 std
- **Low-S normalization** obligatoire : si `s > n/2`, remplacer `s = n - s` (compatibilité Java `SHA256withECDSA`)

### `ed25519.dart`

- `Future<bool> verifyEd25519(String payload, String signatureBase64Url, String publicKeyBase64Url)` — via `cryptography` `Ed25519().verify()`
- `Future<String> signEd25519WithPkcs8(String data, String pkcs8Base64Std)` — signe avec clé PKCS#8 Base64 std, retourne Base64URL no-pad (pour tests d'intégration)
- `SimplePublicKey ed25519PublicKeyFromBase64Url(String base64Url)` — decode raw 32B via `flexibleBase64Decode`
- Signer et vérifier sur `utf8.encode(payload)`

### `secure_random.dart`

- `Uint8List generateSecureRandomBytes(int length)` — via `pointycastle` `FortunaRandom` seedé avec `math.Random.secure()`

### `payload.dart`

- Toutes les fonctions sont **pures, sans état**
- NFC via `String.normalize('NFC')` natif Dart 3.7+ — **zéro dépendance tierce**
- `String buildPendingPayload(String proofToken, bool challengeRequired, String? contextTitle, String? contextMessage)`
  - → `"${nfc(proofToken)}|${challengeRequired}|${nfc(contextTitle ?? '')}|${nfc(contextMessage ?? '')}"`
- `String buildRespondPayload(String proofToken, bool accepted)`
  - → `"${nfc(proofToken)}|${accepted}"`
- `String buildRespondResultPayload(String proofToken, String authAttemptId, String result, String? message)`
  - → `"${nfc(proofToken)}|${authAttemptId}|${result}|${nfc(message ?? '')}"`
- Helper privé `String _nfc(String s) => s.normalize('NFC')`

---

## Phase 2 — Façade protocole (`lib/src/ezkey_crypto.dart`)

Classe `EzKeyCrypto` stateless, méthodes statiques :

```dart
class EzKeyCrypto {
  static EcP256KeyPair generateDeviceKeyPair();
  static String exportDevicePublicKey(EcP256KeyPair keyPair);       // → Base64 SPKI std
  static String signWithDeviceKey(EcP256KeyPair keyPair, String data); // → Base64 DER std
  static Future<bool> verifyIntegrationSignature(
    String payload,
    String signatureBase64Url,
    String publicKeyBase64Url,
  );
}
```

---

## Phase 3 — Tests unitaires purs (`test/`)

*(Pas de docker requis — `dart test` suffit)*

### `encoding_test.dart`

- SPKI prefix 12B correct (vector fixe)
- `wrapEd25519Raw32ToSpki(raw32)` → 44B, premier octet `0x30`
- Base64URL encode/decode roundtrip, flexible decode accepte les deux formes

### `ec_p256_test.dart`

- `generateEcP256KeyPair()` → keygen valide, public key exportable SPKI
- `signEcdsaSha256` + `verifyEcdsaSha256` → roundtrip
- Low-S : signer 50 messages aléatoires, vérifier `s <= n/2` sur chaque signature DER extraite
- Signature modifiée (1 byte flip) → `verifyEcdsaSha256` retourne false

### `ed25519_test.dart`

- Vecteur fixe (clé/signature/payload hardcodés) → `verifyEd25519` retourne true
- Payload modifié d'un caractère → false
- `flexibleBase64Decode` accepte Base64URL (sans padding) et Base64 std (avec padding)

### `payload_test.dart`

- `buildPendingPayload("tok", true, "Title", "Msg")` → `"tok|true|Title|Msg"`
- `buildPendingPayload("tok", false, null, null)` → `"tok|false||"`
- NFC : chaîne NFD input → output NFC (vecteur Unicode fixe)
- `buildRespondPayload` et `buildRespondResultPayload` — vecteurs fixes

---

## Phase 4 — Tests d'intégration oracle (`--tags=integration`)

*(Requiert docker clean-start — `CRYPTO_API_URL=http://localhost:9090`)*

```bash
# Lancer les tests d'intégration
dart test --tags=integration
```

### `ec_p256_test.dart` (integ)

1. Générer paire EC P-256 locale (Dart)
2. Signer payload via Dart → `POST /validate` avec public key Dart → assert `valid: true`
3. Générer paire via `GET /keypair` → signer via `POST /sign` → vérifier en Dart → true

### `ed25519_test.dart` (integ) — *dépend du pré-requis Crypto API*

1. `GET /integration-keypair` → `{ privateKey, publicKey }`
2. `POST /payload-helper` avec type `pending` pour obtenir `"tok|true|Title|Msg"`
3. `POST /sign-ed25519` avec ce payload et `privateKey`
4. `verifyEd25519(payload, signature, publicKey)` → true
5. `POST /verify-ed25519` avec le même triplet comme oracle secondaire → `valid: true`
6. Replay avec payload modifié → false

### `enrollment_flow_test.dart` (integ)

1. `GET /integration-keypair` → `integrationPublicKey` Base64URL raw 32B
2. `flexibleBase64Decode(integrationPublicKey)` → 32B Uint8List ✓
3. Générer paire device Dart, exporter SPKI Base64 std
4. Signer `enrollmentProofToken` (UTF-8) avec clé device
5. `POST /validate` : public key SPKI, signature DER → `valid: true`

### `auth_flow_test.dart` (integ)

1. **Pending** : `POST /payload-helper` → `POST /sign-ed25519` → `verifyEd25519` → true
2. **Respond** : build `respondPayload` → signEcdsaSha256 en Dart → `POST /validate` → true
3. **RespondResult** : `POST /payload-helper` → `POST /sign-ed25519` → `verifyEd25519` → true

---

## Phase 5 — README (`ezkey_dart/README.md`)

Pragmatique : 3 sections maximum.

**Prérequis :** Flutter `C:\Tools\flutter` (inclut Dart 3.11.4) ; docker clean-start pour tests d'intégration.

**Tests unitaires (sans docker) :**
```bash
scripts\dart-test.bat
# ou directement :
C:\Tools\flutter\bin\dart test
```

**Tests d'intégration (avec docker) :**
```bash
# Démarrer le stack EZKey (depuis docker/)
# puis :
C:\Tools\flutter\bin\dart test --tags=integration
# Variable optionnelle : CRYPTO_API_URL=http://localhost:9090 (défaut)
```

**Structure :** `lib/src/` — primitives crypto ; `lib/ezkey_dart.dart` — API publique ; `test/` — tests unitaires et intégration.

---

## Fichiers à créer

```
c:\github\ezkey-worktree3\
├── ezkey_dart/
│   ├── pubspec.yaml
│   ├── analysis_options.yaml
│   ├── README.md
│   ├── lib/
│   │   ├── ezkey_dart.dart                ← barrel export public
│   │   └── src/
│   │       ├── encoding.dart
│   │       ├── ec_p256.dart
│   │       ├── ed25519.dart
│   │       ├── secure_random.dart
│   │       ├── payload.dart
│   │       └── ezkey_crypto.dart
│   └── test/
│       ├── encoding_test.dart
│       ├── ec_p256_test.dart
│       ├── ed25519_test.dart
│       ├── payload_test.dart
│       ├── enrollment_flow_test.dart
│       └── auth_flow_test.dart
└── scripts/
    └── dart-test.bat
```

---

## Vérification

1. `C:\Tools\flutter\bin\dart --version` → `Dart SDK version: 3.11.4`
2. `cd ezkey_dart && C:\Tools\flutter\bin\dart pub get` → zéro erreur de résolution
3. `C:\Tools\flutter\bin\dart analyze` → zéro warning
4. `C:\Tools\flutter\bin\dart test` → Phase 3 (tests purs) — tous passent sans docker
5. Docker clean-start → `C:\Tools\flutter\bin\dart test --tags=integration` → Phase 4 — tous passent
6. `scripts\dart-test.bat` → fonctionne depuis Windows CMD/PowerShell

---

## Décisions actées

| Décision | Choix | Raison |
|---|---|---|
| Type de package | Dart pur (pas Flutter plugin) | Compatible CLI, server, Flutter, web ; pas de native channels nécessaires |
| EC P-256 / ECDSA | `pointycastle ^4.0.0` | Seul package Dart couvrant ASN.1 DER, low-S, keygen P-256 |
| Ed25519 | `cryptography ^2.9.0` | Support natif Ed25519, API propre ; pointycastle ne l'a pas |
| Base64 | `convert ^3.1.2` (Dart team) | Zéro risque, Dart team, stable |
| NFC normalization | `String.normalize('NFC')` natif Dart 3.7+ | Zéro dépendance tierce |
| Emplacement | `ezkey-worktree3/ezkey_dart/` | Dans le monorepo pour faciliter l'itération avec le Crypto API |
| Scope exclu | Secure Enclave/Keystore, UI, iOS RSA legacy, OTP, publication pub.dev | Hors scope Phase 1 |

---

## Prompt de délégation — Évolution Crypto API et Postman pour ezkeyDart

**À soumettre dans une session agent séparée (mode Agent / implement) AVANT de démarrer la Phase 4.**

---

**Contexte :** EZKey est une plateforme MFA cryptographique open source (Spring Boot, multi-module Maven, workspace `c:\github\ezkey-worktree3`). Le module `ezkey-crypto-api` (port 9090) est un outil de debug/test sans auth qui expose des primitives REST.

**Objectif :** Faire évoluer `ezkey-crypto-api` pour supporter pleinement les validations du plan
`ezkey_dart`. Cela implique :
- exposer les primitives Ed25519 déjà présentes dans `ezkey-core` `SignatureService`
- exposer un helper de payload canonique basé sur `AuthAttemptSignaturePayload`
- ajuster la collection Postman crypto correspondante, puis harmoniser la collection Postman auth attempts concernée

**Format des clés (rappel) :**
- Clé privée : PKCS#8 DER encodé en Base64 standard — c'est le format retourné par `GET /integration-keypair`
- Clé publique : raw 32B encodés en Base64URL sans padding — c'est le format retourné par `GET /integration-keypair`
- Signature : raw 64B Ed25519 encodés en Base64URL sans padding

**Travail demandé :**
1. Ajouter `POST /api/v1/crypto/sign-ed25519` → `signatureService.signIntegrationPayload(...)`
2. Ajouter `POST /api/v1/crypto/verify-ed25519` → `signatureService.verifyIntegrationSignature(...)`
3. Ajouter `POST /api/v1/crypto/payload-helper` pour les types `pending`, `respond`, `respond-result`
4. Réutiliser `AuthAttemptSignaturePayload` pour reconstruire exactement les payloads canoniques avec NFC
5. Documenter ces endpoints avec `@Operation` Swagger, dans le même style que les endpoints existants
6. Étendre `CryptoControllerTest.java` avec happy paths et erreurs de validation pour Ed25519 et `payload-helper`
7. Mettre à jour `ezkey-crypto-api/AGENTS.md`
8. Mettre à jour `postman/collections/v2.1/EZ Key crypto.postman_collection.json`
9. Harmoniser `postman/collections/v2.1/EZ Key Auth Attempts auth.postman_collection.json` pour utiliser `verify-ed25519` et le helper de payload dans les flux de validation d'intégrité

**Conventions obligatoires (copilot-instructions.md) :** Google Java Format (2 espaces, 100 chars max), license header Ezkey sur chaque nouveau fichier Java, `@Valid` sur les DTOs de requête, `ResponseEntity<...>` sur tous les endpoints, Lombok interdit, constructor injection.

**Validation finale :**
```bash
# Depuis la racine du monorepo (scripts/build-local.cmd ou Git Bash)
mvn spotless:apply
mvn checkstyle:check
mvn clean install -DskipTests
```

**Commit :** `feat(crypto-api): add Ed25519 and canonical payload helper endpoints`

**Vérification manuelle post-déploiement (docker clean-start) :**
```bash
# Générer une paire Ed25519
curl -s http://localhost:9090/api/v1/crypto/integration-keypair | jq .

# Construire le payload canonique Pending
curl -s -X POST http://localhost:9090/api/v1/crypto/payload-helper \
  -H "Content-Type: application/json" \
  -d '{"type":"pending","proofToken":"hello","challengeRequired":true,"contextTitle":"Title","contextMessage":"Message"}' | jq .payload

# Signer
curl -s -X POST http://localhost:9090/api/v1/crypto/sign-ed25519 \
  -H "Content-Type: application/json" \
  -d '{"data":"hello|true|Title|Message","privateKey":"<PKCS8_BASE64_DU_KEYPAIR>"}' | jq .signature

# Vérifier
curl -s -X POST http://localhost:9090/api/v1/crypto/verify-ed25519 \
  -H "Content-Type: application/json" \
  -d '{"data":"hello|true|Title|Message","signature":"<SIG>","publicKey":"<PUB_BASE64URL>"}' | jq .valid
# → true
```
