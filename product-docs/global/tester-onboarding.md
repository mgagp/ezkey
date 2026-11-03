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
| Marc | Invitations, tenant et enrollment, config Acme, envoi des courriels, fermeture des runs |
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
| 5 | Tenant dédié + enrollment sur l'instance communautaire (ezkey.online) ; Marc envoie le code QR et le code d'enrollment **par courriel** | Marc | Le testeur a activé l'enrollment dans l'app |
| 6 | Lien Acme : bloc `ezkey.access-codes` ajouté à l'Acme communautaire | Marc | Le lien Acme ouvre la page de connexion du tenant |
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

### Étape 5 : tenant et enrollment

- Un tenant dédié par testeur sur ezkey.online.
- Marc envoie le code QR et le code d'enrollment au testeur **par courriel**.
- Ne jamais coller le code QR ni le code d'enrollment dans QA Sphere (placeholders seulement dans
  ce dépôt : `<code-qr>`, `<code-enrollment>`).

### Étape 6 : lien Acme

Dans l'`application.properties` de l'Acme communautaire (volume externe, pas l'image), ajouter un
bloc de quatre lignes. Le slot n'utilise **pas** l'UUID du tenant : il porte la **clé
d'intégration** et la **clé secrète** du tenant (étape 5), plus un code aléatoire et un libellé.

```properties
ezkey.access-codes.<slot>.code=<openssl rand -hex 16>
ezkey.access-codes.<slot>.integration-key=<clé d'intégration du tenant>
ezkey.access-codes.<slot>.secret-key=<clé secrète du tenant — jamais dans le chat>
ezkey.access-codes.<slot>.label=<court libellé, ex. prénom du testeur>
```

- Édition : en tant que `spring`, sauvegarde `.bak`, écriture atomique (temp + `mv`), puis
  **redémarrage du seul conteneur Acme** (les slots sont lus au démarrage JVM). Procédure Edgar :
  [`docs/lightsail/acme-access-code-runbook.md`](../../docs/lightsail/acme-access-code-runbook.md).
- Le code doit être 32–64 hex minuscules ; un code simple comme « ABC » fait échouer le démarrage.
- La **clé secrète** ne va jamais dans le chat, un courriel, QA Sphere ou ce dépôt. Seul le lien
  `/t/<code>` (et le `user_identifier`) partent au testeur.

### Étape 7 : courriel de tests

Contenu :

- L'URL Acme (avec le code d'accès).
- L'identifiant usager exact de l'enrollment (`user_identifier`) — **pas** le nom affiché. C'est
  la valeur passée à `createAuthAttemptByUserIdentifier`, celle que le testeur saisit dans le
  champ **USERNAME** d'Acme. Exemple fictif : identifiant `elodie.martin` ; nom affiché
  « Élodie Martin — iPhone 15 ». Le courriel doit donner l'identifiant exact (`elodie.martin`),
  jamais seulement le nom affiché.
- La marche à suivre courte.
- Le lien vers la run des cas d'usage (ex. « Reine - acme-approuvée »).

## Cas du premier cycle

Détail des cas dans QA Sphere (projet `EZK`). Titres :

| Code | Dossier | Sujet |
| ---- | ------- | ----- |
| `DOC-ONB-001` | DOC (dossier 23) | Initiation à QA Sphere (générique, tous testeurs) |
| `MOB-ONB-001` | MOB | Installer l'app et inscrire la version |
| `MOB-ONB-002` | MOB | Activer l'enrollment |
| `MOB-AUTH-010` | MOB | Connexion Acme approuvée |
| `MOB-AUTH-011` | MOB | Connexion Acme refusée |

Reporté au cycle 2 : `MOB-AUTH-012` Demande laissée expirer.

Les codes `MOB-ONB-*` suivent la nomenclature `CODE-AREA-NNN` de
[`test-strategy.md`](test-strategy.md) (zone courte `ONB` sous `MOB`). Les connexions Acme
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

Textes d'écran tirés du code (`ezkey_mobile` ; `ezkey-demo-app-acme`). Type : `functional`.
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

### MOB-AUTH-010 — Connexion Acme approuvée

- Dossier : 16 MOB · Priorité : high
- Préconditions : MOB-ONB-002 passé ; courriel de tests reçu (URL Acme avec code d'accès,
  identifiant usager `user_identifier` exact — ex. fictif `elodie.martin`, pas le nom affiché).
- Étapes / résultats attendus :
  1. Ouvrir l'URL Acme dans un navigateur. → La page **SIGN IN** s'affiche (sous-titre
     « Passwordless authentication with EZKey »).
  2. Saisir l'identifiant usager (`user_identifier`) dans le champ **USERNAME**, puis toucher
     **LOGIN WITH EZKEY**. → Acme affiche **AWAITING APPROVAL** / « Waiting for device
     approval... ». Si l'enrollment exige un challenge, un **CHALLENGE CODE** à 2 chiffres
     apparaît aussi (sinon non — cela dépend du réglage de l'enrollment, pas d'Acme).
  3. Sur le téléphone, ouvrir l'enrôlement, toucher **Vérifier les demandes en attente**. →
     L'écran **Authentification en attente** s'affiche.
  4. Si un code à 2 chiffres est affiché (sur Acme ou sur le téléphone), le saisir / le vérifier
     (consigne mobile : « Entrez le code à 2 chiffres affiché sur l'écran de connexion »), puis
     toucher **Approuver**. S'il n'y a pas de code à 2 chiffres, approuver directement. → L'app
     affiche **Approuvée** ; Acme passe à « Authentication successful! Redirecting... » puis au
     tableau de bord (**WELCOME, …**).
- Commentaire : version de l'app et ce qui a semblé confus.

### MOB-AUTH-011 — Connexion Acme refusée

- Dossier : 16 MOB · Priorité : high
- Préconditions : comme MOB-AUTH-010.
- Étapes / résultats attendus :
  1–3. Comme MOB-AUTH-010 jusqu'à l'écran **Authentification en attente**. → La demande
     s'affiche sur le téléphone.
  4. Si un code à 2 chiffres est affiché, le saisir / le vérifier ; sinon passer. Puis toucher
     **Refuser**. → L'app affiche **Rejetée** ; Acme montre un message clair du type
     « Authentication rejected by user. / Authentification refusée sur l'appareil. » (puis retour
     vers la page de connexion).
- Commentaire : version de l'app et ce qui a semblé confus.

### Reporté au cycle 2 — MOB-AUTH-012 Demande laissée expirer

Ne pas créer ce cas dans le premier cycle. Message Acme attendu (pour référence) :
« Authentication request expired / La demande d'authentification a expiré ».
