/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Utility: TenantAdminTestHelper
 * Description: Helper for creating TenantAdmin with full device simulation and authentication
 */

package org.ezkey.tests.util;

import static io.restassured.RestAssured.given;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import org.ezkey.tests.config.DockerStackConfig;
import org.ezkey.tests.util.CryptoApiClient.EcP256KeyPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Helper class for creating TenantAdmin with full device simulation and authentication.
 *
 * <p>This helper automates the complete TenantAdmin enrollment and login flow:
 *
 * <ol>
 *   <li>Create TenantAdmin record via Admin API
 *   <li>Retrieve onboarding credentials (enrollmentId, enrollmentProofToken, enrollmentChallenge)
 *   <li>Generate device key pair via Crypto API
 *   <li>Bind device to enrollment via Auth API
 *   <li>Verify enrollment with signature
 *   <li>Login and obtain TenantAdmin token
 *   <li>Cache device credentials and token for reuse
 * </ol>
 *
 * <p>Uses a 3-tier strategy similar to GlobalAdmin:
 *
 * <ol>
 *   <li>Tier 1: Reuse cached token (if valid)
 *   <li>Tier 2: Reuse device credentials to create new token
 *   <li>Tier 3: Perform full device simulation and enrollment
 * </ol>
 *
 * @since 2025
 */
public class TenantAdminTestHelper {

  private static final Logger log = LoggerFactory.getLogger(TenantAdminTestHelper.class);

  private static final String STATE_DIR = System.getProperty("ezkey.test.state.dir", ".ezkey-test");

  private static final String TENANT_ADMIN_DEVICE_CREDENTIALS_FILE_PATTERN =
      STATE_DIR + "/tenant-admin-%d-device-credentials.json";
  private static final String TENANT_ADMIN_TOKEN_FILE_PATTERN =
      STATE_DIR + "/tenant-admin-%d-token.json";

  private final DockerStackConfig dockerStackConfig;
  private final CryptoApiClient cryptoApiClient;

  // Synchronization lock to prevent parallel enrollment for same tenant
  private static final ReentrantLock enrollmentLock = new ReentrantLock();

  /**
   * Represents device credentials saved after enrollment.
   *
   * @param enrollmentId Enrollment ID
   * @param adminId Admin ID
   * @param username Admin username
   * @param privateKey Base64-encoded EC P-256 device private key (PKCS#8 DER format)
   * @param publicKey Base64-encoded EC P-256 device public key (X.509 SubjectPublicKeyInfo DER
   *     format)
   */
  private record DeviceCredentials(
      Integer enrollmentId,
      Integer adminId,
      String username,
      String enrollmentProofToken,
      String privateKey,
      String publicKey) {}

  /**
   * Creates a new TenantAdminTestHelper.
   *
   * @param dockerStackConfig Docker stack configuration
   * @param testDataFactory Test data factory
   * @param cryptoApiClient Crypto API client
   */
  public TenantAdminTestHelper(
      DockerStackConfig dockerStackConfig,
      TestDataFactory testDataFactory,
      CryptoApiClient cryptoApiClient) {
    this.dockerStackConfig = dockerStackConfig;
    this.testDataFactory = testDataFactory;
    this.cryptoApiClient = cryptoApiClient;
  }

  /**
   * Creates and logs in a TenantAdmin, returning a ready-to-use bearer token.
   *
   * <p>Follows 3-tier strategy:
   *
   * <ol>
   *   <li>Check cached token (validated)
   *   <li>Check device credentials, create new token if available
   *   <li>Perform full enrollment and create token
   * </ol>
   *
   * @param username Admin username (must be unique)
   * @param tenantId Tenant ID
   * @param globalAdminToken Global admin token for creating admin record
   * @return TenantAdmin bearer token ready to use
   * @throws IllegalStateException if token creation fails
   */
  public String createAndLoginTenantAdmin(
      String username, Integer tenantId, String globalAdminToken) {
    log.info("Creating and logging in TenantAdmin: {} for tenant: {}", username, tenantId);

    // Tier 1: Try cached token
    String cachedToken = loadTokenFromFile(tenantId);
    if (cachedToken != null && !cachedToken.isEmpty() && isTokenValid(cachedToken)) {
      log.info("✅ Using validated cached token for tenant: {}", tenantId);
      return cachedToken;
    }

    // Tier 2: Try device credentials
    DeviceCredentials deviceCredentials = loadDeviceCredentials(tenantId);
    if (deviceCredentials != null) {
      log.info("✅ Device credentials found for tenant: {}, creating new token", tenantId);
      try {
        String token =
            createTokenWithDeviceCredentials(
                deviceCredentials.username(), deviceCredentials.enrollmentId(), deviceCredentials);
        saveTokenToFile(token, tenantId);
        return token;
      } catch (Exception e) {
        log.warn(
            "Failed to create token with device credentials: {}, will perform full enrollment",
            e.getMessage());
        // Fall through to Tier 3
      }
    }

    // Tier 3: Full enrollment
    enrollmentLock.lock();
    try {
      // Double-check device credentials (another thread might have created them)
      deviceCredentials = loadDeviceCredentials(tenantId);
      if (deviceCredentials != null) {
        log.info("Device credentials created by another thread, reusing");
        String token =
            createTokenWithDeviceCredentials(
                deviceCredentials.username(), deviceCredentials.enrollmentId(), deviceCredentials);
        saveTokenToFile(token, tenantId);
        return token;
      }

      return performFullEnrollmentAndLogin(username, tenantId, globalAdminToken);
    } finally {
      enrollmentLock.unlock();
    }
  }

  /**
   * Performs full enrollment and login flow.
   *
   * @param username Admin username
   * @param tenantId Tenant ID
   * @param globalAdminToken Global admin token
   * @return TenantAdmin bearer token
   */
  private String performFullEnrollmentAndLogin(
      String username, Integer tenantId, String globalAdminToken) {
    log.info("Performing full enrollment for TenantAdmin: {}", username);

    // Step 1: Create TenantAdmin record
    RestAssuredTestConfig.configureForAdminApi(dockerStackConfig);

    Map<String, Object> request = new HashMap<>();
    request.put("username", username);
    request.put("email", username + "@example.com");
    request.put("firstName", "Test");
    request.put("lastName", "TenantAdmin");
    request.put("tenantId", tenantId);

    Response createResponse =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + globalAdminToken)
            .body(request)
            .when()
            .post("/admins/tenant")
            .then()
            .statusCode(201)
            .extract()
            .response();

    Integer adminId = createResponse.jsonPath().getInt("adminId");
    log.info("Created TenantAdmin with ID: {}", adminId);

    // Step 2: Get onboarding credentials
    Response onboardingResponse =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + globalAdminToken)
            .when()
            .get("/admins/" + adminId + "/onboarding")
            .then()
            .statusCode(200)
            .extract()
            .response();

    Integer enrollmentId = onboardingResponse.jsonPath().getInt("enrollmentId");
    String enrollmentProofToken = onboardingResponse.jsonPath().getString("enrollmentProofToken");
    Integer enrollmentChallenge = onboardingResponse.jsonPath().getInt("enrollmentChallenge");

    log.info("Retrieved onboarding credentials for enrollment: {}", enrollmentId);

    // Step 3: Generate device key pair
    EcP256KeyPair keyPair = cryptoApiClient.generateKeyPair();
    log.info("Generated device key pair");

    // Step 4: Bind device to enrollment
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

    // Note: The field is called "enrollmentProofToken" in the response, not
    // "bindProofToken"
    String bindProofToken = bindResponse.jsonPath().getString("enrollmentProofToken");
    log.info("Device bound successfully");

    // Step 5: Sign canonical enrollment verify payload
    String verifyPayload =
        EnrollmentVerifyDevicePayload.build(
            bindProofToken, enrollmentId, enrollmentChallenge, keyPair.publicKey());
    String bindSignature = cryptoApiClient.signData(verifyPayload, keyPair.privateKey());

    // Step 6: Verify enrollment
    RestAssuredTestConfig.configureForAuthApi(dockerStackConfig);

    Map<String, Object> verifyRequest = new HashMap<>();
    verifyRequest.put("enrollmentId", enrollmentId);
    verifyRequest.put("challengeResponse", enrollmentChallenge);
    verifyRequest.put("devicePublicKey", keyPair.publicKey());
    verifyRequest.put("enrollmentProofTokenSigned", bindSignature);

    Response verifyResponse =
        given()
            .contentType(ContentType.JSON)
            .body(verifyRequest)
            .when()
            .post("/enrollments/verify")
            .then()
            .extract()
            .response();

    // Handle 409 CONFLICT - enrollment with same name already VERIFIED
    // This can occur when tests are re-executed with persistent enrollment data (idempotence)
    if (verifyResponse.getStatusCode() == 409) {
      String responseBody = verifyResponse.getBody().asString();
      if (responseBody != null
          && (responseBody.contains("verified enrollment with the same name")
              || responseBody.contains("recovery process"))) {
        log.warn(
            "Enrollment verification rejected (409): A VERIFIED enrollment with the same name"
                + " already exists. This can occur when tests are re-executed with persistent"
                + " enrollment data. Checking if current enrollment is already VERIFIED"
                + " (idempotence check)...");

        // Check if current enrollment is already VERIFIED
        // This supports test idempotence: if enrollment is already verified, reuse it
        org.ezkey.tests.util.DatabaseHelper databaseHelper =
            new org.ezkey.tests.util.DatabaseHelper();
        String enrollmentStatus = databaseHelper.getEnrollmentStatus(enrollmentId);
        log.info("Current enrollment {} status: {}", enrollmentId, enrollmentStatus);

        if ("VERIFIED".equals(enrollmentStatus)) {
          log.info(
              "Current enrollment {} is already VERIFIED - Reusing existing verified enrollment "
                  + "(idempotence: test can be re-executed safely). Username: {}, TenantId: {}",
              enrollmentId,
              username,
              tenantId);
          // Enrollment is already verified, continue with saving credentials
          // This supports test idempotence: tests can be re-executed with persistent data
        } else {
          log.error(
              "Enrollment verification failed: A VERIFIED enrollment with the same name exists, but"
                  + " current enrollment {} is in {} state. This indicates a duplicate enrollment"
                  + " name conflict despite username uniqueness. Username: {}, TenantId: {}. This"
                  + " should not occur if usernames are unique. Please check if enrollment name"
                  + " generation includes username correctly.",
              enrollmentId,
              enrollmentStatus,
              username,
              tenantId);
          throw new IllegalStateException(
              "Enrollment verification failed: A VERIFIED enrollment with the same name already"
                  + " exists, but current enrollment is not VERIFIED. This indicates a conflict."
                  + " Enrollment ID: "
                  + enrollmentId
                  + ", Status: "
                  + enrollmentStatus
                  + ", Username: "
                  + username
                  + ". Please ensure each test uses a unique username.");
        }
      } else {
        // Other 409 error (not related to uniqueness)
        log.error(
            "Enrollment verification failed with 409: {}", verifyResponse.getBody().asString());
        throw new IllegalStateException(
            "Enrollment verification failed: " + verifyResponse.getBody().asString());
      }
    } else if (verifyResponse.getStatusCode() != 200) {
      log.error(
          "Enrollment verification failed with status {}: {}",
          verifyResponse.getStatusCode(),
          verifyResponse.getBody().asString());
      throw new IllegalStateException(
          "Enrollment verification failed: "
              + verifyResponse.getStatusCode()
              + " - "
              + verifyResponse.getBody().asString());
    }

    log.info("Enrollment verified successfully");

    // Step 7: Save device credentials
    DeviceCredentials deviceCredentials =
        new DeviceCredentials(
            enrollmentId,
            adminId,
            username,
            enrollmentProofToken,
            keyPair.privateKey(),
            keyPair.publicKey());
    saveDeviceCredentials(deviceCredentials, tenantId);

    // Step 8: Login and get token
    String token = createTokenWithDeviceCredentials(username, enrollmentId, deviceCredentials);
    saveTokenToFile(token, tenantId);

    log.info("✅ TenantAdmin fully enrolled and logged in");

    return token;
  }

  /**
   * Creates token using existing device credentials.
   *
   * @param username Admin username
   * @param enrollmentId Enrollment ID
   * @param deviceCredentials Device credentials
   * @return Bearer token
   */
  private String createTokenWithDeviceCredentials(
      String username, Integer enrollmentId, DeviceCredentials deviceCredentials) {
    log.info("🔐 STEP 1: Login to create auth attempt for TenantAdmin: {}", username);

    // Step 1: Login (create auth attempt)
    RestAssuredTestConfig.configureForAdminApi(dockerStackConfig);

    Map<String, Object> loginRequest = new HashMap<>();
    loginRequest.put("username", username);
    loginRequest.put("challengeRequested", true); // <-- CHANGED: Need TWO-CALL mode!

    log.info("📤 Sending login request: {}", loginRequest);
    Response loginResponse =
        given()
            .contentType(ContentType.JSON)
            .body(loginRequest)
            .when()
            .post("/admin/auth/login")
            .then()
            .statusCode(200) // <-- Pending responses return 200, not 202
            .extract()
            .response();

    Integer authAttemptId = loginResponse.jsonPath().getInt("authAttemptId");
    Integer challengeCode = loginResponse.jsonPath().getInt("challengeCode");
    log.info("✅ Auth attempt created with ID: {}, challengeCode: {}", authAttemptId, challengeCode);

    // Step 2: Generate device proof token
    log.info("🔐 STEP 2: Generating device proof token");
    String deviceProofToken = cryptoApiClient.generateProofToken();
    log.info("✅ Device proof token generated");

    log.info("🔐 STEP 3: Signing device proof token");
    String deviceProofTokenSigned =
        cryptoApiClient.signData(deviceProofToken, deviceCredentials.privateKey());
    log.info("✅ Device proof token signed");

    // Step 3: Call pending to get auth attempt proof token
    log.info("🔐 STEP 4: Calling /auth-attempts/pending on Auth API");
    RestAssuredTestConfig.configureForAuthApi(dockerStackConfig);

    Map<String, Object> pendingRequest = new HashMap<>();
    pendingRequest.put("enrollmentId", enrollmentId);
    pendingRequest.put("enrollmentProofToken", deviceCredentials.enrollmentProofToken());
    pendingRequest.put("deviceProofToken", deviceProofToken);
    pendingRequest.put("deviceProofTokenSigned", deviceProofTokenSigned);

    log.info("📤 Sending pending request: enrollmentId={}, tokens=***", enrollmentId);
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
    log.info("✅ Auth attempt proof token received");

    // Step 4: Sign canonical respond payload (proofToken|accepted) per
    // AUTH_ATTEMPT_SIGNATURE_PAYLOAD
    log.info("🔐 STEP 5: Signing auth attempt respond payload (proofToken|accepted)");
    boolean accepted = true;
    String respondPayload = authAttemptProofToken + "|" + (accepted ? "true" : "false");
    String authAttemptProofTokenSignedByDevice =
        cryptoApiClient.signData(respondPayload, deviceCredentials.privateKey());
    log.info("✅ Auth attempt respond payload signed");

    // Step 5: Respond to auth attempt
    log.info("🔐 STEP 6: Calling /auth-attempts/respond on Auth API");
    RestAssuredTestConfig.configureForAuthApi(dockerStackConfig);

    Map<String, Object> respondRequest = new HashMap<>();
    respondRequest.put("authAttemptId", authAttemptId);
    respondRequest.put("authAttemptAccepted", true);
    respondRequest.put("authAttemptProofTokenSignedByDevice", authAttemptProofTokenSignedByDevice);
    if (challengeCode != null) {
      respondRequest.put("authAttemptChallengeResponse", challengeCode);
    }

    log.info("📤 Sending respond request: authAttemptId={}, accepted=true", authAttemptId);
    given()
        .contentType(ContentType.JSON)
        .body(respondRequest)
        .when()
        .post("/auth-attempts/respond")
        .then()
        .statusCode(200);

    log.info("✅ Auth attempt responded successfully");

    // Step 6: Wait for token (with timeout to avoid 2-minute hang)
    log.info("🔐 STEP 7: Calling /admin/auth/passwordless-wait to get token");
    RestAssuredTestConfig.configureForAdminApi(dockerStackConfig);

    Map<String, Object> waitRequest = new HashMap<>();
    waitRequest.put("authAttemptId", authAttemptId);
    waitRequest.put("challengeCode", challengeCode);

    log.info(
        "📤 Waiting for token with authAttemptId={}, challengeCode={}",
        authAttemptId,
        challengeCode);

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
    log.info("✅ Token created successfully");

    return token;
  }

  /**
   * Validates a token by making a test request.
   *
   * @param token Bearer token
   * @return true if valid, false otherwise
   */
  private boolean isTokenValid(String token) {
    try {
      RestAssuredTestConfig.configureForAdminApi(dockerStackConfig);

      Response response =
          given()
              .contentType(ContentType.JSON)
              .header("Authorization", "Bearer " + token)
              .when()
              .get("/integrations")
              .then()
              .extract()
              .response();

      return response.getStatusCode() == 200;
    } catch (Exception e) {
      log.debug("Token validation failed: {}", e.getMessage());
      return false;
    }
  }

  /**
   * Loads device credentials from file.
   *
   * @param tenantId Tenant ID
   * @return Device credentials or null if not found
   */
  private DeviceCredentials loadDeviceCredentials(Integer tenantId) {
    Path credentialsPath =
        Path.of(TENANT_ADMIN_DEVICE_CREDENTIALS_FILE_PATTERN.formatted(tenantId));

    if (!Files.exists(credentialsPath)) {
      return null;
    }

    try {
      ObjectMapper mapper = new ObjectMapper();
      ObjectNode jsonNode = (ObjectNode) mapper.readTree(credentialsPath.toFile());

      log.info("=== DEVICE CREDENTIALS JSON ===\n{}", jsonNode.toPrettyString());
      log.info(
          "enrollmentId: {}, adminId: {}, username: {}, enrollmentProofToken: {}, privateKey: {},"
              + " publicKey: {}",
          jsonNode.get("enrollmentId"),
          jsonNode.get("adminId"),
          jsonNode.get("username"),
          jsonNode.get("enrollmentProofToken"),
          jsonNode.get("privateKey"),
          jsonNode.get("publicKey"));

      return new DeviceCredentials(
          jsonNode.get("enrollmentId").asInt(),
          jsonNode.get("adminId").asInt(),
          jsonNode.get("username").asString(),
          jsonNode.get("enrollmentProofToken").asString(),
          jsonNode.get("privateKey").asString(),
          jsonNode.get("publicKey").asString());
    } catch (Exception e) {
      log.warn("Failed to load device credentials: {}", e.getMessage());
      return null;
    }
  }

  /**
   * Saves device credentials to file.
   *
   * @param credentials Device credentials
   * @param tenantId Tenant ID
   */
  private void saveDeviceCredentials(DeviceCredentials credentials, Integer tenantId) {
    Path credentialsPath =
        Path.of(TENANT_ADMIN_DEVICE_CREDENTIALS_FILE_PATTERN.formatted(tenantId));

    try {
      Files.createDirectories(credentialsPath.getParent());

      ObjectMapper mapper = new ObjectMapper();
      ObjectNode jsonNode = mapper.createObjectNode();
      jsonNode.put("enrollmentId", credentials.enrollmentId());
      jsonNode.put("adminId", credentials.adminId());
      jsonNode.put("username", credentials.username());
      jsonNode.put("enrollmentProofToken", credentials.enrollmentProofToken());
      jsonNode.put("privateKey", credentials.privateKey());
      jsonNode.put("publicKey", credentials.publicKey());

      mapper.writerWithDefaultPrettyPrinter().writeValue(credentialsPath.toFile(), jsonNode);
      log.debug("Device credentials saved for tenant: {}", tenantId);
    } catch (IOException e) {
      log.warn("Failed to save device credentials: {}", e.getMessage());
    }
  }

  /**
   * Loads token from file.
   *
   * @param tenantId Tenant ID
   * @return Token or null if not found
   */
  private String loadTokenFromFile(Integer tenantId) {
    Path tokenPath = Path.of(TENANT_ADMIN_TOKEN_FILE_PATTERN.formatted(tenantId));

    if (!Files.exists(tokenPath)) {
      return null;
    }

    try {
      ObjectMapper mapper = new ObjectMapper();
      ObjectNode jsonNode = (ObjectNode) mapper.readTree(tokenPath.toFile());
      return jsonNode.get("token").asString();
    } catch (Exception e) {
      log.warn("Failed to load token: {}", e.getMessage());
      return null;
    }
  }

  /**
   * Saves token to file.
   *
   * @param token Bearer token
   * @param tenantId Tenant ID
   */
  private void saveTokenToFile(String token, Integer tenantId) {
    Path tokenPath = Path.of(TENANT_ADMIN_TOKEN_FILE_PATTERN.formatted(tenantId));

    try {
      Files.createDirectories(tokenPath.getParent());

      ObjectMapper mapper = new ObjectMapper();
      ObjectNode jsonNode = mapper.createObjectNode();
      jsonNode.put("token", token);

      mapper.writerWithDefaultPrettyPrinter().writeValue(tokenPath.toFile(), jsonNode);
      log.debug("Token saved for tenant: {}", tenantId);
    } catch (IOException e) {
      log.warn("Failed to save token: {}", e.getMessage());
    }
  }
}
