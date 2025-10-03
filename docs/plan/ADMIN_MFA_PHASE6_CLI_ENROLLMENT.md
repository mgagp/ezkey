# Phase 6: CLI Python - Enrollment Management (Détails)

**Version:** 1.0  
**Date:** 2025-10-03  
**Durée:** 2-3 jours  
**Status:** Planning

---

## Objectif

Permettre la gestion de l'enrollment admin via CLI (bind, status, renew) en utilisant les endpoints sécurisés anti-énumération.

---

## Contexte Sécurité: Endpoints BIND et VERIFY Améliorés

### 🔒 Protection Anti-Énumération

Ezkey a implémenté une protection contre les attaques d'énumération sur l'endpoint bind. Le nouvel endpoint utilise **POST avec body** au lieu de **GET avec path params**.

**AVANT (Vulnérable):**
```http
GET /api/v1/enrollments/bind/{enrollmentId}
```
❌ Problème: Attaquant peut tester 1, 2, 3, 4... et découvrir enrollments valides

**APRÈS (Sécurisé):**
```http
POST /api/v1/enrollments/bind
Content-Type: application/json

{
  "enrollmentId": 123,
  "enrollmentProofToken": "abc123-def456-ghi789"
}
```
✅ **Avantages:**
- **Pas d'énumération**: Impossible de deviner les tokens
- **Authentification**: Seules les parties avec token valide peuvent accéder
- **Rate Limiting**: 5 requêtes par 10 minutes
- **Audit Trail**: Tous les accès sont loggés

**Référence:** Voir `docs/features/BIND_ENUM_PROTECTION.md` pour détails complets

---

## Architecture CLI

### Structure des Fichiers

```
ezkey-cli-python/
├── ezkey_cli/
│   ├── admin/
│   │   ├── __init__.py
│   │   ├── auth.py          # Login/logout MFA
│   │   ├── enrollment.py    # Manage admin enrollment ⭐ NOUVEAU
│   │   ├── config.py        # CLI config management
│   │   └── storage.py       # Local credential storage
│   ├── commands/
│   │   ├── login.py         # ezkey admin login
│   │   ├── logout.py        # ezkey admin logout
│   │   ├── status.py        # ezkey admin status
│   │   └── enroll.py        # ezkey admin enroll ⭐ NOUVEAU
│   └── main.py
```

### Local Storage

**Fichier:** `~/.ezkey/admin-config.json`

**Structure:**
```json
{
  "admin_api_url": "http://localhost:9080",
  "auth_api_url": "http://localhost:9081",
  "admin": {
    "username": "admin",
    "bearer_token": "ezkey_abc123...",
    "token_expires_at": "2025-10-04T10:00:00Z",
    "enrollment_id": 1,
    "enrollment_proof_token": "abc123-def456-ghi789",  // ⭐ NOUVEAU
    "device_public_key": "...",
    "device_private_key": "..."  // Encrypted locally
  }
}
```

**Security:**
- File permissions: 600 (owner only)
- Private key encrypted with user password or system keychain
- Token stored securely

---

## 6.1 Command: ezkey admin enroll bind

### Usage

```bash
ezkey admin enroll bind \
  --enrollment-id 1 \
  --enrollment-proof-token "abc123-def456-ghi789"
```

**Note:** Les deux paramètres sont requis pour la sécurité anti-énumération.

### Obtenir les Paramètres

L'`enrollmentProofToken` est obtenu lors de la création de l'enrollment via Admin API:

```bash
# Via Postman ou CLI admin
POST http://localhost:9080/api/v1/admin/enrollments
Authorization: Bearer {admin-token}
Content-Type: application/json

{
  "enrollmentName": "Admin MFA",
  "integrationId": 1
}

# Response contient:
{
  "enrollmentId": 1,
  "enrollmentProofToken": "abc123-def456-ghi789",  // ⭐ À sauvegarder
  "enrollmentName": "Admin MFA",
  ...
}
```

**Workflow complet:**
1. Admin global login (password)
2. Admin crée enrollment via Admin API
3. Admin sauvegarde `enrollmentId` + `enrollmentProofToken`
4. Admin bind l'enrollment avec CLI: `ezkey admin enroll bind`
5. Admin login avec MFA

---

### Flow d'Implémentation

**Fichier:** `ezkey_cli/admin/enrollment.py`

```python
import base64
import json
import requests
from pathlib import Path
from getpass import getpass
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa, padding
from cryptography.hazmat.backends import default_backend

class EnrollmentManager:
    """
    Manage admin enrollment binding with secure POST endpoint.
    """
    
    def __init__(self, config_manager):
        self.config_manager = config_manager
        self.auth_api_url = config_manager.get_auth_api_url()
    
    def bind_enrollment(self, enrollment_id, enrollment_proof_token):
        """
        Bind admin enrollment using secure POST endpoint.
        
        Args:
            enrollment_id: The enrollment ID
            enrollment_proof_token: The enrollment proof token (required for security)
        
        Returns:
            bool: True if binding successful, False otherwise
        """
        print(f"🔐 Binding enrollment {enrollment_id}...")
        
        # 1. Generate device key pair (if not exists)
        if not self._has_device_keys():
            print("📱 Generating device key pair (RSA-2048)...")
            self._generate_device_keys()
        
        # 2. Load device public key
        device_public_key_pem = self._load_device_public_key()
        device_public_key_b64 = base64.b64encode(device_public_key_pem).decode()
        
        # 3. POST /enrollments/bind (secure - requires proof token)
        # This prevents enumeration by requiring authentication via proof token
        print("🔗 Fetching enrollment details...")
        
        try:
            response = requests.post(
                f"{self.auth_api_url}/api/v1/enrollments/bind",
                json={
                    "enrollmentId": enrollment_id,
                    "enrollmentProofToken": enrollment_proof_token
                },
                timeout=10
            )
            
            if response.status_code != 200:
                error_msg = response.json().get('message', 'Invalid enrollment or proof token')
                print(f"❌ Bind failed: {error_msg}")
                if response.status_code == 400:
                    print("   💡 Hint: Check that enrollmentId and enrollmentProofToken are correct")
                elif response.status_code == 409:
                    print("   💡 Hint: Enrollment may already be bound")
                return False
            
            bind_data = response.json()
            
        except requests.exceptions.RequestException as e:
            print(f"❌ Network error: {e}")
            return False
        
        # 4. Display enrollment info and confirm
        print(f"\n📋 Enrollment Details:")
        print(f"   Name: {bind_data.get('enrollmentName', 'N/A')}")
        print(f"   Integration: {bind_data.get('integrationName', 'N/A')}")
        print(f"   Integration ID: {bind_data.get('integrationId', 'N/A')}")
        print(f"   Status: {bind_data.get('enrollmentStatus', 'N/A')}")
        
        confirm = input("\n🔐 Bind this enrollment to your CLI device? [y/N]: ")
        
        if confirm.lower() != 'y':
            print("❌ Enrollment binding cancelled")
            return False
        
        # 5. Sign proof token (returned in bind response for verification)
        enrollment_proof_token_for_signature = bind_data.get("enrollmentProofToken")
        
        if not enrollment_proof_token_for_signature:
            print("❌ Missing enrollment proof token in response")
            return False
        
        print("🔏 Signing proof token with device private key...")
        signature = self._sign_with_device_key(enrollment_proof_token_for_signature)
        
        # 6. POST /enrollments/verify (completes the binding)
        print("✍️  Verifying enrollment...")
        
        try:
            verify_response = requests.post(
                f"{self.auth_api_url}/api/v1/enrollments/verify",
                json={
                    "enrollmentId": enrollment_id,
                    "devicePublicKey": device_public_key_b64,
                    "enrollmentProofTokenSignedByDevice": signature
                },
                timeout=10
            )
            
            if verify_response.status_code == 200:
                # Save enrollment info locally
                self._save_enrollment_info(enrollment_id, enrollment_proof_token, bind_data)
                print("✅ Enrollment bound successfully!")
                print(f"   Device public key registered for enrollment {enrollment_id}")
                print(f"   🔐 You can now use MFA for admin login")
                return True
            else:
                error_msg = verify_response.json().get('message', 'Unknown error')
                print(f"❌ Enrollment verification failed: {error_msg}")
                return False
                
        except requests.exceptions.RequestException as e:
            print(f"❌ Network error during verification: {e}")
            return False
    
    def _has_device_keys(self):
        """Check if device keys exist."""
        keys_dir = Path.home() / ".ezkey" / "device-keys"
        return (keys_dir / "device-private.pem").exists() and (keys_dir / "device-public.pem").exists()
    
    def _generate_device_keys(self):
        """Generate RSA-2048 key pair for device."""
        # Generate key pair
        private_key = rsa.generate_private_key(
            public_exponent=65537,
            key_size=2048,
            backend=default_backend()
        )
        
        # Get encryption password
        print("🔐 Enter password to encrypt device private key:")
        password = getpass("Password: ")
        password_confirm = getpass("Confirm password: ")
        
        if password != password_confirm:
            raise ValueError("Passwords do not match")
        
        # Save private key (encrypted)
        private_pem = private_key.private_bytes(
            encoding=serialization.Encoding.PEM,
            format=serialization.PrivateFormat.PKCS8,
            encryption_algorithm=serialization.BestAvailableEncryption(password.encode())
        )
        
        # Save public key
        public_pem = private_key.public_key().public_bytes(
            encoding=serialization.Encoding.PEM,
            format=serialization.PublicFormat.SubjectPublicKeyInfo
        )
        
        # Write to files
        keys_dir = Path.home() / ".ezkey" / "device-keys"
        keys_dir.mkdir(parents=True, exist_ok=True)
        
        (keys_dir / "device-private.pem").write_bytes(private_pem)
        (keys_dir / "device-public.pem").write_bytes(public_pem)
        
        # Set permissions (Unix only)
        import os
        if os.name != 'nt':  # Not Windows
            (keys_dir / "device-private.pem").chmod(0o600)
            (keys_dir / "device-public.pem").chmod(0o644)
        
        print("✅ Device keys generated successfully")
    
    def _load_device_public_key(self):
        """Load device public key PEM bytes."""
        keys_dir = Path.home() / ".ezkey" / "device-keys"
        return (keys_dir / "device-public.pem").read_bytes()
    
    def _sign_with_device_key(self, data):
        """Sign data with device private key."""
        # Load private key
        keys_dir = Path.home() / ".ezkey" / "device-keys"
        private_pem = (keys_dir / "device-private.pem").read_bytes()
        
        # Get password
        password = getpass("🔐 Enter device key password: ")
        
        try:
            private_key = serialization.load_pem_private_key(
                private_pem,
                password=password.encode(),
                backend=default_backend()
            )
        except Exception:
            raise ValueError("Invalid password for device private key")
        
        # Sign
        signature = private_key.sign(
            data.encode(),
            padding.PKCS1v15(),
            hashes.SHA256()
        )
        
        return base64.b64encode(signature).decode()
    
    def _save_enrollment_info(self, enrollment_id, enrollment_proof_token, bind_data):
        """Save enrollment info to local config."""
        config = self.config_manager.load_config()
        
        if "admin" not in config:
            config["admin"] = {}
        
        config["admin"]["enrollment_id"] = enrollment_id
        config["admin"]["enrollment_proof_token"] = enrollment_proof_token
        config["admin"]["enrollment_name"] = bind_data.get("enrollmentName")
        config["admin"]["integration_id"] = bind_data.get("integrationId")
        
        self.config_manager.save_config(config)
```

---

## 6.2 Command: ezkey admin enroll status

### Usage

```bash
ezkey admin enroll status
```

### Output

```
Admin Enrollment Status:
  Enrollment ID: 1
  Enrollment Name: Admin MFA
  Enrollment Proof Token: abc123-def456-ghi789 (secured)
  Integration: Ezkey System Admin
  Integration ID: 1
  Device Public Key: MIIBIjANBg... (first 20 chars)
  Bound at: 2025-10-03 10:00:00
  Status: Active ✅
```

### Implémentation

```python
def enrollment_status(self):
    """Display enrollment status."""
    config = self.config_manager.load_config()
    
    if "admin" not in config or "enrollment_id" not in config["admin"]:
        print("❌ No enrollment found")
        print("   💡 Hint: Run 'ezkey admin enroll bind' first")
        return
    
    admin_config = config["admin"]
    
    print("\nAdmin Enrollment Status:")
    print(f"  Enrollment ID: {admin_config.get('enrollment_id', 'N/A')}")
    print(f"  Enrollment Name: {admin_config.get('enrollment_name', 'N/A')}")
    print(f"  Enrollment Proof Token: {admin_config.get('enrollment_proof_token', 'N/A')[:20]}... (secured)")
    print(f"  Integration: {admin_config.get('integration_name', 'N/A')}")
    print(f"  Integration ID: {admin_config.get('integration_id', 'N/A')}")
    
    # Load device public key (first 20 chars)
    if self._has_device_keys():
        device_public_key_pem = self._load_device_public_key()
        device_public_key_str = device_public_key_pem.decode()[:60]
        print(f"  Device Public Key: {device_public_key_str}...")
    
    print(f"  Status: Active ✅")
```

---

## 6.3 Security Benefits du Nouveau Format

### Protection Anti-Énumération

**Avant (GET avec path params):**
- Attaquant peut tester: GET /bind/1, /bind/2, /bind/3...
- Découvre enrollments valides
- Obtient enrollmentProofToken sans authentification

**Après (POST avec body):**
- Attaquant ne peut PAS énumérer sans enrollmentProofToken
- Token unique et cryptographiquement sécurisé
- Même si enrollment ID deviné, token requis

### Rate Limiting

**Configuration:**
```properties
# ezkey-auth-api/config/application.properties
ezkey.rate-limit.bind.enabled=true
ezkey.rate-limit.bind.requests=5
ezkey.rate-limit.bind.window-minutes=10
ezkey.rate-limit.bind.key-strategy=client-ip
```

**Protection:**
- 5 requêtes maximum par 10 minutes
- Bloque attaques automatisées
- HTTP 429 Too Many Requests

### Audit Trail

Tous les accès au bind endpoint sont loggés:
```
INFO  EnrollmentService - Bind attempt: enrollmentId=1, ip=192.168.1.100
WARN  EnrollmentService - Invalid proof token: enrollmentId=1, ip=192.168.1.100
INFO  EnrollmentService - Bind successful: enrollmentId=1, ip=192.168.1.100
```

---

## 6.4 Tests Phase 6

### Tests Unitaires (Python)

```bash
pytest tests/test_enrollment_manager.py -v
```

**Test Coverage:**
- ✅ `test_bind_enrollment_success()` - Bind réussi
- ✅ `test_bind_enrollment_invalid_token()` - Token invalide → 400
- ✅ `test_bind_enrollment_not_found()` - Enrollment inexistant → 404
- ✅ `test_bind_enrollment_already_bound()` - Déjà bindé → 409
- ✅ `test_generate_device_keys()` - Génération clés device
- ✅ `test_sign_with_device_key()` - Signature valide
- ✅ `test_enrollment_status()` - Affichage status

### Tests Intégration (End-to-End)

**Scenario 1: Full Enrollment Flow (Success)**
```bash
# 1. Admin login
ezkey admin login
# Username: admin
# Password: MySecurePassword123!
# ✅ Login successful

# 2. Create enrollment (via Postman or Admin API)
# POST http://localhost:9080/api/v1/admin/enrollments
# Returns: enrollmentId=1, enrollmentProofToken="abc123..."

# 3. Bind enrollment with CLI
ezkey admin enroll bind \
  --enrollment-id 1 \
  --enrollment-proof-token "abc123-def456-ghi789"
# ✅ Enrollment bound successfully!

# 4. Check status
ezkey admin enroll status
# Status: Active ✅

# 5. Logout and login with MFA
ezkey admin logout
ezkey admin login
# 🔐 MFA required
# 📱 MFA attempt created: 1
# Waiting for approval on your mobile device...
# ✅ MFA approved
# ✅ Login successful
```

**Scenario 2: Invalid Proof Token**
```bash
ezkey admin enroll bind \
  --enrollment-id 1 \
  --enrollment-proof-token "invalid-token"
# ❌ Bind failed: Invalid enrollment or proof token
#    💡 Hint: Check that enrollmentId and enrollmentProofToken are correct
```

**Scenario 3: Already Bound**
```bash
# Bind twice avec même enrollment
ezkey admin enroll bind \
  --enrollment-id 1 \
  --enrollment-proof-token "abc123-def456-ghi789"
# ❌ Bind failed: Enrollment already bound
#    💡 Hint: Enrollment may already be bound
```

**Scenario 4: Rate Limiting**
```bash
# Tenter 6+ bind requests en 10 minutes
for i in {1..6}; do
  ezkey admin enroll bind --enrollment-id $i --enrollment-proof-token "test$i"
done
# ❌ Bind failed: Too many requests. Please try again later.
# Retry-After: 600 seconds
```

---

## 6.5 Fichiers à Créer/Modifier

### Nouveaux Fichiers

1. **`ezkey_cli/admin/enrollment.py`** (nouveau)
   - Class `EnrollmentManager`
   - Méthodes: `bind_enrollment()`, `enrollment_status()`, `_generate_device_keys()`, etc.

2. **`ezkey_cli/commands/enroll.py`** (nouveau)
   - Command CLI: `ezkey admin enroll bind`
   - Command CLI: `ezkey admin enroll status`

3. **`tests/test_enrollment_manager.py`** (nouveau)
   - Tests unitaires pour `EnrollmentManager`

### Fichiers à Modifier

1. **`ezkey_cli/main.py`**
   - Ajouter commande `admin enroll`
   - Router vers `enroll.py`

2. **`ezkey_cli/admin/config.py`**
   - Ajouter champs enrollment dans config
   - Méthodes: `save_enrollment_info()`, `get_enrollment_info()`

3. **`requirements.txt`**
   - Vérifier: `cryptography>=41.0.0`
   - Vérifier: `requests>=2.31.0`

---

## 6.6 Migration depuis Ancien Format

### Rétrocompatibilité

**Ancien code (GET avec path params):**
```python
# ANCIEN - NE PLUS UTILISER
response = requests.get(
    f"{auth_api_url}/api/v1/enrollments/bind/{enrollment_id}",
    params={"integrationId": integration_id}
)
```

**Nouveau code (POST avec body):**
```python
# NOUVEAU - UTILISER MAINTENANT
response = requests.post(
    f"{auth_api_url}/api/v1/enrollments/bind",
    json={
        "enrollmentId": enrollment_id,
        "enrollmentProofToken": enrollment_proof_token
    }
)
```

### Breaking Changes

⚠️ **ATTENTION:** Il s'agit d'un **breaking change** qui nécessite:

1. **Mise à jour CLI**: Tous les utilisateurs CLI doivent mettre à jour vers la nouvelle version
2. **Nouveau workflow**: Les administrateurs doivent maintenant sauvegarder `enrollmentProofToken` lors de la création d'enrollment
3. **Documentation**: Mise à jour de tous les guides et tutoriels

---

## Récapitulatif Phase 6

| Tâche | Durée | Difficulté | Testable |
|-------|-------|------------|----------|
| **6.1** Créer `EnrollmentManager` class | 4h | 🟡 Moyen | ✅ Tests unitaires |
| **6.2** Implémenter `bind` command | 2h | 🟢 Facile | ✅ CLI interactif |
| **6.3** Implémenter `status` command | 1h | 🟢 Facile | ✅ CLI output |
| **6.4** Gestion device keys (génération, chiffrement) | 3h | 🟡 Moyen | ✅ Tests crypto |
| **6.5** Signature proof token avec device key | 2h | 🟡 Moyen | ✅ Tests signature |
| **6.6** Tests intégration end-to-end | 3h | 🟡 Moyen | ✅ Scénarios complets |
| **6.7** Documentation CLI | 2h | 🟢 Facile | ✅ Review |
| **TOTAL** | **17h (2-3 jours)** | **🟡 Moyen** | **✅ Complet** |

---

## Commit

```bash
git add ezkey_cli/admin/enrollment.py
git add ezkey_cli/commands/enroll.py
git add tests/test_enrollment_manager.py
git add ezkey_cli/main.py
git add ezkey_cli/admin/config.py
git commit -m "feat(cli): Add admin enrollment management with secure POST bind endpoint

- Implement EnrollmentManager class for enrollment binding
- Add 'ezkey admin enroll bind' command with proof token authentication
- Add 'ezkey admin enroll status' command for enrollment info
- Generate and manage device RSA-2048 key pairs
- Sign enrollment proof tokens with device private key
- Use secure POST /enrollments/bind endpoint (anti-enumeration)
- Add comprehensive tests for enrollment flow
- Update CLI config to store enrollment info

Security improvements:
- No enumeration: requires enrollmentProofToken upfront
- Rate limiting: 5 requests per 10 minutes
- Device key encryption with user password
- Audit trail for all bind attempts

Breaking change: Requires enrollmentProofToken parameter for bind command"
```

---

**Document Version**: 1.0  
**Created**: 2025-10-03  
**Status**: Ready for Implementation

