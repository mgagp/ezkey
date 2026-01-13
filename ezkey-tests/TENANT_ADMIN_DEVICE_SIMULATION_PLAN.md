# Plan: Device Simulation TenantAdmin pour Tests Isolation Multi-Tenant

Le système utilise `POST /admin/auth/login` pour tous les admins. Les TenantAdmin obtiennent leurs onboarding credentials via `GET /admins/{id}/onboarding` (enrollmentProofToken, enrollmentChallenge, enrollmentId). Les device credentials sont **réutilisés** via fichiers cache séparés par tenant. Les recovery codes (null) sont **ignorés**. Focus P0: **(A) isolation cross-tenant** et **(B) boundary permissions tenant-level**.

## Steps

1. Analyser le **flow device simulation GlobalAdmin** : Examiner [AuthTokenManager](ezkey-tests/src/test/java/org/ezkey/tests/auth/AuthTokenManager.java) pour comprendre la stratégie 3-tier (token cache → device credentials → bootstrap) et identifier les parties réutilisables pour TenantAdmin.

2. Créer **TenantAdminTestHelper** inspiré du pattern [createApiKeyForIntegration](ezkey-tests/src/test/java/org/ezkey/tests/security/enrollment/EnrollmentManagementSecurityTest.java#L346) : Implémenter `createAndLoginTenantAdmin(username, tenantId)` qui crée l'admin via [TestDataFactory](ezkey-tests/src/test/java/org/ezkey/tests/util/TestDataFactory.java), récupère onboarding, génère/lie device keys avec [CryptoApiClient](ezkey-tests/src/test/java/org/ezkey/tests/crypto/CryptoApiClient.java), effectue login, retourne token immédiatement utilisable.

3. Implémenter **cache device credentials par tenant** : Créer `.ezkey-test/tenant-admin-{tenantId}-device-credentials.json` et `.ezkey-test/tenant-admin-{tenantId}-token.json` pour réutilisation entre tests, avec validation token avant réutilisation (stratégie 3-tier adaptée).

4. Enrichir **TestDataFactory avec réutilisation opportuniste** : Ajouter `findOrCreateTenant(name)`, `findOrCreateIntegration(name, tenantId)` qui recherchent des entités existantes par nom avant création, réduisant overhead dans tests répétés.

5. Créer tests **isolation cross-tenant (A)** : TenantAdmin A tente d'accéder aux ressources de Tenant B (GET/POST/DELETE integrations, enrollments, api-keys d'autre tenant) et valider que listing ne retourne que son scope (expect 403/404).

6. Créer tests **boundary permissions tenant-level (B)** : TenantAdmin tente des opérations globales réservées au GlobalAdmin (POST/GET/DELETE /tenants, accès cross-tenant aux admins) et valider refus d'accès (expect 403).

7. Documenter dans **FUNCTIONAL_TESTING_GUIDE.md** : (a) mécanisme login unique, (b) `authTokenManager.getAdminToken()` pour GlobalAdmin, (c) `tenantAdminTestHelper.createAndLoginTenantAdmin()` pour TenantAdmin, (d) patterns indépendance (graceful degradation, timestamps), (e) template tests isolation multi-tenant.

## Further Considerations

1. **Structure cache multi-tenant**: Un seul fichier `.ezkey-test/tenant-admin-device-credentials.json` avec map `{tenantId: credentials}` ou fichiers séparés par tenant? **→ Fichiers séparés confirmé**

2. **Gestion recovery codes**: L'exemple montre `recoveryCodes: null`. Sont-ils optionnels pour TenantAdmin ou faut-il les gérer différemment du GlobalAdmin? **→ Ignorés pour l'instant (bug possible)**

3. **Autres tests P0**: Au-delà de (A) et (B), faut-il aussi tester que TenantAdmin ne peut pas modifier les admins d'autres tenants (`PUT /admins/{otherTenantAdminId}`) ou accéder aux API keys d'autres tenants? **→ S'en tenir aux tests identifiés pour avoir une base fonctionnelle**
