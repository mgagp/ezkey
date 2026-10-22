> GitHub issue: [#988](https://github.com/mgagp/ezkey/issues/988)

# Candidats pour les prochains labs d'intégration Ezkey — rapport de recherche (BROUILLON)

> Statut : brouillon approuvé par Marc, sources vérifiées le 2026-10-22 (GO requis avant tout dépôt). Date : 2026-10-22.
> Chemin proposé : `product-docs/global/integration-lab-candidates-evaluation-2026-10-22.md`
> (suit la convention existante `*-evaluation-AAAA-MM-JJ.md`, p. ex. `java-doctor-curated-evaluation-2026-07-11.md`, `security-pentest-tool-evaluation-2026-07-12.md`).

## 1. Cadre

Posture confirmée : laboratoire d'abord (ingénierie multi-agents AI-first), chemin d'adoption mince mais réel :
protection forte, sans mot de passe, d'outils internes utilisés par un petit groupe qui n'a pas d'autre besoin de Duo.
Points d'appui actuels : module PAM SSH (en cours de solidification) et lab plugin Jenkins (#972, M0–M4).
Ezkey n'est **pas** WebAuthn/FIDO2 et ne résiste pas au phishing par proxy en temps réel (voir #960).

## 2. Critères (note 1–3, 3 = meilleur)

- **a. Audience interne** : petit groupe, colle à la niche.
- **b. Docker / autonomie agentique** : boucle d'agent autonome sans humain.
- **c. Point d'extension auth documenté** (plugin, SPI, OIDC/SAML/LDAP, PAM, forward auth) et langage (Java = bonus).
- **d. Visibilité / adoption en entreprise.**
- **e. Adéquation avec le parcours de Marc** (Jenkins, Java/Spring Boot).
- **f. Trou MFA natif** (3 = MFA natif absent ou faible ; 1 = déjà bien couvert).

## 3. Tableau de notation

| Candidat | Point d'extension (langage) | a | b | c | d | e | f | Total /18 |
|---|---|---|---|---|---|---|---|---|
| **Keycloak** (multiplicateur) | Authenticator SPI (Java) [1] | 3 | 3 | 3 | 3 | 3 | 1 | **16** |
| **Forward auth Traefik / Caddy / nginx** (multiplicateur) | ForwardAuth [2], `forward_auth` [3], `auth_request` [4] (service HTTP, Java possible) | 3 | 3 | 3 | 3 | 2 | 2 | **16** |
| **SonarQube CE** | Plugin API Java ; point d'extension `IdentityProvider` / `BaseIdentityProvider` (`@ExtensionPoint`, depuis 5.4) [5][11] | 3 | 3 | 2 | 3 | 3 | 2 | **16** |
| **Nexus Repository OSS** | Realms intégrés (LDAP, Crowd en Pro, tokens…) ; aucun guide public pour un realm tiers [6] | 3 | 3 | 1 | 3 | 3 | 2 | **15** |
| **Rundeck (OSS)** | Auth via modules JAAS (Java), dont `PropertyFileLoginModule`, LDAP et un module PAM [7] | 3 | 3 | 2 | 2 | 3 | 2 | **15** |
| **Gitea / Forgejo** | Sources d'auth OIDC, LDAP, PAM (Go) [8] ; TOTP/WebAuthn natifs | 3 | 3 | 2 | 2 | 1 | 1 | 12 |
| **Grafana OSS** | OAuth générique, LDAP, auth proxy (Go) [9] | 3 | 3 | 2 | 3 | 1 | 2 | 14 |
| **Apache Guacamole** | Extensions `AuthenticationProvider` Java [10] ; extensions TOTP [12] et Duo [13] déjà officielles | 3 | 3 | 3 | 2 | 3 | 1 | 15 |
| **GitLab CE** | OmniAuth / OIDC / LDAP (Ruby) ; 2FA TOTP/WebAuthn natifs | 2 | 1 | 2 | 3 | 1 | 1 | 10 |
| **Vault / OpenBao** | Plugins auth (Go) ; MFA natif | 2 | 3 | 3 | 2 | 1 | 1 | 12 |
| **Mattermost** | Plugins Go/React ; SSO surtout en édition payante | 2 | 3 | 1 | 2 | 1 | 2 | 11 |
| **Nextcloud** | Apps 2FA (PHP), fournisseurs 2FA documentés | 2 | 3 | 3 | 3 | 1 | 1 | 13 |
| **Wiki.js / BookStack** | Stratégies OIDC/SAML/LDAP (Node / PHP) | 3 | 3 | 2 | 2 | 1 | 2 | 13 |
| **Portainer CE** | OAuth / LDAP (Go) | 3 | 3 | 2 | 2 | 1 | 2 | 13 |
| **Argo CD** | OIDC via Dex (Go) | 2 | 2 | 2 | 2 | 1 | 2 | 11 |
| **Harbor** | OIDC / LDAP (Go) | 2 | 2 | 2 | 2 | 1 | 2 | 11 |
| **Artifactory OSS** | Plugins Groovy ; SSO avancé surtout en Pro | 2 | 3 | 1 | 3 | 2 | 2 | 13 |
| **Vaultwarden** | Pas d'extension auth (Rust) ; 2FA natif | 2 | 3 | 1 | 2 | 1 | 1 | 10 |

Notes : notes indicatives, à valider par un spike. a et d sont des hypothèses (pas de données d'adoption mesurées).

## 4. Top 3 recommandé

1. **Keycloak Authenticator SPI** — un seul plugin Java branché dans un IdP standard couvre, par OIDC/SAML, Grafana, Gitea, Argo CD, Harbor, Wiki.js, Portainer… (multiplicateur, Java pur).
2. **Service forward auth (Traefik/Caddy/nginx)** — protège n'importe quel outil interne web sans plugin, y compris ceux sans SSO dans leur édition gratuite ; petit service Spring Boot.
3. **SonarQube CE** — outil d'équipe Java très répandu en entreprise, API plugin Java, terrain connu de Marc ; c'est le plus proche « frère » du lab Jenkins.

Suppléant : Guacamole (extension Java, audience très interne), mais TOTP/Duo y existent déjà — à garder comme preuve de pattern, pas comme trou.

## 5. Note honnête : multiplicateurs vs plugins ponctuels

Un plugin par produit = coût de maintenance par produit et suivi des API internes (ex. Nexus, Jenkins).
Keycloak et le forward auth coûtent une intégration pour N apps et reposent sur des contrats stables (SPI, en-têtes HTTP).
Contrepartie : moins de visibilité « marque » par produit, et Keycloak a déjà TOTP/WebAuthn natifs — la valeur d'Ezkey y est le push à clé d'appareil, pas l'existence d'un 2e facteur.
Le forward auth n'a de sens que si l'app accepte un en-tête d'identité de confiance (risque de contournement si l'app est joignable sans le proxy — à documenter dans SECURITY_POSTURE).
Recommandation : les plugins ponctuels servent la démo et la méthodologie ; les multiplicateurs servent l'adoption.

## 6. Leçons à hériter du lab Jenkins (#972)

- Jalons M0–M4 identiques : instance Docker + job trivial, hello-world plugin, survey des plugins 2FA existants + mock auto-accept, adéquation SDK Java, aller-retour réel.
- Admin break-glass dès M2 (ne jamais s'enfermer dehors).
- Règle d'impasse : stop et rapport après deux approches échouées.
- Critère d'acceptation vérifiable et rejouable sans humain (test fonctionnel sur stack propre).
- Rapports courts de Mathieu ; gates tenus par Marc ; budget surveillé.
- Ne pas ralentir PAM ni l'alpha (pushback produit standard).
- Un lab à la fois : le suivant démarre après M3 de Jenkins au plus tôt.

## 7. Porte ouverte — déclencheurs de réévaluation

Réévaluer cette liste si : (1) un adopter réel nomme un outil ; (2) le lab Jenkins atteint M4 (ou bute sur la règle d'impasse) ; (3) un candidat change de licence ou de modèle d'auth (ex. SSO déplacé vers payant) ; (4) Ezkey publie un SDK non-Java (Go/Node) qui change le critère c/e. Sinon, revue trimestrielle.

## Vérification (2026-10-22)

Toutes les URL ci-dessous ont été ouvertes et leur contenu contrôlé par mots-clés le 2026-10-22 (vérifié 2026-10-22). Les notes d'audience (a) et d'adoption (d) restent des hypothèses non mesurées. Aucune note n'a changé : les faits vérifiés confirment les notes du brouillon.

## Sources

1. Keycloak Server Developer Guide — Authentication SPI : https://www.keycloak.org/docs/latest/server_development/#_auth_spi — vérifié 2026-10-22
2. Traefik ForwardAuth middleware : https://doc.traefik.io/traefik/reference/routing-configuration/http/middlewares/forwardauth/ — vérifié 2026-10-22
3. Caddy `forward_auth` : https://caddyserver.com/docs/caddyfile/directives/forward_auth — vérifié 2026-10-22
4. nginx `ngx_http_auth_request_module` : https://nginx.org/en/docs/http/ngx_http_auth_request_module.html — vérifié 2026-10-22
5. SonarQube plugin basics : https://docs.sonarsource.com/sonarqube-server/latest/extension-guide/developing-a-plugin/plugin-basics/ — vérifié 2026-10-22
6. Nexus Repository — Realms : https://help.sonatype.com/en/realms.html — vérifié 2026-10-22
7. Rundeck — authentication (JAAS) : https://docs.rundeck.com/docs/administration/security/authentication.html — vérifié 2026-10-22
8. Gitea — authentication : https://docs.gitea.com/usage/authentication — vérifié 2026-10-22
9. Grafana — configure authentication : https://grafana.com/docs/grafana/latest/setup-grafana/configure-security/configure-authentication/ — vérifié 2026-10-22
10. Guacamole manual — custom authentication / TOTP / Duo : https://guacamole.apache.org/doc/gug/custom-auth.html — vérifié 2026-10-22
11. SonarQube `IdentityProvider` (source) : https://github.com/SonarSource/sonar-plugin-api/blob/master/plugin-api/src/main/java/org/sonar/api/server/authentication/IdentityProvider.java — vérifié 2026-10-22
12. Guacamole — TOTP : https://guacamole.apache.org/doc/gug/totp-auth.html — vérifié 2026-10-22
13. Guacamole — Duo : https://guacamole.apache.org/doc/gug/duo-auth.html — vérifié 2026-10-22
