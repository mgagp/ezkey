## Mini-RFC produit — Onboarding expérimental tenant admin “recovery-codes-first”

### Contexte

EZKey veut préparer un pré-pilote expérimental à faible volume, destiné à un public restreint de développeurs ou d’early adopters capables d’installer un APK debug Android et d’accepter une posture plus expérimentale que celle d’un produit prêt pour une diffusion large.

Dans ce cadre, le provisioning standard d’un tenant admin est trop couplé à l’enrollment immédiat. L’objectif de ce mini-RFC est de planifier une petite évolution produit qui rende explicite un mode d’onboarding où un tenant admin peut être provisionné pour démarrer à partir de recovery codes, puis compléter lui-même l’enrôlement de son appareil.

### Problème à résoudre

Aujourd’hui, le modèle de provisioning admin crée un admin, génère des recovery codes, et crée en même temps un enrollment admin. Pour le pré-pilote expérimental, le besoin est légèrement différent:

- le Global Admin veut pouvoir créer rapidement un tenant et un tenant admin pour un participant externe;
- le participant doit pouvoir recevoir par courriel un paquet minimal d’accès expérimental;
- ce paquet doit permettre au participant de démarrer seul le parcours de première liaison avec l’application mobile;
- le système doit rester cohérent avec la philosophie actuelle d’EZKey: recovery codes contraints, audit explicite, appareil mobile comme base du fonctionnement normal.

Le problème n’est donc pas seulement technique. Il est aussi produit et opératoire: il faut éviter un flux ambigu où les recovery codes deviennent un quasi-login permanent ou où l’état réel de l’admin devient difficile à comprendre.

### Objectif

Définir la plus petite évolution produit qui permet un onboarding expérimental clair, auditable, réutilisable, et simple à opérer.

### Résultat recherché

Le résultat attendu de la future session de planification est une recommandation nette sur:

- le comportement cible du produit;
- le plus petit scope API/UI/domain nécessaire;
- les impacts sur audit et sécurité;
- le parcours opérateur et le parcours utilisateur final;
- les non-objectifs à conserver pour éviter de surconcevoir.

### Parcours cible envisagé

1. Un développeur intéressé demande un accès expérimental via courriel.
2. Le Global Admin accepte la demande.
3. Le Global Admin crée un tenant dédié.
4. Le Global Admin crée un tenant admin dans un mode spécial d’onboarding expérimental.
5. Le système génère le matériel de départ nécessaire, en particulier les recovery codes.
6. Le Global Admin envoie au participant un courriel contenant:
   - l’URL de l’Admin UI,
   - le lien de téléchargement de l’APK expérimental,
   - les recovery codes,
   - une procédure courte d’installation et de premier enrôlement.
7. Le participant installe l’application mobile, accède à l’Admin UI, et utilise le mécanisme prévu pour initier ou reprendre son parcours d’enrôlement.
8. Le participant lie son appareil, complète le flow bind/verify, puis utilise ensuite EZKey selon le mode normal.

### Principes de conception à préserver

- Rester pragmatique et proche du modèle existant.
- Introduire le minimum de complexité accidentelle.
- Éviter que les recovery codes deviennent un mode d’authentification nominal.
- Préserver un audit explicite des états et transitions importants.
- Rendre le comportement compréhensible dans l’Admin UI pour un opérateur.
- Garder le flux suffisamment simple pour un public technique autonome.

### Options à comparer

#### Option A — Enrollment différé explicite

Ajouter un mode explicite de provisioning admin où l’admin est créé, les recovery codes sont générés, mais l’enrollment est différé ou marqué comme non encore activé.

Points à examiner:

- besoin d’un flag API explicite au moment de la création d’admin;
- besoin d’un état métier visible côté UI et audit;
- besoin éventuel d’un endpoint d’activation ou d’amorçage d’enrollment;
- impact sur les entités, validations, et invariants existants.

Avantages:

- modèle produit plus clair;
- séparation nette entre provisioning admin et liaison de l’appareil;
- meilleure lisibilité fonctionnelle et opératoire.

Inconvénients:

- demande probablement un peu plus de travail côté domaine/API/UI;
- risque d’introduire une nouvelle nuance de cycle de vie admin/enrollment.

#### Option B — Enrollment existant mais parcours de reprise explicite

Conserver le comportement actuel de création d’un enrollment, mais formaliser un mode supporté où les recovery codes servent à enclencher proprement un reset, une activation, ou une reprise contrôlée du parcours d’enrôlement.

Points à examiner:

- est-ce suffisamment clair pour les opérateurs;
- est-ce acceptable comme posture produit;
- quelle ambiguïté reste-t-il entre admin créé, enrollment existant, et appareil réellement lié;
- quel risque d’effet de bord sur la logique de recovery actuelle.

Avantages:

- scope potentiellement plus petit;
- plus proche de l’implémentation actuelle.

Inconvénients:

- modèle potentiellement moins propre;
- risque de confusion fonctionnelle;
- peut ressembler à un contournement plutôt qu’à une vraie capacité produit.

### Recommandation de départ

La planification devrait partir avec un biais favorable pour l’option A, puis vérifier si son coût reste raisonnable. Si l’option A s’avère trop lourde pour la valeur visée dans le pré-pilote, l’option B peut servir de variante plus légère, mais elle ne devrait être retenue que si le parcours reste réellement explicable, auditable, et simple à opérer.

### Questions de conception à trancher

1. Le produit doit-il autoriser un admin sans enrollment actif au moment de la création?
2. Faut-il un vrai état métier de type “enrollment deferred”, “pending setup”, ou équivalent?
3. Quel est le contrat API minimal pour rendre le mode explicite sans créer une surface trop large?
4. Quelle UX minimale faut-il dans l’Admin UI pour éviter la confusion chez le Global Admin?
5. Quelle UX faut-il pour le participant afin qu’il comprenne qu’il doit terminer un premier enrôlement, pas seulement se connecter?
6. Comment conserver la logique actuelle des recovery codes comme mécanisme contraint, et non comme substitut permanent à l’appareil lié?
7. Quels événements d’audit doivent distinguer:
   - création standard d’admin,
   - création d’admin en mode expérimental,
   - activation ou reprise d’enrollment,
   - régénération éventuelle de recovery codes,
   - échec du parcours initial?

### Contraintes et garde-fous

- Ne pas transformer ce chantier en refonte générale du modèle d’onboarding admin.
- Ne pas créer un second mode d’authentification durable parallèle à l’appareil mobile.
- Ne pas introduire une UX trop sophistiquée pour un besoin temporaire ou de faible volume.
- Ne pas casser la cohérence avec la posture break-glass actuelle.
- Ne pas négliger les cas d’échec initiaux: perte des recovery codes, abandon du participant, reset nécessaire avant premier bind réussi.

### Impacts à analyser

#### Produit

- clarté du modèle d’administration;
- compréhension par le Global Admin;
- compréhension par le participant;
- cohérence avec les valeurs Simplicity et Pragmatism.

#### API et domaine

- création d’admin;
- état ou cycle de vie d’enrollment;
- éventuel endpoint d’activation, reset, ou reprise;
- compatibilité avec les flows existants.

#### UI

- présence ou non d’une case à cocher “mode expérimental”;
- wording du flux;
- visibilité d’un état de type “enrollment pending”; 
- actions disponibles après création.

#### Sécurité

- exposition minimale du matériel transmis par courriel;
- rôle exact des recovery codes;
- auditabilité du parcours;
- réduction du risque d’ambiguïté ou d’abus.

#### Opérations

- gabarit d’email d’acceptation;
- procédure courte de support;
- règle de révocation du tenant expérimental;
- régénération du matériel si le participant échoue ou perd ses codes.

### Non-objectifs

- Concevoir un onboarding grand public.
- Introduire un store mobile ou un vrai canal de distribution Android durable.
- Repenser l’ensemble du modèle de recovery admin.
- Étendre ce flux à des cas non expérimentaux sans validation produit explicite.

### Livrables attendus de la future session de planification

1. Une recommandation finale entre option A et option B.
2. Un scope de changement minimal découpé par couches: domaine, API, UI, audit, documentation.
3. Une liste de décisions explicites et de non-décisions assumées.
4. Un parcours opérateur cible.
5. Un parcours participant cible.
6. Une liste de risques et mitigations simples.
7. Une proposition de wording UI et opérateur si pertinent.

### Références à relire avant la planification détaillée

- `c:\github\ezkey-worktree3\docs\RECOVERY_CODES_LIFECYCLE_ANALYSIS.md`
- `c:\github\ezkey-worktree3\docs\ADMIN_UI.md`
- `c:\github\ezkey-worktree3\docs\ENDPOINT.md`
- `c:\github\ezkey-worktree3\ezkey-admin-api\src\main\java\org\ezkey\admin\service\AdminProvisioningService.java`
- `c:\github\ezkey-worktree3\ezkey-admin-api\src\main\java\org\ezkey\admin\service\AdminRecoveryService.java`
- `c:\github\ezkey-worktree3\ezkey-admin-ui\src\pages\admins.tsx`

### Demande pour la prochaine session d’agent

Produire un plan d’exécution détaillé pour cette mini-évolution produit d’onboarding expérimental “recovery-codes-first”, avec recommandation d’architecture fonctionnelle, impacts API/UI/audit, découpage du travail, risques, et proposition de plus petit incrément viable.