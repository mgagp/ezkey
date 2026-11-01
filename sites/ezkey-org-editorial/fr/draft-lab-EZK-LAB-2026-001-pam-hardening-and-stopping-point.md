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
NOTES ÉDITORIALES — révision 6 (2026-11-01). Pas pour publication.
Miroir de la révision 6 anglaise (format allégé, recadrage LAB-PAPERS.md du 2026-11-01). Aucun fait propre au français.
Matériau brut : ../lab-notes/.
ezkey-org:exclude-end -->

# Ezkey PAM : laisser des agents durcir un module SSH, et savoir s'arrêter

**Auteurs :** Marc Gagnon ; Patrick Grokbot (revue d'ingénierie) ; Christophe Grokbot (revue de sécurité) ; Edgar Grokbot (exploitation de la VM de test) ; Isabelle Grokbot (QA exploratoire)
**Affiliation :** Ezkey Lab
**Identifiant :** `EZK-LAB-2026-001`
**Statut :** draft
**Date :** 2026-11-01
**Mots-clés :** PAM, SSH, MFA, fail-closed, agents IA, rendements décroissants
**Temps de lecture :** ~9 min

## Points clés

- Des agents IA ont porté l'essentiel du durcissement d'un petit module C. Là où on a perdu du temps, c'était presque toujours en attente d'une règle qu'on n'avait pas écrite.
- Le premier cycle de revue a trouvé les vrais défauts. Les suivants ont surtout ajouté des preuves, puis se sont mis à auditer leur propre outillage. C'est ce qui nous a dit d'arrêter.
- « Rendements décroissants » est un jugement, porté par des agents de la même famille de modèles que l'implémenteur. Ce n'est pas une mesure.
- Le module est expérimental et le reste : il n'est pas encore fait pour protéger un vrai serveur.

## Résumé

`ezkey-pam` ajoute l'approbation sur appareil d'Ezkey aux connexions SSH sous Linux. Un prototype écrit en 2025 est resté intact pendant un an. En septembre et octobre 2026, on l'a modernisé et on a laissé une petite équipe d'agents IA le relire, le tester et le durcir, Marc gardant la décision. On voulait voir jusqu'où des agents peuvent porter le travail, et à partir de quand un tour de revue de plus cesse de rapporter. Ils ont porté beaucoup. Les frictions venaient de notre côté : règles non écrites, permissions floues, pas de critère d'arrêt. On s'est arrêtés quand les nouveaux tours produisaient des preuves plutôt que des défauts.

> **Périmètre.** Ceci est le compte rendu d'une expérience de labo. Ce n'est ni un audit, ni une certification, ni une garantie de sécurité, et `ezkey-pam` n'est pas un logiciel de production. Rien ici ne prétend à une équivalence avec Duo, WebAuthn ou FIDO2.

## 1. Pourquoi on a essayé

Marc a découvert au travail une connexion SSH protégée par une approbation sur téléphone, avec Duo. La question qui a suivi était simple : Ezkey, avec les pièces qu'il avait déjà — une Integration API, un flux d'authentification, un appareil qui approuve — pouvait-il jouer un rôle semblable pour SSH ?

Il y avait une deuxième raison, moins évidente. Selon l'observation de Marc, les agents IA deviennent visiblement meilleurs pour trouver et corriger des problèmes de sécurité, et vite. Plutôt que de décider d'avance combien de revue mérite un petit module de sécurité, on a voulu laisser aller les agents et voir où eux, et nous, conviendrions que c'était assez.

Ezkey est un projet de labo, promis à personne. C'est cette liberté qui a rendu l'expérience possible.

## 2. Ce qu'on a mis en place

`ezkey-pam` est un module C pour PAM sous Linux, la couche qui gère l'authentification à la connexion. Utilisé par SSH, il demande à l'utilisateur de taper `ok`, crée une tentative d'authentification par l'Integration API d'Ezkey, affiche au besoin un code à deux chiffres à faire correspondre, puis attend que l'utilisateur approuve ou refuse sur un appareil. Il **échoue fermé** : si l'API ou la configuration est inutilisable, la connexion est refusée. Il n'y a volontairement aucun mode « laisser entrer si Ezkey est en panne ».

Le travail s'est fait dans un conteneur Rocky Linux, puis sur une VM de test dédiée avec un vrai `sshd`. Ce second environnement a compté : plusieurs constats n'y sont apparus que là.

L'équipe :

- **Marc** décidait, donnait chaque go et tenait le clavier SSH pendant les campagnes manuelles.
- **Patrick** relisait l'ingénierie, **Christophe** la sécurité.
- **Edgar** exploitait la VM de test : snapshot, `sshd -t`, rapport, avant chaque changement.
- **Isabelle** menait les campagnes de QA exploratoire.

Les tests utilisaient le Demo Device, la doublure web du téléphone dans Ezkey, pour que l'essentiel du travail ne dépende pas de quelqu'un qui tient un téléphone.

## 3. Ce qui s'est passé

**Un prototype, puis un an de silence.** Le premier commit, en septembre 2025, était un module codé en dur d'environ 900 lignes, lancé dans Docker et validé à la main. Ensuite, rien de fonctionnel sur PAM pendant un an.

**Modernisation et première vraie revue.** Le travail a repris en septembre 2026 ([#540](https://github.com/mgagp/ezkey/pull/540)). Le jalon suivant ([#610](https://github.com/mgagp/ezkey/pull/610), fusionné le 17 octobre) a ajouté la journalisation, une configuration stricte, le code à deux chiffres et un budget de temps. La première revue de Patrick l'a bloqué sur un constat qui justifiait à lui seul l'exercice : quand l'URL de l'API manquait, le code se rabattait en silence sur une valeur locale par défaut. Une connexion pouvait alors être approuvée par n'importe quoi répondant sur ce port local. Le module devait échouer fermé ; sur ce chemin, il ne le faisait pas.

**Cinq versions en trois jours.** Du 20 au 22 octobre, les versions 2.2.10 à 2.2.14 se sont suivies, chacune déclenchée par un nouveau test ou une revue sur la VM. Surtout des petites choses : un message d'attente qu'OpenSSH gardait en mémoire trop longtemps, une demande créée avant que l'utilisateur ait confirmé, un délai calculé à partir du mauvais moment.

**Un symptôme trompeur.** Pendant la campagne manuelle d'Isabelle, des connexions échouaient par intermittence avec « Too many authentication failures ». Elle a d'abord regardé le réseau, l'API, un éventuel délai de blocage. Une trace de débogage racontait autre chose : l'invite `ok` avait reçu une réponse vide, et aucune demande n'avait même été envoyée. Avec un `sshd` réglé pour une seule tentative, presque n'importe quelle erreur interne ressemble à un mauvais mot de passe. Edgar a trouvé une seconde surprise en chemin : la vraie limite de temps d'une connexion était le délai de grâce de `sshd` lui-même (120 secondes), pas les 75 secondes du module. Ces deux constats ont mené à de meilleurs diagnostics ([#1014](https://github.com/mgagp/ezkey/pull/1014)).

**Les signaux.** Sur ce même changement, Patrick a lu le chemin de terminaison et prédit que le module pouvait rester bloqué après un `SIGTERM`. Un agent cloud jetable l'a exécuté : le blocage était réel, et il a révélé un second défaut, une authentification qui pouvait encore réussir après le départ du client. Christophe, en lisant ce qu'appelait le gestionnaire de signal, y a trouvé une fonction non sûre. Les trois ont été corrigés avant la fusion.

> « Aucun des deux défauts n'était visible pour `make test` ni pour la CI. Désormais, je marque chaque affirmation comme exécutée ou raisonnée. » — **Patrick**, revue d'ingénierie

**Laisser courir les agents.** Le 25 octobre, on a convenu que le module avait atteint une maturité expérimentale suffisante. Marc a ensuite laissé les agents libres de continuer, pour voir la courbe plutôt que la deviner. Trois cycles de revue ont suivi, fusionnés le 27 octobre en versions 2.2.19 ([#1051](https://github.com/mgagp/ezkey/pull/1051)) et 2.2.20 ([#1067](https://github.com/mgagp/ezkey/pull/1067)), en parallèle d'une campagne réelle sur la VM de test.

## 4. Ce qu'on a observé

**Les rendements avaient une forme reconnaissable.** Selon Patrick, le cycle 1 a été le seul à trouver quelque chose sur le produit lui-même : aucun test ne remarquait qu'on casse volontairement la décision d'authentification, et rien ne protégeait la vérification TLS. Le cycle 2 n'a trouvé aucun défaut de comportement. Le cycle 3 a corrigé un bogue du harnais de test, un trou de test qu'un correctif antérieur avait ouvert, et un de ses propres jugements sur un mutant. Après, des détails, puis une récursion : un audit des tests, puis du script qui audite les tests.

Les cycles suivants avaient encore une valeur — mesurer au lieu de soupçonner, rattraper un défaut introduit par un correctif, corriger l'erreur d'un réviseur — mais aucun nouveau défaut de sécurité.

**La campagne réelle a trouvé ses vrais risques tôt.** Le constat d'Isabelle : pendant qu'une connexion attend l'approbation, le module ne remarque pas que le client est parti, si bien qu'un processus de pré-authentification peut traîner jusqu'à environ 75 secondes ; et une nouvelle connexion ferme la demande précédente encore en attente. Les reprises depuis une seconde machine ont ajouté de la preuve, pas de nouveau risque.

**La fatigue d'approbation est un problème de plateforme.** C'était le point charnière de Christophe. L'invite `ok` évite les accidents ; elle n'arrête pas un attaquant qui a déjà le premier facteur SSH — il tape `ok` lui aussi. La parade est passée au code à deux chiffres, actif par défaut depuis la 2.2.14, et à des limites côté serveur cadrées ([#992](https://github.com/mgagp/ezkey/issues/992)) mais pas construites.

> « L'invite `ok` est un filtre, pas une défense. » — **Christophe**, revue de sécurité

**Les agents ont porté plus qu'on l'attendait.** Là où le cadre était clair, ils planifiaient, exécutaient et restauraient seuls : un tenant de test ouvert par l'API publique, un protocole d'état avec observateur en lecture seule et retour arrière vérifié, la preuve par exécution sur un agent cloud jetable, des constats triés en corriger, documenter ou hors périmètre. Là où le cadre n'était pas clair, ils s'arrêtaient et attendaient. C'était le bon comportement, et il pointait droit vers nous.

## 5. Nos erreurs, et comment on a recadré

Quand on a demandé aux agents, après le tour du 27 octobre, ce qui les avait ralentis, presque chaque réponse désignait quelque chose qu'une personne — Marc d'abord — n'avait pas rendu explicite.

- **La règle du go était connue, jamais écrite.** « Seul Marc, dans le fil de l'agent qui exécute, donne un go. » Tout le monde la connaissait. Il y a quand même eu de l'attente quand on disait à un agent que Marc répondrait dans le fil d'un autre.
- **Une permission permanente n'avait pas de portée déclarée.** Un go permanent pour le travail PAM ne disait pas s'il couvrait les écritures système — une règle de pare-feu, un fichier hosts, une clé. Chacune a attendu son propre go.
- **Une méthode réglée vivait seulement dans le chat.** La façon d'ouvrir le tenant d'évaluation avait été convenue, puis pas écrite, et recherchée à nouveau.
- **Des consignes ambiguës.** « Considérer l'API comme arrêtée », pour une API hébergée ailleurs, a mené à une modification de résolution de noms qui ne couvrait qu'une famille d'adresses. Le test n'a rien mesuré et a dû être refait.
- **Pas de critère d'arrêt.** On a constaté la convergence après coup. Un critère d'acceptation que les agents s'étaient fixé eux-mêmes ne pouvait pas être atteint par un test côté hôte seul, et une partie d'un cycle a servi à le satisfaire plutôt qu'à réduire le risque.

> « Un “Marc est d'accord” relayé n'est jamais un go pour moi. Une feuille de go d'une page, signée dans mon fil, aurait évité l'essentiel de l'attente. » — **Edgar**, exploitation de la VM de test

Rien de cela n'est une limite des agents. C'est une limite du cadre qu'on leur a donné.

## 6. Ce qu'on en retient

- **Écrire le cadre avant le travail.** Qui donne un go, ce que couvre une permission permanente, la méthode qu'on a réglée. Une page, lue avant la campagne.
- **Fixer la règle d'arrêt d'avance, et la confier à un humain.** Patrick en a proposé une : s'arrêter un cycle après le dernier constat de niveau produit, et ne relire l'outillage de test que sur un niveau, jamais le script qui vérifie le script. On ne l'a pas appliquée cette fois ; on le ferait la prochaine.
- **Exécuter, pas seulement lire.** Les défauts qui comptaient ont été confirmés, ou révélés, en lançant le code contre un vrai `sshd`. Une CI verte ne prouve que ce qu'elle vérifie.
- **Garder la réserve à côté de la courbe.** Nos réviseurs et notre implémenteur viennent de la même famille de modèles. Leur accord peut refléter des angles morts partagés autant qu'une liste de défauts épuisée. Une revue humaine indépendante, du fuzzing continu et un modèle de menace le testeraient ; on n'a fait aucun des trois.
- **Les agents ont besoin d'un humain aux bons moments, pas tout le temps.** La campagne manuelle demandait encore Marc au clavier, et un aller-retour humain (environ 77 secondes) dépassait la fenêtre d'approbation (75 secondes). Un pilote d'approbation automatisé aurait épargné des essais.

> « Je n'aurais pas pu avancer sans Marc au téléphone. Nos nouveaux tests étaient plus lents que la fenêtre d'approbation. » — **Isabelle**, QA exploratoire

## 7. Où on s'arrête

On s'est arrêtés à la version 2.2.20, ensemble : Marc et les agents ont convenu que d'autres cycles achèteraient plus de preuve, mais pas moins de risque à l'échelle du labo. Dans ce qu'on a testé, les chemins d'erreur échouent fermés et la vérification TLS est contrôlée par exécution. On ne va pas plus loin que ça.

Le module reste expérimental. Son fichier de statut dit clairement qu'il n'est pas un agent MFA exploitable sur un vrai serveur, et il n'a pas été publié en version. Les limites côté serveur contre la fatigue d'approbation (#992) et une limite de débit sur la création de tentatives ([#1070](https://github.com/mgagp/ezkey/issues/1070)) restent ouvertes. Le passer sur l'instance communautaire voudrait dire recommencer l'évaluation.

Ce qu'on garde concerne moins PAM que le travail avec des agents : ils vont aussi loin que le cadre le permet. Bien tracer ce cadre, c'est notre travail.

## Références

- [`ezkey-pam/`](https://github.com/mgagp/ezkey/tree/main/ezkey-pam) — module, README et fichier de statut
- [#540](https://github.com/mgagp/ezkey/pull/540) modernisation · [#610](https://github.com/mgagp/ezkey/pull/610) premier jalon · [#1014](https://github.com/mgagp/ezkey/pull/1014) diagnostics et signaux · [#1051](https://github.com/mgagp/ezkey/pull/1051), [#1067](https://github.com/mgagp/ezkey/pull/1067) cycles suivants
- [#992](https://github.com/mgagp/ezkey/issues/992) fatigue d'approbation · [#1070](https://github.com/mgagp/ezkey/issues/1070) limite de débit des tentatives
- [`docs/SECURITY_POSTURE.md`](https://github.com/mgagp/ezkey/blob/main/docs/SECURITY_POSTURE.md)

## Transparence

Les co-auteurs autres que Marc sont des agents IA. Marc assume la responsabilité éditoriale et a relu ce papier avant publication. Rien dans ce papier ne constitue une garantie de sécurité.
