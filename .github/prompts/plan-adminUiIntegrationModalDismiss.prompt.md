## Plan: Comprendre et corriger la fermeture du modal

**TL;DR** — Le comportement vient d'une décision de conception explicite dans le composant `Dialog` personnalisé : le fond semi-transparent (backdrop) est directement câblé à `onClose`. C'est un comportement intentionnel mais mal adapté aux formulaires. La correction est simple et localisée.

---

### Pourquoi ça arrive — les concepts en jeu

**1. "Light dismiss" vs. "Confirmed dismiss"**
Il existe deux philosophies de fermeture de modal :
- **Light dismiss** (fermeture légère) : cliquer en dehors ferme le modal. Pratique pour les modaux d'information ou de confirmation simple. C'est ce qui est implémenté ici.
- **Confirmed dismiss** : seules des actions explicites (bouton "Annuler" ou "Créer") ferment le modal. Mieux adapté aux formulaires où l'utilisateur peut perdre de la saisie.

**2. Comment le backdrop est implémenté**
Dans `ezkey-admin-ui/src/components/ui/dialog.tsx`, le fond obscurci est un simple `<div>` dont le `onClick` appelle directement `onClose`. Rien de "magique" — c'est du HTML + React standard.

**3. L'état du formulaire (React Hook Form)**
Le formulaire utilise **React Hook Form** : l'état (valeurs saisies) vit dans le composant `CreateIntegrationDialog`. Quand `createOpen` passe à `false` (suite au clic backdrop), React re-render sans le composant, ce qui détruit son état. De plus, `reset()` est explicitement appelé à la fermeture, effaçant toutes les valeurs.

**4. Est-ce normal ?**
C'est un choix courant pour les UX minimalistes, mais il est généralement considéré comme une mauvaise pratique pour les formulaires multi-champs. Les guidelines Material Design, HIG (Apple) et ARIA recommandent d'empêcher la fermeture accidentelle quand des données non sauvegardées sont présentes.

---

### Est-ce difficile à corriger ?

**Non, très facile.** Deux approches possibles :

**Steps**

1. **Approche simple — désactiver le clic backdrop** dans `ezkey-admin-ui/src/components/ui/dialog.tsx` : supprimer le `onClick={onClose}` du backdrop, ou ajouter un prop optionnel `dismissible?: boolean` (défaut `true`) qui conditionne ce comportement. Le `Dialog` garderait ainsi sa flexibilité pour les autres usages.

2. **Approche robuste — prop `dismissible` sur le `Dialog`** : ajouter `dismissible?: boolean` à l'interface `DialogProps`. Le backdrop et la touche Escape ne fermeraient le modal que si `dismissible !== false`. Dans `ezkey-admin-ui/src/pages/integrations.tsx`, on passerait `dismissible={false}` au `CreateIntegrationDialog`.

3. **Approche UX avancée — confirmation si le formulaire est "dirty"** : React Hook Form expose `formState.isDirty` (vrai si au moins un champ a été modifié). On pourrait afficher un `window.confirm("Abandonner la saisie ?")` uniquement si `isDirty` est vrai avant de fermer. Cela permet le light dismiss sur un formulaire vide, mais protège la saisie en cours.

---

### Verification

Tester manuellement :
- Ouvrir le modal de création d'intégration
- Saisir quelques champs
- Cliquer en dehors → le modal ne doit pas se fermer (ou demander confirmation)
- Tester "Cancel" et "Create" → doivent toujours fonctionner normalement
- Tester la touche Escape → même comportement que le backdrop

---

### Décision à prendre

Quelle approche préfères-tu ?
- **Simple** (désactiver complètement le clic backdrop pour ce modal)
- **Flexible** (prop `dismissible` pour garder la configurabilité)
- **UX avancée** (confirmation uniquement si `isDirty`)
