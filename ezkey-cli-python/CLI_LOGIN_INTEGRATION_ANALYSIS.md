# CLI Login Integration Analysis

## Date: 2025-01-XX
## Objectif: Faire le point sur l'intégration du processus de login admin zéro dans le CLI

---

## État Actuel de l'Implémentation

### ✅ Ce qui est déjà implémenté

#### 1. Commandes d'authentification
- ✅ `ezkey admin auth login` - Login passwordless
- ✅ `ezkey admin auth passwordless-wait` - Attente avec challenge
- ✅ `ezkey admin auth recover` - Récupération avec recovery code
- ✅ `ezkey admin auth logout` - Logout et révocation du token

#### 2. Gestion du token
- ✅ Sauvegarde du token dans la config (`set_bearer_token()`)
- ✅ Suppression du token (`clear_bearer_token()`)
- ✅ Persistance dans `~/.ezkey/ezkey.json` ou `./ezkey.json`

#### 3. Client HTTP
- ✅ `HttpClient` avec méthode `_setup_auth()`
- ✅ Ajout automatique du header `Authorization: Bearer <token>`
- ✅ Support de l'authentification API key (Basic Auth) en fallback

#### 4. Format des requêtes
- ✅ Format correct pour `/api/v1/admin/auth/login`
- ✅ Format correct pour `/api/v1/admin/auth/passwordless-wait`
- ✅ Format correct pour `/api/v1/admin/auth/recover`
- ✅ Format correct pour `/api/v1/admin/auth/logout`

---

## Problèmes Identifiés

### 🔴 Problème 1: Gestion du mode single-call (sans challenge) - CRITIQUE

**Situation actuelle:**
- Le CLI envoie la requête POST `/api/v1/admin/auth/login` avec `challengeRequested: false`
- Le serveur fait le polling en interne (5 minutes, 2 secondes d'intervalle)
- Le CLI attend la réponse HTTP qui peut prendre jusqu'à 5 minutes
- **Le timeout par défaut du CLI est de 30 secondes** (`timeout: 30000` dans config, converti en 30s)

**Problème:**
- Si le device met plus de 30 secondes à répondre, la requête timeout avant que le serveur ne reçoive la réponse du device
- Le CLI ne gère pas correctement les timeouts longs
- **CONFIRMÉ:** Le code dans `http_client.py` ligne 40-41: `timeout = config.get('timeout', 30000)` puis `self.timeout = timeout / 1000.0` = 30 secondes

**Solution nécessaire:**
- Augmenter le timeout automatiquement pour les commandes de login (au moins 6 minutes = 360000ms)
- Ajouter un message informatif pendant l'attente
- Gérer les timeouts de manière gracieuse avec un message clair

### 🔴 Problème 2: Gestion de la réponse du login

**Situation actuelle:**
Le CLI vérifie:
```python
if data.get('success') and data.get('token'):
    # Token reçu
elif data.get('status') == 'pending' and data.get('challengeCode'):
    # Challenge mode
else:
    # Erreur
```

**Problème:**
- Le format de réponse du serveur utilise `status: "approved"` quand c'est un succès (pas `success: true` uniquement)
- Le CLI vérifie `success` ET `token`, mais le serveur peut retourner `success: true` avec `status: "approved"` et `token`
- Besoin de vérifier le format exact de la réponse

**Solution nécessaire:**
- Vérifier le format exact de `AdminLoginResponseDto` du serveur
- Adapter la logique de parsing pour gérer tous les cas

### 🔴 Problème 3: Token non utilisé dans toutes les requêtes

**Situation actuelle:**
- Le `HttpClient` configure l'auth dans `_setup_auth()` au moment de l'initialisation
- Le token est lu depuis la config au moment de l'initialisation

**Problème:**
- Si le token est sauvegardé après l'initialisation du `HttpClient`, il n'est pas utilisé
- Chaque commande crée un nouveau `HttpClient` qui lit la config, donc ça devrait fonctionner
- MAIS: Si le token est sauvegardé dans la config mais que le fichier n'est pas rechargé, le token n'est pas utilisé

**Solution nécessaire:**
- S'assurer que le token est bien rechargé depuis la config après sauvegarde
- Ou reconfigurer le `HttpClient` après le login

### 🔴 Problème 4: Gestion des erreurs d'authentification

**Situation actuelle:**
- Le CLI affiche les erreurs HTTP mais ne gère pas spécifiquement les erreurs 401/403
- Pas de message clair quand le token est expiré

**Solution nécessaire:**
- Détecter les erreurs 401/403 et suggérer de refaire un login
- Vérifier l'expiration du token avant de faire des requêtes

### 🔴 Problème 5: Pas de vérification de l'enrollment

**Situation actuelle:**
- Le CLI ne vérifie pas si l'admin a un enrollment lié avant de tenter le login
- L'erreur vient du serveur, mais le message pourrait être plus clair

**Solution nécessaire:**
- Optionnel: Vérifier l'enrollment avant le login (mais le serveur le fait déjà)
- Améliorer les messages d'erreur pour guider l'utilisateur

### 🔴 Problème 6: Pas de gestion du recovery token vs bearer token - IMPORTANT

**Situation actuelle:**
- Le recovery token est sauvegardé comme un bearer token normal via `set_bearer_token()`
- Le CLI vérifie `token.startswith('ezkey_recovery_')` pour `enrollment reset` (ligne 977)
- **CONFIRMÉ:** Dans `admin.py` ligne 709, le recovery token est sauvegardé avec `config.set_bearer_token(token)`

**Problème:**
- Le recovery token a une durée de vie limitée (30 minutes)
- Le recovery token a des permissions limitées (enrollment reset uniquement)
- Le CLI ne distingue pas clairement les deux types de tokens
- Le recovery token est sauvegardé dans le même champ que le bearer token normal
- Impossible de distinguer un recovery token d'un bearer token après sauvegarde

**Solution nécessaire:**
- Ajouter un champ séparé dans la config: `recoveryToken` vs `bearerToken`
- Ou ajouter un champ `tokenType` pour distinguer les types
- Afficher un avertissement quand un recovery token est utilisé
- Suggérer de faire un vrai login après recovery
- Empêcher l'utilisation d'un recovery token pour des opérations non autorisées

---

## Format de Réponse du Serveur

### Login Success (Single-call, no challenge)
```json
{
  "success": true,
  "message": "Authentication successful",
  "status": "approved",
  "token": "ezkey_bearer_abc123...",
  "adminType": "GLOBAL_ADMIN",
  "username": "admin",
  "expiresAt": "2025-10-15T14:30:00+01:00",
  "authAttemptId": null,
  "challengeCode": null
}
```

### Login Pending (Two-call, with challenge)
```json
{
  "success": false,
  "message": "Challenge verification required. Enter code 654321 on your device, then call /passwordless-wait.",
  "status": "pending",
  "token": null,
  "adminType": "GLOBAL_ADMIN",
  "username": "admin",
  "expiresAt": "2025-10-15T14:35:00+01:00",
  "authAttemptId": 123,
  "challengeCode": 654321
}
```

### Login Error
```json
{
  "success": false,
  "message": "No device enrolled for passwordless authentication",
  "status": null,
  "token": null,
  "adminType": null,
  "username": null,
  "expiresAt": null,
  "authAttemptId": null,
  "challengeCode": null
}
```

---

## Plan de Redressement

### Phase 1: Corrections Critiques (Priorité Haute)

#### 1.1 Corriger le timeout pour le login
- [ ] Augmenter le timeout automatiquement pour `admin auth login` (au moins 5 minutes)
- [ ] Ajouter un message informatif pendant l'attente
- [ ] Gérer les timeouts de manière gracieuse

#### 1.2 Corriger la gestion de la réponse du login
- [ ] Vérifier le format exact de la réponse du serveur
- [ ] Adapter la logique pour gérer `status: "approved"` ET `success: true`
- [ ] Tester avec les deux modes (single-call et two-call)

#### 1.3 S'assurer que le token est utilisé
- [ ] Vérifier que le token est bien rechargé après sauvegarde
- [ ] Tester que les requêtes suivantes utilisent bien le token
- [ ] Ajouter des logs verbose pour déboguer

### Phase 2: Améliorations (Priorité Moyenne)

#### 2.1 Gestion des erreurs d'authentification
- [ ] Détecter les erreurs 401/403
- [ ] Afficher un message clair suggérant de refaire un login
- [ ] Vérifier l'expiration du token (si disponible dans la config)

#### 2.2 Distinction recovery token vs bearer token
- [ ] Ajouter un champ dans la config pour distinguer les types de tokens
- [ ] Afficher un avertissement quand un recovery token est utilisé
- [ ] Suggérer de faire un vrai login après recovery

#### 2.3 Amélioration des messages d'erreur
- [ ] Messages plus clairs pour "No device enrolled"
- [ ] Guide pour lier un device
- [ ] Messages d'aide contextuels

### Phase 3: Améliorations Optionnelles (Priorité Basse)

#### 3.1 Vérification préalable
- [ ] Option pour vérifier l'enrollment avant le login
- [ ] Commande pour vérifier le statut de l'enrollment

#### 3.2 Gestion avancée des tokens
- [ ] Affichage de l'expiration du token
- [ ] Renouvellement automatique du token (si supporté)
- [ ] Gestion de plusieurs tokens (par environnement)

---

## Tests à Effectuer

### Test 1: Login single-call (sans challenge)
```bash
ezkey admin auth login --username admin
```
**Vérifier:**
- Le timeout est suffisant (5+ minutes)
- Le token est sauvegardé correctement
- Les requêtes suivantes utilisent le token

### Test 2: Login two-call (avec challenge)
```bash
ezkey admin auth login --username admin --challenge
ezkey admin auth passwordless-wait --auth-attempt-id <id> --challenge-code <code>
```
**Vérifier:**
- Le challenge code est affiché correctement
- Le wait fonctionne correctement
- Le token est sauvegardé après le wait

### Test 3: Recovery
```bash
ezkey admin auth recover --username admin --recovery-code "XXXX-XXXX-..."
```
**Vérifier:**
- Le recovery token est sauvegardé (actuellement dans `bearerToken`, devrait être dans `recoveryToken`)
- Le recovery token est utilisé pour `enrollment reset`
- Un avertissement est affiché pour le recovery token
- Le recovery token n'est PAS utilisé pour des opérations normales (intégrations, etc.)

### Test 4: Logout
```bash
ezkey admin auth logout
```
**Vérifier:**
- Le token est supprimé de la config
- Le token est révoqué sur le serveur
- Les requêtes suivantes échouent avec 401

### Test 5: Utilisation du token
```bash
ezkey admin auth login --username admin
ezkey admin integration list
```
**Vérifier:**
- Le token est utilisé dans la requête `integration list`
- Pas d'erreur 401

---

## Fichiers à Modifier

1. `ezkey_cli/commands/admin.py`
   - Corriger la gestion de la réponse du login
   - Augmenter le timeout pour le login
   - Améliorer les messages d'erreur

2. `ezkey_cli/utils/http_client.py`
   - S'assurer que le token est bien rechargé
   - Gérer les erreurs 401/403

3. `ezkey_cli/config/config_manager.py`
   - Ajouter un champ pour distinguer recovery token vs bearer token
   - Ajouter une méthode pour vérifier l'expiration

4. Documentation
   - Mettre à jour README.md avec les bonnes pratiques
   - Ajouter des exemples de troubleshooting

---

## Notes Techniques

### Timeout du serveur
- Le serveur attend jusqu'à 5 minutes (300 secondes) avec un polling de 2 secondes
- Le CLI doit avoir un timeout supérieur à 5 minutes pour le login

### Format du token
- Bearer token: `ezkey_bearer_...`
- Recovery token: `ezkey_recovery_...`

### Expiration
- Bearer token: 24 heures (configurable)
- Recovery token: 30 minutes (fixe)

---

## Conclusion

Le CLI a une bonne base d'implémentation, mais il manque quelques ajustements pour être pleinement fonctionnel avec le système admin zéro:

1. **Critique:** Gestion du timeout pour le login
2. **Critique:** Parsing correct de la réponse du login
3. **Important:** Distinction recovery token vs bearer token
4. **Important:** Gestion des erreurs d'authentification
5. **Optionnel:** Améliorations UX

Le plan de redressement est structuré en 3 phases pour prioriser les corrections critiques.

