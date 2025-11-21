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

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.ezkey.tests.config.DockerStackConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;

/**
 * REST client for Crypto API operations.
 *
 * <p>This client provides methods to interact with the Crypto API deployed in Docker for
 * cryptographic operations needed in tests. All operations use the Crypto API REST endpoints, not
 * the ezkey-core crypto module.
 *
 * <p>Supported operations:
 *
 * <ul>
 *   <li>Generate RSA key pairs for device simulation
 *   <li>Generate proof tokens
 *   <li>Sign data with private keys
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
   * Represents an RSA key pair returned by the Crypto API.
   *
   * @param privateKey Base64-encoded private key
   * @param publicKey Base64-encoded public key
   * @param keySize Key size in bits
   */
  public record RsaKeyPair(String privateKey, String publicKey, int keySize) {}

  /**
   * Generates a new RSA key pair using the Crypto API.
   *
   * <p>Calls GET /api/v1/crypto/keypair with optional key size parameter.
   *
   * @param keySize Key size in bits (1024-4096, default: 2048)
   * @return RSA key pair with private key, public key, and key size
   * @throws RuntimeException if key generation fails
   */
  public RsaKeyPair generateKeyPair(int keySize) {
    log.debug("Generating RSA key pair with size: {} bits", keySize);

    RestAssuredTestConfig.configureForCryptoApi(dockerStackConfig);

    Response response =
        given()
            .contentType(ContentType.JSON)
            .queryParam("keySize", keySize)
            .when()
            .get("/keypair")
            .then()
            .statusCode(200)
            .extract()
            .response();

    String privateKey = response.jsonPath().getString("privateKey");
    String publicKey = response.jsonPath().getString("publicKey");
    int actualKeySize = response.jsonPath().getInt("keySize");

    log.debug("Generated RSA key pair with size: {} bits", actualKeySize);

    return new RsaKeyPair(privateKey, publicKey, actualKeySize);
  }

  /**
   * Generates a new RSA key pair with default 2048-bit key size.
   *
   * @return RSA key pair
   */
  public RsaKeyPair generateKeyPair() {
    return generateKeyPair(2048);
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

