# Ezkey Mobile – Phase 1 : Guide de démarrage Flutter

## 1. Pré-requis
- Flutter et Dart installés (test : `flutter doctor`)
- VSCode ou Android Studio recommandé
- Un device ou un émulateur Android/iOS

## 2. Création du projet
```bash
flutter create ezkey_mobile
cd ezkey_mobile
```

## 3. Structure initiale recommandée
```
ezkey_mobile/
  lib/
    main.dart
    features/
      enrollments/
        enrollment_list_page.dart
        enrollment_add_page.dart
        enrollment_model.dart
        enrollment_mock_service.dart
      auth/
        auth_push_page.dart
        auth_mock_service.dart
    shared/
      widgets/
      models/
      utils/
    settings/
      settings_page.dart
      export_mock_service.dart
```

## 4. Fonctionnalités à implémenter (Phase 1)
- Page d’accueil avec bouton + pour ajouter un enrollment (QR ou deep link mock)
- Liste des enrollments mockés (nom, code, logo, marque par défaut, suppression possible)
- Mock du processus d’enrollment (bind/confirm) et d’authentification push (initiate/complete)
- Menu paramètres avec export mock
- Navigation simple (BottomNavigationBar ou Drawer)

## 5. Mocks à prévoir (DTOs simplifiés)

### Enrollment (bind/confirm)
- **Bind (GET /api/v1/enrollments/bind/{id})**
  - Réponse mock :
    ```json
    {
      "enrollmentId": 1,
      "integrationPublicKey": "...",
      "enrollmentCode": "ENROLL123",
      "enrollmentCodeSigned": "...",
      "simulationDevicePublicKey": "...",
      "simulationDevicePrivateKey": "..."
    }
    ```
- **Confirm (POST /api/v1/enrollments/confirm)**
  - Requête mock :
    ```json
    {
      "enrollmentId": 1,
      "challengeResponse": 123456,
      "devicePublicKey": "...",
      "enrollmentCode": "ENROLL123",
      "enrollmentCodeSigned": "..."
    }
    ```
  - Réponse mock :
    ```json
    { "active": true }
    ```

### AuthAttempt (initiate/complete)
- **Initiate (POST /api/v1/authattempts/initiate/{id})**
  - Requête mock :
    ```json
    {
      "enrollmentId": 1,
      "authAttemptEnrolleeCode": "ENROLL123",
      "authAttemptEnrolleeCodeSigned": "..."
    }
    ```
  - Réponse mock :
    ```json
    {
      "authAttemptId": 42,
      "authAttemptCode": "CODE123",
      "authAttemptCodeSigned": "...",
      "authAttemptChallengeRequired": false
    }
    ```
- **Complete (POST /api/v1/authattempts/complete/{id})**
  - Requête mock :
    ```json
    {
      "enrollmentId": 1,
      "authAttemptId": 42,
      "authAttemptEnrolleeCode": "ENROLL123",
      "authAttemptEnrolleeCodeSigned": "...",
      "authAttemptCode": "CODE123",
      "authAttemptCodeSigned": "...",
      "authAttemptChallengeResponse": 123456,
      "authAttemptAccepted": true
    }
    ```
  - Réponse mock :
    ```json
    { "success": true, "message": "Authentication complete" }
    ```

## 6. Conseils pour builder et tester
- Pour lancer l’app :
  ```bash
  flutter run
  ```
- Pour hot reload :
  - Sauvegarder un fichier, l’app se met à jour automatiquement
- Pour tester sur un device :
  - Brancher le téléphone, activer le mode développeur, accepter le debug USB
- Pour tester sur un émulateur :
  - Lancer un émulateur Android/iOS depuis Android Studio ou VSCode
- Pour générer la documentation :
  ```bash
  dart doc
  ```
- Pour organiser le code :
  - Utiliser des classes pour les mocks, les modèles, et les pages
  - Documenter chaque classe/fonction avec ///
  - Prévoir des TODO pour la phase 2 (stockage sécurisé, QR réel, REST réel)

## 7. Bonnes pratiques pour la phase 1
- Garder le code simple, modulaire, bien commenté
- Utiliser des modèles pour les DTOs (enrollment, authattempt)
- Prévoir des services mock injectables (pour tests et évolutions)
- UI minimaliste, navigation claire
- Préparer la structure pour évoluer facilement vers la phase 2

---

**Passe à l’étape suivante en créant la structure Flutter et les premiers mocks !** 