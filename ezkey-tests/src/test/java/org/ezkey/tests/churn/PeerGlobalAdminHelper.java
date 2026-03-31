/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * One-shot provisioning of a peer Global Admin for operational churn (POST /admins/global + MFA enrollment).
 */

package org.ezkey.tests.churn;

import static io.restassured.RestAssured.given;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.HashMap;
import java.util.Map;
import org.ezkey.tests.config.DockerStackConfig;
import org.ezkey.tests.util.CryptoApiClient;
import org.ezkey.tests.util.CryptoApiClient.EcP256KeyPair;
import org.ezkey.tests.util.RestAssuredTestConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates a peer {@code GLOBAL_ADMIN} via {@code POST /api/v1/admins/global} using the bootstrap
 * global token, then completes passwordless enrollment and returns a bearer token.
 *
 * <p>Flow mirrors {@link org.ezkey.tests.util.TenantAdminTestHelper} but for {@code /admins/global}
 * and without persisting tenant-scoped cache files.
 */
public final class PeerGlobalAdminHelper {

  private static final Logger log = LoggerFactory.getLogger(PeerGlobalAdminHelper.class);

  /** Fixed username (short) so enrollment display name stays within VARCHAR(64). */
  public static final String CHURN_GLOBAL_USERNAME = "churnglo";

  private final DockerStackConfig dockerStackConfig;
  private final CryptoApiClient cryptoApiClient;

  public PeerGlobalAdminHelper(
      DockerStackConfig dockerStackConfig, CryptoApiClient cryptoApiClient) {
    this.dockerStackConfig = dockerStackConfig;
    this.cryptoApiClient = cryptoApiClient;
  }

  /**
   * Creates peer global admin and returns bearer token, or throws if the API rejects the request.
   *
   * @param bootstrapGlobalToken bearer token of the bootstrap global admin
   * @return operational churn global admin bearer token
   */
  public String createPeerGlobalAdminAndObtainToken(String bootstrapGlobalToken) {
    log.info("Provisioning peer Global Admin for operational churn: {}", CHURN_GLOBAL_USERNAME);

    RestAssuredTestConfig.configureForAdminApi(dockerStackConfig);

    Map<String, Object> createRequest = new HashMap<>();
    createRequest.put("username", CHURN_GLOBAL_USERNAME);
    createRequest.put("email", CHURN_GLOBAL_USERNAME + "@example.com");
    createRequest.put("firstName", "Test");
    createRequest.put("lastName", "Ops");

    Response createResponse =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + bootstrapGlobalToken)
            .body(createRequest)
            .when()
            .post("/admins/global")
            .then()
            .extract()
            .response();

    int status = createResponse.getStatusCode();
    if (status != 201) {
      String body = createResponse.asString();
      if (status == 409) {
        throw new IllegalStateException(
            "Peer Global Admin username already exists: "
                + CHURN_GLOBAL_USERNAME
                + ". Remove that admin from the database or delete"
                + " .ezkey-test/operational-churn-global-admin.json if you are re-initializing."
                + " Response: "
                + body);
      }
      throw new IllegalStateException(
          "POST /admins/global failed with status "
              + status
              + " (e.g. max global admins reached). Response: "
              + body);
    }

    Integer adminId = createResponse.jsonPath().getInt("adminId");
    log.info("Created peer Global Admin with ID: {}", adminId);

    Response onboardingResponse =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + bootstrapGlobalToken)
            .when()
            .get("/admins/" + adminId + "/onboarding")
            .then()
            .statusCode(200)
            .extract()
            .response();

    Integer enrollmentId = onboardingResponse.jsonPath().getInt("enrollmentId");
    String enrollmentProofToken = onboardingResponse.jsonPath().getString("enrollmentProofToken");
    Integer enrollmentChallenge = onboardingResponse.jsonPath().getInt("enrollmentChallenge");

    EcP256KeyPair keyPair = cryptoApiClient.generateKeyPair();
    RestAssuredTestConfig.configureForAuthApi(dockerStackConfig);

    Map<String, Object> bindRequest = new HashMap<>();
    bindRequest.put("enrollmentId", enrollmentId);
    bindRequest.put("enrollmentProofToken", enrollmentProofToken);
    bindRequest.put("devicePublicKey", keyPair.publicKey());

    Response bindResponse =
        given()
            .contentType(ContentType.JSON)
            .body(bindRequest)
            .when()
            .post("/enrollments/bind")
            .then()
            .statusCode(200)
            .extract()
            .response();

    String bindProofToken = bindResponse.jsonPath().getString("enrollmentProofToken");
    String bindSignature = cryptoApiClient.signData(bindProofToken, keyPair.privateKey());

    RestAssuredTestConfig.configureForAuthApi(dockerStackConfig);

    Map<String, Object> verifyRequest = new HashMap<>();
    verifyRequest.put("enrollmentId", enrollmentId);
    verifyRequest.put("challengeResponse", enrollmentChallenge);
    verifyRequest.put("devicePublicKey", keyPair.publicKey());
    verifyRequest.put("enrollmentProofTokenSigned", bindSignature);

    given()
        .contentType(ContentType.JSON)
        .body(verifyRequest)
        .when()
        .post("/enrollments/verify")
        .then()
        .statusCode(200);

    DeviceCredentials creds =
        new DeviceCredentials(enrollmentId, CHURN_GLOBAL_USERNAME, enrollmentProofToken, keyPair);

    return completePasswordlessLoginAndObtainToken(creds);
  }

  private record DeviceCredentials(
      Integer enrollmentId, String username, String enrollmentProofToken, EcP256KeyPair keyPair) {}

  private String completePasswordlessLoginAndObtainToken(DeviceCredentials deviceCredentials) {
    String username = deviceCredentials.username();
    Integer enrollmentId = deviceCredentials.enrollmentId();
    EcP256KeyPair keyPair = deviceCredentials.keyPair();

    RestAssuredTestConfig.configureForAdminApi(dockerStackConfig);

    Map<String, Object> loginRequest = new HashMap<>();
    loginRequest.put("username", username);
    loginRequest.put("challengeRequested", true);

    Response loginResponse =
        given()
            .contentType(ContentType.JSON)
            .body(loginRequest)
            .when()
            .post("/admin/auth/login")
            .then()
            .statusCode(200)
            .extract()
            .response();

    Integer authAttemptId = loginResponse.jsonPath().getInt("authAttemptId");
    Integer challengeCode = loginResponse.jsonPath().getInt("challengeCode");

    String deviceProofToken = cryptoApiClient.generateProofToken();
    String deviceProofTokenSigned =
        cryptoApiClient.signData(deviceProofToken, keyPair.privateKey());

    RestAssuredTestConfig.configureForAuthApi(dockerStackConfig);

    Map<String, Object> pendingRequest = new HashMap<>();
    pendingRequest.put("enrollmentId", enrollmentId);
    pendingRequest.put("enrollmentProofToken", deviceCredentials.enrollmentProofToken());
    pendingRequest.put("deviceProofToken", deviceProofToken);
    pendingRequest.put("deviceProofTokenSigned", deviceProofTokenSigned);

    Response pendingResponse =
        given()
            .contentType(ContentType.JSON)
            .body(pendingRequest)
            .when()
            .post("/auth-attempts/pending")
            .then()
            .statusCode(200)
            .extract()
            .response();

    String authAttemptProofToken = pendingResponse.jsonPath().getString("authAttemptProofToken");

    boolean accepted = true;
    String respondPayload = authAttemptProofToken + "|" + (accepted ? "true" : "false");
    String authAttemptProofTokenSignedByDevice =
        cryptoApiClient.signData(respondPayload, keyPair.privateKey());

    RestAssuredTestConfig.configureForAuthApi(dockerStackConfig);

    Map<String, Object> respondRequest = new HashMap<>();
    respondRequest.put("authAttemptId", authAttemptId);
    respondRequest.put("authAttemptAccepted", true);
    respondRequest.put("authAttemptProofTokenSignedByDevice", authAttemptProofTokenSignedByDevice);
    if (challengeCode != null) {
      respondRequest.put("authAttemptChallengeResponse", challengeCode);
    }

    given()
        .contentType(ContentType.JSON)
        .body(respondRequest)
        .when()
        .post("/auth-attempts/respond")
        .then()
        .statusCode(200);

    RestAssuredTestConfig.configureForAdminApi(dockerStackConfig);

    Map<String, Object> waitRequest = new HashMap<>();
    waitRequest.put("authAttemptId", authAttemptId);
    waitRequest.put("challengeCode", challengeCode);

    Response tokenResponse =
        given()
            .contentType(ContentType.JSON)
            .body(waitRequest)
            .when()
            .post("/admin/auth/passwordless-wait")
            .then()
            .statusCode(200)
            .extract()
            .response();

    String token = tokenResponse.jsonPath().getString("token");
    log.info("Peer Global Admin passwordless login complete for {}", username);
    return token;
  }
}
