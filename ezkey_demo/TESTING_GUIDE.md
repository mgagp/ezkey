# 🧪 Guide de Test - Ezkey Demo avec API Locale

## ✅ Application Mise à Jour !

L'application Ezkey Demo a été transformée pour utiliser votre **API locale** au lieu des mocks !

### 🔄 **Changements Apportés :**

#### **Avant (Mock) :**
- ❌ Données simulées
- ❌ Pas d'appels réseau
- ❌ Pas de vraie API

#### **Maintenant (Vrai API) :**
- ✅ Appels à `http://localhost:8080`
- ✅ Vraies données de votre API
- ✅ Workflow complet BIND → CONFIRM
- ✅ Authentification réelle

## 🚀 **Comment Tester**

### **1. Prérequis**
- ✅ Votre API Ezkey locale doit tourner sur `http://localhost:8080`
- ✅ L'application Flutter doit tourner sur `http://localhost:8081`
- ✅ Les deux doivent être accessibles simultanément

### **2. Test d'Enrollment**

#### **Étape 1: Préparer l'URL**
Entrez une URL d'enrollment valide dans le champ :
```
http://localhost:8080/api/v1/enrollments/bind/3
```

#### **Étape 2: Lancer l'Enrollment**
1. Cliquez sur **"Start Enrollment"**
2. Observez les étapes en temps réel :
   - 🔄 Calling BIND API...
   - 🔄 Generating device keys...
   - 🔄 Generating challenge response...
   - 🔄 Signing enrollment data...
   - 🔄 Confirming enrollment...

#### **Étape 3: Vérifier le Résultat**
Vous devriez voir :
```
✅ Enrollment successful!
Enrollment ID: 3
Enrollment Code: [votre code]
Status: Active
Challenge Response: [nombre aléatoire]
```

### **3. Test d'Authentification**

#### **Étape 1: Créer une Tentative d'Auth**
Via votre API locale, créez une tentative d'authentification pour l'enrollment actif.

#### **Étape 2: Vérifier les Tentatives**
1. Cliquez sur **"Check for Pending Authentication"**
2. Vous devriez voir :
   ```
   🔔 Pending authentication request!
   Request ID: [ID]
   Challenge Required: [Yes/No]
   ```

#### **Étape 3: Répondre à l'Auth**
1. Cliquez sur **"Approve"** ou **"Deny"**
2. Si challenge requis, entrez la réponse
3. Observez le résultat

## 🔍 **Debugging et Dépannage**

### **Problèmes Courants**

#### **1. "Network error: Connection refused"**
- **Cause :** API locale non démarrée
- **Solution :** Démarrez votre API Ezkey sur le port 8080

#### **2. "Failed to bind enrollment: 404"**
- **Cause :** Enrollment ID inexistant
- **Solution :** Utilisez un ID d'enrollment valide (ex: 3)

#### **3. "Failed to bind enrollment: 500"**
- **Cause :** Erreur côté serveur
- **Solution :** Vérifiez les logs de votre API locale

#### **4. "Invalid enrollment URL format"**
- **Cause :** URL mal formatée
- **Solution :** Utilisez le format exact : `http://localhost:8080/api/v1/enrollments/bind/{id}`

### **Vérification de l'API Locale**

#### **Test Direct de l'API :**
```bash
# Test BIND endpoint
curl http://localhost:8080/api/v1/enrollments/bind/3

# Test PENDING endpoint
curl http://localhost:8080/api/v1/authattempts/pending/3
```

#### **Vérification des Logs :**
- Surveillez les logs de votre API locale
- Vérifiez que les requêtes arrivent bien
- Identifiez les erreurs côté serveur

## 📊 **Workflow Complet Testé**

### **Enrollment Workflow :**
1. **BIND** → `GET /api/v1/enrollments/bind/{id}`
2. **Generate Keys** → Génération de clés RSA (mock)
3. **Sign Data** → Signature des données
4. **CONFIRM** → `POST /api/v1/enrollments/confirm`

### **Authentication Workflow :**
1. **PENDING** → `GET /api/v1/authattempts/pending/{id}`
2. **Sign Response** → Signature de la réponse
3. **RESPOND** → `POST /api/v1/authattempts/respond/{id}`

## 🎯 **Scénarios de Test**

### **Scénario 1: Enrollment Réussi**
1. Entrez `http://localhost:8080/api/v1/enrollments/bind/3`
2. Cliquez "Start Enrollment"
3. Vérifiez le succès

### **Scénario 2: Pas d'Auth en Attente**
1. Après enrollment réussi
2. Cliquez "Check for Pending Authentication"
3. Vérifiez le message "No pending requests"

### **Scénario 3: Auth avec Challenge**
1. Créez une tentative d'auth avec challenge
2. Vérifiez que le champ challenge apparaît
3. Entrez une réponse et approuvez

### **Scénario 4: Auth sans Challenge**
1. Créez une tentative d'auth sans challenge
2. Vérifiez que seuls les boutons Approve/Deny apparaissent
3. Testez les deux options

## 🔧 **Améliorations Futures**

### **Crypto Réel :**
- Remplacer les mocks par de vraies clés RSA
- Utiliser PointyCastle pour la cryptographie
- Implémenter RSA-PSS signing

### **Gestion d'Erreurs :**
- Messages d'erreur plus détaillés
- Retry automatique pour les erreurs réseau
- Validation des réponses API

### **UX Améliorée :**
- Historique des transactions
- Indicateurs de statut en temps réel
- Animations de transition

## 📞 **Support**

### **En Cas de Problème :**
1. Vérifiez que votre API locale fonctionne
2. Testez les endpoints directement avec curl
3. Vérifiez les logs de l'application Flutter
4. Consultez les logs de votre API locale

### **Logs Utiles :**
- **Flutter :** Console du navigateur (F12)
- **API :** Logs de votre serveur Ezkey
- **Réseau :** Onglet Network des outils de développement

---

**🎉 Votre application Ezkey Demo est maintenant connectée à votre API locale !**

**Testez les workflows complets et profitez de l'expérience réelle !**
