# Stratégie de Pagination CLI - Ezkey

Ce fichier sera en UTF-8 sans BOM.

## Résumé Exécutif

Ce document établit la stratégie de gestion de pagination et tri pour les commandes CLI d'Ezkey qui interrogent les endpoints paginés de l'Admin API.

---

## 1. Inventaire des Contrôleurs Pageable

### Contrôleurs avec Support de Pagination (Backend)

| Contrôleur | Endpoint | Défaut Page | Défaut Size | Défaut Sort | Champs Triables |
|------------|----------|-------------|-------------|-------------|-----------------|
| **AuditLogController** | `GET /api/v1/audit-logs` | 0 | 20 | createdAt,DESC | auditLogId, createdAt, eventType, eventStatus, apiName |
| **AuthAttemptController** | `GET /api/v1/auth-attempts` | 0 | 20 | createdAt,DESC | authAttemptId, createdAt, expiresAt, enrollmentId |
| **EnrollmentController** | `GET /api/v1/enrollments` | 0 | 20 | createdAt,DESC | enrollmentId, enrollmentName, createdAt, integrationId, status |
| **IntegrationController** | `GET /api/v1/integrations` | 0 | 20 | createdAt,DESC | id, createdAt, active |
| **AdminProvisioningController** | `GET /api/v1/admins` | 0 | 20 | createdAt,DESC | *(à documenter)* |

### Format de Réponse Spring Page

Les réponses paginées suivent la structure standard Spring Data Page :

```json
{
  "content": [...],           // Tableau des résultats
  "pageable": {
    "pageNumber": 0,           // Numéro de page (0-based)
    "pageSize": 20,            // Taille de la page
    "sort": {
      "sorted": true,
      "unsorted": false
    },
    "offset": 0
  },
  "totalPages": 5,             // Nombre total de pages
  "totalElements": 87,         // Nombre total d'éléments
  "last": false,               // Est-ce la dernière page ?
  "first": true,               // Est-ce la première page ?
  "numberOfElements": 20,      // Nombre d'éléments dans cette page
  "size": 20,                  // Taille de la page
  "number": 0,                 // Numéro de page (0-based)
  "empty": false               // La page est-elle vide ?
}
```

---

## 2. État Actuel du CLI

### Lacunes Identifiées

1. **Aucun paramètre de pagination** exposé (`--page`, `--size`)
2. **Aucun paramètre de tri** exposé (`--sort`)
3. **Pas de navigation** entre les pages
4. **Pas d'affichage des métadonnées** de pagination (page actuelle, total, etc.)
5. **Filtres incomplets** pour chaque contrôleur (voir CLI_CONTROLLER_REVIEW.md)

### Exemple de Commande Actuelle

```bash
# Commande actuelle (limitée)
ezkey admin enrollment list --integration-id 1

# Retourne toute la réponse JSON incluant les métadonnées de pagination
# sans permettre à l'utilisateur de naviguer ou de personnaliser
```

---

## 3. Stratégie Proposée

### 3.1 Approche : Progressive Enhancement

**Principe :** Améliorer progressivement l'expérience utilisateur sans casser l'existant.

#### Phase 1 : Paramètres de Base (MVP)
- Ajouter `--page` et `--size` comme options facultatives
- Maintenir les défauts du serveur (page=0, size=20)
- Conserver l'affichage JSON complet par défaut

#### Phase 2 : Tri
- Ajouter `--sort` avec validation des champs disponibles
- Format : `--sort field,direction` (ex: `--sort createdAt,desc`)

#### Phase 3 : Affichage Amélioré
- Ajouter `--summary` pour afficher les métadonnées de pagination
- Améliorer le formatage de la sortie avec des tableaux

#### Phase 4 : Navigation Interactive (optionnel)
- Considérer un mode interactif pour naviguer entre les pages

---

### 3.2 Spécifications Techniques

#### A. Options CLI Standard pour Commandes List

Toutes les commandes `list` des contrôleurs Pageable devront supporter :

```python
@click.option('--page', type=int, default=None,
              help='Page number (0-based). Default: 0')
@click.option('--size', type=int, default=None,
              help='Page size (number of results per page). Default: 20')
@click.option('--sort', type=str, default=None,
              help='Sort criteria in format: field,direction (e.g., createdAt,desc)')
@click.option('--summary', is_flag=True, default=False,
              help='Display pagination summary instead of full JSON')
```

#### B. Construction des Paramètres de Requête

```python
def build_pagination_params(page, size, sort):
    """
    Construit les paramètres de requête pour la pagination Spring.

    Args:
        page: Numéro de page (0-based, optionnel)
        size: Taille de page (optionnel)
        sort: Critères de tri au format "field,direction" (optionnel)

    Returns:
        Dict des paramètres de requête
    """
    params = {}

    if page is not None:
        params['page'] = page

    if size is not None:
        params['size'] = size

    if sort:
        # Validation du format
        if ',' not in sort:
            raise ValueError("Sort format must be: field,direction (e.g., createdAt,desc)")

        field, direction = sort.split(',', 1)
        direction = direction.lower()

        if direction not in ['asc', 'desc']:
            raise ValueError("Sort direction must be 'asc' or 'desc'")

        params['sort'] = f"{field},{direction}"

    return params
```

#### C. Affichage des Métadonnées de Pagination

```python
def display_page_summary(response_data):
    """
    Affiche un résumé des métadonnées de pagination.

    Args:
        response_data: La réponse JSON décodée (objet Page)
    """
    if not isinstance(response_data, dict):
        return

    # Extraire les métadonnées
    total_elements = response_data.get('totalElements', 0)
    total_pages = response_data.get('totalPages', 0)
    current_page = response_data.get('number', 0)
    page_size = response_data.get('size', 20)
    number_of_elements = response_data.get('numberOfElements', 0)
    is_first = response_data.get('first', False)
    is_last = response_data.get('last', False)

    # Affichage formaté
    click.echo(f"\n{Fore.CYAN}═══ Pagination Summary ═══{Style.RESET_ALL}")
    click.echo(f"Page:     {current_page + 1}/{total_pages} (showing {number_of_elements} items)")
    click.echo(f"Total:    {total_elements} items")
    click.echo(f"Per page: {page_size}")

    # Navigation hints
    if not is_first:
        click.echo(f"{Fore.YELLOW}← Previous: --page {current_page - 1}{Style.RESET_ALL}")
    if not is_last:
        click.echo(f"{Fore.YELLOW}→ Next:     --page {current_page + 1}{Style.RESET_ALL}")

    click.echo(f"{Fore.CYAN}═══════════════════════════{Style.RESET_ALL}\n")
```

#### D. Validation des Champs de Tri

Chaque contrôleur doit définir ses champs triables valides :

```python
# Dans admin.py ou dans un module de validation dédié
SORTABLE_FIELDS = {
    'audit-log': ['auditLogId', 'createdAt', 'eventType', 'eventStatus', 'apiName'],
    'auth-attempt': ['authAttemptId', 'createdAt', 'expiresAt', 'enrollmentId'],
    'enrollment': ['enrollmentId', 'enrollmentName', 'createdAt', 'integrationId', 'status'],
    'integration': ['id', 'createdAt', 'active'],
}

def validate_sort_field(entity_type, sort_string):
    """
    Valide que le champ de tri est supporté par le contrôleur.

    Args:
        entity_type: Type d'entité (ex: 'enrollment')
        sort_string: String de tri au format "field,direction"

    Raises:
        ValueError si le champ n'est pas valide
    """
    if not sort_string:
        return

    field = sort_string.split(',')[0]
    valid_fields = SORTABLE_FIELDS.get(entity_type, [])

    if field not in valid_fields:
        raise ValueError(
            f"Invalid sort field '{field}' for {entity_type}. "
            f"Valid fields: {', '.join(valid_fields)}"
        )
```

---

### 3.3 Modes d'Affichage

#### Mode 1 : JSON Complet (par défaut, rétrocompatible)

```bash
ezkey admin enrollment list
# Retourne tout l'objet Page en JSON
```

#### Mode 2 : Avec Résumé de Pagination

```bash
ezkey admin enrollment list --summary
# Affiche les métadonnées + le JSON complet
```

#### Mode 3 : Avec Pagination et Tri Personnalisés

```bash
ezkey admin enrollment list --page 2 --size 50 --sort enrollmentName,asc --summary
```

#### Mode 4 : Futur - Table Formatée (Phase 3+)

```bash
ezkey admin enrollment list --format table --page 0 --size 10
# Affichage en tableau ASCII avec les colonnes principales
```

---

### 3.4 Gestion des Erreurs

#### Erreurs à Gérer

1. **Numéro de page invalide** (négatif, hors limite)
   - Le serveur retourne une page vide si page > totalPages
   - CLI devrait avertir l'utilisateur

2. **Taille de page invalide** (négative, trop grande)
   - Valider côté client : `1 <= size <= 100`

3. **Champ de tri invalide**
   - Valider contre la liste SORTABLE_FIELDS
   - Retourner un message clair avec les champs valides

4. **Format de tri invalide**
   - Valider le format `field,direction`
   - Direction doit être `asc` ou `desc`

#### Exemple de Gestion d'Erreur

```python
def validate_pagination_options(page, size, sort, entity_type):
    """
    Valide les options de pagination avant l'appel API.

    Raises:
        click.BadParameter si les paramètres sont invalides
    """
    if page is not None and page < 0:
        raise click.BadParameter("Page number must be >= 0")

    if size is not None:
        if size < 1:
            raise click.BadParameter("Page size must be >= 1")
        if size > 100:
            OutputUtils.warning("Large page size may impact performance")

    if sort:
        try:
            validate_sort_field(entity_type, sort)
        except ValueError as e:
            raise click.BadParameter(str(e))
```

---

## 4. Plan d'Implémentation

### Étape 1 : Utilitaires de Pagination (1 heure)

**Fichier :** `ezkey-cli-python/ezkey_cli/utils/pagination_utils.py`

**Contenu :**
- `build_pagination_params(page, size, sort) -> dict`
- `display_page_summary(response_data) -> None`
- `validate_sort_field(entity_type, sort_string) -> None`
- `SORTABLE_FIELDS` constant dict

**Tests :** Ajouter tests unitaires pour chaque fonction.

---

### Étape 2 : Mise à Jour d'une Commande Pilote (1-2 heures)

**Commande choisie :** `ezkey admin enrollment list`

**Raison :**
- Cas d'usage fréquent
- Bon nombre de champs triables (5)
- Documentation complète dans le contrôleur

**Modifications :**
1. Ajouter les options `--page`, `--size`, `--sort`, `--summary`
2. Utiliser `build_pagination_params()` pour construire les params
3. Passer les params à `http_client.get(url, params=params)`
4. Si `--summary`, appeler `display_page_summary()`
5. Ajouter validation avec `validate_pagination_options()`

**Test manuel :**
```bash
# Test basique
ezkey admin enrollment list

# Test avec pagination
ezkey admin enrollment list --page 1 --size 10

# Test avec tri
ezkey admin enrollment list --sort enrollmentName,asc

# Test avec résumé
ezkey admin enrollment list --summary

# Test combiné
ezkey admin enrollment list --page 0 --size 5 --sort createdAt,desc --summary
```

---

### Étape 3 : Déploiement sur Autres Commandes List (3-4 heures)

**Ordre de priorité :**

1. **AuthAttemptController** (`ezkey admin auth-attempt list`)
   - Priorité haute (audit/sécurité)
   - 4 champs triables

2. **AuditLogController** (`ezkey admin audit-log list`)
   - Priorité haute (compliance)
   - 5 champs triables

3. **IntegrationController** (`ezkey admin integration list`)
   - Priorité moyenne
   - 3 champs triables

4. **AdminProvisioningController** (`ezkey admin provisioning list`)
   - À créer d'abord (commande n'existe pas encore)

**Processus :**
- Copier le pattern de l'étape 2
- Ajuster `entity_type` pour la validation
- Tester chaque commande individuellement

---

### Étape 4 : Documentation (1 heure)

**Fichiers à mettre à jour :**

1. **README du CLI** (`ezkey-cli-python/README.md`)
   - Section "Pagination and Sorting"
   - Exemples d'utilisation

2. **CLI_CONTROLLER_REVIEW.md**
   - Marquer les actions comme complétées

3. **Help strings** intégrées
   - Chaque commande doit expliquer la pagination dans `--help`

**Exemple de documentation :**
```markdown
### Pagination and Sorting

All list commands support pagination and sorting:

```bash
# Navigate through pages
ezkey admin enrollment list --page 0 --size 25

# Sort results
ezkey admin enrollment list --sort enrollmentName,asc

# Display pagination summary
ezkey admin enrollment list --summary

# Combine options
ezkey admin enrollment list --page 1 --size 10 --sort createdAt,desc --summary
```

**Valid sort directions:** `asc` (ascending), `desc` (descending)

**Sort fields by command:**
- `enrollment list`: enrollmentId, enrollmentName, createdAt, integrationId, status
- `auth-attempt list`: authAttemptId, createdAt, expiresAt, enrollmentId
- `audit-log list`: auditLogId, createdAt, eventType, eventStatus, apiName
- `integration list`: id, createdAt, active
```

---

### Étape 5 : Tests d'Intégration (2 heures)

**Scénarios de test :**

1. **Navigation de pagination**
   ```python
   def test_enrollment_list_pagination():
       # Créer 50 enrollments
       # Lister avec size=10
       # Vérifier que 5 pages existent
       # Parcourir les pages
       # Vérifier que tous les enrollments sont trouvés
   ```

2. **Tri ascendant/descendant**
   ```python
   def test_enrollment_list_sorting():
       # Créer enrollments avec noms différents
       # Trier par nom asc
       # Vérifier l'ordre
       # Trier par nom desc
       # Vérifier l'ordre inversé
   ```

3. **Validation des erreurs**
   ```python
   def test_enrollment_list_validation():
       # Page négative -> erreur
       # Size négative -> erreur
       # Champ de tri invalide -> erreur
       # Format de tri invalide -> erreur
   ```

4. **Affichage du résumé**
   ```python
   def test_enrollment_list_summary():
       # Créer 25 enrollments
       # Lister avec --summary
       # Vérifier que les métadonnées sont affichées
   ```

---

## 5. Considérations Futures

### 5.1 Mode Interactif

Possibilité d'ajouter un mode interactif avec navigation au clavier :

```bash
ezkey admin enrollment list --interactive
# Affiche la première page avec options :
# [N]ext page, [P]revious page, [S]ort, [F]ilter, [Q]uit
```

**Bibliothèques possibles :**
- `click-repl` pour REPL interactif
- `prompt_toolkit` pour auto-completion
- `rich` pour affichage de tableaux enrichis

**Effort estimé :** 8-10 heures

---

### 5.2 Format de Sortie Alternatifs

Supporter différents formats d'affichage :

```bash
# JSON (par défaut)
ezkey admin enrollment list

# Table ASCII
ezkey admin enrollment list --format table

# CSV (pour export)
ezkey admin enrollment list --format csv > enrollments.csv

# Markdown (pour documentation)
ezkey admin enrollment list --format markdown
```

**Effort estimé :** 4-6 heures

---

### 5.3 Auto-Pagination (Fetch All)

Option pour récupérer automatiquement toutes les pages :

```bash
ezkey admin enrollment list --all
# Récupère automatiquement toutes les pages
# Affiche ou exporte la liste complète
```

**Attention :** Peut être coûteux pour de gros volumes.

**Effort estimé :** 2-3 heures

---

### 5.4 Cache et Performance

Pour améliorer l'expérience en mode interactif :
- Mettre en cache les pages déjà visitées
- Précharger la page suivante

**Effort estimé :** 4-5 heures

---

## 6. Checklist de Validation

### Pour Chaque Commande List Implémentée

- [ ] Options `--page`, `--size`, `--sort` ajoutées
- [ ] Option `--summary` ajoutée
- [ ] Validation des paramètres implémentée
- [ ] Champs triables documentés dans SORTABLE_FIELDS
- [ ] Tests unitaires pour validation
- [ ] Tests d'intégration pour pagination/tri
- [ ] Documentation dans `--help`
- [ ] README mis à jour
- [ ] CLI_CONTROLLER_REVIEW.md mis à jour

---

## 7. Résumé des Temps Estimés

| Étape | Description | Temps Estimé |
|-------|-------------|--------------|
| 1 | Utilitaires de pagination | 1h |
| 2 | Commande pilote (enrollment list) | 1-2h |
| 3 | Autres commandes (3-4 commandes) | 3-4h |
| 4 | Documentation | 1h |
| 5 | Tests d'intégration | 2h |
| **Total MVP** | | **8-10h** |
| Futures (interactif, formats, all) | | +18-24h |

---

## 8. Conclusion

Cette stratégie propose une approche **progressive et pragmatique** pour ajouter le support de pagination et tri au CLI Ezkey.

**Points clés :**
- ✅ **Rétrocompatibilité** : Les commandes existantes continuent de fonctionner
- ✅ **Extensibilité** : Architecture réutilisable pour toutes les commandes list
- ✅ **UX Progressive** : De simple (JSON brut) à avancé (résumé, interactif)
- ✅ **Validation robuste** : Erreurs claires et précoces
- ✅ **Testabilité** : Design orienté tests

**Prochaines actions recommandées :**
1. Valider cette stratégie avec l'équipe
2. Implémenter l'étape 1 (utilitaires)
3. Implémenter l'étape 2 (commande pilote)
4. Réviser et ajuster si nécessaire
5. Déployer sur les autres commandes

---

**Dernière mise à jour :** 2026-01-30
**Auteur :** GitHub Copilot
**Status :** Proposition - En attente de validation
