import java.security.*;
import java.security.spec.*;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

public class TestSignature {
    public static void main(String[] args) {
        try {
            System.out.println("🚀 Testing Ezkey Cryptographic Compatibility");
            System.out.println("📱 Platform: Java (Reference Implementation)");
            System.out.println("🔐 Algorithm: RSA-2048 with SHA256withRSA");
            System.out.println("============================================================");
            
            // Generate RSA key pair
            System.out.println("\n📋 TEST 1: RSA Key Pair Generation");
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            KeyPair keyPair = keyGen.generateKeyPair();
            
            String privateKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
            String publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
            
            System.out.println("✅ Key pair generated successfully");
            System.out.println("📏 Private key length: " + privateKeyBase64.length() + " chars");
            System.out.println("📏 Public key length: " + publicKeyBase64.length() + " chars");
            
            // Generate proof token
            System.out.println("\n📋 TEST 2: Proof Token Generation");
            SecureRandom secureRandom = new SecureRandom();
            byte[] randomBytes = new byte[32];
            secureRandom.nextBytes(randomBytes);
            long timestamp = System.currentTimeMillis();
            byte[] salt = new byte[16];
            secureRandom.nextBytes(salt);
            
            String randomPart = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
            String saltPart = Base64.getUrlEncoder().withoutPadding().encodeToString(salt);
            String proofToken = randomPart + "." + timestamp + "." + saltPart;
            
            System.out.println("✅ Proof token generated: " + proofToken);
            
            // Generate signature
            System.out.println("\n📋 TEST 3: Digital Signature Creation");
            byte[] keyBytes = Base64.getDecoder().decode(privateKeyBase64);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PrivateKey privateKey = kf.generatePrivate(spec);
            
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(proofToken.getBytes(StandardCharsets.UTF_8));
            byte[] signed = signature.sign();
            String signatureBase64 = Base64.getEncoder().encodeToString(signed);
            
            System.out.println("✅ Signature generated successfully");
            System.out.println("📏 Signature length: " + signatureBase64.length() + " chars");
            
            // Validate signature
            System.out.println("\n📋 TEST 4: Signature Validation");
            byte[] pubKeyBytes = Base64.getDecoder().decode(publicKeyBase64);
            X509EncodedKeySpec pubSpec = new X509EncodedKeySpec(pubKeyBytes);
            PublicKey publicKey = kf.generatePublic(pubSpec);
            
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(publicKey);
            verifier.update(proofToken.getBytes(StandardCharsets.UTF_8));
            byte[] signatureBytes = Base64.getDecoder().decode(signatureBase64);
            boolean isValid = verifier.verify(signatureBytes);
            
            if (isValid) {
                System.out.println("✅ Signature validation: PASSED");
                System.out.println("🎯 Cryptographic integrity confirmed");
            } else {
                System.out.println("❌ Signature validation: FAILED");
                System.exit(1);
            }
            
            System.out.println("\n============================================================");
            System.out.println("✅ ALL JAVA REFERENCE TESTS COMPLETED SUCCESSFULLY!");
            System.out.println("🔒 Implementation ready for Android Kotlin translation");
            
        } catch (Exception e) {
            System.out.println("❌ ERROR: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
