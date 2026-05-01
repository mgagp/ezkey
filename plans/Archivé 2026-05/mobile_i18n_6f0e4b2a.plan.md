## Plan: Mobile i18n FR/EN

**Status**
- Archive 2026-05
- Completed
- Implemented
- Validated

Proposition: introduire une internationalisation simple et durable dans ezkey_mobile avec anglais par defaut, bascule manuelle depuis Settings, et application du changement apres redemarrage de l'application. La solution retenue devait rester pragmatique pour une application React Native en developpement actif, avec seulement deux langues, sans detection automatique de la langue systeme ni live switching complet du runtime. Le plan visait aussi a aligner la documentation mobile, les tests unitaires, le bootstrap applicatif et la validation sur appareil avec cette nouvelle direction produit.

**Steps**
1. Cadrage documentaire. Relire le corpus mobile de reference sous ezkey_mobile/docs, puis lever explicitement la contrainte historique "English-only runtime; no live locale switching" dans ezkey_mobile/PRD.md et ezkey_mobile/AGENTS.md pour eviter une divergence entre code, comportement reel et documentation.
2. Architecture i18n. Introduire i18next et react-i18next comme base technique standard pour React Native, avec ressources statiques locales en anglais et en francais, locale par defaut = en, et cles stables par ecran et domaine.
3. Persistance et bootstrap. Ajouter une facade de stockage locale pour la preference de langue via AsyncStorage, puis initialiser la langue avant le rendu principal dans la couche providers. Le comportement retenu est: charger la preference persistante si elle existe, sinon tomber sur l'anglais.
4. UX Settings. Ajouter une entree Language dans le hub Settings, creer un ecran dedie de selection de langue, persister la preference immediatement, puis demander un redemarrage de l'application pour appliquer le changement partout. Aucun redemarrage automatique ni bascule live globale n'est introduit au premier cycle.
5. Extraction des chaines. Remplacer les textes visibles de l'interface par des cles de traduction sur l'ensemble de l'application mobile courante: navigation, Home, Enrollment Detail, Enrollment Wizard, Pending Auth, Settings, About, Danger Zone, Licenses, messages d'erreur, et etats de chargement.
6. Tests et qualite. Mettre a jour les tests existants pour tenir compte de l'infrastructure i18n, ajouter des tests cibles sur la preference de langue, l'ecran Language, le menu Settings et les composants critiques deja couverts, puis valider via lint, typecheck et tests Jest.
7. Documentation. Mettre a jour la documentation mobile pour decrire l'architecture i18n retenue, la decision produit "anglais par defaut + choix manuel + redemarrage requis", l'emplacement du choix dans Settings, et la strategie de fallback.
8. Validation sur appareil. Installer la build debug sur le device Android connecte avec l'environnement JDK 17 documente, verifier la navigation principale, le changement de langue, la persistence locale, et l'absence de regression evidente sur les ecrans critiques.
9. Stabilisation en mode questions-reponses. Utiliser le retour de validation manuelle pour corriger les derniers details de finition, notamment sur l'ecran Language et le comportement de rehydratation de la preference persistante.
10. Cloture et archivage. Archiver le plan dans le depot sous plans/Archivé 2026-05 avec un identifiant stable, en conservant les observations de stabilisation et les lecons apprises.

**Implemented Scope**
- Ajout de l'infrastructure i18n dans ezkey_mobile/app/i18n et ezkey_mobile/app/services/storage/localeStorage.ts.
- Bootstrap de la langue au demarrage depuis ezkey_mobile/app/providers/AppProviders.tsx.
- Ajout d'un ecran Language et d'une entree dediee dans Settings.
- Localisation EN/FR des titres de navigation et des ecrans visibles majeurs: Home, Enrollment Detail, Enrollment Wizard, Pending Auth, Settings, About, Danger Zone, Licenses.
- Mise a jour des tests unitaires et du setup Jest pour supporter la couche de traduction.
- Mise a jour de la documentation mobile et du PRD pour supprimer la contradiction avec l'ancien mode English-only.

**Relevant files**
- c:\github\ezkey-worktree1\ezkey_mobile\app\i18n\index.ts - initialisation i18n et fallback.
- c:\github\ezkey-worktree1\ezkey_mobile\app\i18n\resources.ts - catalogue EN/FR.
- c:\github\ezkey-worktree1\ezkey_mobile\app\services\storage\localeStorage.ts - persistance de la preference de langue.
- c:\github\ezkey-worktree1\ezkey_mobile\app\providers\AppProviders.tsx - bootstrap de la langue avant rendu.
- c:\github\ezkey-worktree1\ezkey_mobile\app\navigation\AppNavigator.tsx - titres de navigation localises.
- c:\github\ezkey-worktree1\ezkey_mobile\app\screens\Settings\SettingsScreen.tsx - entree Language.
- c:\github\ezkey-worktree1\ezkey_mobile\app\screens\Language\LanguageScreen.tsx - selecteur manuel de langue.
- c:\github\ezkey-worktree1\ezkey_mobile\app\screens\Home\HomeScreen.tsx - textes localises du hub principal.
- c:\github\ezkey-worktree1\ezkey_mobile\app\screens\EnrollmentDetail\EnrollmentDetailScreen.tsx - detail d'enrolement localise.
- c:\github\ezkey-worktree1\ezkey_mobile\app\screens\EnrollmentWizard\EnrollmentWizardScreen.tsx - flow d'enrolement localise.
- c:\github\ezkey-worktree1\ezkey_mobile\app\screens\PendingAuth\PendingAuthScreen.tsx - flow d'authentification localise.
- c:\github\ezkey-worktree1\ezkey_mobile\app\screens\About\AboutScreen.tsx - ecran About localise.
- c:\github\ezkey-worktree1\ezkey_mobile\app\screens\DangerZone\DangerZoneScreen.tsx - actions destructives localisees.
- c:\github\ezkey-worktree1\ezkey_mobile\app\screens\Licenses\LicensesScreen.tsx - ecran licences localise.
- c:\github\ezkey-worktree1\ezkey_mobile\__tests__\LanguageScreen.test.tsx - tests du selecteur de langue.
- c:\github\ezkey-worktree1\ezkey_mobile\__tests__\SettingsScreen.test.tsx - test de presence/navigation de l'entree Language.
- c:\github\ezkey-worktree1\ezkey_mobile\app\services\storage\__tests__\localeStorage.test.ts - tests de persistance/fallback de la locale.
- c:\github\ezkey-worktree1\ezkey_mobile\PRD.md - contrainte produit i18n mise a jour.
- c:\github\ezkey-worktree1\ezkey_mobile\AGENTS.md - consigne repo mobile mise a jour.
- c:\github\ezkey-worktree1\ezkey_mobile\docs\MOBILE_SCREENS_AND_WIREFLOWS.md - ecran Language et navigation documentes.
- c:\github\ezkey-worktree1\ezkey_mobile\docs\MOBILE_STACK_AND_ARCHITECTURE.md - pile i18n documentee.

**Verification**
1. Validation statique complete du module mobile via yarn validate dans ezkey_mobile.
2. Validation ciblee des tests i18n ajoutes: LanguageScreen, SettingsScreen, localeStorage, puis rerun de la suite Jest complete.
3. Validation de type et de lint apres chaque coupe importante de refactorisation i18n.
4. Validation manuelle sur appareil Android debug avec JDK 17: lancement, navigation sur pratiquement tous les ecrans, changement de langue, persistence, et verification sommaire du rendu.
5. Reinstallation reussie de la build debug sur le device apres les derniers correctifs de stabilisation.

**Decisions**
- Inclus: anglais et francais uniquement pour ce cycle.
- Inclus: anglais par defaut, choix manuel via Settings.
- Inclus: changement applique apres redemarrage de l'application.
- Inclus: localisation de l'interface mobile existante et des ecrans visibles principaux.
- Exclu pour ce cycle: detection automatique de la langue du telephone.
- Exclu pour ce cycle: live locale switching sans redemarrage global.
- Exclu pour ce cycle: ajout d'autres langues et experimentation avec une solution i18n maison.

**Stabilization Notes — Questions/Reponses**
1. Observation. La navigation manuelle sur pratiquement tous les ecrans a confirme que le socle i18n, le menu Settings et l'ecran Language fonctionnaient correctement sans regression notable.
2. Ajustement retenu. L'ecran Language a ete affine pour rehydrater explicitement la preference persistante au montage, afin que l'etat affiche reflete toujours le stockage local meme apres un cycle precedent ou un changement hors session courante.
3. Ajustement retenu. La re-selection de la langue deja active n'entraine plus d'ecriture redondante ni d'alerte de redemarrage. Le comportement reste sobre et previsible.
4. Observation de build. Les installations debug Android ont reussi apres desinstallations/reinstallations correctes. Le principal incident technique rencontre pendant la phase a ete INSTALL_FAILED_UPDATE_INCOMPATIBLE lorsqu'une version signee differemment etait deja presente sur l'appareil; ce n'etait pas un defaut du code i18n mais un detail d'environnement device.
5. Observation d'environnement. Les builds Android doivent continuer a utiliser JDK 17 sur cette machine. Cette contrainte n'est pas liee a l'i18n mais reste obligatoire pour les validations mobiles.
6. Observation de qualite. Les warnings .env manquant, baseline-browser-mapping et certaines deprecations Kotlin/React Native restent presents, mais ils n'ont pas bloque ni la suite de tests ni la compilation ni l'installation debug pendant cette cloture.

**Lessons Learned**
1. Pour une application React Native encore en developpement actif avec seulement deux langues, i18next + react-i18next avec ressources locales statiques est un meilleur compromis que toute solution maison. Le gain de simplicite vient du fait que l'ecosysteme, les tests et les patterns de maintenance sont deja connus.
2. Le choix "anglais par defaut + choix manuel + redemarrage requis" a reduit fortement la complexite accidentelle. Il a permis d'introduire une vraie i18n durable sans devoir traiter immediatement les risques de rafraichissement live de tous les titres, stores et ecrans montes.
3. Le rendement de test le plus fort est venu de petits tests cibles sur la persistance de locale, le rendu du menu Settings et le selecteur Language, completes ensuite par la suite Jest complete du module. Cette approche a donne une bonne confiance sans sur-investir dans des tests fragiles d'interface.
4. La documentation doit etre mise a jour tot quand une contrainte produit change. L'ancien "English-only runtime" dans le PRD et AGENTS.md aurait sinon laisse une contradiction durable entre code, tests et corpus de reference.
5. Le passage de stabilisation en mode questions-reponses a ete utile pour finaliser les details UX a faible risque apres une premiere implementation stable, plutot que d'essayer d'anticiper tous les raffinements des le debut.

**Final Note — Closure 2026-05-01**
1. Statut final. Le plan est complete, implemente, valide et archive.
2. Le perimetre produit vise pour ce cycle est atteint: application mobile localisable en anglais et en francais, anglais par defaut, choix manuel via Settings, persistance locale, et changement applique apres redemarrage.
3. La documentation, les tests et la validation sur appareil ont ete alignes avec le comportement reel.
4. Le dossier est clos avec un resultat coherent avec le principe 80-20: solution standard, simple, maintenable et suffisante pour la phase actuelle du produit.
