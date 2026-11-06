# Vision Note — `V-2026-10-16-fsm-deterministic-boundaries` Machines à états comme générateur de signal

## Metadata

- **ID:** `V-2026-10-16-fsm-deterministic-boundaries`
- **Status:** `draft`
- **Lane:** méthode (qualité d'autonomie « AI Software Factory ») + inventaire de candidats programme
- **Created at:** `2026-10-16`
- **Updated at:** `2026-10-16`
- **Captured by:** Marc
- **Priority:** `P2`
- **Langue:** français, à la demande explicite de Marc (exception à la convention anglaise du
  corpus ; à traduire si la note est promue)
- **Baseline code:** `main` @ `0e2b3cda` (après merge de [#835](https://github.com/mgagp/ezkey/pull/835)).
  Les citations `fichier:ligne` renvoient à ce commit.
- **Does not edit:** [`product-orientation-notes.md`](product-orientation-notes.md) (index mis à
  jour après merge sur `main`)
- **Ne modifie aucun code.** Note de réflexion, aucune décision verrouillée.

## Intention

Les agents de codage hallucinent et ne sont pas déterministes. Répéter la spécification aligne
humain et IA, mais laisse des zones d'ombre. Modéliser un sous-ensemble du domaine en machine à
états finis (FSM) remet une frontière déterministe : on raisonne sur des états nommés, des
transitions, des gardes et des effets, dans un ordre fixe. On cherche les zones d'Ezkey où cet
exercice serait, comme sur le mobile, un **générateur fort de signaux fiables et actionnables**.
Le critère n'est pas d'avoir un beau diagramme.

**Source.** Marc a cité une société vue sur YouTube (« Xcase », nom à vérifier). Une recherche
n'a trouvé sous ce nom qu'un outil de modélisation de bases de données, sans lien avec le sujet.
Cette note ne cite donc aucune source externe. Voir la question 5.

---

## 1. Retour d'expérience — FSM mobile (#834 / #835)

Livrables : [`MOBILE_APP_STATE_MACHINE.md`](../../../ezkey_mobile/docs/MOBILE_APP_STATE_MACHINE.md),
[`mobile-app-state-machine.html`](../../../ezkey_mobile/docs/mobile-app-state-machine.html) et
[`MOBILE_APP_STRATEGIC_ORIENTATION.md`](../../../ezkey_mobile/docs/MOBILE_APP_STRATEGIC_ORIENTATION.md).
Les ajustements de code passent par [#833](https://github.com/mgagp/ezkey/pull/833) (encore ouverte)
et [#844](https://github.com/mgagp/ezkey/issues/844).

### Ce qui a produit du signal

1. **Une FSM descriptive, tirée du code** (`fichier:ligne`), sans librairie. Elle a mis au jour
   14 décisions cachées : du comportement existait sans intention explicite. Bilan : 13 décisions,
   1 question fermée par une autre, aucune laissée ouverte.
2. **L'inventaire des gardes et des refs.** Il a révélé deux vrais défauts : le double `goBack`
   pendant un `respond` en vol (Finding 4), et le verrou de décision attaché à l'écran au lieu de
   `authAttemptId` (Finding 9). Les deux sont corrigés dans #833.
3. **Les arêtes entre acteurs.** Exemple : l'Admin annule pendant que le téléphone affiche la
   demande. La Question 8 a fixé l'attendu (409, phrase « plus active », Retour seul). Le cas QA
   Sphere MOB-AUTH-007 a donc pu **échouer contre un oracle explicite**, et cet échec est devenu
   #844. Sans FSM, ce comportement aurait été « bizarre », pas « faux ».
4. **Le recul sur la boucle vécue.** Le gain le plus net est ce qu'on a décidé de **ne pas**
   construire : pas de minuteur sur le téléphone, pas de champ d'expiration sur `pending`, pas de
   relance automatique, pas de protocole de libération, et le `respond` idempotent est différé.
   La FSM a retiré de la complexité accidentelle au lieu d'en ajouter.
5. **La critique adverse par mandat.** Elle a corrigé une prémisse fausse : « HTTP 200 = décision
   engagée » (Christophe ; un 200 peut porter `FAILED`). Elle a ancré la baseline sur un commit
   (Patrick) et ajouté les arêtes oubliées : app tuée, TTL écoulé pendant la biométrie
   (Christophe, Patrick).

### Ce qui a coûté

- **Le volume.** #835 ajoute environ 1 750 lignes, dont 890 pour la note d'orientation. Les avis
  des relecteurs et les options écartées y restent, et la note est longue à relire.
- **Une baseline sur une branche non mergée** (#833 @ `fcc31fbf`). Il a fallu des réserves
  « pending #833 », une dette de rafraîchissement des numéros de ligne et une chorégraphie
  « doc d'abord, puis rebase de #833 ». Pendant ce temps, #833 a grossi (+3 680 / −4 052) et
  reste ouverte.
- **Trois documents pour un seul sujet** (FSM, HTML, orientation), plus les synchronisations avec
  les flux fonctionnels et `docs/ENDPOINT.md`.

### Ce qui n'a pas servi (ou peu)

- **Le HTML interactif.** Aucune trace qu'il ait éclairé une décision. Il coûte une seconde
  maintenance à chaque changement de la FSM.
- **Les « user stories »** dans la FSM. Elles redisent les flux fonctionnels.
- **Les tableaux d'options conservés après décision.** Ils ont une valeur d'archive, mais git la
  porte déjà.
- **Le catalogue de cas d'usage**, refusé explicitement à la clôture : la boucle vécue suffisait.

### Leçons à réutiliser

- Ancrer la baseline sur `main` (ou sur un SHA mergé), jamais sur une branche en vol.
- Poser d'abord la question de la boucle vécue, avant les questions d'écran.
- La valeur vient de la **matrice état × acteur** et des **gardes**, pas du diagramme.
- Une décision vaut une ligne « Locked » dans le document propriétaire. Les options appartiennent
  au brouillon.

---

## 2. Inventaire des candidats

Barème : **Valeur** = criticité sécurité ou UX, nombre d'états et d'acteurs, ambiguïté actuelle,
fréquence de bugs. La fréquence est une heuristique : `git log --grep` sur les commits `fix`
depuis 2026-07-01, avec des recouvrements. **Coût** : **S** = un document ≤ ~300 lignes, un
module, ≤ 8 questions ; **M** = 2 ou 3 modules, ~10 à 15 questions ; **L** = plusieurs modules et
HA.

| # | Candidat | Entités et cycles (code réel) | Risques connus | Valeur | Coût |
| --- | --- | --- | --- | --- | --- |
| **A** | **Tentative d'authentification, vue serveur multi-acteurs** : la moitié serveur de la FSM mobile | `AuthAttemptStatus` `PENDING→READ→ACCEPTED/REJECTED/INVALID/EXPIRED`. Écrivains : `AuthAttemptService` L418 et L506, `AuthAttemptPendingService` L242, `AuthAttemptRespondService` L307 et L311, `AuthAttemptTxHelper` L68 et L87, `AuthAttemptExpiryScheduler`. Acteurs : appareil, attente Admin (`passwordless-wait`, ≤ 300 s), attente Integration API (SDK, PAM, Acme), annulation Admin ou Integration, planificateur | #844, #831, #833 ouverte, #793 (proxy 100 s), [`V-2026-07-18`](V-2026-07-18-authentication-wait-evolution.md) ; ~19 commits `fix` | Très haute : un approve ouvre une session. 6 états × 5 acteurs. Codes 200 `FAILED` / 400 / 401 / 408 / 409 propres à chaque acteur | **S–M** |
| **B** | **Accès administrateur** : identité × crédential × codes × session | `AdminLifecycleStatus` `PENDING_ACTIVATION/ACTIVE/DEACTIVATED` **plus** booléen `active` (`EzkeyAdmin` L102, L277, L287). Enrôlement `status × active`. Codes de récupération, émis seulement après le premier login. Jeton de session (rotation, révocation, nettoyage). Jeton de récupération à usage limité. Session évaluateur temporaire avec planificateur d'expiration. Tenant actif. Côté Admin UI : `LoginState` à 6 valeurs × `authFlow` à 3 (`login.tsx` L29, L81) | SEC-006/021/024/026/027, #637 (jetons révoqués ressuscités), révocation des sessions au reset, #654, #758 ; grill [2026-06-05](../backlog/grill-sessions/2026-06-05-admin-enrollment-vs-login-state-clarification-grill-me.md) (enrôlement vérifié sans codes : écart de modèle mental) ; ~24 commits `fix` | Maximale : accès à la console. Grand produit d'états, dont la plupart des combinaisons sont illégales. Double codage enum + booléen | **M** |
| **C** | **Actions lourdes et jobs** : exclusion, crash, reprise, réplica, proxy | `ReencryptionBatch.BatchStatus` `PENDING/IN_PROGRESS/COMPLETED/FAILED/PAUSED` (L553–567). `EncryptionKey.KeyStatus` `PENDING/PRIMARY/ENABLED/DISABLED`. `IntegrityAsyncJobStatus` (dont `INTERRUPTED`). `ScheduledJobLastRunStatus`. Verrou lourd local au processus, ShedLock, ~12 planificateurs sur un thread unique | #789, #790 (P1), #791, #745, #747, #784, #782, #757 ; ~18 commits `fix` (clés et ré-chiffrement) | Haute : intégrité des données au repos et disponibilité HA. Signaux statiques déjà visibles (voir plus bas) | **M** (tranche ré-chiffrement : **S–M**) |
| D | Enrôlement côté serveur | `EnrollmentStatus` 6 valeurs × `active` × garde « admin lié », `EnrollmentExpiredCleanupScheduler` | Déjà une FSM dans [`LIFECYCLE_GOVERNANCE.md`](../../../docs/LIFECYCLE_GOVERNANCE.md) §3.3. Le diagramme omet `INVALID` et `EXPIRED` | Moyenne : bien couvert | S (passe de conformité seulement) |
| E | Intégrité de la chaîne d'audit | Heartbeat `OK/BOOTSTRAPPING/UNSUPERVISED_ACTIVITY/DEGRADED_SERVICE`. Incident `IN_PROGRESS/RECOVERED_PENDING_DECLARATION/CLOSED`. Point de contrôle `ACTIVE→SEALED→EXPORTED→PURGEABLE→PURGED` | #795, #784, #782 | Haute, mais déjà très modélisée (design pack, cuts 2 et 3, Walk gates) | L ; risque de doublon |
| F | Hôte communautaire et déploiement (ezkey.online) | Séquence image → push → déploiement → vérification → registre `DEPLOYED.md` | #740, #749, #832, #846 | Opérabilité. Séquentiel, peu d'états | S, mais une checklist suffit |
| G | PAM (labo) | Tous les échecs donnent `PAM_AUTH_ERR` (`pam_ezkey.c` L444–570) ; `PAM_USER_UNKNOWN` L612 ; échec fermé décidé (`STATUS.md`) | Verrouillage de sshd sur l'hôte | Faible hors dogfood | S, mais une table de résultats suffit |

### Top 3 justifié

**1. A — Tentative d'authentification, vue serveur multi-acteurs.** C'est le moins cher, car les
états sont déjà nommés et la moitié appareil existe. C'est aussi le plus chargé en signal vivant :
#844, #831 et #833 sont ouverts aujourd'hui. Angles de découpage :
- **Matrice état × acteur → réponse HTTP + signature.** C'est là que #844 est né : le 409 arrive
  sans signature, le mobile vérifie la signature d'abord et affiche une erreur générique.
- **Invariant « toute écriture de statut est conditionnelle à l'état courant ».** Sur `main`,
  `AuthAttemptTxHelper.markAsInvalid` écrit `INVALID` sans condition dans une transaction
  `REQUIRES_NEW` (L64–72). #833 ajoute des écritures conditionnelles pour approve, deny et cancel.
  Question à poser : le chemin `INVALID` est-il couvert ?
- **Deux horloges et un proxy.** Le compte à rebours de l'Admin UI compare `expiresAt` à
  `Date.now()` (`login.tsx` L98–111). C'est le décalage d'horloge que Patrick avait signalé pour
  le téléphone. L'attente bloque jusqu'à 300 s alors que Cloudflare coupe à 100 s.
- **Annulation.** Sur `main`, « Annuler » dans l'Admin UI n'agit que localement (`login.tsx`
  L248–254) : la tentative reste `PENDING` jusqu'à expiration (#831, point 4).

**Baseline :** après le merge de #833, pas avant (leçon 1).

**2. B — Accès administrateur.** La criticité sécurité est maximale, l'historique de correctifs SEC
est dense, et une confusion de modèle mental est déjà documentée. Angle : ne pas modéliser chaque
entité, mais l'**état composé « cette personne peut-elle ouvrir une console, et par quel
chemin ? »**. On découpe par chemin : activation, login, récupération, désactivation et
réactivation, évaluateur temporaire. Ensuite, on liste les transitions qui **doivent** révoquer
les sessions. Le double codage `lifecycleStatus` + `active` doit être justifié ou fusionné, et
c'est la FSM qui le dira.

**3. C — Actions lourdes, en commençant par les lots de ré-chiffrement.** Dix minutes de lecture
statique donnent déjà deux signaux de type FSM :
- **`PAUSED` est un état fantôme.** Aucun écrivain en production (pas de `setStatus(PAUSED)`), et
  pourtant il est lu comme reprenable (`ReencryptionBatchRepository` L60–69), documenté dans le
  filtre API (`EncryptionKeyController` L387, L402), affiché dans l'Admin UI
  (`encryption-keys.tsx` L167, L937) et présent dans l'audit (`EventType.REENCRYPTION_PAUSED`).
- **`IN_PROGRESS` orphelin après un crash (hypothèse, à confirmer à l'exécution).** La reprise ne
  lit que `FAILED`, `PAUSED` et `PENDING`. `findMostRecentResumableBatch`, qui inclut
  `IN_PROGRESS`, n'a aucun appelant en production (L200–205). Les gardes « lot actif » comptent
  `IN_PROGRESS` (L94, L115, L140). Un lot interrompu pourrait donc ne jamais être repris et
  bloquer sa cible. À l'inverse, les jobs d'intégrité ont `INTERRUPTED` et un abandon automatique
  au démarrage (`IntegrityAsyncJobController` L213–223).

Angle : pour chaque job, quatre événements uniformes : *qui détient le créneau*,
*crash ou redémarrage*, *second réplica*, *client coupé à 100 s*. Invariant à énoncer :
« exactement une clé `PRIMARY` ». Il est aujourd'hui vérifié après coup dans plusieurs chemins de
promotion (`KeyRotationService` L314/322, 673/699, 813/826, contrôles L733 et L886). À faire
**avant** de coder #790 et #791.

### Ce qu'il ne faut PAS modéliser

- **Les entités à deux états** (tenant actif, intégration `ACTIVE/RETIRED`, clé API, alerte
  `OPEN/RESOLVED`). [`LIFECYCLE_GOVERNANCE.md`](../../../docs/LIFECYCLE_GOVERNANCE.md) suffit.
- **Une nouvelle FSM de l'intégrité d'audit (E).** Le corpus est déjà riche. On n'y ajoute que la
  sous-machine « fraîcheur du verdict » si une troisième ambiguïté apparaît (#795 n1 et n2, #696).
  Ses jobs passent par C.
- **Le déploiement (F), le PAM (G), les scripts** (`clean-start`, `quality-gate` dont les codes de
  sortie 0 à 4 sont déjà un petit modèle formel), **les écrans de liste de l'Admin UI, la CLI
  Python et le SDK.** Une checklist ou une table de résultats suffit.
- **Le Demo Device**, sauf divergence observée avec la FSM mobile.
- **Pas de librairie FSM dans le code**, pas d'enum `phase` unique imposé par principe. La FSM
  reste descriptive. Une garde de code n'arrive que si un invariant est violé.

---

## 3. Méthode proposée et phasage

### Ce qu'on réutilise du corpus

| Brique existante | Réemploi |
| --- | --- |
| [Méthodologie](../../methodology/README.md) : niveaux `V`/`I`/`TB`, hygiène vs programme, incertitude close | Une FSM **n'est pas un nouveau type d'artefact**. Le document FSM vit à côté du canon du composant (comme `ezkey_mobile/docs/`). Il ne devient un `I-*` ou un `TB-*` que s'il révèle un programme |
| [`assessment-curated`](../hygiene/assessment-curated/README.md) | Mandat en trois lignes (surface, axes, non-objectifs), petit lot, HITL **une question à la fois**, porte de réinjection du design avant de fermer |
| [`critical-review`](../hygiene/critical-review/README.md) | Un tour (deux au plus), famille de modèle **différente** de l'auteur, ≤ 10 constats, et **métrique de survie** (taux d'`amend` < 30 % sur 3 passes → ajuster ou retirer la méthode) |
| Grill Me (`backlog/grill-sessions/`) | Format des questions et déductions établies |
| Walk gate / QA Sphere | Chaque transition décidée devient un cas de test avec un oracle (le précédent est MOB-AUTH-007) |
| Repo-as-memory (`AGENTS.md`) | Une ligne, une raison et un numéro d'issue dans le document propriétaire, dans la même PR |

### Quand déclencher une FSM

Il faut **au moins deux** de ces conditions :

1. Frontière de sécurité ou action irréversible (session, crédential, clé, suppression).
2. Au moins deux acteurs concurrents, ou un minuteur ou job asynchrone, sur le même objet.
3. Au moins deux correctifs sur ce cycle de vie en 90 jours, ou un incident de confusion.
4. État codé par plusieurs champs (enum + booléen, refs dispersées).
5. Nouveau mode d'entrée ou nouveau chemin (règle « nouveau mode → re-challenger l'existant »).

On ne déclenche pas pour 2 ou 3 états avec un seul acteur, pour de la présentation pure, ou pour
un domaine déjà modélisé et stable.

### Gabarit minimal (un document, ≤ ~300 lignes)

1. **Baseline** : SHA sur `main`.
2. **États** : comment on les reconnaît dans le code.
3. **Transitions** : événement / depuis / vers / garde / effet / citation.
4. **Matrice acteur × état** si plusieurs acteurs.
5. **Invariants** : 3 à 7, chacun réfutable.
6. **Arêtes manquantes et limites acceptées.**
7. **Questions**, dans une liste séparée et éphémère.
8. **Provenance.**

Le Mermaid est optionnel. Pas de HTML ni de user stories sans demande explicite.

### Format des questions à l'humain

Une question par tour : **observé** (`fichier:ligne`) → **scénario de la boucle vécue** →
**options** (≤ 3) → **recommandation** → **ce que la réponse ne décide pas**.

- La première question est toujours la boucle vécue (leçon de la Question 1 mobile).
- Réponses attendues : `décidé` / `différé` / `décliné`.
- Chaque décision s'écrit en puces « Locked », sans paragraphe de justification au-delà d'un seul.

### Critique adverse

Un tour de `critical-review` sur le document FSM et les questions, **avant** les décisions de
Marc. L'angle est imposé : *arêtes oubliées* (crash, double acteur, horloge, proxy, rejeu) et
*prémisses fausses*. En parallèle, une relecture par mandat (tableau ci-dessous). Un constat
retenu modifie la FSM. On n'ouvre pas de seconde note.

### Retour des décisions dans les documents propriétaires

- **Comportement** : la FSM et les flux du composant.
- **Cycles d'entité** : `LIFECYCLE_GOVERNANCE.md`.
- **Fil HTTP** : `docs/ENDPOINT.md`.
- **Propriétés** : `CONFIGURATION.md`.
- **Tests** : cas QA Sphere et tests automatisés dérivés des transitions.

Les options écartées restent dans la note d'orientation (ou dans git). Avant de fermer, on
applique la porte de réinjection d'`assessment-curated`.

### Critère d'arrêt (anti-perfectionnisme)

On s'arrête dès que **l'une** de ces conditions est vraie :

- chaque transition à effet a sa citation, chaque question a une issue, et le dernier tour de 3
  questions n'a produit aucune décision nouvelle ;
- le budget est atteint : un document FSM, une liste de ≤ 15 questions, un tour de critique.

On ne rafraîchit pas les numéros de ligne sans changement de décision. **Métrique de survie de la
méthode** : si deux passes de suite produisent moins de 30 % de questions qui mènent à une
décision ou à un correctif, on abandonne la méthode. La passe mobile est à ~100 %, plus 4
correctifs.

### Phasage

| Phase | Contenu | Porte |
| --- | --- | --- |
| 0 | Cette note ; Marc choisit | Réponses aux questions ci-dessous |
| 1 | **A**, après le merge de #833 | Critique + mandats, puis décisions de Marc |
| 2 | **B** | Idem ; Walk Julie sur le login |
| 3 | **C**, tranche ré-chiffrement, branchée sur le design de #789 et #790 **avant le code** | Idem ; preuve du crash `IN_PROGRESS` à l'exécution |
| Rétro | Taux de survie après chaque phase | Continuer, ajuster ou retirer |

### Qui relit quoi

| Relecteur | Mandat ([`team-roles.md`](../team-roles.md)) | À relire en priorité |
| --- | --- | --- |
| **Alex** | Direction produit | Classement §2, état composé de B (modèle mental de l'opérateur), critère d'arrêt |
| **Patrick** | Craft | Conformité FSM ↔ code, invariants d'écriture conditionnelle (A), crash et reprise (C), coûts estimés |
| **Christophe** | Sécurité | A (approve → session, écriture `INVALID` sans condition), B (jetons, récupération), C (unicité de `PRIMARY`) |
| **Julie** | Opérabilité + Walk Gate | B et A côté Admin UI : états du login, annulation locale, compte à rebours, 408/524 |
| **Isabelle** | QA exploratoire | Transitions → cas QA Sphere ; arêtes multi-acteurs de type #844 ; scénarios de crash pour C |

---

## 4. Questions ouvertes pour Marc

1. **Top 3 et ordre.** Validez-vous A (après le merge de #833), puis B, puis C (tranche
   ré-chiffrement, avant #790 et #791) ?
2. **Rattachement.** Une passe FSM est-elle une instance d'`assessment-curated` avec l'angle
   « FSM » (sans nouveau mot-clé), ou voulez-vous un mot-clé dédié ?
3. **Portée.** La FSM reste descriptive. Acceptez-vous qu'un invariant violé débouche directement
   sur une garde de code, comme l'écriture conditionnelle de tous les statuts ou la fusion de
   `active` et `lifecycleStatus` ?
4. **Budget.** Plafond de ~15 questions et d'un tour de critique par FSM, et abandon de la
   méthode sous 30 % de survie sur deux passes : est-ce le bon niveau ?
5. **Source et langue.** Avez-vous le lien de la vidéo pour vérifier et citer l'intervenant ? Et
   la note reste-t-elle en français, ou la traduit-on en anglais à la promotion ?

## Promotion

À promouvoir quand Marc aura répondu aux questions 1 à 4. La méthode (déclencheur, gabarit,
arrêt) irait alors dans le README d'`assessment-curated`, et le classement dans le backlog sous
forme d'un `I-*` par candidat retenu.

## Related documents

- [`MOBILE_APP_STATE_MACHINE.md`](../../../ezkey_mobile/docs/MOBILE_APP_STATE_MACHINE.md) ·
  [`MOBILE_APP_STRATEGIC_ORIENTATION.md`](../../../ezkey_mobile/docs/MOBILE_APP_STRATEGIC_ORIENTATION.md)
- [`docs/LIFECYCLE_GOVERNANCE.md`](../../../docs/LIFECYCLE_GOVERNANCE.md) ·
  [`docs/HA-JOB-COORDINATION.md`](../../../docs/HA-JOB-COORDINATION.md)
- [`V-2026-07-18-authentication-wait-evolution`](V-2026-07-18-authentication-wait-evolution.md) ·
  [`V-2026-06-05-admin-enrollment-vs-admin-login-state-model`](V-2026-06-05-admin-enrollment-vs-admin-login-state-model.md)
- Issues : [#833](https://github.com/mgagp/ezkey/pull/833), [#834](https://github.com/mgagp/ezkey/issues/834),
  [#844](https://github.com/mgagp/ezkey/issues/844), [#831](https://github.com/mgagp/ezkey/issues/831),
  [#789](https://github.com/mgagp/ezkey/issues/789), [#790](https://github.com/mgagp/ezkey/issues/790)
