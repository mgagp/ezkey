# Plan : Mobile Production Security Hardening (Android)

> **STATUT : COMPLÉTÉ** — Implémenté et validé le 25 avril 2026.

**Périmètre confirmé :** Android uniquement · Certificate pinning = analyse documentée seulement · Build `lab` = distribution interne (pas Play Store)

---

## Contexte

React Native 0.76 app (`ezkey_mobile`). Un feature de simulation d'attaque MITM existe pour tester
la résistance des protocoles cryptographiques entre les API d'authentification et l'application
mobile. L'objectif est de décider de la posture de sécurité pour la release officielle sur Google
Play Store.

## Pourquoi le flag ENV seul est insuffisant

La posture actuelle (`.env.production` avec `EZKEY_LAB_RESPOND_MITM_SIMULATOR=false`) a trois
failles structurelles :

1. **Le code existe dans le binaire.** ProGuard/R8 obfusque Java/Kotlin, mais ne touche pas le
   bundle JS. `RespondMitmLabControl.tsx` et la logique de tamper dans `PendingAuthScreen.tsx`
   sont entièrement récupérables depuis l'APK par extraction du bundle Hermes.

2. **Défaut dangereux dans `env.ts`.** `parseBool(..., true)` fixe la valeur par défaut à `true`
   lorsque la clé est absente. Un build CI qui oublie de charger `.env.production` expédie l'app
   avec le toggle MITM visible et activable par l'utilisateur final.

3. **Risque Google Play.** La politique Play Protect "Malicious Behavior" peut signaler une app
   contenant du code de manipulation de trafic réseau — même si le code est "désactivé". Pour une
   app MFA cryptographique, ce signal est particulièrement sensible.

---

## Phase 0 — Prérequis bloquant (Play Store)

**0.** Mettre à jour `targetSdkVersion` dans `ezkey_mobile/android/build.gradle` :
- Changer `targetSdkVersion = 34` → `35`
- Le `compileSdkVersion` est déjà à 35 — le risque de régression est minimal
- **Raison :** depuis le 31 août 2025, Google Play refuse les mises à jour ciblant API < 35 (vérifié
  le 22 avril 2026). Sans ce changement, aucune release ne peut être soumise.

**0b.** Supprimer (ou commenter) les attributs `android:statusBarColor` et
`android:navigationBarColor` dans
`ezkey_mobile/android/app/src/main/res/values/styles.xml` :
- Android 15 (API 35) force le mode edge-to-edge : ces attributs sont ignorés par le système et
  la barre de navigation devient transparente par défaut
- **Ce qui est déjà correct :** `SafeAreaProvider` est à la racine dans `App.tsx`,
  `StatusBar` est déjà `translucent` sur Android, et `useSafeAreaInsets()` est déjà utilisé
  correctement dans tous les écrans (HomeScreen, SettingsScreen, LicensesScreen, etc.) — aucun
  refactoring n'est nécessaire
- **Validation requise :** test visuel sur émulateur API 35 pour confirmer qu'aucun bouton d'action
  en bas (ex. boutons Approve/Deny de `PendingAuthScreen`) n'est masqué par la barre de navigation

---

## Phase 1 — Fixes immédiats (indépendants de l'architecture)

**1.** Corriger le **défaut dangereux** dans `ezkey_mobile/app/config/env.ts` (L57) :
changer `parseBool(..., true)` → `parseBool(..., false)` pour `labRespondMitmSimulator`.
Ce fix est urgent — il protège contre tout build CI qui oublierait de charger `.env.production`.

**2.** Ajouter un guard `if (__DEV__)` explicite autour des `console.log` dans
`ezkey_mobile/app/screens/EnrollmentWizard/EnrollmentWizardScreen.tsx` (L582-585) pour ne plus
dépendre implicitement du dead-code elimination de Hermes sur `enrollmentId`/`enrollmentProofToken`.

---

## Phase 2 — ~~Architecture Gradle product flavors~~ (annulée — suppression complète)

**Décision révisée :** le code MITM a été **entièrement supprimé** plutôt qu'isolé via Gradle
product flavors. Justification :

- La feature est utilisée de façon exceptionnelle ; la complexité Gradle (flavors + source sets +
  Metro resolver + stubs) n'est pas justifiée pour un outil de démo rare.
- La suppression élimine le risque Google Play "Malicious Behavior" de façon définitive.
- La réintroduction éventuelle se fera dans un sous-projet dédié ou une configuration de build
  séparée, de façon plus contrôlée.

**Fichiers supprimés / nettoyés :**
- `ezkey_mobile/app/components/RespondMitmLabControl.tsx` — **supprimé**
- `ezkey_mobile/app/screens/PendingAuth/PendingAuthScreen.tsx` — import, état, logique
  `wireAccepted`, JSX et dépendance `useCallback` retirés
- `ezkey_mobile/app/config/env.ts` — propriété `labRespondMitmSimulator` retirée
- `ezkey_mobile/.env.example` — entrée `EZKEY_LAB_RESPOND_MITM_SIMULATOR` retirée

---

## Phase 3 — Évaluation certificate pinning (documentée, pas d'implémentation)

**7.** Rédiger un document de décision sur le certificate pinning couvrant :
- **Avantages :** résistance aux proxy MITM légitimes en enterprise, défense en profondeur
- **Risques :** casse sur rotation de certificat (app force-update obligatoire), incompatible avec
  certains MDM/proxies enterprise, complexité de gestion des backup pins
- **Recommandation :** déféré au Phase 2 avec Network Security Config Android (approche légère,
  sans bibliothèque tierce) plutôt que pinning applicatif — correspond à 80% de la valeur pour
  20% de la complexité

---

## Phase 4 — Vérification

**8.** Build `productionRelease` APK → `yarn android:build:productionRelease`
- Vérifier que le toggle MITM est absent de l'UI (test fonctionnel)
- Extraire le bundle JS avec `apktool` ou `unzip` → chercher `RespondMitmLabControl` — doit être **absent**

**9.** Build `labRelease` → vérifier que le toggle MITM est présent et fonctionnel

**10.** `yarn lint` + `yarn typecheck` + `./gradlew :app:testDebugUnitTest` passent sur les deux flavors

---

## Fichiers impactés

| Fichier | Changement |
|---|---|
| `ezkey_mobile/android/build.gradle` | `targetSdkVersion` 34 → 35 (Phase 0) |
| `ezkey_mobile/android/app/src/main/res/values/styles.xml` | Supprimer `statusBarColor` / `navigationBarColor` (Phase 0b) |
| `ezkey_mobile/android/app/build.gradle` | Ajouter `productFlavors` (lab / production) |
| `ezkey_mobile/app/config/env.ts` | Défaut MITM `true` → `false` (L57) |
| `ezkey_mobile/app/screens/PendingAuth/PendingAuthScreen.tsx` | Import conditionnel du composant MITM |
| `ezkey_mobile/app/components/RespondMitmLabControl.tsx` | Déplacer vers source set `lab/` |
| `ezkey_mobile/app/screens/EnrollmentWizard/EnrollmentWizardScreen.tsx` | Wrapper `if (__DEV__)` sur logs L582-585 |

---

## Décisions actées

- **MITM code → absent du binaire production** via Gradle flavors (pas un toggle ENV)
- **Défaut env.ts** → corrigé à `false` immédiatement (Phase 1, urgence haute)
- **Certificate pinning** → évaluation documentée seulement, implémentation déférée Phase 2
- **iOS** → hors scope de ce plan
- **Build `lab`** → distribution interne uniquement (Firebase / APK direct)
- **Server-side MITM** (`EZKEY_DEMO_MITM_SIGNATURE_ENABLED`) → concern deployment Docker, hors scope mobile

---

## Matrice de build cible

| Flavor + Build type | Signing | MITM code | Destination |
|---|---|---|---|
| `lab` + `debug` | debug keystore | ✅ présent | Dev/QA interne |
| `lab` + `release` | upload keystore | ✅ présent | Démos, tests protocole (Firebase/APK) |
| `production` + `release` | upload keystore | ❌ absent | Google Play |
