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

### 1. Désactiver `open-in-view` dans auth-api et admin-api

C'est la correction la plus importante. Ajouter dans `application.yml` (ou la section JPA) des modules `ezkey-auth-api` et `ezkey-admin-api` :

```yaml
spring:
  jpa:
    open-in-view: false
```

Cela élimine la classe entière de bugs de pollution de session OEMIV : chaque appel de service obtient sa propre session Hibernate, cloisonnée à sa transaction `@Transactional`. C'est la bonne pratique standard pour toute REST API.

### 2. Ajouter une projection scalaire `findTenantIdByIntegrationId` dans `IntegrationRepository`

Remplacer l'accès à l'entité complète dans `resolveTenantId` par une requête qui retourne uniquement un `Integer` (tenantId) sans charger l'entité `Integration` dans la session :

```jpql
SELECT i.tenant.tenantId FROM Integration i WHERE i.id = :id
```

Retour : `Optional<Integer>`.

### 3. Mettre à jour `resolveTenantId` dans `EnrollmentController`

Remplacer la chaîne `findById` → `.map(Integration::getTenant)` par l'appel direct à `findTenantIdByIntegrationId`. L'entité `Integration` n'est plus jamais chargée dans la session du contrôleur. Retirer l'import `Integration`, retirer l'usage de `integrationRepository::findById`. Vérifier si d'autres usages de `integrationRepository` existent dans ce contrôleur — si non, considérer de déplacer `resolveTenantId` dans un helper partagé.

### 4. Ajouter une méthode `findSystemIntegrationReadOnly` dans `IntegrationRepository`

Créer une variante read-only de `findByIsSystemIntegrationAndActiveTrue` :

```jpql
SELECT i FROM Integration i LEFT JOIN FETCH i.tenant WHERE i.isSystemIntegration = true AND i.active = true
```

Avec `@QueryHints(@QueryHint(name = "org.hibernate.readOnly", value = "true"))`. Note : pas besoin de `LEFT JOIN FETCH i.i18n` ici car `AdminProvisioningService` n'utilise que `getId()`.

### 5. Mettre à jour les 3 callers dans admin-api

- `AdminProvisioningService` ligne ~256 → `findSystemIntegrationReadOnly()`
- `AdminProvisioningService` ligne ~377 → `findSystemIntegrationReadOnly()`
- `AdminBootstrapService` ligne ~174 → `findSystemIntegrationReadOnly()` (pour la branche "already exists") — garder `findByIsSystemIntegrationAndActiveTrue` pour la branche de création (où l'entité restera modifiable)

### 6. Corriger le double-chargement d'enrollment dans `EnrollmentVerifyService`

Le pattern snapshot (step 1, `findById`) + lock (step 5, `findAndLockBoundById`) produit deux objets Java pour le même enrollment dans la même session, causant le double-fire de `EncryptionEntityListener`. Remplacer le `findById` de step 1 par une validation sans chargement d'entité (requête d'existence/statut), ou éviter de garder l'enrollment de step 1 dans la session en faisant la validation de step 1 dans un contexte `readOnly = true` séparé. Alternativement, après step 5, `evict` l'entité de step 1 de la session avant le flush.

### 7. Documenter le pattern dans `IntegrationRepository`

Ajouter un commentaire de mise en garde sur `findByIsSystemIntegrationAndActiveTrue` (méthode conservée pour création/modification) indiquant d'utiliser `findSystemIntegrationReadOnly` pour toute lecture.

---

## Verification

- Relancer la suite de tests fonctionnels (clean start) → absence de `JpaSystemException: Found shared references to a collection`
- Vérifier dans les logs que `EncryptionEntityListener` ne fire plus qu'une seule fois par enrollment dans le flow verify
- `mvn clean verify` pour validation build + checkstyle

---

## Décisions

- **`open-in-view=false` vs. projections seules** : les deux mesures sont complémentaires. `open-in-view=false` élimine la cause racine de contamination de session entre contrôleur et service. Les projections et hints read-only sont une défense en profondeur valide même sans OEMIV (charges inutiles évitées, dirty-checking réduit).
- **Pas de cache applicatif** : l'introduction d'un cache Spring (`@Cacheable`) serait de la complexité accidentelle. Le read-only hint + session cloisonnée est la bonne pratique JPA pour ce pattern.
- **`List` vs `Set` pour `i18n`** : changer `List` en `Set` réduirait la sévérité du problème (Hibernate gère les `Set` avec `PersistentSet` qui a une identité plus forte), mais ce serait un contournement sans adresser la cause racine.
