## Pourquoi deux APIs pour les credentials d'enrollment

### Le problème fondamental : un mur d'accès tenant

L'API originale `GET /api/v1/enrollments/{id}` valide les droits d'accès via la chaîne **enrollment → intégration → tenant**. Les enrollments des administrateurs (GlobalAdmin, TenantAdmin) sont rattachés à l'intégration système (`tenant_id = 1`, le System Tenant).

**Conséquence directe** : un TenantAdmin, dont le scope est limité à `tenant_id ≥ 2`, échoue systématiquement le check `AccessControlService.canAccessEnrollment()` avec un 403 — même pour *son propre* enrollment. Il ne peut donc jamais récupérer son QR code ni son proof token via l'API enrollment classique.

### La solution : un pivot d'accès différent

L'API `/api/v1/admins/{id}/onboarding` résout exactement ce problème en validant les droits via la chaîne **admin → tenant** au lieu d'**enrollment → intégration → tenant**. Un TenantAdmin peut accéder à son propre enregistrement parce que l'admin *appartient* bien à son tenant.

### Résumé des deux contextes

| | Enrollment API | Onboarding API |
|---|---|---|
| **Sujet** | Enrollments génériques liés à une intégration (cas D-to-D standard) | Credentials d'un administrateur (GlobalAdmin / TenantAdmin) |
| **Identifiant** | `enrollmentId` | `adminId` |
| **Pivot d'accès** | via intégration → tenant | via admin → tenant |
| **Peut être utilisé par TenantAdmin** | Non (403 structurel) | Oui |
| **Données retournées** | Enregistrement complet (statut, clés, timestamps…) | Credentials minimaux : proofToken, challenge, recoveryCodes (une seule fois) |

### Consommateurs

| Consommateur | Utilise |
|---|---|
| **App mobile Ezkey** | L'un ou l'autre (le payload QR est identique) |
| **GlobalAdmin** | Les deux fonctionnent ; `/api/v1/enrollments/{id}` donne plus de données |
| **TenantAdmin** | **Doit** utiliser `/api/v1/admins/{id}/onboarding` — enrollment API : 403 structurel |
| **Récupération après perte d'appareil** | `POST /api/v1/admin/enrollments/reset` (recovery token, pas de bearer) |

### En un mot

La duplication n'est pas conceptuelle — c'est une **nécessité d'autorisation**. L'API d'onboarding existe parce que le modèle multi-tenant rend structurellement impossible pour un TenantAdmin d'accéder à ses propres credentials via l'API enrollment standard. La documentation explicite de ce choix se trouve dans `docs/ENDPOINT.md` à la section *"When to Use Admin Onboarding API vs Enrollment API"*.

### Contrôleurs de référence

- `GET /api/v1/enrollments/{id}/qrcode` → `EnrollmentController`
- `GET /api/v1/admins/{id}/onboarding` → `AdminProvisioningController`
- `GET /api/v1/admins/{id}/onboarding/qrcode` → `AdminProvisioningController`
- `POST /api/v1/admin/enrollments/reset` → `AdminEnrollmentController` (recovery token)
