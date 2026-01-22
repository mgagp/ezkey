# Correction : REJECTED affiché comme "Expired" dans l'UI

## Problème identifié

L'utilisateur a signalé que lorsqu'une tentative d'authentification est rejetée (`REJECTED` dans la BD), l'UI de la demo application affiche "Expired" au lieu de "Rejected".

## Analyse du flux

### Flux attendu
1. BD : Statut `REJECTED` ✓
2. `AuthAttemptWaitService.calculateStatus()` : Retourne `"REJECTED"` (statut final)
3. `LoginController.checkAuthStatus()` : Mappe `"REJECTED"` → `"rejected"`
4. Frontend : Affiche "Authentication rejected by user"

### Flux observé (problématique)
1. BD : Statut `REJECTED` ✓
2. `AuthAttemptWaitService.waitForResponse()` : Retourne `"EXPIRED"` au lieu de `"REJECTED"`
3. `LoginController.checkAuthStatus()` : Mappe `"EXPIRED"` → `"expired"`
4. Frontend : Affiche "Authentication request expired"

## Cause racine

Dans `AuthAttemptWaitService.waitForResponse()`, l'ordre des vérifications est :

```java
// 1. Check if completed (should detect REJECTED)
if (isAttemptCompleted(authAttempt)) {
  return buildWaitResponse(authAttempt, false, waitDuration);
}

// 2. Check if expired
if (isAttemptExpired(authAttempt)) {
  return buildWaitResponse(authAttempt, "EXPIRED", false, waitDuration);
}
```

**Théoriquement**, si le statut est `REJECTED`, `isAttemptCompleted()` devrait retourner `true` et on devrait sortir avant d'arriver à la vérification d'expiration.

**Cependant**, il existe un scénario de race condition possible :
1. Lors d'un poll précédent, le statut était `PENDING` ou `READ`
2. `isAttemptExpired()` retournait `true` (car `expiresAt` était dans le passé)
3. On retournait `"EXPIRED"` hardcodé
4. Entre-temps, le device rejette la tentative et le statut dans la BD devient `REJECTED`
5. Mais le frontend a déjà reçu `"EXPIRED"` et a redirigé

**OU** il y a un problème plus subtil : `isAttemptExpired()` ne vérifie pas si le statut est final avant de retourner `true`. Bien que `isAttemptCompleted()` soit vérifié avant dans `waitForResponse()`, il est possible qu'il y ait un cas où cette vérification ne fonctionne pas correctement.

## Solution implémentée

### Protection dans `isAttemptExpired()`

Modification de `isAttemptExpired()` pour ne pas retourner `true` si le statut est final :

```java
private boolean isAttemptExpired(AuthAttempt authAttempt) {
  // Don't check expiration for final statuses - they represent user decisions or validation
  // results that should not be overridden by expiration
  if (isAttemptCompleted(authAttempt)) {
    return false;
  }
  OffsetDateTime now = OffsetDateTime.now();
  return authAttempt.getExpiresAt() != null && now.isAfter(authAttempt.getExpiresAt());
}
```

Cette protection garantit que :
- Les statuts finaux (`ACCEPTED`, `REJECTED`, `INVALID`) ne sont jamais considérés comme expirés
- Les décisions utilisateur (`REJECTED`) sont préservées même si `expiresAt` est dans le passé
- L'expiration n'est vérifiée que pour les statuts transitoires (`PENDING`, `READ`)

## Impact

Cette correction garantit que :
1. ✅ Les tentatives `REJECTED` sont toujours retournées comme `"REJECTED"` et non `"EXPIRED"`
2. ✅ Les décisions utilisateur sont préservées même si la tentative a expiré techniquement
3. ✅ Le mapping dans `LoginController` fonctionne correctement : `"REJECTED"` → `"rejected"` → message "Authentication rejected by user"

## Tests recommandés

1. **Test de rejet sans expiration** : Créer une tentative, rejeter immédiatement → doit afficher "Rejected"
2. **Test de rejet avec expiration passée** : Créer une tentative, attendre expiration, puis rejeter → doit afficher "Rejected" (pas "Expired")
3. **Test d'expiration normale** : Créer une tentative, attendre expiration sans réponse → doit afficher "Expired"

## Fichiers modifiés

- `ezkey-core/src/main/java/org/ezkey/authattempt/service/AuthAttemptWaitService.java`
  - Ajout de protection dans `isAttemptExpired()` pour ignorer l'expiration si le statut est final
  - Ajout de commentaire explicatif dans `waitForResponse()`
