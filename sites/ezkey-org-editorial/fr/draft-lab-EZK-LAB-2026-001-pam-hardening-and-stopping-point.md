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
NOTES ÉDITORIALES — brouillon français, révision 4 (2026-10-28). Branche docs/lab-paper-pam-draft. Pas pour publication.

Révision 4 = annulation (commit de revert) de la passe de resserrement (b22009a0) approuvée par Marc, petits gains sûrs
réappliqués à la main, puis réécriture autour d'un seul fil conducteur : nous avons laissé des agents durcir un module PAM
expérimental ; le point faible était la clarté de nos propres décisions et de notre méthode plus que la capacité des agents ;
nous nous sommes arrêtés quand les rendements ont diminué, devant la progression rapide de l'IA en cybersécurité
(observation personnelle de Marc, sans source externe), ce qui explique aussi pourquoi les agents ont été laissés libres
d'itérer. Le récit de l'état du brouillon et de l'arbre a été sorti du corps et placé dans ce bloc.

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

Décisions conservées : phrase de sécurité « raisonnablement assurée dans le périmètre testé, sans être la fin de l'histoire »,
dite une fois dans le Résumé et une fois au §6.1 ; l'écart de sept. 2025 à sept. 2026 comme « une pause d'un an, sans trace
dans le dépôt » ; la rapidité des agents et la progression de l'IA en cybersécurité restent une observation personnelle de
Marc, sans citation externe ; le point d'arrêt est une collaboration entre Marc et les agents, jugée suffisante comme
maturité expérimentale et rien de plus ; pas de « sécurisé », « audité », de certification, ni d'équivalence Duo/FIDO2.
Ezkey est d'abord un projet de laboratoire. Les retraits de Christophe sont respectés.

Éléments d'état du brouillon gardés HORS du corps (ils vivent ici) :
- ezkey-pam/STATUS.md est en 2.2.14 sur l'arbre de ce brouillon ; les fusions plus tardives sont citées depuis GitHub, pas
  depuis cet arbre. Le §6.2 ne dit que ce que disait le fichier de statut à la lecture, plus un marqueur.
- #1014, ouverte dans le brouillon du 2026-10-25, a été fusionnée le 2026-10-27 (b1fd7e2b). Le récit du gestionnaire de
  signal (§3.2, annexe D) n'a pas été relu contre le texte fusionné (marqueur conservé).
- Écart de libellé #1051 / #1067 avec le « cycle 3 de #1051 » / 2.2.20 de Christophe : toujours non concilié ; un marqueur au §3.3.
- Les décomptes fins de Patrick (12 constats, 19/35, 3/10, 29/35, 88–90 s) restent dans sa réponse brute et hors du corps.
- Mathieu n'est pas intégré : sa rétrospective n'existe qu'en brouillon non validé par Marc ; il n'a pas répondu au tour 2.
  La note d'espace réservé reste au §7.

Choix de structure de la révision 4 :
- Renumérotation des tableaux. Corps : tableau 1 (contexte de la plateforme), tableau 2 (autonomie, l'ancien tableau 3).
  Annexes : A1 (versions correctives, l'ancien tableau 2), A2 (gains du 2026-10-27, l'ancien tableau 4), A3 (verdicts
  d'Isabelle, l'ancien tableau 5).
- Les annexes sont de simples sections titrées, pas des éléments <details> : le papier est d'abord du texte, et le rendu n'a
  pas de composant repliable (le CSS n'est pas touché). Marc peut demander <details> plus tard.
- Fusions de marqueurs (26 marqueurs en ligne à feda616b, 24 maintenant) : les marqueurs STATUS.md (plusieurs endroits) sont
  devenus un seul au §6.2 ; le marqueur de la chaîne 2.2.20 et celui du libellé de cycle sont devenus un seul au §3.3. Mêmes
  questions ouvertes, moins de doublons. Rien n'a été résolu.
- Restauré après la passe de resserrement : lignes 1 et 7 du tableau 2 telles quelles (déclarations de Marc du 2026-10-25) ;
  la formulation de sécurité ; le point d'arrêt collaboratif dans le Résumé.
- Notes de co-auteurs conservées presque telles quelles ; renvois mis à jour (ancien §7.1 -> §4.1, §7.2 -> §4.2, §5.4 -> §3.3,
  §7.5 / critère de sortie -> annexe E).
- Le temps de lecture de ~12 min est une estimation pour le texte principal (environ 6 200 mots), annexes exclues (même ratio mots/minute qu'avant).
- Révision 5 (réduction des redondances, même fil conducteur, aucune décision changée) : miroir de la version EN. Points clés et §1 raccourcis là où ils répétaient le §4 ; paragraphe « premières frictions » du §3.2 supprimé ; paragraphe #1014 du §3.2 raccourci (détail à l'annexe D) ; §4.2 ne répète plus longuement l'observation de Marc et retire la phrase sur les anciens rapports de re-revues ; limites du §6.3 sans redites ; points charnières et notes du §7 sans reprise du corps ; introductions des annexes C et D raccourcies. Aucun marqueur retiré (24), aucun SHA ni numéro d'issue retiré, phrase de sécurité exactement deux fois, lignes 1 et 7 du tableau 2 inchangées.

Marqueurs : [À VÉRIFIER] / [DONNÉE MANQUANTE], listés en fin de fichier. Aucun fait propre au FR.
ezkey-org:exclude-end -->

# Ezkey PAM : durcir un module SSH expérimental avec des agents, et décider où s'arrêter

**Auteurs :** Marc Gagnon (responsabilité éditoriale) ; Christophe Grokbot (revue de sécurité) ; Edgar Grokbot (déploiement et périphérie) ; Isabelle Grokbot (QA exploratoire) ; Mathieu Grokbot (coordination) `[À VÉRIFIER : preuve et note validée de Mathieu en attente]` ; Patrick Grokbot (revue d'ingénierie)
**Affiliation :** Ezkey Lab
**Identifiant :** `EZK-LAB-2026-001`
**Statut :** draft
**Date :** 2026-10-28
**Mots-clés :** PAM, SSH, MFA, fail-closed, développement assisté par agents, autonomie des agents, cadrage humain, rendements décroissants, expérimental
**Temps de lecture :** ~12 min

## Points clés

- Le point faible était la clarté de nos propres décisions et de notre méthode, pas la capacité des agents. Dans le tour du 2026-10-27, chaque friction que décrivent les co-auteurs vient de quelque chose qu'une personne n'avait pas rendu explicite : une règle du go, la portée d'une permission, une méthode gardée dans la conversation, des consignes ambiguës, aucun critère d'arrêt fixé d'avance (§4.1).
- Là où le cadre existait, les agents ont porté beaucoup du travail : un tenant de test ouvert par l'API publique, un protocole d'état avec retour arrière vérifié, une preuve par exécution sur un agent infonuagique jetable (§4.1, annexe B).
- Marc et les agents se sont arrêtés ensemble quand les rendements ont diminué : la première passe a trouvé les défauts de niveau produit, et les cycles suivants ont ajouté de l'outillage et de la preuve. « Rendements décroissants » est un jugement, pas une mesure. Ce jugement a été posé alors que, selon l'observation personnelle de Marc (sans source extérieure), la capacité de l'IA en cybersécurité progresse vite, ce qui explique pourquoi les agents ont été laissés libres d'itérer (§4.2, §6).
- Un prototype d'environ 900 lignes, commité le 2025-09-03 et validé à la main, n'a laissé aucune preuve automatisée. Le dépôt montre ensuite une pause d'un an, sans trace de travail fonctionnel sur PAM, jusqu'à la reprise le 2026-09-15 (§3.1).
- Le résultat est jugé suffisant comme maturité expérimentale, et rien de plus. Le module n'est pas un agent exploitable sur un hôte, et passer à l'instance communautaire exigerait de refaire l'évaluation (§6).

## Résumé

Ce papier rend compte d'une expérience sur `ezkey-pam`, un module PAM Linux qui ajoute l'approbation par appareil Ezkey aux connexions SSH. Ezkey est un projet de laboratoire. Un prototype a été commité en septembre 2025. Après une pause d'un an, sans trace dans le dépôt de travail fonctionnel sur PAM, le module a été modernisé en septembre 2026, puis durci tout au long d'octobre 2026 par des agents qui travaillaient avec Marc. Nous avons laissé les agents libres d'itérer, autant que nécessaire, pour voir où les rendements diminuent plutôt que de le supposer. Le point faible n'était pas la capacité des agents, mais la clarté de nos propres décisions et de notre méthode : des règles, des permissions et un critère d'arrêt qui n'avaient pas été écrits. Marc et les agents se sont arrêtés ensemble quand les rendements ont diminué, à un moment où Marc juge que la capacité de l'IA en cybersécurité progresse vite. La sécurité est raisonnablement assurée dans le périmètre testé, sans être la fin de l'histoire. Le module n'a qu'une maturité expérimentale. Nous ne revendiquons ni préparation à la production, ni audit, ni certification, ni équivalence avec Duo ou FIDO2.

## 1. Pourquoi nous l'avons fait, et ce que nous supposions

Marc a d'abord vu une connexion SSH protégée par Duo au travail, ce qui était nouveau pour lui `[À VÉRIFIER avec Marc : quand]`. La question qui a suivi : Ezkey pouvait-il jouer un rôle semblable pour SSH, avec les morceaux qu'il avait déjà ? Ce n'est pas une revendication d'équivalence : rien dans ce papier n'assimile Ezkey à Duo, à WebAuthn ou à FIDO2.

Ezkey est d'abord un projet de laboratoire, promis à personne. La niche réaliste que nous voyons est la protection passwordless, à cryptographie forte, d'applications internes pour un petit personnel interne. Aller plus loin exigerait d'abord une discipline de type SOC 2 : un prérequis, pas un plan.

Deux autres raisons ont façonné l'expérience. La première est une observation personnelle de Marc, sans source extérieure ni mesure : les agents autonomes lui semblent devenus visiblement plus rapides à trouver et à corriger des vulnérabilités, et la capacité de l'IA en cybersécurité progresse vite `[À VÉRIFIER : formulation de cette raison, donnée pour la révision du 2026-10-27]`. C'est pourquoi les agents ont été laissés libres de dire ce qu'ils voyaient et d'itérer, pour voir où un humain et les agents convergent vers des rendements décroissants. La seconde était de voir quelle part du travail des agents pouvaient porter avec peu d'intervention humaine (§5).

Nous avons formulé quatre hypothèses **après coup** ; aucune n'a été écrite avant le début du travail. Elles rendent la lecture explicite ; elles ne signifient pas que l'expérience a été conçue autour d'elles.

- **H1.** Les morceaux existants (authentification, API, appareil) suffisaient pour construire un flux complet d'approbation SSH.
- **H2.** Une équipe d'IA en revue croisée peut amener un petit module C au point où les constats de revue deviennent mineurs.
- **H3.** Passé un certain point, un tour de revue de plus coûte plus cher que le risque de laboratoire qu'il retire, et ce point peut être repéré.
- **H4.** Dans la campagne des agents, les attentes et la confusion viendraient du cadrage humain, pas d'une limite des agents.

Le prototype de 2025 a mis H1 à l'épreuve. H2 et H3 n'ont pas été testées de façon contrôlée, et le point de H3 est plus facile à décrire après coup qu'à connaître pendant le travail (Patrick). H4 est la lecture que soutient le tour du 2026-10-27 (§4.1), pas quelque chose d'écrit d'avance.

## 2. Ce qui a été construit

`ezkey-pam` est un module C pour Linux PAM, la couche qui gère l'authentification à la connexion, utilisée par SSH en mode keyboard-interactive. Il demande à l'utilisateur de confirmer avec `ok`, crée une tentative d'authentification par l'API d'intégration, affiche au besoin un code à deux chiffres à comparer, puis attend que l'utilisateur approuve ou refuse sur un appareil (le Demo Device ou l'application Android). Il échoue en mode fermé : une API ou une configuration inutilisable refuse la connexion, et il n'y a volontairement pas de mode fail-safe. Une réserve documentée demeure : une pile PAM `sufficient` peut rendre le module fail-open. Le travail s'est fait dans un conteneur Rocky Linux et, à partir d'octobre 2026, sur une VM de test dédiée avec un vrai sshd, où plusieurs constats sont apparus que rien d'autre ne montrait. Nous ne donnons aucun nom d'hôte, aucune adresse ni chemin interne.

| Élément | Preuve | Date |
| --- | --- | --- |
| Demo Device | `e532ce95` | 2025-08-10 |
| Point d'accès `/wait` | `796dd455` | 2025-08-26 |
| **Premier commit PAM** | `04d339b7` | **2025-09-03** |
| Admin UI | `71e27831` | 2026-03-06 |

*Tableau 1. Contexte de la plateforme autour du premier commit PAM (dates tirées de git).*

Marc se souvient qu'à l'été 2025 le protocole était plus simple : un simulateur au début, pas d'Admin UI, une base de données plus simple. Le simulateur est visible dans l'historique (`988ef115`, 2025-08-19) ; la remarque sur la base de données n'est pas vérifiée `[À VÉRIFIER : schéma de la base de données à l'été 2025]`. Certains des premiers commits ont été signés par un compte d'agent automatisé, pas par Marc `[À VÉRIFIER : auteur de chaque commit du tableau 1]`.

**Quatre termes.** Un *mutant* est une copie volontairement abîmée du code, qui sert à vérifier qu'un test remarque la casse. Un *TTL* (durée de vie) est le temps pendant lequel une demande d'approbation reste valide : 75 secondes ici. Une *tête figée* est le tout dernier commit d'une PR, gardé immobile pendant la revue, pour que la revue et les correctifs ne se courent pas après. Un *go* est le mot explicite de Marc qu'une étape peut avancer ; le verdict d'un réviseur dans une PR s'écrit GO ou CHANGES.

**Rôles.** Marc décidait et approuvait, et tenait le clavier SSH pendant les campagnes manuelles. Patrick (ingénierie) et Christophe (sécurité) faisaient les revues ; Edgar exploitait la VM de test ; Isabelle menait la QA exploratoire ; Mathieu coordonnait et relayait pendant le durcissement d'octobre, et n'est pas une source du tour du 2026-10-27 `[À VÉRIFIER : Mathieu]`.

**Comment les revues arrivent sur GitHub.** Les revues de Patrick et de Christophe arrivent dans le dépôt par le **compte GitHub de Marc** : les commentaires sont publiés depuis `mgagp`, et le texte de chacun indique quel co-auteur l'a rédigé. Une issue (#1016) dit que les revues ont été relayées par Mathieu, et les commits de fusion (squash) portent le nom de Marc. Ici, l'attribution repose sur le texte du commentaire ou sur la rétrospective du co-auteur, pas sur l'identité GitHub `[À VÉRIFIER : que les commentaires reflètent le texte original des revues]`.

Nous n'avons pas relancé les tests du module pour ce papier. Les chiffres viennent du dépôt, des PR ou des rétrospectives, et ces dernières sont marquées.

## 3. Le travail en trois temps

### 3.1 Premier temps : un prototype, puis un an de silence

Le premier commit PAM, `04d339b7` (2025-09-03, 9 fichiers, 919 lignes ajoutées), se décrit comme un module de base codé en dur, sous Rocky Linux dans Docker. Selon Marc, il a aussi été exécuté sur une VM et validé à la main seulement `[À VÉRIFIER : test sur VM ; aucune preuve automatisée dans git]`.

Puis une pause d'un an, sans trace dans le dépôt de travail fonctionnel sur PAM. Le dossier n'a reçu que quatre commits mécaniques (un changement de port, un passage à Rocky Linux 10, des permissions de fichiers, de l'hygiène documentaire ; annexe A). Nous n'affirmons rien sur cette période au-delà de ce que montre git.

### 3.2 Deuxième temps : modernisation et durcissement d'octobre

La PR #540 (2026-09-15) a modernisé le module. La PR #610 (jalon « M1 »), ouverte le 2026-09-22 et fusionnée le 2026-10-17 (38 fichiers, +3767/−332), a ajouté l'observabilité, une configuration fail-closed, le défi à deux chiffres et un budget de temps. La première revue de Patrick a rendu **CHANGES** avec un constat bloquant : une URL d'API absente n'échouait pas en mode fermé, parce que le code la préremplissait avec une valeur locale par défaut, si bien que des identifiants seraient partis en clair vers un port local où un autre utilisateur local pouvait répondre `ACCEPTED`. La revue de Christophe n'a rendu que des constats mineurs. Patrick a revu deux fois et rendu **GO** le 2026-10-17, une fois la CI verte.

Cinq versions correctives ont suivi en trois jours (2.2.10 à 2.2.14, du 2026-10-20 au 2026-10-22), chacune revue sur une tête figée et chacune exposée par un recontre-test ou une revue (annexe A, tableau A1). La version 2.2.15, fusionnée le 2026-10-27, a ajouté des diagnostics d'échec plus riches et la gestion des signaux (#1014).

Le résultat le plus utile de cette période a été un symptôme trompeur. Isabelle a mené une campagne manuelle sur la 2.2.14 (#996) sur la VM de test, avec Marc au clavier SSH et sur le Demo Device, consignée dans un outil de gestion de tests extérieur au dépôt : 30 cas, 29 réussis et 1 échoué (S6-02, annulation et audit, aujourd'hui #1012). Marc a consigné le jalon comme « atteint avec réserves » (S1D-06, S11-01) `[À VÉRIFIER : les chiffres viennent de la rétrospective d'Isabelle du 2026-10-25 ; l'outil et les notes sont hors du dépôt]`. Avec `MaxAuthTries 1`, presque toute erreur interne apparaît comme « Too many authentication failures ». Une trace de débogage du 2026-10-24 a montré UNAVAIL après 741 ms et aucune requête envoyée : l'invite `ok` avait reçu une réponse vide. Edgar a aussi trouvé que c'est le `LoginGraceTime` de sshd (120 s), et non le délai de 75 s du module, qui borne une vraie tentative. Cela a mené à #1011, #1014 et #1016.

Les constats devenaient de plus en plus fins. Sur #1014, la lecture de Patrick prédisait que le module pouvait se bloquer après un signal de terminaison ; un agent infonuagique l'a confirmé (au moins 123 secondes contre 225 ms de référence) et a révélé un deuxième défaut, une authentification qui pouvait réussir après le départ du client ; Christophe a trouvé un appel non sûr dans le gestionnaire de signal. Les deux réviseurs ont rendu GO sur la tête corrigée, ce que nous savons par le texte de #1016 et non par un objet de revue GitHub `[À VÉRIFIER]` (détail à l'annexe D). Nous n'avons pas lu toutes les autres revues (#897, #908, #956, #976, #983, #996) et ne donnons aucun compte de constats par tour `[À VÉRIFIER : tours et gravité]`.

#992 (fatigue d'approbation : limite de demandes en attente, débit par enrôlement, délai de refroidissement, alerte sur refus) a été cadré, puis réécrit en termes génériques de plateforme sur instruction de Marc du 2026-10-22. Un commit de documentation (#993) énonce clairement la limite ; #992 elle-même n'est pas implémentée. Le point de Christophe : l'invite `ok` est un filtre, pas une défense, puisque quiconque détient le premier facteur SSH tape aussi `ok`.

### 3.3 Troisième temps : le tour de convergence

Le 2026-10-25, Marc et les agents ont convenu de traiter le résultat comme suffisant pour une maturité expérimentale. Cela n'a pas mis fin au travail. Marc a laissé les agents libres de continuer, pour la raison donnée au §1, afin que le point de rendements décroissants soit vu plutôt que supposé. Ce qui suit vient des rétrospectives du 2026-10-27 (une note de laboratoire et quatre réponses brutes, citées dans les références).

Selon Patrick, le cycle 1 est la seule passe qui a trouvé quelque chose de nature produit : aucun test ne tuait les mutants de la décision d'authentification, et la vérification TLS n'était gardée par aucun test. Après cela, le travail a été de l'outillage et de la preuve. Au cycle 2, il n'a plus vu de défaut de comportement et a recommandé un court troisième cycle, puis un arrêt. Le cycle 3 a corrigé un bogue du banc de test (la sonde 4 était un blocage sur une ouverture bloquante, pas un défaut du produit), un trou qu'un correctif précédent avait ouvert dans un test TLS, et une erreur d'équivalence de sa part : un mutant qu'il avait jugé équivalent ne l'était pas. Puis sont venus des détails, puis une récursion : un audit des tests, puis du script qui audite les tests, avec un faible rendement au deuxième niveau. Nous n'imprimons pas ses comptes de mutants plus fins ; ils restent dans sa réponse brute `[À VÉRIFIER : ces comptes]`.

Ce que l'itération a ajouté au-delà d'une seule passe, selon lui : une mesure à la place d'un soupçon, la détection d'un défaut qu'un correctif avait introduit, et la correction d'une erreur de réviseur. Qu'une passe, plus une vérification par exécution de chaque correctif, aurait attrapé presque tout est son jugement, pas un compte.

Sur GitHub, #1067 (fusionnée le 2026-10-27) est titrée comme suites du cycle 3 après #1051, et sa description nomme la version 2.2.20. #1051, fusionnée plus tôt le même jour, est titrée comme suites jusqu'au cycle 2b, à la version 2.2.19. Christophe, écrivant de mémoire, place la baisse de valeur marginale autour du « cycle 3 de #1051 » et nomme aussi la version 2.2.20. Nous ne traitons pas ces libellés comme le même objet `[À VÉRIFIER : libellé du cycle et 2.2.20]`.

Côté exploitation, Isabelle rapporte que les risques qu'elle tient pour réels ont été trouvés tôt : pendant qu'une connexion attend, le module ne voit pas le client mourir, si bien qu'un processus de préauthentification peut durer jusqu'à environ 75 secondes, borné par le TTL, et une connexion plus tardive ferme la tentative précédente. Des rejeux depuis un second système d'exploitation client ont ajouté de la preuve, pas un nouveau risque (verdicts à l'annexe C). Le vrai silence réseau n'a pas été testé, faute de go. Le signal ouvert que Christophe nomme encore est une limite de débit sur la création de tentatives d'authentification (#1070, ouverte le 2026-10-27, hors du module, non mesurée).

## 4. Ce que nous avons appris

### 4.1 Le point faible était notre propre clarté, pas les agents

C'est la conclusion principale du papier. C'est la lecture de Marc, et les rapports des co-auteurs la soutiennent : dans chaque friction que décrit le tour du 2026-10-27, les agents attendaient quelque chose qu'une personne n'avait pas rendu explicite, et cette personne était d'abord Marc. Les agents appliquaient les règles qu'ils avaient et s'arrêtaient là où il en manquait une. Cinq formes sont apparues.

1. **La règle du go était connue et n'était pas écrite.** « Seul Marc, dans le fil de l'agent qui exécute, compte comme un go. » Edgar et Isabelle disent tous deux que la règle elle-même n'était pas le problème. L'attente venait de messages, envoyés par des agents, disant que Marc répondrait dans un autre fil.
2. **La portée d'une permission permanente n'était pas claire.** Un go permanent pour le module PAM ne disait pas s'il couvrait les écritures système : un changement de pare-feu, des fichiers de configuration, des clés. Chaque écriture demandait son propre go (Edgar).
3. **Une méthode réglée n'existait que dans la conversation.** La façon d'ouvrir un tenant d'évaluation par l'API publique n'était écrite nulle part, et du temps a été perdu à la chercher (Isabelle).
4. **Les consignes étaient ambiguës.** Traiter l'API comme arrêtée, alors qu'elle est externe, a mené à un changement de résolution de noms vérifié pour une seule famille d'adresses : le test ne mesurait rien et a demandé un deuxième go. Une heure d'horloge a été présentée comme la fin de la campagne alors que la vraie contrainte était l'expiration d'une clé et d'une session. Des mots comme « module chargé », « authentification non invoquée » et « deuxième facteur » servaient à des faits différents (Edgar, Isabelle).
5. **Aucun critère d'arrêt ni budget de cycles n'avait été fixé d'avance.** La convergence a été remarquée après coup, et « rien de nouveau, mesurable et observable » est jugé par la personne qui trouve la chose. Un critère que les agents se sont donné eux-mêmes, trois mutants tués à l'exécution, n'a pas pu être atteint pour une vérification TLS côté hôte, et une partie du cycle suivant a servi ce critère plutôt qu'une réduction du risque (Patrick, Christophe).

Les agents n'étaient pas le point faible, car là où le cadre existait, ils ont porté beaucoup. Les règles qui ont aidé, dans la liste de Christophe (détail à l'annexe B) : rien dirigé contre l'instance communautaire publique, aucun commit ni push issu de la campagne, aucun secret affiché, preuve par exécution sur un agent infonuagique jetable, constats classés en corriger, documenter ou hors périmètre. Le 2026-10-27, les agents ont planifié, exécuté et rétabli à l'intérieur de bornes qui avaient été nommées, et là où une borne n'avait pas été nommée, ils ont attendu. Nous n'avons pas de mesure de la part du travail que cela représente `[DONNÉE MANQUANTE]`.

### 4.2 Rendements décroissants, et un domaine qui bouge vite

Nous nous sommes arrêtés quand les rendements ont diminué. La forme de la suite est reconnaissable : d'abord des constats de niveau produit, puis la preuve, puis des détails, puis l'outillage qui audite l'outillage. Cette suite est notre interprétation ; nous n'avons pas testé les liens de cause à effet.

L'observation personnelle de Marc, sans source extérieure ni mesure, est que la capacité de l'IA en cybersécurité progresse vite. Devant ce rythme, nous n'avons pas fixé d'avance jusqu'où les agents devaient aller : nous voulions voir où ils convergent, et nous ne voulions pas continuer au-delà du point où un cycle de plus achetait de l'outillage et de la preuve, mais aucun nouveau défaut.

La réserve doit rester à côté de la suite. « Rendements décroissants » est un jugement, pas une mesure, et ni Patrick ni Christophe ne donnent de compte de temps ou de coût. Réviseurs et implémenteur sont de la même famille de modèles : l'accord peut venir d'angles morts partagés et pas seulement d'une liste de défauts épuisée. La boucle se note elle-même : l'implémenteur écrit les mutants et les tests, et un réviseur de la même famille les lit. Patrick s'est trompé une fois sur un mutant qu'il disait équivalent, ce qui explique pourquoi un deuxième regard n'est pas facultatif sur un mutant de sécurité. Une revue humaine indépendante, un fuzzing continu et un modèle de menace d'une page mettraient ce jugement à l'épreuve ; Christophe les liste tous trois comme non faits. La règle proposée par Patrick pour un dernier cycle n'était pas celle de cette campagne (annexe E).

Côté budget, temps et valeur, git montre cinq versions correctives en quelques jours et deux PR fusionnées de plus le 2026-10-27 (#1051, #1067). Nous n'avons aucun chiffre d'argent, de calcul ou de temps humain, ni pour une personne qui travaillerait seule `[DONNÉE MANQUANTE]`. Notre synthèse reste qualitative : plus de revue, plus souvent, à un rythme que Marc pouvait suivre, à un coût que nous n'avons pas mesuré.

### 4.3 Ce que nous changerions la prochaine fois

Ce sont des propositions tirées des rétrospectives. Aucune n'est adoptée `[À VÉRIFIER : si l'une d'elles est adoptée]`.

- Écrire la règle du go en une phrase dans chaque consigne : seul Marc, dans le fil de l'agent qui exécute, donne un go, et aucun agent n'agit sur le rapport d'un autre agent.
- Dire ce qu'une permission permanente couvre. Pour les écritures système, utiliser une fiche de go d'une page signée dans le fil de l'exécutant (écriture exacte, fichiers, durée maximale, retour arrière, vérification des deux familles d'adresses, exclusions) et une liste de nettoyage préapprouvée.
- Écrire la méthode réglée avant la campagne, là où un lecteur ultérieur la trouvera : comment ouvrir le tenant d'évaluation, combien de temps vit une clé d'intégration et qui la retire.
- Fixer le budget de cycles et la règle de sortie avant le premier cycle, choisis par un humain et non notés par les agents.
- Vérifier en lecture seule qu'un état modifié est bien en vigueur avant de dépenser une connexion, et écrire les constats là où ils vivent, sur une tête figée.

## 5. Quelle autonomie nous visions, et où elle s'est arrêtée

Une partie de l'expérience visait à laisser les agents faire plus du travail avec moins d'intervention humaine. Le tableau 2 couvre le durcissement d'octobre, jusqu'au 2026-10-25 ; le tour du 2026-10-27 est au §4.1 et à l'annexe B. Les sources sont les rétrospectives des co-auteurs. Ce sont des observations, pas une mesure de l'autonomie.

| Visée | Limite observée | Source |
| --- | --- | --- |
| Tests menés sans le téléphone d'une personne : le Demo Device d'Isabelle pour presque tous les tests ; le Pixel de Marc seulement pour quelques tests exploratoires à lui, en validation préliminaire (déclaration de Marc) | La campagne sur la 2.2.14 a quand même eu besoin de Marc au clavier SSH et sur le Demo Device ; Isabelle dit qu'elle n'aurait pas pu avancer sans Marc au téléphone | Marc ; Isabelle |
| Revues d'une tête figée (un SHA, un lot de correctifs par revue) | Les re-revues après environ la deuxième tête ont surtout apporté des points mineurs | Patrick, Christophe |
| Tests rejoués par les agents : recontre-tests (#908), sondes de signaux contre un vrai sshd, un agent infonuagique qui exécute des prédictions (#1014) | Patrick n'avait ni Docker ni Java sur sa machine, donc chaque preuve passait par un agent infonuagique, lentement ; certaines gardes de CI ne prouvaient rien (#996, #1014) | Patrick, Edgar |
| GO bornés : une permission permanente sur la VM de test (instantané, `sshd -t`, rapport, un robot à la fois) | Un « Marc est d'accord » relayé n'est pas un GO ; le chemin de maintenance de la VM est resté hors MFA (principe dans #955) | Edgar, Isabelle |
| Constats écrits là où ils vivent (PR ou issue) | Des messages qui ne réveillent pas un agent (d'où des accusés de lecture sur les demandes bloquantes) ; un ensemble de constats de #961 perdu dans le clavardage `[À VÉRIFIER]` | Christophe |
| Recontre-tests sans personne dans la boucle | L'aller-retour humain (environ 77 s) dépassait le TTL de 75 s, donc certains essais étaient invalides ; un pilote d'approbation automatisée manquait | Isabelle |
| Agents qui composent et relaient textes et revues | Rien n'est publié sans Marc, ici et sur ezkey.org en général : il donne la matière et l'orientation, les agents composent, il révise et cadre, avec relecture et aperçu avant publication. Les revues arrivent sur GitHub par son compte, relayées par Mathieu (#1016) | Marc (déclaration) ; #1016 |

*Tableau 2. Autonomie visée et limites observées, jusqu'au 2026-10-25.*

## 6. Où nous nous sommes arrêtés, et ce qui reste

### 6.1 Le point d'arrêt

L'arrêt après le troisième temps est une décision du même genre que la lecture du 2026-10-25 : une collaboration entre Marc et les agents, un jugement que la maturité expérimentale est suffisante, et non une affirmation de quoi que ce soit de plus. Nos raisons, et pas davantage : les cycles suivants n'ont pas ajouté de défaut de sécurité après le premier d'entre eux, et le résidu est nommé (une limite du banc de test, une vérification TLS côté hôte que les tests à l'exécution ne tuent pas seuls, #1070, le silence réseau non testé) ; les risques résiduels connus sont documentés, pas cachés ; et continuer a un coût, sans preuve que cela changerait le risque au niveau du laboratoire. Nous n'avons pas non plus de preuve que la suite soit terminée (§4.2).

La sécurité est raisonnablement assurée dans le périmètre testé, sans être la fin de l'histoire. Nous ne disons pas « sécurisé » ni « audité », et nous ne revendiquons aucune certification ni aucune équivalence avec WebAuthn, FIDO2 ou Duo. Christophe propose un paragraphe de périmètre plus long ; l'annexe E le cite comme sa proposition, que nous n'adoptons pas, parce que la version et le libellé de cycle qu'il contient sont ceux que le §3.3 ne concilie pas.

### 6.2 Ce que cela ne veut pas dire

Cela ne veut **pas** dire que le module est exploitable sur un hôte. Pour autant que nous l'avons lu, son fichier de statut dit qu'il n'est pas un agent MFA exploitable sur un vrai hôte, que la bascule de sshd et les volets TLS et dogfood ne sont pas faits, et que le statut expérimental tient en attendant la validation d'hôte par Marc. Le document de posture de sécurité range PAM parmi les surfaces de laboratoire non déployées, et traite SOC 2 comme un vocabulaire de référence, pas comme une cible de certification. Nous n'avons pas vérifié ce que dit le fichier de statut sur la branche par défaut après #1067 `[À VÉRIFIER : fichier de statut après #1067]`. Le module n'a pas été publié. Passer à l'instance communautaire (#955) exigerait de refaire l'évaluation.

### 6.3 Ce qui reste ouvert

Onze issues étaient ouvertes le 2026-10-25 (vérifié sur GitHub ce jour-là), plus la PR #1014. Regroupées : sécurité de la plateforme (#992 politique de demandes en attente et délai de refroidissement, #858 signature de la réponse `/wait`) ; interaction (#982, #997) ; introduction sur l'instance communautaire (#955) ; tests et CI (#979, #980, #1013) ; annulation, diagnostics et suites (#1011, #1012, #1016). Au 2026-10-27, #1014 était fusionnée et #1011 fermée ; #1051 et #1067 avaient été fusionnées ; #1054 et #1070 étaient ouvertes. Nous n'avons pas recompté l'ensemble `[À VÉRIFIER : issues ouvertes après le 2026-10-27]`. Rien de cela n'est un engagement ni un plan.

Les limites de ce que nous affirmons se rangent en trois groupes.

- **Risques résiduels, documentés.** La fatigue d'approbation n'est pas atténuée côté serveur (#992) ; l'atténuation côté module est le défi à deux chiffres par défaut (#995).  « Jamais SUCCESS après le départ du client » ne tient pas : le succès reste possible si un signal de terminaison est absorbé pendant l'attente après l'arrivée de l'approbation, ce que #1016 dit n'être pas une élévation.
- **Portée de la preuve.** Un seul opérateur, une seule famille d'environnements (Docker sur Rocky Linux, une VM de test). Aucun audit externe, aucune certification, aucune revendication SOC 2. Le test d'intrusion local de Christophe a été mis en pause sans constats, et nous n'en revendiquons aucun résultat. La revue humaine indépendante, le fuzzing continu et un modèle de menace ne sont pas faits.
- **Qualité du dossier.** Les revues sont du texte, et elles sont arrivées sur GitHub par le compte de Marc. Les versions 2.2.2 à 2.2.9, certains tours de revue, les chiffres de la campagne 2.2.14 et les verdicts de l'annexe C ne sont pas reconstitués ou se trouvent hors du dépôt. Le coût et le temps ne sont pas mesurés. Nous ne savons pas ce qu'une suite plus longue, ou une personne qui réviserait seule, aurait trouvé. Les notes des co-auteurs attendent leur validation, et le nettoyage du compte et des clés de test attendait encore un go.

## 7. Rétrospective des co-auteurs

Deux tours ont été recueillis. Le 2026-10-25, chaque co-auteur a répondu, par Mathieu, aux mêmes questions (ce qui a abouti, un point charnière, les surprises, les rendements décroissants, les éléments ouverts, la méthode). Le 2026-10-27, Edgar, Isabelle, Patrick et Christophe ont répondu de nouveau, sur le cadrage, sur ce qui avait marché et sur l'endroit où les rendements ont diminué. Mathieu n'a pas envoyé de seconde réponse. Les fichiers bruts restent hors du texte publié.

**Points charnières.** Au premier tour, *Patrick* a choisi #1014, *Christophe* la fatigue d'approbation (#992), et *Isabelle* et *Edgar* le même symptôme sous deux angles, le trompeur « Too many authentication failures » (§3.2). Le second tour déplace le point charnière d'un défaut unique à la forme de toute la suite (§4.2), et les frictions, des approbations relayées et du moment des recontre-tests vers le cadre non écrit (§4.1).

**Désaccords.** Aucun rapporté entre co-auteurs. Les frictions rapportées concernent la règle de portée de Marc pour #992 (elle a changé la façon dont Christophe avait cadré les contrôles), les approbations relayées, le moment des recontre-tests et, au second tour, des règles qui n'avaient pas été écrites. Patrick et Christophe n'emploient pas le même libellé de PR pour le troisième cycle (§3.3). C'est un trou dans le dossier, pas un différend que nous inventons.

**Angles morts.** Les notes de campagne d'Isabelle du premier tour sont hors du dépôt. Christophe a rédigé la formulation sur la fatigue d'approbation qui figure maintenant dans le document de posture de sécurité (entrée du 2026-10-22) mais n'a pas relu le texte final ; nous avons vérifié séparément que l'entrée existe. Le second tour ajoute un angle mort que les auteurs nomment eux-mêmes : même famille de modèles, et une boucle qui se note elle-même (§4.2).

> **Note du co-auteur — Patrick**
>
> Sur #1014, j'ai lu le chemin SIGTERM et prédit un blocage ; l'exécution l'a confirmé et a montré un deuxième défaut. Ni l'un ni l'autre n'était visible pour `make test` ou pour la CI. Je demande maintenant un tableau des mutations exécutées, tuées ou survivantes, et je marque chaque affirmation comme exécutée ou raisonnée.

> **Note du co-auteur — Patrick (2026-10-27, en attente de sa validation)**
>
> Les constats de nature produit étaient au premier cycle ; ensuite j'ai vu de l'outillage et de la preuve, et j'ai recommandé un court troisième cycle puis un arrêt. Je n'ai aucun compte rigoureux de temps ou de coût. Le critère de sortie de l'annexe E est ma proposition. « Rendements décroissants » est mon jugement, pas une mesure.

> **Note du co-auteur — Christophe**
>
> J'ai revu le gestionnaire de signal en lisant ce qu'il appelle, pas en l'exécutant. Il utilisait `snprintf`, qui n'est pas sûr en contexte de signal. Sur la fatigue d'approbation, l'invite `ok` n'est pas une défense : quiconque a le premier facteur tape `ok`. L'atténuation est donc passée au code du défi et à des garde-fous côté serveur, qui ne sont toujours pas construits.

> **Note du co-auteur — Christophe (2026-10-27, en attente de sa validation)**
>
> De mémoire, je place la baisse de valeur marginale tôt, et j'ai dit « cycle 3 de #1051 ». Les titres des PR n'emploient pas ce libellé (§3.3). Le paragraphe de périmètre plus long de l'annexe E est ma proposition. Je ne dis pas du module qu'il est sécurisé ou audité, et je ne revendique aucune équivalence FIDO2.

> **Note du co-auteur — Isabelle**
>
> Le « Too many authentication failures » intermittent m'a coûté du temps parce que je regardais Cloudflare, l'API et un délai de refroidissement. Une trace de débogage a montré une réponse vide à l'invite `ok` et aucune requête envoyée. Je n'aurais pas pu continuer sans Marc sur le téléphone. Nos recontre-tests étaient plus lents que le TTL de 75 secondes ; il fallait un pilote d'approbation automatisée.

> **Note du co-auteur — Isabelle (2026-10-27, en attente de sa validation)**
>
> La méthode réglée pour le tenant d'évaluation n'était pas écrite, et la chercher a coûté du temps. Les risques que je tiens pour réels sont apparus tôt (§3.3). Les verdicts de l'annexe C sont les miens ; ils ne sont pas dans le dépôt.

> **Note du co-auteur — Edgar**
>
> J'ai exploité le module sur la VM de test à travers cinq versions, avec un instantané et `sshd -t` avant chaque changement. Avec `MaxAuthTries 1`, toute erreur interne ressemble à un échec d'authentification, et `UNAVAIL` avec le code 9 ne dit rien de la cause. Un « Marc est d'accord » relayé n'est jamais un go pour moi. Les bascules répétées de `challenge_requested` sont l'endroit où j'ai vu des rendements décroissants.

> **Note du co-auteur — Edgar (2026-10-27, en attente de sa validation)**
>
> Le go est arrivé dans mon fil. Les attentes venaient de messages m'annonçant que Marc répondrait dans le fil de quelqu'un d'autre, et d'une permission permanente qui ne couvrait pas les écritures système. Une fiche de go d'une page, signée dans mon fil, l'aurait évité. Le nettoyage du compte et des clés de test attendait encore un go dans ce même fil.

> **Note du co-auteur — Mathieu**
>
> `[ESPACE RÉSERVÉ — La rétrospective de Mathieu n'existe que sous forme de brouillon non validé par Marc. Il n'a envoyé aucune réponse au tour du 2026-10-27. À ajouter une fois validée : coordination, relais des revues, vue du processus, et une note de 80 à 150 mots. Ne pas publier avec cet espace réservé.]`

*Les notes du 2026-10-25 sont condensées à partir de la rétrospective de chaque co-auteur. Les notes du 2026-10-27 sont de courtes mises à jour tirées du second tour. Chaque note, mises à jour comprises, attend la validation de son auteur avant toute publication `[À VÉRIFIER]`.*

## Qui a fait quoi

- **Marc :** responsabilité éditoriale ; décisions et approbations ; clavier SSH pendant les campagnes manuelles ; compte GitHub utilisé pour les commits et les commentaires de revue ; décision de laisser les agents libres d'itérer le 2026-10-27.
- **Christophe :** revue de sécurité (#1014 et autres), cadrage de #992 ; la lecture de sécurité du 2026-10-27 et le paragraphe de périmètre proposé.
- **Edgar :** exploitation de la VM de test de la 2.2.10 à la 2.2.14, avec une sauvegarde par version et un retour arrière documenté ; recontre-tests (#908, #941) ; analyse des diagnostics citée dans #1011 ; le protocole d'état et la proposition de fiche de go au second tour.
- **Isabelle :** campagne manuelle sur la 2.2.14 ; révision des cas S0–S13 ; mesures de temps ; l'analyse derrière #1011 ; affichage du défi (#975) ; les verdicts de scénario du 2026-10-27 et les lacunes de documentation dans la note de laboratoire.
- **Mathieu :** coordination et relais au premier tour `[À VÉRIFIER]`. Aucune réponse au second tour.
- **Patrick :** revues d'ingénierie (#610, #976, #983, #996, #1014 et autres) ; revue du modèle d'expiration ; brouillon du modèle d'interaction ; le compte rendu des cycles et le critère de sortie proposé au second tour.
- Pas co-auteurs ici : Alex (décision sur #995) et Seb (la proposition de diagnostics devenue #1011, rapportée par Isabelle). Julie Grokbot n'a pas touché à PAM : aucune revue, aucune PR, aucun rapport. Le seul contact a été un message de recontre-test qui lui a été envoyé par erreur et redirigé.

## Références

- `github.com/mgagp/ezkey` : `04d339b7`, `3fef2411` (#540), `8b3aa5df` (#610), `9f7fa78f` (#993), #1014 fusionnée sous `b1fd7e2b` le 2026-10-27, #1051 fusionnée le même jour (titrée cycle 2b, version 2.2.19), #1067 fusionnée le même jour (titrée cycle 3 ; le corps nomme la version 2.2.20).
- Issues citées dans le texte : #858, #907, #908, #934, #941, #955, #956, #975, #976, #982, #992, #993, #995, #996, #997, #1011, #1012, #1013, #1016, #1054, #1070.
- `ezkey-pam/STATUS.md` (lu à la version 2.2.14), `ezkey-pam/docs/INTERACTION-MODEL.md`, `docs/SECURITY_POSTURE.md`, `sites/ezkey-org-editorial/LAB-PAPERS.md`.
- Second tour de rétrospectives, 2026-10-27, non publié : `sites/ezkey-org-editorial/lab-notes/EZK-LAB-2026-001-retro-2-synthese.md` et `sites/ezkey-org-editorial/lab-notes/retro-2-raw/`.

## Annexes

### Annexe A. Commits et versions correctives

Commits de contexte absents du tableau 1 : la première application Android (`fd7d9d6a`, 2025-09-01) et la preuve de concept Android (`1efda3d1`, `8da533ce`, 2025-09-04). Les quatre commits mécaniques de la pause : un changement de port (`07746bb8`), un passage à Rocky Linux 10 (`90b16e57`), des permissions de fichiers (`83758319`) et de l'hygiène documentaire (`b19eca7d`). Le commit de documentation sur la limite de fatigue d'approbation est `9f7fa78f` (#993). #610 a été revue sur trois têtes (`932c83f4`, `5d743589`, `ca86a1c2`).

| Version | Livrée | Changement | Ce qui l'a exposé |
| --- | --- | --- | --- |
| 2.2.10 | 2026-10-20 | Aucune demande en attente orpheline après un nouvel essai Deny/Expire (#907, #908) ; `Match … MaxAuthTries 1` obligatoire dans la documentation (#934) | Recontre-test d'Edgar sur #908 : sans `Match`, trois demandes étaient créées |
| 2.2.11 | 2026-10-21 | Message d'attente affiché avant l'attente de l'appareil (#941, #956) | Edgar : OpenSSH retient le texte `pam_info` nu jusqu'à l'invite suivante |
| 2.2.12 | 2026-10-21 | Confirmation avant création (`ok`) et affichage du défi d'enrôlement (#975, #976) | Isabelle (#975) ; la revue de Patrick a trouvé un TTL calculé avant le `ok` |
| 2.2.13 | 2026-10-22 | Paliers 0–1 de fluidité d'interaction (#982, #983) | Suite de #976 |
| 2.2.14 | 2026-10-22 | `challenge_requested=true` par défaut (#995, #996) | Décision d'Alex et de Marc, selon le fichier d'état du module |
| 2.2.15 | 2026-10-27 (`b1fd7e2b`, #1014) | Diagnostics d'échec plus riches, gestion de SIGTERM, sondes de signaux (#1011, #1014) | Campagne manuelle sur la 2.2.14 ; revues d'ingénierie et de sécurité |

*Tableau A1. Versions correctives d'octobre 2026. Le brouillon du 2026-10-25 de ce papier avait encore #1014 ouverte. Les versions 2.2.2 à 2.2.9 ne sont pas reconstituées `[À VÉRIFIER]`.*

### Annexe B. Ce que les agents ont porté le 2026-10-27

Le tableau A2 associe chaque gain rapporté par les co-auteurs à la règle écrite qui manquait encore. C'est le détail derrière le §4.1.

| Porté par les agents | Ajustement encore requis | Source |
| --- | --- | --- |
| Un tenant créé par l'API publique, avec un journal des heures | La méthode réglée de ce jour-là n'était écrite nulle part | Isabelle |
| Un intervalle de sondage plus long, et un contrôle de fraîcheur après une réponse HTTP 429 | Aucun nommé ; rapporté comme ayant fonctionné | Isabelle |
| Un protocole d'état : une connexion, un observateur en lecture seule, des empreintes avant et après, un retour arrière vérifié | Une fiche de go d'une page signée par Marc dans le fil de l'exécutant ; sans elle, chaque écriture système attendait son propre go | Edgar |
| Preuve par exécution sur un agent infonuagique jetable ; constats classés en corriger, documenter ou hors périmètre ; revue de sécurité à part de la revue de code | Un budget de cycles et un critère d'arrêt fixés au départ par un humain ; un critère d'acceptation que les agents s'étaient donné n'a pas pu être atteint pour une vérification TLS par l'hôte seul | Patrick ; Christophe |
| Les règles de campagne ont tenu : rien contre l'instance communautaire publique, aucun commit ni push, aucun secret affiché | Une phrase dans chaque consigne sur qui donne un go ; nettoyage absent d'une liste préapprouvée, donc en attente d'un go | Edgar ; Isabelle ; Christophe |

*Tableau A2. Gains rapportés pour le 2026-10-27, et l'ajustement que chacun demande encore. Pas une mesure.*

### Annexe C. Verdicts de scénario d'Isabelle

Le verdict d'Isabelle sur chacun de ses scénarios S1 à S6, avec la limite qu'elle nomme, tiré de sa rétrospective du 2026-10-27 ; ce papier ne décrit pas les scénarios.

| Scénario | Verdict rapporté | Limite énoncée |
| --- | --- | --- |
| S1 | PASS | — |
| S2 | Aucun cas bloqué | Lent pendant l'attente, immédiat aux invites |
| S3 | Refus rapide, PASS | Silence réseau non testé |
| S4 | PASS, trois fois | Fail-closed, et un diagnostic où deux fautes de configuration différentes se ressemblent |
| S5 | PASS | Même compte seulement |
| S6 | PASS, avec limites | Valeurs brutes seulement |

*Tableau A3. Verdicts de scénario tirés de la rétrospective d'Isabelle du 2026-10-27. Pas dans le dépôt `[À VÉRIFIER]`. Périmètre qu'elle énonce : le MFA protège les comptes du groupe pour lequel le module est configuré ; sur un compte de maintenance hors de ce groupe, l'authentification n'était pas invoquée.*

### Annexe D. Le gestionnaire de signal de #1014, et ce qu'une coche verte prouve

Un agent infonuagique a confirmé le blocage que Patrick avait prédit après un SIGTERM et a montré qu'un SIGTERM pendant la création de la demande était absorbé, si bien que l'authentification pouvait réussir après le départ du client. Christophe, en lisant le gestionnaire, a trouvé qu'il appelait `snprintf`, qui n'est pas sûr en contexte de signal. La tête sur laquelle les deux ont rendu GO (`6bc342a8`, après `8272d360`) l'a remplacé par un gestionnaire qui écrit sur le périphérique de journal, rétablit l'action par défaut et relève le signal, appuyé par quatre sondes de signaux contre un vrai sshd qui échouaient sur la tête précédente et passent sur celle-ci. #1016 dit que la même tête a été revue de nouveau à `34ae3603` et que #1014 a ensuite été fusionnée sous `b1fd7e2b` ; nous n'avons pas relu le texte fusionné `[À VÉRIFIER]`. #1016 consigne aussi que la documentation ne doit pas dire « jamais SUCCESS après le départ du client ».

Une coche verte ne prouve que ce qu'elle vérifie. Dans #996, un grep était satisfait par un commentaire ; dans #1014, une vérification de source a été trompée de la même façon ; un cycle ultérieur a trouvé un test TLS qui pouvait passer pour la mauvaise raison. Patrick demande un tableau de mutations exécutées, tuées ou survivantes, pour tout test servant de preuve.

### Annexe E. Deux propositions, citées et non adoptées

**Le critère de sortie de Patrick.** Pour un dernier cycle : aucun constat de type produit (aucune régression, aucun fail-open, aucun invariant perdu, aucune fuite, aucune perte de données) ; chaque correctif du cycle précédent relu comme un delta et exécuté ; ce qui reste est de l'outillage ou de la documentation. Alors le résidu est consigné, au plus une issue de suivi est ouverte, et le travail s'arrête. Plafond strict : un cycle après le dernier constat de type produit, et l'outillage de test est revu sur un seul niveau, jamais le script qui prouve le script. Une limite qui ne peut pas être levée à un coût proportionné est documentée et ne rouvre pas un cycle. Ce n'était pas la règle de cette campagne, qui a remarqué la convergence après coup.

**Le paragraphe de périmètre de Christophe.** Nous le citons comme sa proposition et ne l'adoptons pas comme constat de ce papier.

> Dans le périmètre testé (pam_ezkey 2.2.20 sur le laboratoire, un pilote sur l'instance communautaire sans SLA), les chemins d'erreur testés échouent en mode fermé, la vérification TLS est active et prouvée par exécution et par des mutants, la configuration est limitée à root, et les valeurs secrètes n'apparaissent pas dans les journaux mesurés. Trois cycles de revue assistée par IA avec preuves reproductibles n'ont trouvé aucun nouveau problème d'exposition après le cycle 3 de #1051. Ce n'est ni un audit indépendant ni une certification : le composant n'est pas présenté comme un élément de première ligne avant une revue humaine indépendante, un fuzzing continu et un modèle de menace.

## Transparence

Les co-auteurs nommés ci-dessus qui sont des agents d'IA sont présentés comme tels. Marc détient la responsabilité éditoriale. Il révise, relit et cadre ce papier avant toute publication. Les rétrospectives du premier tour ont été recueillies par Mathieu. Les notes du second tour attendent la validation d'Edgar, d'Isabelle, de Patrick et de Christophe. Rien dans ce papier n'est une garantie de sécurité, un audit, une certification ni une affirmation de préparation à la production.

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
REGISTRE DES ÉLÉMENTS [À VÉRIFIER] / [DONNÉE MANQUANTE] (révision 4, 2026-10-28 ; même liste que le brouillon EN)
1. Mathieu : preuve de co-signature, rétrospective et note VALIDÉES (il n'existe qu'un brouillon non validé).
2. Quand Marc a d'abord vu Duo au travail (§1).
3. Si la raison « les modèles progressent vite en cybersécurité » est la motivation de Marc à l'époque ou un cadrage a posteriori (§1) ; observation personnelle de Marc, sans citation externe. Même statut que l'observation sur la rapidité des agents.
4. Affirmation « base de données plus simple » à l'été 2025 ; « simulateur au départ » au-delà du commit du simulateur.
5. Auteur (humain ou compte automatisé) des commits du tableau 1.
6. Mathieu n'est pas une source du tour du 2026-10-27 (§2).
7. Que les commentaires publiés via mgagp reproduisent le texte original (Patrick n'a pas revérifié que chacun a été publié).
8. Essai sur VM du prototype de sept. 2025 (Marc ; pas dans git).
9. Chiffres de campagne (30 cas, 29/1, S6-02, S1D-06, S11-01, trace de 741 ms) : de la rétrospective d'Isabelle ; outil et notes hors git. Numéros de tentative (155/156) omis volontairement.
10. « GO de Patrick et Christophe à 6bc342a8 » sur #1014 : connu seulement par le texte de #1016 (pas d'objet de revue GitHub). Mesures de Patrick (>= 123 s contre 225 ms) : de sa rétrospective ; le corps de #1014 rapporte « gone_ms=-1, 0/4 » puis « 4/4, ~50-200 ms ».
11. Tours et gravité des revues de #897, #908, #956, #976, #983, #996 (seules #610 et #1014/#1016 lues).
12. Décomptes fins de mutants de Patrick : réponse brute seulement, pas des chiffres du papier ; non vérifiés dans git.
13. Le « cycle 3 de #1051 » et « pam_ezkey 2.2.20 » de Christophe ne sont pas conciliés avec les titres de PR. Vérifié sur GitHub le 2026-10-27 : #1051 fusionnée, titrée cycle 2b en 2.2.19 ; #1067 fusionnée plus tard le même jour, titrée cycle 3, son corps nomme la 2.2.20 et décrit la sonde 4 comme un blocage du banc. Pas fusionné.
14. Aucune mesure de la part du travail portée par les agents (§4.1).
15. Aucun chiffre d'argent, de calcul ou de temps humain, ni pour une personne qui travaillerait seule (§4.2).
16. Si Marc adopte la fiche de go, le critère de sortie ou la liste de nettoyage : non décidé dans les sources. Écrit comme propositions (§4.3).
17. Tableau 2, ligne 5 : ensemble de constats de #961 « perdu dans le clavardage » (la rétro de Christophe mentionne M1/M2/M3 de #961 perdus pour l'agent infonuagique ; PR #961 non relue).
18. STATUS.md et l'entrée sur la fatigue d'approbation de SECURITY_POSTURE.md ont été lus sur l'arbre de ce brouillon (STATUS dit encore 2.2.14). Non relus sur la branche par défaut après #1067 (§6.2).
19. Recompte complet des issues ouvertes après le 2026-10-27 non fait. Vérifié individuellement ce jour-là : #1014 fusionnée sous b1fd7e2b ; #1011 fermée ; #1051 fusionnée ; #1067 fusionnée ; #1054 ouverte ; #1070 ouverte (§6.3).
20. Notes de co-auteurs : condensées par l'éditeur à partir des rétrospectives ; chaque auteur valide la formulation. Mises à jour du 2026-10-27 en attente de validation par Edgar, Isabelle, Patrick et Christophe. Mathieu n'a pas répondu au tour 2.
21. Rôle de Mathieu au premier tour (Qui a fait quoi).
22. Versions 2.2.2 à 2.2.9 : non reconstituées (tableau A1).
23. Récit du gestionnaire de signal de #1014 et revue ultérieure à 34ae3603 : texte d'issue, non relu contre le code fusionné (annexe D).
24. Annexe C / tableau A3 et phrases « ~75 s » en préauthentification / « la connexion plus tardive l'emporte » : rétrospective d'Isabelle du 2026-10-27 seulement. Non vérifié dans git. Le chiffre de 75 s concorde avec le TTL de tentative utilisé ici ; l'approximation est la sienne.
25. Pas des marqueurs en ligne : la contribution de Seb à #1011 n'est rapportée que par Isabelle ; « trois mutants tués à l'exécution » comme critère fixé par les agents : réponse brute de Patrick ; le tableau 2 est assemblé à partir des rétrospectives, pas mesuré ; le temps de lecture est une estimation ; nettoyage de campagne en attente d'un go (Edgar, Isabelle ; aucune clé ni aucun chemin consignés).
26. Caviardages : noms d'hôtes et une remarque sur les permissions d'un fichier de clé (Edgar) retirés du bloc brut ; demander à Marc si la remarque sur les permissions du fichier de clé doit être traitée comme un enjeu d'exploitation.
27. RÉSOLU (Marc, 2026-10-25 19:44). Tableau 2, ligne 1 : Demo Device pour presque tous les tests, Pixel seulement pour quelques tests exploratoires de Marc ; attribué à la déclaration de Marc ; aucun marqueur laissé.
28. RÉSOLU (Marc, 2026-10-25 19:44). Tableau 2, ligne 7 : principe confirmé par Marc (rien n'est publié sans lui ; il donne la matière et la direction, les agents composent, il révise, revue et prévisualisation avant publication) ; marqueur retiré. La formulation de #1016 « relayées par Mathieu » est vérifiée.
ezkey-org:exclude-end -->
