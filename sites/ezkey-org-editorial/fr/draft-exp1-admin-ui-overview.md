---
audience: "Invited EXP1 users acting as Tenant Admins; conceptual overview of the Admin UI (mental map), not a hands-on tutorial or guided walkthrough."
planned_slug_fr: "exp1-admin-ui-overview.html"
planned_canonical: "https://ezkey.org/fr/exp1-admin-ui-overview.html"
status: draft
source_of_truth: draft
---

# EXP1 — survol conceptuel de l’Admin UI Ezkey (administrateur de locataire)

<!-- ezkey-org:exclude-start
Editorial intent: short conceptual orientation for invited experimental users — a map of what the Admin UI is for and how the main areas relate. Not a click-by-click tour; the hands-on path lives elsewhere (e.g. demo / tutorial material).
Reader posture: curious but new to Ezkey; technically comfortable enough to understand a self-hosted admin product, but not looking for field-by-field documentation.
Keep the published body concise and confidence-building. Do not turn it into a user manual.
ezkey-org:exclude-end -->

Sur exp1, vous n’entrez pas dans Ezkey comme administrateur global de la plateforme. Vous entrez comme **administrateur de locataire**. Cette distinction compte, parce qu’elle dit tout de suite quelque chose d’important sur Ezkey : l’interface n’est pas un grand tableau de bord uniforme où tout le monde voit tout. Elle s’adapte au **rôle réel** de l’opérateur.

L’Admin UI est la surface humaine principale d’Ezkey. C’est là qu’on administre les intégrations, les enrôlements, les administrateurs d’une organisation, les clés API et le suivi opérationnel courant. Mais l’Admin UI n’est pas, à elle seule, « le système ». Ezkey reste une plateforme **backend-first** : l’interface pilote et rend visibles les capacités, tandis que l’autorité, l’état et les vérifications restent côté serveur.

Il faut aussi lire cette interface avec honnêteté : **Ezkey reste un produit expérimental**. L’Admin UI montre une plateforme déjà structurée, mais elle ne prétend pas encore couvrir tout ce qu’un déploiement mature intégrerait nativement.

## Pourquoi cette interface existe

Ezkey vise une installation **autonome**, **self-hosted** ou opérée dans l’environnement choisi par l’organisation, y compris dans des contextes on-prem ou cloud maîtrisés. Cela change naturellement la forme du produit.

Quand on veut qu’un système d’authentification reste sous contrôle de l’organisation, il ne suffit pas d’avoir une API. Il faut aussi une console qui rende l’ensemble **opérable** : comprendre ce qui existe, créer les objets utiles, suivre les événements, donner l’accès aux bonnes personnes, et garder une vue claire de ce qui se passe sans dépendre d’une boîte noire extérieure.

C’est pour cela qu’Ezkey a plusieurs écrans. Non pas pour ajouter de la complexité décorative, mais pour couvrir les responsabilités qu’un produit autonome doit prendre en charge.

## Ce qui manque encore, volontairement ou provisoirement

L’état actuel d’Ezkey n’est pas celui d’une suite déjà fermée, certifiée et équipée de tous les connecteurs attendus dans un contexte d’entreprise. C’est un choix assumé. Le projet préfère d’abord établir une plateforme principale cohérente, lisible et opérable, avant d’empiler les couches d’intégration.

Par exemple, Ezkey **n’intègre pas encore ses propres canaux d’envoi** pour des informations sensibles comme celles liées à un enrôlement. Il n’y a pas encore, dans le produit, de mécanisme natif pour envoyer un QR code par courriel ou un code de challenge par SMS. Pourtant, dans un écosystème réel, ce serait une direction très naturelle : transmettre, par exemple, le QR code par courriel et le code de défi à six chiffres par un autre canal comme le SMS. Ce n’est pas la seule approche possible, mais cela illustre bien la situation actuelle : **la plateforme existe, mais certains canaux périphériques restent à construire**.

Il faut être tout aussi clair sur le positionnement plus large. Ezkey n’est pas aujourd’hui un produit certifié, entériné ou porté par les grands écosystèmes établis. Là encore, c’est volontaire. Le projet assume de **faire table rase d’une partie de la complexité héritée** pour explorer une autre voie, plus directe, plus backend-first, plus autonome. C’est un pari audacieux. Il peut séduire autant qu’il peut diviser, mais il fait partie de l’ADN du projet.

## Ce que vous voyez, et ce que vous ne voyez pas

Sur exp1, les personnes invitées utilisent Ezkey dans une posture **Tenant Admin**. Concrètement, vous voyez les écrans utiles à la vie d’une organisation dans la plateforme :

- le **dashboard**,
- les **integrations**,
- les **enrollments**,
- les **auth attempts**,
- les **audit logs**,
- les **admins**,
- les **API keys**.

En revanche, vous ne voyez pas les zones réservées au **Global Admin**, par exemple la gestion globale des locataires, les alertes d’instance, ou les opérations sensibles autour des clés de chiffrement de la plateforme. Ce n’est pas une limitation arbitraire : c’est le reflet d’une séparation volontaire entre la **gestion du périmètre métier d’un locataire** et la **gestion de l’instance Ezkey elle-même**.

## Une console pensée pour opérer, pas pour impressionner

L’Admin UI Ezkey n’essaie pas de ressembler à une vitrine marketing ni à un cockpit surchargé. Sa philosophie est plus sobre : montrer l’essentiel, rendre les actions fréquentes compréhensibles, et laisser au back-end le rôle d’autorité.

Cette approche reflète aussi le positionnement d’Ezkey dans son ensemble. Ezkey n’essaie pas d’être un simple générateur de codes à six chiffres de plus, ni une variation habillée du monde des passkeys. Le produit suit une voie plus particulière : une MFA cryptographique, centrée back-end, avec un mobile qui participe réellement au flux de confiance. L’Admin UI sert cette logique. Elle ne raconte pas une histoire de gadgets ; elle donne une prise concrète sur un système qui veut rester lisible et maîtrisable.

Dans le même esprit, il faut comprendre que le projet avance aujourd’hui avec un **focus fort sur le noyau principal**. Dans un futur plus large, on peut très bien imaginer un écosystème de **ponts** ou de **plugins** pour raccorder Ezkey à d’autres réalités d’entreprise. Un exemple évident serait une intégration avec **Active Directory**, dans l’esprit de certains ponts de synchronisation que d’autres acteurs du marché ont su proposer. Pour beaucoup d’organisations, la gestion des accès et des rôles passe déjà par ce type d’annuaire. Un tel chantier pourrait favoriser l’adoption, mais il appartient encore à la suite du projet, pas à son état actuel.

## Le dashboard : point d’ancrage de la vue d’ensemble

Le dashboard est la meilleure porte d’entrée pour comprendre l’esprit de l’interface. Son rôle n’est pas de tout expliquer ; son rôle est de donner un **état de situation rapide**.

On y trouve d’abord une vue d’ensemble des principaux objets et de leur activité récente. L’idée est simple : en quelques secondes, un opérateur doit pouvoir se demander si son espace Ezkey ressemble à quelque chose de vivant, de vide, de sain, d’incomplet ou de problématique.

Le dashboard sert aussi de point de passage vers les écrans où l’on agit vraiment. Il rassemble les raccourcis vers les flux fréquents, montre l’activité récente utile, et évite d’obliger l’opérateur à parcourir l’interface à l’aveugle. Pour un expérimentateur qui découvre Ezkey, c’est aussi une manière de saisir rapidement la hiérarchie du produit : d’abord la vue d’ensemble, puis les objets qui structurent le reste.

## Les principales zones : ce qu’elles représentent

Les titres ci-dessous décrivent le **rôle** de chaque partie de l’Admin UI dans le modèle du produit — une carte mentale, pas une procédure à suivre dans l’ordre des clics.

### Integrations

L’écran **Integrations** représente les applications ou services que vous voulez protéger avec Ezkey. C’est un écran central, parce qu’une intégration sert ensuite de point d’ancrage à d’autres éléments, notamment les enrôlements et les clés API.

Autrement dit, l’intégration n’est pas un détail administratif. C’est l’unité qui relie Ezkey à un usage réel. Présenter les intégrations explicitement dans l’Admin UI aide à garder une frontière claire entre le système Ezkey et les applications qui s’y branchent.

### Enrollments

L’écran **Enrollments** est celui où l’on voit le lien entre un contexte utilisateur, une intégration et un appareil mobile. Là encore, Ezkey ne traite pas cela comme un simple “compte activé”. Un enrôlement représente une relation établie dans le modèle du produit, avec son propre cycle de vie.

Dans une expérimentation comme exp1, cet écran est particulièrement important, parce qu’il rend visible la manière dont Ezkey pense l’attachement d’un appareil, son état, et les actions possibles autour de ce lien. On comprend ici qu’Ezkey ne veut pas seulement “faire passer une validation” ; il veut garder un état administrable de la relation entre le système, l’utilisateur et le mobile.

### Auth Attempts

L’écran **Auth Attempts** rend visibles les tentatives d’authentification. C’est l’un des endroits où l’approche Ezkey devient la plus concrète : au lieu de laisser l’authentification dans une boîte noire, l’interface montre qu’il existe des tentatives, des états, des résultats, des délais et un historique d’exécution.

Pour un opérateur, cela aide à répondre à des questions très pratiques : qu’est-ce qui a été demandé, qu’est-ce qui a abouti, qu’est-ce qui a expiré, qu’est-ce qui a été refusé. Pour Ezkey, c’est cohérent avec une philosophie où l’authentification reste un processus backend compréhensible, pas seulement une sensation d’instantanéité côté interface.

### Audit Logs

L’écran **Audit Logs** donne une vue humaine des événements importants. Dans un produit qui veut rester opérable et autonome, l’audit n’est pas un luxe. C’est une manière de garder une mémoire des actions et des changements.

Du point de vue Tenant Admin, cet écran sert surtout au suivi de sécurité et à l’investigation opérationnelle courante. Vous n’y voyez pas l’ensemble des mécanismes de supervision d’instance réservés au Global Admin, mais vous avez accès à ce qui permet de comprendre l’activité utile dans votre périmètre.

### Admins

L’écran **Admins** permet de gérer les administrateurs du locataire. C’est là qu’on retrouve une autre idée importante d’Ezkey : l’administration elle-même fait partie du système de sécurité. Gérer qui peut opérer, qui peut être activé, qui doit être désactivé, ou comment un pair reçoit son matériel d’onboarding fait partie du produit, et non d’un bricolage périphérique.

Pour exp1, cet écran est particulièrement parlant, parce qu’il montre qu’Ezkey ne sépare pas artificiellement la MFA des questions d’organisation humaine. L’accès administratif a son propre cycle de vie, ses propres garde-fous et sa propre discipline.

### API Keys

L’écran **API Keys** rappelle qu’Ezkey n’est pas seulement une interface pour humains. La plateforme doit aussi pouvoir s’insérer dans des systèmes réels. Les clés API donnent donc une place claire à l’authentification machine-à-machine et aux workflows d’intégration.

Le fait que cet écran soit présent dans la surface Tenant Admin dit quelque chose d’important sur la philosophie du produit : un locataire n’est pas seulement un utilisateur final d’un tableau de bord. C’est aussi un opérateur qui doit pouvoir connecter ses propres systèmes à Ezkey dans un cadre explicite et administrable.

## Pourquoi cette organisation

Si l’on regarde l’ensemble, l’Admin UI Ezkey est organisée autour d’une idée simple : **chaque écran correspond à une responsabilité réelle**.

Le dashboard donne l’état général. Les integrations définissent le périmètre applicatif. Les enrollments rendent visible le lien entre utilisateurs, applications et appareils. Les auth attempts montrent la vie de l’authentification. Les audit logs conservent la trace. Les admins gèrent le facteur humain de l’administration. Les API keys couvrent la relation avec les systèmes.

Cette structure n’est pas là pour “faire enterprise”. Elle est là parce qu’Ezkey assume qu’un système d’authentification sérieux, autonome et self-hosted doit pouvoir être compris et opéré sans magie.

## Ce qu’il faut retenir avant d’expérimenter

Ce texte vise un **cadre de lecture** : il ne remplace pas un tutoriel pas à pas ni la pratique dans l’interface.

Si vous découvrez Ezkey pour la première fois, le meilleur réflexe n’est pas de chercher immédiatement toutes les options avancées. Le bon réflexe est plutôt de lire l’interface comme une carte :

1. **Dashboard** pour comprendre le terrain.
2. **Integrations** pour voir où Ezkey s’attache au métier.
3. **Enrollments** pour comprendre le rôle du mobile et des liens établis.
4. **Auth Attempts** et **Audit Logs** pour voir comment Ezkey rend l’activité observable.
5. **Admins** et **API Keys** pour comprendre que la plateforme prend au sérieux autant les personnes que les systèmes.

À partir de là, l’Admin UI cesse d’être un ensemble d’écrans abstraits. Elle devient ce qu’elle veut être : une console sobre, pensée pour exploiter une plateforme MFA cryptographique qui reste sous contrôle de l’organisation.

---

[← ezkey.org (français)](/fr/)
