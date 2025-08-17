package org.ezkey.demo.device.service;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Cryptographic utilities for the simulated device.
 * <p>
 * Generates RSA keys and performs signing operations compatible with the
 * Ezkey demo flows. This service is intentionally independent from
 * {@code ezkey-core} to keep the demo-device module standalone.
 * </p>
 *
 * @since 2025
 */
@Service
public class DeviceCryptoService {

    private static final Logger logger = LoggerFactory.getLogger(DeviceCryptoService.class);

    private static final String KEY_ALGORITHM = "RSA";

    private static final int KEY_SIZE_BITS = 2048;

    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";

    /**
     * Generates a new RSA-2048 key pair for the device.
     *
     * @return key pair
     */
    public KeyPair generateDeviceKeyPair() {
        try{
            KeyPairGenerator generator = KeyPairGenerator.getInstance(KEY_ALGORITHM);
            generator.initialize(KEY_SIZE_BITS,SecureRandom.getInstanceStrong());
            return generator.generateKeyPair();
        } catch (Exception e){
            logger.error("Failed to generate device key pair",e);
            throw new IllegalStateException("Unable to generate RSA key pair",e);
        }
    }

    /**
     * Signs raw bytes using the provided private key and returns the signature bytes.
     *
     * @param dataToSign bytes to sign
     * @param privateKey RSA private key
     * @return signature bytes
     */
    public byte[] signBytes(byte[] dataToSign,PrivateKey privateKey) {
        Objects.requireNonNull(dataToSign,"dataToSign must not be null");
        Objects.requireNonNull(privateKey,"privateKey must not be null");
        try{
            Signature signer = Signature.getInstance(SIGNATURE_ALGORITHM);
            signer.initSign(privateKey);
            signer.update(dataToSign);
            return signer.sign();
        } catch (Exception e){
            logger.error("Failed to sign data",e);
            throw new IllegalStateException("Signing failure",e);
        }
    }

    /**
     * Signs a UTF-8 string and returns the Base64-encoded signature.
     *
     * @param content string to sign
     * @param privateKey RSA private key
     * @return Base64 signature string
     */
    public String signStringToBase64(String content,PrivateKey privateKey) {
        // Use default charset to match SignatureService implementation
        byte[] signature = signBytes(content.getBytes(StandardCharsets.UTF_8),privateKey);
        return Base64.getEncoder().encodeToString(signature);
    }

    /**
     * Serializes the given public key to Base64 (X.509 DER).
     *
     * @param publicKey key
     * @return Base64-encoded X.509 DER
     */
    public String publicKeyToBase64(PublicKey publicKey) {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    /**
     * Serializes the given private key to Base64 (PKCS#8 DER).
     *
     * @param privateKey key
     * @return Base64-encoded PKCS#8 DER
     */
    public String privateKeyToBase64(PrivateKey privateKey) {
        return Base64.getEncoder().encodeToString(privateKey.getEncoded());
    }

    /**
     * Reconstructs a PublicKey from Base64-encoded X.509 DER.
     *
     * @param base64 base64 encoded key
     * @return public key instance
     */
    public PublicKey base64ToPublicKey(String base64) {
        try{
            byte[] der = Base64.getDecoder().decode(base64);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(der);
            return KeyFactory.getInstance(KEY_ALGORITHM).generatePublic(spec);
        } catch (Exception e){
            throw new IllegalArgumentException("Invalid public key encoding",e);
        }
    }

    /**
     * Reconstructs a PrivateKey from Base64-encoded PKCS#8 DER.
     *
     * @param base64 base64 encoded key
     * @return private key instance
     */
    public PrivateKey base64ToPrivateKey(String base64) {
        try{
            byte[] der = Base64.getDecoder().decode(base64);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);
            return KeyFactory.getInstance(KEY_ALGORITHM).generatePrivate(spec);
        } catch (Exception e){
            throw new IllegalArgumentException("Invalid private key encoding",e);
        }
    }

    /**
     * Builds a simple device proof by concatenating sorted key=value pairs and signing the result.
     * This is deliberately simple for demo use; the server-side verifier only requires a valid
     * device-controlled signature over expected fields.
     *
     * @param claims key/value pairs to include in the proof
     * @param privateKey device private key
     * @return Base64-encoded signature over the canonicalized claims string
     */
    public String buildAndSignDeviceProof(Map<String, Object> claims,PrivateKey privateKey) {
        String canonical = claims.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e -> e.getKey() + "=" + String.valueOf(e.getValue()))
                .reduce((a,b) -> a + "\n" + b).orElse("");
        return signStringToBase64(canonical,privateKey);
    }
}
