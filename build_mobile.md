# Rapport : Build et Automatisation pour Ezkey Mobile (Flutter/Dart)

## 1. Peut-on utiliser Maven (pom.xml) pour piloter un build Flutter/Dart ?

- **Non recommandé** :
  - Maven est l'outil standard pour les projets Java, mais il n'est pas conçu pour gérer des projets Flutter/Dart.
  - Il existe des plugins Maven pour exécuter des commandes shell ou piloter des scripts externes, mais cela ajoute de la complexité inutile et n'est pas supporté par la communauté Flutter.
  - Les outils natifs de Flutter (CLI, pub, etc.) sont beaucoup plus adaptés, maintenus et documentés.

- **Cas d'usage** :
  - Si tu as un monorepo avec du Java (Spring Boot) et du Flutter, il est préférable de séparer les builds : Maven pour le backend, outils Flutter pour le mobile.
  - Si tu veux automatiser le build de l'app mobile dans une CI/CD Java, il vaut mieux appeler les commandes Flutter/Dart via des scripts shell ou des jobs dédiés, pas via Maven.

## 2. Meilleures pratiques pour le build Flutter/Dart

- **Utiliser les outils natifs Flutter/Dart**
  - `flutter build apk` (Android)
  - `flutter build ios` (iOS)
  - `flutter test` (tests unitaires)
  - `flutter pub get` (gestion des dépendances)
  - `dart doc` (génération de documentation)

- **Automatisation CI/CD**
  - Utiliser des workflows dédiés (GitHub Actions, GitLab CI, etc.) avec des jobs séparés pour le backend et le mobile
  - Exemples de jobs pour le mobile :
    - Installer Flutter/Dart
    - `flutter pub get`
    - `flutter test`
    - `flutter build apk` ou `flutter build ios`

- **Plugins recommandés**
  - Pour Flutter : utiliser les plugins officiels via `pubspec.yaml` (pas Maven)
  - Exemples utiles pour Ezkey mobile :
    - `provider` ou `riverpod` (state management)
    - `http` (requêtes REST)
    - `qr_flutter` (QR code)
    - `url_launcher` (deep linking)
    - `flutter_secure_storage` (stockage sécurisé)
    - `intl` (localisation)
    - `mockito` (tests)

- **Structure du projet**
  - Garder le dossier mobile séparé (`ezkey-mobile/`)
  - Gérer les dépendances et scripts dans `pubspec.yaml` (pas dans pom.xml)
  - Documenter dans le README comment builder et tester l'app mobile

## 3. Conclusion

- **Ne pas utiliser Maven/pom.xml pour piloter le build Flutter/Dart**
  - Ce n'est pas standard, pas supporté, et cela complexifie inutilement le projet
- **Utiliser les outils natifs Flutter/Dart et des workflows CI/CD dédiés**
  - Plus simple, plus robuste, mieux documenté, et accepté par la communauté open source
- **Séparer clairement le build backend (Maven) et mobile (Flutter)**
  - Chacun avec ses outils, scripts et documentation

---

**En résumé : pour Ezkey mobile, utilise exclusivement les outils Flutter/Dart pour le build, les tests et la CI/CD. Documente bien la procédure dans le README du dossier mobile.** 