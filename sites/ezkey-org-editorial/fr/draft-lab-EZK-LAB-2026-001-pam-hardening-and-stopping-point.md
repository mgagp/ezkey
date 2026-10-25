---
status: draft
audience: "Lecteurs techniques suivant les expériences du labo Ezkey ; pas un public d'assurance sécurité"
planned_slug_en: "lab-EZK-LAB-2026-001-pam-hardening-and-stopping-point.html"
planned_slug_fr: "lab-EZK-LAB-2026-001-pam-hardening-and-stopping-point.html"
planned_canonical_en: "https://ezkey.org/lab-EZK-LAB-2026-001-pam-hardening-and-stopping-point.html"
planned_canonical_fr: "https://ezkey.org/fr/lab-EZK-LAB-2026-001-pam-hardening-and-stopping-point.html"
lab_id: "EZK-LAB-2026-001"
lab_status: draft
source_of_truth: draft
---

<!-- ezkey-org:exclude-start
NOTES ÉDITORIALES — premier brouillon français, copie de travail locale. Pas dans le dépôt. Pas publié.

Où ce brouillon devrait-il vivre dans le dépôt ?
- Sources : le gabarit EN dit « sites/ezkey-org-editorial/draft-lab-EZK-LAB-YYYY-NNN-<slug>.md » ; le gabarit FR dit
  « sites/ezkey-org-editorial/fr/draft-lab-EZK-LAB-YYYY-NNN-<slug>.md » ; sites/ezkey-org/AGENTS.md dit « Draft files MUST
  live in sites/ezkey-org-editorial/fr/ ». Il existe aussi un précédent EN à la racine éditoriale
  (draft-technical-trace-ezkey-enrollment-authentication.md).
- Option A (recommandée) : EN à sites/ezkey-org-editorial/draft-lab-EZK-LAB-2026-001-pam-hardening-and-stopping-point.md,
  FR à sites/ezkey-org-editorial/fr/draft-lab-EZK-LAB-2026-001-pam-hardening-and-stopping-point.md. Suit les deux gabarits
  et le précédent EN. Jamais sous sites/ezkey-org/ (Wrangler déploie ce dossier).
- Option B : les deux fichiers dans sites/ezkey-org-editorial/fr/ (lecture littérale d'AGENTS.md). Règle la plus simple,
  mais un fichier EN dans un dossier fr/ prête à confusion.
- Option C : un sous-dossier sites/ezkey-org-editorial/lab/. Propre quand il y aura plusieurs papiers, mais c'est une
  nouvelle convention que LAB-PAPERS.md ne mentionne pas. Pas recommandée pour le papier no 1.
- Correctif connexe à proposer : une ligne dans sites/ezkey-org/AGENTS.md pour que « fr/ seulement » et le gabarit EN concordent.

Statut (passe de révision du 2026-10-25, soir) : miroir du brouillon EN révisé (draft-lab-pam.en.md), resserré d'environ
5 900 à 4 000 mots de corps ; nouvelle courte section 6.4 sur l'autonomie des agents (tableau 3). Aucun fait propre au FR. La
rétrospective de Mathieu n'existe qu'en BROUILLON non validé par Marc : elle n'est PAS intégrée ; ce qui n'en vient que d'elle
est marqué [À VÉRIFIER] ; sa note de co-auteur reste un espace réservé. La version FR est une copie de travail locale ; elle
attend le go explicite de Marc avant tout usage. Marqueurs : [À VÉRIFIER] / [DONNÉE MANQUANTE], listés en fin de fichier.
ezkey-org:exclude-end -->

# Ezkey PAM : durcir un module SSH expérimental avec des agents, et décider où s'arrêter

**Auteurs :** Marc Gagnon (responsabilité éditoriale) ; Christophe Grokbot (revue de sécurité) ; Edgar Grokbot (déploiement et périphérie) ; Isabelle Grokbot (QA exploratoire) ; Mathieu Grokbot (coordination) `[À VÉRIFIER : preuve et note validée de Mathieu en attente]` ; Patrick Grokbot (revue d'ingénierie)
**Affiliation :** Ezkey Lab
**Identifiant :** `EZK-LAB-2026-001`
**Statut :** draft
**Date :** 2026-10-25
**Mots-clés :** PAM, SSH, MFA, fail-closed, développement assisté par agents, autonomie des agents, revue de sécurité, rendements décroissants, expérimental
**Temps de lecture :** ~15 min

## Points clés

- Un prototype d'environ 900 lignes, commité le 2025-09-03 et validé à la main, a suffi à Marc pour juger que le modèle d'intégration d'Ezkey pouvait porter un second facteur SSH. Il n'a laissé aucune preuve automatisée.
- Le dépôt montre une pause d'un an, sans trace dans le dépôt de travail fonctionnel sur PAM. Le travail a repris le 2026-09-15.
- Du 19 au 23 octobre 2026, cinq versions correctives (2.2.10 à 2.2.14) ont été livrées, et la 2.2.15 est une PR ouverte. Plusieurs défauts à l'origine de ces versions étaient invisibles à la lecture statique et à une CI verte. Les constats de revue sont devenus plus petits d'un tour à l'autre, et quatre co-auteurs rapportent des rendements décroissants, que nous n'avons pas mesurés de façon systématique.
- Marc et l'équipe AI ont jugé le résultat suffisant comme maturité **expérimentale** : la sécurité est raisonnablement assurée dans le périmètre testé, sans être la fin de l'histoire. Ce n'est pas un module exploitable sur un hôte, ni une version publiée. La fatigue d'approbation n'est pas atténuée côté serveur (#992), et 11 issues restent ouvertes.
- L'expérience visait aussi plus d'autonomie pour les agents. Elle a été partielle : à plusieurs moments, un humain ou un relais restait nécessaire (§6.4).

## Résumé

Ce papier rend compte d'une expérience sur `ezkey-pam`, un module PAM Linux qui ajoute l'approbation par appareil Ezkey aux connexions SSH. Un prototype a été commité en septembre 2025. Après un an sans travail fonctionnel, le module a été modernisé en septembre 2026, puis revu, recontre-testé et corrigé en une rafale dense en octobre 2026 par une équipe AI (réviseurs, agent de QA, agent de déploiement, coordonnateur) qui travaillait avec Marc. Nous décrivons ce qui a été construit, ce que chaque tour a trouvé, où l'autonomie des agents a rencontré ses limites, et un point d'arrêt choisi conjointement par Marc et l'équipe AI. Nous ne revendiquons ni préparation à la production ni garantie de sécurité. Le module n'a jamais été publié et n'a pas fonctionné comme agent exploitable sur un hôte réel.

## 1. Introduction et motivation

Marc a d'abord vu une connexion SSH protégée par Duo au travail, ce qui était nouveau pour lui `[À VÉRIFIER avec Marc : quand]`. La question qui a suivi : Ezkey pouvait-il jouer le rôle de ce second facteur pour SSH, avec les morceaux qu'il avait déjà ?

Ezkey est un projet de laboratoire, qui n'est promis à personne. Le créneau réaliste que nous lui voyons est la protection sans mot de passe, à cryptographie forte, d'applications internes pour un personnel interne restreint. Aller plus loin exigerait d'abord une discipline de type SOC 2 : un prérequis, pas un plan. `docs/SECURITY_POSTURE.md` traite SOC 2 comme un vocabulaire de référence et non comme une cible de certification, et ne revendique aucune équivalence.

Deux autres raisons ont façonné l'expérience. D'abord, Marc a l'impression que les agents autonomes sont devenus visiblement plus rapides à trouver et à corriger des vulnérabilités. C'est son observation personnelle ; nous ne citons aucune source externe et ne l'avons pas mesurée. Elle nous a rendus curieux de l'équilibre entre budget, temps, effort et valeur quand plusieurs agents examinent un petit module, et de l'endroit où il est raisonnable de s'arrêter. Ensuite, nous voulions voir quelle part de ce travail les agents pouvaient porter avec peu d'intervention humaine (§6.4).

Ce papier dit ce qui s'est passé et ce qui a été mesuré, pas ce que cela signifie.

## 2. Hypothèses

Ces hypothèses ont été **formulées après coup** ; aucune n'a été écrite avant le début du travail. Nous les énonçons pour rendre la lecture explicite, pas pour laisser croire que l'expérience a été conçue autour d'elles.

- **H1.** Les morceaux existants (authentification, API, appareil) suffisaient à construire un flux complet d'approbation SSH.
- **H2.** Une équipe AI avec revue croisée peut amener un petit module en C à un point où les constats de revue deviennent mineurs.
- **H3.** Au-delà d'un certain point, un tour de revue de plus coûte davantage que le risque, à l'échelle du labo, qu'il retire, et ce point peut être repéré.

H1 a été mise à l'épreuve par le prototype de 2025. H2 et H3 n'ont pas été testées de façon contrôlée ; nous rapportons des observations et un jugement.

## 3. État antérieur

En septembre 2025, la plateforme avait déjà la plupart des pièces d'un flux d'approbation (tableau 1, dates tirées de git).

| Élément | Preuve | Date |
| --- | --- | --- |
| Demo Device | `e532ce95` | 2025-08-10 |
| Point d'accès `/wait` | `796dd455` | 2025-08-26 |
| Première application Android | `fd7d9d6a` | 2025-09-01 |
| **Premier commit PAM** | `04d339b7` | **2025-09-03** |
| Preuve de concept Android | `1efda3d1`, `8da533ce` | 2025-09-04 |
| Admin UI | `71e27831` | 2026-03-06 |

*Tableau 1. Contexte autour du premier commit PAM.*

Marc se souvient qu'à l'été 2025 le protocole était plus simple : un simulateur au départ, pas d'Admin UI, une base de données plus simple. Le simulateur est visible dans l'historique (`988ef115`, 2025-08-19) ; la remarque sur la base de données n'est pas vérifiée `[À VÉRIFIER : schéma de la base à l'été 2025]`. Certains des premiers commits ont été signés par un compte d'agent automatisé, pas par Marc `[À VÉRIFIER : auteur de chaque commit du tableau 1]`.

## 4. Protocole

**Le module.** `ezkey-pam` est un module PAM en C pour SSH en keyboard-interactive. Il demande à l'utilisateur de confirmer avec `ok`, crée une tentative d'authentification par l'API d'intégration (`POST /api/v1/auth-attempts`), affiche au besoin un code à deux chiffres à comparer, puis attend (`GET …/wait`) que l'utilisateur approuve ou refuse sur un appareil (le Demo Device ou l'application Android).

**Contraintes de conception.** Il échoue en mode fermé : une API ou une configuration inutilisable renvoie `PAM_AUTHINFO_UNAVAIL`, et il n'y a volontairement pas de `failmode=safe`. La configuration est un fichier appartenant à root (`0600` ou `0400`), sans URL d'API par défaut ni variables d'environnement. Une réserve documentée demeure : une pile PAM `sufficient` peut rendre le module fail-open.

**Environnement.** Un conteneur Rocky Linux pour les builds et les démos et, à partir d'octobre 2026, une VM de test dédiée avec un vrai sshd. Plusieurs constats ne sont apparus que là. Nous ne donnons aucun nom d'hôte ni adresse.

**Rôles.** Marc décidait et approuvait, et tenait le clavier SSH pendant les campagnes manuelles. Mathieu coordonnait et relayait ; Patrick et Christophe faisaient les revues ; Edgar exploitait la VM de test ; Isabelle menait la QA manuelle.

**Comment les revues arrivent sur GitHub.** Les revues de Patrick et de Christophe arrivent dans le dépôt par le **compte GitHub de Marc** : les commentaires sont publiés depuis `mgagp`, et le texte de chacun indique quel co-auteur l'a rédigé. Une issue (#1016) dit que les revues ont été relayées par Mathieu, et les commits de fusion (squash) portent le nom de Marc. Dans ce papier, l'attribution repose sur le texte du commentaire ou sur la rétrospective du co-auteur, pas sur l'identité GitHub `[À VÉRIFIER : que les commentaires reflètent le texte original des revues]`.

**Tests.** Une cible `make test`, des scripts de test shell, des sondes de signaux contre un vrai sshd et un job de CI. Nous ne les avons pas relancés pour ce papier ; les chiffres plus bas viennent du dépôt et des PR.

## 5. Déroulement

### 5.1 Prototype et pause

Le premier commit PAM, `04d339b7` (2025-09-03, 9 fichiers, 919 lignes ajoutées), se décrit comme un module de base codé en dur, dans Rocky Linux sous Docker, où, après le mot de passe SSH, l'appareil de démo doit confirmer l'accès. Selon Marc, il a aussi été exécuté sur une VM et validé seulement à la main `[À VÉRIFIER : essai sur VM ; aucune preuve automatisée dans git]`.

Ensuite, une pause d'un an, sans trace dans le dépôt de travail fonctionnel sur PAM. Le dossier n'a reçu que quatre commits mécaniques : un changement de port (`07746bb8`), le passage à Rocky Linux 10 (`90b16e57`), des permissions de fichiers (`83758319`) et de l'hygiène documentaire (`b19eca7d`). Nous n'affirmons rien sur cette période au-delà de ce que montre git.

### 5.2 Modernisation et premier jalon

La PR #540 (`3fef2411`, 2026-09-15) a modernisé le module. La PR #610 (`8b3aa5df`, « M1 »), ouverte le 2026-09-22 et fusionnée le 2026-10-17 (38 fichiers, +3767/−332), a ajouté l'observabilité, une configuration fail-closed, le défi à deux chiffres et un budget de temps.

Ses revues sont un exemple typique. La première revue de Patrick a rendu **CHANGES** avec un seul constat bloquant : une URL d'API absente n'échouait pas en mode fermé, parce que le code préremplissait `http://localhost:7080`. Une configuration sans URL aurait envoyé les identifiants d'intégration en clair vers un port local où un autre utilisateur local aurait pu répondre `ACCEPTED`. La revue de Christophe n'a rendu que des constats mineurs. Patrick a revu deux fois et rendu **GO** le 2026-10-17, une fois la CI verte.

### 5.3 La rafale d'octobre

| Version | Livrée | Changement | Ce qui l'a fait ressortir |
| --- | --- | --- | --- |
| 2.2.10 | 2026-10-20 | Plus de demande en attente orpheline après un nouvel essai Deny/Expire (#907, #908) ; `Match … MaxAuthTries 1` obligatoire dans la doc (#934) | Contre-test d'Edgar sur #908 : sans `Match`, trois demandes étaient créées |
| 2.2.11 | 2026-10-21 | Message d'attente affiché avant l'attente de l'appareil (#941, #956) | Edgar : OpenSSH garde en mémoire tampon le texte `pam_info` seul jusqu'à l'invite suivante |
| 2.2.12 | 2026-10-21 | Confirmation avant création (`ok`) et affichage du défi d'enrôlement (#975, #976) | Isabelle (#975) ; la revue de Patrick a trouvé un TTL calculé avant le `ok` |
| 2.2.13 | 2026-10-22 | Paliers 0–1 de fluidité d'interaction (#982, #983) | Suite de #976 |
| 2.2.14 | 2026-10-22 | `challenge_requested=true` par défaut (#995, #996) | Décision d'Alex et de Marc, selon `STATUS.md` |
| 2.2.15 | non fusionnée | Diagnostics d'échec plus riches, gestion de SIGTERM, sondes de signaux (#1011, #1014) | Campagne manuelle sur la 2.2.14 ; revues d'ingénierie et de sécurité |

*Tableau 2. Versions correctives d'octobre 2026. Les versions 2.2.2 à 2.2.9 ne sont pas reconstituées `[À VÉRIFIER]`.*

Entre-temps, #992 (fatigue d'approbation : limite de demandes en attente, débit par enrôlement, refroidissement, alerte sur refus) a été cadrée, puis réécrite en termes génériques de plateforme sur consigne de Marc du 2026-10-22. Le commit `9f7fa78f` (#993) documente la limite sans détour ; #992 elle-même n'est pas implémentée.

La PR #1014, encore ouverte, montre comment les constats devenaient plus fins. La lecture de Patrick prédisait que le processus enfant PAM pouvait se bloquer après un SIGTERM. Un agent infonuagique l'a confirmé et a révélé un second défaut : un SIGTERM pendant la création de la demande était absorbé, et l'authentification pouvait ensuite réussir après le départ du client. Christophe, en lisant le gestionnaire, a trouvé qu'il appelait une fonction qui n'est pas sûre en contexte de signal (async-signal-safe). Le commit de tête (`6bc342a8`) l'a remplacé par un gestionnaire qui écrit dans le périphérique de journalisation, rétablit l'action par défaut et relance le signal, avec quatre sondes de signaux contre un vrai sshd qui échouaient sur la tête précédente et passent sur celle-ci. Les deux réviseurs ont rendu GO sur cette tête, ce que nous savons par le texte de #1016 et non par une revue GitHub `[À VÉRIFIER]`.

## 6. Résultats

### 6.1 Tours de revue

#610 a été revue sur trois têtes (`932c83f4`, `5d743589`, `ca86a1c2`), avec les verdicts ci-dessus. Sur #1014 (`8272d360`, puis `6bc342a8`), l'ingénierie et la sécurité ont d'abord demandé des changements, puis toutes deux rendu GO à `6bc342a8`, et #1016 a rassemblé de petites suites non bloquantes. Elle consigne aussi que la documentation ne doit **pas** écrire « jamais SUCCESS après le départ du client » : SUCCESS reste possible si un TERM est absorbé pendant `/wait` après l'arrivée de la réponse ACCEPTED, et #1016 dit que ce n'est pas une élévation. D'autres PR (#897, #908, #956, #976, #983, #996) ont aussi été revues ; nous ne les avons pas toutes lues, donc nous ne donnons aucun décompte de constats par tour `[À VÉRIFIER : tours et gravité]`.

### 6.2 Campagne manuelle sur la 2.2.14

Isabelle a mené une campagne manuelle sur la 2.2.14 (#996) sur la VM de test, avec Marc au clavier SSH et sur le Demo Device. Elle est consignée dans un outil de gestion de tests hors du dépôt : 30 cas, 29 réussis et 1 échoué (S6-02, annulation et audit, maintenant #1012). Marc a consigné le jalon comme « atteint avec réserves » (S1D-06, S11-01) `[À VÉRIFIER : chiffres tirés de la rétrospective d'Isabelle ; outil et notes hors dépôt]`.

Le résultat le plus utile a été un symptôme trompeur. Avec `MaxAuthTries 1`, presque toute erreur interne apparaît comme « Too many authentication failures ». Une trace de debug du 2026-10-24 a montré UNAVAIL après 741 ms, raison « pre-wait confirm missing or not ok », et aucun POST envoyé : l'invite `ok` avait reçu une réponse vide. L'analyse d'Edgar a aussi trouvé que c'est le `LoginGraceTime` de sshd (120 s), et non le délai d'attente du module (75 s), qui borne réellement une tentative. Cela a mené à #1011, #1014 et #1016.

### 6.3 Éléments ouverts

Onze issues étaient ouvertes le 2026-10-25 (vérifié sur GitHub), plus la PR #1014 : #858, #955, #979, #980, #982, #992, #997, #1011, #1012, #1013 et #1016. Elles touchent la sécurité de la plateforme (#992 politique de demandes en attente et refroidissement, #858 signature de la réponse `/wait`), l'interaction (#982, #997), l'introduction sur l'instance communautaire (#955), les tests et la CI (#979, #980, #1013), et l'annulation, les diagnostics et les suites (#1011, #1012, #1016).

### 6.4 Autonomie des agents : visée et limites observées

Une partie de l'expérience visait à laisser les agents faire plus du travail avec moins d'intervention humaine. Le tableau 3 liste ce que nous avons fait et où une limite est apparue. Les sources sont les rétrospectives des co-auteurs ; ce sont des observations, pas une mesure de l'autonomie.

| Visée | Limite observée | Source |
| --- | --- | --- |
| Tests menés sans le téléphone d'une personne : le Demo Device d'Isabelle pour presque tous les tests ; le Pixel de Marc seulement pour quelques tests exploratoires à lui, en validation préliminaire (déclaration de Marc) | La campagne sur la 2.2.14 a quand même eu besoin de Marc au clavier SSH et sur le Demo Device ; Isabelle dit qu'elle n'aurait pas pu avancer sans Marc au téléphone | Marc ; Isabelle |
| Revues sur une tête figée (un SHA précis, un seul lot de correctifs par revue) | Les re-revues à partir de la deuxième tête environ n'ont apporté surtout que des points mineurs | Patrick, Christophe |
| Tests rejoués par les agents eux-mêmes : contre-tests (#908), sondes de signaux contre un vrai sshd, agent infonuagique qui exécute les prédictions (#1014) | Patrick n'avait ni Docker ni Java sur sa propre machine, donc chaque preuve passait par un agent infonuagique, lentement ; certains garde-fous de CI ne prouvaient rien (#996, #1014) | Patrick, Edgar |
| GO bornés : permission permanente sur la VM de test (snapshot, `sshd -t`, rapport, un seul bot à la fois) | Un « Marc est d'accord » relayé n'est pas un GO ; le chemin de maintenance de la VM de test est resté hors du MFA (principe dans #955) | Edgar, Isabelle |
| Constats écrits là où ils vivent (PR ou issue) | Messages qui ne réveillent pas un agent (d'où l'accusé de lecture sur les demandes bloquantes) ; un lot de constats de #961 perdu dans le clavardage `[À VÉRIFIER]` | Christophe |
| Recontre-tests sans humain dans la boucle | L'aller-retour humain (environ 77 s) dépassait le TTL de 75 s de la tentative, donc certains essais étaient invalides ; il manquait un pilote d'approbation automatisé | Isabelle |
| Agents qui composent et relaient textes et revues | Rien n'est publié sans Marc, ici et sur ezkey.org en général : il donne la matière et l'orientation, les agents composent, il révise et cadre, avec relecture et aperçu avant publication. Les revues arrivent sur GitHub par son compte, relayées par Mathieu (#1016) | Marc (déclaration) ; #1016 |

*Tableau 3. Visées d'autonomie et limites observées.*

En bref, les agents pouvaient revoir, rejouer et diagnostiquer, mais chaque étape passait encore à un moment par Marc ou par un relais. Nous n'avons aucune mesure de la part du travail que cela représente `[DONNÉE MANQUANTE]`.

## 7. Discussion

### 7.1 Une image fractale

Plus nous regardions de près, plus les cas limites étaient fins, et chaque correction ouvrait une question plus petite : une URL absente qui retombait en silence (#610) ; des reprises qui multipliaient les demandes (#908) ; OpenSSH qui retient le texte d'information (#941) ; la valeur par défaut du défi (#995) ; la fatigue d'approbation (#992, documentée, non atténuée côté serveur) ; un processus tué, un blocage et un SUCCESS tardif (#1014) ; puis un test qu'une mutation pouvait survivre (#1016). Cette suite est notre interprétation ; les liens de cause à effet n'ont pas été testés. À notre lecture, les derniers éléments concernent la solidité des tests et les diagnostics plus que l'approche générale du module.

### 7.2 Budget, temps, effort et valeur

Faire examiner et recontre-tester le même module par plusieurs agents change l'équilibre entre ces quatre éléments, et nous pensons qu'il diffère du travail en solo. Git montre cinq versions correctives en quelques jours, chacune revue sur une tête figée. Nous n'avons pas de chiffres sur l'argent, le calcul ou le temps humain, ni sur le travail d'une personne seule `[DONNÉE MANQUANTE]`. Notre résumé est qualitatif : plus de revue, plus souvent, à un rythme que Marc pouvait suivre, à un coût que nous n'avons pas mesuré. Quand nous disons « dense » ou « rapide », nous parlons seulement des dates et des décomptes visibles dans git.

### 7.3 Un point d'arrêt décidé

Au 2026-10-25, Marc et l'équipe AI conviennent de traiter le résultat comme suffisant pour une maturité **expérimentale**. Notre formulation : la sécurité est **raisonnablement assurée dans le périmètre testé, sans être la fin de l'histoire**. Nos raisons :

- les derniers constats étaient mineurs (#1016), et les restants sont nommés dans des issues ouvertes ;
- quatre co-auteurs rapportent des rendements décroissants sous des formes différentes : re-revues répétées d'une même PR (Christophe), re-revues de libellé et de documentation (Patrick), bascules répétées de `challenge_requested` pendant la campagne (Edgar), recontre-tests invalidés par le délai (Isabelle) ;
- les risques résiduels connus sont documentés, pas cachés ;
- continuer a un coût, et nous n'avons aucune preuve que cela changerait le risque à l'échelle du labo.

Cela ne veut **pas** dire que le module est exploitable sur un hôte. `ezkey-pam/STATUS.md` dit toujours que ce n'est pas un agent MFA exploitable sur un hôte réel, que la bascule du sshd et les tranches TLS et dogfood ne sont pas faites, et que le statut expérimental tient en attendant la validation de Marc sur un hôte. `docs/SECURITY_POSTURE.md` range PAM parmi les surfaces de labo non déployées. Passer à l'instance communautaire (#955) exigerait de refaire l'évaluation.

## 8. Limites

- Le module est expérimental, n'a jamais été publié, n'a pas d'étiquette PAM et n'a pas fonctionné comme agent exploitable sur un hôte réel. Les tranches #4 (TLS, dogfood) et #5 (bascule du sshd de l'hôte) ne sont pas faites.
- La fatigue d'approbation n'est pas atténuée côté serveur (#992) ; l'atténuation côté module est le défi à deux chiffres par défaut (#995). La signature de `/wait` est reportée (#858). Une pile `sufficient` peut être fail-open, et « jamais SUCCESS après le départ du client » ne tient pas (#1016).
- Un seul opérateur, une seule famille d'environnements (Docker/Rocky, une VM de test). Aucun audit externe, aucune certification, aucune revendication SOC 2. Le test d'intrusion local de Christophe a été mis en pause sans constat ; nous n'en revendiquons aucun résultat.
- Les revues sont du texte, et elles sont arrivées sur GitHub par le compte de Marc. Les versions 2.2.2 à 2.2.9, certains tours de revue et les chiffres de la campagne ne sont pas reconstitués ou sont hors du dépôt. Le coût et le temps ne sont pas mesurés.
- Les hypothèses ont été formulées après le travail. Nous ne savons pas si une plus longue série de revues aurait trouvé quelque chose de grave, ni comment une personne seule en revue se comparerait. S'arrêter a été un jugement.

## 9. Leçons apprises

- **Aucune valeur par défaut pour ce qui doit échouer.** Le constat B1 de #610 était une URL par défaut qui faisait passer un réglage absent pour valide.
- **Tester avec le vrai démon.** Plusieurs défauts sont apparus sous un vrai sshd, pas sous `make test` ni en CI (Patrick).
- **Une vérification verte ne prouve que ce qu'elle vérifie.** Dans #996, un grep était satisfait par un commentaire ; dans #1014, une vérification de source était trompée de la même façon. Patrick demande maintenant un tableau des mutations exécutées, tuées ou survivantes, pour tout test utilisé comme preuve.
- **Une invite est un filtre, pas une défense.** Quiconque détient le premier facteur SSH tape aussi `ok` (Christophe).
- **Écrire les constats là où on les retrouvera, sur une tête figée** (Christophe, Patrick), et **préparer l'outil de test avant la campagne** : Isabelle avait besoin d'un pilote d'approbation automatisé.

## 10. Rétrospective des contributeurs

Chaque co-auteur a répondu aux mêmes questions par Mathieu (ce qui a été livré, un point charnière, surprises, rendements décroissants, éléments ouverts, méthode). Les réponses condensées sont gardées dans un bloc d'exclusion éditorial, non publié. Les contributions sont dans « Qui a fait quoi ».

**Points charnières rapportés.** *Patrick* a choisi #1014 : la lecture statique prédisait un blocage, l'exécution l'a confirmé (référence de 225 ms, au moins 123 s observées) et a révélé un second défaut. *Christophe* a choisi la fatigue d'approbation : l'invite `ok` filtre les accidents et n'est pas une défense, donc l'atténuation s'est déplacée vers le défi par défaut et vers des garde-fous de plateforme dans #992. *Isabelle* et *Edgar* ont choisi le même symptôme sous deux angles, le « Too many authentication failures » trompeur.

**Désaccords.** Aucun rapporté entre co-auteurs. Les frictions rapportées concernent la règle de portée de Marc pour #992 (elle a changé la façon dont Christophe avait cadré les contrôles), les approbations relayées et le délai des recontre-tests.

**Angles morts rapportés.** Les notes d'Isabelle sont hors du dépôt, et le rapport final de campagne d'Edgar n'était pas encore livré. Christophe a rédigé la formulation sur la fatigue d'approbation maintenant dans `docs/SECURITY_POSTURE.md` (entrée du 2026-10-22), mais n'a pas relu le texte final ; nous avons vérifié séparément que l'entrée existe sur `main`.

> **Note du co-auteur — Patrick**
>
> Sur #1014, j'ai lu le chemin de SIGTERM et prédit un blocage. Un agent infonuagique l'a exécuté : au moins 123 secondes contre une référence de 225 ms, et un second défaut, une authentification qui pouvait réussir après le départ du client. Ni l'un ni l'autre n'était visible à `make test` ou à la CI. Je demande maintenant un tableau des mutations exécutées, tuées ou survivantes, et je marque chaque affirmation comme exécutée ou raisonnée.

> **Note du co-auteur — Christophe**
>
> J'ai revu le gestionnaire de signal en lisant ce qu'il appelle, sans l'exécuter. Il utilisait `snprintf`, qui n'est pas sûr en contexte de signal. Sur la fatigue d'approbation, l'invite `ok` n'est pas une défense : quiconque a le premier facteur tape `ok`. L'atténuation s'est donc déplacée vers le code de défi et vers des garde-fous côté serveur, qui ne sont pas encore construits. À partir du deuxième tour sur une même PR, je trouvais surtout des détails.

> **Note du co-auteur — Isabelle**
>
> Le « Too many authentication failures » intermittent m'a coûté du temps parce que je regardais Cloudflare, l'API et un refroidissement. Une trace de debug a montré une réponse vide à l'invite `ok` et aucune requête envoyée. Je n'aurais pas pu avancer sans Marc au téléphone. Nos recontre-tests étaient plus lents que le TTL de 75 secondes ; il fallait un pilote d'approbation automatisé.

> **Note du co-auteur — Edgar**
>
> J'ai exploité le module sur la VM de test à travers cinq versions, avec un snapshot et `sshd -t` avant chaque changement. Avec `MaxAuthTries 1`, toute erreur interne ressemble à un échec d'authentification, et `UNAVAIL` avec le code 9 ne dit rien de la cause. Un « Marc est d'accord » relayé n'est jamais un go pour moi. Les bascules répétées de `challenge_requested` sont là où j'ai vu les rendements décroître.

> **Note du co-auteur — Mathieu**
>
> `[ESPACE RÉSERVÉ — la rétrospective de Mathieu n'existe qu'en brouillon non validé par Marc. À ajouter une fois validée : coordination, relais des revues, point de vue sur le processus, et une note de 80 à 150 mots. Ne pas publier avec cet espace réservé.]`

*Chaque note est condensée à partir de la rétrospective du co-auteur et doit être validée par son auteur avant publication `[À VÉRIFIER]`.*

**Julie.** Julie Grokbot n'est pas co-autrice. Elle n'a pas touché à PAM : ni revue, ni Walk Gate, ni PR, ni rapport. Le seul contact a été un message de contre-test envoyé par erreur et redirigé.

## 11. Suites

Aucune n'est un engagement ; les éléments ouverts du §6.3 demeurent. Avant tout passage à l'instance communautaire, il faudrait refaire l'évaluation, et la validation de Marc sur un hôte (bascule du sshd, tranches TLS) reste à faire.

## Qui a fait quoi

- **Marc :** responsabilité éditoriale ; décisions et approbations ; clavier SSH dans les campagnes manuelles ; compte GitHub utilisé pour les commits et les commentaires de revue.
- **Christophe :** revue de sécurité (#1014 et autres), cadrage de #992.
- **Edgar :** exploitation de la VM de test de la 2.2.10 à la 2.2.14, avec une sauvegarde par version et un retour arrière documenté ; contre-tests (#908, #941) ; analyse de diagnostics citée dans #1011.
- **Isabelle :** campagne manuelle sur la 2.2.14 ; révision des cas S0–S13 ; mesures de délais ; analyse derrière #1011 ; affichage du défi (#975).
- **Mathieu :** coordination et relais `[À VÉRIFIER]`.
- **Patrick :** revues d'ingénierie (#610, #976, #983, #996, #1014 et autres) ; revue du modèle d'expiration ; brouillon du modèle d'interaction.
- Non co-auteurs ici : Alex (décision sur #995) et Seb (la proposition de diagnostics devenue #1011, selon Isabelle). Julie n'a pas touché à PAM.

## Références

- `github.com/mgagp/ezkey` : `04d339b7`, `3fef2411` (#540), `8b3aa5df` (#610), `9f7fa78f` (#993), PR #1014 ; issues #858, #955, #979, #980, #982, #992, #995, #997, #1011, #1012, #1013, #1016.
- `ezkey-pam/STATUS.md`, `ezkey-pam/docs/INTERACTION-MODEL.md`, `docs/SECURITY_POSTURE.md`, `sites/ezkey-org-editorial/LAB-PAPERS.md`.

## Transparence

Les co-auteurs nommés ci-dessus qui sont des agents AI sont présentés comme tels. Marc a la responsabilité éditoriale. Les rétrospectives condensées ont été recueillies par Mathieu. Rien dans ce papier n'est une garantie de sécurité ni une affirmation de préparation à la production.

<!-- ezkey-org:exclude-start
RÉTROSPECTIVES BRUTES (condensées, telles que reçues). Deux détails d'hôte ont été expurgés dans cette copie ; les fichiers complets sont conservés hors du dépôt ([REDACTED chemin interne]).
Les RETRAITS de Christophe sont respectés : sa deuxième surprise (sur la ligne de posture) et la phrase « j'ai écrit une ligne de posture sur un défaut non vérifié » ne sont utilisées nulle part dans le papier.

----- patrick.md -----
# Retro PAM - Patrick Grokbot (2026-10-25) (condensé fidèle)
Sources : ses rapports de revue + PR; n'a pas revérifié que chaque commentaire a été posté sur GitHub.
1) Revues craft PAM : #610 (M1), #976 (2.2.12), #983 (paliers 0-1), #996 (challenge_requested=true), #1014 (signaux); PR voisines #898 #904 #908 #916 #934 #936 #956. Revue expiration PAM <-> tentative serveur (5 lacunes vérifiées à ab877d33). Modèle d'interaction PAM (brouillon pour ezkey-pam/docs/INTERACTION-MODEL.md, base 2.2.11 + #976), paliers de fluidité. Brouillon d'issue « fatigue d'approbation » (palier 2 de #982), publication inconnue.
2) Moment charnière : #1014. Lecture statique prédisait un blocage du processus [pam] après SIGTERM; l'exécution en agent cloud l'a confirmé (>=123 s vs 225 ms base) et a révélé un 2e défaut : SIGTERM pendant la création absorbé => authentification SUCCESS après déconnexion client (B2, décision sécurité à Christophe). Résolu par handler async-signal-safe + SIG_DFL/raise(SIGTERM), GO à 6bc342a8. Aussi #610 B1 (URL absente = repli silencieux sur localhost:7080, fail-open possible) et #976 B1 (TTL calculé avant le « ok »).
3) Surprises : défaut visible seulement sous vrai sshd, invisible à make test/CI (rien ne compilait le module à l'époque de #610); gardes de CI non probantes (#996 grep satisfait par un commentaire; #1014 m2 check-source trompé par l'émission en commentaire) => un humain lit « CI verte » et passe; attribut challenge par enrôlement, pas par intégration (erreur initiale corrigée par lecture de code sur #996).
4) Rendement décroissant : re-revues sur #976/#983 pour mineures de libellé/doc; revues statiques sans Docker/Java sur sa boîte (agent cloud pour chaque preuve, lent); divergences de documentation. Ouvert : #1014 m1 (abort effacé à l'entrée du wait, latent), m2, m3 (STATUS LINE_MAX, user[128]); fatigue d'approbation (débit, verrouillage après refus); rotation planifiée qui audite une perte de CAS comme échec (clés, pas PAM).
5) Méthode : têtes figées + un seul lot de correctifs par revue; table de mutations exécutées (tuée/survivante) pour tout test de preuve et réinstaller le module muté (faux survivant #961 = jar non réinstallé); agent cloud Java/Docker/libpam permanent; une source canonique par sujet; décisions sécurité à Christophe, défauts produit à Alex; marquer exécuté vs raisonné.

----- christophe.md -----
# Retro PAM - Christophe Grokbot (2026-10-25) (condensé fidèle, avec son correctif)
Seulement ce qui est relié au dépôt; rien publié.
1) #1014 : relecture du handler SIGTERM (ce que le handler appelle réellement), verdict envoyé à Mathieu, 0 commentaire posté sur la PR. #992 (fatigue d'approbation : limite des demandes en attente, débit par enrôlement, refroidissement, alerte REJECTED) cadrée puis réécrite en concepts de plateforme sur consigne de Marc du 22/10. Ligne SECURITY_POSTURE « fatigue d'approbation non encore atténuée côté serveur » rédigée; texte final NON relu par lui dans le dépôt (ne pas affirmer qu'elle y figure). Pentest sur le Mac : P1 local, en pause, aucune trouvaille => rien revendiqué.
2) Charnière : fatigue d'approbation. Le prompt « ok » avant création est un filtre anti-accident pour clients batch honnêtes, pas une défense (l'attaquant avec le 1er facteur SSH tape « ok »; la création supplante la demande en attente = le vecteur). Parade déplacée vers code défi par défaut côté connecteur + garde-fous serveur (#992). Règle « plateforme générique, connecteur dans son README » posée par Marc le 22/10 : a changé la structure de #992.
3) Surprise : dans #1014 le handler SIGTERM utilisait snprintf, non async-signal-safe (seuls write/sigaction/raise le sont) : lecture de ce que le code appelle, pas exécution. [Sa 2e « surprise » sur la ligne de posture est RETIRÉE par lui : ne pas citer.]
4) Rendement décroissant : re-revues successives d'une même PR (#1014 en plusieurs têtes, #961 en trois), dès la 2e que des nits. Ouvert : #992 non implémentée; ligne SECURITY_POSTURE à confirmer; pentest P2 deux tenants non fait; module expérimental et non déployé, la posture doit le dire.
5) Méthode : un finding = texte exact dans la PR/issue, pas dans le chat (M1/M2/M3 de #961 perdu vers l'agent cloud); une revue par tête figée (SHA); toute affirmation de code livré cite le commit (principe, pas aveu); messages en attente qui ne réveillent pas : prévoir un ack de lecture sur demandes de revue bloquantes. [Phrase « j'ai écrit une ligne de posture sur un défaut non vérifié » RETIRÉE par lui.]

----- isabelle.md -----
# Retro PAM - Isabelle Grokbot (2026-10-25) (condensé fidèle)
Sources : PR/issues + ses notes de campagne hors dépôt ([path outside repo] sur sa machine).
1) Campagne manuelle PAM 2.2.14 (#996) sur VM [test VM], Marc au clavier SSH + Demo Device : QA Sphere run 17, 30 cas (29 PASS, 1 FAIL S6-02), milestone « atteint avec réserves » (Marc), réserves S1D-06 et S11-01. Révision cas S0-S13 ([path outside repo]), mesures de timing (TTL tentative 75 s, « ok » 25 s, budget module 105 s, LoginGraceTime sshd 120 s). Analyse avec Edgar/Seb du « Too many authentication failures » -> #1011 (diagnostics) et PR #1014 (2.2.15). Contribution #1015 (accès admin permanent).
2) Charnière : « Too many authentication failures » intermittent. Chasse initiale à Cloudflare/API/cooldown; avec debug=true, ligne du 2026-10-24 14:33:22 HE : UNAVAIL en 741 ms, reason « pre-wait confirm missing or not ok », aucun POST => réponse vide/EOF à l'invite « ok ». Évité un faux cooldown; a produit la proposition de Seb (journaliser stage/reason/http/ms) devenue #1011.
3) Surprises : MaxAuthTries 1 => message trompeur, aucun cooldown côté Ezkey; budget réel = LoginGraceTime sshd 120 s (tentative 155 coupée à 124 s alors qu'approuvée); Demo Device reconstruit la page depuis la session et met en cache (S11-01 non concluant); pam_ezkey dans la pile sshd à l'étape setcred même hors groupe (S5-01, attendu faux). « Un humain seul n'aurait pas vu vite les chiffres de timing ni les lignes d'audit; moi je n'aurais pas pu avancer sans Marc au téléphone. »
4) Rendement décroissant : essais S11/S12, aller-retour Marc -> Isabelle -> exécution (~77 s) > TTL 75 s, essais invalides; il aurait fallu un pilote d'approbation automatisé. Ouvert : #1011/#1014, #1012, #1013, #940, #1015, correction texte cas S0-05/S5-01/S8-01 dans QA Sphere, choix d'outil de pilotage, clôture du run 17 (non décidée), note de rétro sur l'erreur d'optimistic-locking à l'édition des métadonnées d'enrôlement.
5) Méthode : Walk Gate remplie avant de marcher (manqué au départ sur #961); consigner dans le dépôt les constats qui changent un runbook (ses notes de rétro sont hors dépôt); un propriétaire par résultat QA Sphere; revalider « Marc a approuvé » avec Marc; prévoir l'outil de test avant la campagne.

----- edgar.md -----
# Retro PAM - Edgar Grokbot (2026-10-25) (condensé fidèle)
Écriture seule, rien touché sur [internal host] ni [community instance]. Aucune PR/issue PAM rédigée; traces dans le dépôt par citation seulement (non vérifié dans GitHub).
1) Gestion du module PAM sur la VM [test VM] (2.2.10 -> 2.2.14, sauvegarde .bak à chaque version, retour arrière documenté); config pam_ezkey.conf (challenge_requested true/false), drop-in sshd MaxAuthTries 1 pour le groupe ezkey-mfa. Avant chaque changement : snapshot, sshd -t, rapport. Contre-test de #908 cité dans #941; analyse de la tentative 155 citée dans #1011.
2) Charnière : tentative 155 coupée « Too many authentication failures » (trompeur) : debug=true => étape « ok » reçoit une entrée vide; sshd tue le processus à LoginGraceTime 120 s alors que wait_timeout = 75 s. Tentative 156 réussie (S8-02). => #1011 puis #1016.
3) Surprises : avec MaxAuthTries 1 toute erreur interne ressemble à un échec d'auth, et result=UNAVAIL pam_code=9 ne dit rien du motif; budget réel = LoginGraceTime. [maintenance account] seul compte hors du groupe ezkey-mfa, donc chemin de maintenance direct ouvert pendant la campagne (principe posé dans #955).
4) Rendement décroissant : bascules répétées de challenge_requested entre S11 et S12 (voir #997/#998). Ouvert : #1011 #1012 #1013 #1016 #955 #997 #998. Rapport final de campagne pas encore livré; [remarque sur une permission de fichier de clé: expurgée] et doc MaxAuthTries à son arriéré.
5) Méthode : constats opérationnels dans l'issue au moment où ils tombent; GO permanent borné à la VM de test avec snapshot + sshd -t + rapport; un seul bot à la fois sur la VM; un « Marc est d'accord » relayé par un bot ne vaut jamais GO.

----- julie.md -----
# Retro PAM - Julie Grokbot (2026-10-25)
Pas touché au PAM : ni revue, ni Walk Gate, ni PR, ni rapport. Ses dossiers de la période : Walks #786 et #828 (Intégrité), revue #835, revue #948. Seul contact : message « retest PAM #908 » envoyé par erreur par Edgar, redirigé. Rien publié.

RÉTROSPECTIVE DE MATHIEU : existe en BROUILLON non validé par Marc. Non copiée ici et non intégrée comme définitive. Les éléments qui n'en viennent que d'elle sont marqués [À VÉRIFIER] dans le papier.
ezkey-org:exclude-end -->

<!-- ezkey-org:exclude-start
REGISTRE DES ÉLÉMENTS [À VÉRIFIER] / [DONNÉE MANQUANTE] (même liste que le brouillon EN)
1. Mathieu : preuve de co-signature, rétrospective et note VALIDÉES (il n'existe qu'un brouillon non validé).
2. Affirmation « base de données plus simple » à l'été 2025 ; « simulateur au départ » au-delà du commit du simulateur.
3. Auteur (humain ou compte automatisé) des commits du tableau 1.
4. Essai sur VM du prototype de sept. 2025 (Marc ; pas dans git).
5. Versions 2.2.2 à 2.2.9 : non reconstituées.
6. Tours et gravité des revues de #897, #908, #956, #976, #983, #996 (seules #610 et #1014/#1016 lues).
7. Chiffres de campagne (30 cas, 29/1, S6-02, S1D-06, S11-01, trace de 741 ms) : de la rétrospective d'Isabelle ; outil et notes hors git. Numéros de tentative (155/156) omis volontairement.
8. « GO de Patrick et Christophe à 6bc342a8 » sur #1014 : connu seulement par le texte de #1016 (pas d'objet de revue GitHub).
9. Que les commentaires publiés via mgagp reproduisent le texte original (Patrick n'a pas revérifié que chacun a été publié).
10. Mesures de Patrick (>= 123 s contre 225 ms) : de sa rétrospective ; le corps de #1014 rapporte « gone_ms=-1, 0/4 » puis « 4/4, ~50-200 ms ».
11. Si l'accélération des agents est la motivation de Marc ou un cadrage rétrospectif (Marc à confirmer au §1).
12. Contribution de Seb à #1011 (rapportée par Isabelle seulement).
13. Notes de co-auteurs : condensées par l'éditeur ; chaque auteur valide la formulation.
14. Expurgations : noms d'hôtes et une remarque sur une permission de fichier de clé (Edgar) retirés du bloc brut ; demander à Marc si cette remarque doit être traitée comme un enjeu d'exploitation.
15. RÉSOLU (Marc, 2026-10-25 19:44). Tableau 3, ligne 1 : Demo Device pour presque tous les tests, Pixel seulement pour quelques tests exploratoires de Marc ; attribué à sa déclaration ; plus de marqueur, et le Pixel n'apparaît nulle part ailleurs dans le corps.
16. NOUVEAU. Tableau 3, ligne 5 : lot de constats de #961 « perdu dans le clavardage » (la rétro de Christophe mentionne M1/M2/M3 de #961 perdus vers l'agent infonuagique ; PR #961 non relue).
17. RÉSOLU (Marc, 2026-10-25 19:44). Tableau 3, ligne 7 : principe confirmé par Marc (rien n'est publié sans lui ; il fournit la matière et l'orientation, les agents composent, il révise, relecture et aperçu avant publication) ; marqueur retiré. La mention « relayed by Mathieu » de #1016 est vérifiée.
18. NOUVEAU. Tableau 3 dans son ensemble : assemblé à partir des rétrospectives, pas mesuré. Éléments tirés seulement du brouillon de Mathieu et laissés de côté : « Marc fusionne lui-même », « prête seulement si CI verte, 0 fil de revue », « un go de Marc dans le fil de l'agent concerné », « synchronisations de statut sans effet ».
19. NOUVEAU. §1, deuxième raison (voir quelle part du travail les agents peuvent porter avec peu d'intervention humaine) : vient de la demande de Marc via Mathieu, pas d'un document antérieur au 2026-10-25 ; Marc à confirmer la formulation.
20. NOUVEAU. « Temps de lecture ~15 min » est une estimation.
ezkey-org:exclude-end -->
