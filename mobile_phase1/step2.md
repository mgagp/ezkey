# Ezkey Mobile – Phase 1 : Étape 2

## Objectif
Compléter la phase 1 du plan_mobile.md en ajoutant les fonctionnalités et raffinements non couverts dans step1.md, pour obtenir une coquille d’application mobile mockable, testable et prête à évoluer.

## 1. Fonctionnalités à ajouter (suite de la phase 1)

### 1.1. Ajout d’un enrollment par deep link (mock)
- Simuler la réception d’un lien d’enrollment (ex : bouton « Simuler lien SMS » sur la page d’ajout)
- Parser le lien (mock) pour préremplir les champs d’enrollment
- Naviguer automatiquement vers la page d’ajout avec les infos du lien
- Prévoir une structure pour le deep linking réel (TODO phase 2)

### 1.2. Suppression d’un enrollment
- Ajouter une action (icône poubelle ou swipe) sur chaque item de la liste des enrollments
- Confirmer la suppression (dialogue de confirmation)
- Mettre à jour la liste et le mock storage

### 1.3. Marquer un enrollment par défaut
- Permettre à l’utilisateur de sélectionner un enrollment comme « par défaut » (ex : étoile, switch)
- Stocker l’info dans le mock storage
- Afficher visuellement l’enrollment par défaut dans la liste

### 1.4. Affichage des métadonnées d’intégration
- Pour chaque enrollment, afficher dans la liste : nom, code, description, logo (mock)
- Prévoir un modèle d’enrollment enrichi (voir ci-dessous)

### 1.5. Navigation et UI
- Ajouter un menu paramètres (page dédiée)
- Navigation claire entre : liste enrollments, ajout, paramètres
- UI minimaliste, responsive, adaptée mobile

## 2. Modèle d’enrollment enrichi (mock)
```dart
class Enrollment {
  final int id;
  final String integrationName;
  final String integrationCode;
  final String integrationDescription;
  final String integrationLogoUrl;
  final String enrollmentUrl;
  final String integrationPublicKey;
  final String devicePrivateKey;
  final bool isDefault;
  // ... autres champs mockés

  Enrollment({
    required this.id,
    required this.integrationName,
    required this.integrationCode,
    required this.integrationDescription,
    required this.integrationLogoUrl,
    required this.enrollmentUrl,
    required this.integrationPublicKey,
    required this.devicePrivateKey,
    this.isDefault = false,
  });
}
```

## 3. Conseils d’implémentation
- Utiliser un service mock unique pour gérer la liste des enrollments (CRUD, default)
- Prévoir des méthodes pour ajouter, supprimer, marquer par défaut
- Utiliser des modèles bien documentés (///)
- Préparer les pages pour recevoir des données mock ou réelles (phase 2)
- Garder le code modulaire et prêt pour l’injection de dépendances

## 4. Tests et validation
- Vérifier que l’ajout/suppression/marquage par défaut fonctionne sans bug
- Tester la navigation entre les pages
- Vérifier l’affichage des métadonnées (nom, code, logo, description)
- Prévoir des TODO pour la gestion réelle du deep link et du QR code en phase 2

---

**Après cette étape, l’app sera une coquille complète, mockable, avec toutes les interactions principales prêtes à être branchées sur du réel en phase 2.** 