---
name: Loi 25 operator support analysis
overview: "Plan de travail pour produire l'analyse detaillee Loi 25 appliquee a Ezkey, avec traçabilite documentaire stricte et enchainement explicite des impacts: obligations -> donnees personnelles -> base de donnees -> entites/services/controleurs -> Admin UI."
todos:
  - id: legal-scope
    content: Cadrer les obligations Loi 25 pertinentes avec references officielles precises
    status: pending
  - id: data-map
    content: Cartographier les renseignements personnels reels dans Ezkey et leur regime d'action
    status: pending
  - id: db-domain-ui-impact
    content: Deriver les impacts DB, domaine Admin API et Admin UI a partir de l'analyse
    status: pending
  - id: source-register
    content: Constituer le registre historisable des sources Loi 25 utilisees dans le rapport
    status: pending
isProject: false
---

# Loi 25 - Ezkey operator support analysis

## 1. Purpose

Ce document est le plan de travail de reference pour produire le rapport d'analyse Loi 25
applique a Ezkey.

Le but n'est pas de produire un avis juridique final. Le but est de construire une analyse assez
precise et traçable pour permettre des decisions produit et implementation coherentes.

Le resultat attendu suit explicitement cette chaine:

1. identifier les obligations Loi 25 qui comptent reellement pour Ezkey et pour l'operateur qui
   heberge Ezkey;
2. identifier les renseignements personnels reels conserves par la plateforme;
3. en deduire les impacts sur la base de donnees Ezkey;
4. en deduire les impacts sur les entites, services et controleurs du backend et de l'Admin API;
5. en deduire enfin les consequences fonctionnelles et ergonomiques pour l'Admin UI.

Le document doit aussi rendre tres visible la distinction entre:

- ce que la Loi 25 exige reellement;
- ce qu'Ezkey peut prendre en charge nativement pour faciliter l'operateur;
- ce qui demeure une responsabilite procedurale ou organisationnelle de l'exploitant.

## 2. Scope

### In scope

- obligations Loi 25 pertinentes pour une plateforme MFA self-hosted qui traite des
  renseignements personnels de personnes physiques;
- cartographie des donnees personnelles, y compris les donnees directes, contextuelles,
  derivees et journaux;
- analyse des ecarts entre l'etat actuel d'Ezkey et un support operateur raisonnable;
- recommandations sur les impacts base de donnees, domaine, Admin API et Admin UI;
- traçabilite documentaire stricte pour conserver une reference historique exploitable.

### Out of scope

- avis juridique formel ou opinion definitive de conformite;
- redaction complete des politiques internes de l'organisation cliente;
- implementation code dans ce document;
- couverture de tous les autres regimes de protection des donnees au-dela de ce qui est utile
  pour comparer ou renforcer l'analyse.

## 3. Design stance

Le rapport cible un outillage raisonnable, pragmatique et explicite, pas une suite de compliance
maximaliste.

Les positions de travail recommandees sont:

- traiter l'export structure des renseignements personnels comme une capacite serieuse a supporter;
- ne pas confondre revocation securitaire, suppression physique et anonymisation;
- preserver l'integrite de l'audit et de la gouvernance comme contrainte de conception de premier
  ordre;
- eviter de transferer implicitement toute la responsabilite legale dans le produit quand elle
  appartient en realite a l'operateur;
- faire ressortir clairement les limites du produit lorsqu'un effacement complet n'est pas permis
  ou n'est pas souhaitable pour des raisons de securite, d'audit ou d'obligation de conservation.

### 3.1 Execution discipline retained for implementation follow-up

Le present plan retient une discipline d'execution **phase par phase**.

Position de travail retenue:

- ne pas deriver immediatement des specs d'implementation, meme courtes, pour tous les lots;
- commencer par la premiere phase fondatrice, qui touchera vraisemblablement le schema et les
  contrats API;
- prevoir explicitement une revue de coherence et une comparaison d'integrite apres cette premiere
  phase;
- ne specifier la phase suivante qu'apres validation du resultat reel de la phase precedente.

Rationale:

- la premiere phase modifiera des surfaces structurantes;
- elle peut faire emerger des contraintes ou hypotheses invalidantes pour les lots suivants;
- une specification prematuree des phases ulterieures augmenterait le risque de derive et de
  sur-hypothese.

## 4. Final report skeleton

Le rapport detaille a produire a partir de ce plan devrait suivre la structure ci-dessous.

### 4.1 Executive summary

Doit repondre rapidement a quatre questions:

- qu'est-ce que la Loi 25 impose reellement dans le contexte Ezkey;
- qu'est-ce qu'Ezkey supporte deja;
- quels sont les principaux ecarts;
- quelles decisions produit devront etre prises.

### 4.2 Mandate and boundaries

Definir clairement:

- le perimetre du mandat;
- les hypotheses retenues;
- les limites de l'analyse;
- la distinction entre obligations du produit et obligations de l'exploitant.

### 4.3 Methodology and documentary traceability

Expliquer:

- la hierarchie des sources;
- les regles de citation;
- la date de consultation;
- la maniere dont chaque constat sera rattache a une source officielle ou a une ancre technique.

### 4.4 Applicable Loi 25 obligations

Documenter les obligations pertinentes, avec references exactes:

- responsable de la protection des renseignements personnels;
- politiques et pratiques de gouvernance;
- EFVP / PIA;
- collecte necessaire et finalites;
- transparence et information de la personne;
- acces, rectification et delais de reponse;
- portabilite en format technologique structure et couramment utilise;
- conservation, destruction et anonymisation;
- incidents de confidentialite;
- limites et motifs de refus.

Le rapport detaille devrait traiter au minimum les obligations suivantes comme perimetre ferme.

#### 4.4.1 Responsabilite et gouvernance documentaire

- **Responsable de la protection des renseignements personnels**: la loi impose qu'une personne
  assumant la plus haute autorite soit responsable de l'application de la loi, avec possibilite de
  delegation ecrite, et que son titre et ses coordonnees soient publies.
  Reference primaire: Loi, art. 3.1.
- **Politiques et pratiques de gouvernance**: l'organisation doit etablir et mettre en oeuvre des
  politiques et pratiques concernant la protection des renseignements personnels, couvrant
  notamment la conservation, la destruction, les roles et responsabilites, et un processus de
  traitement des plaintes.
  Reference primaire: Loi, art. 3.2.
- **Implication pour Ezkey**: le produit ne peut pas satisfaire seul ces obligations, mais il doit
  eviter de rendre leur execution inutilement difficile. Le rapport devra donc distinguer les
  obligations procedurales de l'operateur des capacites natives qu'Ezkey peut offrir pour les
  supporter.

#### 4.4.2 EFVP / PIA

- **Evaluation des facteurs relatifs a la vie privee**: tout projet d'acquisition, developpement
  ou refonte d'un systeme d'information impliquant la collecte, l'utilisation, la communication,
  la conservation ou la destruction de renseignements personnels doit faire l'objet d'une EFVP
  proportionnee.
  Reference primaire: Loi, art. 3.3.
- **Portabilite pensee des la conception**: ce meme article impose que le projet permette la
  communication a la personne des renseignements personnels informatises collectes aupres d'elle
  dans un format technologique structure et couramment utilise.
  Reference primaire: Loi, art. 3.3.
- **Implication pour Ezkey**: la portabilite n'est pas seulement une commodite UX. Elle doit etre
  traitee comme une exigence de conception serieuse pour les donnees collectees aupres de la
  personne concernee.

#### 4.4.3 Collecte, necessite et transparence

- **Finalites determinees avant la collecte**.
  Reference primaire: Loi, art. 4.
- **Collecte limitee au necessaire**.
  Reference primaire: Loi, art. 5.
- **Information de la personne au moment de la collecte**: finalites, moyens de collecte, droits
  d'acces et de rectification, droit de retirer le consentement a l'utilisation ou a la
  communication, duree de conservation et coordonnees du responsable sur demande.
  Reference primaire: Loi, art. 8.
- **Politique de confidentialite publiee lors d'une collecte par moyens technologiques**.
  Reference primaire: Loi, art. 8.2.
- **Parametres de confidentialite par defaut au niveau le plus eleve pour un produit ou service
  technologique quand applicable**.
  Reference primaire: Loi, art. 9.1.
- **Implication pour Ezkey**: l'analyse doit verifier si les champs stockes au niveau Enrollment,
  EzkeyAdmin et des journaux sont justifies par une finalite explicite et si la surface admin
  permet de rendre cette finalite defendable.

#### 4.4.4 Mesures de securite et incidents de confidentialite

- **Mesures de securite appropriees** selon la sensibilite, la quantite, la finalite et le
  support.
  Reference primaire: Loi, art. 10.
- **Incidents de confidentialite**: obligation de prendre des mesures raisonnables pour reduire le
  risque de prejudice, notifier promptement la CAI et les personnes concernees lorsqu'il existe un
  risque de prejudice serieux, et tenir un registre des incidents.
  References primaires: Loi, art. 3.5, 3.6, 3.7 et 3.8.
- **Implication pour Ezkey**: meme si la gestion procedurale de l'incident appartient a
  l'operateur, le produit doit fournir un niveau raisonnable de visibilite, d'audit et de traces
  permettant de soutenir cette obligation.

#### 4.4.5 Acces, rectification, portabilite et delais

- **Droit d'acces**: confirmation de l'existence des renseignements personnels, communication a la
  personne et obtention d'une copie.
  Reference primaire: Loi, art. 27.
- **Portabilite**: sauf difficulte pratique serieuse, les renseignements personnels informatises
  collectes aupres du demandeur et non crees ou infers a partir d'autres renseignements doivent
  etre communiques dans un format technologique structure et couramment utilise.
  Reference primaire: Loi, art. 27.
- **Rectification**: la personne peut exiger la rectification de renseignements inexacts,
  incomplets, equivoques ou detenus, communiques ou conserves sans autorisation legale.
  Reference primaire: Loi, art. 28.
- **Traitement de la demande**: demande ecrite adressee au responsable, assistance pour preciser la
  demande au besoin, reponse ecrite diligente dans les 30 jours.
  References primaires: Loi, art. 29, 30 et 32.
- **Motifs de refus**: refus motive, avec indication de la base legale et des recours.
  Reference primaire: Loi, art. 34.
- **Implication pour Ezkey**: l'analyse doit distinguer tres clairement ce qui releve de
  l'exportable portable, de l'acces simple, de la rectification possible, et des donnees qui ne
  peuvent pas suivre le meme regime parce qu'elles sont derivees, inferees ou retenues pour audit.

#### 4.4.6 Conservation, destruction et anonymisation

- **Destruction ou anonymisation quand les finalites sont accomplies**, sous reserve d'une periode
  de conservation prevue par la loi.
  Reference primaire: Loi, art. 23.
- **Anonymisation selon les meilleures pratiques generalement reconnues**.
  Reference primaire: Loi, art. 23.
- **Conservation des donnees lorsqu'une demande est contestee ou refusee** pour permettre l'exercice
  des recours.
  Reference primaire: Loi, art. 36.
- **Implication pour Ezkey**: le rapport ne doit jamais assimiler automatiquement le droit d'acces
  ou de rectification a un droit absolu d'effacement physique. Il faut articuler proprement la
  tension entre destruction, anonymisation, retention legitime et integrite d'audit.

#### 4.4.7 Restrictions et limites

- **Refus possible d'acces** notamment lorsqu'une divulgation risquerait d'entraver une enquete ou
  d'affecter des procedures judiciaires.
  Reference primaire: Loi, art. 39.
- **Protection des tiers**: refus lorsqu'une communication revele des renseignements personnels sur
  un tiers et risque de lui causer un prejudice serieux, sauf exceptions.
  Reference primaire: Loi, art. 40.
- **Implication pour Ezkey**: un outillage Loi 25 ne doit pas etre concu comme un simple bouton
  d'export universel. Il doit permettre une evaluation de l'eligibilite et des restrictions.

#### 4.4.8 Engagements publics Ezkey deja pris

- La politique de confidentialite publique du projet Ezkey affirme explicitement un modele a deux
  couches: le projet Ezkey ne traite pas les donnees personnelles du serveur auto-heberge de
  l'organisation, et l'organisation qui exploite son propre backend Ezkey demeure responsable des
  donnees personnelles traitees sur ce serveur.
  References internes: `sites/ezkey-org/privacy.html`, section 2; `sites/ezkey-org/fr/confidentialite.html`, section 2.
- La politique publique identifie deja un contact privacy pour le projet Ezkey et pose une base
  documentaire utile pour distinguer le projet logiciel de l'exploitant du backend.
  References internes: `sites/ezkey-org/privacy.html`, sections 1 et 2; `sites/ezkey-org/fr/confidentialite.html`, sections 1 et 2.

### 4.5 Interpretation matrix

Tableau de travail attendu:

| Obligation Loi 25 | Portee reelle | Impact produit potentiel | Responsabilite Ezkey | Responsabilite operateur | Priorite |
| --- | --- | --- | --- | --- | --- |
| Responsable RP et coordonnees publiees | Obligation organisationnelle de gouvernance | Eventuellement documenter clairement la repartition des roles entre projet Ezkey et exploitant | Documentation produit et cadrage | Nommer, deleguer, publier et operer | Haute |
| Politiques et pratiques de gouvernance | Obligation procedurale et documentaire | Fournir des surfaces et traces qui n'entravent pas les procedures de l'operateur | Support partiel par design et docs | Rediger, approuver, operer et maintenir | Haute |
| EFVP / PIA | Obligation de conception et de projet | Justifier les choix de modelisation et les capacites privacy des la conception | Concevoir un produit defendable et documente | Realiser l'EFVP de son deploiement et de ses usages | Haute |
| Collecte limitee au necessaire et finalites explicites | Obligation sur les donnees stockees et leur usage | Revisiter certains champs et leur justification metier | Minimiser et documenter le schema et les parcours | Choisir les usages et limiter ses propres saisies | Haute |
| Acces et copie | Obligation de permettre l'acces a la personne concernee | Besoin d'un dossier de consultation operateur et d'une vue consolidee | Outillage natif raisonnable | Traiter et autoriser les demandes | Haute |
| Portabilite structuree | Obligation forte pour les donnees collectees aupres de la personne | Export structure et defendable, avec perimetre explicite | Support natif recommande | Valider le demandeur, transmettre et gouverner | Haute |
| Rectification | Obligation de corriger certaines donnees | Identifier les champs rectifiables et les limites sur l'audit | Support de mutation la ou legitime | Decider, executer et tracer la rectification | Haute |
| Destruction ou anonymisation en fin de finalite | Obligation conditionnelle, non absolue | Besoin d'une logique d'eligibilite et de garde-fous | Capacites ciblees de suppression et anonymisation | Appliquer la retention et arbitrer les exceptions | Haute |
| Incidents de confidentialite et registre | Obligation procedurale avec besoin de trace | Tirer parti des audits et journaux existants, identifier les lacunes | Rendre les traces et journaux exploitables | Evaluer le risque, notifier, tenir le registre | Moyenne a haute |
| Motifs de refus et restrictions | Obligation de traitement nuancee | Eviter un UX/API trop simpliste de type bouton universel | Support d'evaluation et d'attestation | Statuer juridiquement sur la demande | Moyenne |

### 4.6 Personal data map in Ezkey

Inventaire conceptuel et technique des renseignements personnels presents dans Ezkey.

Le minimum a couvrir est:

- Enrollment;
- EzkeyAdmin;
- AuthAttempt;
- AuditLog;
- Tenant et Integration quand des champs peuvent identifier indirectement une personne;
- recovery codes, admin tokens et autres artefacts associes a une personne ou a son usage;
- champs contextuels susceptibles de contenir de l'information personnelle.

#### 4.6.1 Review convention for this inventory

Pour permettre une revision efficace dans le futur, chaque bloc d'inventaire du rapport detaille
devra inclure les references suivantes:

- **surface conceptuelle**: ce que la donnee represente fonctionnellement;
- **ancre technique principale**: fichier d'entite ou de persistence de reference;
- **table ou support de persistence**: nom de table ou mecanisme de stockage;
- **champs saillants**: uniquement les champs pertinents pour la vie privee;
- **questions de revision**: ce qu'il faut reverifier si le modele evolue.

Format recommande pour chaque famille de donnees:

| Famille | Surface conceptuelle | Ancre technique principale | Table / support | Champs saillants | Questions de revision |
| --- | --- | --- | --- | --- | --- |

Cette convention est obligatoire pour la version detaillee du rapport afin que l'on puisse:

- revalider rapidement le perimetre de donnees personnelles;
- detecter les changements de schema ou de semantique;
- reouvrir une analyse sans refaire toute l'exploration du code.

#### 4.6.2 Structured inventory - current baseline

##### A. Enrollment data

Surface conceptuelle:

L'enrollment est le point principal ou Ezkey rattache un appareil, un contexte utilisateur et une
integration. C'est aujourd'hui la surface la plus evidente de renseignements personnels lies a une
personne concernee finale.

Ancre technique principale:

- `ezkey-core/src/main/java/org/ezkey/enrollment/domain/entity/Enrollment.java`

Table / support:

- `ezkey_enrollment`

Champs saillants:

- `enrollment_name`: nom lisible de l'enrollment, potentiellement nominatif selon les pratiques
  d'administration;
- `contact_email`: courriel de contact de l'utilisateur final;
- `contact_phone_number`: numero de telephone de contact de l'utilisateur final;
- `user_identifier`: identifiant utilisateur provenant de l'application integratrice;
- `created_by_admin_id`, `deactivated_by_admin_id`, `revoked_by_admin_id`: rattachement a des
  operateurs identifies;
- `created_at`, `verified_at`, `last_used_at`, `deactivated_at`, `revoked_at`: chronologie d'usage
  et de gestion;
- `integration_id`: rattachement indirect a un contexte applicatif;
- `enrollment_proof_token` et ses hashes: secrets techniques, non des renseignements personnels en
  soi, mais associes a une personne ou a son appareil.

Constat de travail:

Enrollment contient a la fois des renseignements personnels directs et des artefacts techniques
 hautement sensibles. Le rapport detaille devra distinguer strictement ces deux categories.

Questions de revision:

- verifier si `enrollment_name` est libre ou guide par convention operateur;
- verifier s'il existe d'autres champs de contact ou d'attribut utilisateur ajoutes dans les DTOs;
- verifier si d'autres tables derivent ou recopient ces donnees.

##### B. Administrator identity data

Surface conceptuelle:

L'entite admin porte l'identite professionnelle ou operationnelle des administrateurs Ezkey. Il
 s'agit de renseignements personnels lies a des operateurs humains, avec une forte valeur d'audit
 et de responsabilisation.

Ancre technique principale:

- `ezkey-core/src/main/java/org/ezkey/integration/domain/entity/EzkeyAdmin.java`

Table / support:

- `ezkey_admin`

Champs saillants:

- `username`;
- `email`;
- `phone_number`;
- `first_name`;
- `last_name`;
- `tenant_id`, `integration_id`: rattachements organisationnels;
- `enrollment_id`: lien avec le MFA de l'administrateur;
- `created_at`, `last_login_at`;
- `active`, `lifecycle_status`.

Constat de travail:

Les donnees admin sont des renseignements personnels plus nettement identifiants que plusieurs
 donnees utilisateur finales actuelles. Elles sont egalement plus difficiles a supprimer parce
 qu'elles soutiennent l'accountability et l'audit.

Questions de revision:

- verifier si certaines informations admin sont recopiees dans les reponses API ou journaux;
- verifier si la granularite actuelle suffit pour distinguer identite admin et artefacts MFA.

##### C. Authentication attempt context

Surface conceptuelle:

AuthAttempt est un objet evenementiel, mais il peut contenir du contexte metier fourni par le
 systeme integrateur. Ce contexte peut contenir des renseignements personnels ou des identifiants
 sensibles selon l'usage fait par l'organisation cliente.

Ancre technique principale:

- `ezkey-core/src/main/java/org/ezkey/authattempt/domain/entity/AuthAttempt.java`

Table / support:

- `ezkey_auth_attempt`

Champs saillants:

- `enrollment_id`: rattachement a une personne via son enrollment;
- `context_title`;
- `context_message`;
- `created_at`, `expires_at`;
- `auth_attempt_status`.

Constat de travail:

`context_title` et `context_message` ne sont pas personnellement identifiants par nature, mais
 ils doivent etre traites comme potentiellement personnels parce qu'ils proviennent du contexte
 applicatif de l'organisation integratrice.

Questions de revision:

- verifier si des garde-fous de contenu existent deja sur les champs de contexte;
- verifier si ces champs sont archives, exportes ou repris dans les journaux.

##### D. Audit log data

Surface conceptuelle:

L'audit log est une source majeure de renseignements personnels indirects ou contextuels. Il sert
 l'integrite, la securite, la forensic et la preuve d'action. C'est aussi la surface la plus
 susceptible d'entrer en tension avec des demandes d'effacement.

Ancre technique principale:

- `ezkey-core/src/main/java/org/ezkey/audit/domain/entity/AuditLog.java`

Table / support:

- `ezkey_audit_log`

Champs saillants:

- `ip_address`;
- `user_agent`;
- `admin_id`, `target_admin_id`;
- `integration_id`, `enrollment_id`, `auth_attempt_id`, `tenant_id`;
- `event_details`;
- `error_message`;
- `reason`;
- `created_at`.

Constat de travail:

L'audit log contient peu de renseignements personnels directs de profil civil, mais beaucoup de
 donnees identifiantes, relationnelles ou contextuelles. Il faut le traiter comme une surface de
 renseignements personnels a regime special, et non comme un simple journal technique neutre.

Questions de revision:

- verifier les contenus reels de `event_details` et `error_message` dans les flux sensibles;
- verifier si certaines donnees de journaux peuvent etre anonymisees sans casser l'integrite
  d'audit;
- verifier les strategies existantes d'archivage, de purge et de retention.

##### E. Recovery code artifacts

Surface conceptuelle:

Les recovery codes sont des secrets d'urgence lies a un administrateur humain. Meme s'ils sont
 stockes haches, ils appartiennent clairement a la surface de donnees rattachees a une personne.

Ancre technique principale:

- `ezkey-core/src/main/java/org/ezkey/integration/domain/entity/EzkeyAdmin.java`

Table / support:

- `ezkey_admin.recovery_codes`

Champs saillants:

- `recovery_codes` (BCrypt hashes);
- evenements associes dans l'audit: `ADMIN_RECOVERY_CODES_ISSUED`,
  `ADMIN_RECOVERY_CODES_REGENERATED`, `ADMIN_RECOVERY_USE`.

Constat de travail:

Le contenu des recovery codes ne doit jamais etre exporte ni expose en clair, mais leur existence,
 leur regeneration et leur consommation font partie de l'historique personnel ou securitaire d'un
 administrateur.

Questions de revision:

- verifier si des metadonnees supplementaires sur les recovery codes sont persistees ailleurs;
- verifier le niveau d'information retournable a la personne concernee sans compromettre la
  securite.

##### F. Admin session and token artifacts

Surface conceptuelle:

Les jetons admin ne stockent pas le secret en clair, mais ils materialisent des sessions et des
 usages relies a un administrateur identifiable. Ils constituent des donnees personnelles de type
 traces d'authentification et de session.

Ancre technique principale:

- `ezkey-core/src/main/java/org/ezkey/integration/domain/entity/AdminToken.java`

Table / support:

- `ezkey_admin_tokens`

Champs saillants:

- `admin_id`;
- `bearer_token_hash`;
- `tenant_id`, `integration_id`;
- `created_at`, `expires_at`, `last_used_at`;
- `ip_address`;
- `user_agent`;
- `active`.

Constat de travail:

Le hash de jeton n'est pas un renseignement personnel direct, mais l'ensemble de la ligne de token
 decrit une session attribuable a une personne identifiee. Cette surface devra etre traitee dans
 l'analyse de retention, d'acces et de preuve de traitement.

Questions de revision:

- verifier si les recovery tokens temporaires reutilisent exactement la meme persistence;
- verifier quelles surfaces d'API permettent deja de lister, invalider ou purger ces traces.

##### G. Contextual and indirect identifiers outside primary tables

Surface conceptuelle:

Certaines donnees n'ont pas pour role principal d'identifier une personne, mais peuvent le faire
 indirectement dans le contexte d'usage Ezkey.

Ancres techniques principales:

- `ezkey-admin-api/src/main/java/org/ezkey/authattempt/dto/AuthAttemptCreateRequestDto.java`
- `ezkey-admin-api/src/main/java/org/ezkey/admin/controller/AuthAttemptController.java`
- `ezkey-admin-api/src/main/java/org/ezkey/enrollment/dto/EnrollmentCreateRequestDto.java`
- `ezkey-admin-api/src/main/java/org/ezkey/enrollment/dto/EnrollmentResponseDto.java`

Supports:

- DTOs d'entree et de sortie;
- messages d'erreur;
- journaux d'audit;
- ecrans Admin UI et exports futurs.

Champs saillants:

- `userIdentifier` dans les DTOs et parcours d'auth attempt;
- `contextTitle` / `contextMessage` dans les DTOs et surfaces de pending;
- references croisees entre enrollment, auth attempt, admin et audit.

Constat de travail:

Ces surfaces sont importantes parce que l'analyse Loi 25 ne doit pas se limiter aux colonnes
 JPA. Il faut aussi suivre les donnees personnelles la ou elles circulent, sont affichees,
 journalisees ou seront exportees.

Questions de revision:

- verifier si des copies ou enrichissements UI existent cote front;
- verifier si certains messages d'erreur ou details d'audit exposent des donnees non prevues.

### 4.7 Data-rights classification matrix

Tableau de travail attendu:

| Entite / table | Champ | Nature | Sensibilite | Collecte aupres de la personne | Exportable | Rectifiable | Supprimable | Anonymisable | Conservation requise | Justification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |

#### 4.7.1 Working baseline classification

Cette matrice est une base de travail initiale. Chaque ligne devra etre relue avec les ancres
 techniques de la section 4.6 avant toute decision d'implementation.

| Entite / table | Champ | Nature | Sensibilite | Collecte aupres de la personne | Exportable | Rectifiable | Supprimable | Anonymisable | Conservation requise | Justification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `ezkey_enrollment` | `enrollment_name` | Libelle d'enrollment potentiellement nominatif | Moyenne a elevee selon usage operateur | Oui, indirectement ou via l'organisation | Oui, dans l'acces et potentiellement dans l'export structure | Oui, en principe | Oui, si suppression de l'enrollment admissible | Oui, probable | Pas systematiquement | Champ descriptif potentiellement identifiant, sans valeur d'integrite comparable a l'audit |
| `ezkey_enrollment` | `contact_email` | Coordonnnee personnelle directe | Elevee | Oui | Oui, format structure recommande | Oui | Oui, si suppression admissible | Oui | Pas systematiquement | Donnee directe collectee aupres ou pour la personne; releve typiquement de l'acces, rectification et portabilite |
| `ezkey_enrollment` | `contact_phone_number` | Coordonnnee personnelle directe | Elevee | Oui | Oui, format structure recommande | Oui | Oui, si suppression admissible | Oui | Pas systematiquement | Meme logique que `contact_email` |
| `ezkey_enrollment` | `user_identifier` | Identifiant metier de la personne dans l'app integratrice | Elevee | Oui, en pratique via l'organisation integratrice | Oui, format structure recommande | Oui, avec prudence | Oui, si suppression admissible | Oui | Potentiellement requise dans traces derivees | Identifiant central pour retrouver une personne; doit etre bien traite dans export et recherche Loi 25 |
| `ezkey_enrollment` | `created_at`, `verified_at`, `last_used_at`, `expires_at` | Chronologie d'existence et d'usage | Moyenne | Non, donnee systeme derivee | Oui, acces simple; portabilite plus discutable | Non, sauf correction exceptionnelle | Non en general tant que l'objet est conserve | Potentiellement apres retention | Souvent oui tant que l'enrollment existe | Donnees systeme derivees, utiles a l'acces mais non necessairement rectifiables ni toujours portables au sens strict |
| `ezkey_enrollment` | `created_by_admin_id`, `deactivated_by_admin_id`, `revoked_by_admin_id` | Trace d'operateur | Moyenne a elevee | Non | Oui dans un dossier d'acces complet, avec prudence tiers | Non | Non en general | Potentiellement par pseudonymisation limitee, pas par defaut | Oui, forte valeur d'audit | Sert a l'accountability et croise la vie privee de l'operateur et de la personne concernee |
| `ezkey_enrollment` | `status`, `active`, `device_private_key_storage_tier` | Etat et caracteristiques techniques | Faible a moyenne | Partiellement, selon le cas | Oui en acces; portable si utile au dossier | Non sauf correction technique | Non par defaut | Pas prioritaire | Tant que l'objet existe | Donnees d'etat plutot que donnees civiles; utiles pour comprehension du dossier |
| `ezkey_enrollment` | `enrollment_proof_token`, `enrollment_proof_token_hash`, `integration_private_key`, `integration_public_key`, `device_public_key`, `device_public_key_hash` | Secrets ou identifiants cryptographiques | Tres elevee | Non au sens vie privee, ou partiellement pour la cle publique d'appareil | Non en clair; acces eventuel sous forme d'attestation ou de presence | Non | Non par mecanisme Loi 25 standard | Non, sauf destruction de l'objet sous conditions strictes | Oui ou fortement contrainte | Ces champs relevent surtout de la securite; l'export en clair serait disproportionne et dangereux |
| `ezkey_admin` | `username`, `email`, `phone_number`, `first_name`, `last_name` | Identite personnelle/professionnelle d'admin | Elevee | Oui | Oui, acces et export possibles | Oui | Non en general tant que l'identite doit etre preservee pour audit | Oui, eventuellement apres retention ou depart definitif, a etudier | Oui, forte valeur d'audit | Donnees personnelles directes, mais rattachees a une fonction d'operateur et a l'accountability |
| `ezkey_admin` | `tenant_id`, `integration_id`, `admin_type`, `challenge_required`, `active`, `lifecycle_status`, `enrollment_id` | Attributs d'habilitation et de rattachement | Moyenne | Non ou partiellement | Oui en acces | Partiellement, selon le champ | Non en general | Peu pertinent a court terme | Oui tant que le compte existe | Attributs metier et de securite utiles pour expliquer le dossier admin |
| `ezkey_admin` | `created_at`, `last_login_at` | Historique de compte et d'usage | Moyenne | Non | Oui en acces | Non sauf correction exceptionnelle | Non en general | Potentiellement apres retention | Oui | Trace operationnelle necessaire pour l'accountability et la securite |
| `ezkey_admin` | `recovery_codes` (hashes) | Secret de recuperation stocke hache | Tres elevee | Non, secret genere par le systeme | Non en clair; attestation seulement | Non | Non comme droit ordinaire | Non | Oui tant que la mecanique de recuperation et l'audit l'exigent | Le hash ne doit pas etre divulgue comme donnee utile a la personne; seule l'existence ou l'etat peut etre communique |
| `ezkey_auth_attempt` | `context_title`, `context_message` | Contexte metier potentiellement personnel | Moyenne a tres elevee selon contenu | Potentiellement oui, via l'organisation integratrice | Oui en acces; portabilite probable si collecte ou contenu relatif a la personne | Non en general a posteriori | Non si l'attempt est conserve pour audit | Oui potentiellement a la retention | Souvent oui tant que l'historique est conserve | Ces champs peuvent contenir des renseignements personnels ou sensibles memes s'ils sont optionnels |
| `ezkey_auth_attempt` | `created_at`, `expires_at`, `auth_attempt_status`, `auth_attempt_challenge` | Etat et chronologie d'evenement | Faible a moyenne | Non | Oui en acces | Non | Non en general | Potentiellement a terme | Oui si l'evenement est conserve | Donnees derivees servant a expliquer une tentative et son issue |
| `ezkey_auth_attempt` | `auth_attempt_proof_token`, `device_proof_token` et hashes | Secrets ou preuves techniques | Tres elevee | Non | Non en clair; attestation seulement | Non | Non via workflow standard | Non | Oui ou strictement contrainte | Relevent de la securite du protocole, pas d'un export utilisateur normal |
| `ezkey_audit_log` | `ip_address`, `user_agent` | Traces techniques identifiantes | Elevee | Non, derive de l'usage | Oui en acces contextuel, mais portabilite stricte discutable | Non | Non en general | Potentiellement apres retention, avec forte prudence | Oui, valeur d'audit forte | Donnees personnelles indirectes, necessaires a la forensic et aux incidents |
| `ezkey_audit_log` | `admin_id`, `target_admin_id`, `integration_id`, `enrollment_id`, `auth_attempt_id`, `tenant_id` | References relationnelles identifiantes | Moyenne a elevee | Non | Oui en acces contextuel | Non | Non en general | Potentiellement partiellement | Oui | Clefs de rattachement necessaires a la coherence d'audit |
| `ezkey_audit_log` | `event_details`, `error_message`, `reason` | Texte libre ou semi-structure potentiellement personnel | Elevee et variable | Non ou partiellement selon la source | Oui en acces, avec revue de contenu et redaction possible | Non en general | Non en general | Potentiellement ciblable apres retention | Oui, surtout pour `reason` et certains details | Surface la plus risquee pour fuite de donnees personnelles indirectes ou inattendues |
| `ezkey_audit_log` | `created_at`, `event_type`, `event_action`, `event_status`, `api_name`, `instance_id`, `entry_hmac` | Metadonnees d'audit et d'integrite | Faible a moyenne | Non | Oui en acces contextuel | Non | Non | Tres peu pertinent | Oui, par definition | Constituent l'ossature d'integrite du journal |
| `ezkey_admin_tokens` | `admin_id`, `tenant_id`, `integration_id` | Rattachement de session a une personne et a un scope | Moyenne a elevee | Non | Oui en acces contextuel | Non | Non en general hors purge reguliere | Potentiellement apres retention | Oui tant que la trace de session est utile | Donnees de session attribuables a une personne identifiee |
| `ezkey_admin_tokens` | `ip_address`, `user_agent`, `created_at`, `expires_at`, `last_used_at`, `active` | Metadonnees de session et d'usage | Elevee pour IP/UA, moyenne pour le reste | Non | Oui en acces contextuel | Non | Non en general hors purge reguliere | Potentiellement apres retention | Oui, au moins a court et moyen terme | Traces utiles pour securite, enquete et preuve d'usage |
| `ezkey_admin_tokens` | `bearer_token_hash` | Secret derive de session | Tres elevee | Non | Non en clair; attestation seulement | Non | Non via workflow standard | Non | Oui tant que la trace subsiste | Hash de jeton non utile a exporter a la personne et sensible pour la securite |
| DTOs / erreurs / surfaces derivees | `userIdentifier`, `contextTitle`, `contextMessage`, messages d'erreur et details UI/export | Copies ou projections de donnees personnelles | Variable | Variable | Oui si la source sous-jacente est communicable | Dependent du champ source | Dependent de la source | Dependent de la source | Dependent de la source | La classification doit suivre la source primaire et non etre redecidee a chaque projection |

#### 4.7.2 Working interpretation rules

- **Exportable** ne signifie pas toujours exportable en clair. Pour les secrets, hashes et preuves
  cryptographiques, la reponse cible doit souvent etre une attestation de presence, de role ou
  d'etat plutot qu'une restitution brute.
- **Rectifiable** vise surtout les donnees de contact, d'identification ou de presentation. Les
  traces systeme, timestamps et journaux d'audit ne doivent pas etre traites comme librement
  rectifiables.
- **Supprimable** ne doit pas etre interprete comme un droit absolu d'effacement physique. Les
  contraintes de cycle de vie, d'integrite d'audit et de securite restent prioritaires.
- **Anonymisable** est particulierement pertinent pour les journaux, les traces de session et les
  historiques a faible valeur operationnelle apres retention, mais cette piste devra etre evaluee
  sans casser la coherene forensic du systeme.
- **Collecte aupres de la personne** devra etre revue finement au moment du rapport detaille pour
  separer ce qui est effectivement fourni par la personne, ce qui est fourni par l'organisation
  integratrice en son nom, et ce qui est derive par le systeme.

### 4.8 Database impacts

Premiere derivee technique attendue de l'analyse.

La section doit repondre explicitement a:

- quelles tables actuelles sont concernees;
- quels liens entre personne concernee et objets Ezkey sont suffisants ou insuffisants;
- quels champs ou marqueurs manquent pour soutenir l'export, l'effacement, l'anonymisation ou la
  preuve de traitement;
- faut-il introduire une notion de dossier de demande Loi 25 ou de journal dedie;
- quelles modifications de schema seraient minimales et defendables.

#### 4.8.1 Current database surfaces materially concerned

Les tables et supports les plus directement concernes a ce stade sont:

- `ezkey_enrollment`
- `ezkey_admin`
- `ezkey_auth_attempt`
- `ezkey_audit_log`
- `ezkey_admin_tokens`
- `ezkey_admin_temp_tokens` comme support probable de tokens temporaires ou de recuperation a
  verifier plus finement dans l'analyse detaillee

Tables ou supports secondaires mais a surveiller:

- `ezkey_integration` et `ezkey_tenant` pour les libelles ou rattachements contextuels;
- checkpoints et artefacts de cycle d'audit lorsque la retention, l'archivage ou la purge sont en
  jeu;
- DTOs, exports futurs et journaux applicatifs, meme lorsqu'ils ne correspondent pas a une table
  JPA distincte.

References d'ancrage pour revision:

- migrations de base: `ezkey-core/src/main/resources/db/migration/V1__core_domain_and_multi_tenant.sql`
- audit et identites associees: `ezkey-core/src/main/resources/db/migration/V2__audit_api_keys_proof_tokens_and_admin_identity.sql`
- partitionnement auth/audit: `ezkey-core/src/main/resources/db/migration/V4__partitioning_auth_audit_and_function.sql`
- lifecycle enrollment/admin tokens: `ezkey-core/src/main/resources/db/migration/V7__enrollment_auth_lifecycle_admin_tokens_and_demo.sql`

#### 4.8.2 What the current schema already does well

Le schema actuel apporte deja plusieurs proprietes utiles pour un futur support Loi 25:

- **Audit trail preservation by design**:
  `ezkey_audit_log` utilise largement des references `ON DELETE SET NULL`, ce qui permet de
  supprimer certains objets operationnels sans detruire automatiquement l'historique d'audit.
- **Hard guard on auth attempts**:
  `ezkey_auth_attempt.enrollment_id` reste un rattachement fort a l'enrollment, ce qui soutient la
  coherence operationnelle et explique pourquoi la suppression d'un enrollment avec historique est
  actuellement bloquee.
- **Separation between identity data and session/token data**:
  les tables admin et admin tokens sont separees, ce qui facilite une future politique de retention
  differenciee.
- **Lifecycle-aware audit purge path already exists**:
  la purge d'audit n'est pas purement age-based; elle est deja contrainte par le lifecycle des
  checkpoints (`PURGEABLE`, `PURGED`). Cela donne une base serieuse pour discuter retention,
  anonymisation et purge sans casser l'integrite.
- **Hash-only storage for certain secrets**:
  les bearer tokens et plusieurs secrets ne sont pas stockes en clair, ce qui simplifie la position
  a tenir sur la non-exportabilite de certains champs sensibles.

#### 4.8.3 Current schema limitations for a Loi 25 workflow

Le schema actuel reste toutefois insuffisant pour un support operateur clair des demandes Loi 25.

##### A. No first-class notion of privacy request or treatment dossier

Aujourd'hui, aucune table ne materialise explicitement:

- la reception d'une demande d'acces;
- la validation de son perimetre;
- la generation d'un export;
- une evaluation d'eligibilite a l'effacement ou a l'anonymisation;
- l'attestation ou le resultat de traitement;
- les motifs de refus ou de traitement partiel.

Consequence: toute prise en charge future risquerait d'etre fragilee si elle repose uniquement sur
 des audits generiques, sans objet metier dedie.

##### B. No stable subject-level key spanning all relevant surfaces

Ezkey rattache aujourd'hui une personne surtout via des chemins indirects:

- utilisateur final via `user_identifier`, `contact_email`, `contact_phone_number`, `enrollment_name`;
- admin via `admin_id` et ses attributs d'identite;
- traces et historiques via references relationnelles ou textes libres.

Il n'existe pas de cle de dossier de personne concernee unifiee qui permette de retrouver,
 consolider et traiter proprement toutes les donnees d'un meme sujet sans heuristiques ou jointures
 ad hoc.

Consequence: la recherche et la consolidation d'un dossier Loi 25 risquent d'etre couteuses,
 fragiles et difficiles a expliquer a l'operateur.

##### C. No explicit field-level privacy semantics in schema

Le schema ne marque pas explicitement:

- les champs collectes aupres de la personne;
- les champs purement derives par le systeme;
- les champs devant etre exclus d'un export en clair pour raison de securite;
- les champs potentiellement anonymisables apres retention.

Consequence: la logique de portabilite ou d'effacement restera sinon dispersee dans le code,
 difficile a auditer et difficile a faire evoluer.

##### D. Tension unresolved between deletion eligibility and retention needs

Le mode actuel de suppression d'enrollment est volontairement restreint:

- suppression impossible si l'enrollment a un historique d'auth attempt;
- suppression impossible si l'enrollment est lie au MFA d'un admin.

Ces contraintes sont saines, mais le schema n'offre pas encore de voie explicite pour un traitement
 alternatif de type:

- anonymisation;
- redaction selective;
- attestation d'impossibilite d'effacement complet;
- retention sous base legale ou de securite.

Consequence: sans schema ou objet metier complementaire, la reponse operateur risque d'etre binaire
 et peu satisfaisante.

##### E. Free-text surfaces remain high-risk and weakly classified

Les colonnes suivantes sont particulierement problematiques pour une future gouvernance Loi 25:

- `ezkey_auth_attempt.context_title`
- `ezkey_auth_attempt.context_message`
- `ezkey_audit_log.event_details`
- `ezkey_audit_log.error_message`
- `ezkey_audit_log.reason`

Le schema ne distingue pas, dans ces textes, ce qui releve:

- de donnees personnelles sur la personne concernee;
- de donnees sur l'operateur;
- de contexte purement technique;
- d'information qui pourrait devoir etre redigee avant export.

Consequence: un futur export structure ou une future reponse d'acces exigera soit une revue
 applicative, soit un schema complementaire de classification/redaction, soit les deux.

#### 4.8.4 Minimum schema-level additions worth considering

Le rapport detaille devrait au minimum evaluer les ajouts suivants.

##### Option 1 - Minimal and pragmatic: dedicated privacy request table

Introduire une table de type `ezkey_privacy_request` ou equivalent, avec un perimetre strictement
 operateur et de traitement.

Champs minimaux envisageables:

- identifiant de demande;
- type de demande (`ACCESS`, `PORTABILITY`, `RECTIFICATION`, `ERASURE`, `ANONYMIZATION`,
  `MIXED`);
- scope (`ADMIN_SUBJECT`, `END_USER_SUBJECT`, `OTHER`);
- identifiant(s) de sujet fournis au depart;
- tenant scope;
- operateur createur;
- statut de traitement;
- dates de creation, revue, completion, refus;
- motif operateur / justification;
- motif de refus;
- resume du resultat;
- reference vers un export genere ou un artefact d'attestation si applicable.

Avantages:

- plus petit changement de schema defendable;
- cree un dossier explicite de traitement;
- renforce la revisabilite documentaire et la preuve de traitement.

##### Option 2 - Slightly richer: request table plus subject resolution table

Ajouter une table secondaire de resolution, par exemple `ezkey_privacy_request_subject_link`, pour
 stocker les objets effectivement rattaches a la demande apres analyse:

- enrollment(s);
- admin(s);
- auth attempt(s);
- audit ranges or references;
- token/session traces.

Avantages:

- rend le traitement reproductible;
- facilite les exports et les justifications;
- evite de recalculer le perimetre de donnees a chaque etape.

##### Option 3 - Deferred but likely useful: privacy export artifact metadata

Si Ezkey produit un export structure telechargeable, une table legere de metadata d'artefact ou des
 colonnes sur la demande pourraient etre utiles pour tracer:

- format genere (`JSON`, `CSV`, bundle mixte);
- date de generation;
- operateur generateur;
- checksum de l'artefact;
- emplacement logique ou identifiant de stockage;
- date d'expiration ou de purge de l'artefact.

#### 4.8.5 Changes not recommended as a first move

Les points suivants ne devraient pas etre les premiers changements schema:

- introduire une refonte complete de type "person/party" transverse sur tout le domaine;
- dupliquer massivement les donnees personnelles dans de nouvelles tables de denormalisation;
- ajouter des colonnes de classification privacy sur chaque champ sans avoir d'abord valide le
  workflow operateur cible;
- autoriser la suppression physique de donnees d'audit sensibles par simple relation ou cascade.

Ces pistes ne sont pas exclues a long terme, mais elles introduiraient une complexite excessive a
 ce stade du chantier.

#### 4.8.6 Draft database impact conclusions

Conclusions de travail a reprendre dans le rapport detaille:

1. Le schema actuel est suffisamment riche pour demarrer un outillage d'acces et de portabilite,
   mais pas suffisamment explicite pour operer un workflow Loi 25 complet et traçable.
2. Le plus grand manque schema n'est pas une table de donnees personnelles supplementaire, mais une
   representation metier du traitement de la demande elle-meme.
3. Il faut preserver les proprietes actuelles d'integrite d'audit, en particulier la logique
   `ON DELETE SET NULL` sur les journaux et les gardes de suppression d'enrollment avec historique.
4. Une strategie de type "dossier de demande + resolution des objets + artefact d'export" semble a
   ce stade plus pragmatique qu'une refonte transverse du modele identitaire.
5. Les zones de schema les plus sensibles pour la suite seront les surfaces de texte libre et les
   relations indirectes qui servent a retrouver un sujet sans cle unifiee.

### 4.9 Domain and Admin API impacts

Deuxieme derivee technique attendue.

La section doit decrire:

- les entites impactees;
- les services impactes;
- les gardes metier imposes par les cycles de vie existants;
- les controleurs existants reutilisables;
- les nouvelles surfaces Admin API minimales a prevoir.

Le rapport devra distinguer:

- ce qui existe deja et peut etre etendu;
- ce qui manque completement;
- ce qui est bloque par conception actuelle;
- ce qui est nouveau mais raisonnable a introduire.

#### 4.9.1 Domain objects already present and reusable

Le chantier Loi 25 ne part pas de zero cote domaine. Ezkey possede deja les agregats qui portent
 la majorite des donnees a retrouver et a expliquer:

- `Enrollment` pour les donnees utilisateur finales et les rattachements principaux;
- `EzkeyAdmin` pour l'identite des operateurs et certaines donnees de recuperation;
- `AuthAttempt` pour le contexte evenementiel et les traces de tentative;
- `AuditLog` pour la preuve d'action, les motifs et les details d'evenement;
- `AdminToken` pour les traces de session admin.

Implication: le premier besoin domaine n'est pas de remodeler ces agregats, mais d'introduire un
 objet metier transverse de traitement de demande qui sache les orchestrer.

Ancres de revision:

- `ezkey-core/src/main/java/org/ezkey/enrollment/domain/entity/Enrollment.java`
- `ezkey-core/src/main/java/org/ezkey/integration/domain/entity/EzkeyAdmin.java`
- `ezkey-core/src/main/java/org/ezkey/authattempt/domain/entity/AuthAttempt.java`
- `ezkey-core/src/main/java/org/ezkey/audit/domain/entity/AuditLog.java`
- `ezkey-core/src/main/java/org/ezkey/integration/domain/entity/AdminToken.java`

#### 4.9.2 Minimal new domain capability to introduce

La capacite domaine minimale et defendable semble etre:

- un aggregate ou dossier de demande de type `PrivacyRequest` ou equivalent;
- eventuellement un objet secondaire de resolution de perimetre (`PrivacyRequestSubjectLink` ou
  equivalent);
- un petit enum de statuts et de types de demande;
- une representation du resultat de traitement ou de l'attestation remise.

Capacites metier attendues sur ce nouvel objet:

- enregistrer la demande et son scope initial;
- rattacher un tenant scope et un operateur responsable;
- consigner la recherche et la resolution des objets concernes;
- consigner une evaluation d'eligibilite a l'acces, a la rectification, a l'effacement ou a
  l'anonymisation;
- stocker un resultat de traitement, y compris refus ou traitement partiel;
- tracer la generation d'un export structure ou d'une attestation.

Ce nouvel objet ne doit pas porter lui-meme toute la donnee personnelle; il doit plutot devenir la
 couche d'orchestration et de preuve de traitement qui manque aujourd'hui.

#### 4.9.3 Services backend likely required

Un support Loi 25 raisonnable appelle vraisemblablement les services suivants.

##### A. Privacy request orchestration service

Service central responsable de:

- creation de la demande;
- transitions d'etat;
- affectation du scope tenant;
- validation du role operateur;
- coordination des etapes d'analyse, export et cloture.

##### B. Subject resolution service

Service dedie a la recherche et consolidation des objets associes a une personne concernee a partir
 d'entrees comme:

- `userIdentifier`;
- `contactEmail`;
- `contactPhoneNumber`;
- `adminId` ou attributs admin;
- IDs connus d'enrollment ou d'auth attempt.

Ce service devra etre explicite sur ses limites, notamment lorsqu'il s'appuie sur des jointures ou
 heuristiques indirectes.

##### C. Export assembly service

Service charge de composer un dossier communicable et structure, avec distinction nette entre:

- donnees restituables en clair;
- donnees a restituer sous forme d'attestation seulement;
- donnees exclues pour raison de securite ou de protection des tiers;
- elements relevant de la simple contextualisation et non de la portabilite stricte.

##### D. Eligibility and redaction service

Service charge d'evaluer:

- ce qui est rectifiable;
- ce qui est supprimable;
- ce qui est anonymisable;
- ce qui doit etre conserve pour integrite, securite ou obligation legitime;
- ce qui doit etre redige avant communication.

##### E. Privacy audit logging policy

Plus qu'un service totalement nouveau, il faudra probablement une politique transversale de
 journalisation des actions Loi 25, pour garantir que toute consultation, export, refus ou action
 de suppression/anonymisation laisse une trace defendable dans `AuditLog`.

#### 4.9.4 Existing services and controllers that can be extended

Plusieurs surfaces actuelles sont reutilisables comme base de travail:

- `EnrollmentController` et `EnrollmentUpdateService` pour les donnees rectifiables et les gardes
  de suppression/revocation;
- `EnrollmentRevocationService` pour la logique de blocage et de raison obligatoire autour de la
  suppression et des transitions irreversibles;
- `AdminProvisioningController` pour les donnees d'identite admin et certaines operations de mise a
  jour;
- `AuthAttemptController` pour l'acces aux tentatives et leur contexte;
- `AuditLogController` pour la consultation de journaux et les surfaces lifecycle deja existantes;
- `AccessControlService` pour les controles de scoping tenant/integration et les autorisations
  d'acces a des objets cibles;
- `AdminTokenCleanupService` et services associes pour la politique de retention sur les traces de
  session admin.

Conclusion pratique: l'Admin API possede deja l'essentiel des lectures par aggregate et des gardes
 d'acces. Ce qui manque est surtout une couche de composition et un contrat dedie.

#### 4.9.5 Domain and lifecycle guardrails that remain non-negotiable

Le futur support Loi 25 devra respecter explicitement les contraintes suivantes.

##### A. Enrollment deletion remains constrained

Les gardes actuelles sur `EnrollmentRevocationService.validateDelete(...)` restent un point dur:

- pas de suppression si historique `AuthAttempt`;
- pas de suppression si enrollment lie au MFA d'un admin;
- pas de suppression si auto-suppression admin interdite par la logique existante.

Implication: l'API Loi 25 ne doit pas promettre un effacement physique systematique. Elle doit
 pouvoir renvoyer un diagnostic motive et proposer, selon le cas, retention, anonymisation ou refus
 motive.

##### B. Audit integrity remains stronger than convenience

Les lignes `AuditLog` et leur chaine d'integrite ne doivent pas devenir librement mutables sous
 pretexte de rectification ou d'effacement. La logique cible doit privilegier:

- redaction ciblee ou anonymisation encadree apres retention, si admissible;
- attestation d'impossibilite d'effacement complet;
- preservation de la preuve d'action et de la coherence forensic.

##### C. Secret material is not a normal export surface

Les proof tokens, token hashes, recovery code hashes et autres artefacts cryptographiques ne doivent
 pas etre traites comme des champs simplement exportables. Une API Loi 25 devra exprimer cette
 distinction au niveau contrat et documentation.

#### 4.9.6 Minimal Admin API surface worth planning

Le chemin le plus pragmatique semble etre l'introduction d'un petit sous-domaine API dedie, plutot
 que d'enrichir de facon diffuse chaque controleur existant.

Endpoints minimaux envisageables:

- `POST /api/v1/admin/privacy/requests`
  pour ouvrir une demande avec son type, son scope et les identifiants initiaux fournis;
- `GET /api/v1/admin/privacy/requests`
  pour lister les demandes selon le scope operateur;
- `GET /api/v1/admin/privacy/requests/{id}`
  pour consulter le dossier, son statut, ses objets resolus et son historique de traitement;
- `POST /api/v1/admin/privacy/requests/{id}/resolve`
  pour lancer ou relancer la resolution des objets concernes;
- `POST /api/v1/admin/privacy/requests/{id}/access-export`
  pour generer un dossier d'acces/export structure;
- `POST /api/v1/admin/privacy/requests/{id}/eligibility-check`
  pour produire un diagnostic d'effacement/anonymisation/rectification;
- `POST /api/v1/admin/privacy/requests/{id}/complete`
  pour cloturer la demande avec resultat, motif, restrictions et attestation;
- `POST /api/v1/admin/privacy/requests/{id}/refuse`
  pour formaliser un refus ou un traitement partiel motive.

Endpoints additionnels possibles seulement si le besoin est confirme:

- `POST /api/v1/admin/privacy/requests/{id}/apply-rectification`;
- `POST /api/v1/admin/privacy/requests/{id}/apply-anonymization`;
- `POST /api/v1/admin/privacy/requests/{id}/apply-erasure`.

Position de travail: l'application automatique d'actions irreversibles devrait venir apres la phase
 d'analyse et d'export, pas dans le premier increment.

#### 4.9.7 Contract and response design implications

Les contrats Admin API devront probablement porter explicitement:

- le type de demande;
- le type de sujet vise;
- le scope tenant;
- les objets resolus avec leur niveau de confiance ou leur methode de rattachement;
- les restrictions applicables;
- la distinction entre `exportable`, `attestable_only`, `excluded_security`, `excluded_third_party`;
- les raisons d'ineligibilite a la suppression ou a l'anonymisation;
- les references d'audit du traitement.

Le format de reponse ne doit pas masquer les limites. Au contraire, il doit expliquer pourquoi un
 champ est communique, masque, exclu ou seulement atteste.

#### 4.9.8 Draft domain and Admin API conclusions

1. Le backend Ezkey est structurellement pret pour un support Loi 25 parce qu'il possede deja les
   agregats, les gardes de cycle de vie et les controles d'acces necessaires.
2. Le manque principal n'est pas une lecture supplementaire d'un aggregate, mais un objet metier de
   demande et un service d'orchestration transverse.
3. La premiere vague API devrait privilegier l'ouverture de dossier, la resolution, l'export
   structure et l'attestation motivee, plutot que l'application immediate d'effacements
   irreversibles.
4. Les controllers existants restent la source de verite pour les mutations locales sur enrollment,
   admin et audit; la nouvelle surface privacy doit les orchestrer plutot que les contourner.

### 4.10 Admin UI consequences

Troisieme derivee technique attendue.

La section devra couvrir un outillage operateur raisonnable, par exemple:

- recherche de la personne concernee ou de ses artefacts associes;
- vue consolidee des renseignements personnels disponibles;
- export structure;
- evaluation de l'eligibilite a la suppression ou a l'anonymisation;
- action guidee avec avertissements lorsque l'effacement complet n'est pas possible;
- attestation ou trace de traitement;
- separation claire des roles Global Admin et Tenant Admin.

#### 4.10.1 UI design stance

Le support Loi 25 dans l'Admin UI ne devrait pas commencer comme une constellation de boutons sur
 chaque ecran existant. La piste la plus defendable est un espace dedie de type dossier operateur,
 avec quelques points d'entree depuis les vues deja existantes.

Pourquoi:

- les demandes Loi 25 sont transverses a plusieurs aggregates;
- elles impliquent evaluation, restrictions, justification et preuve de traitement;
- elles ne se reduisent ni a une simple consultation d'enrollment, ni a un simple export de table.

#### 4.10.2 Existing UI surfaces that can serve as anchors

Les ecrans existants les plus reutilisables ou inspirants sont:

- `ezkey-admin-ui/src/pages/enrollment-detail.tsx` pour la vue detaillee d'un sujet utilisateur;
- `ezkey-admin-ui/src/pages/audit-logs.tsx` pour la consultation de traces, filtres et contexte;
- `ezkey-admin-ui/src/pages/admins.tsx` pour la gestion d'identites administrateur;
- `ezkey-admin-ui/src/routes.tsx` pour l'ajout d'un espace ou workflow dedie.

Implication: un futur module privacy peut s'appuyer sur les patterns existants de page detail,
 table paginee, detail navigation et controle d'acces front, sans changer le langage global de
 l'Admin UI.

#### 4.10.3 Minimal operator workflow worth targeting

Le workflow UI minimal et raisonnable semble etre:

1. ouvrir une demande Loi 25;
2. saisir le type de demande et les identifiants initiaux du sujet;
3. laisser le systeme resoudre les objets associes;
4. afficher un dossier consolide par familles de donnees;
5. montrer clairement ce qui est communicable, ce qui est masque, ce qui est exclu et pourquoi;
6. permettre la generation d'un export structure ou d'une attestation;
7. presenter un diagnostic d'eligibilite pour rectification, anonymisation ou effacement;
8. permettre une cloture motivee avec trace operateur.

Ce workflow correspond mieux a la realite juridique et technique qu'un simple bouton
 "exporter/supprimer" place sur un ecran detail existant.

#### 4.10.4 UI screens or views likely needed

Le rapport detaille devrait au minimum evaluer les vues suivantes.

##### A. Privacy requests list

Une page de liste des demandes avec:

- statut;
- type de demande;
- type de sujet;
- tenant scope;
- date de creation;
- operateur responsable;
- progression du traitement.

##### B. Privacy request detail workspace

Une page detaillee de dossier avec sections distinctes:

- resume de la demande;
- identifiants initiaux fournis;
- objets resolus;
- donnees communicables consolidees;
- restrictions et exclusions;
- chronologie de traitement;
- references d'audit;
- actions disponibles selon le statut.

##### C. Consolidated subject view

Sans dupliquer toutes les pages detail existantes, une vue consolidee devra vraisemblablement
 assembler:

- donnees d'enrollment;
- donnees admin si le sujet est un operateur;
- auth attempts pertinents;
- traces d'audit selectionnees;
- traces de session admin si applicables.

##### D. Export and attestation panel

Une zone clairement separee pour:

- generer l'export structure;
- telecharger ou consulter l'artefact;
- voir la date de generation et la duree de disponibilite;
- afficher les exclusions et leur justification;
- produire une attestation de traitement ou de refus motive.

##### E. Eligibility and action panel

Une zone de diagnostic qui explique:

- ce qui est rectifiable immediatement;
- ce qui exige une action manuelle hors workflow;
- ce qui ne peut pas etre efface physiquement;
- ce qui pourrait etre anonymise ou purge plus tard;
- quels risques ou impacts d'integrite existent.

#### 4.10.5 Role separation and authorization consequences

La separation `Global Admin` / `Tenant Admin` doit etre visible dans l'UX.

- `Tenant Admin` ne devrait voir et traiter que les demandes dans son perimetre de tenant;
- `Global Admin` peut avoir une capacite transverse plus large, mais pas un pouvoir implicite de
  contourner les restrictions metier ou d'integrite;
- certaines actions sensibles comme la cloture d'une demande de refus, ou une anonymisation a effet
  large, pourraient justifier un niveau d'autorisation superieur ou une justification renforcee.

Implication: l'UI doit afficher les restrictions de role de facon lisible, pas seulement compter
 sur des erreurs API tardives.

#### 4.10.6 UX guardrails that matter more than convenience

Le futur parcours UI devra explicitement eviter plusieurs simplifications dangereuses:

- pas de bouton "supprimer toutes les donnees" sans diagnostic prealable;
- pas d'export qui melange sans distinction donnees restituables et secrets techniques;
- pas de promesse d'effacement complet lorsqu'un historique d'audit ou d'auth attempt bloque cette
  option;
- pas d'action irreversible sans recapitulatif, justification et trace.

Au contraire, l'UI devra privilegier:

- des avertissements lisibles;
- des motifs et restrictions affiches clairement;
- une difference nette entre consultation, export, analyse d'eligibilite et application d'action;
- une trace visible du traitement deja effectue.

#### 4.10.7 Draft Admin UI conclusions

1. L'Admin UI devrait porter le sujet Loi 25 comme un workflow de dossier operateur dedie, pas
   comme un simple enrichissement opportuniste de pages existantes.
2. Les pages existantes `enrollment-detail`, `audit-logs` et `admins` restent des ancres utiles,
   mais elles ne suffisent pas seules pour un traitement complet et defendable.
3. Le besoin UI principal est une vue consolidee, explicative et actionnable, qui rende visibles
   les restrictions autant que les capacites du systeme.
4. La qualite du parcours dependra surtout de la clarte sur ce qui est communicable, exportable,
   rectifiable, refusable ou non effacable, pas d'une accumulation d'actions rapides.

### 4.11 Security, audit, and governance guardrails

La section doit couvrir au minimum:

- verification de l'autorite de l'operateur qui agit;
- scoping tenant;
- journalisation des consultations, exports et suppressions;
- contraintes d'integrite d'audit;
- preuve minimale de traitement;
- articulation entre suppression physique, anonymisation et retention legitime.

#### 4.11.1 Authorization is a first-class guardrail, not a UI convenience

Un workflow Loi 25 ne doit jamais etre concu comme un simple parcours documentaire. Il ouvre un
 acces transverse a des donnees personnelles et a des decisions sensibles. Par consequent,
 l'autorite de l'operateur doit etre verifiee explicitement a chaque etape critique.

Constat a partir de l'existant:

- l'Admin API applique deja largement des gardes `@PreAuthorize` par role;
- les acces a des ressources scopees passent deja par `AccessControlService`;
- certaines surfaces sensibles d'audit et d'integrite sont deja reservees aux `GLOBAL_ADMIN`.

Implication pour un futur sous-domaine Loi 25:

- l'ouverture et la consultation d'une demande doivent etre soumises au role admin approprie;
- la consultation d'un dossier ne doit jamais depasser le tenant scope autorise;
- les actions a impact fort comme generation d'export, cloture d'un refus, anonymisation ou
  effacement doivent faire l'objet d'une autorisation explicite et defendable;
- certaines actions instance-level ou inter-tenant devraient probablement rester reservees au
  `GLOBAL_ADMIN`.

Ancres de revision:

- `ezkey-admin-api/src/main/java/org/ezkey/admin/security/AccessControlService.java`
- `ezkey-admin-api/src/main/java/org/ezkey/admin/controller/EnrollmentController.java`
- `ezkey-admin-api/src/main/java/org/ezkey/admin/controller/AuthAttemptController.java`
- `ezkey-admin-api/src/main/java/org/ezkey/admin/controller/AuditLogController.java`

#### 4.11.2 Tenant scoping must remain explicit throughout the workflow

Le plus grand risque d'un outillage Loi 25 mal concu serait de devenir une surface de recherche
 transverse qui contourne, de fait, le cloisonnement tenant.

Garde-fous a maintenir:

- la demande doit porter un tenant scope explicite, sauf cas exceptionnel reserve au niveau global;
- la resolution des objets concernes doit respecter ce scope des la recherche initiale;
- aucun export ne doit agreger des donnees de tenants multiples sans justification metier et garde
  de role exceptionnels;
- l'UI ne doit pas suggerer qu'une recherche globale est normale pour un `TENANT_ADMIN`.

Position de travail: le tenant scope doit etre visible dans le dossier, dans les contrats API et
 dans le journal de traitement, pas seulement applique implicitement par la couche de securite.

#### 4.11.3 Privacy operations must themselves be audited

Un workflow Loi 25 defendable doit laisser des traces sur les actions suivantes au minimum:

- creation d'une demande;
- consultation d'un dossier sensible;
- lancement d'une resolution ou d'un export;
- production d'une attestation;
- diagnostic d'ineligibilite;
- rectification appliquee;
- anonymisation ou effacement applique;
- refus ou traitement partiel motive;
- cloture de la demande.

Le repo montre deja une discipline utile sur ce point:

- motifs (`reason`) deja presents sur plusieurs actions irreversibles ou sensibles;
- `AdminAuditConstants` centralise les actions d'audit;
- `AuditDetailsBuilder` et certains details JSON structures permettent de tracer des contextes de
  facon plus exploitable qu'un simple texte libre.

Implication: le chantier Loi 25 devrait preferer des actions d'audit dediees et des `event_details`
 structures, plutot qu'une simple reutilisation generique de messages libres.

#### 4.11.4 Audit integrity remains a hard boundary

Ezkey a deja une surface explicite d'integrite d'audit:

- checkpoints;
- verification de chaine;
- incidents;
- archivage scelle;
- eligibilite d'archivage;
- declaration de gap.

Cette capacite n'est pas cosmetique. Elle impose des limites fortes a tout futur support Loi 25.

Concretement:

- une demande d'acces ou de rectification ne doit pas autoriser une mutation libre des lignes
  `AuditLog`;
- une demande d'effacement ne doit pas casser la chaine d'integrite ni supprimer les preuves
  minimales de traitement et d'action;
- toute strategie d'anonymisation sur l'audit devra etre compatible avec la logique de checkpoint,
  de verification et d'archivage;
- le rapport final devra expliciter qu'il existe des categories de donnees ou l'integrite et la
  forensic limitent volontairement l'effacement physique.

Ancres de revision:

- `ezkey-admin-api/src/main/java/org/ezkey/admin/controller/AuditLogController.java`
- `docs/AUDIT_LOG_LIFECYCLE_REFRAMING.md`
- `docs/DATABASE_PARTITIONING_COMPLIANCE_CONSIDERATIONS.md`

#### 4.11.5 Proof of treatment must be minimal, structured, and reviewable

La preuve de traitement d'une demande Loi 25 ne devrait pas se limiter a dire "faite" ou
 "refusee". Une preuve minimale defendable devrait inclure:

- l'identifiant de la demande;
- le type de demande;
- le scope traite;
- l'operateur responsable;
- les dates d'ouverture, de revue et de cloture;
- les objets principaux examines;
- les restrictions appliquees;
- le resultat: export, rectification, refus, anonymisation partielle, retention maintenue, etc.;
- les references d'audit du traitement;
- l'identifiant ou checksum de l'artefact remis, si export il y a.

Cette preuve peut etre portee partiellement par un futur objet `PrivacyRequest` et partiellement par
 l'audit existant, mais elle doit rester lisible et reconstituable sans re-enquete manuelle lourde.

#### 4.11.6 Secrets and high-risk fields require a stricter communication policy

Toutes les donnees rattachees a une personne ne sont pas communicables de la meme facon.

Categories a distinguer explicitement dans le futur design:

- **communicables en clair**: donnees d'identite, de contact, certains attributs metier;
- **communicables sous forme d'attestation**: presence d'un secret, existence d'une session,
  execution d'une action;
- **non communicables pour securite**: proof tokens, hashes, recovery code hashes, certains details
  cryptographiques;
- **communicables sous reserve ou redaction**: traces d'audit, `event_details`, `reason`, messages
  pouvant exposer des tiers ou des details sensibles.

Implication: les contrats API et l'UI doivent rendre visible cette politique de communication, au
 lieu de la laisser implicite dans du code de serialization ou des exceptions ad hoc.

#### 4.11.7 Suppression, anonymization, and retention must be articulated as separate outcomes

Le rapport doit rester explicite sur un point central: une demande Loi 25 ne debouche pas toujours
 sur un effacement physique.

Les issues possibles a distinguer sont:

- **suppression physique** lorsque l'objet est admissible et sans impact d'integrite excessif;
- **anonymisation ou redaction** lorsque la conservation du support reste legitime mais que certains
  elements peuvent etre neutralises;
- **retention maintenue** lorsque la securite, l'audit, la gouvernance ou une autre base legitime
  l'exigent;
- **refus motive ou traitement partiel** lorsque la demande ne peut pas etre satisfaite integralement.

Le chantier doit donc eviter deux ecueils:

- presenter l'anonymisation comme un synonyme automatique d'effacement;
- presenter la retention comme un blocage purement technique plutot que comme un arbitrage
  gouverne, explicable et tracable.

#### 4.11.8 Free-text and third-party exposure require explicit review points

Les champs libres ou semi-structures sont le principal point de risque en termes de sur-divulgation:

- `context_title`
- `context_message`
- `event_details`
- `error_message`
- `reason`

Deux garde-fous doivent etre poses:

- revue ou redaction avant communication lorsqu'un contenu peut exposer des tiers, des secrets ou
  des details de securite disproportionnes;
- structuration accrue des details d'audit futurs pour reduire la dependance a des textes libres.

Cette exigence rejoint directement les restrictions prevues par la Loi 25 sur les tiers et les
 motifs de refus partiel.

#### 4.11.9 Governance conclusions for the detailed report

1. Un workflow Loi 25 n'est acceptable que s'il est scope, autorise et journalise comme une action
   sensible de gouvernance, pas comme une simple consultation back-office.
2. L'integrite d'audit et les gardes lifecycle existants doivent etre traites comme des contraintes
   fondatrices du design, pas comme des exceptions a contourner ensuite.
3. La preuve minimale de traitement doit etre structuree, reconstituable et lisible pour un audit
   interne ou une revision documentaire ulterieure.
4. La doctrine de reponse doit distinguer clairement communication, attestation, redaction,
   anonymisation, retention et refus motive.

### 4.12 Gap analysis and phased recommendations

Le rapport doit se conclure par:

- un etat actuel vs cible recommande;
- une priorisation par phases;
- les arbitrages ouverts.

#### 4.12.1 Executive gap analysis

L'analyse menee jusqu'ici conduit a une conclusion simple: Ezkey n'est pas depourvu de capacites
 utiles pour soutenir des demandes Loi 25, mais il ne dispose pas encore d'un workflow natif,
 explicite et tracable de traitement.

En etat actuel, Ezkey offre deja:

- un modele de donnees suffisamment riche pour retrouver une large partie des renseignements
  personnels pertinents;
- des agrégats metier clairs (`Enrollment`, `EzkeyAdmin`, `AuthAttempt`, `AuditLog`, `AdminToken`);
- des gardes lifecycle robustes sur la suppression, la revocation et les transitions irreversibles;
- un scoping tenant et des controles d'acces existants;
- une surface d'audit et d'integrite deja mature sur plusieurs dimensions critiques.

En revanche, Ezkey ne fournit pas encore nativement:

- un dossier de demande Loi 25 explicite;
- une resolution consolidee des objets lies a une personne concernee;
- un export structure et motive, distingue des simples lectures d'agregats;
- une preuve minimale de traitement lisible de bout en bout;
- une doctrine produit explicite sur ce qui est exportable, attestable, redactable ou non
  communicable;
- un outillage UI dedie pour orchestrer ces decisions.

Le gap principal est donc un gap d'orchestration, de gouvernance et d'explicitation, plus qu'un gap
 de lecture brute de donnees.

#### 4.12.2 Current state versus recommended target state

Tableau de synthese recommande:

| Axe | Etat actuel | Etat cible recommande | Ecart | Priorite |
| --- | --- | --- | --- | --- |
| Recherche et consolidation du sujet | Possible mais dispersee entre aggregates et jointures ad hoc | Resolution explicite et tracable via dossier de demande | Eleve | Haute |
| Workflow operateur | Pas de workflow natif dedie | Workflow de dossier avec statut, restrictions et resultat | Eleve | Haute |
| Export structure | Lectures existantes, pas de dossier d'acces structure natif | Export consolide, motive, avec exclusions explicites | Eleve | Haute |
| Rectification | Mutations locales possibles sur certains aggregates | Rectification gouvernee, contextualisee par dossier | Moyen a eleve | Haute |
| Effacement / anonymisation | Partiellement bloque ou non explicite selon les objets | Diagnostic d'eligibilite, puis action ciblee selon categorie | Eleve | Haute |
| Audit des operations Loi 25 | Audit generique reutilisable mais non dedie | Journalisation explicite des actions privacy et preuve de traitement | Moyen | Haute |
| Integrite et retention | Forte maturite cote audit et lifecycle | Conserver ces gardes en les integrant au workflow Loi 25 | Faible sur le principe, fort sur l'integration | Haute |
| Admin UI | Pas de parcours dedie | Espace operateur de dossier, vue consolidee, export, attestation | Eleve | Moyenne a haute |
| Documentation / politique produit | Positionnement privacy public existe | Doctrine produit detaillee sur les limites et capacites Loi 25 | Moyen | Moyenne |

#### 4.12.3 Recommended implementation posture

La recommandation de travail la plus defendable a ce stade est:

- ne pas lancer une refonte globale du modele identitaire;
- ne pas traiter la conformite Loi 25 comme un simple bouton d'export ou de suppression;
- introduire un sous-domaine restreint de traitement de demande;
- privilegier d'abord la traçabilite, l'export motive et l'attestation;
- reporter a une phase ulterieure les mutations irreversibles automatisees les plus sensibles.

Autrement dit, la cible recommandee n'est pas une automatisation maximale immediate, mais une
 capacite operateur defendable, progressive et coherente avec les contraintes existantes d'Ezkey.

#### 4.12.4 Phase 1 recommendation - Foundation and dossier workflow

Objectif:

Donner a Ezkey un support natif minimal mais serieux pour ouvrir, instruire et cloturer une demande
 Loi 25 sans encore automatiser les cas les plus irreversibles.

Perimetre recommande:

- introduire l'objet de demande (`PrivacyRequest` ou equivalent);
- introduire les statuts, types de demande et metadonnees minimales de traitement;
- ajouter la surface Admin API de base:
  - creation de demande
  - consultation/liste
  - resolution des objets
  - generation d'export d'acces
  - cloture motivee / refus motive
- ajouter une premiere journalisation dediee des actions privacy;
- poser une premiere doctrine de classification de sortie:
  - communicable
  - attestable seulement
  - exclu securite
  - exclu tiers / redaction requise
- creer dans l'Admin UI un espace dedie de liste + detail de dossier.

Valeur:

- couvre l'acces, la portabilite et la preuve de traitement de facon credible;
- rend le systeme revisable et explicable;
- limite le risque de promesse excessive sur l'effacement.

#### 4.12.5 Phase 2 recommendation - Eligibility, redaction, and guided rectification

Objectif:

Passer d'un dossier descriptif a un dossier evaluatif et actionnable sur les cas non destructifs ou
 partiellement destructifs.

Perimetre recommande:

- introduire un service de diagnostic d'eligibilite;
- expliciter dans les reponses API les motifs d'ineligibilite a l'effacement ou a l'anonymisation;
- ajouter une capacite de redaction ou d'exclusion structuree pour certains champs a risque;
- ajouter les workflows de rectification encadree pour les champs vraiment rectifiables;
- enrichir l'UI avec un panneau de diagnostic et d'actions guidees.

Valeur:

- donne une reponse operateur plus nuancee que "oui/non";
- commence a traiter la tension entre exactitude, retention et audit;
- prepare le terrain pour les actions plus sensibles sans les banaliser.

#### 4.12.6 Phase 3 recommendation - Targeted anonymization and retention alignment

Objectif:

Traiter les besoins plus difficiles de retention, anonymisation et purge sans affaiblir l'integrite
 ni la forensic.

Perimetre recommande:

- definir les surfaces admissibles a l'anonymisation ou a la redaction apres retention;
- articuler cette logique avec l'audit lifecycle, les checkpoints et les politiques de purge;
- evaluer une table de lien sujet-objets et/ou metadonnees d'artefact plus riches si le volume ou la
  complexite le justifient;
- eventuellement introduire des actions appliquees d'anonymisation ou d'effacement ciblees, apres
  diagnostic et gouvernance renforces.

Valeur:

- traite les cas les plus sensibles de maniere proportionnee;
- aligne mieux l'outil avec la destruction/anonymisation en fin de finalite;
- preserve la maturite d'audit deja existante.

#### 4.12.7 What should not be in the first implementation wave

La premiere vague ne devrait pas inclure:

- une refonte transversale `person/party` du domaine;
- une automatisation large de l'effacement physique sur des objets a fort enjeu d'audit;
- des mutations directes de `AuditLog` sous pretexte de rectification;
- une UI simpliste de type "tout exporter / tout supprimer";
- des engagements produit qui feraient croire a un droit d'effacement absolu sur toutes les traces.

Ces choix augmenteraient fortement la complexite et le risque de regression avant meme que le cadre
 operateur soit stabilise.

#### 4.12.8 Recommended backlog slices

Pour transformer cette analyse en chantier executable, les premiers lots les plus coherents semblent
 etre:

1. **Schema and domain slice**
   introduction de `PrivacyRequest`, enums de statut/type, persistence minimale, audit actions
   associees.
2. **Admin API slice**
   endpoints de creation, lecture, resolution, export, cloture/refus.
3. **Export policy slice**
   assembleur de dossier, classification des champs, attestation et exclusions motivees.
4. **Admin UI slice**
   liste des demandes, detail de dossier, vue consolidee, panneau d'export et statut.
5. **Eligibility slice**
   diagnostic d'effacement/anonymisation/rectification avec restitution explicative.
6. **Retention and anonymization slice**
   seulement apres stabilisation des slices precedentes.

#### 4.12.9 Open tradeoffs to carry into implementation

Les arbitrages suivants restent ouverts et devront etre assumes explicitement dans la phase de mise
 en oeuvre:

- faut-il modeliser seulement la demande, ou aussi une table explicite de resolution des objets;
- faut-il produire un artefact d'export persiste, ou un export ephemere regenerable;
- jusqu'ou structurer `event_details` avant de lancer des workflows de communication plus avances;
- faut-il autoriser certaines rectifications appliquees depuis le dossier, ou garder une logique de
  delegation vers les controles existants;
- quels cas exacts justifient une capacite `GLOBAL_ADMIN` transverse et lesquels doivent rester
  strictement scopes au tenant;
- quelle part de l'anonymisation peut etre automatisee sans mettre en risque la valeur forensic et
  l'integrite d'audit.

#### 4.12.10 Final recommendation for the report

La recommandation globale a porter dans le rapport detaille est la suivante:

1. Ezkey doit se positionner non comme un systeme qui promet un effacement universel, mais comme un
   produit qui permet a l'operateur de traiter des demandes Loi 25 de facon structuree,
   defendable et documentee.
2. La premiere cible produit doit etre un **dossier de demande Loi 25** avec resolution,
   export/acces, restrictions explicites et preuve de traitement.
3. Les gardes d'integrite d'audit, de scoping tenant et de lifecycle doivent etre conserves comme
   principes de conception non negociables.
4. Les actions plus sensibles d'anonymisation appliquee ou d'effacement cible doivent venir dans un
   second temps, apres stabilisation du workflow, de la classification de sortie et de la doctrine
   de retention.

## 5. Recommended work sequence

### Step 1 - Regulatory framing

Construire le cadre d'obligations a partir des sources officielles avant tout raisonnement produit.

### Step 2 - Personal data inventory

Cartographier les renseignements personnels reels et les relier aux entites, tables et flux.

### Step 3 - Database impact analysis

Deriver les impacts schema et persistence a partir de l'inventaire et des droits applicables.

### Step 4 - Domain and API impact analysis

Deriver les changements necessaires sur les entites, services, controleurs et contrats Admin API.

### Step 5 - Admin UI consequence analysis

Deriver les surfaces, parcours et garde-fous operateur a partir des besoins metier et API.

### Step 6 - Recommendation and phasing

Proposer un chemin de mise en oeuvre pragmatique, en preservant la coherence avec les contraintes
existantes d'Ezkey.

## 6. Prioritized source set

### 6.1 Official legal and institutional sources

- Act respecting the protection of personal information in the private sector:
  [legisquebec.gouv.qc.ca/en/document/cs/P-39.1](https://www.legisquebec.gouv.qc.ca/en/document/cs/P-39.1)
- Commission d'acces a l'information du Quebec - entreprises et organisations privees:
  [cai.gouv.qc.ca/entreprises/](https://www.cai.gouv.qc.ca/entreprises/)

### 6.2 Public Ezkey commitments

- `sites/ezkey-org/privacy.html`
- `sites/ezkey-org/fr/confidentialite.html`

### 6.3 Ezkey framing and governance documents

- `PRD.md`
- `README.md`
- `docs/LIFECYCLE_GOVERNANCE.md`
- `plans/mobile_privacy_compliance_roadmap.plan.md`
- `docs/analysis/multi-tenancy-strategy.md`
- `docs/audit/AUDIT_LOGGING_IMPLEMENTATION.md`
- `docs/AUDIT_LOG_LIFECYCLE_REFRAMING.md`
- `docs/DATABASE_PARTITIONING_COMPLIANCE_CONSIDERATIONS.md`

### 6.4 Technical implementation anchors

- `ezkey-core/src/main/java/org/ezkey/enrollment/domain/entity/Enrollment.java`
- `ezkey-core/src/main/java/org/ezkey/integration/domain/entity/EzkeyAdmin.java`
- `ezkey-core/src/main/java/org/ezkey/audit/domain/entity/AuditLog.java`
- `ezkey-admin-api/src/main/java/org/ezkey/admin/controller/EnrollmentController.java`
- `ezkey-admin-api/src/main/java/org/ezkey/admin/controller/AuditLogController.java`
- `ezkey-admin-api/src/main/java/org/ezkey/admin/service/EnrollmentRevocationService.java`
- `ezkey-admin-ui/src/pages/enrollment-detail.tsx`
- `ezkey-admin-ui/src/pages/audit-logs.tsx`
- `ezkey-admin-ui/src/routes.tsx`

## 7. Source register requirement

Le rapport final devra inclure une annexe obligatoire de type registre de sources.

Tableau recommande:

| Type de source | Reference exacte | Article / section | Date de consultation | Sujet couvert | Usage dans le rapport | Niveau d'autorite |
| --- | --- | --- | --- | --- | --- | --- |

Regles obligatoires:

- chaque obligation Loi 25 mentionnee doit pointer vers au moins une source officielle precise;
- chaque recommandation technique importante doit pointer vers au moins une ancre technique du
  repo ou etre marquee comme capacite nouvelle;
- chaque citation doit etre assez precise pour etre re-verifiable plus tard;
- les sources de vulgarisation ne doivent jamais remplacer la source juridique lorsqu'un article
  de loi est disponible.

## 8. Deliverables expected from the detailed analysis

Le travail detaille issu de ce plan devra produire au minimum:

1. une matrice des obligations Loi 25 pertinentes;
2. une cartographie des renseignements personnels dans Ezkey;
3. une matrice champ par champ des regimes d'action;
4. une analyse des impacts sur la base de donnees;
5. une analyse des impacts sur les entites, services et controleurs;
6. une analyse des consequences sur l'Admin UI;
7. une liste priorisee des ecarts et recommandations;
8. une annexe de sources historisable.

### 8.1 Concrete implementation roadmap candidate

La traduction la plus exploitable de ce plan en chantier produit/technique peut se faire par lots
 successifs, chacun avec un perimetre de code, un contrat attendu et un critere de fin explicite.

Important:

- cette roadmap sert de decoupage candidat et d'ordre de travail;
- elle ne vaut pas specification detaillee de tous les lots a l'avance;
- apres le premier lot structurant, une revue d'integrite et de coherence doit confirmer ou ajuster
  la suite avant tout approfondissement de phase ulterieure.

#### Lot A - Privacy request schema and domain foundation

Objectif:

Creer la base metier minimale du dossier Loi 25 sans encore toucher aux actions irreversibles les
 plus sensibles.

Artefacts attendus:

- migration SQL pour `ezkey_privacy_request` ou equivalent;
- entite domaine et repository associes;
- enums de type de demande, type de sujet et statut;
- DTOs minimaux de creation et de lecture;
- premiere convention d'audit pour les operations privacy.

Gate de sortie specifique:

- revue de coherence schema + contrat API;
- comparaison avec le plan Loi 25 pour verifier que les hypotheses structurantes restent valides;
- decision explicite de poursuivre telle quelle, d'ajuster le lot B, ou de revenir sur certaines
  hypotheses de modelisation.

Zones de code probables:

- `ezkey-core/src/main/resources/db/migration/`
- `ezkey-core/src/main/java/org/ezkey/.../domain/entity/`
- `ezkey-core/src/main/java/org/ezkey/.../domain/repository/`
- `ezkey-admin-api/src/main/java/org/ezkey/admin/dto/`
- `ezkey-admin-api/src/main/java/org/ezkey/admin/constants/AdminAuditConstants.java`

#### Lot B - Privacy request Admin API

Objectif:

Exposer un contrat minimal pour ouvrir, consulter et cloturer un dossier de demande.

Artefacts attendus:

- controller dedie de type `PrivacyRequestController`;
- service d'orchestration dedie;
- endpoints de creation, liste, lecture detaillee, cloture et refus;
- regles `@PreAuthorize` et scoping tenant explicites;
- tests d'integration controller sur le role et le scoping.

Zones de code probables:

- `ezkey-admin-api/src/main/java/org/ezkey/admin/controller/`
- `ezkey-admin-api/src/main/java/org/ezkey/admin/service/`
- `ezkey-admin-api/src/test/java/`

#### Lot C - Subject resolution and consolidated access export

Objectif:

Permettre la recherche consolidee des objets lies au sujet et produire un dossier d'acces
 structure avec exclusions motivees.

Artefacts attendus:

- service de resolution de sujet;
- assembleur d'export structure;
- schema de reponse distinguant `communicable`, `attestable_only`, `excluded_security`,
  `excluded_third_party`;
- premiere version de l'artefact d'export ou de son metadata contractuel;
- journalisation dediee de la generation d'export.

Zones de code probables:

- `ezkey-admin-api/src/main/java/org/ezkey/admin/service/`
- `ezkey-admin-api/src/main/java/org/ezkey/admin/dto/response/`
- contrats OpenAPI generes ensuite si le changement est retenu

#### Lot D - Admin UI privacy workspace

Objectif:

Donner a l'operateur un espace de travail lisible pour la demande, plutot qu'une dispersion dans
 les pages existantes.

Artefacts attendus:

- nouvelle route dediee;
- page liste des demandes;
- page detail de dossier;
- section de donnees consolidees;
- section export / attestation;
- etats UI de role, restriction et statut.

Zones de code probables:

- `ezkey-admin-ui/src/routes.tsx`
- `ezkey-admin-ui/src/pages/`
- `ezkey-admin-ui/src/components/`
- `ezkey-admin-ui/src/locales/`

#### Lot E - Eligibility diagnostics and guided actions

Objectif:

Passer d'un dossier descriptif a un dossier evaluatif, avec explication explicite des limites et
 options de traitement.

Artefacts attendus:

- endpoint de diagnostic d'eligibilite;
- reponse explicative par categorie de donnee ou de surface;
- UI de diagnostic avec avertissements et restrictions;
- premiers workflows de rectification encadree si retenus.

#### Lot F - Retention, anonymization, and advanced governance

Objectif:

Traiter les cas de retention et d'anonymisation uniquement apres stabilisation des lots precedents.

Artefacts attendus:

- doctrine de retention/anonymisation formalisee;
- eventuelles actions appliquees ciblees;
- articulation explicite avec audit lifecycle, purge et checkpoints;
- documentation operateur et limites produit mises a jour.

### 8.2 Definition of done by slice

Pour qu'un lot soit considere comme termine, il devrait satisfaire au minimum les criteres
 suivants.

#### Definition of done - Schema/domain lot

- migration reversible en environnement de dev;
- persistence et lecture fonctionnelles;
- statuts/types couverts par tests unitaires ou d'integration;
- impact documente dans ce plan ou dans la spec de mise en oeuvre.

#### Definition of done - Admin API lot

- endpoints documentes et scopes correctement;
- couverture de tests sur autorisation, tenant scope et cas principaux;
- audit des actions sensibles verifie;
- erreurs explicites en cas d'ineligibilite ou de restriction.

#### Definition of done - Export lot

- structure de sortie stable;
- distinction explicite entre donnees communiquees, attestees, masquees ou exclues;
- artefact ou metadata de livraison tracable;
- absence d'exposition involontaire de secrets ou de tiers dans les tests de validation.

#### Definition of done - Admin UI lot

- parcours liste -> detail -> export compréhensible;
- restrictions de role visibles;
- statuts et exclusions rendus lisibles pour l'operateur;
- couverture ciblee au moins sur les helpers critiques et, si utile, sur un scenario browser.

#### Definition of done - Governance lot

- references d'audit consultables;
- preuve minimale de traitement reconstituable;
- limites de retention, anonymisation et refus motive documentees;
- coherence verifiee avec `docs/LIFECYCLE_GOVERNANCE.md` et les surfaces d'audit existantes.

## 9. Verification checklist

1. Le rapport detaille suit bien la chaine d'analyse: loi -> donnees -> DB -> domaine/API -> UI.
2. Les obligations citees sont rattachees a des sources officielles precises.
3. Les recommandations produit et techniques sont rattachees a des ancres de code ou marquees
   comme nouvelles.
4. Les tensions entre effacement, anonymisation, retention et audit sont traitees explicitement.
5. Le rapport separe clairement la responsabilite d'Ezkey de celle de l'operateur.
6. Le registre de sources permet une relecture historique fiable.

## 10. Open decisions to surface in the detailed report

- Faut-il modeliser un dossier de demande Loi 25 comme concept explicite dans Ezkey?
- Jusqu'ou l'export structure doit-il aller nativement en phase initiale?
- Quelles donnees d'audit doivent rester non supprimables meme en cas de demande d'effacement?
- Quand faut-il preferer anonymisation a suppression physique?
- Quel niveau de visibilite donner a Tenant Admin vs Global Admin sur ces workflows?
- A quel niveau de stabilite schema/contrat juge-t-on la phase 1 suffisante pour ouvrir la phase 2
  sans derive d'hypotheses?
