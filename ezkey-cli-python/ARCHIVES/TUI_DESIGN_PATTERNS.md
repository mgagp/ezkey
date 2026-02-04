# TUI Design Patterns - Ezkey Admin Console

## Stratégie : Profondeur d'abord

**Décision :** Implémenter un écran complet (Integrations) avec tous les patterns (list, detail, create, filter, sort) avant de passer aux autres écrans.

**Rationale :**
- Pattern réutilisable établi et testé
- Moins de refactoring futur
- Console cohérente dès le départ

---

## Patterns UI Retenus

### 1. Master-Detail : Écran séparé (push screen)
```
List → Enter on row → Detail screen → Esc → back to List
```
- Simple à implémenter
- Navigation claire (stack Textual)
- Familier aux DevOps (vim/kubectl style)

### 2. Filtres : Inline ou prompt modal
```
Press 'f' → Input modal "Filter by name:" → Apply
```
- Pas de panel latéral (trop complexe)
- Sticky header optionnel pour afficher les filtres actifs

### 3. Tri : Cycle avec binding
```
Press 's' → Cycle: id↑, id↓, name↑, name↓
```
- Simple toggle, pas de menu

### 4. Création/Édition : Modal overlay
```
Press 'c' → Modal form wizard → Submit or Esc to cancel
```
- Isolé du contexte
- Focus sur la tâche
- Plus simple que panel latéral

---

## Plan d'Implémentation

### Phase 1 : Integrations (complet)
1. ✅ List + pagination
2. Detail screen (Enter)
3. Create modal ('c')
4. Filter prompt ('f')
5. Sort cycle ('s')
6. Delete confirmation ('d')

### Phase 2 : Template réutilisable
- Extract `BaseCrudScreen`
- Extract `DetailScreen` base
- Extract `FormModal` base

### Phase 3 : Largeur (autres écrans)
- Enrollments, Audit Logs, Auth Attempts
- Héritent des templates (80% du code déjà là)

---

## Principes
- **Simplicité** : Patterns simples à implémenter
- **Pragmatisme** : Éviter complexité inutile
- **DevOps-friendly** : Keyboard-first, pas de hand-holding excessif
- **Cohérence** : Même UX partout

---

## Bindings Standards

| Key | Action |
|-----|--------|
| `h` | Home |
| `r` | Refresh |
| `Enter` | Detail |
| `c` | Create |
| `e` | Edit (in detail) |
| `d` | Delete (in detail) |
| `f` | Filter |
| `s` | Sort |
| `n/p` | Next/Prev page |
| `Esc` | Back/Cancel |
| `q` | Quit |

---

## Error Handling UX

### Stratégie de gestion des erreurs

**Principe:** Console d'administration = clarté et traçabilité.

#### 1. Erreurs d'action (création/modification bloquée)
- **Affichage:** Message inline dans le modal/formulaire + label d'erreur visible
- **Couleur:** Rouge/warning pour attirer l'attention
- **Contenu:** Message du backend (API) + code d'erreur si disponible
- **Exemple:** "Cannot create enrollment for system integration. System integrations are reserved..."
- **Persistance:** Reste affiché jusqu'à correction ou fermeture

#### 2. Erreurs critiques (401/403, backend down)
- **Affichage:** Modal bloquant avec explication
- **Actions:** Retry / Cancel / Logout (selon contexte)
- **Exemple:** "Authentication failed (401). Please login again."

#### 3. Erreurs de validation (champs manquants/invalides)
- **Affichage:** Message inline sous le champ concerné
- **Validation:** Client-side avant API call quand possible
- **Exemple:** "Integration ID must be a number"

#### 4. Succès d'opération
- **Affichage:** Message bref (✓) dans la vue liste après retour
- **Alternative:** Toast non-bloquant (optionnel pour phase future)

### Patterns d'implémentation

```python
# Dans modal de création:
@property
def error_message(self) -> str:
    return self._error_message

@error_message.setter
def error_message(self, msg: str) -> None:
    self._error_message = msg
    error_label = self.query_one("#error_label", Label)
    error_label.update(f"❌ {msg}")
    error_label.display = bool(msg)

# Lors de l'appel API:
try:
    response = api_client._post("/api/v1/enrollments", payload)
    if not response:
        self.error_message = "Failed to create enrollment (no response)"
except ApiError as e:
    self.error_message = e.message  # From API error response
```

### API Error Response Structure

Ezkey Admin API retourne:
```json
{
  "code": "INVALID_ARGUMENT",
  "message": "Cannot create enrollment for system integration...",
  "path": "/api/v1/enrollments",
  "timestamp": "2026-01-31T16:36:40.386393607Z"
}
```

**Extraction:** Toujours afficher `message` (le plus utile pour l'admin).

---

## Next Steps
1. Implémenter detail screen pour Integrations
2. Implémenter create modal
3. Raffiner patterns
4. Dupliquer vers autres écrans
