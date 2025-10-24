# Ezkey Mobile V1 - Instructions Finales

## ✅ Configuration Terminée

### Modifications Apportées

1. **URL ngrok intégrée** dans `AuthService.kt`
   - Changé de `http://192.168.1.92:8080` vers `https://goateed-katalina-monsoonal.ngrok-free.dev`

2. **Logging amélioré** dans `AuthService.kt`
   - Headers HTTP détaillés
   - Timing des requêtes
   - Corps des réponses formatés
   - Contexte d'erreur avec dépannage

3. **Documentation créée**
   - `QUICKSTART.md` avec commandes bash
   - `setup_environment.sh` pour configuration
   - `IMPLEMENTATION_SUMMARY.md` pour résumé

## 🚀 Instructions pour l'Utilisateur

### 1. Configuration de l'Environnement

Dans votre terminal Git Bash, définissez les variables d'environnement :

```bash
# Définir Java (ajustez le chemin si nécessaire)
export JAVA_HOME="C:/Tools/jdk21"
export PATH="$JAVA_HOME/bin:$PATH"

# Vérifier que Java fonctionne
java -version

# Définir Android SDK
export ANDROID_SDK_ROOT="C:/Users/marcg/AppData/Local/Android/Sdk"
export ANDROID_HOME="$ANDROID_SDK_ROOT"
export PATH="$PATH:$ANDROID_SDK_ROOT/platform-tools"
export PATH="$PATH:$ANDROID_SDK_ROOT/emulator"
export PATH="$PATH:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin"
```

### 2. Compilation de l'App

```bash
# Aller dans le projet
cd ezkey_mobile_v1

# Nettoyer
./gradlew clean

# Compiler
./gradlew assembleDebug

# Vérifier que l'APK est créé
ls -la app/build/outputs/apk/debug/app-debug.apk
```

### 3. Création de l'AVD Pixel 7 Pro

```bash
# Lister les AVD existants
$ANDROID_SDK_ROOT/emulator/emulator -list-avds

# Créer l'AVD si nécessaire
$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/avdmanager create avd \
  --name "Pixel_7_Pro_API_34" \
  --package "system-images;android-34;google_apis;x86_64" \
  --device "pixel_7_pro"
```

### 4. Déploiement et Test

```bash
# Démarrer l'émulateur (en arrière-plan)
$ANDROID_SDK_ROOT/emulator/emulator -avd Pixel_7_Pro_API_34 &

# Attendre que l'appareil soit prêt
$ANDROID_SDK_ROOT/platform-tools/adb wait-for-device

# Installer l'APK
$ANDROID_SDK_ROOT/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk

# Lancer l'app
$ANDROID_SDK_ROOT/platform-tools/adb shell am start -n org.ezkey.mobile.v1/.MainActivity

# Surveiller les logs (ESSENTIEL pour le débogage)
$ANDROID_SDK_ROOT/platform-tools/adb logcat -s "AuthService:*" "MainActivity:*" "System.err:*"
```

## 🔍 Fonctionnalités de l'App

### Tests Cryptographiques (Automatiques)
- Génération de paires de clés RSA-2048
- Génération de tokens de preuve sécurisés
- Création de signatures numériques SHA256withRSA
- Validation de signatures avec compatibilité Java
- Génération de défis sécurisés

### Communication API
- **Vérification d'auth en attente** : `POST /api/v1/auth-attempts/pending/{enrollmentId}`
- **Acceptation d'auth** : `POST /api/v1/auth-attempts/respond/{authAttemptId}`
- **Logging complet** de toutes les requêtes/réponses HTTP
- **Gestion d'erreurs** avec dépannage détaillé

### Interface Utilisateur
- **Résultats des tests crypto** affichés au démarrage
- **Bouton "Check Pending Auth"** pour polling manuel
- **Bouton "Accept Auth"** pour approbation d'authentification
- **Logging en temps réel** avec sortie scrollable
- **Bouton de terminaison** pour sortie propre de l'app

## 🎯 Workflow de Test

1. **Démarrage** : L'app lance automatiquement les tests crypto
2. **Vérification** : Cliquer sur "Check Pending Auth"
3. **Surveillance** : Examiner les logs pour voir la communication API
4. **Acceptation** : Si une auth en attente est trouvée, cliquer "Accept Auth"
5. **Vérification** : Confirmer que l'authentification est complète

## 📝 Notes Importantes

- **App native Android/Kotlin** (PAS React Native)
- **Niveaux API conservateurs** (34/33/24) pour fiabilité de build
- **HTTPS via ngrok** élimine la complexité réseau
- **Logging extensif** critique pour le débogage mobile
- **Phase POC** : Android seulement, pas d'iOS

## 🔧 Dépannage

### Java non trouvé
```bash
# Vérifier le chemin Java
ls -la "C:/Tools/jdk21/bin/java.exe"

# Si le chemin est différent, ajuster JAVA_HOME
export JAVA_HOME="VOTRE_CHEMIN_JDK"
```

### Émulateur ne démarre pas
```bash
# Vérifier que l'AVD existe
$ANDROID_SDK_ROOT/emulator/emulator -list-avds

# Créer l'AVD si nécessaire
$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/avdmanager create avd --name "Pixel_7_Pro_API_34" --package "system-images;android-34;google_apis;x86_64" --device "pixel_7_pro"
```

### App ne se connecte pas à l'API
- Vérifier que ngrok est actif : `curl https://goateed-katalina-monsoonal.ngrok-free.dev`
- Vérifier que ezkey-auth-api est en cours d'exécution sur localhost:8080
- Examiner les logs pour les erreurs de connexion

## ✅ Critères de Succès

- ✅ **Compilation** : APK généré sans erreurs
- ✅ **Émulateur** : Pixel 7 Pro démarre et fonctionne
- ✅ **Installation** : App s'installe et se lance
- ✅ **Tests crypto** : Tous les 5 tests passent au démarrage
- ✅ **Communication API** : Connexion à ngrok réussie
- ✅ **Authentification** : Flux d'auth complet fonctionne
- ✅ **Logging** : Logs détaillés visibles dans logcat
