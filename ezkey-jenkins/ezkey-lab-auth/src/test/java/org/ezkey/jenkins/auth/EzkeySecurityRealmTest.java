package org.ezkey.jenkins.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.util.Secret;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;

class EzkeySecurityRealmTest {

    private EzkeySecurityRealm realm() {
        return new EzkeySecurityRealm(
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
    void nonBreakGlassPasswordPathRejected() {
        assertThrows(BadCredentialsException.class, () -> realm().authenticateByPassword("alice", "any-password"));
    }

    @Test
    void sanitizeStripsNewlines() {
        assertTrue(EzkeySecurityRealm.sanitizeForLog("a\rb\nc").equals("a_b_c"));
    }

    @Test
    void loginUrlIsEzkeySignIn() {
        assertEquals("ezkey-login", realm().getLoginUrl());
    }

    @Test
    void unknownUserIsNotAJenkinsUser() {
        assertThrows(
                org.springframework.security.core.userdetails.UsernameNotFoundException.class,
                () -> realm().loadUserByUsername2("nobody"));
    }

    @Test
    void enabledUserCanBeLoaded() {
        EzkeySecurityRealm realm = realm();
        realm.setEnabledUsers(java.util.List.of(new EzkeySecurityRealm.EzkeyAccount("labuser", "lab-id")));
        assertEquals("labuser", realm.loadUserByUsername2("labuser").getUsername());
        assertEquals("lab-id", realm.integrationUserIdentifier("labuser"));
    }
}
