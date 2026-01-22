# Analyse des Mappings de Statuts d'Authentification - Demo Application

## Vue d'ensemble

Cette analyse examine le flux complet de mapping des statuts d'authentification depuis l'API WAIT (`ezkey-core`) jusqu'à l'affichage dans la demo application (`demo-app-acme`).

---

## 1. Source : Statuts possibles dans `ezkey-core`

### 1.1 Enum `AuthAttemptStatus` (ezkey-core)

Les statuts possibles définis dans le domaine :

| Statut | Description | Type |
|--------|-------------|------|
| `PENDING` | Tentative créée, en attente de réclamation par le device | Transitoire |
| `READ` | Device a réclamé la tentative et la traite | Transitoire |
| `INVALID` | Validation cryptographique échouée (signature invalide) | Final |
| `REJECTED` | Utilisateur a explicitement refusé la demande | Final |
| `ACCEPTED` | Utilisateur a approuvé la demande | Final |
| `EXPIRED` | Tentative expirée (timeout ou supersession) | Final |

### 1.2 Calcul du statut dans `AuthAttemptWaitService.calculateStatus()`

**Logique de priorité :**
1. **Statuts finaux** (`ACCEPTED`, `REJECTED`, `INVALID`) → retournés directement
2. **Statuts transitoires** (`PENDING`, `READ`) → vérification d'expiration
   - Si expiré → `EXPIRED`
   - Sinon → retourne le statut actuel

**Cas spéciaux :**
- **Supersession** : Si une tentative plus récente existe pour le même enrollment → retourne `EXPIRED` (hardcodé)

---

## 2. Mapping : `AuthAttemptWaitService` → DTO

### 2.1 Domain Object `AuthAttemptWaitResponse` (ezkey-core)

```java
- status: String (PENDING, READ, INVALID, REJECTED, ACCEPTED, EXPIRED)
- completed: Boolean (true si ACCEPTED/REJECTED/INVALID)
- timeoutReached: Boolean
- waitDuration: Integer
- completedAt: OffsetDateTime
```

### 2.2 DTO `AuthAttemptWaitResponse` (demo-app-acme)

```java
record AuthAttemptWaitResponse(
    String status,           // Mappé directement depuis le domain object
    Boolean completed,       // Mappé directement
    Boolean timeoutReached  // Mappé directement
)
```

**Mapping :** Direct 1:1 depuis le domain object vers le DTO (via Jackson).

---

## 3. Mapping : DTO → `LoginController.checkAuthStatus()`

### 3.1 Réception du statut

Le `LoginController` reçoit `waitResponse.status()` qui peut être :
- `"ACCEPTED"`
- `"REJECTED"`
- `"EXPIRED"`
- `"INVALID"`
- `"PENDING"`
- `"READ"`

### 3.2 Transformation en `AuthStatusResponse`

| Statut API WAIT | Statut `AuthStatusResponse` | `redirectUrl` | Message | Action Session |
|-----------------|----------------------------|---------------|---------|----------------|
| `ACCEPTED` | `"accepted"` | `"/dashboard"` | "Authentication successful" | Crée `AuthenticatedUser`, nettoie attributs pending |
| `REJECTED` | `"rejected"` | `"/login?error=rejected"` | "Authentication rejected by user" | Nettoie attributs pending |
| `EXPIRED` | `"expired"` | `"/login?error=expired"` | "Authentication request expired" | Nettoie attributs pending |
| `INVALID` | `"error"` | `"/login?error=invalid"` | "Authentication invalid" | Nettoie attributs pending |
| `PENDING` / `READ` | `"pending"` | `null` | "Waiting for device approval..." | **Conserve** attributs pending |
| `completed=true` mais statut inconnu | `"error"` | `"/login?error=authfailed"` | "Authentication completed with unknown status: {status}" | Nettoie attributs pending |

### 3.3 Cas spéciaux dans `checkAuthStatus()`

1. **Vérification préalable** : Si `AuthenticatedUser` existe déjà dans la session → retourne immédiatement `"accepted"` (évite le glitch "expired")
2. **Session expirée** : Si `pendingAuthAttemptId` ou `pendingUsername` sont `null` → retourne `"expired"` avec message "Session expired"
3. **Exception** : Si `EzkeyAuthException` est levée → retourne `"error"` avec `"/login?error=authfailed"`

---

## 4. Mapping : `AuthStatusResponse` → Frontend (challenge-wait.html)

### 4.1 Réception JavaScript

Le frontend reçoit un JSON :
```javascript
{
  status: "accepted" | "rejected" | "expired" | "error" | "pending",
  redirectUrl: string | null,
  message: string
}
```

### 4.2 Actions JavaScript selon le statut

| Statut `AuthStatusResponse` | Action Frontend | Délai | Message affiché |
|----------------------------|-----------------|-------|-----------------|
| `"accepted"` | Redirection vers `/dashboard` | 1000ms | "Authentication successful! Redirecting..." |
| `"rejected"` | Redirection vers `/login?error=rejected` | 3000ms | "Authentication rejected by user. {message}" |
| `"expired"` | Redirection vers `/login?error=expired` | 3000ms | "{message} or 'Authentication request expired'" |
| `"error"` | Redirection vers `/login?error=authfailed` | 3000ms | "{message} or 'Authentication error occurred'" |
| `"pending"` | Continue polling | - | Continue d'afficher "Waiting for device approval..." |

### 4.3 Affichage visuel

- **`pending`** : Spinner animé + texte "Waiting for device approval..."
- **`accepted`** : Icône de succès + texte vert + redirection automatique
- **`rejected` / `expired` / `error`** : Message d'erreur rouge avec bordure + redirection automatique

---

## 5. Mapping : Paramètres d'erreur → Page de login

### 5.1 Paramètres d'erreur possibles

| Paramètre `error` | Message affiché sur login.html | Source |
|-------------------|--------------------------------|--------|
| `rejected` | "Authentication rejected by user" | Redirection depuis `checkAuthStatus()` ou `challenge-wait.html` |
| `expired` | "Authentication request expired" | Redirection depuis `checkAuthStatus()` ou `challenge-wait.html` |
| `invalid` | "Authentication invalid" | Redirection depuis `checkAuthStatus()` |
| `authfailed` | "Authentication failed" | Redirection depuis `checkAuthStatus()` (exception ou statut inconnu) |
| `notfound` | "User not found" | Création de tentative échouée (username non trouvé) |
| `sessionexpired` | "Session expired" | Accès à `/challenge-wait` sans attributs de session valides |

---

## 6. Problèmes identifiés et résolutions

### 6.1 Problème : "REJECTED" affiché comme "EXPIRED"

**Cause :** `AuthAttemptWaitService.calculateStatus()` vérifiait l'expiration avant les statuts finaux.

**Résolution :** Priorité donnée aux statuts finaux (`ACCEPTED`, `REJECTED`, `INVALID`) avant la vérification d'expiration.

### 6.2 Problème : Glitch "expired" avant redirection vers dashboard

**Cause :** Race condition où un poll suivant voyait les attributs de session déjà nettoyés.

**Résolution :**
- Vérification préalable de `AuthenticatedUser` dans la session
- Nettoyage des attributs pending **après** la création de `AuthenticatedUser` pour `ACCEPTED`
- Nettoyage des attributs pending **après** le retour de `ResponseEntity` pour les autres statuts finaux

### 6.3 Problème : "REJECTED" avec challenge marqué comme "INVALID"

**Cause :** `AuthAttemptRespondService.validateChallenge()` validait le challenge même pour les rejets explicites.

**Résolution :** Skip de la validation du challenge si `request.getAuthAttemptAccepted() == false`.

---

## 7. Flux complet de mapping

```
┌─────────────────────────────────────────────────────────────────┐
│ 1. ezkey-core: AuthAttemptWaitService.calculateStatus()         │
│    └─> Retourne: "ACCEPTED" | "REJECTED" | "INVALID" |         │
│        "EXPIRED" | "PENDING" | "READ"                           │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│ 2. Domain → DTO: AuthAttemptWaitResponse                        │
│    └─> status: String (mappé directement)                       │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│ 3. demo-app-acme: EzkeyAuthService.waitForAuthAttempt()         │
│    └─> Retourne: AuthAttemptWaitResponse                        │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│ 4. LoginController.checkAuthStatus()                            │
│    └─> Transforme en AuthStatusResponse:                        │
│        • "ACCEPTED" → "accepted"                                │
│        • "REJECTED" → "rejected"                               │
│        • "EXPIRED" → "expired"                                  │
│        • "INVALID" → "error"                                    │
│        • "PENDING"/"READ" → "pending"                           │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│ 5. Frontend: challenge-wait.html (JavaScript)                    │
│    └─> Actions selon status:                                    │
│        • "accepted" → Redirect /dashboard                       │
│        • "rejected" → Redirect /login?error=rejected           │
│        • "expired" → Redirect /login?error=expired             │
│        • "error" → Redirect /login?error=authfailed            │
│        • "pending" → Continue polling                           │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│ 6. login.html: Affichage du message d'erreur                    │
│    └─> Message selon paramètre error:                           │
│        • error=rejected → "Authentication rejected by user"     │
│        • error=expired → "Authentication request expired"       │
│        • error=invalid → "Authentication invalid"               │
│        • error=authfailed → "Authentication failed"            │
└─────────────────────────────────────────────────────────────────┘
```

---

## 8. Points d'attention

### 8.1 Incohérences potentielles

1. **`INVALID` → `"error"`** : Le mapping transforme `INVALID` en statut générique `"error"`, ce qui peut masquer la distinction entre une erreur de validation cryptographique et une erreur système.

2. **Statut inconnu avec `completed=true`** : Si un statut inconnu arrive avec `completed=true`, il est traité comme `"error"` générique, ce qui peut masquer des problèmes de mapping.

3. **`EXPIRED` vs session expirée** : Le même statut `"expired"` est utilisé pour :
   - Une tentative d'authentification expirée (timeout/supersession)
   - Une session HTTP expirée (attributs manquants)

### 8.2 Recommandations

1. **Distinguer `INVALID` de `error`** : Considérer un statut séparé `"invalid"` dans `AuthStatusResponse` pour mieux distinguer les erreurs de validation cryptographique.

2. **Améliorer les messages d'erreur** : Fournir des messages plus spécifiques pour chaque type d'erreur, notamment pour distinguer l'expiration de tentative vs session.

3. **Logging amélioré** : Ajouter des logs détaillés à chaque étape de mapping pour faciliter le débogage.

4. **Tests de mapping** : Créer des tests unitaires pour valider tous les mappings de statuts, y compris les cas limites.

---

## 9. Conclusion

Le système de mapping des statuts fonctionne globalement bien, mais présente quelques zones d'amélioration :

✅ **Points forts :**
- Mapping clair et direct depuis le domaine jusqu'au frontend
- Gestion appropriée des statuts transitoires vs finaux
- Résolution des problèmes de race condition et de statuts incorrects

⚠️ **Points à améliorer :**
- Distinction plus fine entre types d'erreurs (`INVALID` vs erreurs système)
- Messages d'erreur plus spécifiques pour l'expiration
- Tests de mapping complets
