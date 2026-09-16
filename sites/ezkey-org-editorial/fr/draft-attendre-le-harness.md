---
status: draft
audience: "Développeurs et leads qui suivent l’évolution des agents IA ; retour d’expérience personnel, voix en je, lane « Craft & engineering ». Ton sobre, court (lecture ~3–4 min), ancré dans le vécu Ezkey. Outils nommés comme faits situés (Cursor, Grok Bot, Docker) — pas de pitch produit."
planned_slug_fr: "attendre-le-harness.html"
planned_slug_en: "waiting-for-the-harness.html"
planned_canonical: "https://ezkey.org/fr/attendre-le-harness.html"
source_of_truth: draft
---

<!-- ezkey-org:exclude-start
Editorial context (not publishable):
- Lane: Craft & engineering. Companion tone to hygiene-continue, ablation, intention — short field note, not a long essay.
- Thesis: recent significance is less « smarter models alone » than harness + integration (orchestration, memory, dedicated computers, cloud parallel work, and now phone→desktop remote execution when Grok Bot is open on the workstation).
- Lived arc: chat agents → plan mode → worktrees/multi-agents → Cursor cloud builds (Ezkey Docker single-instance / ports) → Grok Bot (specialized teammates) → early phone/desktop distributed pilotage.
- Naming: Cursor / Grok Bot / Docker as situated facts of the path — same register as Docker; never product pitch, never parity claims, never tutorial.
- Intentional delay on skills & agent customization = cognitive load management (slow AI); MCP wake → minimal hygiene skills; full specialist team only once harness felt mature.
- Honesty: preliminary — just starting to use the team; phone→poste remote jobs are a fresh discovery, not a mature playbook. Cursor Project mode: suspicion only, not claimed lived experience.
- Do not invent security/product maturity claims. Product name: Ezkey. Avoid Duo/Okta/Keycloak parity. Avoid corporate hype.
- Cross-links for HTML later: hygiene-continue-du-code, ablation-methodologie (or published ablation slug), l-intention-prochaine-frontiere, facture-ia / pas-de-côté if slow AI fits.
- Length target: ~550–750 words FR.
- Decision 2026-09-16: name Cursor and Grok Bot soberly, like Docker — facts of the journey, not pitch.
- Decision 2026-09-16: light beat on phone→desktop remote execution via Grok Bot (app open on poste); keep short, in « Encore préliminaire ».
ezkey-org:exclude-end -->

# Attendre le harness

## L’essentiel, d’abord

Ce qui me frappe le plus dans les évolutions récentes des agents, ce n’est pas seulement l’autonomie ou la puissance des modèles. C’est le harness : l’intégration, la mémoire, l’ordinateur dédié, la capacité à orchestrer plusieurs agents sans tout perdre dans un fil de discussion.

Sous la loupe d’Ezkey, c’est devenu concret. Pas une théorie de plus. Une façon de travailler que j’attendais sans vouloir la forcer trop tôt.

## Une progression que j’ai suivie à mon rythme

Dans Cursor, on a commencé avec des agents dans le chat, quelques prompts, un peu de code. Puis le mode plan, pour ne plus dilapider l’analyse. Ensuite plusieurs agents sur des worktrees. Plus récemment, les builds cloud.

Chez Ezkey, ce dernier pas a réglé un problème très terre à terre. Sur mon poste, Docker ne me laisse tourner qu’une instance à la fois : je n’ai pas voulu porter la complexité accidentelle d’une gestion de ports multi-instances. Les builds cloud de Cursor ont contourné ça d’un coup. Plusieurs tâches en parallèle, chacune sur sa machine, souvent à partir de `main`. Pour moi, c’était une avancée majeure — moins spectaculaire qu’un nouveau modèle, plus utile au quotidien.

L’héritage de l’IDE reste là : fenêtres, worktrees, coordination encore trop proche du mode développement classique. La suite logique, telle que je la vis maintenant avec Grok Bot, c’est un niveau au-dessus : une équipe de collaborateurs spécialisés — sécurité, déploiement, produit, ingénierie, mobile, tests — chacun avec sa mémoire, son ordinateur virtuel, et la possibilité de lancer du travail cloud. Le dialogue peut s’élever entre eux. Ce n’est plus seulement un agent dans un onglet ; c’est une forme d’abstraction et de récurrence que je n’avais pas eue auparavant.

## Pourquoi j’ai tardé volontairement

Début 2025, Ezkey baignait déjà dans des nouveautés autour du multi-agents et des skills. J’ai volontairement retardé l’adoption des couches les plus structurantes. Pas par mépris. Pour garder la charge cognitive basse et avancer le concret du projet pendant que l’industrie accélérait.

Entre paresse assumée et volonté éclairée — ce que j’appelle ici mon mode *slow AI* — j’ai surfé le milieu. L’adoption ne faisait aucun doute ; la forme finale, si. Attendre m’a permis de capitaliser sur la qualité d’interaction avec l’agent, plutôt que de disperser mon attention en early adopter de chaque volet.

Les MCP, un échange avec un ancien directeur et ami m’y a éveillé. J’en ai tiré surtout des skills d’hygiène de code, gardés au minimum. Ça me suffit. La personnalisation poussée d’agents, en revanche, je la repoussais encore : trop de complexité pour mon workflow, tant que le cadre d’équipe n’était pas là.

C’est l’arrivée de Grok Bot, dans Cursor, qui a servi de déclencheur. Pas un détour raté : un détour utile. Pour être early adopter d’agents personnalisés, il valait la peine d’attendre que le harness rende l’équipe réelle.

## Encore préliminaire

Je commence à peine à exploiter ces collaborateurs. J’aurai plus à dire. Déjà, un détail d’intégration m’a surpris : avec Grok Bot ouvert sur mon poste, je peux depuis le téléphone lancer certains jobs qui s’exécutent là-bas. L’équipe suit sur les deux appareils ; le pilotage n’exige plus d’être physiquement devant l’écran. Il y a peu, mon flux s’arrêtait au bureau. Là, le harness commence à tenir un mode distribué que je n’osais pas espérer si vite.

Le mode projet que je vois apparaître dans Cursor me semble, en soupçon seulement — je ne l’ai pas encore vraiment utilisé — une porte d’entrée possible vers une prise de décision encore plus déplacée vers cette couche de coordination. Je préfère le nommer comme hypothèse plutôt que comme vécu.

Ce que je peux affirmer déjà : le pattern se tient. Worktrees et cloud pour le parallèle ; Grok Bot pour l’intention, la continuité entre spécialistes, et maintenant un fil de pilotage qui traverse le téléphone et le poste. Sous Ezkey, c’est la réalisation concrète de ce que j’attendais d’un mode agent distribué. Il est temps d’embarquer — sans précipiter la suite, et sans prétendre que le récit est terminé.
