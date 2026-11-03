/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: GateC1RateLimitThresholds
 * Description: Loads gate-c1 Auth API rate-limit thresholds for #912 C1 tests.
 */
package org.ezkey.tests.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;

/**
 * Test-only thresholds for quality-gate C1 (#912). Values come from {@code
 * ezkey-tests/gate-c1/rate-limit-thresholds.properties} (same numbers as the compose overlay env) —
 * no magic numbers in the assertion body.
 *
 * @author Ezkey contributors
 * @since 2026
 */
public final class GateC1RateLimitThresholds {

  private static final String CLASSPATH_RESOURCE = "gate-c1/rate-limit-thresholds.properties";
  private static final String MODULE_RELATIVE =
      "ezkey-tests/gate-c1/rate-limit-thresholds.properties";

  private final int respondRequests;
  private final int respondWindowMinutes;
  private final int failIpRequests;
  private final int failIpWindowMinutes;
  private final int authApiReplicas;

  /**
   * Creates an immutable threshold snapshot.
   *
   * @param respondRequests post-ownership per-id capacity
   * @param respondWindowMinutes post-ownership window in minutes
   * @param failIpRequests respond fail-IP capacity
   * @param failIpWindowMinutes fail-IP window in minutes
   * @param authApiReplicas HA auth-api replica count used for 429 probe budget
   */
  public GateC1RateLimitThresholds(
      int respondRequests,
      int respondWindowMinutes,
      int failIpRequests,
      int failIpWindowMinutes,
      int authApiReplicas) {
    this.respondRequests = respondRequests;
    this.respondWindowMinutes = respondWindowMinutes;
    this.failIpRequests = failIpRequests;
    this.failIpWindowMinutes = failIpWindowMinutes;
    this.authApiReplicas = authApiReplicas;
  }

  /**
   * Loads thresholds from the gate-c1 properties file (module path, then classpath).
   *
   * @return loaded thresholds
   */
  public static GateC1RateLimitThresholds load() {
    Properties props = new Properties();
    Path modulePath = resolveModulePath();
    if (modulePath != null && Files.isRegularFile(modulePath)) {
      try (Reader reader = Files.newBufferedReader(modulePath, StandardCharsets.UTF_8)) {
        props.load(reader);
      } catch (IOException e) {
        throw new IllegalStateException(
            "Failed to read gate-c1 thresholds at " + modulePath.toAbsolutePath(), e);
      }
    } else {
      try (InputStream in =
          GateC1RateLimitThresholds.class
              .getClassLoader()
              .getResourceAsStream(CLASSPATH_RESOURCE)) {
        if (in == null) {
          throw new IllegalStateException(
              "Missing gate-c1 thresholds (expected "
                  + MODULE_RELATIVE
                  + " or classpath "
                  + CLASSPATH_RESOURCE
                  + ")");
        }
        props.load(in);
      } catch (IOException e) {
        throw new IllegalStateException("Failed to read classpath gate-c1 thresholds", e);
      }
    }
    return fromProperties(props);
  }

  static GateC1RateLimitThresholds fromProperties(Properties props) {
    Objects.requireNonNull(props, "props");
    return new GateC1RateLimitThresholds(
        requirePositiveInt(props, "ezkey.rate-limit.respond.requests"),
        requirePositiveInt(props, "ezkey.rate-limit.respond.window-minutes"),
        requirePositiveInt(props, "ezkey.rate-limit.respond.fail-ip-requests"),
        requirePositiveInt(props, "ezkey.rate-limit.respond.fail-ip-window-minutes"),
        requirePositiveInt(props, "ezkey.gate-c1.auth-api-replicas"));
  }

  private static int requirePositiveInt(Properties props, String key) {
    String raw = props.getProperty(key);
    if (raw == null || raw.isBlank()) {
      throw new IllegalStateException("Missing gate-c1 threshold property: " + key);
    }
    int value;
    try {
      value = Integer.parseInt(raw.trim());
    } catch (NumberFormatException e) {
      throw new IllegalStateException("Invalid integer for " + key + ": " + raw, e);
    }
    if (value < 1) {
      throw new IllegalStateException(key + " must be >= 1 (got " + value + ")");
    }
    return value;
  }

  private static Path resolveModulePath() {
    Path cwd = Path.of("").toAbsolutePath().normalize();
    Path direct = cwd.resolve("gate-c1/rate-limit-thresholds.properties");
    if (Files.isRegularFile(direct)) {
      return direct;
    }
    Path fromRepoRoot = cwd.resolve(MODULE_RELATIVE);
    if (Files.isRegularFile(fromRepoRoot)) {
      return fromRepoRoot;
    }
    String stateDir = System.getProperty("ezkey.test.state.dir", "");
    if (stateDir != null && !stateDir.isBlank()) {
      Path stateParent = Path.of(stateDir).toAbsolutePath().normalize().getParent();
      if (stateParent != null) {
        Path fromStateParent = stateParent.resolve("gate-c1/rate-limit-thresholds.properties");
        if (Files.isRegularFile(fromStateParent)) {
          return fromStateParent;
        }
      }
    }
    return null;
  }

  /**
   * Returns post-ownership respond capacity per authAttemptId.
   *
   * @return requests allowed after ownership in the window
   */
  public int respondRequests() {
    return respondRequests;
  }

  /**
   * Returns post-ownership respond window in minutes.
   *
   * @return window minutes
   */
  public int respondWindowMinutes() {
    return respondWindowMinutes;
  }

  /**
   * Returns respond fail-IP capacity.
   *
   * @return fail-IP requests
   */
  public int failIpRequests() {
    return failIpRequests;
  }

  /**
   * Returns respond fail-IP window in minutes.
   *
   * @return fail-IP window minutes
   */
  public int failIpWindowMinutes() {
    return failIpWindowMinutes;
  }

  /**
   * Returns HA auth-api replica count for the 429 probe budget.
   *
   * @return replica count
   */
  public int authApiReplicas() {
    return authApiReplicas;
  }

  /**
   * Max Retry-After seconds for a deny inside the post-ownership window.
   *
   * @return window length in seconds
   */
  public long respondWindowSeconds() {
    return respondWindowMinutes * 60L;
  }

  /**
   * Max ownership-proven respond probes after the first debit (covers HA in-memory buckets).
   *
   * @return probe attempts until 429 must appear when the limiter is on
   */
  public int ownership429ProbeAttempts() {
    return authApiReplicas * respondRequests + 1;
  }
}
