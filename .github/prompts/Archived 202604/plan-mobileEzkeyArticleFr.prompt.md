# Plan : Article Medium + révision guide — Mobile Ezkey (HTML français)

> **Status : COMPLETED — Archivé le 2026-04-14**

## TL;DR
Deux livrables liés :
1. **Article HTML français** (~7 min, ~1 500 mots) : parcours détechnilisé du protocole mobile Ezkey, focalisé sur le "comment" (implémentation, chaînage, proof tokens). Fichier : `sites/ezkey-org/fr/guide-developpeur-mobile-ezkey.html`.
2. **Révision du Mobile Developer Guide** (`docs/MOBILE_DEVELOPER_GUIDE.md`) : clarifier le chaînage pending→respond et documenter explicitement la limite PKI/CA.

---

## Fichiers concernés

**Créer :**
- `sites/ezkey-org/fr/guide-developpeur-mobile-ezkey.html`

**Modifier :**
- `sites/ezkey-org/fr/index.html` — ajout carte `pub-card`
- `docs/MOBILE_DEVELOPER_GUIDE.md` — deux ajouts ciblés

**Template HTML :** `sites/ezkey-org/fr/ezkey-at-a-glance-trust-zone-mobile.html`

---

## Livrable 1 — Outline de l'article

**Titre :** "Comment l'application mobile signe chaque étape — et ce que ça implique"
**Tagline :** "Un parcours détechnilisé dans le protocole Ezkey : enrollment, proof tokens, et chaînage cryptographique de bout en bout."
**Label :** Guide développeur · ~7 min
**Fichier :** `sites/ezkey-org/fr/guide-developpeur-mobile-ezkey.html`

*Focus : le comment (implémentation). Le pourquoi (zones de confiance) est traité dans "Ezkey en un coup d'œil" — lien croisé inclus, pas répété.*

---

### Section 01 — Le monde dans lequel on vit (~120 mots)
- Contexte d'amorce : codes SMS non sécurisés (exemple Canada), codes OTP dans les courriels, multiplication des vecteurs
- Passkeys : bonne direction, déploiement inégal pour les équipes back-end
- Transition : "Et si le vrai problème n'était pas la forme du code, mais la solidité du modèle de confiance ?"
- Intro à Ezkey : une troisième voie — ni mot de passe + SMS, ni la Cadillac des passkeys à grande échelle

---

### Section 02 — L'ancrage : la confiance vient du back-end (~150 mots)
- Rappel bref (1 para) : auto-hébergé on-prem, back-end = socle de confiance (lien vers "Ezkey en un coup d'œil")
- L'app mobile est un participant cryptographique, pas un bouton de confirmation
- Android Keystore (StrongBox si disponible) : clé privée confinée au hardware
- Contraste : push-to-approve = passe-plat ; Ezkey = l'app signe

---

### Section 03 — L'inscription : prouver qu'on possède la clé (~300 mots)
**bind :**
- `enrollmentProofToken` opaque fourni hors-bande (anti-énumération)
- Back-end répond avec clé Ed25519 + signature du bind
- L'app vérifie cette signature avant d'avancer (1re protection MITM)

**verify :**
- L'app génère EC P-256 dans le Keystore
- Charge canonique : `enrollmentProofToken|enrollmentId|challengeResponse|devicePublicKey` — on signe tout le contexte, pas juste un challenge
- Seul bloc technique montré explicitement dans l'article
- Back-end vérifie et contre-signe ; l'app valide avant de clore l'inscription

---

### Section 04 — Le Proof Token : matière non répétable (~180 mots)
- Sans jargon : "jeton frais signé à chaque cycle ; rejouer un appel passé est sans effet"
- Trois tokens en liste narrative courte (pas de tableau) :
  1. `enrollmentProofToken` — lié à l'inscription, fourni hors-bande, stable pour la durée du flux
  2. `deviceProofToken` — généré par l'app à chaque sondage (CSPRNG), signé avec la clé d'inscription
  3. `authAttemptProofToken` — émis par le back-end par tentative, à usage unique
- Analogie notariale : chaque acte porte date, lieu et signature originale

---

### Section 05 — L'authentification : un aller-retour signé (~350 mots)
**pending :**
- L'app se signale avec `deviceProofToken` signé (fraîcheur prouvée)
- Back-end répond avec `authAttemptProofToken` signé Ed25519
- L'app vérifie cette signature **avant d'afficher** quoi que ce soit (MITM → clé Ed25519 absente → vérification échoue)

**Le maillon clé du chaînage pending→respond :**
- L'`authAttemptProofToken` reçu dans `pending` EST la matière signée dans `respond`
- L'app signe `authAttemptProofToken|true` (ou `false`)
- On ne peut pas construire un `respond` valide sans avoir reçu ce token dans le `pending` — les deux appels sont cryptographiquement liés, pas juste séquentiels

**respond :**
- Back-end vérifie → signe le résultat
- L'app vérifie avant d'afficher "approuvé"

**Fil rouge :** schéma textuel simple montrant les 4 moments de vérification

---

### Section 06 — Contre quoi ça protège (~200 mots)
- **Replay** : `authAttemptProofToken` à usage unique → rejouer échoue
- **MITM** : clé Ed25519 ancrée à l'inscription → proxy malveillant ne peut pas forger
- **Énumération** : tokens opaques, aucune surface de devinette
- **Limites (ton honnête) :**
  - `devicePrivateKeyStorageTier` : assertion cliente — le serveur stocke, ne prouve pas ; documenté transparemment
  - Pas de certification PKI/CA de bout en bout : le back-end on-prem est l'ancre de confiance par design ; aucune chaîne d'autorité tierce ; limite connue, candidate à évolution future

---

### Section 07 — La troisième voie : honnêteté sur ce qu'Ezkey est (~200 mots)
- Ce que ce n'est pas : rival des passkeys, ni alternative pour les grandes organisations
- Ce que c'est : chemin de milieu pragmatique
  - Supérieur à mots de passe + SMS/TOTP
  - Plus modeste que passkeys / FIDO2
- Trois voies explicites : (1) mot de passe + MFA SMS/courriel, (2) passkeys, (3) Ezkey = troisième voie pour équipes back-end-first
- Android-first aujourd'hui, iOS à venir ; open-source ; en développement actif
- "Opinionated est une force si les hypothèses sont explicitées et vérifiables"

---

### Section 08 — Pour aller plus loin (~80 mots)
- "Le chaînage ne demande pas de faire confiance à Ezkey — il demande de vérifier les signatures"
- Liens : GitHub, guide technique complet, stack Docker locale (5 min)

---

## Livrable 2 — Révisions ciblées du Mobile Developer Guide

### Ajout A — Chaînage pending→respond
**Endroit :** dans la section "Step 2 — Respond", ajouter un encadré ou paragraphe explicatif :
> "Le `authAttemptProofToken` n'est pas un simple identifiant de session. Il est la matière signée du respond. Obtenir un `respond` valide sans avoir passé par `pending` est cryptographiquement impossible : les deux étapes sont liées par la signature de l'appareil sur le token émis par le back-end."

Ce concept est implicite dans le guide actuel (le payload est documenté) mais jamais mis en avant narrativement. L'article vient valider que cette explication manque.

### Ajout B — Limite PKI/CA explicite
**Endroit :** dans "Cryptographic Foundation" ou section dédiée "Trust Model Boundaries" (à créer) :
> "Ezkey ne fournit pas de chaîne de certification PKI/CA de bout en bout. Le back-end auto-hébergé est l'ancre de confiance par design. Il n'existe pas de tiers de confiance qui valide l'identité du serveur auprès de l'appareil via une chaîne de certificats standard. C'est une limite assumée du modèle, documentée en toute transparence."
> Candidate à évolution : Key Attestation + validation côté serveur est un chemin possible dans une version future.

---

## Contraintes éditoriales (article)
- Pas de tableaux techniques lourds — tout en narration
- Un seul bloc payload canonique (section 03 verify)
- Lien croisé vers "Ezkey en un coup d'œil" (ne pas répéter les zones fonctionnelles)
- Ton : rigoureux, voix de praticien, pas formel

---

## Vérification
1. HTML rendu dans le navigateur — mise en page, liens nav
2. Lien croisé vers `ezkey-at-a-glance-trust-zone-mobile.html` fonctionnel
3. Carte `pub-card` visible dans `fr/index.html`
4. `hreflang` dans le `<head>` (FR + pointeur EN même slug si applicable)
5. Durée de lecture estimée ~200 mots/min → 1 500 mots ≈ 7 min
6. Guide MD : les deux ajouts A et B sont présents et cohérents avec le reste du document
