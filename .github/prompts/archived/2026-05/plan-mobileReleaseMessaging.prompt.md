## Plan: Mobile Release Messaging

Status: Completed on 2026-05-02.

Recommandation: ne pas utiliser un badge flottant en bas a gauche pour annoncer une nouveaute. Pour Ezkey Mobile, la solution la plus robuste et conforme aux usages mobile est une combinaison de 3 surfaces complementaires: 1) un encart visible mais non bloquant en haut de l'ecran d'accueil pour la release experimentale en cours, 2) un ecran durable "What's new" / "Nouveautes" accessible depuis Parametres, 3) un ecran A propos enrichi pour les informations statiques produit/version/support. Le contenu reste embarque dans l'app pour v1, bilingue FR/EN, et versionne a chaque release.

**Steps**
1. Definir le modele d'information produit en separant clairement trois usages: "A propos" pour l'identite et les metadonnees de l'app, "Nouveautes" pour les notes de version et annonces de release, et "Avis experimental" pour le message contextuel de cette publication EXP1/AWBS/Lightsail. Cette etape bloque les textes, la navigation et la persistance.
2. Phase 1 - Surface visible sur l'accueil: ajouter en haut de l'ecran principal un bandeau ou une carte d'information compacte, au-dessus de la liste des enrollements dans [ezkey_mobile/app/screens/Home/HomeScreen.tsx](ezkey_mobile/app/screens/Home/HomeScreen.tsx), avec un titre court, 2-3 lignes maximum, et une action "En savoir plus" / "Learn more" ouvrant l'ecran Nouveautes. Le composant doit reutiliser le langage visuel deja present dans les banners/cartes, pas une nouvelle metaphore flottante. Cette etape depend de 1.
3. Phase 1 - Ecran durable Nouveautes: introduire une route dediee dans [ezkey_mobile/app/navigation/types.ts](ezkey_mobile/app/navigation/types.ts) et [ezkey_mobile/app/navigation/AppNavigator.tsx](ezkey_mobile/app/navigation/AppNavigator.tsx), puis un nouvel ecran de type ScrollView calque sur l'approche de [ezkey_mobile/app/screens/About/AboutScreen.tsx](ezkey_mobile/app/screens/About/AboutScreen.tsx). L'ecran doit presenter la release courante en premier, avec date/version, message experimental limite, URL du site experimental si elle est publique, et l'appel a action vers info@ezkey.org pour les codes d'activation. Cette etape depend de 1.
4. Phase 1 - Parametres et A propos: ajouter une entree "Nouveautes" dans [ezkey_mobile/app/screens/Settings/SettingsScreen.tsx](ezkey_mobile/app/screens/Settings/SettingsScreen.tsx), proche de "A propos". Conserver "A propos" pour les informations stables: version, build, licence, site principal, eventuellement support. Ne pas surcharger "A propos" avec des annonces temporaires sauf un lien vers "Nouveautes". Cette etape peut se faire en parallele avec 3 une fois 1 terminee.
5. Phase 1 - Contenu et i18n: stocker les textes dans les ressources i18n existantes de [ezkey_mobile/app/i18n/resources.ts](ezkey_mobile/app/i18n/resources.ts), avec une structure distincte pour settings, navigation, about, releaseNotes et experimentalNotice. Garder le message d'accueil tres court; deplacer le detail et le contexte dans l'ecran Nouveautes. Cette etape depend de 2 et 3.
6. Phase 2 - Visibilite et comportement: definir une logique simple de visibilite locale pour le bandeau d'accueil. Bonne pratique recommandee pour v1: affichage par defaut tant que l'utilisateur ne l'a pas consulte ou ferme, puis disparition locale avec retour possible via Parametres > Nouveautes. Si l'on veut zero persistance pour la premiere iteration, garder le bandeau toujours visible mais compact. Cette etape depend de 2, 3 et 5.
7. Phase 2 - Copywriting de release experimentale: rediger un texte sobre, non marketing, avec 4 blocs: statut experimental limite, contexte de l'instance unique EXP1/AWBS/Lightsail, canal de contact info@ezkey.org pour demander un code d'activation, et perimetre fonctionnel de la version 1. Eviter les formulations qui suggerent un service grand public, du background tracking, ou une prise en charge multi-environnement. Cette etape depend de 1.
8. Phase 2 - Play Store coherence: preparer une version courte du meme message pour le champ Play Store "What's new" dans les docs de release mobile, idealement alignee avec la version d'app affichee dans [ezkey_mobile/app/config/appInfo.ts](ezkey_mobile/app/config/appInfo.ts) et [ezkey_mobile/package.json](ezkey_mobile/package.json). Le message Play doit rester plus court que le contenu in-app et pointer vers l'experience initiale plutot que tout expliquer. Cette etape peut se faire en parallele avec 7.
9. Tests et validation: etendre les tests d'ecran existants, notamment [ezkey_mobile/__tests__/SettingsScreen.test.tsx](ezkey_mobile/__tests__/SettingsScreen.test.tsx), avec des assertions sur la presence de l'entree Nouveautes, la navigation, et le rendu du bandeau/page en EN et FR. Ajouter un test du comportement de visibilite si un etat local de lecture/fermeture est introduit. Cette etape depend de 2 a 6.

**Relevant files**
- c:\github\ezkey-worktree1\ezkey_mobile\app\screens\Home\HomeScreen.tsx — point d'ancrage pour la surface la plus visible, actuellement avec FAB bas droite et contenu principal scrollable.
- c:\github\ezkey-worktree1\ezkey_mobile\app\screens\Settings\SettingsScreen.tsx — hub naturel pour l'acces durable a Nouveautes, deja structure en cartes simples.
- c:\github\ezkey-worktree1\ezkey_mobile\app\screens\About\AboutScreen.tsx — surface a conserver pour version/build/licence/site, sans la transformer en fil d'annonces.
- c:\github\ezkey-worktree1\ezkey_mobile\app\navigation\types.ts — ajout de la route Nouveautes.
- c:\github\ezkey-worktree1\ezkey_mobile\app\navigation\AppNavigator.tsx — declaration de l'ecran et titre de navigation.
- c:\github\ezkey-worktree1\ezkey_mobile\app\i18n\resources.ts — chaines FR/EN pour navigation, settings, about, bandeau, ecran Nouveautes et message experimental.
- c:\github\ezkey-worktree1\ezkey_mobile\__tests__\SettingsScreen.test.tsx — test existant a etendre pour la nouvelle entree.
- c:\github\ezkey-worktree1\ezkey_mobile\docs\MOBILE_PLAY_PUBLISHING.md — reference pour garder le message Play coherent avec la publication.
- c:\github\ezkey-worktree1\ezkey_mobile\docs\MOBILE_PLAY_RELEASE_READINESS_AUDIT.md — contexte de preparation release et coherence de version.
- c:\github\ezkey-worktree1\ezkey_mobile\docs\MOBILE_POSITIONING.md — garde-fou de positionnement produit pour eviter un message trop consumer ou ambigu.

**Verification**
1. Verifier en navigation que depuis l'accueil, le bandeau ouvre bien l'ecran Nouveautes et que Parametres expose aussi cette entree.
2. Verifier le rendu EN et FR sur l'accueil, Parametres, A propos et Nouveautes avec des textes courts et longs.
3. Verifier que le bandeau ne concurrence pas le bouton + d'ajout d'enrolement, ne couvre pas les safe areas et reste lisible sur petits ecrans.
4. Executer les tests mobiles cibles: yarn test pour les nouveaux tests d'ecran; yarn lint et yarn typecheck pour la surface touchee.
5. Faire une revue manuelle rapide sur appareil Android pour confirmer que la visibilite est forte sans ressembler a une alerte de securite ou a une pub interne.

**Decisions**
- Inclure: une surface d'annonce visible sur l'accueil, un ecran durable Nouveautes, un A propos clarifie, contenu embarque en FR/EN, coherence Play Store minimale.
- Exclure: badge flottant bas gauche, systeme distant de contenu pour cette iteration, multi-instance/multi-environnement dans l'UX, centre de notifications generique.
- Decision UX cle: utiliser une carte/banniere en haut de l'accueil plutot qu'un element flottant secondaire. Le badge flottant bas gauche entre en conflit avec les conventions mobiles, l'equilibre visuel et potentiellement les gestes systeme.
- Decision produit cle: "A propos" et "Nouveautes" doivent etre separes. "A propos" repond a "qu'est-ce que c'est / quelle version / qui contacter", "Nouveautes" repond a "qu'est-ce qui change maintenant".

**Further Considerations**
1. Pour une evolution future, un contenu distant signe cryptographiquement peut etre envisage pour les annonces de release ou de securite, mais il doit etre traite explicitement comme une surface sensible avec verification de signature et mode de repli local.
2. Si l'application reste mono-instance pour cette phase experimentale, il peut etre utile d'afficher discretement le nom de l'environnement cible dans l'ecran Nouveautes plutot que partout dans l'app, afin d'eviter de rigidifier l'UX trop tot.
3. Le champ Play Store "What's new" doit rester tres court et factuel; l'explication complete de l'experimentation doit vivre dans l'app et sur le site/support, pas dans le listing seul.

**Recommended Copy**
- Home banner FR - title: Nouveaute
- Home banner FR - body: Premiere version experimentale d'Ezkey Mobile. Cette edition est preparee pour un auditoire limite sur l'instance EXP1/AWBS/Lightsail.
- Home banner FR - action: En savoir plus
- Home banner FR - secondary action if dismissible: Masquer
- Home banner EN - title: What's new
- Home banner EN - body: First experimental release of Ezkey Mobile. This edition is prepared for a limited audience on the EXP1/AWBS/Lightsail instance.
- Home banner EN - action: Learn more
- Home banner EN - secondary action if dismissible: Dismiss
- Settings entry FR - label: Nouveautes
- Settings entry FR - subtitle: Version en cours et informations experimentales
- Settings entry EN - label: What's new
- Settings entry EN - subtitle: Current release and experimental information
- Navigation FR - title: Nouveautes
- Navigation EN - title: What's new
- Release notes FR - hero label: Version 1.0
- Release notes FR - hero title: Premiere version experimentale
- Release notes FR - intro: Ezkey Mobile est maintenant disponible dans un cadre experimental limite. Cette premiere publication cible l'environnement EXP1/AWBS/Lightsail.
- Release notes FR - section title: Ce qui est inclus
- Release notes FR - bullet 1: Enrolement d'un appareil de confiance par code QR.
- Release notes FR - bullet 2: Approbation ou refus des demandes d'authentification depuis l'appareil mobile.
- Release notes FR - bullet 3: Stockage local des enrôlements et des cles cryptographiques de l'appareil.
- Release notes FR - section title 2: Acces experimental
- Release notes FR - access body: Si vous souhaitez participer a l'experimentation, ecrivez a info@ezkey.org pour demander un code d'activation.
- Release notes FR - section title 3: Site experimental
- Release notes FR - site body public variant: Le site experimental est heberge sur l'instance EXP1/AWBS/Lightsail.
- Release notes FR - site body safer variant: Cette edition est rattachee a l'instance experimentale EXP1/AWBS/Lightsail.
- Release notes FR - section title 4: Notes importantes
- Release notes FR - note 1: Cette publication s'adresse a un auditoire experimental limite.
- Release notes FR - note 2: L'application suit un modele d'interrogation explicite et n'utilise pas de notifications push en arriere-plan dans cette version.
- Release notes FR - note 3: Cette version ne vise pas un usage grand public ni la gestion de plusieurs environnements dans l'app.
- Release notes EN - hero label: Version 1.0
- Release notes EN - hero title: First experimental release
- Release notes EN - intro: Ezkey Mobile is now available in a limited experimental setting. This first publication targets the EXP1/AWBS/Lightsail environment.
- Release notes EN - section title: Included in this release
- Release notes EN - bullet 1: Enroll a trusted device by scanning a QR code.
- Release notes EN - bullet 2: Approve or deny authentication requests from the mobile device.
- Release notes EN - bullet 3: Local storage for enrollments and device cryptographic keys.
- Release notes EN - section title 2: Experimental access
- Release notes EN - access body: If you want to participate in the experiment, email info@ezkey.org to request an activation code.
- Release notes EN - section title 3: Experimental site
- Release notes EN - site body public variant: The experimental site is hosted on the EXP1/AWBS/Lightsail instance.
- Release notes EN - site body safer variant: This edition is tied to the EXP1/AWBS/Lightsail experimental instance.
- Release notes EN - section title 4: Important notes
- Release notes EN - note 1: This publication is intended for a limited experimental audience.
- Release notes EN - note 2: The app follows an explicit pull model and does not use background push notifications in this release.
- Release notes EN - note 3: This version is not positioned as a consumer release and does not provide multi-environment management in-app.
- About FR - keep current positioning text, then add a small support block: Support et acces experimental / info@ezkey.org
- About EN - keep current positioning text, then add a small support block: Support and experimental access / info@ezkey.org
- About FR - optional link line: Voir aussi les Nouveautes pour les informations de publication en cours.
- About EN - optional link line: See also What's new for current release information.
- Play Store short FR candidate: Premiere version experimentale d'Ezkey Mobile. Enrolement par QR, approbation mobile et acces limite sur EXP1/AWBS/Lightsail.
- Play Store short EN candidate: First experimental release of Ezkey Mobile. QR enrollment, mobile approval, and limited access on EXP1/AWBS/Lightsail.