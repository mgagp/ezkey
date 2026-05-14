---
audience: "Invited EXP1 explorers (technically fluent: engineers, analysts, tech press). They receive a deferred activation code from operators; hands-on scripted tour through Admin UI, mobile enrollment, and Demo Acme App to experience a complete Ezkey scenario."
planned_slug_fr: "exp1-guided-tour.html"
planned_canonical_fr: "https://ezkey.org/fr/exp1-guided-tour.html"
planned_slug_en: "exp1-guided-tour.html"
planned_canonical_en: "https://ezkey.org/exp1-guided-tour.html"
screenshot_assets_dir: "/exp1-guided-tour/"
status: published
published_html_en: /exp1-guided-tour.html
published_html_fr: /fr/exp1-guided-tour.html
published_date: 2026-04
html_amended_post_publish: true
source_of_truth: html
---

# EXP1 — parcours guidé : cycle complet jusqu’à la connexion avec l’app démo Acme

<!-- ezkey-org:exclude-start
Companion piece: conceptual map of the Admin UI lives in draft-exp1-admin-ui-overview.md (publish order: overview can precede this hands-on draft).

Screenshots live under sites/ezkey-org/exp1-guided-tour/ and are referenced from published HTML via absolute paths from site root (e.g. /exp1-guided-tour/exp1-tour-01-....webp). Same files serve FR and EN pages.

Suggested capture list for authors (rename files to match when exporting):
  exp1-tour-01-admin-login-activation.webp
  exp1-tour-02-integration-created.webp (or integrations-list)
  exp1-tour-03-api-key-created.webp (redact/blur secrets in post)
  exp1-tour-04-enrollment-qr-challenge.webp
  exp1-tour-05-demo-acme-api-key.webp
  exp1-tour-06-demo-acme-login-pending.webp
  exp1-tour-07-demo-acme-session-success.webp
  Optional: exp1-tour-08-dashboard-overview.webp OR exp1-tour-mobile-approval.webp (pick one narrative slot)

Naming: ASCII, kebab-case, prefix exp1-tour-NN-, language-agnostic (no FR/EN duplicate assets).

Publication note: regenerate HTML omit this block; keep alt text short and factual; lazy loading + width/height in HTML recommended.
ezkey-org:exclude-end -->

## Objectif de ce guide

Ce parcours s’adresse à une **personne invitée sur exp1** qui a manifesté son intérêt pour explorer Ezkey : profil technique (collègue informaticien, équipe produit/sécurité, journaliste spécialisé, etc.) — pas un grand public novice.

**But :** faire vivre une **expérience Ezkey bout en bout**, du premier contact avec la console jusqu’à une authentification réussie via l’application mobile, en s’appuyant sur une **application de démonstration** intégrée au dispositif exp1.

À l’arrivée, un opérateur (vous, côté instance) aura préparé le terrain : création du **tenant**, **mode d’activation différé**, communication du **code d’activation** au participant. À partir de là, ce guide décrit une **séquence scriptée**.

**À garder à l’esprit :**

- Ezkey reste une **plateforme expérimentale** ; exp1 tourne à **petit volume** et sur **demande**.
- Ce n’est **pas** une configuration représentative d’une production mature : nous le rappellerons là où ça évite une mauvaise attente sur les délais, les canaux de communication ou la gestion des secrets.

Une fois les étapes principales assimilées, le participant peut prendre quelques minutes sur le **dashboard** de l’Admin UI pour se faire une lecture « terrain » rapide du tenant — nous y revenons en fin de parcours.

---

## Flux principal du scénario (vue d’ensemble)

Le fil conducteur suit un **cycle complet** où la console Tenant Admin permet de préparer tous les artefacts nécessaires à une tentative de connexion avec l’app démo :

1. Activer son accès Tenant Admin (**code d’activation** puis **association du mobile** depuis l’écran de connexion).
2. Créer une **intégration** (première action métier dans l’Admin UI).
3. Créer une **clé d’API** pour cette intégration ; **conserver précieusement les deux éléments** fournis avec la clé (identifiant + secret tel que présenté à la création) — par exemple dans un gestionnaire de notes ou fichiers hors navigateur selon vos habitudes.
4. Créer un **enrôlement** et le rattacher au **mobile** (scan QR + validation du défi à six chiffres).
5. Dans un **autre navigateur** (fenêtre anonyme ou profil distinct), ouvrir l’**app démo Acme** exposée pour exp1, y saisir la **clé d’API** obtenue plus tôt, puis lancer un **login avec l’identifiant utilisateur** associé à l’enrôlement.
6. Sur le téléphone : **examiner puis approuver** la demande pendant la fenêtre (compte à rebours côté app démo).

Si tout se déroule correctement, l’app démo bascule vers un **écran de session pseudo-appli**, avec possibilité de **déconnexion**. C’est le signal que le flux complet a été vécu avec succès.

---

## Pourquoi l’intégration avant la clé API (et où entre l’app démo)

Dans Ezkey, l’Admin UI permet de piloter une organisation : intégrations, enrôlements, admins, suivis — etc. Mais **pour qu’une application « réelle » parle aux services Ezkey**, l’usage normal passe par une **couche backend** utilisant une **Integration API / client et des clés d’API** adaptées aux flux machine-à-machine.

En bref :

- Votre interlocuteur **automatique**, une fois une intégration définie, c’est l’**application intégrée** (vous ne « parlez » pas uniquement aux écrans de l’Admin UI).
- Sur exp1, l’**app démo Acme** joue le rôle d’**application intégrée** dans l’écosystème de démonstration ; elle permet d’éprouver le cycle sans développer votre propre appli tout de suite.
- Le projet fournit également un **SDK Java** utilisé entre autres par cette démo — annonce pragmatique de **livrables** qui rejoindront le dépôt public prévu (**open source prévu septembre**) et du sérieux côté intégration pour les équipes qui codent.

---

## Étape 1 — Activer l’accès Tenant Admin (code d’activation + mobile)

Le participant ouvre l’URL d’accès à l’**Admin UI** pour exp1 et arrive sur l’écran de **connexion**.

1. Saisir le **code d’activation** qui lui a été communiqué (mode différé géré côté opérateur Global Admin / instance).
2. Suivre le flux affiché pour **associer le téléphone** : en pratique, **scanner le code QR** avec l’application mobile Ezkey et compléter les étapes demandées à l’écran.

![Admin UI — connexion : code d’activation et association mobile (QR)](/exp1-guided-tour/exp1-tour-01-admin-login-activation.webp)

*Légende / captation recommandée : recadrer sur le formulaire de code d’activation et la zone du QR qui guide l’appairage mobile ; éviter d’afficher URLs internes ou métadonnées sensibles.*

---

## Étape 2 — Créer une intégration

Dans l’Admin UI, naviguer vers la section prévue pour les **integrations** puis **créer une intégration** nouvelle avec les données pratiques nécessaires (libellés, identifiants fonctionnels suivant le formulaire exp1).

Une capture utile peut montrer soit le formulaire pertinent, soit la **liste** avec la ligne nouvellement créée — sans surcharger avec tout le tableau de navigation.

![Admin UI — création ou vue d’une intégration](/exp1-guided-tour/exp1-tour-02-integration-created.webp)

---

## Étape 3 — Créer une clé d’API pour cette intégration

Toujours dans l’Admin UI, depuis le contexte de l’intégration créée :

1. Déclencher la création d’une **clé d’API**.
2. Lorsque l’interface affiche les **deux informations** destinées aux appels automatiques (**identifiant + secret**, ou équivalent tel qu’Affichés à ce moment précis), les **copier et les mettre en lieu sûr** immédiatement : elles constituent des **secrets** ; après coup, leur récupération n’est généralement plus possible sous la même forme.

![Admin UI — clé d’API nouvellement créée (capturer après floutage/redaction locale)](/exp1-guided-tour/exp1-tour-03-api-key-created.webp)

*Post-traitement : **flouter ou masquer** la majeure partie du secret avant publication ; garder lisible uniquement ce qui aide le lecteur à reconnaître l’emplacement.*

---

## Étape 4 — Enrôlement : QR + défi à six chiffres (attentes prod)

Les **enrôlements** font partie du **pôle relationnel utilisateur/appareil** d’Ezkey : lier une identité projetée à votre intégration à ce mobile.

Pour ce parcours exp1 :

1. Créer un **nouvel enrôlement** rattaché à l’intégration précédente.
2. Quand le produit affiche ensemble un **QR** et un **défi numérique** (par ex. à six chiffres), suivre ces deux actions depuis le téléphone (**scan puis saisie**).

**Gestion des attentes :**

- Dans ce déploiement d’exploration, le produit peut présenter QR et défi **au même écran**. En **production digne de ce nom**, la pratique cible évite une exposition unique canal : typiquement **QR par courriel** (ou équivalent) et **défi par SMS** ou autre second canal ; d’autres mécaniques pourront aussi s’ajouter.
- À ce stade, Ezkey **n’embarque pas** tous les **connecteurs d’expédition** que l’on verrait dans un produit fermé ; c’est conforme au caractère **non production** explicité ci-dessous.

Concernant les extensions futures (connecteurs externes, autres canaux), le projet peut s’articuler ensuite autour du **modèle de contributions** habituel (« **contrib** » / modules complémentaires) — **Ezkey Contrib / pattern contrib** désigne ici cette logique évolutive, pas un dépôt existant garanti tel quel aujourd’hui.

![Admin UI — enrôlement : QR et défi visibles ensemble (captage exp1)](/exp1-guided-tour/exp1-tour-04-enrollment-qr-challenge.webp)

---

## Étape 5 — App démo Acme : renseigner la clé API (seconde instance de navigateur)

Ouvrir une **nouvelle fenêtre de navigation**, idéalement un **profil ou mode privé** séparé, pour jouer « l’application intégrée » sans mélanger vos cookies Tenant Admin avec le rôle utilisateur démo.

1. Atteindre l’instance **Demo Acme App** mise à disposition pour exp1.
2. Localiser la saisie de **clé d’API** telle que proposée par cette démo.

**Réalité intégration vs démo :** coller une clé depuis l’interface web est une **concession pédagogique** — en intégration sérieuse, la clé reste dans un **référentiel configuré au backend**, fortement gardé (« derrière vos murs »). Ici, l’UX est simplifiée **volontairement** pour suivre pas à pas le flux sous vos yeux.

![Demo Acme App — saisie de la clé d’API (contexte illustration)](/exp1-guided-tour/exp1-tour-05-demo-acme-api-key.webp)

---

## Étape 6 — Login avec l’identifiant de l’enrôlement, puis approbation mobile

Sur l’app démo :

1. Lancer une **demande de login** avec l’identifiant utilisateur indiqué / associé dans le détail **enrollment** côté Admin UI (`user id` projeté suivant votre écran tel qu’expose exp1).

L’application dévoile alors habituellement un **minuteur ou compte à rebours** représentatif de la fenêtre de validation.

Sur le téléphone :

2. Basculer sur l’**application Ezkey**.
3. Vérifier qu’une **requête d’authentification entrante** apparaît et **l’approuver**.

Une capture facultative peut montrer soit l’**écran d’attente** côté Acme soit le flux **liste / approbation** mobile — éviter deux images quasi redondantes.

![Demo Acme App — login en cours / attente validation](/exp1-guided-tour/exp1-tour-06-demo-acme-login-pending.webp)

---

## Étape 7 — Session établie côté app démo

Après accord sur le téléphone, l’app démo doit **changer de vue** pour indiquer que l’utilisateur est « connecté » dans la **pseudo-application** illustrative, avec **logout** disponible selon les écrans.

![Demo Acme App — session active (pseudo-app)](/exp1-guided-tour/exp1-tour-07-demo-acme-session-success.webp)

---

## Après coup : tableau de bord (option conseillée)

Une fois ce cycle vécu — plutôt qu’immédiatement au début — il est pertinent de retourner sur le **dashboard** du tenant : regarder brièvement agrégés et liens courts vers les autres sections. Une **vue d’ensemble optionnelle** peut faire l’objet d’une huitième figure si vous voulez mettre encore un peu en valeur la sobriété de l’Admin UI.

![Admin UI — dashboard (capture optionnelle)](/exp1-guided-tour/exp1-tour-08-dashboard-overview.webp)

---

## En résumé

| Étape | Rôle narratif principal |
|------|--------------------------|
| 1 | Déverrouiller Tenant Admin ; **lier le mobile**. |
| 2 | Ancrer Ezkey dans un **périmètre applicatif**. |
| 3 | Produire des **identifiants machine-à-machine** pour l’Integration API (clé). |
| 4 | **Enrôler** l’usage mobile et accepter le modèle QR/défi *exploration*. |
| 5–7 | Pousser jusqu’à une **demande MFA** puis **succès** vue app démo. |

Ce parcours n’épuise pas le produit ; il en donne une **succession fonctionnelle** suffisamment riche pour un invité qui veut juger Ezkey sérieusement avant de se projeter.

---

[← ezkey.org (français)](/fr/)
