/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * SDK: EzkeyClient
 * Description: Main entry point for the Ezkey Java SDK. Provides M2M authentication operations
 *              using the Ezkey Admin API with API key credentials.
 */

package org.ezkey.sdk;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Main entry point for the Ezkey Java SDK.
 *
 * <p>Provides M2M (machine-to-machine) authentication operations against the Ezkey Admin API using
 * API key credentials (integration key + secret key) transmitted via HTTP Basic Auth.
 *
 * <p>This client is <strong>immutable</strong>, <strong>thread-safe</strong>, and designed to be
 * created once and reused. It uses {@link java.net.http.HttpClient} internally with zero external
 * dependencies.
 *
 * <h2>Usage Examples</h2>
 *
 * <p><strong>Simple construction (80% use case):</strong>
 *
 * <pre>{@code
 * EzkeyClient client = new EzkeyClient("ezkey_ikey_xxx", "ezkey_skey_xxx");
 * }</pre>
 *
 * <p><strong>Builder for full configuration:</strong>
 *
 * <pre>{@code
 * EzkeyClient client = EzkeyClient.builder()
 *     .integrationKey("ezkey_ikey_xxx")
 *     .secretKey("ezkey_skey_xxx")
 *     .baseUrl("https://ezkey.example.com:9080")
 *     .connectTimeout(Duration.ofSeconds(15))
 *     .readTimeout(Duration.ofSeconds(60))
 *     .build();
 * }</pre>
 *
 * <p><strong>From environment variables:</strong>
 *
 * <pre>{@code
 * EzkeyClient client = EzkeyClient.fromEnvironment();
 * }</pre>
 *
 * @since 2025
 */
public final class EzkeyClient {

  private static final System.Logger LOG = System.getLogger(EzkeyClient.class.getName());

  private static final String AUTH_ATTEMPTS_PATH = "/api/v1/auth-attempts";
  private static final String CONTENT_TYPE_JSON = "application/json";

  /** Environment variable name for the integration key. */
  public static final String ENV_INTEGRATION_KEY = "EZKEY_INTEGRATION_KEY";

  /** Environment variable name for the secret key. */
  public static final String ENV_SECRET_KEY = "EZKEY_SECRET_KEY";

  /** Environment variable name for the base URL. */
  public static final String ENV_BASE_URL = "EZKEY_BASE_URL";

  private final EzkeyConfig config;
  private final HttpClient httpClient;
  private final String authorizationHeader;

  /**
   * Creates a new client with the given integration key and secret key, using default base URL and
   * timeouts.
   *
   * @param integrationKey the public integration key (e.g. {@code ezkey_ikey_xxx})
   * @param secretKey the secret key (e.g. {@code ezkey_skey_xxx})
   */
  public EzkeyClient(String integrationKey, String secretKey) {
    this(EzkeyConfig.of(integrationKey, secretKey));
  }

  /**
   * Creates a new client with the given configuration.
   *
   * @param config the SDK configuration
   * @throws NullPointerException if config is null
   */
  public EzkeyClient(EzkeyConfig config) {
    this.config = Objects.requireNonNull(config, "config must not be null");
    this.httpClient = HttpClient.newBuilder().connectTimeout(config.connectTimeout()).build();
    this.authorizationHeader = buildAuthorizationHeader(config);

    LOG.log(
        System.Logger.Level.INFO,
        "EzkeyClient initialized: baseUrl={0}, integrationKey={1}...",
        config.baseUrl(),
        config.integrationKey().substring(0, Math.min(20, config.integrationKey().length())));
  }

  /**
   * Creates a new client from environment variables.
   *
   * <p>Reads:
   *
   * <ul>
   *   <li>{@code EZKEY_INTEGRATION_KEY} (required)
   *   <li>{@code EZKEY_SECRET_KEY} (required)
   *   <li>{@code EZKEY_BASE_URL} (optional, defaults to {@code http://localhost:9080})
   * </ul>
   *
   * @return a new client configured from environment variables
   * @throws IllegalStateException if required environment variables are missing
   */
  public static EzkeyClient fromEnvironment() {
    String integrationKey = System.getenv(ENV_INTEGRATION_KEY);
    String secretKey = System.getenv(ENV_SECRET_KEY);
    String baseUrl = System.getenv(ENV_BASE_URL);

    if (integrationKey == null || integrationKey.isBlank()) {
      throw new IllegalStateException(
          "Environment variable " + ENV_INTEGRATION_KEY + " is required but not set");
    }
    if (secretKey == null || secretKey.isBlank()) {
      throw new IllegalStateException(
          "Environment variable " + ENV_SECRET_KEY + " is required but not set");
    }

    var builder = builder().integrationKey(integrationKey).secretKey(secretKey);
    if (baseUrl != null && !baseUrl.isBlank()) {
      builder.baseUrl(baseUrl);
    }
    return builder.build();
  }

  /**
   * Returns a new builder for configuring an {@link EzkeyClient}.
   *
   * @return a new builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Gets the configuration used by this client.
   *
   * @return the immutable configuration
   */
  public EzkeyConfig getConfig() {
    return config;
  }

  // ---------------------------------------------------------------------------
  // M2M Authentication Operations
  // ---------------------------------------------------------------------------

  /**
   * Creates a new authentication attempt for the given enrollment.
   *
   * <p>Sends a {@code POST /api/v1/auth-attempts} request to the Admin API.
   *
   * @param enrollmentId the enrollment ID to authenticate
   * @param challengeRequested whether a challenge code should be generated
   * @return the created auth attempt details including ID, optional challenge code, and timeout
   * @throws EzkeyException if the request fails (network error, HTTP error, or invalid response)
   */
  public AuthAttemptCreateResponse createAuthAttempt(int enrollmentId, boolean challengeRequested)
      throws EzkeyException {

    LOG.log(
        System.Logger.Level.DEBUG,
        "Creating auth attempt: enrollmentId={0}, challengeRequested={1}",
        enrollmentId,
        challengeRequested);

    // Build JSON request body
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("enrollmentId", enrollmentId);
    body.put("challengeRequested", challengeRequested);
    String jsonBody = JsonHelper.toJson(body);

    // Execute POST
    String responseBody = executePost(AUTH_ATTEMPTS_PATH, jsonBody, 201);

    // Parse response
    Map<String, String> fields = JsonHelper.parseObject(responseBody);
    var response =
        new AuthAttemptCreateResponse(
            JsonHelper.getInt(fields, "authAttemptId", 0),
            JsonHelper.getInteger(fields, "authAttemptChallenge"),
            JsonHelper.getInt(fields, "timeoutSeconds", 120),
            JsonHelper.getString(fields, "expiresAt"));

    LOG.log(
        System.Logger.Level.INFO,
        "Auth attempt created: authAttemptId={0}, challenge={1}",
        response.authAttemptId(),
        response.authAttemptChallenge() != null ? response.authAttemptChallenge() : "none");

    return response;
  }

  /**
   * Waits for an authentication attempt to complete.
   *
   * <p>Sends a {@code GET /api/v1/auth-attempts/{id}/wait} request to the Admin API. This is a
   * long-polling request that blocks until the attempt is completed (approved/rejected/expired) or
   * the server-side timeout is reached.
   *
   * @param authAttemptId the authentication attempt ID
   * @param timeoutSeconds server-side maximum wait duration (1-300, default 30)
   * @param pollingSeconds server-side polling interval (1-60, default 2)
   * @return the wait result containing final status and completion information
   * @throws EzkeyException if the request fails
   */
  public AuthAttemptWaitResponse waitForAuthAttempt(
      int authAttemptId, int timeoutSeconds, int pollingSeconds) throws EzkeyException {

    LOG.log(
        System.Logger.Level.DEBUG,
        "Waiting for auth attempt: authAttemptId={0}, timeout={1}s, polling={2}s",
        authAttemptId,
        timeoutSeconds,
        pollingSeconds);

    String path =
        AUTH_ATTEMPTS_PATH
            + "/"
            + authAttemptId
            + "/wait?timeout="
            + timeoutSeconds
            + "&polling="
            + pollingSeconds;

    String responseBody = executeGet(path, 200);

    Map<String, String> fields = JsonHelper.parseObject(responseBody);
    var response =
        new AuthAttemptWaitResponse(
            JsonHelper.getString(fields, "status"),
            JsonHelper.getBoolean(fields, "completed", false),
            JsonHelper.getBoolean(fields, "timeoutReached", false));

    LOG.log(
        System.Logger.Level.INFO,
        "Auth attempt wait result: authAttemptId={0}, status={1}, completed={2}",
        authAttemptId,
        response.status(),
        response.completed());

    return response;
  }

  /**
   * Cancels a pending authentication attempt.
   *
   * <p>Sends a {@code POST /api/v1/auth-attempts/{id}/cancel} request to the Admin API. Only
   * attempts in PENDING or READ status can be cancelled.
   *
   * @param authAttemptId the authentication attempt ID to cancel
   * @return the cancelled auth attempt details
   * @throws EzkeyException if the request fails or the attempt is already in a final state
   */
  public AuthAttemptCancelResponse cancelAuthAttempt(int authAttemptId) throws EzkeyException {

    LOG.log(System.Logger.Level.DEBUG, "Cancelling auth attempt: authAttemptId={0}", authAttemptId);

    String path = AUTH_ATTEMPTS_PATH + "/" + authAttemptId + "/cancel";
    String responseBody = executePost(path, null, 200);

    Map<String, String> fields = JsonHelper.parseObject(responseBody);
    var response =
        new AuthAttemptCancelResponse(
            JsonHelper.getInt(fields, "authAttemptId", authAttemptId),
            JsonHelper.getString(fields, "authAttemptStatus"));

    LOG.log(
        System.Logger.Level.INFO,
        "Auth attempt cancelled: authAttemptId={0}, status={1}",
        response.authAttemptId(),
        response.authAttemptStatus());

    return response;
  }

  // ---------------------------------------------------------------------------
  // HTTP internals
  // ---------------------------------------------------------------------------

  /**
   * Executes a POST request and returns the response body.
   *
   * @param path the API path (appended to base URL)
   * @param jsonBody the JSON request body, or {@code null} for empty body
   * @param expectedStatus the expected HTTP status code
   * @return the response body as a string
   * @throws EzkeyException if the request fails or returns an unexpected status
   */
  private String executePost(String path, String jsonBody, int expectedStatus)
      throws EzkeyException {
    URI uri = URI.create(config.baseUrl() + path);

    var requestBuilder =
        HttpRequest.newBuilder()
            .uri(uri)
            .timeout(config.readTimeout())
            .header("Authorization", authorizationHeader)
            .header("Accept", CONTENT_TYPE_JSON);

    if (jsonBody != null) {
      requestBuilder
          .header("Content-Type", CONTENT_TYPE_JSON)
          .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
    } else {
      requestBuilder.POST(HttpRequest.BodyPublishers.noBody());
    }

    return executeRequest(requestBuilder.build(), expectedStatus);
  }

  /**
   * Executes a GET request and returns the response body.
   *
   * @param path the API path (appended to base URL)
   * @param expectedStatus the expected HTTP status code
   * @return the response body as a string
   * @throws EzkeyException if the request fails or returns an unexpected status
   */
  private String executeGet(String path, int expectedStatus) throws EzkeyException {
    URI uri = URI.create(config.baseUrl() + path);

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(uri)
            .timeout(config.readTimeout())
            .header("Authorization", authorizationHeader)
            .header("Accept", CONTENT_TYPE_JSON)
            .GET()
            .build();

    return executeRequest(request, expectedStatus);
  }

  /**
   * Executes an HTTP request and handles errors.
   *
   * @param request the HTTP request
   * @param expectedStatus the expected successful HTTP status code
   * @return the response body as a string
   * @throws EzkeyException if the request fails
   */
  private String executeRequest(HttpRequest request, int expectedStatus) throws EzkeyException {
    try {
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

      int status = response.statusCode();
      String body = response.body();

      if (status == expectedStatus) {
        return body;
      }

      // Build descriptive error message
      String message =
          switch (status) {
            case 401 -> "Authentication failed (401). Check API key credentials and IP whitelist.";
            case 403 ->
                "Access denied (403). The API key may not have permission for this operation.";
            case 404 -> "Resource not found (404). Check the auth attempt ID or base URL.";
            case 400 -> "Bad request (400). " + extractErrorMessage(body);
            case 429 -> "Rate limit exceeded (429). Retry after a delay.";
            default -> "Unexpected HTTP status " + status + " from " + request.uri();
          };

      throw new EzkeyException(message, status, body);

    } catch (IOException e) {
      throw new EzkeyException(
          "Network error connecting to Ezkey at " + config.baseUrl() + ": " + e.getMessage(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new EzkeyException("Request interrupted", e);
    }
  }

  /**
   * Builds the HTTP Basic Authorization header value.
   *
   * @param config the configuration containing credentials
   * @return the {@code Basic base64(integrationKey:secretKey)} header value
   */
  private static String buildAuthorizationHeader(EzkeyConfig config) {
    String credentials = config.integrationKey() + ":" + config.secretKey();
    String encoded =
        Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    return "Basic " + encoded;
  }

  /**
   * Attempts to extract an error message from a JSON error response body.
   *
   * <p>Supports multiple error response formats (in priority order):
   *
   * <ul>
   *   <li><strong>RFC 9457 "detail":</strong> Specific, business-logic error message (most
   *       preferred)
   *   <li><strong>RFC 9457 "title":</strong> Error category/title (fallback)
   *   <li><strong>Legacy "message":</strong> Legacy API error message (for backward compatibility)
   *   <li><strong>Raw body:</strong> If no standard fields found, returns truncated response body
   * </ul>
   *
   * @param body the response body
   * @return extracted message or the raw body (truncated to 200 chars if too long)
   */
  private static String extractErrorMessage(String body) {
    if (body == null || body.isBlank()) {
      return "No details provided.";
    }
    try {
      Map<String, String> fields = JsonHelper.parseObject(body);

      // RFC 9457 support: Try 'detail' field first (most specific - business logic error)
      String detail = fields.get("detail");
      if (detail != null && !detail.isBlank()) {
        return detail;
      }

      // RFC 9457 fallback: Try 'title' field (error category)
      String title = fields.get("title");
      if (title != null && !title.isBlank()) {
        return title;
      }

      // Legacy support: Try 'message' field
      String message = fields.get("message");
      if (message != null && !message.isBlank()) {
        return message;
      }
    } catch (EzkeyException ignored) {
      // Not JSON, fall through to raw body
    }
    return body.length() > 200 ? body.substring(0, 200) + "..." : body;
  }

  // ---------------------------------------------------------------------------
  // Builder
  // ---------------------------------------------------------------------------

  /**
   * Builder for constructing an {@link EzkeyClient} with full configuration control.
   *
   * @since 2025
   */
  public static final class Builder {

    private String integrationKey;
    private String secretKey;
    private String baseUrl = EzkeyConfig.DEFAULT_BASE_URL;
    private Duration connectTimeout = EzkeyConfig.DEFAULT_CONNECT_TIMEOUT;
    private Duration readTimeout = EzkeyConfig.DEFAULT_READ_TIMEOUT;

    Builder() {}

    /**
     * Sets the integration key.
     *
     * @param pIintegrationKey the public integration key (e.g. {@code ezkey_ikey_xxx})
     * @return this builder
     */
    public Builder integrationKey(String pIintegrationKey) {
      this.integrationKey = pIintegrationKey;
      return this;
    }

    /**
     * Sets the secret key.
     *
     * @param pSecretKey the secret key (e.g. {@code ezkey_skey_xxx})
     * @return this builder
     */
    public Builder secretKey(String pSecretKey) {
      this.secretKey = pSecretKey;
      return this;
    }

    /**
     * Sets the Admin API base URL.
     *
     * @param paseUrl the base URL (e.g. {@code https://ezkey.example.com:9080})
     * @return this builder
     */
    public Builder baseUrl(String paseUrl) {
      this.baseUrl = paseUrl;
      return this;
    }

    /**
     * Sets the HTTP connection timeout.
     *
     * @param pConnectTimeout the connection timeout
     * @return this builder
     */
    public Builder connectTimeout(Duration pConnectTimeout) {
      this.connectTimeout = pConnectTimeout;
      return this;
    }

    /**
     * Sets the HTTP read/response timeout.
     *
     * @param pReadTimeout the read timeout
     * @return this builder
     */
    public Builder readTimeout(Duration pReadTimeout) {
      this.readTimeout = pReadTimeout;
      return this;
    }

    /**
     * Builds the {@link EzkeyClient} with the configured parameters.
     *
     * @return a new immutable client
     * @throws NullPointerException if required parameters are null
     * @throws IllegalArgumentException if required parameters are blank
     */
    public EzkeyClient build() {
      return new EzkeyClient(
          new EzkeyConfig(baseUrl, integrationKey, secretKey, connectTimeout, readTimeout));
    }
  }
}
