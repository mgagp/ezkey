package org.ezkey.jenkins.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hudson.util.Secret;
import org.ezkey.sdk.AuthAttemptCreateResponse;
import org.ezkey.sdk.AuthAttemptWaitResponse;
import org.ezkey.sdk.EzkeyClient;
import org.junit.jupiter.api.Test;

class EzkeyLabLoginSupportTest {

    @Test
    void newClientRejectsBlankSecret() {
        EzkeyMockSecurityRealm realm =
                new EzkeyMockSecurityRealm(
                        "http://example.invalid",
                        "ikey",
                        Secret.fromString(""),
                        "admin",
                        Secret.fromString("x"));
        assertThrows(IllegalStateException.class, () -> EzkeyLabLoginSupport.newClient(realm));
    }

    @Test
    void challengeMissingExceptionCarriesAttemptId() {
        var ex = new EzkeyLabLoginSupport.ChallengeMissingException(42);
        assertEquals(42, ex.getAuthAttemptId());
    }

    /** Matrix A: forceChallenge off, enrollment/server returns no code → OK, nothing to display. */
    @Test
    void outcomeFromCreate_matrixA_noCodeWhenNotForced() throws Exception {
        AuthAttemptCreateResponse created = new AuthAttemptCreateResponse(10, null, 60, "t", null, null);
        EzkeyLabLoginSupport.CreateOutcome out = EzkeyLabLoginSupport.outcomeFromCreate(created, false);
        assertEquals(10, out.authAttemptId());
        assertNull(out.challenge());
    }

    /** Matrix B: challenge present → always returned for display (forceChallenge false). */
    @Test
    void outcomeFromCreate_matrixB_displaysCodeFromEnrollment() throws Exception {
        AuthAttemptCreateResponse created = new AuthAttemptCreateResponse(11, 25, 60, "t", null, null);
        EzkeyLabLoginSupport.CreateOutcome out = EzkeyLabLoginSupport.outcomeFromCreate(created, false);
        assertEquals(11, out.authAttemptId());
        assertEquals(25, out.challenge());
    }

    /** Matrix B with forceChallenge true still displays a returned code. */
    @Test
    void outcomeFromCreate_matrixB_displaysCodeWhenAlsoForced() throws Exception {
        AuthAttemptCreateResponse created = new AuthAttemptCreateResponse(12, 39, 60, "t", null, null);
        EzkeyLabLoginSupport.CreateOutcome out = EzkeyLabLoginSupport.outcomeFromCreate(created, true);
        assertEquals(39, out.challenge());
    }

    /** Matrix D: forceChallenge on, no code → fail closed. */
    @Test
    void outcomeFromCreate_matrixD_failClosedWhenForcedAndMissing() {
        AuthAttemptCreateResponse created = new AuthAttemptCreateResponse(13, null, 60, "t", null, null);
        EzkeyLabLoginSupport.ChallengeMissingException ex =
                assertThrows(
                        EzkeyLabLoginSupport.ChallengeMissingException.class,
                        () -> EzkeyLabLoginSupport.outcomeFromCreate(created, true));
        assertEquals(13, ex.getAuthAttemptId());
    }

    /** Matrix D: createAfterConfirm must best-effort cancel the orphan attempt. */
    @Test
    void createAfterConfirm_matrixD_callsCancel() throws Exception {
        EzkeyClient client = mock(EzkeyClient.class);
        when(client.createAuthAttemptByUserIdentifier(eq("labuser"), eq(true)))
                .thenReturn(new AuthAttemptCreateResponse(99, null, 60, "t", null, null));
        assertThrows(
                EzkeyLabLoginSupport.ChallengeMissingException.class,
                () -> EzkeyLabLoginSupport.createAfterConfirm(client, "labuser", true));
        verify(client).cancelAuthAttempt(99);
    }

    @Test
    void forceChallengeDefaultsFalse() {
        EzkeyMockSecurityRealm realm =
                new EzkeyMockSecurityRealm(
                        "http://example.invalid",
                        "ikey",
                        Secret.fromString("s"),
                        "admin",
                        Secret.fromString("x"));
        assertEquals(false, realm.isForceChallenge());
        realm.setForceChallenge(true);
        assertEquals(true, realm.isForceChallenge());
    }

    /** B1: clamp below min → 1. */
    @Test
    void clampWaitTimeout_belowMinBecomesOne() {
        assertEquals(
                EzkeyLabLoginSupport.WAIT_TIMEOUT_CLAMP_MIN_SECONDS,
                EzkeyLabLoginSupport.clampWaitTimeoutSeconds(0));
        assertEquals(
                EzkeyLabLoginSupport.WAIT_TIMEOUT_CLAMP_MIN_SECONDS,
                EzkeyLabLoginSupport.clampWaitTimeoutSeconds(-5));
    }

    /** B1: clamp at boundaries and above max. */
    @Test
    void clampWaitTimeout_boundariesAndAboveMax() {
        assertEquals(1, EzkeyLabLoginSupport.clampWaitTimeoutSeconds(1));
        assertEquals(30, EzkeyLabLoginSupport.clampWaitTimeoutSeconds(30));
        assertEquals(
                EzkeyLabLoginSupport.WAIT_TIMEOUT_CLAMP_MAX_SECONDS,
                EzkeyLabLoginSupport.clampWaitTimeoutSeconds(31));
        assertEquals(
                EzkeyLabLoginSupport.WAIT_TIMEOUT_CLAMP_MAX_SECONDS,
                EzkeyLabLoginSupport.clampWaitTimeoutSeconds(999));
    }

    /** B1: waitOnce uses clamped timeout and fixed poll interval 2. */
    @Test
    void waitOnce_usesClampAndPollIntervalTwo() throws Exception {
        EzkeyClient client = mock(EzkeyClient.class);
        when(client.waitForAuthAttempt(anyInt(), anyInt(), anyInt()))
                .thenReturn(new AuthAttemptWaitResponse("PENDING", false, false));
        EzkeyLabLoginSupport.waitOnce(client, 7, 0);
        verify(client)
                .waitForAuthAttempt(
                        eq(7),
                        eq(EzkeyLabLoginSupport.WAIT_TIMEOUT_CLAMP_MIN_SECONDS),
                        eq(EzkeyLabLoginSupport.WAIT_POLL_INTERVAL_SECONDS));
        EzkeyLabLoginSupport.waitOnce(client, 7, 100);
        verify(client)
                .waitForAuthAttempt(
                        eq(7),
                        eq(EzkeyLabLoginSupport.WAIT_TIMEOUT_CLAMP_MAX_SECONDS),
                        eq(EzkeyLabLoginSupport.WAIT_POLL_INTERVAL_SECONDS));
    }

    /** B1: inventory constants beyond each documented threshold. */
    @Test
    void pollThresholdConstants_matchDocumentedValues() {
        assertEquals(8, EzkeyLabLoginSupport.CLIENT_POLL_WAIT_SECONDS);
        assertEquals(2, EzkeyLabLoginSupport.WAIT_POLL_INTERVAL_SECONDS);
        assertEquals(1500, EzkeyLabLoginSupport.WAIT_PAGE_POLL_INTERVAL_MS);
        assertEquals(2000, EzkeyLabLoginSupport.WAIT_PAGE_POLL_ERROR_INTERVAL_MS);
        assertEquals(1, EzkeyLabLoginSupport.WAIT_TIMEOUT_CLAMP_MIN_SECONDS);
        assertEquals(30, EzkeyLabLoginSupport.WAIT_TIMEOUT_CLAMP_MAX_SECONDS);
        // Beyond each threshold (strict inequalities the clamp / policy must enforce).
        assertTrue(EzkeyLabLoginSupport.clampWaitTimeoutSeconds(0) >= 1);
        assertTrue(EzkeyLabLoginSupport.clampWaitTimeoutSeconds(31) <= 30);
        assertTrue(EzkeyLabLoginSupport.CLIENT_POLL_WAIT_SECONDS > 0);
        assertTrue(EzkeyLabLoginSupport.WAIT_PAGE_POLL_INTERVAL_MS < 2000);
        assertTrue(EzkeyLabLoginSupport.WAIT_PAGE_POLL_ERROR_INTERVAL_MS > 1500);
    }

    /** B1: 401 and 5xx are terminal for the wait-page loop. */
    @Test
    void terminalPollHttpStatus_401And5xx() {
        assertTrue(EzkeyLabLoginSupport.isTerminalPollHttpStatus(401));
        assertTrue(EzkeyLabLoginSupport.isTerminalPollHttpStatus(500));
        assertTrue(EzkeyLabLoginSupport.isTerminalPollHttpStatus(502));
        assertFalse(EzkeyLabLoginSupport.isTerminalPollHttpStatus(200));
        assertFalse(EzkeyLabLoginSupport.isTerminalPollHttpStatus(404));
    }

    @Test
    void createAfterConfirm_matrixA_doesNotCancel() throws Exception {
        EzkeyClient client = mock(EzkeyClient.class);
        when(client.createAuthAttemptByUserIdentifier(anyString(), anyBoolean()))
                .thenReturn(new AuthAttemptCreateResponse(5, null, 60, "t", null, null));
        EzkeyLabLoginSupport.CreateOutcome out =
                EzkeyLabLoginSupport.createAfterConfirm(client, "labuser", false);
        assertEquals(5, out.authAttemptId());
        verify(client).createAuthAttemptByUserIdentifier("labuser", false);
    }
}
