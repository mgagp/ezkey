# Ezkey Mobile V1 - Instructions de Build (BASH UNIQUEMENT)

## 🚫 PROBLÈME IDENTIFIÉ
- PowerShell + gradlew = popup Windows qui bloque
- Bash ne peut pas accéder à Java dans `/c/Tools/jdk21`

## ✅ SOLUTION ALTERNATIVE

### Option 1: Utiliser Android Studio (RECOMMANDÉ)
1. **Ouvrir Android Studio**
2. **File → Open** → Sélectionner le dossier `ezkey_mobile_v1`
3. **Build → Make Project** (Ctrl+F9)
4. **Build → Build Bundle(s) / APK(s) → Build APK(s)**

### Option 2: Utiliser le wrapper gradle directement
```bash
# Dans Git Bash, naviguer vers le projet
cd ezkey_mobile_v1

# Essayer d'utiliser gradle directement (si installé)
gradle clean
gradle assembleDebug
```

### Option 3: Utiliser le chemin Windows natif
```bash
# Dans Git Bash, définir le chemin Windows
export JAVA_HOME="/c/Tools/jdk21"
export PATH="$JAVA_HOME/bin:$PATH"

# Vérifier Java
java -version

# Si Java fonctionne, essayer gradlew
./gradlew clean
./gradlew assembleDebug
```

### Option 4: Script de build hybride
Créer un script qui utilise les outils Windows directement :

```bash
# build_hybrid.sh
#!/bin/bash
echo "🔧 Building with hybrid approach..."

# Utiliser les outils Windows directement
"C:/Tools/jdk21/bin/java" -version

# Essayer gradlew avec chemin Windows
export JAVA_HOME="C:/Tools/jdk21"
export PATH="$JAVA_HOME/bin:$PATH"

# Build
./gradlew clean
./gradlew assembleDebug
```

## 🎯 RECOMMANDATION FINALE

**Utilisez Android Studio** - c'est la méthode la plus fiable :

1. **Ouvrir le projet** dans Android Studio
2. **Laisser Android Studio** gérer Java et le build
3. **Build → Build APK(s)** pour générer l'APK
4. **Utiliser bash** pour le déploiement et les tests

## 📋 Workflow Recommandé

### 1. Build dans Android Studio
- Ouvrir `ezkey_mobile_v1` dans Android Studio
- Build → Build APK(s)
- APK généré dans `app/build/outputs/apk/debug/`

### 2. Déploiement avec Bash
```bash
# Créer AVD
bash -c '/c/Users/marcg/AppData/Local/Android/Sdk/cmdline-tools/latest/bin/avdmanager create avd --name "Pixel_7_Pro_API_34" --package "system-images;android-34;google_apis;x86_64" --device "pixel_7_pro"'

# Démarrer émulateur
bash -c '/c/Users/marcg/AppData/Local/Android/Sdk/emulator/emulator -avd Pixel_7_Pro_API_34 &'

# Installer APK
bash -c '/c/Users/marcg/AppData/Local/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk'

# Lancer app
bash -c '/c/Users/marcg/AppData/Local/Android/Sdk/platform-tools/adb shell am start -n org.ezkey.mobile.v1/.MainActivity'

# Surveiller logs
bash -c '/c/Users/marcg/AppData/Local/Android/Sdk/platform-tools/adb logcat -s "AuthService:*" "MainActivity:*"'
```

## 🔧 Avantages de cette Approche

- ✅ **Pas de popups** - Android Studio gère tout
- ✅ **Java automatique** - Android Studio trouve Java
- ✅ **Build fiable** - Environnement optimisé
- ✅ **Bash pour déploiement** - Commandes simples et claires
- ✅ **Débogage facile** - Logs et monitoring avec bash

## 📝 Résumé

1. **Build** : Android Studio (évite les popups)
2. **Déploiement** : Bash (commandes simples)
3. **Test** : Émulateur + logs bash
4. **Débogage** : Logcat avec bash

Cette approche sépare les responsabilités et évite les problèmes de popup !
