## Plan: QR Code Content Enhancement with Auth URL

**TL;DR** — Enrichir le contenu des QR codes pour inclure l'URL publique de l'auth-api. Migrer du format pipe vers JSON. Ajouter `ezkey.qr.auth-base-url` dans l'admin-api. Service centralisé pour éliminer la duplication. Impact tests : mineur. Risque ASCII : modéré mais acceptable (~102 colonnes, OK pour terminaux 120+).

---

### Évaluation de risque : capacité ASCII QR

Le proof token fait ~80 caractères (`base64url(32b).timestamp.base64url(16b)` — voir [SignatureService.java](ezkey-core/src/main/java/org/ezkey/signature/SignatureService.java#L389)).

| Format | Taille estimée | Version QR | Modules | Largeur ASCII (2 chars/module + marge 1) |
|--------|---------------|------------|---------|------------------------------------------|
| Pipe actuel `4\|token` | ~85 chars | Version 5 | 37×37 | 78 colonnes |
| JSON avec authUrl | ~180 chars | Version 8 | 49×49 | 102 colonnes |

**Verdict :** Le format pipe actuel tient déjà serré dans un terminal de 80 colonnes (78 cols). Avec JSON + URL, on passe à ~102 colonnes, ce qui déborde sur un terminal 80-col mais reste confortable sur un terminal 120+ (standard de facto). **Le risque est modéré et acceptable** — la plupart des environnements où le bootstrap s'exécute (Docker logs, terminaux modernes) supportent 120+ colonnes. Option de mitigation : ajouter un message d'avertissement dans le log si la largeur dépasse un seuil, ou documenter la largeur requise.

### Analyse URL Shortener

**Verdict : Non.** Raisons :

1. **Sécurité** — Ezkey est un système MFA. Un shortener tiers compromis pourrait rediriger vers un serveur malicieux (MITM trivial). Si le shortener tombe, tous les QR codes deviennent inutilisables.
2. **Self-hosted** — Dépendre d'un shortener externe contredit la philosophie self-hosted. Héberger son propre shortener ajoute de la complexité pour un gain marginal.
3. **Obfuscation** — Cache l'URL réelle, empêchant la vérification visuelle du serveur cible. Contraire à la transparence attendue d'un outil de sécurité.
4. **Fuite de métadonnées** — Chaque scan passe par le shortener, exposant des métadonnées à un tiers.
5. **Pas nécessaire** — Une URL auth-api typique (~35-50 chars) + payload JSON (~180 chars total) reste à ~42% de la capacité QR Version 9 (432 chars max). Marge confortable.

---

## Steps

### 1. Créer `QrCodeProperties` (`@ConfigurationProperties`)

Créer [QrCodeProperties.java](ezkey-admin-api/src/main/java/org/ezkey/admin/config/QrCodeProperties.java) avec préfixe `ezkey.qr` :
- Champ `authBaseUrl` (String, nullable) — URL publique de l'auth-api
- Validation : si défini, doit être une URL valide HTTP/HTTPS
- S'inspirer de [BootstrapExportProperties.java](ezkey-admin-api/src/main/java/org/ezkey/admin/config/BootstrapExportProperties.java)

### 2. Créer `QrCodePayloadService` (service centralisé)

Créer [QrCodePayloadService.java](ezkey-admin-api/src/main/java/org/ezkey/admin/service/QrCodePayloadService.java) :
- Injecte `QrCodeProperties`
- Méthode `String composePayload(Integer enrollmentId, String enrollmentProofToken)` → JSON
- Si `authBaseUrl` non configuré, omet `authUrl` du JSON (rétrocompatibilité)
- Utiliser Jackson `ObjectMapper` pour la sérialisation

### 3. Refactorer les 3 points de génération QR

**a)** [EnrollmentController.java](ezkey-admin-api/src/main/java/org/ezkey/admin/controller/EnrollmentController.java#L543) — remplacer la concaténation pipe par `QrCodePayloadService.composePayload()`

**b)** [AdminProvisioningController.java](ezkey-admin-api/src/main/java/org/ezkey/admin/controller/AdminProvisioningController.java#L396) — même refactoring

**c)** [AdminBootstrapService.java](ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminBootstrapService.java#L439) — même refactoring pour le QR ASCII

### 4. Mettre à jour `QrCodeAsciiRenderer`

Dans [QrCodeAsciiRenderer.java](ezkey-admin-api/src/main/java/org/ezkey/admin/service/QrCodeAsciiRenderer.java#L39) :
- Augmenter `DEFAULT_SIZE` de 33 à 53 pour accommoder le JSON plus volumineux
- Ajouter un log warning si le contenu dépasse ~150 caractères (alerte largeur terminale)

### 5. Ajouter la propriété dans les fichiers de config

**a)** [application.properties](ezkey-admin-api/config/application.properties) — ajouter `ezkey.qr.auth-base-url=` (commenté, avec explication sur l'URL publique)

**b)** [application-docker.properties](ezkey-admin-api/config/application-docker.properties) — commentaire indiquant que cette valeur doit être l'URL externe, pas l'URL interne Docker

### 6. Mettre à jour `BootstrapCredentialsFileExporter`

Dans [BootstrapCredentialsFileExporter.java](ezkey-admin-api/src/main/java/org/ezkey/admin/service/BootstrapCredentialsFileExporter.java) :
- Injecter `QrCodeProperties`
- Ajouter `authUrl` dans le JSON exporté si configuré

### 7. Mettre à jour les tests unitaires

**a)** [AdminProvisioningControllerTest.java](ezkey-admin-api/src/test/java/org/ezkey/admin/controller/AdminProvisioningControllerTest.java) — mettre à jour le constructeur pour injecter `QrCodePayloadService` (mock)

**b)** [EnrollmentControllerAuditTest.java](ezkey-admin-api/src/test/java/org/ezkey/admin/controller/EnrollmentControllerAuditTest.java) — même mise à jour

**c)** Créer **QrCodePayloadServiceTest.java** — tests : avec/sans `authBaseUrl`, format JSON valide, rétrocompatibilité

**d)** Optionnel : créer **QrCodeAsciiRendererTest.java**

### 8. Vérifier les tests fonctionnels (`ezkey-tests`)

- Aucun test fonctionnel n'appelle les endpoints QR (confirmé) → **impact nul**
- Vérifier que [BootstrapCredentialsExtractor](ezkey-tests/src/test/java/org/ezkey/tests/util/BootstrapCredentialsExtractor.java) ignore gracieusement le nouveau champ `authUrl`

### 9. Mettre à jour la documentation

**a)** [docs/ENDPOINT.md](docs/ENDPOINT.md#L656-L685) — nouveau format JSON, description du champ `authUrl`, exemple

**b)** [docs/testing/MULTI_TENANT_OBSERVATIONS.md](docs/testing/MULTI_TENANT_OBSERVATIONS.md#L311) — mettre à jour les références au format pipe

**c)** Javadoc de [QrCodeGeneratorService.java](ezkey-admin-api/src/main/java/org/ezkey/admin/service/QrCodeGeneratorService.java#L32) — nouveau format

### 10. Mettre à jour les collections Postman

**a)** [EZ Key Enrollments admin.postman_collection.json](postman/collections/v2.1/EZ%20Key%20Enrollments%20admin.postman_collection.json) — description du nouveau format JSON

**b)** [EZ Key Admin Provisioning admin.postman_collection.json](postman/collections/v2.1/EZ%20Key%20Admin%20Provisioning%20admin.postman_collection.json) — même mise à jour

### 11. Référence : impact mobile (COMPLÉTÉ)

Le parser [parseQrPayload](ezkey_mobile/app/screens/EnrollmentWizard/EnrollmentWizardScreen.tsx#L730) supporte le JSON et extrait `authUrl`. Implémentation réalisée :
- `authUrl` extrait du payload QR et validé (HTTPS enforced, regex-based pour compatibilité Hermes)
- `baseURL` Axios overridé par requête via paramètre optionnel sur les 4 fonctions API (`bind`, `verify`, `pending`, `respond`)
- `authUrl` persisté par enrollment dans `StoredEnrollment` (via `EnrollmentSummary`)
- Fallback transparent vers `env.apiBaseUrl` quand `authUrl` absent (rétrocompatibilité pipe et anciens enrollments)
- URL affichée à l'utilisateur dans l'écran de confirmation (TOFU) et dans l'écran de détail enrollment
- Utilitaire de validation : [urlValidation.ts](ezkey_mobile/app/utils/urlValidation.ts)
- Tests unitaires ajoutés : URL validation, API baseURL override

---

## Phase future : Deep Link et URI Scheme `ezkey://`

### Contexte

Actuellement, le QR code contient du JSON brut. Lorsqu'un utilisateur scanne le QR code avec la caméra native du téléphone (hors de l'app Ezkey), le téléphone affiche le JSON mais ne sait pas quelle application ouvrir ("application inconnue"). Cela est dû à l'absence d'un URI scheme reconnu.

### Standard de l'industrie

Les applications d'authentification utilisent des URI schemes dédiés pour le routing automatique depuis la caméra native :
- **TOTP/HOTP** : `otpauth://totp/Issuer:user?secret=BASE32&issuer=Issuer` (RFC 6238)
- **Duo Mobile** : `duo://activation?...`
- **Microsoft Authenticator** : `mfauth://...`
- **WireGuard** : `wireguard://...`

### Proposition pour Ezkey

Adopter un URI scheme `ezkey://` pour les QR codes d'enrollment :

```
ezkey://enroll?enrollmentId=4&enrollmentProofToken=V8JCIil-...&authUrl=https://ezkey.acme.com
```

### Bénéfices

- La caméra native du téléphone ouvre directement l'app Ezkey au scan
- Deep link routing vers le wizard d'enrollment avec les paramètres pré-remplis
- Cohérence avec les standards de l'industrie (otpauth, duo, mfauth)
- UX améliorée : l'utilisateur n'a plus besoin d'ouvrir l'app puis de scanner manuellement

### Implications techniques

**Backend :**
- `QrCodePayloadService` doit générer `ezkey://enroll?...` au lieu de JSON brut
- Les paramètres doivent être URL-encoded

**Android (`AndroidManifest.xml`) :**
- Ajouter un intent filter pour le scheme `ezkey://` dans l'activité principale
- `<data android:scheme="ezkey" android:host="enroll" />`

**iOS (`Info.plist`) :**
- Déclarer le URL type `ezkey` dans `CFBundleURLTypes`

**Mobile (React Navigation) :**
- Configurer le linking dans React Navigation pour intercepter `ezkey://enroll?...`
- Router vers `EnrollmentWizardScreen` avec les paramètres extraits du deep link
- Le parser `parseQrPayload` doit supporter le format URI en plus de JSON et pipe

**Rétrocompatibilité :**
- Le scanner in-app doit continuer à supporter JSON et pipe (anciens QR codes)
- Le deep link est un ajout, pas un remplacement

### Risques et considérations

- Les custom URI schemes ne sont pas uniques : une autre app pourrait enregistrer `ezkey://`
- Alternative plus sécurisée : Universal Links (iOS) / App Links (Android) via `.well-known/assetlinks.json`, mais nécessite un domaine vérifié et est plus complexe à configurer en self-hosted
- Pour un projet open-source self-hosted, le custom URI scheme est le choix pragmatique

---

## Verification

1. `mvn clean verify` — compilation et tests
2. `mvn checkstyle:check` — conformité style
3. Test manuel avec `ezkey.qr.auth-base-url=https://test.example.com` → QR contient JSON avec `authUrl`
4. Test manuel sans la propriété → QR contient JSON sans `authUrl`
5. Bootstrap : QR ASCII lisible et scannable dans les logs
6. `bootstrap-credentials.json` contient `authUrl`

---

## Decisions

- **JSON over pipe** : extensible, déjà supporté par le parser mobile
- **auth-api URL seulement** : seul service invoqué par le mobile
- **Propriété optionnelle** : rétrocompatibilité si non configuré
- **Pas de URL shortener** : risque sécurité, dépendance externe, obfuscation — contraire aux valeurs du projet
- **OpenAPI specs** : hors scope, gérées manuellement via `scripts/update-spec`
- **Service centralisé** : élimine la duplication en 3 endroits
- **JSON brut (phase actuelle)** : suffisant pour le scanner in-app, deep link `ezkey://` planifié en phase future
- **Regex over URL constructor (mobile)** : le runtime Hermes de React Native a un support incomplet de `new URL()`, regex garantit la compatibilité
