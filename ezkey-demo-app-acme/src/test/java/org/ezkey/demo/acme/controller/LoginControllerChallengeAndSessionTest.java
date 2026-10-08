/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: LoginControllerChallengeAndSessionTest
 * Description: Challenge always on, slot label, invalidate on paste, changeSessionId on ACCEPTED.
 */

package org.ezkey.demo.acme.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.ezkey.demo.acme.DemoAuthMessages;
import org.ezkey.demo.acme.config.EzkeyClientProvider;
import org.ezkey.demo.acme.dto.AuthenticatedUser;
import org.ezkey.demo.acme.security.DemoRateLimitService;
import org.ezkey.demo.acme.service.AccessCodeService;
import org.ezkey.demo.acme.service.DemoApiKeyConfigService;
import org.ezkey.sdk.AuthAttemptCreateResponse;
import org.ezkey.sdk.AuthAttemptWaitResponse;
import org.ezkey.sdk.EzkeyClient;
import org.ezkey.sdk.EzkeyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class LoginControllerChallengeAndSessionTest {

  private EzkeyClientProvider ezkeyClientProvider;
  private DemoApiKeyConfigService demoApiKeyConfigService;
  private DemoRateLimitService demoRateLimitService;
  private AccessCodeService accessCodeService;
  private EzkeyClient ezkeyClient;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    ezkeyClientProvider = mock(EzkeyClientProvider.class);
    demoApiKeyConfigService = mock(DemoApiKeyConfigService.class);
    demoRateLimitService = mock(DemoRateLimitService.class);
    accessCodeService = mock(AccessCodeService.class);
    ezkeyClient = mock(EzkeyClient.class);
    when(demoRateLimitService.checkLogin(any()))
        .thenReturn(new DemoRateLimitService.RateLimitDecision(true, 0, "127.0.0.1"));
    when(demoRateLimitService.checkApplyApiKey(any()))
        .thenReturn(new DemoRateLimitService.RateLimitDecision(true, 0, "127.0.0.1"));
    when(ezkeyClientProvider.getClient(any())).thenReturn(ezkeyClient);

    LoginController controller =
        new LoginController(
            ezkeyClientProvider, demoApiKeyConfigService, demoRateLimitService, accessCodeService);
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  @Test
  void shouldForceChallengeRequestedTrueEvenWithoutFormField() throws Exception {
    when(ezkeyClient.createAuthAttemptByUserIdentifier(eq("alice"), eq(true)))
        .thenReturn(sampleCreateResponse(42, 12));

    mockMvc
        .perform(post("/login").param("username", "alice"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/challenge-wait"));

    verify(ezkeyClient).createAuthAttemptByUserIdentifier("alice", true);
    verify(ezkeyClient, never()).createAuthAttemptByUserIdentifier(anyString(), eq(false));
  }

  @Test
  void shouldShowSlotLabelOnLogin() {
    MockHttpSession session = new MockHttpSession();
    when(demoApiKeyConfigService.getActiveSlotId(session)).thenReturn("northwind");
    when(accessCodeService.getLabel("northwind")).thenReturn("Northwind Portal");
    LoginController controller =
        new LoginController(
            ezkeyClientProvider, demoApiKeyConfigService, demoRateLimitService, accessCodeService);
    org.springframework.ui.ExtendedModelMap model = new org.springframework.ui.ExtendedModelMap();

    String view = controller.loginPage(null, null, session, model);

    assertThat(view).isEqualTo("login");
    assertThat(model.get("loginHeading")).isEqualTo("Sign in to Northwind Portal");
    assertThat(model.get("slotLabel")).isEqualTo("Northwind Portal");
  }

  @Test
  void shouldUseGenericErrorForSdkFailures() throws Exception {
    when(ezkeyClient.createAuthAttemptByUserIdentifier(anyString(), anyBoolean()))
        .thenThrow(new EzkeyException("User not found in integration XYZ"));

    mockMvc
        .perform(post("/login").param("username", "nobody"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login?error=authfailed"));
  }

  @Test
  void shouldInvalidateSessionWhenApplyingPastedKeys() throws Exception {
    MockHttpSession prior = new MockHttpSession();
    prior.setAttribute(DemoApiKeyConfigService.SESSION_ACCESS_CODE_SLOT_ID, "northwind");
    when(demoApiKeyConfigService.applyApiKey(any(), eq("ikey"), eq("skey"))).thenReturn(true);

    mockMvc
        .perform(
            post("/api/apply-api-key")
                .session(prior)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"integrationKey\":\"ikey\",\"secretKey\":\"skey\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    assertThat(prior.isInvalid()).isTrue();
  }

  @Test
  void shouldRejectBlankApiKeysWithoutInvalidatingSession() throws Exception {
    MockHttpSession prior = new MockHttpSession();
    prior.setAttribute(DemoApiKeyConfigService.SESSION_ACCESS_CODE_SLOT_ID, "northwind");

    mockMvc
        .perform(
            post("/api/apply-api-key")
                .session(prior)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"integrationKey\":\"\",\"secretKey\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));

    assertThat(prior.isInvalid()).isFalse();
    verify(demoApiKeyConfigService, never()).applyApiKey(any(), anyString(), anyString());
  }

  @Test
  void shouldChangeSessionIdOnAccepted() throws Exception {
    CountingSession session = pendingSession();

    when(ezkeyClient.waitForAuthAttempt(eq(7), anyInt(), anyInt()))
        .thenReturn(new AuthAttemptWaitResponse("ACCEPTED", true, false));

    String beforeId = session.getId();
    mockMvc
        .perform(get("/api/auth-status").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("accepted"));

    assertThat(session.getId()).isNotEqualTo(beforeId);
    assertThat(session.rotationCount.get()).isEqualTo(1);
    assertThat(session.getAttribute("user")).isInstanceOf(AuthenticatedUser.class);
    assertThat(session.isInvalid()).isFalse();
  }

  @Test
  void concurrentAcceptedPollsRotateSessionExactlyOnce() throws Exception {
    CountingSession session = pendingSession();
    session.setAttribute(DemoApiKeyConfigService.SESSION_ACCESS_CODE_SLOT_ID, "northwind");

    CountDownLatch enteredWait = new CountDownLatch(3);
    when(ezkeyClient.waitForAuthAttempt(eq(7), anyInt(), anyInt()))
        .thenAnswer(
            _invocation -> {
              enteredWait.countDown();
              assertThat(enteredWait.await(5, TimeUnit.SECONDS))
                  .as("at least 3 polls should be in waitForAuthAttempt together")
                  .isTrue();
              return new AuthAttemptWaitResponse("ACCEPTED", true, false);
            });

    ExecutorService pool = Executors.newFixedThreadPool(3);
    List<Future<?>> futures = new ArrayList<>();
    try {
      for (int i = 0; i < 3; i++) {
        futures.add(
            pool.submit(
                () -> {
                  try {
                    mockMvc
                        .perform(get("/api/auth-status").session(session))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.status").value("accepted"))
                        .andExpect(jsonPath("$.redirectUrl").value("/dashboard"));
                  } catch (Exception e) {
                    throw new RuntimeException(e);
                  }
                }));
      }
      for (Future<?> future : futures) {
        future.get(15, TimeUnit.SECONDS);
      }
    } finally {
      pool.shutdownNow();
    }

    assertThat(session.rotationCount.get()).isEqualTo(1);
    assertThat(session.isInvalid()).isFalse();
    assertThat(session.getAttribute("user")).isInstanceOf(AuthenticatedUser.class);
    assertThat(session.getAttribute(DemoApiKeyConfigService.SESSION_ACCESS_CODE_SLOT_ID))
        .isEqualTo("northwind");
    assertThat(session.getAttribute(LoginController.AUTH_ACCEPTED_SESSION_ROTATED))
        .isEqualTo(Boolean.TRUE);

    // Late poll after success must not rotate again or drop the user.
    String idAfter = session.getId();
    mockMvc
        .perform(get("/api/auth-status").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("accepted"));
    assertThat(session.rotationCount.get()).isEqualTo(1);
    assertThat(session.getId()).isEqualTo(idAfter);
    assertThat(session.getAttribute("user")).isInstanceOf(AuthenticatedUser.class);
  }

  @Test
  void shouldShowReopenAccessLinkWhenSessionOrSlotLost() throws Exception {
    LoginController controller =
        new LoginController(
            ezkeyClientProvider, demoApiKeyConfigService, demoRateLimitService, accessCodeService);
    org.springframework.ui.ExtendedModelMap model = new org.springframework.ui.ExtendedModelMap();
    MockHttpSession loginSession = new MockHttpSession();

    String view = controller.loginPage("sessionexpired", null, loginSession, model);

    assertThat(view).isEqualTo("login");
    assertThat(model.get("hasError")).isEqualTo(true);
    assertThat(model.get("error")).isEqualTo(DemoAuthMessages.SESSION_OR_SLOT_LOST);

    MockHttpSession empty = new MockHttpSession();
    mockMvc
        .perform(get("/api/auth-status").session(empty))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("expired"))
        .andExpect(jsonPath("$.redirectUrl").value("/login?error=sessionexpired"))
        .andExpect(jsonPath("$.message").value(DemoAuthMessages.SESSION_OR_SLOT_LOST));
  }

  @Test
  void httpServerErrorWithTimeoutInMessageIsNotTreatedAsPending() throws Exception {
    CountingSession session = pendingSession();
    session.setAttribute("pendingExpiresAt", OffsetDateTime.now().plusSeconds(120).toString());

    // Mimics SDK HTTP 500 whose message/body/URL mention "timeout" (wait?timeout=20).
    when(ezkeyClient.waitForAuthAttempt(eq(7), anyInt(), anyInt()))
        .thenThrow(
            new EzkeyException(
                "Unexpected HTTP status 500 from"
                    + " http://integration-api:7080/api/v1/auth-attempts/7/wait?timeout=20&polling=2",
                500,
                "{\"detail\":\"upstream timeout while proxying\"}"));

    mockMvc
        .perform(get("/api/auth-status").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("error"))
        .andExpect(jsonPath("$.redirectUrl").value("/login?error=authfailed"))
        .andExpect(jsonPath("$.message").value(DemoAuthMessages.GENERIC_SIGN_IN_FAILED));

    assertThat(session.getAttribute("authAttemptFinalStatus")).isEqualTo("ERROR");
    assertThat(
            LoginController.isTransientWaitFailure(
                new EzkeyException(
                    "Unexpected HTTP status 500 from …/wait?timeout=20", 500, "timeout")))
        .isFalse();
  }

  @Test
  void realReadTimeoutKeepsPendingUntilAttemptTtl() throws Exception {
    CountingSession session = pendingSession();
    session.setAttribute("pendingExpiresAt", OffsetDateTime.now().plusSeconds(120).toString());

    when(ezkeyClient.waitForAuthAttempt(eq(7), anyInt(), anyInt()))
        .thenThrow(
            new EzkeyException(
                "Read timeout waiting for Ezkey at http://integration-api:7080",
                new HttpTimeoutException("request timed out")));

    mockMvc
        .perform(get("/api/auth-status").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("pending"));

    assertThat(session.getAttribute("authAttemptFinalStatus")).isNull();
    assertThat(
            LoginController.isTransientWaitFailure(
                new EzkeyException("x", new HttpTimeoutException("t"))))
        .isTrue();
  }

  @Test
  void slowApprovalAroundFortySecondsReachesDashboard() throws Exception {
    CountingSession session = pendingSession();
    // Attempt TTL far enough that soft timeouts must keep polling (not abort at ~30s).
    session.setAttribute("pendingExpiresAt", OffsetDateTime.now().plusSeconds(120).toString());

    AtomicInteger waitCalls = new AtomicInteger();
    when(ezkeyClient.waitForAuthAttempt(
            eq(7),
            eq(LoginController.AUTH_STATUS_WAIT_SECONDS),
            eq(LoginController.AUTH_STATUS_POLL_SECONDS)))
        .thenAnswer(
            _invocation -> {
              int call = waitCalls.incrementAndGet();
              if (call == 1) {
                // ~20s wall: SDK read-timeout race (previously aborted challenge-wait).
                throw new EzkeyException(
                    "Network error connecting to Ezkey: request timed out",
                    new HttpTimeoutException("request timed out"));
              }
              if (call == 2) {
                // ~40s wall: still pending after another server wait window.
                return new AuthAttemptWaitResponse("PENDING", false, true);
              }
              return new AuthAttemptWaitResponse("ACCEPTED", true, false);
            });

    mockMvc
        .perform(get("/api/auth-status").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("pending"));
    mockMvc
        .perform(get("/api/auth-status").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("pending"));
    mockMvc
        .perform(get("/api/auth-status").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("accepted"))
        .andExpect(jsonPath("$.redirectUrl").value("/dashboard"));

    assertThat(waitCalls.get()).isEqualTo(3);
    assertThat(session.getAttribute("user")).isInstanceOf(AuthenticatedUser.class);
    assertThat(session.getAttribute("authAttemptFinalStatus")).isNotEqualTo("ERROR");
    verify(ezkeyClient, atLeastOnce())
        .waitForAuthAttempt(
            eq(7),
            eq(LoginController.AUTH_STATUS_WAIT_SECONDS),
            eq(LoginController.AUTH_STATUS_POLL_SECONDS));
  }

  @Test
  void shouldRetryRotationIfChangeSessionIdFailsBeforeMarkingAccepted() throws Exception {
    AtomicInteger attempts = new AtomicInteger();
    CountingSession session =
        new CountingSession() {
          @Override
          public String changeSessionId() {
            if (attempts.incrementAndGet() == 1) {
              throw new IllegalStateException("simulated rotation failure");
            }
            return super.changeSessionId();
          }
        };
    session.setAttribute("pendingAuthAttemptId", 7);
    session.setAttribute("pendingUsername", "alice");
    session.setAttribute("pendingDisplayName", "Alice");
    session.setAttribute("pendingEnrollmentId", 3);

    when(ezkeyClient.waitForAuthAttempt(eq(7), anyInt(), anyInt()))
        .thenReturn(new AuthAttemptWaitResponse("ACCEPTED", true, false));

    try {
      mockMvc.perform(get("/api/auth-status").session(session));
    } catch (Exception ignored) {
      // first poll: rotation throws before user/rotated flags are set
    }

    assertThat(session.getAttribute("user")).isNull();
    assertThat(session.getAttribute(LoginController.AUTH_ACCEPTED_SESSION_ROTATED)).isNull();

    mockMvc
        .perform(get("/api/auth-status").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("accepted"));

    assertThat(attempts.get()).isEqualTo(2);
    assertThat(session.getAttribute("user")).isInstanceOf(AuthenticatedUser.class);
    assertThat(session.getAttribute(LoginController.AUTH_ACCEPTED_SESSION_ROTATED))
        .isEqualTo(Boolean.TRUE);
  }

  @Test
  void challengeWaitPollsSeriallyWithSetTimeout() throws Exception {
    Path source = resolveChallengeWaitHtml();
    String content = Files.readString(source, StandardCharsets.UTF_8);
    assertThat(content).contains("scheduleNextPoll");
    assertThat(content).contains("setTimeout(checkAuthStatus");
    assertThat(content).contains("if (!response.ok)");
    assertThat(content).contains("showError('Authentication rejected by user.');");
    assertThat(content).doesNotContain("Authentication rejected by user. ' + (result.message");
    assertThat(content).doesNotContain("setInterval(checkAuthStatus");
    assertThat(content).doesNotContain("pollingInterval = setInterval");
  }

  private static AuthAttemptCreateResponse sampleCreateResponse(int id, Integer challenge) {
    return new AuthAttemptCreateResponse(id, challenge, 120, "2026-10-08T12:00:00Z", null, null);
  }

  private static CountingSession pendingSession() {
    CountingSession session = new CountingSession();
    session.setAttribute("pendingAuthAttemptId", 7);
    session.setAttribute("pendingUsername", "alice");
    session.setAttribute("pendingDisplayName", "Alice");
    session.setAttribute("pendingEnrollmentId", 3);
    return session;
  }

  private static Path resolveChallengeWaitHtml() {
    Path fromModule =
        Path.of("src/main/resources/templates/challenge-wait.html").toAbsolutePath().normalize();
    if (Files.exists(fromModule)) {
      return fromModule;
    }
    Path fromRepo =
        Path.of("ezkey-demo-app-acme/src/main/resources/templates/challenge-wait.html")
            .toAbsolutePath()
            .normalize();
    assertThat(fromRepo).exists();
    return fromRepo;
  }

  /** Counts {@link MockHttpSession#changeSessionId()} invocations for race tests. */
  private static class CountingSession extends MockHttpSession {
    private final AtomicInteger rotationCount = new AtomicInteger();

    @Override
    public String changeSessionId() {
      rotationCount.incrementAndGet();
      return super.changeSessionId();
    }
  }

  @Test
  void shouldNotEchoRawSdkMessageInAuthStatusError() throws Exception {
    MockHttpSession session = new MockHttpSession();
    session.setAttribute("pendingAuthAttemptId", 7);
    session.setAttribute("pendingUsername", "alice");
    when(ezkeyClient.waitForAuthAttempt(eq(7), anyInt(), anyInt()))
        .thenThrow(new EzkeyException("secret detail from SDK"));

    mockMvc
        .perform(get("/api/auth-status").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value(DemoAuthMessages.GENERIC_SIGN_IN_FAILED));
  }
}
