---
audience: "Lecteurs techniques déjà engagés: développeurs backend, architectes, équipes sécurité ou intégration qui veulent voir un flux Ezkey réel avec ses signatures, ses transitions d'état et ses points de vérification. Pas un public de découverte généraliste."
planned_slug_en: "technical-trace-ezkey-enrollment-authentication.html"
planned_canonical_en: "https://ezkey.org/technical-trace-ezkey-enrollment-authentication.html"
planned_slug_fr: "technical-trace-ezkey-enrollment-authentication.html"
planned_canonical_fr: "https://ezkey.org/fr/technical-trace-ezkey-enrollment-authentication.html"
status: published
published_html_en: /technical-trace-ezkey-enrollment-authentication.html
published_html_fr: /fr/technical-trace-ezkey-enrollment-authentication.html
published_date: 2026-10-05
html_amended_post_publish: false
source_of_truth: html
---

# Trace technique d’un flux Ezkey complet

<!-- ezkey-org:exclude-start
Placement recommendation:
- Lane: Articles -> Craft & engineering, not Guides and not the landing page.
- This should be intentionally linked for readers who already have product context.
- Good companion entry points later: API docs, Source & evaluation, or a shorter conceptual article that explains why Ezkey signs every step.

Positioning note:
- Start as one standalone technical article, not a brand-new public site section.
- If we later publish 2-3 protocol walkthroughs (accept, deny, challenge, API-key posture), revisit a small series model or a lightweight technical sub-index inside Articles.
- Do not surface this as a first-touch homepage destination.

Drafting posture:
- This article is derived from a validated local didactic run and may intentionally publish the full transcript, including bearer tokens, proof tokens, private keys, and signatures, because the stack was a disposable local Docker instance dedicated to this one publication exercise and is now dead.
- Keep it readable for expert readers anyway: explicit trust-chain explanation, clear sectioning, and enough editorial framing that the detail feels intentional rather than dumped.

Candidate teaser for article cards:
- "A concrete walk through one validated Ezkey flow, from pending enrollment to approved authentication, with the signature chain explained step by step."

Possible follow-ups if this lands well:
- deny path
- challenge-required path
- integration API key posture instead of admin-bearer issuance
- compact article on signed instance-info and why it matters
 ezkey-org:exclude-end -->

*Note technique · 5 octobre 2026*

Tous les textes sur l’authentification forte promettent de la cryptographie. Plus rares sont ceux qui montrent, de façon lisible, **où la cryptographie intervient vraiment**, **ce qui est signé**, **par quelle clé**, et **à quel moment le backend ou le mobile doivent décider de faire confiance**.

Ezkey se prête assez bien à cet exercice, justement parce que le produit assume une posture **backend-first**. L'état utile reste côté serveur, les étapes importantes sont exposées par API, et le mobile ne se contente pas d'« afficher une demande »: il participe à une chaîne cryptographique explicite.

Ce qui suit n'est pas une visite produit grand public. C'est une **trace technique commentée** d'un flux complet validé sur stack local: enrôlement, association de l'appareil, récupération de branding signé, puis demande d'authentification approuvée. L'objectif n'est pas de publier un dump intégral de JSON, mais de montrer la structure du protocole sans perdre le lecteur dans les détails inutiles.

## Pour qui est ce texte

Ce texte s'adresse à des lecteurs qui ont déjà le bon niveau de contexte pour l'apprécier:

- développeurs backend qui veulent comprendre ce qu'ils intégreraient réellement;
- architectes qui évaluent la lisibilité d'un protocole MFA propriétaire;
- équipes sécurité qui veulent examiner la continuité entre enrôlement et authentification;
- lecteurs curieux du produit, mais déjà au-delà du niveau "qu'est-ce qu'Ezkey?".

Si vous découvrez Ezkey pour la première fois, ce n'est probablement pas la meilleure porte d'entrée. L'intention ici est différente: **montrer un flux réel en résolution technique**, pas résumer la proposition de valeur du produit.

## Ce que cette trace montre, et ce qu'elle ne montre pas

Le scénario est volontairement sobre.

Un opérateur authentifié côté **Admin API** crée un enrôlement rattaché à une intégration. Un appareil simulé exécute ensuite les appels **bind**, **verify**, **pending** et **respond** contre l’**Auth API**, avec la **Crypto API** utilisée seulement comme oracle de laboratoire pour reconstruire les chaînes canoniques et vérifier les signatures.

Le cas d’usage retenu est lui aussi simple: **un utilisateur approuve l'accès à une intégration**. Pas de message métier riche, pas de validation de paiement, pas de storytelling supplémentaire. Le but est de laisser le protocole parler de lui-même.

Ne sont pas montrés ici:

- le bootstrap de connexion administrateur par login passwordless;
- les variantes `deny`, `challengeRequired=true`, ou posture pilotée principalement par clé d’API d’intégration.

## Vue d’ensemble du flux

Le chemin complet tient en huit mouvements:

1. l’opérateur crée un enrôlement en état pending;
2. il récupère l’`enrollmentProofToken` et le défi associé;
3. l’appareil appelle `bind` et vérifie la signature Ed25519 fournie par l’intégration;
4. l’appareil génère sa paire de clés, signe la chaîne canonique de `verify`, puis active l’enrôlement;
5. l’appareil récupère ensuite l’`instance-info` signé pour ne pas dépendre d’un branding non authentifié;
6. l’opérateur crée une tentative d’authentification pour cet enrôlement;
7. l’appareil réclame une fois le pending, vérifie la signature intégration, puis signe sa réponse;
8. le backend confirme le résultat côté mobile et côté attente opérateur.

Le point important n’est pas seulement l’ordre des appels. C’est la **continuité** entre eux.

## 1. L’enrôlement n’est pas un simple enregistrement

Dans Ezkey, un enrôlement n’est pas un drapeau posé dans une base. C’est le début d’une relation cryptographique entre une intégration, un appareil et une suite de transitions d’état qui devront rester cohérentes plus tard.

L’opérateur crée d’abord la ligne d’enrôlement. Le backend renvoie un identifiant, un défi, puis, via une lecture administrative, le `enrollmentProofToken` dont l’appareil aura besoin ensuite.

Ce détail est important parce qu’il expose déjà une idée structurante d’Ezkey: le flux ne repose pas sur une simple possession de l’identifiant de ligne. Il faut aussi le bon matériau de preuve pour poursuivre.

## 2. `bind`: première ancre de confiance côté intégration

Quand l’appareil appelle `bind`, il n’obtient pas seulement un peu de contexte d’affichage. Il reçoit un paquet de données signé par l’intégration, avec notamment:

- la clé publique d’intégration;
- l’algorithme annoncé (`ed25519`);
- les métadonnées utiles d’intégration et de tenant;
- la signature `enrollmentBindPayloadSignedByIntegration`.

La charge canonique correspondante suit une logique simple et explicite:

```text
<enrollmentProofToken>|<enrollmentId>|<integrationPublicKey>|ed25519|<integrationName>|<integrationDescription>|<enrollmentName>|<tenantId>|<tenantName>|<tenantDescription>|<isSystemIntegration>|<adminType>
```

Le lecteur n’a pas besoin de retenir chaque segment pour comprendre l’intérêt de cette forme: le mobile ne fait pas confiance à un JSON arbitraire. Il reconstruit une ligne UTF-8 déterministe, puis vérifie la signature Ed25519 avec la clé publique d’intégration reçue dans la même réponse.

Autrement dit, la confiance affichée au téléphone doit d’abord être une confiance **vérifiée**.

## 3. `verify`: l’appareil prouve qu’il possède bien sa clé

Après `bind`, l’appareil génère sa propre paire de clés ECDSA P-256. La suite du flux n’est plus seulement “l’intégration parle au mobile”, mais “le mobile prouve à son tour qu’il contrôle bien la clé qu’il présente”.

La chaîne canonique de `verify` est courte:

```text
<enrollmentProofToken>|<enrollmentId>|<challengeResponse>|<devicePublicKey>
```

Elle est signée côté appareil, puis envoyée à l’Auth API. Si la vérification passe, le backend active l’enrôlement et retourne un résultat signé par l’intégration. La logique est symétrique: chaque acteur signe ce qui relève de sa responsabilité.

C’est l’un des traits les plus lisibles du produit. On n’a pas un backend qui “fait globalement confiance” à un appareil déclaré. On a une séquence où le mobile doit satisfaire un engagement cryptographique précis avant que l’état de l’enrôlement ne change.

## 4. L’`instance-info` signé évite un branding implicitement fiable

Une fois l’enrôlement activé, le mobile doit parfois afficher des informations d’instance: nom, description, URL associée, base URL Auth publique si elle existe. Ezkey prévoit ici un chemin signé pour les clients enrôlés.

La charge canonique ressemble à ceci:

```text
<enrollmentProofToken>|<enrollmentId>|INSTANCE_INFO|<authApiPublicBaseUrl>|<instanceName>|<instanceDescription>|<aboutUrl>
```

Ce point mérite d’être souligné car il est facilement négligé dans d’autres systèmes: l’habillage ou le branding ne sont pas forcément “de simples détails UI”. Dès lors qu’un client enrôlé s’appuie sur ces valeurs, Ezkey choisit de leur donner aussi une voie d’authenticité.

## 5. `pending`: la demande d’authentification est elle aussi signée

Le backend crée ensuite une tentative d’authentification. Dans le scénario retenu ici, il s’agit d’un cas de base: l’utilisateur approuve l’accès à l’intégration, sans message métier complémentaire.

L’appareil ne reçoit pas directement la décision à signer. Il doit d’abord réclamer le `pending` avec un `deviceProofToken` frais, lui-même signé côté appareil. Si la requête est valide, l’Auth API renvoie le `authAttemptProofToken` ainsi qu’une signature d’intégration.

La charge canonique associée au `pending` reste simple:

```text
<authAttemptProofToken>|<challengeRequired>|<contextTitle>|<contextMessage>
```

Dans le cas validé ici, `contextTitle` et `contextMessage` sont absents. C’est voulu. Le lecteur voit ainsi la forme la plus nue du protocole, sans emphase sur un décor métier qui n’est pas nécessaire pour comprendre la chaîne de confiance.

Le point clé est ailleurs: **le pending est consommé une fois**. Une lecture réussie fait passer la tentative dans un état qui empêche de traiter indéfiniment la même demande comme si elle était nouvelle. Le `respond` qui suit s’ancre donc sur un matériau déjà lié à cette réclamation précise.

## 6. `respond`: une décision minimale, mais liée exactement au bon flux

Une fois le `pending` vérifié, l’appareil signe sa réponse.

La chaîne canonique est volontairement minimale:

```text
<authAttemptProofToken>|true
```

Dans le cas d’approbation, le backend reconstruit exactement cette ligne, vérifie la signature ECDSA de l’appareil, puis enregistre le résultat. La réponse HTTP du backend ne s’arrête pas à “ok, c’est approuvé”. Elle transporte à son tour un résultat signé par l’intégration.

Le résultat mobile exposé par l’Auth API est `APPROVED`. La surface d’attente côté opérateur, elle, résout le même cycle sous l’étiquette `ACCEPTED`.

Deux mots, une seule réalité: la tentative s’est refermée sur la branche de succès.

## Pourquoi ce niveau de détail vaut la peine

Ce genre de trace intéresse peu de monde en dehors d’un public technique étroit. C’est normal. Mais pour ce public-là, elle dit quelque chose d’important sur le produit.

Elle montre que la valeur de sécurité revendiquée n’est pas posée comme un slogan. Elle est visible dans la structure du protocole:

- des matériaux de preuve à usage borné;
- des signatures qui lient explicitement les étapes;
- des transitions d’état observables côté backend;
- un mobile qui signe réellement des chaînes déterministes, au lieu de servir seulement d’interface d’approbation.

Autrement dit, Ezkey reste fidèle à son intention produit: une MFA moderne, explicite, backend-first, compréhensible par une équipe qui lit des APIs, des états, et des garanties cryptographiques concrètes.

## Où aller ensuite

Cette trace n’est probablement pas le dernier mot sur le sujet. Si elle remplit bien son rôle, plusieurs suites deviennent naturelles:

- publier une variante `deny` pour montrer ce qui change, et ce qui ne change pas;
- montrer une branche avec challenge supplémentaire;
- publier une version orientée **clé d’API d’intégration** plutôt que création pilotée par Admin API;
- relier cet article à une explication plus courte sur ce qu’Ezkey cherche à rendre plus simple, ou au contraire plus explicite, que d’autres approches.

Mais comme point de départ public, un seul exemple proprement cadré est probablement suffisant: un flux complet, validé, lisible, et intentionnellement réservé à ceux qui viennent chercher ce degré de proximité avec le code.

---

[← ezkey.org (français)](/fr/)
