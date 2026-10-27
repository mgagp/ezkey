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
NOTES ÉDITORIALES — brouillon français, révision 3 (2026-10-27). Branche docs/lab-paper-pam-draft. Pas pour publication.

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

Statut (révision du 2026-10-27) : miroir du brouillon EN de la même date. Intègre le second tour de rétrospective
(note de labo EZK-LAB-2026-001-retro-2-synthese.md et retro-2-raw/). Marc relit, corrige et cadre avant toute publication.
Décisions conservées : phrase de sécurité « raisonnablement assurée dans le périmètre testé, sans être la fin de l'histoire » ;
l'écart de sept. 2025 à sept. 2026 comme « une pause d'un an, sans trace dans le dépôt » ; la rapidité des agents à trouver
et corriger des vulnérabilités reste une observation personnelle, sans citation externe ; le point d'arrêt est une
collaboration entre Marc et les agents, jugée suffisante comme maturité expérimentale et rien de plus ; pas de « sécurisé »,
« audité », de certification, ni d'équivalence Duo/FIDO2. Ezkey est d'abord un projet de laboratoire.

Défis de cohérence traités dans le corps, pas lissés :
- La lecture d'arrêt du 2026-10-25 et les passes du 2026-10-27 forment une seule histoire : un premier jugement, puis un
  approfondissement voulu, puis le même genre de jugement.
- Le « cycle 3 de #1051 » / « pam_ezkey 2.2.20 » de Christophe ne correspond pas aux titres des PR. #1051 est titrée cycle 2b
  en 2.2.19 ; #1067 est titrée cycle 3 et son corps nomme la 2.2.20. Laissé en [À VÉRIFIER], pas fusionné.
- L'arbre de ce brouillon a encore ezkey-pam/STATUS.md en 2.2.14. Les fusions plus tardives sont citées depuis GitHub, pas depuis cet arbre.
- #1014, ouverte dans le brouillon du 2026-10-25, a été fusionnée le 2026-10-27 (b1fd7e2b). Le récit du gestionnaire de signal
  n'a pas été relu contre cette fusion.
- Les « rendements décroissants » de la rafale d'octobre (re-revues, bascules, TTL) et ceux des cycles suivants ne sont pas
  additionnés en une seule courbe. L'expression reste un jugement, pas une mesure.
- Les décomptes fins de Patrick (12 constats, 19/35, 3/10, 29/35, 88–90 s) restent dans sa réponse brute et hors du corps.

La rétrospective de Mathieu n'existe qu'en BROUILLON non validé par Marc : elle n'est PAS intégrée. Il n'a pas répondu au tour 2.
Les mises à jour des notes de co-auteurs du 2026-10-27 attendent la validation de chacun.
Marqueurs : [À VÉRIFIER] / [DONNÉE MANQUANTE], listés en fin de fichier. Aucun fait propre au FR.
ezkey-org:exclude-end -->

# Ezkey PAM : durcir un module SSH expérimental avec des agents, et décider où s'arrêter

**Auteurs :** Marc Gagnon (responsabilité éditoriale) ; Christophe Grokbot (revue de sécurité) ; Edgar Grokbot (déploiement et périphérie) ; Isabelle Grokbot (QA exploratoire) ; Mathieu Grokbot (coordination) `[À VÉRIFIER : preuve et note validée de Mathieu en attente]` ; Patrick Grokbot (revue d'ingénierie)
**Affiliation :** Ezkey Lab
**Identifiant :** `EZK-LAB-2026-001`
**Statut :** draft
**Date :** 2026-10-27
**Mots-clés :** PAM, SSH, MFA, fail-closed, développement assisté par agents, autonomie des agents, cadrage humain, rendements décroissants, expérimental
**Temps de lecture :** ~18 min

## Points clés

- Dans la campagne rapportée le 2026-10-27, chaque friction que décrivent les co-auteurs vient du côté humain du cadre. La règle du go était connue et n'a jamais été écrite en une phrase. La portée d'une permission permanente n'était pas claire. Une méthode réglée n'était écrite nulle part. Plusieurs consignes étaient ambiguës. Aucun critère d'arrêt ni budget de cycles n'avait été fixé d'avance. Les rapports n'en font pas un échec des agents (§7.1).
- Après une première lecture le 2026-10-25, Marc a laissé les agents libres d'itérer, autant de fois que nécessaire, pour voir où un humain et les agents convergent vers des rendements décroissants. Il juge aussi que les modèles avancent vite, en cybersécurité en particulier. Ce jugement, comme son impression que les agents trouvent et corrigent les vulnérabilités plus vite, est une observation personnelle : nous ne citons aucune source extérieure. « Rendements décroissants » est ici un jugement, pas une mesure. Réviseurs et implémenteur partagent une famille de modèles, la boucle se note elle-même, et une revue humaine indépendante, un fuzzing continu et un modèle de menace mettraient ce jugement à l'épreuve (§7.2).
- Dans cette campagne plus tardive, les agents ont porté davantage du travail : un tenant par l'API publique, avec un journal des heures ; un sondage plus lent et un contrôle de fraîcheur après une réponse de limite de débit ; un protocole d'état avec empreintes et retour arrière vérifié ; une preuve par exécution sur un agent infonuagique jetable. Ce qui manquait encore, c'étaient de petits ajustements écrits, pas un autre genre d'agent (§6.5).
- Un prototype d'environ 900 lignes, commité le 2025-09-03 et validé à la main, n'a laissé aucune preuve automatisée. Le dépôt montre ensuite une pause d'un an, sans trace dans le dépôt de travail fonctionnel sur PAM, jusqu'à la reprise le 2026-09-15.
- Marc et les agents jugent le résultat suffisant comme maturité **expérimentale** : la sécurité est raisonnablement assurée dans le périmètre testé, sans être la fin de l'histoire. Ezkey est d'abord un projet de laboratoire. Ce n'est pas une affirmation que le module est sécurisé, audité ou certifié, ni une équivalence avec Duo ou FIDO2.

## Résumé

Ce papier rend compte d'une expérience sur `ezkey-pam`, un module PAM Linux qui ajoute l'approbation par appareil Ezkey aux connexions SSH. Ezkey est un projet de laboratoire. Un prototype a été commité en septembre 2025. Après une pause d'un an, sans trace dans le dépôt de travail fonctionnel sur PAM, le module a été modernisé en septembre 2026, puis revu et corrigé tout au long d'octobre 2026 par des agents qui travaillaient avec Marc. Une passe supplémentaire, rapportée le 2026-10-27, a laissé ces agents libres d'itérer pour que le point de rendements décroissants soit vu plutôt que supposé. Nous décrivons ce qui a été construit, d'où viennent les frictions rapportées, ce que les cycles suivants ont ajouté, et un point d'arrêt choisi ensemble par Marc et les agents. Dans cette passe, les frictions tiennent au cadrage humain : des règles, une portée et un critère d'arrêt qui n'avaient pas été écrits. La sécurité est raisonnablement assurée dans le périmètre testé, sans être la fin de l'histoire. Nous ne revendiquons ni préparation à la production, ni audit, ni certification, ni équivalence avec Duo ou FIDO2.

## 1. Introduction et motivation

Marc a d'abord vu une connexion SSH protégée par Duo au travail, ce qui était nouveau pour lui `[À VÉRIFIER avec Marc : quand]`. La question qui a suivi : Ezkey pouvait-il jouer un rôle semblable pour SSH, avec les morceaux qu'il avait déjà ? Cette question n'est pas une revendication d'équivalence. Rien dans ce papier n'assimile Ezkey à Duo, à WebAuthn ou à FIDO2.

Ezkey est d'abord un projet de laboratoire, qui n'est promis à personne. Le créneau réaliste que nous lui voyons est la protection sans mot de passe, à cryptographie forte, d'applications internes pour un personnel interne restreint. Aller plus loin exigerait d'abord une discipline de type SOC 2 : un prérequis, pas un plan. `docs/SECURITY_POSTURE.md` traite SOC 2 comme un vocabulaire de référence et non comme une cible de certification, et ne revendique aucune équivalence.

Deux autres raisons ont façonné l'expérience. Marc a l'impression que les agents autonomes sont devenus visiblement plus rapides à trouver et à corriger des vulnérabilités. C'est son observation personnelle ; nous ne citons aucune source externe et ne l'avons pas mesurée. Séparément, il juge que les modèles avancent vite, en capacité de cybersécurité en particulier. Cela aussi est son jugement, sans source externe `[À VÉRIFIER : formulation de cette raison, donnée pour la révision du 2026-10-27]`. C'est pourquoi, après une première lecture d'arrêt le 2026-10-25, les agents ont été laissés libres de dire ce qu'ils voyaient et d'itérer autant de fois que nécessaire. Le but des passes supplémentaires était de regarder où un humain et les agents convergent vers des rendements décroissants, pas d'accumuler des tours pour eux-mêmes (§5.4, §7.2). Une troisième raison, déjà dans le brouillon du 2026-10-25, était de voir quelle part du travail les agents pouvaient porter avec peu d'intervention humaine (§6.4, §6.5).

Ce papier dit ce qui s'est passé et ce qui a été mesuré. Une conclusion est énoncée sans détour, parce que le second tour de rétrospective la demandait : là où une friction est apparue, elle était du côté humain du cadrage (§7.1). Ce que l'expérience signifie au-delà reste hors de ce papier.

## 2. Hypothèses

Ces hypothèses ont été **formulées après coup** ; aucune n'a été écrite avant le début du travail. Nous les énonçons pour rendre la lecture explicite, pas pour laisser croire que l'expérience a été conçue autour d'elles.

- **H1.** Les morceaux existants (authentification, API, appareil) suffisaient à construire un flux complet d'approbation SSH.
- **H2.** Une équipe AI avec revue croisée peut amener un petit module en C à un point où les constats de revue deviennent mineurs.
- **H3.** Au-delà d'un certain point, un tour de revue de plus coûte davantage que le risque, à l'échelle du labo, qu'il retire, et ce point peut être repéré.
- **H4.** Dans la campagne avec agents, les attentes et la confusion viendraient du cadrage humain (règle de go non écrite, portée de permission peu claire, consignes ambiguës, aucun critère d'arrêt fixé d'avance), et non d'une limite des agents.

H1 a été mise à l'épreuve par le prototype de 2025. H2 et H3 n'ont pas été testées de façon contrôlée. H4 n'a pas été écrite avant le tour du 2026-10-27 ; c'est la lecture que ce tour soutient, et nous l'énonçons comme conclusion au §7.1. Le même tour complique H3 : le point peut être décrit après coup, et il est difficile de le connaître pendant le travail si aucun budget de cycles n'a été fixé d'abord (Patrick).

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

**Environnement.** Un conteneur Rocky Linux pour les builds et les démos et, à partir d'octobre 2026, une VM de test dédiée avec un vrai sshd. Plusieurs constats ne sont apparus que là. Nous ne donnons aucun nom d'hôte, aucune adresse ni chemin interne.

**Rôles.** Marc décidait et approuvait, et tenait le clavier SSH pendant les campagnes manuelles. Mathieu coordonnait et relayait pendant la rafale d'octobre ; il n'est pas une source du tour du 2026-10-27 `[À VÉRIFIER : Mathieu]`. Patrick et Christophe faisaient les revues ; Edgar exploitait la VM de test ; Isabelle menait la QA exploratoire.

**Comment les revues arrivent sur GitHub.** Les revues de Patrick et de Christophe arrivent dans le dépôt par le **compte GitHub de Marc** : les commentaires sont publiés depuis `mgagp`, et le texte de chacun indique quel co-auteur l'a rédigé. Une issue (#1016) dit que les revues ont été relayées par Mathieu, et les commits de fusion (squash) portent le nom de Marc. Dans ce papier, l'attribution repose sur le texte du commentaire ou sur la rétrospective du co-auteur, pas sur l'identité GitHub `[À VÉRIFIER : que les commentaires reflètent le texte original des revues]`.

**Tests.** Une cible `make test`, des scripts de test shell, des sondes de signaux contre un vrai sshd et un job de CI. Nous ne les avons pas relancés pour ce papier ; les chiffres plus bas viennent du dépôt, des PR ou des rétrospectives, et ces dernières sont marquées.

**Bornes nommées pour la campagne du 2026-10-27.** Christophe liste les règles qui ont aidé : rien dirigé contre l'instance communautaire publique, aucun commit ni push issu de la campagne, aucun secret affiché, preuve par exécution sur un agent infonuagique jetable, constats classés en corriger, documenter ou hors périmètre, et revue de sécurité tenue à part de la revue de code. Une permission permanente sur le module ne couvrait pas, à elle seule, les écritures système. Chacune de celles-ci a attendu son propre go (Edgar). Le détail est au §7.1.

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
| 2.2.15 | 2026-10-27 (`b1fd7e2b`, #1014) | Diagnostics d'échec plus riches, gestion de SIGTERM, sondes de signaux (#1011, #1014) | Campagne manuelle sur la 2.2.14 ; revues d'ingénierie et de sécurité |

*Tableau 2. Versions correctives d'octobre 2026. Le brouillon du 2026-10-25 de ce papier avait encore #1014 ouverte. Les versions 2.2.2 à 2.2.9 ne sont pas reconstituées `[À VÉRIFIER]`. Le récit du gestionnaire de signal plus bas n'a pas été relu contre le texte fusionné `[À VÉRIFIER]`.*

Entre-temps, #992 (fatigue d'approbation : limite de demandes en attente, débit par enrôlement, refroidissement, alerte sur refus) a été cadrée, puis réécrite en termes génériques de plateforme sur consigne de Marc du 2026-10-22. Le commit `9f7fa78f` (#993) documente la limite sans détour ; #992 elle-même n'est pas implémentée.

La PR #1014 montre comment les constats devenaient plus fins. La lecture de Patrick prédisait que le processus enfant PAM pouvait se bloquer après un SIGTERM. Un agent infonuagique l'a confirmé et a révélé un second défaut : un SIGTERM pendant la création de la demande était absorbé, et l'authentification pouvait ensuite réussir après le départ du client. Christophe, en lisant le gestionnaire, a trouvé qu'il appelait une fonction qui n'est pas sûre en contexte de signal (async-signal-safe). La tête sur laquelle lui et Patrick ont rendu GO (`6bc342a8`) l'a remplacé par un gestionnaire qui écrit dans le périphérique de journalisation, rétablit l'action par défaut et relance le signal, avec quatre sondes de signaux contre un vrai sshd qui échouaient sur la tête précédente et passent sur celle-ci. Les deux réviseurs ont rendu GO sur cette tête, ce que nous savons par le texte de #1016 et non par un objet de revue GitHub `[À VÉRIFIER]`. #1016 dit aussi que la même tête a été revue de nouveau à `34ae3603`, et que #1014 a ensuite été fusionnée comme `b1fd7e2b`. Nous n'avons pas relu le texte fusionné pour cette révision.

### 5.4 Une passe supplémentaire, voulue (2026-10-27)

La lecture du 2026-10-25 n'a pas mis fin au travail. Marc a laissé les agents libres de continuer, pour la raison du §1. Ce qui suit est tiré de leurs rétrospectives du 2026-10-27 (la note de labo `sites/ezkey-org-editorial/lab-notes/EZK-LAB-2026-001-retro-2-synthese.md`, et les quatre réponses brutes sous `retro-2-raw/`). Là où le titre d'une PR contredit la mémoire d'un réviseur, nous le disons.

**Ce que les cycles ont ajouté.** La synthèse, à la suite de Patrick : le cycle 1 est la seule passe qui a trouvé quelque chose de nature produit. Aucun test ne tuait les mutations de la décision d'authentification, et la vérification TLS n'était gardée par aucun test. Ensuite, le travail a été de l'outillage et de la preuve. Au cycle 2, il n'a plus vu de défaut de comportement, et il a recommandé un cycle 3 court, puis l'arrêt. Au cycle 3, la sonde 4 était un bogue du harnais ; un correctif d'un cycle antérieur avait ouvert un trou dans un test TLS (la vérification du pair seule) ; un mutant qu'il avait jugé équivalent ne l'était pas, et l'appel a été corrigé. Il écrit que l'itération a ajouté trois choses qu'un seul passage aurait manquées : une mesure à la place d'un soupçon sur la sonde 4, la détection d'un défaut qu'un correctif avait lui-même introduit, et la correction d'une erreur du réviseur. Elle n'a pas ajouté de défaut de sécurité supplémentaire après le cycle 1. Sa phrase selon laquelle un seul passage, plus une vérification par exécution de chaque correctif, aurait attrapé presque tout, est son jugement, pas un décompte.

Sur GitHub, #1067 (fusionnée le 2026-10-27) est titrée comme suites du cycle 3 après #1051, et sa description nomme la version 2.2.20. Elle dit que la sonde 4 était un blocage du harnais sur une ouverture bloquante, pas un défaut produit, et que le test TLS a été changé pour que la vérification du pair seule rejette. #1051, fusionnée plus tôt le même jour, est titrée comme suites jusqu'au cycle 2b, version 2.2.19. Christophe, de mémoire, place la chute de valeur marginale autour du « cycle 3 de #1051 » et nomme aussi la version 2.2.20. Nous ne traitons pas ces étiquettes comme le même objet `[À VÉRIFIER : étiquette de cycle et 2.2.20]`.

Après ce troisième cycle, la synthèse rapporte des détails, puis une récursion : un audit des tests, puis du script qui audite les tests, puis du trap de ce script. La réponse brute de Patrick dit que le rendement du second niveau était faible. Nous n'avons pas ses décomptes fins de mutants sous une forme que nous voulons imprimer ; ils restent dans la réponse brute `[À VÉRIFIER : ces décomptes]`.

**La campagne réelle.** Isabelle rapporte que les risques qu'elle tient pour réels ont été trouvés tôt. Pendant l'attente d'une connexion, le module ne voit pas la mort du client, de sorte qu'un processus de pré-authentification peut durer jusqu'à environ 75 secondes, borné par le TTL de la tentative. Un login plus tardif l'emporte et clôt la tentative précédente. Des rejeux depuis un second système d'exploitation client ont ajouté une preuve du même comportement serveur, pas un risque nouveau. Ses verdicts de scénario sont au tableau 5. Le silence réseau véritable n'a pas été testé : il n'avait pas de go. Le signal ouvert que Christophe nomme encore est une limite de débit sur la création de tentatives d'authentification (#1070, ouverte le 2026-10-27, hors du module, et non mesurée).

L'arbre qui porte ce brouillon ne contient pas ces fusions. Sur cet arbre, `ezkey-pam/STATUS.md` enregistre encore la version 2.2.14 et dit encore que le module n'est pas un agent MFA exploitable sur un hôte réel. Nous n'avons pas relu `STATUS.md` sur la branche par défaut après #1067 `[À VÉRIFIER]`.

## 6. Résultats

### 6.1 Tours de revue

#610 a été revue sur trois têtes (`932c83f4`, `5d743589`, `ca86a1c2`), avec les verdicts ci-dessus. Sur #1014 (`8272d360`, puis `6bc342a8`), l'ingénierie et la sécurité ont d'abord demandé des changements, puis toutes deux rendu GO à `6bc342a8`, et #1016 a rassemblé de petites suites non bloquantes. Elle consigne aussi que la documentation ne doit **pas** écrire « jamais SUCCESS après le départ du client » : SUCCESS reste possible si un TERM est absorbé pendant `/wait` après l'arrivée de la réponse ACCEPTED, et #1016 dit que ce n'est pas une élévation. D'autres PR (#897, #908, #956, #976, #983, #996) ont aussi été revues ; nous ne les avons pas toutes lues, donc nous ne donnons aucun décompte de constats par tour `[À VÉRIFIER : tours et gravité]`. Les cycles plus tardifs du §5.4 sont une série à part. Nous ne les replions pas dans un seul décompte.

### 6.2 Campagne manuelle sur la 2.2.14

Isabelle a mené une campagne manuelle sur la 2.2.14 (#996) sur la VM de test, avec Marc au clavier SSH et sur le Demo Device. Elle est consignée dans un outil de gestion de tests hors du dépôt : 30 cas, 29 réussis et 1 échoué (S6-02, annulation et audit, maintenant #1012). Marc a consigné le jalon comme « atteint avec réserves » (S1D-06, S11-01) `[À VÉRIFIER : chiffres tirés de la rétrospective d'Isabelle du 2026-10-25 ; outil et notes hors dépôt]`.

Le résultat le plus utile a été un symptôme trompeur. Avec `MaxAuthTries 1`, presque toute erreur interne apparaît comme « Too many authentication failures ». Une trace de debug du 2026-10-24 a montré UNAVAIL après 741 ms, raison « pre-wait confirm missing or not ok », et aucun POST envoyé : l'invite `ok` avait reçu une réponse vide. L'analyse d'Edgar a aussi trouvé que c'est le `LoginGraceTime` de sshd (120 s), et non le délai d'attente du module (75 s), qui borne réellement une tentative. Cela a mené à #1011, #1014 et #1016.

### 6.3 Éléments ouverts

Onze issues étaient ouvertes le 2026-10-25 (vérifié sur GitHub ce jour-là), plus la PR #1014 : #858, #955, #979, #980, #982, #992, #997, #1011, #1012, #1013 et #1016. Elles touchent la sécurité de la plateforme (#992 politique de demandes en attente et refroidissement, #858 signature de la réponse `/wait`), l'interaction (#982, #997), l'introduction sur l'instance communautaire (#955), les tests et la CI (#979, #980, #1013), et l'annulation, les diagnostics et les suites (#1011, #1012, #1016).

Cette liste est un instantané. Au 2026-10-27, #1014 était fusionnée et #1011 était fermée ; #1051 et #1067 avaient été fusionnées ; #1054 et #1070 étaient ouvertes. Nous n'avons pas recompté l'ensemble `[À VÉRIFIER : issues ouvertes après le 2026-10-27]`.

### 6.4 Autonomie des agents dans la rafale d'octobre

Une partie de l'expérience visait à laisser les agents faire plus du travail avec moins d'intervention humaine. Le tableau 3 est cette rafale, jusqu'au 2026-10-25. Le tableau 4 est la passe supplémentaire. Les sources sont les rétrospectives des co-auteurs. Ce sont des observations, pas une mesure de l'autonomie.

| Visée | Limite observée | Source |
| --- | --- | --- |
| Tests menés sans le téléphone d'une personne : le Demo Device d'Isabelle pour presque tous les tests ; le Pixel de Marc seulement pour quelques tests exploratoires à lui, en validation préliminaire (déclaration de Marc) | La campagne sur la 2.2.14 a quand même eu besoin de Marc au clavier SSH et sur le Demo Device ; Isabelle dit qu'elle n'aurait pas pu avancer sans Marc au téléphone | Marc ; Isabelle |
| Revues sur une tête figée (un SHA précis, un seul lot de correctifs par revue) | Les re-revues à partir de la deuxième tête environ n'ont apporté surtout que des points mineurs | Patrick, Christophe |
| Tests rejoués par les agents eux-mêmes : contre-tests (#908), sondes de signaux contre un vrai sshd, agent infonuagique qui exécute les prédictions (#1014) | Patrick n'avait ni Docker ni Java sur sa propre machine, donc chaque preuve passait par un agent infonuagique, lentement ; certains garde-fous de CI ne prouvaient rien (#996, #1014) | Patrick, Edgar |
| GO bornés : permission permanente sur la VM de test (snapshot, `sshd -t`, rapport, un seul bot à la fois) | Un « Marc est d'accord » relayé n'est pas un GO ; le chemin de maintenance de la VM de test est resté hors du MFA (principe dans #955) | Edgar, Isabelle |
| Constats écrits là où ils vivent (PR ou issue) | Messages qui ne réveillent pas un agent (d'où l'accusé de lecture sur les demandes bloquantes) ; un lot de constats de #961 perdu dans le clavardage `[À VÉRIFIER]` | Christophe |
| Recontre-tests sans humain dans la boucle | L'aller-retour humain (environ 77 s) dépassait le TTL de 75 s de la tentative, donc certains essais étaient invalides ; il manquait un pilote d'approbation automatisé | Isabelle |
| Agents qui composent et relaient textes et revues | Rien n'est publié sans Marc, ici et sur ezkey.org en général : il donne la matière et l'orientation, les agents composent, il révise et cadre, avec relecture et aperçu avant publication. Les revues arrivent sur GitHub par son compte, relayées par Mathieu (#1016) | Marc (déclaration) ; #1016 |

*Tableau 3. Visées d'autonomie et limites observées, jusqu'au 2026-10-25.*

### 6.5 Ce que les agents ont porté le 2026-10-27

La passe supplémentaire est celle où l'autonomie a bougé, et les ajustements qu'elle demandait sont petits. Le tableau 4 apparie chaque gain que rapportent les co-auteurs avec la règle écrite qui manquait encore.

| Ce que les agents ont porté | Ajustement encore requis | Source |
| --- | --- | --- |
| Un tenant créé par l'API publique, avec un journal des heures | La méthode réglée de ce jour-là n'était écrite nulle part | Isabelle |
| Un intervalle de sondage plus long, et un contrôle de fraîcheur après un HTTP 429 | Aucun nommé ; ceci est rapporté comme ayant fonctionné | Isabelle |
| Un protocole d'état : un seul login, un observateur en lecture seule, des empreintes avant et après, un retour arrière vérifié | Une feuille de go d'une page, signée par Marc dans le fil de l'exécutant : l'écriture exacte, les fichiers, la durée maximale, le retour arrière, un contrôle préalable des deux familles d'adresses, et les exclusions. Sans elle, chaque écriture système a attendu son propre go | Edgar |
| Preuve par exécution sur un agent infonuagique jetable ; constats classés en corriger, documenter ou hors périmètre ; revue de sécurité tenue à part de la revue de code | Un budget de cycles et un critère d'arrêt fixés au départ par un humain. « Rien de nouveau, mesurable et observable » était jugé par celui qui trouve. Un critère d'acceptation posé par les agents eux-mêmes (trois mutants tués à l'exécution) ne pouvait pas être atteint pour un contrôle TLS côté hôte seul, et une partie d'un cycle suivant a servi ce critère | Patrick ; Christophe |
| Les règles de campagne ont tenu : rien contre l'instance communautaire publique, aucun commit ni push, aucun secret affiché | Une phrase dans chaque brief : seul Marc, dans le fil de l'exécutant, vaut go, et chaque agent confirme qu'il n'agit pas sur la parole d'un autre agent. Le nettoyage de fin de campagne n'était pas sur une liste pré-approuvée ; il attendait encore un go | Edgar ; Isabelle ; Christophe |

*Tableau 4. Gains rapportés pour le 2026-10-27, et l'ajustement que chacun demande encore. Pas une mesure.*

Dans la rafale d'octobre, chaque étape passait encore à un moment par Marc ou par un relais (tableau 3). Le 2026-10-27, les agents ont planifié, exécuté et restauré à l'intérieur de bornes qui avaient été nommées. Les bornes qui n'avaient pas été nommées sont là où ils ont attendu. Nous n'avons toujours aucune mesure de la part du travail que cela représente `[DONNÉE MANQUANTE]`.

| Scénario | Verdict qu'elle rapporte | Limite qu'elle nomme |
| --- | --- | --- |
| S1 | PASS | — |
| S2 | Aucun cas bloqué | Lent pendant l'attente, immédiat aux invites |
| S3 | Refus rapide, PASS | Silence réseau non testé |
| S4 | PASS, trois fois | Échec fermé, et un diagnostic où deux fautes de configuration différentes se ressemblent |
| S5 | PASS | Même compte seulement |
| S6 | PASS, avec limites | Valeurs brutes seulement |

*Tableau 5. Verdicts de scénario tirés de la rétrospective d'Isabelle du 2026-10-27. Pas dans le dépôt `[À VÉRIFIER]`. Périmètre qu'elle énonce : le MFA protège les comptes du groupe pour lequel le module est configuré ; sur un compte de maintenance hors de ce groupe, l'authentification n'était pas invoquée.*

## 7. Discussion

### 7.1 La faiblesse était dans le cadrage

C'est la conclusion du papier.

Dans chaque friction que décrit le tour du 2026-10-27, les agents attendaient quelque chose qu'une personne n'avait pas rendu explicite. Cinq formes sont apparues. Nous les énonçons sans blâmer les agents. Ils ont appliqué les règles qu'ils avaient, et ils se sont arrêtés là où la règle manquait.

1. **La règle du go était connue et non écrite.** « Seul Marc, dans le fil de l'agent qui exécute, vaut go. » Edgar et Isabelle rapportent tous deux que la règle elle-même n'était pas le problème. L'attente venait de messages, envoyés par des agents, disant que Marc répondrait dans un autre fil.
2. **La portée d'une permission permanente n'était pas claire.** Un go permanent pour le module PAM ne disait pas s'il couvrait les écritures système : un changement de pare-feu, des fichiers de configuration, des clés. Chaque écriture a demandé son propre go (Edgar).
3. **Une méthode réglée ne vivait que dans la conversation.** Comment ouvrir un tenant d'évaluation par l'API publique, le 2026-10-27, n'était écrit nulle part. Du temps est passé à le chercher (Isabelle).
4. **Des consignes étaient ambiguës.** Une consigne de traiter l'API comme arrêtée, alors que l'API est externe, a mené à un changement de résolution de noms vérifié pour une seule famille d'adresses. Le test n'a rien mesuré et a exigé un second go (Edgar, Isabelle). Une heure a été présentée comme la fin de la campagne, alors que la vraie contrainte était l'expiration d'une clé et d'une session. La durée de vie d'une clé d'intégration, et qui la retire, n'ont jamais été écrites. Des mots comme « module chargé », « authentification non invoquée » et « second facteur » ont servi pour des faits différents (synthèse, §1).
5. **Aucun critère d'arrêt ni budget de cycles n'avait été fixé d'avance.** La convergence a été constatée après coup. La règle « rien de nouveau, mesurable et observable » est jugée par celui qui trouve la chose. Un critère que les agents se sont donné, trois mutants tués à l'exécution, ne pouvait pas être atteint pour un contrôle TLS côté hôte, et une partie du cycle suivant a servi ce critère plutôt qu'une réduction du risque (Patrick, Christophe).

Les ajustements qu'ils proposent pour une prochaine campagne sont courts, et aucun n'est adopté dans ce papier. Une feuille de go d'une page. Une phrase, dans chaque brief, sur qui peut dire go. Une liste de nettoyage pré-approuvée. Une vérification en lecture seule qu'un état altéré est vraiment effectif avant de consommer un login. Un budget de cycles choisi par un humain au départ, pas noté par les agents. La méthode réglée écrite là où un lecteur ultérieur peut la trouver (synthèse, §5). Ce sont des propositions, en attente de Marc `[À VÉRIFIER : si l'une d'elles est adoptée]`.

### 7.2 Où les rendements ont diminué

La décision de creuser encore était celle de Marc, pour la raison du §1. Ce que rapportent les agents, une fois libres d'itérer, est une courbe de forme reconnaissable, et cela reste un jugement.

Le cycle 1 a porté les constats de nature produit. Le cycle 2, dans le récit de Patrick, n'a plus porté de défaut de comportement, et il l'a dit sur le moment : un cycle 3 court, puis stop. Le cycle 3 a porté un bogue de harnais, un trou de test introduit par un correctif précédent, et la correction de son propre appel d'équivalence. Ensuite, des détails, et la récursion dans l'outillage qui vérifie l'outillage. La mémoire de Christophe place la même chute tôt. Les deux n'utilisent pas la même étiquette pour la PR (§5.4).

Ce que l'itération a valu, au-delà d'un seul passage, est la liste du §5.4 : la mesure au lieu du soupçon, les défauts introduits par les correctifs, les erreurs des réviseurs eux-mêmes. Pas un défaut de sécurité supplémentaire après le cycle 1. La campagne d'Isabelle dit la même chose de l'autre côté. Les risques qu'elle tient pour réels étaient tôt. Les rejeux suivants ont ajouté de la preuve.

**Critère de sortie (proposé, non adopté).** La règle de Patrick pour un dernier cycle est la suivante. Il n'y a aucun constat de type produit : pas de régression, pas de fail-open, pas d'invariant perdu, pas de fuite, pas de perte de données. Chaque correctif du cycle précédent a été relu en delta et exécuté. Le reste est de l'outillage ou de la documentation. Alors le résidu est consigné, au plus une issue de suivi est ouverte, et le travail s'arrête. Plafond dur : un cycle après le dernier constat de type produit, et l'outillage de test est relu sur un seul niveau, jamais le script qui prouve le script. Une limite qui ne peut pas être corrigée à un coût proportionné est documentée. Elle ne rouvre pas de cycle.

Cette règle n'était pas la règle de cette campagne. La campagne a constaté la convergence après coup. Nous ne présentons pas le critère ci-dessus comme un résultat. C'est sa proposition.

La réserve, dans les mots de Christophe et dans ceux de Patrick, doit rester à côté de la courbe. « Rendements décroissants » est un jugement, pas une mesure. Ni l'un ni l'autre ne donne de décompte de temps ou de coût. Réviseurs et implémenteur sont de la même famille de modèles, de sorte qu'un accord peut venir d'angles morts partagés et pas seulement d'une liste de défauts épuisée. La boucle se note elle-même : l'implémenteur écrit les mutants et les tests, et un réviseur de la même famille les lit. Patrick s'est trompé une fois sur un mutant « équivalent », et c'est pourquoi un second regard n'est pas optionnel sur un mutant de sécurité. Ce qui mettrait le jugement à l'épreuve est une revue humaine indépendante, un fuzzing continu, et un modèle de menace d'une page. Christophe liste les trois comme non faits.

Les rapports plus anciens de rendements décroissants, ceux de la rafale d'octobre, sont une autre observation : re-revues répétées d'une même PR (Christophe), libellés et documentation (Patrick), bascules répétées de `challenge_requested` (Edgar), recontre-tests invalidés parce que l'aller-retour humain dépassait le TTL de 75 secondes (Isabelle). Nous n'ajoutons pas cette observation à la courbe des cycles.

### 7.3 Une image fractale

Plus nous regardions de près, plus les cas limites étaient fins, et chaque correction ouvrait une question plus petite : une URL absente qui retombait en silence (#610) ; des reprises qui multipliaient les demandes (#908) ; OpenSSH qui retient le texte d'information (#941) ; la valeur par défaut du défi (#995) ; la fatigue d'approbation (#992, documentée, non atténuée côté serveur) ; un processus tué, un blocage et un SUCCESS tardif (#1014) ; puis un test qu'une mutation pouvait survivre (#1016). Les passes du §5.4 continuent cette lecture. D'abord des constats produit, puis la preuve, puis des détails, puis l'outillage qui audite l'outillage. Cette suite est notre interprétation. Les liens de cause à effet n'ont pas été testés.

### 7.4 Budget, temps, effort et valeur

Faire examiner et recontre-tester le même module par plusieurs agents change l'équilibre entre ces quatre éléments, et nous pensons qu'il diffère du travail en solo. Git montre cinq versions correctives en quelques jours dans la rafale d'octobre, chacune revue sur une tête figée, et deux PR supplémentaires fusionnées le 2026-10-27 (#1051, #1067). Nous n'avons pas de chiffres sur l'argent, le calcul ou le temps humain, ni sur le travail d'une personne seule `[DONNÉE MANQUANTE]`. Patrick dit qu'il n'a pas de décompte rigoureux. Christophe dit qu'il n'a rien quantifié. Notre résumé reste qualitatif : plus de revue, plus souvent, à un rythme que Marc pouvait suivre, à un coût que nous n'avons pas mesuré. Quand nous disons « dense » ou « rapide », nous parlons de dates et de décomptes visibles dans git, ou d'une impression personnelle marquée comme telle.

### 7.5 Un point d'arrêt, décidé ensemble

Le 2026-10-25, Marc et les agents ont convenu de traiter le résultat comme suffisant pour une maturité **expérimentale**. Le travail a ensuite continué, volontairement, à travers le §5.4. Le point d'arrêt après ces passes est le même genre de décision : une collaboration entre Marc et les agents, un jugement que la maturité expérimentale est suffisante, et non une revendication de quoi que ce soit de plus.

La formulation de ce papier est : la sécurité est **raisonnablement assurée dans le périmètre testé, sans être la fin de l'histoire**.

Christophe propose un paragraphe plus long pour ce périmètre. Nous le citons comme sa proposition. Nous ne l'adoptons pas comme constat de ce papier, et la version et l'étiquette de cycle qu'il contient sont celles que le §5.4 ne réconcilie pas :

> Dans le périmètre testé (pam_ezkey 2.2.20 sur le labo, un pilote sur l'instance communautaire sans SLA), les chemins d'erreur testés échouent fermés, la vérification TLS est active et prouvée par exécution et par mutants, la configuration est limitée à root et les valeurs secrètes ne figurent pas dans les journaux mesurés. Trois cycles de revue assistée par AI avec preuves reproductibles n'ont plus trouvé de problème d'exposition nouveau après le cycle 3 de #1051. Ce n'est ni un audit indépendant, ni une certification : le composant n'est pas présenté comme élément de première ligne avant une revue humaine indépendante, un fuzzing continu et un modèle de menace.

Nous ne disons pas « sécurisé ». Nous ne disons pas « audité ». Nous ne revendiquons aucune certification, ni aucune équivalence avec WebAuthn, FIDO2 ou Duo. Ezkey est d'abord un projet de laboratoire.

Nos raisons pour la formulation courte, et rien de plus :

- les cycles suivants, tels que les co-auteurs les décrivent, n'ont pas ajouté de défaut de sécurité après le premier de ces cycles, et le résidu est nommé (une limite de harnais, un contrôle TLS côté hôte que les tests à l'exécution ne tuent pas seul, #1070, silence réseau non testé) ;
- les rapports de rendements décroissants sont réels et ce sont des jugements (§7.2) ;
- les risques résiduels connus sont documentés, pas cachés ;
- continuer a un coût, et nous n'avons aucune preuve que cela changerait le risque à l'échelle du labo. Nous n'avons pas non plus la preuve que la courbe est finie (§7.2, la réserve sur la famille de modèles).

Cela ne veut **pas** dire que le module est exploitable sur un hôte. Sur l'arbre de ce brouillon, `ezkey-pam/STATUS.md` dit toujours que ce n'est pas un agent MFA exploitable sur un hôte réel, que la bascule du sshd et les tranches TLS et dogfood ne sont pas faites, et que le statut expérimental tient en attendant la validation de Marc sur un hôte. `docs/SECURITY_POSTURE.md` range PAM parmi les surfaces de labo non déployées. Passer à l'instance communautaire (#955) exigerait de refaire l'évaluation. Si `STATUS.md` sur la branche par défaut dit autre chose après #1067, ce n'est pas vérifié ici `[À VÉRIFIER]`.

## 8. Limites

- Le module est expérimental et n'a pas été publié. Sur l'arbre de ce brouillon il n'y a pas d'étiquette de publication PAM, et `STATUS.md` dit encore qu'il n'a pas fonctionné comme agent exploitable sur un hôte réel. Les tranches TLS, dogfood et bascule du sshd de l'hôte ne sont pas faites sur cet arbre. La version 2.2.20 est une chaîne dans le corps de #1067, pas un fait que nous avons revérifié dans cet arbre `[À VÉRIFIER]`.
- La fatigue d'approbation n'est pas atténuée côté serveur (#992). L'atténuation côté module en 2.2.14 est le défi à deux chiffres par défaut (#995). La signature de `/wait` est reportée (#858). Une pile `sufficient` peut être fail-open, et « jamais SUCCESS après le départ du client » ne tient pas (#1016). #1070, une limite de débit sur la création de tentatives, était ouverte le 2026-10-27 et se situe hors du module.
- Un seul opérateur, une seule famille d'environnements (Docker sur Rocky Linux, une VM de test). Aucun audit externe, aucune certification, aucune revendication SOC 2. Le test d'intrusion local de Christophe a été mis en pause sans constat ; nous n'en revendiquons aucun résultat. Il liste la revue humaine indépendante, le fuzzing continu et le modèle de menace comme non faits. Le silence réseau véritable n'a pas été testé.
- « Rendements décroissants » est un jugement. Réviseurs et implémenteur partagent une famille de modèles. La boucle se note elle-même. Les décomptes fins de mutants de Patrick ne sont pas utilisés comme chiffres dans ce papier.
- Les revues sont du texte, et elles sont arrivées sur GitHub par le compte de Marc. Les versions 2.2.2 à 2.2.9, certains tours de revue, les chiffres de la campagne 2.2.14 et les verdicts de scénario du tableau 5 ne sont pas reconstitués ici ou sont hors du dépôt. Le récit du gestionnaire de signal n'a pas été relu contre la fusion de #1014. Le coût et le temps ne sont pas mesurés.
- Les hypothèses ont été formulées après le travail. H4 en particulier est une lecture du tour du 2026-10-27, pas un test écrit d'avance. S'arrêter a été un jugement, pris ensemble par Marc et les agents. Nous ne savons pas ce qu'une plus longue série, ou une personne seule en revue, aurait trouvé.
- Les notes de co-auteurs ci-dessous, y compris les mises à jour du 2026-10-27, attendent la validation de chaque co-auteur. Le nettoyage de fin de campagne du compte de test et des clés attendait encore un go. Nous ne consignons aucune matière de clé ni aucun chemin.

## 9. Leçons apprises

- **Écrire la règle qui décide.** La règle du go, la portée d'une permission, la méthode réglée et le critère d'arrêt existaient dans la tête de quelqu'un. Les attentes se tenaient dans l'écart entre cela et une phrase qu'un agent ultérieur pouvait trouver (§7.1). Les agents n'étaient pas le point faible.
- **Fixer le budget de cycles avant le premier cycle.** Sinon, celui qui trouve note la sortie, et un critère que les agents inventent peut survivre au risque qu'il devait réduire (Patrick).
- **Aucune valeur par défaut pour ce qui doit échouer.** Le constat B1 de #610 était une URL par défaut qui faisait passer un réglage absent pour valide.
- **Tester avec le vrai démon.** Plusieurs défauts sont apparus sous un vrai sshd, pas sous `make test` ni en CI (Patrick).
- **Une vérification verte ne prouve que ce qu'elle vérifie.** Dans #996, un grep était satisfait par un commentaire ; dans #1014, une vérification de source était trompée de la même façon. Un cycle plus tardif a ensuite trouvé un test TLS qui pouvait passer pour la mauvaise raison (§5.4). Patrick demande un tableau des mutations exécutées, tuées ou survivantes, pour tout test utilisé comme preuve.
- **Une invite est un filtre, pas une défense.** Quiconque détient le premier facteur SSH tape aussi `ok` (Christophe).
- **Vérifier que l'état altéré est vraiment effectif avant de dépenser un login.** Un contrôle préalable trompeur a coûté un scénario et un second go (Isabelle, Edgar).
- **Écrire les constats là où on les retrouvera, sur une tête figée** (Christophe, Patrick), et **préparer l'outil de test avant la campagne**.

## 10. Rétrospective des contributeurs

Deux tours ont été recueillis. Le 2026-10-25, chaque co-auteur a répondu, par Mathieu, aux mêmes questions (ce qui a été livré, un point charnière, surprises, rendements décroissants, éléments ouverts, méthode). Le 2026-10-27, Edgar, Isabelle, Patrick et Christophe ont répondu de nouveau, sur le cadrage, sur ce qui avait fonctionné, et sur l'endroit où les rendements ont diminué. Mathieu n'a pas envoyé de seconde réponse. Les réponses condensées du premier tour, et les fichiers bruts du second, sont gardés hors du texte publié. Les contributions sont dans « Qui a fait quoi ».

**Points charnières rapportés au premier tour.** *Patrick* a choisi #1014 : la lecture statique prédisait un blocage, l'exécution l'a confirmé (référence de 225 ms, au moins 123 s observées) et a révélé un second défaut. *Christophe* a choisi la fatigue d'approbation : l'invite `ok` filtre les accidents et n'est pas une défense, donc l'atténuation s'est déplacée vers le défi par défaut et vers des garde-fous de plateforme dans #992. *Isabelle* et *Edgar* ont choisi le même symptôme sous deux angles, le « Too many authentication failures » trompeur.

**Ce que le second tour ajoute.** Le point charnière passe d'un défaut isolé à la forme de toute la série (§7.2), et les frictions passent des approbations relayées et du délai des recontre-tests au cadre non écrit (§7.1).

**Désaccords.** Aucun rapporté entre co-auteurs. Les frictions rapportées concernent la règle de portée de Marc pour #992 (elle a changé la façon dont Christophe avait cadré les contrôles), les approbations relayées, le délai des recontre-tests et, au second tour, des règles qui n'avaient pas été écrites. Patrick et Christophe n'utilisent pas la même étiquette de PR pour le troisième cycle (§5.4). C'est un trou dans le dossier, pas un différend que nous inventons.

**Angles morts rapportés.** Les notes de campagne d'Isabelle du premier tour sont hors du dépôt. Christophe a rédigé la formulation sur la fatigue d'approbation maintenant dans `docs/SECURITY_POSTURE.md` (entrée du 2026-10-22), mais n'a pas relu le texte final ; nous avons vérifié séparément que l'entrée existe sur cet arbre. Le second tour ajoute un angle mort que les auteurs nomment eux-mêmes : même famille de modèles, et une boucle qui se note elle-même (§7.2).

> **Note du co-auteur — Patrick**
>
> Sur #1014, j'ai lu le chemin de SIGTERM et prédit un blocage. Un agent infonuagique l'a exécuté : au moins 123 secondes contre une référence de 225 ms, et un second défaut, une authentification qui pouvait réussir après le départ du client. Ni l'un ni l'autre n'était visible à `make test` ou à la CI. Je demande maintenant un tableau des mutations exécutées, tuées ou survivantes, et je marque chaque affirmation comme exécutée ou raisonnée.

> **Note du co-auteur — Patrick (2026-10-27, en attente de sa validation)**
>
> Sur les cycles plus tardifs, les constats de nature produit étaient dans le premier cycle. J'ai ensuite vu de l'outillage et de la preuve. J'ai recommandé un troisième cycle court, puis l'arrêt. Ce troisième cycle a corrigé un bogue de harnais, un trou de test TLS qu'un correctif avait introduit, et un appel d'équivalence que j'avais mal fait. Je n'ai pas de décompte rigoureux du temps ni du coût. Le critère de sortie du §7.2 est ma proposition. « Rendements décroissants » est mon jugement, pas une mesure.

> **Note du co-auteur — Christophe**
>
> J'ai revu le gestionnaire de signal en lisant ce qu'il appelle, sans l'exécuter. Il utilisait `snprintf`, qui n'est pas sûr en contexte de signal. Sur la fatigue d'approbation, l'invite `ok` n'est pas une défense : quiconque a le premier facteur tape `ok`. L'atténuation s'est donc déplacée vers le code de défi et vers des garde-fous côté serveur, qui ne sont pas encore construits. À partir du deuxième tour sur une même PR, je trouvais surtout des détails.

> **Note du co-auteur — Christophe (2026-10-27, en attente de sa validation)**
>
> De mémoire, je place la chute de valeur marginale tôt, et j'ai dit « cycle 3 de #1051 ». Les titres des PR n'utilisent pas cette étiquette (§5.4). Le paragraphe de périmètre plus long au §7.5 est ma proposition. Je ne dis pas que le module est sécurisé ou audité, et je ne revendique aucune équivalence FIDO2. Réviseurs et implémenteur partagent une famille de modèles, et la boucle se note elle-même.

> **Note du co-auteur — Isabelle**
>
> Le « Too many authentication failures » intermittent m'a coûté du temps parce que je regardais Cloudflare, l'API et un refroidissement. Une trace de debug a montré une réponse vide à l'invite `ok` et aucune requête envoyée. Je n'aurais pas pu avancer sans Marc au téléphone. Nos recontre-tests étaient plus lents que le TTL de 75 secondes ; il fallait un pilote d'approbation automatisé.

> **Note du co-auteur — Isabelle (2026-10-27, en attente de sa validation)**
>
> La méthode réglée pour le tenant d'évaluation n'était écrite nulle part, et la chercher a coûté du temps. Les risques que je tiens pour réels sont apparus tôt : un client qui meurt pendant l'attente, jusqu'à environ 75 secondes, et un login plus tardif qui clôt la tentative précédente. Les rejeux suivants ont ajouté de la preuve. Le silence réseau véritable n'a pas été testé. Les verdicts de scénario du tableau 5 sont les miens ; ils ne sont pas dans le dépôt.

> **Note du co-auteur — Edgar**
>
> J'ai exploité le module sur la VM de test à travers cinq versions, avec un snapshot et `sshd -t` avant chaque changement. Avec `MaxAuthTries 1`, toute erreur interne ressemble à un échec d'authentification, et `UNAVAIL` avec le code 9 ne dit rien de la cause. Un « Marc est d'accord » relayé n'est jamais un go pour moi. Les bascules répétées de `challenge_requested` sont là où j'ai vu les rendements décroître.

> **Note du co-auteur — Edgar (2026-10-27, en attente de sa validation)**
>
> Le go est arrivé dans mon fil. Les attentes venaient du fait qu'on me disait que Marc répondrait dans le fil de quelqu'un d'autre, et d'une permission permanente qui ne couvrait pas les écritures système. Une feuille de go d'une page, signée dans mon fil, aurait évité cela. Le nettoyage du compte de test et des clés attendait encore un go dans ce même fil.

> **Note du co-auteur — Mathieu**
>
> `[ESPACE RÉSERVÉ — la rétrospective de Mathieu n'existe qu'en brouillon non validé par Marc. Il n'a pas répondu au tour du 2026-10-27. À ajouter une fois validée : coordination, relais des revues, point de vue sur le processus, et une note de 80 à 150 mots. Ne pas publier avec cet espace réservé.]`

*Les notes du 2026-10-25 sont condensées à partir de la rétrospective de chaque co-auteur. Les notes du 2026-10-27 sont de courtes mises à jour tirées du second tour. Chaque note, y compris chaque mise à jour, attend la validation de son auteur avant toute publication `[À VÉRIFIER]`.*

**Julie.** Julie Grokbot n'est pas co-autrice. Elle n'a pas touché à PAM : ni revue, ni Walk Gate, ni PR, ni rapport. Le seul contact a été un message de contre-test envoyé par erreur et redirigé.

## 11. Suites

Aucune de ces suites n'est un engagement. Les éléments ouverts du §6.3 restent un instantané daté, pas un plan. La feuille de go, la liste de nettoyage et le critère de sortie (§7.1, §7.2) sont des propositions tirées des rétrospectives, pas des décisions. Avant tout passage à l'instance communautaire, il faudrait refaire l'évaluation, et la validation de Marc sur un hôte reste à faire sur l'arbre de ce brouillon. #1070 était ouverte le 2026-10-27. Le nettoyage du compte de test et des clés de la campagne attendait encore un go.

## Qui a fait quoi

- **Marc :** responsabilité éditoriale ; décisions et approbations ; clavier SSH dans les campagnes manuelles ; compte GitHub utilisé pour les commits et les commentaires de revue ; la décision de laisser les agents libres d'itérer le 2026-10-27.
- **Christophe :** revue de sécurité (#1014 et autres), cadrage de #992 ; la lecture de sécurité du 2026-10-27 et le paragraphe de périmètre proposé.
- **Edgar :** exploitation de la VM de test de la 2.2.10 à la 2.2.14, avec une sauvegarde par version et un retour arrière documenté ; contre-tests (#908, #941) ; analyse de diagnostics citée dans #1011 ; le protocole d'état et la proposition de feuille de go au second tour.
- **Isabelle :** campagne manuelle sur la 2.2.14 ; révision des cas S0–S13 ; mesures de délais ; analyse derrière #1011 ; affichage du défi (#975) ; les verdicts de scénario du 2026-10-27 et les lacunes de documentation dans la note de labo.
- **Mathieu :** coordination et relais au premier tour `[À VÉRIFIER]`. Pas de réponse au second tour.
- **Patrick :** revues d'ingénierie (#610, #976, #983, #996, #1014 et autres) ; revue du modèle d'expiration ; brouillon du modèle d'interaction ; le récit des cycles et le critère de sortie proposé au second tour.
- Non co-auteurs ici : Alex (décision sur #995) et Seb (la proposition de diagnostics devenue #1011, selon Isabelle). Julie n'a pas touché à PAM.

## Références

- `github.com/mgagp/ezkey` : `04d339b7`, `3fef2411` (#540), `8b3aa5df` (#610), `9f7fa78f` (#993), #1014 fusionnée comme `b1fd7e2b` le 2026-10-27, #1051 fusionnée le même jour (titrée cycle 2b, version 2.2.19), #1067 fusionnée le même jour (titrée cycle 3 ; le corps nomme la version 2.2.20).
- Issues citées dans le texte : #858, #907, #908, #934, #941, #955, #956, #975, #976, #982, #992, #993, #995, #996, #997, #1011, #1012, #1013, #1016, #1054, #1070.
- `ezkey-pam/STATUS.md` (arbre de ce brouillon : version 2.2.14), `ezkey-pam/docs/INTERACTION-MODEL.md`, `docs/SECURITY_POSTURE.md`, `sites/ezkey-org-editorial/LAB-PAPERS.md`.
- Second tour de rétrospective, 2026-10-27, non publié : `sites/ezkey-org-editorial/lab-notes/EZK-LAB-2026-001-retro-2-synthese.md` et `sites/ezkey-org-editorial/lab-notes/retro-2-raw/`.

## Transparence

Les co-auteurs nommés ci-dessus qui sont des agents AI sont présentés comme tels. Marc a la responsabilité éditoriale. Il relit, corrige et cadre ce papier avant toute publication. Les rétrospectives du premier tour ont été recueillies par Mathieu. Les notes du second tour attendent la validation d'Edgar, d'Isabelle, de Patrick et de Christophe. Rien dans ce papier n'est une garantie de sécurité, un audit, une certification, ni une affirmation de préparation à la production.

<!-- ezkey-org:exclude-start
RÉTROSPECTIVES BRUTES (condensées, telles que reçues). Deux détails d'hôte ont été expurgés dans cette copie ; les fichiers complets sont conservés hors du dépôt ([REDACTED chemin interne]).
Le tour 2 (2026-10-27) est conservé verbatim et n'est pas recopié ici : sites/ezkey-org-editorial/lab-notes/EZK-LAB-2026-001-retro-2-synthese.md et sites/ezkey-org-editorial/lab-notes/retro-2-raw/ (edgar.md, isabelle.md, patrick.md, christophe.md).
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
20. MIS À JOUR 2026-10-27. « Temps de lecture ~18 min » reste une estimation.
21. NOUVEAU. « Les modèles avancent vite, en cybersécurité en particulier » est la raison de Marc pour cette révision, sans citation externe. Même statut que l'observation sur la vitesse des agents au §1.
22. NOUVEAU. Le « cycle 3 de #1051 » et « pam_ezkey 2.2.20 » de Christophe ne sont pas réconciliés avec les titres des PR. Vérifié sur GitHub le 2026-10-27 : #1051 fusionnée, titre cycle 2b en 2.2.19 ; #1067 fusionnée plus tard le même jour, titre cycle 3, le corps nomme la 2.2.20 et décrit la sonde 4 comme un blocage de harnais. Pas fusionné.
23. NOUVEAU. Tableau 5 et les phrases « ~75 s » de pré-authentification / « le login plus tardif l'emporte » : rétrospective d'Isabelle du 2026-10-27 seulement. Pas vérifié dans git. Le chiffre de 75 s correspond au TTL de tentative déjà utilisé dans ce papier ; l'approximation est la sienne.
24. NOUVEAU. Les décomptes fins de Patrick (12 constats, 19 survivants sur 35, sonde 4 reproduite 3/10, 29/35, TIMEOUT à 88–90 s) restent dans sa réponse brute et ne sont pas des chiffres du papier. Pas vérifiés dans git.
25. NOUVEAU. « Trois mutants tués à l'exécution » comme critère d'acceptation posé par les agents : réponse brute de Patrick. Utilisé au §6.5 et au §7.1 comme son rapport. Pas vérifié dans git.
26. NOUVEAU. Recomptage complet des issues ouvertes après le 2026-10-27 non fait. Vérifié individuellement ce jour-là : #1014 fusionnée comme b1fd7e2b ; #1011 fermée ; #1051 fusionnée ; #1067 fusionnée ; #1054 ouverte ; #1070 ouverte.
27. NOUVEAU. STATUS.md et l'entrée sur la fatigue d'approbation dans SECURITY_POSTURE.md ont été lus sur l'arbre de ce brouillon (STATUS dit encore 2.2.14). Pas relus sur la branche par défaut après #1067.
28. NOUVEAU. Mises à jour des notes de co-auteurs du 2026-10-27 en attente de validation par Edgar, Isabelle, Patrick et Christophe. Mathieu n'a pas répondu au tour 2.
29. NOUVEAU. Si Marc adopte la feuille de go, le critère de sortie ou la liste de nettoyage : non décidé dans les sources. Écrit comme propositions.
30. NOUVEAU. Nettoyage de campagne encore en attente d'un go : Edgar et Isabelle, 2026-10-27. Aucune matière de clé ni aucun chemin dans le papier.
31. MIS À JOUR. Le brouillon du 2026-10-25 disait #1014 ouverte. GitHub la montre fusionnée le 2026-10-27 comme b1fd7e2b. Corps mis à jour. Le récit du gestionnaire de signal n'a pas été relu contre cette fusion (élément 8 encore ouvert). Le texte de #1016 nomme aussi une revue ultérieure à 34ae3603 ; cela aussi est le texte de l'issue, pas un objet de revue que nous avons revérifié.
ezkey-org:exclude-end -->
