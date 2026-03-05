/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Utility: CryptoApiClient
 * Description: REST client for Crypto API operations (key generation, signing, token generation)
 */

package org.ezkey.tests.util;

import static io.restassured.RestAssured.given;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
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
 * <p><b>RestAssured base URL:</b> Each method calls {@link
 * RestAssuredTestConfig#configureForCryptoApi}, so RestAssured is left pointing at the Crypto API.
 * If the test then performs Auth API or Admin API requests (e.g. POST /enrollments/verify, POST
 * /auth-attempts), it must call {@link RestAssuredTestConfig#configureForAuthApi} or {@link
 * RestAssuredTestConfig#configureForAdminApi} before those requests; otherwise they will be sent to
 * the Crypto API and fail (e.g. 500 / "No static resource").
 *
 * <p>Supported operations:
 *
 * <ul>
 *   <li>Generate EC P-256 key pairs for device simulation
 *   <li>Generate proof tokens
 *   <li>Sign data with private keys (ECDSA-SHA256)
 *   <li>Validate signatures (optional, for testing)
 * </ul>
 *
 * @since 2025
 */
public class CryptoApiClient {

  private static final Logger log = LoggerFactory.getLogger(CryptoApiClient.class);

  private final DockerStackConfig dockerStackConfig;

  /**
   * Creates a new CryptoApiClient.
   *
   * @param dockerStackConfig Docker stack configuration
   */
  public CryptoApiClient(DockerStackConfig dockerStackConfig) {
    this.dockerStackConfig = dockerStackConfig;
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

    RestAssuredTestConfig.configureForCryptoApi(dockerStackConfig);

    Response response =
        given()
            .contentType(ContentType.JSON)
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

    RestAssuredTestConfig.configureForCryptoApi(dockerStackConfig);

    Response response =
        given()
            .contentType(ContentType.JSON)
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
   * <p>Calls POST /api/v1/crypto/sign with data and private key.
   *
   * @param data Data to sign
   * @param privateKey Base64-encoded private key
   * @return Base64-encoded signature
   * @throws RuntimeException if signing fails
   */
  public String signData(String data, String privateKey) {
    log.debug("Signing data with private key");

    RestAssuredTestConfig.configureForCryptoApi(dockerStackConfig);

    Map<String, String> requestBody = new HashMap<>();
    requestBody.put("data", data);
    requestBody.put("privateKey", privateKey);

    Response response =
        given()
            .contentType(ContentType.JSON)
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

    RestAssuredTestConfig.configureForCryptoApi(dockerStackConfig);

    Map<String, String> requestBody = new HashMap<>();
    requestBody.put("plaintext", plaintext);

    Response response =
        given()
            .contentType(ContentType.JSON)
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

    RestAssuredTestConfig.configureForCryptoApi(dockerStackConfig);

    Map<String, String> requestBody = new HashMap<>();
    requestBody.put("data", data);
    requestBody.put("signature", signature);
    requestBody.put("publicKey", publicKey);

    Response response =
        given()
            .contentType(ContentType.JSON)
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
