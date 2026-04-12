## Plan: Standardiser le client Auth API mobile

## Statut

Complété le 2026-04-11.

Résultat atteint:

- `ezkey_mobile` versionne désormais sa spec Auth API locale.
- Le refresh de spec passe par les scripts racine centralisés.
- Le sous-projet mobile expose une génération locale via Orval.
- Les wrappers mobiles conservent la logique métier au-dessus du client généré.
- Les DTO générés sont devenus la source de vérité contractuelle côté mobile.
- La documentation README et AGENTS a été réalignée sur cette gouvernance.
- La transition a été validée par typecheck, tests ciblés, build Android propre et validation réelle sur téléphone.

## Constat

Le constat principal reste net.

- Le mobile React Native consomme aujourd'hui l'Authentication API via des DTO et clients HTTP écrits à la main dans [ezkey_mobile/app/services/api/types.ts](ezkey_mobile/app/services/api/types.ts), [ezkey_mobile/app/services/api/enrollments.ts](ezkey_mobile/app/services/api/enrollments.ts) et [ezkey_mobile/app/services/api/authAttempts.ts](ezkey_mobile/app/services/api/authAttempts.ts).
- Le repo dispose déjà d'une source de vérité OpenAPI pour l'Auth API dans [specs/auth-api/openapi-spec.json](specs/auth-api/openapi-spec.json), alimentée via [scripts/update-specs.sh](scripts/update-specs.sh).
- L'Admin UI utilise déjà Orval avec une commande dédiée dans [ezkey-admin-ui/package.json](ezkey-admin-ui/package.json) et une configuration centralisée dans [ezkey-admin-ui/orval.config.ts](ezkey-admin-ui/orval.config.ts).
- Le mobile dispose déjà des briques compatibles avec ce modèle, notamment Axios et TanStack Query v5 dans [ezkey_mobile/package.json](ezkey_mobile/package.json).

## Statut actuel de l'autonomie du sous-projet mobile

À ce stade, le sous-projet mobile n'est pas encore autonome sur cet aspect.

- Il n'existe pas actuellement de fichier versionné [ezkey_mobile/openapi-spec.json](ezkey_mobile/openapi-spec.json).
- Il n'existe pas actuellement de commande équivalente à `generate:api` dans [ezkey_mobile/package.json](ezkey_mobile/package.json).
- Le script global [scripts/update-specs.sh](scripts/update-specs.sh) ne redispatche pas encore la spec Auth API vers `ezkey_mobile/`.

Conclusion pratique: aujourd'hui, le mobile consomme l'Auth API de manière manuelle, sans spec locale versionnée ni pipeline de génération locale comparable à l'Admin UI.

## Recommandation cible

Je recommande de faire évoluer le mobile vers un modèle spec-driven, avec les principes suivants:

- La source unique de vérité pour le contrat reste la spec OpenAPI produite par le backend Auth API.
- Le sous-projet mobile doit versionner sa propre copie locale de la spec, par exemple [ezkey_mobile/openapi-spec.json](ezkey_mobile/openapi-spec.json), afin d'être autonome et reproductible.
- Cette copie locale ne doit jamais être modifiée à la main.
- La mise à jour de cette copie locale doit se faire uniquement via [scripts/update-specs.sh](scripts/update-specs.sh), dans le workflow explicite où le stack Docker est démarré proprement puis les specs sont récupérées, formatées et dispatchées vers les sous-projets.
- Le mobile doit exposer une commande locale de génération, par exemple `yarn generate:api`, sur le même principe que l'Admin UI.
- Orval est le candidat recommandé pour générer les DTO et la couche client bas niveau.
- Les couches métier mobiles doivent rester hand-written: signatures crypto, proof tokens, coercions protocolaires, polling, stratégie offline/retry, overrides d'URL par enrollment, et toute logique d'orchestration spécifique au protocole Ezkey.

Autrement dit: générer le contrat et le transport bas niveau, garder la logique sensible et spécifique au mobile dans une couche mince et explicite.

## Note conceptuelle: ce que Orval génère, et ce qu'il ne faut pas lui demander

Il est utile de clarifier le rôle exact d'Orval.

- Orval génère à partir de la spec les types TypeScript, les fonctions d'appel HTTP, et éventuellement des hooks React Query selon la configuration.
- Orval ne remplace pas la conception applicative.
- Orval ne connaît pas les choix UX du mobile: pagination visuelle, infinite scroll, retry policy métier, polling déclenché par l'utilisateur, ou orchestration crypto.

La bonne séparation mentale est la suivante:

1. Contrat API: types, chemins, méthodes, payloads. C'est le terrain naturel de la génération.
2. Accès aux données: query, mutation, cache, invalidation. Orval peut aider ici lorsqu'on utilise React Query.
3. Politique produit et UX: pagination, scrolling, rafraîchissement, orchestration d'écrans, sécurité applicative. Cela reste applicatif et doit rester sous contrôle explicite du projet.

## Note conceptuelle: pagination Orval côté Admin UI et intérêt côté mobile

L'Admin UI ne repose pas sur une pagination "magiquement générée" par Orval.

- Orval y génère surtout la couche contractuelle et les hooks React Query via [ezkey-admin-ui/orval.config.ts](ezkey-admin-ui/orval.config.ts).
- La gestion pratique de la pagination est encapsulée dans un wrapper applicatif maison, [ezkey-admin-ui/src/hooks/use-paginated-orval.ts](ezkey-admin-ui/src/hooks/use-paginated-orval.ts), qui ajoute l'état `page`, `size`, `sort` et les helpers de navigation.

Donc, le vrai pattern n'est pas "Orval génère la pagination", mais "Orval génère le client, puis l'application ajoute une abstraction de pagination adaptée à son UX".

Pour le mobile, cette approche est techniquement possible, mais elle n'a pas d'intérêt immédiat pour l'Auth API actuelle.

- Les endpoints Auth API actuellement consommés par le mobile sont `bind`, `verify`, `pending` et `respond`.
- Ce sont des endpoints d'action, pas des endpoints de liste paginée.
- Il n'y a donc actuellement rien à paginer dans le périmètre mobile Auth API.

La bonne conclusion est la suivante:

- Orval reste pertinent pour le mobile comme générateur de types et de clients/hooks React Query.
- Le sous-pattern "pagination Admin UI" n'est pas un argument décisif aujourd'hui pour le mobile.
- Si, plus tard, le mobile expose des listes riches comme un historique d'authentifications, un journal d'événements, ou une liste paginée de notifications, alors il deviendra utile de créer un wrapper mobile spécifique au-dessus du client généré.
- Ce wrapper mobile devra probablement être pensé davantage autour de `useInfiniteQuery` ou d'un modèle de scroll progressif que d'une pagination web classique par pages numérotées.

## Politique de build et de gouvernance à expliciter dans le plan

Cette politique doit être explicitement écrite dans le plan, puis répercutée dans la documentation du sous-projet.

- Le fichier OpenAPI versionné dans un sous-projet est un artefact généré et versionné, pas une source primaire.
- On ne modifie jamais directement un `openapi-spec.json` dans un sous-projet.
- La seule manière autorisée de mettre à jour les specs est d'utiliser le script global [scripts/update-specs.sh](scripts/update-specs.sh) ou son équivalent Windows.
- Le workflow humain de référence est: démarrer un stack Docker propre, attendre que les APIs soient disponibles, puis exécuter explicitement `update-specs` pour récupérer, formater et dispatcher les specs vers les sous-projets.
- L'autonomie des sous-projets repose précisément sur ce dispatch explicite: chaque sous-projet versionne ensuite sa copie locale de la spec nécessaire à son fonctionnement.

Ce point est important pour les développeurs comme pour un assistant de codage: la spec locale est versionnée, mais elle n'est jamais éditée à la main.

## Évolutions à inclure dans le plan

### 1. Rendre `ezkey_mobile` autonome sur la spec Auth API

- Ajouter le dispatch de la spec Auth API vers [ezkey_mobile/openapi-spec.json](ezkey_mobile/openapi-spec.json) dans [scripts/update-specs.sh](scripts/update-specs.sh) et dans le script Windows équivalent.
- Versionner ce fichier dans `ezkey_mobile/`.
- Documenter explicitement qu'il s'agit d'une copie locale dérivée de la source de vérité backend via le script de mise à jour.

### 2. Ajouter une génération locale côté mobile

- Ajouter Orval aux dépendances de développement du mobile.
- Ajouter une configuration locale, par exemple `orval.config.ts`, dans `ezkey_mobile/`.
- Ajouter une commande `generate:api` dans [ezkey_mobile/package.json](ezkey_mobile/package.json), alignée conceptuellement avec [ezkey-admin-ui/package.json](ezkey-admin-ui/package.json).
- Générer les DTO et le client bas niveau dans un dossier dédié, versionné ou régénéré selon la politique retenue.

### 3. Conserver une couche mobile hand-written au-dessus du généré

- Garder des wrappers ou hooks applicatifs pour la signature, les proof tokens, le polling manuel, la gestion d'erreurs orientée UX mobile, et l'usage éventuel d'URL Auth API spécifiques à un enrollment.
- Ne pas enfouir la logique crypto ou les décisions métier dans le généré.

### 4. Ajouter la documentation de gouvernance dans README et AGENTS

Le plan doit prévoir une petite mise à jour de [ezkey_mobile/README.md](ezkey_mobile/README.md) et [ezkey_mobile/AGENTS.md](ezkey_mobile/AGENTS.md) pour expliciter:

- que le sous-projet mobile utilise un générateur de code pour son client Auth API;
- quelle est la commande exacte de génération, par exemple `yarn generate:api`;
- que le fichier `openapi-spec.json` local est versionné pour rendre le sous-projet autonome;
- qu'il ne doit jamais être modifié à la main;
- que toute mise à jour de spec doit passer par [scripts/update-specs.sh](scripts/update-specs.sh) après démarrage explicite du stack par l'humain;
- qu'un assistant de codage doit respecter cette règle et ne jamais "corriger" la spec locale directement.

### 5. Validation de la transition

- Générer d'abord dans un namespace parallèle.
- Comparer systématiquement le généré et le manuel existant sur les 4 endpoints actuels.
- Vérifier les types, les noms de champs, les nullability, les codes de réponse et les transformations locales encore nécessaires.
- Migrer les consommateurs par slices, puis supprimer l'ancien manuel seulement après équivalence démontrée.

## Résumé de position

Je ne recommande pas le statu quo manuel comme standard cible.

Je recommande:

- une spec Auth API locale versionnée dans `ezkey_mobile/`;
- une alimentation exclusive par le script global de mise à jour des specs;
- une commande locale de génération dans le sous-projet mobile;
- Orval comme générateur recommandé;
- une couche mobile explicite et non générée pour l'orchestration protocolaires et les comportements UX spécifiques.

La pagination générée au sens Admin UI n'a pas d'utilité immédiate pour le mobile actuel, mais le pattern général Orval + wrapper applicatif est, lui, pleinement pertinent.
