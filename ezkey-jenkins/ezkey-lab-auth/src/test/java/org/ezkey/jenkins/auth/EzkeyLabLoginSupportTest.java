package org.ezkey.jenkins.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import hudson.util.Secret;
import org.ezkey.sdk.AuthAttemptCreateResponse;
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
}
