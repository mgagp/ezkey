## Plan: Stratégie de Gestion du Cycle de Vie des Enrollments — Révocation et Offboarding

### TL;DR

EZKey possède déjà le champ `active` sur l'entité Enrollment et un patron de désactivation éprouvé (tenants/admins), mais **aucun endpoint admin, aucune infrastructure d'événements, et aucun mécanisme webhook** pour la révocation d'enrollments. Le plan propose une approche progressive en 4 phases, alignée avec les valeurs du projet (simplicité, pragmatisme, developer-first), partant des fondations REST déjà existantes jusqu'à un support SCIM 2.0 optionnel pour l'entreprise. L'objectif est de permettre la révocation rapide d'enrollments — manuellement ou automatiquement — sans créer de dépendance à des services externes, tout en offrant les points d'intégration nécessaires aux intégrateurs.

---

### Analyse du marché et des normes

**Ce que font les concurrents :**

| Produit | Révocation manuelle | API de révocation | SCIM 2.0 | AD/LDAP Sync | Webhooks sortants |
|---------|:---:|:---:|:---:|:---:|:---:|
| **Duo** | Oui | Oui (Admin API) | Oui | Oui (AD Sync agent) | Oui |
| **Okta** | Oui | Oui | Oui | Oui (LDAP Agent) | Oui (Event Hooks) |
| **PrivacyIDEA** | Oui | Oui (REST) | Non | Oui (LDAP Resolvers) | Non |
| **EZKey (actuel)** | Non (delete seulement) | Non | Non | Non | Non |

**Ce que SOC 2 exige (contrôles pertinents) :**
- **CC6.1** — Logical access security: capacité de contrôler qui a accès
- **CC6.2** — Prior to issuing access: processus de provisioning
- **CC6.3** — Removal of access: capacité de **révoquer l'accès rapidement** lors du départ d'un employé — c'est le point central
- **CC7.1** — Monitoring: détection des anomalies (enrollments actifs sans activité, etc.)

**Ce qui est normativement acceptable pour un système comme EZKey :**
- SOC 2 n'exige **pas** de synchronisation AD/LDAP. Il exige la **capacité** de révoquer dans un délai raisonnable.
- Un **endpoint API de révocation** documenté + un **audit trail** de la révocation satisfait CC6.3
- Les webhooks entrants/sortants sont des facilitateurs, pas des exigences normatives

**Best practice du marché pour un outil developer-first :**
1. **API REST de révocation** — le minimum absolu (tous les concurrents l'ont)
2. **Webhooks entrants** — pour recevoir des commandes de systèmes externes (IAM, HR, scripts)
3. **Webhooks sortants** — pour notifier les systèmes tiers des changements de statut
4. **SCIM 2.0** — le standard d'entreprise pour le provisioning/deprovisioning automatisé
5. **AD/LDAP sync** — pour les environnements legacy Microsoft/enterprise

---

### Steps

#### Phase 1 — Fondation : Endpoint de Révocation d'Enrollment (Court terme, faible effort)

C'est la pièce manquante la plus critique. Le champ `active` existe déjà sur l'entité Enrollment, le auth flow dans AuthAttemptPendingService le respecte déjà via `findByEnrollmentProofTokenHashAndActive(hash, true)`, et le patron de désactivation existe pour les tenants dans TenantService.

1. **Ajouter `REVOKED` à `EnrollmentStatus`** dans EnrollmentStatus.java — sémantiquement distinct de `INVALID` (admin-initiated vs system-initiated)

2. **Ajouter les event types d'audit** : `ENROLLMENT_REVOKED`, `ENROLLMENT_DEACTIVATED`, `ENROLLMENT_REACTIVATED` dans EventType.java

3. **Créer l'endpoint `POST /api/v1/enrollments/{id}/revoke`** dans EnrollmentController.java — suivre exactement le patron de `POST /api/v1/tenants/{id}/deactivate` : idempotent, audit trail, `204 No Content`

4. **Créer l'endpoint `POST /api/v1/enrollments/{id}/deactivate`** et `POST /api/v1/enrollments/{id}/reactivate` — pattern on/off réversible, distinct de "revoke" qui est définitif

5. **Ajouter les champs d'audit sur Enrollment** : `deactivatedAt`, `deactivatedByAdmin`, `revokedAt`, `revokedByAdmin` — suivre le patron de `Tenant` pour SOC 2 CC6.3

6. **Corriger le gap dans AuthAttemptService** — vérifier `enrollment.getActive()` lors de la **création** d'un auth attempt, pas seulement lors du polling. Un enrollment révoqué/désactivé ne devrait pas pouvoir recevoir de nouvelles tentatives d'authentification.

7. **Ajouter un endpoint bulk** : `POST /api/v1/integrations/{id}/enrollments/revoke-all` — pour révoquer tous les enrollments d'une intégration en une opération (use case: compromission d'un service)

8. **Migration Flyway** pour les nouveaux champs d'audit sur la table enrollment

#### Phase 2 — Webhook Entrant : Recevoir des Commandes de Révocation (Moyen terme, effort modéré)

L'idée ici est de permettre à un système externe (script RH, AD connector maison, Zapier, n8n, etc.) d'appeler EZKey pour déclencher une révocation. C'est le **pont pragmatique** qui ne crée pas de dépendance directe tout en permettant l'automatisme.

1. **Les endpoints de Phase 1 servent déjà de webhook entrant** — un intégrateur peut simplement appeler `POST /enrollments/{id}/revoke` avec une API key. En réalité, la Phase 1 active déjà ce use case via les API keys existantes (`ROLE_API_KEY`).

2. **Étendre le scope des API keys** — actuellement, `ROLE_API_KEY` ne peut que créer/lire des auth attempts (voir API_SECURITY_MATRIX.md). Ajouter une permission granulaire `ENROLLMENT_REVOKE` pour permettre la révocation via API key sans donner l'accès admin complet. C'est le compromis pragmatique : pas un nouveau système d'authentification, juste un nouveau scope.

3. **Ajouter un endpoint de recherche d'enrollment par identifiant externe** : `GET /api/v1/enrollments?externalId={value}` — pour faciliter le bridging. Le système appelant connaît l'employé par un ID externe (email, employeeId AD, etc.), pas par l'enrollmentId interne d'EZKey. Cela nécessite un champ `externalId` optionnel sur l'enrollment.

4. **Documenter le pattern d'intégration** — créer un guide "Revocation Integration Guide" montrant comment connecter EZKey avec un script AD PowerShell, un webhook Zapier, ou un pipeline RH.

#### Phase 3 — Webhooks Sortants : Notifier les Systèmes Externes (Moyen-long terme, effort modéré)

Permet à EZKey de **pousser** des événements vers des systèmes tiers quand un enrollment change d'état.

1. **Infrastructure d'événements interne** — Implémenter `ApplicationEventPublisher` de Spring pour les événements du domaine (`EnrollmentRevokedEvent`, `EnrollmentDeactivatedEvent`, `EnrollmentCreatedEvent`, etc.). Ces événements sont d'abord consommés localement pour l'audit.

2. **Modèle de données webhook** — Nouvelle entité `WebhookSubscription` : `url`, `secret` (pour HMAC signature), `events[]` (liste d'événements écoutés), `integrationId` (scope), `active`, `retryPolicy`. Table Flyway associée.

3. **Endpoints CRUD pour les webhooks** : `POST/GET/DELETE /api/v1/webhooks` — permet aux admins de s'abonner à des événements

4. **Dispatcher asynchrone** — Listener Spring qui consomme les domain events et dispatche des HTTP POST vers les URL enregistrées, avec signature HMAC du payload, retry exponentiel, et dead-letter logging

5. **Payload standard** — Format de webhook inspiré de GitHub/Stripe : header `X-Ezkey-Signature`, body JSON avec `eventType`, `timestamp`, `data` (enrollment complet), `webhookId`

6. **Pas de dépendance externe** — Pas de RabbitMQ, Kafka, ou Redis. Utiliser la base de données existante pour la file d'attente (table `webhook_delivery` avec statut), un `@Scheduled` job pour le retry, et `@Async` pour le dispatch non-bloquant. C'est le pragmatisme EZKey.

#### Phase 4 — SCIM 2.0 Provisioning (Long terme, effort significatif)

Quand EZKey visera le marché entreprise avec des clients SOC 2 Type II et des gros déploiements.

1. **SCIM 2.0 Server partiel** — Implémenter uniquement les opérations `/Users` pertinentes : `PATCH` (pour deprovisioning, car c'est ce que AD/Entra ID et Okta utilisent). Mappée sur la révocation/désactivation d'enrollment.

2. **Schema mapping** — Le `User` SCIM se mappe sur un concept de "personne" au-dessus de l'enrollment. Comme EZKey n'a **pas de concept d'utilisateur** (seulement des enrollments), deux options :
   - **Option A** : SCIM `User.active = false` → révoquer tous les enrollments avec le `externalId` correspondant
   - **Option B** : Créer un concept léger de `Person` pour regrouper des enrollments (plus complexe)

3. **Module séparé `ezkey-scim`** — Pour ne pas polluer le core avec la complexité SCIM. Dépend de `ezkey-core`, expose ses propres endpoints `/scim/v2/Users`.

4. **Conformité partielle délibérée** — SCIM 2.0 est un énorme standard (RFC 7643/7644). Implémenter seulement le subset nécessaire pour le deprovisioning automatisé, bien documenté avec `/Schemas` et `/ServiceProviderConfig` pour indiquer les capabilities supportées.

---

### Architecture évolutive recommandée

```
Système externe (AD, HR, IAM)          EZKey
         │                                │
Phase 1: │── HTTP REST ──────────────────▶│ POST /enrollments/{id}/revoke
         │                                │ (API key auth, audit logged)
         │                                │
Phase 2: │── Script/Connector ───────────▶│ GET /enrollments?externalId=...
         │  (PowerShell, Python, n8n)     │ POST /enrollments/{id}/revoke
         │                                │
Phase 3: │◀── Webhook notification ──────│ POST {subscriber_url}
         │   (enrollment.revoked event)   │ (HMAC signed, async)
         │                                │
Phase 4: │── SCIM 2.0 ──────────────────▶│ PATCH /scim/v2/Users/{id}
         │  (Azure AD/Entra auto-sync)    │ {"active": false}
```

---

### Verification

- **Phase 1** : Tests unitaires + intégration pour revoke/deactivate/reactivate endpoints. Vérifier que l'audit trail est complet. Vérifier qu'un enrollment révoqué ne peut plus recevoir d'auth attempts. Exécuter `mvn clean verify`.
- **Phase 2** : Test E2E avec un script curl/Python qui recherche un enrollment par `externalId` et le révoque via API key.
- **Phase 3** : Test d'intégration avec un mock server qui reçoit les webhooks, vérifie la signature HMAC, et valide le payload.
- **Phase 4** : Tests de conformité SCIM avec les outils de test standard (Runscope SCIM test suite).

### Decisions

- **Deactivate vs Revoke** : Deux opérations distinctes — `deactivate` est réversible (on/off), `revoke` est définitif (destruction des credentials crypto). Ce modèle est aligné avec la pratique du marché (Duo fait cette distinction).
- **Pas de dépendance middleware** : Les webhooks sortants utilisent la DB PostgreSQL comme file d'attente, pas un broker de messages externe. Cohérent avec la valeur de simplicité et d'autonomie.
- **SCIM comme module séparé** : Pour protéger le core de la complexité SCIM et permettre aux déploiements simples de l'ignorer complètement.
- **`externalId` sur Enrollment** : Le champ pont entre l'identité dans le système source (AD, HR) et l'enrollment EZKey. Sans ce champ, le bridging automatisé est impraticable.
- **Phase 1 est suffisante pour SOC 2** : Un endpoint API documenté + audit trail + procédure documentée de révocation satisfait CC6.3. Les phases suivantes sont des améliorations opérationnelles, pas des nécessités normatives.
