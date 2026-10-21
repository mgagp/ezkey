package org.ezkey.jenkins.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import hudson.util.Secret;
import org.junit.jupiter.api.Test;

class EzkeyLabLoginSupportTest {

    @Test
    void newClientRejectsBlankSecret() {
        EzkeyMockSecurityRealm realm =
                new EzkeyMockSecurityRealm("http://example.invalid", "ikey", Secret.fromString(""), "admin", Secret.fromString("x"));
        assertThrows(IllegalStateException.class, () -> EzkeyLabLoginSupport.newClient(realm));
    }

    @Test
    void challengeMissingExceptionCarriesAttemptId() {
        var ex = new EzkeyLabLoginSupport.ChallengeMissingException(42);
        assertEquals(42, ex.getAuthAttemptId());
    }
}
