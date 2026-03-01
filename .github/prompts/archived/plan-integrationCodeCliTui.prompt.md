# Plan : Intégration du Code d'Intégration au CLI et TUI

**TL;DR** — Ajouter le paramètre `code` (identifiant métier texte, format slug) à deux endroits : (1) la commande CLI `integration create` en tant qu'option `--code` requise, et (2) le formulaire modal TUI `CreateIntegrationModal` avec un nouveau champ input validé. Le backend accepte déjà ce paramètre avec validation et contrainte d'unicité. L'utilisateur testera après pip install.

---

## Steps

### **A. Mise à jour CLI**
[ezkey-cli-python/ezkey_cli/commands/admin.py](ezkey-cli-python/ezkey_cli/commands/admin.py) — Modifier la fonction `create_integration()` (~ligne 179)

1. **Ajouter l'option CLI** : Nouvelle option `--code` avec les attributs :
   - Type : `str`
   - **Requis** (`required=True`)
   - Help text : `"Unique code for the integration (alphanumeric, hyphens, underscores). Example: web-portal, mobile-app"`
   - Format slug déjà validé côté backend

2. **Intégrer dans le payload JSON** : Passer `code` au body de POST `/api/v1/integrations`
   - Ajouter `code` au `payload` qui sera sérialisé
   - Le payload contient déjà `i18n` et optionnellement `logo`

3. **Utilisation CLI attendue** :
   ```
   ezkey integration create --code my-app --name "My App" --description "..."
   ```

---

### **B. Mise à jour TUI – Modal de création**
[ezkey-cli-python/ezkey_cli/tui/screens/integration_create.py](ezkey-cli-python/ezkey_cli/tui/screens/integration_create.py) — Fichier entier

1. **Ajouter le champ input pour le code** dans le formulaire :
   - ID du champ : `#code`
   - Label : `"Integration Code"`
   - Placeholder : `"e.g., web-portal, mobile-app"`
   - Requis (tout comme le nom)
   - Validators : durée min=2, max=100 caractères (utiliser pattern Textual existant)

2. **Validator personnalisé (optionnel bonus)** :
   - Pattern regex : `^[a-zA-Z0-9_-]+$` (alphanumeric + hyphens/underscores)
   - Feedback visuel si format invalide (déjà fait dans `admin_provisioning_create.py` — même pattern à replier)

3. **Mettre à jour `_create_integration()`** :
   - Récupérer la valeur du champ `code` : `self.query_one("#code", Input).value.strip()`
   - Ajouter `code` au payload JSON envoyé au backend
   - Passer `code` avant envoi POST

4. **Gestion errors** :
   - Si le backend retourne **409 Conflict** (code déjà existant pour ce tenant) → afficher message : `"This integration code is already in use. Please choose a different one."`
   - Les autres erreurs de validation (backend) remontent déjà via exception handler existant

---

### **C. Optionnel : Mettre à jour l'écran de liste**
[ezkey-cli-python/ezkey_cli/tui/screens/integrations.py](ezkey-cli-python/ezkey_cli/tui/screens/integrations.py) — Ajouter la colonne `Code` à la DataTable

1. Ajouter une colonne `Code` à la table (entre `ID` et `Name` pour clarté)
2. Mapper le champ `code` depuis la réponse API list

---

## Verification

**Manuel (par l'utilisateur)** :

1. **CLI** :
   ```bash
   cd ezkey-cli-python
   pip install -e .

   # Test : créer une intégration avec code
   ezkey admin integration create --code test-app --name "Test App"

   # Test : lister pour vérifier le code
   ezkey admin integration list

   # Test : essayer avec code duplicata → doit retourner erreur 409
   ezkey admin integration create --code test-app --name "Another"
   ```

2. **TUI** :
   ```bash
   ezkey admin tui

   # Dans l'interface TUI :
   # 1. Menu Admin → Integrations (ou équivalent)
   # 2. Cliquer "Create Integration"
   # 3. Vérifier que le champ "Integration Code" est présent (requis)
   # 4. Remplir : Code=my-test, Name=My Test, Description=...
   # 5. Cliquer Create → doit créer sans erreur
   # 6. Vérifier que la nouvelle intégration apparaît dans la liste avec le code
   # 7. Tester doublon → essayer le même code → doit montrer erreur "already in use"
   ```

3. **Validation format** :
   - Code valide : `web-portal`, `mobile_app`, `test-123`, `API-Key`
   - Code invalide : `My App` (espaces), `app/web` (slash), `app@test` (caractères spéciaux)

---

## Decisions

- **Code = identifiant métier** (pas UUID) — décision déjà prise côté backend, on suit le même pattern
- **Unicité par tenant** — backend gère via constraint, CLI/TUI ne besoin de vérifier en amont (validation server-side suffisante)
- **Required vs optional** : `code` est **obligatoire** à la création (comme `name`) — pas d'auto-génération
- **Immutabilité** : Pas de champ "edit code" après création (backend ne supporte pas, et design décidé)
- **Colonne liste** : Optionnelle mais recommandée pour lisibilité (l'utilisateur voit directement le code)

---

## Context de Projet

### Backend (déjà implémenté ✅)
- **Entity** : `Integration.code` (VARCHAR 100, NOT NULL)
- **Constraint** : Unique `(tenant_id, integration_code)` en database
- **Validation** : `@NotBlank`, `@Size(2-100)`, `@Pattern("^[a-zA-Z0-9_-]+$")`
- **Service** : Pré-vérification d'unicité + exception `IntegrationCodeAlreadyExistsException`
- **Exception Handler** : Retourne 409 Conflict pour duplicatas
- **DTOs** : `IntegrationCreateRequestDto` et `IntegrationCreateRequest` contiennent `code`

### CLI/TUI (À implémenter)
- **CLI** : Option `--code` dans la commande `integration create`
- **TUI** : Champ input "Integration Code" dans modal `CreateIntegrationModal`
- **Liste** : Colonne "Code" (optionnel mais recommandé)
