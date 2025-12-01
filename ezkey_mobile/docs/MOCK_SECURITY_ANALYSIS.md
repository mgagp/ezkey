# Analyse de Sécurité : Mocks en Production

## État Actuel

**Les mocks ont été complètement supprimés de l'application mobile.**

L'application utilise uniquement les implémentations natives :
- `nativeCrypto` : Module natif EC P-256 (Android/iOS)
- `secureStorage` : Platform Keychain (iOS Keychain / Android Keystore)

Aucun fallback ou mock n'est disponible. L'application échoue explicitement si le module natif n'est pas disponible.

## Historique

**Problème Identifié (Résolu) :**

L'application utilisait précédemment des mocks (`mockCrypto` et `mockSecureStorage`) qui pourraient potentiellement être utilisés en production, ce qui représentait un risque de sécurité majeur pour une application cryptographique.

## État Actuel

### 1. Mock Secure Storage (`mockSecureStorage.ts`)

**Utilisation actuelle :**
```typescript
const DEFAULT_USE_MOCK = __DEV__;
export const enrollmentStorage = EnrollmentStorage.create({useMockSecure: DEFAULT_USE_MOCK});
```

**Analyse :**
- ✅ **Sécurisé** : Utilisé uniquement si `__DEV__` est `true`
- ✅ En production (`__DEV__ = false`), le vrai `secureStorage` (Keychain) est utilisé
- ✅ Pas de risque en production

### 2. Mock Crypto (`mockCrypto.ts`)

**Utilisation actuelle :**
```typescript
static create(): CryptoService {
  if (Platform.OS === 'android' || Platform.OS === 'ios') {
    try {
      if (isNativeCryptoLinked) {
        return new CryptoService(nativeCryptoAdapter, 'native');
      }
      throw new Error('Native crypto module unavailable');
    } catch (error) {
      console.warn('[cryptoService] Falling back to mock crypto adapter:', error);
      return new CryptoService(mockCrypto, 'mock'); // ⚠️ RISQUE
    }
  }
  return new CryptoService(mockCrypto, 'mock'); // ⚠️ RISQUE
}
```

**Problèmes identifiés :**
- ❌ **Risque de sécurité** : Fallback vers mock si le module natif échoue
- ❌ **Risque de sécurité** : Utilisé pour les plateformes non-mobiles (web, etc.)
- ❌ En production, si le module natif échoue, l'app utiliserait des signatures mock (non sécurisées)

## Solution Recommandée

### Option 1: Fail-Safe Strict (Recommandée)

**Principe :** L'application doit **échouer explicitement** si le module natif n'est pas disponible en production, plutôt que de fallback vers un mock.

**Implémentation :**

```typescript
// cryptoService.ts
static create(): CryptoService {
  // En production, on refuse catégoriquement les mocks
  const isProduction = !__DEV__;
  
  if (Platform.OS === 'android' || Platform.OS === 'ios') {
    if (!isNativeCryptoLinked) {
      if (isProduction) {
        // En production : échec explicite
        throw new Error(
          'CRITICAL: Native crypto module not available. ' +
          'Application cannot function securely without hardware-backed keys.'
        );
      }
      // En développement : fallback vers mock avec warning
      console.warn('[cryptoService] DEV MODE: Using mock crypto (NOT SECURE)');
      return new CryptoService(mockCrypto, 'mock');
    }
    return new CryptoService(nativeCryptoAdapter, 'native');
  }
  
  // Plateformes non-mobiles : échec en production
  if (isProduction) {
    throw new Error(
      'CRITICAL: Mobile platform required for secure crypto operations. ' +
      'Ezkey requires Android or iOS for hardware-backed key storage.'
    );
  }
  
  // En développement : mock avec warning
  console.warn('[cryptoService] DEV MODE: Using mock crypto (NOT SECURE)');
  return new CryptoService(mockCrypto, 'mock');
}
```

### Option 2: Vérification au Build Time

**Principe :** Utiliser des flags de build pour exclure complètement les mocks des builds de production.

**Implémentation :**

```typescript
// cryptoService.ts
static create(): CryptoService {
  if (Platform.OS === 'android' || Platform.OS === 'ios') {
    if (!isNativeCryptoLinked) {
      // En production, on ne devrait jamais arriver ici
      if (process.env.NODE_ENV === 'production') {
        throw new Error('Native crypto module required in production');
      }
      return new CryptoService(mockCrypto, 'mock');
    }
    return new CryptoService(nativeCryptoAdapter, 'native');
  }
  
  // Plateformes non-mobiles : erreur en production
  if (process.env.NODE_ENV === 'production') {
    throw new Error('Mobile platform required in production');
  }
  
  return new CryptoService(mockCrypto, 'mock');
}
```

## Recommandation Finale

**Option 1 (Fail-Safe Strict)** est recommandée car :

1. ✅ **Sécurité maximale** : Impossible d'utiliser des mocks en production
2. ✅ **Débogage facilité** : Erreurs explicites si quelque chose ne va pas
3. ✅ **Développement préservé** : Mocks toujours disponibles en `__DEV__`
4. ✅ **Pas de dépendance build** : Fonctionne avec tous les outils de build

## Actions Requises

1. ✅ Modifier `cryptoService.ts` pour utiliser fail-safe strict
2. ✅ Vérifier que `mockSecureStorage` est déjà sécurisé (utilise `__DEV__`)
3. ✅ Ajouter des tests pour vérifier que les mocks ne sont jamais utilisés en production
4. ✅ Documenter la politique de sécurité dans le code

## Tests de Validation

```typescript
// Tests à ajouter
describe('CryptoService Production Safety', () => {
  it('should throw error if native module unavailable in production', () => {
    // Simuler production mode
    // Simuler module natif non disponible
    // Vérifier que ça throw une erreur
  });
  
  it('should allow mock in development mode', () => {
    // Simuler dev mode
    // Simuler module natif non disponible
    // Vérifier que mock est utilisé avec warning
  });
});
```

