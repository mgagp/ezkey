## Plan : Enrichissement du modèle Tenant — Tier 1 Essentiel

Le Tier 1 ajoute les champs d'identité organisationnelle, de contact opérationnel, et de gouvernance du cycle de vie au modèle Tenant. Ces champs sont les prérequis minimaux pour SOC 2 (CC2.1, CC6.1, CC6.3, CC7.2) et pour opérer une plateforme MFA de façon responsable : savoir à qui on a affaire, pouvoir les joindre, et tracer les changements. **Les Tiers 2 (quotas, extensibilité) et 3 (politiques de sécurité par tenant) sont explicitement reportés à des phases ultérieures.**

---

### Champs ajoutés par ce plan

| Champ | Type DB | Nullable | Justification |
|-------|---------|----------|---------------|
| `organization_name` | `VARCHAR(255)` | Oui | Nom légal de l'organisation (distinct de `tenant_name` qui est un identifiant court/technique) |
| `organization_domain` | `VARCHAR(255)` | Oui | Domaine principal (`acme.com`). Corrélation emails, futur SSO. |
| `country_code` | `CHAR(2)` | Oui | ISO 3166-1. Juridiction légale, résidence des données. |
| `timezone` | `VARCHAR(50)` | Oui | IANA timezone. Rapports d'audit, notifications dans le fuseau du tenant. |
| `primary_contact_name` | `VARCHAR(255)` | Oui | Responsable technique. SOC 2 CC2.1 — point de contact identifiable. |
| `primary_contact_email` | `VARCHAR(255)` | Oui | Canal de notification incidents/expirations. Essentiel opérationnel. |
| `updated_at` | `TIMESTAMPTZ` | Oui | Dernière modification. Absent aujourd'hui — requis pour audit trail (SOC 2 CC7.2). |
| `updated_by_admin_id` | `INT FK` | Oui | Qui a fait la modification. Traçabilité complète. |
| `deactivated_at` | `TIMESTAMPTZ` | Oui | Quand le tenant a été désactivé. La désactivation actuelle ne laisse aucune trace temporelle. |
| `deactivated_by_admin_id` | `INT FK` | Oui | Qui a désactivé. SOC 2 CC6.3. |

Tous les nouveaux champs sont **nullable** — aucun impact sur les tenants existants ni sur le bootstrap du system tenant. Rétro-compatibilité totale.

---

### Steps

**Step 1 — Migration Flyway V30**

Créer `ezkey-core/src/main/resources/db/migration/V30__enrich_tenant_identity_governance.sql`. La migration ajoute 10 colonnes à `ezkey_tenant` :
- 4 colonnes d'identité organisationnelle : `organization_name`, `organization_domain`, `country_code`, `timezone`
- 2 colonnes de contact : `primary_contact_name`, `primary_contact_email`
- 4 colonnes de gouvernance : `updated_at`, `updated_by_admin_id` (FK vers `ezkey_admin`), `deactivated_at`, `deactivated_by_admin_id` (FK vers `ezkey_admin`)
- Index sur `organization_domain` (requêtes futures par domaine)
- Index sur `country_code` (filtrage par juridiction)
- Contrainte CHECK sur `country_code` : longueur exactement 2, majuscules uniquement (`country_code ~ '^[A-Z]{2}$'`)
- Contrainte CHECK sur `primary_contact_email` : format email basique (`primary_contact_email ~ '^[^@]+@[^@]+\.[^@]+$'`)
- Commentaire SQL sur chaque colonne pour documentation du schéma

**Step 2 — Mise à jour de l'entité `Tenant`**

Modifier `ezkey-core/src/main/java/org/ezkey/integration/domain/entity/Tenant.java` :
- Ajouter les 10 nouveaux champs avec annotations JPA (`@Column`), Javadoc complète, getters et setters manuels (pas de Lombok, conformément aux conventions)
- `updatedByAdmin` et `deactivatedByAdmin` sont des `@ManyToOne(LAZY)` vers `EzkeyAdmin` avec `@JoinColumn`
- `countryCode` : `@Column(length = 2)`
- `timezone` : `@Column(length = 50)`
- Le constructeur `Tenant(String tenantName, String tenantDescription)` reste inchangé — les nouveaux champs restent null par défaut
- Ajouter un second constructeur enrichi : `Tenant(String tenantName, String tenantDescription, String organizationName, String primaryContactEmail)` pour les cas de création complète
- Mettre à jour `toString()` pour inclure les nouveaux champs significatifs

**Step 3 — Nouveau DTO : `TenantUpdateRequestDto`**

Créer `ezkey-admin-api/src/main/java/org/ezkey/admin/dto/request/TenantUpdateRequestDto.java` — un Java record avec :
- `tenantName` (`@Size(min=3, max=100)`, nullable — si présent, déclenche un rename)
- `tenantDescription` (`@Size(max=500)`, nullable)
- `organizationName` (`@Size(max=255)`, nullable)
- `organizationDomain` (`@Size(max=255)`, nullable, `@Pattern` pour format domaine)
- `countryCode` (`@Size(min=2, max=2)`, nullable, `@Pattern(regexp="^[A-Z]{2}$")`)
- `timezone` (`@Size(max=50)`, nullable)
- `primaryContactName` (`@Size(max=255)`, nullable)
- `primaryContactEmail` (`@Email`, `@Size(max=255)`, nullable)
- Sémantique de mise à jour partielle (PATCH-like) : seuls les champs non-null sont mis à jour. Les champs null dans le request body ne sont pas modifiés.
- Annotations OpenAPI `@Schema` avec descriptions et exemples

**Step 4 — Mise à jour du `TenantCreateRequestDto`**

Modifier `ezkey-admin-api/src/main/java/org/ezkey/admin/dto/request/TenantCreateRequestDto.java` :
- Ajouter les champs optionnels : `organizationName`, `organizationDomain`, `countryCode`, `timezone`, `primaryContactName`, `primaryContactEmail`
- Mêmes validations que `TenantUpdateRequestDto`
- Tous optionnels à la création (rétro-compatibilité API)

**Step 5 — Mise à jour du `TenantResponseDto`**

Modifier `ezkey-admin-api/src/main/java/org/ezkey/admin/dto/response/TenantResponseDto.java` :
- Ajouter tous les nouveaux champs : `organizationName`, `organizationDomain`, `countryCode`, `timezone`, `primaryContactName`, `primaryContactEmail`, `updatedAt`, `isSystemTenant`
- **Ne pas exposer** `updatedByAdminId`, `deactivatedAt`, `deactivatedByAdminId` dans le DTO standard — ces champs d'audit interne sont réservés à un futur DTO d'audit détaillé (Tier 2)
- Annotations `@Schema` avec exemples

**Step 6 — Créer `TenantMapper` (MapStruct)**

Créer `ezkey-admin-api/src/main/java/org/ezkey/admin/mapper/TenantMapper.java` — une interface MapStruct suivant le pattern de `IntegrationControllerMapper` :
- `@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.WARN)`
- `TenantResponseDto toResponseDto(Tenant tenant)` — mapping entity → response
- `List<TenantResponseDto> toResponseDtoList(List<Tenant> tenants)` — mapping liste
- `@Mapping(target = "tenantId", ignore = true)` et autres champs auto-générés ignorés sur les mappings request → entity
- Ce mapper **remplace** le mapping inline fait actuellement dans le contrôleur

**Step 7 — Mise à jour du `TenantService`**

Modifier `ezkey-admin-api/src/main/java/org/ezkey/admin/service/TenantService.java` :
- Ajouter la méthode `updateTenant(Integer tenantId, TenantUpdateRequestDto request, AdminPrincipal principal)` :
  - Charger le tenant ou lancer `ResourceNotFoundException`
  - Bloquer les mises à jour si tenant inactif (réutiliser `ensureTenantActive`)
  - Si `tenantName` est modifié, vérifier l'unicité via `existsByTenantName()` (exclure le tenant courant)
  - Appliquer les champs non-null du DTO sur l'entité (mise à jour partielle)
  - Positionner `updatedAt = OffsetDateTime.now()` et `updatedByAdmin` vers l'admin actuel
  - Sauvegarder et retourner le tenant
- Modifier `deactivateTenant()` existant :
  - Positionner `deactivatedAt = OffsetDateTime.now()` et `deactivatedByAdmin` vers l'admin actuel lors de la désactivation (en plus du `active = false` existant)
  - Positionner `updatedAt` et `updatedByAdmin` aussi (la désactivation est une modification)

**Step 8 — Mise à jour du `AdminProvisioningService.createTenant()`**

Modifier la méthode `createTenant()` dans `ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminProvisioningService.java` :
- Accepter les nouveaux champs optionnels provenant du `TenantCreateRequestDto` enrichi
- Les propager à l'entité `Tenant` lors de la construction

**Step 9 — Mise à jour du `TenantController`**

Modifier `ezkey-admin-api/src/main/java/org/ezkey/admin/controller/TenantController.java` :
- **Injecter `TenantMapper`** et remplacer tous les mappings inline `new TenantResponseDto(...)` par des appels à `tenantMapper.toResponseDto()` dans les 3 endpoints GET/POST existants
- **Ajouter `PUT /api/v1/tenants/{id}`** — premier endpoint PUT du projet :
  - `@PreAuthorize("hasRole('ROLE_GLOBAL_ADMIN')")` — seuls les global admins peuvent modifier les tenants
  - Accepte `@Valid @RequestBody TenantUpdateRequestDto`
  - Délègue à `TenantService.updateTenant()`
  - Retourne `ResponseEntity<TenantResponseDto>` avec HTTP 200
  - Documentation OpenAPI complète (`@Operation`, `@ApiResponses`)
- Mettre à jour le Javadoc de classe pour lister le nouvel endpoint

**Step 10 — Ajout de la méthode au `TenantRepository`**

Modifier `ezkey-core/src/main/java/org/ezkey/integration/domain/repository/TenantRepository.java` :
- Ajouter `boolean existsByTenantNameAndTenantIdNot(String tenantName, Integer tenantId)` — vérifie l'unicité du nom en excluant le tenant en cours d'édition (pour le rename)
- Ajouter `Optional<Tenant> findByOrganizationDomain(String domain)` — recherche par domaine (utile à terme)

**Step 11 — Tests unitaires**

Modifier `ezkey-admin-api/src/test/java/org/ezkey/admin/service/TenantServiceTest.java` :
- Ajouter une classe `@Nested` `UpdateTenant` avec les cas :
  - Mise à jour partielle (un seul champ) — les autres restent inchangés
  - Mise à jour complète (tous les champs)
  - Rename avec vérification d'unicité — nom déjà pris → exception
  - Rename avec le même nom (pas de changement réel) → succès
  - Mise à jour d'un tenant inactif → `TenantInactiveException`
  - Mise à jour du system tenant → autorisé (seule la désactivation est interdite)
  - Tenant inexistant → `ResourceNotFoundException`
- Modifier les tests de `deactivateTenant` existants pour vérifier que `deactivatedAt` et `deactivatedByAdmin` sont positionnés

**Step 12 — Tests d'intégration**

Créer ou enrichir les tests d'intégration dans le module `ezkey-tests` :
- Ajouter des scénarios dans `ezkey-tests/src/test/java/org/ezkey/tests/security/tenant/TenantBasicOperationsTest.java` :
  - PUT update tenant avec champs enrichis → 200
  - PUT update tenant avec nom dupliqué → 409 ou 400
  - PUT update tenant inactif → 403
  - Vérifier que GET retourne les nouveaux champs après update
  - Créer un tenant avec les champs enrichis → vérifier la persistance
  - Vérifier `updatedAt` automatiquement positionné après un PUT

---

### Vérification

- `mvn clean verify` — compilation, tests unitaires et d'intégration
- `mvn checkstyle:check` — conformité Google Java Style
- Flyway migration : la migration V30 s'applique sans erreur sur une base existante avec le system tenant et des tenants applicatifs déjà créés
- Postman / curl : `PUT /api/v1/tenants/{id}` avec un body partiel (un seul champ) et un body complet
- Vérifier que les endpoints GET existants retournent les nouveaux champs (nulls pour les tenants non encore enrichis)
- Vérifier rétro-compatibilité : `POST /api/v1/tenants` sans les nouveaux champs fonctionne toujours

---

### Décisions

- **PUT vs PATCH** : Choisi PUT avec sémantique de mise à jour partielle (champs null ignorés) plutôt que PATCH+JSON Patch. Plus simple, cohérent avec le style REST du projet (pas de PATCH existant), et suffisant pour le nombre de champs.
- **Champs nullable** : Tous les nouveaux champs sont nullable pour la rétro-compatibilité. On pourra rendre certains obligatoires (ex: `primaryContactEmail`) dans un Tier 2 une fois les tenants existants enrichis.
- **Gouvernance dans la table principale** : Les champs `updatedAt`, `updatedByAdmin`, `deactivatedAt`, `deactivatedByAdmin` sont dans `ezkey_tenant` (pas une table séparée) car ils font partie du cycle de vie fondamental de l'entité.
- **Pas d'exposition des champs d'audit de désactivation** dans le DTO standard : `deactivatedAt` et `deactivatedByAdminId` seront réservés à un futur DTO d'audit enrichi pour éviter de surcharger la réponse courante.
- **MapStruct mapper** : Corrige la dette technique actuelle (mapping inline dans le controller) et prépare les enrichissements futurs.

---

### Hors périmètre (Tiers 2 et 3 — phases ultérieures)

| Éléments | Phase |
|----------|-------|
| Quotas par tenant (`max_integrations`, `max_enrollments`, `max_admins`) | Tier 2 |
| `external_id`, `metadata` (JSONB), `notes` | Tier 2 |
| `technical_contact_email`, `billing_contact_email` | Tier 2 |
| `data_retention_days` | Tier 2 |
| Table `ezkey_tenant_config` (clé-valeur) | Tier 2 |
| `mfa_policy`, `allowed_ip_cidrs`, `session_timeout_minutes` | Tier 3 |
| `rate_limit_tier`, `enrollment_expiry_days` | Tier 3 |
| `industry_type`, `require_admin_mfa` | Tier 3 |
| `tenant_status` enum (remplacer boolean `active`) | Tier 2 |
| Endpoint de réactivation | Tier 2 |
| Pagination sur `GET /api/v1/tenants` | Tier 2 |
