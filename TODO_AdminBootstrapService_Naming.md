# TODO: AdminBootstrapService Naming Cleanup

## Problem

Le nommage dans `AdminBootstrapService.java` est error-prone et a causé des problèmes de contexte lors de l'implémentation de `TenantAdminTestHelper`.

## Issues identifiés

### 1. Confusion `bindProofToken` vs `enrollmentProofToken`
   - La réponse de l'API bind contient un champ `enrollmentProofToken`
   - Le code utilise une variable locale `bindProofToken` qui récupère `enrollmentProofToken`
   - Cette divergence entre le nom de la variable et le nom du champ API crée de la confusion

   Ligne 809 dans AdminBootstrapService:
   ```java
   String bindProofToken = response.jsonPath().getString("enrollmentProofToken");
   ```

### 2. Hallucinations et inventions d'attributs lors du développement
   - Malgré la disponibilité de la spécification OpenAPI, plusieurs champs ont été inventés/hallucinés:
     - `bindProofToken` au lieu de `enrollmentProofToken` dans la réponse bind
     - `deviceSignature` au lieu de `enrollmentProofTokenSigned` dans verify request
     - `enrollmentChallenge` au lieu de `challengeResponse` dans verify request
     - `bindProofToken` au lieu de passer directement l'`enrollmentProofToken` signé
   - Ces erreurs ont nécessité plusieurs cycles de correction et debug
   - Temps perdu: ~30-45 minutes de debugging pour identifier les bons noms de champs

### 3. **CRITIQUE: Workflow d'authentification complètement halluciné**
   - **URLs inventées**: `/auth/attempts/{id}/pending` et `/auth/attempts/{id}/respond` n'existent PAS
   - **Vraies URLs**: `/auth-attempts/pending` et `/auth-attempts/respond` (sans ID dans l'URL)
   - **Champs inventés** dans pending: `authAttemptProofToken` direct (faux)
   - **Vrais champs** dans pending request:
     - `enrollmentId`
     - `enrollmentProofToken`
     - `deviceProofToken` (généré via Crypto API)
     - `deviceProofTokenSigned`
   - **Champs inventés** dans respond: `deviceSignature`
   - **Vrais champs** dans respond request:
     - `authAttemptId`
     - `authAttemptAccepted` (boolean)
     - `authAttemptProofTokenSignedByDevice` (pas `deviceSignature`)
     - `authAttemptChallengeResponse` (optionnel, si challenge demandé)
   - **Impact**: Le test ne faisait JAMAIS les appels `/auth-attempts/pending` et `/auth-attempts/respond`, donc le passwordless-wait attendait indéfiniment (120s timeout) une réponse qui ne viendrait jamais
   - **Root cause du gel**: Workflow d'auth incomplet → Admin API attend Auth API respond → timeout après 2min → credentials expirés
   - Temps perdu total: ~2 heures de debugging et corrections itératives

### 3. Impact sur la réutilisabilité
   - Lors de la création de `TenantAdminTestHelper`, cette confusion a généré:
     - Tentative d'extraction du mauvais champ (`bindProofToken` au lieu de `enrollmentProofToken`)
     - Envoi de requêtes avec des valeurs null
     - Perte de temps en debugging

## Solution proposée: Utiliser le SDK Java comme référence

### Contexte
La spécification OpenAPI seule n'a pas suffi à éviter les hallucinations. Le SDK Java généré doit devenir **la référence canonique** pour les tests fonctionnels et l'intégration.

### Bénéfices doubles

#### 1. Usage interne (tests fonctionnels)
- **Convergence rapide**: Typage fort, autocomplete, pas d'invention de champs
- **Moins d'erreurs**: Les DTOs du SDK sont générés directement depuis l'OpenAPI
- **Maintenance facilitée**: Un changement d'API met à jour le SDK, qui casse la compilation des tests
- **Exemple concret**: Au lieu d'inventer les champs pour verify request, importer `EnrollmentVerifyRequestDto`

#### 2. Expérience développeur (intégration externe)
- **Adoption facilitée**: Les développeurs utilisent les mêmes objets que nos tests
- **Documentation vivante**: Le SDK devient la doc de référence
- **Confiance**: Si nos tests utilisent le SDK, les intégrateurs savent que ça fonctionne

### Actions à prendre

1. [ ] **Générer/publier SDK Java pour ezkey-auth-api et ezkey-admin-api**
   - Vérifier que les DTOs sont bien générés depuis les specs OpenAPI
   - Publier sur Maven Central ou repository interne
   - Documenter l'usage du SDK dans README

2. [ ] **Refactorer tests fonctionnels pour utiliser le SDK**
   - Phase 1: `TenantAdminTestHelper` et `AdminBootstrapService`
   - Phase 2: Tous les helpers et factories dans `ezkey-tests`
   - Exemple: Remplacer `Map<String, Object> verifyRequest` par `EnrollmentVerifyRequestDto`

3. [ ] **Créer guide d'intégration avec SDK**
   - Exemples end-to-end dans docs/
   - Démontrer enrollment, login, verify avec le SDK
   - Templates de code pour intégrations courantes

4. [ ] **CI: Validation SDK vs OpenAPI**
   - Ajouter step CI qui génère SDK et vérifie cohérence avec spec OpenAPI
   - Bloquer merge si SDK diverge de l'API

### Migration progressive

```java
// AVANT (error-prone)
Map<String, Object> verifyRequest = new HashMap<>();
verifyRequest.put("enrollmentId", enrollmentId);
verifyRequest.put("challengeResponse", enrollmentChallenge); // Confusion possible
verifyRequest.put("devicePublicKey", keyPair.publicKey());
verifyRequest.put("enrollmentProofTokenSigned", bindSignature);

// APRÈS (avec SDK)
import org.ezkey.sdk.auth.model.EnrollmentVerifyRequestDto;

EnrollmentVerifyRequestDto verifyRequest = new EnrollmentVerifyRequestDto()
    .enrollmentId(enrollmentId)
    .challengeResponse(enrollmentChallenge)
    .devicePublicKey(keyPair.publicKey())
    .enrollmentProofTokenSigned(bindSignature);
```

## Recommandations immédiates

### Option A: Aligner sur les noms de l'API (préféré pour court terme)
```java
// Au lieu de:
String bindProofToken = response.jsonPath().getString("enrollmentProofToken");

// Utiliser:
String enrollmentProofToken = response.jsonPath().getString("enrollmentProofToken");
```

### Option B: Documenter explicitement
Si le renommage `bindProofToken` a une raison sémantique valide, ajouter un commentaire:
```java
// Note: The API response field is "enrollmentProofToken" but we call it bindProofToken
// locally because it represents the token that proves the bind operation succeeded
String bindProofToken = response.jsonPath().getString("enrollmentProofToken");
```

## Problème de gel (freezing) lors de l'exécution des tests

### Symptôme observé
Les tests gèlent après `"Enrollment verified successfully"` au niveau de la méthode `createTokenWithDeviceCredentials()`, plus précisément lors de l'appel à `/admin/auth/passwordless-wait`.

### Logs observés
```
08:01:56.748 [main] INFO org.ezkey.tests.util.TenantAdminTestHelper -- Enrollment verified successfully
[... puis plus rien, gel complet ...]
```

### Hypothèses à investiguer

1. **✅ CONFIRMÉ: Endpoint `/admin/auth/passwordless-wait` attend indéfiniment**
   - Délai observé: **2 minutes** entre enrollment verify (08:01:56) et login attempt (08:03:56)
   - L'endpoint timeout probablement après 2 minutes (120 secondes)
   - Pendant ce temps, le test est complètement bloqué
   - Résultat: "Authentication expired" car credentials ont un TTL < 2 minutes

2. **✅ IDENTIFIÉ: Champ manquant dans wait request**
   - TenantAdminTestHelper ne passait pas `challengeCode` dans le wait request
   - AdminBootstrapService passe `challengeCode` (ligne 1061)
   - Sans ce champ, le wait timeout et attend 2 minutes avant d'échouer
   - **FIX APPLIQUÉ**: Extraction de `challengeCode` depuis login response + ajout au wait request

3. **Différence avec AdminBootstrapService**
   - AdminBootstrapService fonctionne car il extrait et passe le `challengeCode`
   - TenantAdminTestHelper initialement ne le faisait pas
   - Même avec `challengeRequested: false`, l'API retourne un challengeCode

### Actions debug

1. [✅] ~~Ajouter timeout explicite sur `/admin/auth/passwordless-wait` (ex: 10s max)~~ → **TODO: À faire**
2. [✅] Ajouter logs avant/après l'appel pour confirmer que c'est bien là que ça gèle
3. [✅] Comparer avec `AdminBootstrapService.waitForToken()` → **Identifié: challengeCode manquant**
4. [✅] Extraire `challengeCode` de la réponse login et l'inclure dans wait request
5. [ ] **RESTANT**: Ajouter timeout RestAssured sur passwordless-wait pour fail-fast (10-15s max)
6. [ ] **RESTANT**: Ajouter retry logic si authentication expires (credentials TTL court)

## Contexte

- Date découverte: 2026-01-12
- Impacté par: Implémentation TenantAdminTestHelper pour tests multi-tenant
- Symptômes:
  - Variables null passées au Crypto API, erreurs 400 "data cannot be blank"
  - Mauvais noms de champs dans requêtes (deviceSignature, enrollmentChallenge, bindProofToken)
  - Gel des tests au niveau de passwordless-wait
- Temps perdu: ~45-60 minutes de debugging pour noms de champs + investigation gel en cours
