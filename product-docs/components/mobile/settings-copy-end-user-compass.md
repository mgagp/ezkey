# Settings copy — end-user editorial compass

## Metadata

| Field | Value |
|-------|-------|
| **Status** | draft |
| **Date** | 2026-09-19 |
| **Owner (operability)** | Julie — mobile-on-request |
| **Product** | Alex |
| **Voice polish (optional)** | Audrey — FR/EN later |
| **Acceptance** | Marc (draft until accepted) |
| **Source of truth (strings)** | [`ezkey_mobile/app/i18n/resources.ts`](../../../ezkey_mobile/app/i18n/resources.ts) |
| **Related** | [`ezkey_mobile/docs/MOBILE_POSITIONING.md`](../../../ezkey_mobile/docs/MOBILE_POSITIONING.md), [`product-docs/global/product-intent.md`](../../global/product-intent.md) |

## Intention

Settings / Paramètres informational surfaces are for the **non-technical end user** who uses Ezkey Mobile to enroll a trusted phone and approve or deny sign-in requests. Copy must stay honest (self-hosted, no analytics, real security limits) while staying short and digestible. Protocol thesis, founder narrative, operator evaluation framing, and passkey/FIDO comparisons belong on the website, Admin UI, and product-docs — not as default Settings prose.

This note is the editorial compass for future FR/EN Settings copy. Agents and humans editing Settings strings must follow it.

## Audience and job-to-be-done

| | |
|---|---|
| **Primary audience** | Non-technical end user |
| **Runtime job** | Enroll trusted device → check pending → approve or deny |
| **Trust need** | Clear, calm trust signals; little protocol jargon ([`MOBILE_POSITIONING.md`](../../../ezkey_mobile/docs/MOBILE_POSITIONING.md)) |
| **Not the job of Home Settings** | Distinct-from-passkeys/FIDO thesis for operators/evaluators ([`product-intent.md`](../../global/product-intent.md)) |

## Comparable MFA Settings norms

Typical authenticator apps (Authy, Microsoft Authenticator, Google Authenticator) keep Settings informational copy short:

| Surface | Norm |
|---------|------|
| **About** | Product name, version, one short “what this app does”, optional site link — not a white paper |
| **Security** | Device lock / biometrics preference; optional one honesty line — not an essay on crypto proofs |
| **What’s new** | Changelog / current capabilities — not a roadmap essay nested inside the release notes |
| **Coming soon** | Optional short expectation-setting; never a manifesto footer |

Ezkey should match that density while keeping self-hosted / no-analytics honesty.

## Section map (Marc / Julie decisions)

| Section | Decision | Notes |
|---------|----------|-------|
| **About / À propos** | **Rewrite** | Keep first-sentence job intent; soft “when an app asks you to confirm” (not admin-console-only). Replace protocol/FIDO/operator dense half with end-user value. Move privacy here once. Website link stays. |
| **What’s new / Nouveautés** | **Rewrite + cut** | Keep “included” as current-capability list in plain language. **Remove** nested Coming next. **Move** privacy note out (to About). Keep Requirements with light polish. |
| **Coming next / À venir** (Settings-level) | **Rewrite + cut** | Keep section. Plain user-benefit bullets. **Delete** closing manifesto footer. |
| **Language / Langue** | **Keep** | No change. |
| **Security / Sécurité** | **Rewrite (structure intention)** | Short default summary; optional “Learn more” accordion later. Do not invent stronger hardware claims than the app can prove. Honesty stays, shorter. |
| **Danger zone / Zone dangereuse** | **Keep** | Light wording only if needed. |
| **Open source / Licences** | **Keep** | Unchanged. |
| **Requirements / Prérequis** | **Keep** | Well calibrated; light plain-language polish only. |

## Anti-patterns (do not reintroduce)

1. **Protocol manifesto** in About or Coming soon (proprietary protocol, FIDO2/WebAuthn/passkeys essay, “operators who want full control over their backend”).
2. **Passkey thesis** as default Home Settings education — that belongs in product-intent / website for evaluators.
3. **Duplicate Coming** — nested “Coming next” inside What’s new when Settings already has Coming soon.
4. **Privacy in What’s new** — product identity + privacy live once in About; Security stays device protection.
5. **Closing manifesto** (“intentionally aiming for practical security… same spirit”) — no place for the end user.
6. **Over-claiming hardware** — no StrongBox / attestation claims Settings cannot prove.

## Security structure intention (not a visual redesign)

**This PR:** shorten Security callout copy so the default screen is readable.

**Follow-up (optional, Marc/Julie):** collapse honesty detail behind a single “Learn more” / “En savoir plus” accordion (or keep at most two short visible bullets). Preference controls stay as today. No Settings visual redesign campaign in this pass.

## Implementation status in this draft PR

| Deliverable | Status |
|-------------|--------|
| This compass note | Done (draft) |
| Discoverability pointers (`ezkey_mobile/AGENTS.md`, this pack README) | Done |
| Proposed string table (below) | Done |
| Applied `resources.ts` + light screen structure (remove nested Coming / privacy from What’s new; About privacy line; Coming footer removed; Security shortened) | Done in same PR — 1:1 with the table |
| Security accordion UI | Intention only — not implemented |
| Audrey voice polish | Deferred |

## Proposed string table

Keys are under `en.translation` / `fr.translation` in `resources.ts`. Only challenged Settings informational blocks are listed. Unchanged keys are omitted.

### About (`about.*`)

| Key | Current EN | Proposed EN | Current FR | Proposed FR |
|-----|------------|-------------|------------|-------------|
| `tagline` | Cryptographic MFA - backend-first, self-hosted, open source. | Confirm sign-ins from a trusted phone. Self-hosted. Open source. | AMF cryptographique - backend-first, auto-hébergée, open source. | Confirmez les connexions depuis un téléphone de confiance. Auto-hébergé. Open source. |
| `description` | Ezkey lets you enroll trusted devices and approve sign-in requests from your admin console. It is a proprietary cryptographic protocol, intentionally distinct from FIDO2/WebAuthn and passkeys, designed for operators who want full control over their authentication backend. | Ezkey lets you enroll this phone as a trusted device and approve or deny sign-in requests when an app asks you to confirm. Your organization runs Ezkey; this app is the trusted phone that confirms those sign-ins. | Ezkey vous permet d’enrôler des appareils de confiance et d’approuver des demandes de connexion depuis votre console d’administration. Il s’agit d’un protocole cryptographique propriétaire, volontairement distinct de FIDO2/WebAuthn et des passkeys, conçu pour les opérateurs qui veulent un contrôle complet sur leur backend d’authentification. | Ezkey vous permet d’enrôler ce téléphone comme appareil de confiance et d’approuver ou de refuser les demandes de connexion lorsqu’une application vous demande de confirmer. Votre organisation exploite Ezkey ; cette application est le téléphone de confiance qui confirme ces connexions. |
| `privacyNote` *(new)* | — (was `releaseNotes.note1`) | This app does not collect analytics or advertising identifiers. | — | Cette application ne collecte ni analytiques ni identifiants publicitaires. |

**Deliberate omission:** no soft “this is not a passkey app” line in About — it educates on protocols more than it helps the end user. Protocol distinction stays on ezkey.org / product-docs.

### What’s new (`releaseNotes.*`)

| Key | Current EN | Proposed EN | Current FR | Proposed FR |
|-----|------------|-------------|------------|-------------|
| `includedItem3` | Local storage for enrollments and device cryptographic keys. | Local storage for enrollments and the keys used on this phone. | Stockage local des enrôlements et des clés cryptographiques de l’appareil. | Stockage local des enrôlements et des clés utilisées sur ce téléphone. |
| `requirementsBody` | Android 12 or later. Camera access is used only to scan enrollment QR codes. Optional device confirmation (biometrics or screen lock) stays on this phone. | *(keep — light polish only)* Android 12 or later. Camera access is used only to scan enrollment QR codes. Optional device confirmation (biometrics or screen lock) stays on this phone. | Android 12 ou plus récent. L’accès à la caméra sert uniquement à scanner les QR d’enrôlement. La confirmation facultative sur l’appareil (biométrie ou verrouillage) reste sur ce téléphone. | *(keep)* Android 12 ou plus récent. L’accès à la caméra sert uniquement à scanner les QR d’enrôlement. La confirmation facultative sur l’appareil (biométrie ou verrouillage) reste sur ce téléphone. |
| `comingNextTitle`, `comingNextItem1`, `comingNextItem2` | Nested roadmap block | **Removed** from What’s new (use Settings → Coming soon) | Idem | **Supprimé** |
| `notesTitle`, `note1` | Privacy in What’s new | **Moved** to `about.privacyNote` (analytics line only; org self-host already in About description) | Idem | **Déplacé** |

Intro / included items 1–2 / site link: keep (already end-user capable).

### Coming soon (`comingSoon.*` + Settings subtitle)

| Key | Current EN | Proposed EN | Current FR | Proposed FR |
|-----|------------|-------------|------------|-------------|
| `settings.comingSoonSubtitle` | Near-term roadmap and expectation-setting notes | What we plan next for this app | Feuille de route rapprochée et notes de cadrage | Ce que nous prévoyons ensuite pour cette application |
| `eyebrow` | Near-term roadmap | Coming up | Feuille de route rapprochée | Prochaines étapes |
| `intro` | These are the next mobile improvements that matter most for expectation-setting and practical security. This is a focused roadmap, not an exhaustive feature list. | A short look at what we plan next. Not a full feature list. | Voici les prochains chantiers mobiles les plus utiles pour cadrer les attentes et faire progresser la sécurité de façon pragmatique. Il s’agit d’une feuille de route ciblée, pas d’une liste exhaustive. | Un aperçu court de ce que nous prévoyons ensuite. Pas une liste exhaustive. |
| `auth.title` | Authentication policy and stronger validation | Clearer approval rules | Policy d’authentification et validation plus forte | Règles d’approbation plus claires |
| `auth.body` | Future work will continue on authentication policy, local-auth enforcement, and stronger cryptographic validation. The goal is to keep the current protection honest while preparing a clearer policy model and stronger proof boundaries over time. | Make it clearer when this phone must confirm before you approve or deny — and keep that honest. | Le travail futur va continuer sur la policy d’authentification, l’application locale des exigences et une validation cryptographique plus forte. … | Rendre plus clair le moment où ce téléphone doit confirmer avant d’approuver ou de refuser — en restant honnête. |
| `pinning.title` | Certificate pinning, Ezkey-style | Stronger connection checks | Certificate pinning, à la manière Ezkey | Contrôles de connexion plus solides |
| `pinning.body` | Certificate pinning is planned in a pragmatic lightweight form. The initial direction is trust on first use during enrollment, followed by a controlled refresh path if the pin no longer matches, so later authentication cycles get meaningful network trust hardening without pretending to reach the most extreme pinning model. | Remember the server you enrolled with, so later check-ins have a clearer network trust path. | Le certificate pinning est prévu sous une forme légère et assumée. … | Mémoriser le serveur auquel vous vous êtes enrôlé, pour que les vérifications suivantes aient un chemin de confiance réseau plus clair. |
| `footer` | Ezkey Mobile is intentionally aiming for practical, explicit security improvements rather than over-claiming perfection. Future work will continue in that same spirit. | **Deleted** (no manifesto) | Ezkey Mobile vise volontairement des améliorations de sécurité pratiques et explicites plutôt que des promesses excessives. La suite du travail restera dans ce même esprit. | **Supprimé** |

### Security (`security.*`)

| Key | Current EN | Proposed EN | Current FR | Proposed FR |
|-----|------------|-------------|------------|-------------|
| `preferenceNoteTitle` | This setting is your device preference | Device confirmation on this phone | Ce réglage correspond à votre préférence locale | Confirmation sur cet appareil |
| `preferenceNoteBody` | When enabled, this phone asks for device confirmation before it approves or denies requests. Android may use strong biometrics or the device credential, depending on what is available. | When enabled, this phone asks for biometrics or your screen lock before approving or denying. | Lorsque ce mode est activé, ce téléphone demande une confirmation de l’appareil avant d’approuver ou de refuser une demande. Android peut utiliser une biométrie forte ou le code de l’appareil selon ce qui est disponible. | Lorsque ce mode est activé, ce téléphone demande la biométrie ou le verrouillage de l’écran avant d’approuver ou de refuser. |
| `declarativeNoteTitle` | This protection is enforced by the app | Honest limits | Cette protection est appliquée par l’application | Limites honnêtes |
| `declarativeNoteBody` | The app asks for local device confirmation before responding. The signing key itself does not require that confirmation, and the backend receives no cryptographic proof that it occurred or was bound to the signature. | Confirmation happens on this phone. The server is not cryptographically told that it took place. | L’application demande une confirmation locale de l’appareil avant de répondre. La clé de signature elle-même n’exige pas cette confirmation et le backend ne reçoit aucune preuve cryptographique qu’elle a eu lieu ou qu’elle était liée à la signature. | La confirmation a lieu sur ce téléphone. Le serveur n’en reçoit pas de preuve cryptographique. |

Option titles/bodies (`standard*`, `confirmation*`) and error strings: keep (already concrete). Accordion “Learn more” keys: deferred until structure follow-up.

### Danger zone / Open source / Language / Requirements

| Area | Action |
|------|--------|
| Language | Keep as-is |
| Danger zone | Keep (wording already clear) |
| Open source licenses | Keep |
| Requirements (in What’s new) | Keep (calibrated) |

## Review checklist (Marc)

- [ ] About balances substance without protocol manifesto
- [ ] Privacy appears once (About), not in What’s new
- [ ] What’s new has no nested Coming next
- [ ] Coming soon is short user-benefit bullets; no manifesto footer
- [ ] Security is short and honest; no over-claim
- [ ] Optional Audrey FR/EN voice polish after acceptance
