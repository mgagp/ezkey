This file will be in UTF-8 without BOM.

# Plan — Clean Start PowerShell (cible PowerShell 7)

## Objectif
Créer une version PowerShell de `clean-start.sh` pour Windows, avec parité fonctionnelle et scripts Docker de contrôle équivalents, puis documenter l’usage.

## Étapes
1. Analyser le comportement de `ezkey-tests/clean-start.sh` (flags, profils, enchaînements, nettoyage).
2. Inventorier les scripts Docker appelés par clean-start dans `docker/` et vérifier les équivalents Windows existants.
3. Créer les scripts PowerShell manquants dans `docker/` (ex. `manage.ps1`, `manage-ha.ps1`) en reproduisant les options et variables d’environnement attendues.
4. Implémenter `ezkey-tests/clean-start.ps1` en PowerShell 7 avec la même logique :
   - flags `--native`, `--ha`, `--mvn-bootstrap`, `--jmx`, `--prod-safe`
   - choix des profils `SPRING_PROFILES_ACTIVE`
   - arrêt/cleanup docker-compose (volumes)
   - reset du répertoire `.ezkey-test`
   - redémarrage stack (`docker/start*.ps1`)
   - bootstrap Maven optionnel
   - messages d’aide (profils de tests et commandes utiles)
5. Mettre à jour une courte documentation (ex. `README.md` ou `docs/DEVELOPMENT.md`) avec l’usage PowerShell.
6. Valider l’alignement avec la stratégie Docker/tests dans `docs/`.

## Standards PowerShell (cible 7.x)
- `param(...)` + `[ValidateSet()]`
- `Set-StrictMode -Version Latest`
- `-ErrorAction Stop` + `try/catch`
- `Join-Path` pour les chemins
- Scripts idempotents et logs clairs

## Recommandation de version
- Cible minimale: PowerShell 7.x (recommandé pour open‑source et Docker).
- Compatibilité 5.1 uniquement si triviale.
