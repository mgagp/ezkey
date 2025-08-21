# Ezkey API Endpoint Reference

## Contexte général

Ezkey sépare ses API backend en deux applications :
- **admin-api** (interne) : gestion des demandes d’authentification (CRUD), accessible uniquement à l’organisation.
- **auth-api** (externe, mobile) : consommation des demandes d’authentification par l’application mobile Ezkey.

Le modèle d’interaction est **pull** : le mobile vient chercher la demande à valider (pending) en fournissant une signature cryptographique dans le body, garantissant l’authenticité de la requête.

---

## 🔐 Sécurité des Tokens - Conception Critique

### **Principe de Sécurité : Token à Usage Unique**

Ezkey utilise un système de **tokens de preuve à usage unique** pour garantir l'intégrité et la sécurité du processus d'authentification.

#### **🔑 Deux Types de Tokens**

1. **`enrollmentProofToken`** : Token permanent de l'enrollment, utilisé pour lier l'appareil
2. **`authAttemptProofToken`** : Token unique par tentative d'authentification, **CRITIQUE pour la sécurité**

#### **🛡️ Mécanisme de Sécurité**

**Étape 1 - PENDING** :
- Le serveur génère un `authAttemptProofToken` unique pour chaque tentative
- Ce token est **chiffré** avec la clé publique de l'intégration lors de l'envoi
- Le device mobile **déchiffre** le token avec sa clé privée
- **Le token ne peut être lu qu'une seule fois** (par le PENDING)

**Étape 2 - RESPOND** :
- Le device mobile **signe** le `authAttemptProofToken` (en clair) avec sa clé privée
- Cette signature prouve que le device a bien reçu le token original
- **Sécurité renforcée** : impossible de rejouer une tentative sans avoir le token original

#### **⚠️ Points Critiques pour les Développeurs**

```java
// ❌ INCORRECT - Ne jamais signer enrollmentProofToken pour RESPOND
String signature = signWithDeviceKey(enrollmentProofToken);

// ✅ CORRECT - Toujours signer authAttemptProofToken pour RESPOND  
String signature = signWithDeviceKey(authAttemptProofToken);
```

#### **🎯 Pourquoi cette Conception ?**

1. **Anti-replay** : Chaque tentative a un token unique
2. **Authentification forte** : Seul le device légitime peut déchiffrer et signer
3. **Traçabilité** : Chaque token peut être tracé à une tentative spécifique
4. **Sécurité par défaut** : Impossible de contourner sans comprendre le mécanisme

---

## 1. Endpoints auth-api (mobile)

### a) Récupérer la demande en attente (pending)

**POST /api/v1/auth-attempts/pending/{enrollmentId}**

- **Description** : Le mobile interroge le backend pour savoir s’il existe une demande d’authentification en attente pour son enrollmentId. Le body contient une signature cryptographique prouvant l’authenticité de la requête.
- **Pourquoi POST ?** : La signature crypto est transmise dans le body, ce qui n’est pas possible avec GET.

**Request**
```http
POST /api/v1/auth-attempts/pending/{enrollmentId}
Content-Type: application/json

{
  "timestamp": 1712345678,
  "signature": "base64-encoded-signature",
  "publicKey": "base64-encoded-public-key"
}
```

**Response**
- 200 OK + détails de la demande en attente (ou 204 No Content si aucune demande)
```json
{
  "authAttemptId": 123,
  "challenge": "...",
  "createdAt": "2024-06-01T12:34:56Z",
  ...
}
```

### b) Soumettre la réponse à la demande

**POST /api/v1/auth-attempts/respond/{authAttemptId}**

- **Description** : Le mobile soumet la réponse de l’usager (approuvé, refusé, signature, etc.) pour la demande d’authentification reçue.

**Request**
```http
POST /api/v1/auth-attempts/respond/{authAttemptId}
Content-Type: application/json

{
  "approved": true,
  "responseSignature": "base64-encoded-signature",
  "timestamp": 1712345699
}
```

**Response**
- 200 OK + résultat de la validation
```json
{
  "status": "APPROVED"
}
```

### c) Processus d'enrollment (liaison d'appareil)

**GET /api/v1/enrollments/bind/{enrollmentId}**

- **Description** : Initie le processus de liaison d'un enrollment à un appareil mobile. L'appareil récupère les informations nécessaires pour débuter l'enrollment.

**Request**
```http
GET /api/v1/enrollments/bind/456
```

**Response**
- 200 OK + informations de liaison
```json
{
  "enrollmentId": 456,
  "integrationPublicKey": "base64-encoded-integration-key",
  "enrollmentCode": "EZK-ABC123-DEF456",
  "enrollmentCodeSigned": "base64-encoded-signed-code",
  "simulationDevicePublicKey": "base64-encoded-simulation-key",
  "simulationDevicePrivateKey": "base64-encoded-simulation-private-key",
  "simulationEnrollmentCodeSigned": "base64-encoded-simulation-signature"
}
```

### d) Vérification de l'enrollment

**POST /api/v1/enrollments/verify**

- **Description** : Finalise le processus d'enrollment en soumettant les clés cryptographiques de l'appareil et la signature du code d'enrollment.

**Request**
```http
POST /api/v1/enrollments/verify
Content-Type: application/json

{
  "enrollmentId": 456,
  "challengeResponse": 987654,
  "devicePublicKey": "base64-encoded-device-public-key",
  "enrollmentCode": "EZK-ABC123-DEF456",
  "enrollmentCodeSigned": "base64-encoded-signed-enrollment-code"
}
```

**Response**
- 200 OK + confirmation de la vérification
```json
{
  "verified": true,
  "enrollmentId": 456,
  "status": "CONFIRMED"
}
```

---

## 2. Endpoints admin-api (interne)

### a) Gestion des demandes d'authentification

**GET    /api/v1/auth-attemps**          // Lister toutes les demandes d'authentification
**POST   /api/v1/auth-attempts**         // Créer une demande d'authentification
**GET    /api/v1/auth-attempts/{id}**    // Lire une demande
**DELETE /api/v1/auth-attempts/{id}**    // Supprimer une demande

**Exemple de GET**
```
   {
        "authAttemptId": 49,
        "enrollmentId": 61,
        "authAttemptRead": false,
        "authAttemptResponded": false,
        "authAttemptValid": false,
        "authAttemptAccepted": false,
        "authAttemptChallenge": null,
        "authAttemptProofToken": "KstrTWXbywp5Zi-ACI1kIzGrj9thTUkn_-lcOxQxYR0.1755796478548.1HGz9A4uEyYjqdxbYg9U7A",
        "deviceProofTokenValid": "false",
        "createdAt": "2025-08-21T13:14:38.548701"
    }
```

Pour afficher un status associé à une demande, voici les règles en ordre de priorité (#1 en premier)
-authAttemptRead null ou false : PENDING
-authAttemptRead et authAttemptResponded null ou false: READ
-authAttemptValid null ou false: INVALID
-authAttemptAccepted null ou false: REJECTED sinon ACCEPTED

**Exemple de création**
```http
POST /api/v1/auth-attempts
Content-Type: application/json

{
  "enrollmentId": "abc123",
  "requestedBy": "app-backend",
  "challenge": "...",
  ...
}
```

### b) Gestion des enrollments (CRUD)

**GET    /api/v1/enrollments**           // Récupérer tous les enrollments
**GET    /api/v1/enrollments/{id}**      // Récupérer un enrollment par ID
**POST   /api/v1/enrollments**          // Créer un nouveau enrollment
**DELETE /api/v1/enrollments/{id}**     // Supprimer un enrollment

**Création d'un enrollment**
```http
POST /api/v1/enrollments
Content-Type: application/json

{
  "integrationId": 123,
  "name": "Mon Appareil Mobile",
  "authAttemptChallengeRequired": true
}
```

**Response**
- 201 Created + détails de l'enrollment créé
```json
{
  "enrollmentId": 456,
  "enrollmentCode": "EZK-ABC123-DEF456",
  "enrollmentChallenge": 987654,
  "createdAt": "2024-06-01T12:34:56Z"
}
```

**Récupération d'un enrollment**
```http
GET /api/v1/enrollments/456
```

**Response**
- 200 OK + détails complets de l'enrollment
```json
{
  "enrollmentId": 456,
  "integrationId": 123,
  "enrollmentName": "Mon Appareil Mobile",
  "enrollmentRead": false,
  "enrollmentConfirmed": false,
  "enrollmentActive": true,
  "enrollmentChallenge": 987654,
  "authAttemptChallengeRequired": true,
  "integrationPublicKey": "base64-encoded-key",
  "authAttemptPublicKey": null,
  "enrollmentCode": "EZK-ABC123-DEF456",
  "createdAt": "2024-06-01T12:34:56Z"
}
```

**Récupération de tous les enrollments**
```http
GET /api/v1/enrollments
```

**Response**
- 200 OK + liste de tous les enrollments
```json
[
  {
    "enrollmentId": 456,
    "integrationId": 123,
    "enrollmentName": "Mon Appareil Mobile",
    "enrollmentRead": false,
    "enrollmentConfirmed": false,
    "enrollmentActive": true,
    "enrollmentChallenge": 987654,
    "authAttemptChallengeRequired": true,
    "integrationPublicKey": "base64-encoded-key",
    "authAttemptPublicKey": null,
    "enrollmentCode": "EZK-ABC123-DEF456",
    "createdAt": "2024-06-01T12:34:56Z"
  }
]
```

**Suppression d'un enrollment**
```http
DELETE /api/v1/enrollments/456
```

**Response**
- 204 No Content (suppression réussie)
- 404 Not Found (enrollment inexistant)

---

## 3. Résumé des choix de design

- **pending** : exprime clairement la demande en attente pour un enrollment donné.
- **respond** : standard, explicite pour la soumission de la réponse.
- **POST pour pending** : permet de transmettre une signature crypto dans le body, renforçant la sécurité.
- **Séparation claire** entre gestion (admin-api) et consommation (auth-api).
- **Processus d'enrollment** : séparation claire entre binding (GET) et verification (POST) pour la sécurité.
- **CRUD enrollment** : opérations classiques de gestion administrative des enrollments.

---

## 4. Notes complémentaires

- Toujours documenter les codes de retour (200, 204, 400, 401, 403, etc.).
- Expliquer dans la doc que le modèle est pull (pas de push notification).
- Préciser le format attendu de la signature et des clés publiques.

---

**Ce fichier sert de référence pour la conception et la documentation future des endpoints Ezkey.** 