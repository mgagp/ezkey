---
status: draft
audience: "Développeurs et leads qui suivent l’évolution des agents IA ; retour d’expérience personnel, voix en je, lane « Craft & engineering ». Ton collégial québécois. Lecteur froid. Leitmotiv « comme avec les humains ». Préambule bots = clins d’œil ; auteur = humain. Vocabulaire QC : collaboration, équipe AI, ingénierie, consultatif, sécurité, tests exploratoires."
planned_slug_fr: "elever-l-intention-pas-une-panacee.html"
planned_slug_en: "raising-intention-not-a-panacea.html"
planned_canonical: "https://ezkey.org/fr/elever-l-intention-pas-une-panacee.html"
source_of_truth: draft
---

<!-- ezkey-org:exclude-start
Editorial context (not publishable):
- 2026-09-26: passe fluidité « Remettre la pensée en ordre » + transitions globales (sans tout réécrire le malentendu).
- Cross-links: steering-by-intention, l-intention-ailes, from-code-to-intent, pensee-critique.
- Cold reader: no A/B/C, no PR numbers. No publish without Marc.
ezkey-org:exclude-end -->

# Élever l’intention, pas une panacée

## Petite mise en contexte

Avant d’entrer dans le récit : les collaborateurs dont je parle — Patrick, Christophe, Alex, Isabelle, Mathieu, et d’autres — forment mon équipe AI. Ce sont des bots spécialisés. Leurs noms sont des clins d’œil, un peu humoristiques, à mon passé personnel et professionnel. Moi, je reste l’humain dans la boucle. Eux, des agents. Cette distinction compte pour lire ce qui suit sans confusion.

## L’essentiel, d’abord

Sous Ezkey, j’ai récemment dû choisir. Continuer en solo dans l’IDE, mon mode *slow AI* habituel — un fil, un worktree, une charge cognitive que je contrôle — ou pousser plus loin l’élévation d’intention avec cette équipe AI. J’ai choisi la collaboration. Pas par mode, par conviction : c’est là que se joue, pour moi, une part de l’avenir du métier.

Ça n’a pas tout aligné comme par magie. Comme avec les humains. Plus on avançait sur un chantier concret — donner à l’évaluateur un vrai moment dans le produit sans le forcer tout de suite à lier un appareil — plus je me suis rendu compte que mon cerveau humain peinait à tenir le tout. Et que les divergences entre bots, loin d’être un simple problème de processus, étaient souvent le canari : l’intention n’était pas assez claire.

## Un lab, pas une démonstration

Ezkey me sert de laboratoire. Expérimenter, synthétiser, vivre l’évolution de la profession telle qu’elle se redessine en 2026 et après. Pas pour prouver que les agents remplacent quelqu’un. Pour voir ce que ça fait, vraiment, d’élever l’intention produit avec une équipe AI qui a de la mémoire, des rôles, et des avis qui ne se ressemblent pas toujours.

Le départ, c’était une idée. Puis un débat. Puis la collaboration. Patrick a remis en question la portée, le caractère temporaire, le cycle de vie d’une échéance — côté ingénierie, à titre consultatif. Christophe a tenu une garde qui, en cas de doute, bloque : il poussait vers une révocation dure, une coupure nette. Alex a porté l’intention face à l’ingénierie et à la sécurité. Isabelle a fait des tests exploratoires, avec des résultats clairs et des preuves, et a remonté la friction. Mathieu a coordonné — et, surtout, il a stoppé le va-et-vient entre options quand on tournait en rond, pour forcer un choix de mon côté avant de relancer le développement.

Il y a eu de la tension. Utile. Pas toujours confortable. Des petits soucis CORS, de limite de débit, d’ajustements de pile technique : le genre de friction qu’on s’attend à voir. Rien de spectaculaire. Juste le travail, avec un humain et des bots dans la même boucle.

En élaborant ce retour d’expérience, une phrase m’est revenue plusieurs fois : ce qu’on vivait étape par étape, ce sont les mêmes enjeux que ce qui se produit avec des équipes humaines. L’interaction collaborative d’intention avec les bots élève le jeu. Ce n’est pas une panacée — comme avec les humains. Et c’est précisément sur ce chantier-là que le malentendu s’est joué.

## Le malentendu avait un nom

Concrètement, voilà ce qu’on essayait de bâtir. Sur ezkey.org, l’évaluateur s’active comme avant. Il se connecte avec son code d’activation, comme avant. Ensuite, deux suites possibles : soit il lie un appareil et s’enrôle pour de bon, soit — et c’était ça, mon intention — il obtient une session temporaire. Quelques heures pour se promener dans le produit, sans téléphone en main, sans devoir finir l’enrôlement tout de suite. Accompagner quelqu’un qui découvre Ezkey, pas le coincer derrière un mur « appareil obligatoire ».

Ce n’est pas ce que l’équipe AI a d’abord poursuivi. Une partie du travail a glissé vers une version plus étroite du parcours : moins de liberté, plus de garde-fous, presque un parcours d’enrôlement à compléter. L’interface le montrait bien — des libellés du genre « Terminer l’enrôlement », un Admin UI qui ressemble à un petit enclos. Isabelle, en testant, voyait un chemin qui « passait » au clic, mais qui ne racontait pas la même histoire que celle que j’avais en tête. Patrick a fini par le nommer clairement : on était en train de peaufiner la mauvaise chose. Mathieu a forcé l’arrêt du va-et-vient. Moi, j’ai dû dire tout haut : non, la destination ce n’est pas « finir le parcours bridé » ; c’est « pouvoir naviguer un moment sans appareil ».

La clarification majeure, pour moi, a été celle-ci : on avait un malentendu d’intention, malgré l’effort de clarification. Les bots qui tiraient dans des sens différents n’étaient pas simplement « mal synchronisés ». Ils révélaient que ce n’était pas clair — exactement comme des humains le feraient autour d’une table mal cadrée. Quand j’ai envoyé une intention mal bornée, s’en est suivie une escalade probabiliste distribuée entre collaborateurs-bots. Ce n’est pas un défaut isolé du cadre multi-agents : c’est ce qui arrive dès qu’une direction floue rencontre plusieurs agents capables. Comme avec les humains, le diagnostic m’a fait plus de bien qu’une autre passe de consignes.

## Remettre la pensée en ordre

À partir de là, on pouvait enfin parler net. L’activation et le code de connexion, on ne les touchait pas — c’était déjà dit. Ce qu’il fallait trancher, c’était la vie de la session temporaire elle-même. Combien de temps elle dure. Ce qui se passe quand elle expire. Si on en ouvre une nouvelle, est-ce qu’elle remplace la précédente. Qu’est-ce qu’on fait si un second admin est déjà lié. Et surtout : on n’efface pas tout d’un coup sec.

Là, Christophe et le produit ne disaient pas la même chose — et c’était sain. Lui tenait le plafond sécurité : en cas de doute, on coupe. De mon côté, l’intention restait d’accompagner l’évaluateur. On a donc tranché un levier utilisable : désactiver le tenant en douceur, puis permettre une réactivation globale, tracée, plutôt qu’une coupure et un effacement trop brutaux. Doux, ce n’est pas faible — à condition que l’échéance et l’interdiction de se réactiver tout seul soient vraiment testées. Une fois ce cadre posé, avec ce que la chose *n’est pas* aussi clairement que ce qu’elle est, le faux problème « finir le parcours bridé » est tombé de lui-même. Alex avait raison de vouloir figer vision et critères avant le code ; mes décisions explicites valaient mieux que laisser flotter.

Le reste a suivi un ordre simple. D’abord verrouiller l’intention. Ensuite formaliser le chantier. Seulement après, développer. Un seul consolidateur — Mathieu — une décision de mon côté, puis le travail d’équipe. Pas l’inverse. Et surtout pas « coder d’abord, comprendre après » : plusieurs agents capables amplifient ce réflexe très vite. J’ai déjà écrit, dans [Piloter par l’intention, fermer la boucle](/fr/steering-by-intention-closing-the-loop.html), à quel point la boucle ne tient que lorsque « complété » est explicite et observable. Ici, c’était la même exigence, un cran plus tôt : borner l’intention *avant* que l’équipe AI ne se mette en marche.

## Ce que je retiens

Plusieurs agents spécialisés ouvrent des portes. Chacun avec sa personnalisation, ils élèvent la capacité à porter une intention plus large que ce qu’un seul fil de discussion peut tenir. Il ne faut pas pour autant [couper les ailes au moteur probabiliste](/fr/l-intention-prochaine-frontiere-partenariat-humain-ia.html) : les voies ouvertes restent une force. Mais ultimement, malgré l’évolution incroyable des modèles, [parler avec l’IA reste une façon très sophistiquée de se parler à soi-même](/fr/from-code-to-intent-ai-workflow.html). Sans jugement critique — sans borner clairement ce qui doit être testé et les surfaces à couvrir, comme je l’ai rappelé dans [Pensée critique, chambres d’écho et alignement d’idées](/fr/pensee-critique-chambres-echo-et-alignement-idees-ia.html) — on accélère surtout le malentendu.

Ce n’est pas une panacée. Comme avec les humains. Le débat jusqu’au choix difficile — tout jeter, repartir de zéro — arrive aussi avec des équipes de chair et d’os. Ce n’est pas un échec de l’outil. C’est le métier. Et sous Ezkey, pour l’instant, c’est exactement le genre de friction que je veux vivre de près, sans la maquiller.
