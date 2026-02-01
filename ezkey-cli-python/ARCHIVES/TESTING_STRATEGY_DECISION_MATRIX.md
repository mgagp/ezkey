# Stratégie de Tests Fonctionnels: ezkey-tests vs CLI Tests

## Vue d'Ensemble

Le projet Ezkey possède **deux suites de tests fonctionnels distinctes** avec des responsabilités différentes:

1. **`ezkey-tests/`** - Tests fonctionnels Java pour la plateforme backend
2. **`ezkey-cli-python/tests/integration/`** - Tests fonctionnels Python pour le CLI

Ce document établit les **critères de décision** pour déterminer où implémenter un nouveau test fonctionnel.

## 🎯 Principe Directeur

```
┌─────────────────────────────────────────────────────────────┐
│ RÈGLE D'OR                                                  │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Si le test valide le COMPORTEMENT DES APIs                │
│  → ezkey-tests (Java)                                       │
│                                                              │
│  Si le test valide l'EXPÉRIENCE CLI                        │
│  → ezkey-cli-python/tests (Python)                         │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

## 📊 Tableau de Décision

| Critère | ezkey-tests (Java) | CLI Tests (Python) |
|---------|-------------------|-------------------|
| **Objectif** | Valider le backend et les APIs | Valider l'expérience utilisateur CLI |
| **Langage** | Java (JUnit 5) | Python (pytest) |
| **Scope** | API REST, logique métier, DB | Interface CLI, workflow utilisateur |
| **Focus** | Réponses HTTP, codes status, données | Commandes, arguments, output, UX |
| **Utilisateur** | Développeurs d'intégrations, SDK | Développeurs et ops utilisant CLI |
| **Environnement** | Docker stack complet | Docker stack + CLI installé sur hôte |

## 🔍 Critères de Décision Détaillés

### ✅ Test va dans `ezkey-tests/` SI:

#### 1. **Validation du Comportement API**
- Teste les réponses HTTP des APIs
- Valide les codes de statut (200, 400, 403, 404, etc.)
- Vérifie la structure des réponses JSON
- Teste les headers HTTP
- Valide les erreurs d'API

**Exemple:**
```java
// ezkey-tests/
@Test
public void testCreateIntegration_Success() {
    IntegrationCreateRequestDto request = new IntegrationCreateRequestDto()
        .name("Test Integration")
        .type(IntegrationType.STANDARD);

    ResponseEntity<IntegrationResponseDto> response =
        adminApi.createIntegration(tenantAdminToken, request);

    // Teste la réponse API
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody().getName()).isEqualTo("Test Integration");
    assertThat(response.getHeaders().getLocation()).isNotNull();
}
```

#### 2. **Logique Métier Backend**
- Teste les règles métier
- Valide les validations côté serveur
- Teste la logique d'autorisation
- Vérifie l'isolation des tenants
- Teste les workflows complexes backend

**Exemple:**
```java
// ezkey-tests/
@Test
public void testTenantIsolation_IntegrationAccess() {
    // Tenant A ne peut pas accéder aux intégrations de Tenant B
    Integer tenantAIntegrationId = createIntegrationForTenantA();

    ResponseEntity<?> response = adminApi.getIntegration(
        tenantBAdminToken,  // Token de Tenant B
        tenantAIntegrationId // ID de Tenant A
    );

    // Validation d'isolation
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
}
```

#### 3. **Tests d'Intégration Backend**
- Teste l'interaction entre modules (admin-api ↔ auth-api ↔ crypto-api)
- Valide les transactions distribuées
- Teste la cohérence des données
- Vérifie les états en base de données

**Exemple:**
```java
// ezkey-tests/
@Test
public void testEnrollmentWorkflow_EndToEnd() {
    // 1. Créer integration (Admin API)
    Integer integrationId = createIntegration();

    // 2. Créer enrollment (Admin API)
    Integer enrollmentId = createEnrollment(integrationId);

    // 3. Bind device (Auth API)
    bindDevice(enrollmentId);

    // 4. Verify enrollment (Auth API)
    verifyEnrollment(enrollmentId);

    // 5. Vérifier état en DB
    assertEnrollmentIsActive(enrollmentId);
}
```

#### 4. **Tests de Performance/Charge Backend**
- Teste les performances des APIs
- Valide la pagination
- Teste les requêtes lourdes
- Vérifie les timeouts

#### 5. **Tests de Sécurité Backend**
- Teste l'authentification API
- Valide les tokens
- Teste les rate limits
- Vérifie les validations de sécurité

### ✅ Test va dans `ezkey-cli-python/tests/` SI:

#### 1. **Validation de l'Interface CLI**
- Teste les commandes CLI
- Valide les arguments et options
- Vérifie l'output console
- Teste les codes de sortie
- Valide les messages d'erreur CLI

**Exemple:**
```python
# ezkey-cli-python/tests/
def test_admin_integration_list_output_format():
    """Teste le format de sortie de la commande list."""
    runner = CliRunner()
    result = runner.invoke(cli, ["admin", "integration", "list"])

    # Valide l'expérience CLI
    assert result.exit_code == 0
    assert "┌" in result.output  # Table formatting
    assert "│" in result.output
    assert "Integration Name" in result.output
```

#### 2. **Workflow Utilisateur CLI**
- Teste les séquences de commandes
- Valide les interactions utilisateur
- Teste les prompts interactifs
- Vérifie le mode batch vs interactif

**Exemple:**
```python
# ezkey-cli-python/tests/
def test_admin_login_workflow_with_approval():
    """Teste le workflow complet de login admin."""
    # 1. Initier login
    # 2. Attendre approbation (simulée)
    # 3. Recevoir token
    # 4. Vérifier token sauvegardé

    result = subprocess.run(["ezkey", "admin", "auth", "login"])

    # Valide l'expérience utilisateur
    assert result.returncode == 0
    assert "✅" in result.stdout
    assert "Authentication successful" in result.stdout
```

#### 3. **Configuration CLI**
- Teste la gestion de config
- Valide les fichiers de configuration
- Teste les variables d'environnement
- Vérifie les profils

**Exemple:**
```python
# ezkey-cli-python/tests/
def test_configure_interactive():
    """Teste la configuration interactive du CLI."""
    runner = CliRunner()
    result = runner.invoke(
        cli,
        ["configure", "interactive"],
        input="http://localhost:9080\nhttp://localhost:8080\n"
    )

    assert result.exit_code == 0
    assert "Configuration saved" in result.output
```

#### 4. **Formats de Sortie CLI**
- Teste JSON output
- Valide les tables
- Teste les couleurs/formatage
- Vérifie la verbosité

#### 5. **Erreurs et Messages CLI**
- Teste les messages d'erreur utilisateur
- Valide l'aide (--help)
- Teste les suggestions de commandes
- Vérifie les codes de sortie

## 🎨 Exemples de Cas d'Usage

### Cas 1: Créer une Intégration

```
┌─────────────────────────────────────────────────────────────┐
│ Fonctionnalité: Créer une intégration                      │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│ ezkey-tests (Java):                                         │
│ ✅ POST /api/v1/admin/integrations retourne 201           │
│ ✅ Response contient l'ID de l'intégration                 │
│ ✅ Integration est en DB avec status ACTIVE                │
│ ✅ Tenant isolation fonctionne                             │
│ ✅ Validation des champs requis                            │
│                                                              │
│ CLI Tests (Python):                                         │
│ ✅ ezkey admin integration create affiche succès           │
│ ✅ Output contient l'ID de l'intégration                   │
│ ✅ --from-file charge correctement le JSON                 │
│ ✅ Erreurs sont affichées clairement                       │
│ ✅ Aide (--help) est complète                              │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### Cas 2: Authentification Admin

```
┌─────────────────────────────────────────────────────────────┐
│ Fonctionnalité: Authentification admin                     │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│ ezkey-tests (Java):                                         │
│ ✅ POST /api/v1/admin/auth/login crée auth_attempt         │
│ ✅ Token est généré correctement                           │
│ ✅ Token expire au bon moment                              │
│ ✅ Rate limiting fonctionne                                │
│ ✅ Validation device signature                             │
│                                                              │
│ CLI Tests (Python):                                         │
│ ✅ ezkey admin auth login attend l'approbation             │
│ ✅ Progress indicator s'affiche                            │
│ ✅ Token est sauvegardé dans config                        │
│ ✅ Message de succès avec expiration                       │
│ ✅ Timeout est géré correctement                           │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### Cas 3: Lister des Intégrations avec Pagination

```
┌─────────────────────────────────────────────────────────────┐
│ Fonctionnalité: Lister intégrations (paginé)              │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│ ezkey-tests (Java):                                         │
│ ✅ GET /api/v1/admin/integrations?page=0&size=20           │
│ ✅ Pagination metadata correct (totalPages, totalElements) │
│ ✅ Filtrage par tenant fonctionne                          │
│ ✅ Sorting fonctionne                                       │
│ ✅ Performance acceptable (1000+ records)                  │
│                                                              │
│ CLI Tests (Python):                                         │
│ ✅ ezkey admin integration list affiche tableau            │
│ ✅ --page et --size fonctionnent                           │
│ ✅ Indicateur "Page 1 of 5" visible                        │
│ ✅ --format json retourne JSON valide                      │
│ ✅ Liste vide affiche message approprié                    │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

## 🚦 Arbre de Décision

```
Nouveau test fonctionnel à implémenter
│
├─► Question 1: Teste-t-il une API REST directement?
│   │
│   ├─► OUI → ezkey-tests
│   │
│   └─► NON → Question 2
│
├─► Question 2: Teste-t-il l'output/comportement CLI?
│   │
│   ├─► OUI → CLI Tests
│   │
│   └─► NON → Question 3
│
├─► Question 3: Est-ce un test de logique métier backend?
│   │
│   ├─► OUI → ezkey-tests
│   │
│   └─► NON → Question 4
│
├─► Question 4: Est-ce un workflow utilisateur via CLI?
│   │
│   ├─► OUI → CLI Tests
│   │
│   └─► NON → Question 5
│
└─► Question 5: Teste-t-il l'intégration backend (API ↔ API)?
    │
    ├─► OUI → ezkey-tests
    │
    └─► NON → Discuter avec l'équipe
```

## 🔄 Tests Potentiellement Dupliqués (Acceptables)

Certains tests peuvent exister dans les deux suites, mais avec des objectifs différents:

### Exemple: Créer une Intégration

```java
// ezkey-tests/ - Focus: API et données
@Test
public void testCreateIntegration_ValidatesBusinessRules() {
    // Teste que l'API valide correctement les règles métier
    IntegrationCreateRequestDto request = invalidRequest();
    ResponseEntity<?> response = adminApi.createIntegration(token, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().getErrors()).contains("name is required");
}
```

```python
# ezkey-cli-python/tests/ - Focus: UX et messages
def test_create_integration_shows_validation_errors():
    """Teste que les erreurs de validation sont bien affichées."""
    runner = CliRunner()
    result = runner.invoke(cli, [
        "admin", "integration", "create",
        "--name", ""  # Invalid: empty name
    ])

    # Vérifie que l'utilisateur comprend l'erreur
    assert result.exit_code == 1
    assert "❌" in result.output
    assert "name is required" in result.output
    assert "Try: ezkey admin integration create --help" in result.output
```

**Les deux tests sont valides car:**
- Java teste la **réponse API** (codes status, structure erreur)
- Python teste **l'expérience utilisateur** (message clair, aide contextuelle)

## 📋 Checklist pour Nouveau Test

Avant d'implémenter un nouveau test fonctionnel:

- [ ] **Question principale**: Ce test valide-t-il le backend ou l'expérience CLI?
- [ ] **Scope**: Quel est le scope du test (API vs CLI)?
- [ ] **Objectif**: Qu'est-ce qui est validé (données vs UX)?
- [ ] **Audience**: Qui bénéficie du test (dev backend vs dev/ops CLI)?
- [ ] **Maintenance**: Où sera-t-il le plus facile à maintenir?
- [ ] **Duplication**: Y a-t-il déjà un test similaire dans l'autre suite?

## 🎯 Guidelines Spécifiques

### Pour ezkey-tests (Java)

**À FAIRE:**
- ✅ Tester les APIs REST directement
- ✅ Valider la logique métier
- ✅ Vérifier l'état en base de données
- ✅ Tester l'isolation des tenants
- ✅ Valider les règles de sécurité
- ✅ Tester les performances backend

**À ÉVITER:**
- ❌ Parser l'output CLI
- ❌ Tester les formats de sortie utilisateur
- ❌ Valider les messages d'erreur CLI
- ❌ Tester la configuration CLI

### Pour CLI Tests (Python)

**À FAIRE:**
- ✅ Tester les commandes CLI
- ✅ Valider l'output utilisateur
- ✅ Vérifier les codes de sortie
- ✅ Tester la configuration CLI
- ✅ Valider l'expérience interactive
- ✅ Tester les formats de sortie

**À ÉVITER:**
- ❌ Tester les APIs REST directement (sauf pour helpers)
- ❌ Valider la logique métier backend
- ❌ Vérifier l'état DB (sauf helpers opportunistes)
- ❌ Tester les performances backend

## 🔧 Exceptions et Cas Spéciaux

### 1. Helpers Opportunistes (CLI Tests)

Les CLI tests **PEUVENT** utiliser DB/API directement via helpers pour:
- Setup de test (créer données)
- Vérification d'état (confirmer opération)
- Cleanup (nettoyer après test)

**Mais** le test lui-même doit valider le CLI, pas l'API.

```python
# OK: Helper utilise DB pour setup
def test_integration_get(database_helper):
    # Setup via helper (pas le focus du test)
    integration_id = database_helper.create_integration()

    # TEST: Valide le CLI
    result = subprocess.run(["ezkey", "admin", "integration", "get", "--id", str(integration_id)])

    assert result.returncode == 0
    assert f"ID: {integration_id}" in result.stdout
```

### 2. Tests de Bout en Bout (Potentiellement les Deux)

Pour les workflows complets impliquant backend ET CLI:

```
Backend workflow (ezkey-tests):
└─> Teste API ↔ API ↔ DB

CLI workflow (CLI tests):
└─> Teste CLI ↔ API ↔ Utilisateur
```

**Exemple:**
- **ezkey-tests**: Teste le workflow d'enrollment (API → API → DB)
- **CLI tests**: Teste le workflow d'enrollment (CLI → API → Feedback utilisateur)

## 📊 Matrice de Responsabilités

| Aspect | ezkey-tests | CLI Tests | Les Deux |
|--------|-------------|-----------|----------|
| **API REST responses** | ✅ Primaire | ❌ | |
| **Codes status HTTP** | ✅ Primaire | ❌ | |
| **Logique métier** | ✅ Primaire | ❌ | |
| **État base de données** | ✅ Primaire | Helpers seulement | |
| **Isolation tenants** | ✅ Primaire | ❌ | |
| **Sécurité backend** | ✅ Primaire | ❌ | |
| **Performance backend** | ✅ Primaire | ❌ | |
| **Output CLI** | ❌ | ✅ Primaire | |
| **Codes de sortie** | ❌ | ✅ Primaire | |
| **Configuration CLI** | ❌ | ✅ Primaire | |
| **Messages d'erreur CLI** | ❌ | ✅ Primaire | |
| **Formats de sortie** | ❌ | ✅ Primaire | |
| **Workflow utilisateur** | Backend | Frontend (CLI) | |
| **Bootstrap/Setup** | Setup Java | Setup Python | |

## 🚀 Workflow de Développement

### Ajouter un Nouveau Feature

```
1. Développer le backend (APIs)
   └─> Ajouter tests dans ezkey-tests/

2. Développer le CLI (commandes)
   └─> Ajouter tests dans ezkey-cli-python/tests/

3. Documentation
   └─> README, USAGE_GUIDE, etc.
```

### Modifier un Feature Existant

```
1. Identifier l'impact:
   ├─> API change? → Mettre à jour ezkey-tests/
   └─> CLI change? → Mettre à jour CLI tests/

2. Exécuter les deux suites:
   ├─> mvn clean verify (ezkey-tests)
   └─> pytest tests/integration/ (CLI tests)

3. Vérifier les deux passent ✅
```

## 📚 Exemples de Classification

| Test | Suite | Raison |
|------|-------|--------|
| POST /api/v1/admin/integrations retourne 201 | ezkey-tests | Validation API |
| ezkey admin integration create affiche succès | CLI tests | Output CLI |
| Tenant A ne peut pas voir intégrations de B | ezkey-tests | Logique métier |
| Token est sauvegardé dans ~/.ezkey/config.json | CLI tests | Comportement CLI |
| Pagination fonctionne avec 1000+ records | ezkey-tests | Performance backend |
| --page et --size affichent bon nombre de lignes | CLI tests | UX pagination |
| Rate limiting bloque après 100 requêtes | ezkey-tests | Sécurité backend |
| Timeout message s'affiche après 5 minutes | CLI tests | UX timeout |

## ✅ Conclusion

**Règle simple:**

```
Backend/API → ezkey-tests (Java)
CLI/UX → ezkey-cli-python/tests (Python)
```

**En cas de doute:**
- Posez la question: "Ce test valide-t-il comment l'API fonctionne ou comment le CLI se comporte?"
- Si vous ne pouvez pas décider, implémentez dans **ezkey-tests** (plus complet)

## 📖 Références

- **ezkey-tests Philosophy**: `../ezkey-tests/guides/TEST_PHILOSOPHY.md`
- **ezkey-tests Writing Guide**: `../ezkey-tests/guides/WRITING_TESTS.md`
- **CLI Testing Strategy**: `docs/CLI_INTEGRATION_TESTING_STRATEGY.md`
- **Automated Device Approval**: `docs/AUTOMATED_DEVICE_APPROVAL_TESTING.md`
