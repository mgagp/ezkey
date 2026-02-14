# Analyse : Confusion de statut lors d'un reject avec challenge requis

## Date
2026-01-20

## Problème identifié

Lorsqu'une demande d'authentification est faite **avec challenge** et que l'utilisateur fait un **reject** dans le demo-device, le statut retourné et stocké en BD est **"INVALID"** au lieu de **"REJECTED"**.

**Comportement observé :**
- ✅ Sans challenge + reject → statut **"REJECTED"** (correct)
- ❌ Avec challenge + reject → statut **"INVALID"** (incorrect, devrait être "REJECTED")

## Analyse du code

### Flux actuel dans `AuthAttemptRespondService.respond()`

1. **Ligne 109-110** : Valide et récupère l'auth attempt
2. **Ligne 112-113** : Valide l'enrollment
3. **Ligne 115-116** : Valide la signature du device
4. **Ligne 118-119** : **Valide le challenge** (si requis) ← **PROBLÈME ICI**
5. **Ligne 121-122** : Met à jour le statut (ACCEPTED ou REJECTED)

### Code problématique : `validateChallenge()` (lignes 245-272)

```java
private void validateChallenge(
    AuthAttemptRespondRequest request, AuthAttempt authAttempt, Enrollment enrollment) {
  boolean challengeRequired =
      Boolean.TRUE.equals(enrollment.getAuthAttemptChallengeRequired())
          || authAttempt.getAuthAttemptChallenge() != null;
  if (challengeRequired) {
    if (request.getAuthAttemptChallengeResponse() == null
        || !request.getAuthAttemptChallengeResponse()
            .equals(authAttempt.getAuthAttemptChallenge())) {
      // ❌ PROBLÈME : Marque comme INVALID même si c'est un reject
      authAttemptTxHelper.markAsInvalid(authAttempt.getAuthAttemptId());
      throw new IllegalArgumentException("Challenge value mismatch");
    }
  }
}
```

### Scénario de bug

**Quand l'utilisateur fait un reject avec challenge requis :**

1. L'utilisateur clique sur "Deny" dans le demo-device
2. Le demo-device envoie :
   - `authAttemptAccepted = false` (reject)
   - `authAttemptChallengeResponse = null` (pas fourni car reject)
3. Le backend exécute `validateChallenge()` **AVANT** de vérifier si c'est un reject
4. Comme `challengeResponse` est `null` et que le challenge est requis, la validation échoue
5. L'auth attempt est marquée comme **INVALID** (ligne 259)
6. Une exception est lancée (ligne 266)
7. `updateAttemptStatus()` n'est **jamais appelé** (ligne 122)
8. Le statut reste **INVALID** au lieu de **REJECTED**

### Code de `updateAttemptStatus()` (lignes 283-291)

```java
private void updateAttemptStatus(AuthAttempt authAttempt, AuthAttemptRespondRequest request) {
  if (Boolean.TRUE.equals(request.getAuthAttemptAccepted())) {
    authAttempt.setAuthAttemptStatus(AuthAttemptStatus.ACCEPTED);
  } else {
    authAttempt.setAuthAttemptStatus(AuthAttemptStatus.REJECTED); // ← Jamais atteint si challenge invalide
  }
  authAttemptRepository.save(authAttempt);
}
```

## Cause racine

**L'ordre des validations est incorrect :**

La validation du challenge se fait **AVANT** de vérifier si l'utilisateur a accepté ou rejeté. Si c'est un reject, on ne devrait **pas** valider le challenge - on devrait simplement marquer comme REJECTED.

## Impact

1. **Statut incorrect en BD** : Les rejects avec challenge sont marqués comme INVALID au lieu de REJECTED
2. **Métriques incorrectes** : Les statistiques de sécurité sont faussées (trop d'INVALID, pas assez de REJECTED)
3. **Expérience utilisateur** : L'application demo affiche "Authentication invalid" au lieu de "Authentication rejected"
4. **Audit trail** : Les logs d'audit ne reflètent pas correctement l'intention de l'utilisateur

## Solution proposée

**Modifier `validateChallenge()` pour skip la validation si c'est un reject :**

```java
private void validateChallenge(
    AuthAttemptRespondRequest request, AuthAttempt authAttempt, Enrollment enrollment) {
  // Skip challenge validation if user rejected - no need to validate challenge for rejects
  if (Boolean.FALSE.equals(request.getAuthAttemptAccepted())) {
    logger.debug("Skipping challenge validation for rejected auth attempt: {}", 
        authAttempt.getAuthAttemptId());
    return;
  }
  
  // Validate challenge only for accepted attempts
  boolean challengeRequired =
      Boolean.TRUE.equals(enrollment.getAuthAttemptChallengeRequired())
          || authAttempt.getAuthAttemptChallenge() != null;
  if (challengeRequired) {
    if (request.getAuthAttemptChallengeResponse() == null
        || !request.getAuthAttemptChallengeResponse()
            .equals(authAttempt.getAuthAttemptChallenge())) {
      authAttemptTxHelper.markAsInvalid(authAttempt.getAuthAttemptId());
      throw new IllegalArgumentException("Challenge value mismatch");
    }
  }
}
```

**Justification de sécurité :**
- La signature du device est déjà validée (ligne 115-116) avant la validation du challenge
- Si la signature est valide et que `authAttemptAccepted = false`, on peut faire confiance que c'est vraiment l'utilisateur qui rejette
- Il n'y a pas de risque de sécurité à skip la validation du challenge pour un reject

## Fichiers à modifier

1. `ezkey-core/src/main/java/org/ezkey/authattempt/service/AuthAttemptRespondService.java`
   - Modifier `validateChallenge()` pour skip la validation si `authAttemptAccepted = false`

## Tests à effectuer

1. ✅ Test : Reject **sans** challenge → doit retourner **REJECTED**
2. ✅ Test : Reject **avec** challenge (challenge fourni) → doit retourner **REJECTED**
3. ✅ Test : Reject **avec** challenge (challenge **non** fourni) → doit retourner **REJECTED** (actuellement retourne INVALID)
4. ✅ Test : Accept **avec** challenge (challenge correct) → doit retourner **ACCEPTED**
5. ✅ Test : Accept **avec** challenge (challenge incorrect) → doit retourner **INVALID**
6. ✅ Test : Accept **avec** challenge (challenge non fourni) → doit retourner **INVALID**

## Conclusion

Le problème est confirmé : la validation du challenge se fait avant de vérifier si c'est un reject, ce qui cause une confusion de statut. La solution est de skip la validation du challenge si l'utilisateur a rejeté l'authentification.
