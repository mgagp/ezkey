# Accueil des testeurs externes (application mobile)

Runbook pour accueillir un testeur externe sur l'application mobile Ezkey et le mener jusqu'aux
tests continus. Les règles générales (codes, nommage, runs, hygiène) sont dans
[`test-strategy.md`](test-strategy.md) : ce document ne les répète pas.

- **Le dépôt fait foi.** Ce runbook et les cas décrits ici sont la référence. QA Sphere sert au
  suivi (runs, résultats, commentaires), avec des liens vers ce document.
- **Un testeur à la fois.** Reine d'abord, pour valider le flux, puis des proches de Marc, un par un.
- **Aucun secret** dans les courriels, QA Sphere ou ce dépôt (clés, mots de passe, clés
  d'intégration). Pas de données personnelles dans QA Sphere au-delà d'un court identifiant.
- **Runs.** Une run par étape et par testeur, nommée avec un court identifiant (ex. « Reine -
  initiation »), jamais une adresse courriel.

## Rôles

| Qui | Fait quoi |
| --- | --------- |
| Marc | Invitations, enrollment dans le tenant de démo, usager Demo Login, envoi des courriels, fermeture des runs |
| Isabelle (agent QA) | Rédige les courriels, prépare les runs, relit les résultats, ferme les runs avec Marc |
| Testeur | Suit les courriels, exécute les cas dans **sa** run, inscrit résultat et commentaire |

## Checklist par testeur

Cocher chaque étape dans l'ordre. Une étape ne commence que si la précédente est faite.

| # | Étape | Qui | Fait quand |
| - | ----- | --- | ---------- |
| 1 | Invitation QA Sphere (rôle Test Runner) et lien d'installation Play Store (test interne) | Marc | Le testeur a accepté l'invitation QA Sphere |
| 2 | Courriel d'accueil | Isabelle rédige, Marc valide et envoie | Courriel envoyé |
| 3 | Cas d'initiation réussi | Testeur | Résultat et commentaire inscrits dans sa run |
| 4 | App installée, version inscrite | Testeur | Cas d'installation passé, version notée |
| 5 | Enrollment dans le tenant de démo de l'instance communautaire (ezkey.online), intégration Demo Login ; Marc envoie le code QR et le code d'enrollment **par courriel** | Marc | Le testeur a activé l'enrollment dans l'app |
| 6 | Usager ajouté dans Demo Login (ligne de commande) | Marc | `user list` montre l'usager |
| 7 | Courriel de tests | Isabelle rédige, Marc valide et envoie | Courriel envoyé |
| 8 | Tests continus : runs courtes, puis bilan | Testeur, puis Marc + Isabelle | Bilan fait avec Marc |

### Étape 2 : courriel d'accueil

Contenu :

- Le but : aider à préparer l'application mobile pour le test interne du Play Store.
- Comment se connecter à QA Sphere.
- Toujours passer par **la run qui lui est assignée**, pas par la liste des cas : c'est dans la
  run que le résultat est enregistré.
- La convention de fermeture : quand QA Sphere demande « Would you like to close this test run? »,
  répondre **Not now**. Les testeurs ne ferment pas les runs ; Marc ou Isabelle les ferment après
  relecture.
- Le lien vers la run d'initiation (ex. nom de run : « Reine - initiation »).

### Étape 5 : enrollment

- Un enrollment par testeur dans le **tenant de démo**, sous l'intégration de Demo Login (#1430).
  Plus de tenant dédié par testeur : la démo hébergée est liée à ce seul tenant.
- **User ID** de l'enrollment = l'identifiant usager choisi (ex. fictif `elodie.martin`).
- Marc envoie le code QR et le code d'enrollment au testeur **par courriel**.
- Ne jamais coller le code QR ni le code d'enrollment dans QA Sphere (placeholders seulement dans
  ce dépôt : `<code-qr>`, `<code-enrollment>`).

### Étape 6 : usager Demo Login

Demo Login a sa propre table d'usagers : un nom qu'elle ne connaît pas n'atteint jamais Ezkey. Sur
la VM communautaire :

```bash
docker exec ezkey-exp-demo-login node server/cli.js user add elodie.martin --name "Élodie Martin"
docker exec ezkey-exp-demo-login node server/cli.js user list
```

`--ezkey-id` n'est utile que si le User ID de l'enrollment diffère du nom d'usager. Aucune clé ni
aucun secret ne part au testeur : seulement l'adresse de Demo Login et son nom d'usager.

### Étape 7 : courriel de tests

Contenu :

- L'adresse de Demo Login : `https://demo-login.ezkey.online`.
- Le nom d'usager exact (ex. fictif `elodie.martin`) — **pas** le nom affiché. C'est ce que le
  testeur saisit dans le champ **Username** de Demo Login.
- La marche à suivre courte.
- Le lien vers la run des cas d'usage (ex. « Reine - connexion-approuvée »).

## Cas du premier cycle

Détail des cas dans QA Sphere (projet `EZK`). Titres :

| Code | Dossier | Sujet |
| ---- | ------- | ----- |
| `DOC-ONB-001` | DOC (dossier 23) | Initiation à QA Sphere (générique, tous testeurs) |
| `MOB-ONB-001` | MOB | Installer l'app et inscrire la version |
| `MOB-ONB-002` | MOB | Activer l'enrollment |
| `MOB-AUTH-010` | MOB | Connexion Demo Login approuvée |
| `MOB-AUTH-011` | MOB | Connexion Demo Login refusée |

Reporté au cycle 2 : `MOB-AUTH-012` Demande laissée expirer.

Les codes `MOB-ONB-*` suivent la nomenclature `CODE-AREA-NNN` de
[`test-strategy.md`](test-strategy.md) (zone courte `ONB` sous `MOB`). Les connexions Demo Login
continuent la série existante `MOB-AUTH-001`…`009`.

## Retours conservés

| Quoi | Où |
| ---- | -- |
| Résultat par cas (passé / échoué / bloqué) | Run QA Sphere |
| Version de l'app | Commentaire du cas d'installation, puis de chaque run |
| Commentaire libre : ce qui a semblé confus | Un par run |
| Nombre de blocages qui ont demandé notre aide | Un compte par testeur, tenu par Isabelle |

**On ne mesure pas le temps passé.** Décision voulue : peu utile pour ce but et peu intuitif dans
QA Sphere. On y revient seulement pour une raison précise et exceptionnelle.

## Bilan et suite

Après chaque testeur : relire les résultats, fermer la run, noter les ambiguïtés. Toute leçon qui
change ce flux est inscrite **dans ce document**, dans la même PR que le changement.

---

## Annexe — détail des cas (premier cycle)

Textes d'écran tirés du code (`ezkey_mobile` ; `ezkey-demo-login`, en anglais ou en français selon le choix EN | FR). Type : `functional`.
Chaque cas renvoie à ce runbook.

### DOC-ONB-001 — Initiation à QA Sphere

- Dossier : 23 DOC · Priorité : medium
- Préconditions : invitation QA Sphere acceptée ; une run est assignée au testeur (ex. « Reine -
  initiation »).
- Étapes / résultats attendus :
  1. Se connecter à QA Sphere et ouvrir la run assignée (lien du courriel). → La run s'ouvre et
     montre ce cas.
  2. Lire le cas, puis marquer le résultat « Passed ». → Le résultat est enregistré.
  3. Ajouter un commentaire court (ex. « premier essai »). → Le commentaire apparaît sur le
     résultat.
  4. Si QA Sphere propose de fermer la run, choisir **Not now**. → La run reste ouverte.

### MOB-ONB-001 — Installer l'app et inscrire la version

- Dossier : 16 MOB · Priorité : high
- Préconditions : DOC-ONB-001 passé ; lien Play Store (test interne) reçu ; téléphone Android.
- Étapes / résultats attendus :
  1. Ouvrir le lien Play Store et installer Ezkey. → L'app est installée.
  2. Ouvrir l'app. → L'écran d'accueil s'affiche (« Bienvenue dans Ezkey » s'il n'y a pas encore
     d'enrôlement), sans erreur.
  3. Ouvrir **Paramètres**, puis **À propos**. Lire la ligne **Version** et l'inscrire dans le
     commentaire du cas. → Version notée (ex. `0.1.0-alpha` — placeholder ; utiliser la valeur
     affichée).

### MOB-ONB-002 — Activer l'enrollment

- Dossier : 16 MOB · Priorité : high
- Préconditions : MOB-ONB-001 passé ; code QR et code d'enrollment reçus de Marc **par courriel**.
- Étapes / résultats attendus :
  1. Sur l'accueil, toucher le bouton **+** (**Ajouter un enrôlement**). → L'écran **Ajouter un
     enrôlement** s'ouvre sur l'étape **Scannez le code QR**.
  2. Toucher **Ouvrir le scanner** et scanner le code QR. → L'app reconnaît le code (sinon message
     **QR invalide**).
  3. À l'étape **Vérification**, saisir le code à 6 chiffres affiché avec le QR (champ décrit
     comme « Entrez le code à 6 chiffres affiché avec le code QR »), puis toucher **Terminer
     l'enrôlement**. → L'activation se termine.
  4. Revenir à l'accueil. → Le compte apparaît dans la liste des enrôlements.
- Ne pas copier le code QR ni le code d'enrollment dans QA Sphere.

### MOB-AUTH-010 — Connexion Demo Login approuvée

- Dossier : 16 MOB · Priorité : high
- Préconditions : MOB-ONB-002 passé ; courriel de tests reçu (adresse de Demo Login, nom
  d'usager exact — ex. fictif `elodie.martin`).
- Étapes / résultats attendus :
  1. Ouvrir `https://demo-login.ezkey.online` dans un navigateur (FR en haut à droite pour le
     français). → La boîte **Demo Login** s'affiche avec le champ **Nom d'usager**.
  2. Saisir le nom d'usager, laisser cochée **Afficher un code à choisir sur mon appareil**, puis
     toucher **Se connecter avec Ezkey**. → Demo Login affiche **Choisissez ce code sur votre
     appareil**, un **code de vérification** à 2 chiffres et le **temps restant**.
  3. Sur le téléphone, ouvrir l'enrôlement, toucher **Vérifier les demandes en attente**. →
     L'écran **Authentification en attente** s'affiche.
  4. Saisir le code à 2 chiffres affiché par Demo Login (consigne mobile : « Entrez le code à 2
     chiffres affiché sur l'écran de connexion »), puis toucher **Approuver**. → L'app affiche
     **Approuvée** ; Demo Login affiche **Vous êtes connecté** et **Bienvenue, …**.
- Commentaire : version de l'app et ce qui a semblé confus.

### MOB-AUTH-011 — Connexion Demo Login refusée

- Dossier : 16 MOB · Priorité : high
- Préconditions : comme MOB-AUTH-010.
- Étapes / résultats attendus :
  1–3. Comme MOB-AUTH-010 jusqu'à l'écran **Authentification en attente**. → La demande
     s'affiche sur le téléphone.
  4. Toucher **Refuser**. → L'app affiche **Rejetée** ; Demo Login affiche **Connexion refusée** —
     « La demande a été refusée sur l'appareil. », avec **Réessayer**.
- Commentaire : version de l'app et ce qui a semblé confus.

### Reporté au cycle 2 — MOB-AUTH-012 Demande laissée expirer

Ne pas créer ce cas dans le premier cycle. Message Demo Login attendu (pour référence) :
**Demande expirée** — « Personne n'a répondu sur l'appareil à temps. »
