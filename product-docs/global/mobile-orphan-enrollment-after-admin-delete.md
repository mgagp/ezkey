# Mobile — Enrollment orphelin après delete admin (constat alpha)

## Metadata

- **Document ID:** `mobile-orphan-enrollment-after-admin-delete`
- **Status:** `draft` (constat backlog / observation alpha — **pas** une feature engagée)
- **Owner (opérabilité):** Julie (opérabilité / mobile on request)
- **Product priority (related):** Alex
- **Security honesty:** Christophe (ne pas inventer un wipe serveur de l’état appareil)
- **Purpose:** Documenter un edge case connu sous architecture pull-only ; compass d’évolution ordonné ; workaround ops alpha sans nouveau code.
- **Evidence:** observation alpha — cartes Home indiscernables (ex. deux « Oscar Boulon — iPhone 15 » sous le même tenant) alors que le backend ne garde que le nouvel enrollment.
- **Related:**
  - Pull model: [`architecture-decisions.md`](architecture-decisions.md) ADR-0002 ; [`../components/mobile/design-decisions.md`](../components/mobile/design-decisions.md) ADR-MOB-0001
  - Auth API device plane: [`../../docs/ENDPOINT.md`](../../docs/ENDPOINT.md) (pending / respond / bind / verify / enrolled instance-info) ; [`../../ezkey-auth-api/AGENTS.md`](../../ezkey-auth-api/AGENTS.md)
  - Signature payloads: [`../../docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md`](../../docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md), [`../../docs/ENROLLMENT_SIGNATURE_PAYLOAD.md`](../../docs/ENROLLMENT_SIGNATURE_PAYLOAD.md)
  - Mobile storage / identity: [`../../ezkey_mobile/docs/MOBILE_DATA_MODEL.md`](../../ezkey_mobile/docs/MOBILE_DATA_MODEL.md), [`../../ezkey_mobile/app/services/storage/enrollmentStorage.ts`](../../ezkey_mobile/app/services/storage/enrollmentStorage.ts), [`../../ezkey_mobile/app/utils/localEnrollmentIdentity.ts`](../../ezkey_mobile/app/utils/localEnrollmentIdentity.ts), [`../../ezkey_mobile/app/utils/enrollmentDisplay.ts`](../../ezkey_mobile/app/utils/enrollmentDisplay.ts)
  - Lifecycle admin delete / revoke: [`../../docs/LIFECYCLE_GOVERNANCE.md`](../../docs/LIFECYCLE_GOVERNANCE.md) §3.3 ; `DELETE /api/v1/enrollments/{id}` in ENDPOINT
  - Operator posture: [`operator-alignment-guide.md`](operator-alignment-guide.md) (Ezkey n’est jamais le cœur de métier de l’adopter)

---

## Scénario (alpha)

1. Un opérateur **supprime** (ou révoque / désactive de façon à rendre non-opérationnel) un enrollment côté Admin API.
2. Il **recrée immédiatement** un enrollment pour la même personne / le même appareil (même `enrollmentName`, même device label, même tenant / intégration).
3. L’utilisateur mobile **ré-enrol** avec succès → une **nouvelle** ligne locale est ajoutée.
4. L’ancienne liaison locale **reste** sur le téléphone.
5. Home affiche **N cartes indiscernables** (même Purpose « Administration », même Account « Oscar Boulon — iPhone 15 », pas d’id enrollment ni de statut visible).
6. Quand l’utilisateur « vérifie les demandes en attente », il peut ouvrir / poller une **copie morte** : le backend n’a plus (ou n’a plus d’actif pour) cet enrollment ; seule la carte live répond utilement.

Ce n’est **pas** un échec de ré-enrollment. Le nouveau bind/verify a réussi. C’est un **orphelin local** sous architecture **pull-only** (mobile → Auth API) : le serveur ne pousse jamais une révocation vers l’appareil.

---

## Cause (classe)

| Couche | Comportement constaté |
|--------|------------------------|
| **Backend** | Après delete (ou inactive), l’ancien `enrollmentId` / proof token n’est plus un enrollment actif. Le catalogue serveur ne contient que le nouvel enrollment. |
| **Mobile** | Catalogue local indépendant : AsyncStorage (`ezkey-mobile/enrollments`) + secrets scellés + clé privée Keystore, clé locale dérivée de `{installationScope}_e{serverEnrollmentId}` (`deriveLocalEnrollmentId`). Un nouvel id serveur → **nouvelle** entrée ; l’ancienne n’est pas écrasée ni invalidée. |
| **UI Home** | `buildHomeCardLabels` expose Purpose + Account (+ Role admin) — pas l’id serveur, pas `createdAt`, pas un statut « live / orphelin ». Deux enrollments person-first identiques → cartes clones. |
| **Protocol** | ADR-0002 / ADR-MOB-0001 : **pas de push**, pas de background poll. Aucun canal pour « wipe » l’état appareil depuis le serveur. Affirmer le contraire serait une erreur de posture (Christophe). |

---

## Ce que l’Auth API offre aujourd’hui (pas inventé)

Le mobile ne parle qu’à l’Auth API. Surfaces device utiles pour un enrollment déjà stocké :

| Endpoint | Rôle | Comportement si enrollment mort / inactif |
|----------|------|---------------------------------------------|
| `POST /api/v1/auth-attempts/pending` | Poll user-initiated d’une demande MFA | `AuthAttemptPendingService.validateEnrollment` résout via `findByEnrollmentProofTokenHashAndActive(..., true)`. Token absent / inactif → `AuthAttemptRequestFailedException` → RFC 9457 **`https://ezkey.io/problems/auth/auth-attempt-binding-failed`** (détail générique, anti-énumération). **Ce n’est pas** le type Integration API `…/enrollment/enrollment-inactive`. |
| `POST /api/v1/auth-attempts/respond` | Approve / deny | Même famille d’échecs de binding / state si la preuve ou l’attempt n’est plus valide. |
| `POST /api/v1/enrollments/instance-info` | Branding installation signé | Même gate `findByEnrollmentProofTokenHashAndActive` → échec générique `enrollment-instance-info-failed` si token mort. |
| `POST /api/v1/enrollments/bind` / `verify` | Onboarding seulement | Ne « soignent » pas un orphelin déjà listé. |

**Absent aujourd’hui (à ne pas inventer) :**

- Endpoint dédié « check enrollment liveness / tombstone » pour le mobile.
- Push / silent revoke vers l’appareil.
- API qui efface le stockage local du téléphone.

Note : `enrollment-inactive` (403, Integration API) protège la **création** d’attempts côté intégration — ce n’est pas le signal que le mobile reçoit sur `pending` pour un orphelin.

Côté mobile, `usePendingAuth` / Danger Zone permettent le **retrait local** (`enrollmentStorage.deleteEnrollment`) ; il n’y a **pas** aujourd’hui de self-heal automatique sur `auth-attempt-binding-failed`.

---

## Workaround ops alpha (aucun nouveau code)

Pragmatique, suffisant pour alpha :

1. **Sur le mobile** : Zone de danger (ou retrait local) — **supprimer toutes** les cartes ambiguës pour cette personne / ce device sur cette installation.
2. **Côté admin** : s’assurer qu’il n’existe **qu’un** enrollment actif pour ce compte (sinon en recréer **un** après nettoyage mobile).
3. **Ré-enrol** une seule fois avec le QR / invitation courant.

Ne pas demander à l’utilisateur de « deviner » laquelle des N cartes est live. Ne pas promettre que le serveur a déjà nettoyé le téléphone.

---

## Options d’évolution (analyse seule — pas d’implémentation dans ce document)

Ordonnées par pragmatisme / coût de posture (du plus réaliste au plus architectural) :

| | Option | Effet | Coût / risque |
|---|--------|-------|----------------|
| **A** | **Self-heal mobile** sur échec de pull connu (`auth-attempt-binding-failed` / instance-info failed après preuve valide côté client) → marquer ou retirer l’enrollment local, UX claire | Réduit les orphelins sans changer le protocole push | Attention anti-énumération : le détail serveur est volontairement générique ; il faut une heuristique UX prudente (pas supprimer sur toute erreur réseau). |
| **B** | Endpoint explicite **« enrollment status / tombstone »** que le mobile appelle à l’ouverture ou périodiquement (user-initiated ou léger) | Signal stable, moins ambigu qu’un binding-failed | Nouveau contrat Auth API + OpenAPI + clients ; reste pull-only. |
| **C** | **Distinguer les cartes** (id enrollment, `createdAt`, badge statut) **avant** heal | Réduit l’ambiguïté visuelle seulement ; l’orphelin reste | Faible coût UI ; n’enlève pas l’entrée morte. |
| **D** | **Push / silent revoke** | Sync serveur → appareil | **Hors posture** tant qu’ADR-0002 / ADR-MOB-0001 tiennent. Changement architectural produit, pas un petit fix. Rouvrir explicitement le choix « no push ». |
| **E** | **Guidance Admin UI / runbook** seule | Déjà couvert par le workaround alpha | Zéro code mobile ; utile en complément, insuffisant seul si on veut du self-heal. |

Recommandation de compass (non engagée) : **E maintenant** ; explorer **A** puis éventuellement **C** en alpha/ops ; **B** si A reste trop ambigu côté problèmes génériques ; **D** seulement si le produit rouvre le no-push.

---

## Contraintes à respecter

- Ezkey n’est **jamais** le cœur de métier de l’adopter — opérable, simple, 80/20 ([`operator-alignment-guide.md`](operator-alignment-guide.md)).
- **No push** est intentionnel (ADR-0002, ADR-MOB-0001).
- Honnêteté sécurité : le serveur **ne peut pas** revendiquer d’effacer l’état local de l’appareil ; l’utilisateur (ou le self-heal client) retire la liaison.
- Ce document est un **constat + compass**, pas un engagement de feature ni un `TB-*`.

---

## Statut

`draft` — prêt pour relecture Marc (acceptation du constat alpha + compass). Promotion éventuelle vers runbook ops / intention mobile uniquement après décision produit explicite.
