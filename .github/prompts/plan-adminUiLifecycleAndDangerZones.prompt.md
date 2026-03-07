# Plan: Admin UI — Lifecycle & Danger Zones

## Objectif de la session

Combler les gaps opérationnels les plus critiques pour un Tenant Admin : les actions de cycle de vie
des enrollments et des intégrations sont disponibles en API mais totalement absentes du UI. En
situation d'incident de sécurité, un admin doit pouvoir révoquer, désactiver ou supprimer sans
passer par l'API directement.

Un deuxième objectif est de compléter le workflow de provisioning d'admin en permettant la création
d'un Global Admin depuis le UI (actuellement impossible).

Enfin, cette session introduit le composant **Toast** — nécessaire dès qu'une action destructive
ferme son dialog immédiatement sans step 2 de confirmation.

---

## Contexte technique

### Patterns déjà établis à réutiliser

- **Danger Zone inline** : section rouge en bas de page avec 2-step confirm via état local
  (`confirmAction: null | 'deactivate' | 'revoke' | ...`). Voir `admins.tsx` (deactivate admin).
- **Dialog 2 steps** : step 1 = form/confirm, step 2 = credential reveal. Voir `admins.tsx`
  (create admin → onboarding credentials).
- **useMutation + queryClient.invalidateQueries** : pattern standard pour toutes les mutations.
  Voir `api-keys.tsx` (revoke key) et `admins.tsx` (deactivate admin).
- **Alert variant="error"** dans le Dialog pour afficher les erreurs de mutation.
- **`useAuth()`** pour accéder au `adminType` du compte courant et gate les actions.

### Types générés disponibles (Orval)

```typescript
import type {
  EnrollmentResponseDto,
  EnrollmentResponseDtoEnrollmentStatus,
  AdminResponseDto,
  AdminResponseDtoAdminType,
  AdminCreateRequestDto,
  AdminOnboardingResponseDto,
  AdminProvisioningResponseDto,
} from '@/generated/admin-api/model';
```

---

## Étape 1 — Composant Toast (infrastructure UX)

### Pourquoi maintenant

Les actions Revoke, Deactivate, Delete ferment leur dialog immédiatement après succès. Sans toast,
l'utilisateur n'a aucun feedback visuel. C'est la seule nouvelle infrastructure UI de la session —
tout le reste est du métier.

### Implémentation

Créer `src/components/ui/toast.tsx` — composant autonome, **aucune dépendance externe**.

```
src/components/ui/toast.tsx       ← composant + hook useToast
src/context/toast-context.tsx     ← ToastProvider (wrappé dans main.tsx ou App.tsx)
```

**API du composant :**

```typescript
// Hook à utiliser dans les pages
const { toast } = useToast();
toast({ message: 'Enrollment revoked.', variant: 'success' }); // ou 'error' | 'info'
```

**Comportement :**
- Apparaît en bas à droite, z-index élevé
- Durée : 3s pour success/info, 5s pour error, avec dismiss manuel
- Max 3 toasts simultanés (les plus anciens disparaissent)
- Pas d'animation complexe — un simple fade/slide CSS suffit

**Variants visuels :** `success` (vert), `error` (rouge), `info` (neutre) — reprend les couleurs
du système de `Badge` et `Alert` existants.

### Intégration

Wrapper `ToastProvider` autour de l'app dans `src/main.tsx` ou `src/App.tsx`.

---

## Étape 2 — `enrollment-detail.tsx` : Danger Zone

### Endpoints API à câbler

| Action | Méthode | Endpoint |
|---|---|---|
| Deactivate | `POST` | `/api/v1/enrollments/{id}/deactivate` |
| Reactivate | `POST` | `/api/v1/enrollments/{id}/reactivate` |
| Revoke | `POST` | `/api/v1/enrollments/{id}/revoke` |
| Delete | `DELETE` | `/api/v1/enrollments/{id}` |

### Règles de visibilité des boutons

| Bouton | Condition d'affichage |
|---|---|
| **Deactivate** | `enrollmentActive === true` ET status ≠ `REVOKED` |
| **Reactivate** | `enrollmentActive === false` ET status ≠ `REVOKED` |
| **Revoke** | status ≠ `REVOKED` (irréversible, toujours available si pas encore révoqué) |
| **Delete** | Toujours visible (admin override ultime) |

### Implémentation dans la page

Ajouter une `Card` "Danger Zone" en bas de la colonne gauche (après Enrollment Info), avec le
pattern 2-step inline déjà utilisé :

```typescript
type DangerAction = 'deactivate' | 'reactivate' | 'revoke' | 'delete' | null;
const [pendingAction, setPendingAction] = useState<DangerAction>(null);
```

**Step 1** : bouton "Deactivate" → remplacé par ligne de confirmation inline avec message + boutons
"Cancel" / "Confirm Deactivate".

**Step 2** : en cas de succès → toast `{ message: 'Enrollment deactivated.', variant: 'success' }` +
`queryClient.invalidateQueries(['enrollment', id])`. Pas de step 2 dialog.

**Cas Revoke** : la confirmation doit être plus forte. Afficher le message :
> "This will permanently revoke the enrollment. The device will no longer be able to authenticate.
> This cannot be undone."

**Cas Delete** : afficher un champ de saisie du nom de l'enrollment pour confirmer
(pattern "type the name to confirm" — comme GitHub). Activer le bouton "Delete" seulement si
`inputValue === enrollment.enrollmentName`.

**Après Delete** : `navigate('/enrollments')` (la ressource n'existe plus).

### Mutations

```typescript
const deactivateMutation = useMutation({
  mutationFn: () => api.post(`/api/v1/enrollments/${id}/deactivate`),
  onSuccess: () => {
    queryClient.invalidateQueries({ queryKey: ['enrollment', id] });
    setPendingAction(null);
    toast({ message: 'Enrollment deactivated.', variant: 'success' });
  },
});
// idem pour reactivate, revoke, delete
```

---

## Étape 3 — `integration-detail.tsx` : Danger Zone

### Endpoints API à câbler

| Action | Méthode | Endpoint |
|---|---|---|
| Deactivate All Enrollments | `POST` | `/api/v1/integrations/{id}/deactivate-enrollments` |
| Reactivate All Enrollments | `POST` | `/api/v1/integrations/{id}/reactivate-enrollments` |
| Revoke All Enrollments | `POST` | `/api/v1/integrations/{id}/revoke-enrollments` |
| Delete Integration | `DELETE` | `/api/v1/integrations/{id}` |

> **Note** : Vérifier les noms exacts des endpoints dans `specs/admin-api/openapi-spec.json` avant
> d'implémenter — les suffixes peuvent différer (`/bulk-deactivate`, `/actions/deactivate-all`, etc.).

### Implémentation

Ajouter une `Card` "Danger Zone" en bas de `integration-detail.tsx`. La structure visuelle est
identique à celle de `enrollment-detail.tsx` : confirmations inline 2-step, état local
`pendingAction`.

**Actions de masse** (Deactivate All, Reactivate All, Revoke All) : message de confirmation clair
avec le nombre d'enrollments concernés si disponible (utiliser `enrollments.length` déjà chargé
sur la page).

**Delete Integration** : saisie du `code` de l'intégration pour confirmer. Après succès :
`navigate('/integrations')`.

**Revoke All** : phrasing fort car irréversible :
> "This will revoke all {n} enrollments. All associated devices will immediately lose the ability
> to authenticate. This cannot be undone."

---

## Étape 4 — `admins.tsx` : Création d'un Global Admin

### Contexte

Le dialog "Create Admin" poste actuellement toujours vers `/api/v1/admins/tenant`. L'endpoint
`/api/v1/admins/global` n'est accessible que par un `GLOBAL_ADMIN` et crée un admin lié au tenant
système.

### Implémentation

Dans le dialog de création, ajouter un `Select` ou un toggle "Admin Type" visible **uniquement**
si `currentUser.adminType === 'GLOBAL_ADMIN'` :

```typescript
const { admin: currentAdmin } = useAuth();
const isGlobalAdmin = currentAdmin?.adminType === 'GLOBAL_ADMIN';
```

```tsx
{isGlobalAdmin && (
  <div>
    <Label>Admin Type</Label>
    <Select value={adminType} onChange={(e) => setAdminType(e.target.value)}>
      <option value="TENANT_ADMIN">Tenant Admin</option>
      <option value="GLOBAL_ADMIN">Global Admin</option>
    </Select>
  </div>
)}
```

Le champ `tenantId` dans le schema Zod doit devenir optionnel si `adminType === 'GLOBAL_ADMIN'`
(l'endpoint global ne prend pas de `tenantId`).

**Routing de la mutation :**

```typescript
const endpoint = adminType === 'GLOBAL_ADMIN'
  ? '/api/v1/admins/global'
  : '/api/v1/admins/tenant';
mutationFn: (data) => api.post<AdminProvisioningResponseDto>(endpoint, data)
```

**Validation Zod conditionnelle :**

```typescript
const schema = z.object({
  username: z.string().min(3),
  email: z.string().email(),
  adminType: z.enum(['TENANT_ADMIN', 'GLOBAL_ADMIN']),
  tenantId: z.string().optional(),
}).refine(
  (d) => d.adminType === 'GLOBAL_ADMIN' || !!d.tenantId,
  { message: 'Tenant is required for Tenant Admin', path: ['tenantId'] }
);
```

---

## Ordre d'exécution recommandé

1. **Toast** — d'abord, car toutes les étapes suivantes l'utilisent
2. **Enrollment Danger Zone** — périmètre le plus ciblé, bon test du pattern
3. **Integration Danger Zone** — réutilise exactement le même pattern
4. **Global Admin creation** — indépendant, peut être fait dans n'importe quel ordre

---

## Critères de sortie de la session

1. `npm run build` — zéro erreur TypeScript
2. `npm run dev` — les 3 flows sont testés manuellement :
   - Deactivate + Reactivate d'un enrollment → toast de succès visible, badge mis à jour
   - Revoke d'un enrollment → irréversible, confirm fort, toast
   - Delete enrollment → saisie du nom, navigate vers `/enrollments`
   - Revoke All sur une intégration → message avec compte, confirm fort
   - Delete intégration → saisie du code, navigate vers `/integrations`
   - Création d'un Global Admin (si compte GLOBAL_ADMIN disponible en dev)
3. Aucun toast d'erreur "unexpected" — toutes les erreurs API sont catchées et affichées dans
   la UI (Alert dans le dialog ou toast variant="error")

---

## Hors-scope explicite

- Page Tenants → session dédiée suivante
- Opérations d'intégrité Audit Logs → après Tenants
- Migration Phase 2 Orval hooks → chantier technique distinct
- Inline edit (PUT) sur Integration et Enrollment → inclus dans la session Tenants
- Pagination API Keys → inclus dans la session Tenants (refonte des pages liste)
