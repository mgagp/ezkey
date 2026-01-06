# 🔧 Solution de Correction - Path Codé en Dur pour le MasterKey

**Date:** 5 janvier 2026
**Problème:** L'application cherche le MasterKey à `/c/ProgramData/ezkey/` qui n'existait que sur Windows local, empêchant Docker et Clean Start de fonctionner.
**Solution:** Implémentation de **Spring Profiles** pour séparer les configurations Docker et Windows.

---

## 📋 Résumé Exécutif

Votre instance Ezkey échouait au démarrage car l'application était configurée avec un chemin d'accès au MasterKey codé en dur pour Windows (`/c/ProgramData/ezkey/`). Cela causait :

- ❌ **Docker ne fonctionnait pas** - Le chemin n'existait pas dans les volumes Docker
- ❌ **Clean Start échouait** - Recherche du fichier au mauvais endroit
- ✅ **Windows natif fonctionnait** - Mais c'était le seul mode supporté

**Nous avons corrigé cela en créant deux modes de fonctionnement cohérents :**

| Mode | Profil | MasterKey | Cas d'Usage |
|------|--------|-----------|------------|
| 🐳 **Docker** | `docker` | `/etc/ezkey/secrets/master.key` (volume Docker) | Déploiement conteneur (production-like) |
| 🪟 **Windows** | `windows` | `C:\ProgramData\ezkey\secrets\master.key` | Développement natif Windows |

---

## ✅ Qu'Est-Ce Qui A Été Fait

### 1. **Création de Profils Spring**
Deux nouveaux fichiers de configuration pour chaque API :

- ✅ `application-windows.properties` (Windows natif)
- ✅ `application-docker.properties` (déjà existait, maintenant validé)

### 2. **Nettoyage de la Configuration Par Défaut**
- ❌ Supprimé le path codé en dur `/c/ProgramData/ezkey` de `application.properties`
- ✅ Ajouté une documentation claire indiquant que les paths doivent être configurés par profil

### 3. **Documentation Complète**
- 📚 [SPRING_PROFILES_CONFIGURATION.md](docs/SPRING_PROFILES_CONFIGURATION.md) - Guide complet
- 📚 [MIGRATION_HARDCODED_PATHS_FIX.md](docs/MIGRATION_HARDCODED_PATHS_FIX.md) - Guide de migration
- 📚 [FIX_SUMMARY_SPRING_PROFILES.md](FIX_SUMMARY_SPRING_PROFILES.md) - Résumé technique

### 4. **Scripts de Vérification**
- 🔍 `verify-profiles.sh` (Linux/Mac)
- 🔍 `verify-profiles.ps1` (Windows)

---

## 🚀 Comment Utiliser la Correction

### **Mode 1 : Docker (Nouveau / Recommandé)**

```bash
# Étape 1 : Naviguer dans le dossier tests
cd ezkey-tests

# Étape 2 : Lancer Clean Start (cela va tout régénérer)
./clean-start.sh

# ✅ Résultat : Docker démarre correctement avec le MasterKey généré
```

**Qu'il se passe :**
1. Le volume Docker `ezkey_encryption-secrets` est créé
2. La clé de chiffrement est générée à `/etc/ezkey/secrets/master.key` (à l'intérieur du conteneur)
3. Les deux APIs démarrent avec le profil `docker`
4. Tout fonctionne correctement ! 🎉

---

### **Mode 2 : Windows Natif (Développement Local)**

```powershell
# Étape 1 : Générer la clé MasterKey (en tant qu'Administrateur)
.\scripts\generate-master-key.ps1

# Étape 2 : Définir le profil Spring
$env:SPRING_PROFILES_ACTIVE = "windows"

# Étape 3 : Lancer l'application Admin API
java -jar ezkey-admin-api.jar

# ✅ Résultat : L'application démarre et trouve la clé à C:\ProgramData\ezkey\secrets\master.key
```

---

## 🔍 Pour Vérifier Que Cela Fonctionne

### **Vérification Docker**
```bash
# Le volume Docker doit contenir le MasterKey
docker volume inspect ezkey_encryption-secrets

# L'API doit répondre
curl http://localhost:9080
```

### **Vérification Windows**
```powershell
# Le fichier doit exister
Test-Path "C:\ProgramData\ezkey\secrets\master.key"
# Résultat: True

# Le profil doit être actif
(Invoke-WebRequest http://localhost:9080/actuator/env).Content | ConvertFrom-Json | Select-ExpandProperty activeProfiles
# Résultat: windows
```

---

## 📁 Fichiers Modifiés / Créés

### **Fichiers Créés (Nouveaux)**
```
✨ ezkey-admin-api/config/application-windows.properties
✨ ezkey-auth-api/config/application-windows.properties
✨ docs/SPRING_PROFILES_CONFIGURATION.md (guide complet)
✨ docs/MIGRATION_HARDCODED_PATHS_FIX.md (guide de migration)
✨ FIX_SUMMARY_SPRING_PROFILES.md (résumé technique)
✨ ezkey-tests/verify-profiles.sh (vérification Linux/Mac)
✨ ezkey-tests/verify-profiles.ps1 (vérification Windows)
```

### **Fichiers Modifiés (Corrigés)**
```
🔧 ezkey-admin-api/config/application.properties (supprimé hardcoding)
🔧 ezkey-auth-api/config/application.properties (supprimé hardcoding)
```

### **Fichiers Validés (Déjà Corrects)**
```
✓ ezkey-admin-api/config/application-docker.properties
✓ ezkey-auth-api/config/application-docker.properties
✓ docker/generate-encryption-keys.sh
✓ scripts/generate-master-key.ps1
✓ scripts/generate-master-key.sh
```

---

## 🎯 Prochaines Étapes Pour Votre Collaboratrice

### **Immédiatement (pour faire fonctionner Ezkey)**

1. ✅ Faire un `git pull` pour récupérer les corrections
2. ✅ Exécuter `./ezkey-tests/clean-start.sh`
3. ✅ Attendre que Docker démarre complètement (cela génère la clé automatiquement)
4. ✅ Accéder aux APIs : http://localhost:9080 et http://localhost:8080

### **Documentation (pour comprendre)**

- 📖 Lire : [SPRING_PROFILES_CONFIGURATION.md](docs/SPRING_PROFILES_CONFIGURATION.md)
- 📖 Ou si vous êtes passé par Windows : [MIGRATION_HARDCODED_PATHS_FIX.md](docs/MIGRATION_HARDCODED_PATHS_FIX.md)

### **Vérification (optionnel)**

```bash
cd ezkey-tests
./verify-profiles.sh
# Doit afficher: ✅ All checks passed!
```

---

## 🧠 Explication Technique (Optionnel)

**Avant (Problème) :**
```
Clean Start (Docker)
    ↓
Génère MasterKey dans: /etc/ezkey/ (volume Docker)
    ↓
Application cherche: /c/ProgramData/ezkey/ (Windows local)
    ↓
❌ ERREUR: Fichier not found
```

**Après (Solution) :**
```
Clean Start (Docker)
    ↓
Active profil: docker
    ↓
Config dit: chercher à /etc/ezkey/ (dans le conteneur)
    ↓
Génère et trouve MasterKey au bon endroit
    ↓
✅ SUCCÈS
```

---

## 📞 Questions Fréquentes

**Q: Qu'est-ce qui change pour moi?**
R: Rien si vous utilisez Docker. Si vous utilisiez Windows natif avant, vous devez maintenant définir `SPRING_PROFILES_ACTIVE=windows` avant de lancer.

**Q: Est-ce que mes données vont être perdues?**
R: Clean Start supprime tout et recommence à zéro. Sauvegardez vos données avant si nécessaire.

**Q: Pourquoi deux modes?**
R: Docker est production-like et utilisable en entreprise. Windows natif est utile pour le développement local.

**Q: Et en production sur Linux?**
R: Utilisez Docker (même profil que développement). Les clés vont dans `/etc/ezkey/` comme prévu.

---

## ✨ Avantages de la Correction

✅ **Docker fonctionne correctement** (le gros problème est résolu)
✅ **Clean Start fonctionne** (déploiement en un seul script)
✅ **Windows natif toujours supporté** (avec profil explicite)
✅ **Code plus maintenable** (pas de paths codés en dur)
✅ **Prêt pour le cloud** (facile d'ajouter d'autres profils)
✅ **Documentation complète** (guides pas à pas)

---

## 🎯 Résumé En Une Ligne

**Au lieu de chercher le MasterKey au mauvais endroit, on utilise maintenant des profils Spring qui disent à l'application où chercher selon qu'elle s'exécute en Docker ou sur Windows.**

---

## 📚 Ressources

- **Guide complet:** [SPRING_PROFILES_CONFIGURATION.md](docs/SPRING_PROFILES_CONFIGURATION.md)
- **Résumé technique:** [FIX_SUMMARY_SPRING_PROFILES.md](FIX_SUMMARY_SPRING_PROFILES.md)
- **Guide de migration:** [MIGRATION_HARDCODED_PATHS_FIX.md](docs/MIGRATION_HARDCODED_PATHS_FIX.md)

---

**Bonne chance avec Ezkey! 🚀**

_Si vous avez des questions, consultez les guides ou exécutez le script de vérification._
