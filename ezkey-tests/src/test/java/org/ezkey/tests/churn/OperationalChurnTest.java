/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: OperationalChurnTest
 * Description: Sustained operational churn — steady-state auth batches after one-time tenant setup.
 */

package org.ezkey.tests.churn;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.ezkey.tests.util.RestAssuredTestConfig.configureForAdminApi;
import static org.ezkey.tests.util.RestAssuredTestConfig.configureForAuthApi;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.ezkey.tests.security.AbstractSecurityTest;
import org.ezkey.tests.tags.TestTags;
import org.ezkey.tests.util.CryptoApiClient.EcP256KeyPair;
import org.ezkey.tests.util.TenantAdminTestHelper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * Sustained activity against a running Docker stack: loads the peer Global Admin token from {@link
 * OperationalChurnGlobalAdminState}, performs a <strong>one-time</strong> tenant setup (tenant,
 * tenant admin, integrations, enrollments), then loops on authentication batches only.
 *
 * <p>Invoked via {@code -P operational-churn-tests} or {@code -Dgroups=operational-churn}. Run
 * {@link OperationalChurnInitTest} first. See {@code docs/plan/operational-churn-ezkey.plan.md} and
 * {@code docs/plan/operational-churn-strategy-and-implementation.md}.
 *
 * <p>System properties: {@code churn.profile} (light|medium), {@code churn.maxDurationMinutes}
 * (default 120), {@code churn.maxIterations} (optional), {@code churn.random.seed} (optional).
 */
@Tag(TestTags.OPERATIONAL_CHURN)
@DisplayName("Operational churn (sustained activity)")
public class OperationalChurnTest extends AbstractSecurityTest {

  private static final int DEFAULT_MAX_DURATION_MINUTES = 120;

  private TenantAdminTestHelper tenantAdminTestHelper;

  /** Mutable knobs for one run (from {@code churn.profile}). */
  private record ChurnIntensity(
      int authBatchSize, int sleepBetweenAuthsMs, int sleepBetweenIterationsMs) {}

  private record EnrollmentDeviceContext(
      Integer enrollmentId, String enrollmentProofToken, EcP256KeyPair deviceKeyPair) {}

  @BeforeEach
  void churnSetup() {
    tenantAdminTestHelper =
        new TenantAdminTestHelper(dockerStackConfig, testDataFactory, cryptoApiClient);
  }

  @Test
  @DisplayName("Loop: steady-state auth after one-time tenant + integrations + enrollments")
  void operationalChurnLoop() {
    String churnInstanceId = UUID.randomUUID().toString();
    MDC.put("churnInstanceId", churnInstanceId);
    try {
      OperationalChurnGlobalAdminState churnGlobalState = OperationalChurnGlobalAdminState.load();
      if (churnGlobalState == null) {
        Assumptions.abort(
            "Missing operational churn Global Admin state. Run once: mvn test -pl ezkey-tests"
                + " -P operational-churn-init (or ./ezkey-tests/scripts/run-operational-churn.sh"
                + " --init). See docs/plan/operational-churn-strategy-and-implementation.md.");
      }
      if (!authTokenManager.isAdminTokenValid(churnGlobalState.bearerToken())) {
        Assumptions.abort(
            "operational-churn-global-admin.json token is invalid or expired. Re-run: mvn test"
                + " -pl ezkey-tests -P operational-churn-init");
      }

      long effectiveSeed = initAndLogRandomSeed();
      Random rng = new Random(effectiveSeed);
      ChurnIntensity intensity = resolveIntensityProfile();

      String resourceSuffix = Long.toHexString(rng.nextLong()) + Long.toHexString(rng.nextLong());
      String tenantAdminUsername = "churn-ta-" + String.format("%017x", rng.nextLong());
      String churnGlobalToken = churnGlobalState.bearerToken();

      long maxDurationMs = resolveMaxDurationMinutes() * 60_000L;
      long maxIterations = resolveMaxIterations();
      long startNs = System.nanoTime();
      long iteration = 0L;

      log.info(
          "Operational churn starting: churnInstanceId={} churn.random.seed.effective={} "
              + "profile={} maxDurationMinutes={} maxIterations={} authBatchSize={}",
          churnInstanceId,
          effectiveSeed,
          System.getProperty("churn.profile", "medium"),
          resolveMaxDurationMinutes(),
          maxIterations == Long.MAX_VALUE ? "unlimited" : String.valueOf(maxIterations),
          intensity.authBatchSize());

      log.info(
          "One-time setup: resourceSuffix={} tenantAdminUsername={}",
          resourceSuffix,
          tenantAdminUsername);

      String tenantName = "Churn Tenant " + resourceSuffix;
      Integer tenantId = testDataFactory.createTenant(tenantName, churnGlobalToken);
      String tenantAdminToken =
          tenantAdminTestHelper.createAndLoginTenantAdmin(
              tenantAdminUsername, tenantId, churnGlobalToken);

      String intNameA = "Churn Int A " + resourceSuffix;
      String intNameB = "Churn Int B " + resourceSuffix;
      Integer integrationAId =
          testDataFactory.createIntegrationForTenant(intNameA, tenantId, tenantAdminToken);
      Integer integrationBId =
          testDataFactory.createIntegrationForTenant(intNameB, tenantId, tenantAdminToken);

      testDataFactory.createApiKeyForIntegration(integrationAId, tenantAdminToken);
      testDataFactory.createApiKeyForIntegration(integrationBId, tenantAdminToken);

      EnrollmentDeviceContext ctxA =
          completeIntegrationEnrollment(integrationAId, tenantAdminToken);
      EnrollmentDeviceContext ctxB =
          completeIntegrationEnrollment(integrationBId, tenantAdminToken);

      while (iteration < maxIterations && elapsedMs(startNs) < maxDurationMs) {
        iteration++;
        MDC.put("churnIteration", String.valueOf(iteration));
        log.info("churn iteration (auth only): iteration={}", iteration);

        for (int i = 0; i < intensity.authBatchSize(); i++) {
          runFullAuthAttempt(ctxA, tenantAdminToken);
          sleepQuiet(intensity.sleepBetweenAuthsMs());
        }

        for (int i = 0; i < intensity.authBatchSize(); i++) {
          runFullAuthAttempt(ctxB, tenantAdminToken);
          sleepQuiet(intensity.sleepBetweenAuthsMs());
        }

        sleepQuiet(intensity.sleepBetweenIterationsMs());
      }

      log.info(
          "Operational churn finished: churnInstanceId={} iterationsCompleted={} elapsedMs={}",
          churnInstanceId,
          iteration,
          elapsedMs(startNs));
    } finally {
      MDC.clear();
    }
  }

  private long initAndLogRandomSeed() {
    String prop = System.getProperty("churn.random.seed");
    if (prop != null && !prop.isBlank()) {
      long seed = Long.parseLong(prop.trim());
      log.info("churn.random.seed explicit from -Dchurn.random.seed={}", seed);
      return seed;
    }
    long generated = new Random().nextLong();
    log.info(
        "churn.random.seed generated={} (re-run with -Dchurn.random.seed={} for replay)",
        generated,
        generated);
    return generated;
  }

  private ChurnIntensity resolveIntensityProfile() {
    String profile = System.getProperty("churn.profile", "medium").trim().toLowerCase();
    return switch (profile) {
      case "light" -> new ChurnIntensity(2, 150, 800);
      case "medium" -> new ChurnIntensity(5, 40, 400);
      default -> new ChurnIntensity(5, 40, 400);
    };
  }

  private int resolveMaxDurationMinutes() {
    String p = System.getProperty("churn.maxDurationMinutes");
    if (p == null || p.isBlank()) {
      return DEFAULT_MAX_DURATION_MINUTES;
    }
    return Integer.parseInt(p.trim());
  }

  private long resolveMaxIterations() {
    String p = System.getProperty("churn.maxIterations");
    if (p == null || p.isBlank()) {
      return Long.MAX_VALUE;
    }
    return Long.parseLong(p.trim());
  }

  private static long elapsedMs(long startNs) {
    return (System.nanoTime() - startNs) / 1_000_000L;
  }

  private EnrollmentDeviceContext completeIntegrationEnrollment(
      Integer integrationId, String tenantAdminToken) {
    configureForAdminApi(dockerStackConfig);
    Integer enrollmentId =
        testDataFactory.createEnrollment(integrationId, "Test Device", false, tenantAdminToken);

    Response enrollmentResponse =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + tenantAdminToken)
            .when()
            .get("/enrollments/" + enrollmentId)
            .then()
            .statusCode(200)
            .extract()
            .response();

    String enrollmentProofToken = enrollmentResponse.jsonPath().getString("enrollmentProofToken");
    Integer challengeCode = enrollmentResponse.jsonPath().getInt("enrollmentChallenge");
    assertThat(enrollmentProofToken).isNotNull().isNotEmpty();

    EcP256KeyPair deviceKeyPair = cryptoApiClient.generateKeyPair();
    configureForAuthApi(dockerStackConfig);

    Map<String, Object> bindRequest = new HashMap<>();
    bindRequest.put("enrollmentId", enrollmentId);
    bindRequest.put("enrollmentProofToken", enrollmentProofToken);

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
    assertThat(bindProofToken).isNotNull().isNotEmpty();

    String bindSignature = cryptoApiClient.signData(bindProofToken, deviceKeyPair.privateKey());
    configureForAuthApi(dockerStackConfig);

    Map<String, Object> verifyRequest = new HashMap<>();
    verifyRequest.put("enrollmentId", enrollmentId);
    verifyRequest.put("challengeResponse", challengeCode);
    verifyRequest.put("devicePublicKey", deviceKeyPair.publicKey());
    verifyRequest.put("enrollmentProofTokenSigned", bindSignature);

    given()
        .contentType(ContentType.JSON)
        .body(verifyRequest)
        .when()
        .post("/enrollments/verify")
        .then()
        .statusCode(200);

    return new EnrollmentDeviceContext(enrollmentId, enrollmentProofToken, deviceKeyPair);
  }

  private void runFullAuthAttempt(EnrollmentDeviceContext ctx, String tenantAdminToken) {
    configureForAdminApi(dockerStackConfig);
    Integer authAttemptId =
        testDataFactory.createAuthAttempt(ctx.enrollmentId(), false, tenantAdminToken);

    String deviceProofToken = cryptoApiClient.generateProofToken();
    String deviceProofTokenSigned =
        cryptoApiClient.signData(deviceProofToken, ctx.deviceKeyPair().privateKey());

    configureForAuthApi(dockerStackConfig);

    Map<String, Object> pendingRequest = new HashMap<>();
    pendingRequest.put("enrollmentId", ctx.enrollmentId());
    pendingRequest.put("enrollmentProofToken", ctx.enrollmentProofToken());
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
    assertThat(authAttemptProofToken).isNotNull().isNotEmpty();

    boolean accepted = true;
    String respondPayload = authAttemptProofToken + "|" + (accepted ? "true" : "false");
    String authSignature =
        cryptoApiClient.signData(respondPayload, ctx.deviceKeyPair().privateKey());

    configureForAuthApi(dockerStackConfig);

    Map<String, Object> respondRequest = new HashMap<>();
    respondRequest.put("authAttemptId", authAttemptId);
    respondRequest.put("authAttemptAccepted", true);
    respondRequest.put("authAttemptProofTokenSignedByDevice", authSignature);

    Response respondResponse =
        given()
            .contentType(ContentType.JSON)
            .body(respondRequest)
            .when()
            .post("/auth-attempts/respond")
            .then()
            .statusCode(200)
            .extract()
            .response();

    assertThat(respondResponse.jsonPath().getString("authAttemptResult")).isEqualTo("APPROVED");

    configureForAdminApi(dockerStackConfig);
    Response statusResponse =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + tenantAdminToken)
            .when()
            .get("/auth-attempts/" + authAttemptId)
            .then()
            .statusCode(200)
            .extract()
            .response();

    assertThat(statusResponse.jsonPath().getString("authAttemptStatus")).isEqualTo("ACCEPTED");
  }

  private static void sleepQuiet(int ms) {
    if (ms <= 0) {
      return;
    }
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted during churn sleep", e);
    }
  }
}
