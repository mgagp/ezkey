Note: plan implemented on 2026-04-06. Archived after implementation of the Crypto API Ed25519 endpoints, canonical payload helper, controller tests, Postman collection updates, and ezkeyDart prompt revision.

## Plan: Crypto API for ezkeyDart

Approche recommandee: option 2, complete protocolaire. Elle couvre pleinement le besoin du plan ezkeyDart sans tomber dans la surconstruction. L’option 1 est trop etroite: elle debloque Ed25519 mais laisse hors API la construction canonique des payloads EZKey, alors que le plan Dart vise a reproduire fidelement le contrat cryptographique complet. L’option 3 est trop large pour l’objectif courant: les helpers d’encodage et de normalisation ont une valeur de diagnostic, mais ne sont pas necessaires pour supporter pleinement le plan de reference.

**Steps**
1. Cadrer l’evolution Crypto API autour du contrat cryptographique EZKey a valider depuis Dart. Inclure explicitement les deux roles cryptographiques distincts: device en EC P-256 / ECDSA-SHA256 et integration en Ed25519. Cette etape reutilise les conventions et formats de org.ezkey.signature.SignatureService et la specification de payloads canoniques de org.ezkey.authattempt.service.AuthAttemptSignaturePayload.
2. Ajouter les endpoints Ed25519 manquants dans ezkey-crypto-api. Cette etape depend de 1.
   - POST /api/v1/crypto/sign-ed25519: expose SignatureService.signIntegrationPayload(data, privateKeyPkcs8Base64Std).
   - POST /api/v1/crypto/verify-ed25519: expose SignatureService.verifyIntegrationSignature(data, signatureBase64Url, publicKeyBase64Url).
   - Reprendre les patterns DTO / controller / OpenAPI deja utilises par les endpoints sign et validate existants.
3. Ajouter un helper de payload canonique dans ezkey-crypto-api. Cette etape depend de 1 et peut etre implementee en parallele de 2.
   - POST /api/v1/crypto/payload-helper ou /payload-builder.
   - Supporter au minimum les trois types utilises par le plan Dart: pending, respond, respond-result.
   - Le helper doit reconstruire exactement les payloads canoniques EZKey, avec NFC sur les champs textuels pertinents, afin de servir d’oracle de validation pour Dart et d’outil utilisable dans Postman.
4. Garder hors scope les helpers d’encodage generiques. Cette etape formalise la borne de perimetre.
   - Ne pas ajouter pour l’instant d’endpoint normalize-base64 ou d’outils de conversion SPKI / raw si aucun scenario concret du plan Dart n’en depend.
   - S’appuyer sur les formats et decodeurs flexibles deja portes par SignatureService et sur la documentation protocolaire pour ces aspects.
5. Mettre a jour les tests du module ezkey-crypto-api. Cette etape depend de 2 et 3.
   - Ajouter des tests controller pour sign-ed25519 et verify-ed25519, sur le modele de CryptoControllerTest.
   - Ajouter des tests pour payload-helper, incluant les cas pending, respond et respond-result avec normalisation NFC.
   - Verifier que les reponses exposent clairement l’algorithme et un message de validation coherent.
6. Mettre a jour la collection Postman crypto. Cette etape depend de 2 et 3.
   - Ajouter les requetes sign-ed25519 et verify-ed25519 a la collection crypto existante.
   - Ajouter une requete payload-helper pour construire les payloads canoniques au lieu de dupliquer la logique JavaScript dans plusieurs workflows.
   - Aligner les noms et variables d’environnement avec les conventions existantes: integration_privateKey, integration_publicKey, pendingPayload, respondPayload, respondResultPayload.
7. Ajuster les workflows Postman qui s’appuient indirectement sur la crypto EZKey. Cette etape depend de 6.
   - Dans les collections auth attempts et, si utile, enrollments, remplacer ou simplifier les pre-request scripts qui reconstruisent manuellement les payloads quand l’usage du helper apporte une meilleure fiabilite.
   - Conserver une approche pragmatique: ne migrer que les scripts lies aux payloads canoniques qui comptent pour le plan Dart et les validations interop.
8. Reviser le prompt de plan ezkeyDart pour refleter la bonne dependance amont. Cette etape depend de 2, 3 et 6.
   - Remplacer le prerequis actuel limite a sign-ed25519 et verify-ed25519 par un prerequis plus juste: evolution du Crypto API pour exposer les primitives Ed25519 et un oracle de payload canonique.
   - Ajouter explicitement dans le plan l’ajustement requis de la collection Postman correspondante.
9. Valider l’ensemble. Cette etape depend de 5, 6, 7 et 8.
   - Validation unitaire des nouveaux endpoints et DTOs.
   - Verification manuelle via Postman du cycle integration keypair -> payload-helper -> sign-ed25519 -> verify-ed25519.
   - Verification de bout en bout de l’usage oracle pour les flux pending, respond et respond-result attendus par ezkeyDart.

**Relevant files**
- [`ezkey_dart/README.md`](../../../ezkey_dart/README.md) — Dart helpers + Crypto API oracle usage (Ed25519 and canonical payload-helper).
- c:\github\ezkey-worktree3\ezkey-crypto-api\src\main\java\org\ezkey\crypto\controller\CryptoController.java — surface REST a etendre avec les endpoints Ed25519 et le helper de payload.
- c:\github\ezkey-worktree3\ezkey-crypto-api\src\main\java\org\ezkey\crypto\dto\SignDataRequestDto.java — patron DTO de signature a reutiliser ou dupliquer pour Ed25519.
- c:\github\ezkey-worktree3\ezkey-crypto-api\src\main\java\org\ezkey\crypto\dto\ValidateSignatureRequestDto.java — patron DTO de verification a reutiliser pour verify-ed25519.
- c:\github\ezkey-worktree3\ezkey-crypto-api\src\main\java\org\ezkey\crypto\dto\ValidateSignatureResponseDto.java — patron DTO de reponse de verification a reutiliser.
- c:\github\ezkey-worktree3\ezkey-crypto-api\src\test\java\org\ezkey\crypto\controller\CryptoControllerTest.java — tests MockMvc de reference a etendre.
- c:\github\ezkey-worktree3\ezkey-core\src\main\java\org\ezkey\signature\SignatureService.java — logique existante a exposer: signIntegrationPayload, verifyIntegrationSignature, decodeFlexibleBase64ToBytes, normalizeIntegrationPublicKeyToBase64, generateProofToken.
- c:\github\ezkey-worktree3\ezkey-core\src\main\java\org\ezkey\signature\Ed25519SpkiBytes.java — reference sur les formats Ed25519 raw et SPKI.
- c:\github\ezkey-worktree3\ezkey-core\src\main\java\org\ezkey\authattempt\service\AuthAttemptSignaturePayload.java — reference canonique pour les payloads pending, respond et respond-result avec NFC.
- c:\github\ezkey-worktree3\postman\collections\v2.1\EZ Key crypto.postman_collection.json — collection principale a etendre.
- c:\github\ezkey-worktree3\postman\collections\v2.1\EZ Key Auth Attempts auth.postman_collection.json — collection a harmoniser si l’on remplace les scripts manuels par le helper de payload.
- c:\github\ezkey-worktree3\docs\CRYPTO.md — reference sur les formats de cles, signatures et encodages.
- c:\github\ezkey-worktree3\docs\AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md — reference sur les payloads canoniques et la normalisation NFC.

**Verification**
1. Verifier que sign-ed25519 produit bien une signature Ed25519 raw 64 bytes encodee en Base64URL sans padding a partir d’une cle privee PKCS#8 Base64 standard.
2. Verifier que verify-ed25519 accepte les cles publiques Ed25519 raw 32 bytes en Base64URL et rejette un payload modifie.
3. Verifier que payload-helper reconstruit exactement les trois payloads attendus par le protocole EZKey avec NFC sur les champs textuels.
4. Verifier que la collection Postman crypto permet de reproduire un flux oracle complet sans logique crypto hors API.
5. Verifier que le prompt plan-ezkeyDart reflète bien la dependance amont complete sur Crypto API et Postman.

**Decisions**
- Recommandation: option 2.
- Inclus: Ed25519 sign and verify, helper de payload canonique, ajustement de la collection Postman crypto, harmonisation ciblee des collections auth attempts si utile.
- Exclu: endpoints generiques d’encodage, de normalisation ou de conversion supplementaires tant qu’aucun cas du plan ezkeyDart ne les justifie.
- Justification: le plan ezkeyDart cherche a valider le vrai contrat crypto EZKey, pas seulement deux primitives brutes; le helper de payload apporte la couverture manquante la plus utile avec un cout limite.

**Further Considerations**
1. Pour le nommage du helper, preferer payload-helper plutot que payload-builder si l’intention principale est l’inspection et la validation, pas la production applicative.
2. Pour Postman, limiter les remplacements de scripts manuels aux flux pending, respond et respond-result afin de conserver un diff focalise et lisible.
3. Si un besoin concret apparait plus tard sur les variantes Base64 ou SPKI, l’option 3 pourra etre ajoutee ensuite sans remettre en cause cette base.