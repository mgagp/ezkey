/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Utility: CryptoApiClient
 * Description: REST client for Crypto API operations (key generation, signing, token generation)
 */

package org.ezkey.tests.util;

import static io.restassured.RestAssured.given;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.ObjectMapperConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import io.restassured.mapper.ObjectMapperType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.HashMap;
import java.util.Map;
import org.ezkey.tests.config.DockerStackConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST client for Crypto API operations.
 *
 * <p>This client provides methods to interact with the Crypto API deployed in Docker for
 * cryptographic operations needed in tests. All operations use the Crypto API REST endpoints, not
 * the ezkey-core crypto module.
 *
 * <p><b>Isolation:</b> Uses a dedicated {@link RequestSpecification} so Crypto API calls do
 * <em>not</em> mutate RestAssured static {@code baseURI}/{@code basePath}. Callers that hit Auth
 * API or Admin API keep their own RestAssured configuration.
 *
 * <p>Supported operations:
 *
 * <ul>
 *   <li>Generate EC P-256 key pairs for device simulation
 *   <li>Generate proof tokens
 *   <li>Encrypt plaintext (returns ENC:keyID:Base64 value for test data)
 *   <li>Sign data with private keys (ECDSA-SHA256)
 *   <li>Validate signatures (optional, for testing)
 * </ul>
 *
 * @since 2025
 */
public class CryptoApiClient {

  private static final Logger log = LoggerFactory.getLogger(CryptoApiClient.class);

  private final RequestSpecification cryptoSpec;

  /**
   * Creates a new CryptoApiClient.
   *
   * @param dockerStackConfig Docker stack configuration
   */
  public CryptoApiClient(DockerStackConfig dockerStackConfig) {
    this.cryptoSpec =
        new RequestSpecBuilder()
            .setBaseUri(dockerStackConfig.getCryptoApiUrl())
            .setBasePath("/api/v1/crypto")
            .setContentType(ContentType.JSON)
            .setConfig(
                RestAssuredConfig.config()
                    .objectMapperConfig(
                        ObjectMapperConfig.objectMapperConfig()
                            .defaultObjectMapperType(ObjectMapperType.JACKSON_3)))
            .build();
  }

  /**
   * Represents an EC P-256 key pair returned by the Crypto API.
   *
   * <p>Keys are ASN.1 DER encoded and Base64-encoded for transport: private key in PKCS#8 format
   * (~121 chars Base64), public key in X.509 SubjectPublicKeyInfo format (~88 chars Base64).
   *
   * @param privateKey Base64-encoded EC P-256 private key (PKCS#8 DER format)
   * @param publicKey Base64-encoded EC P-256 public key (X.509 SubjectPublicKeyInfo DER format)
   */
  public record EcP256KeyPair(String privateKey, String publicKey) {}

  /**
   * Generates a new EC P-256 key pair using the Crypto API.
   *
   * <p>Calls GET /api/v1/crypto/keypair. Returns an ECDSA key pair with private key in PKCS#8
   * format and public key in X.509 SubjectPublicKeyInfo format, both Base64-encoded.
   *
   * @return EC P-256 key pair with private key and public key
   * @throws RuntimeException if key generation fails
   */
  public EcP256KeyPair generateKeyPair() {
    log.debug("Generating EC P-256 key pair");

    Response response =
        given()
            .spec(cryptoSpec)
            .when()
            .get("/keypair")
            .then()
            .statusCode(200)
            .extract()
            .response();

    String privateKey = response.jsonPath().getString("privateKey");
    String publicKey = response.jsonPath().getString("publicKey");

    log.debug("Generated EC P-256 key pair (PKCS#8 private, X.509 public)");

    return new EcP256KeyPair(privateKey, publicKey);
  }

  /**
   * Generates a cryptographically secure proof token using the Crypto API.
   *
   * <p>Calls GET /api/v1/crypto/prooftoken.
   *
   * @return Proof token string
   * @throws RuntimeException if token generation fails
   */
  public String generateProofToken() {
    log.debug("Generating proof token");

    Response response =
        given()
            .spec(cryptoSpec)
            .when()
            .get("/prooftoken")
            .then()
            .statusCode(200)
            .extract()
            .response();

    String proofToken = response.jsonPath().getString("proofToken");

    log.debug("Generated proof token");

    return proofToken;
  }

  /**
   * Signs data using a private key via the Crypto API.
   *
   * <p>Calls POST /api/v1/crypto/sign with data and private key. Does not alter RestAssured static
   * base URI/path.
   *
   * @param data Data to sign
   * @param privateKey Base64-encoded private key
   * @return Base64-encoded signature
   * @throws RuntimeException if signing fails
   */
  public String signData(String data, String privateKey) {
    log.debug("Signing data with private key");

    Map<String, String> requestBody = new HashMap<>();
    requestBody.put("data", data);
    requestBody.put("privateKey", privateKey);

    Response response =
        given()
            .spec(cryptoSpec)
            .body(requestBody)
            .when()
            .post("/sign")
            .then()
            .statusCode(200)
            .extract()
            .response();

    String signature = response.jsonPath().getString("signature");

    log.debug("Data signed successfully");

    return signature;
  }

  /**
   * Encrypts plaintext via Crypto API and returns the key ID used for encryption.
   *
   * <p>Calls POST /api/v1/crypto/encrypt. Returns the primary key ID that was used to encrypt the
   * value, or null if encryption failed or is unavailable.
   *
   * @param plaintext plaintext to encrypt
   * @return key ID used for encryption, or null if encryption failed/unavailable
   */
  public Long encryptAndGetKeyId(String plaintext) {
    log.debug("Encrypting plaintext to get key ID");

    Map<String, String> requestBody = new HashMap<>();
    requestBody.put("plaintext", plaintext);

    Response response =
        given()
            .spec(cryptoSpec)
            .body(requestBody)
            .when()
            .post("/encrypt")
            .then()
            .statusCode(200)
            .extract()
            .response();

    Boolean encryptionSuccessful = response.jsonPath().getBoolean("encryptionSuccessful");
    String keyIdStr = response.jsonPath().getString("keyId");

    if (!Boolean.TRUE.equals(encryptionSuccessful) || keyIdStr == null || keyIdStr.isBlank()) {
      log.debug(
          "Encryption failed or keyId not returned: encryptionSuccessful={}, keyId={}",
          encryptionSuccessful,
          keyIdStr);
      return null;
    }

    try {
      return Long.parseUnsignedLong(keyIdStr);
    } catch (NumberFormatException e) {
      log.warn("Failed to parse keyId from Crypto API: {}", keyIdStr, e);
      return null;
    }
  }

  /**
   * Encrypts plaintext via Crypto API and returns the encrypted value string.
   *
   * <p>Calls POST /api/v1/crypto/encrypt. Returns the value in application format {@code
   * ENC:keyID:Base64(ciphertext)}, or null if encryption failed or is unavailable. Use this when
   * tests need to persist encrypted values (e.g. raw SQL INSERT with enrollment_proof_token).
   *
   * @param plaintext plaintext to encrypt
   * @return encrypted value with ENC: prefix, or null if encryption failed/unavailable
   */
  public String encrypt(String plaintext) {
    if (plaintext == null) {
      return null;
    }
    log.debug("Encrypting plaintext (length {})", plaintext.length());

    Map<String, String> requestBody = new HashMap<>();
    requestBody.put("plaintext", plaintext);

    Response response =
        given()
            .spec(cryptoSpec)
            .body(requestBody)
            .when()
            .post("/encrypt")
            .then()
            .statusCode(200)
            .extract()
            .response();

    Boolean encryptionSuccessful = response.jsonPath().getBoolean("encryptionSuccessful");
    String encryptedValue = response.jsonPath().getString("encryptedValue");

    if (!Boolean.TRUE.equals(encryptionSuccessful) || encryptedValue == null) {
      log.debug(
          "Encryption failed or no encryptedValue: encryptionSuccessful={}, hasValue={}",
          encryptionSuccessful,
          encryptedValue != null);
      return null;
    }
    if (!encryptedValue.startsWith("ENC:")) {
      log.debug("Encryption returned plaintext (no ENC: prefix), encryption likely unavailable");
      return null;
    }
    return encryptedValue;
  }

  /**
   * Validates a signature against data and public key using the Crypto API.
   *
   * <p>Calls POST /api/v1/crypto/validate with data, signature, and public key.
   *
   * @param data Original data
   * @param signature Base64-encoded signature
   * @param publicKey Base64-encoded public key
   * @return true if signature is valid, false otherwise
   * @throws RuntimeException if validation fails
   */
  public boolean validateSignature(String data, String signature, String publicKey) {
    log.debug("Validating signature");

    Map<String, String> requestBody = new HashMap<>();
    requestBody.put("data", data);
    requestBody.put("signature", signature);
    requestBody.put("publicKey", publicKey);

    Response response =
        given()
            .spec(cryptoSpec)
            .body(requestBody)
            .when()
            .post("/validate")
            .then()
            .statusCode(200)
            .extract()
            .response();

    boolean isValid = response.jsonPath().getBoolean("valid");

    log.debug("Signature validation result: {}", isValid);

    return isValid;
  }
}
