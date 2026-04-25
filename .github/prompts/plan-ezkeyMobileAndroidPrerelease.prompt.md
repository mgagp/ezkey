# Plan: Ezkey Mobile — Positionnement pré-release Android

TL;DR — Préparer une première publication Android de l'app Ezkey en deux temps : (1) une vague de **quick wins** validables immédiatement en build debug local (splash, écran About, version affichée, copy d'onboarding, icône monochrome vérifiée), et (2) les **fondations release + conformité Play Console** (keystore prod, R8, AAB signé, politique de confidentialité, listing copy, data safety, screenshots) menant à une distribution **Internal Testing → Closed Testing (bêta fermée) → Production**. Marque conservée : **Ezkey** (com.ezkeymobile).

---

## Topo Play Store (à intégrer dans la documentation)

### Compte développeur
- **Coût** : 25 USD une seule fois (vs Apple 99 USD/an).
- **Personnel** : validation par pièce d'identité, pas de DUNS, nom légal visible publiquement sur la fiche.
- **Organisation** : depuis 2023, Google exige un **numéro DUNS** (gratuit, ~30 jours via Dun & Bradstreet), une adresse vérifiable, et un site web officiel. Nom de l'organisation visible publiquement. Recommandé si Ezkey est porté par une entité légale (même OSS) car cela renforce la crédibilité enterprise — alignement avec le positionnement « backend-first, self-hosted » du PRD.
- **Recommandation** : si une entité existe déjà (ou peut être créée rapidement), partir **organisation** dès le départ pour éviter une migration ultérieure (qui n'est pas triviale côté Google).

### Pistes de distribution (tracks)
Google Play offre 4 tracks empilables sur la même app :
1. **Internal testing** — jusqu'à 100 testeurs invités par email, propagation en ~minutes, pas de revue Play. Idéal pour valider l'AAB signé et les flows critiques avant d'élargir.
2. **Closed testing** (bêta fermée) — listes d'emails ou Google Groups, **revue Play légère** requise. Depuis 2024, **Google exige 12 testeurs actifs pendant 14 jours** avant qu'un nouveau compte développeur puisse passer en production. C'est la voie naturelle pour une « bêta limitée ».
3. **Open testing** (bêta publique) — n'importe qui peut s'inscrire via lien public, apparaît dans le Play Store avec étiquette "Early access".
4. **Production** — fiche publique standard.

→ Pour Ezkey : **Internal → Closed (12+ testeurs, 14j) → Production** est le chemin officiel recommandé.

### Format de livraison
- **AAB obligatoire** depuis août 2021 (plus d'APK pour les nouvelles apps). Déjà supporté côté code (`yarn android:bundle:release`).
- **Play App Signing** activé par défaut : Google détient la clé d'app signing, tu détiens la **upload key** (celle générée localement). Permet de récupérer/rotater la clé en cas de perte.
- **Target API** : Google impose un targetSdk ≤ 1 an d'âge. Actuellement 34 ✅, mais à surveiller (35 deviendra obligatoire mi-2025).

---

## Phase 1 — Quick wins en build debug (priorité haute, validables immédiatement)

Tous validables via `yarn android:install:debug` sur ton appareil ou émulateur, **aucune dépendance Play Console**.

1. **Écran « About »** dans Settings — affiche version (`versionName`), build number (`versionCode`), buildTimestamp (déjà injecté dans `BuildConfig`), commit court (à injecter via Gradle), lien vers documentation, mention « Open source — MIT », logo. Critique pour le support : un utilisateur peut t'envoyer sa version exacte en cas de bug.
   - *Réutilise* : `HeaderSettingsButton`, palette `app/config/theme.ts`, `EzkeyLogo`.
   - *Ajoute* : `app/screens/About/AboutScreen.tsx`, route dans le navigator, exposition `BuildConfig.GIT_COMMIT` via `android/app/build.gradle`.

2. **Splash screen Android 12+ natif** — actuellement transition blanc/noir par défaut, peu professionnel. Implémenter via `androidx.core:core-splashscreen` (style `Theme.SplashScreen`) avec icône Ezkey sur fond `#0b0d11` (cohérent avec le thème dark de l'app).
   - *Modifie* : `android/app/build.gradle` (dépendance), `res/values/styles.xml` (thème SplashScreen), `MainActivity.kt` (`installSplashScreen()` avant `super.onCreate`).
   - *Ajoute* : `res/drawable/splash_icon.xml` ou raster équivalent.

3. **Polish d'onboarding / empty state Home** — l'écran Home actuel propose juste « Add Enrollment ». Ajouter :
   - Titre court (« Welcome to Ezkey »), sous-titre expliquant la valeur en une phrase (« Approve sign-ins from your trusted device »).
   - Petit pictogramme QR + texte d'amorce (« Scan a QR code from your Ezkey-enabled service to get started »).
   - Lien « Learn more » vers documentation publique.
   - *Modifie* : `app/screens/Home/HomeScreen.tsx`.

4. **Vérifier l'icône monochrome (Android 13+ themed icons)** — l'adaptive icon contient un `<monochrome>` mais à valider visuellement sur device avec « Themed icons » activé dans les paramètres système. Ajuster la silhouette si elle se lit mal en monochrome (souvent le cas avec un foreground trop détaillé).

5. **Display name finalisé + version visible dans le drawer** — déjà `Ezkey` dans `strings.xml` ✅. Ajouter un footer discret « v1.0.0 (build 1) » dans le menu Settings pour traçabilité immédiate.

→ **Livrable Phase 1** : APK debug installable, screenshots maison de qualité « démo » utilisables ensuite pour la fiche store.

---

## Phase 2 — Fondations release (peuvent démarrer en parallèle de Phase 1)

Préparer la machinerie de build sans encore publier.

1. **Génération du keystore de production** (upload key) :
   - `keytool -genkeypair -v -keystore ezkey-upload.keystore -alias ezkey-upload -keyalg RSA -keysize 4096 -validity 10000`
   - Stocker hors-repo (1Password / coffre), documenter dans `ezkey_mobile/docs/MOBILE_RELEASE_SIGNING.md` (nouveau).
   - Ne **jamais** commiter ; utiliser `~/.gradle/gradle.properties` ou variables d'env CI pour `EZKEY_UPLOAD_STORE_FILE`, `EZKEY_UPLOAD_KEY_ALIAS`, `EZKEY_UPLOAD_STORE_PASSWORD`, `EZKEY_UPLOAD_KEY_PASSWORD`.

2. **`signingConfigs.release` dans `android/app/build.gradle`** — remplacer l'usage actuel de `signingConfigs.debug`. Garder un fallback explicite (build échoue si vars d'env manquantes en mode release, plutôt que de retomber silencieusement sur debug).

3. **R8 / minification** — passer `enableProguardInReleaseBuilds = true`, ajouter règles ProGuard pour React Native, Hermes, Vision Camera, Keychain (les libs publient leurs propres `consumer-rules.pro` mais à vérifier). Builder un AAB local et tester l'install (`bundletool build-apks` + `install-apks`).

4. **Stratégie versionCode/versionName** — script Node ou Gradle task qui dérive `versionCode` du nombre de commits + `versionName` du tag git le plus proche. Documenter dans `MOBILE_RELEASE_SIGNING.md`. Alternative simple : table manuelle dans le doc + bump explicite avant chaque release.

5. **Workflow CI release** (optionnel pour V1) — étendre `.github/workflows/ezkey-mobile-unit-tests.yml` ou créer `ezkey-mobile-release.yml` déclenché par tag `mobile-v*`, qui produit l'AAB signé avec secrets GitHub. Permet une release reproductible sans builder localement.

---

## Phase 3 — Conformité Play Console (bloque la soumission)

À démarrer en parallèle dès que Phase 1 est en bonne voie.

1. **Politique de confidentialité publique** — URL HTTPS obligatoire. Héberger sur `sites/ezkey-org` (site Cloudflare Pages déjà existant). Contenu minimal : quelles données l'app stocke (clés EC P-256 locales, métadonnées d'enrôlement), absence totale de télémétrie/tracking, permissions justifiées (Camera = QR uniquement, Internet = appels Auth API choisi par l'utilisateur), pas de partage tiers, contact support.
   - *Ajoute* : `sites/ezkey-org/content/legal/mobile-privacy-policy.md`.

2. **Data Safety form** (Play Console) — déclarer :
   - Données collectées : aucune côté serveur Google/Ezkey ; toutes les données restent locales (Keychain) ou transitent vers le backend self-hosted choisi par l'utilisateur.
   - Tracking : non.
   - Chiffrement en transit : oui (HTTPS).
   - Possibilité de demander suppression : « désinstallation = effacement complet ».

3. **Listing copy** — drafter dans `ezkey_mobile/docs/PLAY_LISTING.md` :
   - **Titre** (≤30 car) : `Ezkey — Cryptographic MFA`
   - **Short description** (≤80 car) : `Approve sign-ins from your trusted device. Open-source, self-hosted MFA.`
   - **Full description** (≤4000 car) : positionnement OSS / backend-first / non-FIDO2 / contrôle total des données / cas d'usage opérateurs et développeurs (aligné avec `docs/PROJECT_POSITIONING.md`).
   - **Catégorie** : Tools ou Business.

4. **Assets graphiques** :
   - **Icône Play Store** (512×512) — dérivée de l'adaptive icon existante.
   - **Feature graphic** (1024×500) — bannière avec logo + tagline sur fond `#0b0d11`.
   - **Screenshots** : 4–6 captures (Home avec enrollments, scan QR, demande d'auth pending, écran About, dark mode). Capturer depuis Phase 1 finalisée.

5. **Support contact** — email dédié (`support@ezkey.org` ou similaire) + URL doc (`https://ezkey.org/docs`).

6. **Content rating questionnaire** — Play Console le génère automatiquement à partir d'un formulaire (sans contenu sensible : devrait sortir « Everyone »).

---

## Phase 4 — Bêta puis production

1. Compte développeur Google Play créé (décision personnel/org prise — voir Topo).
2. App créée dans Play Console, AAB Phase 2 uploadé sur **Internal testing**, 2–5 testeurs internes (toi + collaborateurs).
3. Une fois les flows validés (enrôlement, pending auth, respond, désinstall/réinstall), promotion vers **Closed testing** avec liste d'emails ou Google Group. Recruter ≥12 testeurs actifs sur 14 jours (exigence pour comptes neufs).
4. Itérations sur retours bêta (versionCode +1 à chaque upload).
5. Promotion vers **Production**, soumission revue Google (~24–72h typique).

---

## Steps (séquence d'exécution recommandée)

**Sprint 1 (quick wins debug)** — *parallélisable en interne* :
1. Implémenter écran About + injection commit Git
2. Ajouter splash screen natif Android 12+
3. Étoffer onboarding HomeScreen
4. Auditer icône monochrome sur device réel
5. Capture d'écran de référence pour usage Phase 3

**Sprint 2 (release plumbing)** — *peut démarrer en parallèle de Sprint 1* :
6. Générer keystore upload + doc `MOBILE_RELEASE_SIGNING.md`
7. Configurer `signingConfigs.release` + variables d'env
8. Activer R8, valider AAB local via `bundletool`
9. Stratégie versionCode/versionName documentée

**Sprint 3 (conformité)** — *dépend de Sprint 1 pour les screenshots* :
10. Privacy policy publiée sur `ezkey.org`
11. `PLAY_LISTING.md` rédigé
12. Feature graphic + screenshots produits
13. Décision compte personnel vs organisation tranchée

**Sprint 4 (publication)** — *dépend de Sprint 2 + 3* :
14. Création compte Play Console + paiement 25 USD
15. Création app, upload AAB en Internal testing
16. Promotion Closed testing avec ≥12 testeurs
17. Production

---

## Relevant files

- [ezkey_mobile/android/app/build.gradle](ezkey_mobile/android/app/build.gradle) — `versionCode`/`versionName` (L94-95), `signingConfig` (L125-129), `enableProguardInReleaseBuilds` (L55).
- [ezkey_mobile/android/app/src/main/AndroidManifest.xml](ezkey_mobile/android/app/src/main/AndroidManifest.xml) — permissions, theme, intent-filters.
- [ezkey_mobile/android/app/src/main/res/values/strings.xml](ezkey_mobile/android/app/src/main/res/values/strings.xml) — display name `Ezkey`.
- [ezkey_mobile/android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml](ezkey_mobile/android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml) — adaptive icon + monochrome.
- [ezkey_mobile/app/config/theme.ts](ezkey_mobile/app/config/theme.ts) — palette à réutiliser pour splash et About.
- [ezkey_mobile/app/screens/Home/HomeScreen.tsx](ezkey_mobile/app/screens/Home/HomeScreen.tsx) — onboarding/empty state à étoffer.
- [ezkey_mobile/app/components/HeaderSettingsButton.tsx](ezkey_mobile/app/components/HeaderSettingsButton.tsx) — point d'entrée vers About.
- [ezkey_mobile/docs/MOBILE_PLAY_PUBLISHING.md](ezkey_mobile/docs/MOBILE_PLAY_PUBLISHING.md) — checklist existante à enrichir/cocher.
- [ezkey_mobile/scripts/generate-third-party-licenses.mjs](ezkey_mobile/scripts/generate-third-party-licenses.mjs) — à exécuter avant chaque release.
- [sites/ezkey-org/](sites/ezkey-org/) — hébergement de la privacy policy.
- *Nouveaux* : `ezkey_mobile/app/screens/About/AboutScreen.tsx`, `ezkey_mobile/docs/MOBILE_RELEASE_SIGNING.md`, `ezkey_mobile/docs/PLAY_LISTING.md`, `sites/ezkey-org/content/legal/mobile-privacy-policy.md`.

---

## Verification

**Phase 1 (debug local)** :
- `yarn android:install:debug` → vérifier visuellement splash, About (version + commit affichés), Home onboarding, monochrome icon avec Themed icons activé.
- Capturer 4–6 screenshots haute qualité depuis l'émulateur Pixel ou device réel.

**Phase 2 (release plumbing)** :
- `yarn android:bundle:release` produit un `.aab` signé (vérifier signature : `jarsigner -verify -verbose app-release.aab`).
- `bundletool build-apks --bundle=app-release.aab --output=ezkey.apks` puis `bundletool install-apks` sur device → app installable et lance correctement avec R8 actif.
- Vérifier taille AAB (< 30 MB attendu).

**Phase 3 (conformité)** :
- URL privacy policy accessible publiquement en HTTPS, retourne 200.
- Listing copy relu (cohérence avec PROJECT_POSITIONING.md).
- Screenshots respectent dimensions Play Console (min 320 px côté court, ratio 16:9 ou 9:16).

**Phase 4 (Play Console)** :
- AAB uploadé en Internal testing → notification testeurs → installation via lien Play Store fonctionne.
- Flow critique e2e : scan QR → enroll → pending auth → respond, sur build de production signée.
- Aucune erreur dans la section « Pre-launch report » de Play Console (Google teste automatiquement sur plusieurs devices virtuels).

---

## Decisions

- **Marque** : conservée à `Ezkey` (applicationId `com.ezkeymobile` inchangé).
- **Cible OS V1** : Android uniquement, iOS différé.
- **Télémétrie/crash reporting** : aucune en V1 (alignement PRD). À reconsidérer post-bêta selon retours.
- **Localisation V1** : anglais uniquement (alignement PRD).
- **Tracks Play** : Internal → Closed (≥12 testeurs / 14j) → Production. Open testing optionnel.

---

## Further Considerations

1. **Compte développeur — personnel vs organisation** : recommandation = organisation si entité légale disponible (crédibilité enterprise, cohérent avec positionnement self-hosted/OSS pour devs et opérateurs). Sinon personnel pour ne pas bloquer le go-to-market et migrer plus tard reste possible mais coûteux côté Google.
2. **Stockage du keystore upload** : option A = coffre personnel (1Password) + sauvegarde chiffrée hors-ligne ; option B = GitHub Actions secrets seulement (perte si org compromise) ; option C = combinaison A+B (recommandé).
3. **Pre-launch report Play Console** : Google fait tourner l'app sur ~10 devices virtuels avant publication. Vaut la peine d'investir dans des **detox/e2e tests** légers post-V1 pour éviter les surprises, mais pas bloquant pour la première soumission.
