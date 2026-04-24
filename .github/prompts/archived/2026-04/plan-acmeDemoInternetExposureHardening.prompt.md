# Plan : Acme Demo — Internet Exposure Hardening

> **Status : COMPLETED — 2026-04-24**

**TL;DR** : Consolidation des deux plans parallèles ("Acme Demo Web Exposure Hardening" et "Acme Experimental Internet Exposure"). L'objectif a été atteint : `ezkey-demo-app-acme` peut être exposé sur Internet derrière Cloudflare + Caddy comme bac à sable d'évaluation pour des tenant admins invités, avec isolation par session de la clé API, rate limiting et résolution d'IP réelle derrière proxy de confiance. La couche UX a été refondue pour expliquer explicitement le modèle de session et préserver la clé API à travers le logout dans la même session navigateur.

## Résultat atteint

### Modèle de credentials et sécurité applicative

- Clé API stockée **par `HttpSession`** via `DemoApiKeyConfigService` (fin du modèle global JVM)
- `EzkeyClientProvider.getClient(HttpSession)` propagé dans tous les controllers (`LoginController`, `BusinessApprovalController`, `HomeController`)
- Isolation vérifiée : une session navigateur ne voit jamais les credentials d'une autre
- Logout sélectif dans `HomeController` : nettoie auth + pending, **préserve** `demoIntegrationKey` / `demoSecretKey`
- `SecurityConfig` explicite : CSRF (`CsrfTokenRequestAttributeHandler` + `HttpSessionCsrfTokenRepository`, header `X-CSRF-TOKEN`), `permitAll`, basic/form/logout désactivés, `/actuator/**` ignoré pour CSRF
- Cookies de session HttpOnly + SameSite=Lax + Secure (contrôlé par env), session timeout 20 min
- Actuator restreint à `health`

### Durcissement public-web

- Rate limiting in-memory **Bucket4j + Caffeine** sur `/login` (10 req / 5 min) et `/api/apply-api-key` (5 req / 10 min)
- HTTP 429 + header `Retry-After` + message UI dédié (`?error=ratelimited`)
- `ClientIpResolver` (lib `ipaddress`) : résolution IP réelle uniquement si le remote addr appartient à un CIDR de confiance ; ordre `CF-Connecting-IP` → `X-Forwarded-For[0]` → `X-Real-IP`
- `AcmeRateLimitProperties` (`ezkey.rate-limit.*`) et `TrustedProxyProperties` (`ezkey.trusted-proxies.cidrs`) configurables par env

### UX du login

- Section accordéon « ABOUT THIS DEMO » expliquant le modèle de session :
  - clé API temporaire et locale au navigateur
  - logout = signe out user mais garde la clé pour le cycle suivant
  - session expirée = re-saisie de la clé requise
- Étape « Configure the API key for this browser session » ajoutée à la liste des steps
- Message de logout aligné : *"You have been logged out. The demo API key remains available in this browser session."*
- Dialog Apply API Key : meta CSRF, headers conditionnels, gestion 403 explicite

### Déploiement Lightsail

- Service `demo-app-acme` ajouté à `experimental-hybrid/lightsail/docker-compose.yml`
- Hostname `exp1-demo-acme.ezkey.org` routé via `experimental-hybrid/lightsail/Caddyfile` (TLS 1.3, headers conservateurs identiques aux APIs)
- README module mis à jour pour distinguer mode local-dev et mode internet-démo

### Tests

- `HomeControllerTest` : logout préserve les attributs de clé démo, supprime les attributs auth/pending
- `DemoApiKeyConfigServiceTest` : isolation session
- `LoginControllerSecurityTest` : rejet CSRF sur `/login` et `/api/apply-api-key`
- `DemoRateLimitServiceTest` : comportement Bucket4j
- Suite complète : 9/9 OK ; `mvn checkstyle:check` propre

## Hors scope maintenu

- Pas de provisioning tenant ou stockage centralisé de clés API dans Acme
- Pas de transformation en SaaS production multi-tenant
- Pas de profil "internet-mode" séparé : un seul mode, sécurisé par défaut, configuré par env
- Outer gate (Basic Auth / Cloudflare Access) reste optionnel comme défense en profondeur, pas comme modèle primaire

## Décisions clés actées

- **D1** : Isolation session conservée comme modèle de sécurité primaire
- **D2** : Clé API préservée à travers le logout dans la même session navigateur
- **D3** : Modèle de session expliqué directement dans la page de login, pas dans une doc externe
- **D4** : Clarté sur le comportement démo plutôt que dissimulation
- **D5** : Rate limiting in-memory simple (Bucket4j + Caffeine), pas de stack distribuée
- **D6** : Trusted-proxy CIDR explicite, jamais de confiance aveugle des headers `X-Forwarded-For`

## Validation manuelle effectuée

1. Configure API key, login, logout, login à nouveau dans la même session → OK sans re-saisie
2. Deuxième profil navigateur n'hérite pas de la clé du premier → OK
3. Page login communique clairement le modèle de session → OK
4. Image Docker reconstruite, conteneur redémarré, démarrage propre :
   *"ACME demo rate limiting initialized: login=10 per 5 minute(s), applyApiKey=5 per 10 minute(s), enabled=true"*
5. Test fonctionnel utilisateur final → confirmé OK

## Références

- [ezkey-demo-app-acme/src/main/java/org/ezkey/demo/acme/controller/HomeController.java](../../../ezkey-demo-app-acme/src/main/java/org/ezkey/demo/acme/controller/HomeController.java)
- [ezkey-demo-app-acme/src/main/java/org/ezkey/demo/acme/controller/LoginController.java](../../../ezkey-demo-app-acme/src/main/java/org/ezkey/demo/acme/controller/LoginController.java)
- [ezkey-demo-app-acme/src/main/java/org/ezkey/demo/acme/service/DemoApiKeyConfigService.java](../../../ezkey-demo-app-acme/src/main/java/org/ezkey/demo/acme/service/DemoApiKeyConfigService.java)
- [ezkey-demo-app-acme/src/main/java/org/ezkey/demo/acme/config/EzkeyClientProvider.java](../../../ezkey-demo-app-acme/src/main/java/org/ezkey/demo/acme/config/EzkeyClientProvider.java)
- [ezkey-demo-app-acme/src/main/java/org/ezkey/demo/acme/config/SecurityConfig.java](../../../ezkey-demo-app-acme/src/main/java/org/ezkey/demo/acme/config/SecurityConfig.java)
- [ezkey-demo-app-acme/src/main/java/org/ezkey/demo/acme/config/AcmeRateLimitProperties.java](../../../ezkey-demo-app-acme/src/main/java/org/ezkey/demo/acme/config/AcmeRateLimitProperties.java)
- [ezkey-demo-app-acme/src/main/java/org/ezkey/demo/acme/config/TrustedProxyProperties.java](../../../ezkey-demo-app-acme/src/main/java/org/ezkey/demo/acme/config/TrustedProxyProperties.java)
- [ezkey-demo-app-acme/src/main/java/org/ezkey/demo/acme/security/ClientIpResolver.java](../../../ezkey-demo-app-acme/src/main/java/org/ezkey/demo/acme/security/ClientIpResolver.java)
- [ezkey-demo-app-acme/src/main/java/org/ezkey/demo/acme/security/DemoRateLimitService.java](../../../ezkey-demo-app-acme/src/main/java/org/ezkey/demo/acme/security/DemoRateLimitService.java)
- [ezkey-demo-app-acme/src/main/resources/templates/login.html](../../../ezkey-demo-app-acme/src/main/resources/templates/login.html)
- [ezkey-demo-app-acme/src/main/resources/application.properties](../../../ezkey-demo-app-acme/src/main/resources/application.properties)
- [ezkey-demo-app-acme/README.md](../../../ezkey-demo-app-acme/README.md)
- [experimental-hybrid/lightsail/docker-compose.yml](../../../experimental-hybrid/lightsail/docker-compose.yml)
- [experimental-hybrid/lightsail/Caddyfile](../../../experimental-hybrid/lightsail/Caddyfile)
