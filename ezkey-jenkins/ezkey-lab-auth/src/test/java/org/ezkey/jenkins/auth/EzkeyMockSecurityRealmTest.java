package org.ezkey.jenkins.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.util.Secret;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;

class EzkeyMockSecurityRealmTest {

    private EzkeyMockSecurityRealm realm() {
        return new EzkeyMockSecurityRealm(
                "https://example.invalid/ezkey",
                "int-key-1",
                Secret.fromString("lab-secret-value"),
                "admin",
                Secret.fromString("ezkey-lab-admin"));
    }

    @Test
    void breakGlassAcceptsCorrectPassword() {
        UserDetails details = realm().authenticateByPassword("admin", "ezkey-lab-admin");
        assertEquals("admin", details.getUsername());
    }

    @Test
    void breakGlassRejectsWrongPassword() {
        assertThrows(BadCredentialsException.class, () -> realm().authenticateByPassword("admin", "wrong"));
    }

    @Test
    void mockAutoAcceptsOtherUsers() {
        UserDetails details = realm().authenticateByPassword("alice", "any-password");
        assertEquals("alice", details.getUsername());
    }

    @Test
    void mockRejectsEmptyPassword() {
        assertThrows(BadCredentialsException.class, () -> realm().authenticateByPassword("alice", ""));
    }

    @Test
    void sanitizeStripsNewlines() {
        assertTrue(EzkeyMockSecurityRealm.sanitizeForLog("a\rb\nc").equals("a_b_c"));
    }
}
