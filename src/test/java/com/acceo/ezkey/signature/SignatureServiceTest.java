package com.acceo.ezkey.signature;

import org.ezkey.signature.SignatureService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

public class SignatureServiceTest {
    @Test
    public void testKeyPairAndSignatureValidation() throws Exception {
        // Génération d'une paire de clés RSA
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();

        String base64PrivateKey = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        String base64PublicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());

        String data = "Test de signature";
        SignatureService signatureService = new SignatureService();
        String signature = signatureService.generateSignature(data, base64PrivateKey);

        boolean isValid = signatureService.validateSignature(data, signature, base64PublicKey);
        Assertions.assertTrue(isValid, "La signature doit être valide");

        // Test avec des données modifiées
        boolean isInvalid = signatureService.validateSignature("autre data", signature, base64PublicKey);
        Assertions.assertFalse(isInvalid, "La signature ne doit pas être valide pour des données modifiées");
    }
}
