/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Utility: AdminBootstrapService
 * Description: Service for bootstrapping admin enrollment and obtaining admin token
 */

package org.ezkey.tests.util;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import org.ezkey.tests.config.DockerStackConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for bootstrapping admin enrollment and obtaining admin token.
 *
 * <p>This service automates the admin enrollment flow in two phases:
 *
 * <p><b>Phase 1: Initial Bootstrap (one-time)</b>
 *
 * <ol>
 *   <li>Extract bootstrap credentials from Docker logs
 *   <li>Generate device key pair via Crypto API
 *   <li>Bind device to enrollment via Auth API
 *   <li>Verify enrollment via Auth API (with challenge code)
 *   <li>Persist device credentials for reuse
 * </ol>
 *
 * <p><b>Phase 2: Token Creation (reusable)</b>
 *
 * <ol>
 *   <li>Load device credentials from cache
 *   <li>Login admin via Admin API (creates auth attempt)
 *   <li>Respond to auth attempt via Auth API
 *   <li>Wait for completion and obtain token
 *   <li>Persist token for future test runs
 * </ol>
 *
 * <p>Device credentials are persisted to `.ezkey-test/device-credentials.json` to enable reuse
 * across test runs. The token is persisted to `.ezkey-test/admin-token.json` to enable idempotent
 * test runs.
 *
 * @since 2025
 */
public class AdminBootstrapService {

  private static final Logger log = LoggerFactory.getLogger(AdminBootstrapService.class);

  private static final String TOKEN_FILE_PATH = ".ezkey-test/admin-token.json";
  private static final String DEVICE_CREDENTIALS_FILE_PATH = ".ezkey-test/device-credentials.json";
  private static final String ADMIN_USERNAME = "admin.docker";

  /**
   * Represents device credentials saved after initial bootstrap.
   *
   * @param enrollmentId Enrollment ID
   * @param privateKey Base64-encoded Ed25519 device private key seed (32 bytes)
   * @param publicKey Base64-encoded Ed25519 device public key (32 bytes)
   * @param keySize Key size in bits (always 256 for Ed25519)
   */
  private record DeviceCredentials(
      Integer enrollmentId, String privateKey, String publicKey, int keySize) {}

  private final DockerStackConfig dockerStackConfig;
  private final BootstrapCredentialsExtractor bootstrapCredentialsExtractor;
  private final CryptoApiClient cryptoApiClient;
  private final DatabaseHelper databaseHelper;

  // Synchronization lock to prevent parallel bootstrap attempts
  private static final ReentrantLock bootstrapLock = new ReentrantLock();

  /**
   * Creates a new AdminBootstrapService.
   *
   * @param dockerStackConfig Docker stack configuration
   * @param bootstrapCredentialsExtractor Bootstrap credentials extractor
   * @param cryptoApiClient Crypto API client
   */
  public AdminBootstrapService(
      DockerStackConfig dockerStackConfig,
      BootstrapCredentialsExtractor bootstrapCredentialsExtractor,
      CryptoApiClient cryptoApiClient) {
    this.dockerStackConfig = dockerStackConfig;
    this.bootstrapCredentialsExtractor = bootstrapCredentialsExtractor;
    this.cryptoApiClient = cryptoApiClient;
    this.databaseHelper = new DatabaseHelper();
  }

  /**
   * Ensures admin token is available, loading from cache or creating new token if needed.
   *
   * <p>This method follows a three-tier strategy:
   *
   * <ol>
   *   <li>Try to load cached token from file
   *   <li>If no token, try to reuse device credentials to create new token
   *   <li>If no device credentials, perform initial bootstrap then create token
   * </ol>
   *
   * @return Admin bearer token
   * @throws IllegalStateException if bootstrap or token creation fails
   */
  public String ensureAdminToken() {
    // Tier 1: Try to load from cache first and validate
    log.info("Checking for cached admin token...");
    String cachedToken = loadTokenFromFile();
    if (cachedToken != null && !cachedToken.isEmpty()) {
      // Validate token before using it (it might have been invalidated)
      if (isTokenValid(cachedToken)) {
        log.info("✅ Using validated cached admin token from file");
        return cachedToken;
      } else {
        log.info("Cached token is invalid, will create new token");
        // Token is invalid, clear cache and continue to Tier 2
        Path tokenPath = Paths.get(TOKEN_FILE_PATH);
        try {
          if (Files.exists(tokenPath)) {
            Files.delete(tokenPath);
          }
        } catch (IOException e) {
          log.warn("Failed to delete invalid token file: {}", e.getMessage());
        }
      }
    }

    // Tier 2: Try to reuse device credentials to create new token
    log.info("No cached token found, checking for device credentials...");
    DeviceCredentials deviceCredentials = loadDeviceCredentials();
    if (deviceCredentials != null) {
      log.info("✅ Device credentials found, creating new admin token...");
      String token = createAdminToken(deviceCredentials);
      log.info("Saving token to file: {}", TOKEN_FILE_PATH);
      saveTokenToFile(token);
      log.info("✅ Admin token created successfully - Token saved to file");
      return token;
    }

    // Tier 3: Perform initial bootstrap then create token
    log.info("No device credentials found, performing initial bootstrap enrollment...");
    deviceCredentials = performInitialBootstrap();
    log.info("Device credentials saved, creating admin token...");
    String token = createAdminToken(deviceCredentials);
    log.info("Saving token to file: {}", TOKEN_FILE_PATH);
    saveTokenToFile(token);
    log.info("✅ Initial bootstrap completed successfully - Token saved to file");
    return token;
  }

  /**
   * Performs the initial bootstrap flow (steps 1-4): extract credentials, generate keys, bind, and
   * verify enrollment.
   *
   * <p>This is a one-time operation that creates the device enrollment. The device credentials are
   * saved to enable reuse for subsequent token creations.
   *
   * <p><b>Synchronization:</b> Uses a lock to prevent multiple parallel bootstrap attempts, which
   * would cause rate limiting issues.
   *
   * @return Device credentials (enrollment ID, key pair)
   * @throws IllegalStateException if bootstrap fails
   */
  private DeviceCredentials performInitialBootstrap() {
    // Acquire lock to prevent parallel bootstrap attempts
    bootstrapLock.lock();
    try {
      // Double-check: maybe another thread already completed bootstrap while we waited
      DeviceCredentials existingCredentials = loadDeviceCredentials();
      if (existingCredentials != null) {
        log.info("Device credentials were created by another thread while waiting for lock");
        return existingCredentials;
      }

      return performInitialBootstrapInternal();
    } finally {
      bootstrapLock.unlock();
    }
  }

  /**
   * Internal method that performs the actual bootstrap flow (without synchronization).
   *
   * @return Device credentials (enrollment ID, key pair)
   * @throws IllegalStateException if bootstrap fails
   */
  private DeviceCredentials performInitialBootstrapInternal() {
    try {
      // Step 1: Extract bootstrap credentials
      log.info("═══════════════════════════════════════════════════════════════");
      log.info("STEP 1: Extracting bootstrap credentials...");
      log.info("═══════════════════════════════════════════════════════════════");
      BootstrapCredentialsExtractor.BootstrapCredentials credentials =
          bootstrapCredentialsExtractor.loadOrExtractCredentials();
      log.info("✅ Step 1 Complete - Credentials loaded:");
      log.info("   Enrollment ID: {}", credentials.enrollmentId());
      log.info(
          "   Enrollment Proof Token: {}...",
          credentials
              .enrollmentProofToken()
              .substring(0, Math.min(30, credentials.enrollmentProofToken().length())));
      log.info("   Challenge Code: {}", credentials.enrollmentChallengeCode());

      // Step 2: Check if enrollment exists and its status in database
      // If verified, we need to use existing device credentials, not generate new ones
      log.info("═══════════════════════════════════════════════════════════════");
      log.info("STEP 2: Checking enrollment status...");
      log.info("═══════════════════════════════════════════════════════════════");
      String enrollmentStatus = databaseHelper.getEnrollmentStatus(credentials.enrollmentId());
      if (enrollmentStatus == null) {
        log.error("   ❌ Enrollment ID {} not found in database!", credentials.enrollmentId());
        log.error("   This usually means:");
        log.error("   1. Admin API bootstrap did not complete successfully");
        log.error("   2. Database was reset after bootstrap");
        log.error("   3. Enrollment was deleted");
        throw new IllegalStateException(
            "Enrollment ID "
                + credentials.enrollmentId()
                + " not found in database. "
                + "Please ensure Admin API bootstrap completed successfully.");
      }
      log.info("   Enrollment status: {}", enrollmentStatus);

      // Debug: Verify proof token hash matches
      String dbTokenHash = databaseHelper.getEnrollmentProofTokenHash(credentials.enrollmentId());
      if (dbTokenHash != null) {
        // Calculate hash from extracted token for comparison
        String extractedTokenHash = calculateSha256Hex(credentials.enrollmentProofToken());
        log.debug(
            "   Database token hash: {}...",
            dbTokenHash.substring(0, Math.min(16, dbTokenHash.length())));
        log.debug(
            "   Extracted token hash: {}...",
            extractedTokenHash != null
                ? extractedTokenHash.substring(0, Math.min(16, extractedTokenHash.length()))
                : "null");
        if (!dbTokenHash.equals(extractedTokenHash)) {
          log.error("   ❌ Token hash mismatch!");
          log.error("   Database hash: {}", dbTokenHash);
          log.error("   Extracted token hash: {}", extractedTokenHash);
          log.error(
              "   Extracted token (first 50 chars): {}",
              credentials
                  .enrollmentProofToken()
                  .substring(0, Math.min(50, credentials.enrollmentProofToken().length())));
          throw new IllegalStateException(
              "Enrollment proof token hash mismatch. The token extracted from logs does not match "
                  + "the token stored in database. This usually means the token was truncated or "
                  + "incorrectly parsed from logs. Please check the bootstrap credentials file or "
                  + "re-extract from Docker logs.");
        } else {
          log.debug("   ✅ Token hash matches");
        }
      }

      CryptoApiClient.Ed25519KeyPair deviceKeyPair;
      DeviceCredentials deviceCredentials;

      if ("VERIFIED".equals(enrollmentStatus)) {
        // Enrollment already verified - check if we have matching device credentials
        log.info(
            "   ⚠️  Enrollment already VERIFIED - Checking for existing device credentials...");
        DeviceCredentials existingCredentials = loadDeviceCredentials();
        if (existingCredentials != null
            && existingCredentials.enrollmentId().equals(credentials.enrollmentId())) {
          log.info("   ✅ Found existing device credentials - Reusing them");
          deviceCredentials = existingCredentials;
          deviceKeyPair =
              new CryptoApiClient.Ed25519KeyPair(
                  existingCredentials.privateKey(), existingCredentials.publicKey());
          log.info("   ⏭️  Skipping bind and verify steps - Using existing credentials");
        } else {
          log.warn("   ⚠️  Enrollment is VERIFIED but no matching device credentials found");
          log.warn(
              "   ⚠️  Cannot proceed: enrollment has device_public_key in DB but we don't have"
                  + " matching private key");
          throw new IllegalStateException(
              "Enrollment "
                  + credentials.enrollmentId()
                  + " is already VERIFIED with a different device. Cannot create new device"
                  + " credentials. Please reset Docker stack (docker-compose down -v) or reset"
                  + " the enrollment manually.");
        }
      } else {
        // Enrollment not verified - proceed with normal bootstrap
        log.info("═══════════════════════════════════════════════════════════════");
        log.info("STEP 2: Generating Ed25519 device key pair...");
        log.info("═══════════════════════════════════════════════════════════════");
        deviceKeyPair = cryptoApiClient.generateKeyPair();
        log.info("✅ Step 2 Complete - Ed25519 device key pair generated:");
        log.info("   Key Size: 256 bits (Ed25519 - fixed size)");
        log.info(
            "   Public Key: {}...",
            deviceKeyPair
                .publicKey()
                .substring(0, Math.min(50, deviceKeyPair.publicKey().length())));

        // Step 3: Bind device to enrollment
        log.info("═══════════════════════════════════════════════════════════════");
        log.info("STEP 3: Binding device to enrollment...");
        log.info("═══════════════════════════════════════════════════════════════");
        String bindProofToken =
            bindDeviceOrSkip(credentials.enrollmentId(), credentials.enrollmentProofToken());
        log.info("✅ Step 3 Complete - Device bound to enrollment:");
        log.info(
            "   Bind Proof Token: {}...",
            bindProofToken.substring(0, Math.min(30, bindProofToken.length())));

        // Step 4: Verify enrollment (skip if already verified)
        log.info("═══════════════════════════════════════════════════════════════");
        log.info("STEP 4: Verifying enrollment...");
        log.info("═══════════════════════════════════════════════════════════════");
        if ("SKIPPED_BIND_ALREADY_VERIFIED".equals(bindProofToken)) {
          log.info("   ⏭️  Skipping verify - Enrollment already verified");
        } else {
          verifyEnrollment(
              credentials.enrollmentId(),
              deviceKeyPair,
              bindProofToken,
              credentials.enrollmentChallengeCode());
          log.info("✅ Step 4 Complete - Enrollment verified and activated");
        }

        // Save device credentials for reuse
        deviceCredentials =
            new DeviceCredentials(
                credentials.enrollmentId(),
                deviceKeyPair.privateKey(),
                deviceKeyPair.publicKey(),
                256); // Ed25519 is always 256 bits (32 bytes)
        log.info("Saving device credentials to file: {}", DEVICE_CREDENTIALS_FILE_PATH);
        saveDeviceCredentials(deviceCredentials);
      }

      // Step 5: Write enrollment file to demo-device container
      log.info("═══════════════════════════════════════════════════════════════");
      log.info("STEP 5: Writing enrollment file to demo-device container...");
      log.info("═══════════════════════════════════════════════════════════════");
      try {
        // Create admin token temporarily to fetch enrollment details
        String tempAdminToken = createAdminToken(deviceCredentials);
        DemoDeviceEnrollmentWriter enrollmentWriter =
            new DemoDeviceEnrollmentWriter(dockerStackConfig);
        enrollmentWriter.writeEnrollmentFile(
            credentials.enrollmentId(),
            deviceCredentials.publicKey(),
            deviceCredentials.privateKey(),
            credentials.enrollmentProofToken(),
            tempAdminToken);
        log.info("✅ Step 5 Complete - Enrollment file written to demo-device");
      } catch (Exception e) {
        log.error("❌ Failed to write enrollment file to demo-device", e);
        throw new IllegalStateException(
            "Failed to write enrollment file to demo-device container: " + e.getMessage(), e);
      }

      log.info("═══════════════════════════════════════════════════════════════");
      log.info("🎉 INITIAL BOOTSTRAP COMPLETE - Device enrolled and credentials saved!");
      log.info("═══════════════════════════════════════════════════════════════");
      return deviceCredentials;
    } catch (Exception e) {
      log.error("❌ INITIAL BOOTSTRAP FAILED at step", e);
      throw new IllegalStateException("Initial bootstrap failed: " + e.getMessage(), e);
    }
  }

  /**
   * Creates admin token using existing device credentials (steps 5-7).
   *
   * <p>This method reuses device credentials from a previous bootstrap to create a new admin token.
   * This is much faster than performing the full bootstrap flow.
   *
   * @param deviceCredentials Device credentials from initial bootstrap
   * @return Admin bearer token
   * @throws IllegalStateException if token creation fails
   */
  private String createAdminToken(DeviceCredentials deviceCredentials) {
    try {
      // Load bootstrap credentials (for enrollment proof token)
      BootstrapCredentialsExtractor.BootstrapCredentials credentials =
          bootstrapCredentialsExtractor.loadOrExtractCredentials();

      // Reconstruct device key pair from saved credentials
      CryptoApiClient.Ed25519KeyPair deviceKeyPair =
          new CryptoApiClient.Ed25519KeyPair(
              deviceCredentials.privateKey(), deviceCredentials.publicKey());

      // Step 5: Login admin (creates auth attempt)
      log.info("═══════════════════════════════════════════════════════════════");
      log.info("STEP 5: Logging in admin (creating auth attempt)...");
      log.info("═══════════════════════════════════════════════════════════════");
      AdminLoginResult loginResult =
          loginAdmin(credentials.enrollmentId(), credentials.enrollmentProofToken());
      log.info("✅ Step 5 Complete - Auth attempt created:");
      log.info("   Auth Attempt ID: {}", loginResult.authAttemptId());
      log.info("   Challenge Code: {}", loginResult.challengeCode());

      // Step 6: Respond to auth attempt
      log.info("═══════════════════════════════════════════════════════════════");
      log.info("STEP 6: Responding to auth attempt...");
      log.info("═══════════════════════════════════════════════════════════════");
      respondToAuthAttempt(
          loginResult.authAttemptId(),
          deviceKeyPair,
          credentials.enrollmentProofToken(),
          loginResult.challengeCode());
      log.info("✅ Step 6 Complete - Auth attempt responded and approved");

      // Step 7: Wait for completion and obtain token
      log.info("═══════════════════════════════════════════════════════════════");
      log.info("STEP 7: Waiting for authentication completion...");
      log.info("═══════════════════════════════════════════════════════════════");
      String token =
          waitForPasswordlessAuth(loginResult.authAttemptId(), loginResult.challengeCode());
      log.info("✅ Step 7 Complete - Token obtained:");
      log.info("   Token: {}...", token.substring(0, Math.min(30, token.length())));

      log.info("═══════════════════════════════════════════════════════════════");
      log.info("🎉 TOKEN CREATION COMPLETE - All steps successful!");
      log.info("═══════════════════════════════════════════════════════════════");
      return token;
    } catch (Exception e) {
      log.error("❌ TOKEN CREATION FAILED at step", e);
      throw new IllegalStateException("Admin token creation failed: " + e.getMessage(), e);
    }
  }

  /**
   * Binds device to enrollment via Auth API with retry logic for rate limiting.
   *
   * <p>If enrollment is already bound and verified, throws an exception indicating that Docker
   * should be reset or enrollment should be reset manually.
   *
   * <p>Handles rate limiting (429) with exponential backoff retry to accommodate parallel test
   * execution.
   *
   * @param enrollmentId Enrollment ID
   * @param enrollmentProofToken Enrollment proof token
   * @return Bind proof token for verification
   * @throws IllegalStateException if enrollment is already bound but not verified, or if rate limit
   *     retries are exhausted
   */
  private String bindDeviceOrSkip(Integer enrollmentId, String enrollmentProofToken) {
    log.info("   Calling: POST /api/v1/enrollments/bind");
    log.info("   Enrollment ID: {}", enrollmentId);
    log.debug(
        "   Enrollment Proof Token: {}...",
        enrollmentProofToken != null && enrollmentProofToken.length() > 30
            ? enrollmentProofToken.substring(0, 30) + "..."
            : enrollmentProofToken);
    RestAssuredTestConfig.configureForAuthApi(dockerStackConfig);

    Map<String, Object> bindRequest = new HashMap<>();
    bindRequest.put("enrollmentId", enrollmentId);
    bindRequest.put("enrollmentProofToken", enrollmentProofToken);

    // Retry logic for rate limiting (429)
    int maxRetries = 5;
    long baseDelayMs = 1000; // Start with 1 second

    for (int attempt = 0; attempt < maxRetries; attempt++) {
      if (attempt > 0) {
        // Exponential backoff: 1s, 2s, 4s, 8s, 16s
        long delayMs = baseDelayMs * (1L << (attempt - 1));
        log.info(
            "   ⏳ Rate limit hit (429), retrying in {}ms (attempt {}/{})...",
            delayMs,
            attempt + 1,
            maxRetries);
        try {
          Thread.sleep(delayMs);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException("Interrupted while waiting for rate limit", e);
        }
      }

      Response response =
          given()
              .contentType(ContentType.JSON)
              .body(bindRequest)
              .when()
              .post("/enrollments/bind")
              .then()
              .extract()
              .response();

      log.info("   Response Status: {}", response.getStatusCode());

      // If enrollment already bound (409), check enrollment status in database and handle
      // opportunistically based on actual state.
      //
      // STRATEGY: Use direct database access to check enrollment status (opportunistic approach
      // for functional E2E tests in clean room Docker environment).
      // - If enrollment is VERIFIED: Skip bind and continue (enrollment is usable)
      // - If enrollment is BOUND but not VERIFIED: Reset enrollment in DB to CREATED state
      //   (allows clean retry without requiring Docker reset)
      // - Check local device credentials first (fastest path if available)
      if (response.getStatusCode() == 409) {
        log.warn("   ⚠️  Enrollment already bound (409) - Checking enrollment status...");

        // First check: Local device credentials (fastest, no DB query needed)
        DeviceCredentials existingCredentials = loadDeviceCredentials();
        if (existingCredentials != null
            && existingCredentials.enrollmentId().equals(enrollmentId)) {
          log.info(
              "   ✅ Found existing device credentials for enrollment {} - Enrollment was"
                  + " previously verified in a completed bootstrap",
              enrollmentId);
          log.info(
              "   ⏭️  Skipping bind step (enrollment already VERIFIED, will use existing"
                  + " credentials)");
          return "SKIPPED_BIND_ALREADY_VERIFIED";
        }

        // Second check: Database status (opportunistic - check real state)
        String enrollmentStatus = databaseHelper.getEnrollmentStatus(enrollmentId);
        log.info("   Enrollment status in database: {}", enrollmentStatus);

        if ("VERIFIED".equals(enrollmentStatus)) {
          log.info(
              "   ✅ Enrollment is VERIFIED in database - Skipping bind step (enrollment is"
                  + " usable)");
          return "SKIPPED_BIND_ALREADY_VERIFIED";
        } else if ("BOUND".equals(enrollmentStatus)) {
          log.warn(
              "   ⚠️  Enrollment is BOUND but not VERIFIED - Resetting enrollment in database to"
                  + " allow clean retry");
          databaseHelper.resetEnrollment(enrollmentId);
          log.info("   ✅ Enrollment reset to CREATED state - Retrying bind...");
          // Retry bind after reset
          return bindDeviceOrSkip(enrollmentId, enrollmentProofToken);
        } else {
          log.error(
              "   ❌ Unexpected enrollment status: {} - Response: {}",
              enrollmentStatus,
              response.asString());
          throw new IllegalStateException(
              "Enrollment is in unexpected state: "
                  + enrollmentStatus
                  + ". Please reset Docker stack (docker-compose down -v) or reset the enrollment"
                  + " manually.");
        }
      }

      // If rate limited (429), retry with backoff
      if (response.getStatusCode() == 429) {
        if (attempt < maxRetries - 1) {
          continue; // Retry
        } else {
          log.error(
              "   ❌ Rate limit exceeded after {} attempts - Response: {}",
              maxRetries,
              response.asString());
          throw new IllegalStateException(
              "Rate limit exceeded on enrollment bind after "
                  + maxRetries
                  + " attempts. This usually happens when multiple tests run in parallel. "
                  + "Consider running tests sequentially or increasing rate limit configuration.");
        }
      }

      // If successful (200), return immediately
      if (response.getStatusCode() == 200) {
        String bindProofToken = response.jsonPath().getString("enrollmentProofToken");
        assertThat(bindProofToken).isNotNull().isNotEmpty();
        log.info("   ✅ Bind successful - Proof token received");
        return bindProofToken;
      }

      // For other errors, fail immediately
      log.error(
          "   ❌ Unexpected status code: {} - Response: {}",
          response.getStatusCode(),
          response.asString());
      assertThat(response.getStatusCode()).isEqualTo(200);
    }

    // This should never be reached, but compiler requires it
    throw new IllegalStateException("Failed to bind device after " + maxRetries + " attempts");
  }

  /**
   * Verifies enrollment via Auth API with challenge code.
   *
   * @param enrollmentId Enrollment ID
   * @param deviceKeyPair Device key pair
   * @param bindProofToken Bind proof token
   * @param challengeCode Enrollment challenge code
   */
  private void verifyEnrollment(
      Integer enrollmentId,
      CryptoApiClient.Ed25519KeyPair deviceKeyPair,
      String bindProofToken,
      Integer challengeCode) {
    log.info("   Signing bind proof token with device private key...");
    // Sign bind proof token (this configures RestAssured for Crypto API)
    String signature = cryptoApiClient.signData(bindProofToken, deviceKeyPair.privateKey());
    log.info(
        "   ✅ Signature generated: {}...",
        signature.substring(0, Math.min(30, signature.length())));

    // Reconfigure RestAssured for Auth API after Crypto API call
    RestAssuredTestConfig.configureForAuthApi(dockerStackConfig);

    log.info("   Calling: POST /api/v1/enrollments/verify");
    log.info("   Enrollment ID: {}", enrollmentId);
    log.info("   Challenge Response: {}", challengeCode);
    Map<String, Object> verifyRequest = new HashMap<>();
    verifyRequest.put("enrollmentId", enrollmentId);
    verifyRequest.put("challengeResponse", challengeCode);
    verifyRequest.put("devicePublicKey", deviceKeyPair.publicKey());
    verifyRequest.put("enrollmentProofTokenSigned", signature);

    Response response =
        given()
            .contentType(ContentType.JSON)
            .body(verifyRequest)
            .when()
            .post("/enrollments/verify")
            .then()
            .extract()
            .response();

    log.info("   Response Status: {}", response.getStatusCode());
    if (response.getStatusCode() != 200) {
      log.error("   ❌ Verification failed - Response: {}", response.asString());
    } else {
      Boolean active = response.jsonPath().getBoolean("active");
      log.info("   ✅ Verification successful - Enrollment active: {}", active);
    }
    assertThat(response.getStatusCode()).isEqualTo(200);
  }

  /**
   * Result of admin login request.
   *
   * @param authAttemptId Auth attempt ID
   * @param challengeCode Challenge code
   */
  private record AdminLoginResult(Integer authAttemptId, Integer challengeCode) {}

  /**
   * Logs in admin via Admin API (creates auth attempt).
   *
   * @param enrollmentId Enrollment ID (for getting proof token)
   * @param enrollmentProofToken Enrollment proof token
   * @return AdminLoginResult with auth attempt ID and challenge code
   */
  private AdminLoginResult loginAdmin(Integer enrollmentId, String enrollmentProofToken) {
    log.info("   Calling: POST /api/v1/admin/auth/login");
    log.info("   Username: {}", ADMIN_USERNAME);
    log.info("   Challenge Requested: true");
    RestAssuredTestConfig.configureForAdminApi(dockerStackConfig);

    Map<String, Object> loginRequest = new HashMap<>();
    loginRequest.put("username", ADMIN_USERNAME);
    loginRequest.put("challengeRequested", true); // Use two-call mode

    Response response =
        given()
            .contentType(ContentType.JSON)
            .body(loginRequest)
            .when()
            .post("/admin/auth/login")
            .then()
            .extract()
            .response();

    log.info("   Response Status: {}", response.getStatusCode());
    if (response.getStatusCode() != 200) {
      log.error("   ❌ Login failed - Response: {}", response.asString());
    }

    assertThat(response.getStatusCode()).isEqualTo(200);

    String status = response.jsonPath().getString("status");
    log.info("   Status: {}", status);
    assertThat(status).isEqualTo("pending");

    Integer authAttemptId = response.jsonPath().getInt("authAttemptId");
    Integer challengeCode = response.jsonPath().getInt("challengeCode");

    assertThat(authAttemptId).isNotNull();
    assertThat(challengeCode).isNotNull();

    log.info("   ✅ Login successful - Auth attempt created");
    return new AdminLoginResult(authAttemptId, challengeCode);
  }

  /**
   * Responds to auth attempt via Auth API.
   *
   * @param authAttemptId Auth attempt ID
   * @param deviceKeyPair Device key pair
   * @param enrollmentProofToken Enrollment proof token
   * @param challengeCode Challenge code for challenge response
   */
  private void respondToAuthAttempt(
      Integer authAttemptId,
      CryptoApiClient.Ed25519KeyPair deviceKeyPair,
      String enrollmentProofToken,
      Integer challengeCode) {
    log.info("   Sub-step 6a: Generating device proof token...");
    RestAssuredTestConfig.configureForAuthApi(dockerStackConfig);

    // Get pending auth attempt to obtain proof token
    // First, generate device proof token and sign it (required for pending request)
    // Note: generateProofToken() configures RestAssured for Crypto API, so we need to reconfigure
    // for Auth API after
    String deviceProofToken = cryptoApiClient.generateProofToken();
    log.info("   ✅ Device proof token generated");

    log.info("   Sub-step 6b: Signing device proof token...");
    String deviceProofTokenSigned =
        cryptoApiClient.signData(deviceProofToken, deviceKeyPair.privateKey());
    log.info("   ✅ Device proof token signed");

    // Reconfigure RestAssured for Auth API after Crypto API calls
    RestAssuredTestConfig.configureForAuthApi(dockerStackConfig);

    BootstrapCredentialsExtractor.BootstrapCredentials credentials =
        bootstrapCredentialsExtractor.loadOrExtractCredentials();

    log.info("   Sub-step 6c: Calling: POST /api/v1/auth-attempts/pending");
    log.info("   Enrollment ID: {}", credentials.enrollmentId());
    Map<String, Object> pendingRequest = new HashMap<>();
    pendingRequest.put("enrollmentId", credentials.enrollmentId());
    pendingRequest.put("enrollmentProofToken", enrollmentProofToken);
    pendingRequest.put("deviceProofToken", deviceProofToken);
    pendingRequest.put("deviceProofTokenSigned", deviceProofTokenSigned);

    Response pendingResponse =
        given()
            .contentType(ContentType.JSON)
            .body(pendingRequest)
            .when()
            .post("/auth-attempts/pending")
            .then()
            .extract()
            .response();

    log.info("   Response Status: {}", pendingResponse.getStatusCode());
    if (pendingResponse.getStatusCode() != 200) {
      log.error("   ❌ Pending request failed - Response: {}", pendingResponse.asString());
    }

    assertThat(pendingResponse.getStatusCode()).isEqualTo(200);

    String authAttemptProofToken = pendingResponse.jsonPath().getString("authAttemptProofToken");
    assertThat(authAttemptProofToken).isNotNull().isNotEmpty();
    log.info(
        "   ✅ Auth attempt proof token received: {}...",
        authAttemptProofToken.substring(0, Math.min(30, authAttemptProofToken.length())));

    // Sign auth attempt proof token with device private key
    log.info("   Sub-step 7a: Signing auth attempt proof token...");
    // Note: signData() configures RestAssured for Crypto API, so we need to reconfigure for Auth
    // API after
    String authAttemptProofTokenSignedByDevice =
        cryptoApiClient.signData(authAttemptProofToken, deviceKeyPair.privateKey());
    log.info("   ✅ Auth attempt proof token signed");

    // Reconfigure RestAssured for Auth API after Crypto API call
    RestAssuredTestConfig.configureForAuthApi(dockerStackConfig);

    // Respond to auth attempt with correct field names
    log.info("   Sub-step 7b: Calling: POST /api/v1/auth-attempts/respond");
    log.info("   Auth Attempt ID: {}", authAttemptId);
    log.info("   Challenge Response: {}", challengeCode);
    Map<String, Object> respondRequest = new HashMap<>();
    respondRequest.put("authAttemptId", authAttemptId);
    respondRequest.put("authAttemptAccepted", true);
    respondRequest.put("authAttemptProofTokenSignedByDevice", authAttemptProofTokenSignedByDevice);
    if (challengeCode != null) {
      respondRequest.put("authAttemptChallengeResponse", challengeCode);
    }

    Response respondResponse =
        given()
            .contentType(ContentType.JSON)
            .body(respondRequest)
            .when()
            .post("/auth-attempts/respond")
            .then()
            .extract()
            .response();

    log.info("   Response Status: {}", respondResponse.getStatusCode());
    if (respondResponse.getStatusCode() != 200) {
      log.error("   ❌ Respond failed - Response: {}", respondResponse.asString());
    }

    assertThat(respondResponse.getStatusCode()).isEqualTo(200);

    String result = respondResponse.jsonPath().getString("result");
    log.info("   Result: {}", result);
    assertThat(result).isEqualTo("APPROVED");
    log.info("   ✅ Auth attempt approved");
  }

  /**
   * Waits for passwordless authentication completion and obtains token.
   *
   * @param authAttemptId Auth attempt ID
   * @param challengeCode Challenge code
   * @return Admin bearer token
   */
  private String waitForPasswordlessAuth(Integer authAttemptId, Integer challengeCode) {
    log.info("   Calling: POST /api/v1/admin/auth/passwordless-wait");
    log.info("   Auth Attempt ID: {}", authAttemptId);
    log.info("   Challenge Code: {}", challengeCode);
    RestAssuredTestConfig.configureForAdminApi(dockerStackConfig);

    Map<String, Object> waitRequest = new HashMap<>();
    waitRequest.put("authAttemptId", authAttemptId);
    waitRequest.put("challengeCode", challengeCode);

    Response response =
        given()
            .contentType(ContentType.JSON)
            .body(waitRequest)
            .when()
            .post("/admin/auth/passwordless-wait")
            .then()
            .extract()
            .response();

    log.info("   Response Status: {}", response.getStatusCode());
    if (response.getStatusCode() != 200) {
      log.error("   ❌ Wait failed - Response: {}", response.asString());
    }

    assertThat(response.getStatusCode()).isEqualTo(200);

    Boolean success = response.jsonPath().getBoolean("success");
    log.info("   Success: {}", success);
    assertThat(success).isTrue();

    String token = response.jsonPath().getString("token");
    assertThat(token).isNotNull().isNotEmpty();
    log.info("   ✅ Token received successfully");

    return token;
  }

  /**
   * Loads admin token from cache file.
   *
   * @return Admin token, or null if not found
   */
  private String loadTokenFromFile() {
    Path tokenPath = Paths.get(TOKEN_FILE_PATH);
    if (!Files.exists(tokenPath)) {
      return null;
    }

    try {
      ObjectMapper mapper = new ObjectMapper();
      ObjectNode jsonNode = (ObjectNode) mapper.readTree(tokenPath.toFile());
      return jsonNode.get("token").asText();
    } catch (IOException e) {
      log.warn("Failed to load token from file: {}", e.getMessage());
      return null;
    }
  }

  /**
   * Saves admin token to cache file.
   *
   * @param token Admin token to save
   */
  private void saveTokenToFile(String token) {
    try {
      Path tokenPath = Paths.get(TOKEN_FILE_PATH);
      Path parentDir = tokenPath.getParent();

      if (parentDir != null && !Files.exists(parentDir)) {
        Files.createDirectories(parentDir);
      }

      ObjectMapper mapper = new ObjectMapper();
      ObjectNode jsonNode = mapper.createObjectNode();
      jsonNode.put("token", token);

      mapper.writerWithDefaultPrettyPrinter().writeValue(tokenPath.toFile(), jsonNode);
      log.info("Token saved to: {}", TOKEN_FILE_PATH);
    } catch (IOException e) {
      log.warn("Failed to save token to file: {}", e.getMessage());
      // Don't throw - token is still valid even if save fails
    }
  }

  /**
   * Loads device credentials from cache file.
   *
   * @return Device credentials, or null if not found
   */
  private DeviceCredentials loadDeviceCredentials() {
    Path credentialsPath = Paths.get(DEVICE_CREDENTIALS_FILE_PATH);
    if (!Files.exists(credentialsPath)) {
      return null;
    }

    try {
      ObjectMapper mapper = new ObjectMapper();
      ObjectNode jsonNode = (ObjectNode) mapper.readTree(credentialsPath.toFile());

      Integer enrollmentId = jsonNode.get("enrollmentId").asInt();
      String privateKey = jsonNode.get("privateKey").asText();
      String publicKey = jsonNode.get("publicKey").asText();
      int keySize =
          jsonNode.has("keySize") ? jsonNode.get("keySize").asInt() : 256; // Ed25519 default

      log.info("Device credentials loaded from file");
      return new DeviceCredentials(enrollmentId, privateKey, publicKey, keySize);
    } catch (IOException e) {
      log.warn("Failed to load device credentials from file: {}", e.getMessage());
      return null;
    }
  }

  /**
   * Saves device credentials to cache file.
   *
   * @param credentials Device credentials to save
   */
  private void saveDeviceCredentials(DeviceCredentials credentials) {
    try {
      Path credentialsPath = Paths.get(DEVICE_CREDENTIALS_FILE_PATH);
      Path parentDir = credentialsPath.getParent();

      if (parentDir != null && !Files.exists(parentDir)) {
        Files.createDirectories(parentDir);
      }

      ObjectMapper mapper = new ObjectMapper();
      ObjectNode jsonNode = mapper.createObjectNode();
      jsonNode.put("enrollmentId", credentials.enrollmentId());
      jsonNode.put("privateKey", credentials.privateKey());
      jsonNode.put("publicKey", credentials.publicKey());
      jsonNode.put("keySize", credentials.keySize());

      mapper.writerWithDefaultPrettyPrinter().writeValue(credentialsPath.toFile(), jsonNode);
      log.info("Device credentials saved to: {}", DEVICE_CREDENTIALS_FILE_PATH);
    } catch (IOException e) {
      log.warn("Failed to save device credentials to file: {}", e.getMessage());
      // Don't throw - bootstrap can continue even if save fails
    }
  }

  /**
   * Validates a token by making a test request to the Admin API.
   *
   * <p>This method checks if the token is still valid in the database by attempting to access a
   * protected endpoint. This is necessary because tokens can be invalidated by token rotation or
   * database resets.
   *
   * @param token Admin bearer token to validate
   * @return true if token is valid, false otherwise
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
   * Calculates SHA-256 hash of a string (same as SensitiveDataHasher.sha256Hex).
   *
   * @param value the value to hash
   * @return hexadecimal SHA-256 hash or null if value is null/blank
   */
  private String calculateSha256Hex(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }

    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashBytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder hexString = new StringBuilder(hashBytes.length * 2);
      for (byte hashByte : hashBytes) {
        String hex = Integer.toHexString(0xff & hashByte);
        if (hex.length() == 1) {
          hexString.append('0');
        }
        hexString.append(hex);
      }
      return hexString.toString();
    } catch (NoSuchAlgorithmException e) {
      log.error("SHA-256 algorithm not available", e);
      throw new IllegalStateException("SHA-256 algorithm not available", e);
    }
  }
}
