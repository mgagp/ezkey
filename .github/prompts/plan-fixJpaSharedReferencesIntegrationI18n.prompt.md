# Plan: Corriger les erreurs JPA "Found shared references to Integration.i18n"

## Diagnostic

La trace pointe vers deux problèmes distincts mais liés, dont l'un est le **véritable déclencheur** :

### Cause racinaire : `OpenEntityManagerInView` + `resolveTenantId`

Le `EnrollmentController` a une méthode privée `resolveTenantId()` qui appelle `integrationRepository::findById` (méthode standard, sans hint read-only) **avant** d'appeler le service. Puisque `spring.jpa.open-in-view` n'est pas configuré à `false`, Spring Boot active l'`OpenEntityManagerInViewFilter` par défaut — la même session Hibernate reste ouverte pour toute la durée de la requête HTTP.

Conséquence dans le flux **bind** : `resolveTenantId` charge Integration comme entité trackée (BAG-1, lazy), puis `EnrollmentBindService.bind()` appelle `findByIdWithI18nAndTenant` avec `readOnly=true` dans la même session — Hibernate, selon la configuration, peut créer un second état de collection (BAG-2, eager). La même entité Integration se retrouve avec deux bags dans la même session → exception "Found shared references".

Dans le flux **verify** : `resolveTenantId` charge Integration trackée, puis dans `EnrollmentVerifyService.verify()`, la step 5 (`findAndLockBoundById`) crée un second objet Java pour le même enrollment (les queries de lock peuvent contourner le L1 cache), ce qui fait tirer `EncryptionEntityListener` deux fois (d'où les deux lignes identiques dans la trace). À la fin de la transaction OEMIV, le flush de session voit l'entité Integration dans un état incohérent.

### Problème secondaire : callers sans hint read-only

`findByIsSystemIntegrationAndActiveTrue` — utilisé dans `AdminProvisioningService` (×2) et `AdminBootstrapService` (×1) — n'a aucun `@QueryHints(readOnly=true)`. Ces services n'ont besoin que de `systemIntegration.getId()`, mais obtiennent une entité pleinement trackée avec cascade.

---

## Steps

### 1. ~~Désactiver `open-in-view` dans auth-api et admin-api~~ ⛔ Tenté, régression, différé

> **Statut : non appliqué — voir section "Chantier futur" ci-dessous.**

L'ajout de `spring.jpa.open-in-view=false` a produit une régression massive (50 tests fonctionnels en échec HTTP 500). La raison : l'admin-api possède de nombreux flux où des entités `Integration` (collections `i18n` lazy) sont retournées par les services puis mappées vers des DTOs *dans les contrôleurs*, hors transaction. Avec OEMIV actif, la session restait ouverte et ces accès lazy fonctionnaient silencieusement. Sans OEMIV, tous de lèvent une `LazyInitializationException`.

Pour appliquer `open-in-view=false` proprement, il faudrait refactorer tous les services de l'admin-api pour que la conversion MapStruct (inclus accès lazy) s'effectue *à l'intérieur* du `@Transactional`, retournant directement des DTOs plutôt que des entités. C'est un chantier distinct documenté en fin de plan.

À la place, les deux fichiers de config contiennent un commentaire explicatif du contexte :
```properties
# NOTE: spring.jpa.open-in-view is intentionally left at the Spring Boot default (true) until all
# admin/auth controller layers are refactored to initialize lazy associations inside @Transactional
# service boundaries. The shared-reference hazard on Integration.i18n is mitigated by steps 2-5.
```

### 2. Ajouter une projection scalaire `findTenantIdByIntegrationId` dans `IntegrationRepository` ✅

Remplacer l'accès à l'entité complète dans `resolveTenantId` par une requête qui retourne uniquement un `Integer` (tenantId) sans charger l'entité `Integration` dans la session :

```jpql
SELECT i.tenant.tenantId FROM Integration i WHERE i.id = :id
```

Retour : `Optional<Integer>`.

### 3. Mettre à jour `resolveTenantId` dans `EnrollmentController` ✅

Remplacer la chaîne `findById` → `.map(Integration::getTenant)` par l'appel direct à `findTenantIdByIntegrationId`. L'entité `Integration` n'est plus jamais chargée dans la session du contrôleur. Retirer l'import `Integration`, retirer l'usage de `integrationRepository::findById`. Vérifier si d'autres usages de `integrationRepository` existent dans ce contrôleur — si non, considérer de déplacer `resolveTenantId` dans un helper partagé.

### 4. Ajouter une méthode `findSystemIntegrationReadOnly` dans `IntegrationRepository` ✅

Créer une variante read-only de `findByIsSystemIntegrationAndActiveTrue` :

```jpql
SELECT i FROM Integration i LEFT JOIN FETCH i.tenant WHERE i.isSystemIntegration = true AND i.active = true
```

Avec `@QueryHints(@QueryHint(name = "org.hibernate.readOnly", value = "true"))`. Note : pas besoin de `LEFT JOIN FETCH i.i18n` ici car `AdminProvisioningService` n'utilise que `getId()`.

### 5. Mettre à jour les 3 callers dans admin-api ✅

- `AdminProvisioningService` ligne ~256 → `findSystemIntegrationReadOnly()`
- `AdminProvisioningService` ligne ~377 → `findSystemIntegrationReadOnly()`
- `AdminBootstrapService` ligne ~174 → `findSystemIntegrationReadOnly()` (pour la branche "already exists") — garder `findByIsSystemIntegrationAndActiveTrue` pour la branche de création (où l'entité restera modifiable)

### 6. Corriger le double-chargement d'enrollment dans `EnrollmentVerifyService` ✅

Le pattern snapshot (step 1, `findById`) + lock (step 5, `findAndLockBoundById`) produit deux objets Java pour le même enrollment dans la même session, causant le double-fire de `EncryptionEntityListener`. Résolu en remplaçant `enrollmentRepository.save(enrollment)` par `enrollmentRepository.saveAndFlush(enrollment)` dans `markAsVerified`, ce qui force le flush dans la frontière transactionnelle et élimine tout résidu dirty.

### 7. Documenter le pattern dans `IntegrationRepository` ✅

Ajouter un commentaire de mise en garde sur `findByIsSystemIntegrationAndActiveTrue` (méthode conservée pour création/modification) indiquant d'utiliser `findSystemIntegrationReadOnly` pour toute lecture.

---

## Verification

- Relancer la suite de tests fonctionnels (clean start) → absence de `JpaSystemException: Found shared references to a collection`
- Vérifier dans les logs que `EncryptionEntityListener` ne fire plus qu'une seule fois par enrollment dans le flow verify
- `mvn clean verify` pour validation build + checkstyle

---

## Décisions

- **`open-in-view=false` tenté puis annulé** : activé en phase d'implémentation, a causé 50 régressions de tests fonctionnels. L'admin-api mappe des associations lazy (`Integration.i18n`) depuis la couche contrôleur/MapStruct *après* la fin de la transaction de service — OEMIV masquait ce pattern. Sans OEMIV, `LazyInitializationException` → HTTP 500. Le correctif réel du bug des shared references est assuré par les étapes 2-5, sans OEMIV. Voir chantier futur.
- **Pas de cache applicatif** : l'introduction d'un cache Spring (`@Cacheable`) serait de la complexité accidentelle. Le read-only hint + projections scalaires est la bonne pratique JPA pour ce pattern.
- **`List` vs `Set` pour `i18n`** : changer `List` en `Set` réduirait la sévérité du problème (Hibernate gère les `Set` avec `PersistentSet` qui a une identité plus forte), mais ce serait un contournement sans adresser la cause racine.

---

## Chantier futur : `open-in-view=false` propre

Pour éliminer définitivement OEMIV (bonne pratique REST, libération plus rapide des connexions DB), il faudra refactorer l'ensemble des services de l'admin-api selon le pattern suivant :

**Principe** : toute conversion MapStruct qui touche une association lazy doit s'effectuer *à l'intérieur* du `@Transactional`, en retournant un DTO plutôt qu'une entité.

**Avant (problématique avec `open-in-view=false`)** :
```java
// Service — retourne l'entité, transaction fermée
@Transactional(readOnly = true)
public Integration getById(Integer id) {
    return integrationRepository.findById(id).orElseThrow(...);
}

// Contrôleur — accès lazy hors transaction → LazyInitializationException
Integration integration = service.getById(id);
return ResponseEntity.ok(mapper.toResponse(integration)); // accède i18n ici
```

**Après (compatible `open-in-view=false`)** :
```java
// Service — convertit en DTO dans la transaction, associations initialisées
@Transactional(readOnly = true)
public IntegrationResponseDto getById(Integer id) {
    Integration integration = integrationRepository.findByIdWithI18nAndTenant(id)
        .orElseThrow(...);
    return mapper.toResponse(integration); // i18n déjà chargé, dans la transaction
}

// Contrôleur — reçoit un DTO, aucun accès lazy possible
return ResponseEntity.ok(service.getById(id));
```

**Portée estimée** : tous les `@Service` de `ezkey-admin-api` et `ezkey-auth-api` qui retournent des entités `Integration` ou `EzkeyAdmin` avec associations lazy. Travail à planifier dans un sprint dédié au refactoring de la couche service.

Une fois ce refactoring complété, `spring.jpa.open-in-view=false` peut être activé dans les deux `application.properties` (les commentaires préparatoires sont déjà en place).
