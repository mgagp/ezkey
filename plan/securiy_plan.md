# Plan de Séparation et Sécurisation des API - Projet Ezkey

## 1. Problématique

Actuellement, les API REST d'Ezkey sont monolithiques :
- Les endpoints d'intégration, d'enrollment et d'authentification sont accessibles à tous (organisation et mobile).
- Risque : Les mobiles pourraient accéder à des endpoints réservés à l'organisation (ex : création d'intégration, d'enrollment, etc.).

**Objectif :** Séparer et sécuriser les flux API pour :
- Les applications d'organisation (admin, intégration, création d'enrollment/authattempt)
- Les applications mobiles (consommation/completion d'enrollment, validation d'authentification)

---

## 2. Approches Possibles

### A. Séparation Logique (dans le même projet Spring Boot)
- **Principe :**
  - Créer des controllers distincts :
    - `AdminIntegrationController`, `AdminEnrollmentController`, `AdminAuthAttemptController` (pour l'organisation)
    - `MobileEnrollmentController`, `MobileAuthAttemptController` (pour le mobile)
  - Utiliser des préfixes d'URL (`/api/v1/admin/*` vs `/api/v1/mobile/*`)
  - Sécuriser chaque groupe d'API avec des filtres/sécurité Spring (JWT, rôles, etc.)

- **Avantages :**
  - Déploiement unique, plus simple à maintenir au début
  - Mutualisation de la logique métier et de la base de données
  - Facile à refactorer si besoin d'extraire plus tard

- **Inconvénients :**
  - Surface d'attaque plus large (un bug de config peut exposer un endpoint admin)
  - Moins scalable à long terme (difficulté à séparer les cycles de vie, logs, monitoring)
  - Risque de confusion dans les dépendances et la gestion des accès

---

### B. Séparation Physique (plusieurs apps Spring Boot)
- **Principe :**
  - Créer deux applications Spring Boot distinctes :
    - `ezkey-admin-api` : endpoints pour l'organisation (intégration, création d'enrollment, création d'authattempt)
    - `ezkey-authentication-api` : endpoints pour le mobile (consommation/completion d'enrollment, validation d'authentification)
  - Chaque app a sa propre config de sécurité, ses propres contrôleurs, et éventuellement sa propre base de données (ou schéma partagé)

- **Avantages :**
  - Cloisonnement fort : impossible pour le mobile d'accéder aux endpoints admin
  - Sécurité accrue (surface d'attaque réduite, isolation réseau possible)
  - Déploiement, scaling, monitoring indépendants
  - Aligné avec les architectures modernes (microservices, hexagonal)
  - Plus facile à ouvrir à la communauté (contributeurs peuvent travailler sur un sous-ensemble)

- **Inconvénients :**
  - Plus de maintenance (2 projets, 2 configs, 2 pipelines CI/CD)
  - Nécessite une gestion de la communication inter-apps si besoin (ex : events, messages)
  - Migration initiale plus longue

---

### C. Séparation Hybride (mono-repo, multi-app)
- **Principe :**
  - Un seul repo (ex : `ezkey/ezkey`), mais plusieurs modules/applications Spring Boot (`admin-api`, `authentication-api`)
  - Mutualisation du code métier dans des modules partagés (`ezkey-core`)

- **Avantages :**
  - Bénéficie de la séparation physique tout en gardant la simplicité du mono-repo
  - Mutualisation du code métier, DTO, mappers, etc.
  - Facile à tester et à versionner

- **Inconvénients :**
  - Complexité du build (multi-module Maven/Gradle)
  - Peut devenir un mono-repo lourd si beaucoup de modules

---

## 3. Sécurisation des API

- **API Admin** :
  - Authentification forte (JWT, OAuth2, API Key, mutual TLS)
  - Gestion des rôles et permissions (Spring Security, RBAC)
  - Limitation d'accès par IP ou VPN (optionnel)

- **API Mobile** :
  - Authentification par device ID, JWT, ou OAuth2
  - Limitation stricte des endpoints accessibles
  - Validation des tokens côté serveur

- **Bonnes pratiques** :
  - Toujours valider les entrées côté serveur
  - Journaliser les accès sensibles
  - Versionner les API (`/api/v1/`)
  - Documenter les flux d'authentification et d'autorisation

---

## 4. Recommandation Alignée Open Source

- **Ce qui est le plus attendu dans la communauté :**
  - **Séparation physique** (option B ou C) :
    - C'est la norme dans les projets open source modernes (Keycloak, Argo, Supabase, etc.)
    - Facilite la contribution, la sécurité, et la scalabilité
    - Permet d'avoir des cycles de vie indépendants
  - **Nommage attendu :**
    - `ezkey-api-admin` ou `ezkey-admin-api` pour l'admin
    - `ezkey-api-authentication` ou `ezkey-authentication-api` pour le mobile
    - `ezkey-core` pour la logique partagée

---

## 5. Résumé

| Approche         | Sécurité | Simplicité | Scalabilité | Communauté | Recommandé |
|------------------|----------|------------|-------------|------------|------------|
| Logique (A)      | Moyen    | Facile     | Limité      | Moyen      | Non        |
| Physique (B)     | Forte    | Moyen      | Excellente  | Excellente | Oui        |
| Hybride (C)      | Forte    | Bonne      | Excellente  | Excellente | Oui        |

**Conclusion :**
- Pour un projet open source moderne, la séparation physique (B) ou hybride (C) est la plus attendue et la plus sécurisée.
- Démarrer avec un mono-repo multi-app (C) est souvent le meilleur compromis : facile à migrer, à tester, à ouvrir à la communauté.
- Toujours appliquer une authentification forte et des contrôles d'accès stricts sur les API admin.

---

**Prochaine étape :**
- Découper les controllers existants selon les flux (admin vs mobile)
- Créer deux modules/applications Spring Boot
- Mettre en place la sécurité adaptée à chaque flux
- Documenter les endpoints et les flux d'authentification 