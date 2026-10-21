package org.ezkey.jenkins.auth;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.security.AbstractPasswordBasedSecurityRealm;
import hudson.security.GroupDetails;
import hudson.security.SecurityRealm;
import hudson.util.Secret;
import java.util.Collections;
import java.util.logging.Logger;
import org.kohsuke.stapler.DataBoundConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * Lab-only mock SecurityRealm for #972 M2.
 *
 * <p>Break-glass admin authenticates with a local password (anti-lockout, PAM spirit). All other
 * usernames are auto-accepted after a non-empty password — simulating an Ezkey device approve.
 * Real Ezkey HTTP round-trips are M4.
 *
 * <p>The Ezkey integration secret is stored as {@link Secret} (Jenkins secret handling). It must
 * never appear in logs. Credentials-plugin {@code StringCredentials} is preferred for production;
 * deferred here because the lab skips the setup wizard and avoids a large detached-plugin fan-out.
 *
 * @since 2026
 */
public class EzkeyMockSecurityRealm extends AbstractPasswordBasedSecurityRealm {

    private static final Logger LOGGER = Logger.getLogger(EzkeyMockSecurityRealm.class.getName());

    private final String ezkeyBaseUrl;
    private final String integrationKey;
    private final Secret ezkeySecret;
    private final String breakGlassUsername;
    private final Secret breakGlassPassword;

    /**
     * @param ezkeyBaseUrl Integration / Auth API base URL (config only in M2; unused for HTTP)
     * @param integrationKey Ezkey integration key id/string (config only in M2)
     * @param ezkeySecret Ezkey integration secret (Jenkins {@link Secret}; never logged)
     * @param breakGlassUsername local admin username that bypasses the mock Ezkey path
     * @param breakGlassPassword local password for the break-glass user
     */
    @DataBoundConstructor
    public EzkeyMockSecurityRealm(
            String ezkeyBaseUrl,
            String integrationKey,
            Secret ezkeySecret,
            String breakGlassUsername,
            Secret breakGlassPassword) {
        this.ezkeyBaseUrl = ezkeyBaseUrl == null ? "" : ezkeyBaseUrl.trim();
        this.integrationKey = integrationKey == null ? "" : integrationKey.trim();
        this.ezkeySecret = ezkeySecret;
        this.breakGlassUsername =
                breakGlassUsername == null || breakGlassUsername.isBlank() ? "admin" : breakGlassUsername.trim();
        this.breakGlassPassword = breakGlassPassword;
    }

    public String getEzkeyBaseUrl() {
        return ezkeyBaseUrl;
    }

    public String getIntegrationKey() {
        return integrationKey;
    }

    public Secret getEzkeySecret() {
        return ezkeySecret;
    }

    public String getBreakGlassUsername() {
        return breakGlassUsername;
    }

    public Secret getBreakGlassPassword() {
        return breakGlassPassword;
    }

    @Override
    protected UserDetails authenticate2(String username, String password) {
        if (username == null || username.isBlank()) {
            throw new BadCredentialsException("username required");
        }
        if (password == null || password.isEmpty()) {
            throw new BadCredentialsException("password required");
        }

        if (breakGlassUsername.equals(username)) {
            String expected = Secret.toString(breakGlassPassword);
            if (expected == null || expected.isEmpty() || !expected.equals(password)) {
                LOGGER.warning("ezkey-lab break-glass login rejected for user=" + sanitizeForLog(username));
                throw new BadCredentialsException("break-glass credentials rejected");
            }
            LOGGER.info("ezkey-lab break-glass login ok user=" + sanitizeForLog(username));
            return userDetails(username);
        }

        // Mock Ezkey path (M2): require secret configured, then auto-accept.
        String secretPlain = Secret.toString(ezkeySecret);
        if (secretPlain == null || secretPlain.isEmpty()) {
            LOGGER.warning("ezkey-lab mock login denied: ezkey secret not configured");
            throw new BadCredentialsException("Ezkey secret not configured");
        }
        if (ezkeyBaseUrl.isEmpty() || integrationKey.isEmpty()) {
            LOGGER.warning("ezkey-lab mock login denied: base URL or integration key missing");
            throw new BadCredentialsException("Ezkey base URL and integration key required");
        }
        // Secret intentionally never logged. Challenge-display rules for M3/M4: see DESIGN.md.
        LOGGER.info("ezkey-lab mock auto-accept user="
                + sanitizeForLog(username)
                + " baseUrl="
                + sanitizeForLog(ezkeyBaseUrl)
                + " integrationKey="
                + sanitizeForLog(integrationKey));
        return userDetails(username);
    }

    @Override
    public UserDetails loadUserByUsername2(String username) throws UsernameNotFoundException {
        if (username == null || username.isBlank()) {
            throw new UsernameNotFoundException("empty");
        }
        return userDetails(username);
    }

    private static UserDetails userDetails(String username) {
        return new User(username, "", true, true, true, true, Collections.singleton(AUTHENTICATED_AUTHORITY2));
    }

    @Override
    public GroupDetails loadGroupByGroupname2(String groupname, boolean fetchMembers) {
        return new GroupDetails() {
            @Override
            public String getName() {
                return groupname;
            }
        };
    }

    static String sanitizeForLog(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            sb.append(c == '\r' || c == '\n' ? '_' : c);
        }
        return sb.toString();
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<SecurityRealm> {
        @NonNull
        @Override
        public String getDisplayName() {
            return "Ezkey Lab Mock Auth (experimental)";
        }
    }
}
