# Ezkey Mobile App – Implementation Plan

## 1. Synthèse du besoin (d'après PRD et consignes)

- **Objectif** : Fournir une application mobile open source, multiplateforme, minimaliste, dédiée à l'écosystème Ezkey (MFA, enrollment, authentification push).
- **Fonctionnalités principales** :
  - Ajouter une intégration Ezkey (enrollment) via QR code **ou via lien reçu par SMS** (deep link)
  - Gérer plusieurs enrollments (liste, marquer un par défaut, **supprimer un enrollment**)
  - Stocker pour chaque enrollment :
    - URL d'enrollment (auth-api)
    - Clé publique de l'app intégrée
    - Clé privée du device
    - **Nom, code, description et URL du logo de l'intégration** (affichés lors de l'authentification)
  - Authentification push (initiate/complete via REST, mockable)
  - Export sécurisé des données (chiffré par mot de passe)
- **Non-objectifs** :
  - Pas d'intégration d'autres moyens d'authentification (FIDO2, OTP, etc.)
  - Pas de surcouche complexe, interface épurée et simple

## 2. Recommandations technologiques

- **Flutter (Dart)**
  - Multiplateforme (iOS, Android, Web, Desktop)
  - Large communauté, documentation abondante
  - Facile à maintenir, à faire évoluer, et à automatiser (AI coding, CI/CD)
  - Bon support pour la cryptographie, QR code, stockage sécurisé (KeyStore/Keychain), **deep linking**
  - Open source, licence permissive
- **Alternatives** : React Native (moins adapté pour la crypto et la simplicité du packaging), Kotlin Multiplatform (plus complexe, moins mature pour iOS)
- **Sécurité** :
  - Utiliser le stockage sécurisé natif (KeyStore/Keychain)
  - Chiffrement fort pour l'export/import
  - Jamais stocker de clé privée en clair
  - Code source et dépendances audités

## 3. Phasage et features

### Phase 1 – Coquille initiale (Mockable, testable)
- Structure Flutter de base (navigation, pages, menu principal)
- Mock des appels REST (enrollment, authattempt)
- Page d'accueil : bouton + (ajout d'intégration)
- **Ajout d'un enrollment par QR code ou par lien deep link (mock)**
- Liste des enrollments mockés (affichage minimal, marque par défaut, **suppression possible**)
- **Stockage et affichage du nom, code, description, logo de l'intégration dans la liste**
- Scan QR code (mock, sans parsing réel)
- Stockage local mock (en mémoire)
- Menu paramètres (option export mock, mot de passe non effectif)
- Tests unitaires de navigation et logique de base

### Phase 2 – Fonctionnalités réelles et sécurité
- Intégration réelle du scan QR (librairie Flutter QR)
- **Support du deep link pour enrollment via lien reçu par SMS**
- Parsing QR/lien pour préremplir l'enrollment (URL, clé publique, nom, code, description, logo, etc.)
- Stockage sécurisé (KeyStore/Keychain, chiffrement local)
- Génération/stockage de la clé privée device (crypto natif)
- Appels REST réels (enrollment, authattempt) avec gestion d'erreur
- Export sécurisé (fichier chiffré, mot de passe)
- UI/UX minimaliste, responsive, accessibilité
- **Affichage du nom, description et logo de l'intégration lors d'une authentification**
- **Suppression d'un enrollment (UI et logique)**
- Tests unitaires et d'intégration (mock + réel)

### Phase 3 – Approche production
- Gestion fine des erreurs et des états (loading, offline, etc.)
- Sécurité renforcée (verrouillage biométrique, audit code)
- Support multi-device (sauvegarde/restauration)
- Documentation utilisateur et développeur
- Publication sur stores (open source, instructions build)
- Automatisation CI/CD (tests, build, lint)
- **Intégration backend admin-api pour l'envoi de SMS avec lien d'enrollment**

## 4. Bonnes pratiques recommandées

- **Architecture** : MVVM ou Clean Architecture (séparation UI, logique, data)
- **Tests** : Unitaires, widget, intégration (mock et réel)
- **Sécurité** :
  - Toujours chiffrer les données sensibles
  - Utiliser des librairies reconnues (cryptographie, QR, HTTP, deep linking)
  - Jamais stocker de secrets en clair
- **Code** :
  - Documentation claire (README, commentaires)
  - Convention de nommage et structure de projet Flutter standard
  - Linting et formatage automatique
- **Évolutivité** :
  - Modularité du code (feature folders)
  - Prévoir l'injection de dépendances (ex : get_it, riverpod)
  - Mock facile pour les tests et l'IA
- **Open Source** :
  - Licence MIT ou Apache 2.0
  - README détaillé (build, contribution, sécurité)

## 5. Synthèse des features (roadmap)

- [ ] Coquille Flutter, navigation, pages principales (mock)
- [ ] Scan QR (mock puis réel)
- [ ] **Ajout d'un enrollment par QR ou lien deep link (mock puis réel)**
- [ ] Liste enrollments, ajout/suppression, marque par défaut
- [ ] **Stockage et affichage du nom, code, description, logo de l'intégration**
- [ ] Stockage sécurisé (clé privée, clé publique, URL, métadonnées)
- [ ] Authentification push (initiate/complete, mock puis REST réel)
- [ ] **Affichage du nom, description, logo lors d'une authentification**
- [ ] Export/import sécurisé (mot de passe, chiffrement)
- [ ] UI/UX minimaliste, accessibilité
- [ ] Sécurité avancée (biométrie, audit)
- [ ] Documentation et publication open source
- [ ] **Intégration backend admin-api pour l'envoi de SMS avec lien d'enrollment**

---

**Ce plan est évolutif et pourra être raffiné à chaque étape selon les retours et besoins.** 