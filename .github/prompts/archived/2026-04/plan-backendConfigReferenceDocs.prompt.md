# Plan : Ezkey Backend Configuration Reference Documentation

> **Status : COMPLETED — 2026-04-15**

**TL;DR** : Créer une documentation de référence exhaustive par module — `CONFIGURATION.md` colocalisé dans chaque back-end, groupement fonctionnel, tableau de propriétés avec 3 niveaux d'obligation, matrice de profils, mapping env vars Docker — plus un index central et les métadonnées machine-readable.

---

## Contexte

### Modules concernés

| Module | Classes @ConfigurationProperties | Fichiers .properties | CONFIGURATION.md |
|---|---|---|---|
| `ezkey-core` (lib partagée) | 8 classes, ~20 préfixes | — | Partiel (2/8 groupes) |
| `ezkey-admin-api` | 11 classes | 5 profils | ❌ |
| `ezkey-auth-api` | 2 classes + hérite core | 5 profils | ❌ |
| `ezkey-integration-api` | 2 classes + hérite core | 3 profils | ❌ |
| `ezkey-crypto-api` | 0 (hérite core) | 2 profils | ❌ |

### Tous les préfixes `ezkey.*` (20 au total)

| Préfixe | Défini dans | Utilisé par |
|---|---|---|
| `ezkey.admin.initial` | admin-api | admin-api |
| `ezkey.admin.mfa` | admin-api | admin-api |
| `ezkey.admin.token` | admin-api | admin-api |
| `ezkey.admin.token.cleanup` | admin-api | admin-api |
| `ezkey.admin.recovery` | admin-api | admin-api |
| `ezkey.admin.rate-limit` | admin-api | admin-api |
| `ezkey.admin-operations.rate-limit` | admin-api | admin-api |
| `ezkey.admin.bootstrap.export` | admin-api | admin-api |
| `ezkey.security.admin` | admin-api | admin-api |
| `ezkey.api-key.rate-limit` | admin-api & integration-api | admin-api, integration-api |
| `ezkey.rate-limit` | auth-api | auth-api |
| `ezkey.trusted-proxies` | (3 copies) | admin-api, auth-api, integration-api |
| `ezkey.encryption` | core (`TinkProperties`) | admin-api, auth-api, integration-api, crypto-api |
| `ezkey.qr` | core (`QrCodeProperties`) | admin-api, auth-api |
| `ezkey.organization` | core (`OrganizationProperties`) | admin-api, auth-api |
| `ezkey.core` | core (`EzkeyCoreProperties`) | admin-api, auth-api |
| `ezkey.enrollment` | core (`EnrollmentProperties`) | admin-api |
| `ezkey.demo` | core (`EzkeyDemoProperties`) | auth-api |
| `ezkey.audit.integrity` | core (`AuditHmacProperties`) | admin-api, auth-api, integration-api |
| `ezkey.audit.chain` | core (`AuditChainProperties`) | admin-api |

---

## Phases

### Phase 1 — Fondation et partagés

**Step 1 — Mettre à jour `ezkey-core/CONFIGURATION.md`**

Couvrir les 8 classes `@ConfigurationProperties` core (actuellement 2/8 : crypto + auth-attempt). Les 6 groupes manquants :
- `TinkProperties` (`ezkey.encryption.*`)
- `AuditHmacProperties` (`ezkey.audit.integrity.*`)
- `AuditChainProperties` (`ezkey.audit.chain.*`)
- `OrganizationProperties` (`ezkey.organization.*`)
- `QrCodeProperties` (`ezkey.qr.*`)
- `EnrollmentProperties` (`ezkey.enrollment.*`)
- `EzkeyDemoProperties` (`ezkey.demo.*`)

**Step 2 — Créer `docs/configuration/README.md`**

Index central :
- Registre complet des 20 préfixes `ezkey.*`
- Matrice module × préfixe (quelle doc consulter pour quel préfixe)
- Liens vers chaque CONFIGURATION.md

---

### Phase 2 — Documentation par module *(steps 3–6 indépendants)*

**Step 3 — Créer `ezkey-admin-api/CONFIGURATION.md`** (le plus dense)

11 groupes fonctionnels :
1. Bootstrap admin initial (`ezkey.admin.initial.*`)
2. MFA admin (`ezkey.admin.mfa.*`)
3. Tokens (`ezkey.admin.token.*`, `ezkey.admin.token.cleanup.*`)
4. Security limits (`ezkey.security.admin.*`)
5. Admin rate limiting (`ezkey.admin.rate-limit.*`)
6. Admin operations rate limiting (`ezkey.admin-operations.rate-limit.*`)
7. API key limits (`ezkey.api-key.rate-limit.*`)
8. Audit (`ezkey.audit.integrity.*`, `ezkey.audit.chain.*`) — ref → core
9. Encryption (`ezkey.encryption.*`) — ref → core
10. Trusted proxies (`ezkey.trusted-proxies.*`)
11. Organization (`ezkey.organization.*`) — ref → core
12. Bootstrap export (`ezkey.admin.bootstrap.export.*`)

**Step 4 — Créer `ezkey-auth-api/CONFIGURATION.md`**

Groupes propres :
- Rate limiting (`ezkey.rate-limit.*`)
- Trusted proxies (`ezkey.trusted-proxies.*`)
- Demo (`ezkey.demo.*`)

Références core : encryption, audit.integrity, organization, QR, core.auth-attempt

**Step 5 — Créer `ezkey-integration-api/CONFIGURATION.md`**

Groupes propres :
- API key rate limiting (`ezkey.api-key.rate-limit.*`)
- Trusted proxies (`ezkey.trusted-proxies.*`)

Références core : encryption, audit.integrity

**Step 6 — Créer `ezkey-crypto-api/CONFIGURATION.md`**

- Encryption/Tink (ref → core `TinkProperties`)
- Profils Docker uniquement (pas de DB, `MANAGEMENT_SERVER_PORT` séparé)

---

### Phase 3 — Métadonnées machine-readable

**Step 7 — Ajouter `spring-boot-configuration-processor` dans les `pom.xml` des 4 modules API**

Génère `spring-configuration-metadata.json` automatiquement à chaque build Maven depuis `@ConfigurationProperties` + Javadoc existante. Fondation directe de tout futur générateur de configuration (pattern HAProxy/httpd config generator).

Modules concernés :
- `ezkey-admin-api/pom.xml`
- `ezkey-auth-api/pom.xml`
- `ezkey-integration-api/pom.xml`
- `ezkey-crypto-api/pom.xml`

**Step 8 — Créer `additional-spring-configuration-metadata.json` dans chaque module**

Pour enrichir les hints non inférables par le processeur :
- Valeurs enum (ex : `credentialsOutputMode`, `storageMode`, `keyStrategy`)
- Groupes logiques
- Déprécations éventuelles

---

## Structure-type de chaque CONFIGURATION.md

```markdown
# Configuration Reference — ezkey-{module}

## Vue d'ensemble rapide
Table : propriété | env var Docker | défaut | requis / requis [docker] / optionnel

## Propriétés par groupe fonctionnel

### [Nom du groupe] (`ezkey.xxx.*`)
Description : pourquoi ce groupe existe, quel comportement il contrôle.

| Propriété | Type | Défaut | Obligation | Description |
|---|---|---|---|---|
| `ezkey.xxx.yyy` | `String` | `value` | `requis [docker]` | ... |

**Exemple minimal :**
```properties
ezkey.xxx.yyy=value
```

## Matrice de profils
Table : propriété | default | docker | docker-test | docker-dev | windows

## Référence variables d'environnement Docker
Table : propriété Spring → variable env Docker correspondante  
(ex : `ezkey.admin.initial.username` → `EZKEY_ADMIN_INITIAL_USERNAME`)

## Propriétés partagées (ezkey-core)
Liste des préfixes core utilisés par ce module, avec liens → `ezkey-core/CONFIGURATION.md`

## Références
Liens vers docs connexes
```

---

## Niveaux d'obligation (décision retenue : Option B)

| Niveau | Signification |
|---|---|
| `requis` | L'application ne démarre pas ou plante immédiatement si absent |
| `requis [docker]` | A un stub ou défaut dev ; doit être explicitement défini pour tout déploiement réel |
| `optionnel` | Défaut production-viable ; fine-tuning seulement |

Les conditionnels (ex : *"requis si `ezkey.encryption.enabled=true`"*) sont absorbés dans la colonne description.

---

## Fichiers à créer / modifier

| Fichier | Action |
|---|---|
| `ezkey-core/CONFIGURATION.md` | Extension (2 groupes → 8) |
| `docs/configuration/README.md` | Création |
| `ezkey-admin-api/CONFIGURATION.md` | Création |
| `ezkey-auth-api/CONFIGURATION.md` | Création |
| `ezkey-integration-api/CONFIGURATION.md` | Création |
| `ezkey-crypto-api/CONFIGURATION.md` | Création |
| `ezkey-admin-api/pom.xml` | Modification (ajout processor) |
| `ezkey-auth-api/pom.xml` | Modification (ajout processor) |
| `ezkey-integration-api/pom.xml` | Modification (ajout processor) |
| `ezkey-crypto-api/pom.xml` | Modification (ajout processor) |
| `ezkey-admin-api/src/main/resources/META-INF/additional-spring-configuration-metadata.json` | Création |
| `ezkey-auth-api/src/main/resources/META-INF/additional-spring-configuration-metadata.json` | Création |
| `ezkey-integration-api/src/main/resources/META-INF/additional-spring-configuration-metadata.json` | Création |
| `ezkey-crypto-api/src/main/resources/META-INF/additional-spring-configuration-metadata.json` | Création |

---

## Critères de vérification

1. Tous les préfixes `ezkey.*` (20 au total) documentés dans au moins un CONFIGURATION.md
2. Chaque propriété présente dans les `.properties` files est couverte dans la doc (valeurs par défaut cohérentes)
3. Mapping env var validé contre `docker-compose.yml` (`EZKEY_QR_AUTH_BASE_URL`, `EZKEY_ADMIN_INITIAL_*`, etc.)
4. Liens de l'index central fonctionnels
5. Build `mvn clean install` produit `spring-configuration-metadata.json` dans les 4 modules

---

## Hors scope explicite

- Migration vers Spring Cloud Config Server
- Développement d'un générateur de configuration interactif
- Documentation des API endpoints (déjà dans `docs/ENDPOINT.md`)
